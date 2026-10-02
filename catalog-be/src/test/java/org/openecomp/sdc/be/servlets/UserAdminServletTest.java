/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ==============================================================================
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fj.data.Either;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openecomp.sdc.be.auditing.impl.AuditingManager;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.utils.UserStatusEnum;
import org.openecomp.sdc.be.facade.operations.UserOperation;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.model.operations.impl.UserAdminOperation;
import org.openecomp.sdc.be.servlets.exception.ComponentExceptionMapper;
import org.openecomp.sdc.be.user.Role;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.be.user.UserBusinessLogicExt;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class UserAdminServletTest extends JerseyTest {

    private HttpServletRequest request;
    private UserAdminOperation userAdminOperation;

    @Override
    protected ResourceConfig configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(),
            "src/test/resources/config/catalog-be"));
        request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/v1/user/users");
        userAdminOperation = mock(UserAdminOperation.class);
        when(userAdminOperation.getUserData(anyString(), anyBoolean())).thenReturn(Either.right(ActionStatus.USER_NOT_FOUND));
        final ComponentsUtils componentsUtils = new ComponentsUtils(mock(AuditingManager.class));
        final UserBusinessLogic userBusinessLogic = new UserBusinessLogic(userAdminOperation, componentsUtils, mock(UserOperation.class));
        return new ResourceConfig()
            .register(new UserAdminServlet(userBusinessLogic, componentsUtils, mock(UserBusinessLogicExt.class)))
            .register(new ComponentExceptionMapper(componentsUtils))
            .register(new AbstractBinder() {
                @Override
                protected void configure() {
                    bind(request).to(HttpServletRequest.class);
                }
            })
            .property("contextConfig", new AnnotationConfigApplicationContext(BaseTestConfig.class));
    }

    @BeforeEach
    void startServer() throws Exception {
        super.setUp();
        addUser("otheruser", Role.ADMIN, UserStatusEnum.ACTIVE);
        clearInvocations(userAdminOperation);
    }

    @AfterEach
    void stopServer() throws Exception {
        super.tearDown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"otheruser", "otheruser/role", "admins", "users", "users?roles=ADMIN"})
    void rejectsMissingOrBlankCaller(String path) {
        for (String caller : new String[] {null, "", " "}) {
            assertRead(path, caller, 401);
            verifyNoInteractions(userAdminOperation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"otheruser", "otheruser/role", "admins", "users", "users?roles=ADMIN"})
    void rejectsUnknownCaller(String path) {
        assertRead(path, "unknownuser", 404);
        verifyNoDirectoryRead();
    }

    @ParameterizedTest
    @ValueSource(strings = {"otheruser", "otheruser/role", "admins", "users", "users?roles=ADMIN"})
    void rejectsInactiveAdministrator(String path) {
        addUser("caller", Role.ADMIN, UserStatusEnum.INACTIVE);
        assertRead(path, "caller", 403);
        verifyNoDirectoryRead();
    }

    @ParameterizedTest
    @ValueSource(strings = {"otheruser", "otheruser/role", "admins", "users", "users?roles=ADMIN"})
    void rejectsOtherUserReadsForEveryNonAdminRole(String path) {
        for (Role role : Role.values()) {
            if (role != Role.ADMIN) {
                addUser("caller", role, UserStatusEnum.ACTIVE);
                assertRead(path, "caller", 403);
                verifyNoDirectoryRead();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"caller", "caller/role"})
    void allowsOwnRecordForEveryActiveRole(String path) {
        for (Role role : Role.values()) {
            addUser("caller", role, UserStatusEnum.ACTIVE);
            assertRead(path, "caller", 200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"caller", "caller/role"})
    void rejectsInactiveSelfRead(String path) {
        addUser("caller", Role.DESIGNER, UserStatusEnum.INACTIVE);
        assertRead(path, "caller", 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"otheruser", "otheruser/role", "admins", "users", "users?roles=ADMIN"})
    void allowsAdministratorReads(String path) {
        addUser("caller", Role.ADMIN, UserStatusEnum.ACTIVE);
        User otherUser = addUser("otheruser", Role.ADMIN, UserStatusEnum.ACTIVE);
        when(userAdminOperation.getAllUsersWithRole(any(), any())).thenReturn(Either.left(List.of(otherUser)));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("caller");
        try (Response response = read(path, "caller")) {
            assertEquals(200, response.getStatus());
            final String body = response.readEntity(String.class);
            assertTrue(body.contains(path.endsWith("/role") ? "ADMIN" : "otheruser"));
        }
    }

    private User addUser(String userId, Role role, UserStatusEnum status) {
        User user = new User(userId);
        user.setRole(role.name());
        user.setStatus(status);
        user.setEmail(userId + "@example.com");
        when(userAdminOperation.getUserData(userId, false)).thenReturn(Either.left(user));
        return user;
    }

    private void assertRead(String path, String caller, int expectedStatus) {
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn(caller);
        try (Response response = read(path, caller)) {
            assertEquals(expectedStatus, response.getStatus());
            if (expectedStatus != 200) {
                assertFalse(response.readEntity(String.class).contains("otheruser@example.com"));
            }
        }
    }

    private Response read(String path, String caller) {
        String[] parts = path.split("\\?", 2);
        Invocation.Builder builder = parts.length == 2
            ? target("/v1/user/" + parts[0]).queryParam("roles", "ADMIN").request(MediaType.APPLICATION_JSON)
            : target("/v1/user/" + path).request(MediaType.APPLICATION_JSON);
        if (caller != null) {
            builder.header(Constants.USER_ID_HEADER, caller);
        }
        return builder.get();
    }

    private void verifyNoDirectoryRead() {
        verify(userAdminOperation, never()).getUserData("otheruser", false);
        verify(userAdminOperation, never()).getAllUsersWithRole(any(), any());
    }
}
