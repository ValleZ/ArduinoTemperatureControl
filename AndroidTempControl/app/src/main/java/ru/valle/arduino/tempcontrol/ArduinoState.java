package ru.valle.arduino.tempcontrol;

final class ArduinoState {
    final float temperature;
    final String message;
    final float desiredTemperature;

    public ArduinoState(float temperature, float desiredTemperature) {
        this(temperature, desiredTemperature, null);
    }

    public ArduinoState(String message) {
        this(Float.NaN, -1, message);

    }

    public ArduinoState(float temperature, float desiredTemperature, String errorMessage) {
        this.temperature = temperature;
        this.desiredTemperature = desiredTemperature;
        this.message = errorMessage;
    }
}
