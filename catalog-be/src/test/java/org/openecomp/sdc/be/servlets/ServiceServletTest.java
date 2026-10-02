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

package org.openecomp.sdc.be.servlets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import org.junit.jupiter.api.Test;

class ServiceServletTest {

    @Test
    void tempUrlToBeDeletedEndpointIsNotExposed() {
        List<String> paths = Arrays.stream(ServiceServlet.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(Path.class))
            .filter(path -> path != null)
            .map(Path::value)
            .collect(Collectors.toList());
        assertTrue(paths.stream().noneMatch(path -> path.contains("tempUrlToBeDeleted")), "Unexpected paths: " + paths);
        assertTrue(Arrays.stream(ServiceServlet.class.getDeclaredMethods()).map(Method::getName)
            .noneMatch("tempUrlToBeDeleted"::equals));
    }
}
