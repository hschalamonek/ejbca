package se.anatom.ejbca.authorization;

/**
 * A class containing constats used when configuring Basic Access Rule Set 
 *
 * @author  herrvendil 
 * @version $Id: BasicAccessRuleSet.java,v 1.2 2004-02-19 12:16:49 herrvendil Exp $
 */
public class BasicAccessRuleSet implements java.io.Serializable {

	public static final int ROLE_NONE                           = 0;
    public static final int ROLE_SUPERADMINISTRATOR = 1;
    public static final int ROLE_CAADMINISTRATOR       = 2;
    public static final int ROLE_RAADMINISTRATOR       = 3;
    public static final int ROLE_SUPERVISOR                 = 4;
    public static final int ROLE_HARDTOKENISSUER       = 5;

    public static final int ENDENTITY_VIEW                      = 2;
    public static final int ENDENTITY_VIEWHISTORY         = 4;
    public static final int ENDENTITY_VIEWHARDTOKENS  = 8;    
    public static final int ENDENTITY_CREATE                   = 16;    
    public static final int ENDENTITY_EDIT                        = 32;
    public static final int ENDENTITY_DELETE                    = 64;
    public static final int ENDENTITY_REVOKE                   = 128;
    public static final int ENDENTITY_KEYRECOVER           = 256;
    
    public static final int ENDENTITYPROFILE_ALL  = 0;
    
    public static final int CA_ALL  = 0;
    
    public static final int OTHER_VIEWLOG = 1;
    public static final int OTHER_ISSUEHARDTOKENS = 2;
    
    public static final String[]  ROLETEXTS = {"NONE","SUPERADMINISTRATOR","CAADMINISTRATOR",
    		                                                         "RAADMINISTRATOR", "SUPERVISOR",
                                                                     "HARDTOKENISSUER"};
    
    public static final String[]  ENDENTITYRULETEXTS =  {"VIEWENDENTITYRULE","VIEWHISTORYRULE","VIEWHARDTOKENRULE",
    	                                                                                  "CREATEENDENTITYRULE","EDITENDENTITYRULE","DELETEENDENTITYRULE",
																						  "REVOKEENDENTITYRULE", "KEYRECOVERENDENTITYRULE"};
    		
    public static final String[]  OTHERTEXTS = {"","VIEWLOG","ISSUEHARDTOKENS"};
        
   /**
     * This class should not be able to be instantiated.
     */
    private BasicAccessRuleSet(){}
    
    public static String getEndEntityRuleText(int endentityrule){
    	String returnval = "";
    	
    	switch(endentityrule){
    	   case BasicAccessRuleSet.ENDENTITY_VIEW:
    	   	  returnval = ENDENTITYRULETEXTS[0];
    	   	  break;
    	   case BasicAccessRuleSet.ENDENTITY_VIEWHISTORY:
    	   	  returnval = ENDENTITYRULETEXTS[1];
    	   	  break;
    	   case BasicAccessRuleSet.ENDENTITY_VIEWHARDTOKENS:
    	      returnval = ENDENTITYRULETEXTS[2];
    	      break;
    	   case BasicAccessRuleSet.ENDENTITY_CREATE:
    	   	  returnval = ENDENTITYRULETEXTS[3];
    	   	  break;
    	   case BasicAccessRuleSet.ENDENTITY_EDIT:
    	   	  returnval = ENDENTITYRULETEXTS[4];
    	   	  break;
    	   case BasicAccessRuleSet.ENDENTITY_DELETE:
    	   	returnval = ENDENTITYRULETEXTS[5];
    	   	break;
    	   case BasicAccessRuleSet.ENDENTITY_REVOKE:
    	   	  returnval = ENDENTITYRULETEXTS[6];
    	   	  break;
    	   case BasicAccessRuleSet.ENDENTITY_KEYRECOVER:
    	   	returnval = ENDENTITYRULETEXTS[7];
    	   	break;
    	}
    	return returnval;
    }
    
   
}
