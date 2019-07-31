/**
 *  Copyright 2015 NodOn
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
 *  2019-07-29
 *  - Create child devices (to workaround button data not being passed through ST REST API)
 *  - Updated tiles to support child devices
 */

/* Source: https://raw.githubusercontent.com/Alutun/SmartThingsPublic/master/devicetypes/nodon/wall-switch.src/wall-switch.groovy */

metadata {
	definition (name: "NodOn Wall Switch", namespace: "NodOn", author: "Alexis Lutun") {
        capability "Actuator"
        capability "Sensor"
		capability "Button"
		capability "Configuration"
        capability "Sleep Sensor"
		capability "Battery"

		attribute "numberOfButtons", "number"

		command "pushButton"
		command	"refresh"

	fingerprint mfr: "0165", prod: "0002", model: "0003", cc: "5E,85,59,80,5B,70,5A,72,73,86,84", ccOut: "5E,5B,2B,27,22,20,26,84" // Wall Switch
    //LEGACY FINGERPRINT GENERIC FOR THREE : fingerprint deviceId: "0x0101", inClusters: "0x5E,0x85,0x59,0x80,0x5B,0x70,0x5A,0x72,0x73,0x86,0x84,0xEF,0x5E,0x5B,0x2B,0x27,0x22,0x20,0x26,0x84"

    }

	tiles(scale: 2) {
        standardTile("button", "device.button", width: 6, height: 1, decoration: "flat", canChangeIcon: true) {
            state "default", label: "Button 1", action: "pushButton", icon:"http://nodon.fr/smarthings/wall-switch/wallswitchfullicon.png", defaultState: true, backgroundColor: "#ffffff"
        }
        multiAttributeTile(name:"BatteryTile", type: "generic", width: 6, height: 4) {
       		tileAttribute ("device.battery", key: "PRIMARY_CONTROL") {
        		attributeState "default", backgroundColor: "#f58220", decoration: "flat", icon:"http://nodon.fr/smarthings/wall-switch/wallswitchfullicon.png"
           	}
        	tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
        		attributeState "default", label:'${currentValue}% battery', unit:"%"
            }
		}
        standardTile("refresh", "generic", inactiveLabel: false, decoration: "flat", width: 2, height: 2)
        {
			state "default", label:'', action: "refresh", icon:"st.secondary.refresh"
		}

        standardTile("configure", "device.Configuration", decoration: "flat", width: 2, height: 2)
        {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
		main "button"
		// details(["BatteryTile", "configure", "refresh", "childButtons"])
		details(["BatteryTile", childDeviceTiles("childButtons")])
	}
}
def installed()
{
	runIn(2, "initialize", [overwrite: true])
	// sendEvent(name: "button", value: "pushed", isStateChange: true)
}

def updated() {
	runIn(2, "initialize", [overwrite: true])
}

def initialize()
{
    // log.debug "initialize()"
	state.myRefresh = 0
    state.batteryRefresh = 0
    state.numberOfButtons = 4
	sendEvent(name: "numberOfButtons", value: numberOfButtons, displayed: false)

	// deleteChildButtons()
	if (!childDevices) {
//		log.debug("Creating Child Buttons")
		addChildButtons(state.numberOfButtons)
	}
}

private addChildButtons(numberOfButtons) {
	for(def endpoint : 1..numberOfButtons) {
		try {
			String childDni = "${device.deviceNetworkId}:$endpoint"
			def componentLabel = device.displayName + " (B${endpoint})"
			def child = addChildDevice("NodOn Wall Switch Child Button", childDni, device.getHub().getId(), [
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
    
def parse(String description)
{
	def results = []
	if (description.startsWith("Err"))
    {
	    results = createEvent(descriptionText:description, displayed:true)
	}
    else
    {
		def cmd = zwave.parse(description, [0x5B: 1, 0x80: 1, 0x84: 1]) //Central Scene , battery, wake up
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

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{

	def results = [createEvent(descriptionText: "$device.displayName woke up", isStateChange: false)]
	def prevBattery = device.currentState("battery")
   	if (!prevBattery || (new Date().time - prevBattery.date.time)/60000 >= 60 * 53 || state.batteryRefresh == 1)  //
	{
		results << response(zwave.batteryV1.batteryGet().format())
        createEvent(name: "battery", value: "10", descriptionText: "battery is now ${currentValue}%", isStateChange: true, displayed: true)
        state.batteryRefresh == 0
	}
    if (state.myRefresh == 1)
    {
    	results << response(zwave.configurationV1.configurationSet(parameterNumber: 8, scaledConfigurationValue:2).format())
        results << response(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format())
        state.myRefresh = 0
    }
	results << response(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
	return results
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification  cmd)
{
    Integer sceneNumber = cmd.sceneNumber as Integer
	Integer keyAttributes = cmd.keyAttributes as Integer
    Integer sequenceNumber = cmd.sequenceNumber as Integer

	buttonEvent(sceneNumber, keyAttributes)
}

def buttonEvent(button, keyAttributes)
{
	// NOTE: does not check sequence number
	def action
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
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd)
{
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF)
    {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
	}
    else
    {
		map.value = cmd.batteryLevel
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	[ descriptionText: "$device.displayName: $cmd", linkText:device.displayName, displayed: false ]
}

def refresh()
{
	state.batteryRefresh = 1
}

def configure()
{
	state.myRefresh = 1
}

def pushButton()
{
	buttonEvent(1, 0)
}