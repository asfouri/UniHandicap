package com.universite.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.vosk.Model;
import org.vosk.Recognizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAIWhisperUtil – Transcription vocale avec fallback hors-ligne Vosk.
 *
 * STRATÉGIE :
 *   1. Si OPENAI_API_KEY (ou -Dopenai.apiKey) est défini → Whisper API.
 *   2. Sinon → Vosk local, 100 % hors-ligne, aucune clé requise.
 *
 * SETUP VOSK (une seule fois) :
 *   a) Téléchargez le modèle français (~50 Mo) :
 *        https://alphacephei.com/vosk/models  →  vosk-model-small-fr-0.22.zip
 *   b) Décompressez l'archive.
 *   c) Placez le dossier obtenu dans l'un de ces emplacements :
 *        • Répertoire courant du projet
 *        • Dossier utilisateur (~/)
 *        • Variable d'env   VOSK_MODEL_PATH=/chemin/vers/dossier
 *        • Propriété JVM    -Dvosk.modelPath=/chemin/vers/dossier
 *
 * SETUP OPENAI (optionnel – active Whisper si présent) :
 *   Windows  : set OPENAI_API_KEY=sk-...
 *   Mac/Linux: export OPENAI_API_KEY=sk-...
 *   Maven    : mvn javafx:run -Dopenai.apiKey=sk-...
 */
public class OpenAIWhisperUtil {

    private static final ObjectMapper MAPPER    = new ObjectMapper();
    private static final long         MIN_BYTES = 1_000;
    private static final int          TIMEOUT_S = 30;

    private static final String[] VOSK_PREFIXES = {
        "vosk-model-fr", "vosk-model-small-fr", "vosk-model"
    };

    private static volatile Model   voskModel     = null;
    private static volatile boolean voskAttempted = false;
    private static volatile boolean whisperDisabledForSession = false;

    private OpenAIWhisperUtil() {}

    // ─── Point d'entrée public ────────────────────────────────────

    public static String transcrire(File audioFile) throws IOException, InterruptedException {

        if (audioFile == null || !audioFile.exists()) {
            throw new IOException("[SR] Fichier audio introuvable : " +
                (audioFile != null ? audioFile.getAbsolutePath() : "null"));
        }
        long fileSize = audioFile.length();
        System.out.println("[SR] Taille fichier : " + fileSize + " octets");
        System.out.println("[SR] Chemin fichier : " + audioFile.getAbsolutePath());
        if (fileSize < MIN_BYTES) {
            System.err.println("[SR] Fichier trop petit (" + fileSize + " octets) – audio vide.");
            return null;
        }

        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.isBlank() && !whisperDisabledForSession) {
            System.out.println("[SR] Clé OpenAI détectée → Whisper API.");
            try {
                return transcrireWhisper(audioFile, apiKey, fileSize);
            } catch (IOException e) {
                System.err.println(e.getMessage());
                if (isOpenAIQuotaOrAuthError(e)) {
                    whisperDisabledForSession = true;
                    System.err.println("[SR] Whisper desactive pour cette session : quota/cle OpenAI indisponible.");
                }
                System.err.println("[SR] Whisper indisponible -> basculement sur Vosk (hors-ligne).");
            }
        }

