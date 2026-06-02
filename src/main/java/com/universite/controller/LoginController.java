package com.universite.controller;

import com.universite.dao.UtilisateurDAO;
import com.universite.model.Utilisateur;
import com.universite.util.AudioUtil;
import com.universite.util.MicRecorder;
import com.universite.util.NavigationUtil;
import com.universite.util.OpenAIWhisperUtil;
import com.universite.util.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.io.File;
import java.text.Normalizer;
import java.util.Locale;

public class LoginController {

    @FXML private TextField         champEmail;
    @FXML private PasswordField     champMotDePasse;
    @FXML private Button            boutonConnexion;
    @FXML private Button            boutonVocal;
    @FXML private Label             labelErreur;
    @FXML private Label             labelEtatVocal;
    @FXML private ProgressIndicator indicateurChargement;

    private final UtilisateurDAO utilisateurDAO = new UtilisateurDAO();
    private final MicRecorder    micRecorder    = new MicRecorder();

    @FXML
    public void initialize() {
        labelErreur.setVisible(false);
        if (indicateurChargement != null) indicateurChargement.setVisible(false);
        if (labelEtatVocal != null) labelEtatVocal.setVisible(false);
        if (!SessionManager.isCommandeVocaleActivee()) {
            if (boutonVocal != null) {
                boutonVocal.setVisible(false);
                boutonVocal.setManaged(false);
            }
            return;
        }

        champEmail.focusedProperty().addListener((obs, old, nv) -> {
            if (nv) AudioUtil.prononcer("Saisissez votre adresse e-mail universitaire.");
        });
        champMotDePasse.focusedProperty().addListener((obs, old, nv) -> {
            if (nv) AudioUtil.prononcer("Saisissez votre mot de passe.");
        });

        // Auto-start voice login after page announcement
        new Thread(() -> {
            AudioUtil.prononcerEtAttendre(
                "Page de connexion. Connexion vocale automatique. " +
                "Préparez-vous à dicter votre adresse e-mail."
            );
            // Only auto-start if user hasn't typed anything
            if (champEmail.getText().isBlank() && champMotDePasse.getText().isBlank()) {
                demarrerWorkflowVocal();
            }
        }, "Login-AutoVoice").start();
    }

    @FXML
    private void handleConnexion() {
        String email      = champEmail.getText().trim();
        String motDePasse = champMotDePasse.getText();

        if (email.isEmpty() || motDePasse.isEmpty()) {
            afficherErreur("Veuillez remplir tous les champs.");
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Erreur : veuillez remplir tous les champs.");
            return;
        }

        if (indicateurChargement != null) indicateurChargement.setVisible(true);
        boutonConnexion.setDisable(true);
        if (boutonVocal != null) boutonVocal.setDisable(true);

        new Thread(() -> {
            Utilisateur utilisateur = utilisateurDAO.authentifier(email, motDePasse);
            Platform.runLater(() -> {
                if (indicateurChargement != null) indicateurChargement.setVisible(false);
                boutonConnexion.setDisable(false);
                if (boutonVocal != null) boutonVocal.setDisable(false);

                if (utilisateur != null) {
                    SessionManager.setUtilisateur(utilisateur);
                    AudioUtil.jouerSucces();
                    AudioUtil.prononcer("Connexion réussie. Bienvenue " + utilisateur.getNomComplet() + ".");
                    redirigerSelonRole(utilisateur);
                } else {
                    afficherErreur("Email ou mot de passe incorrect.");
                    AudioUtil.jouerErreur();
                    // Re-try voice login after failure
                    if (SessionManager.isCommandeVocaleActivee()) new Thread(() -> {
                        AudioUtil.prononcerEtAttendre(
                            "Échec de connexion. Email ou mot de passe incorrect. " +
                            "Veuillez réessayer. Dites votre adresse e-mail."
                        );
                        demarrerWorkflowVocal();
                    }, "Login-Retry-Voice").start();
                    champMotDePasse.clear();
                }
            });
        }).start();
    }

