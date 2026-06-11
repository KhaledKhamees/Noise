package com.example.noiselevel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class NoiseCapture {

    private static final int SAMPLING_RATE = 44100;
    private static final int CHANNEL_SETUP = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int UPDATE_INTERVAL = 100;

    private AudioRecord recorder;
    private Thread recordingThread;
    private boolean isRecording = false;
    private final int minBufferSize;
    
    private final AudioDataListener dataListener;

    public NoiseCapture(AudioDataListener listener) {
        this.dataListener = listener;
        minBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_SETUP, ENCODING);
    }

    public void start() {
        if (isRecording) return;

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLING_RATE, CHANNEL_SETUP, ENCODING, minBufferSize);

        isRecording = true;
        recorder.startRecording();

        recordingThread = new Thread(this::recordLoop, "AudioCaptureThread");
        recordingThread.start();
    }

    public void stop() {
        isRecording = false;
        
        if (recordingThread != null) {
            recordingThread.interrupt();
            recordingThread = null;
        }

        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }

    private void recordLoop() {
        int samplesCount = SAMPLING_RATE * UPDATE_INTERVAL / 1000;
        short[] audioBuffer = new short[samplesCount];

        while (isRecording && !Thread.currentThread().isInterrupted()) {
            int readCount = recorder.read(audioBuffer, 0, audioBuffer.length);
            
            if (readCount > 0) {
                double decibelValue = computeDecibels(audioBuffer, readCount);
                dataListener.onReading(decibelValue);
            }
        }
    }

    private double computeDecibels(short[] buffer, int count) {
        double squareSum = 0;
        
        for (int i = 0; i < count; i++) {
            double sampleValue = buffer[i];
            squareSum += sampleValue * sampleValue;
        }

        double rmsValue = Math.sqrt(squareSum / count);
        if (rmsValue < 1) return 0.0;

        double dbLevel = 20.0 * Math.log10(rmsValue / Short.MAX_VALUE);
        return dbLevel + 90.0;
    }

    public interface AudioDataListener {
        void onReading(double decibels);
    }
}