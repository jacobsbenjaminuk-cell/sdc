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

import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.util.ThreadLocalsHolder;

@ExtendWith(MockitoExtension.class)
class ThreadLocalUtilsTest {

    @Mock
    private UserBusinessLogic userBusinessLogic;
    @Mock
    private HttpServletRequest request;
    @InjectMocks
    private ThreadLocalUtils threadLocalUtils;

    @BeforeEach
    @AfterEach
    void clearUserContext() {
        ThreadLocalsHolder.setUserContext(null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void doesNotAssumeAnyUserWhenTheRequestCarriesNone(final String userId) {
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn(userId);

        threadLocalUtils.setUserContextFromDB(request);

        assertNull(ThreadLocalsHolder.getUserContext());
        verify(userBusinessLogic, never()).getUser(anyString(), anyBoolean());
    }

    @Test
    void loadsTheUserNamedInTheRequest() {
        final User user = new User("Ann", "Bee", "ab0001", "ab@sdc.com", "DESIGNER", null);
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("ab0001");
        when(userBusinessLogic.getUser("ab0001", false)).thenReturn(user);

        threadLocalUtils.setUserContextFromDB(request);

        assertEquals("ab0001", ThreadLocalsHolder.getUserContext().getUserId());
    }
}