    @FXML
    private void handleToucheEntree(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) handleConnexion();
    }

    @FXML
    private void handleConnexionVocale() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        new Thread(this::demarrerWorkflowVocal, "VoiceLogin-Thread").start();
    }

    private void demarrerWorkflowVocal() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        Platform.runLater(() -> {
            if (boutonVocal != null) boutonVocal.setDisable(true);
            boutonConnexion.setDisable(true);
            champEmail.clear();
            champMotDePasse.clear();
            afficherEtatVocal("Workflow vocal démarré...");
        });

        try {
            // STEP 1: Email
            Platform.runLater(() -> afficherEtatVocal("Dites votre adresse e-mail..."));
            AudioUtil.prononcerEtAttendre("Dites votre adresse e-mail universitaire maintenant.");

            String email = enregistrerEtTranscrire(6000);
            if (email == null || email.isBlank()) {
                echecVocal("Impossible de capter votre e-mail. Veuillez réessayer.");
                return;
            }
            email = nettoyerEmail(email);
            email = completerEmailDepuisBase(email);
            if (!email.contains("@")) {
                echecVocal("Adresse e-mail non reconnue dans la base. Veuillez redire votre prenom et nom.");
                return;
            }
            System.out.println("[VoiceLogin] E-mail normalise : \"" + email + "\"");
            final String emailFinal = email;
            Platform.runLater(() -> {
                champEmail.setText(emailFinal);
                afficherEtatVocal("E-mail capturé : " + emailFinal);
            });

            AudioUtil.prononcerEtAttendre("E-mail enregistré. Dites maintenant votre mot de passe.");

            // STEP 2: Password
            Platform.runLater(() -> afficherEtatVocal("Dites votre mot de passe..."));
            AudioUtil.prononcerEtAttendre("Dites votre mot de passe maintenant.");

            String motDePasse = enregistrerEtTranscrire(6000);
            if (motDePasse == null || motDePasse.isBlank()) {
                echecVocal("Impossible de capter votre mot de passe. Veuillez réessayer.");
                return;
            }
            final String mdpFinal = nettoyerMotDePasse(motDePasse);
            System.out.println("[VoiceLogin] Mot de passe normalise : \"" + mdpFinal + "\"");
            Platform.runLater(() -> {
                champMotDePasse.setText(mdpFinal);
                afficherEtatVocal("Mot de passe capturé. Connexion en cours...");
            });

            AudioUtil.prononcerEtAttendre("Mot de passe enregistré. Tentative de connexion.");
            Platform.runLater(this::handleConnexion);

        } catch (Exception ex) {
            echecVocal("Erreur lors du workflow vocal.");
            System.err.println("[VoiceLogin] " + ex.getMessage());
        }
    }

    private String enregistrerEtTranscrire(long dureeMs) {
        try {
            File audioFile = micRecorder.start();
            System.out.println("[VoiceLogin] Enregistrement " + dureeMs + "ms...");
            Thread.sleep(dureeMs);
            micRecorder.stop();
            System.out.println("[VoiceLogin] Transcription...");
            String result = OpenAIWhisperUtil.transcrire(audioFile);
            System.out.println("[VoiceLogin] Résultat : \"" + result + "\"");
            return result;
        } catch (Exception e) {
            System.err.println("[VoiceLogin] Erreur : " + e.getMessage());
            return null;
        }
    }

    private String nettoyerEmail(String raw) {
        String email = sansAccents(raw)
            .toLowerCase(Locale.ROOT)
            .replace('\'', ' ')
            .replace('-', ' ')
            .replaceAll("[^a-z0-9@._+ ]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        email = " " + email + " ";
        email = email
            .replaceAll("\\b(a\\s*robase|arobase|arrobase|a\\s*commercial|at|apos|apostrophe)\\b", " @ ")
            .replaceAll("\\b(point|poin|poing|dot)\\b", " . ")
            .replaceAll("\\b(tiret\\s+bas|underscore|sous\\s*tiret)\\b", " _ ")
            .replaceAll("\\b(tiret|moins)\\b", " - ")
            .replaceAll("\\b(plus)\\b", " + ");

        email = email
            .replaceAll("\\.\\s*(eff\\s*erre|f\\s*r|france|fer|frere|faire)\\b", ".fr")
            .replaceAll("\\.\\s*(m\\s*a|ma|maroc|moi)\\b", ".ma")
            .replaceAll("\\.\\s*(c\\s*o\\s*m|com)\\b", ".com")
            .replaceAll("\\.\\s*(o\\s*r\\s*g|org)\\b", ".org");

        email = email
            .replaceAll("\\s+", "")
            .replace("..", ".")
            .replace("admins@", "admin@");

        return email;
    }

    private String completerEmailDepuisBase(String texte) {
        if (texte == null || texte.isBlank()) return texte;
        if (texte.contains("@") && utilisateurDAO.emailExiste(texte)) return texte;

        String indice = texte.contains("@") ? texte.substring(0, texte.indexOf('@')) : texte;
        indice = normaliserIndiceEmail(indice);
        if (indice.isBlank()) return texte;

        Utilisateur meilleur = null;
        int scoreMeilleur = -1;
        boolean ambigu = false;

        for (Utilisateur utilisateur : utilisateurDAO.trouverTous()) {
            String email = utilisateur.getEmail();
            if (email == null || email.isBlank()) continue;
            int atIndex = email.indexOf('@');
            if (atIndex <= 0) continue;

            String local = normaliserIndiceEmail(email.substring(0, atIndex));
            String prenom = normaliserIndiceEmail(utilisateur.getPrenom());
            String nom = normaliserIndiceEmail(utilisateur.getNom());
            String complet = normaliserIndiceEmail(utilisateur.getNomComplet());

            int score = scoreCorrespondanceEmail(indice, local, prenom, nom, complet);
            if (score > scoreMeilleur) {
                meilleur = utilisateur;
                scoreMeilleur = score;
                ambigu = false;
            } else if (score > 0 && score == scoreMeilleur) {
                ambigu = true;
            }
        }

        if (meilleur != null && scoreMeilleur > 0 && !ambigu) {
            System.out.println("[VoiceLogin] E-mail complete depuis la base : \"" + meilleur.getEmail() + "\"");
            return meilleur.getEmail();
        }

        if (ambigu) {
            System.out.println("[VoiceLogin] Plusieurs e-mails correspondent a : \"" + texte + "\"");
        }
        return texte;
    }

    private int scoreCorrespondanceEmail(String indice, String local, String prenom, String nom, String complet) {
        if (indice.equals(local)) return 100;
        if (indice.equals(prenom) || indice.equals(nom)) return 90;
        if (local.startsWith(indice) || indice.startsWith(local)) return 80;
        if (prenom.startsWith(indice) || nom.startsWith(indice)) return 70;
        if (complet.contains(indice)) return 60;
        if (scoreSimilarite(indice, local) >= 78) return 55;
        if (scoreSimilarite(indice, complet) >= 72) return 50;
        return 0;
    }

    private String normaliserIndiceEmail(String value) {
        if (value == null) return "";
        String indice = sansAccents(value)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        return corrigerErreursReconnaissanceEmail(indice);
    }

    private String corrigerErreursReconnaissanceEmail(String indice) {
        return indice
            .replace("lecorps", "lucas")
            .replace("lecorp", "lucas")
            .replace("lucasse", "lucas")
            .replace("morte", "martin")
            .replace("mortin", "martin")
            .replace("martine", "martin");
    }

    private int scoreSimilarite(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return 0;
        int distance = distanceLevenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        return (int) Math.round((1.0 - ((double) distance / max)) * 100);
    }

    private int distanceLevenshtein(String a, String b) {
        int[] precedent = new int[b.length() + 1];
        int[] courant = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) precedent[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            courant[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cout = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                courant[j] = Math.min(
                    Math.min(courant[j - 1] + 1, precedent[j] + 1),
                    precedent[j - 1] + cout
                );
            }
            int[] temp = precedent;
            precedent = courant;
            courant = temp;
        }

        return precedent[b.length()];
    }

    private String nettoyerMotDePasse(String raw) {
        String texte = sansAccents(raw)
            .toLowerCase(Locale.ROOT)
            .replace('-', ' ')
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        StringBuilder mdp = new StringBuilder();
        for (String mot : texte.split(" ")) {
            String chiffre = motVersChiffre(mot);
            if (chiffre != null) {
                mdp.append(chiffre);
            } else if (mot.matches("\\d+")) {
                mdp.append(mot);
            } else if (!motParasiteMotDePasse(mot)) {
                mdp.append(mot);
            }
        }

        return mdp.toString();
    }

    private boolean motParasiteMotDePasse(String mot) {
        switch (mot) {
            case "a":
            case "de":
            case "des":
            case "du":
            case "la":
            case "le":
            case "les":
            case "pour":
            case "par":
            case "mon":
            case "mot":
            case "passe":
            case "est":
                return true;
            default:
                return false;
        }
    }

    private String motVersChiffre(String mot) {
        switch (mot) {
            case "zero":
            case "zeros":
                return "0";
            case "un":
            case "une":
                return "1";
            case "deux":
                return "2";
            case "trois":
                return "3";
            case "quatre":
                return "4";
            case "cinq":
                return "5";
            case "six":
                return "6";
            case "sept":
            case "sette":
                return "7";
            case "huit":
                return "8";
            case "neuf":
                return "9";
            default:
                return null;
        }
    }

    private String sansAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private void afficherEtatVocal(String message) {
        Platform.runLater(() -> {
            if (labelEtatVocal != null) {
                labelEtatVocal.setText(message);
                labelEtatVocal.setVisible(true);
            }
        });
    }

    private void echecVocal(String message) {
        Platform.runLater(() -> {
            afficherErreur(message);
            afficherEtatVocal(message);
            if (boutonVocal != null) boutonVocal.setDisable(false);
            boutonConnexion.setDisable(false);
        });
        AudioUtil.jouerErreur();
        AudioUtil.prononcerEtAttendre(message);
    }

    private void redirigerSelonRole(Utilisateur utilisateur) {
        Stage stage = NavigationUtil.getStage(boutonConnexion);
        if (utilisateur.isAdmin()) {
            NavigationUtil.naviguerVers(stage,
                "/fxml/admin/DashboardAdmin.fxml", "Tableau de bord – Administration");
        } else {
            NavigationUtil.naviguerVers(stage,
                "/fxml/student/DashboardEtudiant.fxml", "Mon espace étudiant");
        }
    }

    private void afficherErreur(String message) {
        Platform.runLater(() -> {
            labelErreur.setText(message);
            labelErreur.setVisible(true);
        });
    }
}
