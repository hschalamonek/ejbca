/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/ 
package org.cesecore.certificates.ca;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEREnumerated;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.jce.X509KeyUsage;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Hex;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.certificate.request.PKCS10RequestMessage;
import org.cesecore.certificates.certificateprofile.CertificatePolicy;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.AlgorithmTools;
import org.cesecore.certificates.util.cert.CrlExtensions;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenFactory;
import org.cesecore.keys.token.SoftCryptoToken;
import org.cesecore.keys.token.p11.exception.NoSuchSlotException;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.util.CeSecoreNameStyle;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.StringTools;
import org.junit.Test;

/** JUnit test for X.509 CA
 * 
 * @version $Id$
 */
public class X509CATest {

	public static final String CADN = "CN=TEST";
	
	public X509CATest() {
		CryptoProviderTools.installBCProvider();
	}
	
	@Test
	public void testX509CABasicOperationsRSA() throws Exception {
	    doTestX509CABasicOperations(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
	}
	
	@Test
    public void testX509CABasicOperationsGOST() throws Exception {
	    assumeTrue(AlgorithmTools.isGost3410Enabled());
        doTestX509CABasicOperations(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410);
    }
    
    @Test
    public void testX509CABasicOperationsDSTU() throws Exception {
        assumeTrue(AlgorithmTools.isDstu4145Enabled());
        doTestX509CABasicOperations(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145);
    }
	
    private void doTestX509CABasicOperations(String algName) throws Exception {
	    final CryptoToken cryptoToken = getNewCryptoToken();
        final X509CA x509ca = createTestCA(cryptoToken, CADN);
        Certificate cacert = x509ca.getCACertificate();
        
        // Start by creating a PKCS7
        byte[] p7 = x509ca.createPKCS7(cryptoToken, cacert, true);
        assertNotNull(p7);
        CMSSignedData s = new CMSSignedData(p7);
        CertStore certstore = s.getCertificatesAndCRLs("Collection","BC");
        Collection<?> certs = certstore.getCertificates(null);
        assertEquals(2, certs.size());
        p7 = x509ca.createPKCS7(cryptoToken, cacert, false);
        assertNotNull(p7);
        s = new CMSSignedData(p7);
        certstore = s.getCertificatesAndCRLs("Collection","BC");
        certs = certstore.getCertificates(null);
        assertEquals(1, certs.size());
        
		// Create a certificate request (will be pkcs10)
        byte[] req = x509ca.createRequest(cryptoToken, null, algName, cacert, CATokenConstants.CAKEYPURPOSE_CERTSIGN);
        PKCS10CertificationRequest p10 = new PKCS10CertificationRequest(req);
        assertNotNull(p10);
        String dn = p10.getSubject().toString();
        assertEquals(CADN, dn);
        
        // Make a request with some pkcs11 attributes as well
		Collection<ASN1Encodable> attributes = new ArrayList<ASN1Encodable>();
		// Add a subject alternative name
		ASN1EncodableVector altnameattr = new ASN1EncodableVector();
		altnameattr.add(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
		GeneralNames san = CertTools.getGeneralNamesFromAltName("dNSName=foobar.bar.com");
		ExtensionsGenerator extgen = new ExtensionsGenerator();
		extgen.addExtension(Extension.subjectAlternativeName, false, san);
		Extensions exts = extgen.generate();
		altnameattr.add(new DERSet(exts));
        // Add a challenge password as well
        ASN1EncodableVector pwdattr = new ASN1EncodableVector();
        pwdattr.add(PKCSObjectIdentifiers.pkcs_9_at_challengePassword); 
        ASN1EncodableVector pwdvalues = new ASN1EncodableVector();
        pwdvalues.add(new DERUTF8String("foobar123"));
        pwdattr.add(new DERSet(pwdvalues));
        attributes.add(new DERSequence(altnameattr));
        attributes.add(new DERSequence(pwdattr));
        // create the p10
        req = x509ca.createRequest(cryptoToken, attributes, algName, cacert, CATokenConstants.CAKEYPURPOSE_CERTSIGN);
        p10 = new PKCS10CertificationRequest(req);
        assertNotNull(p10);
        dn = p10.getSubject().toString();
        assertEquals(CADN, dn);
        Attribute[] attrs = p10.getAttributes();
        assertEquals(2, attrs.length);
        PKCS10RequestMessage p10msg = new PKCS10RequestMessage(new JcaPKCS10CertificationRequest(p10));
        assertEquals("foobar123", p10msg.getPassword());
        assertEquals("dNSName=foobar.bar.com", p10msg.getRequestAltNames());

        try {
            x509ca.createAuthCertSignRequest(cryptoToken, p10.getEncoded());
        } catch (UnsupportedOperationException e) {
            // Expected for a X509 CA
        }
        
        // Generate a client certificate and check that it was generated correctly
        EndEntityInformation user = new EndEntityInformation("username", "CN=User", 666, "rfc822Name=user@user.com", "user@user.com", new EndEntityType(EndEntityTypes.ENDUSER), 0, 0, EndEntityConstants.TOKEN_USERGEN, 0, null);
        KeyPair keypair = genTestKeyPair(algName);
        CertificateProfile cp = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        cp.addCertificatePolicy(new CertificatePolicy("1.1.1.2", null, null));
        cp.setUseCertificatePolicies(true);
        Certificate usercert = x509ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
        assertNotNull(usercert);
        assertEquals("CN=User", CertTools.getSubjectDN(usercert));
        assertEquals(CADN, CertTools.getIssuerDN(usercert));
        assertEquals(getTestKeyPairAlgName(algName).toUpperCase(), AlgorithmTools.getCertSignatureAlgorithmNameAsString(usercert).toUpperCase());
        assertEquals(new String(CertTools.getSubjectKeyId(cacert)), new String(CertTools.getAuthorityKeyId(usercert)));
        assertEquals("user@user.com", CertTools.getEMailAddress(usercert));
        assertEquals("rfc822name=user@user.com", CertTools.getSubjectAlternativeName(usercert));
        assertNull(CertTools.getUPNAltName(usercert));
        assertFalse(CertTools.isSelfSigned(usercert));
        usercert.verify(cryptoToken.getPublicKey(x509ca.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN)));
        usercert.verify(x509ca.getCACertificate().getPublicKey());
        assertTrue(CertTools.isCA(x509ca.getCACertificate()));
        assertFalse(CertTools.isCA(usercert));
        assertEquals("1.1.1.2", CertTools.getCertificatePolicyId(usercert, 0));
        X509Certificate cert = (X509Certificate)usercert;
        boolean[] ku = cert.getKeyUsage();
        assertTrue(ku[0]);
        assertTrue(ku[1]);
        assertTrue(ku[2]);
        assertFalse(ku[3]);
        assertFalse(ku[4]);
        assertFalse(ku[5]);
        assertFalse(ku[6]);
        assertFalse(ku[7]);
        int bcku = CertTools.sunKeyUsageToBC(ku);
        assertEquals(X509KeyUsage.digitalSignature|X509KeyUsage.nonRepudiation|X509KeyUsage.keyEncipherment, bcku);
        
        // Create a CRL
        Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
        X509CRLHolder crl = x509ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
        X509CRL xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        assertEquals(CADN, CertTools.getIssuerDN(xcrl));
        Set<?> set = xcrl.getRevokedCertificates();
        assertNull(set);
        BigInteger num = CrlExtensions.getCrlNumber(xcrl);
        assertEquals(1, num.intValue());
        BigInteger deltanum = CrlExtensions.getDeltaCRLIndicator(xcrl);
        assertEquals(-1, deltanum.intValue());
        // Revoke some cert
        Date revDate = new Date();
        revcerts.add(new RevokedCertInfo(CertTools.getFingerprintAsString(usercert).getBytes(), CertTools.getSerialNumber(usercert).toByteArray(), revDate.getTime(), RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, CertTools.getNotAfter(usercert).getTime()));
        crl = x509ca.generateCRL(cryptoToken, revcerts, 2);
        assertNotNull(crl);
        xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        set = xcrl.getRevokedCertificates();
        assertEquals(1, set.size());
        num = CrlExtensions.getCrlNumber(xcrl);
        assertEquals(2, num.intValue());
        X509CRLEntry entry = (X509CRLEntry)set.iterator().next();
        assertEquals(CertTools.getSerialNumber(usercert).toString(), entry.getSerialNumber().toString());
        assertEquals(revDate.toString(), entry.getRevocationDate().toString());
        // Getting the revocation reason is a pita...
        byte[] extval = entry.getExtensionValue(Extension.reasonCode.getId());
        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(extval));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        ASN1Primitive obj = aIn.readObject();
        CRLReason reason = CRLReason.getInstance((DEREnumerated)obj);
        assertEquals("CRLReason: certificateHold", reason.toString());
        //DEROctetString ostr = (DEROctetString)obj;
        
        // Create a delta CRL
        revcerts = new ArrayList<RevokedCertInfo>();
        crl = x509ca.generateDeltaCRL(cryptoToken, revcerts, 3, 2);
        assertNotNull(crl);
        xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        assertEquals(CADN, CertTools.getIssuerDN(xcrl));
        set = xcrl.getRevokedCertificates();
        assertNull(set);
        num = CrlExtensions.getCrlNumber(xcrl);
        assertEquals(3, num.intValue());
        deltanum = CrlExtensions.getDeltaCRLIndicator(xcrl);
        assertEquals(2, deltanum.intValue());
        revcerts.add(new RevokedCertInfo(CertTools.getFingerprintAsString(usercert).getBytes(), CertTools.getSerialNumber(usercert).toByteArray(), revDate.getTime(), RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, CertTools.getNotAfter(usercert).getTime()));
        crl = x509ca.generateDeltaCRL(cryptoToken, revcerts, 4, 3);
        assertNotNull(crl);
        xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        deltanum = CrlExtensions.getDeltaCRLIndicator(xcrl);
        assertEquals(3, deltanum.intValue());
        set = xcrl.getRevokedCertificates();
        assertEquals(1, set.size());
        entry = (X509CRLEntry)set.iterator().next();
        assertEquals(CertTools.getSerialNumber(usercert).toString(), entry.getSerialNumber().toString());
        assertEquals(revDate.toString(), entry.getRevocationDate().toString());
        // Getting the revocation reason is a pita...
        extval = entry.getExtensionValue(Extension.reasonCode.getId());
        aIn = new ASN1InputStream(new ByteArrayInputStream(extval));
        octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        obj = aIn.readObject();
        reason = CRLReason.getInstance((DEREnumerated)obj);
        assertEquals("CRLReason: certificateHold", reason.toString());
	}
	
