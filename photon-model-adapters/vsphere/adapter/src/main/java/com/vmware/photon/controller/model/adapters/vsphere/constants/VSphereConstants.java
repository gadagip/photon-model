/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere.constants;

/**
 * Constants to be used across vSphere adapter
 */
public class VSphereConstants {

    // snapshot related constants
    public static final String VSPHERE_SNAPSHOT_NAME = "name";
    public static final String VSPHERE_SNAPSHOT_DESCRIPTION = "description";
    public static final String VSPHERE_SNAPSHOT_MEMORY = "snapshotMemory";
    public static final String VSPHERE_SNAPSHOT_REQUEST_TYPE = "requestType";

    public static enum VSphereResourceType {
        vsphere_vm("vsphere_vm");

        private final String value;

        private VSphereResourceType(String value) {
            this.value = value;
        }

        public String toString() {
            return this.value;
        }
    }

}
