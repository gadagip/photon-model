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

package com.vmware.photon.controller.model.adapters.azure.instance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME;
import static com.vmware.photon.controller.model.ComputeProperties.RESOURCE_GROUP_NAME;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_OSDISK_CACHING;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY1;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNT_KEY2;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_BLOBS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINERS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_CONTAINER_LEASE_STATUS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_CAPACITY;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_SERVICE_REFERENCE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_DISK_TYPE;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DEFAULT_INSTANCE_ADAPTER_REFERENCE;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CPU_UTILIZATION_PERCENT;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.CUSTOM_PROP_ENDPOINT_LINK;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.MEMORY_USED_PERCENT;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.microsoft.azure.CloudException;
import com.microsoft.azure.SubResource;
import com.microsoft.azure.management.compute.CachingTypes;
import com.microsoft.azure.management.compute.InstanceViewTypes;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.VirtualMachineInner;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.IPAllocationMethod;
import com.microsoft.azure.management.network.SecurityRuleAccess;
import com.microsoft.azure.management.network.SecurityRuleDirection;
import com.microsoft.azure.management.network.SecurityRuleProtocol;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySku;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySkuName;
import com.microsoft.azure.management.network.VirtualNetworkGatewaySkuTier;
import com.microsoft.azure.management.network.VirtualNetworkGatewayType;
import com.microsoft.azure.management.network.VpnType;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.SecurityRuleInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkGatewayIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkGatewayInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureComputeEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.GatewaySpec;
import com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.AzureNicSpecs.NetSpec;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.IpAssignment;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService;
import com.vmware.photon.controller.model.tasks.ResourceRemovalTaskService.ResourceRemovalTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsAggregationTaskService.StatsAggregationTaskState;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class AzureTestUtil {
    public static final String AZURE_ADMIN_USERNAME = "azureuser";
    public static final String AZURE_ADMIN_PASSWORD = "Pa$$word1";

    // This instance size DOES support 2 NICs! If you change it please correct NUMBER_OF_NICS.
    public static final String AZURE_VM_SIZE = "STANDARD_A3";

    public static final String IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";

    public static final String AZURE_RESOURCE_GROUP_LOCATION = "westus";

    public static final String AZURE_STORAGE_ACCOUNT_TYPE = "Standard_RAGRS";

    /*
     * VERY IMPORTANT: Do NOT change the vNet-subnet name to something random/unique per test run.
     * There's no need since for every VM deployment we are creating new Resource Group and the
     * names of the entities within (vNets inclusive) need to be unique just in the scope of the RG,
     * and not across the subscription.
     *
     * Otherwise this will break TestAzureProvisionTask.testProvisionVMUsingSharedNetwork and will
     * result in orphan vNets under 'test-sharedNetworkRG' RG.
     */

    public static final String AZURE_SECURITY_GROUP_NAME = "test-NSG";

    public static final String AZURE_SHARED_NETWORK_RESOURCE_GROUP_NAME = "test-sharedNetworkRG";

    public static final AzureNicSpecs DEFAULT_NIC_SPEC;
    public static final AzureNicSpecs SHARED_NETWORK_NIC_SPEC;
    public static final AzureNicSpecs NO_PUBLIC_IP_NIC_SPEC;

    public static final long AZURE_CUSTOM_OSDISK_SIZE = 1024 * 32; // 32 GBs. We can only increase
    // size of OS Disk. The Image used already has 30 GB size.

    private static final String AZURE_NETWORK_NAME = "test-vNet";
    private static final String AZURE_NETWORK_CIDR = "172.16.0.0/16";
    private static final String AZURE_SUBNET_NAME = "test-subnet";

    // The number of subnet CIDRs drives the number of nics created.
    private static final String[] AZURE_SUBNET_CIDR = { "172.16.0.0/18", "172.16.64.0/18" };

    private static final String AZURE_GATEWAY_NAME = "gateway";
    private static final String AZURE_GATEWAY_CIDR = "172.16.128.0/18";
    private static final String AZURE_GATEWAY_IP_CONFIGURATION_NAME = "gateway-ipconfig";
    private static final String AZURE_GATEWAY_PUBLIC_IP_NAME = "gateway-pip";

    static {
        DEFAULT_NIC_SPEC = initializeNicSpecs(null /* prefix */ , false /* assignGateway */,
                true /* assignPublicIpAddress */);

        NO_PUBLIC_IP_NIC_SPEC = initializeNicSpecs(null /* prefix */ , false /* assignGateway */,
                false /* assignPublicIpAddress */);

        SHARED_NETWORK_NIC_SPEC = initializeNicSpecs(null /* prefix */ , true /* assignGateway */,
                true /* assignPublicIpAddress */);
    }

    public static final CachingTypes DEFAULT_OS_DISK_CACHING = CachingTypes.NONE;

    public static class AzureNicSpecs {

        public static class NetSpec {

            public final String name;
            public final String cidr;
            public final String zoneId;

            public NetSpec(String name, String cidr, String zoneId) {
                this.name = name;
                this.cidr = cidr;
                this.zoneId = zoneId;
            }
        }

        public static class GatewaySpec {
            public final String name;
            public final String cidr;
            public final String zoneId;
            public final String ipConfigurationName;
            public final String publicIpName;
            public final IPAllocationMethod ipAllocationMethod;
            public final VirtualNetworkGatewaySkuName skuName;
            public final VirtualNetworkGatewaySkuTier skuTier;
            public final VirtualNetworkGatewayType type;
            public final VpnType vpnType;

            public GatewaySpec(String name, String cidr, String zoneId, String ipConfigurationName,
                    String publicIpName, IPAllocationMethod ipAllocationMethod, VirtualNetworkGatewaySkuName skuName,
                               VirtualNetworkGatewaySkuTier skuTier, VirtualNetworkGatewayType type, VpnType vpnType) {
                this.name = name;
                this.cidr = cidr;
                this.zoneId = zoneId;
                this.ipConfigurationName = ipConfigurationName;
                this.publicIpName = publicIpName;
                this.ipAllocationMethod = ipAllocationMethod;
                this.skuName = skuName;
                this.skuTier = skuTier;
                this.type = type;
                this.vpnType = vpnType;
            }
        }

        public final NetSpec network;
        public final List<NetSpec> subnets;
        public final GatewaySpec gateway;
        public final boolean assignPublicIpAddress;

        public AzureNicSpecs(NetSpec network, List<NetSpec> subnets, GatewaySpec gateway,
                boolean assignPublicIpAddress) {
            this.network = network;
            this.subnets = subnets;
            this.gateway = gateway;
            this.assignPublicIpAddress = assignPublicIpAddress;
        }
    }

    public static AzureNicSpecs initializeNicSpecs(String prefix, boolean assignGateway,
            boolean assignPublicIpAddress) {
        String networkName = (prefix != null ? prefix + "-" : "") + AZURE_NETWORK_NAME;
        NetSpec network = new NetSpec(
                networkName,
                AZURE_NETWORK_CIDR,
                AZURE_RESOURCE_GROUP_LOCATION);

        List<NetSpec> subnets = new ArrayList<>();
        for (int i = 0; i < AZURE_SUBNET_CIDR.length; i++) {
            String subnetName = (prefix != null ? prefix + "-" : "") + AZURE_SUBNET_NAME + i;

            subnets.add(new NetSpec(subnetName,
                    AZURE_SUBNET_CIDR[i],
                    AZURE_RESOURCE_GROUP_LOCATION));
        }
        GatewaySpec gateway = assignGateway ? new GatewaySpec(AZURE_GATEWAY_NAME,
                AZURE_GATEWAY_CIDR,
                AZURE_RESOURCE_GROUP_LOCATION,
                AZURE_GATEWAY_IP_CONFIGURATION_NAME,
                AZURE_GATEWAY_PUBLIC_IP_NAME,
                IPAllocationMethod.DYNAMIC,
                VirtualNetworkGatewaySkuName.STANDARD,
                VirtualNetworkGatewaySkuTier.STANDARD,
                VirtualNetworkGatewayType.VPN,
                VpnType.ROUTE_BASED) : null;

        return new AzureNicSpecs(network, subnets, gateway, assignPublicIpAddress);
    }

    public static ResourcePoolState createDefaultResourcePool(
            VerificationHost host)
            throws Throwable {
        ResourcePoolState inPool = new ResourcePoolState();
        inPool.name = UUID.randomUUID().toString();
        inPool.id = inPool.name;

        inPool.minCpuCount = 1L;
        inPool.minMemoryBytes = 1024L;

        ResourcePoolState returnPool = TestUtils.doPost(host, inPool, ResourcePoolState.class,
                UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK));

        return returnPool;
    }

    public static VirtualNetworkInner getAzureVirtualNetwork(
            NetworkManagementClientImpl networkManagementClient, String resourceGroupName,
            String virtualNetworkName) throws Exception {
        VirtualNetworkInner response = networkManagementClient
                .virtualNetworks().getByResourceGroup(resourceGroupName, virtualNetworkName, null);

        return response;
    }

    public static VirtualMachineInner getAzureVirtualMachine(
            ComputeManagementClientImpl computeManagementClient,
            String resourceGroupName, String vmName) throws Exception {
        VirtualMachineInner response = computeManagementClient
                .virtualMachines().getByResourceGroup(resourceGroupName, vmName, null);

        return response;
    }

    public static VirtualMachineInner getAzureVirtualMachineWithExtension(
            ComputeManagementClientImpl computeManagementClient,
            String resourceGroupName, String vmName, InstanceViewTypes expand) throws Exception {
        VirtualMachineInner response = computeManagementClient
                .virtualMachines().getByResourceGroup(resourceGroupName, vmName, expand);

        return response;
    }


    public static NetworkSecurityGroupInner getAzureSecurityGroup(
            NetworkManagementClientImpl networkManagementClient, String resourceGroupName,
            String securityGroupName) throws Exception {
        NetworkSecurityGroupInner response = networkManagementClient
                .networkSecurityGroups()
                .getByResourceGroup(resourceGroupName, securityGroupName, null);

        return response;
    }

    public static VirtualNetworkInner updateAzureVirtualNetwork(
            NetworkManagementClientImpl networkManagementClient, String resourceGroupName,
            String virtualNetworkName, VirtualNetworkInner parameters) throws Exception {
        VirtualNetworkInner response = networkManagementClient
                .virtualNetworks()
                .createOrUpdate(resourceGroupName, virtualNetworkName, parameters);

        return response;
    }

    public static NetworkSecurityGroupInner updateAzureSecurityGroup(
            NetworkManagementClientImpl networkManagementClient, String resourceGroupName,
            String networkSecurityGroupName, NetworkSecurityGroupInner parameters) throws Exception {
        NetworkSecurityGroupInner response = networkManagementClient
                .networkSecurityGroups()
                .createOrUpdate(resourceGroupName, networkSecurityGroupName, parameters);

        return response;
    }

    public static VirtualMachineInner updateAzureVirtualMachine(
            ComputeManagementClientImpl computeManagementClient, String resourceGroupName,
            String vmName, VirtualMachineInner parameters) throws Exception {
        VirtualMachineInner response = computeManagementClient
                .virtualMachines()
                .createOrUpdate(resourceGroupName, vmName, parameters);

        return response;
    }

    public static int getAzureVMCount(ComputeManagementClientImpl computeManagementClient)
            throws Exception {
        List<VirtualMachineInner> response = computeManagementClient
                .virtualMachines().list();

        int count = 0;
        for (VirtualMachineInner virtualMachine : response) {
            if (AzureComputeEnumerationAdapterService.AZURE_VM_TERMINATION_STATES
                    .contains(virtualMachine.provisioningState())) {
                continue;
            }
            count++;
        }

        return count;
    }

    public static void deleteVMs(VerificationHost host, String documentSelfLink, boolean isMock,
            int numberOfRemainingVMs)
            throws Throwable {

        // query VM doc to delete
        QuerySpecification resourceQuerySpec = new QuerySpecification();
        resourceQuerySpec.query
                .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
                .setTermMatchValue(documentSelfLink);

        ResourceRemovalTaskState deletionState = new ResourceRemovalTaskState();
        deletionState.resourceQuerySpec = resourceQuerySpec;
        deletionState.isMockRequest = isMock;

        // Post/Start the ResourceRemovalTaskState...
        deletionState = TestUtils.doPost(host, deletionState, ResourceRemovalTaskState.class,
                UriUtils.buildUri(host, ResourceRemovalTaskService.FACTORY_LINK));
        // ...and wait for the task to complete
        host.waitForFinishedTask(ResourceRemovalTaskState.class, deletionState.documentSelfLink);

        ProvisioningUtils.queryDocumentsAndAssertExpectedCount(host, numberOfRemainingVMs,
                ComputeService.FACTORY_LINK, false);
    }

    public static AuthCredentialsServiceState createDefaultAuthCredentials(VerificationHost host,
            String clientID, String clientKey, String subscriptionId, String tenantId)
            throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKeyId = clientID;
        auth.privateKey = clientKey;
        auth.userLink = subscriptionId;
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_TENANT_ID, tenantId);
        auth.documentSelfLink = UUID.randomUUID().toString();

        return TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
    }

    /**
     * Create a compute host description for an Azure instance
     */
    public static ComputeState createDefaultComputeHost(
            VerificationHost host, String resourcePoolLink, EndpointState endpointState) throws Throwable {

        ComputeDescription azureHostDescription = new ComputeDescription();
        azureHostDescription.id = UUID.randomUUID().toString();
        azureHostDescription.name = azureHostDescription.id;
        azureHostDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureHostDescription.documentSelfLink = azureHostDescription.id;
        azureHostDescription.supportedChildren = new ArrayList<>();
        azureHostDescription.supportedChildren.add(ComputeType.VM_GUEST.name());
        azureHostDescription.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);
        azureHostDescription.enumerationAdapterReference = UriUtils.buildUri(
                host,
                AzureUriPaths.AZURE_ENUMERATION_ADAPTER);
        azureHostDescription.statsAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_STATS_ADAPTER);
        azureHostDescription.authCredentialsLink = endpointState.authCredentialsLink;
        azureHostDescription.endpointLink = endpointState.documentSelfLink;
        azureHostDescription.tenantLinks = endpointState.tenantLinks;

        azureHostDescription = TestUtils.doPost(host, azureHostDescription,
                ComputeDescription.class,
                UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ComputeState azureComputeHost = new ComputeState();
        azureComputeHost.id = UUID.randomUUID().toString();
        azureComputeHost.documentSelfLink = azureComputeHost.id;
        azureComputeHost.type = ComputeType.VM_HOST;
        azureComputeHost.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureComputeHost.name = azureHostDescription.name;
        azureComputeHost.descriptionLink = azureHostDescription.documentSelfLink;
        azureComputeHost.resourcePoolLink = resourcePoolLink;
        azureComputeHost.endpointLink = endpointState.documentSelfLink;
        azureComputeHost.tenantLinks = endpointState.tenantLinks;

        return TestUtils.doPost(host, azureComputeHost, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    public static EndpointState createDefaultEndpointState(VerificationHost host, String authLink)
            throws Throwable {

        return createEndpointState(host, authLink, EndpointType.azure);
    }

    public static EndpointState createEndpointState(VerificationHost host, String authLink, EndpointType endpointType)
            throws Throwable {

        EndpointState endpoint = new EndpointState();

        endpoint.endpointType = endpointType.name();
        endpoint.id = endpointType.name() + "-id";
        endpoint.name = endpointType.name() + "-name";

        endpoint.authCredentialsLink = authLink;

        // Skipping region (EndpointConfigRequest.REGION_KEY) should fall back to default region
        endpoint.endpointProperties = Collections.emptyMap();

        endpoint.tenantLinks = Collections.singletonList(endpointType.name() + "-tenant");

        return TestUtils.doPost(host, endpoint, EndpointState.class,
                UriUtils.buildUri(host, EndpointService.FACTORY_LINK));
    }

    /**
     * Generate random names. For Azure, storage account names need to be unique across Azure.
     */
    public static String generateName(String prefix) {
        return prefix + randomString(5);
    }

    public static String randomString(int length) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append((char) ('a' + random.nextInt(26)));
        }
        return stringBuilder.toString();
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs) throws Throwable {

        return createDefaultVMResource(host, azureVMName, computeHost, endpointState, nicSpecs, null /* networkRGLink */);
    }

    public static ComputeState createDefaultVMResource(VerificationHost host, String azureVMName,
            ComputeState computeHost, EndpointState endpointState,
            AzureNicSpecs nicSpecs, String networkRGLink)
            throws Throwable {

        ResourceGroupState vmRG = createDefaultResourceGroupState(
                host, azureVMName, computeHost, endpointState, ResourceGroupStateType.AzureResourceGroup);

        String resourceGroupLink = vmRG.documentSelfLink;

        if (networkRGLink == null) {
            // The RG where the VM is deployed is also used as RG for the Network!
            networkRGLink = resourceGroupLink;
        }

        AuthCredentialsServiceState azureVMAuth = new AuthCredentialsServiceState();
        azureVMAuth.userEmail = AZURE_ADMIN_USERNAME;
        azureVMAuth.privateKey = AZURE_ADMIN_PASSWORD;
        azureVMAuth = TestUtils.doPost(host, azureVMAuth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));

        // Create a VM desc
        ComputeDescription azureVMDesc = new ComputeDescription();
        azureVMDesc.id = UUID.randomUUID().toString();
        azureVMDesc.documentSelfLink = azureVMDesc.id;
        azureVMDesc.name = azureVMDesc.id;
        azureVMDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        azureVMDesc.authCredentialsLink = azureVMAuth.documentSelfLink;
        azureVMDesc.tenantLinks = endpointState.tenantLinks;
        azureVMDesc.endpointLink = endpointState.documentSelfLink;
        azureVMDesc.instanceType = AZURE_VM_SIZE;
        azureVMDesc.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        azureVMDesc.customProperties = new HashMap<>();

        // set the create service to the azure instance service
        azureVMDesc.instanceAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_INSTANCE_ADAPTER);

        azureVMDesc.powerAdapterReference = UriUtils.buildUri(host,
                AzureUriPaths.AZURE_POWER_ADAPTER);

        azureVMDesc = TestUtils.doPost(host, azureVMDesc, ComputeDescription.class,
                        UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK));

        ImageState bootImage;
        {
            bootImage = new ImageState();
            bootImage.id = IMAGE_REFERENCE;
            bootImage.endpointType = endpointState.endpointType;

            bootImage = TestUtils.doPost(host, bootImage, ImageState.class,
                    UriUtils.buildUri(host, ImageService.FACTORY_LINK));
        }

        List<String> vmDisks = new ArrayList<>();

        DiskState rootDisk = new DiskState();
        rootDisk.name = azureVMName + "-boot-disk";
        rootDisk.id = UUID.randomUUID().toString();
        rootDisk.documentSelfLink = rootDisk.id;
        rootDisk.type = DiskType.HDD;
        rootDisk.capacityMBytes = AZURE_CUSTOM_OSDISK_SIZE; // Custom OSDisk size of 32 GBs
        // Set both sourceImageReference and imageLink. Still imageLink should be used. {{
        rootDisk.sourceImageReference = URI.create(IMAGE_REFERENCE + "dummy");
        rootDisk.imageLink = bootImage.documentSelfLink;
        // }}
        rootDisk.bootOrder = 1;

        rootDisk.endpointLink = endpointState.documentSelfLink;
        rootDisk.tenantLinks = endpointState.tenantLinks;

        rootDisk.customProperties = new HashMap<>();
        rootDisk.customProperties.put(AZURE_OSDISK_CACHING, DEFAULT_OS_DISK_CACHING.name());

        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_NAME,
                (azureVMName + "sa").replace("-", "").toLowerCase());
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_RG_NAME,
                azureVMName);
        rootDisk.customProperties.put(AzureConstants.AZURE_STORAGE_ACCOUNT_TYPE,
                AZURE_STORAGE_ACCOUNT_TYPE);

        rootDisk = TestUtils.doPost(host, rootDisk, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));

        vmDisks.add(rootDisk.documentSelfLink);

        // Create NICs
        List<String> nicLinks = createDefaultNicStates(
                host, computeHost, endpointState, networkRGLink, nicSpecs)
                        .stream()
                        .map(nic -> nic.documentSelfLink)
                        .collect(Collectors.toList());

        // Finally create the compute resource state to provision using all constructs above.
        ComputeState computeState = new ComputeState();
        computeState.id = UUID.randomUUID().toString();
        computeState.name = azureVMName;
        computeState.parentLink = computeHost.documentSelfLink;
        computeState.type = ComputeType.VM_GUEST;
        computeState.environmentName = ComputeDescription.ENVIRONMENT_NAME_AZURE;
        computeState.descriptionLink = azureVMDesc.documentSelfLink;
        computeState.resourcePoolLink = computeHost.resourcePoolLink;
        computeState.diskLinks = vmDisks;
        computeState.networkInterfaceLinks = nicLinks;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(RESOURCE_GROUP_NAME, azureVMName);
        computeState.groupLinks = new HashSet<>();
        computeState.groupLinks.add(resourceGroupLink);
        computeState.endpointLink = endpointState.documentSelfLink;
        computeState.tenantLinks = endpointState.tenantLinks;

        return TestUtils.doPost(host, computeState, ComputeState.class,
                UriUtils.buildUri(host, ComputeService.FACTORY_LINK));
    }

    public static void deleteServiceDocument(VerificationHost host, String documentSelfLink)
            throws Throwable {
        host.testStart(1);
        host.send(
                Operation.createDelete(host, documentSelfLink).setCompletion(host.getCompletion()));
        host.testWait();
    }

    public static StorageDescription createDefaultStorageAccountDescription(VerificationHost host,
            String storageAccountName,
            ComputeState computeHost, EndpointState endpointState) throws Throwable {

        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.customProperties = new HashMap<>();
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY1, randomString(15));
        auth.customProperties.put(AZURE_STORAGE_ACCOUNT_KEY2, randomString(15));
        auth.documentSelfLink = UUID.randomUUID().toString();

        TestUtils.doPost(host, auth, AuthCredentialsServiceState.class,
                UriUtils.buildUri(host, AuthCredentialsService.FACTORY_LINK));
        String authLink = UriUtils.buildUriPath(AuthCredentialsService.FACTORY_LINK,
                auth.documentSelfLink);

        // Create a storage description
        StorageDescription storageDesc = new StorageDescription();
        storageDesc.id = "testStorAcct-" + randomString(4);
        storageDesc.name = storageAccountName;
        storageDesc.regionId = AZURE_RESOURCE_GROUP_LOCATION;
        storageDesc.computeHostLink = computeHost.documentSelfLink;
        storageDesc.authCredentialsLink = authLink;
        storageDesc.resourcePoolLink = computeHost.resourcePoolLink;

        storageDesc.tenantLinks = endpointState.tenantLinks;
        storageDesc.endpointLink = endpointState.documentSelfLink;

        storageDesc.customProperties = new HashMap<>();
        storageDesc.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_ACCOUNTS);

        return TestUtils.doPost(host, storageDesc, StorageDescription.class,
                UriUtils.buildUri(host, StorageDescriptionService.FACTORY_LINK));
    }

    public static ResourceGroupState createDefaultResourceGroupState(VerificationHost host,
            String resourceGroupName, ComputeState computeHost, EndpointState endpointState, ResourceGroupStateType resourceGroupType)
            throws Throwable {

        ResourceGroupState rGroupState = new ResourceGroupState();

        rGroupState.id = "testResGroup-" + randomString(4);
        rGroupState.name = resourceGroupName;

        rGroupState.groupLinks = new HashSet<>();
        rGroupState.groupLinks.add("testResGroup-" + randomString(4));

        rGroupState.tenantLinks = endpointState.tenantLinks;

        rGroupState.customProperties = new HashMap<>();
        rGroupState.customProperties.put(COMPUTE_HOST_LINK_PROP_NAME, computeHost.documentSelfLink);
        rGroupState.customProperties.put(CUSTOM_PROP_ENDPOINT_LINK, endpointState.documentSelfLink);
        rGroupState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_CONTAINERS);
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_LAST_MODIFIED,
                randomString(10));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATE,
                randomString(5));
        rGroupState.customProperties.put(AZURE_STORAGE_CONTAINER_LEASE_STATUS,
                randomString(5));
        rGroupState.customProperties.put(ComputeProperties.RESOURCE_TYPE_KEY,
                resourceGroupType.name());

        return TestUtils.doPost(host, rGroupState,
                ResourceGroupState.class,
                UriUtils.buildUri(host, ResourceGroupService.FACTORY_LINK));
    }

    /**
     * Create a disk state
     */
    public static DiskState createDefaultDiskState(VerificationHost host, String diskName,
            String storageContainerLink, ComputeState computeHost, EndpointState endpointState)
            throws Throwable {

        DiskState diskState = new DiskState();

        diskState.id = UUID.randomUUID().toString();
        diskState.documentSelfLink = diskState.id;
        diskState.name = diskName;
        diskState.computeHostLink = computeHost.documentSelfLink;
        diskState.resourcePoolLink = computeHost.resourcePoolLink;

        diskState.tenantLinks = endpointState.tenantLinks;
        diskState.endpointLink = endpointState.documentSelfLink;

        diskState.storageDescriptionLink = storageContainerLink;
        diskState.type = DEFAULT_DISK_TYPE;
        diskState.capacityMBytes = DEFAULT_DISK_CAPACITY;
        diskState.sourceImageReference = URI.create(DEFAULT_DISK_SERVICE_REFERENCE);
        diskState.customProperties = new HashMap<>();
        diskState.customProperties.put(AZURE_STORAGE_TYPE, AZURE_STORAGE_BLOBS);

        return TestUtils.doPost(host, diskState, DiskState.class,
                UriUtils.buildUri(host, DiskService.FACTORY_LINK));
    }

    /*
     * NOTE: It is highly recommended to keep this method in sync with its AWS counterpart:
     * TestAWSSetupUtils.createAWSNicStates
     */
    public static List<NetworkInterfaceState> createDefaultNicStates(
            VerificationHost host,
            ComputeState computeHost, EndpointState endpointSate,
            String networkRGLink,
            AzureNicSpecs nicSpecs) throws Throwable {

        // Create network state.
        NetworkState networkState;
        {
            networkState = new NetworkState();
            networkState.id = nicSpecs.network.name;
            networkState.name = nicSpecs.network.name;
            networkState.subnetCIDR = nicSpecs.network.cidr;
            networkState.authCredentialsLink = endpointSate.authCredentialsLink;
            networkState.endpointLink = endpointSate.documentSelfLink;
            networkState.tenantLinks = endpointSate.tenantLinks;
            networkState.resourcePoolLink = computeHost.resourcePoolLink;
            networkState.groupLinks = Collections.singleton(networkRGLink);
            networkState.regionId = nicSpecs.network.zoneId;
            networkState.instanceAdapterReference = UriUtils.buildUri(host,
                    DEFAULT_INSTANCE_ADAPTER_REFERENCE);

            networkState = TestUtils.doPost(host, networkState,
                    NetworkState.class,
                    UriUtils.buildUri(host, NetworkService.FACTORY_LINK));
        }

        // Create NIC states.
        List<NetworkInterfaceState> nics = new ArrayList<>();

        for (int i = 0; i < nicSpecs.subnets.size(); i++) {

            // Create subnet state per NIC.
            SubnetState subnetState;
            {
                subnetState = new SubnetState();

                subnetState.id = nicSpecs.subnets.get(i).name;
                subnetState.name = nicSpecs.subnets.get(i).name;
                subnetState.subnetCIDR = nicSpecs.subnets.get(i).cidr;
                subnetState.zoneId = nicSpecs.subnets.get(i).zoneId;
                subnetState.networkLink = networkState.documentSelfLink;
                subnetState.endpointLink = endpointSate.documentSelfLink;
                subnetState.tenantLinks = endpointSate.tenantLinks;

                subnetState = TestUtils.doPost(host, subnetState,
                        SubnetState.class,
                        UriUtils.buildUri(host, SubnetService.FACTORY_LINK));
            }

            // Create security group state
            SecurityGroupState securityGroupState;
            {
                securityGroupState = new SecurityGroupState();
                securityGroupState.name = AZURE_SECURITY_GROUP_NAME;
                securityGroupState.authCredentialsLink = endpointSate.authCredentialsLink;
                securityGroupState.endpointLink = endpointSate.documentSelfLink;
                securityGroupState.tenantLinks = endpointSate.tenantLinks;
                securityGroupState.groupLinks = Collections.singleton(networkRGLink);
                ArrayList<Rule> ingressRules = new ArrayList<>();

                Rule ssh = new Rule();
                ssh.name = "ssh-in";
                ssh.protocol = "tcp";
                ssh.ipRangeCidr = "0.0.0.0/0";
                ssh.ports = "22";
                ingressRules.add(ssh);
                securityGroupState.ingress = ingressRules;

                ArrayList<Rule> egressRules = new ArrayList<>();
                Rule out = new Rule();
                out.name = "out";
                out.protocol = "tcp";
                out.ipRangeCidr = "0.0.0.0/0";
                out.ports = "1-65535";
                egressRules.add(out);
                securityGroupState.egress = egressRules;

                securityGroupState.regionId = "regionId";
                securityGroupState.resourcePoolLink = "/link/to/rp";
                securityGroupState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");

                securityGroupState = TestUtils.doPost(host, securityGroupState,
                        SecurityGroupState.class,
                        UriUtils.buildUri(host, SecurityGroupService.FACTORY_LINK));
            }

            // Create NIC description.
            NetworkInterfaceDescription nicDescription;
            {
                nicDescription = new NetworkInterfaceDescription();

                nicDescription.id = "nicDesc" + i;
                nicDescription.name = "nicDesc" + i;
                nicDescription.deviceIndex = i;
                nicDescription.assignment = IpAssignment.DYNAMIC;
                nicDescription.assignPublicIpAddress = nicSpecs.assignPublicIpAddress;

                nicDescription.tenantLinks = endpointSate.tenantLinks;
                nicDescription.endpointLink = endpointSate.documentSelfLink;

                nicDescription = TestUtils.doPost(host, nicDescription,
                        NetworkInterfaceDescription.class,
                        UriUtils.buildUri(host, NetworkInterfaceDescriptionService.FACTORY_LINK));
            }

            NetworkInterfaceState nicState = new NetworkInterfaceState();

            nicState.id = "nic" + i;
            nicState.name = "nic" + i;
            nicState.deviceIndex = nicDescription.deviceIndex;

            if (i == 0) {
                // Attach security group only on the primary nic.
                nicState.securityGroupLinks = Collections
                        .singletonList(securityGroupState.documentSelfLink);
            }

            nicState.networkInterfaceDescriptionLink = nicDescription.documentSelfLink;
            nicState.subnetLink = subnetState.documentSelfLink;
            nicState.networkLink = subnetState.networkLink;
            nicState.tenantLinks = endpointSate.tenantLinks;
            nicState.endpointLink = endpointSate.documentSelfLink;

            nicState = TestUtils.doPost(host, nicState,
                    NetworkInterfaceState.class,
                    UriUtils.buildUri(host, NetworkInterfaceService.FACTORY_LINK));

            nics.add(nicState);
        }

        return nics;
    }

    /**
     * Create SHARED vNet-Subnets-Gateway in a separate RG.
     * <p>
     * NOTE1: The names of the vNet and Subnets MUST be equal to the ones set by
     * AzureTestUtil.createDefaultNicStates.
     * <p>
     * NOTE2: Since this is SHARED vNet it's not deleted after the test.
     */
    public static ResourceGroupInner createResourceGroupWithSharedNetwork(
            ResourceManagementClientImpl resourceManagementClient,
            NetworkManagementClientImpl networkManagementClient,
            AzureNicSpecs nicSpecs) throws Throwable {

        // Create the shared RG itself
        ResourceGroupInner sharedNetworkRGParams = new ResourceGroupInner();
        sharedNetworkRGParams.withName(AZURE_SHARED_NETWORK_RESOURCE_GROUP_NAME);
        sharedNetworkRGParams.withLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

        ResourceGroupInner sharedNetworkRG = resourceManagementClient.resourceGroups()
                .createOrUpdate(sharedNetworkRGParams.name(), sharedNetworkRGParams);

        // Create shared vNet-Subnet-Gateway under shared RG
        createAzureVirtualNetwork(sharedNetworkRG.name(), nicSpecs,
                networkManagementClient);

        // Create shared NSG under shared RG
        createAzureNetworkSecurityGroup(sharedNetworkRG.name(), networkManagementClient);

        return sharedNetworkRG;
    }

    private static void createAzureVirtualNetwork(String resourceGroupName,
            AzureNicSpecs nicSpecs,
            NetworkManagementClientImpl networkManagementClient) throws Exception {

        try {
            VirtualNetworkInner vNet = new VirtualNetworkInner();
            vNet.withLocation(nicSpecs.network.zoneId);

            AddressSpace addressSpace = new AddressSpace();
            addressSpace.withAddressPrefixes(
                    Collections.singletonList(nicSpecs.network.cidr));
            vNet.withAddressSpace(addressSpace);

            List<SubnetInner> subnetList = new ArrayList<>();

            for (int i = 0; i < nicSpecs.subnets.size(); i++) {
                SubnetInner subnet = new SubnetInner();
                subnet.withName(nicSpecs.subnets.get(i).name);
                subnet.withAddressPrefix(nicSpecs.subnets.get(i).cidr);

                subnetList.add(subnet);
            }

            vNet.withSubnets(subnetList);

            networkManagementClient.virtualNetworks().createOrUpdate(
                    resourceGroupName, nicSpecs.network.name, vNet);

            addAzureGatewayToVirtualNetwork(resourceGroupName, nicSpecs, networkManagementClient);

        } catch (CloudException ex) {
            /*
             * CloudException is thrown if the vNet already exists and we are trying to do an
             * update, because there are objects (GatewaySubnet) attached to it
             */
            assertTrue(ex.body().code().equals("InUseSubnetCannotBeDeleted"));
        }
    }

    private static void createAzureNetworkSecurityGroup(String resourceGroupName,
            NetworkManagementClientImpl networkManagementClient) throws Exception {

        final NetworkSecurityGroupInner sharedNSG = new NetworkSecurityGroupInner();
        sharedNSG.withLocation(AzureTestUtil.AZURE_RESOURCE_GROUP_LOCATION);

        SecurityRuleInner sr = new SecurityRuleInner();
        sr.withPriority(AzureConstants.AZURE_SECURITY_GROUP_PRIORITY);
        sr.withAccess(SecurityRuleAccess.ALLOW);
        sr.withDirection(SecurityRuleDirection.INBOUND);
        sr.withSourceAddressPrefix(AzureConstants.AZURE_SECURITY_GROUP_SOURCE_ADDRESS_PREFIX);
        sr.withDestinationAddressPrefix(
                AzureConstants.AZURE_SECURITY_GROUP_DESTINATION_ADDRESS_PREFIX);

        sr.withSourcePortRange(AzureConstants.AZURE_SECURITY_GROUP_SOURCE_PORT_RANGE);
        sr.withDestinationPortRange(
                AzureConstants.AZURE_LINUX_SECURITY_GROUP_DESTINATION_PORT_RANGE);
        sr.withName(AzureConstants.AZURE_LINUX_SECURITY_GROUP_NAME);
        sr.withProtocol(SecurityRuleProtocol.TCP);

        sharedNSG.withSecurityRules(Collections.singletonList(sr));

        networkManagementClient.networkSecurityGroups()
                .createOrUpdate(resourceGroupName, AzureTestUtil.AZURE_SECURITY_GROUP_NAME,
                        sharedNSG);
    }

    /**
     * Adds Gateway to Virtual Network in Azure
     */
    private static void addAzureGatewayToVirtualNetwork(String resourceGroupName,
            AzureNicSpecs nicSpecs, NetworkManagementClientImpl networkManagementClient)
            throws CloudException, IOException, InterruptedException {

        // create Gateway Subnet
        SubnetInner gatewaySubnetParams = new SubnetInner();
        gatewaySubnetParams.withName(nicSpecs.gateway.name);
        gatewaySubnetParams.withAddressPrefix(nicSpecs.gateway.cidr);
        SubnetInner gatewaySubnet = networkManagementClient
                .subnets()
                .createOrUpdate(resourceGroupName, nicSpecs.network.name,
                        AzureConstants.GATEWAY_SUBNET_NAME,
                        gatewaySubnetParams);

        // create Public IP
        PublicIPAddressInner publicIPAddressParams = new PublicIPAddressInner();
        publicIPAddressParams.withPublicIPAllocationMethod(IPAllocationMethod.DYNAMIC);
        publicIPAddressParams.withLocation(nicSpecs.gateway.zoneId);

        PublicIPAddressInner publicIPAddress = networkManagementClient
                .publicIPAddresses()
                .createOrUpdate(resourceGroupName, nicSpecs.gateway.publicIpName,
                        publicIPAddressParams);

        SubResource publicIPSubResource = new SubResource();
        publicIPSubResource.withId(publicIPAddress.id());

        // create IP Configuration
        VirtualNetworkGatewayIPConfigurationInner ipConfiguration = new VirtualNetworkGatewayIPConfigurationInner();
        ipConfiguration.withName(nicSpecs.gateway.ipConfigurationName);
        ipConfiguration.withSubnet(gatewaySubnet);
        ipConfiguration.withPrivateIPAllocationMethod(IPAllocationMethod.DYNAMIC);

        ipConfiguration.withPublicIPAddress(publicIPSubResource);

        // create Virtual Network Gateway
        VirtualNetworkGatewayInner virtualNetworkGateway = new VirtualNetworkGatewayInner();
        virtualNetworkGateway.withGatewayType(VirtualNetworkGatewayType.VPN);
        virtualNetworkGateway.withVpnType(VpnType.ROUTE_BASED);
        VirtualNetworkGatewaySku vNetGatewaySku = new VirtualNetworkGatewaySku();
        vNetGatewaySku.withName(VirtualNetworkGatewaySkuName.STANDARD);
        vNetGatewaySku.withTier(VirtualNetworkGatewaySkuTier.STANDARD);
        vNetGatewaySku.withCapacity(2);
        virtualNetworkGateway.withSku(vNetGatewaySku);
        virtualNetworkGateway.withLocation(AZURE_RESOURCE_GROUP_LOCATION);

        List<VirtualNetworkGatewayIPConfigurationInner> ipConfigurations = new ArrayList<>();
        ipConfigurations.add(ipConfiguration);
        virtualNetworkGateway.withIpConfigurations(ipConfigurations);

        // Call the async variant because the virtual network gateway provisioning depends on
        // the public IP address assignment which is time-consuming operation
        networkManagementClient.virtualNetworkGateways().createOrUpdateAsync(
                resourceGroupName, nicSpecs.gateway.name, virtualNetworkGateway,
                new ServiceCallback<VirtualNetworkGatewayInner>() {
                    @Override
                    public void failure(Throwable throwable) {
                        throw new RuntimeException(
                                "Error creating Azure Virtual Network Gateway.", throwable);
                    }

                    @Override
                    public void success(
                            VirtualNetworkGatewayInner response) {

                    }
                });
    }

    /**
     * Assert that a resource with the provided name exist in the document store.
     *
     * @param factoryLink
     *            Factory link to the stateful service which states to check.
     * @param name
     *            name of the resource to assert if exists.
     * @param shouldExists
     *            whether to assert if a resource exists or not.
     */
    public static void assertResourceExists(VerificationHost host, String factoryLink,
            String name, boolean shouldExists) {

        ServiceDocumentQueryResult result = host.getExpandedFactoryState(
                UriUtils.buildUri(host, factoryLink));

        boolean exists = false;
        for (Object document : result.documents.values()) {
            ResourceState state = Utils.fromJson(document, ResourceState.class);

            if (name.equals(state.name)) {
                exists = true;
                break;
            }
        }

        assertEquals("Expected: " + shouldExists + ", but was: " + exists, shouldExists, exists);
    }

    /**
     * Query to get ResourceMetrics document for a specific resource containing a specific metric.
     *
     * @param host
     *            host against which query is triggered
     * @param resourceLink
     *            Link to the resource on which stats are being collected.
     * @param metricKey
     *            Metric name.
     * @return ResourceMetrics document.
     */
    public static ResourceMetrics getResourceMetrics(VerificationHost host, String resourceLink,
            String metricKey) {
        QueryTask qt = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setQuery(QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                        UriUtils.getLastPathSegment(resourceLink)),
                                QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QuerySpecification
                                .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES,
                                        metricKey),
                                QueryTask.NumericRange
                                        .createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build())
                .build();
        Operation op = Operation
                .createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setReferer(host.getUri()).setBody(qt).setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.INFO, e.toString());
                    }
                });
        Operation result = host.waitForResponse(op);
        QueryTask qtResult = result.getBody(QueryTask.class);
        ResourceMetrics resourceMetric = null;
        if (qtResult.results.documentLinks.size() > 0) {
            String documentLink = qtResult.results.documentLinks.get(0);
            resourceMetric = Utils.fromJson(qtResult.results.documents.get(documentLink),
                    ResourceMetrics.class);
        }
        return resourceMetric;
    }

    /**
     * Runs azure enumeration.
     */
    public static void runEnumeration(VerificationHost host, String hostSelfLink,
            String resourcePoolLink, EndpointState endpointState, boolean isMock) throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.endpointLink = endpointState.documentSelfLink;
        enumerationTaskState.tenantLinks = endpointState.tenantLinks;
        enumerationTaskState.parentComputeLink = hostSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = resourcePoolLink;
        if (isMock) {
            enumerationTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(host, ResourceEnumerationTaskService.FACTORY_LINK));

        host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = host
                        .waitForFinishedTask(ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }

    /**
     * Helper for running stats collection task.
     *
     * @param host
     * @param isMock
     * @throws Throwable
     */
    public static void resourceStatsCollection(VerificationHost host,
            boolean isMock, String resourcePoolLink) throws Throwable {
        resourceStatsCollection(host, null,
                isMock ? EnumSet.of(TaskOption.IS_MOCK) : null, resourcePoolLink);
    }

    /**
     * Waits for stats collection task to be finished.
     *
     * @param host
     * @param peerURI
     * @param options
     * @throws Throwable
     */
    public static void resourceStatsCollection(VerificationHost host, URI peerURI,
            EnumSet<TaskOption> options, String resourcePoolLink) throws Throwable {
        StatsCollectionTaskState statsTask = performResourceStatsCollection(
                host, options, resourcePoolLink);

        // Wait for the stats collection task to be completed.
        host.waitForFinishedTask(StatsCollectionTaskState.class,
                ProvisioningUtils.createServiceURI(host, peerURI, statsTask.documentSelfLink));
    }

    /**
     * Performs stats collection for given resourcePoolLink.
     */
    public static StatsCollectionTaskState performResourceStatsCollection(
            VerificationHost host, EnumSet<TaskOption> options, String resourcePoolLink)
            throws Throwable {

        StatsCollectionTaskState statsCollectionTaskState = new StatsCollectionTaskState();

        statsCollectionTaskState.resourcePoolLink = resourcePoolLink;
        statsCollectionTaskState.options = EnumSet.noneOf(TaskOption.class);

        if (options != null) {
            statsCollectionTaskState.options = options;
        }

        URI uri = UriUtils.buildUri(host, StatsCollectionTaskService.FACTORY_LINK);
        StatsCollectionTaskState statsTask = TestUtils.doPost(
                host, statsCollectionTaskState, StatsCollectionTaskState.class, uri);

        return statsTask;
    }

    /**
     * Performs stats collection for given resourcePoolLink.
     */
    public static void resourceStatsAggregation(VerificationHost host,
            String resourcePoolLink) throws Throwable {
        host.testStart(1);
        StatsAggregationTaskState statsAggregationTaskState = new StatsAggregationTaskState();
        QueryTask.Query taskQuery = QueryTask.Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink)
                .build();
        statsAggregationTaskState.query = taskQuery;
        statsAggregationTaskState.taskInfo = TaskState.createDirect();
        statsAggregationTaskState.metricNames = getMetricNames();

        host.send(Operation
                .createPost(
                        UriUtils.buildUri(host,
                                StatsAggregationTaskService.FACTORY_LINK))
                .setBody(statsAggregationTaskState)
                .setCompletion(host.getCompletion()));
        host.testWait();
    }

    /**
     * Define the metric names that should be aggregated.
     */
    public static Set<String> getMetricNames() {
        Set<String> metricNames = new HashSet<>();
        // CPU
        metricNames.add(CPU_UTILIZATION_PERCENT);

        // Memory
        metricNames.add(MEMORY_USED_PERCENT);

        // Storage
        metricNames.add(STORAGE_USED_BYTES);

        return metricNames;
    }
}
