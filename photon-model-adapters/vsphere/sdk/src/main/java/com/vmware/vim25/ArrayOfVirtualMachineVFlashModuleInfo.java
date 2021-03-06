
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVirtualMachineVFlashModuleInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVirtualMachineVFlashModuleInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="VirtualMachineVFlashModuleInfo" type="{urn:vim25}VirtualMachineVFlashModuleInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVirtualMachineVFlashModuleInfo", propOrder = {
    "virtualMachineVFlashModuleInfo"
})
public class ArrayOfVirtualMachineVFlashModuleInfo {

    @XmlElement(name = "VirtualMachineVFlashModuleInfo")
    protected List<VirtualMachineVFlashModuleInfo> virtualMachineVFlashModuleInfo;

    /**
     * Gets the value of the virtualMachineVFlashModuleInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the virtualMachineVFlashModuleInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVirtualMachineVFlashModuleInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualMachineVFlashModuleInfo }
     * 
     * 
     */
    public List<VirtualMachineVFlashModuleInfo> getVirtualMachineVFlashModuleInfo() {
        if (virtualMachineVFlashModuleInfo == null) {
            virtualMachineVFlashModuleInfo = new ArrayList<VirtualMachineVFlashModuleInfo>();
        }
        return this.virtualMachineVFlashModuleInfo;
    }

}
