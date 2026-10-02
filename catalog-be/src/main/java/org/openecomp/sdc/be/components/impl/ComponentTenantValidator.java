/*
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2026 jacobsbenjaminuk-cell. All rights reserved.
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

import fj.data.Either;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.cassandra.AuditCassandraDao;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphDao;
import org.openecomp.sdc.be.dao.jsongraph.types.JsonParseFlagEnum;
import org.openecomp.sdc.be.datatypes.enums.ComponentTypeEnum;
import org.openecomp.sdc.be.model.Component;
import org.openecomp.sdc.be.model.ComponentParametersView;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.ToscaOperationFacade;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.be.resources.data.auditing.DistributionStatusEvent;
import org.openecomp.sdc.be.resources.data.auditing.ResourceAdminEvent;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.common.util.Multitenancy;

/**
 * Single place where the caller's tenants (Keycloak realm roles) are compared with the tenant stored on a component.
 * A component with no tenant is shared and visible to everyone.
 */
@org.springframework.stereotype.Component
public class ComponentTenantValidator {

    static final List<String> COMPONENT_ID_PARAMS = List.of("componentId", "serviceId", "resourceId", "containerComponentId",
        "componentUniqueId", "originComponentUid");
    static final List<String> COMPONENT_UUID_PARAMS = List.of("serviceUUID", "uuid", "componentUuid");
    static final String DISTRIBUTION_ID_PARAM = "did";
    static final String CSAR_UUID_PARAM = "csaruuid";
    private static final Logger log = Logger.getLogger(ComponentTenantValidator.class);
    private final ToscaOperationFacade toscaOperationFacade;
    private final AuditCassandraDao auditCassandraDao;
    private final JanusGraphDao janusGraphDao;

    public ComponentTenantValidator(ToscaOperationFacade toscaOperationFacade, AuditCassandraDao auditCassandraDao, JanusGraphDao janusGraphDao) {
        this.toscaOperationFacade = toscaOperationFacade;
        this.auditCassandraDao = auditCassandraDao;
        this.janusGraphDao = janusGraphDao;
    }

    public static boolean isMultitenancyEnabled() {
        return new Multitenancy().multiTenancyCheck();
    }

    public static Set<String> getCallerTenants(HttpServletRequest request) {
        Principal principal = request == null ? null : request.getUserPrincipal();
        if (!(principal instanceof KeycloakPrincipal)) {
            return Collections.emptySet();
        }
        KeycloakSecurityContext securityContext = ((KeycloakPrincipal<?>) principal).getKeycloakSecurityContext();
        AccessToken token = securityContext == null ? null : securityContext.getToken();
        AccessToken.Access realmAccess = token == null ? null : token.getRealmAccess();
        Set<String> roles = realmAccess == null ? null : realmAccess.getRoles();
        return roles == null ? Collections.emptySet() : roles;
    }

    public static boolean isTenantAllowed(String componentTenant, Set<String> callerTenants) {
        return StringUtils.isBlank(componentTenant) || callerTenants.contains(componentTenant);
    }

    /**
     * A new or imported component may only be given a tenant the caller belongs to.
     */
    public static boolean canAssignTenant(HttpServletRequest request, String tenant) {
        return !isMultitenancyEnabled() || (tenant != null && getCallerTenants(request).contains(tenant));
    }

    /**
     * Like {@link #canAssignTenant} but also accepts a component with no tenant, for paths that may run without a caller.
     */
    public static boolean canAssignOptionalTenant(HttpServletRequest request, String tenant) {
        return !isMultitenancyEnabled() || isTenantAllowed(tenant, getCallerTenants(request));
    }

    public static <T> List<T> filterByTenant(HttpServletRequest request, List<T> items, Function<T, String> tenantOf) {
        if (items == null || !isMultitenancyEnabled()) {
            return items;
        }
        return filterByTenant(getCallerTenants(request), items, tenantOf);
    }

    public static <T> Map<String, List<T>> filterByTenant(HttpServletRequest request, Map<String, List<T>> itemsByKey,
                                                          Function<T, String> tenantOf) {
        if (itemsByKey == null || !isMultitenancyEnabled()) {
            return itemsByKey;
        }
        Set<String> callerTenants = getCallerTenants(request);
        Map<String, List<T>> filtered = new LinkedHashMap<>();
        itemsByKey.forEach((key, items) -> filtered.put(key, items == null ? null : filterByTenant(callerTenants, items, tenantOf)));
        return filtered;
    }

