package se.anatom.ejbca.protocol;

import java.io.IOException;
import java.io.Serializable;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.log4j.Logger;

import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.smime.SMIMECapability;

import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;

import se.anatom.ejbca.util.Base64;
import se.anatom.ejbca.util.CertTools;

/**
 * A response message for scep (pkcs7).
 *
 * @version $Id: ScepResponseMessage.java,v 1.11 2003-10-09 08:46:27 anatom Exp $
 */
public class ScepResponseMessage implements IResponseMessage, Serializable {
    private static Logger log = Logger.getLogger(ScepResponseMessage.class);

    /** The encoded response message */
    private byte[] responseMessage = null;

    /** status for the response */
    private ResponseStatus status = ResponseStatus.SUCCESS;

    /** Possible fail information in the response. Defaults to 'badRequest (2)'. */
    private FailInfo failInfo = FailInfo.BAD_REQUEST;

    /**
     * SenderNonce. This is base64 encoded bytes
     */
    private String senderNonce = null;
    /**
     * RecipientNonce in a response is the senderNonce from the request. This is base64 encoded bytes
     */
    private String recipientNonce = null;
    
    /** transaction id */
    private String transactionId = null;

    /** recipient key identifier, usually IssuerAndSerialno in X509 world. */
    private byte[] recipientKeyInfo = null;
    
    /** The un-encoded response message itself */
    private transient CMSSignedData signedData = null;

    /** Certificate to be in response message, not serialized */
    private transient Certificate cert = null;
    private transient X509Certificate signCert = null;
    private transient PrivateKey signKey = null;

    /**
     * Sets the complete certificate in the response message.
     *
     * @param cert certificate in the response message.
     */
    public void setCertificate(Certificate cert) {
        this.cert = cert;
    }

    /**
     * Gets the response message in the default encoding format.
     *
     * @return the response message in the default encoding format.
     */
    public byte[] getResponseMessage() throws IOException, CertificateEncodingException {
        return responseMessage;
    }

    /**
     * Sets the status of the response message.
     *
     * @param status status of the response.
     */
    public void setStatus(ResponseStatus status) {
        this.status = status;
    }

    /**
     * Gets the status of the response message.
     *
     * @return status status of the response.
     */
    public ResponseStatus getStatus() {
        return status;
    }

    /**
     * Sets info about reason for failure.
     *
     * @param failInfo reason for failure.
     */
    public void setFailInfo(FailInfo failInfo) {
        this.failInfo = failInfo;
    }

    /**
     * Gets info about reason for failure.
     *
     * @return failInfo reason for failure.
     */
    public FailInfo getFailInfo() {
        return failInfo;
    }

    /**
     * Create encrypts and creates signatures as needed to produce a complete response message.  If
     * needed setSignKeyInfo and setEncKeyInfo must be called before this method. After this is
     * called the response message can be retrieved with getResponseMessage();
     *
     * @return True if signature/encryption was successful, false if it failed, request should not
     *         be sent back i failed.
     *
     * @throws IOException If input/output or encoding failed.
     * @throws InvalidKeyException If the key used for signing/encryption is invalid.
     * @throws NoSuchProviderException if there is an error with the Provider.
     * @throws NoSuchAlgorithmException if the signature on the request is done with an unhandled
     *         algorithm.
     *
     * @see #setSignKeyInfo
     * @see #setEncKeyInfo
     */
    public boolean create()
        throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
        boolean ret = false;

