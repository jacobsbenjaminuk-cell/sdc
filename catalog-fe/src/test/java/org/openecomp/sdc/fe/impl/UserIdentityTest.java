/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2026 the SDC contributors. All rights reserved.
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
package org.openecomp.sdc.fe.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.onap.portalsdk.core.onboarding.util.CipherUtil;
import org.openecomp.sdc.fe.config.Configuration;

class UserIdentityTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        final Configuration.CookieConfig authCookie = new Configuration.CookieConfig();
        authCookie.setSecurityKey(KEY);
        configuration.setAuthCookie(authCookie);
    }

    @Test
    void sealedCookieIdentifiesTheUser() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", UserIdentity.seal("cs0008", configuration))});
        assertEquals(Optional.of("cs0008"), UserIdentity.fromRequest(request, configuration));
    }

    @Test
    void plainOrPortalEncryptedCookieIsNotAnIdentity() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", "jh0003")});
        assertTrue(UserIdentity.fromRequest(request, configuration).isEmpty());
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", CipherUtil.encryptPKC("jh0003"))});
        assertTrue(UserIdentity.fromRequest(request, configuration).isEmpty());
    }

    @Test
    void tamperedCookieIsNotAnIdentity() throws Exception {
        final byte[] sealed = Base64.getDecoder().decode(UserIdentity.seal("cs0008", configuration));
        sealed[sealed.length - 1] ^= 1;
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", Base64.getEncoder().encodeToString(sealed))});
        assertTrue(UserIdentity.fromRequest(request, configuration).isEmpty());
    }

    @Test
    void conflictingCookiesAreNotAnIdentity() throws Exception {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("USER_ID", UserIdentity.seal("cs0008", configuration)),
            new Cookie("USER_ID", UserIdentity.seal("jh0003", configuration))});
        assertTrue(UserIdentity.fromRequest(request, configuration).isEmpty());
    }

    @Test
    void proxyHeadersCountOnlyWhenTrusted() {
        when(request.getHeader("HTTP_IV_USER")).thenReturn("jh0003");
        assertTrue(UserIdentity.fromRequest(request, configuration).isEmpty());
        configuration.setTrustProxyIdentityHeaders(true);
        assertEquals(Optional.of("jh0003"), UserIdentity.fromRequest(request, configuration));
    }
}
