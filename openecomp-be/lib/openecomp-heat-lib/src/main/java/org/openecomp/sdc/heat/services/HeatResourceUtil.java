/*
 * Copyright © 2016-2018 European Support Limited
 *
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
 */
package org.openecomp.sdc.heat.services;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openecomp.sdc.heat.datatypes.model.HeatResourcesTypes;

/**
 * Extracts the network role from port resource ids of the forms
 * {@code <prefix>_<role>_<portType>[_<n>]*}, {@code <prefix>_int_<role>_<portType>[_<n>]*} and
 * {@code <prefix>_subint_<role>_<portType>[_<n>]*}, where every character is a word character ({@code [A-Za-z0-9_]}).
 * When several splits are possible, the longest prefix and then the longest role are chosen. Parsing is linear in the
 * length of the resource id.
 */
public class HeatResourceUtil {

    private static final char UNDERSCORE = '_';
    private static final String EXTERNAL_ROLE_MARKER = "_";
    private static final String INTERNAL_ROLE_MARKER = "_int_";
    private static final String SUB_INTERFACE_ROLE_MARKER = "_subint_";

    public static Optional<String> evaluateNetworkRoleFromResourceId(String resourceId, String resourceType) {
        Optional<PortType> portType = getPortType(resourceType);
        if (portType.isPresent()) {
            String portNetworkRole = getNetworkRole(resourceId, EXTERNAL_ROLE_MARKER, portType.get());
            String portIntNetworkRole = getNetworkRole(resourceId, INTERNAL_ROLE_MARKER, portType.get());
            return Optional.ofNullable(portNetworkRole != null ? portNetworkRole : portIntNetworkRole);
        }
        return Optional.empty();
    }

    private static Optional<PortType> getPortType(String resourceType) {
        if (HeatResourcesTypes.CONTRAIL_V2_VIRTUAL_MACHINE_INTERFACE_RESOURCE_TYPE.getHeatResource().equals(resourceType)) {
            return Optional.of(PortType.VMI);
        } else if (HeatResourcesTypes.NEUTRON_PORT_RESOURCE_TYPE.getHeatResource().equals(resourceType)) {
            return Optional.of(PortType.PORT);
        }
        return Optional.empty();
    }

    /**
     * Extract network role from sub interface id optional.
     *
     * @param resourceId   the resource id
     * @param resourceType the resource type
     * @return the optional
     */
    public static Optional<String> extractNetworkRoleFromSubInterfaceId(String resourceId, String resourceType) {
        Optional<PortType> portType = getPortType(resourceType);
        if (portType.isPresent()) {
            return Optional.ofNullable(getNetworkRole(resourceId, SUB_INTERFACE_ROLE_MARKER, portType.get()));
        }
        return Optional.empty();
    }

    private static String getNetworkRole(String resourceId, String roleMarker, PortType portType) {
        if (!isWordString(resourceId)) {
            return null;
        }
        String portTypeSuffix = UNDERSCORE + portType.getPortTypeName();
        int roleEnd = findLastPortTypeSuffix(resourceId, portTypeSuffix);
        if (roleEnd < 0) {
            return null;
        }
        int markerStart = resourceId.lastIndexOf(roleMarker, roleEnd - roleMarker.length() - 1);
        if (markerStart < 1) {
            return null;
        }
        String networkRole = resourceId.substring(markerStart + roleMarker.length(), roleEnd);
        //Assuming network role will not contain ONLY digits
        return isDigits(networkRole, 0, networkRole.length()) ? null : networkRole;
    }

    /**
     * Returns the last index at which {@code portTypeSuffix} occurs and is followed only by zero or more
     * {@code _<digits>} groups, or -1 if there is none.
     */
    private static int findLastPortTypeSuffix(String resourceId, String portTypeSuffix) {
        int length = resourceId.length();
        boolean[] isNumericGroupsTail = new boolean[length + 1];
        isNumericGroupsTail[length] = true;
        int segmentEnd = length;
        for (int i = length - 1; i >= 0; i--) {
            if (resourceId.charAt(i) == UNDERSCORE) {
                isNumericGroupsTail[i] = isNumericGroupsTail[segmentEnd] && segmentEnd - i > 1 && isDigits(resourceId, i + 1, segmentEnd);
                segmentEnd = i;
            }
        }
        for (int i = length - portTypeSuffix.length(); i >= 0; i--) {
            if (isNumericGroupsTail[i + portTypeSuffix.length()] && resourceId.startsWith(portTypeSuffix, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWordString(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c == UNDERSCORE || isAsciiDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigits(String value, int from, int to) {
        if (from >= to) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (!isAsciiDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    @AllArgsConstructor
    @Getter
    private enum PortType {
        PORT("port"), VMI("vmi");
        private String portTypeName;
    }
}
