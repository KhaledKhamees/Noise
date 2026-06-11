package com.example.noiselevel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class NoiseCapture {

    private static final int SAMPLE_RATE     = 44100;
    private static final int CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT;
    private static final int READ_INTERVAL_MS = 100;

    private AudioRecord audioRecord;
    private Thread      captureThread;
    private boolean     isRunning = false;

    private final int bufferSize;
    private final OnNoiseReadingListener listener;

    public NoiseCapture(OnNoiseReadingListener listener) {
        this.listener = listener;
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    }

    public void start() {
        if (isRunning) return;

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        isRunning = true;
        audioRecord.startRecording();

        captureThread = new Thread(this::captureLoop, "NoiseCapture-Thread");
        captureThread.start();
    }

    public void stop() {
        isRunning = false;

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void captureLoop() {

        int samplesPerRead = SAMPLE_RATE * READ_INTERVAL_MS / 1000;
        short[] buffer = new short[samplesPerRead];

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            int samplesRead = audioRecord.read(buffer, 0, buffer.length);

            if (samplesRead > 0) {
                double db = calculateDecibels(buffer, samplesRead);
                listener.onReading(db);
            }
        }
    }

    private double calculateDecibels(short[] buffer, int sampleCount) {

        double sumOfSquares = 0;
        for (int i = 0; i < sampleCount; i++) {
            double sample = buffer[i];
            sumOfSquares += sample * sample;
        }

        double rms = Math.sqrt(sumOfSquares / sampleCount);

        if (rms < 1) return 0.0;

        double db = 20.0 * Math.log10(rms / Short.MAX_VALUE);

        return db + 90.0;
    }

    public interface OnNoiseReadingListener {
        void onReading(double decibels);
    }
}