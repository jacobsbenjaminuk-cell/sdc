/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import fj.data.Either;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openecomp.sdc.be.catalog.enums.ChangeTypeEnum;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.components.validation.AccessValidations;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphDao;
import org.openecomp.sdc.be.dao.jsongraph.types.JsonParseFlagEnum;
import org.openecomp.sdc.be.datatypes.enums.ComponentTypeEnum;
import org.openecomp.sdc.be.facade.operations.CatalogOperation;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.model.Component;
import org.openecomp.sdc.be.model.ComponentParametersView;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.ArchiveOperation;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.ToscaOperationFacade;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.be.user.Role;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.openecomp.sdc.exception.ResponseFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ArchiveBusinessLogicTest {
    @Mock
    private ComponentsUtils componentsUtils;
    @Mock
    private ToscaOperationFacade toscaOperationFacade;
    @Mock
    private User user;
    @Mock
    private ResponseFormat responseFormat;
    @Mock
    private Component component;
    @Mock
    private AccessValidations accessValidations;
    @Mock
    private ArchiveOperation archiveOperation;
    @Mock
    private JanusGraphDao janusGraphDao;
    @Mock
    private CatalogOperation catalogOperations;

    @InjectMocks
    private ArchiveBusinessLogic archiveBusinessLogic;

    @BeforeClass
    public static void setUpConfiguration() {
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(), "src/test/resources/config/catalog-be"));
    }

    @Test
    public void auditLastComponentVersionOnlyAndIgnorePreviousVersions() {
        List<String> archivedCompIds = Arrays.asList("1", "2", "3", "4", "5");
        when(toscaOperationFacade.getToscaElement(any(String.class), any(ComponentParametersView.class)))
                .thenReturn(Either.left(component));
        when(componentsUtils.getResponseFormat(ActionStatus.OK)).thenReturn(responseFormat);
        when(component.getUUID())
                .thenReturn("1")
                .thenReturn("1")
                .thenReturn("2")
                .thenReturn("3")
                .thenReturn("2");

        archiveBusinessLogic.auditAction(ArchiveOperation.Action.ARCHIVE, archivedCompIds, user, ComponentTypeEnum.RESOURCE_PARAM_NAME);
        verify(componentsUtils, times(3)).auditComponentAdmin(eq(responseFormat), eq(user), eq(component),
                eq(AuditingActionEnum.ARCHIVE_COMPONENT), eq(ComponentTypeEnum.RESOURCE), any(String.class));
    }


    @Test
    public void auditLastComponentVersionOnly() {
        List<String> archivedCompIds = Arrays.asList("1", "2", "3", "4", "5");
        when(toscaOperationFacade.getToscaElement(any(String.class), any(ComponentParametersView.class)))
                .thenReturn(Either.left(component));
        when(componentsUtils.getResponseFormat(ActionStatus.OK)).thenReturn(responseFormat);
        when(component.getUUID())
                .thenReturn("1")
                .thenReturn("2")
                .thenReturn("4")
                .thenReturn("5")
                .thenReturn("3");

        archiveBusinessLogic.auditAction(ArchiveOperation.Action.RESTORE, archivedCompIds, user, ComponentTypeEnum.RESOURCE_PARAM_NAME);
        verify(componentsUtils, times(5)).auditComponentAdmin(eq(responseFormat), eq(user), eq(component),
                eq(AuditingActionEnum.RESTORE_COMPONENT), eq(ComponentTypeEnum.RESOURCE), any(String.class));
    }


    @Test
    public void noAuditDoneForEmptyList() {
        List<String> archivedCompIds = ImmutableList.of();
        archiveBusinessLogic.auditAction(ArchiveOperation.Action.RESTORE, archivedCompIds, user, ComponentTypeEnum.RESOURCE_PARAM_NAME);
        verify(componentsUtils, times(0)).auditComponentAdmin(any(ResponseFormat.class), any(User.class), any(Component.class),
                any(AuditingActionEnum.class), any(ComponentTypeEnum.class), any(String.class));
    }


    @Test
    public void noAuditOnErrorGetElementResponse() {
        List<String> archivedCompIds = Arrays.asList("1", "2", "3", "4", "5");
        when(toscaOperationFacade.getToscaElement(any(String.class), any(ComponentParametersView.class)))
                .thenReturn(Either.right(StorageOperationStatus.NOT_FOUND));
        archiveBusinessLogic.auditAction(ArchiveOperation.Action.RESTORE, archivedCompIds, user, ComponentTypeEnum.RESOURCE_PARAM_NAME);
        verify(componentsUtils, times(0)).auditComponentAdmin(any(ResponseFormat.class), any(User.class), any(Component.class),
                any(AuditingActionEnum.class), any(ComponentTypeEnum.class), any(String.class));
    }

    private static final String COMPONENT_ID = "componentId";
    private static final String USER_ID = "designer1";

    private void givenCaller(Role role) {
        when(accessValidations.userIsAdminOrDesigner(eq(USER_ID), anyString())).thenReturn(user);
        when(user.getRole()).thenReturn(role.name());
    }

    private void givenComponentOwnedBy(String creatorUserId, String lastUpdaterUserId) {
        when(user.getUserId()).thenReturn(USER_ID);
        when(toscaOperationFacade.getToscaElement(COMPONENT_ID, JsonParseFlagEnum.ParseMetadata)).thenReturn(Either.left(component));
        when(component.isHighestVersion()).thenReturn(true);
        when(component.getCreatorUserId()).thenReturn(creatorUserId);
        when(component.getLastUpdaterUserId()).thenReturn(lastUpdaterUserId);
    }

    private void givenOlderVersionRequested() {
        when(user.getUserId()).thenReturn(USER_ID);
        when(toscaOperationFacade.getToscaElement(COMPONENT_ID, JsonParseFlagEnum.ParseMetadata)).thenReturn(Either.left(component));
        when(component.isHighestVersion()).thenReturn(false);
        when(component.getUUID()).thenReturn("componentUuid");
    }

    @Test
    public void archiveRejectedWhenDesignerOnlyOwnsOlderVersion() {
        givenCaller(Role.DESIGNER);
        givenOlderVersionRequested();
        Component latestVersion = mock(Component.class);
        when(latestVersion.getCreatorUserId()).thenReturn("someoneElse");
        when(latestVersion.getLastUpdaterUserId()).thenReturn("anotherUser");
        when(toscaOperationFacade.getLatestComponentByUuid("componentUuid")).thenReturn(Either.left(latestVersion));
        ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class,
                () -> archiveBusinessLogic.archiveComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID));
        assertEquals(ActionStatus.RESTRICTED_OPERATION, e.getActionStatus());
        verify(archiveOperation, never()).archiveComponent(anyString());
    }

    @Test
    public void restoreAllowedWhenDesignerOwnsLatestVersion() {
        givenCaller(Role.DESIGNER);
        givenOlderVersionRequested();
        Component latestVersion = mock(Component.class);
        when(latestVersion.getLastUpdaterUserId()).thenReturn(USER_ID);
        when(toscaOperationFacade.getLatestComponentByUuid("componentUuid")).thenReturn(Either.left(latestVersion));
        when(archiveOperation.restoreComponent(COMPONENT_ID)).thenReturn(Either.left(Collections.emptyList()));
        givenFacadeNotificationSucceeds(ChangeTypeEnum.RESTORE);
        archiveBusinessLogic.restoreComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID);
        verify(archiveOperation).restoreComponent(COMPONENT_ID);
    }

    private void givenFacadeNotificationSucceeds(ChangeTypeEnum changeType) {
        when(toscaOperationFacade.getToscaElement(COMPONENT_ID)).thenReturn(Either.left(component));
        when(catalogOperations.updateCatalog(changeType, component)).thenReturn(ActionStatus.OK);
    }

    @Test
    public void archiveRejectedWhenDesignerDoesNotOwnComponent() {
        givenCaller(Role.DESIGNER);
        givenComponentOwnedBy("someoneElse", "anotherUser");
        ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class,
                () -> archiveBusinessLogic.archiveComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID));
        assertEquals(ActionStatus.RESTRICTED_OPERATION, e.getActionStatus());
        verify(archiveOperation, never()).archiveComponent(anyString());
    }

    @Test
    public void restoreRejectedWhenDesignerDoesNotOwnComponent() {
        givenCaller(Role.DESIGNER);
        givenComponentOwnedBy("someoneElse", "anotherUser");
        ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class,
                () -> archiveBusinessLogic.restoreComponent(ComponentTypeEnum.SERVICE_PARAM_NAME, USER_ID, COMPONENT_ID));
        assertEquals(ActionStatus.RESTRICTED_OPERATION, e.getActionStatus());
        verify(archiveOperation, never()).restoreComponent(anyString());
    }

    @Test
    public void archiveAllowedForCreator() {
        givenCaller(Role.DESIGNER);
        givenComponentOwnedBy(USER_ID, "anotherUser");
        when(archiveOperation.archiveComponent(COMPONENT_ID)).thenReturn(Either.left(Collections.emptyList()));
        givenFacadeNotificationSucceeds(ChangeTypeEnum.ARCHIVE);
        archiveBusinessLogic.archiveComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID);
        verify(archiveOperation).archiveComponent(COMPONENT_ID);
    }

    @Test
    public void restoreAllowedForLastUpdater() {
        givenCaller(Role.DESIGNER);
        givenComponentOwnedBy("someoneElse", USER_ID);
        when(archiveOperation.restoreComponent(COMPONENT_ID)).thenReturn(Either.left(Collections.emptyList()));
        givenFacadeNotificationSucceeds(ChangeTypeEnum.RESTORE);
        archiveBusinessLogic.restoreComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID);
        verify(archiveOperation).restoreComponent(COMPONENT_ID);
    }

    @Test
    public void archiveAllowedForAdminWithoutOwnership() {
        givenCaller(Role.ADMIN);
        when(archiveOperation.archiveComponent(COMPONENT_ID)).thenReturn(Either.left(Collections.emptyList()));
        givenFacadeNotificationSucceeds(ChangeTypeEnum.ARCHIVE);
        archiveBusinessLogic.archiveComponent(ComponentTypeEnum.SERVICE_PARAM_NAME, USER_ID, COMPONENT_ID);
        verify(archiveOperation).archiveComponent(COMPONENT_ID);
        verify(toscaOperationFacade, never()).getToscaElement(anyString(), any(JsonParseFlagEnum.class));
    }

    @Test
    public void archiveOfUnknownComponentReturnsNotFound() {
        givenCaller(Role.DESIGNER);
        when(toscaOperationFacade.getToscaElement(COMPONENT_ID, JsonParseFlagEnum.ParseMetadata))
                .thenReturn(Either.right(StorageOperationStatus.NOT_FOUND));
        ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class,
                () -> archiveBusinessLogic.archiveComponent(ComponentTypeEnum.RESOURCE_PARAM_NAME, USER_ID, COMPONENT_ID));
        assertEquals(ActionStatus.RESOURCE_NOT_FOUND, e.getActionStatus());
        verify(archiveOperation, never()).archiveComponent(anyString());
    }

    private static final String VSP_TOKEN = "vsp-notification-token";
    private static final List<String> CSAR_UUIDS = Collections.singletonList("csarUuid");

    private static void configureVspToken(String token) {
        ConfigurationManager.getConfigurationManager().getConfiguration().setVspNotificationToken(token);
    }

    @After
    public void resetVspToken() {
        configureVspToken(null);
    }

    private void assertVspNotificationRejected(ThrowingRunnable call) {
        ByActionStatusComponentException e = assertThrows(ByActionStatusComponentException.class, call);
        assertEquals(ActionStatus.RESTRICTED_OPERATION, e.getActionStatus());
        verify(accessValidations, never()).userIsAdminOrDesigner(anyString(), anyString());
        verify(archiveOperation, never()).onVspArchived(anyString());
        verify(archiveOperation, never()).onVspRestored(anyString());
    }

    @Test
    public void vspArchiveRejectedWithoutToken() {
        configureVspToken(VSP_TOKEN);
        assertVspNotificationRejected(() -> archiveBusinessLogic.onVspArchive(USER_ID, null, CSAR_UUIDS));
    }

    @Test
    public void vspRestoreRejectedWithWrongToken() {
        configureVspToken(VSP_TOKEN);
        assertVspNotificationRejected(() -> archiveBusinessLogic.onVspRestore(USER_ID, "wrong-token", CSAR_UUIDS));
    }

    @Test
    public void vspNotificationRejectedWhenNoTokenConfigured() {
        configureVspToken("");
        assertVspNotificationRejected(() -> archiveBusinessLogic.onVspArchive(USER_ID, "", CSAR_UUIDS));
    }

    @Test
    public void vspArchiveAcceptedWithValidToken() {
        configureVspToken(VSP_TOKEN);
        when(archiveOperation.onVspArchived("csarUuid")).thenReturn(ActionStatus.OK);
        assertTrue(archiveBusinessLogic.onVspArchive(USER_ID, VSP_TOKEN, CSAR_UUIDS).isEmpty());
        verify(archiveOperation).onVspArchived("csarUuid");
    }

    @Test
    public void vspRestoreAcceptedWithValidToken() {
        configureVspToken(VSP_TOKEN);
        when(archiveOperation.onVspRestored("csarUuid")).thenReturn(ActionStatus.OK);
        assertTrue(archiveBusinessLogic.onVspRestore(USER_ID, VSP_TOKEN, CSAR_UUIDS).isEmpty());
        verify(archiveOperation).onVspRestored("csarUuid");
    }
}
