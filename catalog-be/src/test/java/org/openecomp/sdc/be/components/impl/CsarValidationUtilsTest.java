/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.sdc.be.components.impl;

import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fj.data.Either;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Test;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.exception.ResponseFormat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CsarValidationUtilsTest {
    private String[] invalidExtensions = { null, ".bla", ".yaml", ".yml", ".txt", ".zip" };
    private String[] validExtensions = { ".csar", ".cSAr", ".Csar", ".CSAR" };
    private static final String CSAR_UUID = "csarUUID";
    private static final String YAML_CONTENT = "tosca_definitions_version: tosca_simple_yaml_1_1";

    @Test
    public void testIsCsarPayloadName() {
        Arrays.stream(invalidExtensions).forEach(e -> Assert.assertFalse(CsarValidationUtils.isCsarPayloadName(e)));
        Arrays.stream(validExtensions).forEach(e -> Assert.assertTrue(CsarValidationUtils.isCsarPayloadName(e)));
    }

    @Test
    public void testGetToscaYamlFindsEntryDefinitionsByExactName() {
        final ComponentsUtils componentsUtils = mock(ComponentsUtils.class, RETURNS_MOCKS);
        final Map<String, byte[]> csar = buildCsar("Definitions/main.yml", "Definitions/main.yml");

        final Either<ImmutablePair<String, String>, ResponseFormat> result =
            CsarValidationUtils.getToscaYaml(csar, CSAR_UUID, componentsUtils, AuditingActionEnum.CREATE_RESOURCE);

        Assert.assertTrue(result.isLeft());
        Assert.assertEquals("Definitions/main.yml", result.left().value().getLeft());
        Assert.assertEquals(YAML_CONTENT, result.left().value().getRight());
    }

    @Test
    public void testGetToscaYamlDoesNotTreatEntryDefinitionsAsRegex() {
        final ComponentsUtils componentsUtils = mock(ComponentsUtils.class, RETURNS_MOCKS);
        final Map<String, byte[]> csar = buildCsar(".*", "Definitions/main.yml");

        final Either<ImmutablePair<String, String>, ResponseFormat> result =
            CsarValidationUtils.getToscaYaml(csar, CSAR_UUID, componentsUtils, AuditingActionEnum.CREATE_RESOURCE);

        Assert.assertTrue(result.isRight());
        verify(componentsUtils).getResponseFormat(ActionStatus.YAML_NOT_FOUND_IN_CSAR, CSAR_UUID, ".*");
    }

    @Test(timeout = 5000)
    public void testGetToscaYamlWithCatastrophicBacktrackingEntryDefinitionsReturnsQuickly() {
        final ComponentsUtils componentsUtils = mock(ComponentsUtils.class, RETURNS_MOCKS);
        final String entryDefinitions = "(.*a){12}b";
        final Map<String, byte[]> csar = buildCsar(entryDefinitions, StringUtils.repeat('a', 40));

        final Either<ImmutablePair<String, String>, ResponseFormat> result =
            CsarValidationUtils.getToscaYaml(csar, CSAR_UUID, componentsUtils, AuditingActionEnum.CREATE_RESOURCE);

        Assert.assertTrue(result.isRight());
        verify(componentsUtils).getResponseFormat(ActionStatus.YAML_NOT_FOUND_IN_CSAR, CSAR_UUID, entryDefinitions);
    }

    @Test
    public void testGetToscaYamlWithoutEntryDefinitionsReturnsYamlNotFound() {
        final ComponentsUtils componentsUtils = mock(ComponentsUtils.class, RETURNS_MOCKS);
        final Map<String, byte[]> csar = new HashMap<>();
        csar.put("TOSCA-Metadata/TOSCA.meta", "TOSCA-Meta-File-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        csar.put("Definitions/main.yml", YAML_CONTENT.getBytes(StandardCharsets.UTF_8));

        final Either<ImmutablePair<String, String>, ResponseFormat> result =
            CsarValidationUtils.getToscaYaml(csar, CSAR_UUID, componentsUtils, AuditingActionEnum.CREATE_RESOURCE);

        Assert.assertTrue(result.isRight());
        verify(componentsUtils).getResponseFormat(ActionStatus.YAML_NOT_FOUND_IN_CSAR, CSAR_UUID, null);
    }

    private static Map<String, byte[]> buildCsar(final String entryDefinitions, final String yamlEntryName) {
        final String toscaMeta = "TOSCA-Meta-File-Version: 1.0\n"
            + "CSAR-Version: 1.1\n"
            + "Created-By: test\n"
            + "Entry-Definitions: " + entryDefinitions + "\n";
        final Map<String, byte[]> csar = new HashMap<>();
        csar.put("TOSCA-Metadata/TOSCA.meta", toscaMeta.getBytes(StandardCharsets.UTF_8));
        csar.put(yamlEntryName, YAML_CONTENT.getBytes(StandardCharsets.UTF_8));
        return csar;
    }
}
