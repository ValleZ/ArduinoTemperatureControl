#include <OneWire.h>
#include <DallasTemperature.h>
#include <CurieBLE.h>
#include "CurieTimerOne.h"

#define ONE_WIRE_BUS 2
#define RELAY_PIN 4
#define PWM_TICKS_COUNT 5


OneWire ourWire(ONE_WIRE_BUS);
DallasTemperature sensors(&ourWire);
BLEService bleService("19B10010-E8F2-537E-4F6C-D104768A1214");
BLEFloatCharacteristic temperatureCharacteristic("19B10011-E8F2-537E-4F6C-D104768A1214", BLEWrite | BLERead | BLENotify);
BLECharCharacteristic relayCharacteristic("19B10012-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
bool relayOn;
float temperature;
float prevErrorP = -1000;
float prevErrorD = 0;
float desiredTemperature = 45;
float T = 150.0;
float KP = 0.5;
float KI = 1.0 / T;
float KD = 2 * T / 40;
float errorP = 0;
float errorI = 0;
float errorD = 0;
float control = 0;
int ticks = 0;
int heatQuants = 0;

void freqTick() {
  if (ticks % PWM_TICKS_COUNT == 0) {
    controlTick();
  }
  ticks++;
  if (heatQuants > 0) {
    heatQuants--;
    setRelay(true);
  } else {
    setRelay(false);
  }
}

void controlTick() {
  errorP = desiredTemperature - temperature;
  if (abs(errorP) < 2) {
    if (abs(KI * (errorI + errorP)) < 1.1) {
      errorI += errorP;
    }
  } else {
    errorI = 0;
  }
  errorD = ((prevErrorP > -1000 ? errorP - prevErrorP : 0) + prevErrorD) / 2;
  prevErrorP = errorP;
  prevErrorD = errorD;
  control = KP * errorP + KI * errorI + KD * errorD;
  heatQuants = min(PWM_TICKS_COUNT, max(0, PWM_TICKS_COUNT * control));

  Serial.print(temperature);
  Serial.print(", P ");
  Serial.print(errorP);
  Serial.print(", KP*errorP ");
  Serial.print(KP * errorP);
  Serial.print(", I ");
  Serial.print(errorI);
  Serial.print(", KI*errorI ");
  Serial.print(KI * errorI);
  Serial.print(", D ");
  Serial.print(errorD);
  Serial.print(", KD*errorD ");
  Serial.print(KD * errorD);
  Serial.print(", control ");
  Serial.print(control);
  Serial.print(", hq ");
  Serial.println(heatQuants);
}

void setup() {
  Serial.begin(9600);
  Serial.println("Init temperature sensor");
  sensors.begin();
  setPrecisionForAllSensors(12);
  /*
     9 bit 0.5 degrees C 93.75 mSec
    10 bit  0.25 degrees C  187.5 mSec
    11 bit  0.125 degrees C 375 mSec
    12 bit  0.0625 degrees C  750 mSec
  */
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  errorI = 0;
  prevErrorP = -1000;
  prevErrorD = 0;
  heatQuants = 0;

  temperature = desiredTemperature;
  relayOn = true;
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
  Serial.println("Bluetooth device active");
  CurieTimerOne.start(30000000 / PWM_TICKS_COUNT, &freqTick); //once in 30 seconds
  Serial.println("Timer started.");
}

void setRelay(bool on) {
  if (relayOn != on) {
    relayOn = on;
    digitalWrite(LED_BUILTIN, on ? HIGH : LOW);
    digitalWrite(RELAY_PIN, on ? LOW : HIGH);
  }
}

void setPrecisionForAllSensors(byte precision) {
  byte i;
  byte addr[8];
  Serial.println("Looking for 1-Wire devices...");
  while (ourWire.search(addr)) {
    Serial.println("Found \'1-Wire\' device with address:");
    for ( i = 0; i < 8; i++) {
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
}


void loop() {
  BLE.poll();
  sensors.requestTemperatures(); // Send the command to get temperatures
  temperature = sensors.getTempCByIndex(0);

  if (temperatureCharacteristic.value() != temperature) {
    temperatureCharacteristic.setValue(temperature);
  }

  if (temperatureCharacteristic.written()) {
    float requestedTemperature = temperatureCharacteristic.value();
    if (requestedTemperature > -50 && requestedTemperature < 150) {
      desiredTemperature = requestedTemperature;
      Serial.print("Requested temperature: ");
      Serial.println(requestedTemperature);
    } else {
      Serial.print("Requested temperature is invalid: ");
      Serial.println(requestedTemperature);
    }
    setRelay(temperatureCharacteristic.value());
  }

  //delay(1000);

}

