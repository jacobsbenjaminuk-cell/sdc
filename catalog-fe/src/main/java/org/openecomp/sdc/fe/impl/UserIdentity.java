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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.onap.sdc.security.CipherUtil;
import org.onap.sdc.security.CipherUtilException;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.fe.Constants;
import org.openecomp.sdc.fe.config.Configuration;

/**
 * Works out who the browser is. The answer is the only {@code USER_ID} the front end forwards to the back ends; whatever the browser put in
 * that header is discarded.
 *
 * <p>Two sources are trusted. An authenticating proxy's user header, but only when {@code trustProxyIdentityHeaders} is on, because a browser
 * talking to the front end directly can set any header it likes. And the {@code USER_ID} cookie the portal servlet issues, which is sealed with
 * AES-GCM so it cannot be edited or forged without the key.</p>
 */
public final class UserIdentity {

    public static final List<String> PROXY_IDENTITY_HEADERS = List.of(Constants.WEBSEAL_USER_ID_HEADER, Constants.HTTP_IV_USER, "iv-user");
    private static final Logger log = Logger.getLogger(UserIdentity.class.getName());
    private static final int GENERATED_KEY_BYTES = 16;
    private static String generatedKey;

    private UserIdentity() {
    }

    public static Optional<String> fromRequest(final HttpServletRequest request, final Configuration configuration) {
        final Optional<String> proxyUser = fromProxyHeaders(request, configuration);
        if (proxyUser.isPresent()) {
            return proxyUser;
        }
        return fromCookie(request, configuration);
    }

    public static Optional<String> fromProxyHeaders(final HttpServletRequest request, final Configuration configuration) {
        if (!configuration.isTrustProxyIdentityHeaders()) {
            return Optional.empty();
        }
        return PROXY_IDENTITY_HEADERS.stream().map(request::getHeader).filter(StringUtils::isNotBlank).map(String::trim).findFirst();
    }

    /**
     * Every {@code USER_ID} cookie has to open with the key and name the same user, otherwise the request has no identity: a browser can carry
     * several cookies of one name and the back end must not be left to pick one.
     */
    public static Optional<String> fromCookie(final HttpServletRequest request, final Configuration configuration) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        final List<Optional<String>> userIds = Arrays.stream(cookies).filter(Objects::nonNull).filter(cookie -> Constants.USER_ID.equals(cookie.getName()))
            .map(cookie -> open(cookie.getValue(), configuration)).collect(Collectors.toList());
        if (userIds.isEmpty() || userIds.stream().anyMatch(Optional::isEmpty) || userIds.stream().map(Optional::get).distinct().count() != 1) {
            return Optional.empty();
        }
        return userIds.get(0);
    }

    public static String seal(final String userId, final Configuration configuration) throws CipherUtilException {
        return CipherUtil.encryptPKC(userId, key(configuration));
    }

    private static Optional<String> open(final String value, final Configuration configuration) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CipherUtil.decryptPKC(value, key(configuration))).filter(StringUtils::isNotBlank);
        } catch (final Exception e) {
            log.debug("Ignoring a USER_ID cookie that does not open with the front end key");
            return Optional.empty();
        }
    }

    /**
     * Uses {@code authCookie.securityKey} when it is set. Otherwise a random key is made once per process, which is enough for a single front end
     * but means cookies stop working after a restart; deployments with several front ends must configure the key.
     */
    private static synchronized String key(final Configuration configuration) {
        final Configuration.CookieConfig authCookie = configuration.getAuthCookie();
        if (authCookie != null && StringUtils.isNotBlank(authCookie.getSecurityKey())) {
            return authCookie.getSecurityKey();
        }
        if (generatedKey == null) {
            final byte[] keyBytes = new byte[GENERATED_KEY_BYTES];
            new SecureRandom().nextBytes(keyBytes);
            generatedKey = Base64.getEncoder().encodeToString(keyBytes);
            log.warn("authCookie.securityKey is not set, sealing USER_ID cookies with a key generated for this process");
        }
        return generatedKey;
    }
}
