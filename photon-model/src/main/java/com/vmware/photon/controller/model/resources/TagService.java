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

import java.util.EnumSet;
import java.util.List;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.DocumentIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Describes a tag instance. Each tag consists of a key and an optional value.
 * Tags are assigned to resources through the {@link ResourceState#tagLinks} field.
 */
public class TagService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES + "/tags";

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.photon.controller.model.resources.TagService}.
     */
    public static class TagState extends ServiceDocument {

        public static final String FIELD_NAME_KEY = "key";
        public static final String FIELD_NAME_VALUE = "value";
        public static final String FIELD_NAME_EXTERNAL = "external";
        public static final String FIELD_NAME_DELETED = "deleted";
        public static final String FIELD_NAME_ORIGIN = "origins";

        /**
         * Values allowed in TagState.origins field
         * <ul>
         * <li>SYSTEM: tags are internal to the platform and were created under the hood by photon-model</li>
         * <li>USER_DEFINED: tags are internal to the platform were created from the user</li>
         * <li>DISCOVERED: tags collected from the cloud and were discovered during enumeration</li>
         * </ul>
         */

        public static enum TagOrigin {
            SYSTEM, USER_DEFINED, DISCOVERED
        }

        @Documentation(description = "Tag key")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.SORT })
        public String key;

        @Documentation(description = "Tag value, empty string used for none (null not accepted)")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
                PropertyIndexingOption.SORT })
        public String value;

        @Documentation(description = "A list of tenant links that can access this tag")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = PropertyIndexingOption.EXPAND)
        public List<String> tenantLinks;

        /**
         * NOTE: this property is deprecated and will be removed soon. Please use the origin
         * field instead.
         *
         * Each tag is categorized as local or external.
         *
         * <ul>
         * <li>Local (default) means the tag is managed by this system. If the tag is assigned
         * to a local resource, changes on the remote state will never affect this assignment.
         * <li>External means the tag is managed by an external system (e.g. cloud provider). In
         * this case, if the tag assignment is removed on the remote state, the local state is
         * updated accordingly.
         * </ul>
         *
         * <p>This flag does not affect the generated unique documentSelfLink for the tag. Thus the
         * same key-value pair (for the same tenantLinks) can be either local or external, but
         * cannot have both versions.
         *
         * <p>A previously external tag can be turned into local (goes under this system's
         * management) by a POST/PUT request, but a local tag cannot be turned into an external
         * anymore. If {@code null} is passed, the current value is not changed.
         */
        @Documentation(description = "Whether this tag is from external source")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @PropertyOptions(indexing = PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_6)
        @Deprecated
        public Boolean external;


        /**
         * <p>The origins field is used to maintain the source of each tag.
         *
         * <p>Each tag can originate one or more source. The three possible sources are:
         * <ul>
         *     <li>SYSTEM: under the hood photon model creates tags for internal use
         *     <li>USER_DEFINED: the user creates tags and associates them with resources
         *     <li>DISCOVERED: these are external tags that were discovered during enumeration
         * </ul>
         *
         * <p>Both SYSTEM and USER_DEFINED tags are local to the system. That means that if the
         * tag is assigned to a local resource, changes on the remote state will never affect this
         * assignment.
         *
         * <p> The DISCOVERED tags on the other hand are managed by an external system (e.g. cloud
         * provider). In this case, if the tag assignment is removed on the remote state, the local
         * state is updated accordingly.
         *
         * <p>The origins property does not affect the generated unique documentSelfLink for the tag.
         * The same key-value pair (for the same tenantLinks) can have one or more of the above
         * values.
         *
         * <p>A previously DISCOVERED tag can later be added from the user as well. That will result
         * in the origins field to get updated and contain both the DISCOVERED and the USER_DEFINED
         * values.
         * If {@code null} is passed, the current value is not changed.
         */
        @Documentation(description = "Maintain the different sources of the tag - i.e., user" +
                " defined, discovered, system generated")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @PropertyOptions(indexing = {PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE,
                PropertyIndexingOption.EXPAND})
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_48)
        public EnumSet<TagOrigin> origins = EnumSet.noneOf(TagOrigin.class);

        /**
         * Tag states are either being referenced in one or more resources or they are idle.
         * <p>The deleted field will be used accordingly to represent this. When idle they field will
         * be set to true, otherwise it will be set to false.
         *
         * <p>This flag does not affect the generated unique documentSelfLink for the tag.
         */
        @Documentation(description = "Whether this tag is marked as deleted")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @PropertyOptions(indexing = PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE)
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_45)
        public Boolean deleted;
    }

    public TagService() {
        super(TagState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation create) {
        try {
            processInput(create);
            create.complete();
        } catch (Throwable t) {
            create.fail(t);
        }
    }

    @Override
    public void handleDelete(Operation delete) {
        ResourceUtils.handleDelete(delete, this);
    }

    @Override
    public void handlePatch(Operation patch) {
        // Only allow the external field and the deleted field to be changed
        TagState patchState = patch.getBody(TagState.class);
        TagState currentState = getState(patch);

        if (!ServiceDocument.equals(getStateDescription(), currentState, patchState)) {
            patch.fail(new UnsupportedOperationException("Tags may not be modified"));
            return;
        }

        boolean modified = false;
        if (patchState.external != null) {
            if (currentState.external == null || (Boolean.TRUE.equals(currentState.external)
                    && Boolean.FALSE.equals(patchState.external))) {
                currentState.external = patchState.external;
                modified = true;
            }
        }

        // Add new origin sources to the set - removal of values is not allowed to this point
        if (patchState.origins != null && !patchState.origins.isEmpty()) {
            if (currentState.origins == null || currentState.origins.isEmpty()) {
                currentState.origins = patchState.origins;
                modified = true;
            } else {
                if (!currentState.origins.equals(patchState.origins)) {
                    currentState.origins.addAll(patchState.origins);
                    modified = true;
                }
            }
        }

        // update the deleted property accordingly
        if (patchState.deleted != null && currentState.deleted != patchState.deleted) {
            currentState.deleted = patchState.deleted;

            // if a tag is marked as deleted set expiry on it
            // if a previously soft-deleted tag gets reused, reset expiry to 0
            if (currentState.deleted) {
                currentState.documentExpirationTimeMicros = patchState.documentExpirationTimeMicros;
            } else {
                currentState.documentExpirationTimeMicros = 0;
            }
            modified = true;
        }

        if (!modified) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.setBody(currentState);
        patch.complete();
    }

    @Override
    public void handlePut(Operation put) {
        try {
            TagState currentState = getState(put);
            TagState newTagState = put.getBody(TagState.class);

            if (!ServiceDocument.equals(getStateDescription(), currentState, newTagState)) {
                put.fail(new UnsupportedOperationException("Tags may not be modified"));
                return;
            }

            boolean modified = false;
            // check if the tag has to be turned from external to local
            if (newTagState.external != null) {
                if (currentState.external == null || (Boolean.TRUE.equals(currentState.external)
                        && Boolean.FALSE.equals(newTagState.external))) {
                    currentState.external = newTagState.external;
                    modified = true;
                }
            }

            // Add new origin sources to the set - removal of values is not allowed at this point
            if (newTagState.origins != null && !newTagState.origins.isEmpty()) {
                if (currentState.origins == null || currentState.origins.isEmpty()) {
                    currentState.origins = newTagState.origins;
                    modified = true;
                } else {
                    if (!currentState.origins.equals(newTagState.origins)) {
                        currentState.origins.addAll(newTagState.origins);
                        modified = true;
                    }
                }
            }

            // update the deleted property accordingly
            if (newTagState.deleted != null && currentState.deleted != newTagState.deleted) {
                currentState.deleted = newTagState.deleted;

                // if a tag is marked as deleted set expiry on it
                // if a previously soft-deleted tag gets reused, reset expiry to 0
                if (currentState.deleted) {
                    currentState.documentExpirationTimeMicros =
                            newTagState.documentExpirationTimeMicros;
                } else {
                    currentState.documentExpirationTimeMicros = 0;
                }
                modified = true;
            }

            if (!modified) {
                put.addPragmaDirective(Operation.PRAGMA_DIRECTIVE_STATE_NOT_MODIFIED);
            }
            put.setBody(currentState);
            put.complete();
        } catch (Throwable t) {
            put.fail(t);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument td = super.getDocumentTemplate();
        // enable metadata indexing
        td.documentDescription.documentIndexingOptions =
                EnumSet.of(DocumentIndexingOption.INDEX_METADATA);
        if (td.documentDescription.propertyDescriptions != null) {
            PropertyDescription documentExpirationPropertyDescription = td.documentDescription
                    .propertyDescriptions.get(ServiceDocument.FIELD_NAME_EXPIRATION_TIME_MICROS);
            if (documentExpirationPropertyDescription != null) {
                documentExpirationPropertyDescription.indexingOptions
                        .add(PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE);
                td.documentDescription.propertyDescriptions.put(ServiceDocument
                        .FIELD_NAME_EXPIRATION_TIME_MICROS, documentExpirationPropertyDescription);
            }
        }
        ServiceUtils.setRetentionLimit(td);
        TagState template = (TagState) td;
        template.key = "key-1";
        template.value = "value-1";
        return template;
    }

    private TagState processInput(Operation op) {
        if (!op.hasBody()) {
            throw (new IllegalArgumentException("body is required"));
        }
        TagState state = op.getBody(TagState.class);
        Utils.validateState(getStateDescription(), state);
        return state;
    }
}
