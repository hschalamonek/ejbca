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
 
package org.ejbca.core.model.ca.certificateprofiles;

import java.util.ArrayList;

/**
 * HardTokenSignCertificateProfile is a class defining the fixed characteristics 
 * of a hard token sign certificate.
 *
 * @version $Id: HardTokenSignCertificateProfile.java,v 1.2 2006-05-01 14:20:00 anatom Exp $
 */
public class HardTokenSignCertificateProfile extends CertificateProfile{

    // Public Constants

    public static final String CERTIFICATEPROFILENAME =  "HARDTOKEN_SIGN";

    // Public Methods
    /** Creates a certificate with the characteristics of an end user. 
     * General options are set in the superclass's default contructor that is called automatically.
     * You can override the general options by defining them again with different parameters here.
     */
    public HardTokenSignCertificateProfile() {

      setType(TYPE_ENDENTITY);

      setUseKeyUsage(true);
      setKeyUsage(new boolean[9]);
      setKeyUsage(NONREPUDIATION,true);	          
      setKeyUsageCritical(true);

      setUseExtendedKeyUsage(true);
      ArrayList eku = new ArrayList();  
      eku.add(new Integer(EMAILPROTECTION));
      setExtendedKeyUsage(eku);
      setExtendedKeyUsageCritical(false);
      
    }

    // Public Methods.
    public void upgrade(){
      if(LATEST_VERSION != getVersion()){
        // New version of the class, upgrade

        super.upgrade();         
      }
    }


    // Private fields.
}
