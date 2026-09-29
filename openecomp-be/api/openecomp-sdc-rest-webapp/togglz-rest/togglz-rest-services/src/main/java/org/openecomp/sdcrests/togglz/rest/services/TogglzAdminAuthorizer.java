/*
 * Copyright © 2016-2017 European Support Limited
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
package org.openecomp.sdcrests.togglz.rest.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import org.openecomp.sdc.common.CommonConfigurationManager;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;

/**
 * Checks HTTP Basic credentials against the {@code togglzAdmin} section of the onboarding configuration. Access is denied when the section, the
 * user name or the password is missing or blank.
 */
public class TogglzAdminAuthorizer {

    static final String CONFIG_SECTION = "togglzAdmin";
    static final String USER_NAME_KEY = "userName";
    static final String USER_PASS_KEY = "userPass";
    private static final Logger LOGGER = LoggerFactory.getLogger(TogglzAdminAuthorizer.class);
    private static final String BASIC_PREFIX = "Basic ";
    private final String userName;
    private final String userPass;

    public TogglzAdminAuthorizer(String userName, String userPass) {
        this.userName = userName;
        this.userPass = userPass;
    }

    public static TogglzAdminAuthorizer fromConfiguration() {
        try {
            Map<String, Object> section = CommonConfigurationManager.getInstance().getConfigValue(CONFIG_SECTION);
            if (section == null) {
                return new TogglzAdminAuthorizer(null, null);
            }
            return new TogglzAdminAuthorizer(asString(section.get(USER_NAME_KEY)), asString(section.get(USER_PASS_KEY)));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to read " + CONFIG_SECTION + " configuration. Togglz updates are disabled", e);
            return new TogglzAdminAuthorizer(null, null);
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isAuthorized(String authorizationHeader) {
        if (isBlank(userName) || isBlank(userPass) || authorizationHeader == null || !authorizationHeader.startsWith(BASIC_PREFIX)) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length()).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return false;
        }
        boolean userMatches = constantTimeEquals(userName, decoded.substring(0, separator));
        boolean passMatches = constantTimeEquals(userPass, decoded.substring(separator + 1));
        return userMatches && passMatches;
    }
}
