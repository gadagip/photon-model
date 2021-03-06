
package com.vmware.pbm;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:23.463+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "PbmNonExistentHubsFault", targetNamespace = "urn:pbm")
public class PbmNonExistentHubsFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.pbm.PbmNonExistentHubs pbmNonExistentHubsFault;

    public PbmNonExistentHubsFaultMsg() {
        super();
    }
    
    public PbmNonExistentHubsFaultMsg(String message) {
        super(message);
    }
    
    public PbmNonExistentHubsFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public PbmNonExistentHubsFaultMsg(String message, com.vmware.pbm.PbmNonExistentHubs pbmNonExistentHubsFault) {
        super(message);
        this.pbmNonExistentHubsFault = pbmNonExistentHubsFault;
    }

    public PbmNonExistentHubsFaultMsg(String message, com.vmware.pbm.PbmNonExistentHubs pbmNonExistentHubsFault, Throwable cause) {
        super(message, cause);
        this.pbmNonExistentHubsFault = pbmNonExistentHubsFault;
    }

    public com.vmware.pbm.PbmNonExistentHubs getFaultInfo() {
        return this.pbmNonExistentHubsFault;
    }
}
