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

package org.openecomp.sdc.webseal.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openecomp.sdc.webseal.simulator.conf.Conf;

class SimulatorIdentityTest {

    private static final String DESIGNER = "cs0008";
    private static final String ADMIN = "jh0003";
    private static final String PASSWORD = "s3cret-pass";

    private HttpServer fe;
    private final AtomicInteger feHits = new AtomicInteger();
    private final AtomicReference<Map<String, List<String>>> feHeaders = new AtomicReference<>();
    private SdcProxy proxy;

    @BeforeEach
    void setUp() throws Exception {
        final Map<String, User> users = Conf.getInstance().getUsers();
        users.clear();
        users.put(DESIGNER, new User("Carlos", "Santana", "cs@sdc.com", DESIGNER, "Designer", PASSWORD));
        users.put(ADMIN, new User("Jimmy", "Hendrix", "admin@sdc.com", ADMIN, "Admin", PASSWORD));

        fe = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fe.createContext("/", exchange -> {
            feHits.incrementAndGet();
            feHeaders.set(new HashMap<>(exchange.getRequestHeaders()));
            final byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fe.start();
        Conf.getInstance().setFeHost("http://127.0.0.1:" + fe.getAddress().getPort());
        Conf.getInstance().setPortalCookieName("EPService");

        proxy = new SdcProxy();
        proxy.init(mock(ServletConfig.class));
    }

    @AfterEach
    void tearDown() {
        fe.stop(0);
        Conf.getInstance().getUsers().clear();
    }

    @Test
    void proxyIgnoresUserIdHeaderWithoutSession() throws Exception {
        final HttpServletRequest request = proxiedGet(Map.of("USER_ID", ADMIN), null);
        final HttpServletResponse response = response();

        proxy.doGet(request, response);

        verify(response).sendRedirect("/login");
        assertEquals(0, feHits.get());
    }

    @Test
    void proxyIgnoresUserIdCookieWithoutSession() throws Exception {
        final HttpServletRequest request = proxiedGet(Map.of(), null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", ADMIN), new Cookie("HTTP_IV_USER", ADMIN)});
        final HttpServletResponse response = response();

        proxy.doGet(request, response);

        verify(response).sendRedirect("/login");
        assertEquals(0, feHits.get());
    }

    @Test
    void proxyIgnoresUserIdParameterOnPostWithoutSession() throws Exception {
        final HttpServletRequest request = proxiedGet(Map.of("Content-Type", "application/json"), null);
        when(request.getParameter("userId")).thenReturn(ADMIN);
        when(request.getContentType()).thenReturn("application/json");
        final HttpServletResponse response = response();

        proxy.doPost(request, response);

        verify(response).sendRedirect("/login");
        assertEquals(0, feHits.get());
    }

    @Test
    void proxyForwardsSessionUserAndDropsSpoofedIdentityHeadersInAnyCase() throws Exception {
        final Map<String, String> headers = new HashMap<>();
        headers.put("user_id", ADMIN);
        headers.put("Http_Iv_User", ADMIN);
        headers.put("X-Other", "kept");
        final HttpServletRequest request = proxiedGet(headers, sessionFor(DESIGNER));
        final HttpServletResponse response = response();

        proxy.doGet(request, response);

        verify(response, never()).sendRedirect(anyString());
        assertEquals(1, feHits.get());
        final Map<String, List<String>> received = feHeaders.get();
        assertEquals(List.of(DESIGNER), headerValues(received, "USER_ID"));
        assertEquals(List.of(DESIGNER), headerValues(received, "HTTP_IV_USER"));
        assertEquals(List.of("kept"), headerValues(received, "X-Other"));
    }

    @Test
    void proxyRedirectsWhenSessionUserIsNoLongerConfigured() throws Exception {
        final HttpServletRequest request = proxiedGet(Map.of(), sessionFor("removed-user"));
        final HttpServletResponse response = response();

        proxy.doGet(request, response);

        verify(response).sendRedirect("/login");
        assertEquals(0, feHits.get());
    }

    @Test
    void loginWithCorrectPasswordStartsNewSession() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpSession oldSession = mock(HttpSession.class);
        final HttpSession newSession = mock(HttpSession.class);
        when(request.getParameter("userId")).thenReturn(DESIGNER);
        when(request.getParameter("password")).thenReturn(PASSWORD);
        when(request.getSession(false)).thenReturn(oldSession);
        when(request.getSession(true)).thenReturn(newSession);
        final HttpServletResponse response = response();

        new Login().doPost(request, response);

        verify(oldSession).invalidate();
        verify(newSession).setAttribute(SimulatorSession.USER_ID_ATTRIBUTE, DESIGNER);
        verify(response).sendRedirect("/sdc1");
    }

    @Test
    void loginWithWrongOrMissingPasswordCreatesNoSession() throws Exception {
        for (final String password : new String[]{"wrong", null}) {
            final HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getParameter("userId")).thenReturn(ADMIN);
            when(request.getParameter("password")).thenReturn(password);
            final HttpServletResponse response = response();

            new Login().doPost(request, response);

            verify(request, never()).getSession(anyBoolean());
            verify(request, never()).getSession();
            verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "ERROR: userId or password incorrect");
            verify(response, never()).addCookie(org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void loginPageNeitherAcceptsQueryCredentialsNorPrintsPasswords() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("userId")).thenReturn(DESIGNER);
        when(request.getParameter("password")).thenReturn(PASSWORD);
        final HttpServletResponse response = response();
        final StringWriter page = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(page));

        new Login().doGet(request, response);

        verify(request, never()).getSession(anyBoolean());
        verify(response, never()).sendRedirect(anyString());
        assertTrue(page.toString().contains(DESIGNER));
        assertFalse(page.toString().contains(PASSWORD));
    }

    @Test
    void createUserRequiresSession() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("all")).thenReturn("true");
        when(request.getParameter("adminId")).thenReturn(ADMIN);
        final HttpServletResponse response = response();

        new RequestsClient().doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Log in to the simulator first");
        verify(response, never()).getWriter();
        assertEquals(0, feHits.get());
    }

    @Test
    void sessionUserIsNullWithoutSession() {
        assertNull(SimulatorSession.getUser(mock(HttpServletRequest.class)));
    }

    private static List<String> headerValues(final Map<String, List<String>> headers, final String name) {
        return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst()
            .orElse(List.of());
    }

    private static HttpSession sessionFor(final String userId) {
        final HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(SimulatorSession.USER_ID_ATTRIBUTE)).thenReturn(userId);
        return session;
    }

    private static HttpServletRequest proxiedGet(final Map<String, String> headers, final HttpSession session) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/sdc1/feProxy/rest/v1/user");
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());
        when(request.getSession(false)).thenReturn(session);
        when(request.getHeaderNames()).thenAnswer(i -> Collections.enumeration(headers.keySet()));
        headers.forEach((name, value) -> {
            when(request.getHeader(name)).thenReturn(value);
            when(request.getHeaders(name)).thenAnswer(i -> Collections.enumeration(List.of(value)));
        });
        return request;
    }

    private static HttpServletResponse response() throws Exception {
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(final WriteListener writeListener) {
            }

            @Override
            public void write(final int b) {
                body.write(b);
            }
        });
        return response;
    }
}
