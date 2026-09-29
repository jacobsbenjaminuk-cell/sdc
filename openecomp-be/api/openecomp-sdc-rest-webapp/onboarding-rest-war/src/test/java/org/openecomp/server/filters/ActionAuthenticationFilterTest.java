/*
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
 */
package org.openecomp.server.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActionAuthenticationFilterTest {

    private static final Map<?, ?> CONFIGURATION = Map.of(ActionAuthenticationFilter.CONFIG_SECTION, Map.of("users", List.of(
        Map.of("userName", "reader", "userPass", "readerPass", "privilege", "RETRIEVE"),
        Map.of("userName", "admin", "userPass", "adminPass", "privilege", "delete"))));

    private final ActionAuthenticationFilter filter = new ActionAuthenticationFilter(
        ActionAuthenticationFilter.parseUsers(CONFIGURATION));

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpServletRequest request(String method, String authorization) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeader("Authorization")).thenReturn(authorization);
        return request;
    }

    private void assertRejected(ActionAuthenticationFilter filterUnderTest, String authorization) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        filterUnderTest.doFilter(request("DELETE", authorization), response, chain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    private HttpServletRequest authenticate(String method, String authorization) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        filter.doFilter(request(method, authorization), response, chain);
        ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(captor.capture(), any(ServletResponse.class));
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return (HttpServletRequest) captor.getValue();
    }

    @Test
    void rejectsAuthPrefixedUsernameWithoutConfiguredPassword() throws Exception {
        assertRejected(filter, basic("AUTH-DELETE:anything"));
        assertRejected(filter, basic("AUTHx-DELETE:anything"));
    }

    @Test
    void rejectsWrongPasswordMissingOrMalformedHeader() throws Exception {
        assertRejected(filter, basic("admin:wrong"));
        assertRejected(filter, basic("admin:adminPass "));
        assertRejected(filter, basic("admin"));
        assertRejected(filter, "Basic %%%not-base64");
        assertRejected(filter, "Bearer token");
        assertRejected(filter, null);
    }

    @Test
    void rejectsEverythingWhenNoUsersConfigured() throws Exception {
        ActionAuthenticationFilter unconfigured = new ActionAuthenticationFilter(ActionAuthenticationFilter.parseUsers(Map.of()));
        assertRejected(unconfigured, basic("AUTH-DELETE:anything"));
        assertRejected(unconfigured, basic(":"));
        assertRejected(new ActionAuthenticationFilter(), basic("admin:adminPass"));
    }

    @Test
    void invalidUserEntriesAreSkippedWithoutDroppingValidUsers() throws Exception {
        ActionAuthenticationFilter mixed = new ActionAuthenticationFilter(ActionAuthenticationFilter.parseUsers(
            Map.of(ActionAuthenticationFilter.CONFIG_SECTION, Map.of("users", List.of(
                Map.of("userName", "broken", "userPass", "brokenPass", "privilege", "SUPERUSER"),
                Map.of("userName", "incomplete", "userPass", "incompletePass"),
                Map.of("userName", "admin", "userPass", "adminPass", "privilege", "DELETE"))))));
        assertRejected(mixed, basic("broken:brokenPass"));
        assertRejected(mixed, basic("incomplete:incompletePass"));
        FilterChain chain = mock(FilterChain.class);
        mixed.doFilter(request("GET", basic("admin:adminPass")), mock(HttpServletResponse.class), chain);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void privilegeComesFromConfigurationNotUsername() throws Exception {
        HttpServletRequest reader = authenticate("GET", basic("reader:readerPass"));
        assertEquals("reader", reader.getRemoteUser());
        assertEquals("reader", reader.getUserPrincipal().getName());
        assertTrue(reader.isUserInRole("GET"));
        assertFalse(reader.isUserInRole("POST"));
        assertFalse(reader.isUserInRole("PUT"));
        assertFalse(reader.isUserInRole("DELETE"));
        assertFalse(reader.isUserInRole("OPTIONS"));
        assertFalse(reader.isUserInRole(null));
    }

    @Test
    void higherPrivilegeIncludesLowerOnes() throws Exception {
        HttpServletRequest admin = authenticate("DELETE", basic("admin:adminPass"));
        assertEquals("admin", admin.getRemoteUser());
        assertTrue(admin.isUserInRole("GET"));
        assertTrue(admin.isUserInRole("POST"));
        assertTrue(admin.isUserInRole("PUT"));
        assertTrue(admin.isUserInRole("DELETE"));
    }

    @Test
    void authorizationFilterDeniesRequestsWithoutAuthenticatedPrincipal() throws Exception {
        ActionAuthorizationFilter authorizationFilter = new ActionAuthorizationFilter();
        HttpServletRequest unauthenticated = request("GET", null);
        when(unauthenticated.isUserInRole("GET")).thenReturn(true);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        authorizationFilter.doFilter(unauthenticated, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void authorizationFilterEnforcesConfiguredPrivilege() throws Exception {
        ActionAuthorizationFilter authorizationFilter = new ActionAuthorizationFilter();
        HttpServletRequest reader = authenticate("DELETE", basic("reader:readerPass"));
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        authorizationFilter.doFilter(reader, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());

        HttpServletRequest admin = authenticate("DELETE", basic("admin:adminPass"));
        FilterChain adminChain = mock(FilterChain.class);
        authorizationFilter.doFilter(admin, mock(HttpServletResponse.class), adminChain);
        verify(adminChain).doFilter(any(), any());
    }
}
