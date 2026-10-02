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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.openecomp.sdc.common.util.Multitenancy;
import org.openecomp.sdc.versioning.ItemManager;
import org.openecomp.sdc.versioning.types.Item;

class TenantAuthorizationFilterTest {

    private static final String ITEM_ID = "item-1";

    private ItemManager itemManager;
    private Multitenancy multitenancy;
    private TenantAuthorizationFilter filter;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        itemManager = mock(ItemManager.class);
        multitenancy = spy(new Multitenancy());
        doReturn(true).when(multitenancy).multiTenancyCheck();
        filter = new TenantAuthorizationFilter(multitenancy, () -> itemManager);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void parsesItemIdFromItemScopedRoutes() {
        assertEquals(List.of("vsp1"), parse("/v1.0/vendor-software-products/vsp1"));
        assertEquals(List.of("vsp1"), parse("/v1.0/vendor-software-products/vsp1/versions/v1/components/c1/processes/p1/data"));
        assertEquals(List.of("vsp1"), parse("/v1.0/vendor-software-products/packages/vsp1"));
        assertEquals(List.of("vlm1"), parse("/v1.0/vendor-license-models/vlm1/versions/v1"));
        assertEquals(List.of("i1"), parse("/v1.0/items/i1/versions/v1/activity-logs"));
        assertEquals(List.of("i1"), parse("/v1.0/items/i1;jsessionid=x/actions"));
    }

    @Test
    void decodesItemIdLikeJaxRs() {
        assertEquals(List.of("i1"), parse("/v1.0/items/%69%31"));
        assertEquals(List.of("a+b"), parse("/v1.0/items/a+b"));
    }

    @Test
    void parsesVspIdOfExternalTestingExecution() {
        HttpServletRequest request = request("/v1.0/externaltesting/executions");
        when(request.getQueryString()).thenReturn("vspId=vsp1&vspVersionId=v1&vspId=vsp%32");
        assertEquals(Optional.of(List.of("vsp1", "vsp2")), TenantAuthorizationFilter.parseItemIds(request));
    }

    @Test
    void ignoresCollectionRoutes() {
        assertTrue(parse("/v1.0/vendor-software-products").isEmpty());
        assertTrue(parse("/v1.0/vendor-software-products/packages").isEmpty());
        assertTrue(parse("/v1.0/vendor-software-products/validation-vsp").isEmpty());
        assertTrue(parse("/v1.0/items").isEmpty());
        assertTrue(parse("/v1.0/healthcheck").isEmpty());
        assertTrue(parse("/v1.0/externaltesting/executions").isEmpty());
    }

    @Test
    void rejectsMalformedEncoding() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/items/%zz", "tenantA");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain, itemManager);
    }

    @Test
    void rejectsUnknownItem() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/items/" + ITEM_ID, "tenantA");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsExternalTestingExecutionOfOtherTenantVsp() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/externaltesting/executions", "tenantA");
        when(request.getQueryString()).thenReturn("vspId=" + ITEM_ID);
        when(itemManager.get(ITEM_ID)).thenReturn(item("tenantB"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain);
    }

    @Test
    void allowsCollectionRoutes() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/vendor-software-products", "tenantA");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(itemManager);
    }

    @Test
    void allowsExactTenantMatch() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/vendor-software-products/" + ITEM_ID + "/versions/v1", "tenantA");
        when(itemManager.get(ITEM_ID)).thenReturn(item("tenantA"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void rejectsOtherTenant() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/vendor-software-products/" + ITEM_ID + "/versions/v1", "tenantA");
        when(itemManager.get(ITEM_ID)).thenReturn(item("tenantB"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsTenantThatOnlyContainsRole() throws Exception {
        HttpServletRequest request = authenticatedRequest("/v1.0/items/" + ITEM_ID, "tenant");
        when(itemManager.get(ITEM_ID)).thenReturn(item("tenantB"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsRequestWithoutKeycloakPrincipal() throws Exception {
        HttpServletRequest request = request("/v1.0/vendor-license-models/" + ITEM_ID);
        when(itemManager.get(ITEM_ID)).thenReturn(item("tenantA"));

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized Tenant");
        verifyNoInteractions(chain);
    }

    @Test
    void skipsCheckWhenMultitenancyDisabled() throws Exception {
        doReturn(false).when(multitenancy).multiTenancyCheck();
        HttpServletRequest request = request("/v1.0/items/" + ITEM_ID);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(itemManager);
    }

    private static List<String> parse(String path) {
        return TenantAuthorizationFilter.parseItemIds(request(path)).orElseThrow();
    }

    private static HttpServletRequest request(String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("/onboarding-api");
        when(request.getRequestURI()).thenReturn("/onboarding-api" + path);
        return request;
    }

    private static HttpServletRequest authenticatedRequest(String pathInfo, String role) {
        HttpServletRequest request = request(pathInfo);
        AccessToken token = new AccessToken();
        token.setRealmAccess(new AccessToken.Access().addRole(role));
        KeycloakSecurityContext securityContext = mock(KeycloakSecurityContext.class);
        when(securityContext.getToken()).thenReturn(token);
        when(request.getUserPrincipal()).thenReturn(new KeycloakPrincipal<>("user", securityContext));
        return request;
    }

    private static Item item(String tenant) {
        Item item = new Item();
        item.setId(ITEM_ID);
        item.setTenant(tenant);
        return item;
    }
}
