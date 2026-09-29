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
package org.openecomp.sdc.be.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openecomp.sdc.be.components.impl.ComponentInstanceBusinessLogic;
import org.openecomp.sdc.be.components.impl.ResourceImportManager;
import org.openecomp.sdc.be.components.impl.TogglingBusinessLogic;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.components.impl.exceptions.ComponentException;
import org.openecomp.sdc.be.components.validation.UserValidations;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.config.SpringConfig;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.ServletUtils;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.user.Role;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.common.api.ConfigurationSource;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.exception.ResponseFormat;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class TogglingServletTest extends JerseyTest {

    private static final String ADMIN_USER_ID = "jh0003";
    private static final String DESIGNER_USER_ID = "cs0008";
    private static final String ALL_FEATURES_URL = "/v1/catalog/toggle/state/false";
    private static final String ONE_FEATURE_URL = "/v1/catalog/toggle/HEALING/state/false";

    @Mock
    private HttpServletRequest request;
    @Mock
    private UserBusinessLogic userBusinessLogic;
    @Mock
    private ComponentInstanceBusinessLogic componentInstanceBusinessLogic;
    @Mock
    private ComponentsUtils componentsUtils;
    @Mock
    private ServletUtils servletUtils;
    @Mock
    private ResourceImportManager resourceImportManager;
    @Mock
    private TogglingBusinessLogic togglingBusinessLogic;
    @Mock
    private ResponseFormat okResponseFormat;

    @BeforeEach
    void init() throws Exception {
        super.setUp();
        initConfig();
        when(servletUtils.getComponentsUtils()).thenReturn(componentsUtils);
        when(okResponseFormat.getStatus()).thenReturn(Status.OK.getStatusCode());
        when(componentsUtils.getResponseFormat(ActionStatus.OK)).thenReturn(okResponseFormat);
        when(componentsUtils.getResponseFormat(any(ComponentException.class)))
            .thenAnswer(invocation -> invocation.<ComponentException>getArgument(0).getResponseFormat());
        when(userBusinessLogic.getUser(ADMIN_USER_ID)).thenReturn(user(ADMIN_USER_ID, Role.ADMIN));
        when(userBusinessLogic.getUser(DESIGNER_USER_ID)).thenReturn(user(DESIGNER_USER_ID, Role.DESIGNER));
    }

    @AfterEach
    void destroy() throws Exception {
        super.tearDown();
    }

    private void initConfig() {
        final String appConfigDir = "src/test/resources/config/catalog-be";
        final ConfigurationSource configurationSource = new FSConfigurationSource(ExternalConfiguration.getChangeListener(), appConfigDir);
        final ConfigurationManager configurationManager = new ConfigurationManager(configurationSource);
        final org.openecomp.sdc.be.config.Configuration configuration = new org.openecomp.sdc.be.config.Configuration();
        configuration.setJanusGraphInMemoryGraph(true);
        configurationManager.setConfiguration(configuration);
        ExternalConfiguration.setAppName("catalog-be");
    }

    @Override
    protected ResourceConfig configure() {
        MockitoAnnotations.openMocks(this);
        forceSet(TestProperties.CONTAINER_PORT, "0");
        final ApplicationContext context = new AnnotationConfigApplicationContext(SpringConfig.class);
        final UserValidations userValidations = new UserValidations(userBusinessLogic);
        return new ResourceConfig(TogglingServlet.class)
            .register(new AbstractBinder() {
                @Override
                protected void configure() {
                    bind(request).to(HttpServletRequest.class);
                    bind(componentInstanceBusinessLogic).to(ComponentInstanceBusinessLogic.class);
                    bind(componentsUtils).to(ComponentsUtils.class);
                    bind(servletUtils).to(ServletUtils.class);
                    bind(resourceImportManager).to(ResourceImportManager.class);
                    bind(togglingBusinessLogic).to(TogglingBusinessLogic.class);
                    bind(userValidations).to(UserValidations.class);
                }
            })
            .property("contextConfig", context);
    }

    @Test
    void setAllFeaturesWithoutUserIsRejected() {
        assertEquals(Status.BAD_REQUEST.getStatusCode(), put(ALL_FEATURES_URL, null));
        verify(togglingBusinessLogic, never()).setAllFeatures(anyBoolean());
    }

    @Test
    void setAllFeaturesByNonAdminIsRejected() {
        assertEquals(Status.FORBIDDEN.getStatusCode(), put(ALL_FEATURES_URL, DESIGNER_USER_ID));
        verify(togglingBusinessLogic, never()).setAllFeatures(anyBoolean());
    }

    @Test
    void setAllFeaturesByAdminSucceeds() {
        assertEquals(Status.OK.getStatusCode(), put(ALL_FEATURES_URL, ADMIN_USER_ID));
        verify(togglingBusinessLogic).setAllFeatures(false);
    }

    @Test
    void updateFeatureStateWithoutUserIsRejected() {
        assertEquals(Status.BAD_REQUEST.getStatusCode(), put(ONE_FEATURE_URL, null));
        verify(togglingBusinessLogic, never()).updateFeatureState(anyString(), anyBoolean());
    }

    @Test
    void updateFeatureStateByUnknownUserIsRejected() {
        when(userBusinessLogic.getUser("unknown")).thenThrow(new ByActionStatusComponentException(ActionStatus.USER_NOT_FOUND, "unknown"));
        assertEquals(Status.NOT_FOUND.getStatusCode(), put(ONE_FEATURE_URL, "unknown"));
        verify(togglingBusinessLogic, never()).updateFeatureState(anyString(), anyBoolean());
    }

    @Test
    void updateFeatureStateByNonAdminIsRejected() {
        assertEquals(Status.FORBIDDEN.getStatusCode(), put(ONE_FEATURE_URL, DESIGNER_USER_ID));
        verify(togglingBusinessLogic, never()).updateFeatureState(anyString(), anyBoolean());
    }

    @Test
    void updateFeatureStateByAdminSucceeds() {
        assertEquals(Status.OK.getStatusCode(), put(ONE_FEATURE_URL, ADMIN_USER_ID));
        verify(togglingBusinessLogic).updateFeatureState("HEALING", false);
    }

    @Test
    void updateUnknownFeatureStateIsRejected() {
        doThrow(new ByActionStatusComponentException(ActionStatus.INVALID_CONTENT))
            .when(togglingBusinessLogic).updateFeatureState("NOT_A_FEATURE", true);
        assertEquals(Status.BAD_REQUEST.getStatusCode(), put("/v1/catalog/toggle/NOT_A_FEATURE/state/true", ADMIN_USER_ID));
    }

    private int put(final String url, final String userId) {
        Invocation.Builder builder = target(url).request(MediaType.APPLICATION_JSON);
        if (userId != null) {
            builder = builder.header(Constants.USER_ID_HEADER, userId);
        }
        return builder.put(Entity.json("")).getStatus();
    }

    private static User user(final String userId, final Role role) {
        final User user = new User(userId);
        user.setRole(role.name());
        return user;
    }
}
