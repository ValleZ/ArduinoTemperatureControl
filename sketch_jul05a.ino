#include <OneWire.h>
#include <DallasTemperature.h>
#include <CurieBLE.h>

#define ONE_WIRE_BUS 2
#define RELAY_PIN 4

OneWire ourWire(ONE_WIRE_BUS);
DallasTemperature sensors(&ourWire);
BLEService bleService("19B10010-E8F2-537E-4F6C-D104768A1214");
BLEFloatCharacteristic temperatureCharacteristic("19B10011-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLECharCharacteristic relayCharacteristic("19B10012-E8F2-537E-4F6C-D104768A1214", BLEWrite | BLERead | BLENotify); 
bool relayOn;

void setup() {
  Serial.begin(9600);
  Serial.println("Init temperature sensor");
  sensors.begin();
  setPrecisionForAllSensors(11);
  /*
   * 9 bit 0.5 degrees C 93.75 mSec
10 bit  0.25 degrees C  187.5 mSec
11 bit  0.125 degrees C 375 mSec
12 bit  0.0625 degrees C  750 mSec
   */
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  setRelay(false);
  Serial.println("Init BLE");
  BLE.begin();
  BLE.setLocalName("Tempc");
  BLE.setAdvertisedService(bleService);
  bleService.addCharacteristic(temperatureCharacteristic);
  bleService.addCharacteristic(relayCharacteristic);
  BLE.addService(bleService);
  temperatureCharacteristic.setValue(0.0);
  relayCharacteristic.setValue(0);
  BLE.advertise();
  Serial.println("Bluetooth device active, waiting for connections...");
}

void setRelay(bool on) {
  relayOn = on;
  digitalWrite(LED_BUILTIN, on ? HIGH : LOW);
  digitalWrite(RELAY_PIN, on ? HIGH : LOW);
  if(on) {
    Serial.println("relay on");
  } else {
    Serial.println("relay off");
  }
}

void setPrecisionForAllSensors(byte precision) {
  byte i;
  byte addr[8];
  Serial.print("Looking for 1-Wire devices...\n\r");// "\n\r" is NewLine 
  while(ourWire.search(addr)) {
    Serial.print("\n\r\n\rFound \'1-Wire\' device with address:\n\r");
    for( i = 0; i < 8; i++) {
      if (addr[i] < 16) {
        Serial.print('0');
      }
      Serial.print(addr[i], HEX);
    }
    if (OneWire::crc8( addr, 7) != addr[7]) {
      Serial.print("CRC is not valid!\n\r");
      return;
    }
    sensors.setResolution(addr, precision);
  }
  Serial.println();
  Serial.println("Done");
  ourWire.reset_search();
  return;
}


void loop() {
  BLE.poll();
  sensors.requestTemperatures(); // Send the command to get temperatures
  float temperature = sensors.getTempCByIndex(0);
  
  if(temperatureCharacteristic.value() != temperature) {
    Serial.print(temperature);
    Serial.println(" C");
    temperatureCharacteristic.setValue(temperature);
    
  }

  if (relayCharacteristic.written()) {
      setRelay(relayCharacteristic.value());
  }
  
}

