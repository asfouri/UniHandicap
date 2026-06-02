package com.universite.util;

import javafx.scene.media.AudioClip;
import java.net.URL;

/**
 * AudioUtil – TTS + sound feedback.
 *
 * KEY FIX: prononcer() is now BLOCKING on the calling thread when called
 * from a background thread (voice workflow threads). This ensures the mic
 * never starts recording while TTS is still speaking.
 *
 * From the JavaFX thread it remains non-blocking (daemon thread).
 *
 * Also adds prononcerEtAttendre() for explicit blocking TTS in workflows.
 */
public class AudioUtil {

    private static AudioClip sonSucces;
    private static AudioClip sonErreur;
    private static AudioClip sonNotification;
    private static boolean   sonsCharges = false;

    private static volatile Process ttsProcess = null;

    private AudioUtil() {}

    public static void chargerSons() {
        try {
            URL urlSucces = AudioUtil.class.getResource("/sounds/succes.mp3");
            URL urlErreur = AudioUtil.class.getResource("/sounds/erreur.mp3");
            URL urlNotif  = AudioUtil.class.getResource("/sounds/notification.mp3");

            if (urlSucces != null) sonSucces       = new AudioClip(urlSucces.toExternalForm());
            if (urlErreur != null) sonErreur       = new AudioClip(urlErreur.toExternalForm());
            if (urlNotif  != null) sonNotification = new AudioClip(urlNotif.toExternalForm());

            sonsCharges = true;
            System.out.println("[AudioUtil] Sons chargés avec succès.");
        } catch (Exception e) {
            System.err.println("[AudioUtil] Impossible de charger les sons : " + e.getMessage());
        }
    }

    public static void jouerSucces()       { if (sonsCharges && sonSucces       != null) sonSucces.play(); }
    public static void jouerErreur()       { if (sonsCharges && sonErreur       != null) sonErreur.play(); }
    public static void jouerNotification() { if (sonsCharges && sonNotification != null) sonNotification.play(); }

    /**
     * Stops any currently-speaking TTS immediately.
     */
    public static void arreter() {
        Process p = ttsProcess;
        if (p != null && p.isAlive()) p.destroyForcibly();
    }

    /**
     * Speaks text via TTS.
     * - Called from JavaFX thread → non-blocking (daemon thread)
     * - Called from background thread → BLOCKING (waits for speech to finish)
     *   This ensures mic recording never starts while TTS is still speaking.
     */
    public static void prononcer(String texte) {
        if (texte == null || texte.isBlank()) return;
        if (!SessionManager.isCommandeVocaleActivee()) return;
        boolean estThreadJavafx = javafx.application.Platform.isFxApplicationThread();
        if (estThreadJavafx) {
            // Non-blocking from UI thread
            Thread t = new Thread(() -> parler(texte), "TTS-Thread");
            t.setDaemon(true);
            t.start();
        } else {
            // Blocking from background/voice thread — mic won't start until done
            parler(texte);
        }
    }

    /**
     * Always blocking — use in voice workflow threads before starting mic.
     */
    public static void prononcerEtAttendre(String texte) {
        if (texte == null || texte.isBlank()) return;
        if (!SessionManager.isCommandeVocaleActivee()) return;
        parler(texte);
    }

    public static void prononcerToujoursEtAttendre(String texte) {
        if (texte == null || texte.isBlank()) return;
        parler(texte);
    }

    private static void parler(String texte) {
        arreter(); // stop any previous TTS
        String propre = assainir(texte);
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            Process p;
            if (os.contains("win")) {
                String ps =
                    "Add-Type -AssemblyName System.Speech; " +
                    "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "$s.Rate = 1; " +
                    "try { $s.SelectVoice('Microsoft Hortense Desktop') } catch {}; " +
                    "$s.Speak('" + propre + "')";
                p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
                    .redirectErrorStream(true).start();
            } else if (os.contains("mac")) {
                p = new ProcessBuilder("say", "-v", "Thomas", propre).start();
            } else {
                p = new ProcessBuilder("espeak-ng", "-v", "fr", "-s", "145", propre)
                    .redirectErrorStream(true).start();
            }
            ttsProcess = p;
            p.waitFor();
        } catch (Exception e) {
            System.err.println("[AudioUtil] TTS erreur : " + e.getMessage());
        } finally {
            ttsProcess = null;
        }
    }

    private static String assainir(String t) {
        return t.replace("'", " ").replace("\"", " ").replace("\\", " ")
                .replace("`", " ").replace("$", " ").replace(";", ".")
                .replace("\n", " ").trim();
    }
}
