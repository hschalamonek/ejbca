package se.anatom.ejbca.ra.authorization;

import java.util.Vector;
import java.util.Collections;
import java.io.Serializable;

/**
 * A class that represents a group of users and their access rules.
 *
 * @version $Id: UserGroup.java,v 1.4 2002-09-12 18:14:14 herrvendil Exp $
 */
public class UserGroup implements Serializable {

    public static final String SPECIALUSERGROUP_PUBLICWEBUSER         = "SPECIAL_PUBLIC_WEBUSER";
    public static final String SPECIALUSERGROUP_CACOMMANDLINEADMIN    = "SPECIAL_CA_COMMANDLINEADMIN";
    public static final String SPECIALUSERGROUP_RACOMMANDLINEADMIN    = "SPECIAL_RA_COMMANDLINEADMIN";
    public static final String SPECIALUSERGROUP_BATCHCOMMANDLINEADMIN = "SPECIAL_BATCH_COMMANDLINEADMIN";
    
    /** Creates a new instance of UserGroup */
    public UserGroup() {
      accessrules = new Vector();
      userentities = new Vector();
    }

    public UserGroup(Vector accessrules, Vector userentities){
      this.accessrules=accessrules;
      this.userentities=userentities;
    }

    // Public methods
    /** Returns the number of accessrules applied to this usergroup */
    public int getNumberOfAccessRules() {
      return accessrules.size();
    }

    /** Returns an array containing all the usergroup's accessrules.*/
    public AccessRule[] getAccessRules() {
      AccessRule[] dummy={};
      return (AccessRule[]) accessrules.toArray(dummy);
    }

    /** Returns the number of user entities in this usergroup */
    public int getNumberUserEntities() {
      return userentities.size();
    }

    /** Returns an array containing all the usergroup's user entities.*/
    public UserEntity[] getUserEntities() {
      UserEntity[] dummy = {};
      return (UserEntity[]) userentities.toArray(dummy);
    }

    /** Method that given an array of available directories returns which isn't already
     * in use by the rule set. */
    public String[] nonUsedDirectories(String[] availabledirectories){
      Vector nonuseddirectories = new Vector();
      String[] dummy = {};
      int result;
      Collections.sort(accessrules);
      if(availabledirectories != null){
        for( int i=0; i < availabledirectories.length;i++){
          result=java.util.Collections.binarySearch(accessrules,new AccessRule(availabledirectories[i],0,false));
          if(result < 0){
            // Directory isn't in use.
            nonuseddirectories.addElement(availabledirectories[i]);
          }
        }
      }
      return  (String[]) nonuseddirectories.toArray(dummy);
    }
    // Private methods

    // Private fields
    private Vector accessrules;
    private Vector userentities;
 }
