package com.universite.controller;

import com.universite.dao.ReclamationDAO;
import com.universite.model.Reclamation;
import com.universite.model.Reclamation.Statut;
import com.universite.util.AudioUtil;
import com.universite.util.MicRecorder;
import com.universite.util.NavigationUtil;
import com.universite.util.OpenAIWhisperUtil;
import com.universite.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * ReclamationController – Formulaire de réclamation étudiant.
 * TTS annonce la page à l'arrivée.
 * Bouton saisie vocale pour dicter la description.
 */
public class ReclamationController {

    @FXML private TextField  champSujet;
    @FXML private TextArea   champDescription;
    @FXML private Label      labelConfirmation;
    @FXML private Label      labelEtatVocal;
    @FXML private Label      labelDocument;
    @FXML private Button     boutonVocal;
    // GAP 3 FIX: guided full-form voice workflow button
    @FXML private Button     boutonVocalComplet;
    @FXML private Button     boutonSoumettre;
    @FXML private Button     boutonAnnulerEdition;

    @FXML private TableView<Reclamation>           tableauReclamations;
    @FXML private TableColumn<Reclamation, String> colSujet;
    @FXML private TableColumn<Reclamation, String> colStatut;
    @FXML private TableColumn<Reclamation, String> colDate;
    @FXML private TableColumn<Reclamation, String> colReponse;

    private final ReclamationDAO reclamationDAO = new ReclamationDAO();
    private final ObservableList<Reclamation> listeReclamations = FXCollections.observableArrayList();
    private final MicRecorder micRecorder = new MicRecorder();
    private String cheminDocument = null;
    private boolean enregistrementVocal = false;
    private volatile boolean workflowVocalCompletDemarre = false;
    private File dernierEnregistrement;
    private Reclamation reclamationEnEdition = null;

