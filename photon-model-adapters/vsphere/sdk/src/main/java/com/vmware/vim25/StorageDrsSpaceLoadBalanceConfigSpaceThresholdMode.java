
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="utilization"/&gt;
 *     &lt;enumeration value="freeSpace"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode")
@XmlEnum
public enum StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode {

    @XmlEnumValue("utilization")
    UTILIZATION("utilization"),
    @XmlEnumValue("freeSpace")
    FREE_SPACE("freeSpace");
    private final String value;

    StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode fromValue(String v) {
        for (StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode c: StorageDrsSpaceLoadBalanceConfigSpaceThresholdMode.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
