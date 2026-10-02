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

package org.openecomp.sdc.common.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataValidatorFilterAbstractTest {

    private static final long MAX_BODY_SIZE = 16;

    private TestDataValidatorFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        filter = new TestDataValidatorFilter();
        filter.init(null);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/sdc2/rest/v1/catalog/resources");
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/json");
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    }

    @Test
    void rejectsDeclaredContentLengthAboveLimitWithoutReadingBody() throws Exception {
        when(request.getContentLengthLong()).thenReturn(MAX_BODY_SIZE + 1);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, DataValidatorFilterAbstract.ERROR_REQUEST_BODY_TOO_LARGE);
        verify(request, never()).getInputStream();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsChunkedBodyAboveLimit() throws Exception {
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream()).thenReturn(servletInputStream("{\"name\":\"this body is too long\"}"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, DataValidatorFilterAbstract.ERROR_REQUEST_BODY_TOO_LARGE);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void passesBodyWithinLimitToChain() throws Exception {
        final String body = "{\"name\":\"ok\"}";
        when(request.getContentLengthLong()).thenReturn((long) body.length());
        when(request.getInputStream()).thenReturn(servletInputStream(body));

        filter.doFilter(request, response, chain);

        final ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        verify(response, never()).sendError(anyInt(), anyString());
        try (InputStream inputStream = captor.getValue().getInputStream()) {
            assertEquals(body, new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ServletInputStream servletInputStream(final String content) {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                // not used
            }

            @Override
            public int read() {
                return inputStream.read();
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) {
                return inputStream.read(buffer, offset, length);
            }
        };
    }

    private static class TestDataValidatorFilter extends DataValidatorFilterAbstract {

        @Override
        protected List<String> getDataValidatorFilterExcludedUrls() {
            return Collections.emptyList();
        }

        @Override
        protected long getMaxBodySize() {
            return MAX_BODY_SIZE;
        }
    }
}
