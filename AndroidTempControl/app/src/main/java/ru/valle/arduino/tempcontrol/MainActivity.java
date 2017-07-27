package ru.valle.arduino.tempcontrol;

import android.Manifest;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Loader;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<ArduinoState> {
    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSION = 1;
    public static final int MIN_TEMPERATURE = -273;
    private TextView tempView;
    private View setTargetButton;
    private GraphView graph;
    private LineGraphSeries<DataPoint> series;
    private double graphX;
    private long lastTs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tempView = (TextView) findViewById(R.id.temperature_view);
        setTargetButton = findViewById(R.id.set_target_button);
        setTargetButton.setOnClickListener(new View.OnClickListener() {
            AlertDialog alert;

            @Override
            public void onClick(final View view) {
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
                alertBuilder.setMessage(R.string.edit_temp_target_title);
                alertBuilder.setView(R.layout.edit_temp);
                alertBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        View inputView = alert.findViewById(R.id.input);
                        if (inputView != null) {
                            String valueStr = String.valueOf(((EditText) inputView).getText());
                            double value = Double.parseDouble(valueStr);
                            Loader<ArduinoState> loader = getLoaderManager().getLoader(0);
                            ((ArduinoStateListener) loader).sendDesiredTemperature((float) value);
                        }
                        alert = null;
                    }
                });
                alertBuilder.setNegativeButton(R.string.cancel, null);
                alert = alertBuilder.show();
            }
        });
        graph = (GraphView) findViewById(R.id.graph);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(60);
        graph.getGridLabelRenderer().setLabelVerticalWidth(100);
        series = new LineGraphSeries<>();
        series.setDrawBackground(false);
        series.setDrawDataPoints(false);
        graph.addSeries(series);
        graph.setVisibility(View.INVISIBLE);
        start();
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
            setTargetButton.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "temp " + state.temperature);
            graph.setVisibility(View.VISIBLE);
            if (state.desiredTemperature > MIN_TEMPERATURE && state.temperature > MIN_TEMPERATURE) {
                setTargetButton.setVisibility(View.VISIBLE);
                tempView.setText(String.format("%s/%s C", state.temperature, state.desiredTemperature));
                addPoint(state);
            } else if (state.temperature > MIN_TEMPERATURE) {
                tempView.setText(String.format("%s C", state.temperature));
                addPoint(state);
            }
        }
    }

    private void addPoint(ArduinoState state) {
        long time = SystemClock.elapsedRealtime();
        double timeDiffMinutes = lastTs == 0 ? 0 : (time - lastTs) / 60_000.0;
        lastTs = time;
        graphX += timeDiffMinutes;
        series.appendData(new DataPoint(graphX, state.temperature), false, 1000);
    }

    @Override
    public void onLoaderReset(Loader<ArduinoState> loader) {
        tempView.setText("");
    }
}
