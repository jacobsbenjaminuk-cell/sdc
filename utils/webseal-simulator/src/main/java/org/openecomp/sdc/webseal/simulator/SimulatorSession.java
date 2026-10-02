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

package org.openecomp.sdc.webseal.simulator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.openecomp.sdc.webseal.simulator.conf.Conf;

/**
 * The only source of the caller's identity. The user id lives in a server-side session that is created after a successful password check, so
 * client-supplied {@code USER_ID} headers, cookies or parameters cannot choose who the simulator forwards requests as.
 */
final class SimulatorSession {

    static final String USER_ID_ATTRIBUTE = SimulatorSession.class.getName() + ".userId";

    private SimulatorSession() {
    }

    static void login(final HttpServletRequest request, final User user) {
        final HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true).setAttribute(USER_ID_ATTRIBUTE, user.getUserId());
    }

    static User getUser(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        final Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
        if (!(userId instanceof String)) {
            return null;
        }
        return Conf.getInstance().getUsers().get(userId);
    }
}
