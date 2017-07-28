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
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public final class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<ArduinoState> {
    private static final String TAG = "MainActivity";
    private static final int REQ_PERMISSION = 1;
    public static final int MIN_TEMPERATURE = -273;
    private TextView tempView;
    private View setTargetButton;
    private GraphView graph;

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
            graph.setVisibility(View.VISIBLE);
            if (state.desiredTemperature > MIN_TEMPERATURE && state.getTemperature() > MIN_TEMPERATURE) {
                setTargetButton.setVisibility(View.VISIBLE);
                tempView.setText(String.format("%s/%s C", state.getTemperature(), state.desiredTemperature));
                show(state);
            } else if (state.getTemperature() > MIN_TEMPERATURE) {
                tempView.setText(String.format("%s C", state.getTemperature()));
                show(state);
            }
        }
    }

    private static class Point implements DataPointInterface {
        private final float x;
        private final float value;

        Point(float x, float value) {
            this.x = x;
            this.value = value;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return value;
        }
    }

    private void show(ArduinoState state) {
        graph.removeAllSeries();
        if (state.temperatures != null && state.count > 1) {
            Point[] points = new Point[state.count];
            double approxTimespanMinutes = state.deltaSeconds[state.position] * state.count / 60;
            if (approxTimespanMinutes > 60) {
                approxTimespanMinutes = 60;
            } else if (approxTimespanMinutes < 1) {
                approxTimespanMinutes = 1;
            }
            double maxx = approxTimespanMinutes;
            graph.getViewport().setMinX(0);
            graph.getViewport().setMaxX(maxx);
            double x = maxx;
            for (int i = 0, p = state.position; i < state.count; i++, p--) {
                if (p < 0) {
                    p = state.temperatures.length - 1;
                }
                points[p] = new Point((float) x, state.temperatures[p]);
                x -= (state.deltaSeconds[p] & 0xff) / 60.0;
            }
            LineGraphSeries<Point> series = new LineGraphSeries<>(points);
            graph.addSeries(series);
            Point[] dpoints = new Point[2];
            dpoints[0] = new Point(0, state.desiredTemperature);
            dpoints[1] = new Point((float) maxx, state.desiredTemperature);
            LineGraphSeries<Point> desiredSeries = new LineGraphSeries<>(dpoints);
            desiredSeries.setColor(0xFFB22222);
            graph.addSeries(desiredSeries);
        }
    }

    @Override
    public void onLoaderReset(Loader<ArduinoState> loader) {
        tempView.setText("");
    }
}

