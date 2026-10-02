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

package org.openecomp.sdc.webseal.simulator;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.openecomp.sdc.webseal.simulator.conf.Conf;

/**
 * Creates the users defined in the simulator configuration in SDC. Only accepts POST requests carrying the CSRF token issued by the
 * login page, and only for users present in {@link Conf#getUsers()}.
 */
public class RequestsClient extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final String ADMIN_ID = "jh0003";

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        if (!CsrfToken.isValid(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token");
            return;
        }
        final Map<String, User> users = Conf.getInstance().getUsers();
        final String url = Conf.getInstance().getFeHost() + "/sdc1/feProxy/rest/v1/user";

        if ("true".equals(request.getParameter("all"))) {
            final PrintWriter writer = getHtmlWriter(response);
            for (final User user : users.values()) {
                writer.println(createUser(user, url) + "<br>");
            }
            return;
        }

        final User user = users.get(request.getParameter("userId"));
        if (user == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown user");
            return;
        }
        getHtmlWriter(response).println(createUser(user, url));
    }

    private PrintWriter getHtmlWriter(final HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return response.getWriter();
    }

    private String createUser(final User user, final String url) throws IOException {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("USER_ID", ADMIN_ID);
        final int resultCode = sendHttpPost(url, toJson(user), headers);
        return StringEscapeUtils.escapeHtml4("User " + user.getFirstName() + " " + user.getLastName() + getResultMessage(resultCode));
    }

    static String toJson(final User user) {
        final JsonObject json = new JsonObject();
        json.addProperty("firstName", user.getFirstName());
        json.addProperty("lastName", user.getLastName());
        json.addProperty("userId", user.getUserId());
        json.addProperty("email", user.getEmail());
        json.addProperty("role", user.getRole() == null ? null : user.getRole().toUpperCase());
        return json.toString();
    }

    private String getResultMessage(int resultCode) {
        return 201 == resultCode ? " created successfuly" : " not created (" + resultCode + ")";
    }

    private int sendHttpPost(String url, String body, Map<String, String> headers) throws IOException {

        String responseString = "";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // add request method
        con.setRequestMethod("POST");

        // add request headers
        if (headers != null) {
            for (Entry<String, String> header : headers.entrySet()) {
                String key = header.getKey();
                String value = header.getValue();
                con.setRequestProperty(key, value);
            }
        }

        // Send post request
        if (body != null) {
            con.setDoOutput(true);
            try (OutputStream wr = con.getOutputStream()) {
                wr.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int responseCode = con.getResponseCode();
        // logger.debug("Send POST http request, url: {}", url);
        // logger.debug("Response Code: {}", responseCode);

        StringBuilder response = new StringBuilder();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
        } catch (Exception e) {
            // logger.debug("response body is null");
        }

        String result;

        try {
            result = IOUtils.toString(con.getErrorStream());
            response.append(result);
        } catch (Exception e2) {
        }

        con.disconnect();
        return responseCode;

    }

}
