package com.universite.controller;

import com.universite.util.AudioUtil;
import com.universite.util.MicRecorder;
import com.universite.util.NavigationUtil;
import com.universite.util.OpenAIWhisperUtil;
import com.universite.util.SessionManager;
import com.universite.util.TexteUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ColorVisionTestController {

    @FXML private Label labelQuestion;
    @FXML private Label labelEtape;
    @FXML private StackPane zoneTest;
    @FXML private FlowPane zoneReponses;
    @FXML private Button boutonPasser;
    @FXML private Button boutonRepondreVocalement;

    private final MicRecorder micRecorder = new MicRecorder();
    private final List<String> reponsesUtilisateur = new ArrayList<>();
    private final Object verrouChoixCommandeVocale = new Object();
    private final Object verrouReponseTest = new Object();
    private int etapeActuelle = 0;
    private int derniereEtapeRepondue = -1;
    private volatile boolean choixCommandeVocaleRecu = false;
    private volatile boolean testCouleurDemarre = false;

    private final String[][] etapes = {
        {"Quelle couleur voyez-vous principalement ?", "#FF0000", "#00FF00"},
        {"Quel chiffre distinguez-vous dans ce cercle ?", "#FF6600", "#009900"},
        {"Ces deux couleurs vous semblent-elles differentes ?", "#0000FF", "#800080"}
    };

    @FXML
    public void initialize() {
        if (SessionManager.isChoixCommandeVocaleEffectue()) {
            demarrerTestApresChoixCommandeVocale();
            return;
        }

        afficherQuestionCommandeVocale("Dites \"oui activer\" ou \"non desactiver\", ou cliquez sur un bouton.");
        new Thread(this::demanderPreferenceCommandeVocale, "ColorTest-Init").start();
    }

    private void demanderPreferenceCommandeVocale() {
        if (SessionManager.isChoixCommandeVocaleEffectue()) {
            demarrerTestApresChoixCommandeVocale();
            return;
        }

        for (int tentative = 0; tentative < 3; tentative++) {
            if (choixCommandeVocaleRecu) return;
            try {
                final int tentativeCourante = tentative + 1;
                Platform.runLater(() -> afficherQuestionCommandeVocale(
                    tentativeCourante == 1
                        ? "Dites \"oui activer\" ou \"non desactiver\", ou cliquez sur un bouton."
                        : "Je n'ai pas compris. Dites une phrase complete : \"oui activer\" ou \"non desactiver\"."
                ));
                AudioUtil.prononcerToujoursEtAttendre(
                    "Voulez-vous utiliser les commandes vocales dans l'application ? " +
                    "Dites oui activer, ou non desactiver, maintenant."
                );
                if (choixCommandeVocaleRecu) return;

                File audioFile = micRecorder.start();
                long finEcoute = System.currentTimeMillis() + 5500;
                while (System.currentTimeMillis() < finEcoute && !choixCommandeVocaleRecu) {
                    Thread.sleep(100);
                }
                micRecorder.stop();
                if (choixCommandeVocaleRecu) return;

                String transcrit = OpenAIWhisperUtil.transcrire(audioFile);
                String reponse = normaliserCommande(transcrit);
                System.out.println("[VoicePreference] Reponse : \"" + transcrit + "\" -> " + reponse);

                if (estNon(reponse)) {
                    validerChoixCommandeVocale(false, "voix");
                    return;
                }
                if (estOui(reponse)) {
                    validerChoixCommandeVocale(true, "voix");
                    return;
                }
            } catch (Exception e) {
                System.err.println("[VoicePreference] " + e.getMessage());
            }
        }

        validerChoixCommandeVocale(false, "defaut");
    }

    private void choisirCommandeVocaleParClic(boolean activee) {
        if (validerChoixCommandeVocale(activee, "clic")) {
            stopperMicroSiNecessaire();
        }
    }

    private boolean validerChoixCommandeVocale(boolean activee, String source) {
        synchronized (verrouChoixCommandeVocale) {
            if (choixCommandeVocaleRecu || SessionManager.isChoixCommandeVocaleEffectue()) {
                return false;
            }
            choixCommandeVocaleRecu = true;
            SessionManager.setCommandeVocaleActivee(activee);
        }

        System.out.println("[VoicePreference] Choix par " + source + " : " + (activee ? "activee" : "desactivee"));
        Platform.runLater(() -> {
            afficherQuestionCommandeVocale(activee ? "Commandes vocales activees." : "Commandes vocales desactivees.");
            if (!activee && boutonRepondreVocalement != null) {
                boutonRepondreVocalement.setVisible(false);
                boutonRepondreVocalement.setManaged(false);
            }
        });
        demarrerTestApresChoixCommandeVocale();
        return true;
    }

    private void stopperMicroSiNecessaire() {
        try {
            if (micRecorder.isRecording()) {
                micRecorder.stop();
            }
        } catch (Exception e) {
            System.err.println("[VoicePreference] Arret micro ignore : " + e.getMessage());
        }
    }

    private void demarrerTestApresChoixCommandeVocale() {
        synchronized (verrouChoixCommandeVocale) {
            if (testCouleurDemarre) return;
            testCouleurDemarre = true;
        }

        Platform.runLater(() -> afficherEtape(0));
    }

    private void afficherQuestionCommandeVocale(String aide) {
        if (labelEtape != null) {
            labelEtape.setText("Choix des commandes vocales");
        }
        if (labelQuestion != null) {
            labelQuestion.setText(
                "Voulez-vous utiliser les commandes vocales dans l'application ?\n" +
                aide
            );
        }
        if (zoneTest != null) {
            zoneTest.getChildren().clear();
            Label statut = new Label(messageStatutCommandeVocale(aide));
            statut.setWrapText(true);
            statut.setTextAlignment(TextAlignment.CENTER);
            statut.setMaxWidth(360);
            statut.setStyle(
                "-fx-text-fill: #173B6D;" +
                "-fx-font-size: 17px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 24;" +
                "-fx-background-color: #FFFFFF;" +
                "-fx-border-color: #D0D7E2;" +
                "-fx-border-radius: 8;" +
                "-fx-background-radius: 8;"
            );
            zoneTest.getChildren().add(statut);
        }
        if (zoneReponses != null) {
            zoneReponses.getChildren().clear();
            Button oui = new Button("Oui, activer");
            oui.getStyleClass().add("bouton-reponse");
            oui.setOnAction(e -> choisirCommandeVocaleParClic(true));
            Button non = new Button("Non, desactiver");
            non.getStyleClass().add("bouton-reponse");
            non.setOnAction(e -> choisirCommandeVocaleParClic(false));
            boolean choixTermine = choixCommandeVocaleRecu || SessionManager.isChoixCommandeVocaleEffectue();
            oui.setDisable(choixTermine);
            non.setDisable(choixTermine);
            zoneReponses.getChildren().addAll(oui, non);
        }
        if (boutonRepondreVocalement != null) {
            boutonRepondreVocalement.setDisable(true);
        }
        if (boutonPasser != null) {
            boutonPasser.setDisable(true);
        }
    }

    private String messageStatutCommandeVocale(String aide) {
        String normalisee = normaliserCommande(aide);
        if (normalisee.contains("activees") || normalisee.contains("desactivees")) {
            return aide + "\nLe test va commencer.";
        }
        if (normalisee.contains("noncomprise")) {
            return aide + "\nLe test va commencer sans commande vocale.";
        }
        return "L'application ecoute votre reponse.\nDites OUI ACTIVER, ou NON DESACTIVER.";
    }

    private void afficherEtape(int index) {
        if (index >= etapes.length) {
            analyserEtAppliquerResultats();
            return;
        }

        etapeActuelle = index;
        String[] etape = etapes[index];
        labelQuestion.setText(etape[0]);
        labelEtape.setText("Etape " + (index + 1) + " / " + etapes.length);

        zoneTest.getChildren().clear();
        Circle cercle = new Circle(80);
        cercle.setFill(Color.web(etape[1]));
        Circle interieur = new Circle(40);
        interieur.setFill(Color.web(etape[2]));
        zoneTest.getChildren().addAll(cercle, interieur);

        zoneReponses.getChildren().clear();
        String[] options = optionsPourEtape(index);
        for (String option : options) {
            Button btn = new Button(option);
            btn.getStyleClass().add("bouton-reponse");
            btn.setOnAction(e -> handleReponse(option));
            zoneReponses.getChildren().add(btn);
        }

        if (boutonRepondreVocalement != null) {
            boutonRepondreVocalement.setDisable(!SessionManager.isCommandeVocaleActivee());
        }
        if (boutonPasser != null) {
            boutonPasser.setDisable(false);
        }
        if (SessionManager.isCommandeVocaleActivee()) {
            demarrerReponseVocaleAutomatique(index, etape[0], options);
        }
    }

    private String[] optionsPourEtape(int index) {
        switch (index) {
            case 0:
                return new String[]{"Rouge", "Vert", "Les deux pareils", "Je ne sais pas"};
            case 1:
                return new String[]{"Je vois un chiffre", "Rien de distinct", "Couleurs similaires", "Je ne sais pas"};
            default:
                return new String[]{"Oui, differentes", "Non, pareilles", "Legerement differentes", "Je ne sais pas"};
        }
    }

    @FXML
    private void handleReponse(String reponse) {
        enregistrerReponseEtape(etapeActuelle, reponse);
    }

    private void enregistrerReponseEtape(int indexEtape, String reponse) {
        synchronized (verrouReponseTest) {
            if (indexEtape != etapeActuelle || derniereEtapeRepondue == indexEtape) return;
            derniereEtapeRepondue = indexEtape;
        }
        arreterMicroSiActif();
        reponsesUtilisateur.add(reponse);
        AudioUtil.jouerSucces();
        afficherEtape(etapeActuelle + 1);
    }

    @FXML
    private void handlePasser() {
        SessionManager.setProfilCouleur("NORMAL");
        SessionManager.setThemeCSS("style");
        AudioUtil.prononcer("Test ignore. Interface standard appliquee.");
        naviguerVersConnexion();
    }

    @FXML
    private void handleRepondreVocalement() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(true);

        new Thread(() -> {
            try {
                AudioUtil.prononcerEtAttendre("Dites votre reponse maintenant.");

                File audioFile = micRecorder.start();
                Thread.sleep(4000);
                micRecorder.stop();

                String transcrit = OpenAIWhisperUtil.transcrire(audioFile);
                System.out.println("[ColorTest] Transcription vocale : \"" + transcrit + "\"");

                if (transcrit == null || transcrit.isBlank()) {
                    AudioUtil.prononcerEtAttendre("Je n'ai pas compris. Veuillez cliquer sur un bouton.");
                    Platform.runLater(() -> {
                        if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(false);
                    });
                    return;
                }

                String[] options = optionsPourEtape(etapeActuelle);
                String meilleure = choisirReponseVocale(transcrit, options);
                if (meilleure == null) meilleure = options[options.length - 1];
                final String reponseFinale = meilleure;

                Platform.runLater(() -> {
                    AudioUtil.prononcer("Reponse enregistree : " + reponseFinale + ".");
                    handleReponse(reponseFinale);
                    if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(false);
                });
            } catch (Exception e) {
                System.err.println("[ColorTest] Erreur capture vocale : " + e.getMessage());
                AudioUtil.prononcer("Erreur lors de la capture vocale. Utilisez les boutons.");
                Platform.runLater(() -> {
                    if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(false);
                });
            }
        }, "VoiceTest-Thread").start();
    }

    private void demarrerReponseVocaleAutomatique(int indexEtape, String question, String[] options) {
        new Thread(() -> {
            try {
                AudioUtil.prononcerEtAttendre("Etape " + (indexEtape + 1) + ". " + question +
                    " Les options sont : " + String.join(", ", options) + ". Repondez maintenant.");
                if (etapeActuelle != indexEtape || !SessionManager.isCommandeVocaleActivee()) return;

                Platform.runLater(() -> {
                    if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(true);
                });

                File audioFile = micRecorder.start();
                Thread.sleep(4000);
                micRecorder.stop();

                if (etapeActuelle != indexEtape) return;

                String transcrit = OpenAIWhisperUtil.transcrire(audioFile);
                System.out.println("[ColorTest-Auto] Transcription vocale : \"" + transcrit + "\"");
                String meilleure = choisirReponseVocale(transcrit, options);
                if (meilleure == null) {
                    AudioUtil.prononcerEtAttendre("Je n'ai pas compris. Vous pouvez cliquer sur une reponse.");
                    Platform.runLater(() -> {
                        if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(false);
                    });
                    return;
                }

                final String reponseFinale = meilleure;
                Platform.runLater(() -> enregistrerReponseEtape(indexEtape, reponseFinale));
            } catch (Exception e) {
                System.err.println("[ColorTest-Auto] Erreur capture vocale : " + e.getMessage());
                Platform.runLater(() -> {
                    if (boutonRepondreVocalement != null) boutonRepondreVocalement.setDisable(false);
                });
            }
        }, "ColorTest-AutoVoice").start();
    }

    private void arreterMicroSiActif() {
        try {
            if (micRecorder.isRecording()) micRecorder.stop();
        } catch (Exception e) {
            System.err.println("[ColorTest] Arret micro ignore : " + e.getMessage());
        }
    }

    private String choisirReponseVocale(String transcrit, String[] options) {
        String reponse = normaliserCommande(transcrit);
        if (reponse.isBlank()) return null;

        if (etapeActuelle == 0) {
            if (correspond(reponse, "rouge", "rouges", "bouge", "bouche", "route", "roug")) return options[0];
            if (correspond(reponse, "vert", "verte", "verts", "verre", "vers", "vair", "ver")) return options[1];
            if (correspond(reponse, "lesdeuxpareils", "deuxpareils", "deuxpareil", "pareil", "pareils", "pareille", "memecouleur", "identique")) return options[2];
        } else if (etapeActuelle == 1) {
            if (correspond(reponse, "jevoischuniffre", "voischuniffre", "chiffre", "chiffres", "nombre", "numero", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf")) return options[0];
            if (correspond(reponse, "riendedistinct", "rien", "aucun", "rienvoir", "jevoisrien", "pasdistinct", "pasdechiffre")) return options[1];
            if (correspond(reponse, "couleurssimilaires", "similaire", "similaires", "pareil", "pareils", "meme", "identique")) return options[2];
        } else {
            if (correspond(reponse, "oui", "ouidifferentes", "differente", "differentes", "different", "differents", "distinctes", "paspareilles")) return options[0];
            if (correspond(reponse, "non", "nonpareilles", "pareil", "pareils", "pareille", "pareilles", "identiques", "memes")) return options[1];
            if (correspond(reponse, "legerementdifferentes", "legerement", "leger", "legere", "unpeu", "unpetitpeu", "peudifferent", "peudifferentes")) return options[2];
        }

        if (correspond(reponse, "jenesaispas", "jesaispas", "saispas", "saisp", "ignore", "aucuneidee")) {
            return options[options.length - 1];
        }

        for (String opt : options) {
            String option = normaliserCommande(opt);
            if (!option.isBlank() && reponse.length() >= 2
                && (reponse.contains(option) || option.contains(reponse) || scoreSimilarite(reponse, option) >= 72)) {
                return opt;
            }
        }
        return null;
    }

    private boolean correspond(String reponse, String... variantes) {
        if (reponse == null || reponse.isBlank() || reponse.length() < 2) return false;
        for (String variante : variantes) {
            String v = normaliserCommande(variante);
            if (!v.isBlank() && (reponse.contains(v) || v.contains(reponse) || scoreSimilarite(reponse, v) >= 74)) {
                return true;
            }
        }
        return false;
    }

    private boolean estOui(String reponse) {
        if (reponse == null || reponse.isBlank()) return false;
        if (contientMotNegatifCommandeVocale(reponse)) return false;
        return reponse.equals("oui") ||
            reponse.equals("ouais") ||
            reponse.equals("wi") ||
            reponse.equals("activez") ||
            reponse.equals("activer") ||
            reponse.equals("active") ||
            reponse.contains("ouiactiv") ||
            reponse.contains("daccord") ||
            reponse.contains("aveccommandevocale") ||
            reponse.contains("aveccommandesvocales") ||
            reponse.contains("activercommandevocale") ||
            reponse.contains("activerlescommandesvocales");
    }

    private boolean estNon(String reponse) {
        if (reponse == null || reponse.isBlank()) return false;
        return reponse.equals("non") ||
            reponse.equals("no") ||
            reponse.equals("nan") ||
            reponse.startsWith("non") ||
            reponse.contains("desactiv") ||
            reponse.contains("pasvocal") ||
            reponse.contains("sansvocal") ||
            reponse.contains("sanscommandevocale") ||
            reponse.contains("sanscommandesvocales");
    }

    private boolean contientMotNegatifCommandeVocale(String reponse) {
        return reponse.contains("non") ||
            reponse.contains("desactiv") ||
            reponse.contains("pasvocal") ||
            reponse.contains("sansvocal") ||
            reponse.contains("sanscommande");
    }

    private String normaliserCommande(String value) {
        if (value == null) return "";
        String cleaned = TexteUtil.reparerEncodage(value);
        return Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
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

    private void analyserEtAppliquerResultats() {
        long difficultes = reponsesUtilisateur.stream()
            .filter(r -> r.contains("pareils") || r.contains("pareilles")
                || r.contains("Rien") || r.contains("Je ne sais pas"))
            .count();

        boolean deficienceBleu = reponsesUtilisateur.size() >= 3 &&
            (reponsesUtilisateur.get(2).contains("pareilles")
                || reponsesUtilisateur.get(2).contains("Je ne sais pas"));

        String profil;
        String theme;
        if (deficienceBleu || difficultes >= 2) {
            profil = "DEUTERANOPIE";
            theme = "theme-daltonien";
            AudioUtil.prononcer("Difficulte visuelle detectee. Interface adaptee en orange.");
        } else if (difficultes == 1) {
            profil = "VISION_REDUITE";
            theme = "theme-contraste";
            AudioUtil.prononcer("Legere difficulte detectee. Contraste augmente.");
        } else {
            profil = "NORMAL";
            theme = "style";
            AudioUtil.prononcer("Vision normale. Interface standard appliquee.");
        }

        SessionManager.setProfilCouleur(profil);
        SessionManager.setThemeCSS(theme);
        appliquerTheme(theme);
        System.out.println("[ColorVisionTest] Profil : " + profil + " -> theme : " + theme);
        naviguerVersConnexion();
    }

    private void appliquerTheme(String theme) {
        try {
            Stage stage = NavigationUtil.getStage(boutonPasser);
            Scene scene = stage.getScene();
            if (scene == null) return;
            scene.getStylesheets().clear();
            var url = getClass().getResource("/css/" + theme + ".css");
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            }
        } catch (Exception e) {
            System.err.println("[ColorVisionTest] Impossible d'appliquer le theme : " + e.getMessage());
        }
    }

    private void naviguerVersConnexion() {
        Stage stage = NavigationUtil.getStage(boutonPasser);
        NavigationUtil.naviguerVers(stage, "/fxml/auth/Login.fxml",
            "Connexion - Systeme d'Amenagement Universitaire");
    }
}
