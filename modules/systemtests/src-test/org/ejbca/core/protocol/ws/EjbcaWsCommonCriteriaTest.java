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
package org.ejbca.core.protocol.ws;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Represents a subset of WS tests to quickly get an estimate of CC compliance.
 * 
 * @version $Id$
 *
 */
@Ignore
public class EjbcaWsCommonCriteriaTest {

    private EjbcaWSTest test;
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        EjbcaWSTest.beforeClass();
    }
    
    @Before
    public void setUp() throws Exception {
        test = new EjbcaWSTest();
        EjbcaWSTest.setupAccessRights(EjbcaWSTest.WS_ADMIN_ROLENAME);
        test.setUpAdmin();
    }
    
    @After
    public void tearDown() throws Exception {
     
        test.tearDown();
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        EjbcaWSTest.afterClass();
    }
    
    @Test
    public void testCertificateLifeCycle() {
        //Complete life cycle is done as a part of setup and cleanup
    }
    
    @Test
    public void testCrlIssuance() throws Exception {
        test.test25GreateCRL();
    }
    
    @Test
    public void testEndEndEntityManagement() {
        //Complete life cycle is done as a part of setup and cleanup
    }
    
    @Test
    public void testKeyRecovery() throws Exception {
        test.test20KeyRecoverNewest();
    }
    
    @Test
    public void testHardTokenOpertations() throws Exception {
        test.test19RevocationApprovals();
    }
    
    @Test 
    public void testPublisherOperations() throws Exception {
        test.test32OperationOnNonexistingCA();
        test.test33CheckQueueLength();
    }
    
    @Test
    public void testIsAuthorized() throws Exception {
        test.testIsAuthorized(true);
        // Test non authorized by revoking the certificate
        EjbcaWSTest.afterClass();
        test.tearDown();
        test.testIsAuthorized(false);
    }
}
