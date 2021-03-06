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

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_PROP_STORAGE_SHARED;
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.convertOnlyResultToDocument;
import static com.vmware.photon.controller.model.adapters.vsphere.VsphereEnumerationHelper.withTaskResults;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_AVAILABLE_BYTES;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ObjectUpdateKind;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class VsphereDatastoreEnumerationHelper {

    public static QueryTask queryForStorageWithDSMoref(
            EnumerationProgress ctx, List<String> morefId) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(StorageDescriptionService.StorageDescription.class)
                .addFieldClause(StorageDescriptionService.StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        ctx.getRequest().adapterManagementReference.toString());

        QueryTask.Query.Builder morefBuilder = QueryTask.Query.Builder.create();
        for (String morefIdValue: morefId) {
            morefBuilder.addCompositeFieldClause(
                    StorageDescriptionService.StorageDescription.FIELD_NAME_CUSTOM_PROPERTIES,
                    CustomProperties.MOREF, morefIdValue, QueryTask.Query.Occurance.SHOULD_OCCUR);
        }
        builder.addClause(morefBuilder.build());

        QueryUtils.addEndpointLink(builder, StorageDescriptionService.StorageDescription.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    public static QueryTask queryForStorage(
            EnumerationProgress ctx, String name, String morefId, String groupLink) {
        return queryForStorage(ctx, name, morefId, ctx.getRegionId(), groupLink);
    }

    public static QueryTask queryForStorage(
            EnumerationProgress ctx, String name, String morefId, String regionId, String groupLink) {
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(StorageDescriptionService.StorageDescription.class)
                .addFieldClause(StorageDescriptionService.StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        ctx.getRequest().adapterManagementReference.toString());

        if (null != regionId) {
            builder.addFieldClause(
                    StorageDescriptionService.StorageDescription.FIELD_NAME_REGION_ID, ctx.getRegionId());
        }

        if (name != null) {
            builder.addCaseInsensitiveFieldClause(
                    StorageDescriptionService.StorageDescription.FIELD_NAME_NAME, name,
                    QueryTask.QueryTerm.MatchType.TERM, QueryTask.Query.Occurance.MUST_OCCUR);
        }
        if (morefId != null) {
            builder.addCompositeFieldClause(
                    StorageDescriptionService.StorageDescription.FIELD_NAME_CUSTOM_PROPERTIES,
                    CustomProperties.MOREF, morefId);
        }
        if (groupLink != null) {
            builder.addCollectionItemClause(ResourceState.FIELD_NAME_GROUP_LINKS, groupLink);
        }
        QueryUtils.addEndpointLink(builder, StorageDescriptionService.StorageDescription.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private static StorageDescription makeStorageFromResults(ComputeEnumerateResourceRequest request,
                                                             DatastoreOverlay ds, String regionId,
                                                             EnumerationProgress ctx) {
        StorageDescription res = new StorageDescription();
        res.id = res.name = ds.getName();
        res.type = ds.getType();
        res.resourcePoolLink = request.resourcePoolLink;
        res.endpointLink = request.endpointLink;
        AdapterUtils.addToEndpointLinks(res, request.endpointLink);
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        res.regionId = regionId;
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(STORAGE_USED_BYTES, ds.getCapacityBytes() - ds.getFreeSpaceBytes())
                .put(STORAGE_AVAILABLE_BYTES, ds.getFreeSpaceBytes())
                .put(CUSTOM_PROP_STORAGE_SHARED, ds.isMultipleHostAccess())
                .put(CustomProperties.TYPE, ds.getType())
                .put(CustomProperties.DS_PATH, ds.getPath())
                .put(CustomProperties.DATACENTER_SELF_LINK, ctx.getDcLink())
                .put(CustomProperties.PROPERTY_NAME, ds.getName())
                .put(CustomProperties.DS_FREE_SPACE_IN_GB, AdapterUtils.convertBytesToGB(ds.getFreeSpaceBytes()));
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(res, ctx.getVcUuid());
        return res;
    }

    private static CompletionHandler trackDatastore(EnumerationProgress enumerationProgress,
                                                    DatastoreOverlay ds) {
        return (o, e) -> {
            enumerationProgress.touchResource(VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getDatastoreTracker().track(ds.getId(),
                        VsphereEnumerationHelper.getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getDatastoreTracker().track(ds.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private static ResourceGroupState makeStorageGroup(DatastoreOverlay ds, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = ds.getName();
        res.name = "Hosts that can access datastore '" + ds.getName() + "'";
        res.endpointLink = ctx.getRequest().endpointLink;
        AdapterUtils.addToEndpointLinks(res, ctx.getRequest().endpointLink);
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getDatastoreTracker().getSelfLink(ds.getId
                        ()));
        res.documentSelfLink = VsphereEnumerationHelper.computeGroupStableLink(ds.getId(),
                VSphereIncrementalEnumerationService.PREFIX_DATASTORE, ctx.getRequest()
                        .endpointLink);
        VsphereEnumerationHelper.populateResourceStateWithAdditionalProps(res, ctx.getVcUuid());
        return res;
    }

    private static void updateStorageStats(VSphereIncrementalEnumerationService service, DatastoreOverlay ds,
                                           String selfLink) {
        // update stats only if capacity is greater than zero
        if (ds.getCapacityBytesOrZero() > 0) {
            ResourceMetrics metrics = new ResourceMetrics();
            metrics.timestampMicrosUtc = Utils.getNowMicrosUtc();
            metrics.documentSelfLink = StatsUtil.getMetricKey(selfLink, metrics.timestampMicrosUtc);
            metrics.entries = new HashMap<>();
            metrics.entries.put(STORAGE_USED_BYTES, (double) ds.getCapacityBytes() - ds
                    .getFreeSpaceBytes());
            metrics.entries.put(STORAGE_AVAILABLE_BYTES, (double) ds.getFreeSpaceBytes());
            metrics.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(
                    SingleResourceStatsCollectionTaskService.EXPIRATION_INTERVAL);

            metrics.customProperties = new HashMap<>();
            metrics.customProperties
                    .put(ResourceMetrics.PROPERTY_RESOURCE_LINK, selfLink);

            Operation.createPost(UriUtils.buildUri(
                    ClusterUtil.getClusterUri(service.getHost(),
                            ServiceTypeCluster.METRIC_SERVICE),
                    ResourceMetricsService.FACTORY_LINK))
                    .setBodyNoCloning(metrics)
                    .sendWith(service);
        }
    }

    private static void createNewStorageDescription(VSphereIncrementalEnumerationService service,
                                                    EnumerationProgress enumerationProgress,
                                                    DatastoreOverlay ds) {

        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();
        StorageDescription desc = makeStorageFromResults(request, ds, regionId, enumerationProgress);
        desc.tenantLinks = enumerationProgress.getTenantLinks();

        // if the data store is associated with storage policy, then populate it.
        if (null != enumerationProgress.getDataStoresToStoragePolicyMap().get(ds.getId().getValue())) {
            desc.groupLinks = new HashSet(enumerationProgress.getDataStoresToStoragePolicyMap().get(ds.getId().getValue()));
        }

        service.logFine(() -> String.format("Found new Datastore %s", ds.getName()));

        VsphereEnumerationHelper.submitWorkToVSpherePool(service, () -> {
            VsphereEnumerationHelper.populateTags(service, enumerationProgress, ds, desc);

            Operation.createPost(
                    PhotonModelUriUtils.createInventoryUri(service.getHost(),
                            StorageDescriptionService.FACTORY_LINK))
                    .setBody(desc)
                    .setCompletion((o, e) -> {
                        trackDatastore(enumerationProgress, ds).handle(o, e);
                        Operation.createPost(PhotonModelUriUtils.createInventoryUri(service
                                        .getHost(),
                                ResourceGroupService.FACTORY_LINK))
                                .setBody(makeStorageGroup(ds, enumerationProgress))
                                .sendWith(service);
                        updateStorageStats(service, ds, o.getBody(ServiceDocument.class)
                                .documentSelfLink);
                    })
                    .sendWith(service);
        });
    }

    private static void updateStorageDescription(VSphereIncrementalEnumerationService service,
                                                 StorageDescription oldDocument,
                                                 EnumerationProgress enumerationProgress,
                                                 DatastoreOverlay ds, boolean fullUpdate) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();
        StorageDescription desc;
        if (fullUpdate) {
            desc = makeStorageFromResults(request, ds, regionId, enumerationProgress);
        } else {
            desc = makeStorageFromChanges(ds, oldDocument);
        }

        desc.documentSelfLink = oldDocument.documentSelfLink;
        desc.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = enumerationProgress.getTenantLinks();
        }

        service.logFine(() -> String.format("Syncing Storage %s", ds
                .getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri
                (service.getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion((o, e) -> {
                    trackDatastore(enumerationProgress, ds).handle(o, e);
                    if (e == null) {
                        VsphereEnumerationHelper.submitWorkToVSpherePool(
                                service, () -> {
                                    VsphereEnumerationHelper.updateLocalTags(
                                            service, enumerationProgress,
                                            ds, o.getBody(ResourceState.class));
                                    updateStorageStats(service, ds, o
                                            .getBody(ServiceDocument.class).documentSelfLink);
                                });
                    }
                }).sendWith(service);
    }

    private static StorageDescription makeStorageFromChanges(
            DatastoreOverlay datastore, StorageDescription oldDocument) {
        StorageDescriptionService.StorageDescription storageDescription = new StorageDescriptionService.StorageDescription();

        storageDescription.name = datastore.getNameOrNull();
        storageDescription.id = datastore.getNameOrNull();

        // if capacity changes, update both used bytes and available bytes from change.
        // if free space changes, update used bytes from old capacity.
        if (datastore.getCapacityBytesOrZero() > 0L) {
            storageDescription.capacityBytes = datastore.getCapacityBytesOrZero();
            CustomProperties.of(storageDescription)
                    .put(STORAGE_USED_BYTES, datastore.getCapacityBytes() - datastore.getFreeSpaceBytes())
                    .put(STORAGE_AVAILABLE_BYTES, datastore.getFreeSpaceBytes())
                    .put(CustomProperties.DS_FREE_SPACE_IN_GB, AdapterUtils.convertBytesToGB(datastore.getFreeSpaceBytes()));
        } else if (datastore.getFreeSpaceBytes() > 0L) {
            CustomProperties.of(storageDescription)
                    .put(STORAGE_USED_BYTES, oldDocument.capacityBytes - datastore.getFreeSpaceBytes())
                    .put(STORAGE_AVAILABLE_BYTES, datastore.getFreeSpaceBytes())
                    .put(CustomProperties.DS_FREE_SPACE_IN_GB, AdapterUtils.convertBytesToGB(datastore.getFreeSpaceBytes()));
        }
        if (null != datastore.getPath()) {
            CustomProperties.of(storageDescription)
                    .put(CustomProperties.DS_PATH, datastore.getPath());
        }

        return storageDescription;
    }

    public static void processFoundDatastore(VSphereIncrementalEnumerationService service,
                                             EnumerationProgress enumerationProgress, DatastoreOverlay ds) {
        QueryTask task = queryForStorage(
                enumerationProgress, ds.getName(), VimUtils.convertMoRefToString(ds.getId()), null);

        withTaskResults(service, task,
                enumerationProgress.getDatastoreTracker(), result -> {
                    try {
                        if (result.documentLinks.isEmpty()) {
                            createNewStorageDescription(service,
                                    enumerationProgress, ds);
                        } else {
                            StorageDescription oldDocument =
                                    convertOnlyResultToDocument(result,
                                            StorageDescription.class);
                            updateStorageDescription(service, oldDocument,
                                    enumerationProgress, ds, true);
                        }
                    } catch (Exception e) {
                        service.logSevere("Error while processing datastore: %s", Utils.toString(e));
                        enumerationProgress.getDatastoreTracker().track(ds.getId(), ResourceTracker.ERROR);
                    }
                });
    }

    /**
     * Handles changes to datastores. Changes to name, capacity and free space are handled.
     * new datastore addition and existing datastore deletion is handled.
     *
     * @param service             the vsphere incremental service
     * @param datastores          the list of datastore overlays
     * @param enumerationProgress the context of enumeration
     */
    public static void handleDatastoreChanges(VSphereIncrementalEnumerationService service,
                                              List<DatastoreOverlay> datastores,
                                              EnumerationProgress enumerationProgress) {

        enumerationProgress.expectDatastoreCount(datastores.size());

        for (DatastoreOverlay datastore : datastores) {

            // check if its a new datastore
            if (ObjectUpdateKind.ENTER == datastore.getObjectUpdateKind()) {
                createNewStorageDescription(service, enumerationProgress, datastore);
            } else {
                // "name" may not necessarily present in the object update. so passed as "null"
                QueryTask task = queryForStorage(enumerationProgress, null, VimUtils.convertMoRefToString(datastore.getId()), null);
                withTaskResults(service, task, enumerationProgress.getDatastoreTracker(), result -> {
                    try {
                        if (!result.documentLinks.isEmpty()) {
                            // Object is either modified or deleted
                            StorageDescriptionService.StorageDescription oldDocument = convertOnlyResultToDocument(
                                    result, StorageDescriptionService.StorageDescription.class);
                            // if the data store is modified
                            if (ObjectUpdateKind.MODIFY == datastore.getObjectUpdateKind()) {
                                // Handle the property changes here
                                updateStorageDescription(service, oldDocument, enumerationProgress, datastore, false);
                            } else {
                                // if it's not modified, it should've been deleted in the vCenter.
                                // So, delete the document from photon store
                                Operation.createDelete(
                                        PhotonModelUriUtils.createInventoryUri(service.getHost(), oldDocument.documentSelfLink))
                                        .setCompletion((o, e) -> {
                                            enumerationProgress.getDatastoreTracker().track();
                                        })
                                        .sendWith(service);
                            }
                        } else {
                            enumerationProgress.getDatastoreTracker().track();
                        }
                    } catch (Exception e) {
                        service.logSevere("Error occurred while processing incremental datastore changes: %s", Utils.toString(e));
                        enumerationProgress.getDatastoreTracker().track();
                    }
                });
            }
        }
        try {
            enumerationProgress.getDatastoreTracker().await();
        } catch (InterruptedException e) {
            service.logSevere("Interrupted during incremental enumeration for networks: %s", Utils.toString(e));
        }
    }
}
