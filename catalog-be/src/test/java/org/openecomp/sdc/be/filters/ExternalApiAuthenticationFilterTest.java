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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fj.data.Either;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openecomp.sdc.be.components.impl.exceptions.ComponentException;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphGenericDao;
import org.openecomp.sdc.be.datatypes.elements.ConsumerDataDefinition;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.model.operations.impl.ConsumerOperation;
import org.openecomp.sdc.be.resources.data.ConsumerData;
import org.openecomp.sdc.be.servlets.exception.ComponentExceptionMapper;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalApiAuthenticationFilterTest {

    private static final String CONSUMER = "testConsumer";
    private static final String PASSWORD = "123456";
    private static final String SALT = "2a1f887d607d4515d4066fe0f5452a50";
    private static final String HASHED_PASSWORD = "0a0dc557c3bf594b1a48030e3e99227580168b21f44e285c69740b8d5b13e33b";
    private static final String ALLOWED_USER = "cs0008";

    @Mock
    private ConsumerOperation consumerOperation;
    @Mock
    private JanusGraphGenericDao janusGraphGenericDao;
    @Mock
    private ComponentExceptionMapper componentExceptionMapper;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private ExternalApiAuthenticationFilter filter;

    @BeforeAll
    static void initConfiguration() {
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(), "src/test/resources/config/catalog-be"));
    }

    @BeforeEach
    void setUp() {
        filter = new ExternalApiAuthenticationFilter(consumerOperation, janusGraphGenericDao, componentExceptionMapper);
        final ConsumerDataDefinition consumer = new ConsumerDataDefinition();
        consumer.setConsumerName(CONSUMER);
        consumer.setConsumerSalt(SALT);
        consumer.setConsumerPassword(HASHED_PASSWORD);
        when(consumerOperation.getCredentials(CONSUMER)).thenReturn(Either.left(new ConsumerData(consumer)));
        when(consumerOperation.getCredentials("unknown")).thenReturn(Either.right(StorageOperationStatus.NOT_FOUND));
    }

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws IOException, ServletException {
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn(ALLOWED_USER);

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.AUTH_REQUIRED);
        verify(response).setHeader(eq("WWW-Authenticate"), any());
    }

    @Test
    void rejectsNonBasicAuthorizationHeader() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn("Bearer token");

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.AUTH_FAILED_INVALIDE_HEADER);
    }

    @Test
    void rejectsMalformedBasicCredentials() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn("Basic " + encode("no-separator"));

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.AUTH_FAILED_INVALIDE_HEADER);
    }

    @Test
    void rejectsWrongPassword() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(CONSUMER, "wrong"));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn(ALLOWED_USER);

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.AUTH_FAILED);
    }

    @Test
    void rejectsUnknownConsumer() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic("unknown", PASSWORD));

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.AUTH_FAILED);
        verify(janusGraphGenericDao).commit();
    }

    @Test
    void rejectsUserIdNotAllowedForConsumer() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(CONSUMER, PASSWORD));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn("jh0003");

        filter.doFilter(request, response, filterChain);

        assertRejectedWith(ActionStatus.RESTRICTED_OPERATION);
    }

    @Test
    void acceptsAllowedUserIdForAuthenticatedConsumer() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(CONSUMER, PASSWORD));
        when(request.getHeader(Constants.USER_ID_HEADER)).thenReturn(ALLOWED_USER);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(componentExceptionMapper, never()).writeToResponse(any(), any());
    }

    @Test
    void acceptsAuthenticatedConsumerWithoutUserId() throws IOException, ServletException {
        when(request.getHeader(Constants.AUTHORIZATION_HEADER)).thenReturn(basic(CONSUMER, PASSWORD));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private void assertRejectedWith(final ActionStatus expected) throws IOException, ServletException {
        final ArgumentCaptor<ComponentException> captor = ArgumentCaptor.forClass(ComponentException.class);
        verify(componentExceptionMapper).writeToResponse(captor.capture(), eq(response));
        assertEquals(expected, captor.getValue().getActionStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    private static String basic(final String name, final String password) {
        return "Basic " + encode(name + ":" + password);
    }

    private static String encode(final String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
