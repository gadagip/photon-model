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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.ComputeProperties.CUSTOM_DISPLAY_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_GATEWAY_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_SUBNET_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAGS;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_TAG_NAME;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.AWS_VPC_ROUTE_TABLE_ID;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryPageSize;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.setQueryResultLimit;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.cleanupEC2ClientResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.AWSUtils.tagResourcesWithName;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSComputeHost;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSResourcePool;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.createAWSVMResource;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsOnThisEndpoint;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.deleteVMsUsingEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.enumerateResources;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.getBaseLineInstanceCount;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.instanceType_t2_micro;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionAWSVMWithEC2Client;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.provisionMachine;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForInstancesToBeTerminated;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.waitForProvisioningToComplete;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.zoneId;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestUtils.getExecutor;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.getNetworkStates;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryComputeInstances;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.BaseLineState;
import com.vmware.photon.controller.model.adapters.awsadapter.enumeration.AWSComputeStateCreationAdapterService.AWSTags;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;

import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.CommandLineArgumentParser;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Test to enumerate instances on AWS and tear it down. The test creates VM using the Provisioning task as well as
 * directly creating instances on AWS using the EC2 client.It then invokes the AWS enumeration adapter to enumerate
 * all the resources on the AWS endpoint and validates that all the updates to the local state are as expected.If the 'isMock'
 * flag is set to true the test runs the adapter in mock mode and does not actually create a VM.
 * Minimally the accessKey and secretKey for AWS must be specified to run the test.
 *
 */
public class TestAWSEnumerationTask extends BasicReusableHostTestCase {
    private static final int ZERO = 0;
    private static final int count1 = 1;
    private static final int count2 = 2;
    private static final int count3 = 3;
    private static final int count4 = 4;
    private static final int count6 = 6;
    private static final int count7 = 7;
    private static final int count8 = 8;

    private static final String TEST_CASE_INITIAL = "Initial Run ";
    private static final String TEST_CASE_ADDITIONAL_VM = "Additional VM ";
    private static final String TEST_CASE_PURE_UPDATE = "Only Update to existing VM.";
    private static final String TEST_CASE_DELETE_VM = "Delete VM ";
    private static final String TEST_CASE_DELETE_VMS = "Delete multiple VMs ";
    private static final String TEST_CASE_MOCK_MODE = "Mock Mode ";
    private static final int DEFAULT_TEST_PAGE_SIZE = 5;
    private static final String T2_NANO_INSTANCE_TYPE = "t2.nano";
    private static final String VM_NAME = "TestAWSEnumerationTask-create";
    private static final String VM_UPDATED_NAME = "TestAWSEnumerationTask-update";

    private static List<String> testComputeDescriptions = new ArrayList<String>(
            Arrays.asList(zoneId + "~" + T2_NANO_INSTANCE_TYPE,
                    zoneId + "~" + instanceType_t2_micro));

    private ComputeService.ComputeState vmState;
    private String computeDescriptionLink;
    private ResourcePoolState outPool;
    private ComputeService.ComputeState outComputeHost;
    private AuthCredentialsServiceState creds;

    private List<String> instancesToCleanUp = new ArrayList<String>();
    private List<String> instanceIdsToDeleteFirstTime = new ArrayList<String>();
    private List<String> instanceIdsToDeleteSecondTime = new ArrayList<String>();
    private AmazonEC2AsyncClient client;
    private boolean isMock = true;
    private boolean isAwsClientMock = false;
    private String awsMockEndpointReference = null;
    private BaseLineState baseLineState;

    private String accessKey = "accessKey";
    private String secretKey = "secretKey";

