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
package org.ejbca.core.ejb.ra;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.control.AccessControlSessionLocal;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.util.CertTools;
import org.cesecore.util.StringTools;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionLocal;
import org.ejbca.core.model.InternalEjbcaResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.authorization.AccessRulesConstants;

/**
 * @version $Id$
 *
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "EndEntityAccessSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class EndEntityAccessSessionBean implements EndEntityAccessSessionLocal, EndEntityAccessSessionRemote {

    private static final Logger log = Logger.getLogger(EndEntityAccessSessionBean.class);
    /** Internal localization of logs and errors */
    private static final InternalEjbcaResources intres = InternalEjbcaResources.getInstance();
    
    @PersistenceContext(unitName = "ejbca")
    private EntityManager entityManager;
    
    @EJB
    private AccessControlSessionLocal authorizationSession;
    @EJB
    private GlobalConfigurationSessionLocal globalConfigurationSession;
    
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public EndEntityInformation findUserBySubjectDN(final AuthenticationToken admin, final String subjectdn) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserBySubjectDN(" + subjectdn + ")");
        }
        // String used in SQL so strip it
        final String dn = CertTools.stringToBCDNString(StringTools.strip(subjectdn));
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with subjectdn: " + dn);
        }
        final UserData data = UserData.findBySubjectDN(entityManager, dn);
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with subjectdn: " + dn);
            }
        }
        final EndEntityInformation returnval = returnUserDataVO(admin, data, null);
        if (log.isTraceEnabled()) {
            log.trace("<findUserBySubjectDN(" + subjectdn + ")");
        }
        return returnval;
    }
    
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public EndEntityInformation findUserBySubjectAndIssuerDN(final AuthenticationToken admin, final String subjectdn, final String issuerdn) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserBySubjectAndIssuerDN(" + subjectdn + ", " + issuerdn + ")");
        }
        // String used in SQL so strip it
        final String dn = CertTools.stringToBCDNString(StringTools.strip(subjectdn));
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with subjectdn: " + dn + ", issuerdn : " + issuerdn);
        }
        final UserData data = UserData.findBySubjectDNAndCAId(entityManager, dn, issuerdn.hashCode());
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with subjectdn: " + dn + ", issuerdn : " + issuerdn);
            }
        }
        final EndEntityInformation returnval = returnUserDataVO(admin, data, null);
        if (log.isTraceEnabled()) {
            log.trace("<findUserBySubjectAndIssuerDN(" + subjectdn + ", " + issuerdn + ")");
        }
        return returnval;
    }

    
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public EndEntityInformation findUser(final AuthenticationToken admin, final String username) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUser(" + username + ")");
        }
        final UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with username='" + username + "'");
            }
        }
        final EndEntityInformation ret = returnUserDataVO(admin, data, username);
        if (log.isTraceEnabled()) {
            log.trace("<findUser(" + username + "): " + (ret == null ? "null" : ret.getDN()));
        }
        return ret;
    }
    

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public List<EndEntityInformation> findUserByEmail(AuthenticationToken admin, String email) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserByEmail(" + email + ")");
        }
        if (log.isDebugEnabled()) {
            log.debug("Looking for user with email: " + email);
        }
        final List<UserData> result = UserData.findBySubjectEmail(entityManager, email);
        if (result.size() == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with Email='" + email + "'");
            }
        }
        final List<EndEntityInformation> returnval = new ArrayList<EndEntityInformation>();
        for (final UserData data : result) {
            if (globalConfigurationSession.getCachedGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
                // Check if administrator is authorized to view user.
                if (!authorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.VIEW_RIGHTS)) {
                    continue;
                }
            }
            if (!authorizedToCA(admin, data.getCaId())) {
                continue;
            }
            returnval.add(data.toUserDataVO());
        }
        if (log.isTraceEnabled()) {
            log.trace("<findUserByEmail(" + email + ")");
        }
        return returnval;
    }


    /** @return the userdata value object if admin is authorized. Does not leak username if auth fails. */
    private EndEntityInformation returnUserDataVO(final AuthenticationToken admin, final UserData data, final String requestedUsername) throws AuthorizationDeniedException {
        if (data != null) {
            if (globalConfigurationSession.getCachedGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
                // Check if administrator is authorized to view user.
                if (!authorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.VIEW_RIGHTS)) {
                        if (requestedUsername == null) {
                        final String msg = intres.getLocalizedMessage("ra.errorauthprofile", Integer.valueOf(data.getEndEntityProfileId()));
                        throw new AuthorizationDeniedException(msg);
                        } else {
                        final String msg = intres.getLocalizedMessage("ra.errorauthprofileexist", Integer.valueOf(data.getEndEntityProfileId()), requestedUsername);
                        throw new AuthorizationDeniedException(msg);
                        }
                }
            }
            if (!authorizedToCA(admin, data.getCaId())) {
                if (requestedUsername == null) {
                    final String msg = intres.getLocalizedMessage("ra.errorauthca", Integer.valueOf(data.getCaId()));
                    throw new AuthorizationDeniedException(msg);
                } else {
                        final String msg = intres.getLocalizedMessage("ra.errorauthcaexist", Integer.valueOf(data.getCaId()), requestedUsername);
                    throw new AuthorizationDeniedException(msg);
                }
            }
            return data.toUserDataVO();
        }
        return null;
    }
    
    private boolean authorizedToEndEntityProfile(AuthenticationToken admin, int profileid, String rights) {
        boolean returnval = false;
        if (profileid == SecConst.EMPTY_ENDENTITYPROFILE
                && (rights.equals(AccessRulesConstants.CREATE_RIGHTS) || rights.equals(AccessRulesConstants.EDIT_RIGHTS))) {
            if (authorizationSession.isAuthorizedNoLogging(admin, "/super_administrator")) {
                returnval = true;
            } else {
                log.info("Admin " + admin.toString() + " was not authorized to resource /super_administrator");
            }
        } else {
            returnval = authorizationSession.isAuthorizedNoLogging(admin, AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + rights)
                    && authorizationSession.isAuthorizedNoLogging(admin, AccessRulesConstants.REGULAR_RAFUNCTIONALITY + rights);
        }
        return returnval;
    }
    
    private boolean authorizedToCA(AuthenticationToken admin, int caid) {
        boolean returnval = false;
        returnval = authorizationSession.isAuthorizedNoLogging(admin, StandardRules.CAACCESS.resource() + caid);
        if (!returnval) {
            log.info("Admin " + admin.toString() + " not authorized to resource " + StandardRules.CAACCESS.resource() + caid);
        }
        return returnval;
    }
}