        try {

            if (status.equals(ResponseStatus.SUCCESS)) {
                log.debug("Creating a STATUS_OK message.");
            } else {
                log.debug("Creating a STATUS_FAILED message.");
            }

            CMSProcessable msg;

            if (status.equals(ResponseStatus.SUCCESS)) {

                CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();
                // Add the issued certificate to the signed portion of the CMS (as signer, degenerate case)
                ArrayList certList = new ArrayList();

                certList.add(cert);

                certList.add(signCert);
                CertStore certs = CertStore.getInstance("Collection",
                        new CollectionCertStoreParameters(certList), "BC");

                // Create the signed CMS message to be contained inside the envelope
                msg = new CMSProcessableByteArray("PrimeKey".getBytes());
                CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                gen.addSigner(signKey, signCert, CMSSignedDataGenerator.DIGEST_SHA1);
                gen.addCertificatesAndCRLs(certs);

                CMSSignedData s = gen.generate(msg, true, "BC");

                // Envelope the CMS message

                if (recipientKeyInfo != null) {
                    try {
                    X509Certificate rec = CertTools.getCertfromByteArray(recipientKeyInfo);
                    edGen.addKeyTransRecipient(rec);
                    } catch (CertificateException e) {
                        throw new IOException("Can not decode recipients self signed certificate!");
                    }
                } else {
                    edGen.addKeyTransRecipient((X509Certificate) cert);
                }


                CMSEnvelopedData ed = edGen.generate(new CMSProcessableByteArray(s.getEncoded()),
                                    SMIMECapability.dES_CBC.getId(), "BC");

                log.debug("Signed data is " + ed.getEncoded().length +" bytes long");

                msg = new CMSProcessableByteArray(ed.getEncoded());
            } else {
                //TODO : Create an empty message here - causes problems for now
                msg = new CMSProcessableByteArray(new byte[]{0});
            }

            // Create the outermost signed data

            CMSSignedDataGenerator gen1 = new CMSSignedDataGenerator();

            // add authenticated attributes...status, transactionId, sender- and recipientNonce and more...
            Hashtable attributes = new Hashtable();

            // Content Type
            DERObjectIdentifier oid = PKCSObjectIdentifiers.pkcs_9_at_contentType;
            DERSet value = new DERSet(PKCSObjectIdentifiers.data);
            Attribute attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            // Message digest
            oid = PKCSObjectIdentifiers.pkcs_9_at_messageDigest;
            value = new DERSet(new DEROctetString("foo".getBytes()));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            // Message type (certrep)
            oid = new DERObjectIdentifier(ScepRequestMessage.id_messageType);
            value = new DERSet(new DERPrintableString("3"));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            // TransactionId
            if (transactionId != null) {
                oid = new DERObjectIdentifier(ScepRequestMessage.id_transId);
                value = new DERSet(new DERPrintableString(transactionId));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // status
            oid = new DERObjectIdentifier(ScepRequestMessage.id_pkiStatus);
            value = new DERSet(new DERPrintableString(status.getValue()));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);

            if (status.equals(ResponseStatus.FAILURE)) {
                oid = new DERObjectIdentifier(ScepRequestMessage.id_failInfo);
                log.debug("Added failInfo: "+failInfo.getValue());
                value = new DERSet(new DERPrintableString(failInfo.getValue()));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // senderNonce
            if (senderNonce != null) {
                oid = new DERObjectIdentifier(ScepRequestMessage.id_senderNonce);
                log.debug("Added senderNonce: "+senderNonce);
                value = new DERSet(new DEROctetString(Base64.decode(senderNonce.getBytes())));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // recipientNonce
            if (recipientNonce != null) {
                oid = new DERObjectIdentifier(ScepRequestMessage.id_recipientNonce);
                log.debug("Added recipientNonce: "+recipientNonce);                
                value = new DERSet(new DEROctetString(Base64.decode(recipientNonce.getBytes())));
                attr = new Attribute(oid, value);
                attributes.put(attr.getAttrType(), attr);
            }

            // Put our signer info and all newly generated attributes
            gen1.addSigner(signKey, signCert, CMSSignedDataGenerator.DIGEST_SHA1,
                new AttributeTable(attributes), null);
            signedData = gen1.generate(msg, true, "BC");

            responseMessage = signedData.getEncoded();

            if (responseMessage != null) {
                ret = true;
            }
        } catch (InvalidAlgorithmParameterException e) {
            log.error("Error creating CertStore: ", e);
        } catch (CertStoreException e) {
            log.error("Error creating CertStore: ", e);
        } catch (CMSException e) {
            log.error("Error creating CMS message: ", e);
        }

        return ret;
    }

    /**
     * indicates if this message needs recipients public and private key to sign. If this returns
     * true, setSignKeyInfo() should be called.
     *
     * @return True if public and private key is needed.
     */
    public boolean requireSignKeyInfo() {
        return true;
    }

    /**
     * indicates if this message needs recipients public and private key to encrypt. If this
     * returns true, setEncKeyInfo() should be called.
     *
     * @return True if public and private key is needed.
     */
    public boolean requireEncKeyInfo() {
        return false;
    }

    /**
     * Sets the public and private key needed to sign the message. Must be set if
     * requireSignKeyInfo() returns true.
     *
     * @param cert certificate containing the public key.
     * @param key private key.
     *
     * @see #requireSignKeyInfo()
     */
    public void setSignKeyInfo(X509Certificate cert, PrivateKey key) {
        signCert = cert;
        signKey = key;
    }

    /**
     * Sets the public and private key needed to encrypt the message. Must be set if
     * requireEncKeyInfo() returns true.
     *
     * @param cert certificate containing the public key.
     * @param key private key.
     *
     * @see #requireEncKeyInfo()
     */
    public void setEncKeyInfo(X509Certificate cert, PrivateKey key) {
        // We don't need these.
    }

    /**
     * Sets a senderNonce if it should be present in the response
     *
     * @param senderNonce a string of base64 encoded bytes
     */
    public void setSenderNonce(String senderNonce) {
        this.senderNonce = senderNonce;
    }
    /**
     * Sets a recipient if it should be present in the response
     *
     * @param recipientNonce a string of base64 encoded bytes
     */
    public void setRecipientNonce(String recipientNonce) {
        this.recipientNonce = recipientNonce;
    }

    /**
     * Sets a transaction identifier if it should be present in the response
     *
     * @param transactionId transaction id
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    /**
     * Sets recipient key info, key id or similar. This is the requestors self-signed cert from the request message.
     *
     * @param recipientKeyInfo key info
     */
    public void setRecipientKeyInfo(byte[] recipientKeyInfo) {
        this.recipientKeyInfo = recipientKeyInfo;
    }
    
}
