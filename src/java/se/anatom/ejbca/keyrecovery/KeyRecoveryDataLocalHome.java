/*
 * Generated by XDoclet - Do not edit!
 */
package se.anatom.ejbca.keyrecovery;

/**
 * Local home interface for KeyRecoveryData.
 */
public interface KeyRecoveryDataLocalHome
   extends javax.ejb.EJBLocalHome
{
   public static final String COMP_NAME="java:comp/env/ejb/KeyRecoveryDataLocal";
   public static final String JNDI_NAME="KeyRecoveryData";

   public se.anatom.ejbca.keyrecovery.KeyRecoveryDataLocal create(java.math.BigInteger certificatesn , java.lang.String issuerdn , java.lang.String username , byte[] keydata)
      throws javax.ejb.CreateException;

   public java.util.Collection findByUsername(java.lang.String username)
      throws javax.ejb.FinderException;

   public java.util.Collection findByUserMark(java.lang.String usermark)
      throws javax.ejb.FinderException;

   public se.anatom.ejbca.keyrecovery.KeyRecoveryDataLocal findByPrimaryKey(se.anatom.ejbca.keyrecovery.KeyRecoveryDataPK pk)
      throws javax.ejb.FinderException;

}
