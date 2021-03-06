
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFaultToleranceManagerComponentHealthInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFaultToleranceManagerComponentHealthInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="isStorageHealthy" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="isNetworkHealthy" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFaultToleranceManagerComponentHealthInfo", propOrder = {
    "isStorageHealthy",
    "isNetworkHealthy"
})
public class HostFaultToleranceManagerComponentHealthInfo
    extends DynamicData
{

    protected boolean isStorageHealthy;
    protected boolean isNetworkHealthy;

    /**
     * Gets the value of the isStorageHealthy property.
     * 
     */
    public boolean isIsStorageHealthy() {
        return isStorageHealthy;
    }

    /**
     * Sets the value of the isStorageHealthy property.
     * 
     */
    public void setIsStorageHealthy(boolean value) {
        this.isStorageHealthy = value;
    }

    /**
     * Gets the value of the isNetworkHealthy property.
     * 
     */
    public boolean isIsNetworkHealthy() {
        return isNetworkHealthy;
    }

    /**
     * Sets the value of the isNetworkHealthy property.
     * 
     */
    public void setIsNetworkHealthy(boolean value) {
        this.isNetworkHealthy = value;
    }

}
