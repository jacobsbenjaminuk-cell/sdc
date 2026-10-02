/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2022 Tech-Mahindra Intellectual Property. All rights reserved.
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


package org.openecomp.sdc.common.util;


import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.openecomp.sdc.common.log.wrappers.Logger;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.Set;


/**
 * To check the Multitenancy
 */

public class Multitenancy {
    private static final Logger log = Logger.getLogger(Multitenancy.class);
    private boolean keycloak;
    public AccessToken getAccessToken(HttpServletRequest request){
     KeycloakPrincipal principal = (KeycloakPrincipal) request.getUserPrincipal();
        return  principal.getKeycloakSecurityContext().getToken();
    }

    /**
     * Returns the realm roles of the Keycloak principal of the request, or an empty set when the request carries no
     * Keycloak token.
     */
    public Set<String> getRealmRoles(HttpServletRequest request) {
        Principal principal = request == null ? null : request.getUserPrincipal();
        if (!(principal instanceof KeycloakPrincipal)) {
            return Collections.emptySet();
        }
        KeycloakSecurityContext securityContext = ((KeycloakPrincipal<?>) principal).getKeycloakSecurityContext();
        AccessToken token = securityContext == null ? null : securityContext.getToken();
        AccessToken.Access realmAccess = token == null ? null : token.getRealmAccess();
        if (realmAccess == null || realmAccess.getRoles() == null) {
            return Collections.emptySet();
        }
        return realmAccess.getRoles();
    }

    /**
     * Checks that the tenant exactly matches one of the realm roles of the request's Keycloak principal.
     */
    public boolean isTenantAllowed(HttpServletRequest request, String tenant) {
        return tenant != null && getRealmRoles(request).contains(tenant);
    }

    public boolean multiTenancyCheck() {
        log.info("Checking the Multitenancy ");
        try (InputStream ioStream = Multitenancy.class.getResourceAsStream("/multitenancy.json");
             BufferedReader br = new BufferedReader(new InputStreamReader(ioStream, StandardCharsets.UTF_8))) {
            keycloak = (boolean) ((JSONObject) new JSONParser().parse(br)).get("multitenancy");
        } catch (Exception e) {
            log.debug("Multitenancy Exception", e);
        }
        log.info("Multitenancy= {}",keycloak);
        return keycloak;
    }
}
