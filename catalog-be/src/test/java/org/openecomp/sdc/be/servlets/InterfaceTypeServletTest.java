/*
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fj.data.Either;
import java.util.List;
import javax.servlet.ServletContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.components.validation.UserValidations;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.WebAppContextWrapper;
import org.openecomp.sdc.be.model.InterfaceDefinition;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.model.operations.impl.InterfaceLifecycleOperation;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.be.user.Role;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.exception.ResponseFormat;
import org.springframework.web.context.WebApplicationContext;

class InterfaceTypeServletTest extends JerseySpringBaseTest {

    private static final String USER_ID = "jh0003";
    private static final String INTERFACE_TYPE_ID = "tosca.interfaces.Custom.interface";
    private static final String PATH = "/v1/catalog/interface-types/" + INTERFACE_TYPE_ID;

    private ComponentsUtils componentsUtils;
    private InterfaceLifecycleOperation interfaceLifecycleOperation;
    private UserValidations userValidations;
    private ServletContext servletContext;
    private WebApplicationContext webApplicationContext;
    private WebAppContextWrapper webAppContextWrapper;

    @Override
    protected ResourceConfig configure() {
        componentsUtils = mock(ComponentsUtils.class);
        interfaceLifecycleOperation = mock(InterfaceLifecycleOperation.class);
        userValidations = mock(UserValidations.class);
        servletContext = mock(ServletContext.class);
        webApplicationContext = mock(WebApplicationContext.class);
        webAppContextWrapper = mock(WebAppContextWrapper.class);
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return super.configure().register(new InterfaceTypeServlet(componentsUtils, interfaceLifecycleOperation, userValidations));
    }

    @BeforeEach
    void before() throws Exception {
        super.setUp();
        when(request.getSession()).thenReturn(session);
        when(session.getServletContext()).thenReturn(servletContext);
        when(webApplicationContext.getBean(ComponentsUtils.class)).thenReturn(componentsUtils);
        when(servletContext.getAttribute(Constants.WEB_APPLICATION_CONTEXT_WRAPPER_ATTR)).thenReturn(webAppContextWrapper);
        when(webAppContextWrapper.getWebAppContext(servletContext)).thenReturn(webApplicationContext);
    }

    @AfterEach
    void after() throws Exception {
        super.tearDown();
    }

    @Test
    void deleteInterfaceType_Success_Admin() {
        final User user = mockUser();
        final InterfaceDefinition interfaceDefinition = buildInterfaceType(true);
        final ResponseFormat noContent = new ResponseFormat(HttpStatus.SC_NO_CONTENT);
        when(componentsUtils.getResponseFormat(ActionStatus.NO_CONTENT)).thenReturn(noContent);
        when(interfaceLifecycleOperation.getInterface(INTERFACE_TYPE_ID)).thenReturn(Either.left(interfaceDefinition));

        final Response response = deleteRequest(USER_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
        verify(userValidations).validateUserRole(user, List.of(Role.ADMIN));
        verify(interfaceLifecycleOperation).deleteInterfaceTypeById(INTERFACE_TYPE_ID);
        verify(interfaceLifecycleOperation).removeInterfaceTypeFromAdditionalType(interfaceDefinition);
        verify(componentsUtils).auditResource(noContent, user, INTERFACE_TYPE_ID, AuditingActionEnum.DELETE_INTERFACE_TYPE);
    }

    @Test
    void deleteInterfaceType_Fail_MissingUserId() {
        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .delete(Response.class);

        assertNotEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
        verify(userValidations, never()).validateUserExists(anyString());
        verify(interfaceLifecycleOperation, never()).getInterface(anyString());
        verify(interfaceLifecycleOperation, never()).deleteInterfaceTypeById(anyString());
    }

    @Test
    void deleteInterfaceType_Fail_NotAdmin() {
        final User user = mockUser();
        final ResponseFormat restricted = new ResponseFormat(HttpStatus.SC_FORBIDDEN);
        when(componentsUtils.getResponseFormat(ActionStatus.RESTRICTED_OPERATION)).thenReturn(restricted);
        doThrow(new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION))
            .when(userValidations).validateUserRole(user, List.of(Role.ADMIN));

        final Response response = deleteRequest(USER_ID);

        assertNotEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
        verify(interfaceLifecycleOperation, never()).getInterface(anyString());
        verify(interfaceLifecycleOperation, never()).deleteInterfaceTypeById(anyString());
        verify(interfaceLifecycleOperation, never()).removeInterfaceTypeFromAdditionalType(any());
        verify(componentsUtils).auditResource(restricted, user, INTERFACE_TYPE_ID, AuditingActionEnum.DELETE_INTERFACE_TYPE);
    }

    @Test
    void deleteInterfaceType_Fail_NormativeType() {
        final User user = mockUser();
        final ResponseFormat cannotDelete = new ResponseFormat(HttpStatus.SC_FORBIDDEN);
        when(componentsUtils.getResponseFormat(ActionStatus.CANNOT_DELETE_SYSTEM_DEPLOYED_RESOURCES, "interface_types", INTERFACE_TYPE_ID))
            .thenReturn(cannotDelete);
        when(interfaceLifecycleOperation.getInterface(INTERFACE_TYPE_ID)).thenReturn(Either.left(buildInterfaceType(false)));

        final Response response = deleteRequest(USER_ID);

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
        verify(interfaceLifecycleOperation, never()).deleteInterfaceTypeById(anyString());
        verify(componentsUtils).auditResource(cannotDelete, user, INTERFACE_TYPE_ID, AuditingActionEnum.DELETE_INTERFACE_TYPE);
    }

    private Response deleteRequest(final String userId) {
        return target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header(Constants.USER_ID_HEADER, userId)
            .delete(Response.class);
    }

    private User mockUser() {
        final User user = new User();
        user.setUserId(USER_ID);
        user.setRole(Role.ADMIN.name());
        when(userValidations.validateUserExists(USER_ID)).thenReturn(user);
        return user;
    }

    private InterfaceDefinition buildInterfaceType(final boolean userCreated) {
        final InterfaceDefinition interfaceDefinition = new InterfaceDefinition();
        interfaceDefinition.setUniqueId(INTERFACE_TYPE_ID);
        interfaceDefinition.setUserCreated(userCreated);
        return interfaceDefinition;
    }
}