    static <T> List<T> filterByTenant(Set<String> callerTenants, List<T> items, Function<T, String> tenantOf) {
        return items.stream().filter(item -> isTenantAllowed(tenantOf.apply(item), callerTenants)).collect(Collectors.toList());
    }

    /**
     * Checks every component referenced by the request path (unique id, UUID, name and version, or distribution id).
     *
     * @return true when the caller may access all referenced components
     */
    public boolean isPathAccessAllowed(Set<String> callerTenants, Map<String, List<String>> pathParams) {
        try {
            for (String param : COMPONENT_ID_PARAMS) {
                for (String id : values(pathParams, param)) {
                    if (!isAllowed(callerTenants, () -> toscaOperationFacade.getToscaElement(id, new ComponentParametersView(true)))) {
                        return false;
                    }
                }
            }
            for (String param : COMPONENT_UUID_PARAMS) {
                for (String uuid : values(pathParams, param)) {
                    if (!isUuidAllowed(callerTenants, uuid)) {
                        return false;
                    }
                }
            }
            if (!isNameAndVersionAllowed(callerTenants, pathParams, ComponentTypeEnum.RESOURCE, "resourceName", "version", "resourceVersion")
                || !isNameAndVersionAllowed(callerTenants, pathParams, ComponentTypeEnum.SERVICE, "serviceName", "version", "serviceVersion")) {
                return false;
            }
            for (String csarUuid : values(pathParams, CSAR_UUID_PARAM)) {
                if (!isAllowed(callerTenants,
                    () -> toscaOperationFacade.getLatestComponentByCsarOrName(ComponentTypeEnum.RESOURCE, csarUuid, "", JsonParseFlagEnum.ParseMetadata))) {
                    return false;
                }
            }
            for (String did : values(pathParams, DISTRIBUTION_ID_PARAM)) {
                if (!isDistributionAllowed(callerTenants, did)) {
                    return false;
                }
            }
            return true;
        } finally {
            janusGraphDao.rollback();
        }
    }

    private boolean isNameAndVersionAllowed(Set<String> callerTenants, Map<String, List<String>> pathParams, ComponentTypeEnum type,
                                            String nameParam, String... versionParams) {
        for (String name : values(pathParams, nameParam)) {
            for (String versionParam : versionParams) {
                for (String version : values(pathParams, versionParam)) {
                    if (!isAllowed(callerTenants,
                        () -> toscaOperationFacade.getComponentByNameAndVersion(type, name, version, JsonParseFlagEnum.ParseMetadata))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean isUuidAllowed(Set<String> callerTenants, String uuid) {
        return isAllowed(callerTenants, () -> toscaOperationFacade.getLatestComponentByUuid(uuid));
    }

    private boolean isDistributionAllowed(Set<String> callerTenants, String did) {
        Either<List<ResourceAdminEvent>, ActionStatus> requests = auditCassandraDao
            .getDistributionRequest(did, AuditingActionEnum.DISTRIBUTION_STATE_CHANGE_REQUEST.getName());
        Either<List<DistributionStatusEvent>, ActionStatus> statuses = auditCassandraDao.getListOfDistributionStatuses(did);
        if (requests.isRight() || statuses.isRight()) {
            log.debug("failed to resolve the service of distribution {}", did);
            return false;
        }
        Set<String> serviceUuids = new HashSet<>();
        requests.left().value().stream().map(ResourceAdminEvent::getServiceInstanceId).filter(StringUtils::isNotBlank).forEach(serviceUuids::add);
        statuses.left().value().stream().map(DistributionStatusEvent::getServiceInstanceId).filter(StringUtils::isNotBlank)
            .forEach(serviceUuids::add);
        if (serviceUuids.isEmpty()) {
            return requests.left().value().isEmpty() && statuses.left().value().isEmpty();
        }
        return serviceUuids.stream().allMatch(uuid -> isUuidAllowed(callerTenants, uuid));
    }

    private <T extends Component> boolean isAllowed(Set<String> callerTenants, Supplier<Either<T, StorageOperationStatus>> lookup) {
        Either<T, StorageOperationStatus> component = lookup.get();
        if (component.isRight()) {
            return component.right().value() == StorageOperationStatus.NOT_FOUND;
        }
        return isTenantAllowed(component.left().value().getTenant(), callerTenants);
    }

    private static Collection<String> values(Map<String, List<String>> pathParams, String name) {
        List<String> values = pathParams.get(name);
        return values == null ? Collections.emptyList() : values;
    }
}
