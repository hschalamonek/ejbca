package se.anatom.ejbca.ca.publisher;

import java.security.cert.Certificate;
import java.util.Properties;

import se.anatom.ejbca.ca.exception.PublisherConnectionException;
import se.anatom.ejbca.ca.exception.PublisherException;
import se.anatom.ejbca.log.Admin;

/**
 * This is an class used for testing and example purposes.
 * I supposed to illustrat how to implement a custom publisher to EJBCA 3.
 *  
 *
 * @version $Id: DummyCustomPublisher.java,v 1.1 2004-03-07 12:08:50 herrvendil Exp $
 */
public class DummyCustomPublisher implements ICustomPublisher{
    		
    // Public Methods

    /**
     * Creates a new instance of DummyCustomPublisher
     */
    public DummyCustomPublisher() {}

	/**
	 * @see se.anatom.ejbca.ca.publisher.ICustomPublisher#init(java.util.Properties)
	 */
	public void init(Properties properties) {
	  // This method sets up the communication with the publisher	
		
	  System.out.println("Initializing DummyCustomPublisher");		
	}

	/**
	 * @see se.anatom.ejbca.ca.publisher.ICustomPublisher#storeCertificate(se.anatom.ejbca.log.Admin, java.security.cert.Certificate, java.lang.String, java.lang.String, int, int)
	 */
	public boolean storeCertificate(Admin admin, Certificate incert, String username, String cafp, int status, int type) throws PublisherException {
		System.out.println("DummyCustomPublisher, Storing Certificate for user: " + username);	
		return true;
	}

	/**
	 * @see se.anatom.ejbca.ca.publisher.ICustomPublisher#storeCRL(se.anatom.ejbca.log.Admin, byte[], java.lang.String, int)
	 */
	public boolean storeCRL(Admin admin, byte[] incrl, String cafp, int number) throws PublisherException {
		System.out.println("DummyCustomPublisher, Storing CRL");
		return true;
	}

	/**
	 * @see se.anatom.ejbca.ca.publisher.ICustomPublisher#revokeCertificate(se.anatom.ejbca.log.Admin, java.security.cert.Certificate, int)
	 */
	public void revokeCertificate(Admin admin, Certificate cert, int reason) throws PublisherException {
		System.out.println("DummyCustomPublisher, Rekoving Certificate");
		
	}	

	/**
	 * @see se.anatom.ejbca.ca.publisher.ICustomPublisher#testConnection(se.anatom.ejbca.log.Admin)
	 */
	public void testConnection(Admin admin) throws PublisherConnectionException {
		System.out.println("DummyCustomPublisher, Testing connection");			
	}

	
	protected void finalize() throws Throwable {
		System.out.println("DummyCustomPublisher, closing connection");
		// This method closes the communication with the publisher.	
			
		super.finalize(); 
	}
	
}
