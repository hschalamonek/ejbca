/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
 
package se.anatom.ejbca.ca.caadmin.extendedcaservices;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.ocsp.BasicOCSPResp;

/**
 * Class used when delevering OCSP service response from a CA.  
 *
 * @version $Id: OCSPCAServiceResponse.java,v 1.5 2004-04-16 07:38:57 anatom Exp $
 */
public class OCSPCAServiceResponse extends ExtendedCAServiceResponse implements Serializable {    
             
    private List ocspcertificatechain = null;
    private BasicOCSPResp basicResp = null;
    
        
    public OCSPCAServiceResponse(BasicOCSPResp basicResp, List ocspsigningcertificatechain) {
        this.basicResp = basicResp;
        this.ocspcertificatechain = ocspsigningcertificatechain;
    }    
           
    public X509Certificate getOCSPSigningCertificate() { return (X509Certificate) this.ocspcertificatechain.get(0); }
	public Collection getOCSPSigningCertificateChain() { 
        if (ocspcertificatechain != null) {
            return this.ocspcertificatechain;
        } else {
            return new ArrayList();
        }
    }
    public BasicOCSPResp getBasicOCSPResp() { return this.basicResp; }
        
}
