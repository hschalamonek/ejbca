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
package org.ejbca.core.model.era;

import java.math.BigInteger;
import java.security.KeyStoreException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cesecore.audit.enums.EventType;
import org.cesecore.authentication.AuthenticationFailedException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.access.AccessSet;
import org.cesecore.certificates.ca.ApprovalRequestType;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CAOfflineException;
import org.cesecore.certificates.ca.IllegalNameException;
import org.cesecore.certificates.ca.IllegalValidityException;
import org.cesecore.certificates.ca.InvalidAlgorithmException;
import org.cesecore.certificates.ca.SignRequestException;
import org.cesecore.certificates.ca.SignRequestSignatureException;
import org.cesecore.certificates.certificate.CertificateCreateException;
import org.cesecore.certificates.certificate.CertificateDataWrapper;
import org.cesecore.certificates.certificate.CertificateRevokeException;
import org.cesecore.certificates.certificate.CertificateStatus;
import org.cesecore.certificates.certificate.CertificateStoreSession;
import org.cesecore.certificates.certificate.CertificateWrapper;
import org.cesecore.certificates.certificate.IllegalKeyException;
import org.cesecore.certificates.certificate.certextensions.CertificateExtensionException;
import org.cesecore.certificates.certificate.exception.CertificateSerialNumberException;
import org.cesecore.certificates.certificate.exception.CustomCertificateSerialNumberException;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileDoesNotExistException;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.config.RaStyleInfo;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.roles.Role;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.member.RoleMember;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ca.auth.EndEntityAuthenticationSessionLocal;
import org.ejbca.core.ejb.dto.CertRevocationDto;
import org.ejbca.core.ejb.ra.CouldNotRemoveEndEntityException;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionLocal;
import org.ejbca.core.ejb.ra.NoSuchEndEntityException;
import org.ejbca.core.model.approval.AdminAlreadyApprovedRequestException;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.ApprovalRequestExecutionException;
import org.ejbca.core.model.approval.ApprovalRequestExpiredException;
import org.ejbca.core.model.approval.SelfApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.approval.profile.ApprovalProfile;
import org.ejbca.core.model.ca.AuthLoginException;
import org.ejbca.core.model.ca.AuthStatusException;
import org.ejbca.core.model.ra.AlreadyRevokedException;
import org.ejbca.core.model.ra.CustomFieldException;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.RevokeBackDateNotAllowedForProfileException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.core.protocol.NoSuchAliasException;
import org.ejbca.core.protocol.cmp.CmpMessageDispatcherSessionLocal;
import org.ejbca.core.protocol.rest.EnrollPkcs10CertificateRequest;
import org.ejbca.core.protocol.ws.objects.UserDataVOWS;
import org.ejbca.core.protocol.ws.objects.UserMatch;
import org.ejbca.ui.web.protocol.CertificateRenewalException;
import org.ejbca.util.query.IllegalQueryException;

/**
 * API of available methods on the CA that can be invoked by the RA.
 * 
 * Implementation restrictions:
 * - Keep in mind that there is latency, so batch things and don't for things twice unless it is expected to have change.
 * - Method names must be unique and signature is not allowed change after a release
 * - Any used object in this class must be Java Serializable
 * - Any used object in this class should be possible to use with an older or newer version of the peer
 * - Checked Exceptions are forwarded in full the implementation is responsible for not leaking sensitive information in
 *   nested causedBy exceptions.
 * 
 * @version $Id$
 */
public interface RaMasterApi {

    /** @return true if the implementation if the interface is available and usable. */
    boolean isBackendAvailable();
    
    /**
     * Get the current (lowest) back-end API version.
     * 
     * Note that this will not lead to a request over network since peers (if any) will report their API version when
     * connecting and this will return the cached and current number.
     * 
     * @return the current (lowest) back-end API version
     * @since RA Master API version 1 (EJBCA 6.8.0)
     */
    int getApiVersion();

    /**
     * The AuthenticationToken is preliminary authorized to a resource if it is either
     * 1. authorized by the local system
     * 2. authorized by the remote system(s)
     * 
     * The local authorization system is always checked first and authorization is cached separately for local and remote system.
     * Since actual authorization check is performed during API call, a local override can never harm the remote system.
     * 
     * @return true if the authenticationToken is authorized to all the resources
     */
    boolean isAuthorizedNoLogging(AuthenticationToken authenticationToken, String...resources);

    /**
     * @return the access rules and corresponding authorization system update number for the specified AuthenticationToken.
     * @throws AuthenticationFailedException if the provided authenticationToken is invalid
     * @since RA Master API version 1 (EJBCA 6.8.0)
     */
    RaAuthorizationResult getAuthorization(AuthenticationToken authenticationToken) throws AuthenticationFailedException;

    /**
     * Returns an AccessSet containing the access rules that are allowed for the given authentication token.
     * Note that AccessSets do not support deny rules.
     * @deprecated RA Master API version 1 (EJBCA 6.8.0). Use {@link #getAuthorization(AuthenticationToken)} instead.
     */
    @Deprecated
    AccessSet getUserAccessSet(AuthenticationToken authenticationToken) throws AuthenticationFailedException;

    /**
     * Gets multiple access sets at once. Returns them in the same order as in the parameter.
     * Note that AccessSets do not support deny rules.
     * @deprecated RA Master API version 1 (EJBCA 6.8.0). Use {@link #getAuthorization(AuthenticationToken)} instead.
     */
    @Deprecated
    List<AccessSet> getUserAccessSets(List<AuthenticationToken> authenticationTokens);

    /** @return a list with information about non-external CAs that the caller is authorized to see. */
    List<CAInfo> getAuthorizedCas(AuthenticationToken authenticationToken);
    
    /**
     * Retrieves a list of all custom style archives 
     * @param authenticationToken of the requesting administrator
     * @return List of all style archives or null if no styles were found
     * @throws AuthorizationDeniedException if requesting administrator is unauthorized to style archives
     */
    LinkedHashMap<Integer, RaStyleInfo> getAllCustomRaStyles(AuthenticationToken authenticationToken) throws AuthorizationDeniedException;
    
    /**
     * Returns a list of all style archives associated to roles which the requesting administrator is member of.
     * @param authenticationToken of the requesting administrator
     * @param hashCodeOfCurrentList will be compared with RaStyleInfos. If equal, null is returned to avoid heavy network traffic. Set 0 to ignore.
     * @return list of associated style archives. Empty list if administrator is not a member of any role or if role has no custom styles applied. Null if 
     * hashCodeOfCurrentList matched, hence doesn't require an update
     */
    List<RaStyleInfo> getAvailableCustomRaStyles(AuthenticationToken authenticationToken, int hashCodeOfCurrentList);
    
