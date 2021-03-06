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

package com.vmware.photon.controller.model.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.NETWORK_SUBTYPE_NETWORK_STATE;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.TenantService;

/**
 * This class implements tests for the {@link NetworkService} class.
 */
@RunWith(NetworkServiceTest.class)
@SuiteClasses({ NetworkServiceTest.ConstructorTest.class,
        NetworkServiceTest.HandleStartTest.class,
        NetworkServiceTest.HandlePatchTest.class,
        NetworkServiceTest.HandlePutTest.class,
        NetworkServiceTest.QueryTest.class })
public class NetworkServiceTest extends Suite {

    public NetworkServiceTest(Class<?> klass, RunnerBuilder builder)
            throws InitializationError {
        super(klass, builder);
    }

    private static NetworkService.NetworkState buildValidStartState(boolean assignHost) {
        NetworkService.NetworkState networkState = new NetworkService.NetworkState();
        networkState.id = UUID.randomUUID().toString();
        networkState.name = "networkName";
        networkState.subnetCIDR = "10.0.0.0/10";
        networkState.tenantLinks = new ArrayList<>();
        networkState.tenantLinks.add("tenant-linkA");
        networkState.regionId = "regionId";
        networkState.authCredentialsLink = "http://authCredentialsLink";
        networkState.resourcePoolLink = "http://resourcePoolLink";
        if (assignHost) {
            networkState.computeHostLink = "host-1";
        }
        try {
            networkState.instanceAdapterReference = new URI(
                    "http://instanceAdapterReference");
        } catch (Exception e) {
            networkState.instanceAdapterReference = null;
        }

        return networkState;
    }

    /**
     * This class implements tests for the constructor.
     */
    public static class ConstructorTest {
        private NetworkService networkService = new NetworkService();

        @Before
        public void setupTest() {
            this.networkService = new NetworkService();
        }

        @Test
        public void testServiceOptions() {
            EnumSet<Service.ServiceOption> expected = EnumSet.of(
                    Service.ServiceOption.CONCURRENT_GET_HANDLING,
                    Service.ServiceOption.PERSISTENCE,
                    Service.ServiceOption.REPLICATION,
                    Service.ServiceOption.OWNER_SELECTION,
                    Service.ServiceOption.IDEMPOTENT_POST);
            assertThat(this.networkService.getOptions(), is(expected));
        }
    }

