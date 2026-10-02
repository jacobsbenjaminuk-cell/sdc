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

package org.openecomp.server.filters;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openecomp.sdc.common.util.Multitenancy;
import org.openecomp.sdc.logging.api.Logger;
import org.openecomp.sdc.logging.api.LoggerFactory;
import org.openecomp.sdc.versioning.ItemManager;
import org.openecomp.sdc.versioning.ItemManagerFactory;
import org.openecomp.sdc.versioning.types.Item;

/**
 * When multitenancy is enabled, rejects every request scoped to a VSP, VLM or item whose tenant is not one of the
 * caller's Keycloak realm roles.
 */
public class TenantAuthorizationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantAuthorizationFilter.class);
    private static final String API_VERSION = "v1.0";
    private static final String VSP_ROOT = "vendor-software-products";
    private static final String PACKAGES = "packages";
    private static final Set<String> ITEM_ROOTS = Set.of(VSP_ROOT, "vendor-license-models", "items");
    private static final Set<String> VSP_COLLECTION_SEGMENTS = Set.of(PACKAGES, "validation-vsp");

    private final Multitenancy multitenancy;
    private final Supplier<ItemManager> itemManagerSupplier;
    private ItemManager itemManager;

    public TenantAuthorizationFilter() {
        this(new Multitenancy(), () -> ItemManagerFactory.getInstance().createInterface());
    }

    TenantAuthorizationFilter(Multitenancy multitenancy, Supplier<ItemManager> itemManagerSupplier) {
        this.multitenancy = multitenancy;
        this.itemManagerSupplier = itemManagerSupplier;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // required by servlet API
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest) || !multitenancy.multiTenancyCheck()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        Optional<String> itemId = parseItemId(request);
        if (itemId.isPresent()) {
            Item item = getItemManager().get(itemId.get());
            if (item != null && !multitenancy.isTenantAllowed(request, item.getTenant())) {
                LOGGER.error("Tenant of item {} is not authorized for the caller", itemId.get());
                ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    static Optional<String> parseItemId(HttpServletRequest request) {
        String path = (request.getServletPath() == null ? "" : request.getServletPath())
            + (request.getPathInfo() == null ? "" : request.getPathInfo());
        List<String> segments = Arrays.stream(path.split("/"))
            .map(segment -> segment.split(";", 2)[0])
            .filter(segment -> !segment.isEmpty())
            .collect(Collectors.toList());
        if (segments.size() < 3 || !API_VERSION.equals(segments.get(0)) || !ITEM_ROOTS.contains(segments.get(1))) {
            return Optional.empty();
        }
        String root = segments.get(1);
        String candidate = segments.get(2);
        if (VSP_ROOT.equals(root) && VSP_COLLECTION_SEGMENTS.contains(candidate)) {
            return PACKAGES.equals(candidate) && segments.size() > 3 ? Optional.of(segments.get(3)) : Optional.empty();
        }
        return Optional.of(candidate);
    }

    private synchronized ItemManager getItemManager() {
        if (itemManager == null) {
            itemManager = itemManagerSupplier.get();
        }
        return itemManager;
    }

    @Override
    public void destroy() {
        // required by servlet API
    }
}
