<%@page contentType="text/html"%>
<%@page errorPage="/errorpage.jsp"  import="se.anatom.ejbca.webdist.webconfiguration.EjbcaWebBean,se.anatom.ejbca.webdist.webconfiguration.GlobalConfiguration, 
                se.anatom.ejbca.webdist.webconfiguration.UserPreference, se.anatom.ejbca.webdist.webconfiguration.GlobalConfigurationDataHandler,
                se.anatom.ejbca.webdist.webconfiguration.WebLanguages"%>

<jsp:useBean id="ejbcawebbean" scope="session" class="se.anatom.ejbca.webdist.webconfiguration.EjbcaWebBean" />
<jsp:setProperty name="ejbcawebbean" property="*" /> 

<%! // Declarations 

  static final String ACTION                                 = "action";
  static final String ACTION_NEXT_DEFAULT_PREFERENCES        = "actionnextdefaultpreferences";
  static final String ACTION_SAVE                            = "actionsave";
  static final String ACTION_CANCEL                          = "actioncancel";


  static final String BUTTON_NEXT                            = "buttonnext"; 
  static final String BUTTON_PREVIOUS                        = "buttonprevious"; 
  static final String BUTTON_SAVE                            = "buttonsave";
  static final String BUTTON_CANCEL                          = "buttoncancel";

// Textfields used in webconfiguration.jsp
  static final String TEXTFIELD_TITLE                        = "textfieldtitle";
  static final String TEXTFIELD_HEADBANNER                   = "textfieldheadbanner";
  static final String TEXTFIELD_FOOTBANNER                   = "textfieldfootbanner";
  static final String TEXTFIELD_OPENDIRECTORIES              = "textfieldopendirectories";

// Lists used in defaultuserprefereces.jsp
  static final String LIST_PREFEREDLANGUAGE                  = "listpreferedlanguage";
  static final String LIST_SECONDARYLANGUAGE                 = "listsecondarylanguage";
  static final String LIST_THEME                             = "listtheme";
  static final String LIST_ENTIESPERPAGE                     = "listentriesperpage";

%> 
<% 
  // Initialize environment.
  final String THIS_FILENAME                          =  "configuration.jsp";

  GlobalConfiguration globalconfiguration = ejbcawebbean.initialize(request); 

  String forwardurl = "/" + globalconfiguration .getMainFilename(); 

    // Determine action 
  if( request.getParameter(BUTTON_CANCEL) != null){
       // Cancel current values and go back to old ones.
       ejbcawebbean.reloadGlobalConfiguration ();
      
%> 
 <jsp:forward page="<%= forwardurl %>"/>
<%  }
    if( request.getParameter(BUTTON_PREVIOUS) != null){
      // Return to Webconfiguration
      // Temporatly save preivous settings
        UserPreference dup = ejbcawebbean.getGlobalConfiguration().getRealDefaultPreference();
        if(request.getParameter(LIST_PREFEREDLANGUAGE) != null){
          String preferedlanguage = request.getParameter(LIST_PREFEREDLANGUAGE); 
          dup.setPreferedLanguage(preferedlanguage.trim());
        }
        if(request.getParameter(LIST_SECONDARYLANGUAGE) != null){
          String secondarylanguage = request.getParameter(LIST_SECONDARYLANGUAGE); 
          dup.setSecondaryLanguage(secondarylanguage.trim());
        }
        if(request.getParameter(LIST_THEME) != null){
          String theme = request.getParameter(LIST_THEME); 
          dup.setTheme(theme.trim());
        }
        if(request.getParameter(LIST_ENTIESPERPAGE) != null){
          String entriesperpage = request.getParameter(LIST_ENTIESPERPAGE); 
          dup.setEntriesPerPage(Integer.parseInt(entriesperpage.trim()));
        }

%>
       <%@ include file="webconfiguration.jsp" %>
<%  }

    if( request.getParameter(BUTTON_NEXT) != null){
       // Change global configuration and proceed with default user preferences.
      GlobalConfiguration gc = ejbcawebbean.getGlobalConfiguration();
       if(request.getParameter(TEXTFIELD_TITLE) != null){
         String title = request.getParameter(TEXTFIELD_TITLE); 
         gc.setEjbcaTitle(title);
       }
       if(request.getParameter(TEXTFIELD_HEADBANNER) != null){
         String headbanner = request.getParameter(TEXTFIELD_HEADBANNER); 
         gc.setHeadBanner(headbanner);
       }
       if(request.getParameter(TEXTFIELD_FOOTBANNER) != null){
         String footbanner = request.getParameter(TEXTFIELD_FOOTBANNER); 
         gc.setFootBanner(footbanner);
       }
       if(request.getParameter(TEXTFIELD_OPENDIRECTORIES) != null){
         String opendirectories = request.getParameter(TEXTFIELD_OPENDIRECTORIES); 
         gc.setOpenDirectories(opendirectories);
       }

%>  
           <%@ include file="defaultuserpreferences.jsp" %>
<%  }
     if( request.getParameter(BUTTON_SAVE) != null){
        // Save global configuration.
        UserPreference dup = ejbcawebbean.getGlobalConfiguration().getRealDefaultPreference();
        if(request.getParameter(LIST_PREFEREDLANGUAGE) != null){
          String preferedlanguage = request.getParameter(LIST_PREFEREDLANGUAGE); 
          dup.setPreferedLanguage(preferedlanguage.trim());
        }
        if(request.getParameter(LIST_SECONDARYLANGUAGE) != null){
          String secondarylanguage = request.getParameter(LIST_SECONDARYLANGUAGE); 
          dup.setSecondaryLanguage(secondarylanguage.trim());
        }
        if(request.getParameter(LIST_THEME) != null){
          String theme = request.getParameter(LIST_THEME); 
          dup.setTheme(theme.trim());
        }
        if(request.getParameter(LIST_ENTIESPERPAGE) != null){
          String entriesperpage = request.getParameter(LIST_ENTIESPERPAGE); 
          dup.setEntriesPerPage(Integer.parseInt(entriesperpage.trim()));
        }
        ejbcawebbean.saveGlobalConfiguration();
%>          
 <jsp:forward page="<%=forwardurl %>"/>
<%   }
     if(request.getParameter(BUTTON_SAVE) == null &&
        request.getParameter(BUTTON_NEXT) == null &&
        request.getParameter(BUTTON_CANCEL) == null &&
        request.getParameter(BUTTON_PREVIOUS) == null){
 
      // get current global configuration.
        ejbcawebbean.reloadGlobalConfiguration();
%>
           <%@ include file="webconfiguration.jsp" %>
<%  }  %>




