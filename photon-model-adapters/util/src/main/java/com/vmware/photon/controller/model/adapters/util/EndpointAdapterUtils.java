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

package com.vmware.photon.controller.model.adapters.util;

import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_ENDPOINT_NOT_UNIQUE_MESSAGE;
import static com.vmware.photon.controller.model.adapters.util.AdapterConstants.PHOTON_MODEL_ADAPTER_ENDPOINT_NOT_UNIQUE_MESSAGE_CODE;
import static com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster.INVENTORY_SERVICE;
import static com.vmware.photon.controller.model.util.PhotonModelUriUtils.createInventoryUri;
import static com.vmware.xenon.common.UriUtils.buildUri;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.RequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ServiceEndpointLocator;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class EndpointAdapterUtils {

    public static final String MOCK_REQUEST = "mockRequest";
    public static final String ENDPOINT_REFERENCE_URI = "endpointReferenceUri";

    /**
     * see {@link #registerEndpointAdapters(ServiceHost, EndpointType, String[], Map, ServiceEndpointLocator)}
     */
    public static void registerEndpointAdapters(
            ServiceHost host,
            EndpointType endpointType,
            String[] startedAdapterLinks,
            Map<String, String> adapterLinksToRegister) {
        registerEndpointAdapters(host, endpointType, startedAdapterLinks, adapterLinksToRegister,
                null);
    }

    /**
     * Register end-point adapters into End-point Adapters Registry.
     *
     * @param host
     *         The host the end-point is running on.
     * @param endpointType
     *         The type of the end-point.
     * @param startedAdapterLinks
     *         The array of started adapter links.
     * @param adapterLinksToRegister
     *         Map of adapter links (to be registered) to their adapter type key. e.g for
     *         standard adapters this is {@link com.vmware.photon.controller.model.UriPaths.AdapterTypePath#key}
     * @param registryLocator
     *         ServiceEndpointLocator containing the host of the adapter registry. Can be null if the
     *         registry is on the same host.
     * @see #handleEndpointRegistration(ServiceHost, EndpointType, Consumer, ServiceEndpointLocator)
     */
    public static void registerEndpointAdapters(
            ServiceHost host,
            EndpointType endpointType,
            String[] startedAdapterLinks,
            Map<String, String> adapterLinksToRegister,
            ServiceEndpointLocator registryLocator) {

        // Count all adapters - both FAILED and STARTED
        AtomicInteger adaptersCountDown = new AtomicInteger(startedAdapterLinks.length);

        // Keep started adapters only...
        // - key = adapter type ket (e.g. AdapterTypePath.key)
        // - value = adapter URI
        Map<String, String> startedAdapters = new ConcurrentHashMap<>();

        // Wait for all adapter services to start
        host.registerForServiceAvailability((op, ex) -> {

            if (ex != null) {
                //When xenon is in cluster the operation might be null if there is an exception
                String adapterPath = Optional.ofNullable(op)
                        .map(Operation::getUri)
                        .map(URI::getPath)
                        .orElse("");

                host.log(Level.WARNING, "Starting '%s' adapter [%s]: FAILED - %s",
                        endpointType, adapterPath, Utils.toString(ex));
            } else {
                String adapterPath = op.getUri().getPath();
                host.log(Level.FINE, "Starting '%s' adapter [%s]: SUCCESS",
                        endpointType, adapterPath);

                String adapterKey = adapterLinksToRegister.get(adapterPath);

                if (adapterKey != null) {
                    startedAdapters.put(
                            adapterKey,
                            AdapterUriUtil.buildPublicAdapterUri(host, adapterPath).toString());
                }
            }

            if (adaptersCountDown.decrementAndGet() == 0) {
                // Once ALL Adapters are started register them into End-point Adapters Registry

                host.log(Level.INFO, "Starting %d '%s' adapters: SUCCESS",
                        startedAdapters.size(), endpointType);

                // Populate end-point config with started adapters
                Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer = ep -> ep.adapterEndpoints
                        .putAll(startedAdapters);

                // Delegate to core end-point config/registration logic
                handleEndpointRegistration(
                        host, endpointType, endpointConfigEnhancer, registryLocator);
            }

        }, /* this services are not replicated */ false, startedAdapterLinks);

    }

    /**
     * Enhance end-point config with all adapters that are to be published/registered to End-point
     * Adapters Registry.
     *
     * @param host
     *         The host the end-point is running on.
     * @param endpointType
     *         The type of the end-point.
     * @param endpointConfigEnhancer
     *         Optional {@link PhotonModelAdapterConfig} enhance logic specific to the end-point
     *         type. The config passed to the callback is pre-populated with id, name and
     *         documentSelfLink (all set with {@code endpointType} param). The enhancer might
     *         populate the config with the links of end-point adapters. Once enhanced the config is
     *         posted to the {@link PhotonModelAdaptersRegistryService Adapters Registry}.
     */
    public static void handleEndpointRegistration(
            ServiceHost host,
            EndpointType endpointType,
            Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer,
            ServiceEndpointLocator registryLocator) {

        // If registry locator is set we assume the service is started
        if (registryLocator != null && registryLocator.getUri() != null) {
            registerEndpoint(host, endpointType, endpointConfigEnhancer, registryLocator);

        } else {
            host.registerForServiceAvailability((op, ex) -> {

                //Once End-point Adapters Registry is available register end-point adapters

                if (ex != null) {
                    host.log(Level.WARNING,
                            "End-point Adapters Registry is not available on this host. Please ensure %s is started.",
                            PhotonModelAdaptersRegistryService.class.getSimpleName());
                    return;
                }

                registerEndpoint(host, endpointType, endpointConfigEnhancer, registryLocator);

            }, true, PhotonModelAdaptersRegistryService.FACTORY_LINK);
        }

    }

    private static void registerEndpoint(
            ServiceHost host,
            EndpointType endpointType,
            Consumer<PhotonModelAdapterConfig> endpointConfigEnhancer,
            ServiceEndpointLocator registryLocator) {

        PhotonModelAdapterConfig endpointConfig = new PhotonModelAdapterConfig();

        // By contract the id MUST equal to endpointType
        endpointConfig.id = endpointType.name();
        endpointConfig.documentSelfLink = endpointConfig.id;
        endpointConfig.name = endpointType.toString();
        endpointConfig.adapterEndpoints = new HashMap<>();

        if (endpointConfigEnhancer != null) {
            // Pass to enhancer to customize the end-point config.
            endpointConfigEnhancer.accept(endpointConfig);
        }

        URI uri = buildUri(ClusterUtil.getClusterUri(host, registryLocator),
                PhotonModelAdaptersRegistryService.FACTORY_LINK);

        Operation postEndpointConfigOp = Operation.createPost(uri)
                .setReferer(host.getUri())
                .setBody(endpointConfig);

        host.sendWithDeferredResult(postEndpointConfigOp).whenComplete((o, e) -> {
            if (e != null) {
                host.log(Level.WARNING,
                        "Registering %d '%s' adapters into End-point Adapters Registry: FAILED - %s",
                        endpointConfig.adapterEndpoints.size(), endpointType,
                        Utils.toString(e));
            } else {
                host.log(Level.INFO,
                        "Registering %d '%s' adapters into End-point Adapters Registry: SUCCESS",
                        endpointConfig.adapterEndpoints.size(), endpointType);
            }
        });
    }

    public static void handleEndpointRequest(StatelessService service, Operation op,
            EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<EndpointState, Retriever> endpointEnhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        switch (body.requestType) {
        case VALIDATE:
            if (body.isMockRequest) {
                op.complete();
            } else {
                validate(service, op, body, credEnhancer, validator);
            }
            break;

        case ENHANCE:
            op.complete();
            configureEndpoint(service, body, credEnhancer, descEnhancer, compEnhancer,
                    endpointEnhancer);
            break;

        case CHECK_IF_ACCOUNT_EXISTS:
            op.complete();
            break;

        default:
            op.fail(new IllegalArgumentException(
                    "Unexpected endpoint request: " + body.requestType.toString()));
        }
    }

    private static void configureEndpoint(StatelessService service, EndpointConfigRequest body,
            BiConsumer<AuthCredentialsServiceState, Retriever> credEnhancer,
            BiConsumer<ComputeDescription, Retriever> descEnhancer,
            BiConsumer<ComputeState, Retriever> compEnhancer,
            BiConsumer<EndpointState, Retriever> endpointEnhancer) {

        TaskManager tm = new TaskManager(service, body.taskReference, body.resourceLink());
        Consumer<Throwable> onFailure = tm::patchTaskToFailure;

        Consumer<Operation> onSuccess = (op) -> {

            EndpointState endpoint = op.getBody(EndpointState.class);
            op.complete();

            AuthCredentialsServiceState authState = new AuthCredentialsServiceState();

            Map<String, String> props = new HashMap<>(body.endpointProperties);
            props.put(MOCK_REQUEST, String.valueOf(body.isMockRequest));
            props.put(ENDPOINT_REFERENCE_URI, body.resourceReference.toString());

            Retriever r = Retriever.of(props);
            try {
                credEnhancer.accept(authState, r);

                ComputeDescription cd = new ComputeDescription();
                descEnhancer.accept(cd, r);

                ComputeState cs = new ComputeState();
                cs.powerState = PowerState.ON;
                compEnhancer.accept(cs, r);

                EndpointState es = new EndpointState();
                es.endpointProperties = new HashMap<>();
                es.regionId = r.get(EndpointConfigRequest.REGION_KEY).orElse(null);
                endpointEnhancer.accept(es, r);

                Stream<Operation> operations = Stream.of(
                        Pair.of(authState, endpoint.authCredentialsLink),
                        Pair.of(cd, endpoint.computeDescriptionLink),
                        Pair.of(cs, endpoint.computeLink),
                        Pair.of(es, endpoint.documentSelfLink))
                        .map((p) -> Operation
                                .createPatch(createInventoryUri(service.getHost(), p.right))
                                .setBody(p.left)
                                .setReferer(service.getUri()));

                applyChanges(tm, service, endpoint, operations);
            } catch (Exception e) {
                tm.patchTaskToFailure(e);
            }

        };

        AdapterUtils.getServiceState(service, body.resourceReference, onSuccess, onFailure);
    }

    private static void applyChanges(TaskManager tm, StatelessService service,
            EndpointState endpoint, Stream<Operation> operations) {

        OperationJoin joinOp = OperationJoin.create(operations);
        joinOp.setCompletion((ox, exc) -> {
            if (exc != null) {
                service.logSevere(
                        "Error patching endpoint configuration data for %s. %s",
                        endpoint.endpointType,
                        Utils.toString(exc));
                tm.patchTaskToFailure(exc.values().iterator().next());
                return;
            }
            service.logFine(
                    () -> String.format("Successfully completed %s endpoint configuration tasks.",
                            endpoint.endpointType));
            tm.finishTask();
        });
        joinOp.sendWith(service);
    }

    private static void validate(StatelessService service, Operation op,
            EndpointConfigRequest configRequest,
            BiConsumer<AuthCredentialsServiceState, Retriever> enhancer,
            BiConsumer<AuthCredentialsServiceState, BiConsumer<ServiceErrorResponse, Throwable>> validator) {

        Consumer<Operation> onSuccessGetCredentials = oc -> {
            try {
                AuthCredentialsServiceState credentials = oc
                        .getBody(AuthCredentialsServiceState.class);
                enhancer.accept(credentials, Retriever.of(configRequest.endpointProperties));

                BiConsumer<ServiceErrorResponse, Throwable> callback = (r, e) -> {
                    service.logInfo("Finished validating credentials for operation: %d",
                            op.getId());
                    if (r == null && e == null) {
                        if (configRequest.requestType == RequestType.VALIDATE) {
                            op.complete();
                        }
                    } else {
                        op.fail(e, r);
                    }
                };
                service.logInfo("Validating credentials for operation: %d", op.getId());
                validator.accept(credentials, callback);
            } catch (Throwable e) {
                op.fail(e);
            }
        };

        Consumer<Operation> onSuccessGetEndpoint = o -> {
            EndpointState endpointState = o.getBody(EndpointState.class);

            if (endpointState.authCredentialsLink != null) {
                AdapterUtils.getServiceState(service,
                        createInventoryUri(service.getHost(), endpointState.authCredentialsLink),
                        onSuccessGetCredentials, op::fail);
            } else {
                onSuccessGetCredentials.accept(getEmptyAuthCredentialState(configRequest));
            }
        };

        // if there is an endpoint, get it and then get the credentials
        if (configRequest.resourceReference != null) {
            // If there is an error getting endpoint state, we assume that endpoint is not yet
            // created, but it was requested with a predefined link
            AdapterUtils.getServiceState(service, configRequest.resourceReference,
                    onSuccessGetEndpoint,
                    e -> onSuccessGetCredentials
                            .accept(getEmptyAuthCredentialState(configRequest)));
        } else { // otherwise, proceed with empty credentials and rely on what's in
            // endpointProperties
            onSuccessGetCredentials.accept(getEmptyAuthCredentialState(configRequest));
        }
    }

    private static Operation getEmptyAuthCredentialState(EndpointConfigRequest configRequest) {
        AuthCredentialsServiceState authCredentials = new AuthCredentialsServiceState();
        if (configRequest.tenantLinks != null) {
            authCredentials.tenantLinks = configRequest.tenantLinks;
        }
        return new Operation().setBody(authCredentials);
    }

    public static class Retriever {
        final Map<String, String> values;

        private Retriever(Map<String, String> values) {
            this.values = values;
        }

        public static Retriever of(Map<String, String> values) {
            return new Retriever(values);
        }

        public Optional<String> get(String key) {
            return Optional.ofNullable(this.values.get(key));
        }

        public String getRequired(String key) {
            return get(key).orElseThrow(
                    () -> new IllegalArgumentException(String.format("%s is required", key)));
        }
    }

    /**
     * Validates that no endpoint exists that have the same credentials and identifier.
     *
     * @param host
     *         The host to use to query the photon-model
     * @param authQuery
     *         (Optional) Query used to express the criteria for @{@link
     *         AuthCredentialsServiceState}
     * @param endpointQuery
     *         (Optional) Query used to express the criteria for @{@link EndpointState}
     * @param endpointType
     *         The endpoint type of the adapter
     * @param queryTaskTenantLinks
     *         The tenantLinks used for creating a QueryTask
     * @return A void DeferredResult when validation is successful and a failed DeferredResult when
     * validation is not successful
     */
    public static DeferredResult<Void> validateEndpointUniqueness(ServiceHost host, Query authQuery,
            Query endpointQuery, String endpointType, List<String> queryTaskTenantLinks) {

        return getAuthLinks(host, authQuery, queryTaskTenantLinks)
                .thenCompose(links -> getEndpointLinks(host,
                        endpointQuery, links, endpointType, queryTaskTenantLinks))
                .thenCompose(EndpointAdapterUtils::verifyLinks)
                .thenApply(ignore -> null);
    }

    private static DeferredResult<List<String>> getAuthLinks(ServiceHost host, Query
            authQuery, List<String> queryTaskTenantLinks) {
        Query.Builder authQueryBuilder = Builder.create()
                .addKindFieldClause(AuthCredentialsServiceState.class);

        if (authQuery != null) {
            authQueryBuilder.addClause(authQuery);
        }

        QueryTop<AuthCredentialsServiceState> queryAuth = new QueryTop<>(
                host,
                authQueryBuilder.build(),
                AuthCredentialsServiceState.class,
                queryTaskTenantLinks)
                .setQueryTaskTenantLinks(queryTaskTenantLinks);
        queryAuth.setClusterType(INVENTORY_SERVICE);

        return queryAuth.collectLinks(Collectors.toList());
    }

    private static DeferredResult<List<String>> getEndpointLinks(ServiceHost host, Query
            endpointQuery, List<String> credentialsLinks, String endpointType, List<String>
            queryTaskTenantLinks) {
        if (credentialsLinks.isEmpty()) {
            return DeferredResult.completed(Collections.emptyList());
        }

        Query.Builder qBuilder = Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addFieldClause(EndpointState.FIELD_NAME_ENDPOINT_TYPE, endpointType)
                .addInClause(EndpointState.FIELD_NAME_AUTH_CREDENTIALS_LINK, credentialsLinks);

        if (endpointQuery != null) {
            qBuilder.addClause(endpointQuery);
        }

        QueryTop<EndpointState> queryEndpoints = new QueryTop<>(
                host,
                qBuilder.build(),
                EndpointState.class,
                queryTaskTenantLinks)
                .setQueryTaskTenantLinks(queryTaskTenantLinks)
                .setMaxResultsLimit(1);
        queryEndpoints.setClusterType(INVENTORY_SERVICE);

        return queryEndpoints.collectLinks(Collectors.toList());
    }

    private static DeferredResult<Void> verifyLinks(List<String> links) {
        if (!links.isEmpty()) {
            return DeferredResult.failed(
                    new LocalizableValidationException(
                            PHOTON_MODEL_ADAPTER_ENDPOINT_NOT_UNIQUE_MESSAGE,
                            PHOTON_MODEL_ADAPTER_ENDPOINT_NOT_UNIQUE_MESSAGE_CODE));
        }
        return DeferredResult.completed(null);
    }
}
