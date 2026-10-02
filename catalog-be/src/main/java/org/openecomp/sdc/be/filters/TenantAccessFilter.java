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
package org.openecomp.sdc.be.filters;

import com.google.gson.Gson;
import javax.annotation.Priority;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.openecomp.sdc.be.components.impl.ComponentTenantValidator;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.impl.ComponentsUtils;
import org.openecomp.sdc.be.impl.WebAppContextWrapper;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.openecomp.sdc.exception.ResponseFormat;
import org.springframework.web.context.WebApplicationContext;

/**
 * Rejects requests whose path references a component (by unique id, UUID, name and version, or distribution id) that belongs
 * to a tenant the caller is not a member of.
 */
@Priority(12)
public class TenantAccessFilter implements ContainerRequestFilter {

    private static final Logger log = Logger.getLogger(TenantAccessFilter.class);
    private final Gson gson = new Gson();
    @Context
    private HttpServletRequest sr;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!ComponentTenantValidator.isMultitenancyEnabled()) {
            return;
        }
        WebApplicationContext webApplicationContext = getWebApplicationContext();
        ComponentTenantValidator validator = webApplicationContext.getBean(ComponentTenantValidator.class);
        if (!validator.isPathAccessAllowed(ComponentTenantValidator.getCallerTenants(sr), requestContext.getUriInfo().getPathParameters())) {
            log.debug("Unauthorized tenant for {}", requestContext.getUriInfo().getPath());
            ResponseFormat responseFormat = webApplicationContext.getBean(ComponentsUtils.class).getResponseFormat(ActionStatus.RESTRICTED_OPERATION);
            requestContext.abortWith(Response.status(responseFormat.getStatus()).entity(gson.toJson(responseFormat.getRequestError())).build());
        }
    }

    private WebApplicationContext getWebApplicationContext() {
        ServletContext context = sr.getServletContext();
        WebAppContextWrapper webAppContextWrapper = (WebAppContextWrapper) context.getAttribute(Constants.WEB_APPLICATION_CONTEXT_WRAPPER_ATTR);
        return webAppContextWrapper.getWebAppContext(context);
    }
}
