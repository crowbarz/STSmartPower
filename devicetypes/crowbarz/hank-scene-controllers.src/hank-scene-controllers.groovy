/**
 *  Hank HKZW-SCN01/SCN04 DTH by Crowbarz (@crowbarz)
 *  Based on Hank HKZW-SCN01 and HKZW-SCN04 DTHs by Emil Åkered (@emilakered)
 *  Based on DTH "Fibaro Button", copyright 2017 Ronald Gouldner (@gouldner)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	2019-07-28
 *	- Forked from DTHs by Emil Åkered (@emilakered)
 *  - Merged Hank 1 and 4 button DTHs into one DTH
 *  - Create child devices (to workaround button data not being passed through ST REST API)
 */
 
metadata {
    definition (name: "Hank Scene Controllers", namespace: "crowbarz", author: "Crowbar Z") {
        capability "Actuator"
        capability "Sensor"
        capability "Battery"
        capability "Button"
        capability "Configuration"
        
        attribute "lastPressed", "string"
		attribute "numberOfButtons", "number"
		attribute "lastSequence", "number"

		command "pushButton"

        fingerprint mfr: "0208", prod: "0200", model: "000B", deviceJoinName: "Hank 4-Key Scene Controller"
        fingerprint mfr: "0208", prod: "0200", model: "0009", deviceJoinName: "Hank 1-Key Scene Controller"
        fingerprint deviceId: "0x1801", inClusters: "0x5E,0x86,0x72,0x5B,0x59,0x85,0x80,0x84,0x73,0x70,0x7A,0x5A", outClusters: "0x26"
    }

    simulator {
    }

    tiles (scale: 2) {      
        standardTile("button", "device.button", width: 6, height: 1, decoration: "flat") {
        	state "label", label: "Button 1", action: "pushButton", icon: "https://github.com/crowbarz/SmartThings-crowbarz/raw/master/devicetypes/crowbarz/circle-slice-8.png", defaultState: true
        }
		standardTile("battery", "device.battery", decoration: "flat", width: 1, height: 1) {
			state "battery", label: '${currentValue}%', icon: "https://github.com/crowbarz/SmartThings-crowbarz/raw/master/devicetypes/crowbarz/battery.png", unit: "%"
		}
		valueTile("lastPressed", "device.lastPressed", decoration: "flat", width: 5, height: 1) {
        	state "default", label: 'Last Pressed: ${currentValue}', defaultState: true
        }
        
        main "button"
        details(["battery", "lastPressed", childDeviceTiles("childButtons")])
    }
}

// Map of prod/model to number of buttons
private getNumberOfButtonsProdModel() {[
		"0200" : [ // Hank Scene Controllers
        	"000B": 4,
            "0009": 1
        ],
        "1001" : 6,
		"0102" : 4,
		"0002" : 4
]}

def installed() {
	runIn(2, "initialize", [overwrite: true])
	// sendEvent(name: "button", value: "pushed", isStateChange: true)
}

def updated() {
	runIn(2, "initialize", [overwrite: true])
}

def initialize() {
	// log.debug("initialize()")

	// Determine number of buttons from prod/model map
    def numberOfButtonsProd = numberOfButtonsProdModel[zwaveInfo.prod]
	def numberOfButtons
    if (numberOfButtonsProd instanceof Integer) {
    	numberOfButtons = numberOfButtonsProd
    } else if (numberOfButtonsProd != null) {
        numberOfButtons = numberOfButtonsProd[zwaveInfo.model]
    } else {
		log.debug("Unknown number of buttons, assuming 1 button")
        numberOfButtons = 1
    }
    log.debug("Setting number of buttons to $numberOfButtons")
    state.numberOfButtons = numberOfButtons
    sendEvent(name: "numberOfButtons", value: numberOfButtons, displayed: false)
	
    // Create child button objects if there are more than one button
	// deleteChildButtons()
	if (numberOfButtons > 1 && !childDevices) {
        // log.debug("Creating Child Buttons")
        addChildButtons(numberOfButtons)
	}
}

private addChildButtons(numberOfButtons) {
	for(def endpoint : 1..numberOfButtons) {
		try {
			String childDni = "${device.deviceNetworkId}:$endpoint"
			def componentLabel = device.displayName + " (B${endpoint})"
			def child = addChildDevice("Hank Scene Controller Child Button", childDni, device.getHub().getId(), [
					completedSetup: true,
					label         : componentLabel,
					isComponent   : true,
					componentName : "button$endpoint",
					componentLabel: "Button $endpoint"
			])
            // log.debug("Added Child Button $componentLabel")
            child.sendEvent(name: "buttonId", value: endpoint, displayed: false)
            // child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
            // child.sendEvent(name: "button", value: "pushed", isStateChange: true)
		} catch(Exception e) {
			log.debug("Exception: ${e}")
		}
	}
}