    @Before
    public void setUp() throws Exception {
        CommandLineArgumentParser.parseFromProperties(this);

        setAwsClientMockInfo(this.isAwsClientMock, this.awsMockEndpointReference);
        // create credentials
        this.creds = new AuthCredentialsServiceState();
        this.creds.privateKey = this.secretKey;
        this.creds.privateKeyId = this.accessKey;
        this.client = AWSUtils.getAsyncClient(this.creds, TestAWSSetupUtils.zoneId, getExecutor());
        try {
            PhotonModelServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            // TODO: VSYM-992 - improve test/fix arbitrary timeout
            this.host.setTimeoutSeconds(200);

            // create the compute host, resource pool and the VM state to be used in the test.
            createResourcePoolComputeHostAndVMState();

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);
        } catch (Throwable e) {
            this.host.log("Error starting up services for the test %s", e.getMessage());
            throw new Exception(e);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (this.host == null) {
            return;
        }
        try {
            // Delete all vms from the endpoint that were provisioned from the test.
            this.host.log("Deleting %d instance created from the test ",
                    this.instancesToCleanUp.size());
            if (this.instancesToCleanUp.size() == 0) {
                cleanupEC2ClientResources(this.client);
                return;
            }
            deleteVMsOnThisEndpoint(this.host, this.isMock,
                    this.outComputeHost.documentSelfLink, this.instancesToCleanUp);
            // Check that all the instances that are required to be deleted are in
            // terminated state on AWS
            waitForInstancesToBeTerminated(this.client, this.host, this.instancesToCleanUp);
            cleanupEC2ClientResources(this.client);
            setAwsClientMockInfo(false, null);
        } catch (Throwable deleteEx) {
            // just log and move on
            this.host.log(Level.WARNING, "Exception deleting VMs - %s", deleteEx.getMessage());
        }
    }

    // Runs the enumeration task on the AWS endpoint to list all the instances on the endpoint.
    @Test
    public void testEnumeration() throws Throwable {
        if (!this.isMock) {
            this.host.setTimeoutSeconds(600);
            // Overriding the page size to test the pagination logic with limited instances on AWS.
            // This is a functional test
            // so the latency numbers maybe higher from this test due to low page size.
            setQueryPageSize(DEFAULT_TEST_PAGE_SIZE);
            setQueryResultLimit(DEFAULT_TEST_PAGE_SIZE);
            this.baseLineState = getBaseLineInstanceCount(this.host, this.client, testComputeDescriptions);
            this.host.log(this.baseLineState.toString());
            // Provision a single VM . Check initial state.
            provisionMachine(this.host, this.vmState, this.isMock, this.instancesToCleanUp);
            queryComputeInstances(this.host, count2);
            queryDocumentsAndAssertExpectedCount(this.host, count2,
                    ComputeDescriptionService.FACTORY_LINK);

            // CREATION directly on AWS
            this.instanceIdsToDeleteFirstTime = provisionAWSVMWithEC2Client(this.client, this.host,
                    count4, T2_NANO_INSTANCE_TYPE);
            List<String> instanceIds = provisionAWSVMWithEC2Client(this.client, this.host, count1,
                    instanceType_t2_micro);
            this.instanceIdsToDeleteFirstTime.addAll(instanceIds);
            this.instancesToCleanUp.addAll(this.instanceIdsToDeleteFirstTime);
            waitForProvisioningToComplete(this.instanceIdsToDeleteFirstTime, this.host, this.client, ZERO);
            // Tag the first VM with a name
            tagProvisionedVM(this.vmState.id, VM_NAME, this.client);

            // Xenon does not know about the new instances.
            ProvisioningUtils.queryComputeInstances(this.host, count2);

            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_INITIAL);
            // 5 new resources should be discovered. Mapping to 2 new compute description and 5 new
            // compute states.
            // Even though the "t2.micro" is common to the VM provisioned from Xenon
            // service and the one directly provisioned on EC2, there is no Compute description
            // linking of discovered resources to user defined compute descriptions. So a new system
            // generated compute description will be created for "t2.micro"
            queryDocumentsAndAssertExpectedCount(this.host,
                    count4 + this.baseLineState.baselineComputeDescriptionCount,
                    ComputeDescriptionService.FACTORY_LINK);
            queryComputeInstances(this.host,
                    count7 + this.baseLineState.baselineVMCount);

            // Update Scenario : Check that the tag information is present for the VM tagged above.
            String vpCId = validateTagAndNetworkAndComputeDescriptionInformation();
            validateVPCInformation(vpCId);
            // Count should be 2 NICs per discovered VM
            int totalNetworkInterfaceStateCount = (count6 + this.baseLineState.baselineVMCount) * 2;
            validateNetworkInterfaceCount(totalNetworkInterfaceStateCount);
            // One VPC should be discovered in the test.
            queryDocumentsAndAssertExpectedCount(this.host, count1,
                    NetworkService.FACTORY_LINK);

            // Pure UPDATE scenario with no new resources to discover
            // Update the tag on the VM already known to the system
            tagProvisionedVM(this.vmState.id, VM_UPDATED_NAME, this.client);
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_PURE_UPDATE);