    /**
     * This class implements tests for the handleStart method.
     */
    public static class HandleStartTest extends BaseModelTest {
        @Test
        public void testValidStartState() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                            startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));

        }

        @Test
        public void testValidStartStateWithHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testInvalidStartState() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);
            startState.subnetCIDR = null;
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.id, is(startState.id));
            assertThat(returnState.name, is(startState.name));
            assertThat(returnState.subnetCIDR, is(startState.subnetCIDR));
            assertThat(returnState.tenantLinks.get(0),
                    is(startState.tenantLinks.get(0)));
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(startState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(startState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(startState.instanceAdapterReference));

        }

        @Test
        public void testDuplicatePost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                            startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.name, is(startState.name));
            startState.name = "new-name";
            returnState = postServiceSynchronously(NetworkService.FACTORY_LINK,
                            startState, NetworkService.NetworkState.class);
            assertThat(returnState.name, is(startState.name));
        }

        @Test
        public void testDuplicatePostAssignComputeHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.name, is(startState.name));
            assertNull(returnState.computeHostLink);
            startState.name = "new-name";
            startState.computeHostLink = "host-1";
            returnState = postServiceSynchronously(NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink, is(startState.computeHostLink));
        }

        @Test
        public void testDuplicatePostModifyComputeHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertEquals(returnState.type, NETWORK_SUBTYPE_NETWORK_STATE);
            assertThat(returnState.name, is(startState.name));
            assertNotNull(returnState.computeHostLink);

            returnState.computeHostLink = "host-2";
            postServiceSynchronously(NetworkService.FACTORY_LINK,
                    returnState, NetworkService.NetworkState.class, IllegalArgumentException.class);
        }

        @Test
        public void testDuplicatePostModifyCreationTime() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);
            assertNotNull(returnState.documentCreationTimeMicros);

            long originalTime = returnState.documentCreationTimeMicros;
            returnState.documentCreationTimeMicros = originalTime;

            returnState = postServiceSynchronously(NetworkService.FACTORY_LINK,
                    returnState, NetworkService.NetworkState.class);
            assertThat(originalTime, is(returnState.documentCreationTimeMicros));
        }

        @Test
        public void testInvalidValues() throws Throwable {
            NetworkService.NetworkState invalidSubnet1 = buildValidStartState(false);
            NetworkService.NetworkState invalidSubnet2 = buildValidStartState(false);
            NetworkService.NetworkState invalidSubnet3 = buildValidStartState(false);
            NetworkService.NetworkState invalidSubnet4 = buildValidStartState(false);
            NetworkService.NetworkState invalidSubnet5 = buildValidStartState(false);

            invalidSubnet1.subnetCIDR = "10.0.0.0";
            // invalid IP
            invalidSubnet2.subnetCIDR = "10.0.0.A";
            // invalid Subnet range
            invalidSubnet3.subnetCIDR = "10.0.0.0/33";
            // invalid Subnet range
            invalidSubnet4.subnetCIDR = "10.0.0.0/-1";
            // invalid Subnet separator
            invalidSubnet5.subnetCIDR = "10.0.0.0\\0";

            NetworkService.NetworkState[] states = {
                    invalidSubnet1, invalidSubnet2, invalidSubnet3,
                    invalidSubnet4, invalidSubnet5 };
            for (NetworkService.NetworkState state : states) {
                postServiceSynchronously(NetworkService.FACTORY_LINK,
                        state, NetworkService.NetworkState.class,
                        IllegalArgumentException.class);
            }
        }
    }

    /**
     * This class implements tests for the handlePatch method.
     */
    public static class HandlePatchTest extends BaseModelTest {
        @Test
        public void testPatch() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                            startState, NetworkService.NetworkState.class);
            assertNull(returnState.computeHostLink);
            assertNotNull(returnState.documentCreationTimeMicros);

            NetworkService.NetworkState patchState = new NetworkService.NetworkState();
            patchState.name = "patchNetworkName";
            patchState.subnetCIDR = "152.151.150.222/22";
            patchState.customProperties = new HashMap<>();
            patchState.customProperties.put("patchKey", "patchValue");
            patchState.regionId = "patchRregionID";
            patchState.authCredentialsLink = "http://patchAuthCredentialsLink";
            patchState.resourcePoolLink = "http://patchResourcePoolLink";
            try {
                patchState.instanceAdapterReference = new URI(
                        "http://patchInstanceAdapterReference");
            } catch (Exception e) {
                patchState.instanceAdapterReference = null;
            }
            patchState.tenantLinks = new ArrayList<String>();
            patchState.tenantLinks.add("tenant1");
            patchState.groupLinks = new HashSet<String>();
            patchState.groupLinks.add("group1");
            patchState.computeHostLink = "host-1";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkService.NetworkState.class);

            assertThat(returnState.name, is(patchState.name));
            assertThat(returnState.subnetCIDR, is(patchState.subnetCIDR));
            assertThat(returnState.customProperties,
                    is(patchState.customProperties));
            // region ID should not be updated
            assertThat(returnState.regionId, is(startState.regionId));
            assertThat(returnState.authCredentialsLink,
                    is(patchState.authCredentialsLink));
            assertThat(returnState.resourcePoolLink,
                    is(patchState.resourcePoolLink));
            assertThat(returnState.instanceAdapterReference,
                    is(patchState.instanceAdapterReference));
            assertEquals(returnState.tenantLinks.size(), 2);
            assertEquals(returnState.groupLinks, patchState.groupLinks);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink,
                    is(patchState.computeHostLink));

        }

        @Test
        public void testPatchAssignHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            NetworkService.NetworkState patchState = new NetworkService.NetworkState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkService.NetworkState.class);
            assertNotNull(returnState.computeHostLink);
            assertThat(returnState.computeHostLink,
                    is(patchState.computeHostLink));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPatchModifyHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            NetworkService.NetworkState patchState = new NetworkService.NetworkState();
            patchState.computeHostLink = "host-2";
            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);
        }

        @Test
        public void testPatchModifyCreationTime() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);
            long originalCreationTime = returnState.documentCreationTimeMicros;

            NetworkService.NetworkState patchState = new NetworkService.NetworkState();
            long currentCreationTime = Utils.getNowMicrosUtc();
            patchState.documentCreationTimeMicros = currentCreationTime;

            patchServiceSynchronously(returnState.documentSelfLink,
                    patchState);

            returnState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkService.NetworkState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertThat(returnState.documentCreationTimeMicros, is(originalCreationTime));
        }
    }

    /**
     * This class implements tests for the handlePut method.
     */
    public static class HandlePutTest extends BaseModelTest {

        @Test
        public void testPut() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            NetworkService.NetworkState newState = new NetworkService.NetworkState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.regionId = "regionId";
            newState.authCredentialsLink = "http://authCredentialsLink";
            newState.resourcePoolLink = "http://resourcePoolLink";
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;
            try {
                newState.instanceAdapterReference = new URI(
                        "http://instanceAdapterReference");
            } catch (Exception e) {
                newState.instanceAdapterReference = null;
            }

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            NetworkService.NetworkState getState = getServiceSynchronously(returnState.documentSelfLink,
                    NetworkService.NetworkState.class);
            assertThat(getState.id, is(newState.id));
            assertThat(getState.name, is(newState.name));
            assertThat(getState.subnetCIDR, is(newState.subnetCIDR));
            assertEquals(getState.tenantLinks, newState.tenantLinks);
            assertEquals(getState.groupLinks, newState.groupLinks);
            // make sure launchTimeMicros was preserved
            assertEquals(getState.creationTimeMicros, returnState.creationTimeMicros);
            assertEquals(getState.documentCreationTimeMicros, returnState.documentCreationTimeMicros);
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyCreationTime() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(false);
            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);

            assertNotNull(returnState);

            NetworkService.NetworkState newState = new NetworkService.NetworkState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");

            long currentTime = Utils.getNowMicrosUtc();
            newState.documentCreationTimeMicros = currentTime;

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);

            NetworkService.NetworkState getState = getServiceSynchronously(
                    returnState.documentSelfLink,
                    NetworkService.NetworkState.class);
            assertThat(getState.documentCreationTimeMicros, is(returnState.documentCreationTimeMicros));
        }

        @Test(expected = IllegalArgumentException.class)
        public void testPutModifyHost() throws Throwable {
            NetworkService.NetworkState startState = buildValidStartState(true);

            NetworkService.NetworkState returnState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                    startState, NetworkService.NetworkState.class);
            assertNotNull(returnState.documentCreationTimeMicros);
            assertNotNull(returnState.computeHostLink);

            NetworkService.NetworkState newState = new NetworkService.NetworkState();
            newState.id = UUID.randomUUID().toString();
            newState.name = "networkName";
            newState.subnetCIDR = "10.0.0.0/10";
            newState.tenantLinks = new ArrayList<>();
            newState.tenantLinks.add("tenant-linkA");
            newState.documentCreationTimeMicros = returnState.documentCreationTimeMicros;

            newState.computeHostLink = "host-2";

            putServiceSynchronously(returnState.documentSelfLink,
                    newState);
        }
    }

    /**
     * This class implements tests for query.
     */
    public static class QueryTest extends BaseModelTest {
        @Test
        public void testTenantLinksQuery() throws Throwable {
            NetworkService.NetworkState networkState = buildValidStartState(false);
            URI tenantUri = UriUtils.buildFactoryUri(this.host, TenantService.class);
            networkState.tenantLinks = new ArrayList<>();
            networkState.tenantLinks.add(UriUtils.buildUriPath(
                    tenantUri.getPath(), "tenantA"));
            NetworkService.NetworkState startState = postServiceSynchronously(
                    NetworkService.FACTORY_LINK,
                            networkState, NetworkService.NetworkState.class);

            String kind = Utils.buildKind(NetworkService.NetworkState.class);
            String propertyName = QueryTask.QuerySpecification
                    .buildCollectionItemName(ResourceState.FIELD_NAME_TENANT_LINKS);

            QueryTask q = createDirectQueryTask(kind, propertyName,
                    networkState.tenantLinks.get(0));
            q = querySynchronously(q);
            assertNotNull(q.results.documentLinks);
            assertThat(q.results.documentCount, is(1L));
            assertThat(q.results.documentLinks.get(0),
                    is(startState.documentSelfLink));
        }
    }

}