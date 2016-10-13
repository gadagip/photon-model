/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.tasks;

public enum TaskOption {
    /**
     * Update local document state; leave the remote state unmodified
     */
    DOCUMENT_CHANGES_ONLY,

    /**
     * Task will do only validation of the payload. No state will be created or modified.
     */
    VALIDATE_ONLY,

    /**
     * Option indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    IS_MOCK,

    /**
     * Option indicating to preserve missing resource during enumeration.
     */
    PRESERVE_MISSING_RESOUCES,

    /**
     * Delete self on completion
     */
    SELF_DELETE_ON_COMPLETION
}
