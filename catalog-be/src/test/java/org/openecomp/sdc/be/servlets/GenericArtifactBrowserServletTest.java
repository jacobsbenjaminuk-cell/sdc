/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2026 Nokia Intellectual Property. All rights reserved.
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onap.sdc.gab.model.GABQuery;
import org.openecomp.sdc.be.components.impl.ArtifactsBusinessLogic;
import org.openecomp.sdc.be.components.impl.GenericArtifactBrowserBusinessLogic;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.info.GenericArtifactQueryInfo;
import org.openecomp.sdc.exception.ResponseFormat;

class GenericArtifactBrowserServletTest {

    private static final String PARENT_ID = "parentId";
    private static final String ARTIFACT_ID = "artifactId";

    @Mock
    private ComponentsUtils componentsUtils;
    @Mock
    private ArtifactsBusinessLogic artifactsBusinessLogic;
    @Mock
    private GenericArtifactBrowserBusinessLogic gabLogic;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpSession session;
    @Mock
    private ServletContext servletContext;

    private GenericArtifactBrowserServlet servlet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(request.getSession()).thenReturn(session);
        when(session.getServletContext()).thenReturn(servletContext);
        when(componentsUtils.getResponseFormat(ActionStatus.INVALID_CONTENT)).thenReturn(new ResponseFormat(400));
        servlet = spy(new GenericArtifactBrowserServlet(componentsUtils, artifactsBusinessLogic, gabLogic));
        doReturn(artifactsBusinessLogic).when(servlet).getArtifactBL(any());
        doReturn(gabLogic).when(servlet).getGenericArtifactBrowserBL(any());
    }

    @Test
    void shouldSearchArtifactWithinLimits() throws Exception {
        byte[] payload = "event: {presence: required}".getBytes(StandardCharsets.UTF_8);
        when(artifactsBusinessLogic.downloadArtifact(PARENT_ID, ARTIFACT_ID)).thenReturn(ImmutablePair.of("a.yml", payload));
        when(gabLogic.searchFor(any(GABQuery.class))).thenReturn("{}");

        Response response = servlet.searchFor(query(Set.of("event.presence")), request);

        assertEquals(200, response.getStatus());
        verify(gabLogic).searchFor(any(GABQuery.class));
    }

    @Test
    void shouldRejectTooManyFieldsWithoutDownloadingArtifact() {
        Set<String> fields = IntStream.rangeClosed(0, GenericArtifactBrowserServlet.MAX_QUERY_FIELDS).mapToObj(i -> "event.f" + i)
            .collect(Collectors.toSet());

        Response response = servlet.searchFor(query(fields), request);

        assertEquals(400, response.getStatus());
        verifyNoInteractions(artifactsBusinessLogic, gabLogic);
    }

    @Test
    void shouldRejectTooLongField() {
        String field = "a".repeat(GenericArtifactBrowserServlet.MAX_QUERY_FIELD_LENGTH + 1);

        Response response = servlet.searchFor(query(Set.of(field)), request);

        assertEquals(400, response.getStatus());
        verifyNoInteractions(artifactsBusinessLogic, gabLogic);
    }

    @Test
    void shouldRejectMissingFields() {
        Response response = servlet.searchFor(query(null), request);

        assertEquals(400, response.getStatus());
        verifyNoInteractions(artifactsBusinessLogic, gabLogic);
    }

    @Test
    void shouldRejectArtifactLargerThanLimit() throws Exception {
        byte[] payload = new byte[GenericArtifactBrowserServlet.MAX_ARTIFACT_SIZE_BYTES + 1];
        when(artifactsBusinessLogic.downloadArtifact(PARENT_ID, ARTIFACT_ID)).thenReturn(ImmutablePair.of("a.yml", payload));

        Response response = servlet.searchFor(query(Set.of("event.presence")), request);

        assertEquals(400, response.getStatus());
        verify(gabLogic, never()).searchFor(any(GABQuery.class));
    }

    private GenericArtifactQueryInfo query(Set<String> fields) {
        return new GenericArtifactQueryInfo(fields, PARENT_ID, ARTIFACT_ID);
    }
}
