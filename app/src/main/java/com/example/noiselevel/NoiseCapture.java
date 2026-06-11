package com.example.noiselevel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class NoiseCapture {
    interface Listener { void onReading(double db); }

    private AudioRecord rec;
    private Thread thread;
    private boolean running;
    private Listener listener;

    public NoiseCapture(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        int bufSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        rec = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
        running = true;
        rec.startRecording();
        thread = new Thread(() -> {
            short[] buffer = new short[4410];
            while (running) {
                rec.read(buffer, 0, buffer.length);
                double sum = 0;
                for (short s : buffer) sum += s * s;
                double rms = Math.sqrt(sum / buffer.length);
                listener.onReading(20 * Math.log10(rms / 32767) + 90);
            }
        });
        thread.start();
    }

    public void stop() {
        running = false;
        if (rec != null) { rec.stop(); rec.release(); }
    }
}