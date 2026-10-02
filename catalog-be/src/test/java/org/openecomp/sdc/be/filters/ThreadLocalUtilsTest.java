/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2026 the SDC contributors. All rights reserved.
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */
package org.openecomp.sdc.be.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.config.Configuration;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.common.util.ThreadLocalsHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThreadLocalUtilsTest {

    private static final String SERVICE_USER = "testName";
    private static final String SERVICE_PASS = "testPass";

    @Mock
    private UserBusinessLogic userBusinessLogic;
    @InjectMocks
    private ThreadLocalUtils threadLocalUtils;
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private Configuration.BasicAuthConfig basicAuth;

    @BeforeEach
    void setUp() {
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(), "src/test/resources/config/catalog-be"));
        basicAuth = new Configuration.BasicAuthConfig();
        basicAuth.setEnabled(true);
        basicAuth.setUserName(SERVICE_USER);
        basicAuth.setUserPass(SERVICE_PASS);
        basicAuth.setExcludedUrls("/sdc2/rest/healthCheck");
        ConfigurationManager.getConfigurationManager().getConfiguration().setBasicAuth(basicAuth);
        when(request.getRequestURI()).thenReturn("/sdc2/rest/v1/user");
        when(request.getServletPath()).thenReturn("/sdc2");
        final User user = new User();
        user.setUserId("cs0008");
        user.setRole("DESIGNER");
        when(userBusinessLogic.getUser(anyString(), anyBoolean())).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalsHolder.cleanup();
    }

    @Test
    void userIdWithServiceCredentialsIsTrusted() {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(SERVICE_USER, SERVICE_PASS));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("cs0008");
        assertEquals(Optional.of("cs0008"), threadLocalUtils.setUserContextFromDB(request));
        assertEquals("cs0008", ThreadLocalsHolder.getUserContext().getUserId());
    }

    @Test
    void userIdWithoutCredentialsIsRejectedBeforeLookup() {
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("jh0003");
        assertAuthRequired();
    }

    @Test
    void userIdWithWrongCredentialsIsRejectedBeforeLookup() {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(SERVICE_USER, "guess"));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("jh0003");
        assertAuthRequired();
    }

    @Test
    void userIdIsRejectedWhenBasicAuthIsDisabled() {
        basicAuth.setEnabled(false);
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("jh0003");
        assertAuthRequired();
    }

    @Test
    void missingUserIdOnInternalApiIsRejectedInsteadOfDefaulted() {
        basicAuth.setEnabled(false);
        assertAuthRequired();
    }

    @Test
    void missingUserIdOnExternalApiGetsNoContext() {
        basicAuth.setEnabled(false);
        when(request.getServletPath()).thenReturn("/sdc");
        when(request.getRequestURI()).thenReturn("/sdc/v1/catalog/resources");
        assertTrue(threadLocalUtils.setUserContextFromDB(request).isEmpty());
        assertNull(ThreadLocalsHolder.getUserContext());
    }

    @Test
    void excludedUrlNeedsNoIdentity() {
        when(request.getRequestURI()).thenReturn("/sdc2/rest/healthCheck");
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("jh0003");
        assertTrue(threadLocalUtils.setUserContextFromDB(request).isEmpty());
        assertNull(ThreadLocalsHolder.getUserContext());
        verify(userBusinessLogic, never()).getUser(anyString(), anyBoolean());
    }

    private void assertAuthRequired() {
        final ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class,
            () -> threadLocalUtils.setUserContextFromDB(request));
        assertEquals(ActionStatus.AUTH_REQUIRED, e.getActionStatus());
        assertNull(ThreadLocalsHolder.getUserContext());
        verify(userBusinessLogic, never()).getUser(anyString(), anyBoolean());
    }

    private static String basic(final String user, final String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }
}
