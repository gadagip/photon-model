
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmFaultToleranceTooManyVMsOnHost complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmFaultToleranceTooManyVMsOnHost"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InsufficientResourcesFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="maxNumFtVms" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmFaultToleranceTooManyVMsOnHost", propOrder = {
    "hostName",
    "maxNumFtVms"
})
public class VmFaultToleranceTooManyVMsOnHost
    extends InsufficientResourcesFault
{

    protected String hostName;
    protected int maxNumFtVms;

    /**
     * Gets the value of the hostName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the value of the hostName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHostName(String value) {
        this.hostName = value;
    }

    /**
     * Gets the value of the maxNumFtVms property.
     * 
     */
    public int getMaxNumFtVms() {
        return maxNumFtVms;
    }

    /**
     * Sets the value of the maxNumFtVms property.
     * 
     */
    public void setMaxNumFtVms(int value) {
        this.maxNumFtVms = value;
    }

}
