/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ==============================================================================
 * Copyright (C) 2026 Benjamin Jacobs. All rights reserved.
 * ==============================================================================
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fj.data.Either;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openecomp.sdc.be.components.impl.ComponentInstanceBusinessLogic;
import org.openecomp.sdc.be.components.impl.ComponentInterfaceOperationBusinessLogic;
import org.openecomp.sdc.be.components.impl.ResourceImportManager;
import org.openecomp.sdc.be.components.impl.exceptions.BusinessLogicException;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.datatypes.enums.ComponentTypeEnum;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.ServletUtils;
import org.openecomp.sdc.be.model.InterfaceDefinition;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.resources.data.auditing.AuditingActionEnum;
import org.openecomp.sdc.be.servlets.exception.ComponentExceptionMapper;
import org.openecomp.sdc.be.ui.model.UiComponentDataTransfer;
import org.openecomp.sdc.common.impl.ExternalConfiguration;
import org.openecomp.sdc.common.impl.FSConfigurationSource;
import org.springframework.mock.web.DelegatingServletInputStream;

@ExtendWith(MockitoExtension.class)
class ComponentInterfaceOperationServletTest {

    private static final String COMPONENT_ID = "component-id";
    private static final String INSTANCE_ID = "instance-id";
    private static final String USER_ID = "cs0008";

    @Mock
    private ComponentInstanceBusinessLogic componentInstanceBusinessLogic;
    @Mock
    private ComponentInterfaceOperationBusinessLogic businessLogic;
    @Mock
    private ComponentsUtils componentsUtils;
    @Mock
    private ServletUtils servletUtils;
    @Mock
    private ResourceImportManager resourceImportManager;
    @Mock
    private HttpServletRequest request;

    private ComponentInterfaceOperationServlet servlet;
    private InterfaceDefinition interfaceDefinition;

    @BeforeAll
    static void initConfiguration() {
        new ConfigurationManager(new FSConfigurationSource(ExternalConfiguration.getChangeListener(),
            "src/test/resources/config/catalog-be"));
    }

    @BeforeEach
    void setUp() throws IOException {
        servlet = new ComponentInterfaceOperationServlet(componentInstanceBusinessLogic, componentsUtils, servletUtils,
            resourceImportManager, businessLogic);
        when(servletUtils.getComponentsUtils()).thenReturn(componentsUtils);
        final User user = new User();
        user.setUserId(USER_ID);
        when(businessLogic.validateUser(USER_ID)).thenReturn(user);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));
        interfaceDefinition = new InterfaceDefinition();
        final UiComponentDataTransfer data = new UiComponentDataTransfer();
        data.setInterfaces(Map.of("interface", interfaceDefinition));
        when(componentsUtils.convertJsonToObjectUsingObjectMapper(eq("{}"), eq(user), eq(UiComponentDataTransfer.class),
            eq(AuditingActionEnum.UPDATE_RESOURCE_METADATA), eq(ComponentTypeEnum.RESOURCE))).thenReturn(Either.left(data));
    }

    @ParameterizedTest
    @EnumSource(InterfaceMutation.class)
    void passesCallerIdentityAndPropagatesAuthorisationFailure(InterfaceMutation mutation) throws BusinessLogicException {
        final ByActionStatusComponentException denial = new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION);
        switch (mutation) {
            case UPDATE_INSTANCE:
                when(businessLogic.updateComponentInstanceInterfaceOperation(eq(COMPONENT_ID), eq(USER_ID), eq(INSTANCE_ID),
                    eq(interfaceDefinition), eq(ComponentTypeEnum.RESOURCE), any(), eq(true))).thenThrow(denial);
                break;
            case CREATE_INSTANCE:
                when(businessLogic.createComponentInstanceInterfaceOperation(eq(COMPONENT_ID), eq(USER_ID), eq(INSTANCE_ID),
                    eq(interfaceDefinition), eq(ComponentTypeEnum.RESOURCE), any(), eq(true))).thenThrow(denial);
                break;
            case CREATE_RESOURCE:
                when(businessLogic.createInterfaceOperationInResource(eq(COMPONENT_ID), eq(USER_ID), eq(interfaceDefinition),
                    eq(ComponentTypeEnum.RESOURCE), any(), eq(true))).thenThrow(denial);
                break;
            default:
                throw new IllegalArgumentException("Unsupported mutation: " + mutation);
        }

        assertSame(denial, assertThrows(ByActionStatusComponentException.class, () -> mutateInterface(mutation)));
        assertEquals(Response.Status.FORBIDDEN.getStatusCode(), new ComponentExceptionMapper(componentsUtils).toResponse(denial).getStatus());
    }

    private Response mutateInterface(InterfaceMutation mutation) throws IOException {
        switch (mutation) {
            case UPDATE_INSTANCE:
                return servlet.updateComponentInstanceInterfaceOperation("resources", COMPONENT_ID, INSTANCE_ID, request, USER_ID);
            case CREATE_INSTANCE:
                return servlet.createComponentInstanceInterfaceOperation("resources", COMPONENT_ID, INSTANCE_ID, request, USER_ID);
            case CREATE_RESOURCE:
                return servlet.createInterfaceOperationInResource("resources", COMPONENT_ID, request, USER_ID);
            default:
                throw new IllegalArgumentException("Unsupported mutation: " + mutation);
        }
    }

    private enum InterfaceMutation {
        UPDATE_INSTANCE, CREATE_INSTANCE, CREATE_RESOURCE
    }
}