    /**
     * @return a list with roles that the caller is authorized to see.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    List<Role> getAuthorizedRoles(AuthenticationToken authenticationToken);
    
    /**
     * @return the Role with the given ID, or null if it does not exist
     * @throws AuthorizationDeniedException if missing view access.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    Role getRole(AuthenticationToken authenticationToken, int roleId) throws AuthorizationDeniedException;

    /**
     * @param roleId Only include namespaces from peers where this role is present. Set to 0 to include all.
     * @return a list of role namespaces the caller is authorized to see. Never returns null.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    List<String> getAuthorizedRoleNamespaces(AuthenticationToken authenticationToken, int roleId);
    
    /**
     * @return a list of token types and their match keys, which the caller is authorized to. Only user-configurable token types are returned.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    Map<String,RaRoleMemberTokenTypeInfo> getAvailableRoleMemberTokenTypes(AuthenticationToken authenticationToken);
    
    
    /**
     * Adds or updates a role in the database. If the role has an ID, it will be updated, but only on the system where it exists.
     * Otherwise, this method will try to create it on any of the configured systems.
     * @param authenticationToken Admin
     * @param role Role to persist. The roleId controls whether it should be added or updated.
     * @return The role object if the role was added/updated, otherwise null.
     * @throws AuthorizationDeniedException if unauthorized to update this role, or not authorized on any system to add it.
     * @throws RoleExistsException if a role with the given name already exists (can happen when adding or renaming)
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    Role saveRole(AuthenticationToken authenticationToken, Role role) throws AuthorizationDeniedException, RoleExistsException;

    /**
     * Deletes a role.
     * @param authenticationToken Administrator
     * @param roleId ID of role to delete.
     * @return true if the role was found and was deleted, and false if it didn't exist.
     * @throws AuthorizationDeniedException If unauthorized, or if trying to delete a role that the requesting admin belongs to itself.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    boolean deleteRole(AuthenticationToken authenticationToken, int roleId) throws AuthorizationDeniedException;
    
    /**
     * @return the Role Member with the given ID, or null if it does not exist
     * @throws AuthorizationDeniedException if missing view access.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    RoleMember getRoleMember(AuthenticationToken authenticationToken, int roleMemberId) throws AuthorizationDeniedException;

    /**
     * Adds or updates a role member in the database. If the role member has an ID, it will be updated, but only on the system where it exists.
     * Otherwise, this method will try to create it on any of the configured systems.
     * @param authenticationToken Admin
     * @param roleMember RoleMember to persist. The roleMemberId controls whether it should be added or updated.
     * @return The role member object if the role member was added/updated, otherwise null.
     * @throws AuthorizationDeniedException if unauthorized to update this role member, or not authorized on any system to add it.
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    RoleMember saveRoleMember(AuthenticationToken authenticationToken, RoleMember roleMember) throws AuthorizationDeniedException;
    
    /**
     * Removes a role member from a role.
     * @param authenticationToken Administrator
     * @param roleId ID of role (used as a safety check to prevent ID collisions).
     * @param roleMemberId ID of role member to delete.
     * @return true if the role member was found and was deleted, and false if it didn't exist.
     * @throws AuthorizationDeniedException If not authorized to edit the given role
     * @since Master RA API version 1 (EJBCA 6.8.0)
     */
    boolean deleteRoleMember(AuthenticationToken authenticationToken, int roleId, int roleMemberId) throws AuthorizationDeniedException;
    
    /** @return the approval request with the given id, or null if it doesn't exist or if authorization was denied */
    RaApprovalRequestInfo getApprovalRequest(AuthenticationToken authenticationToken, int id);

    /**
     * Finds an approval by a hash of the request data.
     * 
     * @param approvalId Calculated hash of the request (this somewhat confusing name is re-used from the ApprovalRequest class)
     */
    RaApprovalRequestInfo getApprovalRequestByRequestHash(AuthenticationToken authenticationToken, int approvalId);
    
    /**
     * Modifies an approval request and sets the current admin as a blacklisted admin.
     * @return The new approval request (which may have a new id)
     */
    RaApprovalRequestInfo editApprovalRequest(AuthenticationToken authenticationToken, RaApprovalEditRequest edit) throws AuthorizationDeniedException;
    
    /**
     * Extends the validity of an approval request for the given amount of time. The status is set to Waiting for Approval if it was expired.
     * @param authenticationToken Admin
     * @param id Id of approval request
     * @param extendForMillis Milliseconds to extend the validity for
     * @throws IllegalStateException if the request is in approval or rejected state already.
     * @throws AuthorizationDeniedException If the admin does not have approval access to this request, e.g. due to missing access to CAs or missing approval access. 
     */
    void extendApprovalRequest(AuthenticationToken authenticationToken, int id, long extendForMillis) throws AuthorizationDeniedException;
    
    /** Approves, rejects or saves (not yet implemented) a step of an approval request. The action is determined by the "action" value in the given RaApprovalResponseRequest.
     * @return true if the approval request exists on this node, false if not.
     * @throws SelfApprovalException if trying to approve one's own action.
     * @throws AdminAlreadyApprovedRequestException if this approval request has been approved or rejected already.
     * @throws ApprovalRequestExecutionException if execution of the approval request (e.g. adding an end endity) failed.
     * @throws ApprovalRequestExpiredException if the approval request is older than the configured expiry time
     * @throws AuthenticationFailedException if the authentication token couldn't be validated
     * @throws ApprovalException is thrown for other errors, such as the approval being in the wrong state, etc.
     */
    boolean addRequestResponse(AuthenticationToken authenticationToken, RaApprovalResponseRequest requestResponse)
            throws AuthorizationDeniedException, ApprovalException, ApprovalRequestExpiredException, ApprovalRequestExecutionException,
            AdminAlreadyApprovedRequestException, SelfApprovalException, AuthenticationFailedException;
    
    /**
     * Searches for approval requests.
     * @param authenticationToken administrator (affects the search results)
     * @param raRequestsSearchRequest specifies which requests to include (e.g. requests that can be approved by the given administrator)
     * @return list of approval requests from the specified search criteria
     */
    RaRequestsSearchResponse searchForApprovalRequests(AuthenticationToken authenticationToken, RaRequestsSearchRequest raRequestsSearchRequest);
    
