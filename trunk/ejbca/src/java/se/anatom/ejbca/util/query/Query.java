/*
 * Query.java
 *
 * Created on den 23 juli 2002, 01:24
 */

package se.anatom.ejbca.util.query;
import java.util.Vector;
import java.util.Iterator;
import java.util.Date;

/**
 * A class used to produce advanced querys from the log and user data tables. It's main function is getQueryString which returns a
 * string which should be placed in the 'WHERE' clause of a SQL statement. 
 *
 * 
 * @author  tomselleck
 */
public class Query implements java.io.Serializable {
    // Public Constants.
    public static final int TYPE_LOGQUERY    = 0;
    public static final int TYPE_USERQUERY   = 1;
    
    public static final int CONNECTOR_AND    = 0;
    public static final int CONNECTOR_OR     = 1;
    public static final int CONNECTOR_ANDNOT = 2;
    public static final int CONNECTOR_ORNOT  = 3;
    
    // Public methods.
    /** Creates a new instance of Query 
     * 
     * @param type is the typ of query to produce. Should be one of the 'TYPE' constants of this class.
     */
    public Query(int type) {
      matches = new Vector();
      connectors = new Vector();
      this.type=type;
    }
    
    /** Adds a time restraint to the query. Both parameter cannot be null
     *  This method should only be used in ra user queries.     
     *
     * @param startdate gives the start date of the query or null if it no startdate.
     * @param enddate gives the end date of the query or null if it no startdate.
     */
    public void add(Date startdate, Date enddate) {
      matches.addElement(new TimeMatch(type,startdate,enddate));  
    }
 
    /** Adds a time restraint to the query. Both start and enddate parameters cannot be null
     *  This method should only be used in ra user queries.
     *
     * @param matchwith should indicate which field to match with, on of the TimeMatch.MATCH_WITH constants.
     * @param startdate gives the start date of the query or null if it no startdate.
     * @param enddate gives the end date of the query or null if it no startdate.
     */
    public void add(int matchwith, Date startdate, Date enddate) {
      matches.addElement(new TimeMatch(type,matchwith,startdate,enddate));  
    }
    
    /** Adds a time restraint and a connector to the query. Both parameter cannot be null.
     *  This method should only be used in log queries.     
     *
     * @param startdate gives the start date of the query or null if it no startdate.
     * @param enddate gives the end date of the query or null if it no startdate.
     * @param connector should be one of the 'CONNECTOR' constants.
     */    
    public void add(Date startdate, Date enddate, int connector) {
       matches.addElement(new TimeMatch(type,startdate,enddate));    
       connectors.addElement(new Integer(connector));
    }
    
     /** Adds a time restraint and a connector to the query. Both start and enddate parameters cannot be null.
     *  This method should only be used in ra user queries.
     *  
     * @param matchwith should indicate which field to match with, on of the TimeMatch.MATCH_WITH constants.
     * @param startdate gives the start date of the query or null if it no startdate.
     * @param enddate gives the end date of the query or null if it no startdate.
     * @param connector should be one of the 'CONNECTOR' constants.
     */    
    public void add(int matchwith, Date startdate, Date enddate, int connector) {
       matches.addElement(new TimeMatch(type,matchwith,startdate,enddate));    
       connectors.addElement(new Integer(connector));
    }
    
    /** Adds a match ot type UserMatch or LogMatch to the query. 
     *
     * @param matchwith should be one of the the UserMatch.MATCH_WITH or LogMatch.MATCH_WITH connstants depending on query type.
     * @param matchtype should be one of BasicMatch.MATCH_TYPE constants.
     * @param matchvalue should be a string representation to match against.
     *
     * @throws NumberFormatException if there is an illegal character in matchvalue string.
     */        
    public void add(int matchwith, int matchtype, String matchvalue) throws NumberFormatException {
      switch(this.type){
          case TYPE_LOGQUERY :
             // TODO 
            break;
          case TYPE_USERQUERY :
             matches.addElement(new UserMatch(matchwith,matchtype,matchvalue)); 
            break;  
      }
    }

    /** Adds a match ot type UserMatch or LogMatch ant a connector to the query. 
     *
     * @param matchwith should be one of the the UserMatch.MATCH_WITH or LogMatch.MATCH_WITH connstants depending on query type.
     * @param matchtype should be one of BasicMatch.MATCH_TYPE constants.
     * @param matchvalue should be a string representation to match against.
     * @param connector should be one of the 'CONNECTOR' constants.
     *
     * @throws NumberFormatException if there is an illegal character in matchvalue string.
     */        
    public void add(int matchwith, int matchtype, String matchvalue, int connector) throws NumberFormatException {
       switch(this.type){
          case TYPE_LOGQUERY :
             // TODO 
            break;
          case TYPE_USERQUERY :
             matches.addElement(new UserMatch(matchwith,matchtype,matchvalue)); 
            break;  
      }
      connectors.addElement(new Integer(connector));       
    }

    /** 
     * Adds a connector to the query.
     * @param connector should be one of the 'CONNECTOR' constants.
     *
     * @throws NumberFormatException if there is an illegal character in matchvalue string.
     */            
    public void add(int connector) {
      connectors.addElement(new Integer(connector));        
    }
    
    /**
     * Gives the string to be used in the 'WHERE' clause int the SQL-statement.
     *
     * @return the string to be used in the 'WHERE'-clause.
     */
    public String getQueryString() {
      String returnval = "";
      
      for(int i=0; i < matches.size()-1;i++){
         returnval += ((BasicMatch) matches.elementAt(i)).getQueryString();
         returnval += CONNECTOR_SQL_NAMES[((Integer) connectors.elementAt(i)).intValue()];
      }
      returnval += ((BasicMatch) matches.elementAt(matches.size()-1)).getQueryString();
      
      return returnval;
    }
    
    /** 
     * Checks if the present query is legal by checking if every match is legal and that
     * the number of connectors is one less than matches.
     */
    public boolean isLegalQuery() {
      boolean returnval = true;  
      Iterator i = matches.iterator();
      while(i.hasNext()){
        returnval = returnval && ((BasicMatch) i.next()).isLegalQuery();   
      }
      
      returnval = returnval && (matches.size() -1 == connectors.size());
      
      returnval = returnval && (matches.size() > 0 );
      
      return returnval;
    }
    
    // Private Constants.
    final static String[] CONNECTOR_SQL_NAMES = {" AND "," OR "," AND NOT "," OR NOT "};
    
    // Private fields.
    private Vector matches = null; // Should only contain BasicMatch objects.
    private Vector connectors = null; // Should only containg CONNECTOR constants.
    private int type = 0;
    
}
