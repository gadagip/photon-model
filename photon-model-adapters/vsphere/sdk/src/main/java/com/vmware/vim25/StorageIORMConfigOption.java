
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for StorageIORMConfigOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StorageIORMConfigOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="enabledOption" type="{urn:vim25}BoolOption"/&gt;
 *         &lt;element name="congestionThresholdOption" type="{urn:vim25}IntOption"/&gt;
 *         &lt;element name="statsCollectionEnabledOption" type="{urn:vim25}BoolOption" minOccurs="0"/&gt;
 *         &lt;element name="reservationEnabledOption" type="{urn:vim25}BoolOption" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StorageIORMConfigOption", propOrder = {
    "enabledOption",
    "congestionThresholdOption",
    "statsCollectionEnabledOption",
    "reservationEnabledOption"
})
public class StorageIORMConfigOption
    extends DynamicData
{

    @XmlElement(required = true)
    protected BoolOption enabledOption;
    @XmlElement(required = true)
    protected IntOption congestionThresholdOption;
    protected BoolOption statsCollectionEnabledOption;
    protected BoolOption reservationEnabledOption;

    /**
     * Gets the value of the enabledOption property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getEnabledOption() {
        return enabledOption;
    }

    /**
     * Sets the value of the enabledOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setEnabledOption(BoolOption value) {
        this.enabledOption = value;
    }

    /**
     * Gets the value of the congestionThresholdOption property.
     * 
     * @return
     *     possible object is
     *     {@link IntOption }
     *     
     */
    public IntOption getCongestionThresholdOption() {
        return congestionThresholdOption;
    }

    /**
     * Sets the value of the congestionThresholdOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntOption }
     *     
     */
    public void setCongestionThresholdOption(IntOption value) {
        this.congestionThresholdOption = value;
    }

    /**
     * Gets the value of the statsCollectionEnabledOption property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getStatsCollectionEnabledOption() {
        return statsCollectionEnabledOption;
    }

    /**
     * Sets the value of the statsCollectionEnabledOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setStatsCollectionEnabledOption(BoolOption value) {
        this.statsCollectionEnabledOption = value;
    }

    /**
     * Gets the value of the reservationEnabledOption property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getReservationEnabledOption() {
        return reservationEnabledOption;
    }

    /**
     * Sets the value of the reservationEnabledOption property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setReservationEnabledOption(BoolOption value) {
        this.reservationEnabledOption = value;
    }

}
