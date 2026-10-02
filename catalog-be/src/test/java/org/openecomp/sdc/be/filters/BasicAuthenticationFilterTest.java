/*-
 * ============LICENSE_START=======================================================
 * SDC
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.WebAppContextWrapper;
import org.openecomp.sdc.common.api.ConfigurationSource;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.exception.ResponseFormat;
import org.openecomp.sdc.exception.ServiceException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;

class BasicAuthenticationFilterTest {

    private static final String PATH = "/v1/catalog/services";

    private BasicAuthenticationFilter filter;
    private ContainerRequest requestContext;
    private ComponentsUtils componentsUtils;

    @BeforeAll
    static void setUpConfiguration() {
        ConfigurationSource configurationSource = new FSConfigurationSource(ExternalConfiguration.getChangeListener(),
            "src/test/resources/config/catalog-be");
        ConfigurationManager configurationManager = new ConfigurationManager(configurationSource);
        configurationManager.getConfiguration().getBasicAuth().setEnabled(true);
    }

    @BeforeEach
    void setUp() {
        componentsUtils = mock(ComponentsUtils.class);
        ResponseFormat responseFormat = new ResponseFormat(401);
        responseFormat.setServiceException(new ServiceException("SVC4000", "Authentication failed", new String[0]));
        when(componentsUtils.getResponseFormat(any(ActionStatus.class))).thenReturn(responseFormat);

        WebApplicationContext webApplicationContext = mock(WebApplicationContext.class);
        when(webApplicationContext.getBean(ComponentsUtils.class)).thenReturn(componentsUtils);
        ServletContext servletContext = mock(ServletContext.class);
        WebAppContextWrapper webAppContextWrapper = mock(WebAppContextWrapper.class);
        when(webAppContextWrapper.getWebAppContext(servletContext)).thenReturn(webApplicationContext);
        when(servletContext.getAttribute(Constants.WEB_APPLICATION_CONTEXT_WRAPPER_ATTR)).thenReturn(webAppContextWrapper);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS);
        when(servletRequest.getSession().getServletContext()).thenReturn(servletContext);

        filter = new BasicAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "sr", servletRequest);

        requestContext = mock(ContainerRequest.class, RETURNS_DEEP_STUBS);
        when(requestContext.getRequestUri()).thenReturn(URI.create("http://localhost:8080/sdc2/rest" + PATH));
        when(requestContext.getUriInfo().getPath()).thenReturn(PATH);
    }

    @Test
    void validCredentialsAuditSuccessOnly() throws Exception {
        withCredentials("test", "test");

        filter.filter(requestContext);

        verify(componentsUtils).auditAuthEvent(PATH, "test", BasicAuthenticationFilter.AuthStatus.AUTH_SUCCESS.toString(), "ASDC");
        verify(componentsUtils, never()).auditAuthEvent(anyString(), anyString(),
            eq(BasicAuthenticationFilter.AuthStatus.AUTH_FAILED_INVALID_PASSWORD.toString()), anyString());
        verify(requestContext, never()).abortWith(any(Response.class));
    }

    @Test
    void wrongPasswordIsAbortedAndNeverAuditedAsSuccess() throws Exception {
        withCredentials("test", "wrong");

        filter.filter(requestContext);

        verify(componentsUtils).auditAuthEvent(PATH, "test", BasicAuthenticationFilter.AuthStatus.AUTH_FAILED_INVALID_PASSWORD.toString(), "ASDC");
        verify(componentsUtils, never()).auditAuthEvent(anyString(), anyString(),
            eq(BasicAuthenticationFilter.AuthStatus.AUTH_SUCCESS.toString()), anyString());
        verify(requestContext).abortWith(any(Response.class));
    }

    @Test
    void unknownUserIsAbortedAndNeverAuditedAsSuccess() throws Exception {
        withCredentials("attacker", "test");

        filter.filter(requestContext);

        verify(componentsUtils).auditAuthEvent(PATH, "attacker", BasicAuthenticationFilter.AuthStatus.AUTH_FAILED_INVALID_PASSWORD.toString(),
            "ASDC");
        verify(componentsUtils, never()).auditAuthEvent(anyString(), anyString(),
            eq(BasicAuthenticationFilter.AuthStatus.AUTH_SUCCESS.toString()), anyString());
        verify(requestContext).abortWith(any(Response.class));
    }

    private void withCredentials(String userName, String password) {
        String encoded = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        when(requestContext.getHeaderString(Constants.AUTHORIZATION_HEADER)).thenReturn("Basic " + encoded);
    }
}
