<%@ page pageEncoding="ISO-8859-1"%>
<%@ page contentType="text/html; charset=@page.encoding@" %>
<%@ page language="java" import="javax.naming.*,javax.rmi.*,java.util.*,java.net.*,java.security.cert.*,java.math.BigInteger,org.ejbca.core.ejb.ca.store.*,org.bouncycastle.util.encoders.Hex, org.ejbca.core.model.log.Admin,org.ejbca.ui.web.RequestHelper"%>
<%@ include file="header.jsp" %>
<%
  RequestHelper.setDefaultCharacterEncoding(request);
%>
<h1>Certificates for <%=request.getParameter("subject")%></h1> 
  <%
try  {
    String dn=request.getParameter("subject");
    if (dn == null) {
%>
  <p>Usage: listcerts.jsp?subject=<DN> 
  <%
    } else {
        InitialContext ctx = new InitialContext();
        ICertificateStoreSessionHome home = (ICertificateStoreSessionHome) PortableRemoteObject.narrow(
        ctx.lookup("CertificateStoreSession"), ICertificateStoreSessionHome.class );
        ICertificateStoreSessionRemote store = home.create();
        Collection certs = store.findCertificatesBySubject(new Admin(Admin.TYPE_PUBLIC_WEB_USER, request.getRemoteAddr()), dn);
        Iterator i = certs.iterator();
        while (i.hasNext()) {
            X509Certificate x509cert = (X509Certificate)i.next();
            Date notBefore = x509cert.getNotBefore();
            Date notAfter = x509cert.getNotAfter();
            String subject = x509cert.getSubjectDN().toString();
            String issuer = x509cert.getIssuerDN().toString();
            BigInteger serno = x509cert.getSerialNumber();
            String hexSerno = new String(Hex.encode(serno.toByteArray()));
            String urlEncIssuer = URLEncoder.encode(issuer);
%>
  <pre>Subject: <%=subject%>
Issuer: <%=issuer%>
NotBefore: <%=notBefore.toString()%>
NotAfter: <%=notAfter.toString()%>
Serial number: <%=hexSerno%>
</pre>
  <a href="check_status_result.jsp?issuer=<%=urlEncIssuer%>&serno=<%=hexSerno%>">Check if 
  certificate is revoked</a> 
  <%
        }
        if (certs.isEmpty()) {
%>
  <p>No certificates exists for '<%=dn%>'. 
  <%
        }
    }
} catch(Exception ex) {
    ex.printStackTrace();
}                                             
%>
</div>
<%@ include file="footer.inc" %>
