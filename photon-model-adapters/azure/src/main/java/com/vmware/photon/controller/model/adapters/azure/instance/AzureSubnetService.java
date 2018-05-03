/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;

import java.net.URI;
import java.util.UUID;

import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.SubnetsInner;
import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.SubnetInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback.Default;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureProvisioningCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.support.LifecycleState;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter to create/delete a subnet on Azure.
 */
public class AzureSubnetService extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_SUBNET_ADAPTER;

    /**
     * Subnet request context.
     */
    private static class AzureSubnetContext implements AutoCloseable {

        final SubnetInstanceRequest request;

        EndpointState endpoint;
        AuthCredentialsServiceState authentication;

        AzureSdkClients azureSdkClients;

        SubnetState subnetState;
        NetworkState parentNetwork;
        String parentNetworkResourceGroupName;

        TaskManager taskManager;

        AzureSubnetContext(StatelessService service, SubnetInstanceRequest request) {
            this.request = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }

        @Override
        public void close() {
            if (this.azureSdkClients != null) {
                this.azureSdkClients.close();
                this.azureSdkClients = null;
            }
        }
    }

    @Override
    public void handlePatch(Operation op) {

        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // initialize context object
        AzureSubnetContext context = new AzureSubnetContext(this,
                op.getBody(SubnetInstanceRequest.class));

        // Immediately complete the Operation from calling task.
        op.complete();

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleSubnetInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                    context.close();
                });
    }

    private DeferredResult<AzureSubnetContext> populateContext(AzureSubnetContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getSubnetState)
                .thenCompose(this::getParentNetwork)
                .thenCompose(this::getParentNetworkRG)
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getAuthentication)
                .thenCompose(this::getAzureClient);
    }

    private DeferredResult<AzureSubnetContext> getSubnetState(AzureSubnetContext context) {
        return sendWithDeferredResult(
                Operation.createGet(context.request.resourceReference),
                SubnetState.class)

                .thenApply(subnetState -> {
                    context.subnetState = subnetState;
                    return context;
                });
    }

    private DeferredResult<AzureSubnetContext> getParentNetworkRG(AzureSubnetContext context) {

        AssertUtil.assertNotNull(context.parentNetwork.groupLinks,
                "context.parentNetwork.groupLinks is null: "
                        + Utils.toJson(context.parentNetwork));

        AssertUtil.assertTrue(context.parentNetwork.groupLinks.size() == 1,
                "context.parentNetwork.groupLinks doesn't contain exactly one element: "
                        + Utils.toJson(context.parentNetwork));

        URI rgUri = context.request.buildUri(context.parentNetwork.groupLinks.iterator().next());

        return sendWithDeferredResult(
                Operation.createGet(rgUri),
                ResourceGroupState.class)

                .thenApply(rgState -> {
                    if (rgState.customProperties != null &&
                            ResourceGroupStateType.AzureResourceGroup.name().equals(
                                    rgState.customProperties.get(
                                            ComputeProperties.RESOURCE_TYPE_KEY))) {

                        context.parentNetworkResourceGroupName = rgState.name;

                        return context;
                    }

                    throw new IllegalStateException(
                                String.format("Resource group with id [%s] has an invalid type",
                                        rgState.id));
                });
    }

    private DeferredResult<AzureSubnetContext> getParentNetwork(AzureSubnetContext context) {
        URI uri = context.request.buildUri(context.subnetState.networkLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                NetworkState.class)
                .thenApply(parentNetwork -> {
                    context.parentNetwork = parentNetwork;
                    return context;
                });
    }

    private DeferredResult<AzureSubnetContext> getEndpointState(AzureSubnetContext context) {
        URI uri = context.request.buildUri(context.subnetState.endpointLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                EndpointState.class)
                .thenApply(endpointState -> {
                    context.endpoint = endpointState;
                    return context;
                });
    }

    private DeferredResult<AzureSubnetContext> getAuthentication(AzureSubnetContext context) {
        URI uri = createInventoryUri(this.getHost(), context.endpoint.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri),
                AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.authentication = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AzureSubnetContext> handleSubnetInstanceRequest(
            AzureSubnetContext context) {

        DeferredResult<AzureSubnetContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just generate Azure Subnet Id.
                context.subnetState.id = UUID.randomUUID().toString();
            } else {
                execution = execution
                        // .thenCompose(this::checkSubnetCIDR)
                        .thenCompose(this::createSubnet);
            }

            return execution.thenCompose(this::updateSubnetState);

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point
                this.logFine("Mock request to delete an Azure subnet ["
                        + context.subnetState.name + "] processed.");
            } else {
                execution = execution.thenCompose(this::deleteSubnet);
            }

            return execution.thenCompose(this::deleteSubnetState);
        default:
            IllegalStateException ex = new IllegalStateException("unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    private DeferredResult<AzureSubnetContext> getAzureClient(AzureSubnetContext ctx) {

        if (ctx.azureSdkClients == null) {
            ctx.azureSdkClients = new AzureSdkClients(ctx.authentication);
        }

        return DeferredResult.completed(ctx);
    }

    private DeferredResult<AzureSubnetContext> createSubnet(AzureSubnetContext context) {
        SubnetInner subnet = new SubnetInner();
        subnet.withName(context.subnetState.name);
        subnet.withAddressPrefix(context.subnetState.subnetCIDR);

        String rgName = context.parentNetworkResourceGroupName;
        String vNetName = context.parentNetwork.name;

        final String msg = "Creating Azure Subnet [" + subnet.name()
                + "] in vNet [" + vNetName
                + "] in resource group [" + rgName + "].";

        SubnetsInner subnetsInner = context.azureSdkClients
                .getNetworkManagementClientImpl()
                .subnets();

        AzureProvisioningCallback<SubnetInner> handler = new AzureProvisioningCallback<SubnetInner>(
                this, msg) {

            @Override
            protected DeferredResult<SubnetInner> consumeProvisioningSuccess(SubnetInner subnet) {
                // Populate the subnet id with Azure Subnet ID
                context.subnetState.id = subnet.id();
                return DeferredResult.completed(subnet);
            }

            @Override
            protected String getProvisioningState(SubnetInner subnet) {
                return subnet.provisioningState();
            }

            @Override
            protected Runnable checkProvisioningStateCall(
                    ServiceCallback<SubnetInner> checkProvisioningStateCallback) {
                return () -> subnetsInner.getAsync(
                        rgName,
                        vNetName,
                        subnet.name(),
                        null /* expand */,
                        checkProvisioningStateCallback);
            }
        };

        subnetsInner.createOrUpdateAsync(rgName, vNetName, subnet.name(), subnet, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private DeferredResult<AzureSubnetContext> updateSubnetState(AzureSubnetContext context) {
        context.subnetState.lifecycleState = LifecycleState.READY;

        return this.sendWithDeferredResult(
                Operation.createPatch(this, context.subnetState.documentSelfLink)
                        .setBody(context.subnetState))
                .thenApply(op -> context);
    }

    private DeferredResult<AzureSubnetContext> deleteSubnet(AzureSubnetContext context) {

        String rgName = context.parentNetworkResourceGroupName;
        String vNetName = context.parentNetwork.name;
        String subnetName = context.subnetState.name;

        final String msg = "Deleting Azure Subnet [" + subnetName
                + "] in vNet [" + vNetName
                + "] in resource group [" + rgName + "].";

        AzureDeferredResultServiceCallback<Void> handler = new Default<>(this, msg);

        context.azureSdkClients.getNetworkManagementClientImpl()
                .subnets()
                .deleteAsync(rgName, vNetName, subnetName, handler);

        return handler.toDeferredResult().thenApply(ignore -> context);
    }

    private DeferredResult<AzureSubnetContext> deleteSubnetState(AzureSubnetContext context) {
        return this.sendWithDeferredResult(
                Operation.createDelete(this, context.subnetState.documentSelfLink))
                .thenApply(operation -> context);
    }
}
