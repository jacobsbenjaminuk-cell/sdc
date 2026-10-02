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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Removes secret values (keys and passwords) from a JSON tree before it is returned to a client.
 */
public final class SecretRedactor {

    private SecretRedactor() {
    }

    public static JsonElement redact(final JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(SecretRedactor::redact);
        } else if (element.isJsonObject()) {
            final JsonObject object = element.getAsJsonObject();
            final List<String> secretNames = new ArrayList<>();
            for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (isSecret(entry.getKey())) {
                    secretNames.add(entry.getKey());
                } else {
                    redact(entry.getValue());
                }
            }
            secretNames.forEach(object::remove);
        }
        return element;
    }

    static boolean isSecret(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("password") || lower.contains("securitykey") || lower.contains("secret")
            || lower.equals("userpass") || lower.endsWith("_key") || lower.equals("cipherkey");
    }
}
