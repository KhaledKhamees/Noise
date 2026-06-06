package com.example.noiselevel;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * NoiseCapture handles all microphone interaction.
 *
 * It runs a background thread that continuously reads raw PCM audio samples,
 * converts the amplitude to decibels (dB SPL approximation), and delivers
 * each reading to the caller via a simple callback interface.
 *
 * Design choice: we use AudioRecord (low-level API) rather than MediaRecorder
 * because MediaRecorder cannot give us real-time amplitude at a fine interval
 * without recording to a file — AudioRecord gives us the raw buffer directly.
 */
public class NoiseCapture {

    // ── Audio configuration ──────────────────────────────────────────────────
    private static final int SAMPLE_RATE     = 44100;   // Hz — standard CD quality
    private static final int CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT;

    // How many milliseconds of audio we read per "tick"
    // 100 ms gives us ~10 readings per second — smooth enough for the UI
    private static final int READ_INTERVAL_MS = 100;

    // ── Internal state ───────────────────────────────────────────────────────
    private AudioRecord audioRecord;
    private Thread      captureThread;
    private boolean     isRunning = false;

    // The minimum buffer size required by the hardware driver
    private final int bufferSize;

    // Callback delivered on the capture thread — caller posts to UI thread
    private final OnNoiseReadingListener listener;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * @param listener  Called every ~100 ms with the latest dB reading.
     */
    public NoiseCapture(OnNoiseReadingListener listener) {
        this.listener = listener;

        // Android requires us to request the minimum hardware buffer size first
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Begin capturing audio. Safe to call multiple times — checks isRunning. */
    public void start() {
        if (isRunning) return;

        // Build the AudioRecord object pointing at the device microphone
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        isRunning = true;
        audioRecord.startRecording();

        // All sampling happens off the main thread to avoid blocking the UI
        captureThread = new Thread(this::captureLoop, "NoiseCapture-Thread");
        captureThread.start();
    }

    /** Stop capturing and release the microphone resource. */
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

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * The main sampling loop — runs on captureThread.
     *
     * Each iteration:
     *   1. Read a buffer of 16-bit PCM samples from the microphone.
     *   2. Compute the Root Mean Square (RMS) amplitude of the buffer.
     *   3. Convert RMS → dB using the standard formula: dB = 20 * log10(rms).
     *   4. Fire the callback so MainActivity can update the UI.
     */
    private void captureLoop() {

        // We want to read roughly READ_INTERVAL_MS worth of samples per tick.
        // samplesPerRead = sampleRate * intervalSeconds
        int samplesPerRead = SAMPLE_RATE * READ_INTERVAL_MS / 1000;

        // short[] because we're using ENCODING_PCM_16BIT (16-bit signed integers)
        short[] buffer = new short[samplesPerRead];

        while (isRunning && !Thread.currentThread().isInterrupted()) {
            // Read samples — this call blocks until the buffer is full
            int samplesRead = audioRecord.read(buffer, 0, buffer.length);

            if (samplesRead > 0) {
                double db = calculateDecibels(buffer, samplesRead);
                listener.onReading(db);
            }
        }
    }

    /**
     * Converts a PCM sample buffer into a decibel value.
     *
     * Formula:
     *   RMS   = sqrt( sum(sample²) / N )
     *   dB    = 20 * log10( RMS / MAX_AMPLITUDE )
     *
     * Where MAX_AMPLITUDE for 16-bit PCM = 32767 (Short.MAX_VALUE).
     * This gives us a value roughly in the range 0–90 dB for typical environments.
     *
     * @param buffer      The raw PCM samples (16-bit signed).
     * @param sampleCount How many samples were actually filled in this read.
     * @return            Approximate dB SPL level (0.0 if signal is silent).
     */
    private double calculateDecibels(short[] buffer, int sampleCount) {

        // Step 1: sum of squares
        double sumOfSquares = 0;
        for (int i = 0; i < sampleCount; i++) {
            double sample = buffer[i]; // already a signed int in range [-32768, 32767]
            sumOfSquares += sample * sample;
        }

        // Step 2: RMS (Root Mean Square)
        double rms = Math.sqrt(sumOfSquares / sampleCount);

        // Step 3: Avoid log(0) — silence or near-silence returns 0 dB
        if (rms < 1) return 0.0;

        // Step 4: Convert to dB relative to max 16-bit amplitude
        double db = 20.0 * Math.log10(rms / Short.MAX_VALUE);

        // The formula above gives negative values (0 dB = max amplitude).
        // We shift it to a human-readable positive scale (roughly 0–90 dB)
        // by adding 90. This matches the "dB SPL" feel most users expect.
        return db + 90.0;
    }

    // ── Callback interface ───────────────────────────────────────────────────

    /**
     * Implement this in MainActivity to receive noise readings.
     * Note: onReading() is called from a background thread — always use
     * runOnUiThread() or a Handler before touching any View.
     */
    public interface OnNoiseReadingListener {
        void onReading(double decibels);
    }
}