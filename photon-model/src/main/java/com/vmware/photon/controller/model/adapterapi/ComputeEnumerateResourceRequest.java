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

package com.vmware.photon.controller.model.adapterapi;

import java.net.URI;

/**
 * Request to enumerate instantiated resources. The {@code resourceReference} value is the URI to
 * the parent compute host.
 */
public class ComputeEnumerateResourceRequest extends ResourceRequest {

    /**
     * Uri reference of the resource pool.
     */
    public String resourcePoolLink;

    /**
     * Enumeration Action Start, stop, refresh.
     */
    public EnumerationAction enumerationAction;

    /**
     * Reference to the management endpoint of the compute provider.
     */
    public URI adapterManagementReference;

    /**
     * If set to true, the adapter must not delete the missing resources, but set their
     * {@link com.vmware.photon.controller.model.resources.ComputeService.ComputeState#lifecycleState}
     * field to
     * {@link com.vmware.photon.controller.model.resources.ComputeService.LifecycleState#RETIRED}
     */
    public boolean preserveMissing;

    /**
     * Link reference to the cloud account endpoint of this host
     */
    public String endpointLink;

    /**
     * Time in micros at which deleted resources should be expired.
     */
    public long deletedResourceExpirationMicros;
    /**
     * Return a key to uniquely identify enumeration for endpoint and resource pool instance.
     */
    public String getEnumKey() {
        return "endpoint:[" + this.endpointLink + "],pool:[" + this.resourcePoolLink + "]";
    }
}
