/*
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2022 Nordix Foundation. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.ServletContext;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openecomp.sdc.be.components.impl.DataTypeBusinessLogic;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.components.validation.UserValidations;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.datatypes.elements.DataTypeDataDefinition;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.WebAppContextWrapper;
import org.openecomp.sdc.be.model.PropertyDefinition;
import org.openecomp.sdc.be.model.User;
import org.openecomp.sdc.be.model.dto.PropertyDefinitionDto;
import org.openecomp.sdc.be.model.jsonjanusgraph.operations.exception.OperationException;
import org.openecomp.sdc.be.model.operations.impl.DataTypeOperation;
import org.openecomp.sdc.be.user.Role;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.exception.ResponseFormat;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(MockitoExtension.class)
class DataTypeServletTest extends JerseySpringBaseTest {

    private static final String USER_ID = "cs0008";
    private static final String DATA_TYPE_UID = "ETSI SOL001 v2.5.1.tosca.datatypes.nfv.L3AddressData.datatype";
    private static final String PATH = "/v1/catalog/data-types/" + DATA_TYPE_UID;
    private static final String DATA_TYPE_PROPERTIES_PATH = "/v1/catalog/data-types/%s/properties";
    private static final String PROPERTY_ID = DATA_TYPE_UID + ".property1";

    @InjectMocks
    private DataTypeServlet dataTypeServlet;
    private ComponentsUtils componentsUtils;
    private DataTypeOperation dataTypeOperation;
    private DataTypeBusinessLogic dataTypeBusinessLogic;
    private UserValidations userValidations;
    private ServletContext servletContext;
    private WebApplicationContext webApplicationContext;
    private WebAppContextWrapper webAppContextWrapper;

    @Override
    protected ResourceConfig configure() {
        initMocks();
        MockitoAnnotations.openMocks(this);
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return super.configure().register(dataTypeServlet);
    }

    private void initMocks() {
        componentsUtils = mock(ComponentsUtils.class);
        dataTypeOperation = mock(DataTypeOperation.class);
        dataTypeBusinessLogic = mock(DataTypeBusinessLogic.class);
        userValidations = mock(UserValidations.class);
        servletContext = Mockito.mock(ServletContext.class);
        webApplicationContext = Mockito.mock(WebApplicationContext.class);
        webAppContextWrapper = Mockito.mock(WebAppContextWrapper.class);
    }

    @BeforeEach
    void before() throws Exception {
        super.setUp();
        when(request.getSession()).thenReturn(session);
        when(session.getServletContext()).thenReturn(servletContext);
        when(webApplicationContext.getBean(ComponentsUtils.class)).thenReturn(componentsUtils);
        when(servletContext.getAttribute(Constants.WEB_APPLICATION_CONTEXT_WRAPPER_ATTR)).thenReturn(webAppContextWrapper);
        when(webAppContextWrapper.getWebAppContext(servletContext)).thenReturn(webApplicationContext);
    }

    @AfterEach
    void after() throws Exception {
        super.tearDown();
    }

    @Test
    void fetchDataTypeTest_Success() {
        final DataTypeDataDefinition expectedDataType = new DataTypeDataDefinition();
        expectedDataType.setUniqueId(DATA_TYPE_UID);
        when(componentsUtils.getResponseFormat(ActionStatus.OK)).thenReturn(new ResponseFormat(HttpStatus.SC_OK));
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.of(expectedDataType));

        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .get(Response.class);
        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        final DataTypeDataDefinition actualDataType = response.readEntity(DataTypeDataDefinition.class);
        assertEquals(expectedDataType.getUniqueId(), actualDataType.getUniqueId());
    }

    @Test
    void fetchDataTypeTest_Fail_OperationException() {
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenThrow(OperationException.class);

        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .get(Response.class);
        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void fetchDataTypeTest_Fail_EmptyOptional() {
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.empty());

        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .get(Response.class);
        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void fetchDataTypeTest_Fail_RuntimeException() {
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenThrow(RuntimeException.class);

        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .get(Response.class);
        assertNotNull(response);
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    void fetchDataTypePropertiesTest_Success() {
        final DataTypeDataDefinition expectedDataType = new DataTypeDataDefinition();
        final PropertyDefinition expectedProperty1 = new PropertyDefinition();
        expectedProperty1.setName("property1");
        final PropertyDefinition expectedProperty2 = new PropertyDefinition();
        expectedProperty2.setName("property2");
        expectedDataType.setUniqueId(DATA_TYPE_UID);
        when(dataTypeOperation.findAllProperties(DATA_TYPE_UID)).thenReturn(List.of(expectedProperty1, expectedProperty2));

        final Response response = target()
            .path(String.format(DATA_TYPE_PROPERTIES_PATH, DATA_TYPE_UID))
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .get(Response.class);
        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        final List<Map<String, Object>> actualResponse = response.readEntity(List.class);
        assertEquals(2, actualResponse.size());
        assertEquals(expectedProperty1.getName(), actualResponse.get(0).get("name"));
        assertEquals(expectedProperty2.getName(), actualResponse.get(1).get("name"));
    }

    @Test
    void deletePropertyTest_Success_Admin() {
        final User user = mockUser(USER_ID);
        final DataTypeDataDefinition dataType = buildDataType(false);
        final PropertyDefinitionDto deletedProperty = new PropertyDefinitionDto();
        deletedProperty.setName("property1");
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.of(dataType));
        when(dataTypeOperation.deleteProperty(dataType, PROPERTY_ID)).thenReturn(deletedProperty);

        final Response response = target()
            .path("/v1/catalog/data-types/" + DATA_TYPE_UID + "/" + PROPERTY_ID)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .delete(Response.class);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        verify(userValidations).validateUserRole(user, List.of(Role.ADMIN));
        verify(dataTypeOperation).deleteProperty(dataType, PROPERTY_ID);
    }

    @Test
    void deletePropertyTest_Fail_NotAdmin() {
        final User user = mockUser(USER_ID);
        doThrow(new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION))
            .when(userValidations).validateUserRole(user, List.of(Role.ADMIN));

        final Response response = target()
            .path("/v1/catalog/data-types/" + DATA_TYPE_UID + "/" + PROPERTY_ID)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .delete(Response.class);
        assertNotEquals(HttpStatus.SC_OK, response.getStatus());
        verify(dataTypeOperation, never()).getDataTypeByUid(anyString());
        verify(dataTypeOperation, never()).deleteProperty(any(), anyString());
    }

    @Test
    void deletePropertyTest_Fail_NormativeDataType() {
        mockUser(USER_ID);
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.of(buildDataType(true)));

        final Response response = target()
            .path("/v1/catalog/data-types/" + DATA_TYPE_UID + "/" + PROPERTY_ID)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .delete(Response.class);
        assertNotEquals(HttpStatus.SC_OK, response.getStatus());
        verify(dataTypeOperation, never()).deleteProperty(any(), anyString());
        verify(dataTypeOperation, never()).updatePropertyInAdditionalTypeDataType(any(), any(), any(Boolean.class));
    }

    @Test
    void createPropertyTest_Fail_NotAdmin() {
        final User user = mockUser(USER_ID);
        doThrow(new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION))
            .when(userValidations).validateUserRole(user, List.of(Role.ADMIN));

        final Response response = target()
            .path(String.format(DATA_TYPE_PROPERTIES_PATH, DATA_TYPE_UID))
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .post(Entity.json(buildPropertyDto()), Response.class);
        assertNotEquals(HttpStatus.SC_CREATED, response.getStatus());
        verify(dataTypeOperation, never()).createProperty(anyString(), any());
    }

    @Test
    void createPropertyTest_Fail_NormativeDataType() {
        mockUser(USER_ID);
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.of(buildDataType(true)));

        final Response response = target()
            .path(String.format(DATA_TYPE_PROPERTIES_PATH, DATA_TYPE_UID))
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .post(Entity.json(buildPropertyDto()), Response.class);
        assertNotEquals(HttpStatus.SC_CREATED, response.getStatus());
        verify(dataTypeOperation, never()).createProperty(anyString(), any());
    }

    @Test
    void updatePropertyTest_Fail_NormativeDataType() {
        mockUser(USER_ID);
        when(dataTypeOperation.getDataTypeByUid(DATA_TYPE_UID)).thenReturn(Optional.of(buildDataType(true)));

        final Response response = target()
            .path(String.format(DATA_TYPE_PROPERTIES_PATH, DATA_TYPE_UID))
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .put(Entity.json(buildPropertyDto()), Response.class);
        assertNotEquals(HttpStatus.SC_CREATED, response.getStatus());
        verify(dataTypeOperation, never()).updateProperty(anyString(), any());
    }

    @Test
    void deleteDataTypeTest_Fail_NotAdmin() {
        final User user = mockUser(USER_ID);
        doThrow(new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION))
            .when(userValidations).validateUserRole(user, List.of(Role.ADMIN));

        final Response response = target()
            .path(PATH)
            .request(MediaType.APPLICATION_JSON)
            .header("USER_ID", USER_ID)
            .delete(Response.class);
        assertNotEquals(HttpStatus.SC_OK, response.getStatus());
        verify(dataTypeOperation, never()).deleteDataTypesByDataTypeId(anyString());
    }

    private User mockUser(final String userId) {
        final User user = new User();
        user.setUserId(userId);
        when(userValidations.validateUserExists(userId)).thenReturn(user);
        return user;
    }

    private DataTypeDataDefinition buildDataType(final boolean normative) {
        final DataTypeDataDefinition dataType = new DataTypeDataDefinition();
        dataType.setUniqueId(DATA_TYPE_UID);
        dataType.setNormative(normative);
        return dataType;
    }

    private PropertyDefinitionDto buildPropertyDto() {
        final PropertyDefinitionDto property = new PropertyDefinitionDto();
        property.setName("property1");
        property.setType("string");
        return property;
    }

}