private deleteChildButtons() {
    log.debug("Deleting Child Buttons")
    childDevices.each {
        try {
            deleteChildDevice(it.deviceNetworkId)
        }
        catch (e) {
            log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
        }
    }
}
    
def parse(String description) {
    // log.debug("Parsing description:$description")
    def event
    def results = []

	if (description.startsWith("Err")) {
        log.debug("An error has occurred: $description")
    } else {
        def cmd = zwave.parse(description)
        //log.debug "Parsed Command: $cmd"
        if (cmd) {
            event = zwaveEvent(cmd)
            if (event) {
                results += event
            }
		}
    }
    return results
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
        //log.debug ("SecurityMessageEncapsulation cmd:$cmd")
		//log.debug ("Secure command")
        def encapsulatedCommand = cmd.encapsulatedCommand([0x98: 1, 0x20: 1])

        if (encapsulatedCommand) {
            //log.debug ("SecurityMessageEncapsulation encapsulatedCommand:$encapsulatedCommand")
            return zwaveEvent(encapsulatedCommand)
        }
        log.debug ("No encapsulatedCommand Processed")
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.debug("Button Woke Up!")
    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []
    cmds += zwave.wakeUpV1.wakeUpNoMoreInformation()
    
    [event, encapSequence(cmds, 500)]
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	// log.debug( "CentralSceneNotification: $cmd")
    Integer sceneNumber = cmd.sceneNumber as Integer
	Integer keyAttributes = cmd.keyAttributes as Integer
    Integer sequenceNumber = cmd.sequenceNumber as Integer

	buttonEvent(sceneNumber, keyAttributes, sequenceNumber)
}

def buttonEvent(button, keyAttributes, sequenceNumber)
{
    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    def action
	sendEvent(name: "lastPressed", value: now, displayed: false)
	if (sequenceNumber == -1 || device.currentValue("lastSequence") != sequenceNumber) {
    	if (sequenceNumber != -1) {
			sendEvent(name: "lastSequence", value: sequenceNumber, displayed: false)
        }
		switch (keyAttributes) {
			case 0:
				action = "pushed"
				break
			case 1:			
				action = "double" // Released
				break
			case 2:
				action = "held"
				break
		}
		def event = createEvent(name: "button", value: action, descriptionText: "${device.displayName} button ${button} was ${action}", isStateChange: true, data: [buttonNumber: button, action: action])
        if (!childDevices) {
        	// Send event via parent device
	        // log.debug("Button $button was $action (parent)")
            return event
        } else {
        	// Send event via child device
            String childDni = "${device.deviceNetworkId}:$button"
            def child = childDevices.find { it.deviceNetworkId == childDni }
            if (child) {
                child?.sendEvent(event)
                // log.debug("Button $button was $action (child)")
            } else {
                log.debug("Could not find child for button $button, sending event to parent")
                return event
            }
        }
	} else {
		log.debug("Duplicate sequenceNumber ${cmd.sequenceNumber} dropped!")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    log.debug("BatteryReport: $cmd")
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
	if (val > 100) {
		val = 100
	}  	
	def isNew = (device.currentValue("battery") != val)    
	def result = []
	result << createEvent(name: "battery", value: val, unit: "%", display: isNew, isStateChange: isNew)	
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    log.debug("V1 ConfigurationReport cmd: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd) {
    log.debug("DeviceSpecificReport cmd: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug("ManufacturerSpecificReport cmd: $cmd")
}

private encapSequence(commands, delay=200) {
        delayBetween(commands.collect{ encap(it) }, delay)
}

private secure(physicalgraph.zwave.Command cmd) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private nonsecure(physicalgraph.zwave.Command cmd) {
		"5601${cmd.format()}0000"
}

private encap(physicalgraph.zwave.Command cmd) {
    def secureClasses = [0x5B, 0x85, 0x84, 0x5A, 0x86, 0x72, 0x71, 0x70 ,0x8E, 0x9C]
    if (secureClasses.find{ it == cmd.commandClassId }) {
        secure(cmd)
    } else {
        nonsecure(cmd)
    }
}

def pushButton()
{
	buttonEvent(1, 0, -1)
}
