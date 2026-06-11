package com.example.noiselevel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 101;
    private static final int SAMPLES_PER_10_SECONDS = 100;

    private TextView      tvCurrentDb;
    private TextView      tvAvgDb;
    private TextView      tvAlert;
    private Button        btnToggle;
    private NoiseChartView noiseChartView;

    private NoiseCapture noiseCapture;

    private final Deque<Double> rollingWindow = new ArrayDeque<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCurrentDb    = findViewById(R.id.tvCurrentDb);
        tvAvgDb        = findViewById(R.id.tvAvgDb);
        tvAlert        = findViewById(R.id.tvAlert);
        btnToggle      = findViewById(R.id.btnToggle);
        noiseChartView = findViewById(R.id.noiseChartView);

        btnToggle.setOnClickListener(v -> {
            if (isMonitoring) {
                stopMonitoring();
            } else {
                requestMicPermissionAndStart();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopMonitoring();
    }

    private void requestMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startMonitoring();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring();
            } else {
                Toast.makeText(this,
                        "Microphone permission is required to measure noise levels.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startMonitoring() {
        if (isMonitoring) return;
        isMonitoring = true;

        rollingWindow.clear();
        noiseChartView.clear();
        tvAlert.setVisibility(android.view.View.GONE);

        btnToggle.setText("Stop Monitoring");

        noiseCapture = new NoiseCapture(this::onNewReading);
        noiseCapture.start();
    }

    private void stopMonitoring() {
        if (!isMonitoring) return;
        isMonitoring = false;

        if (noiseCapture != null) {
            noiseCapture.stop();
            noiseCapture = null;
        }

        btnToggle.setText("Start Monitoring");
    }

    private void onNewReading(double db) {

        rollingWindow.addLast(db);
        if (rollingWindow.size() > SAMPLES_PER_10_SECONDS) {
            rollingWindow.removeFirst();
        }

        double sum = 0;
        for (double sample : rollingWindow) {
            sum += sample;
        }
        double average = sum / rollingWindow.size();

        final double currentDb = db;
        final double avgDb     = average;

        uiHandler.post(() -> {

            tvCurrentDb.setText(String.format(Locale.US, "%.1f dB", currentDb));
            tvAvgDb.setText(String.format(Locale.US, "10s Average: %.1f dB", avgDb));

            if (avgDb > 70.0) {
                tvAlert.setVisibility(android.view.View.VISIBLE);
            } else {
                tvAlert.setVisibility(android.view.View.GONE);
            }

            noiseChartView.addReading(currentDb);
        });
    }
}