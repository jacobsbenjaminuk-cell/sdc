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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import org.openecomp.sdc.common.log.enums.EcompLoggerErrorCode;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.exception.RequestBodyTooLargeException;

/**
 * Provides mechanism to wrap request's InputStream and read it more than once.
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    private static final Logger LOGGER = Logger.getLogger(RequestWrapper.class);
    private static final int BUFFER_SIZE = 8192;

    @Getter
    private final String body;

    /**
     * Reads and stores the request body.
     *
     * @param request     the request to wrap
     * @param maxBodySize the maximum number of body bytes to read
     * @throws RequestBodyTooLargeException if the body is larger than {@code maxBodySize}
     */
    public RequestWrapper(final HttpServletRequest request, final long maxBodySize) throws IOException {
        //So that other request method behave just like before
        super(request);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (final InputStream inputStream = request.getInputStream()) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxBodySize) {
                    throw new RequestBodyTooLargeException(maxBodySize);
                }
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (RequestBodyTooLargeException ex) {
            throw ex;
        } catch (IOException ex) {
            LOGGER.warn(EcompLoggerErrorCode.UNKNOWN_ERROR, RequestWrapper.class.getName(), "Failed to read InputStream from request", ex);
            throw ex;
        }
        //Store request body content in 'body' variable
        body = outputStream.toString(Charset.defaultCharset());
    }

    @Override
    public String getParameter(final String name) {
        if (body.contains(name) && super.getParameter(name) == null) {
            final String[] split = body.split("&");
            return Arrays.stream(split).filter(s -> s.contains(name)).findFirst().get().replace(name + '=', "");
        } else {
            return super.getParameter(name);
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                // Nothing to override
            }

            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

}
