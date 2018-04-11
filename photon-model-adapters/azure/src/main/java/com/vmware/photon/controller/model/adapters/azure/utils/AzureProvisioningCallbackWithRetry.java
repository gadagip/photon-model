/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.azure.utils;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.PROVISIONING_STATE_SUCCEEDED;

import java.util.concurrent.TimeUnit;

import com.microsoft.rest.ServiceCallback;

import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils.RetryStrategy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.StatelessService;

/**
 * Use this Azure callback in case of Azure provisioning call (such as create vNet, NIC, etc.) with
 * a method signature similar to:
 * {@code ServiceCall createOrUpdateAsync(..., ServiceCallback<RES> callback)}
 * <p>
 * The provisioning state of resources returned by Azure 'create' call is 'Updating'. This callback
 * is responsible to wait for resource provisioning state to change to 'Succeeded'.
 */
public abstract class AzureProvisioningCallbackWithRetry<RES>
        extends AzureDeferredResultServiceCallbackWithRetry<RES> {

    private static final String MSG = "WAIT provisioning to succeed";

    /**
     * Create a new {@link AzureProvisioningCallbackWithRetry}.
     */
    public AzureProvisioningCallbackWithRetry(StatelessService service, String message) {

        super(service, message);
    }

    @Override
    protected final DeferredResult<RES> consumeSuccess(RES body) {
        return waitProvisioningToSucceed(body).thenCompose(this::consumeProvisioningSuccess);
    }

    /**
     * Hook to be implemented by descendants to handle 'Succeeded' Azure resource provisioning.
     * Since implementations might decide to trigger/initiate sync operation they are required to
     * return {@link DeferredResult} to track its completion.
     * <p>
     * This call is introduced by analogy with {@link #consumeSuccess(Object)}.
     */
    protected abstract DeferredResult<RES> consumeProvisioningSuccess(RES body);

    /**
     * By design Azure resources do not have generic 'provisioningState' getter, so we enforce
     * descendants to provide us with its value.
     * <p>
     * NOTE: Might be done through reflection. For now keep it simple.
     */
    protected abstract String getProvisioningState(RES body);

    /**
     * This Runnable abstracts/models the Azure 'get resource' call used to get/check resource
     * provisioning state.
     *
     * @param checkProvisioningStateCallback
     *            The special callback that should be used while creating the Azure 'get resource'
     *            call.
     */
    protected abstract Runnable checkProvisioningStateCall(
            ServiceCallback<RES> checkProvisioningStateCallback);

    /**
     * The core logic that waits for provisioning to succeed. It polls periodically for resource
     * provisioning state.
     */
    private DeferredResult<RES> waitProvisioningToSucceed(RES body) {

        String provisioningState = getProvisioningState(body);

        this.service.logFine(this.message + ": provisioningState = " + provisioningState);

        long nextDelayMillis = this.retryStrategy.nextDelayMillis();

        if (PROVISIONING_STATE_SUCCEEDED.equalsIgnoreCase(provisioningState)) {

            // Resource 'provisioningState' has changed finally to 'Succeeded'
            // Completes 'waitProvisioningToSucceed' task with success
            this.waitRetryDr.complete(body);

        } else if (nextDelayMillis == RetryStrategy.EXHAUSTED) {

            // Max number of re-tries has reached.
            // Completes 'waitProvisioningToSucceed' task with exception.
            this.waitRetryDr.fail(new IllegalStateException(
                    MSG + ": max wait time ("
                            + TimeUnit.MILLISECONDS.toSeconds(this.retryStrategy.maxWaitMillis)
                            + " secs) exceeded"));
        } else {
            // Retry one more time
            this.service.getHost().schedule(
                    checkProvisioningStateCall(new RetryServiceCallback()),
                    nextDelayMillis,
                    TimeUnit.MILLISECONDS);
        }

        return this.waitRetryDr;
    }

}