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
package org.openecomp.sdc.vendorsoftwareproduct.errors;

import org.openecomp.sdc.common.errors.BaseErrorBuilder;
import org.openecomp.sdc.common.errors.ErrorCategory;

public class UploadTooLargeErrorBuilder extends BaseErrorBuilder {

    private static final String UPLOAD_TOO_LARGE_MSG = "Uploaded file exceeds the maximum allowed size of %d bytes";

    public UploadTooLargeErrorBuilder(final long maxSize) {
        getErrorCodeBuilder().withId(VendorSoftwareProductErrorCodes.UPLOAD_TOO_LARGE);
        getErrorCodeBuilder().withCategory(ErrorCategory.APPLICATION);
        getErrorCodeBuilder().withMessage(String.format(UPLOAD_TOO_LARGE_MSG, maxSize));
    }
}
