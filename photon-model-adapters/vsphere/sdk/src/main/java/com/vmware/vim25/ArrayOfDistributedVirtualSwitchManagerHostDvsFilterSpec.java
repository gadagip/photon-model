
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfDistributedVirtualSwitchManagerHostDvsFilterSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfDistributedVirtualSwitchManagerHostDvsFilterSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="DistributedVirtualSwitchManagerHostDvsFilterSpec" type="{urn:vim25}DistributedVirtualSwitchManagerHostDvsFilterSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfDistributedVirtualSwitchManagerHostDvsFilterSpec", propOrder = {
    "distributedVirtualSwitchManagerHostDvsFilterSpec"
})
public class ArrayOfDistributedVirtualSwitchManagerHostDvsFilterSpec {

    @XmlElement(name = "DistributedVirtualSwitchManagerHostDvsFilterSpec")
    protected List<DistributedVirtualSwitchManagerHostDvsFilterSpec> distributedVirtualSwitchManagerHostDvsFilterSpec;

    /**
     * Gets the value of the distributedVirtualSwitchManagerHostDvsFilterSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the distributedVirtualSwitchManagerHostDvsFilterSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDistributedVirtualSwitchManagerHostDvsFilterSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DistributedVirtualSwitchManagerHostDvsFilterSpec }
     * 
     * 
     */
    public List<DistributedVirtualSwitchManagerHostDvsFilterSpec> getDistributedVirtualSwitchManagerHostDvsFilterSpec() {
        if (distributedVirtualSwitchManagerHostDvsFilterSpec == null) {
            distributedVirtualSwitchManagerHostDvsFilterSpec = new ArrayList<DistributedVirtualSwitchManagerHostDvsFilterSpec>();
        }
        return this.distributedVirtualSwitchManagerHostDvsFilterSpec;
    }

}
