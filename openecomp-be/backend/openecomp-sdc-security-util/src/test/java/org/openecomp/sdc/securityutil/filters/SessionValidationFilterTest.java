/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.sdc.securityutil.filters; 

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.openecomp.sdc.securityutil.AuthenticationCookie;
import org.openecomp.sdc.securityutil.AuthenticationCookieUtils;
import org.openecomp.sdc.securityutil.CipherUtilException;
import org.openecomp.sdc.securityutil.RepresentationUtils;
import org.openecomp.sdc.securityutil.filters.ResponceWrapper;
import org.openecomp.sdc.securityutil.filters.SampleFilter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SessionValidationFilterTest {

    @Mock
    private HttpServletRequest request;
    @Spy
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private FilterConfig filterConfig;
    @Mock
    private ResponceWrapper responceWrapper;

    // implementation of SessionValidationFilter
    @InjectMocks
    @Spy
    private SampleFilter sessionValidationFilter = new SampleFilter();

    @Before
    public void setUpClass() throws ServletException {
        sessionValidationFilter.init(filterConfig);
    }

    @Test
    public void excludedUrlHealthcheck() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/healthCheck");
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void excludedUrlUpload() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/upload/123");
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(filterChain, times(1)).doFilter(request, response);
    }

    // case when url pattern in web.xml is forward slash (/)
    @Test
    public void pathInfoIsNull() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/upload/2");
        when(request.getPathInfo()).thenReturn(null);
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    public void noCookiesInRequest() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/resource");
        when(request.getCookies()).thenReturn(new Cookie[0]);
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
    }

    @Test
    public void nullCookiesInRequest() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/resource");
        when(request.getCookies()).thenReturn(null);
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
    }

    @Test
    public void noCookiesWithCorrectNameInRequest() throws IOException, ServletException {
        when(request.getPathInfo()).thenReturn("/resource");
        String newNameNotContainsRealName = sessionValidationFilter.getFilterConfiguration().getCookieName().substring(1);
        Cookie cookie = new Cookie("fake" + newNameNotContainsRealName + "fake2", RepresentationUtils.toRepresentation(new AuthenticationCookie("kuku")));
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
    }

    @Test
    public void cookieMaxSessionTimeTimedOut() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");
        AuthenticationCookie authenticationCookie = new AuthenticationCookie("kuku");
        // set max session time to timout value
        long maxSessionTimeOut = sessionValidationFilter.getFilterConfiguration().getMaxSessionTimeOut();
        long startTime = authenticationCookie.getMaxSessionTime();
        long timeout = startTime - maxSessionTimeOut - 1000l;
        authenticationCookie.setMaxSessionTime(timeout);
        Cookie cookie = new Cookie(sessionValidationFilter.getFilterConfiguration().getCookieName(), AuthenticationCookieUtils
            .getEncryptedCookie(authenticationCookie, sessionValidationFilter.getFilterConfiguration()));

        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
    }

    @Test
    public void cookieSessionIdle() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");
        AuthenticationCookie authenticationCookie = new AuthenticationCookie("kuku");
        // set session time to timout to idle
        long idleSessionTimeOut = sessionValidationFilter.getFilterConfiguration().getSessionIdleTimeOut();
        long sessionStartTime = authenticationCookie.getCurrentSessionTime();
        long timeout = sessionStartTime - idleSessionTimeOut - 2000;
        authenticationCookie.setCurrentSessionTime(timeout);
        Cookie cookie = new Cookie(sessionValidationFilter.getFilterConfiguration().getCookieName(), AuthenticationCookieUtils.getEncryptedCookie(authenticationCookie, sessionValidationFilter.getFilterConfiguration()));

        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
    }

    @Test
    public void requestThatPassFilter() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");

        AuthenticationCookie authenticationCookie = new AuthenticationCookie("kuku");
        Cookie cookie = new Cookie(sessionValidationFilter.getFilterConfiguration().getCookieName(), AuthenticationCookieUtils.getEncryptedCookie(authenticationCookie, sessionValidationFilter.getFilterConfiguration()));

        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        assertAuthenticatedUserForwarded("kuku");
    }

    @Test
    public void userIdHeaderIsReplacedByCookieUser() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");
        when(request.getHeader("USER_ID")).thenReturn("jh0003");
        AuthenticationCookie authenticationCookie = new AuthenticationCookie("kuku");
        Cookie cookie = new Cookie(sessionValidationFilter.getFilterConfiguration().getCookieName(), AuthenticationCookieUtils.getEncryptedCookie(authenticationCookie, sessionValidationFilter.getFilterConfiguration()));
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        assertAuthenticatedUserForwarded("kuku");
    }

    private void assertAuthenticatedUserForwarded(String userId) throws IOException, ServletException {
        ArgumentCaptor<ServletRequest> forwarded = ArgumentCaptor.forClass(ServletRequest.class);
        Mockito.verify(filterChain, times(1)).doFilter(forwarded.capture(), Mockito.eq(response));
        assertEquals(userId, ((HttpServletRequest) forwarded.getValue()).getHeader("USER_ID"));
    }

    @Test
    public void cookieNameMustMatchExactly() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");

        AuthenticationCookie authenticationCookie = new AuthenticationCookie("kuku");
        Cookie cookie = new Cookie("some" +sessionValidationFilter.getFilterConfiguration().getCookieName() + "Thing", AuthenticationCookieUtils.getEncryptedCookie(authenticationCookie, sessionValidationFilter.getFilterConfiguration()));

        when(request.getCookies()).thenReturn(new Cookie[]{cookie});
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
        Mockito.verify(filterChain, Mockito.never()).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    public void multipleAuthenticationCookiesAreRejected() throws IOException, ServletException, CipherUtilException {
        when(request.getPathInfo()).thenReturn("/resource");
        String cookieName = sessionValidationFilter.getFilterConfiguration().getCookieName();
        Cookie first = new Cookie(cookieName, AuthenticationCookieUtils.getEncryptedCookie(new AuthenticationCookie("kuku"), sessionValidationFilter.getFilterConfiguration()));
        Cookie second = new Cookie(cookieName, AuthenticationCookieUtils.getEncryptedCookie(new AuthenticationCookie("jh0003"), sessionValidationFilter.getFilterConfiguration()));

        when(request.getCookies()).thenReturn(new Cookie[]{first, second});
        sessionValidationFilter.doFilter(request, response, filterChain);
        Mockito.verify(response, times(1)).sendRedirect(sessionValidationFilter.getFilterConfiguration().getRedirectURL());
        Mockito.verify(filterChain, Mockito.never()).doFilter(Mockito.any(), Mockito.any());
    }

}