            validateAWSNameTag(VM_UPDATED_NAME);

            // Provision an additional VM with a different instance type. It should re-use the
            // existing compute description created by the enumeration task above.
            this.instanceIdsToDeleteSecondTime = provisionAWSVMWithEC2Client(this.client, this.host,
                    count1, TestAWSSetupUtils.instanceType_t2_micro);
            this.instancesToCleanUp.addAll(this.instanceIdsToDeleteSecondTime);
            waitForProvisioningToComplete(this.instanceIdsToDeleteSecondTime, this.host, this.client, ZERO);
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_ADDITIONAL_VM);
            // One additional compute state and and one additional compute description should be
            // created. 1) compute host CD 2) t2.nano-system generated 3) t2.micro-system generated
            // 4) t2.micro-created from test code.
            queryDocumentsAndAssertExpectedCount(this.host,
                    count4 + this.baseLineState.baselineComputeDescriptionCount,
                    ComputeDescriptionService.FACTORY_LINK);
            queryComputeInstances(this.host,
                    count8 + this.baseLineState.baselineVMCount);

            // Verify Deletion flow
            // Delete 5 VMs spawned above of type T2_NANO
            deleteVMsUsingEC2Client(this.client, this.host, this.instanceIdsToDeleteFirstTime);
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_DELETE_VMS);
            // Counts should go down 5 compute states.
            queryComputeInstances(this.host,
                    count3 + this.baseLineState.baselineVMCount);

            // Delete 1 VMs spawned above of type T2_Micro
            deleteVMsUsingEC2Client(this.client, this.host, this.instanceIdsToDeleteSecondTime);
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_DELETE_VM);
            // Compute state count should go down by 1
            queryComputeInstances(this.host,
                    count2 + this.baseLineState.baselineVMCount);

        } else {
            // Create basic state for kicking off enumeration
            createResourcePoolComputeHostAndVMState();
            // Just make a call to the enumeration service and make sure that the adapter patches
            // the parent with completion.
            enumerateResources(this.host, this.isMock, this.outPool.documentSelfLink,
                    this.outComputeHost.descriptionLink, this.outComputeHost.documentSelfLink,
                    TEST_CASE_MOCK_MODE);
        }
    }

    /**
     * Tags a provisioned virtual machine with a tag.Get the latest compute state for the provisioned VM to get
     * a handle to the instance Id on AWS.This Id will be used to tag the VM with a custom property like name.
     */
    private void tagProvisionedVM(String id, String vmName, AmazonEC2AsyncClient client2)
            throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        ComputeState compute = getCompute(this.vmState.documentSelfLink);

        tagResourcesWithName(this.client, vmName, compute.id);
    }


    /**
     * Verifies if the tag information exists for a given resource. And that private and public IP addresses
     * are mapped to separate NICs.Also, checks that the compute description mapping is not changed in an updated scenario.
     * Currently, this method is being invoked for a VM provisioned from Xenon, so the check is to make sure
     * that during discovery it is not re-mapped to a system generated compute description.
     * @throws Throwable
     */
    private String validateTagAndNetworkAndComputeDescriptionInformation() throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState taggedComputeState = validateAWSNameTag(VM_NAME);

        assertEquals(taggedComputeState.descriptionLink, this.computeDescriptionLink);
        assertTrue(taggedComputeState.networkLinks != null);
        assertTrue(taggedComputeState.networkLinks.size() == 2);

        URI[] networkLinkURIs = new URI[2];
        for (int i = 0; i < taggedComputeState.networkLinks.size(); i++) {
            networkLinkURIs[i] = UriUtils.buildUri(this.host, taggedComputeState.networkLinks.get(i));
        }

        // Assert that both the public and private IP addresses have been mapped to separated NICs
        Map<URI, NetworkInterfaceState> NICMap = this.host
                .getServiceState(null, NetworkInterfaceState.class, networkLinkURIs);
        assertNotNull(NICMap.get(networkLinkURIs[0]).address);
        assertNotNull(NICMap.get(networkLinkURIs[1]).address);

        // get the VPC information for the provisioned VM
        assertTrue(taggedComputeState.customProperties.get(AWS_VPC_ID) != null);
        return taggedComputeState.customProperties.get(AWS_VPC_ID);

    }

    /**
     * Validates the tag information on a compute state matches an expected virtual machine name.
     */
    private ComputeState validateAWSNameTag(String vmName) throws Throwable {
        if (this.isAwsClientMock) {
            return null;
        }

        ComputeState taggedComputeState = getCompute(this.vmState.documentSelfLink);

        // verify conversion from AWS_TAGS to CUSTOM_DISPLAY_NAME
        String tagNameValue = taggedComputeState.customProperties.get(CUSTOM_DISPLAY_NAME);
        assertNotNull("'displayName' property should be present", tagNameValue);
        assertEquals(vmName, tagNameValue);

        // verify aws tags
        String awsTagsStr = taggedComputeState.customProperties.get(AWS_TAGS);
        assertNotNull(awsTagsStr);

        AWSTags awsTags = Utils.fromJson(awsTagsStr, AWSTags.class);
        assertNotNull(awsTags);
        assertNotNull(awsTags.awsTags);
        assertEquals(1, awsTags.awsTags.size());

        assertEquals(AWS_TAG_NAME, awsTags.awsTags.get(0).getKey());
        assertEquals(vmName, awsTags.awsTags.get(0).getValue());
        return taggedComputeState;
    }

    /**
     * Validates that the VPC information discovered from AWS has all the desired set of fields and the association
     * between a compute state and a network state is established correctly.
     * @throws Throwable
     */
    private void validateVPCInformation(String vpCId) throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        // Get the network state that maps to this VPCID. Right now the id field of the network
        // state is set to the VPC ID, so querying the network state based on that.

        Map<String, NetworkState> networkStateMap = getNetworkStates(this.host);
        assertNotNull(networkStateMap);
        NetworkState networkState = networkStateMap.get(vpCId);
        // The network state for the VPC id of the VM should not be null
        assertNotNull(networkState);
        assertNotNull(networkState.subnetCIDR);
        assertNotNull(networkState.instanceAdapterReference);
        // This is assuming that the internet gateway is attached to the VPC by default
        assertNotNull(networkState.customProperties.get(AWS_GATEWAY_ID));
        assertNotNull(networkState.customProperties.get(AWS_SUBNET_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ROUTE_TABLE_ID));
        assertNotNull(networkState.customProperties.get(AWS_VPC_ID));
    }

    /**
     * Validates the network interface count matches an expected number.
     */
    private void validateNetworkInterfaceCount(int totalNetworkInterfaceStateCount) throws Throwable {
        if (this.isAwsClientMock) {
            return;
        }

        queryDocumentsAndAssertExpectedCount(this.host, totalNetworkInterfaceStateCount,
                NetworkInterfaceService.FACTORY_LINK);
    }

    /**
     * Creates the state associated with the resource pool, compute host and the VM to be created.
     * @throws Throwable
     */
    public void createResourcePoolComputeHostAndVMState() throws Throwable {
        // Create a resource pool where the VM will be housed
        this.outPool = createAWSResourcePool(this.host);

        // create a compute host for the AWS EC2 VM
        this.outComputeHost = createAWSComputeHost(this.host, this.outPool.documentSelfLink,
                this.accessKey, this.secretKey, this.isAwsClientMock, this.awsMockEndpointReference);

        // create a AWS VM compute resource
        this.vmState = createAWSVMResource(this.host, this.outComputeHost.documentSelfLink,
                this.outPool.documentSelfLink, TestAWSSetupUtils.class);
        this.computeDescriptionLink = this.vmState.descriptionLink;
    }

    /**
     * Lookup a Compute
     */
    private ComputeState getCompute(String computeLink) throws Throwable {
        Operation response = host
                .waitForResponse(Operation.createGet(this.host, this.vmState.documentSelfLink));
        return response.getBody(ComputeState.class);
    }

}
