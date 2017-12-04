/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.ejb.upgrade;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URL;
import java.security.cert.Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.audit.log.SecurityEventsLoggerSessionLocal;
import org.cesecore.authentication.tokens.AlwaysAllowLocalAuthenticationToken;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authentication.tokens.X509CertificateAuthenticationTokenMetaData;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.cache.AccessTreeUpdateSessionLocal;
import org.cesecore.authorization.control.AuditLogRules;
import org.cesecore.authorization.control.CryptoTokenRules;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.AccessUserAspectData;
import org.cesecore.authorization.user.matchvalues.X500PrincipalAccessMatchValue;
import org.cesecore.certificates.ca.ApprovalRequestType;
import org.cesecore.certificates.ca.CA;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.certificates.ca.X509CA;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceTypes;
import org.cesecore.certificates.certificate.CertificateStoreSessionLocal;
import org.cesecore.certificates.certificate.certextensions.AvailableCustomCertificateExtensionsConfiguration;
import org.cesecore.certificates.certificateprofile.CertificatePolicy;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileData;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionLocal;
import org.cesecore.certificates.certificatetransparency.CTLogInfo;
import org.cesecore.certificates.ocsp.OcspResponseGeneratorSessionLocal;
import org.cesecore.config.AvailableExtendedKeyUsagesConfiguration;
import org.cesecore.config.ConfigurationHolder;
import org.cesecore.config.GlobalOcspConfiguration;
import org.cesecore.config.OcspConfiguration;
import org.cesecore.configuration.GlobalConfigurationData;
import org.cesecore.configuration.GlobalConfigurationSessionLocal;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.keybind.InternalKeyBindingRules;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.CryptoTokenSessionLocal;
import org.cesecore.roles.AccessRulesHelper;
import org.cesecore.roles.AccessRulesMigrator;
import org.cesecore.roles.AdminGroupData;
import org.cesecore.roles.Role;
import org.cesecore.roles.management.RoleDataSessionLocal;
import org.cesecore.roles.management.RoleSessionLocal;
import org.cesecore.roles.member.RoleMember;
import org.cesecore.roles.member.RoleMemberDataSessionLocal;
import org.cesecore.util.CertTools;
import org.cesecore.util.JBossUnmarshaller;
import org.cesecore.util.ui.PropertyValidationException;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.DatabaseConfiguration;
import org.ejbca.config.EjbcaConfiguration;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.config.InternalConfiguration;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.EnterpriseEditionEjbBridgeSessionLocal;
import org.ejbca.core.ejb.approval.ApprovalData;
import org.ejbca.core.ejb.approval.ApprovalProfileExistsException;
import org.ejbca.core.ejb.approval.ApprovalProfileSessionLocal;
import org.ejbca.core.ejb.approval.ApprovalSessionLocal;
import org.ejbca.core.ejb.authentication.cli.CliAuthenticationTokenMetaData;
import org.ejbca.core.ejb.authentication.cli.CliUserAccessMatchValue;
import org.ejbca.core.ejb.authorization.AuthorizationSystemSessionLocal;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionLocal;
import org.ejbca.core.ejb.config.GlobalUpgradeConfiguration;
import org.ejbca.core.ejb.hardtoken.HardTokenData;
import org.ejbca.core.ejb.hardtoken.HardTokenIssuerData;
import org.ejbca.core.ejb.ra.raadmin.AdminPreferencesData;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileData;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSessionLocal;
import org.ejbca.core.model.approval.Approval;
import org.ejbca.core.model.approval.profile.AccumulativeApprovalProfile;
import org.ejbca.core.model.approval.profile.ApprovalPartition;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.CmsCAService;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAService;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.KeyRecoveryCAService;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.KeyRecoveryCAServiceInfo;
import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.upgrade.BasePublisherConverter;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.util.JDBCUtil;
import org.ejbca.util.SqlExecutor;

