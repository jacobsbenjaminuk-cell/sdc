/*
 * Copyright © 2019 iconectiv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openecomp.sdcrests.externaltesting.rest.services;


import static org.mockito.MockitoAnnotations.openMocks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openecomp.core.externaltesting.api.ClientConfiguration;
import org.openecomp.core.externaltesting.api.ExternalTestingManager;
import org.openecomp.core.externaltesting.api.RemoteTestingEndpointDefinition;
import org.openecomp.core.externaltesting.api.TestTreeNode;
import org.openecomp.core.externaltesting.api.VtpNameDescriptionPair;
import org.openecomp.core.externaltesting.api.VtpTestCase;
import org.openecomp.core.externaltesting.api.VtpTestExecutionOutput;
import org.openecomp.core.externaltesting.api.VtpTestExecutionRequest;
import org.openecomp.core.externaltesting.api.VtpTestExecutionResponse;
import org.openecomp.core.externaltesting.errors.ExternalTestingException;
import org.openecomp.sdc.itempermissions.PermissionsManager;
import org.openecomp.sdc.vendorsoftwareproduct.VendorSoftwareProductManager;


public class ApiTest {

    private static final String EP = "ep";
    private static final String EXEC = "exec";
    private static final String SC = "sc";
    private static final String TS = "ts";
    private static final String TC = "tc";
    private static final String EXPECTED = "Expected";
    private static final String VSP_ID = "vspId";
    private static final String USER = "cs0008";
    private static final String EDIT_ITEM = "Edit_Item";

    @Mock
    private ExternalTestingManager testingManager;

    @Mock
    VendorSoftwareProductManager vendorSoftwareProductManager;

    @Mock
    private PermissionsManager permissionsManager;

    @Before
    public void setUp() {
        try {
            openMocks(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Mockito.when(permissionsManager.isAllowed(VSP_ID, USER, EDIT_ITEM)).thenReturn(true);
    }


    /**
     * At the API level, test that the code does not throw
     * exceptions but there's not much to test.
     */
    @Test
    public void testApi() {


        ExternalTestingImpl testing = new ExternalTestingImpl(testingManager, vendorSoftwareProductManager, permissionsManager);
        Assert.assertNotNull(testing.getConfig());
        Assert.assertNotNull(testing.getEndpoints());
        Assert.assertNotNull(testing.getExecution(EP, EXEC));
        Assert.assertNotNull(testing.getScenarios(EP));
        Assert.assertNotNull(testing.getTestcase(EP, SC, TS, TC));
        Assert.assertNotNull(testing.getTestcases(EP, SC));
        Assert.assertNotNull(testing.getTestsuites(EP, SC));
        Assert.assertNotNull(testing.getTestCasesAsTree());

        List<VtpTestExecutionRequest> requests =
                Arrays.asList(new VtpTestExecutionRequest(), new VtpTestExecutionRequest());
        Response executeResponse = testing.execute(VSP_ID, "vspVersionId", "abc", USER, null, "[]");
        Assert.assertEquals(200, executeResponse.getStatus());
    }

    /**
     * Callers without permission on the VSP must not be able to run tests against it.
     */
    @Test
    public void testExecuteRequiresVspPermission() {
        ExternalTestingImpl testing = new ExternalTestingImpl(testingManager, vendorSoftwareProductManager, permissionsManager);

        Assert.assertEquals(403, testing.execute(VSP_ID, "vspVersionId", "abc", "intruder", null, "[]").getStatus());
        Assert.assertEquals(403, testing.execute(VSP_ID, "vspVersionId", "abc", null, null, "[]").getStatus());
        Assert.assertEquals(403, testing.execute(null, "vspVersionId", "abc", USER, null, "[]").getStatus());
        Mockito.verify(testingManager, Mockito.never())
                .execute(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    class ApiTestExternalTestingManager implements ExternalTestingManager {

        @Override
        public ClientConfiguration getConfig() {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public TestTreeNode getTestCasesAsTree() {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<RemoteTestingEndpointDefinition> getEndpoints() {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<VtpNameDescriptionPair> getScenarios(String endpoint) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<VtpNameDescriptionPair> getTestSuites(String endpoint, String scenario) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<VtpTestCase> getTestCases(String endpoint, String scenario) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public VtpTestCase getTestCase(String endpoint, String scenario, String testSuite, String testCaseName) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<VtpTestExecutionResponse> execute(List<VtpTestExecutionRequest> requests, String vspId,
                String vspVersionId, String requestId, Map<String, byte[]> fileMap) {

            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public VtpTestExecutionResponse getExecution(String endpoint, String executionId) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

        @Override
        public List<VtpTestExecutionOutput> getExecutionIds(String endpoint, String requestId) {
            throw new ExternalTestingException(EXPECTED, 500, EXPECTED);
        }

    }

    /**
     * Test the exception handler logic for configuration get/set.
     */
    @Test()
    public void testConfigExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response getResponse = testingF.getConfig();
        Assert.assertEquals(500, getResponse.getStatus());
    }

    /**
     * Test the exception handler logic for endpoint get/set.
     */
    @Test()
    public void testEndpointExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response getResponse = testingF.getEndpoints();
        Assert.assertEquals(500, getResponse.getStatus());
    }

    /**
     * Test the exception handler logic for executions (invocation and query).
     */
    @Test()
    public void testExecutionExceptions() {
        openMocks(this);
        Mockito.when(permissionsManager.isAllowed(VSP_ID, USER, EDIT_ITEM)).thenReturn(true);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response invokeResponse = testingF.execute(VSP_ID, "vspVersionId", "abc", USER, null, "[]");
        Assert.assertEquals(500, invokeResponse.getStatus());

        Response getResponse = testingF.getExecution(EP, EXEC);
        Assert.assertEquals(500, getResponse.getStatus());
    }


    /**
     * Test the exception handler logic for the cases when the
     * testing manager throws an accessing the scenarios.
     */
    @Test()
    public void testScenarioExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response response = testingF.getScenarios(EP);
        Assert.assertEquals(500, response.getStatus());
    }

    /**
     * Test the exception handler logic for the cases when the
     * testing manager throws an accessing a test case.
     */
    @Test()
    public void testTestCaseExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response response = testingF.getTestcase(EP, SC, TS, TC);
        Assert.assertEquals(500, response.getStatus());
    }

    /**
     * Test the exception handler logic for the cases when the
     * testing manager throws an accessing the test cases.
     */
    @Test()
    public void testTestCasesExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response response = testingF.getTestcases(EP, SC);
        Assert.assertEquals(500, response.getStatus());
    }

    /**
     * Test the exception handler logic for the cases when the
     * testing manager throws an accessing the test suites.
     */
    @Test()
    public void testTestSuitesExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response response = testingF.getTestsuites(EP, SC);
        Assert.assertEquals(500, response.getStatus());
    }

    /**
     * Test the exception handler logic for the cases when the
     * testing manager throws an accessing the test tree.
     */
    @Test()
    public void testTreeExceptions() {
        openMocks(this);

        ExternalTestingManager m = new ApiTestExternalTestingManager();
        ExternalTestingImpl testingF = new ExternalTestingImpl(m, vendorSoftwareProductManager, permissionsManager);

        Response response = testingF.getTestCasesAsTree();
        Assert.assertEquals(500, response.getStatus());
    }
}
