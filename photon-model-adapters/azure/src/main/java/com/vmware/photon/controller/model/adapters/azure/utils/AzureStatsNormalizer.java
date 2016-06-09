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

package com.vmware.photon.controller.model.adapters.azure.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;

public class AzureStatsNormalizer {
    private static final Map<String, String> PHOTON_MODEL_UNIT_MAP;
    private static final Map<String, String> PHOTON_MODEL_STATS_MAP;

    static {
        // Map of Azure-specific Units to Photon-Model Units
        Map<String, String> unitMap = new HashMap<>();
        unitMap.put(AzureConstants.UNIT_COST,
                PhotonModelConstants.UNIT_COST);
        unitMap.put(AzureConstants.UNIT_BYTES,
                PhotonModelConstants.UNIT_BYTES);
        unitMap.put(AzureConstants.UNIT_COUNT,
                PhotonModelConstants.UNIT_COUNT);
        unitMap.put(AzureConstants.UNIT_PERCENT,
                PhotonModelConstants.UNIT_PERCENT);
        PHOTON_MODEL_UNIT_MAP = Collections.unmodifiableMap(unitMap);

        // Map of Azure-specific stat keys to Photon-Model stat keys
        Map<String, String> statMap = new HashMap<>();
        statMap.put(AzureConstants.NETWORK_BYTES_IN,
                PhotonModelConstants.NETWORK_IN_BYTES);
        statMap.put(AzureConstants.NETWORK_BYTES_OUT,
                PhotonModelConstants.NETWORK_OUT_BYTES);
        statMap.put(AzureConstants.DISK_WRITES_PER_SECOND,
                PhotonModelConstants.DISK_WRITE_OPS_COUNT);
        statMap.put(AzureConstants.DISK_READS_PER_SECOND,
                PhotonModelConstants.DISK_READ_OPS_COUNT);
        statMap.put(AzureConstants.CPU_UTILIZATION,
                PhotonModelConstants.CPU_UTILIZATION_PERCENT);
        statMap.put(AzureConstants.MEMORY_AVAILABLE,
                PhotonModelConstants.MEMORY_AVAILABLE_BYTES);
        statMap.put(AzureConstants.MEMORY_USED,
                PhotonModelConstants.MEMORY_USED_BYTES);
        statMap.put(AzureConstants.DISK_READ_BYTES_PER_SECOND,
                PhotonModelConstants.DISK_READ_BYTES);
        statMap.put(AzureConstants.DISK_WRITE_BYTES_PER_SECOND,
                PhotonModelConstants.DISK_WRITE_BYTES);
        PHOTON_MODEL_STATS_MAP = Collections.unmodifiableMap(statMap);
    }

    public static String getNormalizedUnitValue(String cloudSpecificUnit) {
        return PHOTON_MODEL_UNIT_MAP.get(cloudSpecificUnit);
    }

    public static String getNormalizedStatKeyValue(String cloudSpecificStatKey) {
        return PHOTON_MODEL_STATS_MAP.get(cloudSpecificStatKey);
    }
}
