
package se.anatom.ejbca.ca.auth;

import java.rmi.*;

import javax.naming.*;
import javax.rmi.*;
import javax.ejb.*;

import se.anatom.ejbca.BaseSessionBean;
import se.anatom.ejbca.ra.UserDataPK;
import se.anatom.ejbca.ra.UserData;
import se.anatom.ejbca.ra.UserDataHome;

/**
 * Authenticates users towards a user database.
 *
 * @version $Id: LocalAuthenticationSessionBean.java,v 1.3 2002-03-07 15:00:36 anatom Exp $
 */
public class LocalAuthenticationSessionBean extends BaseSessionBean implements IAuthenticationSession {

    /** home interface to user entity bean */
    private UserDataHome userHome = null;

    /**
     * Default create for SessionBean without any creation Arguments.
     * @throws CreateException if bean instance can't be created
     */
    public void ejbCreate () throws CreateException {
        debug(">ejbCreate()");
        // Look up the UserData entity bean home interface
        userHome = (UserDataHome)lookup("java:comp/env/ejb/UserData", UserDataHome.class);
        debug("<ejbCreate()");
    }

   /**
    * Implements IAuthenticationSession::authenticateUser.
    * Implements a mechanism that queries a local database directly. Only allows authentication when user status is
    * STATUS_NEW, STATUS_FAILED or STATUS_INPROCESS.
    */
    public UserAuthData authenticateUser(String username, String password) throws RemoteException {
        debug(">authenticateUser("+username+", hiddenpwd)");
        try {
            // Find the user with username username
            UserDataPK pk = new UserDataPK();
            pk.username = username;
            UserData data = userHome.findByPrimaryKey(pk);
            int status = data.getStatus();
            if ( (status == UserData.STATUS_NEW) || (status == UserData.STATUS_FAILED) || (status == UserData.STATUS_INPROCESS) ) {
                info("Trying to authenticate user: username="+data.getUsername()+", dn="+data.getSubjectDN()+", email="+data.getSubjectEmail()+", status="+data.getStatus()+", type="+data.getType());
                if (data.comparePassword(password) == false)
                {
                    error("Wrong password for user "+username);
                    throw new SecurityException("Wrong password for user.");
                }
                info("Authenticated user "+username);
                UserAuthData ret = new UserAuthData(data.getUsername(), data.getSubjectDN(), data.getSubjectEmail(), data.getType());
                debug("<authenticateUser("+username+", hiddenpwd)");
                return ret;
            } else {
                error("User "+username+" has status '"+status+"', NEW, FAILED or INPROCESS required.");
                throw new SecurityException("User "+username+" has status '"+status+"', NEW, FAILED or INPROCESS required.");
            }
        } catch (Exception e) {
            throw new EJBException(e.toString());
        }
    } //authenticateUser

   /**
    * Implements IAuthenticationSession::finishUser.
    * Implements a mechanism that uses a local database directly to set users status to UserData.STATUS_GENERATED.
    */
    public void finishUser(String username, String password) throws RemoteException {
        debug(">finishUser("+username+", hiddenpwd)");
        try {
            // Find the user with username username
            UserDataPK pk = new UserDataPK();
            pk.username = username;
            UserData data = userHome.findByPrimaryKey(pk);
            data.setStatus(UserData.STATUS_GENERATED);
            debug("<finishUser("+username+", hiddenpwd)");
        } catch (Exception e) {
            throw new EJBException(e.toString());
        }
    } //finishUser
}
