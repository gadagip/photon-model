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

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.DISK_CONTROLLER_NUMBER;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.microsoft.azure.management.compute.DataDisk;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.azure.management.compute.VirtualMachine;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Service to attach a disk to VM
 */
public class AzureComputeDiskDay2Service extends StatelessService {

    public static final String SELF_LINK = AzureUriPaths.AZURE_DISK_DAY2_ADAPTER;

    private ExecutorService executorService;

    /**
     * Azure Disk attach request context.
     */
    public static class AzureComputeDiskContext {

        public final ResourceOperationRequest request;

        public ComputeState computeState;
        public DiskState diskState;
        public EndpointState endpointState;
        public TaskManager taskManager;
        public AuthCredentialsService.AuthCredentialsServiceState authentication;
        public AzureSdkClients azureSdkClients;
        public VirtualMachine provisionedVm;

        public AzureComputeDiskContext(StatelessService service, ResourceOperationRequest request) {
            this.request = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        this.executorService = getHost().allocateExecutor(this);
        Operation.CompletionHandler completionHandler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this,
                completionHandler, getResourceOperationSpecs());
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("Body is required"));
            return;
        }

        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);

        try {
            validateInputRequest(request);
        } catch (Exception ex) {
            op.fail(ex);
            return;
        }
        op.complete();

        // initialize context object
        AzureComputeDiskContext context = new AzureComputeDiskContext(this, request);

        if (request.operation.equals(ResourceOperation.ATTACH_DISK.operation)) {
            DeferredResult.completed(context)
                    .thenCompose(this::getComputeState)
                    .thenCompose(this::getDiskState)
                    .thenCompose(this::configureAzureSDKClient)
                    .thenCompose(this::performDiskAttachment)
                    .thenCompose(this::updateComputeStateAndDiskState)
                    .whenComplete((o, ex) -> {
                        if (ex != null) {
                            context.taskManager.patchTaskToFailure(ex);
                            return;
                        }
                        context.taskManager.finishTask();
                    });
        } else if (request.operation.equals(ResourceOperation.DETACH_DISK.operation)) {
            //TODO: detach a disk from its vm
        } else {
            context.taskManager.patchTaskToFailure(new IllegalArgumentException(
                    String.format("Unknown operation %s for a disk", request.operation)));
        }
    }

    /**
     * Validate input request before performing the disk day 2 operation.
     */
    private void validateInputRequest(ResourceOperationRequest request) {
        if (request.operation == null) {
            throw new IllegalArgumentException(
                    "Operation cannot be empty");
        }

        if (request.resourceReference == null) {
            throw new IllegalArgumentException(
                    "Compute resource reference to perform the disk operation cannot be empty");
        }

        if (request.payload == null || request.payload.get(PhotonModelConstants.DISK_LINK) == null) {
            throw new IllegalArgumentException(
                    "Disk reference to attach to Compute cannot be empty");
        }
    }

    /**
     * Get ComputeState object of a VM using resource reference and save in context
     */
    private DeferredResult<AzureComputeDiskContext> getComputeState(AzureComputeDiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(context.request.resourceReference),
                ComputeState.class)
                .thenApply(computeState -> {
                    context.computeState = computeState;
                    return context;
                });
    }

    /**
     * Get DiskState object of disk using disk reference and save in context
     */
    private DeferredResult<AzureComputeDiskContext> getDiskState(AzureComputeDiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(this.getHost(), context.request
                        .payload.get(PhotonModelConstants.DISK_LINK)), DiskState.class)
                .thenApply(diskState -> {
                    context.diskState = diskState;
                    return context;
                });
    }

    /**
     * Method to attach Azure disk to VM
     */
    private DeferredResult<AzureComputeDiskContext> performDiskAttachment(AzureComputeDiskContext context) {

        if (context.request.isMockRequest) {
            return DeferredResult.completed(context);
        }

        String instanceId = context.computeState.id;
        if (instanceId == null) {
            String msg = "Compute Id cannot be null";
            this.logSevere("[AzureComputeDiskDay2Service] " + msg);
            return DeferredResult.failed(new IllegalArgumentException(msg));
        }

        String diskId = context.diskState.id;
        if (diskId == null) {
            String msg = "Disk Id cannot be null";
            this.logSevere("[AzureComputeDiskDay2Service] " + msg);
            return DeferredResult.failed(new IllegalArgumentException(msg));
        }
        Disk disk = context.azureSdkClients.getComputeManager().disks()
                .getById(diskId);

        final String msg = "Attaching an independent disk with name [" + context.diskState.name +
                "]" + "to machine with name [" + context.computeState.name + "]";


        AzureDeferredResultServiceCallback<VirtualMachine> handler =
                new AzureDeferredResultServiceCallback<VirtualMachine>(this, msg) {

                    @Override
                    protected DeferredResult<VirtualMachine> consumeSuccess(VirtualMachine vm) {
                        String msg = String.format("[AzureComputeDiskDay2Service] Successfully attached volume %s to instance %s",
                                diskId, instanceId);
                        this.service.logInfo(() -> msg);
                        context.provisionedVm = vm;
                        return DeferredResult.completed(vm);
                    }
                };

        context.azureSdkClients.getComputeManager().virtualMachines()
                .getById(instanceId)
                .update()
                .withExistingDataDisk(disk)
                .applyAsync(handler);

        return handler.toDeferredResult().thenApply(virtualMachine -> context);
    }

    private DeferredResult<AzureComputeDiskContext> updateComputeStateAndDiskState(AzureComputeDiskContext context) {
        List<DeferredResult<Operation>> patchedDRs = new ArrayList<>();
        patchedDRs.add(updateDiskState(context));
        patchedDRs.add(updateComputeState(context));

        return DeferredResult.allOf(patchedDRs).handle((o, e) -> {
            if (e != null) {
                logSevere(() -> String.format("Updating ComputeState %s and DiskState %s : FAILED with %s",
                        context.computeState.name, context.diskState.name,
                        Utils.toString(e)));
                throw new IllegalStateException(e);
            } else {
                logFine(() -> String.format("Updating ComputeState %s and DiskState %s : SUCCESS",
                        context.computeState.name, context.diskState.name));
            }
            return context;
        });
    }

    /**
     * Update the diskLink of DiskState in ComputeState
     */
    private DeferredResult<Operation> updateComputeState(AzureComputeDiskContext context) {
        ComputeState computeState = context.computeState;
        if (null == computeState.diskLinks) {
            computeState.diskLinks = new ArrayList<>();
        }
        computeState.diskLinks.add(context.diskState.documentSelfLink);

        Operation computeStatePatchOp = Operation.createPatch(UriUtils.buildUri(this.getHost(),
                computeState.documentSelfLink))
                .setBody(computeState)
                .setReferer(this.getUri());

        return this.sendWithDeferredResult(computeStatePatchOp);
    }

    /**
     * Update status and LUN of DiskState
     */
    private DeferredResult<Operation> updateDiskState(AzureComputeDiskContext context) {
        DiskState diskState = context.diskState;
        diskState.status = DiskService.DiskStatus.ATTACHED;

        if (!context.request.isMockRequest) {
            DataDisk dataDisk = context.provisionedVm.inner().storageProfile().dataDisks()
                    .stream()
                    .filter(dd -> diskState.name.equalsIgnoreCase(dd.name()))
                    .findFirst()
                    .orElse(null);

            if (dataDisk != null) {
                diskState.customProperties.put(DISK_CONTROLLER_NUMBER, String.valueOf(dataDisk.lun()));
            }
        }

        Operation diskPatchOp = Operation.createPatch(UriUtils.buildUri(this.getHost(),
                diskState.documentSelfLink))
                .setBody(diskState)
                .setReferer(this.getUri());

        return this.sendWithDeferredResult(diskPatchOp);
    }

    private DeferredResult<AzureComputeDiskContext> configureAzureSDKClient(AzureComputeDiskContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getEndpointState)
                .thenCompose(this::getAuthentication)
                .thenCompose(this::getAzureClient);
    }

    /**
     * Get the endpoint state object
     */
    private DeferredResult<AzureComputeDiskContext> getEndpointState(AzureComputeDiskContext context) {
        URI uri = context.request.buildUri(context.computeState.endpointLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri), EndpointState.class)
                .thenApply(endpointState -> {
                    context.endpointState = endpointState;
                    return context;
                });
    }

    /**
     * Get the authentication object using endpoint authentication
     */
    private DeferredResult<AzureComputeDiskContext> getAuthentication(AzureComputeDiskContext context) {
        return this.sendWithDeferredResult(
                Operation.createGet(getHost(), context.endpointState.authCredentialsLink),
                AuthCredentialsService.AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.authentication = authCredentialsServiceState;
                    return context;
                });
    }

    /**
     * Get Azure sdk client object to access Azure APIs
     */
    private DeferredResult<AzureComputeDiskContext> getAzureClient(AzureComputeDiskContext context) {
        if (context.azureSdkClients == null) {
            context.azureSdkClients = new AzureSdkClients(this.executorService, context.authentication);
        }
        return DeferredResult.completed(context);
    }

    /**
     * List of resource operations that are supported by this service.
     */
    private ResourceOperationSpecService.ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpecService.ResourceOperationSpec attachDiskSpec = createResourceOperationSpec(
                ResourceOperation.ATTACH_DISK);
        ResourceOperationSpecService.ResourceOperationSpec detachDiskSpec = createResourceOperationSpec(
                ResourceOperation.DETACH_DISK);
        return new ResourceOperationSpecService.ResourceOperationSpec[] { attachDiskSpec,
                detachDiskSpec };
    }

    /**
     * Create a resource operation spec
     */
    private ResourceOperationSpecService.ResourceOperationSpec createResourceOperationSpec(
            ResourceOperation operationType) {
        ResourceOperationSpecService.ResourceOperationSpec spec = new ResourceOperationSpecService.ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = PhotonModelConstants.EndpointType.azure.name();
        spec.resourceType = ResourceOperationSpecService.ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        return spec;
    }
}