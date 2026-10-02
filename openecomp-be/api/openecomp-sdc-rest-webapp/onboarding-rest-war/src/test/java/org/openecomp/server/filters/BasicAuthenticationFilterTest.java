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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicAuthenticationFilterTest {

    private static final String CONFIG_FILE_PROPERTY = "configuration.yaml";

    @TempDir
    Path tempDir;
    private final Map<String, Object> attributes = new HashMap<>();
    private String previousConfig;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        Path config = tempDir.resolve("onboarding_configuration.yaml");
        Files.writeString(config, "basicAuth:\n  enabled: true\n  userName: service\n  userPass: secret\n  excludedUrls: \"/v1.0/healthcheck\"\n");
        previousConfig = System.setProperty(CONFIG_FILE_PROPERTY, config.toString());
        request = mock(HttpServletRequest.class);
        lenient().when(request.getServletPath()).thenReturn("");
        lenient().when(request.getPathInfo()).thenReturn("/v1.0/vendor-software-products");
        lenient().when(request.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.<String>getArgument(0)));
        lenient().doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1))).when(request)
            .setAttribute(anyString(), any());
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        if (previousConfig == null) {
            System.clearProperty(CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(CONFIG_FILE_PROPERTY, previousConfig);
        }
    }

    @Test
    void rejectsMissingCredentials() throws Exception {
        new BasicAuthenticationFilter().doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
        assertFalse(BasicAuthenticationFilter.isAuthenticated(request));
    }

    @Test
    void rejectsWrongCredentials() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(basic("service:wrong"));

        new BasicAuthenticationFilter().doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsMalformedCredentials() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic not-base64!");

        new BasicAuthenticationFilter().doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void marksRequestAuthenticatedForValidCredentials() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(basic("service:secret"));

        new BasicAuthenticationFilter().doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertTrue(BasicAuthenticationFilter.isAuthenticated(request));
    }

    @Test
    void letsExcludedUrlThroughWithoutMarkingAuthenticated() throws Exception {
        when(request.getPathInfo()).thenReturn("/v1.0/healthcheck");

        new BasicAuthenticationFilter().doFilter(request, response, chain);

        verify(chain).doFilter(any(), any());
        assertFalse(BasicAuthenticationFilter.isAuthenticated(request));
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
