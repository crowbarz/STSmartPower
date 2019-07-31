/**
 *  Child Button
 *
 *  Copyright 2017 SmartThings
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
 */
metadata {
	definition (name: "Hank Scene Controller Child Button", namespace: "crowbarz", author: "Crowbar Z") {
        capability "Actuator"
        capability "Sensor"
		capability "Button"

		attribute "buttonId", "number"

		command "pushButton"
	}

	tiles(scale: 2) {
        standardTile("button", "device.button", width: 6, height: 1, decoration: "flat") {
        	state "label", label: "Push", action: "pushButton", icon: "https://github.com/crowbarz/SmartThings-crowbarz/raw/master/devicetypes/crowbarz/circle-slice-8.png", defaultState: true
        }
	}
}

def pushButton() {
	def button = device.currentValue("buttonId")
	//log.debug("UX button pushed for ${device.name} ${button}")
    parent.buttonEvent(button, 0, -1)
}
