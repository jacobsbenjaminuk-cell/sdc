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
package org.openecomp.sdc.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openecomp.sdc.be.config.validation.DeploymentArtifactHeatConfiguration;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.resolver.Resolver;

class SafeYamlFactoryTest {

    private static final String GLOBAL_TAG_PAYLOAD = "exploit: !!java.net.URL [\"http://localhost/\"]\n";
    private static final String GADGET_PAYLOAD =
        "!!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://localhost/\"]]]]\n";

    @Test
    void loadsPlainYaml() {
        final Map<String, Object> result = SafeYamlFactory.create().load("tosca_definitions_version: tosca_simple_yaml_1_3\nlist: [1, two]\n");
        assertEquals("tosca_simple_yaml_1_3", result.get("tosca_definitions_version"));
        assertEquals(List.of(1, "two"), result.get("list"));
    }

    @Test
    void rejectsGlobalJavaTags() {
        assertThrows(YAMLException.class, () -> SafeYamlFactory.create().load(GLOBAL_TAG_PAYLOAD));
        assertThrows(YAMLException.class, () -> SafeYamlFactory.create().load(GADGET_PAYLOAD));
        assertThrows(YAMLException.class, () -> SafeYamlFactory.create().loadAll(GADGET_PAYLOAD).iterator().next());
    }

    @Test
    void rejectsGlobalJavaTagsWithCustomResolver() {
        final Resolver resolver = new Resolver() {
            @Override
            protected void addImplicitResolvers() {
                addImplicitResolver(Tag.STR, EMPTY, "");
            }
        };
        final Map<String, Object> result = SafeYamlFactory.create(resolver).load("flag: true\n");
        assertEquals("true", result.get("flag"));
        assertThrows(YAMLException.class, () -> SafeYamlFactory.create(resolver).load(GLOBAL_TAG_PAYLOAD));
    }

    @Test
    void rejectsAliasBomb() {
        final StringBuilder yaml = new StringBuilder("a0: &a0 [x]\n");
        for (int i = 1; i <= SafeYamlFactory.MAX_ALIASES_FOR_COLLECTIONS + 1; i++) {
            yaml.append("a").append(i).append(": &a").append(i).append(" [*a").append(i - 1).append(", *a").append(i - 1).append("]\n");
        }
        assertThrows(YAMLException.class, () -> SafeYamlFactory.create().load(yaml.toString()));
    }

    @Test
    void yamlToObjectConverterRejectsGlobalTags() {
        final YamlToObjectConverter converter = new YamlToObjectConverter();
        final byte[] payload = GLOBAL_TAG_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        assertFalse(converter.isValidYaml(payload));
        assertNull(converter.convert(payload, DeploymentArtifactHeatConfiguration.class));
        assertTrue(converter.isValidYaml("heat_template_version: 2013-05-23\n".getBytes(StandardCharsets.UTF_8)));
    }
}
