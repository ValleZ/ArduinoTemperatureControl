#include <OneWire.h>
#include <DallasTemperature.h>
#define ONE_WIRE_BUS 2

OneWire ourWire(ONE_WIRE_BUS);
DallasTemperature sensors(&ourWire);

void setup() {
  Serial.begin(9600);
  Serial.println("Start");
  sensors.begin();
  setPrecisionForAllSensors(10);
  /*
   * 9 bit 0.5 degrees C 93.75 mSec
10 bit  0.25 degrees C  187.5 mSec
11 bit  0.125 degrees C 375 mSec
12 bit  0.0625 degrees C  750 mSec
   */
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
  sensors.requestTemperatures(); // Send the command to get temperatures
  Serial.print(sensors.getTempCByIndex(0));
  Serial.println(" C");
  delay(5000);
}

