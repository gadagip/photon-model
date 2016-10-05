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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Enumeration Adapter for Azure. Performs a list call to the Azure API
 * and reconciles the local state with the state on the remote system. It lists the instances on the remote system.
 * Compares those with the local system, creates the instances that are missing in the local system, and deletes the
 * ones that no longer exist in the Azure environment.
 */
public class AzureEnumerationAdapterService extends StatelessService {
    public static final String SELF_LINK = AzureUriPaths.AZURE_ENUMERATION_ADAPTER;
    public static final Integer SERVICES_TO_REGISTER = 2;

    public AzureEnumerationAdapterService() {
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static enum AzureEnumerationStages {
        TRIGGER_ENUMERATION, FINISHED, ERROR
    }

    /**
     * The enumeration service context needed trigger adapters for Azure.
     */
    public static class EnumerationContext {
        public ComputeEnumerateResourceRequest computeEnumerationRequest;
        public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
        public ComputeStateWithDescription parentCompute;
        public AzureEnumerationStages stage;
        public List<Operation> enumerationOperations;
        public Throwable error;
        public Operation azureAdapterOperation;

        public EnumerationContext(ComputeEnumerateResourceRequest request, Operation op) {
            this.computeEnumerationRequest = request;
            this.stage = AzureEnumerationStages.TRIGGER_ENUMERATION;
            this.enumerationOperations = new ArrayList<Operation>();
            this.azureAdapterOperation = op;
        }
    }

    @Override
    public void handleStart(Operation startPost) {
        startHelperServices();
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        setOperationHandlerInvokeTimeStat(op);
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        op.complete();

        EnumerationContext azureEnumerationContext  = new EnumerationContext(
                op.getBody(ComputeEnumerateResourceRequest.class), op);
        AdapterUtils.validateEnumRequest(azureEnumerationContext .computeEnumerationRequest);
        handleEnumerationRequest(azureEnumerationContext );
    }

    /**
     * Starts the related services for the Enumeration Service
     */
    public void startHelperServices() {
        Operation postComputeEnumAdapterService = Operation
                .createPost(this, AzureComputeEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        Operation postStorageEnumAdapterService = Operation
                .createPost(this, AzureStorageEnumerationAdapterService.SELF_LINK)
                .setReferer(this.getUri());

        this.getHost().startService(postComputeEnumAdapterService,
                new AzureComputeEnumerationAdapterService());
        this.getHost().startService(postStorageEnumAdapterService,
                new AzureStorageEnumerationAdapterService());

        AtomicInteger completionCount = new AtomicInteger(0);
        getHost().registerForServiceAvailability((o, e) -> {
            if (e != null) {
                String message = "Failed to start up all the services related to the Azure Enumeration Adapter Service";
                this.logInfo(message);
                throw new IllegalStateException(message);
            }
            if (completionCount.incrementAndGet() == SERVICES_TO_REGISTER) {
                this.logFine("Successfully started up all Azure Enumeration Adapter Services");
            }
        }, AzureComputeEnumerationAdapterService.SELF_LINK,
                AzureStorageEnumerationAdapterService.SELF_LINK);
    }

    /**
     * Creates operations to trigger off adapter services in parallel
     *
     */
    public void handleEnumerationRequest(EnumerationContext context) {
        switch (context.stage) {
        case TRIGGER_ENUMERATION:
            triggerEnumerationAdapters(context, AzureEnumerationStages.FINISHED);
            break;
        case FINISHED:
            setOperationDurationStat(context.azureAdapterOperation);
            AdapterUtils.sendPatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference);
            break;
        case ERROR:
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference, context.error);
            break;
        default:
            logSevere("Unknown Azure enumeration stage %s ", context.stage.toString());
            context.error = new Exception("Unknown Azure enumeration stage");
            AdapterUtils.sendFailurePatchToEnumerationTask(this,
                    context.computeEnumerationRequest.taskReference, context.error);
            break;
        }
    }

    /**
     * Trigger the enumeration adapters
     */
    public void triggerEnumerationAdapters(EnumerationContext context, AzureEnumerationStages next) {
        Operation patchComputeEnumAdapterService = Operation
                .createPatch(this, AzureComputeEnumerationAdapterService.SELF_LINK)
                .setBody(context.computeEnumerationRequest)
                .setReferer(this.getUri());

        Operation patchStorageEnumAdapterService = Operation
                .createPatch(this, AzureStorageEnumerationAdapterService.SELF_LINK)
                .setBody(context.computeEnumerationRequest)
                .setReferer(this.getUri());

        context.enumerationOperations.add(patchComputeEnumAdapterService);
        context.enumerationOperations.add(patchStorageEnumAdapterService);

        if (context.enumerationOperations == null || context.enumerationOperations.size() == 0) {
            logInfo("There are no enumeration tasks to run.");
            context.stage = next;
            handleEnumerationRequest(context);
            return;
        }
        OperationJoin.JoinedCompletionHandler joinCompletion = (ox,
                exc) -> {
            if (exc != null) {
                logSevere("Error triggering Azure enumeration adapters. %s", Utils.toString(exc));
                AdapterUtils.sendFailurePatchToEnumerationTask(this,
                        context.computeEnumerationRequest.taskReference,
                        exc.values().iterator().next());
                return;
            }
            logInfo("Successfully completed Azure enumeration.");
            context.stage = next;
            handleEnumerationRequest(context);
            return;
        };
        OperationJoin joinOp = OperationJoin.create(context.enumerationOperations);
        joinOp.setCompletion(joinCompletion);
        joinOp.sendWith(getHost());
        logInfo("Triggered Azure enumeration adapters");
    }

    /**
     * Method to get the failed consumer to handle the error that was raised.
     * @param ctx The enumeration context
     * @return
     */
    private Consumer<Throwable> getFailureConsumer(EnumerationContext ctx) {
        return (t) -> {
            ctx.error = t;
            ctx.stage = AzureEnumerationStages.ERROR;
            handleEnumerationRequest(ctx);
        };
    }
}