    /**
     * Tests the extension CRL Distribution Point on CRLs
     * 
     */
	@Test
	public void testCRLDistPointOnCRL() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
        final X509CA ca = createTestCA(cryptoToken, CADN);

        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        X509CAInfo cainfo = (X509CAInfo) ca.getCAInfo();

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        ca.updateCA(cryptoToken, cainfo);
        
        Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
        X509CRLHolder crl = ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
        X509CRL xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());

        byte[] cdpDER = xcrl.getExtensionValue(Extension.issuingDistributionPoint.getId());
        assertNotNull("CRL has no distribution points", cdpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cdpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        IssuingDistributionPoint cdp = IssuingDistributionPoint.getInstance((ASN1Sequence) aIn.readObject());
        DistributionPointName distpoint = cdp.getDistributionPoint();

        assertEquals("CRL distribution point is different", cdpURL, ((DERIA5String) ((GeneralNames) distpoint.getName()).getNames()[0].getName()).getString());

        cainfo.setUseCrlDistributionPointOnCrl(false);
        cainfo.setDefaultCRLDistPoint(null);
        ca.updateCA(cryptoToken, cainfo);
        crl = ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
        xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        assertNull("CRL has distribution points", xcrl.getExtensionValue(Extension.cRLDistributionPoints.getId()));
    }

    /**
     * Tests the extension Freshest CRL DP.
     * 
     * @throws Exception
     *             in case of error.
     */
	@Test
    public void testCRLFreshestCRL() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
        final X509CA ca = createTestCA(cryptoToken, CADN);
        final String cdpURL = "http://www.ejbca.org/foo/bar.crl";
        final String freshestCdpURL = "http://www.ejbca.org/foo/delta.crl";
        X509CAInfo cainfo = (X509CAInfo) ca.getCAInfo();

        cainfo.setUseCrlDistributionPointOnCrl(true);
        cainfo.setDefaultCRLDistPoint(cdpURL);
        cainfo.setCADefinedFreshestCRL(freshestCdpURL);
        ca.updateCA(cryptoToken, cainfo);

        Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
        X509CRLHolder crl = ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
        X509CRL xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());

        byte[] cFreshestDpDER = xcrl.getExtensionValue(Extension.freshestCRL.getId());
        assertNotNull("CRL has no Freshest Distribution Point", cFreshestDpDER);

        ASN1InputStream aIn = new ASN1InputStream(new ByteArrayInputStream(cFreshestDpDER));
        ASN1OctetString octs = (ASN1OctetString) aIn.readObject();
        aIn = new ASN1InputStream(new ByteArrayInputStream(octs.getOctets()));
        CRLDistPoint cdp = CRLDistPoint.getInstance((ASN1Sequence) aIn.readObject());
        DistributionPoint[] distpoints = cdp.getDistributionPoints();

        assertEquals("More CRL Freshest distributions points than expected", 1, distpoints.length);
        assertEquals("Freshest CRL distribution point is different", freshestCdpURL, ((DERIA5String) ((GeneralNames) distpoints[0].getDistributionPoint()
                .getName()).getNames()[0].getName()).getString());

        cainfo.setUseCrlDistributionPointOnCrl(false);
        cainfo.setDefaultCRLDistPoint(null);
        cainfo.setCADefinedFreshestCRL(null);
        ca.updateCA(cryptoToken, cainfo);

        crl = ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
        xcrl = CertTools.getCRLfromByteArray(crl.getEncoded());
        assertNull("CRL has freshest crl extension", xcrl.getExtensionValue(Extension.freshestCRL.getId()));
    }

	@Test
    public void testStoreAndLoadRSA() throws Exception {
	    doTestStoreAndLoad(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
    }
    
    @Test
    public void testStoreAndLoadGOST() throws Exception {
        assumeTrue(AlgorithmTools.isGost3410Enabled());
        doTestStoreAndLoad(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410);
    }
    
    @Test
    public void testStoreAndLoadDSTU() throws Exception {
        assumeTrue(AlgorithmTools.isDstu4145Enabled());
        doTestStoreAndLoad(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145);
    }
	
	private void doTestStoreAndLoad(String algName) throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
		final X509CA ca = createTestCA(cryptoToken, CADN);
		
        EndEntityInformation user = new EndEntityInformation("username", "CN=User", 666, "rfc822Name=user@user.com", "user@user.com", new EndEntityType(EndEntityTypes.ENDUSER), 0, 0, EndEntityConstants.TOKEN_USERGEN, 0, null);
        KeyPair keypair = genTestKeyPair(algName);
        CertificateProfile cp = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        cp.addCertificatePolicy(new CertificatePolicy("1.1.1.2", null, null));
        cp.setUseCertificatePolicies(true);
        Certificate usercert = ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
        String authKeyId = new String(Hex.encode(CertTools.getAuthorityKeyId(usercert)));
        String keyhash = CertTools.getFingerprintAsString(cryptoToken.getPublicKey(ca.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN)).getEncoded());

        // Save CA data
		Object o = ca.saveData();
		
		// Restore CA from data (and other things)
		@SuppressWarnings({ "rawtypes", "unchecked" })
        X509CA ca1 = new X509CA((HashMap)o, 777, CADN, "test", CAConstants.CA_ACTIVE, new Date(), new Date());

		Certificate usercert1 = ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");

        String authKeyId1 = new String(Hex.encode(CertTools.getAuthorityKeyId(usercert1)));
        PublicKey publicKey1 = cryptoToken.getPublicKey(ca1.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        String keyhash1 = CertTools.getFingerprintAsString(publicKey1.getEncoded());
        assertEquals(authKeyId, authKeyId1);
        assertEquals(keyhash, keyhash1);
        
        CAInfo cainfo = ca.getCAInfo();
        CAData cadata = new CAData(cainfo.getSubjectDN(), cainfo.getName(), cainfo.getStatus(), ca);

        CA ca2 = cadata.getCA();
		Certificate usercert2 = ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
        String authKeyId2 = new String(Hex.encode(CertTools.getAuthorityKeyId(usercert2)));
        PublicKey publicKey2 = cryptoToken.getPublicKey(ca2.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        String keyhash2 = CertTools.getFingerprintAsString(publicKey2.getEncoded());
        assertEquals(authKeyId, authKeyId2);
        assertEquals(keyhash, keyhash2);
        
        // Check CAinfo and CAtokeninfo
        final CAInfo cainfo1 = ca.getCAInfo();
        final CAToken caToken1 = cainfo1.getCAToken();
        assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA, caToken1.getSignatureAlgorithm());
        assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA, caToken1.getEncryptionAlgorithm());
        assertEquals(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC, caToken1.getKeySequenceFormat());
        
        final CAInfo cainfo2 = ca2.getCAInfo();
        final CAToken caToken2 = cainfo2.getCAToken();
        assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA, caToken2.getSignatureAlgorithm());
        assertEquals(AlgorithmConstants.SIGALG_SHA256_WITH_RSA, caToken2.getEncryptionAlgorithm());
        assertEquals(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC, caToken2.getKeySequenceFormat());
	}

	@Test
	public void testExtendedCAServices() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
		final X509CA ca = createTestCA(cryptoToken, CADN);
		assertEquals(ca.getExternalCAServiceTypes().size(), 0);
		assertNull(ca.getExtendedCAServiceInfo(1));
		
		CAInfo info = ca.getCAInfo();
		Collection<ExtendedCAServiceInfo> infos = new ArrayList<ExtendedCAServiceInfo>();
		infos.add(new MyExtendedCAServiceInfo(0));
		info.setExtendedCAServiceInfos(infos);
		ca.updateCA(cryptoToken, info);
		
		assertEquals(ca.getExternalCAServiceTypes().size(), 1);
		assertNotNull(ca.getExtendedCAServiceInfo(4711));
		assertNull(ca.getExtendedCAServiceInfo(1));
		assertNotNull("org.cesecore.certificates.ca.MyExtendedCAServiceInfo", ca.getExtendedCAServiceInfo(4711).getClass().getName());
		
		// Try to run it
		assertEquals(0, MyExtendedCAService.didrun);
		ca.extendedService(cryptoToken, new MyExtendedCAServiceRequest());
		assertEquals(1, MyExtendedCAService.didrun);
		ca.extendedService(cryptoToken, new MyExtendedCAServiceRequest());
		assertEquals(2, MyExtendedCAService.didrun);
		
		// Does is store and load ok?
		Object o = ca.saveData();		
		// Restore CA from data (and other things)
		@SuppressWarnings({ "rawtypes", "unchecked" })
        X509CA ca1 = new X509CA((HashMap)o, 777, CADN, "test", CAConstants.CA_ACTIVE, new Date(), new Date());
		ca1.extendedService(cryptoToken, new MyExtendedCAServiceRequest());
		assertEquals(3, MyExtendedCAService.didrun);
	}

	@Test
	public void testCAInfo() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
		X509CA ca = createTestCA(cryptoToken, CADN);
		assertEquals(CAConstants.CA_ACTIVE, ca.getStatus());
		assertEquals(CAConstants.CA_ACTIVE, ca.getCAInfo().getStatus());
		ca.setStatus(CAConstants.CA_OFFLINE);
		assertEquals(CAConstants.CA_OFFLINE, ca.getStatus());
		assertEquals(CAConstants.CA_OFFLINE, ca.getCAInfo().getStatus());
	}
	
	@Test
	public void testInvalidSignatureAlg() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
		try {
			createTestCA(cryptoToken, CADN, "MD5WithRSA", null, null);
			fail("This should throw because md5withRSA is not an allowed signature algorithm. It is vulnerable.");
		} catch (InvalidAlgorithmException e) {
			// NOPMD: this is what we want
		}
		X509CA ca = createTestCA(cryptoToken, CADN, "SHA1WithRSA", null, null);
		assertNotNull("should work to create a CA", ca);
		CAToken token = new CAToken(0, new Properties());
		ca.setCAToken(token);
	}
	
	@Test
    public void testWrongCAKeyRSA() throws Exception {
        doTestWrongCAKey(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
    }
    
    @Test
    public void testWrongCAKeyGOST() throws Exception {
        assumeTrue(AlgorithmTools.isGost3410Enabled());
        doTestWrongCAKey(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410);
    }
	
	@Test
    public void testWrongCAKeyDSTU() throws Exception {
        assumeTrue(AlgorithmTools.isDstu4145Enabled());
        doTestWrongCAKey(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145);
    }
	
	public void doTestWrongCAKey(String algName) throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
	    X509CA x509ca = createTestCA(cryptoToken, CADN);

	    // Generate a client certificate and check that it was generated correctly
	    EndEntityInformation user = new EndEntityInformation("username", "CN=User", 666, "rfc822Name=user@user.com", "user@user.com", new EndEntityType(EndEntityTypes.ENDUSER), 0, 0, EndEntityConstants.TOKEN_USERGEN, 0, null);
	    KeyPair keypair = genTestKeyPair(algName);
	    CertificateProfile cp = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
	    cp.addCertificatePolicy(new CertificatePolicy("1.1.1.2", null, null));
	    cp.setUseCertificatePolicies(true);
	    Certificate usercert = x509ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
	    assertNotNull(usercert);
	    
	    // Change CA keys, but not CA certificate, should not work to issue a certificate with this CA, when the 
	    // issued cert can not be verified by the CA certificate
        cryptoToken.generateKeyPair(getTestKeySpec(algName), CAToken.SOFTPRIVATESIGNKEYALIAS);
	    
	    try {
	        usercert = x509ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
	        fail("should not work to issue this certificate");
	    } catch (SignatureException e) {} // NOPMD: BC 1.47
        try {
            Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
            x509ca.generateCRL(cryptoToken, revcerts, 1);
            fail("should not work to issue this CRL");
        } catch (SignatureException e) {
            // NOPMD: this is what we want
        }

	    // New CA certificate to make it work again
        PublicKey publicKey = cryptoToken.getPublicKey(x509ca.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        PrivateKey privateKey = cryptoToken.getPrivateKey(x509ca.getCAToken().getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        X509Certificate cacert = CertTools.genSelfCert(CADN, 10L, "1.1.1.1", privateKey, publicKey, "SHA256WithRSA", true);
	    assertNotNull(cacert);
	    Collection<Certificate> cachain = new ArrayList<Certificate>();
	    cachain.add(cacert);
	    x509ca.setCertificateChain(cachain);
        usercert = x509ca.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
        assertNotNull(usercert);
        Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
        X509CRLHolder crl = x509ca.generateCRL(cryptoToken, revcerts, 1);
        assertNotNull(crl);
	}
	
	/** Test implementation of Authority Information Access CRL Extension according to RFC 4325 */
	@Test
	public void testAuthorityInformationAccessCrlExtension() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
	    X509CA testCa = createTestCA(cryptoToken, "CN=foo");
	    Collection<String> authorityInformationAccess = new ArrayList<String>();
	    authorityInformationAccess.add("http://example.com/0");
	    authorityInformationAccess.add("http://example.com/1");
	    authorityInformationAccess.add("http://example.com/2");
	    authorityInformationAccess.add("http://example.com/3");
	    testCa.setAuthorityInformationAccess(authorityInformationAccess);
	    Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
	    X509CRLHolder testCrl = testCa.generateCRL(cryptoToken, revcerts, 0);	    
        assertNotNull(testCrl);
        X509CRL xcrl = CertTools.getCRLfromByteArray(testCrl.getEncoded());
	    Collection<String> result = CertTools.getAuthorityInformationAccess(xcrl);
	    assertEquals("Number of URLs do not match", authorityInformationAccess.size(), result.size());
	    for(String url : authorityInformationAccess) {
	        if(!result.contains(url)) {
	            fail("URL " + url + " was not found.");
	        }
	    }
	}
	
	/** Test implementation of Authority Information Access CRL Extension according to RFC 4325 */
    @Test
    public void testAuthorityInformationAccessCrlExtensionWithEmptyList() throws Exception{
        final CryptoToken cryptoToken = getNewCryptoToken();
        X509CA testCa = createTestCA(cryptoToken, "CN=foo");    
        Collection<RevokedCertInfo> revcerts = new ArrayList<RevokedCertInfo>();
        X509CRLHolder testCrl = testCa.generateCRL(cryptoToken, revcerts, 0);
        assertNotNull(testCrl);
        X509CRL xcrl = CertTools.getCRLfromByteArray(testCrl.getEncoded());
        Collection<String> result = CertTools.getAuthorityInformationAccess(xcrl);
        assertEquals("A list was returned without any values present.", 0, result.size());        
    }
	
    /** 
     * Test that the CA refuses to issue certificates outside of the PrivateKeyUsagePeriod, but that it does issue a cert within this period.
     * This test has some timing, so it sleeps in total 11 seconds during the test.
     */
    @Test
    public void testCAPrivateKeyUsagePeriodRequest() throws Exception {
        // User keypair, generate first so it will not take any seconds from the timing test below
        final KeyPair keypair = KeyTools.genKeys("512", "RSA");
        // Create a new CA with private key usage period
        final CryptoToken cryptoToken = getNewCryptoToken();
        Calendar notBefore = Calendar.getInstance();
        notBefore.add(Calendar.SECOND, 5); // 5 seconds in the future
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(Calendar.SECOND, 10); // 10 seconds in the future gives us a 5 second window to generate a cert
        X509CA testCa = createTestCA(cryptoToken, "CN=foo", "SHA256WithRSA", notBefore.getTime(), notAfter.getTime());    
        // Issue a certificate before PrivateKeyUsagePeriod has started to be valid
        EndEntityInformation user = new EndEntityInformation("username", "CN=User", 666, "rfc822Name=user@user.com", "user@user.com", new EndEntityType(EndEntityTypes.ENDUSER), 0, 0, EndEntityConstants.TOKEN_USERGEN, 0, null);
        CertificateProfile cp = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        cp.addCertificatePolicy(new CertificatePolicy("1.1.1.2", null, null));
        cp.setUseCertificatePolicies(true);
        try {
            testCa.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
            fail("Should throw CAOfflineException when trying to issue cert before PrivateKeyUsagePeriod starts.");
        } catch (CAOfflineException e) {
            // NOPMD: this is what we expect
        }
        // Issue a certificate within private key usage period
        // Sleep 6 seconds, now it should work
        Thread.sleep(6000);
        try {
            Certificate cert = testCa.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
            assertNotNull("A certificate should have been issued", cert);
        } catch (CAOfflineException e) {
            fail("Should not throw CAOfflineException when issuing a certificate within PrivateKeyUsagePeriod.");
        }
        // Issue a certificate after private key usage period expires
        // Sleep 5 seconds, now it should not work again since PrivateKeyUsagePeriod has expired
        Thread.sleep(5000);
        try {
            testCa.generateCertificate(cryptoToken, user, keypair.getPublic(), 0, null, 10L, cp, "00000");
            fail("Should throw CAOfflineException when trying to issue cert after PrivateKeyUsagePeriod ands.");
        } catch (CAOfflineException e) {
            // NOPMD: this is what we expect
        }
    }
    
    /**
     * Tests default value of "use printable string" option (should be disabled by default)
     * and tests that the option works.
     */
    @Test
    public void testPrintableString() throws Exception {
        final CryptoToken cryptoToken = getNewCryptoToken();
        final String caDN = "CN=foo CA,O=Bar,JurisdictionCountry=DE,JurisdictionState=Stockholm,JurisdictionLocality=Solna,C=SE";
        final X509CA testCa = createTestCA(cryptoToken, caDN);
        assertFalse("\"Use Printable String\" should be turned off by default", testCa.getUsePrintableStringSubjectDN());
        
        Certificate cert = testCa.getCACertificate();
        assertTrue("Certificate CN was not UTF-8 encoded by default.", getValueFromDN(cert, X509ObjectIdentifiers.commonName) instanceof DERUTF8String);
        assertTrue("Certificate C was not PrintableString encoded.", getValueFromDN(cert, X509ObjectIdentifiers.countryName) instanceof DERPrintableString); // C is always PrintableString
        
        // Test generation by calling generateCertificate directly
        final String subjectDN = "CN=foo subject,O=Bar,JurisdictionCountry=DE,JurisdictionState=Stockholm,JurisdictionLocality=Solna,C=SE";
        final EndEntityInformation subject = new EndEntityInformation("testPrintableString", subjectDN, testCa.getCAId(), null, null, new EndEntityType(EndEntityTypes.ENDUSER), 0, 0, EndEntityConstants.TOKEN_USERGEN, 0, null);
        final CertificateProfile certProfile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        cert = testCa.generateCertificate(cryptoToken, subject, cert.getPublicKey(), KeyUsage.digitalSignature | KeyUsage.keyEncipherment, null, 30, certProfile, null);
        assertTrue("Certificate CN was not UTF-8 encoded by default.", getValueFromDN(cert, X509ObjectIdentifiers.commonName) instanceof DERUTF8String);
        assertTrue("Certificate O was not UTF-8 encoded by default.", getValueFromDN(cert, X509ObjectIdentifiers.organization) instanceof DERUTF8String);
        assertTrue("Certificate JurisdictionState was not UTF-8 encoded.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_STATE) instanceof DERUTF8String);
        assertTrue("Certificate JurisdictionLocality was not UTF-8 encoded.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_LOCALITY) instanceof DERUTF8String);
        assertTrue("Certificate C was not PrintableString encoded.", getValueFromDN(cert, X509ObjectIdentifiers.countryName) instanceof DERPrintableString); // C is always PrintableString
        assertTrue("Certificate JurisdictionCountry was not PrintableString encoded.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_COUNTRY) instanceof DERPrintableString); // C is always PrintableString
        
        // Now generate a new certificate with a PrintableString-encoded DN
        testCa.setUsePrintableStringSubjectDN(true);
        cert = testCa.generateCertificate(cryptoToken, subject, cert.getPublicKey(), KeyUsage.digitalSignature | KeyUsage.keyEncipherment, null, 30, certProfile, null);
        assertTrue("Certificate CN was not encoded as PrintableString.", getValueFromDN(cert, X509ObjectIdentifiers.commonName) instanceof DERPrintableString);
        assertTrue("Certificate O was not encoded as PrintableString.", getValueFromDN(cert, X509ObjectIdentifiers.organization) instanceof DERPrintableString);
        assertTrue("Certificate JurisdictionState was not encoded as PrintableString.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_STATE) instanceof DERPrintableString);
        assertTrue("Certificate JurisdictionLocality was not encoded as PrintableString.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_LOCALITY) instanceof DERPrintableString);
        assertTrue("Certificate C was not PrintableString encoded.", getValueFromDN(cert, X509ObjectIdentifiers.countryName) instanceof DERPrintableString); // C is always PrintableString
        assertTrue("Certificate JurisdictionCountry was not PrintableString encoded.", getValueFromDN(cert, CeSecoreNameStyle.JURISDICTION_COUNTRY) instanceof DERPrintableString); // C is always PrintableString
    }
    
    private static ASN1Encodable getValueFromDN(Certificate cert, ASN1ObjectIdentifier oid) {
        final X500Principal principal = ((X509Certificate)cert).getSubjectX500Principal();
        final X500Name xname = X500Name.getInstance(principal.getEncoded());
        final RDN rdn = xname.getRDNs(oid)[0];
        return rdn.getTypesAndValues()[0].getValue();
    }

	private static X509CA createTestCA(CryptoToken cryptoToken, final String cadn) throws Exception {
		return createTestCA(cryptoToken, cadn, AlgorithmConstants.SIGALG_SHA256_WITH_RSA, null, null);
	}

	private static X509CA createTestCA(CryptoToken cryptoToken, final String cadn, final String sigAlg, Date notBefore, Date notAfter) throws Exception {
        cryptoToken.generateKeyPair(getTestKeySpec(sigAlg), CAToken.SOFTPRIVATESIGNKEYALIAS);
        cryptoToken.generateKeyPair(getTestKeySpec(sigAlg), CAToken.SOFTPRIVATEDECKEYALIAS);
        // Create CAToken
        Properties caTokenProperties = new Properties();
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS); 
        caTokenProperties.setProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING, CAToken.SOFTPRIVATEDECKEYALIAS);
		CAToken caToken = new CAToken(cryptoToken.getId(), caTokenProperties);
		// Set key sequence so that next sequence will be 00001 (this is the default though so not really needed here)
		caToken.setKeySequence(CAToken.DEFAULT_KEYSEQUENCE);
		caToken.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC);
		caToken.setSignatureAlgorithm(sigAlg);
		caToken.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA256_WITH_RSA);
        // No extended services
        X509CAInfo cainfo = new X509CAInfo(cadn, "TEST", CAConstants.CA_ACTIVE,
                CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA, 3650, CAInfo.SELFSIGNED, null, caToken);
        cainfo.setDescription("JUnit RSA CA");        
        X509CA x509ca = new X509CA(cainfo);
        x509ca.setCAToken(caToken);
        // A CA certificate
        final PublicKey publicKey = cryptoToken.getPublicKey(caToken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        final PrivateKey privateKey = cryptoToken.getPrivateKey(caToken.getAliasFromPurpose(CATokenConstants.CAKEYPURPOSE_CERTSIGN));
        int keyusage = X509KeyUsage.keyCertSign + X509KeyUsage.cRLSign;
        X509Certificate cacert = CertTools.genSelfCertForPurpose(cadn, 10L, "1.1.1.1", privateKey, publicKey, "SHA256WithRSA", true, keyusage, notBefore, notAfter, "BC");
		assertNotNull(cacert);
        Collection<Certificate> cachain = new ArrayList<Certificate>();
        cachain.add(cacert);
        x509ca.setCertificateChain(cachain);
        // Now our CA should be operational
        return x509ca;
	}

	/** @return a new empty soft auto-activated CryptoToken */
    private CryptoToken getNewCryptoToken() {
        final Properties cryptoTokenProperties = new Properties();
        cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, "foo1234");
        CryptoToken cryptoToken;
        try {
            cryptoToken = CryptoTokenFactory.createCryptoToken(
                    SoftCryptoToken.class.getName(), cryptoTokenProperties, null, 17, "CryptoToken's name");
        } catch (NoSuchSlotException e) {
            throw new RuntimeException("Attempted to find a slot for a soft crypto token. This should not happen.");
        }
        return cryptoToken;
    }
    
    private static KeyPair genTestKeyPair(String algName) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        if (algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410)) {
            final String keyspec = CesecoreConfiguration.getExtraAlgSubAlgName("gost3410", "B");
            return KeyTools.genKeys(keyspec, AlgorithmConstants.KEYALGORITHM_ECGOST3410);
        } else if(algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145)) {
            final String keyspec = CesecoreConfiguration.getExtraAlgSubAlgName("dstu4145", "233");
            return KeyTools.genKeys(keyspec, AlgorithmConstants.KEYALGORITHM_DSTU4145);
        } else {
            return KeyTools.genKeys("512", "RSA");
        }
    }
    
    /** @return Algorithm name for test key pair */
    private static String getTestKeyPairAlgName(String algName) {
        if (algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410) ||
            algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145)) {
            return algName;
        } else {
            return "SHA256withRSA";
        }
    }
    
    private static String getTestKeySpec(String algName) {
        if (algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_ECGOST3410)) {
            return CesecoreConfiguration.getExtraAlgSubAlgName("gost3410", "B");
        } else if (algName.equals(AlgorithmConstants.SIGALG_GOST3411_WITH_DSTU4145)) {
            return CesecoreConfiguration.getExtraAlgSubAlgName("dstu4145", "233");
        } else {
            return "512"; // Assume RSA
        }
    }
}
