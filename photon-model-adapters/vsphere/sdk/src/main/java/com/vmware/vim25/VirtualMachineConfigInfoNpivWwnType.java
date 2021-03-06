
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineConfigInfoNpivWwnType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineConfigInfoNpivWwnType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="vc"/&gt;
 *     &lt;enumeration value="host"/&gt;
 *     &lt;enumeration value="external"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineConfigInfoNpivWwnType")
@XmlEnum
public enum VirtualMachineConfigInfoNpivWwnType {

    @XmlEnumValue("vc")
    VC("vc"),
    @XmlEnumValue("host")
    HOST("host"),
    @XmlEnumValue("external")
    EXTERNAL("external");
    private final String value;

    VirtualMachineConfigInfoNpivWwnType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineConfigInfoNpivWwnType fromValue(String v) {
        for (VirtualMachineConfigInfoNpivWwnType c: VirtualMachineConfigInfoNpivWwnType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
