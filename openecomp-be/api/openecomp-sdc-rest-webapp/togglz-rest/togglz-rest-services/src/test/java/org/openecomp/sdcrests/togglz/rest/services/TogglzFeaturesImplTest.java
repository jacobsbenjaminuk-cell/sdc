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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class TogglzFeaturesImplTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "secret";

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private final TogglzFeaturesImpl togglzFeatures = new TogglzFeaturesImpl(new TogglzAdminAuthorizer(ADMIN_USER, ADMIN_PASS));

    @Test
    void setAllFeaturesRejectsMissingCredentials() {
        Response response = togglzFeatures.setAllFeatures(true, null);
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertEquals("Basic realm=\"togglz\"", response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void setAllFeaturesRejectsWrongCredentials() {
        Response response = togglzFeatures.setAllFeatures(true, basic(ADMIN_USER + ":wrong"));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void setFeatureStateRejectsMissingCredentials() {
        Response response = togglzFeatures.setFeatureState("EXTERNAL_LICENSE", true, null);
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }

    @Test
    void setFeatureStateRejectsUnknownFeatureForAdmin() {
        Response response = togglzFeatures.setFeatureState("NOT_A_FEATURE", true, basic(ADMIN_USER + ":" + ADMIN_PASS));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    @Test
    void mutationsAreRejectedWhenAdminIsNotConfigured() {
        TogglzFeaturesImpl unconfigured = new TogglzFeaturesImpl(new TogglzAdminAuthorizer(null, null));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), unconfigured.setAllFeatures(true, basic(":")).getStatus());
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(),
            unconfigured.setFeatureState("EXTERNAL_LICENSE", true, basic("null:null")).getStatus());
    }

    @Test
    void authorizerAcceptsOnlyExactAdminCredentials() {
        TogglzAdminAuthorizer authorizer = new TogglzAdminAuthorizer(ADMIN_USER, ADMIN_PASS);
        assertTrue(authorizer.isAuthorized(basic(ADMIN_USER + ":" + ADMIN_PASS)));
        assertFalse(authorizer.isAuthorized(basic(ADMIN_USER + ":" + ADMIN_PASS + "x")));
        assertFalse(authorizer.isAuthorized(basic("other:" + ADMIN_PASS)));
        assertFalse(authorizer.isAuthorized(basic(ADMIN_USER + ADMIN_PASS)));
        assertFalse(authorizer.isAuthorized("Bearer " + ADMIN_PASS));
        assertFalse(authorizer.isAuthorized("Basic %%%not-base64"));
        assertFalse(authorizer.isAuthorized(""));
    }

    @Test
    void authorizerRejectsBlankConfiguredCredentials() {
        assertFalse(new TogglzAdminAuthorizer("", "").isAuthorized(basic(":")));
        assertFalse(new TogglzAdminAuthorizer(ADMIN_USER, " ").isAuthorized(basic(ADMIN_USER + ": ")));
    }
}
