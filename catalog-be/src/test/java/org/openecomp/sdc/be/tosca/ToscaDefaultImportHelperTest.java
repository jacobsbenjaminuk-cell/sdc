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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToscaDefaultImportHelperTest {

    @Test
    void addModelAsFilePrefix_pathWithoutParent() {
        final Path originalPath = Path.of("anImport");
        final var modelId = "modelId";
        final Path actualPath = ToscaDefaultImportHelper.addModelAsFilePrefix(originalPath, modelId);
        assertEquals(Path.of("modelId-anImport"), actualPath);
    }

    @Test
    void addModelAsFilePrefix_pathWithParent() {
        final Path originalPath = Path.of("parent/anImport");
        final var modelId = "modelId";
        final Path actualPath = ToscaDefaultImportHelper.addModelAsFilePrefix(originalPath, modelId);
        assertEquals(Path.of("parent/modelId-anImport"), actualPath);
    }

    @Test
    void addModelAsFilePrefix_nullOrEmptyModel() {
        final Path originalPath = Path.of("parent/anImport");
        assertEquals(originalPath, ToscaDefaultImportHelper.addModelAsFilePrefix(originalPath, null));
        assertEquals(originalPath, ToscaDefaultImportHelper.addModelAsFilePrefix(originalPath, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anImport.yaml", "parent/anImport.yaml", "parent/./anImport.yaml", "parent..name/anImport.yaml"})
    void isSafeImportPath_relativePath(final String importPath) {
        assertTrue(ToscaDefaultImportHelper.isSafeImportPath(importPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "/opt/app/anImport.yaml", "\\opt\\anImport.yaml", "C:/anImport.yaml", "c:anImport.yaml",
        "C:\\anImport.yaml", "../anImport.yaml", "parent/../../anImport.yaml", "parent\\..\\..\\anImport.yaml", "parent/.."})
    void isSafeImportPath_unsafePath(final String importPath) {
        assertFalse(ToscaDefaultImportHelper.isSafeImportPath(importPath));
    }

    @Test
    void resolveImportEntryPath_relativePath() {
        final Path definitionsPath = Path.of("Definitions/");
        assertEquals(Optional.of(Path.of("Definitions/parent/anImport.yaml")),
            ToscaDefaultImportHelper.resolveImportEntryPath(definitionsPath, Path.of("parent/anImport.yaml")));
        assertEquals(Optional.of(Path.of("parent/anImport.yaml")),
            ToscaDefaultImportHelper.resolveImportEntryPath(Path.of(""), Path.of("parent/anImport.yaml")));
    }

    @Test
    void resolveImportEntryPath_absoluteOrEscapingPath() {
        final Path definitionsPath = Path.of("Definitions/");
        assertEquals(Optional.empty(), ToscaDefaultImportHelper.resolveImportEntryPath(definitionsPath, Path.of("/opt/app/anImport.yaml")));
        assertEquals(Optional.empty(), ToscaDefaultImportHelper.resolveImportEntryPath(definitionsPath, Path.of("../anImport.yaml")));
        assertEquals(Optional.empty(), ToscaDefaultImportHelper.resolveImportEntryPath(Path.of(""), Path.of("/opt/app/anImport.yaml")));
    }
}