    /**
     * Searches for a certificate. If present locally, then the data (revocation status etc.) from the local database will be returned 
     * @return CertificateDataWrapper if it exists and the caller is authorized to see the data or null otherwise
     */
    CertificateDataWrapper searchForCertificate(AuthenticationToken authenticationToken, String fingerprint);
    
    /**
     * Searches for a certificate. If present locally, then the data (revocation status etc.) from the local database will be returned
     * @return CertificateDataWrapper if it exists and the caller is authorized to see the data or null otherwise
     */
    CertificateDataWrapper searchForCertificateByIssuerAndSerial(AuthenticationToken authenticationToken, String issuerDN, String serno);
    
    /**
     * Searches for certificates. Data (e.g. revocation status) of remote certificates take precedence over local ones.
     * @return list of certificates from the specified search criteria
     */
    RaCertificateSearchResponse searchForCertificates(AuthenticationToken authenticationToken, RaCertificateSearchRequest raCertificateSearchRequest);

    /**
     * Searches for end entities. Remote end entities take precedence over local ones.
     * @return list of end entities from the specified search criteria
     */
    RaEndEntitySearchResponse searchForEndEntities(AuthenticationToken authenticationToken, RaEndEntitySearchRequest raEndEntitySearchRequest);

    /**
     * Searches for roles that the given authentication token has access to.
     * @param authenticationToken administrator (affects the search results)
     * @param raRoleSearchRequest Object specifying the search criteria.
     * @return Object containing list of roles and search status.
     */
    RaRoleSearchResponse searchForRoles(AuthenticationToken authenticationToken, RaRoleSearchRequest raRoleSearchRequest);
    
    /**
     * Searches for role members in all roles that the given authentication token has access to.
     * @param authenticationToken administrator (affects the search results)
     * @param raRoleMemberSearchRequest Object specifying the search criteria.
     * @return Object containing list of role members and search status.
     */
    RaRoleMemberSearchResponse searchForRoleMembers(AuthenticationToken authenticationToken, RaRoleMemberSearchRequest raRoleMemberSearchRequest);
    
    
    /** @return map of authorized certificate profile Ids and each mapped name */
    Map<Integer, String> getAuthorizedCertificateProfileIdsToNameMap(AuthenticationToken authenticationToken);

    /** @return map of authorized entity profile Ids and each mapped name */
    Map<Integer, String> getAuthorizedEndEntityProfileIdsToNameMap(AuthenticationToken authenticationToken);

    /** @return map of authorized end entity profiles for the provided authentication token */
    IdNameHashMap<EndEntityProfile> getAuthorizedEndEntityProfiles(AuthenticationToken authenticationToken, String endEntityAccessRule);

    /** @return map of authorized and enabled CAInfos for the provided authentication token*/
    IdNameHashMap<CAInfo> getAuthorizedCAInfos(AuthenticationToken authenticationToken);

    /** @return map of authorized certificate profiles for the provided authentication token*/
    IdNameHashMap<CertificateProfile> getAuthorizedCertificateProfiles(AuthenticationToken authenticationToken);
    
    /** @return CertificateProfile with the specified Id or null if it can not be found */
    CertificateProfile getCertificateProfile(int id);
    
    /**
     * Adds (end entity) user.
     * @param authenticationToken authentication token
     * @param endEntity end entity data as EndEntityInformation object
     * @param clearpwd 
     * @throws AuthorizationDeniedException
     * @throws EjbcaException if an EJBCA exception with an error code has occurred during the process
     * @throws WaitingForApprovalException if approval is required to finalize the adding of the end entity
     * @return true if used has been added, false otherwise
     */
    boolean addUser(AuthenticationToken authenticationToken, EndEntityInformation endEntity, boolean clearpwd) throws AuthorizationDeniedException,
    EjbcaException, WaitingForApprovalException;

    /**
     * addUserFromWS is called from EjbcaWS if profile specifies merge data from
     * profile to user we merge them before calling addUser
     * 
     * @param admin the administrator performing the action
     * @param userdata a UserDataVOWS object from WS
     * @param clearpwd true if the password will be stored in clear form in the  db, otherwise it is hashed.
     *            
     * @return true if used has been added, false otherwise
     *
     * @throws AuthorizationDeniedException if administrator isn't authorized to add user
     * @throws EndEntityProfileValidationException if data doesn't fulfill requirements of end entity profile
     * @throws EndEntityExistsException  if user already exists or some other database error occur during commit
     * @throws WaitingForApprovalException if approval is required and the action have been added in the approval queue.
     * @throws CADoesntExistsException if the caid of the user does not exist
     * @throws CustomFieldException if the end entity was not validated by a locally defined field validator
     * @throws CertificateSerialNumberException if SubjectDN serial number already exists.
     * @throws ApprovalException if an approval already exists for this request.
     * @throws IllegalNameException if the Subject DN failed constraints
     * @throws EjbcaException if userdata couldn't be converted to an EndEntityInformation
     * @since RA Master API version 4 (EJBCA 6.14.0)
     */
    boolean addUserFromWS(AuthenticationToken admin, UserDataVOWS userdata, boolean clearpwd)
            throws AuthorizationDeniedException, EndEntityProfileValidationException, EndEntityExistsException, WaitingForApprovalException,
            CADoesntExistsException, CustomFieldException, IllegalNameException, ApprovalException, CertificateSerialNumberException, EjbcaException;
    
    /**
     * Deletes (end entity) user. Does not propagate the exceptions but logs them.
     * @param authenticationToken
     * @param username the username of the end entity user about to delete
     * @throws AuthorizationDeniedException
     */
    void deleteUser(final AuthenticationToken authenticationToken, final String username) throws AuthorizationDeniedException;
    
