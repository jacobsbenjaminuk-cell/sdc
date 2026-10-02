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
package org.openecomp.sdc.be.filters;

import fj.data.Either;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.openecomp.sdc.be.components.impl.exceptions.ByActionStatusComponentException;
import org.openecomp.sdc.be.components.impl.exceptions.ComponentException;
import org.openecomp.sdc.be.config.Configuration.ExternalApiAuthConfig;
import org.openecomp.sdc.be.config.ConfigurationManager;
import org.openecomp.sdc.be.dao.api.ActionStatus;
import org.openecomp.sdc.be.dao.janusgraph.JanusGraphGenericDao;
import org.openecomp.sdc.be.datatypes.elements.ConsumerDataDefinition;
import org.openecomp.sdc.be.model.operations.api.StorageOperationStatus;
import org.openecomp.sdc.be.model.operations.impl.ConsumerOperation;
import org.openecomp.sdc.be.resources.data.ConsumerData;
import org.openecomp.sdc.be.servlets.exception.ComponentExceptionMapper;
import org.openecomp.sdc.common.api.Constants;
import org.openecomp.sdc.common.log.wrappers.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Authenticates every external API (/sdc/*) request against the stored consumer credentials and restricts the USER_ID header to the user IDs
 * that the authenticated consumer is allowed to act for.
 */
@Component("externalApiAuthenticationFilter")
public class ExternalApiAuthenticationFilter implements Filter {

    private static final Logger log = Logger.getLogger(ExternalApiAuthenticationFilter.class);
    private static final String BASIC_SCHEME = "Basic ";
    private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";
    private static final String WWW_AUTHENTICATE_VALUE = "Basic realm=\"ASDC\"";

    private final ConsumerOperation consumerOperation;
    private final JanusGraphGenericDao janusGraphGenericDao;
    private final ComponentExceptionMapper componentExceptionMapper;

    @Autowired
    public ExternalApiAuthenticationFilter(final ConsumerOperation consumerOperation,
                                           @Qualifier("janusgraph-generic-dao") final JanusGraphGenericDao janusGraphGenericDao,
                                           final ComponentExceptionMapper componentExceptionMapper) {
        this.consumerOperation = consumerOperation;
        this.janusGraphGenericDao = janusGraphGenericDao;
        this.componentExceptionMapper = componentExceptionMapper;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain)
        throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        try {
            final String consumerName = authenticate(httpRequest.getHeader(Constants.AUTHORIZATION_HEADER));
            authorizeUserId(consumerName, httpRequest.getHeader(Constants.USER_ID_HEADER));
        } catch (final ComponentException e) {
            if (e.getActionStatus() == ActionStatus.AUTH_REQUIRED) {
                httpResponse.setHeader(WWW_AUTHENTICATE_HEADER, WWW_AUTHENTICATE_VALUE);
            }
            componentExceptionMapper.writeToResponse(e, httpResponse);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String authenticate(final String authorizationHeader) {
        if (StringUtils.isBlank(authorizationHeader)) {
            log.error("External API request rejected: no authorization header");
            throw new ByActionStatusComponentException(ActionStatus.AUTH_REQUIRED);
        }
        if (!authorizationHeader.regionMatches(true, 0, BASIC_SCHEME, 0, BASIC_SCHEME.length())) {
            log.error("External API request rejected: authorization header is not basic authentication");
            throw new ByActionStatusComponentException(ActionStatus.AUTH_FAILED_INVALIDE_HEADER);
        }
        final String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(authorizationHeader.substring(BASIC_SCHEME.length()).trim()),
                StandardCharsets.UTF_8);
        } catch (final IllegalArgumentException e) {
            log.error("External API request rejected: authorization header is not valid base64");
            throw new ByActionStatusComponentException(ActionStatus.AUTH_FAILED_INVALIDE_HEADER);
        }
        final int separator = credentials.indexOf(':');
        if (separator <= 0) {
            log.error("External API request rejected: authorization header has no consumer name");
            throw new ByActionStatusComponentException(ActionStatus.AUTH_FAILED_INVALIDE_HEADER);
        }
        final String consumerName = credentials.substring(0, separator);
        final String password = credentials.substring(separator + 1);
        if (!isValidConsumerPassword(consumerName, password)) {
            log.error("External API request rejected: invalid credentials for consumer {}", consumerName);
            throw new ByActionStatusComponentException(ActionStatus.AUTH_FAILED);
        }
        return consumerName;
    }

    private boolean isValidConsumerPassword(final String consumerName, final String password) {
        final Either<ConsumerData, StorageOperationStatus> consumer;
        try {
            consumer = consumerOperation.getCredentials(consumerName);
        } finally {
            janusGraphGenericDao.commit();
        }
        if (consumer.isRight()) {
            return false;
        }
        final ConsumerDataDefinition consumerDefinition = consumer.left().value().getConsumerDataDefinition();
        if (consumerDefinition == null || StringUtils.isAnyBlank(consumerDefinition.getConsumerSalt(), consumerDefinition.getConsumerPassword())) {
            return false;
        }
        try {
            final MessageDigest digest = DigestUtils.getSha256Digest();
            digest.update(Hex.decodeHex(consumerDefinition.getConsumerSalt()));
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(digest.digest(), Hex.decodeHex(consumerDefinition.getConsumerPassword()));
        } catch (final DecoderException e) {
            log.error("Stored credentials of consumer {} are not valid hex", consumerName);
            return false;
        }
    }

    private void authorizeUserId(final String consumerName, final String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        final List<String> allowedUserIds = Optional.ofNullable(ConfigurationManager.getConfigurationManager().getConfiguration().getExternalApiAuth())
            .map(ExternalApiAuthConfig::getConsumerAllowedUserIds)
            .map((Map<String, List<String>> allowList) -> allowList.get(consumerName))
            .orElse(Collections.emptyList());
        if (!allowedUserIds.contains(userId)) {
            log.error("External API request rejected: consumer {} is not allowed to act as user {}", consumerName, userId);
            throw new ByActionStatusComponentException(ActionStatus.RESTRICTED_OPERATION);
        }
    }

    @Override
    public void destroy() {
    }
}
