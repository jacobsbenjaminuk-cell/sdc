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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CipherKeyValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validate(null));
        assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validate("  "));
    }

    @Test
    void rejectsPublicOnapKey() {
        assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validate("AGLDdG4D04BKm2IxIWEr8o=="));
    }

    @Test
    void rejectsBadKeys() {
        assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validate("not base64!"));
        assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validate("c2hvcnQ="));
    }

    @Test
    void acceptsDeploymentKey() {
        assertDoesNotThrow(() -> CipherKeyValidator.validate("RrqjN9OPmbfIGMt6g+MUuA=="));
    }

    @Test
    void readsKeyFromClasspath() throws Exception {
        Files.writeString(tempDir.resolve(CipherKeyValidator.KEY_FILE), "cipher.enc.key = RrqjN9OPmbfIGMt6g+MUuA==\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null)) {
            assertDoesNotThrow(() -> CipherKeyValidator.validateClasspathKey(loader));
        }
    }

    @Test
    void failsWhenKeyFileIsAbsent() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null)) {
            assertThrows(IllegalStateException.class, () -> CipherKeyValidator.validateClasspathKey(loader));
        }
    }
}
