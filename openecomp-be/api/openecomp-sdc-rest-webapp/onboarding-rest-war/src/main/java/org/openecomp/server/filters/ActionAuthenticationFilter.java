/*
 * Copyright © 2018 European Support Limited
 *
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.onap.sdc.tosca.services.YamlUtil;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;

/**
 * Authenticates action-library callers with HTTP Basic credentials against the users listed in the
 * {@code actionLibraryAuth} section of the file named by the {@code configuration.yaml} system property:
 * <pre>
 * actionLibraryAuth:
 *   users:
 *     - userName: someUser
 *       userPass: somePassword
 *       privilege: RETRIEVE   # RETRIEVE, CREATE, UPDATE or DELETE
 * </pre>
 * Each privilege includes the ones before it. When no users are configured, every request is rejected.
 */
public class ActionAuthenticationFilter implements Filter {

    static final String CONFIG_FILE_PROPERTY = "configuration.yaml";
    static final String CONFIG_SECTION = "actionLibraryAuth";
    private static final String BASIC_PREFIX = "Basic ";
    private static final Logger log = LoggerFactory.getLogger(ActionAuthenticationFilter.class);

    private Map<String, ActionLibraryUser> users = Collections.emptyMap();

    public ActionAuthenticationFilter() {
    }

    ActionAuthenticationFilter(Map<String, ActionLibraryUser> users) {
        this.users = users;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String file = System.getProperty(CONFIG_FILE_PROPERTY);
        if (file == null) {
            log.error("Action library authentication is not configured: system property {} is not set. All requests will be rejected",
                CONFIG_FILE_PROPERTY);
            return;
        }
        try (InputStream input = new FileInputStream(file)) {
            users = parseUsers(new YamlUtil().yamlToMap(input));
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to load action library users from " + file + ". All requests will be rejected", exception);
            users = Collections.emptyMap();
        }
        if (users.isEmpty()) {
            log.error("No action library users configured in section {}. All requests will be rejected", CONFIG_SECTION);
        }
    }

    static Map<String, ActionLibraryUser> parseUsers(Map<?, ?> configuration) {
        if (configuration == null || !(configuration.get(CONFIG_SECTION) instanceof Map)) {
            return Collections.emptyMap();
        }
        Object userList = ((Map<?, ?>) configuration.get(CONFIG_SECTION)).get("users");
        if (!(userList instanceof List)) {
            return Collections.emptyMap();
        }
        Map<String, ActionLibraryUser> parsed = new HashMap<>();
        for (Object entry : (List<?>) userList) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<?, ?> user = (Map<?, ?>) entry;
            Object userName = user.get("userName");
            Object userPass = user.get("userPass");
            Object privilege = user.get("privilege");
            if (userName == null || userPass == null || privilege == null || userName.toString().isEmpty() || userPass.toString()
                .isEmpty()) {
                log.error("Ignoring incomplete action library user entry in section {}", CONFIG_SECTION);
                continue;
            }
            parsed.put(userName.toString(), new ActionLibraryUser(userName.toString(), userPass.toString(),
                ActionLibraryPrivilege.valueOf(privilege.toString().toUpperCase(Locale.ROOT))));
        }
        return Collections.unmodifiableMap(parsed);
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ActionLibraryUser user = authenticate(httpRequest.getHeader("Authorization"));
        if (user == null) {
            httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"action-library\"");
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(new AuthenticatedRequest(httpRequest, user), response);
    }

    private ActionLibraryUser authenticate(String authorizationHeader) {
        if (users.isEmpty() || authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, BASIC_PREFIX, 0,
            BASIC_PREFIX.length())) {
            return null;
        }
        String decodedCredentials;
        try {
            decodedCredentials = new String(Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length()).trim()),
                StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            log.error("Failed to decode action library credentials");
            return null;
        }
        int separator = decodedCredentials.indexOf(':');
        if (separator < 0) {
            return null;
        }
        ActionLibraryUser user = users.get(decodedCredentials.substring(0, separator));
        byte[] suppliedPassword = decodedCredentials.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
        byte[] expectedPassword = (user == null ? "" : user.password).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(suppliedPassword, expectedPassword) || user == null) {
            log.error("Action library authentication failed. Invalid user name or password");
            return null;
        }
        return user;
    }

    static final class ActionLibraryUser {

        private final String userName;
        private final String password;
        private final ActionLibraryPrivilege privilege;

        ActionLibraryUser(String userName, String password, ActionLibraryPrivilege privilege) {
            this.userName = userName;
            this.password = password;
            this.privilege = privilege;
        }
    }

    private static final class AuthenticatedRequest extends HttpServletRequestWrapper {

        private final ActionLibraryUser user;

        AuthenticatedRequest(HttpServletRequest request, ActionLibraryUser user) {
            super(request);
            this.user = user;
        }

        @Override
        public String getRemoteUser() {
            return user.userName;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> user.userName;
        }

        @Override
        public String getAuthType() {
            return BASIC_AUTH;
        }

        @Override
        public boolean isUserInRole(String role) {
            if (role == null) {
                return false;
            }
            ActionLibraryPrivilege requiredPrivilege = ActionLibraryPrivilege.getPrivilege(role.toUpperCase(Locale.ROOT));
            return requiredPrivilege != null && user.privilege.ordinal() >= requiredPrivilege.ordinal();
        }
    }
}
