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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fj.data.Either;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.openecomp.sdc.be.model.Resource;
import org.openecomp.sdc.be.model.Service;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.ToscaOperationFacade;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.be.resources.data.auditing.DistributionStatusEvent;
import org.openecomp.sdc.be.resources.data.auditing.ResourceAdminEvent;

class ComponentTenantValidatorTest {

    private static final Set<String> TENANT_A = Set.of("tenantA");
    private ToscaOperationFacade toscaOperationFacade;
    private AuditCassandraDao auditCassandraDao;
    private JanusGraphDao janusGraphDao;
    private ComponentTenantValidator validator;

    @BeforeEach
    void setUp() {
        toscaOperationFacade = mock(ToscaOperationFacade.class);
        auditCassandraDao = mock(AuditCassandraDao.class);
        janusGraphDao = mock(JanusGraphDao.class);
        validator = new ComponentTenantValidator(toscaOperationFacade, auditCassandraDao, janusGraphDao);
    }

    @Test
    void tenantAllowedOnlyForMemberOrSharedComponent() {
        assertTrue(ComponentTenantValidator.isTenantAllowed("tenantA", TENANT_A));
        assertTrue(ComponentTenantValidator.isTenantAllowed(null, TENANT_A));
        assertTrue(ComponentTenantValidator.isTenantAllowed("", Collections.emptySet()));
        assertFalse(ComponentTenantValidator.isTenantAllowed("tenantB", TENANT_A));
        assertFalse(ComponentTenantValidator.isTenantAllowed("tenantA", Collections.emptySet()));
    }

    @Test
    void callerTenantsComeFromKeycloakRealmRoles() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        KeycloakPrincipal<KeycloakSecurityContext> principal = mock(KeycloakPrincipal.class);
        KeycloakSecurityContext securityContext = mock(KeycloakSecurityContext.class);
        AccessToken token = new AccessToken();
        AccessToken.Access realmAccess = new AccessToken.Access();
        realmAccess.addRole("tenantA");
        token.setRealmAccess(realmAccess);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(principal.getKeycloakSecurityContext()).thenReturn(securityContext);
        when(securityContext.getToken()).thenReturn(token);

