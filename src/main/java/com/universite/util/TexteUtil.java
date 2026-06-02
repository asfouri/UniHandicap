package com.universite.util;

import java.nio.charset.StandardCharsets;

public final class TexteUtil {

    private TexteUtil() {}

    public static String nettoyerTexteReconnu(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return reparerEncodage(cleaned);
    }

    public static String reparerEncodage(String value) {
        if (value == null || value.isBlank()) return value;
        String prepared = value
            .replace("Ã ", "Ã\u00a0")
            .replace("Ã'", "\u00e0'")
            .replace("Ã.", "\u00e0.")
            .replace("Ã,", "\u00e0,");

        if (!contientMojibake(prepared)) return prepared;

        try {
            String repaired = new String(
                prepared.getBytes(StandardCharsets.ISO_8859_1),
                StandardCharsets.UTF_8
            );
            return repaired
                .replace("\u00c2\u00a0", " ")
                .replace("\u00a0", " ")
                .trim();
        } catch (Exception e) {
            return prepared;
        }
    }

    private static boolean contientMojibake(String value) {
        return value.contains("Ã") || value.contains("Â") || value.contains("â");
    }
}
