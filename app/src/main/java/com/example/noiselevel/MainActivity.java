package com.example.noiselevel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private TextView dbText, avgText, alertText;
    private Button btnToggle;
    private NoiseChartView chartView;
    private NoiseCapture capture;
    private ArrayList<Double> window = new ArrayList<>();
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbText = findViewById(R.id.tvCurrentDb);
        avgText = findViewById(R.id.tvAvgDb);
        alertText = findViewById(R.id.tvAlert);
        btnToggle = findViewById(R.id.btnToggle);
        chartView = findViewById(R.id.noiseChartView);

        btnToggle.setOnClickListener(v -> {
            if (isRunning) {
                stop();
            } else {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                } else {
                    start();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) start();
    }

    private void start() {
        isRunning = true;
        btnToggle.setText("Stop");
        window.clear();
        chartView.clear();
        
        capture = new NoiseCapture(db -> runOnUiThread(() -> {
            window.add(db);
            if (window.size() > 100) window.remove(0);
            
            double avg = 0;
            for (double d : window) avg += d;
            avg /= window.size();

            dbText.setText(String.format("%.0f dB", db));
            avgText.setText(String.format("Avg: %.0f dB", avg));
            alertText.setVisibility(avg > 70 ? android.view.View.VISIBLE : android.view.View.GONE);
            chartView.add(db);
        }));
        capture.start();
    }

    private void stop() {
        isRunning = false;
        btnToggle.setText("Start");
        if (capture != null) capture.stop();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stop();
    }
}