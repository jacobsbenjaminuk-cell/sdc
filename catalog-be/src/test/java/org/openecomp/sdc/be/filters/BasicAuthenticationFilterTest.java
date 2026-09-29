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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openecomp.sdc.be.config.Configuration.BasicAuthConfig;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.WebAppContextWrapper;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.exception.ResponseFormat;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(MockitoExtension.class)
class BasicAuthenticationFilterTest {

    private static final String DISTRIBUTION_SERVLET_PATH = "/sdc";
    private static final String INTERNAL_SERVLET_PATH = "/sdc2";

    @InjectMocks
    private BasicAuthenticationFilter filter;
    @Mock
    private HttpServletRequest sr;
    @Mock
    private HttpSession session;
    @Mock
    private ServletContext servletContext;
    @Mock
    private WebAppContextWrapper webAppContextWrapper;
    @Mock
    private WebApplicationContext webApplicationContext;
    @Mock
    private ComponentsUtils componentsUtils;
    @Mock(lenient = true)
    private ContainerRequest requestContext;
    @Mock(lenient = true)
    private ExtendedUriInfo uriInfo;

    private BasicAuthConfig basicAuthConfig;
    private boolean originalEnabled;

    @BeforeEach
    void setUp() {
        ExternalConfiguration.setAppName("catalog-be");
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(), "src/test/resources/config/catalog-be"));
        basicAuthConfig = ConfigurationManager.getConfigurationManager().getConfiguration().getBasicAuth();
        originalEnabled = basicAuthConfig.isEnabled();

        lenient().when(sr.getSession()).thenReturn(session);
        lenient().when(session.getServletContext()).thenReturn(servletContext);
        lenient().when(servletContext.getAttribute(Constants.WEB_APPLICATION_CONTEXT_WRAPPER_ATTR)).thenReturn(webAppContextWrapper);
        lenient().when(webAppContextWrapper.getWebAppContext(servletContext)).thenReturn(webApplicationContext);
        lenient().when(webApplicationContext.getBean(ComponentsUtils.class)).thenReturn(componentsUtils);
        ResponseFormat responseFormat401 = responseFormat(401);
        ResponseFormat responseFormat403 = responseFormat(403);
        ResponseFormat responseFormat400 = responseFormat(400);
        lenient().when(componentsUtils.getResponseFormat(ActionStatus.AUTH_REQUIRED)).thenReturn(responseFormat401);
        lenient().when(componentsUtils.getResponseFormat(ActionStatus.AUTH_FAILED)).thenReturn(responseFormat403);
        lenient().when(componentsUtils.getResponseFormat(ActionStatus.AUTH_FAILED_INVALIDE_HEADER)).thenReturn(responseFormat400);
        lenient().when(requestContext.getRequestUri()).thenReturn(URI.create("http://localhost:8080/sdc/v1/catalog/services/x/1.0/artifacts/a"));
        lenient().when(requestContext.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getPath()).thenReturn("/v1/catalog/services/x/1.0/artifacts/a");
        lenient().when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost:8080/sdc/v1/catalog/services/x/1.0/artifacts/a"));
        lenient().when(uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/sdc/"));
    }

    @AfterEach
    void tearDown() {
        basicAuthConfig.setEnabled(originalEnabled);
    }

    @Test
    void distributionApiWithoutCredentialsIsRejectedWhenBasicAuthDisabled() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);

        filter.filter(requestContext);

        assertEquals(401, abortedResponse().getStatus());
    }

    @Test
    void distributionApiWithWrongCredentialsIsRejectedWhenBasicAuthDisabled() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);
        when(requestContext.getHeaderString(Constants.AUTHORIZATION_HEADER)).thenReturn(basic("test", "wrong"));

        filter.filter(requestContext);

        assertEquals(403, abortedResponse().getStatus());
    }

    @Test
    void distributionApiWithMalformedHeaderIsRejected() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);
        when(requestContext.getHeaderString(Constants.AUTHORIZATION_HEADER)).thenReturn("Bearer token");

        filter.filter(requestContext);

        assertEquals(400, abortedResponse().getStatus());
    }

    @Test
    void distributionApiWithValidCredentialsSetsPrincipalWhenBasicAuthDisabled() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);
        when(requestContext.getHeaderString(Constants.AUTHORIZATION_HEADER)).thenReturn(basic("test", "test"));

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(requestContext).setSecurityContext(captor.capture());
        assertEquals("test", captor.getValue().getUserPrincipal().getName());
    }

    @Test
    void internalApiIsNotAuthenticatedWhenBasicAuthDisabled() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(INTERNAL_SERVLET_PATH);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(requestContext, never()).setSecurityContext(any());
    }

    @Test
    void internalApiWithoutCredentialsIsRejectedWhenBasicAuthEnabled() throws Exception {
        basicAuthConfig.setEnabled(true);
        lenient().when(sr.getServletPath()).thenReturn(INTERNAL_SERVLET_PATH);

        filter.filter(requestContext);

        assertEquals(401, abortedResponse().getStatus());
    }

    @Test
    void excludedUrlIsNotAuthenticatedOnInternalApi() throws Exception {
        basicAuthConfig.setEnabled(true);
        when(sr.getServletPath()).thenReturn(INTERNAL_SERVLET_PATH);
        when(requestContext.getRequestUri()).thenReturn(URI.create("http://localhost:8080/test1"));

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void excludedUrlIsStillAuthenticatedOnDistributionApi() throws Exception {
        basicAuthConfig.setEnabled(false);
        when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);
        when(requestContext.getRequestUri()).thenReturn(URI.create("http://localhost:8080/test1"));

        filter.filter(requestContext);

        assertEquals(401, abortedResponse().getStatus());
    }

    @Test
    void distributionApiIsRejectedWhenCredentialsAreNotConfigured() throws Exception {
        String originalUserName = basicAuthConfig.getUserName();
        String originalUserPass = basicAuthConfig.getUserPass();
        basicAuthConfig.setUserName("");
        basicAuthConfig.setUserPass("");
        try {
            when(sr.getServletPath()).thenReturn(DISTRIBUTION_SERVLET_PATH);
            lenient().when(requestContext.getHeaderString(Constants.AUTHORIZATION_HEADER)).thenReturn(basic("", ""));

            filter.filter(requestContext);

            assertEquals(401, abortedResponse().getStatus());
        } finally {
            basicAuthConfig.setUserName(originalUserName);
            basicAuthConfig.setUserPass(originalUserPass);
        }
    }

    private Response abortedResponse() {
        ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(captor.capture());
        verify(requestContext, never()).setSecurityContext(any());
        return captor.getValue();
    }

    private static ResponseFormat responseFormat(int status) {
        ResponseFormat responseFormat = mock(ResponseFormat.class, withSettings().lenient());
        when(responseFormat.getStatus()).thenReturn(status);
        when(responseFormat.getFormattedMessage()).thenReturn("authentication error " + status);
        return responseFormat;
    }

    private static String basic(String userName, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
