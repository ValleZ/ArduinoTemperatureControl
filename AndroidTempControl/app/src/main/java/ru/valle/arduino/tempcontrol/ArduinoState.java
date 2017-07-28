package ru.valle.arduino.tempcontrol;

import static ru.valle.arduino.tempcontrol.MainActivity.MIN_TEMPERATURE;

final class ArduinoState {
    final String message;
    final float desiredTemperature;
    final float[] deltaSeconds;
    final float[] temperatures;
    final int position, count;

    public ArduinoState(int position, int count, float[] temperatures, float[] deltaSeconds, float desiredTemperature) {
        this(position, count, temperatures, deltaSeconds, desiredTemperature, null);
    }

    public ArduinoState(String message) {
        this(-1, 0, null, null, MIN_TEMPERATURE, message);

    }

    public ArduinoState(int position, int count, float[] temperatures, float[] deltaSeconds, float desiredTemperature, String errorMessage) {
        this.position = position;
        this.count = count;
        this.temperatures = temperatures;
        this.deltaSeconds = deltaSeconds;
        this.desiredTemperature = desiredTemperature;
        this.message = errorMessage;
    }

    public float getTemperature() {
        return temperatures != null ? temperatures[position] : MIN_TEMPERATURE - 1;
    }
}
