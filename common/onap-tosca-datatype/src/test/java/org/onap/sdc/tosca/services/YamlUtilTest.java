/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.sdc.tosca.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.onap.sdc.tosca.datatypes.model.ServiceTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class YamlUtilTest {

    private YamlUtil yamlUtil = new YamlUtil();

    @Test
    public void testYamlToMap() {
        InputStream is = yamlUtil.loadYamlFileIs("/yamlMap.yaml");
        Map<String, LinkedHashMap<String, Object>> res = yamlUtil.yamlToMap(is);
        assertNotNull(res);
        assertEquals(1, res.size());
        assertEquals("value1", (String)res.get("complex-mapping").get("parameter1"));
        assertEquals("value2", (String)res.get("complex-mapping").get("parameter2"));
        assertEquals("value3", (String)res.get("complex-mapping").get("parameter3"));
    }

    @Test
    public void testYamlToList() {
        InputStream is = yamlUtil.loadYamlFileIs("/yamlList.yaml");
        Optional<List<Object>> res = yamlUtil.yamlToList(is);
        assertEquals(3, res.get().size());
        assertEquals("value1", res.get().get(0));

        InputStream is2 = yamlUtil.loadYamlFileIs("/yamlListError.yaml");
        Optional<List<Object>> res2 = yamlUtil.yamlToList(is2);
        assertTrue(res2.isEmpty());
    }

    @Test
    public void testIsYamlFileContentValid() {
        String yamlString = "tosca_definitions_version: tosca_simple_yaml_1_1\n" +
                "imports: []\n" +
                "node_types:\n" +
                "  tosca.nodes.Root:\n" +
                "    description: The TOSCA Node Type all other TOSCA base Node Types derive from";
        boolean res = yamlUtil.isYamlFileContentValid(yamlString);
        assertTrue(res);

        String yamlString2 = "{tosca_definitions_version: tosca_simple_yaml_1_1\n" +
                "node_types:\n" +
                "  tosca.nodes.Root:\n" +
                "    description: The TOSCA Node Type all other TOSCA base Node Types derive from}";
        boolean res2 = yamlUtil.isYamlFileContentValid(yamlString2);
        assertFalse(res2);
    }

    @Test
    public void testGlobalJavaTagsAreRejected() {
        final String nestedTag = "tosca_definitions_version: tosca_simple_yaml_1_1\n"
            + "description: !!java.net.URL [\"http://localhost/\"]\n";
        final String rootTag = "!!java.net.URL [\"http://localhost/\"]\n";
        assertThrows(RuntimeException.class, () -> yamlUtil.yamlToObject(nestedTag, Map.class));
        assertThrows(RuntimeException.class, () -> yamlUtil.yamlToObject(nestedTag, ServiceTemplate.class));
        assertThrows(RuntimeException.class, () -> yamlUtil.yamlToObject(rootTag, Map.class));
        assertThrows(RuntimeException.class,
            () -> new ToscaExtensionYamlUtil().yamlToObject(nestedTag, ServiceTemplate.class));
        assertThrows(RuntimeException.class,
            () -> YamlUtil.read(new ByteArrayInputStream(nestedTag.getBytes())));
        assertFalse(yamlUtil.isYamlFileContentValid(nestedTag));
    }

    @Test
    public void testTypedParsingStillWorks() {
        final String yaml = "tosca_definitions_version: tosca_simple_yaml_1_1\n"
            + "description: plain\n"
            + "node_types:\n"
            + "  my.Node:\n"
            + "    derived_from: tosca.nodes.Root\n";
        final ServiceTemplate serviceTemplate = new ToscaExtensionYamlUtil().yamlToObject(yaml, ServiceTemplate.class);
        assertEquals("plain", serviceTemplate.getDescription());
        assertEquals("tosca.nodes.Root", serviceTemplate.getNode_types().get("my.Node").getDerived_from());
        final Map<Object, Object> map = yamlUtil.yamlToObject(yaml, Map.class);
        assertEquals("plain", map.get("description"));
    }
}