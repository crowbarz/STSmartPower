/**
 *  Copyright 2014 SmartThings
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
 */
metadata {
    // Automatically generated. Make future change here.
    definition (name: "MQTT Temperature Sensor", namespace: "crowbarz", author: "SmartThings / Crowbar") {
        capability "Temperature Measurement"
        capability "Sensor"
        capability "Health Check"

        command "setTemperature", ["number"]
        command "setStatus", ["number"]
    }

    // UI tile definitions
    tiles {
        valueTile("temperature", "device.temperature", canChangeIcon: true, width: 2, height: 2) {
            state("temperature", label:'${currentValue}°', unit:"C",
                backgroundColors:[
 					// Celsius
					[value: 0, color: "#153591"],
					[value: 7, color: "#1e9cbb"],
					[value: 15, color: "#90d2a7"],
					[value: 23, color: "#44b621"],
					[value: 28, color: "#f1d801"],
					[value: 35, color: "#d04e00"],
					[value: 37, color: "#bc2323"],
					// Fahrenheit
					[value: 40, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
                ]
            )
        }
        main "temperature"
        details("temperature")
    }
}

// Parse incoming device messages to generate events
def parse(String description) {
    def pair = description.split(":")
    createEvent(name: pair[0].trim(), value: pair[1].trim(), unit:"C")
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    sendEvent(name: "DeviceWatch-DeviceStatus", value: "online")
    sendEvent(name: "healthStatus", value: "online")
    sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "cloud", scheme:"untracked"].encodeAsJson(), displayed: false)
    if (!device.currentState("temperature")) {
        setTemperature(getTemperature())
    }
}

def setTemperature(value) {
    sendEvent(name:"temperature", value: value, unit: getTemperatureScale())
}

private getTemperature() {
    def ts = device.currentState("temperature")
    Integer value = ts ? ts.integerValue : 20
    return value
}

// MQTT support

def setStatus(type, status) {
    log.debug("Setting status to ${status}")
    setTemperature(status)
}
