/*
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
package org.openecomp.sdc.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;

/**
 * Checks the Portal SDK cipher key (cipher.enc.key in key.properties) before the application starts.
 * The key must be supplied per deployment and must not be the key published with ONAP.
 */
public final class CipherKeyValidator {

    public static final String KEY_FILE = "key.properties";
    public static final String KEY_PROPERTY = "cipher.enc.key";
    private static final String PUBLIC_ONAP_KEY_SHA256 = "521872dfa1d4bb887fe1a6e5408c5b981ea5937d1b362f110cc511c345afc884";
    private static final Set<Integer> AES_KEY_LENGTHS = Set.of(16, 24, 32);

    private CipherKeyValidator() {
    }

    public static void validateClasspathKey(final ClassLoader classLoader) {
        validate(readKey(classLoader));
    }

    static String readKey(final ClassLoader classLoader) {
        try (InputStream in = classLoader.getResourceAsStream(KEY_FILE)) {
            if (in == null) {
                return null;
            }
            final Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(KEY_PROPERTY);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read " + KEY_FILE, e);
        }
    }

    static void validate(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(KEY_PROPERTY + " is not set. Provide " + KEY_FILE
                + " on the server classpath (for example /app/jetty/resources/" + KEY_FILE + ") or set SDC_CIPHER_ENC_KEY");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key.trim().getBytes(StandardCharsets.US_ASCII));
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(KEY_PROPERTY + " is not valid base64", e);
        }
        if (!AES_KEY_LENGTHS.contains(decoded.length)) {
            throw new IllegalStateException(KEY_PROPERTY + " must decode to 16, 24 or 32 bytes");
        }
        if (PUBLIC_ONAP_KEY_SHA256.equals(sha256Hex(decoded))) {
            throw new IllegalStateException(KEY_PROPERTY + " is the public ONAP default key. Generate a new key for this deployment");
        }
    }

    private static String sha256Hex(final byte[] value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            final StringBuilder hex = new StringBuilder();
            for (final byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
