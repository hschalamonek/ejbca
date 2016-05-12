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
package org.ejbca.ra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.event.ValueChangeEvent;

import org.apache.log4j.Logger;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.AlgorithmTools;
import org.cesecore.certificates.util.DnComponents;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.util.StringTools;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.era.IdNameHashMap;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.model.util.GenerateToken;

/**
 * Managed bean that backs up the enrollingmakenewrequest.xhtml page
 * 
 * @version $Id$
 */
@ManagedBean
@SessionScoped
public class EnrollMakeNewRequestBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = Logger.getLogger(EnrollMakeNewRequestBean.class);

    @EJB
    private RaMasterApiProxyBeanLocal raMasterApiProxyBean;

    @ManagedProperty(value = "#{raAuthenticationBean}")
    private RaAuthenticationBean raAuthenticationBean;

    public void setRaAuthenticationBean(final RaAuthenticationBean raAuthenticationBean) {
        this.raAuthenticationBean = raAuthenticationBean;
    }

    @ManagedProperty(value = "#{raLocaleBean}")
    private RaLocaleBean raLocaleBean;

    public void setRaLocaleBean(final RaLocaleBean raLocaleBean) {
        this.raLocaleBean = raLocaleBean;
    }

    //1. Authorized end entity profiles (certificate types)
    private IdNameHashMap<EndEntityProfile> authorizedEndEntityProfiles = new IdNameHashMap<EndEntityProfile>();
    private IdNameHashMap<CertificateProfile> authorizedCertificateProfiles = new IdNameHashMap<>();
    private IdNameHashMap<CAInfo> authorizedCAInfos = new IdNameHashMap<CAInfo>();
    private String selectedEndEntityProfile;
    private boolean endEntityProfileChanged;
    private Map<String, String> availableEndEntityProfiles = new HashMap<String, String>();

    //2. Available certificate profiles (certificate subtypes)
    private Map<String, String> availableCertificateProfiles = new HashMap<String, String>();
    private String selectedCertificateProfile;
    private boolean certificateProfileChanged;

    //3. Available Certificate Authorities
    private Map<String, String> availableCertificateAuthorities = new HashMap<String, String>();
    private String selectedCertificateAuthority;
    private boolean certificateAuthorityChanged;

    //4. Key-pair generation
    public enum KeyPairGeneration {
        ON_SERVER("Generated on server"), BY_BROWSER_CLIENT("Generated by browser client"), PROVIDED_BY_USER("Provided by user");
        private String value;

        private KeyPairGeneration(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private Map<String, KeyPairGeneration> availableKeyPairGenerations = new HashMap<String, KeyPairGeneration>();
    private String selectedKeyPairGeneration;
    private boolean keyPairGenerationChanged;

    //5. Key-pair generation on server
    private Map<String, String> availableAlgorithms = new TreeMap<String, String>();
    private String selectedAlgorithm;
    private boolean algorithmChanged;

    //6. Certificate data
    private SubjectDn subjectDn;
    private SubjectAlternativeName subjectAlternativeName;
    private SubjectDirectoryAttributes subjectDirectoryAttributes;
    private boolean certificateDataReady;

    //7. Download credentials data
    private EndEntityInformation endEntityInformation;
    private String confirmPassword;

    public enum DownloadCredentialsType {
        NO_CREDENTIALS_DIRECT_DOWNLOAD("No credentials (direct download)"), USERNAME_PASSWORD("Username and password credentials"), EMAIL(
                "Download credentials will be sent to the specified email");
        private String value;

        private DownloadCredentialsType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private Map<String, DownloadCredentialsType> availableDownloadCredentials = new HashMap<String, DownloadCredentialsType>();
    private String selectedDownloadCredentialsType;
    private boolean downloadCredentialsChanged;

    @PostConstruct
    private void postContruct() {
        initAll();
    }

    public void initAll() {
        initAuthorizedEndEntityProfiles();
        if (availableEndEntityProfiles.size() == 1) {
            setSelectedEndEntityProfile(availableEndEntityProfiles.keySet().iterator().next());
            selectEndEntityProfile();
        }
    }

    //-----------------------------------------------------------
    //All init* methods should contain ONLY application logic 

    public void initAuthorizedEndEntityProfiles() {
        setAuthorizedEndEntityProfiles(raMasterApiProxyBean.getAuthorizedEndEntityProfiles(raAuthenticationBean.getAuthenticationToken()));
        setAuthorizedCertificateProfiles(raMasterApiProxyBean.getAuthorizedCertificateProfiles(raAuthenticationBean.getAuthenticationToken()));
        setAuthorizedCAInfos(raMasterApiProxyBean.getAuthorizedCAInfos(raAuthenticationBean.getAuthenticationToken()));

        for (IdNameHashMap<EndEntityProfile>.Tuple tuple : authorizedEndEntityProfiles.values()) {
            availableEndEntityProfiles.put(tuple.getName(), tuple.getName());
        }
    }

    private void initAvailableCertificateProfiles() {
        EndEntityProfile endEntityProfile = getEndEntityProfile();
        if (endEntityProfile == null) {
            return;
        }
        String[] availableCertificateProfileIds = endEntityProfile.getValue(EndEntityProfile.AVAILCERTPROFILES, 0).split(EndEntityProfile.SPLITCHAR);
        for (String id : availableCertificateProfileIds) {
            IdNameHashMap<CertificateProfile>.Tuple tuple = authorizedCertificateProfiles.get(Integer.parseInt(id));
            if (tuple != null) {
                String defaultCertProfileId = endEntityProfile.getValue(EndEntityProfile.DEFAULTCERTPROFILE, 0);
                if (id.equalsIgnoreCase(defaultCertProfileId)) {
                    availableCertificateProfiles.put(tuple.getName(), tuple.getName() + " (default)");
                } else {
                    availableCertificateProfiles.put(tuple.getName(), tuple.getName());
                }
            }
        }
    }

    private void initAvailableCertificateAuthorities() {
        //Get all available CAs from the selected EEP
        EndEntityProfile endEntityProfile = getEndEntityProfile();
        if (endEntityProfile == null) {
            return;
        }
        String[] availableCAsFromEEPArray = endEntityProfile.getValue(EndEntityProfile.AVAILCAS, 0).split(EndEntityProfile.SPLITCHAR);
        boolean anyCAAvailableFromEEP = availableCAsFromEEPArray.length == 1 && availableCAsFromEEPArray[0].equalsIgnoreCase(SecConst.ALLCAS + "");

        //Get all available CAs from the selected CP
        CertificateProfile certificateProfile = authorizedCertificateProfiles.get(selectedCertificateProfile).getValue();
        if (certificateProfile == null) {
            return;
        }
        List<Integer> availableCAsFromCP = certificateProfile.getAvailableCAs();
        boolean anyCAAvailableFromCP = availableCAsFromCP.size() == 1 && availableCAsFromCP.iterator().next() == CertificateProfile.ANYCA;

        //Intersect both with authorized CAs
        for (IdNameHashMap<CAInfo>.Tuple tuple : authorizedCAInfos.values()) {
            if ((anyCAAvailableFromEEP || Arrays.asList(availableCAsFromEEPArray).contains(tuple.getId() + ""))
                    && (anyCAAvailableFromCP || availableCAsFromCP.contains(tuple.getId()))) {
                String defaultCAId = endEntityProfile.getValue(EndEntityProfile.DEFAULTCA, 0);
                if (!defaultCAId.isEmpty() && tuple.getId() == Integer.parseInt(defaultCAId)) {
                    availableCertificateAuthorities.put(tuple.getName(), tuple.getName() + " (default)");
                } else {
                    availableCertificateAuthorities.put(tuple.getName(), tuple.getName());
                }
            }
        }
    }

    private void initAvailableKeyPairGeneration() {
        EndEntityProfile endEntityProfile = getEndEntityProfile();
        if (endEntityProfile == null) {
            return;
        }
        String availableKeyStores = endEntityProfile.getValue(EndEntityProfile.AVAILKEYSTORE, 0);
        //TOKEN_SOFT_BROWSERGEN = 1;
        //TOKEN_SOFT_P12 = 2;
        //TOKEN_SOFT_JKS = 3;
        //TOKEN_SOFT_PEM = 4;
        if (availableKeyStores.contains("2") || availableKeyStores.contains("3") || availableKeyStores.contains("4")) {
            availableKeyPairGenerations.put(KeyPairGeneration.ON_SERVER.getValue(), KeyPairGeneration.ON_SERVER);
        }
        if (availableKeyStores.contains("1")) {
            availableKeyPairGenerations.put(KeyPairGeneration.BY_BROWSER_CLIENT.getValue(), KeyPairGeneration.BY_BROWSER_CLIENT);
            availableKeyPairGenerations.put(KeyPairGeneration.PROVIDED_BY_USER.getValue(), KeyPairGeneration.PROVIDED_BY_USER);
        }
    }

    private void initAvailableAlgorithms() {
        CertificateProfile certificateProfile = getCertificateProfile();
        final List<String> availableKeyAlgorithms = certificateProfile.getAvailableKeyAlgorithmsAsList();
        final List<Integer> availableBitLengths = certificateProfile.getAvailableBitLengthsAsList();
        if (availableKeyAlgorithms.contains(AlgorithmConstants.KEYALGORITHM_DSA)) {
            for (final int availableBitLength : availableBitLengths) {
                if (availableBitLength == 1024) {
                    availableAlgorithms.put(AlgorithmConstants.KEYALGORITHM_DSA + "_" + availableBitLength,
                            AlgorithmConstants.KEYALGORITHM_DSA + " " + availableBitLength + " bits");
                }
            }
        }
        if (availableKeyAlgorithms.contains(AlgorithmConstants.KEYALGORITHM_RSA)) {
            for (final int availableBitLength : availableBitLengths) {
                if (availableBitLength >= 1024) {
                    availableAlgorithms.put(AlgorithmConstants.KEYALGORITHM_RSA + "_" + availableBitLength,
                            AlgorithmConstants.KEYALGORITHM_RSA + " " + availableBitLength + " bits");
                }
            }
        }
        if (availableKeyAlgorithms.contains(AlgorithmConstants.KEYALGORITHM_ECDSA)) {
            final Set<String> ecChoices = new HashSet<>();
            final Map<String, List<String>> namedEcCurvesMap = AlgorithmTools.getNamedEcCurvesMap(false);
            if (certificateProfile.getAvailableEcCurvesAsList().contains(CertificateProfile.ANY_EC_CURVE)) {
                final String[] keys = namedEcCurvesMap.keySet().toArray(new String[namedEcCurvesMap.size()]);
                for (final String ecNamedCurve : keys) {
                    if (CertificateProfile.ANY_EC_CURVE.equals(ecNamedCurve)) {
                        continue;
                    }
                    final int bitLength = AlgorithmTools.getNamedEcCurveBitLength(ecNamedCurve);
                    if (availableBitLengths.contains(Integer.valueOf(bitLength))) {
                        ecChoices.add(ecNamedCurve);
                    }
                }
            }
            ecChoices.addAll(certificateProfile.getAvailableEcCurvesAsList());
            ecChoices.remove(CertificateProfile.ANY_EC_CURVE);
            final List<String> ecChoicesList = new ArrayList<>(ecChoices);
            Collections.sort(ecChoicesList);
            for (final String ecNamedCurve : ecChoicesList) {
                availableAlgorithms.put(AlgorithmConstants.KEYALGORITHM_ECDSA + "_" + ecNamedCurve, AlgorithmConstants.KEYALGORITHM_ECDSA + " "
                        + StringTools.getAsStringWithSeparator(" / ", namedEcCurvesMap.get(ecNamedCurve)));
            }
        }
        for (final String algName : CesecoreConfiguration.getExtraAlgs()) {
            if (availableKeyAlgorithms.contains(CesecoreConfiguration.getExtraAlgTitle(algName))) {
                for (final String subAlg : CesecoreConfiguration.getExtraAlgSubAlgs(algName)) {
                    final String name = CesecoreConfiguration.getExtraAlgSubAlgName(algName, subAlg);
                    final int bitLength = AlgorithmTools.getNamedEcCurveBitLength(name);
                    if (availableBitLengths.contains(Integer.valueOf(bitLength))) {
                        availableAlgorithms.put(CesecoreConfiguration.getExtraAlgTitle(algName) + "_" + name,
                                CesecoreConfiguration.getExtraAlgSubAlgTitle(algName, subAlg));
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Excluding " + name + " from enrollment options since bit length " + bitLength + " is not available.");
                        }
                    }
                }
            }
        }
    }

    private void initCertificateData() {
        EndEntityProfile endEntityProfile = getEndEntityProfile();
        if (endEntityProfile == null) {
            return;
        }
        subjectDn = new SubjectDn(endEntityProfile);
        subjectAlternativeName = new SubjectAlternativeName(endEntityProfile);
        subjectDirectoryAttributes = new SubjectDirectoryAttributes(endEntityProfile);
    }

    private void initDownloadCredentialsType() {
        availableDownloadCredentials.put(DownloadCredentialsType.NO_CREDENTIALS_DIRECT_DOWNLOAD.getValue(),
                DownloadCredentialsType.NO_CREDENTIALS_DIRECT_DOWNLOAD);
        availableDownloadCredentials.put(DownloadCredentialsType.USERNAME_PASSWORD.getValue(), DownloadCredentialsType.USERNAME_PASSWORD);
        availableDownloadCredentials.put(DownloadCredentialsType.EMAIL.getValue(), DownloadCredentialsType.EMAIL);
    }

    private void initDownloadCredentialsData() {
        endEntityInformation = new EndEntityInformation();
    }

    //-----------------------------------------------------------------------------------------------
    // Helpers
    public String getSubjectDnFieldOutputName(String keyName) {
        return raLocaleBean.getMessage("subject_dn_" + keyName);
    }

    public String getSubjectAlternativeNameFieldOutputName(String keyName) {
        return raLocaleBean.getMessage("subject_alternative_name_" + keyName);
    }

    public String getSubjectDirectoryAttributesFieldOutputName(String keyName) {
        return raLocaleBean.getMessage("subject_directory_attributes_" + keyName);
    }

    public boolean getUsernameRendered() {
        return selectedDownloadCredentialsType != null
                && selectedDownloadCredentialsType.equalsIgnoreCase(DownloadCredentialsType.USERNAME_PASSWORD.getValue());
    }

    public boolean getPasswordRendered() {
        return getUsernameRendered();
    }

    public boolean getEmailRendered() {
        return selectedDownloadCredentialsType != null
                && !selectedDownloadCredentialsType.equalsIgnoreCase(DownloadCredentialsType.NO_CREDENTIALS_DIRECT_DOWNLOAD.getValue());
    }

    public boolean getDownloadRendered() {
        return selectedDownloadCredentialsType != null
                && (selectedDownloadCredentialsType.equalsIgnoreCase(DownloadCredentialsType.NO_CREDENTIALS_DIRECT_DOWNLOAD.getValue()) ||
                        selectedDownloadCredentialsType.equalsIgnoreCase(DownloadCredentialsType.USERNAME_PASSWORD.getValue()));//TODO probably will need to get updated once approvals are implemented in kickassra
    }

    public String getDownloadButtonValue() {
        return raLocaleBean.getMessage("generic_download", "p12");
    }

    //-----------------------------------------------------------------------------------------------
    //All reset* methods should be able to clear/reset states that have changed during init* methods.
    //Always make sure that reset methods are chained!

    public final void reset() {
        availableEndEntityProfiles.clear();
        selectedEndEntityProfile = null;
        endEntityProfileChanged = false;
        resetCertificateProfile();

        initAll();
    }

    private final void resetCertificateProfile() {
        availableCertificateProfiles.clear();
        selectedCertificateProfile = null;
        certificateProfileChanged = false;

        resetCertificateAuthority();
    }

    private final void resetCertificateAuthority() {
        availableCertificateAuthorities.clear();
        selectedCertificateAuthority = null;
        certificateAuthorityChanged = false;

        resetKeyPairGeneration();
    }

    private final void resetKeyPairGeneration() {
        availableKeyPairGenerations.clear();
        selectedKeyPairGeneration = null;
        keyPairGenerationChanged = false;

        resetAlgorithm();
    }

    private final void resetAlgorithm() {
        availableAlgorithms.clear();
        selectedAlgorithm = null;
        algorithmChanged = false;

        resetCertificateData();
    }

    private final void resetCertificateData() {
        subjectDn = null;
        subjectAlternativeName = null;
        subjectDirectoryAttributes = null;
        certificateDataReady = false;

        resetDownloadCredentialsType();
    }

    private final void resetDownloadCredentialsType() {
        availableDownloadCredentials.clear();
        selectedDownloadCredentialsType = null;

        resetDownloadCredentialsData();
    }

    private final void resetDownloadCredentialsData() {
        endEntityInformation = null;
    }

    /**
     * Proceeds to a next step of enrollment phase. In situations where AJAX is provided this method is not needed and used.
     * This method can be invoked with "Next" button.
     * @throws IOException
     */
    public final void next() throws IOException {
        if (endEntityProfileChanged) {
            selectEndEntityProfile();
        } else if (certificateProfileChanged) {
            selectCertificateProfile();
        } else if (certificateAuthorityChanged) {
            selectCertificateAuthority();
        } else if (keyPairGenerationChanged) {
            selectKeyPairGeneration();
        } else if (algorithmChanged) {
            selectAlgorithm();
        } else if (downloadCredentialsChanged) {
            selectDownloadCredentialsType();
        } else {
            if (selectedDownloadCredentialsType != null) {
                selectDownloadCredentialsType();
            } else if (subjectDn != null) {
                finalizeCertificateData();
            } else if (selectedAlgorithm != null) {
                selectAlgorithm();
            } else if (selectedKeyPairGeneration != null) {
                selectKeyPairGeneration();
            } else if (selectedCertificateAuthority != null) {
                selectCertificateAuthority();
            } else if (selectedCertificateProfile != null) {
                selectCertificateProfile();
            } else {
                selectEndEntityProfile();
            }
        }
    }

    //-----------------------------------------------------------------------------------------------
    //Action methods (e.g. select*, submit*..) that are called directly from appropriate AJAX listener or from next() method

    private final void selectEndEntityProfile() {
        setEndEntityProfileChanged(false);

        resetCertificateProfile();
        initAvailableCertificateProfiles();
        if (availableCertificateProfiles.size() == 1) {
            setSelectedCertificateProfile(availableCertificateProfiles.keySet().iterator().next());
            selectCertificateProfile();
        }
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedEndEntityProfile", selectedEndEntityProfile);
    }

    private final void selectCertificateProfile() {
        setCertificateProfileChanged(false);

        resetCertificateAuthority();
        initAvailableCertificateAuthorities();
        if (availableCertificateAuthorities.size() == 1) {
            setSelectedCertificateAuthority(availableCertificateAuthorities.keySet().iterator().next());
            selectCertificateAuthority();
        }
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedCertificateAuthority", selectedCertificateAuthority);
    }

    private final void selectCertificateAuthority() {
        setCertificateAuthorityChanged(false);

        resetKeyPairGeneration();
        initAvailableKeyPairGeneration();
        if (availableKeyPairGenerations.size() == 1) {
            setSelectedKeyPairGeneration(availableKeyPairGenerations.keySet().iterator().next());
            selectKeyPairGeneration();
        }
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedCertificateProfile", selectedCertificateProfile);
    }

    private final void selectKeyPairGeneration() {
        setKeyPairGenerationChanged(false);

        resetAlgorithm();
        if (selectedKeyPairGeneration.equalsIgnoreCase(KeyPairGeneration.ON_SERVER.getValue())) {
            initAvailableAlgorithms();
        }
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedKeyPairGeneration", selectedKeyPairGeneration);
    }

    private final void selectAlgorithm() {
        setAlgorithmChanged(false);

        resetCertificateData();
        initCertificateData();
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedAlgorithm", selectedAlgorithm);
    }

    private final void finalizeCertificateData() {
        certificateDataReady = true;

        initDownloadCredentialsType();
    }

    private final void selectDownloadCredentialsType() {
        setDownloadCredentialsChanged(false);

        resetDownloadCredentialsData();
        initDownloadCredentialsData();
        raLocaleBean.addMessageInfo("somefunction_testok", "selectedDownloadCredentials", selectedDownloadCredentialsType);
    }

    private final void setDownloadCredentialsData() {
        endEntityInformation.setCAId(getCAInfo().getCAId());
        endEntityInformation.setCardNumber(""); //TODO Card Number
        endEntityInformation.setCertificateProfileId(authorizedCertificateProfiles.get(selectedCertificateProfile).getId());
        endEntityInformation.setDN(subjectDn.toString());
        endEntityInformation.setEndEntityProfileId(authorizedEndEntityProfiles.get(selectedEndEntityProfile).getId());
        endEntityInformation.setExtendedinformation(new ExtendedInformation());//TODO don't know anything about it...
        endEntityInformation.setHardTokenIssuerId(0); //TODO not sure....
        endEntityInformation.setKeyRecoverable(false); //TODO not sure...
        endEntityInformation.setPrintUserData(false); // TODO not sure...
        endEntityInformation.setSendNotification(false); // TODO will be updated
        endEntityInformation.setStatus(EndEntityConstants.STATUS_NEW);
        endEntityInformation.setSubjectAltName(subjectAlternativeName.toString());
        endEntityInformation.setTimeCreated(new Date());//TODO client vs server time issues?
        endEntityInformation.setTimeModified(new Date());//TODO client vs server time issues?
        endEntityInformation.setTokenType(SecConst.TOKEN_SOFT_P12); //TODO make it configurable
        endEntityInformation.setType(new EndEntityType(EndEntityTypes.ENDUSER));
        //TODO how to set subject directory attributes?
    }

    public final void download() {
        //Update the EndEntityInformation data
        subjectDn.updateValue();
        subjectAlternativeName.updateValue();
        subjectDirectoryAttributes.updateValue();
        setDownloadCredentialsData();
        
        //Enter temporary credentials
        if(selectedDownloadCredentialsType.equalsIgnoreCase(DownloadCredentialsType.NO_CREDENTIALS_DIRECT_DOWNLOAD.getValue())){
            String commonName = subjectDn.getFieldInstancesMap().get(DnComponents.COMMONNAME).getValue(); //Common Name has to be required field
            endEntityInformation.setUsername(commonName);
            endEntityInformation.setPassword(commonName);
        }

        //Add end-entity
        try {
            raMasterApiProxyBean.addUser(raAuthenticationBean.getAuthenticationToken(), endEntityInformation, true);//TODO clear password config
            log.info(raLocaleBean.getMessage("enroll_end_entity_has_been_successfully_added", endEntityInformation.getUsername()));
        } catch (EndEntityExistsException | CADoesntExistsException | AuthorizationDeniedException | EjbcaException
                | UserDoesntFullfillEndEntityProfile | WaitingForApprovalException e) {
            raLocaleBean.addMessageInfo("enroll_end_entity_could_not_be_added", endEntityInformation.getUsername(), e.getMessage());
            log.error(raLocaleBean.getMessage("enroll_end_entity_could_not_be_added", endEntityInformation.getUsername(), e.getMessage()), e);
            return;
        }

        //Generate keystore
        KeyStore keystore = null;
        try {
            keystore = raMasterApiProxyBean.generateKeystore(raAuthenticationBean.getAuthenticationToken(), endEntityInformation);//TODO clear password config
            log.info(raLocaleBean.getMessage("enroll_token_has_been_successfully_generated", "PKCS #12", endEntityInformation.getUsername()));
        } catch (KeyStoreException| AuthorizationDeniedException e) {
            raLocaleBean.addMessageInfo("enroll_keystore_could_not_be_generated", endEntityInformation.getUsername(), e.getMessage());
            log.error(raLocaleBean.getMessage("enroll_keystore_could_not_be_generated", endEntityInformation.getUsername(), e.getMessage()), e);
            return;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            keystore.store(buffer, endEntityInformation.getPassword().toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            raLocaleBean.addMessageInfo("enroll_keystore_could_not_be_generated", endEntityInformation.getUsername(), e.getMessage());
            log.error(raLocaleBean.getMessage("enroll_keystore_could_not_be_generated", endEntityInformation.getUsername(), e.getMessage()), e);
            return;
        }
        
        //Download
        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();
        ec.responseReset(); // Some JSF component library or some Filter might have set some headers in the buffer beforehand. We want to get rid of them, else it may collide.
        ec.setResponseContentType("application/x-pkcs12");
        ec.setResponseContentLength(buffer.size());
        ec.setResponseHeader("Content-Disposition",
                "attachment; filename=\"" + StringTools.stripFilename(endEntityInformation.getUsername() + ".p12") + "\""); // The Save As popup magic is done here. You can give it any file name you want, this only won't work in MSIE, it will use current request URL as file name instead.
        OutputStream output = null;
        try {
            output = ec.getResponseOutputStream();
            buffer.writeTo(output);
            output.flush();
        } catch (IOException e) {
            log.error(raLocaleBean.getMessage("enroll_keystore_could_not_be_generated", endEntityInformation.getUsername(), e.getMessage()), e);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                }
            }
            fc.responseComplete(); // Important! Otherwise JSF will attempt to render the response which obviously will fail since it's already written with a file and closed.
        }
    }

    //-----------------------------------------------------------------------------------------------
    //Listeners that will be invoked from xhtml
    public final void endEntityProfileChangedListener(ValueChangeEvent e) {
        setEndEntityProfileChanged(true);
    }

    public final void certificateProfileChangedListener(ValueChangeEvent e) {
        setCertificateProfileChanged(true);
    }

    public final void certificateAuthorityChangedListener(ValueChangeEvent e) {
        setCertificateAuthorityChanged(true);
    }

    public final void keyPairGenerationChangedListener(ValueChangeEvent e) {
        setKeyPairGenerationChanged(true);
    }

    public final void algorithmChangedListener(ValueChangeEvent e) {
        setAlgorithmChanged(true);
    }

    public final void downloadCredentialsTypeChangedListener(ValueChangeEvent e) {
        setDownloadCredentialsChanged(true);
    }

    public final void endEntityProfileAjaxListener(final AjaxBehaviorEvent event) {
        selectEndEntityProfile();
    }

    public final void certificateProfileAjaxListener(final AjaxBehaviorEvent event) {
        selectCertificateProfile();
    }

    public final void certificateAuthorityAjaxListener(final AjaxBehaviorEvent event) {
        selectCertificateAuthority();
    }

    public final void keyPairGenerationAjaxListener(final AjaxBehaviorEvent event) {
        selectKeyPairGeneration();
    }

    public final void algorithmAjaxListener(final AjaxBehaviorEvent event) {
        selectAlgorithm();
    }

    public final void downloadCredentialsTypeAjaxListener(final AjaxBehaviorEvent event) {
        selectDownloadCredentialsType();
    }

    //-----------------------------------------------------------------------------------------------
    //Automatically generated getters/setters
    /**
     * @return the authorizedEndEntityProfiles
     */
    public IdNameHashMap<EndEntityProfile> getAuthorizedEndEntityProfiles() {
        return authorizedEndEntityProfiles;
    }

    /**
     * @param authorizedEndEntityProfiles the authorizedEndEntityProfiles to set
     */
    private void setAuthorizedEndEntityProfiles(IdNameHashMap<EndEntityProfile> authorizedEndEntityProfiles) {
        this.authorizedEndEntityProfiles = authorizedEndEntityProfiles;
    }

    /**
     * @return the selectedEndEntityProfile
     */
    public String getSelectedEndEntityProfile() {
        return selectedEndEntityProfile;
    }

    public EndEntityProfile getEndEntityProfile() {
        if (selectedEndEntityProfile == null) {
            return null;
        }
        IdNameHashMap<EndEntityProfile>.Tuple temp = authorizedEndEntityProfiles.get(selectedEndEntityProfile);
        if (temp == null) {
            return null;
        }
        return temp.getValue();
    }

    public CertificateProfile getCertificateProfile() {
        if (selectedCertificateProfile == null) {
            return null;
        }
        IdNameHashMap<CertificateProfile>.Tuple temp = authorizedCertificateProfiles.get(selectedCertificateProfile);
        if (temp == null) {
            return null;
        }
        return temp.getValue();
    }

    public CAInfo getCAInfo() {
        if (selectedCertificateAuthority == null) {
            return null;
        }
        IdNameHashMap<CAInfo>.Tuple temp = authorizedCAInfos.get(selectedCertificateAuthority);
        if (temp == null) {
            return null;
        }
        return temp.getValue();
    }

    /**
     * @param selectedEndEntityProfile the selectedEndEntityProfile to set
     */
    public void setSelectedEndEntityProfile(String selectedEndEntityProfile) {
        this.selectedEndEntityProfile = selectedEndEntityProfile;
    }

    /**
     * @return the selectedKeyPairGeneration
     */
    public String getSelectedKeyPairGeneration() {
        return selectedKeyPairGeneration;
    }

    /**
     * @param selectedKeyPairGeneration the selectedKeyPairGeneration to set
     */
    public void setSelectedKeyPairGeneration(String selectedKeyStoreGeneration) {
        this.selectedKeyPairGeneration = selectedKeyStoreGeneration;
    }

    /**
     * @return the endEntityProfileChanged
     */
    public boolean isEndEntityProfileChanged() {
        return endEntityProfileChanged;
    }

    /**
     * @param endEntityProfileChanged the endEntityProfileChanged to set
     */
    public void setEndEntityProfileChanged(boolean endEntityProfileChanged) {
        this.endEntityProfileChanged = endEntityProfileChanged;
    }

    public Map<String, KeyPairGeneration> getAvailableKeyPairGenerations() {
        return availableKeyPairGenerations;
    }

    public void setAvailableKeyPairGenerations(Map<String, KeyPairGeneration> availableKeyPairGenerations) {
        this.availableKeyPairGenerations = availableKeyPairGenerations;
    }

    /**
     * @return the keyPairGenerationChanged
     */
    public boolean isKeyPairGenerationChanged() {
        return keyPairGenerationChanged;
    }

    /**
     * @param keyPairGenerationChanged the keyPairGenerationChanged to set
     */
    public void setKeyPairGenerationChanged(boolean keyPairGenerationChanged) {
        this.keyPairGenerationChanged = keyPairGenerationChanged;
    }

    /**
     * @return the availableCertificateProfiles
     */
    public Map<String, String> getAvailableCertificateProfiles() {
        return availableCertificateProfiles;
    }

    /**
     * @param availableCertificateProfiles the availableCertificateProfiles to set
     */
    public void setAvailableCertificateProfiles(Map<String, String> availableCertificateProfiles) {
        this.availableCertificateProfiles = availableCertificateProfiles;
    }

    /**
     * @return the selectedCertificateProfile
     */
    public String getSelectedCertificateProfile() {
        return selectedCertificateProfile;
    }

    /**
     * @param selectedCertificateProfile the selectedCertificateProfile to set
     */
    public void setSelectedCertificateProfile(String selectedCertificateProfile) {
        this.selectedCertificateProfile = selectedCertificateProfile;
    }

    /**
     * @return the certificateProfileChanged
     */
    public boolean isCertificateProfileChanged() {
        return certificateProfileChanged;
    }

    /**
     * @param certificateProfileChanged the certificateProfileChanged to set
     */
    public void setCertificateProfileChanged(boolean certificateProfileChanged) {
        this.certificateProfileChanged = certificateProfileChanged;
    }

    /**
     * @return the availableAlgorithms
     */
    public Map<String, String> getAvailableAlgorithms() {
        return availableAlgorithms;
    }

    /**
     * @param availableAlgorithms the availableAlgorithms to set
     */
    public void setAvailableAlgorithms(Map<String, String> availableAlgorithms) {
        this.availableAlgorithms = availableAlgorithms;
    }

    /**
     * @return the selectedAlgorithm
     */
    public String getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    /**
     * @param selectedAlgorithm the selectedAlgorithm to set
     */
    public void setSelectedAlgorithm(String selectedAlgorithm) {
        this.selectedAlgorithm = selectedAlgorithm;
    }

    /**
     * @return the algorithmChanged
     */
    public boolean isAlgorithmChanged() {
        return algorithmChanged;
    }

    /**
     * @param algorithmChanged the algorithmChanged to set
     */
    public void setAlgorithmChanged(boolean algorithmChanged) {
        this.algorithmChanged = algorithmChanged;
    }

    /**
     * @return the endEntityInformation
     */
    public EndEntityInformation getEndEntityInformation() {
        return endEntityInformation;
    }

    /**
     * @param endEntityInformation the endEntityInformation to set
     */
    public void setEndEntityInformation(EndEntityInformation endEntityInformation) {
        this.endEntityInformation = endEntityInformation;
    }

    /**
     * @return the confirmPassword
     */
    public String getConfirmPassword() {
        return confirmPassword;
    }

    /**
     * @param confirmPassword the confirmPassword to set
     */
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    /**
     * @return the availableCertificateAuthorities
     */
    public Map<String, String> getAvailableCertificateAuthorities() {
        return availableCertificateAuthorities;
    }

    /**
     * @param availableCertificateAuthorities the availableCertificateAuthorities to set
     */
    public void setAvailableCertificateAuthorities(Map<String, String> availableCertificateAuthorities) {
        this.availableCertificateAuthorities = availableCertificateAuthorities;
    }

    /**
     * @return the selectedCertificateAuthority
     */
    public String getSelectedCertificateAuthority() {
        return selectedCertificateAuthority;
    }

    /**
     * @param selectedCertificateAuthority the selectedCertificateAuthority to set
     */
    public void setSelectedCertificateAuthority(String selectedCertificateAuthority) {
        this.selectedCertificateAuthority = selectedCertificateAuthority;
    }

    /**
     * @return the certificateAuthorityChanged
     */
    public boolean isCertificateAuthorityChanged() {
        return certificateAuthorityChanged;
    }

    /**
     * @param certificateAuthorityChanged the certificateAuthorityChanged to set
     */
    public void setCertificateAuthorityChanged(boolean certificateAuthorityChanged) {
        this.certificateAuthorityChanged = certificateAuthorityChanged;
    }

    public IdNameHashMap<CertificateProfile> getAuthorizedCertificateProfiles() {
        return authorizedCertificateProfiles;
    }

    public void setAuthorizedCertificateProfiles(IdNameHashMap<CertificateProfile> authorizedCertificateProfiles) {
        this.authorizedCertificateProfiles = authorizedCertificateProfiles;
    }

    /**
     * @return the authorizedCAInfos
     */
    public IdNameHashMap<CAInfo> getAuthorizedCAInfos() {
        return authorizedCAInfos;
    }

    /**
     * @param authorizedCAInfos the authorizedCAInfos to set
     */
    public void setAuthorizedCAInfos(IdNameHashMap<CAInfo> authorizedCAInfos) {
        this.authorizedCAInfos = authorizedCAInfos;
    }

    /**
     * @return the availableEndEntityProfiles
     */
    public Map<String, String> getAvailableEndEntityProfiles() {
        return availableEndEntityProfiles;
    }

    /**
     * @param availableEndEntityProfiles the availableEndEntityProfiles to set
     */
    public void setAvailableEndEntityProfiles(Map<String, String> availableEndEntities) {
        this.availableEndEntityProfiles = availableEndEntities;
    }

    /**
     * @return the subjectDN
     */
    public SubjectDn getSubjectDn() {
        return subjectDn;
    }

    /**
     * @param subjectDn the subjectDN to set
     */
    public void setSubjectDn(SubjectDn subjectDn) {
        this.subjectDn = subjectDn;
    }

    /**
     * @return the subjectAlternativeName
     */
    public SubjectAlternativeName getSubjectAlternativeName() {
        return subjectAlternativeName;
    }

    /**
     * @param subjectAlternativeName the subjectAlternativeName to set
     */
    public void setSubjectAlternativeName(SubjectAlternativeName subjectAlternativeName) {
        this.subjectAlternativeName = subjectAlternativeName;
    }

    /**
     * @return the subjectDirectoryAttributes
     */
    public SubjectDirectoryAttributes getSubjectDirectoryAttributes() {
        return subjectDirectoryAttributes;
    }

    /**
     * @param subjectDirectoryAttributes the subjectDirectoryAttributes to set
     */
    public void setSubjectDirectoryAttributes(SubjectDirectoryAttributes subjectDirectoryAttributes) {
        this.subjectDirectoryAttributes = subjectDirectoryAttributes;
    }

    /**
     * @return the certificateDataReady
     */
    public boolean isCertificateDataReady() {
        return certificateDataReady;
    }

    /**
     * @param certificateDataReady the certificateDataReady to set
     */
    public void setCertificateDataReady(boolean certificateDataReady) {
        this.certificateDataReady = certificateDataReady;
    }

    /**
     * @return the availableDownloadCredentials
     */
    public Map<String, DownloadCredentialsType> getAvailableDownloadCredentials() {
        return availableDownloadCredentials;
    }

    /**
     * @param availableDownloadCredentials the availableDownloadCredentials to set
     */
    public void setAvailableDownloadCredentials(Map<String, DownloadCredentialsType> availableDownloadCredentials) {
        this.availableDownloadCredentials = availableDownloadCredentials;
    }

    /**
     * @return the selectedDownloadCredentials
     */
    public String getSelectedDownloadCredentialsType() {
        return selectedDownloadCredentialsType;
    }

    /**
     * @param selectedDownloadCredentialsType the selectedDownloadCredentials to set
     */
    public void setSelectedDownloadCredentialsType(String selectedDownloadCredentialsType) {
        this.selectedDownloadCredentialsType = selectedDownloadCredentialsType;
    }

    /**
     * @return the downloadCredentialsChanged
     */
    public boolean isDownloadCredentialsChanged() {
        return downloadCredentialsChanged;
    }

    /**
     * @param downloadCredentialsChanged the downloadCredentialsChanged to set
     */
    public void setDownloadCredentialsChanged(boolean downloadCredentialsChanged) {
        this.downloadCredentialsChanged = downloadCredentialsChanged;
    }
}
