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

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * Creates SnakeYAML instances for parsing untrusted content. They only build standard YAML types (maps, lists, scalars)
 * and reject global tags such as {@code !!java.net.URL}, so a document cannot instantiate arbitrary Java classes.
 */
public final class SafeYamlFactory {

    public static final int MAX_ALIASES_FOR_COLLECTIONS = 50;
    public static final int NESTING_DEPTH_LIMIT = 50;
    public static final int CODE_POINT_LIMIT = 3 * 1024 * 1024;

    private SafeYamlFactory() {
    }

    public static Yaml create() {
        return new Yaml(new SafeConstructor(createLoaderOptions()));
    }

    public static Yaml create(final Resolver resolver) {
        final LoaderOptions loaderOptions = createLoaderOptions();
        final DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(new SafeConstructor(loaderOptions), new Representer(dumperOptions), dumperOptions, loaderOptions, resolver);
    }

    public static LoaderOptions createLoaderOptions() {
        final LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowRecursiveKeys(false);
        loaderOptions.setMaxAliasesForCollections(MAX_ALIASES_FOR_COLLECTIONS);
        loaderOptions.setNestingDepthLimit(NESTING_DEPTH_LIMIT);
        loaderOptions.setCodePointLimit(CODE_POINT_LIMIT);
        return loaderOptions;
    }
}
