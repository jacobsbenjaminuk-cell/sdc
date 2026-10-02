/*
 * -
 *  ============LICENSE_START=======================================================
 *  Copyright (C) 2021 Nordix Foundation.
 *  ================================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 *  ============LICENSE_END=========================================================
 */

package org.openecomp.sdc.be.tosca;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

/**
 * Helper class for TOSCA default imports.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ToscaDefaultImportHelper {

    private static final Pattern WINDOWS_DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");

    /**
     * Add the model as a file prefix in the given path, e.g.: "path/to/entry.yaml -> path/to/modelId-entry.yaml".
     *
     * @param originalPath the entry original path
     * @param modelId      the model id to add as prefix
     * @return the modified file path with a model prefix.
     */
    public static Path addModelAsFilePrefix(final Path originalPath, final String modelId) {
        if (StringUtils.isEmpty(modelId)) {
            return originalPath;
        }
        final var fileName = originalPath.getFileName().toString();
        final var newFileName = String.format("%s-%s", modelId, fileName);
        if (originalPath.getParent() == null) {
            return Path.of(newFileName);
        }
        return originalPath.getParent().resolve(newFileName);
    }

    /**
     * Checks if the given import path is a relative path that cannot point outside the folder it is placed in: it must not be blank,
     * start with a root ("/" or "\\"), start with a drive letter (e.g. "C:") or contain a ".." segment.
     *
     * @param importPath the import path
     * @return true if the import path is safe to be used as a relative entry path
     */
    public static boolean isSafeImportPath(final String importPath) {
        if (StringUtils.isBlank(importPath)) {
            return false;
        }
        final String unixImportPath = importPath.replace('\\', '/');
        if (unixImportPath.startsWith("/") || WINDOWS_DRIVE_PREFIX.matcher(unixImportPath).matches()) {
            return false;
        }
        return Arrays.stream(unixImportPath.split("/")).noneMatch(".."::equals);
    }

    /**
     * Resolves the import path against the definitions path, ensuring the result stays under the definitions path.
     *
     * @param definitionsPath the CSAR definitions folder path
     * @param importPath      the import path, relative to the definitions folder
     * @return the resolved entry path, or empty if the import path is not safe or would leave the definitions folder
     */
    public static Optional<Path> resolveImportEntryPath(final Path definitionsPath, final Path importPath) {
        if (!isSafeImportPath(importPath.toString())) {
            return Optional.empty();
        }
        final Path entryPath = definitionsPath.resolve(importPath);
        final Path normalizedDefinitionsPath = definitionsPath.normalize();
        final Path normalizedEntryPath = entryPath.normalize();
        if (normalizedEntryPath.isAbsolute() != normalizedDefinitionsPath.isAbsolute()) {
            return Optional.empty();
        }
        if (!normalizedDefinitionsPath.toString().isEmpty() && !normalizedEntryPath.startsWith(normalizedDefinitionsPath)) {
            return Optional.empty();
        }
        return Optional.of(entryPath);
    }

}