        assertEquals(TENANT_A, ComponentTenantValidator.getCallerTenants(request));
    }

    @Test
    void callerWithoutKeycloakPrincipalHasNoTenants() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertTrue(ComponentTenantValidator.getCallerTenants(request).isEmpty());
        assertTrue(ComponentTenantValidator.getCallerTenants(null).isEmpty());
    }

    @Test
    void filterKeepsOwnAndSharedComponents() {
        Component own = component("tenantA");
        Component shared = component(null);
        Component other = component("tenantB");
        List<Component> filtered = ComponentTenantValidator.filterByTenant(TENANT_A, List.of(own, shared, other), Component::getTenant);
        assertEquals(List.of(own, shared), filtered);
    }

    @Test
    void requestFilterIsNoOpWhenMultitenancyDisabled() {
        List<Component> components = List.of(component("tenantB"));
        Map<String, List<Component>> byType = Map.of("resources", components);
        Function<Component, String> tenantOf = Component::getTenant;
        assertSame(components, ComponentTenantValidator.filterByTenant(mock(HttpServletRequest.class), components, tenantOf));
        assertSame(byType, ComponentTenantValidator.filterByTenant(mock(HttpServletRequest.class), byType, tenantOf));
        assertTrue(ComponentTenantValidator.canAssignTenant(mock(HttpServletRequest.class), "tenantB"));
    }

    @Test
    void componentIdOfOtherTenantIsDenied() {
        Component other = component("tenantB");
        when(toscaOperationFacade.getToscaElement(eq("id-b"), any(ComponentParametersView.class))).thenReturn(Either.left(other));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("serviceId", List.of("id-b"))));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("componentId", List.of("id-b"))));
        verify(janusGraphDao, times(2)).rollback();
    }

    @Test
    void componentIdOfOwnTenantOrUnknownIdIsAllowed() {
        Component own = component("tenantA");
        when(toscaOperationFacade.getToscaElement(eq("id-a"), any(ComponentParametersView.class))).thenReturn(Either.left(own));
        when(toscaOperationFacade.getToscaElement(eq("missing"), any(ComponentParametersView.class)))
            .thenReturn(Either.right(StorageOperationStatus.NOT_FOUND));
        assertTrue(validator.isPathAccessAllowed(TENANT_A, Map.of("resourceId", List.of("id-a"))));
        assertTrue(validator.isPathAccessAllowed(TENANT_A, Map.of("resourceId", List.of("missing"))));
        assertTrue(validator.isPathAccessAllowed(TENANT_A, Map.of("componentType", List.of("services"))));
    }

    @Test
    void lookupErrorIsDenied() {
        when(toscaOperationFacade.getToscaElement(eq("id-x"), any(ComponentParametersView.class)))
            .thenReturn(Either.right(StorageOperationStatus.GENERAL_ERROR));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("componentId", List.of("id-x"))));
    }

    @Test
    void serviceUuidOfOtherTenantIsDenied() {
        Component other = component("tenantB");
        when(toscaOperationFacade.getLatestComponentByUuid("uuid-b")).thenReturn(Either.left(other));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("serviceUUID", List.of("uuid-b"))));
    }

    @Test
    void nameAndVersionOfOtherTenantIsDenied() {
        Component other = component("tenantB");
        when(toscaOperationFacade.getComponentByNameAndVersion(ComponentTypeEnum.SERVICE, "svc", "1.0", JsonParseFlagEnum.ParseMetadata))
            .thenReturn(Either.left(other));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("serviceName", List.of("svc"), "version", List.of("1.0"))));
    }

    @Test
    void csarUuidOfOtherTenantIsDenied() {
        Resource other = new Resource();
        other.setTenant("tenantB");
        when(toscaOperationFacade.getLatestComponentByCsarOrName(ComponentTypeEnum.RESOURCE, "csar-b", "", JsonParseFlagEnum.ParseMetadata))
            .thenReturn(Either.left(other));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("csaruuid", List.of("csar-b"))));
    }

    @Test
    void optionalTenantAssignmentAllowedWhenMultitenancyDisabled() {
        assertTrue(ComponentTenantValidator.canAssignOptionalTenant(mock(HttpServletRequest.class), "tenantB"));
        assertTrue(ComponentTenantValidator.canAssignOptionalTenant(mock(HttpServletRequest.class), null));
    }

    @Test
    void distributionOfOtherTenantServiceIsDenied() {
        ResourceAdminEvent request = mock(ResourceAdminEvent.class);
        when(request.getServiceInstanceId()).thenReturn("uuid-b");
        when(auditCassandraDao.getDistributionRequest("did-b", AuditingActionEnum.DISTRIBUTION_STATE_CHANGE_REQUEST.getName()))
            .thenReturn(Either.left(List.of(request)));
        when(auditCassandraDao.getListOfDistributionStatuses("did-b")).thenReturn(Either.left(Collections.emptyList()));
        Component other = component("tenantB");
        when(toscaOperationFacade.getLatestComponentByUuid("uuid-b")).thenReturn(Either.left(other));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("did", List.of("did-b"))));
    }

    @Test
    void distributionOfOwnTenantServiceIsAllowed() {
        DistributionStatusEvent status = mock(DistributionStatusEvent.class);
        when(status.getServiceInstanceId()).thenReturn("uuid-a");
        when(auditCassandraDao.getDistributionRequest("did-a", AuditingActionEnum.DISTRIBUTION_STATE_CHANGE_REQUEST.getName()))
            .thenReturn(Either.left(Collections.emptyList()));
        when(auditCassandraDao.getListOfDistributionStatuses("did-a")).thenReturn(Either.left(List.of(status)));
        Component own = component("tenantA");
        when(toscaOperationFacade.getLatestComponentByUuid("uuid-a")).thenReturn(Either.left(own));
        assertTrue(validator.isPathAccessAllowed(TENANT_A, Map.of("did", List.of("did-a"))));
    }

    @Test
    void distributionWithUnresolvableServiceIsDenied() {
        DistributionStatusEvent status = mock(DistributionStatusEvent.class);
        when(auditCassandraDao.getDistributionRequest("did-x", AuditingActionEnum.DISTRIBUTION_STATE_CHANGE_REQUEST.getName()))
            .thenReturn(Either.left(Collections.emptyList()));
        when(auditCassandraDao.getListOfDistributionStatuses("did-x")).thenReturn(Either.left(List.of(status)));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("did", List.of("did-x"))));
        when(auditCassandraDao.getListOfDistributionStatuses("did-x")).thenReturn(Either.right(ActionStatus.GENERAL_ERROR));
        assertFalse(validator.isPathAccessAllowed(TENANT_A, Map.of("did", List.of("did-x"))));
    }

    private static Component component(String tenant) {
        Component component = new Service();
        component.setTenant(tenant);
        return component;
    }
}
