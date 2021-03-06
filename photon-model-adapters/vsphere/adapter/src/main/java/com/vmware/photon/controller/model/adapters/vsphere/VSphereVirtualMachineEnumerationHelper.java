/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.vsphere;

import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.findMatchingDiskState;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.handleVirtualDeviceUpdate;
import static com.vmware.photon.controller.model.adapters.vsphere.ClientUtils.handleVirtualDiskUpdate;
import static com.vmware.photon.controller.model.adapters.vsphere.util.VimNames.TYPE_PORTGROUP;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.vsphere.VSphereIncrementalEnumerationService.InterfaceStateMode;
import com.vmware.photon.controller.model.adapters.vsphere.VsphereResourceCleanerService.ResourceCleanRequest;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.LifecycleState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskStateExpanded;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectUpdate;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.vim25.VirtualCdrom;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardDistributedVirtualPortBackingInfo;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualEthernetCardOpaqueNetworkBackingInfo;
import com.vmware.vim25.VirtualFloppy;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceStateCollectionUpdateRequest;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class VSphereVirtualMachineEnumerationHelper {
    /*
     * A VM must "ferment" for a few minutes before being eligible for enumeration. This is the time
     * between a VM is created and its UUID is recorded back in the ComputeState resource. This way
     * a VM being provisioned by photon-model will not be enumerated mid-flight.
     */
    static final long VM_FERMENTATION_PERIOD_MILLIS = 3 * 60 * 1000;

    /**
     * Builds a query for finding a ComputeState by instanceUuid from vsphere and parent compute
     * link.
     */
    static QueryTask queryForVm(EnumerationProgress ctx, String parentComputeLink,
            String instanceUuid, ManagedObjectReference moref) {
        Builder builder = Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);
        if (null != instanceUuid) {
            builder.addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid);
        } else {
            builder.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                    CustomProperties.MOREF,
                    VimUtils.convertMoRefToString(moref), QueryTask.Query.Occurance.MUST_OCCUR);
        }
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    static ComputeDescription makeDescriptionForVm(VSphereIncrementalEnumerationService service,
            EnumerationProgress enumerationProgress,
            VmOverlay vm) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(res, enumerationProgress.getRequest().endpointLink);
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, service.getHost().nextUUID());
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.powerAdapterReference = enumerationProgress
                .getParent().description.powerAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;

        res.regionId = enumerationProgress.getRegionId();

        res.cpuCount = vm.getNumCpu();
        res.totalMemoryBytes = vm.getMemoryBytes();
        return res;
    }

    private static ComputeState makeVmFromChanges(VmOverlay vm) {
        ComputeState state = new ComputeState();

        // CPU count can be changed
        state.cpuCount = (long) vm.getNumCpu();
        // Memory can be changed
        if (vm.getMemoryBytes() != 0) {
            state.totalMemoryBytes = vm.getMemoryBytes();
            CustomProperties.of(state)
                    .put(CustomProperties.VM_MEMORY_IN_GB,
                            AdapterUtils.convertBytesToGB(vm.getMemoryBytes()));
        }
        // Power state can be changed
        state.powerState = vm.getPowerStateOrNull();
        // Name can be changed
        state.name = vm.getNameOrNull();

        // OS can be changed
        CustomProperties.of(state)
                .put(CustomProperties.VM_SOFTWARE_NAME, vm.getOS())
                .put(ComputeProperties.CUSTOM_OS_TYPE, ClientUtils.getNormalizedOSType(vm));

        return state;
    }

    /**
     * Make a ComputeState from the request and a vm found in vsphere.
     */
    static ComputeState makeVmFromResults(EnumerationProgress enumerationProgress, VmOverlay vm) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        ComputeState state = new ComputeState();
        state.type = ComputeType.VM_GUEST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = request.endpointLink;
        AdapterUtils.addToEndpointLinks(state, request.endpointLink);
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;

        state.instanceAdapterReference = enumerationProgress.getParent()
                .description.instanceAdapterReference;
        state.enumerationAdapterReference = enumerationProgress.getParent()
                .description.enumerationAdapterReference;
        state.powerAdapterReference = enumerationProgress.getParent()
                .description.powerAdapterReference;

        state.regionId = enumerationProgress.getRegionId();
        state.cpuCount = (long) vm.getNumCpu();
        state.totalMemoryBytes = vm.getMemoryBytes();

        state.hostName = vm.getHostName();
        state.powerState = vm.getPowerState();
        state.primaryMAC = vm.getPrimaryMac();
        if (!vm.isTemplate()) {
            state.address = vm.guessPublicIpV4Address();
        }
        state.id = vm.getInstanceUuid();
        state.name = vm.getName();

        String selfLink = enumerationProgress.getHostSystemTracker().getSelfLink(vm.getHost());

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
                .put(CustomProperties.TYPE, VimNames.TYPE_VM)
                .put(CustomProperties.VM_SOFTWARE_NAME, vm.getOS())
                .put(ComputeProperties.CUSTOM_OS_TYPE, ClientUtils.getNormalizedOSType(vm))
                .put(CustomProperties.DATACENTER_SELF_LINK, enumerationProgress.getDcLink())
                .put(CustomProperties.DATACENTER, enumerationProgress.getRegionId())
                .put(CustomProperties.VM_MEMORY_IN_GB,
                        AdapterUtils.convertBytesToGB(vm.getMemoryBytes()))
                .put(ComputeProperties.COMPUTE_HOST_LINK_PROP_NAME, selfLink);

        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(state,
                enumerationProgress.getVcUuid());


        return state;
    }

    static String getPrimaryIPv4Address(VirtualEthernetCard nic, Map<String, List<String>>
            nicMACToIPv4Addresses) {
        if (nicMACToIPv4Addresses == null) {
            return null;
        }

        String macAddress = nic.getMacAddress();
        List<String> ipv4Addresses = nicMACToIPv4Addresses.get(macAddress);
        if (ipv4Addresses != null && ipv4Addresses.size() > 0) {
            return ipv4Addresses.get(0);
        }

        return null;
    }

    /***
     * Query sub networks and networks for distributed port group id and opaque network id
     * @param service
     * @param ctx The context
     * @param fieldKey The field key to query
     * @param fieldValue The field value to query
     * @param type The type
     * @return The query task operation
     */
    static Operation queryByPortGroupIdOrByOpaqueNetworkId(
            VSphereIncrementalEnumerationService service, EnumerationProgress ctx,
            String fieldKey, String fieldValue, Class<? extends ServiceDocument> type) {

        Builder builder = Builder.create()
                .addKindFieldClause(type)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        fieldKey, fieldValue);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .setResultLimit(1)
                .addOption(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT)
                .build();

        return QueryUtils.createQueryTaskOperation(service, queryTask, ServiceTypeCluster
                .INVENTORY_SERVICE);
    }

    /***
     * Create new network interface state
     * @param service
     * @param id The id of the distributed virtual port or opaque network
     * @param networkLink The network link
     * @param subnetworkLink The subnetwork link
     * @return The network interface state
     */
    static NetworkInterfaceState createNewInterfaceState(
            VSphereIncrementalEnumerationService service,
            String id, String networkLink, String
            subnetworkLink, String ipAddress) {
        NetworkInterfaceState iface = new NetworkInterfaceState();
        iface.name = id;
        iface.documentSelfLink = buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                service.getHost().nextUUID());
        iface.networkLink = networkLink;
        iface.subnetLink = subnetworkLink;
        iface.address = ipAddress;
        Operation.createPost(PhotonModelUriUtils.createInventoryUri
                (service.getHost(),
                        NetworkInterfaceService.FACTORY_LINK))
                .setBody(iface)
                .sendWith(service);
        return iface;
    }

    /***
     * Add new interface state to compute network interface links
     * @param service
     * @param enumerationProgress Enumeration progress
     * @param state Compute state
     * @param id The id of distributed virtual port or opaque network
     * @param mode Either using distributed virtual port or opaque network
     * @param docType The document class type
     * @param type The type
     * @param ipAddress The primary ip address of this interface
     */
    static <T> Operation addNewInterfaceState(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            ComputeState state, String id, InterfaceStateMode mode,
            Class<? extends ServiceDocument> docType, Class<T> type, String ipAddress) {

        String fieldKey;
        String fieldValue;

        switch (mode) {
        case INTERFACE_STATE_WITH_DISTRIBUTED_VIRTUAL_PORT: {
            fieldKey = CustomProperties.MOREF;
            fieldValue = TYPE_PORTGROUP + ":" + id;
            break;
        }
        case INTERFACE_STATE_WITH_OPAQUE_NETWORK: {
            fieldKey = NsxProperties.OPAQUE_NET_ID;
            fieldValue = id;
            break;
        }
        default: {
            service.logFine(() -> String.format("invalid mode when creating compute state with "
                    + "network interface links: [%s]", mode));
            return null;
        }
        }

        Operation operation = queryByPortGroupIdOrByOpaqueNetworkId(
                service, enumerationProgress, fieldKey, fieldValue, docType)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.logWarning(() ->
                                String.format(
                                        "Error processing queryByPortGroupIdOrByOpaqueNetworkId for id: [%s],"
                                                +
                                                "error: [%s]", fieldValue, e.toString()));
                        return;
                    }
                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results != null && !task.results.documentLinks.isEmpty()) {
                        T netState = VsphereEnumerationHelper
                                .convertOnlyResultToDocument(task.results, type);
                        NetworkInterfaceState iface = null;
                        if (netState instanceof SubnetState) {
                            SubnetState subnetState = (SubnetState) netState;
                            iface = createNewInterfaceState(service, id, subnetState.networkLink,
                                    subnetState.documentSelfLink, ipAddress);
                        } else if (netState instanceof NetworkState) {
                            NetworkState networkState = (NetworkState) netState;
                            iface = createNewInterfaceState(service, id, null, networkState
                                    .documentSelfLink, ipAddress);
                        }
                        if (iface != null) {
                            state.networkInterfaceLinks.add(iface.documentSelfLink);
                        }
                    } else {
                        service.logFine(
                                () -> String.format("Will not add nic with id: [%s]", fieldValue));
                    }
                });
        return operation;
    }

    static Operation processVirtualDevice(
            VSphereIncrementalEnumerationService service, DiskStateExpanded matchedDs,
            VirtualDevice device, EnumerationProgress enumerationProgress, List<String> diskLinks,
            String vm, ComputeState oldDocument) {
        if (device instanceof VirtualDisk) {
            return handleVirtualDiskUpdate(enumerationProgress.getRequest().endpointLink,
                    enumerationProgress.getTenantLinks(), matchedDs,
                    (VirtualDisk) device, diskLinks, enumerationProgress.getRegionId(), service,
                    vm, enumerationProgress.getDcLink(), enumerationProgress, oldDocument,
                    Collections.emptyMap(), false);
        } else if (device instanceof VirtualCdrom) {
            return handleVirtualDeviceUpdate(enumerationProgress.getRequest().endpointLink,
                    enumerationProgress.getTenantLinks(), matchedDs, DiskService.DiskType.CDROM,
                    device, diskLinks, enumerationProgress.getRegionId(), service, false,
                    enumerationProgress.getDcLink(), false);
        } else if (device instanceof VirtualFloppy) {
            return handleVirtualDeviceUpdate(enumerationProgress.getRequest().endpointLink,
                    enumerationProgress.getTenantLinks(), matchedDs, DiskService.DiskType.FLOPPY,
                    device, diskLinks, enumerationProgress.getRegionId(), service, false,
                    enumerationProgress.getDcLink(), false);
        }
        return null;
    }

    static CompletionHandler trackVm(EnumerationProgress enumerationProgress) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            enumerationProgress.getVmTracker().arrive();
        };
    }

    static CompletionHandler updateVirtualDisksAndTrackVm(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            Map<Long, Operation> operationMap) {
        return (o, e) -> {
            // for each disk created, populate the vm selfLink as a custom property
            for (Operation operation : operationMap.values()) {
                if (VsphereEnumerationHelper.getSelfLinkFromOperation(operation)
                        .startsWith(DiskService.FACTORY_LINK)) {
                    DiskState diskState = operation.getBody(DiskState.class);
                    CustomProperties.of(diskState)
                            .put(CustomProperties.VIRTUAL_MACHINE_LINK,
                                    VsphereEnumerationHelper.getSelfLinkFromOperation(o));
                    Operation.createPatch(service, diskState.documentSelfLink).setBody(diskState)
                            .sendWith(service);
                }
            }
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            enumerationProgress.getVmTracker().arrive();
        };
    }

    static void patchOnComputeState(
            VSphereIncrementalEnumerationService service, ComputeState newDocument,
            ComputeState oldDocument, EnumerationProgress enumerationProgress, VmOverlay vm) {
        Operation.createPatch(
                PhotonModelUriUtils
                        .createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                .setBody(newDocument)
                .setCompletion((o, e) -> {
                    trackVm(enumerationProgress).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(service, ()
                                -> VsphereEnumerationHelper
                                .updateLocalTags(service, enumerationProgress, vm,
                                        o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(service);
    }

    static void updateVm(VSphereIncrementalEnumerationService service,
            ComputeState oldDocument, EnumerationProgress enumerationProgress,
            VmOverlay vm, boolean fullUpdate) {
        ComputeState state;
        if (fullUpdate) {
            state = makeVmFromResults(enumerationProgress, vm);
        } else {
            state = makeVmFromChanges(vm);
        }
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;
        state.lifecycleState = LifecycleState.READY;
        state.networkInterfaceLinks = oldDocument.networkInterfaceLinks;
        state.diskLinks = oldDocument.diskLinks;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing VM %s", state.documentSelfLink));
        if (CollectionUtils.isNotEmpty(state.diskLinks) && CollectionUtils.isNotEmpty(vm.getDisks())) {
            // Now check how many disks are added / deleted / needs to be updated.
            List<Operation> ops = state.diskLinks.stream()
                    .map(link -> {
                        URI diskStateUri = UriUtils.buildUri(service.getHost(), link);
                        return Operation.createGet(createInventoryUri(service.getHost(),
                                DiskService.DiskStateExpanded.buildUri(diskStateUri)));
                    })
                    .collect(Collectors.toList());

            OperationJoin.create(ops).setCompletion((operations, failures) -> {
                if (failures != null) {
                    service.logFine(() -> String.format("Error in sync disks of VM %s", state
                            .documentSelfLink));
                    patchOnComputeState(service, state, oldDocument, enumerationProgress, vm);
                } else {
                    List<DiskStateExpanded> currentDisks = operations.values().stream()
                            .map(op -> op.getBody(DiskStateExpanded.class))
                            .collect(Collectors.toList());
                    List<Operation> diskUpdateOps = new ArrayList<>(currentDisks.size());

                    // Select all disks to delete
                    List<String> disksToDelete = new ArrayList<>(state.diskLinks);

                    // Process the update of disks and then patch the compute
                    for (VirtualDevice device : vm.getDisks()) {
                        DiskStateExpanded matchedDs = findMatchingDiskState(device,
                                currentDisks);

                        // remove the active disk links
                        if (null != matchedDs) {
                            disksToDelete.remove(matchedDs.documentSelfLink);
                        }

                        Operation vdOp = processVirtualDevice(service, matchedDs, device,
                                enumerationProgress, state.diskLinks,
                                VimUtils.convertMoRefToString(vm.getId()), oldDocument);
                        if (vdOp != null) {
                            diskUpdateOps.add(vdOp);
                        }
                    }

                    //  Delete disk states whose disks are deleted in vsphere.
                    for (String diskToDelete : disksToDelete) {
                        state.diskLinks.remove(diskToDelete);
                        diskUpdateOps.add(Operation.createDelete(PhotonModelUriUtils
                                .createInventoryUri(service.getHost(), diskToDelete)));
                    }

                    // when a host moves inside cluster, vm receive updates only on resourcelinks.
                    // in such case, diskUpdateOps is empty.
                    if (CollectionUtils.isNotEmpty(diskUpdateOps)) {
                        OperationJoin.create(diskUpdateOps).setCompletion((operationMap, exception) -> {
                            //  Delete disk states whose disks are deleted in vsphere.
                            if (CollectionUtils.isNotEmpty(disksToDelete)) {
                                Map<String, Collection<Object>> collectionToRemove = Collections
                                        .singletonMap(ComputeState.FIELD_NAME_DISK_LINKS,
                                                new ArrayList<>(disksToDelete));
                                ServiceStateCollectionUpdateRequest updateDiskLinksRequest = ServiceStateCollectionUpdateRequest
                                        .create(null, collectionToRemove);
                                Operation.createPatch(PhotonModelUriUtils
                                        .createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                                        .setBody(updateDiskLinksRequest).setCompletion((o, e) -> {
                                            if (null != e) {
                                                service.logSevere("Error while removing disks %s for vm %s", Utils.toJson(disksToDelete), oldDocument.documentSelfLink);
                                            }
                                        }).sendWith(service);
                            }

                            updateComputeStateWithProvisionGB(state, operationMap);
                            patchOnComputeState(service, state, oldDocument, enumerationProgress, vm);
                        }).sendWith(service);
                    }
                }
            }).sendWith(service);
        } else {
            patchOnComputeState(service, state, oldDocument, enumerationProgress, vm);
        }
    }

    static void createNewVm(VSphereIncrementalEnumerationService service,
            EnumerationProgress enumerationProgress, VmOverlay vm) {

        ComputeDescription desc = makeDescriptionForVm(service, enumerationProgress, vm);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(service.getHost(),
                        ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(service);

        ComputeState state = makeVmFromResults(enumerationProgress, vm);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationProgress.getTenantLinks();

        List<Operation> operations = new ArrayList<>();

        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, vm, state);
            state.networkInterfaceLinks = new ArrayList<>();
            Map<String, List<String>> nicToIPv4Addresses = vm.getMapNic2IpV4Addresses();
            for (VirtualEthernetCard nic : vm.getNics()) {
                VirtualDeviceBackingInfo backing = nic.getBacking();
                if (backing instanceof VirtualEthernetCardNetworkBackingInfo) {
                    VirtualEthernetCardNetworkBackingInfo veth = (VirtualEthernetCardNetworkBackingInfo) backing;
                    NetworkInterfaceState iface = new NetworkInterfaceState();
                    iface.networkLink = enumerationProgress.getNetworkTracker()
                            .getSelfLink(veth.getNetwork());
                    iface.name = nic.getDeviceInfo().getLabel();
                    iface.documentSelfLink = buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                            service.getHost().nextUUID());
                    iface.address = getPrimaryIPv4Address(nic, nicToIPv4Addresses);
                    CustomProperties.of(iface)
                            .put(CustomProperties.DATACENTER_SELF_LINK,
                                    enumerationProgress.getDcLink());
                    Operation.createPost(PhotonModelUriUtils.createInventoryUri(service.getHost(),
                            NetworkInterfaceService.FACTORY_LINK))
                            .setBody(iface)
                            .sendWith(service);
                    state.networkInterfaceLinks.add(iface.documentSelfLink);
                } else if (backing instanceof VirtualEthernetCardDistributedVirtualPortBackingInfo) {
                    VirtualEthernetCardDistributedVirtualPortBackingInfo veth =
                            (VirtualEthernetCardDistributedVirtualPortBackingInfo) backing;
                    String portgroupKey = veth.getPort().getPortgroupKey();
                    Operation op = addNewInterfaceState(service, enumerationProgress, state,
                            portgroupKey,
                            InterfaceStateMode.INTERFACE_STATE_WITH_DISTRIBUTED_VIRTUAL_PORT,
                            SubnetState.class, SubnetState.class, getPrimaryIPv4Address(nic,
                                    nicToIPv4Addresses));
                    if (op != null) {
                        operations.add(op);
                    }
                } else if (backing instanceof VirtualEthernetCardOpaqueNetworkBackingInfo) {
                    VirtualEthernetCardOpaqueNetworkBackingInfo veth =
                            (VirtualEthernetCardOpaqueNetworkBackingInfo) backing;
                    String opaqueNetworkId = veth.getOpaqueNetworkId();
                    Operation op = addNewInterfaceState(service, enumerationProgress, state,
                            opaqueNetworkId,
                            InterfaceStateMode.INTERFACE_STATE_WITH_OPAQUE_NETWORK,
                            NetworkState.class, NetworkState.class, getPrimaryIPv4Address(nic,
                                    nicToIPv4Addresses));
                    if (op != null) {
                        operations.add(op);
                    }
                } else {
                    // TODO add support for DVS
                    service.logFine(() -> String.format("Will not add nic of type %s",
                            backing.getClass().getName()));
                }
            }

            // Process all the disks attached to the VM
            List<VirtualDevice> disks = vm.getDisks();
            if (CollectionUtils.isNotEmpty(disks)) {
                state.diskLinks = new ArrayList<>(disks.size());
                for (VirtualDevice device : disks) {
                    Operation vdOp = processVirtualDevice(service, null, device,
                            enumerationProgress, state
                                    .diskLinks, VimUtils.convertMoRefToString(vm.getId()), null);
                    if (vdOp != null) {
                        operations.add(vdOp);
                    }
                }
            }

            service.logFine(() -> String.format("Found new VM %s", vm.getInstanceUuid()));
            if (operations.isEmpty()) {
                Operation.createPost(PhotonModelUriUtils
                        .createInventoryUri(service.getHost(), ComputeService.FACTORY_LINK))
                        .setBody(state)
                        .setCompletion(trackVm(enumerationProgress))
                        .sendWith(service);
            } else {
                OperationJoin.create(operations).setCompletion((operationMap, exception) -> {
                    updateComputeStateWithProvisionGB(state, operationMap);
                    Operation.createPost(PhotonModelUriUtils
                            .createInventoryUri(service.getHost(), ComputeService.FACTORY_LINK))
                            .setBody(state)
                            .setCompletion(
                                    updateVirtualDisksAndTrackVm(service, enumerationProgress,
                                            operationMap))
                            .sendWith(service);
                }).sendWith(service);
            }
        });
    }

    private static void updateComputeStateWithProvisionGB(ComputeState state,
            Map<Long, Operation> operationMap) {
        long totalProvisionGB = 0;
        for (Operation diskOperation : operationMap.values()) {
            if (!Service.Action.DELETE.equals(diskOperation.getAction())) {
                DiskState diskState = diskOperation.getBody(DiskState.class);
                long diskProvisionGB = CustomProperties.of(diskState)
                        .getLong(CustomProperties.DISK_PROVISION_IN_GB, 0L);
                totalProvisionGB += diskProvisionGB;
            }
        }
        CustomProperties.of(state)
                .put(CustomProperties.DISK_PROVISION_IN_GB, totalProvisionGB);
    }

    static void processFoundVm(VSphereIncrementalEnumerationService service,
            EnumerationProgress enumerationProgress, VmOverlay vm) {
        long latestAcceptableModification = System.currentTimeMillis()
                - VM_FERMENTATION_PERIOD_MILLIS;
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForVm(enumerationProgress, request.resourceLink(),
                vm.getInstanceUuid(), vm.getId());
        VsphereEnumerationHelper.withTaskResults(service, task, enumerationProgress.getVmTracker(), result -> {
            try {
                if (result.documentLinks.isEmpty()) {
                    if (vm.getLastReconfigureMillis() < latestAcceptableModification) {
                        // If vm is not settled down don't create a new resource
                        createNewVm(service, enumerationProgress, vm);
                    } else {
                        enumerationProgress.getVmTracker().arrive();
                    }
                } else {
                    // always update state of a vm even if modified recently
                    ComputeState oldDocument = VsphereEnumerationHelper
                            .convertOnlyResultToDocument(result, ComputeState.class);
                    updateVm(service, oldDocument, enumerationProgress, vm, true);
                }
            } catch (Exception e) {
                service.logSevere("Error occurred while processing virtual machine: %s", Utils.toString(e));
                enumerationProgress.getVmTracker().arrive();
            }
        }, 1);
    }

    static DeferredResult<SnapshotState> createSnapshot(
            VSphereIncrementalEnumerationService service,
            SnapshotState snapshot) {
        service.logFine(() -> String.format("Creating new snapshot %s", snapshot.name));
        Operation opCreateSnapshot = Operation.createPost(service, SnapshotService.FACTORY_LINK)
                .setBody(snapshot);

        return service.sendWithDeferredResult(opCreateSnapshot, SnapshotState.class);
    }

    public static void handleVMChanges(
            VSphereIncrementalEnumerationService service, List<ObjectUpdate> resourcesUpdates,
            EnumerationProgress enumerationProgress, EnumerationClient client) {

        List<VmOverlay> vmOverlays = new ArrayList<>();
        for (ObjectUpdate objectUpdate : resourcesUpdates) {
            if (VimUtils.isVirtualMachine(objectUpdate.getObj())) {
                VmOverlay vm = new VmOverlay(objectUpdate);
                if (vm.isTemplate()) {
                    // templates are skipped, enumerated as "images" instead
                    continue;
                }
                if (vm.getInstanceUuid() != null || !objectUpdate.getKind().equals(ObjectUpdateKind.ENTER)) {
                    vmOverlays.add(vm);
                }
            }
        }
        for (VmOverlay vmOverlay : vmOverlays) {
            ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
            QueryTask task = queryForVm(enumerationProgress, request.resourceLink(), null,
                    vmOverlay.getId());
            enumerationProgress.getVmTracker().register();
            VsphereEnumerationHelper.withTaskResults(service, task, enumerationProgress.getVmTracker(), result -> {
                try {
                    if (result.documentLinks.isEmpty()
                            && ObjectUpdateKind.ENTER == vmOverlay.getObjectUpdateKind()) {
                        createNewVm(service, enumerationProgress, vmOverlay);
                    } else {
                        ComputeState oldDocument = VsphereEnumerationHelper
                                .convertOnlyResultToDocument(result, ComputeState.class);
                        if (ObjectUpdateKind.MODIFY == vmOverlay.getObjectUpdateKind()
                                || ObjectUpdateKind.ENTER == vmOverlay.getObjectUpdateKind()) {
                            updateVm(service, oldDocument, enumerationProgress, vmOverlay, false);
                        } else if (ObjectUpdateKind.LEAVE == vmOverlay.getObjectUpdateKind()) {
                            deleteVM(enumerationProgress, vmOverlay, service, oldDocument);
                        }
                    }
                } catch (Exception e) {
                    service.logSevere("Error occurred while processing virtual machine: %s", Utils.toString(e));
                    enumerationProgress.getVmTracker().arrive();
                }
            }, 1);
        }
        enumerationProgress.getVmTracker().arriveAndAwaitAdvance();
        // enumerate snapshots
        VSphereVMSnapshotEnumerationHelper.enumerateSnapshots(service, enumerationProgress, vmOverlays);
    }

    private static void deleteVM(EnumerationProgress enumerationProgress, VmOverlay vmOverlay,
            VSphereIncrementalEnumerationService service, ServiceDocument oldDocument) {
        if (!enumerationProgress.getRequest().preserveMissing) {
            Operation.createDelete(
                    PhotonModelUriUtils
                            .createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                    .setCompletion((o, e) -> {
                        trackVm(enumerationProgress).handle(o, e);
                    })
                    .sendWith(service);
        } else {
            ResourceCleanRequest patch = new ResourceCleanRequest();
            patch.resourceLink = oldDocument.documentSelfLink;
            Operation.createPatch(service, VSphereUriPaths.RESOURCE_CLEANER)
                    .setBody(patch)
                    .setCompletion((o, e) -> {
                        trackVm(enumerationProgress).handle(o, e);
                    }).sendWith(service);
        }
    }
}
