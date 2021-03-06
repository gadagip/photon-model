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

import static com.vmware.photon.controller.model.adapters.vsphere.util.VimNames.TYPE_PORTGROUP;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.TaggingClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.InvalidPropertyFaultMsg;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFaultFaultMsg;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class VsphereEnumerationHelper {

    static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);

    public static String getSelfLinkFromOperation(Operation o) {
        return o.getBody(ServiceDocument.class).documentSelfLink;
    }

    public static <T> T convertOnlyResultToDocument(ServiceDocumentQueryResult result, Class<T> type) {
        return Utils.fromJson(result.documents.values().iterator().next(), type);
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *  @param service
     * @param task
     * @param handler
     * @param tracker
     * @param resultLimit
     */
    static void withTaskResults(
            VSphereIncrementalEnumerationService service, QueryTask task,
            ResourceTracker tracker, Consumer<ServiceDocumentQueryResult> handler, int resultLimit) {
        enhanceQueryTask(task, resultLimit);

        QueryUtils.startInventoryQueryTask(service, task)
                .whenComplete((o, e) -> {
                    if (e != null) {
                        service.logWarning(() -> String.format("Error processing task %s Exception: %s",
                                Utils.toJson(task), Utils.toString(e)));
                        // if a tracker is passed, then track down on error
                        if (null != tracker) {
                            tracker.track();
                        }
                        return;
                    }

                    handler.accept(o.results);
                });
    }

    static void withTaskResults(
            VSphereIncrementalEnumerationService service, QueryTask task,
            Phaser phaser, Consumer<ServiceDocumentQueryResult> handler, int resultLimit) {
        enhanceQueryTask(task, resultLimit);

        QueryUtils.startInventoryQueryTask(service, task)
                .whenComplete((o, e) -> {
                    if (e != null) {
                        service.logWarning(() -> String.format("Error processing task %s Exception: %s",
                                Utils.toJson(task), Utils.toString(e)));
                        // if a tracker is passed, then track down on error
                        if (null != phaser) {
                            phaser.arrive();
                        }
                        return;
                    }

                    handler.accept(o.results);
                });
    }

    static void withTaskResults(
            VSphereIncrementalEnumerationService service, QueryTask task,
            Consumer<ServiceDocumentQueryResult> handler, int resultLimit) {
        enhanceQueryTask(task, resultLimit);

        QueryUtils.startInventoryQueryTask(service, task)
                .whenComplete((o, e) -> {
                    if (e != null) {
                        service.logWarning(() -> String.format("Error processing task %s Exception: %s",
                                Utils.toJson(task), Utils.toString(e)));
                        return;
                    }

                    handler.accept(o.results);
                });
    }

    private static void enhanceQueryTask(QueryTask task, int resultLimit) {
        task.querySpec.options = EnumSet.of(
                QueryOption.EXPAND_CONTENT,
                QueryOption.INDEXED_METADATA,
                QueryOption.TOP_RESULTS);
        if (resultLimit > 0) {
            task.querySpec.resultLimit = resultLimit;
        }

        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QUERY_TASK_EXPIRY_MICROS);
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param service
     * @param task
     * @param handler
     */
    static void withTaskResults(VSphereIncrementalEnumerationService service,
                                QueryTask task, ResourceTracker tracker, Consumer<ServiceDocumentQueryResult> handler) {
        withTaskResults(service, task, tracker, handler, 1);
    }

    /**
     * Builds a function to retrieve tags given and endpoint.
     *
     * @param client
     * @return
     */
    static Function<String, TagState> newTagRetriever(TaggingClient client) {
        return (tagId) -> {
            try {
                ObjectNode tagModel = client.getTagModel(tagId);
                if (tagModel == null) {
                    return null;
                }

                TagState res = new TagState();
                res.value = tagModel.get("name").asText();
                res.key = client.getCategoryName(tagModel.get("category_id").asText());
                return res;
            } catch (IOException | RpcException e) {
                return null;
            }
        };
    }

    /**
     * Retreives all tags for a MoRef from an endpoint.
     *
     * @return empty list if no tags found, never null
     */
    static List<TagState> retrieveAttachedTags(
            VSphereIncrementalEnumerationService service, VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) throws IOException, RpcException {
        TaggingClient taggingClient = endpoint.newTaggingClient();
        List<String> tagIds = taggingClient.getAttachedTags(ref);

        List<TagState> res = new ArrayList<>();
        for (String id : tagIds) {
            TagState cached = service.getTagCache().get(id, newTagRetriever(taggingClient));
            if (cached != null) {
                TagState tag = TagsUtil.newTagState(cached.key, cached.value, true, tenantLinks);
                res.add(tag);
            }
        }

        return res;
    }

    static Set<String> createTagsAsync(VSphereIncrementalEnumerationService service,
                                       List<TagState> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        Stream<Operation> ops = tags.stream()
                .map(s -> Operation
                        .createPost(UriUtils.buildFactoryUri(service.getHost(), TagService.class))
                        .setBody(s));

        OperationJoin.create(ops)
                .sendWith(service);

        return tags.stream()
                .map(s -> s.documentSelfLink)
                .collect(Collectors.toSet());
    }

    /**
     * After the tags for the ref are retrieved from the endpoint they are posted to the tag service
     * and the selfLinks are collected ready to be used in a ComputeState#tagLinks.
     */
    static Set<String> retrieveTagLinksAndCreateTagsAsync(
            VSphereIncrementalEnumerationService service, VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) {
        List<TagState> tags = null;
        try {
            tags = retrieveAttachedTags(service, endpoint, ref, tenantLinks);
        } catch (IOException | RpcException ignore) {

        }

        return createTagsAsync(service, tags);
    }

    static void populateTags(
            VSphereIncrementalEnumerationService service, EnumerationProgress enumerationProgress,
            AbstractOverlay obj, ResourceState state) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(service, enumerationProgress.getEndpoint(),
                obj.getId(), enumerationProgress.getTenantLinks());
    }

    static void submitWorkToVSpherePool(VSphereIncrementalEnumerationService service, Runnable work) {
        // store context at the moment of submission
        OperationContext orig = OperationContext.getOperationContext();
        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(service);

        pool.submit(() -> {
            OperationContext old = OperationContext.getOperationContext();

            OperationContext.setFrom(orig);
            try {
                work.run();
            } finally {
                OperationContext.restoreOperationContext(old);
            }
        });
    }

    static void updateLocalTags(VSphereIncrementalEnumerationService service,
                                EnumerationProgress enumerationProgress, AbstractOverlay obj,
                                ResourceState patchResponse) {
        List<TagState> tags;
        try {
            tags = retrieveAttachedTags(service, enumerationProgress.getEndpoint(),
                    obj.getId(),
                    enumerationProgress.getTenantLinks());
        } catch (IOException | RpcException e) {
            service.logWarning("Error updating local tags for %s", patchResponse.documentSelfLink);
            return;
        }

        Map<String, String> remoteTagMap = new HashMap<>();
        for (TagState ts : tags) {
            remoteTagMap.put(ts.key, ts.value);
        }

        TagsUtil.updateLocalTagStates(service, patchResponse, remoteTagMap, null);
    }

    static String computeGroupStableLink(ManagedObjectReference ref, String prefix, String endpointLink) {
        return UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                prefix + "-" +
                        VimUtils.buildStableManagedObjectId(ref, endpointLink));
    }

    public static Set<String> getConnectedDatastoresAndNetworks(
            EnumerationProgress ctx, List<ManagedObjectReference> datastores,
            List<ManagedObjectReference> networks, EnumerationClient enumerationClient)
            throws InvalidPropertyFaultMsg, RuntimeFaultFaultMsg {
        Set<String> res = new TreeSet<>();

        for (ManagedObjectReference ref : datastores) {
            res.add(computeGroupStableLink(ref,
                    VSphereIncrementalEnumerationService.PREFIX_DATASTORE, ctx.getRequest().endpointLink));
        }

        for (ManagedObjectReference ref : networks) {
            if (TYPE_PORTGROUP.equals(ref.getType())) {
                NetworkOverlay ovly = (NetworkOverlay) ctx.getOverlay(ref);
                ManagedObjectReference parentSwitch = null;
                if (null == ovly) {
                    parentSwitch = enumerationClient.getParentSwitchForDVPortGroup(ref);
                } else {
                    parentSwitch = ovly.getParentSwitch();
                }
                res.add(computeGroupStableLink(parentSwitch,
                        VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink));
            } else {
                res.add(computeGroupStableLink(ref,
                        VSphereIncrementalEnumerationService.PREFIX_NETWORK, ctx.getRequest().endpointLink));
            }
        }
        return res;
    }

    // The below methods will populate the custom properties required for identifying the resource using
    // functional key - orgId:vcUuid:moid

    static void populateResourceStateWithAdditionalProps(ResourceState state, String vcUuid) {
        populateResourceStateWithAdditionalProps(state, vcUuid, null);
    }

    // moid is already populated in "__moref" property in the respective make*Object*FromResults() methods
    // The moref parameter will be null in such cases. See VSphereHostSystemEnumerationHelper.makeDescriptionForHost
    static void populateResourceStateWithAdditionalProps(ResourceState state, String vcUuid,
                                                         ManagedObjectReference moref) {
        if (null != moref) {
            CustomProperties.of(state)
                    .put(CustomProperties.MOREF, moref);
        }
        CustomProperties.of(state)
                .put(CustomProperties.VC_UUID, vcUuid);
    }

    static DeferredResult<EndpointState> getEndpoint(ServiceHost host, URI endpointLinkReference) {
        Operation getEndpointOp = Operation.createGet(PhotonModelUriUtils.createInventoryUri
                (host, endpointLinkReference));
        getEndpointOp.setReferer(host.getUri());
        return host.sendWithDeferredResult(getEndpointOp, EndpointState.class);
    }

    static DeferredResult<ComputeStateWithDescription> getComputeStateDescription(ServiceHost host, URI parentUri) {
        Operation getComputeStateOp = Operation.createGet(ComputeService.ComputeStateWithDescription
                .buildUri(PhotonModelUriUtils.createInventoryUri(host, parentUri)));
        getComputeStateOp.setReferer(host.getUri());
        return host.sendWithDeferredResult(getComputeStateOp, ComputeService.ComputeStateWithDescription.class);
    }
}
