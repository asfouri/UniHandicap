package com.universite.util;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

/**
 * MicRecorder – records microphone input to a WAV file.
 *
 * FIX (v5): stop() now waits for the AudioSystem.write() worker to fully
 * flush and close the file before returning.  Previously the file was
 * truncated / corrupt because stop() closed the line while the daemon
 * writer thread was still flushing data, causing Whisper to reject it.
 *
 * Key changes:
 *  - worker thread is NOT a daemon (so JVM waits for it on shutdown too)
 *  - stop() calls line.stop() + line.close() THEN joins the worker
 *  - start() returns the file path immediately (unchanged API)
 */
public class MicRecorder {

    private final AudioFormat format;
    private TargetDataLine   line;
    private Thread           worker;
    private volatile boolean recording;
    private volatile File    outputFile;

    public MicRecorder() {
        this.format = new AudioFormat(
            16000.0f,  // sample rate  – Whisper accepts 16 kHz
            16,        // sample size
            1,         // mono
            true,      // signed
            false      // little-endian
        );
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    /**
     * Opens the microphone and starts recording to a temp WAV file.
     * @return the file that will contain the recording (written asynchronously)
     */
    public synchronized File start() throws LineUnavailableException, IOException {
        if (recording) return outputFile;   // idempotent

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException(
                "Aucun microphone compatible trouvé sur ce système.");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        outputFile = File.createTempFile("ua_mic_", ".wav");
        outputFile.deleteOnExit();
        recording = true;

        final File dest   = outputFile;
        final TargetDataLine tdl = line;
        final AudioInputStream ais = new AudioInputStream(tdl);

        // NON-daemon so JVM doesn't kill it mid-write
        worker = new Thread(() -> {
            try {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, dest);
            } catch (IOException e) {
                System.err.println("[MicRecorder] Erreur écriture WAV : " + e.getMessage());
            }
        }, "MicRecorder-Writer");
        worker.setDaemon(false);
        worker.start();

        return outputFile;
    }

    /**
     * Stops recording and waits until the WAV file is fully written.
     * The returned File is complete and ready for Whisper.
     */
    public synchronized File stop() {
        if (!recording) return outputFile;
        recording = false;

        // 1. Tell the line to stop capturing – this causes AudioSystem.write()
        //    to detect end-of-stream and close the WAV file cleanly.
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }

        // 2. Wait for the writer thread to finish flushing + closing the file.
        if (worker != null && worker.isAlive()) {
            try {
                worker.join(8_000);   // max 8 s grace period
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }

        return outputFile;
    }
}
