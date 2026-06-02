package com.universite.util;

import com.universite.model.Utilisateur;

/**
 * SessionManager – Gère la session utilisateur en cours.
 * Classe singleton statique accessible partout dans l'application.
 */
public class SessionManager {

    private static Utilisateur utilisateurConnecte = null;
    private static String profilCouleur = "NORMAL";   // résultat du test de vision
    private static String themeCSS      = "style"; // nom du fichier CSS appliqué (sans extension)
    private static double zoomFactor    = 1.0;
    private static boolean commandeVocaleActivee = true;
    private static boolean choixCommandeVocaleEffectue = false;

    private SessionManager() {}

    // ── Session utilisateur ───────────────────────────────────────

    public static void setUtilisateur(Utilisateur u) {
        utilisateurConnecte = u;
    }

    public static Utilisateur getUtilisateur() {
        return utilisateurConnecte;
    }

    public static boolean estConnecte() {
        return utilisateurConnecte != null;
    }

    public static boolean estAdmin() {
        return estConnecte() && utilisateurConnecte.isAdmin();
    }

    // ── Profil d'accessibilité ────────────────────────────────────

    public static String getProfilCouleur() { return profilCouleur; }
    public static void setProfilCouleur(String profil) { profilCouleur = profil; }

    public static String getThemeCSS() { return themeCSS; }
    public static void setThemeCSS(String theme) { themeCSS = theme; }

    public static double getZoomFactor() { return zoomFactor; }

    public static void setZoomFactor(double zoom) {
        if (Double.isNaN(zoom) || Double.isInfinite(zoom)) return;
        if (zoom < 0.8) zoom = 0.8;
        if (zoom > 1.5) zoom = 1.5;
        zoomFactor = zoom;
    }

    public static boolean isCommandeVocaleActivee() { return commandeVocaleActivee; }

    public static void setCommandeVocaleActivee(boolean activee) {
        commandeVocaleActivee = activee;
        choixCommandeVocaleEffectue = true;
    }

    public static boolean isChoixCommandeVocaleEffectue() {
        return choixCommandeVocaleEffectue;
    }

    // ── Déconnexion ───────────────────────────────────────────────

    public static void clear() {
        utilisateurConnecte = null;
        profilCouleur = "NORMAL";
        themeCSS = "style";
        zoomFactor = 1.0;
    }
}
