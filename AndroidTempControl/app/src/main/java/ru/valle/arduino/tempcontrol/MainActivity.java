package ru.valle.arduino.tempcontrol;

import android.Manifest;
import android.app.LoaderManager;
import android.content.Loader;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<ArduinoState> {
    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSION = 1;
    private TextView tempView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start();
        tempView = (TextView) findViewById(R.id.temperature_view);
    }

    private void start() {
        boolean hasBtPermission = checkSelfPermission(Manifest.permission.BLUETOOTH) == PERMISSION_GRANTED;
        boolean hasLocPermission = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED;
        if (!hasBtPermission || !hasLocPermission) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMISSION);
        } else {
            getLoaderManager().initLoader(0, null, this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION && grantResults.length > 0 && !isDestroyed()) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PERMISSION_GRANTED) {
                    Log.e(TAG, "Permission " + permissions[i] + " denied");
                    finish();
                    return;
                }
            }
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    start();
                }
            });
        }
    }

    @Override
    public Loader<ArduinoState> onCreateLoader(int i, Bundle bundle) {
        return new ArduinoStateListener(this);
    }

    @Override
    public void onLoadFinished(Loader<ArduinoState> loader, ArduinoState state) {
        if (state.message != null) {
            tempView.setText(state.message);
        } else {
            Log.d(TAG, "temp " + state.temperature);
            if (state.desiredTemperature > 0 && state.temperature > 0) {
                tempView.setText(String.format("%s/%s C", state.temperature, state.desiredTemperature));
            } else if (state.temperature > 0) {
                tempView.setText(String.format("%s C", state.temperature));
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<ArduinoState> loader) {
        tempView.setText("");
    }
}
