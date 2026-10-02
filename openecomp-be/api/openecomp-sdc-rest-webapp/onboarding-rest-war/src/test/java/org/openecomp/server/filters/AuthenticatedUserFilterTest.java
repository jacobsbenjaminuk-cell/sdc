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
package org.openecomp.server.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openecomp.sdc.securityutil.AuthenticationCookie;
import org.openecomp.sdc.securityutil.AuthenticationCookieUtils;
import org.openecomp.sdc.securityutil.ISessionValidationFilterConfiguration;

class AuthenticatedUserFilterTest {

    private static final String COOKIE_NAME = "AuthenticationCookie";
    private static final String BASIC_AUTHENTICATED = BasicAuthenticationFilter.class.getName() + ".authenticated";

    private final Map<String, Object> attributes = new HashMap<>();
    private ISessionValidationFilterConfiguration configuration;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private AuthenticatedUserFilter filter;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        configuration = mock(ISessionValidationFilterConfiguration.class);
        lenient().when(configuration.getSecurityKey()).thenReturn(Base64.getEncoder().encodeToString(key));
        lenient().when(configuration.getCookieName()).thenReturn(COOKIE_NAME);
        lenient().when(configuration.getMaxSessionTimeOut()).thenReturn(86400000L);
        lenient().when(configuration.getSessionIdleTimeOut()).thenReturn(3600000L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn("/onboarding-api/v1.0/items/1/permissions/Owner");
        lenient().when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        lenient().when(request.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.<String>getArgument(0)));
        lenient().doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1))).when(request)
            .setAttribute(anyString(), any());
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        filter = new AuthenticatedUserFilter(() -> configuration);
    }

    @Test
    void rejectsUserIdWithoutAnyAuthentication() throws Exception {
        when(request.getHeader("USER_ID")).thenReturn("victim");

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsUserIdThatDiffersFromCookieUser() throws Exception {
        Cookie cookie = authenticationCookie("alice");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("USER_ID")).thenReturn("victim");

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void bindsUserFromPortalPrefixedCookie() throws Exception {
        Cookie cookie = new Cookie("EPPortal" + COOKIE_NAME, authenticationCookie("alice").getValue());
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("USER_ID")).thenReturn("alice");

        HttpServletRequest forwarded = runAndCaptureForwardedRequest();

        assertEquals("alice", forwarded.getHeader("USER_ID"));
    }

    @Test
    void rejectsUserIdThatDiffersFromPrincipal() throws Exception {
        when(request.getUserPrincipal()).thenReturn(() -> "alice");
        when(request.getHeader("USER_ID")).thenReturn("victim");

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsCookieThatCannotBeDecrypted() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "forged")});
        when(request.getHeader("USER_ID")).thenReturn("victim");
        attributes.put(BASIC_AUTHENTICATED, Boolean.TRUE);

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void bindsCookieUserWhenHeaderMatches() throws Exception {
        Cookie cookie = authenticationCookie("alice");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        when(request.getHeader("USER_ID")).thenReturn("alice");

        HttpServletRequest forwarded = runAndCaptureForwardedRequest();

        assertEquals("alice", forwarded.getHeader("USER_ID"));
        assertEquals("alice", attributes.get(AuthenticatedUserFilter.AUTHENTICATED_USER_ATTRIBUTE));
    }

    @Test
    void suppliesCookieUserWhenHeaderMissing() throws Exception {
        Cookie cookie = authenticationCookie("alice");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        HttpServletRequest forwarded = runAndCaptureForwardedRequest();

        assertEquals("alice", forwarded.getHeader("user_id"));
        assertEquals("alice", forwarded.getHeaders("USER_ID").nextElement());
        assertTrue(Collections.list(forwarded.getHeaderNames()).contains("USER_ID"));
    }

    @Test
    void acceptsUserIdAssertedByBasicAuthenticatedCaller() throws Exception {
        when(request.getHeader("USER_ID")).thenReturn("cs0008");
        attributes.put(BASIC_AUTHENTICATED, Boolean.TRUE);

        HttpServletRequest forwarded = runAndCaptureForwardedRequest();

        assertEquals("cs0008", forwarded.getHeader("USER_ID"));
        assertEquals("cs0008", attributes.get(AuthenticatedUserFilter.AUTHENTICATED_USER_ATTRIBUTE));
    }

    @Test
    void passesRequestsThatAssertNoUser() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
        assertEquals(null, attributes.get(AuthenticatedUserFilter.AUTHENTICATED_USER_ATTRIBUTE));
    }

    @Test
    void sessionContextUsesBoundUserNotHeader() {
        when(request.getHeader("USER_ID")).thenReturn("victim");
        attributes.put(AuthenticatedUserFilter.AUTHENTICATED_USER_ATTRIBUTE, "alice");

        assertEquals("alice", new OnboardingSessionContextFilter().getUser(request));
    }

    private HttpServletRequest runAndCaptureForwardedRequest() throws Exception {
        filter.doFilter(request, response, chain);
        ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(captor.capture(), eq(response));
        verify(response, never()).sendError(anyInt(), anyString());
        return (HttpServletRequest) captor.getValue();
    }

    private Cookie authenticationCookie(String userId) throws Exception {
        AuthenticationCookie authenticationCookie = new AuthenticationCookie(userId);
        return new Cookie(COOKIE_NAME, AuthenticationCookieUtils.getEncryptedCookie(authenticationCookie, configuration));
    }
}
