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
 
package se.anatom.ejbca.ca.exception;

import se.anatom.ejbca.exception.EjbcaException;


/**
 * Authentication error due to wrong status of user object. To authenticate a user the user must
 * have status new, failed or inprocess.
 *
 * @version $Id: AuthStatusException.java,v 1.4 2004-04-16 07:38:55 anatom Exp $
 */
public class AuthStatusException extends EjbcaException {
    /**
     * Constructor used to create exception with an errormessage. Calls the same constructor in
     * baseclass <code>Exception</code>.
     *
     * @param message Human redable error message, can not be NULL.
     */
    public AuthStatusException(String message) {
        super(message);
    }
}
