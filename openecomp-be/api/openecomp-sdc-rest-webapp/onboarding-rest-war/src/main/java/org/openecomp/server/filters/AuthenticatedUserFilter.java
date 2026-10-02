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

import static org.openecomp.sdcrests.common.RestConstants.USER_ID_HEADER_PARAM;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;
import org.openecomp.sdc.securityutil.AuthenticationCookie;
import org.openecomp.sdc.securityutil.AuthenticationCookieUtils;
import org.openecomp.sdc.securityutil.ISessionValidationFilterConfiguration;

/**
 * Binds the caller identity to the request before any filter or endpoint reads {@code USER_ID}.
 * <p>
 * The trusted user is taken, in order, from an authenticated principal (Keycloak), from the decrypted portal {@code AuthenticationCookie},
 * or, for callers that passed {@link BasicAuthenticationFilter}, from the {@code USER_ID} they assert on behalf of their user. A {@code USER_ID}
 * that differs from the authenticated user is rejected, and a {@code USER_ID} that no authentication backs is rejected. Downstream code sees the
 * trusted user both as the {@code USER_ID} header and as the {@link #AUTHENTICATED_USER_ATTRIBUTE} request attribute.
 */
public class AuthenticatedUserFilter implements Filter {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = AuthenticatedUserFilter.class.getName() + ".user";
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatedUserFilter.class);
    private final Supplier<ISessionValidationFilterConfiguration> cookieConfiguration;

    public AuthenticatedUserFilter() {
        this(RestrictionAccessFilter::cookieConfiguration);
    }

    AuthenticatedUserFilter(Supplier<ISessionValidationFilterConfiguration> cookieConfiguration) {
        this.cookieConfiguration = cookieConfiguration;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no initialisation needed
    }

    @Override
    public void destroy() {
        // nothing to release
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String claimedUser = request.getHeader(USER_ID_HEADER_PARAM);
        String trustedUser;
        try {
            trustedUser = resolveAuthenticatedUser(request);
        } catch (InvalidIdentityException e) {
            LOGGER.warn("Rejecting request to {}: {}", request.getRequestURI(), e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }
        if (trustedUser == null) {
            if (claimedUser == null) {
                chain.doFilter(request, response);
                return;
            }
            if (!BasicAuthenticationFilter.isAuthenticated(request)) {
                LOGGER.warn("Rejecting request to {}: {} header is not backed by an authenticated caller", request.getRequestURI(),
                    USER_ID_HEADER_PARAM);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, USER_ID_HEADER_PARAM + " requires an authenticated caller");
                return;
            }
            trustedUser = claimedUser;
        } else if (claimedUser != null && !claimedUser.equals(trustedUser)) {
            LOGGER.warn("Rejecting request to {}: {} header does not match the authenticated user", request.getRequestURI(), USER_ID_HEADER_PARAM);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, USER_ID_HEADER_PARAM + " does not match the authenticated user");
            return;
        }
        request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, trustedUser);
        chain.doFilter(new AuthenticatedUserRequest(request, trustedUser), response);
    }

    private String resolveAuthenticatedUser(HttpServletRequest request) throws InvalidIdentityException {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isEmpty()) {
            return principal.getName();
        }
        return resolveCookieUser(request);
    }

    private String resolveCookieUser(HttpServletRequest request) throws InvalidIdentityException {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        ISessionValidationFilterConfiguration configuration = cookieConfiguration.get();
        String cookieName = configuration.getCookieName();
        if (cookieName == null || cookieName.isEmpty()) {
            return null;
        }
        List<Cookie> authenticationCookies = Arrays.stream(cookies).filter(cookie -> cookieName.equals(cookie.getName()))
            .collect(Collectors.toList());
        if (authenticationCookies.isEmpty()) {
            return null;
        }
        if (authenticationCookies.size() > 1) {
            throw new InvalidIdentityException("multiple authentication cookies");
        }
        Cookie cookie = authenticationCookies.get(0);
        AuthenticationCookie authenticationCookie;
        try {
            if (AuthenticationCookieUtils.isSessionExpired(cookie, configuration)) {
                throw new InvalidIdentityException("authentication cookie has expired");
            }
            authenticationCookie = AuthenticationCookieUtils.getAuthenticationCookie(cookie, configuration);
        } catch (InvalidIdentityException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidIdentityException("authentication cookie is not valid");
        }
        String userId = authenticationCookie == null ? null : authenticationCookie.getUserID();
        if (userId == null || userId.isEmpty()) {
            throw new InvalidIdentityException("authentication cookie has no user");
        }
        return userId;
    }

    private static class InvalidIdentityException extends Exception {

        InvalidIdentityException(String message) {
            super(message);
        }
    }

    private static class AuthenticatedUserRequest extends HttpServletRequestWrapper {

        private final String user;

        AuthenticatedUserRequest(HttpServletRequest request, String user) {
            super(request);
            this.user = user;
        }

        @Override
        public String getHeader(String name) {
            return USER_ID_HEADER_PARAM.equalsIgnoreCase(name) ? user : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return USER_ID_HEADER_PARAM.equalsIgnoreCase(name) ? Collections.enumeration(List.of(user)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            if (original != null) {
                names.addAll(Collections.list(original));
            }
            if (names.stream().noneMatch(USER_ID_HEADER_PARAM::equalsIgnoreCase)) {
                names.add(USER_ID_HEADER_PARAM);
            }
            return Collections.enumeration(names);
        }
    }
}
