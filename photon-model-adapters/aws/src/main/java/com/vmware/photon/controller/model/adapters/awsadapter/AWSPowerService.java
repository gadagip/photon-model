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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.resources.ComputeService.PowerState.OFF;

import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;

import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.DefaultAdapterContext;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.StatelessService;

/**
 * Adapter to manage power state of an instance.
 */
public class AWSPowerService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_POWER_ADAPTER;

    private AWSClientManager clientManager;

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    @Override
    public void handleStop(Operation op) {
        AWSClientManagerFactory.returnClientManager(this.clientManager,
                AWSConstants.AwsClientType.EC2);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ComputePowerRequest pr = op.getBody(ComputePowerRequest.class);
        op.complete();
        if (pr.isMockRequest) {
            updateComputeState(pr, new DefaultAdapterContext(this, pr));
        } else {
            new DefaultAdapterContext(this, pr)
                    .populateBaseContext(BaseAdapterStage.VMDESC)
                    .whenComplete((c, e) -> {
                        this.clientManager.getOrCreateEC2ClientAsync(c.endpointAuth,
                                c.child.description.regionId, this)
                                .whenComplete((client, t) -> {
                                    if (t != null) {
                                        c.taskManager.patchTaskToFailure(t);
                                        return;
                                    }

                                    applyPowerOperation(client, pr, c);
                                });
                    });
        }

    }

    private void applyPowerOperation(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        switch (pr.powerState) {
        case OFF:
            powerOff(client, pr, c);
            break;
        case ON:
            powerOn(client, pr, c);
            break;
        case SUSPEND:
            // TODO: Not supported yet, so simply patch the state with requested power state.
            updateComputeState(pr, c);
            break;
        case UNKNOWN:
        default:
            c.taskManager.patchTaskToFailure(
                    new IllegalArgumentException("Unsupported power state transition requested."));
        }

    }

    private void powerOn(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        OperationContext opContext = OperationContext.getOperationContext();

        StartInstancesRequest request = new StartInstancesRequest();
        request.withInstanceIds(c.child.id);
        client.startInstancesAsync(request,
                new AsyncHandler<StartInstancesRequest, StartInstancesResult>() {
                    @Override
                    public void onSuccess(StartInstancesRequest request,
                            StartInstancesResult result) {
                        AWSUtils.waitForTransitionCompletion(getHost(),
                                result.getStartingInstances(), "running", client, (is, e) -> {
                                    OperationContext.restoreOperationContext(opContext);
                                    if (e != null) {
                                        onError(e);
                                        return;
                                    }
                                    updateComputeState(pr, c);
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        OperationContext.restoreOperationContext(opContext);
                        c.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    private void powerOff(AmazonEC2AsyncClient client, ComputePowerRequest pr,
            DefaultAdapterContext c) {
        OperationContext opContext = OperationContext.getOperationContext();

        StopInstancesRequest request = new StopInstancesRequest();
        request.withInstanceIds(c.child.id);
        client.stopInstancesAsync(request,
                new AsyncHandler<StopInstancesRequest, StopInstancesResult>() {
                    @Override
                    public void onSuccess(StopInstancesRequest request,
                            StopInstancesResult result) {
                        AWSUtils.waitForTransitionCompletion(getHost(),
                                result.getStoppingInstances(), "stopped", client, (is, e) -> {
                                    OperationContext.restoreOperationContext(opContext);
                                    if (e != null) {
                                        onError(e);
                                        return;
                                    }

                                    updateComputeState(pr, c);
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        OperationContext.restoreOperationContext(opContext);
                        c.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    private void updateComputeState(ComputePowerRequest pr, DefaultAdapterContext c) {
        ComputeState state = new ComputeState();
        state.powerState = pr.powerState;
        if (OFF.equals(pr.powerState)) {
            state.address = ""; //clear IP address in case of power-off
        }
        Operation.createPatch(pr.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        c.taskManager.patchTaskToFailure(e);
                        return;
                    }
                    c.taskManager.finishTask();
                })
                .sendWith(this);
    }

}