    @FXML
    public void initialize() {
        colSujet.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(r.getValue().getSujet()));
        colStatut.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(r.getValue().getStatutLabel()));
        colDate.setCellValueFactory(r -> {
            var date = r.getValue().getDateSoumission();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.toLocalDate().toString() : "");
        });
        colReponse.setCellValueFactory(r -> {
            String rep = r.getValue().getReponseAdmin();
            return new javafx.beans.property.SimpleStringProperty(rep != null ? rep : "–");
        });

        tableauReclamations.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Reclamation item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                switch (item.getStatut()) {
                    case RESOLUE:  setStyle("-fx-background-color: #d4edda;"); break;
                    case EN_COURS: setStyle("-fx-background-color: #d1ecf1;"); break;
                    default:       setStyle("-fx-background-color: #fff3cd;"); break;
                }
            }
        });

        champDescription.focusedProperty().addListener((obs, o, n) -> {
            if (n) demarrerAssistanceVocale(
                "Décrivez précisément le problème d'accessibilité ou administratif rencontré.");
        });
        champSujet.focusedProperty().addListener((obs, o, n) -> {
            if (n) demarrerAssistanceVocale(
                "Saisissez un résumé court du sujet de votre réclamation.");
        });

        tableauReclamations.setItems(listeReclamations);
        labelConfirmation.setVisible(false);
        chargerReclamations();
        if (!SessionManager.isCommandeVocaleActivee()) {
            desactiverCommandesVocales();
        }

        if (SessionManager.isCommandeVocaleActivee()) {
            demarrerWorkflowVocalAutomatique();
        }
    }

    private void demarrerWorkflowVocalAutomatique() {
        if (workflowVocalCompletDemarre) return;
        workflowVocalCompletDemarre = true;

        new Thread(() -> {
            AudioUtil.prononcerEtAttendre(
                "Formulaire de reclamation. Commande vocale activee. " +
                "Je vais remplir le formulaire avec vous automatiquement."
            );
            Platform.runLater(this::handleSaisieVocaleComplete);
        }, "Reclamation-AutoVoice").start();
    }

    // ── GAP 3 FIX: Saisie vocale complète (sujet + description) ─────────────────
    // Allows a fully voice-only user to fill both required fields without any click.

    @FXML
    private void handleSaisieVocaleComplete() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (boutonVocalComplet != null) boutonVocalComplet.setDisable(true);
        if (boutonVocal       != null) boutonVocal.setDisable(true);

        new Thread(() -> {
            try {
                // STEP 1 — Dictate subject
                Platform.runLater(() -> {
                    if (labelEtatVocal != null)
                        labelEtatVocal.setText("🎤 Dites le sujet...");
                });
                AudioUtil.prononcerEtAttendre(
                    "Étape un. Dites le sujet de votre réclamation en quelques mots."
                );

                File audioSujet = micRecorder.start();
                pauseMs(5000);
                micRecorder.stop();
                String sujet = OpenAIWhisperUtil.transcrire(audioSujet);

                if (sujet == null || sujet.isBlank()) {
                    AudioUtil.jouerErreur();
                    AudioUtil.prononcer("Sujet non capté. Veuillez réessayer.");
                    return;
                }
                final String sujetFinal = sujet.trim();
                Platform.runLater(() -> {
                    champSujet.setText(sujetFinal);
                    if (labelEtatVocal != null)
                        labelEtatVocal.setText("✅ Sujet enregistré.");
                });
                AudioUtil.prononcerEtAttendre("Sujet enregistré : " + sujetFinal +
                    ". Étape deux. Décrivez maintenant votre réclamation en détail.");

                // STEP 2 — Dictate description
                Platform.runLater(() -> {
                    if (labelEtatVocal != null)
                        labelEtatVocal.setText("🔴 Enregistrement description...");
                });
                File audioDesc = micRecorder.start();
                pauseMs(8000);
                micRecorder.stop();
                String desc = OpenAIWhisperUtil.transcrire(audioDesc);

                if (desc != null && !desc.isBlank()) {
                    final String descFinal = desc.trim();
                    Platform.runLater(() -> {
                        champDescription.setText(descFinal);
                        if (labelEtatVocal != null)
                            labelEtatVocal.setText("✅ Description enregistrée.");
                    });
                    AudioUtil.jouerSucces();
                    AudioUtil.prononcerEtAttendre(
                        "Description enregistree. Formulaire pret. " +
                        "Dites Soumettre pour envoyer la reclamation, ou dites Annuler."
                    );
                    if (ecouterConfirmationSoumission()) {
                        Platform.runLater(this::handleSoumettre);
                    } else {
                        AudioUtil.prononcer("La reclamation n'a pas ete envoyee. Vous pouvez la modifier ou appuyer sur Soumettre.");
                    }
                } else {
                    AudioUtil.jouerErreur();
                    AudioUtil.prononcer("Aucune description captée. Veuillez réessayer.");
                    Platform.runLater(() -> {
                        if (labelEtatVocal != null) labelEtatVocal.setText("⚠ Description non captée.");
                    });
                }

            } catch (Exception ex) {
                AudioUtil.jouerErreur();
                AudioUtil.prononcer("Erreur lors du workflow vocal : " + ex.getMessage());
                System.err.println("[VocalComplet-Reclamation] " + ex.getMessage());
            } finally {
                Platform.runLater(() -> {
                    if (boutonVocalComplet != null) boutonVocalComplet.setDisable(false);
                    if (boutonVocal       != null) boutonVocal.setDisable(false);
                });
            }
        }, "VocalComplet-Reclamation").start();
    }

    private void pauseMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private boolean ecouterConfirmationSoumission() throws Exception {
        Platform.runLater(() -> {
            if (labelEtatVocal != null) labelEtatVocal.setText("Dites Soumettre pour envoyer...");
        });

        File audioConfirmation = micRecorder.start();
        pauseMs(4000);
        micRecorder.stop();

        String commande = OpenAIWhisperUtil.transcrire(audioConfirmation);
        String normalisee = normaliserCommande(commande);
        System.out.println("[VocalComplet-Reclamation] Confirmation transcrite : \"" + commande + "\" -> " + normalisee);

        return normalisee.contains("soumettre") ||
            normalisee.contains("sousmettre") ||
            normalisee.contains("valider") ||
            normalisee.contains("envoyer") ||
            normalisee.equals("oui") ||
            normalisee.contains("confirmer");
    }

    private String normaliserCommande(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    @FXML
    private void handleSoumettre() {
        String sujet = champSujet.getText().trim();
        String desc  = champDescription.getText().trim();
        if (sujet.isEmpty() || desc.isEmpty()) {
            afficherMessage("⚠ Veuillez remplir le sujet et la description.", false);
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Erreur : veuillez remplir le sujet et la description.");
            return;
        }
        if (reclamationEnEdition != null) {
            enregistrerModificationReclamation(sujet, desc);
            return;
        }

        Reclamation r = new Reclamation();
        r.setEtudiantId(SessionManager.getUtilisateur().getId());
        r.setSujet(sujet);
        r.setDescription(desc);
        r.setDocumentJoint(cheminDocument);

        if (reclamationDAO.creer(r)) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Votre réclamation a été soumise avec succès.");
            afficherMessage("✅ Votre réclamation a été soumise avec succès.", true);
            reinitialiserFormulaire();
            chargerReclamations();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Une erreur est survenue. Veuillez réessayer.");
            afficherMessage("❌ Erreur lors de la soumission. Veuillez réessayer.", false);
        }
    }

    @FXML
    private void handleModifier() {
        Reclamation sel = tableauReclamations.getSelectionModel().getSelectedItem();
        if (sel == null) {
            afficherMessage("Veuillez selectionner une reclamation a modifier.", false);
            AudioUtil.prononcer("Veuillez selectionner une reclamation a modifier.");
            return;
        }
        if (sel.getStatut() != Statut.EN_ATTENTE) {
            afficherMessage("Seules les reclamations en attente peuvent etre modifiees.", false);
            AudioUtil.prononcer("Seules les reclamations en attente peuvent etre modifiees.");
            return;
        }

        reclamationEnEdition = sel;
        champSujet.setText(sel.getSujet());
        champDescription.setText(sel.getDescription());
        cheminDocument = sel.getDocumentJoint();
        if (labelDocument != null) {
            labelDocument.setText(nomFichierOuVide(cheminDocument));
        }
        if (boutonSoumettre != null) boutonSoumettre.setText("Enregistrer les modifications");
        if (boutonAnnulerEdition != null) {
            boutonAnnulerEdition.setVisible(true);
            boutonAnnulerEdition.setManaged(true);
        }
        afficherMessage("Modification de la reclamation selectionnee.", true);
        champSujet.requestFocus();
    }

    @FXML
    private void handleAnnulerEdition() {
        reclamationEnEdition = null;
        reinitialiserFormulaire();
        afficherMessage("", true);
        labelConfirmation.setVisible(false);
        AudioUtil.prononcer("Modification annulee.");
    }

    private void enregistrerModificationReclamation(String sujet, String desc) {
        Reclamation r = new Reclamation();
        r.setId(reclamationEnEdition.getId());
        r.setEtudiantId(SessionManager.getUtilisateur().getId());
        r.setSujet(sujet);
        r.setDescription(desc);
        r.setDocumentJoint(cheminDocument);

        if (reclamationDAO.modifierParEtudiant(r)) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Votre reclamation a ete modifiee avec succes.");
            afficherMessage("Votre reclamation a ete modifiee avec succes.", true);
            reclamationEnEdition = null;
            reinitialiserFormulaire();
            chargerReclamations();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("La reclamation ne peut plus etre modifiee.");
            afficherMessage("La reclamation ne peut plus etre modifiee.", false);
            chargerReclamations();
        }
    }

    @FXML
    private void handleJoindreDocument() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Joindre un document a la reclamation");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.doc", "*.jpg", "*.jpeg", "*.png"));
        File fichier = fc.showOpenDialog(boutonSoumettre.getScene().getWindow());
        if (fichier != null) {
            cheminDocument = fichier.getAbsolutePath();
            if (labelDocument != null) labelDocument.setText(fichier.getName());
            AudioUtil.prononcer("Document joint : " + fichier.getName());
        }
    }

    @FXML
    private void handleSaisieVocale() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (!enregistrementVocal) {
            enregistrementVocal = true;
            if (boutonVocal    != null) boutonVocal.setText("⏹  Arrêter");
            if (labelEtatVocal != null) labelEtatVocal.setText("🔴 Enregistrement en cours...");
            try {
                dernierEnregistrement = micRecorder.start();
                AudioUtil.prononcer("Parlez maintenant. Décrivez votre réclamation.");
            } catch (Exception ex) {
                enregistrementVocal = false;
                if (boutonVocal    != null) boutonVocal.setText("🎤  Saisie vocale");
                if (labelEtatVocal != null) labelEtatVocal.setText("❌ Micro indisponible");
                AudioUtil.jouerErreur();
                AudioUtil.prononcer("Impossible d'accéder au microphone.");
            }
        } else {
            enregistrementVocal = false;
            if (boutonVocal    != null) { boutonVocal.setText("🎤  Saisie vocale"); boutonVocal.setDisable(true); }
            if (labelEtatVocal != null) labelEtatVocal.setText("⏳ Transcription en cours...");
            micRecorder.stop();

            File audio = dernierEnregistrement;
            new Thread(() -> {
                try {
                    String texte = OpenAIWhisperUtil.transcrire(audio);
                    Platform.runLater(() -> {
                        if (texte != null && !texte.isBlank()) {
                            if (!champDescription.getText().isEmpty() && !champDescription.getText().endsWith("\n"))
                                champDescription.appendText("\n");
                            champDescription.appendText(texte);
                            if (labelEtatVocal != null) labelEtatVocal.setText("✅ Transcription terminée");
                            AudioUtil.jouerSucces();
                            AudioUtil.prononcer("Transcription terminée.");
                        } else {
                            if (labelEtatVocal != null) labelEtatVocal.setText("⚠ Aucun texte reconnu");
                            AudioUtil.jouerErreur();
                            AudioUtil.prononcer("Aucun texte reconnu. Veuillez réessayer.");
                        }
                        if (boutonVocal != null) boutonVocal.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (labelEtatVocal != null) labelEtatVocal.setText("❌ Erreur transcription");
                        if (boutonVocal    != null) boutonVocal.setDisable(false);
                        AudioUtil.jouerErreur();
                        AudioUtil.prononcer("Erreur lors de la transcription.");
                    });
                }
            }, "Whisper-Transcription").start();
        }
    }

    @FXML
    private void handleSupprimer() {
        Reclamation sel = tableauReclamations.getSelectionModel().getSelectedItem();
        if (sel == null) {
            afficherMessage("⚠ Veuillez sélectionner une réclamation.", false);
            AudioUtil.prononcer("Veuillez sélectionner une réclamation.");
            return;
        }
        if (sel.getStatut() != Statut.EN_ATTENTE) {
            afficherMessage("⚠ Seules les réclamations en attente peuvent être supprimées.", false);
            AudioUtil.prononcer("Seules les réclamations en attente peuvent être supprimées.");
            return;
        }
        if (reclamationDAO.supprimer(sel.getId())) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Réclamation supprimée.");
            chargerReclamations();
        }
    }

    @FXML
    private void handleRetour() {
        AudioUtil.prononcer("Retour au tableau de bord.");
        Stage stage = NavigationUtil.getStage(champSujet);
        NavigationUtil.naviguerVers(stage, "/fxml/student/DashboardEtudiant.fxml", "Mon Espace Étudiant");
    }

    private javafx.animation.PauseTransition pauseAssistance;
    private void demarrerAssistanceVocale(String message) {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (pauseAssistance != null) pauseAssistance.stop();
        pauseAssistance = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(5));
        pauseAssistance.setOnFinished(e -> AudioUtil.prononcer(message));
        pauseAssistance.play();
    }

    private void desactiverCommandesVocales() {
        if (boutonVocal != null) {
            boutonVocal.setVisible(false);
            boutonVocal.setManaged(false);
        }
        if (boutonVocalComplet != null) {
            boutonVocalComplet.setVisible(false);
            boutonVocalComplet.setManaged(false);
        }
        if (labelEtatVocal != null) {
            labelEtatVocal.setVisible(false);
            labelEtatVocal.setManaged(false);
        }
    }

    private void chargerReclamations() {
        listeReclamations.clear();
        listeReclamations.addAll(reclamationDAO.trouverParEtudiant(
            SessionManager.getUtilisateur().getId()));
    }

    private void reinitialiserFormulaire() {
        champSujet.clear();
        champDescription.clear();
        cheminDocument = null;
        if (labelDocument != null) labelDocument.setText("Aucun fichier selectionne");
        if (boutonSoumettre != null) boutonSoumettre.setText("📤  Soumettre la réclamation");
        if (boutonAnnulerEdition != null) {
            boutonAnnulerEdition.setVisible(false);
            boutonAnnulerEdition.setManaged(false);
        }
    }

    private void afficherMessage(String message, boolean succes) {
        labelConfirmation.setText(message);
        labelConfirmation.setStyle(succes ? "-fx-text-fill: #28a745;" : "-fx-text-fill: #dc3545;");
        labelConfirmation.setVisible(true);
    }

    private String nomFichierOuVide(String chemin) {
        if (chemin == null || chemin.isBlank()) return "Aucun fichier selectionne";
        return new File(chemin).getName();
    }
}
