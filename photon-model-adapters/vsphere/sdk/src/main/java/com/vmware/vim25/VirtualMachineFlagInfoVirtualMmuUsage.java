
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineFlagInfoVirtualMmuUsage.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineFlagInfoVirtualMmuUsage"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="automatic"/&gt;
 *     &lt;enumeration value="on"/&gt;
 *     &lt;enumeration value="off"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineFlagInfoVirtualMmuUsage")
@XmlEnum
public enum VirtualMachineFlagInfoVirtualMmuUsage {

    @XmlEnumValue("automatic")
    AUTOMATIC("automatic"),
    @XmlEnumValue("on")
    ON("on"),
    @XmlEnumValue("off")
    OFF("off");
    private final String value;

    VirtualMachineFlagInfoVirtualMmuUsage(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineFlagInfoVirtualMmuUsage fromValue(String v) {
        for (VirtualMachineFlagInfoVirtualMmuUsage c: VirtualMachineFlagInfoVirtualMmuUsage.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
