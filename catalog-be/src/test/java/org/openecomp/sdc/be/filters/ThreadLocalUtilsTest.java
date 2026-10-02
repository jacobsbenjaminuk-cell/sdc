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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.onap.sdc.security.PortalClient;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.api.UserRoleEnum;
import org.openecomp.sdc.common.datastructure.UserContext;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.common.util.ThreadLocalsHolder;

@ExtendWith(MockitoExtension.class)
class ThreadLocalUtilsTest {

    @Mock
    private PortalClient portalClient;
    @Mock
    private UserBusinessLogic userBusinessLogic;
    @Mock
    private HttpServletRequest request;
    @InjectMocks
    private ThreadLocalUtils threadLocalUtils;

    @BeforeAll
    static void initConfiguration() {
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(), "src/test/resources/config/catalog-be"));
    }

    @AfterEach
    void cleanUp() {
        ThreadLocalsHolder.setUserContext(null);
    }

    @Test
    void externalApiRequestWithoutUserIdHasNoUserContext() {
        ThreadLocalsHolder.setUserContext(new UserContext("stale", new HashSet<>(Collections.singletonList("ADMIN")), "f", "l"));
        when(request.getServletPath()).thenReturn("/sdc");

        threadLocalUtils.setUserContextFromDB(request);

        assertNull(ThreadLocalsHolder.getUserContext());
        verify(userBusinessLogic, never()).getUser(anyString(), anyBoolean());
    }

    @Test
    void externalApiRequestWithUserIdUsesThatUser() {
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("cs0008");
        when(userBusinessLogic.getUser("cs0008", false)).thenReturn(designer("cs0008"));

        threadLocalUtils.setUserContextFromDB(request);

        assertEquals("cs0008", ThreadLocalsHolder.getUserContext().getUserId());
    }

    @Test
    void internalApiRequestWithoutUserIdKeepsDefaultUser() {
        when(request.getServletPath()).thenReturn("/sdc2");
        when(request.getPathInfo()).thenReturn("/rest/v1/catalog/services");
        when(userBusinessLogic.getUser("cs0008", false)).thenReturn(designer("cs0008"));

        threadLocalUtils.setUserContextFromDB(request);

        assertEquals("cs0008", ThreadLocalsHolder.getUserContext().getUserId());
    }

    private static User designer(final String userId) {
        final User user = new User(userId);
        user.setRole(UserRoleEnum.DESIGNER.getName());
        return user;
    }
}
