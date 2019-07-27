/**
 *  Hank HKZW-SCN04 DTH by Emil Åkered (@emilakered)
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
 *	2018-03-03
 *	- Initial release
 *
 */
 
metadata {
    definition (name: "Hank Four-key Scene Controller", namespace: "emilakered", author: "Emil Åkered") {
        capability "Actuator"
        capability "Battery"
        capability "Button"
        capability "Configuration"
        capability "Holdable Button" 
        
        attribute "lastPressed", "string"
		attribute "numberOfButtons", "number"
		attribute "lastSequence", "number"
		
		fingerprint mfr: "0208", prod: "0200", model: "000B"
        fingerprint deviceId: "0x1801", inClusters: "0x5E,0x86,0x72,0x5B,0x59,0x85,0x80,0x84,0x73,0x70,0x7A,0x5A", outClusters: "0x26"
    }

    simulator {
    }

    tiles (scale: 2) {      
        multiAttributeTile(name:"button", type:"lighting", width:6, height:4, canChangeIcon:true) {
            tileAttribute("device.button", key: "PRIMARY_CONTROL"){
				attributeState("pushed", label:'Pushed', backgroundColor:"#00A0DC")
				attributeState("held", label:'Held', backgroundColor:"#4cbce6")
				attributeState("released", label:'released', backgroundColor:"#99d9f1")
            }
			tileAttribute("device.lastPressed", key: "SECONDARY_CONTROL") {
                attributeState "default", label:'Last used: ${currentValue}'
            }
        }	
        
        valueTile("battery", "device.battery", decoration: "flat", width: 2, height: 2){
			state "battery", label:'${currentValue}% battery', unit:"%"
		}
        
        main "button"
        details(["button","battery"])
    }
}

def parse(String description) {
    //log.debug ("Parsing description:$description")
    def event
    def results = []
	
	def numberOfButtonsVal = device.currentValue("numberOfButtons")
    if ( !state.numberOfButtons || !numberOfButtonsVal || numberOfButtonsVal !=4) {
        log.debug ("Setting number of buttons to 4")
        state.numberOfButtons = "4"
        event = createEvent(name: "numberOfButtons", value: "4", displayed: false)
        if (event) {
            results += event
        }
    }
    
    //log.debug("RAW command: $description")
    if (description.startsWith("Err")) {
        log.debug("An error has occurred")
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
        log.debug ("No encalsulatedCommand Processed")
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.debug("Button Woke Up!")
    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []
    cmds += zwave.wakeUpV1.wakeUpNoMoreInformation()
    
    [event, encapSequence(cmds, 500)]
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    //log.debug( "CentralSceneNotification: $cmd")
	def now = new Date().format("yyyy MMM dd EEE HH:mm:ss", location.timeZone)	
	sendEvent(name: "lastpressed", value: now, displayed: false)
	
	if (device.currentValue("lastSequence") != cmd.sequenceNumber){

		sendEvent(name: "lastSequence", value: cmd.sequenceNumber, displayed: false)
		Integer button = cmd.sceneNumber
		
		if (cmd.keyAttributes == 0) {
			sendEvent(name: "button", value: "pushed", data: [buttonNumber: button], descriptionText: "$device.displayName button $button was pushed", isStateChange: true)
			log.debug( "Button $button pushed" )
		}
		if (cmd.keyAttributes == 2) {
			sendEvent(name: "button", value: "held", data: [buttonNumber: button], descriptionText: "$device.displayName button was $button held", isStateChange: true)
			log.debug( "Button $button held" )
		}
		if (cmd.keyAttributes == 1) {
			sendEvent(name: "button", value: "released", data: [buttonNumber: button], descriptionText: "$device.displayName button was $button released", isStateChange: true)
			log.debug( "Button $button released" )
		}
	} else {
		log.debug( "Duplicate sequenceNumber dropped!")
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