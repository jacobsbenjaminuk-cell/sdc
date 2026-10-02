/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.onap.sdc.security.AuthenticationCookie;
import org.onap.sdc.security.IUsersThreadLocalHolder;
import org.onap.sdc.security.PortalClient;
import org.onap.sdc.security.RestrictionAccessFilterException;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.config.Configuration;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.user.UserBusinessLogic;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.datastructure.UserContext;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.common.util.ThreadLocalsHolder;
import org.springframework.beans.factory.annotation.Autowired;

public class ThreadLocalUtils implements IUsersThreadLocalHolder {

    private static final Logger log = Logger.getLogger(ThreadLocalUtils.class);
    private static final String EXTERNAL_API_SERVLET_PATH = "/sdc";
    private static final String BASIC_PREFIX = "Basic ";
    @Autowired
    private PortalClient portalClient;
    @Autowired
    private UserBusinessLogic userBusinessLogic;

    @Override
    public void setUserContext(AuthenticationCookie authenticationCookie) {
        UserContext userContext;
        userContext = new UserContext(authenticationCookie.getUserID(), authenticationCookie.getRoles(), authenticationCookie.getFirstName(),
                authenticationCookie.getLastName());
        ThreadLocalsHolder.setUserContext(userContext);
    }

    protected void setUserContext(HttpServletRequest httpRequest) {
        final String userId = httpRequest.getHeader(Constants.USER_ID_HEADER);
        if (userId != null) {
            Set<String> roles = null;
            try {
                final Optional<String> userRolesFromPortalOptional = portalClient.fetchUserRolesFromPortal(userId);
                if (userRolesFromPortalOptional.isPresent()) {
                    roles = new HashSet<>(List.of(userRolesFromPortalOptional.get()));
                }
            } catch (RestrictionAccessFilterException e) {
                log.debug("Failed to fetch user ID - {} from portal", userId);
                log.debug(e.getMessage());
            }
            final UserContext userContext = new UserContext(userId, roles, null, null);
            ThreadLocalsHolder.setUserContext(userContext);
        } else {
            log.debug("user_id value in req header is null, userContext will not be initialized");
        }
    }

    /**
     * Establishes who is calling before any user lookup. A {@code USER_ID} is only an assertion, so it is accepted solely on requests that carry
     * the configured basic auth credentials: the front end and the internal services authenticate that way and set {@code USER_ID} from their
     * own authenticated session. Anything else is rejected with 401 before the user store is touched, so callers cannot pick an identity or probe
     * which ids exist.
     *
     * @return the authenticated user id, or empty when the request is allowed to proceed without a user context
     */
    protected Optional<String> setUserContextFromDB(HttpServletRequest httpRequest) {
        ThreadLocalsHolder.setUserContext(null);
        final Configuration.BasicAuthConfig basicAuthConf = ConfigurationManager.getConfigurationManager().getConfiguration().getBasicAuth();
        if (isExcluded(basicAuthConf.getExcludedUrls(), httpRequest.getRequestURI())) {
            log.debug("{} is excluded from authentication, userContext will not be initialized", httpRequest.getRequestURI());
            return Optional.empty();
        }
        final boolean serviceAuthenticated = basicAuthConf.isEnabled() && hasValidBasicAuth(httpRequest, basicAuthConf);
        if (basicAuthConf.isEnabled() && !serviceAuthenticated) {
            log.info("Rejecting request to {}: missing or invalid basic auth credentials", httpRequest.getRequestURI());
            throw new ByActionStatusComponentException(ActionStatus.AUTH_REQUIRED);
        }
        final String userId = httpRequest.getHeader(Constants.USER_ID_HEADER);
        if (StringUtils.isBlank(userId)) {
            if (serviceAuthenticated || isExternalApi(httpRequest)) {
                log.debug("user_id value in req header is null, userContext will not be initialized");
                return Optional.empty();
            }
            log.info("Rejecting request to {}: no USER_ID", httpRequest.getRequestURI());
            throw new ByActionStatusComponentException(ActionStatus.AUTH_REQUIRED);
        }
        if (!serviceAuthenticated) {
            log.info("Rejecting request to {}: USER_ID is not backed by authenticated credentials", httpRequest.getRequestURI());
            throw new ByActionStatusComponentException(ActionStatus.AUTH_REQUIRED);
        }
        updateUserContext(userId);
        return Optional.of(userId);
    }

    /**
     * The distribution and external API ({@code /sdc/*}) has consumers that never send a {@code USER_ID}; they get no user context, so any
     * operation that needs a user still fails.
     */
    private boolean isExternalApi(final HttpServletRequest httpRequest) {
        return EXTERNAL_API_SERVLET_PATH.equals(httpRequest.getServletPath());
    }

    private boolean isExcluded(final String excludedUrls, final String requestUri) {
        if (StringUtils.isBlank(excludedUrls) || StringUtils.isBlank(requestUri)) {
            return false;
        }
        return Arrays.stream(excludedUrls.split("[,;]")).map(String::trim).anyMatch(requestUri::equals);
    }

    private boolean hasValidBasicAuth(final HttpServletRequest httpRequest, final Configuration.BasicAuthConfig basicAuthConf) {
        final String authHeader = httpRequest.getHeader(Constants.AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return false;
        }
        final String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(authHeader.substring(BASIC_PREFIX.length()).trim()), StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            return false;
        }
        final int separator = credentials.indexOf(':');
        if (separator == -1 || basicAuthConf.getUserName() == null || basicAuthConf.getUserPass() == null) {
            return false;
        }
        final boolean userMatches = constantTimeEquals(credentials.substring(0, separator).trim(), basicAuthConf.getUserName());
        final boolean passMatches = constantTimeEquals(credentials.substring(separator + 1).trim(), basicAuthConf.getUserPass());
        return userMatches && passMatches;
    }

    private static boolean constantTimeEquals(final String actual, final String expected) {
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private void updateUserContext(String userId) {
        User user = userBusinessLogic.getUser(userId, false);
        Set<String> roles = new HashSet<>(Arrays.asList(user.getRole()));
        UserContext userContext = new UserContext(user.getUserId(), roles, user.getFirstName(), user.getLastName());
        ThreadLocalsHolder.setUserContext(userContext);
    }
}
