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

package com.vmware.photon.controller.model.adapters.azure;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.UriUtils;

/**
 * URI definitions for Azure adapter.
 */
public class AzureUriPaths {

    public static final String PROVISIONING_AZURE = UriPaths.PROVISIONING
            + "/azure";
    public static final String AZURE_INSTANCE_ADAPTER = AdapterTypePath.INSTANCE_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_SUBNET_ADAPTER = AdapterTypePath.SUBNET_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_DISK_ADAPTER = AdapterTypePath.DISK_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_DISK_DAY2_ADAPTER = ResourceOperationSpecService
            .buildDefaultAdapterLink(
                    EndpointType.azure.name(),
                    ResourceOperationSpecService.ResourceType.COMPUTE, "disk-day2-adapter");
    public static final String AZURE_DAY2_POWER_ADAPTER = ResourceOperationSpecService
            .buildDefaultAdapterLink(
                    EndpointType.azure.name(),
                    ResourceOperationSpecService.ResourceType.COMPUTE, "d2opower");
    public static final String AZURE_COST_STATS_ADAPTER = AdapterTypePath.COST_STATS_ADAPTER
            .adapterLink(EndpointType.azure_ea.name());
    public static final String AZURE_STATS_ADAPTER = AdapterTypePath.STATS_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_COMPUTE_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-stats-gatherer";
    public static final String AZURE_COMPUTE_HOST_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-host-stats-gatherer";
    public static final String AZURE_COMPUTE_HOST_STORAGE_STATS_GATHERER = PROVISIONING_AZURE
            + "/compute-host-storage-stats-gatherer";
    public static final String AZURE_ENUMERATION_ADAPTER = AdapterTypePath.ENUMERATION_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_IMAGE_ENUMERATION_ADAPTER = AdapterTypePath.IMAGE_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.azure.name());
    public static final String AZURE_COMPUTE_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/compute-enumeration-adapter";
    public static final String AZURE_STORAGE_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/storage-enumeration-adapter";
    public static final String AZURE_NETWORK_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/network-enumeration-adapter";
    public static final String AZURE_DISK_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/disk-enumeration-adapter";
    public static final String AZURE_RESOURCE_GROUP_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/resource-group-enumeration-adapter";
    public static final String AZURE_FIREWALL_ENUMERATION_ADAPTER = PROVISIONING_AZURE
            + "/firewall-enumeration-adapter";
    public static final String AZURE_SUBSCRIPTIONS_ENUMERATOR = PROVISIONING_AZURE
            + "/subscriptions-enumerator";
    public static final String AZURE_SUBSCRIPTION_ENDPOINT_CREATOR = PROVISIONING_AZURE
            + "/subscription-endpoint-creator";
    public static final String AZURE_SUBSCRIPTION_ENDPOINTS_ENUMERATOR = PROVISIONING_AZURE
            + "/subscription-endpoints-enumerator";
    public static final String AZURE_REGION_ENUMERATION_ADAPTER_SERVICE = AdapterTypePath.REGION_ENUMERATION_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String AZURE_ENDPOINT_CONFIG_ADAPTER = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String AZURE_EA_ENDPOINT_CONFIG_ADAPTER = AdapterTypePath.ENDPOINT_CONFIG_ADAPTER
            .adapterLink(EndpointType.azure_ea.name());

    public static final String AZURE_SECURITY_GROUP_ADAPTER = AdapterTypePath.SECURITY_GROUP_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String AZURE_LOAD_BALANCER_ADAPTER = AdapterTypePath.LOAD_BALANCER_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String AZURE_POWER_ADAPTER = AdapterTypePath.POWER_ADAPTER
            .adapterLink(EndpointType.azure.name());

    public static final String ADAPTER_AZURE = UriUtils.buildUriPath(
            UriPaths.ADAPTER, EndpointType.azure.name());

    public static final String AZURE_INSTANCE_TYPE_ADAPTER = UriUtils.buildUriPath(ADAPTER_AZURE,
            "instance-type-adapter");
}
