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

package com.vmware.photon.controller.model.adapters.util.instance;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceStateWithDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Base context class for adapters that handle {@link ComputeInstanceRequest} request. It
 * {@link #populateContext() loads} NIC related states:
 * <ul>
 * <li>{@link NetworkInterfaceStateWithDescription nicStateWithDesc}</li>
 * <li>{@link SubnetState subnetState}</li>
 * <li>{@link NetworkState networkState}</li>
 * <li>network {@link ResourceGroupState resourceGroupState}</li>
 * <li>map of {@link SecurityGroupState securityGroupStates}</li>
 * </ul>
 * in addition to the states loaded by parent {@link BaseAdapterContext}.
 */
public class BaseComputeInstanceContext<T extends BaseComputeInstanceContext<T, S>, S extends BaseComputeInstanceContext.BaseNicContext>
        extends BaseAdapterContext<T> {

    /**
     * The class encapsulates NIC related states (resolved by links related to the ComputeState)
     * used during compute provisioning.
     */
    public static class BaseNicContext {

        /**
         * Resolved from {@code ComputeState.networkInterfaceLinks[i]}.
         */
        public NetworkInterfaceStateWithDescription nicStateWithDesc;

        /**
         * Resolved from {@code NetworkInterfaceStateWithDescription.subnetLink}.
         */
        public SubnetState subnetState;

        /**
         * Resolved from {@code SubnetState.networkLink}.
         */
        public NetworkState networkState;

        /**
         * Resolved from FIRST {@code NetworkState.groupLinks}.
         */
        public ResourceGroupState networkResourceGroupState;

        /**
         * Resolved from {@code NetworkInterfaceStateWithDescription.firewallLinks}.
         */
        public Map<String, SecurityGroupState> securityGroupStates = new LinkedHashMap<>();
    }

    /**
     * Holds allocation data for all VM NICs.
     */
    public final List<S> nics = new ArrayList<>();

    /**
     * The {@link ComputeInstanceRequest request} that is being processed.
     */
    public final ComputeInstanceRequest computeRequest;

    /**
     * Supplier/Factory for creating context specific {@link BaseNicContext} instances.
     */
    protected final Supplier<S> nicContextSupplier;

    public BaseComputeInstanceContext(StatelessService service,
            ComputeInstanceRequest computeRequest,
            Supplier<S> nicContextSupplier) {

        super(service, computeRequest.resourceReference);

        this.computeRequest = computeRequest;
        this.nicContextSupplier = nicContextSupplier;
    }

    @Override
    protected URI getParentAuthRef(T context) {
        if (context.computeRequest.requestType == InstanceRequestType.VALIDATE_CREDENTIALS) {
            return UriUtils.buildUri(
                    context.service.getHost(),
                    context.computeRequest.authCredentialsLink);
        }
        return super.getParentAuthRef(context);
    }

    /**
     * First NIC is considered primary.
     *
     * @return <code>null</code> if there are no NICs
     */
    public S getPrimaryNic() {
        return this.nics.isEmpty() ? null : this.nics.get(0);
    }

    public DeferredResult<T> populateContext() {
        return DeferredResult.completed(self())
                .thenCompose(this::getNicStates)
                .thenCompose(this::getNicSubnetStates)
                .thenCompose(this::getNicNetworkStates)
                .thenCompose(this::getNicNetworkResourceGroupStates)
                .thenCompose(this::getNicSecurityGroupStates);
    }

    /**
     * Get NIC states assigned to the compute state we are provisioning.
     */
    private DeferredResult<T> getNicStates(T context) {
        if (context.child.networkInterfaceLinks == null
                || context.child.networkInterfaceLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.child.networkInterfaceLinks.stream()
                .map(nicStateLink -> {
                    S nicContext = context.nicContextSupplier.get();

                    context.nics.add(nicContext);

                    URI nicStateUri = NetworkInterfaceStateWithDescription
                            .buildUri(UriUtils.buildUri(context.service.getHost(), nicStateLink));

                    Operation op = Operation.createGet(nicStateUri);

                    return context.service
                            .sendWithDeferredResult(op, NetworkInterfaceStateWithDescription.class)
                            .thenAccept(nsWithDesc -> nicContext.nicStateWithDesc = nsWithDesc);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link SubnetState}s the NICs are assigned to.
     */
    private DeferredResult<T> getNicSubnetStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.nicStateWithDesc.subnetLink);

                    return context.service
                            .sendWithDeferredResult(op, SubnetState.class)
                            .thenAccept(subnetState -> nicContext.subnetState = subnetState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC Subnet states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link NetworkState}s containing the {@link SubnetState}s the NICs are assigned to.
     *
     * @see #getNicSubnetStates(BaseComputeInstanceContext)
     */
    protected DeferredResult<T> getNicNetworkStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics.stream()
                .map(nicContext -> {
                    Operation op = Operation.createGet(
                            context.service.getHost(),
                            nicContext.subnetState.networkLink);

                    return context.service
                            .sendWithDeferredResult(op, NetworkState.class)
                            .thenAccept(networkState -> nicContext.networkState = networkState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC Network states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link SecurityGroupState}s assigned to NICs.
     */
    protected DeferredResult<T> getNicSecurityGroupStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<String> securityGroupLinks = context.getPrimaryNic().nicStateWithDesc.firewallLinks;

        if (securityGroupLinks == null || securityGroupLinks.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = securityGroupLinks.stream()
                .map(securityGroupLink -> {
                    Operation op = Operation.createGet(context.service.getHost(), securityGroupLink);
                    return context.service
                            .sendWithDeferredResult(op, SecurityGroupState.class)
                            .thenAccept(securityGroupState -> {
                                // Populate _all_ NICs with _same_ SecurityGroup state.
                                for (BaseNicContext nicCtx : context.nics) {
                                    nicCtx.securityGroupStates.put(securityGroupState.name, securityGroupState);
                                }
                            });
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException("Error getting NIC SecurityGroup states.", exc);
            }
            return context;
        });
    }

    /**
     * Get {@link ResourceGroupState}s of the {@link NetworkState}s the NICs are assigned to.
     */
    protected DeferredResult<T> getNicNetworkResourceGroupStates(T context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics
                .stream()
                .filter(nicCtx -> nicCtx.networkState.groupLinks != null
                        && !nicCtx.networkState.groupLinks.isEmpty())
                .map(nicCtx -> {
                    String rgLink = nicCtx.networkState.groupLinks.iterator().next();

                    // NOTE: Get first RG Link! If there are more than one link log a warning.
                    if (nicCtx.networkState.groupLinks.size() > 1) {
                        context.service.logSevere(
                                "More than one resource group links are assigned to [%s] NIC's Network state. Get: %s",
                                nicCtx.networkState.name, rgLink);
                    }

                    Operation op = Operation.createGet(context.service.getHost(), rgLink);

                    return context.service
                            .sendWithDeferredResult(op, ResourceGroupState.class)
                            .thenAccept(rgState -> nicCtx.networkResourceGroupState = rgState);
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                throw new IllegalStateException(
                        "Error getting ResourceGroup states of NIC Network states.", exc);
            }
            return context;
        });
    }

}