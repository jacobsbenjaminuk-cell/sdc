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

package org.openecomp.sdc.fe.impl;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.fe.config.Configuration;

/**
 * Decides whether an unidentified request may be served as the configured {@code defaultUserId}. That is only allowed when the operator has
 * explicitly set {@code allowAnonymousDefaultUser}, because it hands an unauthenticated caller a working identity.
 */
public final class AnonymousDefaultUser {

    private static final Logger log = Logger.getLogger(AnonymousDefaultUser.class.getName());

    private AnonymousDefaultUser() {
    }

    public static Optional<String> resolve(final Configuration configuration) {
        if (configuration == null || !configuration.isAllowAnonymousDefaultUser()) {
            return Optional.empty();
        }
        return Optional.ofNullable(StringUtils.trimToNull(configuration.getDefaultUserId()));
    }

    public static void logStartupState(final Configuration configuration) {
        if (configuration == null) {
            return;
        }
        final Optional<String> defaultUserId = resolve(configuration);
        if (defaultUserId.isPresent()) {
            log.warn("SECURITY WARNING: allowAnonymousDefaultUser is enabled. Every unauthenticated visitor is served as user '{}'. "
                + "This is for isolated development setups only; disable it on any instance reachable by untrusted users.", defaultUserId.get());
        } else if (configuration.isAllowAnonymousDefaultUser()) {
            log.warn("allowAnonymousDefaultUser is enabled but defaultUserId is empty, so unidentified requests are still rejected");
        } else if (StringUtils.isNotBlank(configuration.getDefaultUserId())) {
            log.info("defaultUserId is set but ignored because allowAnonymousDefaultUser is false; unidentified requests are rejected");
        }
    }
}
