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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestsClientTest {

    private static final String TOKEN = "valid-token";

    private final RequestsClient servlet = new RequestsClient();
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(session.getAttribute(CsrfToken.SESSION_ATTRIBUTE)).thenReturn(TOKEN);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    void getIsNotAllowed() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn(TOKEN);
        when(request.getParameter("userId")).thenReturn("cs0008");

        servlet.service(request, response);

        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "HTTP method GET is not supported by this URL");
        verify(response, never()).getWriter();
    }

    @Test
    void postWithoutSessionIsForbidden() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn(TOKEN);

        servlet.service(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
        verify(response, never()).getWriter();
    }

    @Test
    void postWithWrongTokenIsForbidden() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn("forged");

        servlet.service(request, response);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
        verify(response, never()).getWriter();
    }

    @Test
    void postForUnconfiguredUserIsRejected() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn(TOKEN);
        when(request.getParameter("userId")).thenReturn("<script>alert(1)</script>");

        servlet.service(request, response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown user");
        verify(response, never()).getWriter();
    }

    @Test
    void csrfTokenIsStablePerSessionAndValidated() {
        final HttpSession realSession = mock(HttpSession.class);
        final String token = CsrfToken.getOrCreate(realSession);
        verify(realSession).setAttribute(CsrfToken.SESSION_ATTRIBUTE, token);
        when(realSession.getAttribute(CsrfToken.SESSION_ATTRIBUTE)).thenReturn(token);

        assertEquals(token, CsrfToken.getOrCreate(realSession));
        assertTrue(token.length() >= 43);

        when(request.getSession(false)).thenReturn(realSession);
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn(token);
        assertTrue(CsrfToken.isValid(request));
        when(request.getParameter(CsrfToken.PARAMETER_NAME)).thenReturn(null);
        assertFalse(CsrfToken.isValid(request));
    }

    @Test
    void jsonBodyEscapesUserFields() {
        final User user = new User("a\",\"role\":\"ADMIN", "b", "c@d", "x1", "Designer", "pw");

        final JsonObject json = JsonParser.parseString(RequestsClient.toJson(user)).getAsJsonObject();

        assertEquals("a\",\"role\":\"ADMIN", json.get("firstName").getAsString());
        assertEquals("DESIGNER", json.get("role").getAsString());
        assertEquals("x1", json.get("userId").getAsString());
        assertEquals(5, json.size());
    }
}
