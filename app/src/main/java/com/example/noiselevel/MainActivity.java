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

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int WINDOW_SIZE = 100;

    private TextView currentLevelText;
    private TextView averageText;
    private TextView warningText;
    private Button toggleButton;
    private NoiseChartView chartView;

    private NoiseCapture audioCapture;
    private final Deque<Double> dataWindow = new ArrayDeque<>();
    private Handler mainHandler;

    private boolean isActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        
        currentLevelText = findViewById(R.id.tvCurrentDb);
        averageText = findViewById(R.id.tvAvgDb);
        warningText = findViewById(R.id.tvAlert);
        toggleButton = findViewById(R.id.btnToggle);
        chartView = findViewById(R.id.noiseChartView);

        toggleButton.setOnClickListener(v -> {
            if (isActive) {
                stopRecording();
            } else {
                checkPermissionAndStart();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRecording();
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                Toast.makeText(this, "Microphone permission is required to measure noise levels.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startRecording() {
        if (isActive) return;
        
        isActive = true;
        dataWindow.clear();
        chartView.clear();
        warningText.setVisibility(android.view.View.GONE);
        toggleButton.setText("Stop Monitoring");

        audioCapture = new NoiseCapture(this::handleNewSample);
        audioCapture.start();
    }

    private void stopRecording() {
        if (!isActive) return;
        
        isActive = false;
        if (audioCapture != null) {
            audioCapture.stop();
            audioCapture = null;
        }
        toggleButton.setText("Start Monitoring");
    }

    private void handleNewSample(double decibels) {
        dataWindow.addLast(decibels);
        
        if (dataWindow.size() > WINDOW_SIZE) {
            dataWindow.removeFirst();
        }

        double total = 0;
        for (double val : dataWindow) {
            total += val;
        }
        double avg = total / dataWindow.size();

        final double currentLevel = decibels;
        final double averageLevel = avg;

        mainHandler.post(() -> {
            currentLevelText.setText(String.format(Locale.US, "%.1f dB", currentLevel));
            averageText.setText(String.format(Locale.US, "10s Average: %.1f dB", averageLevel));

            if (averageLevel > 70.0) {
                warningText.setVisibility(android.view.View.VISIBLE);
            } else {
                warningText.setVisibility(android.view.View.GONE);
            }
            
            chartView.addReading(currentLevel);
        });
    }
}