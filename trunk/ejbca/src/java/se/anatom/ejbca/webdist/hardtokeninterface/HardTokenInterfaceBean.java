package se.anatom.ejbca.webdist.hardtokeninterface;

import javax.naming.*;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Iterator;
import java.math.BigInteger;

import java.security.cert.X509Certificate;
import javax.servlet.http.HttpServletRequest;

import se.anatom.ejbca.log.*;
import se.anatom.ejbca.hardtoken.*;
import se.anatom.ejbca.authorization.AdminInformation;
import se.anatom.ejbca.util.StringTools;
import se.anatom.ejbca.util.CertTools;
import se.anatom.ejbca.webdist.webconfiguration.EjbcaWebBean;
import se.anatom.ejbca.webdist.webconfiguration.InformationMemory;

/**
 * A java bean handling the interface between EJBCA hard token module and JSP pages.
 *
 * @author  Philip Vendil
 * @version $Id: LogInterfaceBean.java,v 1.13 2002/08/28 12:22:25 herrvendil Exp $
 */
public class HardTokenInterfaceBean {

    /** Creates new LogInterfaceBean */
    public HardTokenInterfaceBean(){
    }
    // Public methods.
    /**
     * Method that initialized the bean.
     *
     * @param request is a reference to the http request.
     */
    public void initialize(HttpServletRequest request, EjbcaWebBean ejbcawebbean) throws  Exception{

      if(!initialized){
        admininformation = new AdminInformation(((X509Certificate[]) request.getAttribute( "javax.servlet.request.X509Certificate" ))[0]);
        admin           = new Admin(((X509Certificate[]) request.getAttribute( "javax.servlet.request.X509Certificate" ))[0]);

        InitialContext jndicontext = new InitialContext();
        IHardTokenSessionLocalHome hardtokensessionhome = (IHardTokenSessionLocalHome) javax.rmi.PortableRemoteObject.narrow(jndicontext.lookup("java:comp/env/HardTokenSessionLocal"),
                                                                                 IHardTokenSessionLocalHome.class);
        hardtokensession = hardtokensessionhome.create();

        IHardTokenBatchJobSessionLocalHome  hardtokenbatchsessionhome = (IHardTokenBatchJobSessionLocalHome) javax.rmi.PortableRemoteObject.narrow(jndicontext.lookup("java:comp/env/HardTokenBatchJobSessionLocal"),
                                                                                 IHardTokenBatchJobSessionLocalHome.class);
        hardtokenbatchsession = hardtokenbatchsessionhome.create();

        availablehardtokens = hardtokensession.getAvailableHardTokens();
        initialized=true;
        
        this.informationmemory = ejbcawebbean.getInformationMemory();

      }
    }

    /* Returns the first found hard token for the given username. */
    public HardTokenView getHardTokenViewWithUsername(String username) throws RemoteException{
      HardTokenView  returnval = null;

      this.result=null;

      Collection res = hardtokensession.getHardTokens(admin, username);
      Iterator iter = res.iterator();

      if(res.size() > 0){
        this.result = new HardTokenView[res.size()];
        for(int i=0;iter.hasNext();i++){
          this.result[i]=new HardTokenView(availablehardtokens, (HardTokenData) iter.next());
        }
      }
      else
        this.result = null;



      if(this.result!= null && this.result.length > 0)
        return this.result[0];
      else
        return null;

    }

    public HardTokenView getHardTokenViewWithIndex(String username, int index) throws RemoteException{
      HardTokenView returnval=null;

      if(result == null)
        getHardTokenViewWithUsername(username);

      if(result!=null)
        if(index < result.length)
          returnval=result[index];

      return returnval;
    }

    public int getHardTokensInCache() {
      int returnval = 0;
      if(result!=null)
        returnval = result.length;

      return returnval;
    }

    public HardTokenView getHardTokenView(String tokensn) throws RemoteException{
      HardTokenView  returnval = null;
      this.result=null;
      HardTokenData token =  hardtokensession.getHardToken(admin, tokensn);
      if(token != null)
        returnval = new  HardTokenView(availablehardtokens, token);

      return returnval;
    }

    public Collection getHardTokenIssuerDatas() throws RemoteException{
      return hardtokensession.getHardTokenIssuerDatas(admin);
    }

    public TreeMap getHardTokenIssuers() throws RemoteException{
      return hardtokensession.getHardTokenIssuers(admin);
    }

    public String[] getHardTokenIssuerAliases() throws RemoteException{
      return (String[]) hardtokensession.getHardTokenIssuers(admin).keySet().toArray(new String[0]);
    }

    /** Returns the alias from id. */
    public String getHardTokenIssuerAlias(int id) throws RemoteException{
      return hardtokensession.getHardTokenIssuerAlias(admin, id);
    }

