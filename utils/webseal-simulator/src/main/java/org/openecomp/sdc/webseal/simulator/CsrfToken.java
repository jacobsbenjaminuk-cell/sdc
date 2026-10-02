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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Per-session synchronizer token that protects state-changing simulator endpoints against CSRF.
 */
public final class CsrfToken {

    public static final String PARAMETER_NAME = "csrfToken";
    static final String SESSION_ATTRIBUTE = CsrfToken.class.getName();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private CsrfToken() {
    }

    public static String getOrCreate(final HttpSession session) {
        synchronized (session) {
            final Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof String) {
                return (String) existing;
            }
            final byte[] bytes = new byte[TOKEN_BYTES];
            RANDOM.nextBytes(bytes);
            final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_ATTRIBUTE, token);
            return token;
        }
    }

    public static boolean isValid(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        final Object expected = session.getAttribute(SESSION_ATTRIBUTE);
        final String actual = request.getParameter(PARAMETER_NAME);
        if (!(expected instanceof String) || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(((String) expected).getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
