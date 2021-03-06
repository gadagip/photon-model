
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskSparseVer1BackingOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskSparseVer1BackingOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceFileBackingOption"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="diskModes" type="{urn:vim25}ChoiceOption"/&gt;
 *         &lt;element name="split" type="{urn:vim25}BoolOption"/&gt;
 *         &lt;element name="writeThrough" type="{urn:vim25}BoolOption"/&gt;
 *         &lt;element name="growable" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskSparseVer1BackingOption", propOrder = {
    "diskModes",
    "split",
    "writeThrough",
    "growable"
})
public class VirtualDiskSparseVer1BackingOption
    extends VirtualDeviceFileBackingOption
{

    @XmlElement(required = true)
    protected ChoiceOption diskModes;
    @XmlElement(required = true)
    protected BoolOption split;
    @XmlElement(required = true)
    protected BoolOption writeThrough;
    protected boolean growable;

    /**
     * Gets the value of the diskModes property.
     * 
     * @return
     *     possible object is
     *     {@link ChoiceOption }
     *     
     */
    public ChoiceOption getDiskModes() {
        return diskModes;
    }

    /**
     * Sets the value of the diskModes property.
     * 
     * @param value
     *     allowed object is
     *     {@link ChoiceOption }
     *     
     */
    public void setDiskModes(ChoiceOption value) {
        this.diskModes = value;
    }

    /**
     * Gets the value of the split property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getSplit() {
        return split;
    }

    /**
     * Sets the value of the split property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setSplit(BoolOption value) {
        this.split = value;
    }

    /**
     * Gets the value of the writeThrough property.
     * 
     * @return
     *     possible object is
     *     {@link BoolOption }
     *     
     */
    public BoolOption getWriteThrough() {
        return writeThrough;
    }

    /**
     * Sets the value of the writeThrough property.
     * 
     * @param value
     *     allowed object is
     *     {@link BoolOption }
     *     
     */
    public void setWriteThrough(BoolOption value) {
        this.writeThrough = value;
    }

    /**
     * Gets the value of the growable property.
     * 
     */
    public boolean isGrowable() {
        return growable;
    }

    /**
     * Sets the value of the growable property.
     * 
     */
    public void setGrowable(boolean value) {
        this.growable = value;
    }

}
