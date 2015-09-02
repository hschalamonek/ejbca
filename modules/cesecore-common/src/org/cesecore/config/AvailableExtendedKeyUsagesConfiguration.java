/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.cesecore.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.configuration.ConfigurationBase;

public class AvailableExtendedKeyUsagesConfiguration extends ConfigurationBase implements Serializable{

    private static final Logger log = Logger.getLogger(AvailableExtendedKeyUsagesConfiguration.class);
    
    private static final long serialVersionUID = -3430732247486886608L;
    public static final String AVAILABLE_EXTENDED_KEY_USAGES_CONFIGURATION_ID = "AVAILABLE_EXTEENDED_KEY_USAGES";
    
    /** Creates a new instance of AvailableExtendedKeyUsagesConfiguration */
    public AvailableExtendedKeyUsagesConfiguration()  {
       super();
    }
    
    public AvailableExtendedKeyUsagesConfiguration(Serializable dataobj) {
        @SuppressWarnings("unchecked")
        LinkedHashMap<Object, Object> d = (LinkedHashMap<Object, Object>) dataobj;
        data = d;
    }
    
    @Override
    public String getConfigurationId() {
        return AVAILABLE_EXTENDED_KEY_USAGES_CONFIGURATION_ID;
    }
    
    /**
     * @return true if there is at least one supported ExtendedKeyUsage. False otherwize 
     */
    public boolean isConfigurationInitialized() {
        return data.size() > 1;
    }
    
    public void addExtKeyUsage(String oid, String name) {
        data.put(oid, name);
    }
    
    public void removeExtKeyUsage(String oid) {
        data.remove(oid);
    }
    
    public String getExtKeyUsageName(String oid) {
        return (String) data.get(oid);
    }
    
    public List<String> getAllOIDs() {
        Set<Object> keyset = data.keySet();
        ArrayList<String> keys = new ArrayList<String>();
        for(Object k : keyset) {
            if(!StringUtils.equalsIgnoreCase((String) k, "version")) {
                keys.add( (String) k );
            }
        }
        return keys;
    }
    
    public Map<String, String> getAllEKUOidsAndNames() {
        Map<String, String> ret = (Map<String, String>) saveData();
        ret.remove("version");
        return ret;
    }
    
    public Properties getAsProperties() {
        Properties properties = new Properties();
        Map<String, String> allEkus = getAllEKUOidsAndNames();
        for(Entry<String, String> eku : allEkus.entrySet()) {
            properties.setProperty(eku.getKey(), eku.getValue());
        }
        return properties;
    }
    
    @Override
    public void upgrade() {}
    
}