    /**
     * Performs a finishUser operation after a key recovery operation. The end entity must be in NEW or KEYRECOVERY status
     * and the admin must have access to the CA of the end entity and key recovery access to the end entity profile. 
     * 
     * In detail this means:
     * Decrements the issue counter for an end entity, and sets the status to GENERATED when it reaches zero.
     * Usually this counter only goes from 1 to 0, so usually this method calls means "set end entity status to GENERATED".
     * When the status is set to GENERATED the password is also cleared.
     * 
     * @param authenticationToken authentication token 
     * @param username username of end entity
     * @param password password of end entity
     * @throws AuthorizationDeniedException if not authorized to perform key recovery for the given end entity
     * @throws EjbcaException if the user was not found or had the wrong status
     */
    void finishUserAfterLocalKeyRecovery(AuthenticationToken authenticationToken, String username, String password) throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Generates keystore for the specified end entity. Used for server side generated key pairs. It can be of PKCS12 or JKS type.
     * Keystore can be loaded with:
     *  
     * KeyStore ks = KeyStore.getInstance(endEntityInformation.getTokenType() == EndEntityConstants.TOKEN_SOFT_P12 ? "PKCS12" : "JKS");
     * ks.load(new ByteArrayInputStream(keystoreAsByteArray), endEntityInformation.getPassword().toCharArray());
     * 
     * Note that endEntityInformation are still needed to load a keystore.
     * @param authenticationToken authentication token
     * @param endEntityInformation holds end entity information (including user's password)
     * @return generated keystore
     * @throws AuthorizationDeniedException
     * @throws KeyStoreException if something went wrong with keystore creation
     */
    byte[] generateKeyStore(AuthenticationToken authenticationToken, EndEntityInformation endEntityInformation)
            throws AuthorizationDeniedException, EjbcaException;

    /**
     * Generates certificate from CSR for the specified end entity. Used for client side generated key pairs.
     * @param authenticationToken authentication token
     * @param endEntity end entity information. CertificateRequest (CSR) must be set under extendedInformation of the endEntityInformation.
     * @return certificate binary data. If the certificate request is invalid, then this can in certain cases be null.
     * @throws AuthorizationDeniedException
     * @throws EjbcaException if an EJBCA exception with an error code has occurred during the process
     */
    byte[] createCertificate(AuthenticationToken authenticationToken, EndEntityInformation endEntity)
            throws AuthorizationDeniedException, EjbcaException;

    /**
     * Generates a certificate. This variant is used from the Web Service interface.
     * @param authenticationToken authentication token.
     * @param userdata end entity information, encoded as a UserDataVOWS (web service value object). Must have been enriched by the WS setUserDataVOWS/enrichUserDataWithRawSubjectDn methods.
     * @param requestData see {@link org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest IEjbcaWS.certificateRequest()}
     * @param requestType see {@link org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest IEjbcaWS.certificateRequest()}
     * @param hardTokenSN see {@link org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest IEjbcaWS.certificateRequest()}
     * @param responseType see {@link org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest IEjbcaWS.certificateRequest()}
     * @return certificate binary data. If the certificate request is invalid, then this can in certain cases be null. 
     * @throws AuthorizationDeniedException if not authorized to create a certificate with the given CA or the profiles
     * @throws ApprovalException if the request requires approval
     * @throws EjbcaException if an EJBCA exception with an error code has occurred during the process, for example non-existent CA
     * @throws EndEntityProfileValidationException if the certificate does not match the profiles.
     * @see org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest
     */
    byte[] createCertificateWS(final AuthenticationToken authenticationToken, final UserDataVOWS userdata, final String requestData, final int requestType,
            final String hardTokenSN, final String responseType) throws AuthorizationDeniedException, ApprovalException, EjbcaException,
            EndEntityProfileValidationException;

    /**
     * Generates a certificate. This variant is used from the REST Service interface.
     * @param authenticationToken authentication token.
     * @param enrollcertificateRequest input data object for enrolling a certificate
     * @throws CertificateProfileDoesNotExistException
     * @throws CADoesntExistsException
     * @throws AuthorizationDeniedException
     * @throws EndEntityProfileNotFoundException
     * @throws EjbcaException
     * @throws EndEntityProfileValidationException
     */
    byte[] createCertificateRest(AuthenticationToken authenticationToken, EnrollPkcs10CertificateRequest enrollcertificateRequest) 
            throws CertificateProfileDoesNotExistException, CADoesntExistsException, AuthorizationDeniedException, EndEntityProfileNotFoundException, 
            EjbcaException, EndEntityProfileValidationException;
    
    
    /**
     * Finds end entity by its username.
     * @param authenticationToken authentication token
     * @param username username of the end entity
     * @return end entity as EndEntityInformation
     */
    EndEntityInformation searchUser(AuthenticationToken authenticationToken, String username);

    /**
     * Gets the certificate chain for the most recently created certificate for the end entity with the given user name.
     * @param authenticationToken Authentication token.
     * @param username User name of end entity.
     * @return Certificate chain, with the leaf certificate first. If the users does not exist, it returns an empty list.
     * @throws AuthorizationDeniedException If not authorized to the end entity of the user
     * @throws EjbcaException On internal errors, such as badly encoded certificate.
     */
    List<CertificateWrapper> getLastCertChain(AuthenticationToken authenticationToken, String username)
            throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Request status change of a certificate (revoke or reactivate).
     * Requires authorization to CA, EEP for the certificate and '/ra_functionality/revoke_end_entity'.
     * 
     * @param authenticationToken of the requesting administrator or client
     * @param fingerprint of the certificate
     * @param newStatus CertificateConstants.CERT_REVOKED (40) or CertificateConstants.CERT_ACTIVE (20)
     * @param newRevocationReason One of RevokedCertInfo.REVOCATION_REASON_...
     * @return true if the operation was successful, false if the certificate could not be revoked for example since it did not exist
     * @throws ApprovalException if there was a problem creating the approval request
     * @throws WaitingForApprovalException if the request has been sent for approval
     */
    boolean changeCertificateStatus(AuthenticationToken authenticationToken, String fingerprint, int newStatus, int newRevocationReason)
            throws ApprovalException, WaitingForApprovalException;
    
    /**
     * @see EndEntityManagementSessionLocal#revokeCert(AuthenticationToken, BigInteger, Date, String, int, boolean)
     * @throws CADoesntExistsException in addition to the above throws if the CA (from issuerdn) is not handled by this instance, fail-fast 
     */
    void revokeCert(AuthenticationToken authenticationToken, BigInteger certserno, Date revocationdate, String issuerdn, int reason, boolean checkDate)
            throws AuthorizationDeniedException, NoSuchEndEntityException, ApprovalException, WaitingForApprovalException,
            RevokeBackDateNotAllowedForProfileException, AlreadyRevokedException, CADoesntExistsException;

    /**
     * Request status change of a certificate (revoke or reactivate). 
     * Requires authorization to CA, EEP for the certificate and '/ra_functionality/revoke_end_entity'.
     * Difference with normal RevokeCertCommand is that 
     * this one here allows to include reason, certificateProfileId and revocationdate as input parameters wrapped into CertRevocationDto dto class
     * 
     * @param authenticationToken of the requesting administrator or client
     * @param certRevocationDto wrapper objects for input parameters for the revoke
     * 
     * @throws AuthorizationDeniedException
     * @throws NoSuchEndEntityException if certificate to revoke can not be found
     * @throws ApprovalException if revocation has been requested and is waiting for approval.
     * @throws WaitingForApprovalException
     * @throws RevokeBackDateNotAllowedForProfileException
     * @throws AlreadyRevokedException
     * @throws CADoesntExistsException in addition to the above throws if the CA (from issuerdn) is not handled by this instance, fail-fast
     * @throws CertificateProfileDoesNotExistException if no profile was found with certRevocationDto.certificateProfileId input parameter.
     */
    void revokeCertWithMetadata(AuthenticationToken authenticationToken, CertRevocationDto certRevocationDto)
            throws AuthorizationDeniedException, NoSuchEndEntityException, ApprovalException, WaitingForApprovalException,
            RevokeBackDateNotAllowedForProfileException, AlreadyRevokedException, CADoesntExistsException, IllegalArgumentException, 
            CertificateProfileDoesNotExistException;
    
    /**
     * Revokes all of a user's certificates.
     *
     * It is also possible to delete
     * a user after all certificates have been revoked.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /ra_functionality/revoke_end_entity
     * - /endentityprofilesrules/&lt;end entity profile&gt;/revoke_end_entity
     * - /ca/<ca of users certificate>
     * </pre>
     *
     * @param authenticationToken of the requesting administrator or client.
     * @param username unique username in EJBCA
     * @param reason for revocation, one of {@link org.ejbca.core.protocol.ws.client.gen.RevokeStatus}.REVOKATION_REASON_ constants
     * or use {@link org.ejbca.core.protocol.ws.client.gen.RevokeStatus}.NOT_REVOKED to un-revoke a certificate on hold.
     * @param deleteUser deletes the users after all the certificates have been revoked.
     * @throws CADoesntExistsException if a referenced CA does not exist
     * @throws AuthorizationDeniedException if client isn't authorized.
     * @throws NotFoundException if user doesn't exist
     * @throws WaitingForApprovalException if request has bean added to list of tasks to be approved
     * @throws ApprovalException if there already exists an approval request for this task
     * @throws AlreadyRevokedException if the user already was revoked
     * @throws NoSuchEndEntityException if End Entity bound to certificate isn't found.
     * @throws CouldNotRemoveEndEntityException if the user could not be deleted.
     * @throws EjbcaException
     */
    void revokeUserWS(AuthenticationToken authenticationToken, String username, int reason, boolean deleteUser) throws CADoesntExistsException, AuthorizationDeniedException,
            NotFoundException, EjbcaException, ApprovalException, WaitingForApprovalException, AlreadyRevokedException, NoSuchEndEntityException, CouldNotRemoveEndEntityException;
    
    /** 
     * @see CertificateStoreSession#getStatus(String, BigInteger)
     * @throws CADoesntExistsException in addition to the above throws if the CA (from issuerdn) is not handled by this instance, fail-fast 
     * @throws AuthorizationDeniedException in addition to the above throws if caller is not authorized to revoke certificates from the CA (from issuerdn)
     */
    CertificateStatus getCertificateStatus(AuthenticationToken authenticationToken, String issuerDN, BigInteger serno) throws CADoesntExistsException, AuthorizationDeniedException;

    /**
     * Marks End entity for key recovery, sets a new enrollment code (used to enroll a new certificate) and marks KeyRecoveryData for recovery.
     * 
     * @param authenticationToken of the requesting administrator
     * @param username of end entity holding the certificate to recover
     * @param newPassword selected new password for key recovery. May be null (e.g. in a call from EjbcaWS)
     * @param cert Certificate to be recovered
     * @return true if key recovery was successful. False should not be returned unless unexpected error occurs. Other cases such as required approval
     * should throw exception instead
     * @throws AuthorizationDeniedException if administrator isn't authorized to operations carried out during key recovery preparations
     * @throws ApprovalException if key recovery is already awaiting approval
     * @throws CADoesntExistsException if CA which enrolled the certificate no longer exists
     * @throws WaitingForApprovalException if operation required approval (expected to be thrown with approvals enabled)
     * @throws NoSuchEndEntityException if End Entity bound to certificate isn't found.
     * @throws EndEntityProfileValidationException if End Entity doesn't match profile
     */
    boolean markForRecovery(AuthenticationToken authenticationToken, String username, String newPassword, CertificateWrapper cert, boolean localKeyGeneration) throws AuthorizationDeniedException, ApprovalException, 
                            CADoesntExistsException, WaitingForApprovalException, NoSuchEndEntityException, EndEntityProfileValidationException;

    /**
     * Edit End Entity information. Can only be used with API version 2 and later.
     * 
     * @param authenticationToken the administrator performing the action
     * @param endEntityInformation an EndEntityInformation object with the new information
     * @throws AuthorizationDeniedException administrator not authorized to edit user
     * @throws EndEntityProfileValidationException data doesn't fulfill EEP requirements
     * @throws ApprovalException if an approval already is waiting for specified action
     * @throws WaitingForApprovalException if the action has been added in the approval queue
     * @throws CADoesntExistsException if the user's CA doesn't exist
     * @throws IllegalNameException if the Subject DN failed constraints
     * @throws CertificateSerialNumberException if SubjectDN serial number already exists
     * @throws NoSuchEndEntityException if the EE was not found
     * @throws CustomFieldException if the EE was not validated by a locally defined field validator
     */
    boolean editUser(AuthenticationToken authenticationToken, EndEntityInformation endEntityInformation)
            throws AuthorizationDeniedException, EndEntityProfileValidationException,
            WaitingForApprovalException, CADoesntExistsException, ApprovalException,
            CertificateSerialNumberException, IllegalNameException, NoSuchEndEntityException, CustomFieldException;
    
    /**
     * Edit End Entity information. Can only be used with API version 2 and later.
     * 
     * @param authenticationToken the administrator performing the action
     * @param userDataVOWS an UserDataVOWS object with the new information
     * @throws AuthorizationDeniedException administrator not authorized to edit user
     * @throws EndEntityProfileValidationException data doesn't fulfill EEP requirements
     * @throws ApprovalException if an approval already is waiting for specified action
     * @throws WaitingForApprovalException if the action has been added in the approval queue
     * @throws CADoesntExistsException if the user's CA doesn't exist
     * @throws IllegalNameException if the Subject DN failed constraints
     * @throws CertificateSerialNumberException if SubjectDN serial number already exists
     * @throws NoSuchEndEntityException if the EE was not found
     * @throws CustomFieldException if the EE was not validated by a locally defined field validator
     * @throws EjbcaException if userDataVOWS couldn't be converted to an EndEntityInformation 
     */
    boolean editUserWs(AuthenticationToken authenticationToken, UserDataVOWS userDataVOWS)
            throws AuthorizationDeniedException, EndEntityProfileValidationException,
            WaitingForApprovalException, CADoesntExistsException, ApprovalException,
            CertificateSerialNumberException, IllegalNameException, NoSuchEndEntityException, CustomFieldException, EjbcaException;

    /**
     * Key recovery method to be called from web services. This method handles some special cases differently from the regular key recovery method.
     * 
     * @param authenticationToken of the requesting administrator
     * @param username of end entity holding the certificate to recover
     * @param certSNinHex of the certificate to recover
     * @param issuerDN which issued the certificate
     * @throws AuthorizationDeniedException if administrator isn't authorized to operations carried out during key recovery preparations
     * @throws EjbcaException wrapped exceptions caught in EjbcaWS
     * @throws WaitingForApprovalException if operation required approval (expected to be thrown with approvals enabled)
     * @throws ApprovalException if an approval is already pending to recover this certificate
     * @throws CADoesntExistsException if CA which enrolled the certificate no longer exists
     */
    void keyRecoverWS(AuthenticationToken authenticationToken, String username, String certSNinHex, String issuerDN) throws AuthorizationDeniedException, EjbcaException, 
                        WaitingForApprovalException, ApprovalException, CADoesntExistsException;
    
    /**
     * Atomic Key recovery and PKCS12 / JKS enrollment method to be called from web services. 
     * @param authenticationToken of the requesting administrator
     * @param username of end entity holding the certificate to recover
     * @param certSNinHex of the certificate to recover
     * @param issuerDN issuer of the certificate
     * @param password new
     * @param hardTokenSN see {@link org.ejbca.core.protocol.ws.common.IEjbcaWS#certificateRequest IEjbcaWS.certificateRequest()}
     * @return KeyStore generated, post recovery
     * @throws AuthorizationDeniedException if administrator isn't authorized to operations carried out during key recovery and enrollment
     * @throws WaitingForApprovalException if operation requires approval (expected to be thrown with approvals enabled)
     * @throws EjbcaException exception with errorCode if check fails
     * @throws CADoesntExistsException if CA which issued the certificate no longer exists
     * @throws ApprovalException if an approval is already pending to recover this certificate
     */
    byte[] keyRecoverEnrollWS(AuthenticationToken authenticationToken, String username, String certSNinHex, String issuerDN, String password,
            String hardTokenSN) throws AuthorizationDeniedException, ApprovalException, CADoesntExistsException, EjbcaException, WaitingForApprovalException;
    
    /**
     * Checks if key recovery is possible for the given parameters. Requesting administrator has be authorized to perform key recovery
     * and authorized to perform key recovery on the End Entity Profile which the End Entity belongs to.
     * KeyRecoverData has to be present in the database for the given certificate, 
     * 
     * @param authenticationToken of the requesting administrator
     * @param cert Certificate to be recovered
     * @param username which the certificate is bound to
     * @return true if key recovery is possible given the parameters
     */    
    boolean keyRecoveryPossible(AuthenticationToken authenticationToken, Certificate cert, String username);
    
    /**
     * Gets approval profile for specified action.
     * @param authenticationToken auth. token to be checked if it has access to the specified caInfo and certificateProfile
     * @param action a ApprovalRequestType constant
     * @param caId id of specified CA
     * @param certificateProfileId id of specified certificate profile
     * @return approval profile if it is required for specified caInfo and certificateProfile, null if it is not
     * @throws AuthorizationDeniedException if authentication token is not authorized to specified CA or certificate profile
     */
    public ApprovalProfile getApprovalProfileForAction(final AuthenticationToken authenticationToken, final ApprovalRequestType action, final int caId, final int certificateProfileId) throws AuthorizationDeniedException;

    /**
     * Performs all "deep" checks of user data (EndEntityInformation) intended to be added. Checks like uniqueness of SubjectDN or username should be part of this test.
     * @param admin auth. token
     * @param endEntity user data as EndEntityInformation object
     * @throws AuthorizationDeniedException if authentication token is not authorized to perform checks on user data
     * @throws EjbcaException exception with errorCode if check fails
     */
    void checkSubjectDn(AuthenticationToken admin, EndEntityInformation endEntity) throws AuthorizationDeniedException, EjbcaException;

    /**
     * @see EndEntityAuthenticationSessionLocal#authenticateUser(AuthenticationToken, String, String)
     */
    void checkUserStatus(AuthenticationToken authenticationToken, String username, String password) throws NoSuchEndEntityException, AuthStatusException, AuthLoginException;

    
    /**
     * Dispatch SCEP message over RaMasterpi.
     * 
     * @param authenticationToken the origin of the request
     * @param operation desired SCEP operation to perform
     * @param message to dispatch
     * @param scepConfigurationAlias name of alias containing SCEP configuration
     * @return byte array containing dispatch response from CA. Content depends on operation
     * @throws CertificateEncodingException
     * @throws NoSuchAliasException
     * @throws CADoesntExistsException
     * @throws NoSuchEndEntityException
     * @throws CustomCertificateSerialNumberException
     * @throws CryptoTokenOfflineException
     * @throws IllegalKeyException
     * @throws SignRequestException
     * @throws SignRequestSignatureException
     * @throws AuthStatusException
     * @throws AuthLoginException
     * @throws IllegalNameException
     * @throws CertificateCreateException
     * @throws CertificateRevokeException
     * @throws CertificateSerialNumberException
     * @throws IllegalValidityException
     * @throws CAOfflineException
     * @throws InvalidAlgorithmException
     * @throws SignatureException
     * @throws CertificateException
     * @throws AuthorizationDeniedException
     * @throws CertificateExtensionException
     * @throws CertificateRenewalException
     */
    byte[] scepDispatch(AuthenticationToken authenticationToken, String operation, String message, String scepConfigurationAlias) throws CertificateEncodingException, 
    NoSuchAliasException, CADoesntExistsException, NoSuchEndEntityException, CustomCertificateSerialNumberException, CryptoTokenOfflineException, IllegalKeyException, SignRequestException, 
    SignRequestSignatureException, AuthStatusException, AuthLoginException, IllegalNameException, CertificateCreateException, CertificateRevokeException, CertificateSerialNumberException, 
    IllegalValidityException, CAOfflineException, InvalidAlgorithmException, SignatureException, CertificateException, AuthorizationDeniedException, 
    CertificateExtensionException, CertificateRenewalException;
    
    /**
     * Dispatch CMP request over RaMasterApi.
     * 
     * Basic ASN.1 validation is performed at a proxy to increase the protection of a CA slightly.
     * 
     * @param authenticationToken the origin of the request
     * @param pkiMessageBytes the ASN.1 encoded CMP message request bytes
     * @param cmpConfigurationAlias the requested CA configuration that should handle the request.
     * @return the CMP response ASN.1 (success or error) message as a byte array or null if no processing could take place
     * @see CmpMessageDispatcherSessionLocal#dispatchRequest(AuthenticationToken, byte[], String)
     * @since RA Master API version 1 (EJBCA 6.8.0)
     */
    byte[] cmpDispatch(AuthenticationToken authenticationToken, byte[] pkiMessageBytes, String cmpConfigurationAlias) throws NoSuchAliasException;

    /**
     * Dispatch EST request over RaMasterApi.
     * 
     * Basic ASN.1 validation is performed at a proxy to increase the protection of a CA slightly.
     * 
     * @param operation the EST operation to perform
     * @param alias the requested CA configuration that should handle the request.
     * @param cert The client certificate used to request this operation if any
     * @param username The authentication username if any
     * @param password The authentication password if any
     * @param requestBody The HTTP request body. Usually a PKCS#10
     * @return the HTTP response body
     * 
     * @throws NoSuchAliasException if the alias doesn't exist
     * @throws CADoesntExistsException if the CA specified in a request for CA certs doesn't exist
     * @throws CertificateCreateException if an error was encountered when trying to enroll
     * @throws CertificateRenewalException if an error was encountered when trying to re-enroll
     * @throws AuthenticationFailedException if request was sent in without an authenticating certificate, or the username/password combo was 
     *           invalid (depending on authentication method). 
     * 
     * @see EstOperationBeanLocal#dispatchRequest(Certificate, String, String, String, String, byte[])
     * @since RA Master API version 1 (EJBCA 6.8.0)
     */
    byte[] estDispatch(String operation, String alias, X509Certificate cert, String username, String password, byte[] requestBody)
            throws NoSuchAliasException, CADoesntExistsException, CertificateCreateException, CertificateRenewalException, AuthenticationFailedException;

    /**
     * Retrieves information about users
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /ra_functionality/view_end_entity
     * - /endentityprofilesrules/&lt;end entity profile of matching users&gt;/view_end_entity
     * - /ca/&lt;ca of usermatch&gt; - when matching on CA
     * </pre>
     * 
     * 
     *
     * @param authenticationToken the administrator performing the action
     * @param usermatch the unique user pattern to search for
     * @return a list of {@link org.ejbca.core.protocol.ws.client.gen.UserDataVOWS} objects (Max 100) containing the information about the user or null if there are no matches.
     * @throws AuthorizationDeniedException if client is not authorized to request.
     * @throws IllegalQueryException if query isn't valid
     * @throws EjbcaException
     * @throws EndEntityProfileNotFoundException
     * @since RA Master API version 4 (EJBCA 6.14.0)
     */
    List<UserDataVOWS> findUserWS(AuthenticationToken authenticationToken, UserMatch usermatch, int maxNumberOfRows)
            throws AuthorizationDeniedException, IllegalQueryException, EjbcaException, EndEntityProfileNotFoundException;
       
    /**
     * Returns the length of a publisher queue (aggregated over all separate instances, if found).
     *
     * @param name the name of the queue.
     * @return the length or -4 if the publisher does not exist
     * @throws AuthorizationDeniedException if client is not authorized to request.
     */
    int getPublisherQueueLengthWS(AuthenticationToken authenticationToken, String name) throws AuthorizationDeniedException;
    
    /**
     * Retrieves the certificate chain for the signer. The returned certificate chain MUST have the
     * RootCA certificate in the last position.
     * @param authenticationToken the administrator performing the action
     * @param caid  is the issuerdn.hashCode()
     * @return Collection of Certificate, the certificate chain, never null.
     * @since RA Master API version 4 (EJBCA 6.14.0)
     * @throws AuthorizationDeniedException if client isn't authorized to request
     */
    Collection<Certificate> getCertificateChain(final AuthenticationToken authenticationToken, int caid) throws AuthorizationDeniedException, CADoesntExistsException;
   
    /**
     * Retrieves the certificates whose expiration date is before the specified number of days.
     * @param days the number of days before the certificates will expire
     * @param maxNumberOfResults the maximum number of returned certificates
     * @param offset return results starting from offset
     * @return A list of certificates, never null
     * @throws EjbcaException if at least one of the certificates is unreadable
     */
    List<Certificate> getCertificatesByExpirationTime(final AuthenticationToken authenticationToken, long days, int maxNumberOfResults, int offset) throws AuthorizationDeniedException;

    /**
     * Finds count of certificates  expiring within a specified time and that have
     * status "active" or "notifiedaboutexpiration".
     * @param days the number of days before the certificates will expire
     * @return return count of query results. */
    int getCountOfCertificatesByExpirationTime(final AuthenticationToken authenticationToken, long days) throws AuthorizationDeniedException;
        
    /**
     * Generates a Custom Log event in the database.
     *
     * Authorization requirements: <pre>
     * - /administrator
     * - /secureaudit/log_custom_events (must be configured in advanced mode when editing access rules)
     * </pre>
     *
     * @param level of the event, one of IEjbcaWS.CUSTOMLOG_LEVEL_ constants.
     * @param type user defined string used as a prefix in the log comment.
     * @param caName of the CA related to the event, use null if no specific CA is related. Then will the ca of the administrator be used.
     * @param username of the related user, use null if no related user exists.
     * @param certificateSn the certificate SN or null.
     * @param msg message data used in the log comment. The log comment will have a syntax of 'type : msg'.
     * @param event the event type.
     * @throws AuthorizationDeniedException if the administrators isn't authorized to log.
     * @throws CADoesntExistsException if a referenced CA does not exist.
     * @throws EjbcaException any EjbcaException.
     */
    void customLogWS(AuthenticationToken authenticationToken, int level, String type, String cAName, String username, String certificateSn, String msg, EventType event) 
                throws AuthorizationDeniedException, CADoesntExistsException, EjbcaException;
    
    /**
     * Retrieves a collection of certificates as byte array generated for a user.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /ra_functionality/view_end_entity
     * - /endentityprofilesrules/&lt;end entity profile&gt;/view_end_entity
     * - /ca/&lt;ca of user&gt;
     * </pre>
     *
     * @param username a unique username.
     * @param onlyValid only return valid certificates not revoked or expired ones.
     * @param now the current time as long value since epoch.
     * @return a collection of Certificates or an empty list if no certificates, or no user, could be found.
     * @throws AuthorizationDeniedException if client isn't authorized to request.
     * @throws CertificateEncodingException if a certificate could not be encoded.
     * @throws EjbcaException any EjbcaException.
     */
    Collection<Certificate> findCertsWS(AuthenticationToken authenticationToken, String username, boolean onlyValid, long now) throws AuthorizationDeniedException, CertificateEncodingException, EjbcaException;
    
    /**
     * Fetches available certificate profiles in an end entity profile.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /endentityprofilesrules/&lt;end entity profile&gt;
     * </pre>
     *
     * @param entityProfileId id of an end entity profile where we want to find which certificate profiles are available.
     * @return a Map containing the name and ID pairs of available certificate profiles or an empty map.
     * @throws AuthorizationDeniedException if client isn't authorized to request.
     * @throws EjbcaException any EjbcaException.
     */
    Map<String,Integer> getAvailableCertificateProfilesWS(AuthenticationToken authenticationToken, int entityProfileId) throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Fetches the IDs and names of available CAs in an end entity profile.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /endentityprofilesrules/&lt;end entity profile&gt;
     * </pre>
     *
     * @param authenticationToken the administrator performing the action.
     * @param entityProfileId the ID of an end entity profile where we want to find which CAs are available.
     * @return a Map containing the name and ID pairs of available CAs in the specified end entity profile or an empty map.
     * @throws AuthorizationDeniedException if client isn't authorized to request.
     * @throws EjbcaException any EjbcaException.
     */
    Map<String,Integer> getAvailableCAsInProfileWS(AuthenticationToken authenticationToken, final int entityProfileId) throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Fetches the end entity profiles that the administrator is authorized to use.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /endentityprofilesrules/&lt;end entity profile&gt;
     * </pre>
     *
     * @param authenticationToken the administrator performing the action.
     * @return a Map containing the name and ID pairs of authorized end entity profiles or an empty map.
     * @throws AuthorizationDeniedException if client isn't authorized to request.
     * @throws EjbcaException any EjbcaException.
     * @see "IRaAdminSessionLocal#getAuthorizedEndEntityProfileIds()"
     */
    Map<String,Integer> getAuthorizedEndEntityProfilesWS(AuthenticationToken authenticationToken) throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Fetches an issued certificate.
     *
     * Authorization requirements:<pre>
     * - A valid certificate
     * - /ca_functionality/view_certificate
     * - /ca/&lt;of the issing CA&gt;
     * </pre>
     *
     * @param authenticationToken the administrator performing the action.
     * @param certSNinHex the certificate serial number in hexadecimal representation.
     * @param issuerDN the issuer of the certificate.
     * @return the certificate or null if certificate couldn't be found.
     * @throws AuthorizationDeniedException if the calling administrator isn't authorized to view the certificate.
     * @throws CADoesntExistsException if a referenced CA does not exist.
     * @throws EjbcaException any EjbcaException.
     */
    Certificate getCertificateWS(AuthenticationToken authenticationToken, String certSNinHex, String issuerDN) throws AuthorizationDeniedException, CADoesntExistsException, EjbcaException;
    
    /**
     * Fetches a list of certificates whose expiration date is before the specified number of days.
     *
     *  Note the whole certificate chain is returned.
     *
     * Authorization requirements:<pre>
     * - /administrator
     * - /ra_functionality/view_end_entity
     * - /endentityprofilesrules/&lt;end entity profile&gt;/view_end_entity
     * - /ca/&lt;ca of user&gt;
     * </pre>
     *
     * @param authenticationToken the administrator performing the action.
     * @param days the number of days before the certificates will expire.
     * @param maxNumberOfResults the maximum number of returned certificates.
     * @return A list of certificates, never null.
     * @throws AuthorizationDeniedException if the calling administrator isn't authorized to fetch one of the certificates (not used).
     * @throws EjbcaException if at least one of the certificates is unreadable.
     */
    List<Certificate> getCertificatesByExpirationTimeWS(AuthenticationToken authenticationToken, long days, int maxNumberOfResults) throws AuthorizationDeniedException, EjbcaException;
    

    /**
     * Fetches a list of certificates that will expire within the given number of days and of the given type.
     *
     * @param authenticationToken the administrator performing the action.
     * @param days Expire time in days.
     * @param certificateType The type of the certificates. Use 0=Unknown  1=EndEntity  2=SUBCA  8=ROOTCA  16=HardToken.
     * @param maxNumberOfResults the maximum number of returned certificates.
     * @return A list of certificates, never null.
     * @throws AuthorizationDeniedException if the calling administrator isn't authorized to fetch one of the certificates (not used).
     * @throws EjbcaException if at least one of the certificates is unreadable
     */
    List<Certificate> getCertificatesByExpirationTimeAndTypeWS(AuthenticationToken authenticationToken, long days, int certificateType, int maxNumberOfResults) throws AuthorizationDeniedException, EjbcaException;
    
    /**
     * Fetches a list of certificates that will expire within the given number of days and issued by the given issuer.
     *
     * @param authenticationToken the administrator performing the action.
     * @param days Expire time in days.
     * @param issuerDN The issuerDN of the certificates.
     * @param maxNumberOfResults the maximum number of returned certificates.
     * @return A list of certificates, never null.
     * @throws AuthorizationDeniedException if the calling administrator isn't authorized to fetch one of the certificates (not used).
     * @throws EjbcaException if at least one of the certificates is unreadable.
     */
    List<Certificate> getCertificatesByExpirationTimeAndIssuerWS(AuthenticationToken authenticationToken, long days, String issuerDN, int maxNumberOfResults) throws AuthorizationDeniedException, EjbcaException;
}
