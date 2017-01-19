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
package org.cesecore.roles.member;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.util.ProfileID;

/**
 * @see RoleMemberSessionLocal
 * 
 * @version $Id$
 *
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "RoleMemberSessionLocal")

@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class RoleMemberSessionBean implements RoleMemberSessionLocal {

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;

    @Override
    public int createOrEdit(RoleMemberData roleMember) {
        if (roleMember.getPrimaryKey() == null) {
            roleMember.setPrimaryKey(findFreePrimaryKey());
            entityManager.persist(roleMember);
        } else {
            entityManager.merge(roleMember);
        }
        return roleMember.getPrimaryKey();

    }
    
    private int findFreePrimaryKey() {
        final ProfileID.DB db = new ProfileID.DB() {
            @Override
            public boolean isFree(int i) {
                return find(i) == null;
            }
        };
        return ProfileID.getNotUsedID(db);
    }



    @Override
    public RoleMemberData find(final int primaryKey) {
        return entityManager.find(RoleMemberData.class, primaryKey);
    }


    @Override
    public void remove(final RoleMemberData roleMember) {
        entityManager.remove(roleMember);
    }

    @Override
    public void remove(final int primaryKey) {
        entityManager.remove(find(primaryKey));
    }

}