/**
 * The upgrade session bean is used to upgrade the database between EJBCA
 * releases.
 *
 * @version $Id$
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "UpgradeSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class UpgradeSessionBean implements UpgradeSessionLocal, UpgradeSessionRemote {

    private static final Logger log = Logger.getLogger(UpgradeSessionBean.class);

    private static final AuthenticationToken authenticationToken = new AlwaysAllowLocalAuthenticationToken("Internal upgrade");

    @PersistenceContext(unitName = "ejbca")
    private EntityManager entityManager;

    @Resource
    private SessionContext sessionContext;

    @EJB
    private AccessTreeUpdateSessionLocal accessTreeUpdateSession;
    @EJB
    private ApprovalProfileSessionLocal approvalProfileSession;
    @EJB
    private ApprovalSessionLocal approvalSession;
    @EJB
    private AuthorizationSystemSessionLocal authorizationSystemSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private CertificateProfileSessionLocal certProfileSession;
    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private CryptoTokenSessionLocal cryptoTokenSession;
    @EJB
    private EndEntityProfileSessionLocal endEntityProfileSession;
    @EJB
    private EnterpriseEditionEjbBridgeSessionLocal enterpriseEditionEjbBridgeSession;
    @EJB
    private GlobalConfigurationSessionLocal globalConfigurationSession;
    @EJB
    private OcspResponseGeneratorSessionLocal ocspResponseGeneratorSession;
    @EJB
    private PublisherSessionLocal publisherSession;
    @EJB
    private RoleDataSessionLocal roleDataSession;
    @SuppressWarnings("deprecation")
    @EJB
    private LegacyRoleManagementSessionLocal legacyRoleManagementSession;
    @EJB
    private RoleMemberDataSessionLocal roleMemberDataSession;
    @EJB
    private RoleSessionLocal roleSession;
    @EJB
    private SecurityEventsLoggerSessionLocal securityEventsLogger;
    @EJB
    private UserDataSourceSessionLocal userDataSourceSession;
    @EJB
    private UpgradeStatusSingletonLocal upgradeStatusSingleton;

    private UpgradeSessionLocal upgradeSession;

    @PostConstruct
    public void ejbCreate() {
    	upgradeSession = sessionContext.getBusinessObject(UpgradeSessionLocal.class);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public String getLastUpgradedToVersion() {
        return getGlobalUpgradeConfiguration().getUpgradedToVersion();
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public String getLastPostUpgradedToVersion() {
        return getGlobalUpgradeConfiguration().getPostUpgradedToVersion();
    }

    private void setLastUpgradedToVersion(final String version) {
        final GlobalUpgradeConfiguration guc = getGlobalUpgradeConfiguration();
        // (From EJBCA 6.8.0) set the oldest known installation ad-hoc if it was previously unset.
        if(guc.getUpgradedFromVersion() == null) {
            String oldVersion = guc.getUpgradedToVersion();
            if(oldVersion == null) {
                guc.setUpgradedFromVersion(version);
            } else {
                guc.setUpgradedFromVersion(oldVersion);
            }
        }
        guc.setUpgradedToVersion(version);
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, guc);
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getUpgradedFromVersion() {
        return getGlobalUpgradeConfiguration().getUpgradedFromVersion();
    }

    private void setLastPostUpgradedToVersion(final String version) {
        final GlobalUpgradeConfiguration guc = getGlobalUpgradeConfiguration();
        guc.setPostUpgradedToVersion(version);
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, guc);
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public long getPostUpgradeStarted() {
        return getGlobalUpgradeConfiguration().getPostUpgradeStarted();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public boolean setPostUpgradeStarted(final long startTimeMs) {
        final GlobalUpgradeConfiguration globalUpgradeConfiguration = getGlobalUpgradeConfiguration();
        if (startTimeMs!=0L && globalUpgradeConfiguration.getPostUpgradeStarted()!=0L) {
            return false;
        }
        globalUpgradeConfiguration.setPostUpgradeStarted(startTimeMs);
        setGlobalUpgradeConfiguration(globalUpgradeConfiguration);
        return true;
    }

    private boolean setPostUpgradeStartedInternal(final long startTimeMs) {
        boolean ret = false;
        try {
            ret = upgradeSession.setPostUpgradeStarted(startTimeMs);
            if (!ret) {
                log.debug("Post upgrade has already been started elsewhere and update prevents start on this node.");
            }
        } catch (RuntimeException e) {
            log.debug("Concurrent persistence update prevents upgrade to start on this node.");
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean isEndEntityProfileInCertificateData() {
        return getGlobalUpgradeConfiguration().isEndEntityProfileInCertificateData();
    }

    private void setEndEntityProfileInCertificateData(final boolean value) {
        final GlobalUpgradeConfiguration guc = getGlobalUpgradeConfiguration();
        guc.setEndEntityProfileInCertificateData(value);
        setGlobalUpgradeConfiguration(guc);
    }

    private GlobalUpgradeConfiguration getGlobalUpgradeConfiguration() {
        return (GlobalUpgradeConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalUpgradeConfiguration.CONFIGURATION_ID);
    }
    private void setGlobalUpgradeConfiguration(final GlobalUpgradeConfiguration globalUpgradeConfiguration) {
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, globalUpgradeConfiguration);
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException(e);
        }
    }


    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Override
    public void performPreUpgrade(final boolean isFreshInstallation) {
        try {
            if (isFreshInstallation) {
                // Unlock statedump in new installations
                final GlobalConfiguration globalConfig = (GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
                globalConfig.setStatedumpLockedDown(false);
                globalConfigurationSession.saveConfiguration(authenticationToken, globalConfig);
                setEndEntityProfileInCertificateData(true);
                // Since we know that this is a brand new installation, no upgrade should be needed
                setLastUpgradedToVersion(InternalConfiguration.getAppVersionNumber());
                setLastPostUpgradedToVersion("6.8.0");
            } else {
                // Ensure that we save currently known oldest installation version before any upgrade is invoked
                if(getLastUpgradedToVersion() != null) {
                    setLastUpgradedToVersion(getLastUpgradedToVersion());
                }
            }
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException("AlwaysAllowLocalAuthenticationToken should not have been denied authorization");
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Override
    public boolean performUpgrade() {
        final String dbType = DatabaseConfiguration.getDatabaseName();
        final String currentVersion = InternalConfiguration.getAppVersionNumber();
        String last = getLastUpgradedToVersion();
        if (last==null) {
            // Start auto-detection, since no version info was present
            // This auto-detection was added for EJBCA 6.4.0
            if (!checkColumnExists500()) {
                // The CAId column was removed during post upgrade to EJBCA 5.0
                last = "5.0";
                if (globalConfigurationSession.findByConfigurationId(GlobalOcspConfiguration.OCSP_CONFIGURATION_ID)!=null) {
                    last = "6.2.4";
                }
                setLastUpgradedToVersion(last);
                if (!publisherSession.isOldVaPublisherPresent()) {
                    // For all practical purposes, this version can used as post-upgrade version
                    setLastPostUpgradedToVersion("6.4.0");
                }
            } else {
                // We are on EJBCA 4.0 or 3.11 or even earlier
                log.error("Unable to detect version of database content and perform automatic upgrade from the version you are running. Run 'ant upgrade' manually.");
                return false;
            }
        }
        boolean ret = true;
        if (isLesserThan(last, currentVersion)) {
            if (log.isDebugEnabled()) {
                log.debug("Database content version: " + last + " Current application version: " + currentVersion + " -> Starting upgrade.");
            }
            ret = upgradeSession.upgrade(dbType, last, false);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Database content version: " + last + " Current application version: " + currentVersion + " -> Upgrade is not needed.");
            }
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Asynchronous
    @Override
    public Future<Boolean> startPostUpgrade() {
        log.trace(">startPostUpgrade");
        boolean ret = false;
        if (setPostUpgradeStartedInternal(System.currentTimeMillis())) {
            try {
                upgradeStatusSingleton.logAppenderAttach(log);
                if (upgradeStatusSingleton.setPostUpgradeInProgressIfDifferent(true)) {
                    try {
                        final String dbType = DatabaseConfiguration.getDatabaseName();
                        final String currentVersion = InternalConfiguration.getAppVersionNumber();
                        final String last = getLastPostUpgradedToVersion();
                        if (isLesserThan(last, currentVersion)) {
                            if (log.isDebugEnabled()) {
                                log.debug("Database content version: " + last + " Current application version: " + currentVersion + " -> Starting post-upgrade.");
                            }
                            ret = upgradeSession.upgrade(dbType, last, true);
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Database content version: " + last + " Current application version: " + currentVersion + " -> Post-upgrade is not needed.");
                            }
                            ret = true;
                        }
                    } finally {
                        upgradeStatusSingleton.resetPostUpgradeInProgress();
                    }
                } else {
                    log.info("Preventing start of post-upgrade background tasks since it has already been started on this cluster node.");
                }
            } catch (RuntimeException e) {
                // Since this is invoked asynchronously the calling client might no longer be around to receive the "result"
                log.error("Unexpected error from post-upgrade: " + e.getMessage(), e);
            } finally {
                setPostUpgradeStartedInternal(0L);
                upgradeStatusSingleton.logAppenderDetach(log);
            }
        } else {
            log.info("Preventing start of post-upgrade background tasks since it has already been started by a cluster node.");
        }
        log.trace("<startPostUpgrade");
        return new AsyncResult<Boolean>(ret);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Override
    public boolean upgrade(String dbtype, String oldVersion, boolean isPost) {
        try {
            log.debug("Upgrading from version=" + oldVersion);
            if (isPost) {
                // TODO: We might want to check that upgrade has run ok before allowing this.
                // ...on the other hand... we wont allow it via the GUI so it might be good to be able to force upgrade retries
                return postUpgrade(oldVersion, dbtype);
            } else {
                return upgrade(dbtype, oldVersion);
            }
        } catch (RuntimeException e) {
        	// We want to log in server.log so we can analyze the error
            log.error("Error thrown during upgrade: ", e);
            throw e;
        } finally {
            log.trace("<upgrade()");
        }
    }

    private boolean upgrade(String dbtype, String oldVersion) {
    	log.debug(">upgrade from version: "+oldVersion+", with dbtype: "+dbtype);
        if (isLesserThan(oldVersion, "5.0.12")) {
            log.error(
                    "Upgrading from EJBCA prior to version 5.0.12 is forbidden. You must upgrade to the intermediate release EJBCA 6.3.2.6 first. Read the EJBCA Upgrade Guide for more information.");
            return false;
        }
        // Upgrade between EJBCA 3.11.x and EJBCA 4.0.x to 5.0.x
        if (isLesserThan(oldVersion, "5")) {
        	if (!upgradeSession.migrateDatabase500(dbtype)) {
        		return false;
        	}
            setLastUpgradedToVersion("5.0");
        }
        if (isLesserThan(oldVersion, "6.0")) {
            // Check and upgrade if this is the first time we start an instance that was previously an stand-alone VA
            ocspResponseGeneratorSession.adhocUpgradeFromPre60(null);
            setLastUpgradedToVersion("6.0");
        }
        if (isLesserThan(oldVersion, "6.2.4")) {
            try {
                upgradeSession.migrateDatabase624();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.2.4");
        }
        if (isLesserThan(oldVersion, "6.3.1")) {
            // Upgrade the old Validation Authority Publisher in Community Edition (leave it be in Enterprise for the sake of 100% uptime)
            if (!enterpriseEditionEjbBridgeSession.isRunningEnterprise()) {
                publisherSession.adhocUpgradeTo6_3_1_1();
            }
            setLastUpgradedToVersion("6.3.1");
        }
        if (isLesserThan(oldVersion, "6.4")) {
            try {
                upgradeSession.migrateDatabase640();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.4");
        }
        if (isLesserThan(oldVersion, "6.4.2")) {
            try {
                upgradeSession.migrateDatabase642();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.4.2");
        }
        if (isLesserThan(oldVersion, "6.5.1")) {
            try {
                upgradeSession.migrateDatabase651();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.5.1");
        }
        if (isLesserThan(oldVersion, "6.6.0")) {
            try {
                upgradeSession.migrateDatabase660();
            } catch (UpgradeFailedException e) {
                return false;
            }
            if (!isEndEntityProfileInCertificateData()) {
                // Persist mark that this upgrade has not been performed so we can do it in later release (unless the value was set due to this being a fresh installation)
                setEndEntityProfileInCertificateData(false);
            }
            setLastUpgradedToVersion("6.6.0");
        }
        if (isLesserThan(oldVersion, "6.8.0")) {
            try {
                upgradeSession.migrateDatabase680();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.8.0");
        }
        if (isLesserThan(oldVersion, "6.10.1")) {
            try {
                upgradeSession.migrateDatabase6101();
            } catch (UpgradeFailedException e) {
                return false;
            }
            setLastUpgradedToVersion("6.10.1");
        }
        setLastUpgradedToVersion(InternalConfiguration.getAppVersionNumber());
        return true;
    }

    private boolean postUpgrade(String oldVersion, String dbtype) {
        log.debug(">post-upgrade from version: "+oldVersion);
        if (isLesserThan(oldVersion, "5.0.12")) {
            log.error(
                    "Post-upgrade from EJBCA prior to version 5.0.12 is forbidden. You must upgrade to the intermediate release EJBCA 6.3.2.6 first. Read the EJBCA Upgrade Guide for more information.");
            return false;
        }
        // Upgrade database change between EJBCA 3.11.x and EJBCA 4.0.x if needed
        if (isLesserThan(oldVersion,"4")) {
            if (!postMigrateDatabase4_0_0()) {
                return false;
            }
            setLastPostUpgradedToVersion("4.0");
        }
        // Upgrade database change between EJBCA 4.0.x and EJBCA 5.0.x if needed, and previous post-upgrade succeeded
        if (isLesserThan(oldVersion, "5")) {
            if (!postMigrateDatabase500(dbtype)) {
                return false;
            }
            setLastPostUpgradedToVersion("5.0");
        }
        if (isLesserThan(oldVersion, "6.3.2")) {
            if (!postMigrateDatabase632()) {
                return false;
            }
            setLastPostUpgradedToVersion("6.3.2");
        }
        if (isLesserThan(oldVersion, "6.8.0")) {
            if (!postMigrateDatabase680()) {
                return false;
            }
            setLastPostUpgradedToVersion("6.8.0");
        }
        if (isLesserThan(oldVersion, "6.10.1")) {
            if (!postMigrateDatabase6101()) {
                return false;
            }
            setLastPostUpgradedToVersion("6.10.1");
        }
        // NOTE: If you add additional post upgrade tasks here, also modify isPostUpgradeNeeded() and performPreUpgrade()
        //setLastPostUpgradedToVersion(InternalConfiguration.getAppVersionNumber());
        return true;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    @Override
    public boolean isPostUpgradeNeeded() {
        return isLesserThan(getLastPostUpgradedToVersion(), "6.10.1");
    }

    /**
     * Called from other migrate methods, don't call this directly, call from an
     * interface-method
     *
     */
    private boolean migrateDatabase(String resource) {
        // Fetch the resource file with SQL to modify the database tables
        InputStream in = this.getClass().getResourceAsStream(resource);
        if (in == null) {
            log.error("Can not read resource for database '" + resource + "', this database probably does not need table definition changes.");
            // no error
            return true;
        }
        // Migrate database tables to new columns etc
        Connection con = null;
        log.info("Start migration of database.");
        try {
            InputStreamReader inreader = new InputStreamReader(in);
            con = JDBCUtil.getDBConnection();
            SqlExecutor sqlex = new SqlExecutor(con, false);
            sqlex.runCommands(inreader);
        } catch (SQLException e) {
            log.error("SQL error during database migration: ", e);
            return false;
        } catch (IOException e) {
            log.error("IO error during database migration: ", e);
            return false;
        } finally {
            JDBCUtil.close(con);
        }
        log.info("Finished migration of database.");
        return true;
    }

    /**
     * (ECA-200:) In EJB 2.1 JBoss CMP used it's own serialization method for all Object/BLOB fields.
     *
     * This affects the following entity fields:
     * - CertificateProfileData.data
     * - HardTokenData.data
     * - HardTokenIssuerData.data
     * - LogConfigurationData.logConfiguration
     * - AdminPreferencesData.data
     * - EndEntityProfileData.data
     * - GlobalConfigurationData.data
     *
     * NOTE: You only need to run this if you upgrade a JBoss installation.
     */
    private boolean postMigrateDatabase4_0_0() {
    	log.error("(this is not an error) Starting post upgrade from EJBCA 3.11.x to EJBCA 4.0.x");
    	boolean ret = true;
    	upgradeSession.postMigrateDatabase400SmallTables();	// Migrate small tables in a new transaction
    	log.info(" Processing HardTokenData entities.");
    	log.info(" - Building a list of entities.");
    	final List<String> tokenSNs = HardTokenData.findAllTokenSN(entityManager);
    	int position = 0;
    	final int chunkSize = 1000;
    	while (position < tokenSNs.size()) {
        	log.info(" - Processing entity " + position + " to " + Math.min(position+chunkSize-1, tokenSNs.size()-1) + ".");
        	// Migrate HardTokenData table in chunks, each running in a new transaction
    		upgradeSession.postMigrateDatabase400HardTokenData(getSubSet(tokenSNs, position, chunkSize));
    		position += chunkSize;
    	}
    	log.error("(this is not an error) Finished post upgrade from EJBCA 3.11.x to EJBCA 4.0.x with result: "+ret);
        return ret;
    }

    /** @return a subset of the source list with index as its first item and index+count-1 as its last. */
    private <T> List<T> getSubSet(final List<T> source, final int index, final int count) {
    	List<T> ret = new ArrayList<T>(count);
    	for (int i=0; i<count; i++) {
            if (source.size() > (index + i)) {
                ret.add(source.get(index + i));

            }
    	}
    	return ret;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void postMigrateDatabase400SmallTables() {
    	// LogConfiguration removed for EJBCA 5.0, so no upgrade of that needed
    	log.info(" Processing CertificateProfileData entities.");
    	final List<CertificateProfileData> cpds = CertificateProfileData.findAll(entityManager);
    	for (CertificateProfileData cpd : cpds) {
    		// When the wrong class is given it can either return null, or throw an exception
    		HashMap h = getDataUnsafe(cpd.getDataUnsafe());
    		cpd.setDataUnsafe(h);
    	}
    	log.info(" Processing HardTokenIssuerData entities.");
    	final List<HardTokenIssuerData> htids = HardTokenIssuerData.findAll(entityManager);
    	for (HardTokenIssuerData htid : htids) {
    		HashMap h = getDataUnsafe(htid.getDataUnsafe());
    		htid.setDataUnsafe(h);
    	}
    	log.info(" Processing AdminPreferencesData entities.");
    	final List<AdminPreferencesData> apds = AdminPreferencesData.findAll(entityManager);
    	for (AdminPreferencesData apd : apds) {
    		HashMap h = getDataUnsafe(apd.getDataUnsafe());
    		apd.setDataUnsafe(h);
    	}
    	log.info(" Processing EndEntityProfileData entities.");
    	final List<EndEntityProfileData> eepds = EndEntityProfileData.findAll(entityManager);
    	for (EndEntityProfileData eepd : eepds) {
    		HashMap h = getDataUnsafe(eepd.getDataUnsafe());
    		eepd.setDataUnsafe(h);
    	}
    	log.info(" Processing GlobalConfigurationData entities.");
    	GlobalConfigurationData gcd = globalConfigurationSession.findByConfigurationId(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
		HashMap h = getDataUnsafe(gcd.getDataUnsafe());
    	gcd.setObjectUnsafe(h);
    }

	/**
	 * @param cpd
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
    private HashMap getDataUnsafe(Serializable s) {
		HashMap h = null;
		try {
			h = JBossUnmarshaller.extractObject(LinkedHashMap.class, s);
			if (h == null) {
				h = new LinkedHashMap(JBossUnmarshaller.extractObject(HashMap.class, s));
			}
		} catch (ClassCastException e) {
			h = new LinkedHashMap(JBossUnmarshaller.extractObject(HashMap.class, s));
		}
		return h;
	}

    @SuppressWarnings("rawtypes")
    @Override
    public void postMigrateDatabase400HardTokenData(List<String> subSet) {
    	for (String tokenSN : subSet) {
    		HardTokenData htd = HardTokenData.findByTokenSN(entityManager, tokenSN);
    		if (htd != null) {
        		HashMap h = getDataUnsafe(htd);
        		htd.setDataUnsafe(h);
    		} else {
    	    	log.warn("Hard token was removed during processing. Ignoring token with serial number '" + tokenSN + "'.");
    		}
    	}
    }

    /**
     * In EJBCA 5.0 we have introduced a new authorization rule system.
     * The old "/super_administrator" rule is replaced by a rule to access "/" (StandardRules.ROLE_ROOT.resource()) with recursive=true.
     * therefore we must insert a new access rule in the database in all roles that have super_administrator access.
     *
     * We have also added a column to the table AdminEntityData: tokenType
     *
     * @param dbtype A string representation of the actual database.
     *
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)   // Until our roles API can handle transactions properly
    @Override
    public boolean migrateDatabase500(String dbtype) {
    	log.error("(this is not an error) Starting upgrade from ejbca 4.0.x to ejbca 5.0.x");
    	boolean ret = true;

    	AuthenticationToken admin = new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("UpgradeSessionBean.migrateDatabase500"));

    	//Upgrade database
    	migrateDatabase("/400_500/400_500-upgrade-"+dbtype+".sql");

    	final AvailableCustomCertificateExtensionsConfiguration cceConfig = (AvailableCustomCertificateExtensionsConfiguration)
    	        globalConfigurationSession.getCachedConfiguration(AvailableCustomCertificateExtensionsConfiguration.CONFIGURATION_ID );
    	// fix CAs that don't have classpath for extended CA services
    	Collection<Integer> caids = caSession.getAllCaIds();
    	for (Integer caid : caids) {
    		try {
				CA ca = caSession.getCAForEdit(admin, caid);
				if (ca.getCAType() == CAInfo.CATYPE_X509) {
					Collection<Integer> extendedServiceTypes = ca.getExternalCAServiceTypes();
					for (Integer type : extendedServiceTypes) {
						ExtendedCAServiceInfo info = ca.getExtendedCAServiceInfo(type);
						if (info != null) {
							@SuppressWarnings("rawtypes")
                            HashMap data = ca.getExtendedCAServiceData(type);
							switch (type) {
							case ExtendedCAServiceTypes.TYPE_CMSEXTENDEDSERVICE:
								data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, CmsCAService.class.getName());
								ca.setExtendedCAServiceData(type, data);
								log.info("Updating extended CA service of type "+type+" with implementation class "+CmsCAService.class.getName());
								break;
							case ExtendedCAServiceTypes.TYPE_HARDTOKENENCEXTENDEDSERVICE:
								data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, HardTokenEncryptCAService.class.getName());
								ca.setExtendedCAServiceData(type, data);
								log.info("Updating extended CA service of type "+type+" with implementation class "+HardTokenEncryptCAService.class.getName());
								break;
							case ExtendedCAServiceTypes.TYPE_KEYRECOVERYEXTENDEDSERVICE:
								data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, KeyRecoveryCAService.class.getName());
								ca.setExtendedCAServiceData(type, data);
								log.info("Updating extended CA service of type "+type+" with implementation class "+KeyRecoveryCAService.class.getName());
								break;
							default:
                                log.info("Found an extended CA service of type "+type+" that we can not upgrade");
								break;
							}
						} else {
							// If we can't get info for the HardTokenEncrypt or KeyRecovery service it means they don't exist
							// as such in the database, but was hardcoded before. We need to create them from scratch
							switch (type) {
							case ExtendedCAServiceTypes.TYPE_HARDTOKENENCEXTENDEDSERVICE:
								HardTokenEncryptCAServiceInfo htinfo = new HardTokenEncryptCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE);
								HardTokenEncryptCAService htservice = new HardTokenEncryptCAService(htinfo);
								log.info("Creating extended CA service of type "+type+" with implementation class "+HardTokenEncryptCAService.class.getName());
								ca.setExtendedCAService(htservice);
								break;
							case ExtendedCAServiceTypes.TYPE_KEYRECOVERYEXTENDEDSERVICE:
								KeyRecoveryCAServiceInfo krinfo = new KeyRecoveryCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE);
								KeyRecoveryCAService krservice = new KeyRecoveryCAService(krinfo);
								log.info("Creating extended CA service of type "+type+" with implementation class "+KeyRecoveryCAService.class.getName());
								ca.setExtendedCAService(krservice);
								break;
							default:
                                log.info("Found an extended CA service of type "+type+" that we can not create");
								break;
							}
						}
					}
					// If key recovery and hard token encrypt service does not exist, we have to create them
					CAInfo cainfo = ca.getCAInfo();
					Collection<ExtendedCAServiceInfo> extendedcaserviceinfos = new ArrayList<>();
					if (!extendedServiceTypes.contains(ExtendedCAServiceTypes.TYPE_HARDTOKENENCEXTENDEDSERVICE)) {
						log.info("Adding new extended CA service of type "+ExtendedCAServiceTypes.TYPE_HARDTOKENENCEXTENDEDSERVICE+" with implementation class "+HardTokenEncryptCAService.class.getName());
						extendedcaserviceinfos.add(new HardTokenEncryptCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
					}
					if (!extendedServiceTypes.contains(ExtendedCAServiceTypes.TYPE_KEYRECOVERYEXTENDEDSERVICE)) {
						log.info("Adding new extended CA service of type "+ExtendedCAServiceTypes.TYPE_KEYRECOVERYEXTENDEDSERVICE+" with implementation class "+KeyRecoveryCAService.class.getName());
						extendedcaserviceinfos.add(new KeyRecoveryCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
					}
					if (!extendedcaserviceinfos.isEmpty()) {
						cainfo.setExtendedCAServiceInfos(extendedcaserviceinfos);
						final CryptoToken cryptoToken = cryptoTokenSession.getCryptoToken(ca.getCAToken().getCryptoTokenId());
						ca.updateCA(cryptoToken, cainfo, cceConfig);
					}
					// Finally store the upgraded CA
					caSession.editCA(admin, ca, true);
				}
			} catch (CADoesntExistsException e) {
				log.error("CA does not exist during upgrade: "+caid, e);
			} catch (AuthorizationDeniedException e) {
				log.error("Authorization denied to CA during upgrade: "+caid, e);
			} catch (InvalidAlgorithmException e) {
                log.error("Illegal Crypto Token algortihm during upgrade. CA Id: "+caid, e);
            }
    	}
    	/*
    	 *  Upgrade super_administrator access rules to be a /* rule, so super_administrators can still do everything.
    	 *
    	 * Also, set token types to the standard X500 principal if otherwise null. Since token types is a new concept,
         * all existing aspects/admin entities must be of this type
    	 */
    	legacyRoleManagementSession.setTokenTypeWhenNull(admin);
        //The old "/super_administrator" rule is replaced by a rule to access "/" (StandardRules.ROLE_ROOT.resource()) with recursive=true.
        // therefore we must insert a new access rule in the database in all roles that have super_administrator access.
        // Note from EJBCA 6.8.0 rewrite: Since this will be normalized and minimized in a later upgrade adding this rule is sufficient
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList("/super_administrator"), Arrays.asList(StandardRules.ROLE_ROOT.resource()), true);

    	accessTreeUpdateSession.signalForAccessTreeUpdate();
    	log.error("(this is not an error) Finished upgrade from ejbca 4.0.x to ejbca 5.0.x with result: "+ret);
        return ret;
    }

    /**
     * Upgrade access rules such that every role that already has access to /system_functionality/edit_systemconfiguration
     * will also have access to the new access rule /system_functionality/edit_available_extended_key_usages
     *
     * @return true if the upgrade was successful and false otherwise
     */
    @SuppressWarnings("deprecation")
    private boolean addEKUAndCustomCertExtensionsAccessRulestoRoles() {
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
        		Arrays.asList(StandardRules.SYSTEMCONFIGURATION_EDIT.resource()),
                Arrays.asList(StandardRules.EKUCONFIGURATION_EDIT.resource(), StandardRules.CUSTOMCERTEXTENSIONCONFIGURATION_EDIT.resource()), false);
        accessTreeUpdateSession.signalForAccessTreeUpdate();
        return true;
    }

    private void importExtendedKeyUsagesFromFile() {
        final URL url = ConfigurationHolder.class.getResource("/conf/extendedkeyusage.properties");
        AvailableExtendedKeyUsagesConfiguration ekuConfig;
        if (url == null) {
            // Create using the default template of the current version if no such file exists
            ekuConfig = (AvailableExtendedKeyUsagesConfiguration)
                    globalConfigurationSession.getCachedConfiguration(AvailableExtendedKeyUsagesConfiguration.CONFIGURATION_ID);
        } else {
            ekuConfig = new AvailableExtendedKeyUsagesConfiguration(false);
            final Configuration conf = ConfigurationHolder.instance();
            final String ekuname = "extendedkeyusage.name.";
            final String ekuoid = "extendedkeyusage.oid.";
            int j=0;
            for (int i = 0; i < 255; i++) {
                final String oid = conf.getString(ekuoid+i);
                if (oid != null) {
                    String name = conf.getString(ekuname+i);
                    if (name != null) {
                        // A null value in the properties file means that we should not use this value, so set it to null for real
                        if (!name.equalsIgnoreCase("null")) {
                            // Set the untranslated name (since the translation is actually only available in the Admin GUI)
                            ekuConfig.addExtKeyUsage(oid, name);
                            j++;
                        }
                    } else {
                        log.error("Found extended key usage oid "+oid+", but no name defined. Not adding to list of extended key usages.");
                    }
                }
                // No eku with a certain number == continue trying next, we will try 0-255.
            }
            if(log.isDebugEnabled()) {
                log.debug("Read " + j + " extended key usages from the configurations file");
            }
        }
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, ekuConfig);
        } catch (AuthorizationDeniedException e) {
            log.error("Received an AuthorizationDeniedException even though AlwaysAllowLocalAuthenticationToken is used. " + e.getLocalizedMessage());
        }
    }

    /**
     * This method adds read-only rules that were created for the new read-only admin in https://jira.primekey.se/browse/ECA-4344. It makes sure that any roles which previously
     * had access to the affected resources retain read rights (in case those roles should be restricted as a result of this ticket).
     *
     * All access has been made more granular, so performing this step post-upgrade is safe.
     *
     *
     * The exact changes performed are documented in the UPGRADE document.
     * @throws UpgradeFailedException if upgrade fails.
     */
    @SuppressWarnings("deprecation")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    private void addReadOnlyRules640() throws UpgradeFailedException {
        // Roles with access to /ca_functionality/basic_functions/activate_ca or just /ca_functionality/ (+recursive)
        // should be given access to /ca_functionality/view_ca
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.CAFUNCTIONALITY.resource(),
                Arrays.asList(AccessRulesConstants.REGULAR_ACTIVATECA), Arrays.asList(StandardRules.CAVIEW.resource()), false);
        // Roles with access to /ca_functionality/edit_certificate_profiles should be given access to /ca_functionality/view_certificate_profiles
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.CAFUNCTIONALITY.resource(),
                Arrays.asList(StandardRules.CERTIFICATEPROFILEEDIT.resource()), Arrays.asList(StandardRules.CERTIFICATEPROFILEVIEW.resource()), false);
        // Roles with access to /ca_functionality/edit_publisher should be given /ca_functionality/view_publisher
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.CAFUNCTIONALITY.resource(),
                Arrays.asList(AccessRulesConstants.REGULAR_EDITPUBLISHER), Arrays.asList(AccessRulesConstants.REGULAR_VIEWPUBLISHER), false);
        // Roles with access to /ra_functionality/edit_end_entity_profiles should be given /ra_functionality/view_end_entity_profiles
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, AccessRulesConstants.REGULAR_RAFUNCTIONALITY,
                Arrays.asList(AccessRulesConstants.REGULAR_EDITENDENTITYPROFILES), Arrays.asList(AccessRulesConstants.REGULAR_VIEWENDENTITYPROFILES), false);
        // Roles with access to "/" (non-recursive) should be given /services/edit, /services/view and /peer/view (+recursive)
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.ROLE_ROOT.resource()), Arrays.asList(AccessRulesConstants.SERVICES_EDIT, AccessRulesConstants.SERVICES_VIEW), false);
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.ROLE_ROOT.resource()), Arrays.asList(AccessRulesConstants.REGULAR_PEERCONNECTOR_VIEW), true);
        // Roles with access to /internalkeybinding should be given /internalkeybinding/view (+recursive)
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(InternalKeyBindingRules.BASE.resource()), Arrays.asList(InternalKeyBindingRules.VIEW.resource()), true);
    }

    /**
     * Adds the access rules defined in https://jira.primekey.se/browse/ECA-4463
     *
     * These are:   View rules for system configuration, EKU config and CCE config
     *
     * Any roles which matched the previous auditor role, or which had edit access to the above will be given view access.
     * @throws UpgradeFailedException
     *
     */
    @SuppressWarnings("deprecation")
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    private void addReadOnlyRules642() throws UpgradeFailedException {
        // If role is the old auditor from 6.4.0, grant new view rights
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(), Arrays.asList(
                AccessRulesConstants.ROLE_ADMINISTRATOR,
                AccessRulesConstants.REGULAR_VIEWCERTIFICATE,
                AuditLogRules.VIEW.resource(),
                InternalKeyBindingRules.VIEW.resource(),
                StandardRules.CAVIEW.resource(),
                StandardRules.CERTIFICATEPROFILEVIEW.resource(),
                StandardRules.APPROVALPROFILEVIEW.resource(),
                CryptoTokenRules.VIEW.resource(),
                AccessRulesConstants.REGULAR_VIEWPUBLISHER,
                AccessRulesConstants.SERVICES_VIEW,
                AccessRulesConstants.REGULAR_VIEWENDENTITYPROFILES,
                AccessRulesConstants.REGULAR_PEERCONNECTOR_VIEW,
                StandardRules.SYSTEMCONFIGURATION_VIEW.resource(),
                StandardRules.EKUCONFIGURATION_VIEW.resource(),
                StandardRules.CUSTOMCERTEXTENSIONCONFIGURATION_VIEW.resource(),
                StandardRules.VIEWROLES.resource(),
                AccessRulesConstants.REGULAR_VIEWENDENTITY
                ), Arrays.asList(
                        StandardRules.SYSTEMCONFIGURATION_VIEW.resource(),
                        StandardRules.EKUCONFIGURATION_VIEW.resource(),
                        StandardRules.CUSTOMCERTEXTENSIONCONFIGURATION_VIEW.resource(),
                        StandardRules.VIEWROLES.resource(),
                        AccessRulesConstants.REGULAR_VIEWENDENTITY
                        ), false);
        // Other cases where we should grant additional access.
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.SYSTEMCONFIGURATION_EDIT.resource()), Arrays.asList(StandardRules.SYSTEMCONFIGURATION_VIEW.resource()), false);
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.EKUCONFIGURATION_EDIT.resource()), Arrays.asList(StandardRules.EKUCONFIGURATION_VIEW.resource()), false);
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.CUSTOMCERTEXTENSIONCONFIGURATION_EDIT.resource()), Arrays.asList(StandardRules.CUSTOMCERTEXTENSIONCONFIGURATION_VIEW.resource()), false);
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.ROLE_ROOT.resource(),
                Arrays.asList(StandardRules.EDITROLES.resource()), Arrays.asList(StandardRules.VIEWROLES.resource()), false);
    }

    /**
     * EJBCA 6.3.1.1 moves the VA Publisher from Community to Enterprise, changing its baseclass in the process for Enterprise users.
     * This method will fail gracefully if user is not running Enterprise. It will also upgrade any placeholder publishers from 6.3.1.1 Community
     * if so required.
     *
     * @return true if the upgrade was successful
     */
    private boolean postMigrateDatabase632() {
        if(!enterpriseEditionEjbBridgeSession.isRunningEnterprise()) {
            log.error("Upgrade procedure to 6.3.2 can only be run on EJBCA Enterprise.");
            return true; // Fail gracefully and pretend it was ok.
        }
        log.error("(this is not an error) Starting post upgrade to 6.3.2");
        //Find all publishers, make copies of them using the new publisher class.
        Map<Integer, BasePublisher> allPublishers = publisherSession.getAllPublishers();
        Map<Integer, String> publisherNames = publisherSession.getPublisherIdToNameMap();
        BasePublisherConverter publisherFactory;
        try {
            publisherFactory = (BasePublisherConverter) Class.forName("org.ejbca.va.publisher.EnterpriseValidationAuthorityPublisherFactoryImpl").newInstance();
        } catch (InstantiationException e) {
            //Shouldn't happen since we've already checked that we're running Enterprise
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            //Shouldn't happen since we've already checked that we're running Enterprise
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            //Shouldn't happen since we've already checked that we're running Enterprise
            throw new IllegalStateException(e);
        }
        AuthenticationToken admin = new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("UpgradeSessionBean.postMigrateDatabase631"));

        for(Integer publisherId : allPublishers.keySet()) {
            BasePublisher newPublisher = publisherFactory.createPublisher(allPublishers.get(publisherId));
            if (newPublisher != null) {
                try {
                    String publisherName = publisherNames.get(publisherId);
                    log.info("Upgrading publisher: " + publisherName);
                    publisherSession.changePublisher(admin, publisherName, newPublisher);
                } catch (AuthorizationDeniedException e) {
                    throw new IllegalStateException("Always allow token was not given access to publishers.", e);
                }
            }
        }
        return true;
    }

    /**
     * EJBCA 6.2.4 introduced default responder configuration in the database.
     *
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @SuppressWarnings("deprecation")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase624() throws UpgradeFailedException {
        // Check if there the default responder has been set. If not, try setting it using the old value.
        GlobalOcspConfiguration globalConfiguration = (GlobalOcspConfiguration) globalConfigurationSession
                .getCachedConfiguration(GlobalOcspConfiguration.OCSP_CONFIGURATION_ID);
        if (StringUtils.isEmpty(globalConfiguration.getOcspDefaultResponderReference())) {
            globalConfiguration.setOcspDefaultResponderReference(OcspConfiguration.getDefaultResponderId());
            try {
                globalConfigurationSession.saveConfiguration(authenticationToken, globalConfiguration);
            } catch (AuthorizationDeniedException e) {
                throw new UpgradeFailedException(e);
            }
            globalConfigurationSession.flushConfigurationCache(GlobalOcspConfiguration.OCSP_CONFIGURATION_ID);
        }
        log.error("(This is not an error) Completed upgrade procedure to 6.2.4");
    }

    /**
     * EJBCA 6.4.0 introduces new sun rules to System Configuration in regards to Custom OIDs and EKUs.
     *
     * Access rules have also been added for read only rights to parts of the GUI.
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase640() throws UpgradeFailedException {
        //First add access rules for handling custom OIDs to any roles which previous had access to system configuration
        // Add the new access rule /system_functionality/edit_available_extended_key_usages to every role that already has the access rule /system_functionality/edit_systemconfiguration
        addEKUAndCustomCertExtensionsAccessRulestoRoles();
        importExtendedKeyUsagesFromFile();
        // Next add access rules for the new audit role template, allowing easy restriction of resources where needed.
        addReadOnlyRules640();
        log.error("(This is not an error) Completed upgrade procedure to 6.4.0");
    }

    /**
     * EJBCA 6.4.2:
     *
     * 1.   Adds view rules to System Configuration, EKU Configuration and Certificate Extension Configuration. Any roles with edit rights to those pages, or which match the Auditor role
     *      from 6.4.0 will be automatically upgraded.
     * 2.   Adds view rules to Roles. Any roles with edit rights roles, or which match the Auditor role from 6.4.0 will be automatically upgraded.
     *
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase642() throws UpgradeFailedException {
        addReadOnlyRules642();
        log.error("(This is not an error) Completed upgrade procedure to 6.4.2");
    }

    /**
     * EJBCA 6.5.1:
     *
     * This upgrade only affects CMP aliases:
     * 1.   End entity profiles will be referred to by ID instead of by name. In consideration of 100% uptime requirements, the value
     *      ra.endentityprofile is replaced by ra.endentityprofileid, allowing legacy configurations to keep using the old value.
     *
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase651() throws UpgradeFailedException {
        CmpConfiguration cmpConfiguration = (CmpConfiguration) globalConfigurationSession.getCachedConfiguration(CmpConfiguration.CMP_CONFIGURATION_ID);
        for(final String cmpAlias : cmpConfiguration.getAliasList()) {
            // Avoid aliases that may already have been upgraded
            if(StringUtils.isEmpty(cmpConfiguration.getRAEEProfile(cmpAlias))) {
                @SuppressWarnings("deprecation")
                String endEntityProfileName = cmpConfiguration.getValue(cmpAlias + "." + CmpConfiguration.CONFIG_RA_ENDENTITYPROFILE, cmpAlias);
                if (!StringUtils.isEmpty(endEntityProfileName)) {
                    try {
                        cmpConfiguration.setRAEEProfile(cmpAlias,
                                Integer.toString(endEntityProfileSession.getEndEntityProfileId(endEntityProfileName)));
                    } catch (EndEntityProfileNotFoundException e) {
                        //Fail gracefully if a CMP alias already is in an error state
                        log.error("CMP alias " + cmpAlias + " could not be upgraded. It refers by name to End Entity Profile " + endEntityProfileName
                                + ", which does not appear to exist. Value has instead been set to 1 (EMPTY). Please review this profile after upgrade.");
                        cmpConfiguration.setRAEEProfile(cmpAlias, CmpConfiguration.DEFAULT_RA_EEPROFILE);
                    }
                } else {
                    //Could be a client alias, we still need to set a default value though
                    cmpConfiguration.setRAEEProfile(cmpAlias, CmpConfiguration.DEFAULT_RA_EEPROFILE);
                }
            }
        }
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, cmpConfiguration);
        } catch (AuthorizationDeniedException e) {
            log.error("Always allow token was denied authoriation to global configuration table.", e);
        }
        log.error("(This is not an error) Completed upgrade procedure to 6.5.1");
    }

    /**
     * EJBCA 6.6.0:
     *
     * 1.   Adds new access rules for approval profiles
     * 2.   If CA or certificate profiles require Approvals, create a new Approval Profile matching those settings and convert to using that
     *
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @SuppressWarnings("deprecation")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase660() throws UpgradeFailedException {
        log.debug("migrateDatabase660: Upgrading roles with approval rules");
        // Any roles with access to /ca_functionality/view_certifcate_profiles should be given /ca_functionality/view_approval_profiles
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.CAFUNCTIONALITY.resource(),
                Arrays.asList(StandardRules.CERTIFICATEPROFILEVIEW.resource()), Arrays.asList(StandardRules.APPROVALPROFILEVIEW.resource()), false);
        // Any roles with access to /ca_functionality/edit_certificate_profiles should be given /ca_functionality/edit_approval_profiles
        legacyRoleManagementSession.addAccessRuleDataToRolesWhenAccessIsImplied(authenticationToken, StandardRules.CAFUNCTIONALITY.resource(),
                Arrays.asList(StandardRules.CERTIFICATEPROFILEEDIT.resource()), Arrays.asList(StandardRules.APPROVALPROFILEEDIT.resource()), false);
        // Create AccumulativeApprovalProfile for all CA's and Certificate Profiles running approvals
        //Sort cache by the number of approvals
        Map<Integer, Integer> approvalProfileCache = new HashMap<>();
        Map<Integer, Integer> approvalPartitionCache = new HashMap<>();
        //Add approval profiles to all CAs with approvals
        try {
            log.debug("migrateDatabase660: Upgrading CAs with approval profiles");
            for (int caId : caSession.getAllCaIds()) {
                try {
                    CA ca = caSession.getCAForEdit(authenticationToken, caId);
                    int numberOfRequiredApprovals = ca.getNumOfRequiredApprovals();
                    //Verify that the CA is in need of an approval profile...
                    if (ca.getApprovalProfile() == -1 && ca.getApprovalSettings().size() > 0) {
                        //Maybe this profile has already been created?
                        if (approvalProfileCache.containsKey(Integer.valueOf(numberOfRequiredApprovals))) {
                            //Indeed it has!
                            ca.setApprovalProfile(approvalProfileCache.get(numberOfRequiredApprovals));
                            caSession.editCA(authenticationToken, ca, true);
                        } else {
                            //None found! Let's create one!
                            String name = "Require " + numberOfRequiredApprovals + " Approval" + (numberOfRequiredApprovals > 1 ? "s" : "");
                            AccumulativeApprovalProfile newProfile = new AccumulativeApprovalProfile(name);
                            try {
                                newProfile.setNumberOfApprovalsRequired(numberOfRequiredApprovals);
                            } catch (PropertyValidationException e1) {
                                log.info("Attempted to upgrade an approval profile with negative value (" + numberOfRequiredApprovals + "). Setting 0 instead.");
                                try {
                                    newProfile.setNumberOfApprovalsRequired(0);
                                } catch (PropertyValidationException e) {
                                    throw new IllegalStateException(e);
                                }
                            }
                            addApprovalNotification(newProfile);
                            try {
                                int newProfileId = approvalProfileSession.addApprovalProfile(authenticationToken, newProfile);
                                approvalProfileCache.put(numberOfRequiredApprovals, newProfileId);
                                approvalPartitionCache.put(numberOfRequiredApprovals, newProfile.getFirstStep().getPartitions().values().iterator().next().getPartitionIdentifier());
                                ca.setApprovalProfile(newProfileId);
                                caSession.editCA(authenticationToken, ca, true);
                            } catch (ApprovalProfileExistsException e) {
                                throw new IllegalStateException("Approval profile was apparently already persisted.", e);
                            }
                        }
                    }
                } catch (CADoesntExistsException e) {
                    throw new IllegalStateException("CA was not found, in spite of ID just being retireved", e);
                }
            }
            //Do the same for all certificate profiles (same boilerplate, repeated).
            log.debug("migrateDatabase660: Upgrading Certificate Profiles with approval profiles");
            Map<Integer, CertificateProfile> allCertificateProfiles = certProfileSession.getAllCertificateProfiles();
            for (Integer certificateProfileId : allCertificateProfiles.keySet()) {
                CertificateProfile certificateProfile = allCertificateProfiles.get(certificateProfileId);
                int numberOfRequiredApprovals = certificateProfile.getNumOfReqApprovals();
                //Verify that the Certificate Profile is in need of an approval profile...
                if (certificateProfile.getApprovalProfileID() == -1 && certificateProfile.getApprovalSettings().size() > 0) {
                    //Maybe this profile has already been created?
                    String certificateProfileName = certProfileSession.getCertificateProfileName(certificateProfileId);
                    if (approvalProfileCache.containsKey(Integer.valueOf(numberOfRequiredApprovals))) {
                        //Indeed it has!
                        certificateProfile.setApprovalProfileID(approvalProfileCache.get(numberOfRequiredApprovals));
                        certProfileSession.changeCertificateProfile(authenticationToken, certificateProfileName, certificateProfile);
                    } else {
                        //None found! Let's create one!
                        String name = "Require " + numberOfRequiredApprovals + " approval" + (numberOfRequiredApprovals > 1 ? "s" : "");
                        AccumulativeApprovalProfile newProfile = new AccumulativeApprovalProfile(name);
                        try {
                            newProfile.setNumberOfApprovalsRequired(numberOfRequiredApprovals);
                        } catch (PropertyValidationException e1) {
                            log.info("Attempted to upgrade an approval profile with negative value (" + numberOfRequiredApprovals + "). Setting 0 instead.");
                            try {
                                newProfile.setNumberOfApprovalsRequired(0);
                            } catch (PropertyValidationException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        addApprovalNotification(newProfile);
                        try {
                            int newProfileId = approvalProfileSession.addApprovalProfile(authenticationToken, newProfile);
                            approvalProfileCache.put(numberOfRequiredApprovals, newProfileId);
                            approvalPartitionCache.put(numberOfRequiredApprovals, newProfile.getFirstStep().getPartitions().values().iterator().next().getPartitionIdentifier());
                            certificateProfile.setApprovalProfileID(newProfileId);
                            certProfileSession.changeCertificateProfile(authenticationToken, certificateProfileName, certificateProfile);
                        } catch (ApprovalProfileExistsException e) {
                            throw new IllegalStateException("Upgrade appears to be happening concurrently.", e);
                        }
                    }
                }
            }

            // An approval now is specific to a partition in a step. Connect previously performed approvals
            // to the newly created partition so that the new code will recognize it. Note that an AccumulativeApprovalProfile
            // only has one step and one partition. The step ID is '0', which is the default step ID in an approval, which
            // is why the step ID in an approval does not need updating the same way as the partition ID needs updating.
            List<ApprovalData> approvalRequests = approvalSession.findWaitingForApprovalApprovalDataLocal();
            if (approvalRequests.isEmpty()) {
                log.debug("migrateDatabase660: No approval requests to upgrade");
            } else {
                log.debug("migrateDatabase660: Upgrading approval requests");
            }
            for(ApprovalData request : approvalRequests) {
                Collection<Approval> approvals = request.getApprovals();
                if(approvals.size() > 0) {
                    final int nrOfRequiredApprovals = request.getRemainingapprovals() + approvals.size();
                    final Integer partitionId = approvalPartitionCache.get(Integer.valueOf(nrOfRequiredApprovals));
                    if (partitionId != null) {
                        // It's an old approval from before 6.6.0, that needs upgrading
                        for (Approval approval : approvals) {
                            approval.setPartitionId(partitionId);
                        }
                        approvalSession.setApprovals(request, approvals);
                    } else {
                        // Might be an approval from 6.6.0, in case the upgrade fails at first and the user adds an approval (in 6.6 or later) before the successful upgrade.
                        // Check that this is really the case
                        boolean error = false;
                        for (Approval approval : approvals) {
                            if (approval.getPartitionId() == 0) { // not from 6.6.0, and can not be upgraded
                                error = true;
                            }
                        }
                        if (error) {
                            log.error("An approval in the approval request with ID " + request.getId() + " could not be upgraded because it could not be mapped to an accumulative approval profile. The approvals in this request have been deleted");
                            approvalSession.setApprovals(request, new ArrayList<Approval>());
                        }
                    }
                }
            }

        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException("AlwaysAllowToken was denied access", e);
        }
        log.error("(This is not an error) Completed upgrade procedure to 6.6.0");
    }

    /**
     * EJBCA 6.8.0:
     *
     * 1.   Converts AdminGroupData, AccessRuleData and AdminEntityData to RoleData and RoleMemberData
     * 2.   Migrates /ca_functionality/basic_functions and /ca_functionality/basic_functions/activate_ca
     *      to a single rule: /ca_functionality/activate_ca
     * 3.   Remove no longer used rules
     * 4.   Upgrades CAs and Certificate Profiles to go from having one approval profile for all approval types to having one for each
     *
     * @throws UpgradeFailedException if upgrade fails (rolls back)
     */
    @SuppressWarnings("deprecation")
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase680() throws UpgradeFailedException {
        log.debug("migrateDatabase680: Upgrading roles, rules and role members.");
        // Get largest possible list of all access rules on this system
        final Set<String> allResourcesInUseOnThisInstallation = authorizationSystemSession.getAllResources(true).keySet();
        // Migrate one AdminGroupData at the time
        final AccessRulesMigrator accessRulesMigrator = new AccessRulesMigrator(allResourcesInUseOnThisInstallation);
        final Collection<AdminGroupData> adminGroupDatas = legacyRoleManagementSession.getAllRoles();
        final boolean isInstalledOn660OrLater = !isLesserThan(getUpgradedFromVersion(), "6.6.0");
        for (final AdminGroupData adminGroupData : adminGroupDatas) {
            // Convert AdminGroupData and linked AccessRuleDatas to RoleData
            final String roleName = adminGroupData.getRoleName();
            final Collection<AccessRuleData> oldAccessRules = adminGroupData.getAccessRules().values();
            HashMap<String, Boolean> newAccessRules = accessRulesMigrator.toNewAccessRules(oldAccessRules, roleName);
            //Migrate rules & rule states changed in 6.8.0.
            newAccessRules = migrate680Rules(newAccessRules, isInstalledOn660OrLater);
            Role role = new Role(null, roleName, newAccessRules);
            // Keep AdminGroupData.primaryKey as RoleData.roleId so HardTokenIssuerData.adminGroupId still works during upgrade
            // (and use direct DB access since the EJB API wont allow us to assign roleId)
            final int roleId = adminGroupData.getPrimaryKey().intValue();
            role.setRoleId(roleId);
            if (roleDataSession.getRole(roleId)!=null) {
                log.info("RoleData '" + role.getRoleName() + "' (" + role.getRoleId() + ") already exists. Will perform merge old role members into this role and overwrite configured access rules.");
            }
            role.normalizeAccessRules();
            role.minimizeAccessRules();
            roleDataSession.persistRole(role);
            // Convert the linked AccessUserAspectDatas to RoleMemberDatas
            final Map<Integer, AccessUserAspectData> accessUsers = adminGroupData.getAccessUsers();
            // Each AccessUserAspectData belongs to one and only one role, so retrieving them this way may be considered safe.
            for (final AccessUserAspectData accessUserAspect : accessUsers.values()) {
                final String tokenType = accessUserAspect.getTokenType();
                // Only the X509CertificateAuthenticationToken actually uses the CA Id, so leave it unset for the rest
                final int tokenIssuerId;
                if (X509CertificateAuthenticationTokenMetaData.TOKEN_TYPE.equals(tokenType) && accessUserAspect.getCaId()!=null) {
                    tokenIssuerId = accessUserAspect.getCaId().intValue();
                } else {
                    tokenIssuerId = RoleMember.NO_ISSUER;
                }
                final int tokenMatchKey = accessUserAspect.getMatchWith();
                int tokenMatchOperator = accessUserAspect.getMatchType();
                String tokenMatchValue = accessUserAspect.getMatchValue();
                String description = "";
                // Straighten out comparison operators that don't make sense, since previous versions of EJBCA might have allowed such configuration
                if (X509CertificateAuthenticationTokenMetaData.TOKEN_TYPE.equals(tokenType)) {
                    if (tokenMatchKey == X500PrincipalAccessMatchValue.NONE.getNumericValue() ||
                            tokenMatchOperator == AccessMatchType.TYPE_NONE.getNumericValue()) {
                        // This will never match anything, drop it
                        log.info("Admin in role '" + roleName + "' of type " + tokenType + " with match key " + tokenMatchKey +
                                " match operator " + tokenMatchOperator + " and match value '" + tokenMatchValue +
                                "' will be dropped since it will never grant any access.");
                        continue;
                    }
                    if (tokenMatchKey == X500PrincipalAccessMatchValue.WITH_SERIALNUMBER.getNumericValue()) {
                        final String serialNumberUppercase = StringUtils.defaultString(tokenMatchValue).toUpperCase(Locale.ROOT).replaceFirst("^0+", "");
                        if (!serialNumberUppercase.equals(tokenMatchValue)) {
                            log.info("Admin in role '" + roleName + "' of type " + tokenType + " has serial number match value '" + tokenMatchValue +
                                    "'. In 6.8.0 all serial numbers are converted to uppercase without leading zeros and match as case sensitive.");
                        } else if (log.isDebugEnabled() && tokenMatchOperator == AccessMatchType.TYPE_EQUALCASEINS.getNumericValue()) {
                            log.debug("Admin in role '" + roleName + "' of type " + tokenType + " has case insensitive serial number match value '" + tokenMatchValue +
                                    "'. In 6.8.0 all serial numbers are converted to uppercase and match as case sensitive.");
                        }
                        tokenMatchOperator = AccessMatchType.TYPE_EQUALCASE.getNumericValue();
                        tokenMatchValue = serialNumberUppercase;
                        // If the certificate is present in the local database, we try to find a human readable description from the certificate
                        try {
                            final CAInfo caInfo = caSession.getCAInfoInternal(tokenIssuerId);
                            final String issuerDn = caInfo.getSubjectDN();
                            final Certificate certificate = certificateStoreSession.findCertificateByIssuerAndSerno(issuerDn, new BigInteger(tokenMatchValue, 16));
                            if (certificate!=null) {
                                final List<String> commonNames = CertTools.getPartsFromDN(CertTools.getSubjectDN(certificate), "CN");
                                if (!commonNames.isEmpty()) {
                                    // Use the first found CN of the mapped certificate
                                    description = commonNames.get(0);
                                }
                            } else {
                                description = "external client certificate";
                                // Since we made the database lookup, take the chance to inform about meaningless configuration
                                if (WebConfiguration.getRequireAdminCertificateInDatabase()) {
                                    log.info("Admin in role '" + roleName + "' of type " + tokenType + " with serial number match value '" + tokenMatchValue +
                                            "' does match a local certificate even though this is required by the '"+WebConfiguration.CONFIG_REQCERTINDB+"' setting." +
                                            "Migrating admin anyway.");
                                }
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Admin in role '" + roleName + "' of type " + tokenType + " with serial number match value '" + tokenMatchValue +
                                    "' could not be interpreted as a hex value. Admin will not be migrated.");
                        } catch (CADoesntExistsException e) {
                            log.info("Admin in role '" + roleName + "' of type " + tokenType + " with serial number match value '" + tokenMatchValue +
                                    "' is issued by a CA with ID " + tokenIssuerId + " that is unknown to this system. Migrating admin anyway.");
                        }
                    }
                    if (tokenMatchOperator == AccessMatchType.TYPE_NOT_EQUALCASE.getNumericValue() ||
                            tokenMatchOperator == AccessMatchType.TYPE_NOT_EQUALCASEINS.getNumericValue()) {
                        log.warn("Admin in role '" + roleName + "' of type " + tokenType + " with match key=" + tokenMatchKey +
                                " match operator " + tokenMatchOperator + " and match value='"+tokenMatchValue +
                                "' is most likely misconfigured. This will grant role access to anything not matching the value!");
                    }
                } else if (CliAuthenticationTokenMetaData.TOKEN_TYPE.equals(tokenType) || "UsernameBasedAuthenticationToken".equals(tokenType)) {
                    if (tokenMatchOperator != AccessMatchType.TYPE_EQUALCASE.getNumericValue()) {
                        // The implementation always does case sensitive compare
                        if (log.isDebugEnabled()) {
                            log.debug("Admin in role '" + roleName + "' of type " + tokenType + " with match key " + CliUserAccessMatchValue.USERNAME.name() +
                                    " match operator " + tokenMatchOperator + " with and match value '" + tokenMatchValue +
                                    "'. Changing match operator type to defacto operator TYPE_EQUALCASE.");
                        }
                        tokenMatchOperator = AccessMatchType.TYPE_EQUALCASE.getNumericValue();
                    }
                } else {
                    // None of the other known tokens when writing this upgrade use any operator
                    tokenMatchOperator = AccessMatchType.TYPE_UNUSED.getNumericValue();
                }
                // Assign upgraded role members the same ID as the old AdminEndEntity.primaryKey so members are merged in case this runs several times (like in tests)
                roleMemberDataSession.persistRoleMember(new RoleMember(accessUserAspect.getPrimaryKey(), tokenType,
                        tokenIssuerId, tokenMatchKey, tokenMatchOperator, tokenMatchValue, roleId, description));
            }
        }
        // Note that this has to happen here and not in X509CA or CvcCA due to the fact that this step has to happen after approval profiles have
        // been created in previous upgrade steps.
        log.debug("migrateDatabase680: Converting Certificate Authorities from using one approval profile for all request types "
                + "to using one profile per request type.");
        try {
            for (int caId : caSession.getAllCaIds()) {
                CA ca = caSession.getCAForEdit(authenticationToken, caId);
                //If approvals map is null or empty, then this CA may be in an unupgraded state.
                if(ca.getApprovals() == null || ca.getApprovals().isEmpty()) {
                	Map<ApprovalRequestType, Integer> approvals = new LinkedHashMap<>();
                    int approvalProfile = ca.getApprovalProfile();
                    if (approvalProfile != -1) {
                        for (int approvalSetting : ca.getApprovalSettings()) {
                            approvals.put(ApprovalRequestType.getFromIntegerValue(approvalSetting), approvalProfile);
                        }
                    }
                    ca.setApprovals(approvals);
                    caSession.editCA(authenticationToken, ca, true);
                }
            }
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException("Always allow token was denied access.", e);
        } catch (CADoesntExistsException e) {
            throw new IllegalStateException("CA doesn't exist in spite of just being retrieved", e);
        }
        // Note that this has to happen here and not in CertificateProfile due to the fact that this step has to happen after approval profiles have
        // been created in previous upgrade steps.
        log.debug("migrateDatabase680: Converting Certificate Profiles from using one approval profile for all request types "
                + "to using one profile per request type.");
        Map<Integer, CertificateProfile> certificateProfiles = certProfileSession.getAllCertificateProfiles();
        for (Integer profileId : certificateProfiles.keySet()) {
            CertificateProfile certificateProfile = certificateProfiles.get(profileId);
            String certificateProfileName = certProfileSession.getCertificateProfileName(profileId);
            Map<ApprovalRequestType, Integer> approvals = new LinkedHashMap<>();
            int approvalProfile = certificateProfile.getApprovalProfileID();
            if (approvalProfile != -1) {
                for (int approvalSetting : certificateProfile.getApprovalSettings()) {
                    approvals.put(ApprovalRequestType.getFromIntegerValue(approvalSetting), approvalProfile);
                }
            }
            certificateProfile.setApprovals(approvals);
            try {
                certProfileSession.changeCertificateProfile(authenticationToken, certificateProfileName, certificateProfile);
            } catch (AuthorizationDeniedException e) {
                throw new IllegalStateException("Always allow token was denied access.", e);
            }

        }

        log.error("(This is not an error) Completed upgrade procedure to 6.8.0");
    }


    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    @Override
    public void migrateDatabase6101() throws UpgradeFailedException {
        log.debug("migrateDatabase6100: Upgrading CT logs");
        final GlobalConfiguration gc = (GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
        final Map<Integer, CertificateProfile> allCertProfiles = certProfileSession.getAllCertificateProfiles();
        final LinkedHashMap<Integer, CTLogInfo> allCtLogs = gc.getCTLogs();
        LinkedHashMap<Integer, CTLogInfo> newCtLogs = new LinkedHashMap<>();
        Map<Integer, Integer> selectedLogs = new HashMap<>();

        if (allCtLogs.isEmpty()) {
            return;
        }

        // Get all logs selected by any certificate profile
        for (Map.Entry<Integer, CertificateProfile> profile : allCertProfiles.entrySet()) {
            for (Integer enabledLog : profile.getValue().getEnabledCTLogs()) {
                selectedLogs.put(enabledLog, enabledLog);
            }
        }

        /* Determine new label for each log...
         * If Google log or previously set to mandatory (6.10), place log under label 'Mandatory'.
         * If log is selected in any cert profile, put it under its own label in order to keep it in the cert profile after upgrade.
         * Gather remaining logs under the label 'Unlabeled'.
         */
        for (Map.Entry<Integer, CTLogInfo> ctLogInfo : allCtLogs.entrySet()) {
            CTLogInfo ctLog = ctLogInfo.getValue();
            if (ctLog.getUrl().contains("ct.googleapis.com") || ctLog.isMandatory()) {
                ctLog.setLabel("Mandatory");
            } else if (selectedLogs.containsKey(ctLog.getLogId())) {
                // Set label to domain name of url
                ctLog.setLabel(ctLog.getUrl().substring(ctLog.getUrl().lastIndexOf("://") + 3, ctLog.getUrl().lastIndexOf(".")));
            } else {
                ctLog.setLabel("Unlabeled");
            }
            newCtLogs.put(ctLog.getLogId(), ctLog);
        }

        // Save CT logs with new labels set
        gc.setCTLogs(newCtLogs);
        try {
            globalConfigurationSession.saveConfiguration(authenticationToken, gc);
        } catch (AuthorizationDeniedException e) {
            throw new IllegalStateException("Always allow token was denied access.", e);
        }

        // Set CT labels corresponding to previously set CT logs in each cert profile
        for (Integer profileId : allCertProfiles.keySet()) {
            CertificateProfile certProfile = allCertProfiles.get(profileId);
            if (certProfile.isUseCertificateTransparencyInCerts() || certProfile.isUseCertificateTransparencyInOCSP() || certProfile.isUseCertificateTransparencyInPublishers()) {
                LinkedHashSet<String> labelsToSelect = new LinkedHashSet<>();
                final String certProfileName = certProfileSession.getCertificateProfileName(profileId);
                for (Integer ctLog : certProfile.getEnabledCTLogs()) {
                    if (newCtLogs.containsKey(ctLog)) {
                        labelsToSelect.add(newCtLogs.get(ctLog).getLabel());
                    }
                }

                certProfile.setEnabledCTLabels(labelsToSelect);
                certProfile.setNumberOfSctByValidity(true);
                certProfile.setNumberOfSctByCustom(false);
                // With the new label system, at least one log from each label will be written to, hence allowing a maximum lower than
                // number of labels would lock out issuance.
                if (certProfile.getCtMaxNonMandatoryScts() < labelsToSelect.size()) {
                    certProfile.setCtMaxScts(labelsToSelect.size());
                } else {
                    certProfile.setCtMaxScts(certProfile.getCtMaxNonMandatoryScts());
                }
                if (certProfile.getCtMaxNonMandatorySctsOcsp() < labelsToSelect.size()) {
                    certProfile.setCtMaxSctsOcsp(labelsToSelect.size());
                } else {
                    certProfile.setCtMaxSctsOcsp(certProfile.getCtMaxNonMandatorySctsOcsp());
                }
                try {
                    certProfileSession.changeCertificateProfile(authenticationToken, certProfileName, certProfile);
                } catch (AuthorizationDeniedException e) {
                    throw new IllegalStateException("Always allow token was denied access.", e);
                }
            }
        }
    }

    private boolean postMigrateDatabase6101() {
        log.info("Starting post upgrade to 6.10.1.");
        final Map<Integer, CertificateProfile> allCertProfiles = certProfileSession.getAllCertificateProfiles();

        for (Integer profileId : allCertProfiles.keySet()) {
            CertificateProfile certProfile = allCertProfiles.get(profileId);
            final String certProfileName = certProfileSession.getCertificateProfileName(profileId);
            certProfile.removeLegacyCtData();
            try {
                certProfileSession.changeCertificateProfile(authenticationToken, certProfileName, certProfile);
            } catch (AuthorizationDeniedException e) {
                throw new IllegalStateException("Always allow token was denied access.", e);
            }
        }
        log.info("Post upgrade to 6.10.1 complete.");
        return true;
    }


    @SuppressWarnings("deprecation")
    private boolean postMigrateDatabase680() {
        log.info("Starting post upgrade to 6.8.0.");
        // Verify that there are no TYPE_NOT_EQUALCASE* still in use
        log.info("Verifying that there are no TYPE_NOT_EQUALCASE or TYPE_NOT_EQUALCASEINS token match operators still in use.");
        boolean hasNotEquals = false;
        for (final Role role : roleSession.getAuthorizedRoles(authenticationToken)) {
            for (final RoleMember roleMember : roleMemberDataSession.findRoleMemberByRoleId(role.getRoleId())) {
                final int tokenMatchOperator = roleMember.getTokenMatchOperator();
                if (AccessMatchType.TYPE_NOT_EQUALCASE.getNumericValue()==tokenMatchOperator ||
                        AccessMatchType.TYPE_NOT_EQUALCASEINS.getNumericValue()==tokenMatchOperator) {
                    log.error("Role '" + role.getRoleNameFull() + "' has a member with a 'not equals' match operator. Post-upgrade cannot complete until this is corrected.");
                    hasNotEquals = true;
                    break;
                }
            }
        }
        if (hasNotEquals) {
            return false;
        }
        // Change to use union role access rules instead of enum priority matching
        accessTreeUpdateSession.setNewAuthorizationPatternMarker();
        log.info("Admins belonging to multiple roles will now be granted the combined access when cache expires.");
        // Empty the legacy AdminEntityData, AdmingGroupData and AccessRulesData tables.
        if (EjbcaConfiguration.getIsInProductionMode()) {
            log.info("Cleaning up legacy roles and rules.");
            legacyRoleManagementSession.deleteAllRoles(authenticationToken);
        } else {
            log.warn("This EJBCA installation is not running in production mode, so the tables AdminEntityData, AdmingGroupData and AccessRulesData will not be emptied.");
        }
        log.info("Post upgrade to 6.8.0 complete.");
        return true;
    }

    /**
     * Since EJBCA 6.8.0, some rules are either removed or have a changed scope.
     * If Role had access to /ca_functionality/basic_functions or /ca_functionality/basic_functions/activate_ca,
     * grant access to new rule /ca_functionality/activate_ca
     *
     * If upgrading from 6.6.0 or later, grant access to /ca_functionality/view_certificate for roles with access
     * to ra_functionality/view_end_entity
     * @param accessRules HashMap of access rules to migrate
     * @param isInstalledOn660OrLater if upgrading from 6.6.0 or later
     * @return HashMap with migrated rule states
     */
    private HashMap<String, Boolean> migrate680Rules(HashMap<String, Boolean> newAccessRules, boolean isInstalledOn660OrLater) {
        Boolean isAllowedActivateCa = AccessRulesHelper.hasAccessToResource(newAccessRules, REGULAR_ACTIVATECA_OLD);
        Boolean isAllowedViewEndEntity = AccessRulesHelper.hasAccessToResource(newAccessRules, AccessRulesConstants.REGULAR_VIEWENDENTITY);
        if(isAllowedActivateCa) {
            newAccessRules.put(AccessRulesHelper.normalizeResource(AccessRulesConstants.REGULAR_ACTIVATECA), Role.STATE_ALLOW);
        } else {
            newAccessRules.put(AccessRulesHelper.normalizeResource(AccessRulesConstants.REGULAR_ACTIVATECA), Role.STATE_DENY);
        }
        //Remove deprecated rules
        newAccessRules.remove(AccessRulesHelper.normalizeResource(REGULAR_CABASICFUNCTIONS_OLD));
        newAccessRules.remove(AccessRulesHelper.normalizeResource(ROLE_PUBLICWEBUSER));
        newAccessRules.remove(AccessRulesHelper.normalizeResource(REGULAR_ACTIVATECA_OLD));
        if(isInstalledOn660OrLater && isAllowedViewEndEntity) {
            newAccessRules.put(AccessRulesHelper.normalizeResource(AccessRulesConstants.REGULAR_VIEWCERTIFICATE), Role.STATE_ALLOW);
        }
        return newAccessRules;
    }

    /** Add the previously global configuration configured approval notification */
    @SuppressWarnings("deprecation")
    private void addApprovalNotification(final AccumulativeApprovalProfile newProfile) {
        final GlobalConfiguration gc = (GlobalConfiguration) globalConfigurationSession.getCachedConfiguration(GlobalConfiguration.GLOBAL_CONFIGURATION_ID);
        if (gc.getUseApprovalNotifications()) {
            final String hostname = WebConfiguration.getHostName();
            final String baseUrl = gc.getBaseUrl(hostname);
            final String defaultSubject = "[AR-${approvalRequest.ID}-${approvalRequest.STEP_ID}-${approvalRequest.PARTITION_ID}] " +
                    "Approval Request to ${approvalRequest.TYPE} is now in state ${approvalRequest.WORKFLOWSTATE}";
            final String defaultBody = "Approval Request to ${approvalRequest.TYPE} from ${approvalRequest.REQUESTOR} is now in state ${approvalRequest.WORKFLOWSTATE}.\n" +
                    "\n" +
                    "Direct link to the request: " + baseUrl + "ra/managerequest.xhtml?aid=${approvalRequest.ID}";
            final ApprovalPartition approvalPartition = newProfile.getFirstStep().getPartitions().values().iterator().next();
            newProfile.addNotificationProperties(approvalPartition, gc.getApprovalAdminEmailAddress(), gc.getApprovalNotificationFromAddress(), defaultSubject, defaultBody);
        }
    }

    /**
     * In EJBCA 5.0 we have changed classname for CertificatePolicy.
     * In order to allow us to remove the legacy class in the future we want to upgrade all certificate profiles to use the new classname
     *
     * In order to be able to create new Roles we also need to remove the long deprecated database column caId, otherwise
     * we will get a database error during insert. Reading works fine though, so this is good for a post upgrade in order
     * to allow for 100% uptime upgrades.
     */
    @SuppressWarnings("deprecation")
    private boolean postMigrateDatabase500(String dbtype) {
        log.error("(this is not an error) Starting post upgrade from EJBCA 4.0.x to ejbca 5.0.x");
        boolean ret = true;

        AuthenticationToken admin = new AlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("UpgradeSessionBean.postMigrateDatabase500"));

    	// post-upgrade "change CertificatePolicy from ejbca class to cesecore class in certificate profiles that have that defined.
        Map<Integer, String> map = certProfileSession.getCertificateProfileIdToNameMap();
        Set<Integer> ids = map.keySet();
        for (Integer id : ids) {
            CertificateProfile profile = certProfileSession.getCertificateProfile(id);
            final List<CertificatePolicy> policies = profile.getCertificatePolicies();
            if ((policies != null) && (!policies.isEmpty())) {
                List<CertificatePolicy> newpolicies = getNewPolicies(policies);
                // Set the updated policies, replacing the old
                profile.setCertificatePolicies(newpolicies);
                try {
                    final String profName = map.get(id);
                    log.info("Upgrading CertificatePolicy of certificate profile '"+profName+"'. This profile can no longer be used with EJBCA 4.x.");
                    certProfileSession.changeCertificateProfile(admin, profName, profile);
                } catch (AuthorizationDeniedException e) {
                    log.error("Error upgrading certificate policy: ", e);
                }
            }
        }
        // post-upgrade "change CertificatePolicy from ejbca class to cesecore class in CAs profiles that have that defined?
        // fix CAs that don't have classpath for extended CA services
        Collection<Integer> caids = caSession.getAllCaIds();
        for (Integer caid : caids) {
            try {
                CA ca = caSession.getCAForEdit(admin, caid);
                if (ca.getCAType() == CAInfo.CATYPE_X509) {
                    try {
                        X509CA x509ca = (X509CA)ca;
                        final List<CertificatePolicy> policies = x509ca.getPolicies();
                        if ((policies != null) && (!policies.isEmpty())) {
                            List<CertificatePolicy> newpolicies = getNewPolicies(policies);
                            // Set the updated policies, replacing the old
                            x509ca.setPolicies(newpolicies);
                            // Finally store the upgraded CA
                            log.info("Upgrading CertificatePolicy of CA '"+ca.getName()+"'. This CA can no longer be used with EJBCA 4.x.");
                            caSession.editCA(admin, ca, true);
                        }
                    } catch (ClassCastException e) {
                        log.error("CA is not of type X509CA: "+caid+", "+ca.getClass().getName());
                    }
                }
            } catch (CADoesntExistsException e) {
                log.error("CA does not exist during upgrade: "+caid, e);
            } catch (AuthorizationDeniedException e) {
                log.error("Authorization denied to CA during upgrade: "+caid, e);
            }
        }

    	boolean exists = upgradeSession.checkColumnExists500();
    	if (exists) {
    		ret = migrateDatabase("/400_500/400_500-post-upgrade-"+dbtype+".sql");
    	}

        // Creates a super admin role for Cli usage. post-upgrade to remove caId column must have been run in order
    	// for this command to succeed.
    	// In practice this means that when upgrading from EJBCA 4.0 you can not use the CLI in 5.0 before you
    	// have finished migrating all your 4.0 nodes and run post-upgrade.
    	legacyRoleManagementSession.createSuperAdministrator();

        //Remove all old roles, should remove associated aspects and rules as well.
        removeOldRoles500();

    	log.error("(this is not an error) Finished post upgrade from EJBCA 4.0.x to EJBCA 5.0.x with result: "+ret);

        return ret;
    }

    /**
     * This method removes the following now unused roles:
     *                                                  DEFAULT
     *                                                  Temporary Super Administrator Group
     *                                                  Public Web Users
     */
    @SuppressWarnings("deprecation")
    private void removeOldRoles500() {
        legacyRoleManagementSession.deleteRole(authenticationToken, "DEFAULT");
        legacyRoleManagementSession.deleteRole(authenticationToken, "Temporary Super Administrator Group");
        legacyRoleManagementSession.deleteRole(authenticationToken, "Public Web Users");
    }

    private List<CertificatePolicy> getNewPolicies(final List<CertificatePolicy> policies) {
        final List<CertificatePolicy> newpolicies = new ArrayList<>();
        for(final Iterator<?> it = policies.iterator(); it.hasNext(); ) {
            Object o = it.next();
            try {
                final CertificatePolicy policy = (CertificatePolicy)o;
                // This was a new policy (org.cesecore), just add it
                newpolicies.add(policy);
            } catch (ClassCastException e) {
                // Here we stumbled upon an old certificate policy
                final org.ejbca.core.model.ca.certificateprofiles.CertificatePolicy policy = (org.ejbca.core.model.ca.certificateprofiles.CertificatePolicy)o;
                CertificatePolicy newpolicy = new CertificatePolicy(policy.getPolicyID(), policy.getQualifierId(), policy.getQualifier());
                newpolicies.add(newpolicy);
            }
        }
        return newpolicies;
    }

    /**
     * Checks if the column cAId column exists in AdminGroupData
     *
     * @return true or false if the column exists or not
     */
    @Override
    public boolean checkColumnExists500() {
		// Try to find out if caID exists in AdminGroupData, which it did prior to EJBCA 5
        // If it does exist, a post-upgrade has to be done
		final Connection connection = JDBCUtil.getDBConnection();
		boolean exists = false;
		try {
			final PreparedStatement stmt = connection.prepareStatement("select cAId from AdminGroupData where pk='0'");
			stmt.executeQuery();
			// If it did not throw an exception the column exists and we must run the post upgrade sql
			exists = true;
			log.info("cAId column exists in AdminGroupData");
		} catch (SQLException e) {
			// Column did not exist, it's good we are running a newer version
			log.info("cAId column does not exist in AdminGroupData");
			//log.debug(e.getMessage());
		} finally {
			try {
				connection.close();
			} catch (SQLException e) {
				// do nothing
			}
		}
		return exists;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean isLesserThan(final String first, final String second) {
        if (log.isTraceEnabled()) {
            log.trace("isLesserThan("+first+", "+second+")");
        }
        final String delimiter = "\\.";
        if (first == null) {
            if (second != null) {
                return true;    // No version is before a specified version
            }
            throw new IllegalArgumentException("First version argument may not be null");
        }
        if (second == null) {
            throw new IllegalArgumentException("Second version argument may not be null");
        }
        String[] firstSplit = first.split(delimiter);
        String[] secondSplit = second.split(delimiter);
        for (int i = 0; i < Math.max(firstSplit.length, secondSplit.length); i++) {
            if (i == firstSplit.length) {
                //We've gotten this far and passed the number of digits in first, so false
                return true;
            } else if (i == secondSplit.length) {
                return false;
            } else {
                String firstString = firstSplit[i].replaceAll("[^0-9].*", "");    // Remove trailing Beta2, _alpha1 etc
                if (firstString.isEmpty()) {
                    firstString = "0";  // Treat ".x" as ".0"
                }
                String secondString = secondSplit[i].replaceAll("[^0-9].*", "");
                if (secondString.isEmpty()) {
                    secondString = "0";
                }
                if (StringUtils.isNumeric(firstString) && StringUtils.isNumeric(secondString)) {
                    final int firstNumber = Integer.valueOf(firstString);
                    final int secondNumber = Integer.valueOf(secondString);
                    if (firstNumber != secondNumber) {
                        return firstNumber < secondNumber;
                    }
                } else {
                    throw new IllegalArgumentException("Unable to parse version numbers.");
                }
            }
        }
        //Versions must be the same then
        return false;
    }
}