    public int getHardTokenIssuerId(String alias) throws RemoteException{
      return hardtokensession.getHardTokenIssuerId(admin, alias);
    }

    public HardTokenIssuerData getHardTokenIssuerData(String alias) throws RemoteException{
      return hardtokensession.getHardTokenIssuerData(admin, alias);
    }

    public HardTokenIssuerData getHardTokenIssuerData(int id) throws RemoteException{
      return hardtokensession.getHardTokenIssuerData(admin, id);
    }

    public void addHardTokenIssuer(String alias, String certificatesn, String certissuerdn) throws HardTokenIssuerExistsException, RemoteException{
      if(this.informationmemory.getAuthorizedCAIds().contains(new Integer((CertTools.stringToBCDNString(certissuerdn)).hashCode()))){
        certificatesn = StringTools.stripWhitespace(certificatesn);      
        if(!hardtokensession.addHardTokenIssuer(admin, alias, new BigInteger(certificatesn,16), certissuerdn, new HardTokenIssuer()))
          throw new HardTokenIssuerExistsException();
        informationmemory.hardTokenIssuersEdited();
      }  
    }

    public void addHardTokenIssuer(String alias, String certificatesn, String certissuerdn, HardTokenIssuer hardtokenissuer) throws HardTokenIssuerExistsException, RemoteException {
      if(this.informationmemory.getAuthorizedCAIds().contains(new Integer((CertTools.stringToBCDNString(certissuerdn)).hashCode()))){	
        certificatesn = StringTools.stripWhitespace(certificatesn);
        if(!hardtokensession.addHardTokenIssuer(admin, alias, new BigInteger(certificatesn,16), certissuerdn, hardtokenissuer))
          throw new HardTokenIssuerExistsException();
        informationmemory.hardTokenIssuersEdited();
      }
    }

    public void changeHardTokenIssuer(String alias, HardTokenIssuer hardtokenissuer) throws HardTokenIssuerDoesntExistsException, RemoteException{
      if(informationmemory.authorizedToHardTokenIssuer(alias)){	          	
        if(!hardtokensession.changeHardTokenIssuer(admin, alias, hardtokenissuer))
          throw new HardTokenIssuerDoesntExistsException();
        informationmemory.hardTokenIssuersEdited();
      }
    }

    /* Returns false if profile is used by any user or in authorization rules. */
    public boolean removeHardTokenIssuer(String alias)throws RemoteException{		
        boolean issuerused = false;
		if(informationmemory.authorizedToHardTokenIssuer(alias)){
          int issuerid = hardtokensession.getHardTokenIssuerId(admin, alias);
        // Check if any users or authorization rule use the profile.

          issuerused = hardtokenbatchsession.checkForHardTokenIssuerId(admin, issuerid);

          if(!issuerused){
            hardtokensession.removeHardTokenIssuer(admin, alias);
		    informationmemory.hardTokenIssuersEdited();
          }		
		} 
        return !issuerused;	
    }

    public void renameHardTokenIssuer(String oldalias, String newalias, String newcertificatesn, String certissuersn) throws HardTokenIssuerExistsException, RemoteException{
      if(informationmemory.authorizedToHardTokenIssuer(oldalias)){	
        newcertificatesn = StringTools.stripWhitespace(newcertificatesn);
        if(!hardtokensession.renameHardTokenIssuer(admin, oldalias, newalias, new BigInteger(newcertificatesn,16), certissuersn))
         throw new HardTokenIssuerExistsException();
       
         informationmemory.hardTokenIssuersEdited();
      }   
    }

    public void cloneHardTokenIssuer(String oldalias, String newalias, String newcertificatesn, String newcertissuerdn) throws HardTokenIssuerExistsException, RemoteException{
	  if(informationmemory.authorizedToHardTokenIssuer(oldalias)){    	
        newcertificatesn = StringTools.stripWhitespace(newcertificatesn);
        if(!hardtokensession.cloneHardTokenIssuer(admin, oldalias, newalias, new BigInteger(newcertificatesn,16), newcertissuerdn))
          throw new HardTokenIssuerExistsException();
        
        informationmemory.hardTokenIssuersEdited();
	  }
    }

    public AvailableHardToken[] getAvailableHardTokens(){
      return availablehardtokens;
    }
    // Private fields.
    private IHardTokenSessionLocal          hardtokensession;
    private IHardTokenBatchJobSessionLocal  hardtokenbatchsession;
    private AvailableHardToken[]            availablehardtokens;
    private AdminInformation                admininformation;
    private Admin                           admin;
    private InformationMemory      informationmemory;
    private boolean                         initialized=false;
    private HardTokenView[]                 result;
}