        System.out.println("[SR] Pas de clé OpenAI → basculement sur Vosk (hors-ligne).");
        return transcrireVosk(audioFile);
    }

    private static boolean isOpenAIQuotaOrAuthError(IOException e) {
        String message = e.getMessage();
        return message != null && (
            message.contains("insufficient_quota") ||
            message.contains("HTTP 401") ||
            message.contains("HTTP 403") ||
            message.contains("HTTP 429")
        );
    }

    // ─── Moteur 1 : Whisper ───────────────────────────────────────

    private static String transcrireWhisper(File audioFile, String apiKey, long fileSize)
            throws IOException, InterruptedException {

        String boundary = "----UAWhisperBoundary" + System.currentTimeMillis();
        byte[] body     = buildMultipartBody(boundary, audioFile);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type",  "multipart/form-data; boundary=" + boundary)
            .timeout(Duration.ofSeconds(TIMEOUT_S))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        System.out.println("[Whisper] Envoi de " + fileSize + " octets...");
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        System.out.println("[Whisper] HTTP " + status);

        if (status < 200 || status >= 300) {
            throw new IOException("[Whisper] Erreur HTTP " + status + " : " + response.body());
        }

        JsonNode root     = MAPPER.readTree(response.body());
        JsonNode textNode = root.get("text");
        if (textNode == null || textNode.isNull()) {
            throw new IOException("[Whisper] Champ 'text' manquant : " + response.body());
        }

        String text = TexteUtil.nettoyerTexteReconnu(textNode.asText(""));
        System.out.println("[Whisper] Transcription : \"" + text + "\"");
        return text.isEmpty() ? null : text;
    }

    // ─── Moteur 2 : Vosk (hors-ligne) ────────────────────────────

    private static String transcrireVosk(File audioFile) throws IOException {

        Model model = getOrLoadVoskModel();
        if (model == null) {
            throw new IOException(
                "[Vosk] Modèle introuvable.\n\n" +
                "Pour activer la reconnaissance vocale hors-ligne :\n" +
                "  1. Téléchargez vosk-model-small-fr-0.22.zip depuis\n" +
                "     https://alphacephei.com/vosk/models\n" +
                "  2. Décompressez l'archive.\n" +
                "  3. Placez le dossier dans le répertoire du projet.\n" +
                "  4. Relancez l'application.\n\n" +
                "Ou définissez : VOSK_MODEL_PATH=/chemin/vers/vosk-model-small-fr-0.22"
            );
        }

        StringBuilder result = new StringBuilder();

        AudioFormat targetFormat = new AudioFormat(16000.0f, 16, 1, true, false);

        try (AudioInputStream rawAis = AudioSystem.getAudioInputStream(audioFile)) {

            // DEBUG: print the actual format of the WAV file
            AudioFormat actualFormat = rawAis.getFormat();
            System.out.println("[Vosk][DEBUG] Format WAV réel      : " + actualFormat);
            System.out.println("[Vosk][DEBUG] Format cible Vosk    : " + targetFormat);
            System.out.println("[Vosk][DEBUG] Frames disponibles   : " + rawAis.getFrameLength());
            System.out.println("[Vosk][DEBUG] Durée estimée (s)    : " +
                (rawAis.getFrameLength() / actualFormat.getFrameRate()));

            AudioInputStream ais = AudioSystem.isConversionSupported(targetFormat, actualFormat)
                ? AudioSystem.getAudioInputStream(targetFormat, rawAis)
                : rawAis;

            System.out.println("[Vosk][DEBUG] Format utilisé pour reconnaissance : " + ais.getFormat());

            try (Recognizer rec = new Recognizer(model, 16000.0f)) {
                byte[] buffer = new byte[4096];
                int    n;
                int    totalBytes = 0;
                while ((n = ais.read(buffer)) != -1) {
                    totalBytes += n;
                    if (rec.acceptWaveForm(buffer, n)) {
                        String segment = extractVoskText(rec.getResult());
                        if (!segment.isBlank()) result.append(segment).append(" ");
                    }
                }
                System.out.println("[Vosk][DEBUG] Total octets traités : " + totalBytes);
                String finalSeg = extractVoskText(rec.getFinalResult());
                if (!finalSeg.isBlank()) result.append(finalSeg);
            }

        } catch (Exception e) {
            throw new IOException("[Vosk] Erreur transcription : " + e.getMessage(), e);
        }

        String text = TexteUtil.nettoyerTexteReconnu(result.toString());
        System.out.println("[Vosk] Transcription : \"" + text + "\"");
        return text.isEmpty() ? null : text;
    }

    private static synchronized Model getOrLoadVoskModel() {
        if (voskModel != null)  return voskModel;
        if (voskAttempted)      return null;
        voskAttempted = true;

        // 1. Chemins explicites
        String[] explicit = {
            System.getenv("VOSK_MODEL_PATH"),
            System.getProperty("vosk.modelPath")
        };
        for (String p : explicit) {
            if (p != null && !p.isBlank()) {
                Model m = tryLoadModel(new File(p));
                if (m != null) { voskModel = m; return m; }
            }
        }

        // 2. Recherche automatique
        File[] searchDirs = {
            new File("."),
            new File(System.getProperty("user.home")),
            new File(System.getProperty("user.home"), "vosk"),
            new File(System.getProperty("user.home"), "models")
        };

        for (File dir : searchDirs) {
            if (!dir.isDirectory()) continue;
            File[] children = dir.listFiles(File::isDirectory);
            if (children == null) continue;
            for (File child : children) {
                String name = child.getName().toLowerCase();
                for (String prefix : VOSK_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        Model m = tryLoadModel(child);
                        if (m != null) { voskModel = m; return m; }
                    }
                }
            }
        }

        System.err.println("[Vosk] Aucun modèle trouvé. " +
            "Téléchargez vosk-model-small-fr-*.zip et décompressez-le dans le répertoire du projet.");
        return null;
    }

    private static Model tryLoadModel(File dir) {
        if (!dir.isDirectory()) return null;
        // Validation : un modèle Vosk valide contient ces sous-dossiers
        for (String sub : new String[]{"am", "conf", "graph"}) {
            if (!new File(dir, sub).exists()) return null;
        }
        try {
            System.out.println("[Vosk] Chargement depuis : " + dir.getAbsolutePath());
            Model m = new Model(dir.getAbsolutePath());
            System.out.println("[Vosk] Modèle chargé.");
            return m;
        } catch (Exception e) {
            System.err.println("[Vosk] Échec (" + dir.getName() + ") : " + e.getMessage());
            return null;
        }
    }

    private static String extractVoskText(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode t    = node.get("text");
            return (t != null && !t.isNull()) ? t.asText("").trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ─── Helpers HTTP ─────────────────────────────────────────────

    private static String getApiKey() {
        String p = System.getProperty("openai.apiKey");
        if (p != null && !p.isBlank()) return p;
        return System.getenv("OPENAI_API_KEY");
    }

    private static byte[] buildMultipartBody(String boundary, File audioFile) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "Content-Disposition: form-data; name=\"model\"",           "whisper-1");
        writePart(out, boundary, "Content-Disposition: form-data; name=\"language\"",        "fr");
        writePart(out, boundary, "Content-Disposition: form-data; name=\"response_format\"", "json");

        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                   audioFile.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: audio/wav\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        Files.copy(audioFile.toPath(), out);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writePart(ByteArrayOutputStream out, String boundary,
                                  String header, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((header + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write((value  + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
