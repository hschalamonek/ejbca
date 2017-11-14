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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.era.IdNameHashMap;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.core.model.era.KeyToValueHolder;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.ra.RaEndEntityDetails.Callbacks;

/**
 * Backing bean for certificate details view.
 *  
 * @version $Id$
 */
@ManagedBean
@ViewScoped
public class RaEndEntityBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private RaMasterApiProxyBeanLocal raMasterApiProxyBean;

    @ManagedProperty(value="#{raAuthenticationBean}")
    private RaAuthenticationBean raAuthenticationBean;
    public void setRaAuthenticationBean(final RaAuthenticationBean raAuthenticationBean) { this.raAuthenticationBean = raAuthenticationBean; }

    @ManagedProperty(value="#{raLocaleBean}")
    private RaLocaleBean raLocaleBean;
    public void setRaLocaleBean(final RaLocaleBean raLocaleBean) { this.raLocaleBean = raLocaleBean; }

    private String username = null;
    private RaEndEntityDetails raEndEntityDetails = null;
    private Map<Integer, String> eepIdToNameMap = null;
    private Map<Integer, String> cpIdToNameMap = null;
    private Map<Integer,String> caIdToNameMap = new HashMap<>();
    private boolean editEditEndEntityMode = false;
    private List<RaCertificateDetails> issuedCerts = null;

    private final Callbacks raEndEntityDetailsCallbacks = new RaEndEntityDetails.Callbacks() {
        @Override
        public RaLocaleBean getRaLocaleBean() {
            return raLocaleBean;
        }

        @Override
        public EndEntityProfile getEndEntityProfile(int eepId) {
            IdNameHashMap<EndEntityProfile> map = raMasterApiProxyBean.getAuthorizedEndEntityProfiles(raAuthenticationBean.getAuthenticationToken(), AccessRulesConstants.VIEW_END_ENTITY);
            KeyToValueHolder<EndEntityProfile> tuple = map.get(eepId);
            return tuple==null ? null : tuple.getValue();
        }
    };

    @PostConstruct
    public void postConstruct() {
        username = ((HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest()).getParameter("ee");
        reload();
    }
    
    private void reload() {
        if (username!=null) {
            final EndEntityInformation endEntityInformation = raMasterApiProxyBean.searchUser(raAuthenticationBean.getAuthenticationToken(), username);
            if (endEntityInformation!=null) {
                cpIdToNameMap = raMasterApiProxyBean.getAuthorizedCertificateProfileIdsToNameMap(raAuthenticationBean.getAuthenticationToken());
                eepIdToNameMap = raMasterApiProxyBean.getAuthorizedEndEntityProfileIdsToNameMap(raAuthenticationBean.getAuthenticationToken());
                final List<CAInfo> caInfos = new ArrayList<>(raMasterApiProxyBean.getAuthorizedCas(raAuthenticationBean.getAuthenticationToken()));
                for (final CAInfo caInfo : caInfos) {
                    caIdToNameMap.put(caInfo.getCAId(), caInfo.getName());
                }
                raEndEntityDetails = new RaEndEntityDetails(endEntityInformation, raEndEntityDetailsCallbacks, cpIdToNameMap, eepIdToNameMap, caIdToNameMap);
            }
        }
        issuedCerts = null;
    }

    public String getUsername() { return username; }
    public RaEndEntityDetails getEndEntity() { return raEndEntityDetails; }

    public boolean isEditEditEndEntityMode() {
        return editEditEndEntityMode;
    }
    public void editEditEndEntity() {
        editEditEndEntityMode = true;
    }
    public void editEditEndEntityCancel() {
        reload();
        editEditEndEntityMode = false;
    }
    public void editEditEndEntitySave() {
        editEditEndEntityMode = false;
    }

    /**
     * @return a list of the End Entity's certificates
     */
    public List<RaCertificateDetails> getIssuedCerts() {
        if (issuedCerts == null) {
            issuedCerts = RaEndEntityTools.searchCertsByUsernameSorted(
                    raMasterApiProxyBean, raAuthenticationBean.getAuthenticationToken(),
                    username, raLocaleBean);
        }
        return issuedCerts;
    }
}
