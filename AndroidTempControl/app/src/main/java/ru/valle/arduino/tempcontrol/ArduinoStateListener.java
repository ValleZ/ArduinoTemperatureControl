package ru.valle.arduino.tempcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Loader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;
import static ru.valle.arduino.tempcontrol.MainActivity.MIN_TEMPERATURE;

final class ArduinoStateListener extends Loader<ArduinoState> {
    private static final String TAG = "ArduinoStateListener";
    private static final UUID STATE_CHARACTERISTIC_UUID = UUID.fromString("19B10011-E8F2-537E-4F6C-D104768A1214");
    private static final UUID SET_TEMPERATURE_CHARACTERISTIC_UUID = UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1214");
    private BluetoothLeScanner scanner;
    private BluetoothDevice arduino;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic stateCharacteristic, setTemperatureCharacteristic;
    private boolean scanning;
    private float temperature = Float.MIN_VALUE;
    private float desiredTemperature = Float.MIN_VALUE;
    private boolean shouldSendDesiredTemperature;
    private long desiredTemperatureReadTs;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (callbackType != CALLBACK_TYPE_MATCH_LOST) {
                Log.d(TAG, "found " + result);
                final BluetoothDevice device = result.getDevice();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAbandoned() && !isReset()) {
                            stopScan();
                            arduino = device;
                            connectGatt();
                        }
                    }
                });
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            super.onScanFailed(errorCode);
            Log.w(TAG, "Scan failed " + errorCode);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    stopScan();
                    deliverResult(new ArduinoState("Scan failed, errorCode " + errorCode));
                }
            });
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange " + status + " state " + newState);
            if (status == GATT_SUCCESS && newState == STATE_CONNECTED) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ArduinoStateListener.this.gatt = gatt;
                        discoverServices();
                    }
                });
            } else {
                handler.postDelayed(connectGattCallback, 1000);
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered " + status);
            if (status == GATT_SUCCESS) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        BluetoothGattService gattService = gatt.getService(UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214"));
                        if (gattService == null) {
                            Log.e(TAG, "Gatt service not found");
                            deliverResult(new ArduinoState("Gatt service not found"));
                        } else {
                            deliverResult(new ArduinoState("Reading"));
                            ArduinoStateListener.this.stateCharacteristic = gattService.getCharacteristic(STATE_CHARACTERISTIC_UUID);
                            ArduinoStateListener.this.setTemperatureCharacteristic = gattService.getCharacteristic(SET_TEMPERATURE_CHARACTERISTIC_UUID);
                            dataTransmit();
                        }

                    }
                });
            } else {
                handler.postDelayed(discoverServicesCallback, 1000);
            }
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            handler.post(new Runnable() {
                @Override

                public void run() {
                    if (!isAbandoned() && !isReset()) {
                        if (status == GATT_SUCCESS) {
                            byte[] value = characteristic.getValue();
                            if (value != null && value.length > 0) {
                                if (characteristic.getUuid().equals(STATE_CHARACTERISTIC_UUID)) {
                                    temperature = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                } else if (characteristic.getUuid().equals(SET_TEMPERATURE_CHARACTERISTIC_UUID)) {
                                    desiredTemperature = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                                }
                                onTemperatureRead(temperature, desiredTemperature);
                            } else {
                                Log.w(TAG, "stateCharacteristic has no value");
                            }
                        }
                        handler.postDelayed(dataTransmitCallback, 5000);
                    }
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            handler.post(new Runnable() {
                @Override

                public void run() {
                    if (!isAbandoned() && !isReset()) {
                        if (status == GATT_SUCCESS) {
                            shouldSendDesiredTemperature = false;
                            Log.d(TAG, "write successful");
                            dataTransmit();
                        } else {
                            handler.postDelayed(dataTransmitCallback, 5000);
                        }
                    }
                }
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged " + Arrays.toString(characteristic.getValue()));
        }
    };

    private Runnable connectGattCallback = new Runnable() {
        @Override
        public void run() {
            connectGatt();
        }
    };

    private Runnable discoverServicesCallback = new Runnable() {
        @Override
        public void run() {
            discoverServices();
        }
    };

    private Runnable dataTransmitCallback = new Runnable() {
        @Override
        public void run() {
            dataTransmit();
        }
    };

    ArduinoStateListener(Context context) {
        super(context);
    }

    @Override
    protected void onStartLoading() {
        Log.d(TAG, "onStartLoading");
        if (scanner == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (!bluetoothAdapter.isEnabled()) {
                deliverResult(new ArduinoState("Enabling BT..."));
                if (!bluetoothAdapter.enable()) {
                    deliverResult(new ArduinoState("Enabling BT failed"));
                }
            }
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) {
                deliverResult(new ArduinoState("Cannot get LE scanner"));
            }
        }
        if (arduino == null && scanner != null) {
            ScanFilter filter = new ScanFilter.Builder().setDeviceName("Tempc").build();
            ScanSettings settings = new ScanSettings.Builder().setNumOfMatches(1).build();
            scanning = true;
            deliverResult(new ArduinoState("Scanning"));
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
        }
    }

    @Override
    protected void onReset() {
        Log.d(TAG, "onReset");
        stopScan();
        stateCharacteristic = null;
        if (gatt != null) {
            gatt.disconnect();
            gatt = null;
        }
        handler.removeCallbacks(connectGattCallback);
        handler.removeCallbacks(discoverServicesCallback);
        handler.removeCallbacks(dataTransmitCallback);
        temperature = Float.MIN_VALUE;
        desiredTemperatureReadTs = SystemClock.elapsedRealtime();
    }

    private void stopScan() {
        if (scanning) {
            scanning = false;
            scanner.stopScan(scanCallback);
        }
    }

    private void connectGatt() {
        shouldSendDesiredTemperature = false;
        desiredTemperature = MIN_TEMPERATURE;
        temperature = MIN_TEMPERATURE;
        if (arduino != null) {
            if (!isAbandoned() && !isReset()) {
                deliverResult(new ArduinoState("Connecting to " + arduino.getAddress()));
                arduino.connectGatt(getContext(), true, gattCallback);
            } else {
                Log.w(TAG, "don't connectGatt");
            }
        } else {
            Log.e(TAG, "Cannot connectGatt, BT device is null");
        }
    }

    private void discoverServices() {
        if (!isAbandoned() && !isReset() && gatt != null) {
            if (!gatt.discoverServices()) {
                Log.w(TAG, "discoverServices rejected");
            } else {
                deliverResult(new ArduinoState("Discovering services"));
                Log.d(TAG, "Discovering services");
            }
        } else {
            Log.w(TAG, "don't discoverServices");
        }
    }

    private void dataTransmit() {
        if (!isAbandoned() && !isReset() && gatt != null && stateCharacteristic != null) {
            if (shouldSendDesiredTemperature) {
                byte[] temperatureBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(desiredTemperature).array();
                setTemperatureCharacteristic.setValue(temperatureBytes);
                if (!gatt.writeCharacteristic(setTemperatureCharacteristic)) {
                    Log.w(TAG, "setTemperatureCharacteristic write refused");
                }
            } else if (desiredTemperature < 0 || SystemClock.elapsedRealtime() - desiredTemperatureReadTs > TimeUnit.MINUTES.toMillis(10)) {
                desiredTemperatureReadTs = SystemClock.elapsedRealtime();
                if (!gatt.readCharacteristic(setTemperatureCharacteristic)) {
                    Log.w(TAG, "setTemperatureCharacteristic read refused");
                }
            } else if (!gatt.readCharacteristic(stateCharacteristic)) {
                Log.w(TAG, "stateCharacteristic read refused");
            }
        } else {
            Log.w(TAG, "don't read temp from " + stateCharacteristic);
        }
    }

    void sendDesiredTemperature(float value) {
        desiredTemperature = value;
        shouldSendDesiredTemperature = true;
    }

    private long lastTs;
    private int pos, count;
    private static final int DATA_POINTS = 400;
    private float[] deltaSeconds = new float[DATA_POINTS];
    private float[] temp = new float[DATA_POINTS];

    private void onTemperatureRead(float temperature, float desiredTemperature) {
        if (temperature > MIN_TEMPERATURE) {
            long time = SystemClock.elapsedRealtime();
            double diffSeconds = lastTs == 0 ? 0 : (time - lastTs) / 1_000.0;
            double timeDiffSeconds = diffSeconds > 255 ? 255 : diffSeconds;
            deltaSeconds[pos] = (float) timeDiffSeconds;
            temp[pos] = temperature;
            count++;
            if (count >= DATA_POINTS) {
                count = DATA_POINTS;
            }
            ArduinoStateListener.this.deliverResult(new ArduinoState(pos, count, temp, deltaSeconds, desiredTemperature));
            pos++;
            if (pos >= DATA_POINTS) {
                pos = 0;
            }
            lastTs = time;
        }
    }

}

