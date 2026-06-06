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

/**
 * MainActivity is the single Activity in this app.
 *
 * Responsibilities:
 *   • Request RECORD_AUDIO permission at runtime (required on Android 6+).
 *   • Create and control a NoiseCapture instance.
 *   • Receive dB readings from the background thread and update Views safely
 *     using a Handler tied to the main (UI) thread.
 *   • Maintain a sliding 10-second window of readings for the rolling average.
 *   • Show / hide the red alert banner based on the rolling average.
 *   • Feed readings to NoiseChartView for live graphing.
 */
public class MainActivity extends AppCompatActivity {

    // ── Permission request code (arbitrary int, used in callback) ────────────
    private static final int REQUEST_RECORD_AUDIO = 101;

    // ── 10-second average: how many samples fit in 10 seconds?
    //    NoiseCapture fires ~10 readings/second (every 100 ms) → 100 samples
    private static final int SAMPLES_PER_10_SECONDS = 100;

    // ── Views ────────────────────────────────────────────────────────────────
    private TextView      tvCurrentDb;
    private TextView      tvAvgDb;
    private TextView      tvAlert;
    private Button        btnToggle;
    private NoiseChartView noiseChartView;

    // ── Logic components ─────────────────────────────────────────────────────
    private NoiseCapture noiseCapture;

    /**
     * A deque (double-ended queue) acts as a sliding window:
     *   • New readings are added to the back.
     *   • When size exceeds SAMPLES_PER_10_SECONDS, the oldest is removed from the front.
     * This gives us exactly the last 10 seconds worth of readings at all times.
     */
    private final Deque<Double> rollingWindow = new ArrayDeque<>();

    /** Posts Runnables from the background capture thread back to the UI thread. */
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isMonitoring = false;

    // ── Activity lifecycle ────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Wire up Views
        tvCurrentDb    = findViewById(R.id.tvCurrentDb);
        tvAvgDb        = findViewById(R.id.tvAvgDb);
        tvAlert        = findViewById(R.id.tvAlert);
        btnToggle      = findViewById(R.id.btnToggle);
        noiseChartView = findViewById(R.id.noiseChartView);

        // Start/Stop button handler
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
        // Always release the microphone when the Activity is no longer visible.
        // This is good practice — another app might need it, and it drains battery.
        stopMonitoring();
    }

    // ── Permission handling ───────────────────────────────────────────────────

    /**
     * Check whether RECORD_AUDIO is already granted.
     * If not, show the system permission dialog.
     * If yes, start immediately.
     */
    private void requestMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted — go straight to monitoring
            startMonitoring();
        } else {
            // Ask the user — result arrives in onRequestPermissionsResult()
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

    // ── Monitoring control ────────────────────────────────────────────────────

    private void startMonitoring() {
        if (isMonitoring) return;
        isMonitoring = true;

        // Reset state from any previous session
        rollingWindow.clear();
        noiseChartView.clear();
        tvAlert.setVisibility(android.view.View.GONE);

        // Update button label
        btnToggle.setText("Stop Monitoring");

        // Create and start the microphone capture object.
        // The lambda is the OnNoiseReadingListener — called on the capture thread.
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

    // ── Noise reading handler (called from background thread) ─────────────────

    /**
     * Called by NoiseCapture every ~100 ms with the latest dB value.
     * IMPORTANT: this runs on the capture background thread, NOT the UI thread.
     * We must use uiHandler.post() before touching any View.
     *
     * @param db  Latest noise reading in decibels.
     */
    private void onNewReading(double db) {

        // 1. Update the rolling 10-second window
        rollingWindow.addLast(db);
        if (rollingWindow.size() > SAMPLES_PER_10_SECONDS) {
            rollingWindow.removeFirst(); // drop oldest sample
        }

        // 2. Calculate the rolling average
        double sum = 0;
        for (double sample : rollingWindow) {
            sum += sample;
        }
        double average = sum / rollingWindow.size();

        // 3. Capture final values for use inside the UI lambda
        //    (lambdas can only capture effectively-final variables)
        final double currentDb = db;
        final double avgDb     = average;

        // 4. Push all UI updates to the main thread
        uiHandler.post(() -> {

            // Update the large current reading
            tvCurrentDb.setText(String.format(Locale.US, "%.1f dB", currentDb));

            // Update the rolling average label
            tvAvgDb.setText(String.format(Locale.US, "10s Average: %.1f dB", avgDb));

            // Show/hide the red alert banner
            if (avgDb > 70.0) {
                tvAlert.setVisibility(android.view.View.VISIBLE);
            } else {
                tvAlert.setVisibility(android.view.View.GONE);
            }

            // Feed the chart — NoiseChartView.addReading() calls postInvalidate()
            noiseChartView.addReading(currentDb);
        });
    }
}