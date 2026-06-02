package com.universite.controller;

import com.universite.dao.DemandeAmenagementDAO;
import com.universite.model.DemandeAmenagement;
import com.universite.model.DemandeAmenagement.TypeAmenagement;
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
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * DemandeController – Formulaire de demande d'aménagement.
 * TTS lit la page à l'arrivée.
 * Bouton saisie vocale pour dicter la description.
 */
public class DemandeController {

    @FXML private ComboBox<String> comboType;
    @FXML private TextArea         champDescription;
    @FXML private Label            labelDocument;
    @FXML private Button           boutonSoumettre;
    @FXML private Label            labelConfirmation;
    @FXML private Button           boutonVocal;
    @FXML private Label            labelEtatVocal;
    // GAP 3 FIX: new button for guided full-form voice workflow
    @FXML private Button           boutonVocalComplet;
    @FXML private Button           boutonAnnulerEdition;

    @FXML private TableView<DemandeAmenagement>          tableauDemandes;
    @FXML private TableColumn<DemandeAmenagement, String> colonneType;
    @FXML private TableColumn<DemandeAmenagement, String> colonneStatut;
    @FXML private TableColumn<DemandeAmenagement, String> colonneDate;
    @FXML private TableColumn<DemandeAmenagement, String> colonneDescription;

    private final DemandeAmenagementDAO demandeDAO = new DemandeAmenagementDAO();
    private String cheminDocument = null;
    private final ObservableList<DemandeAmenagement> listeDemandes = FXCollections.observableArrayList();
    private final MicRecorder micRecorder = new MicRecorder();
    private volatile boolean enEnregistrement = false;
    private volatile boolean workflowVocalCompletDemarre = false;
    private DemandeAmenagement demandeEnEdition = null;

    @FXML
    public void initialize() {
        comboType.setItems(FXCollections.observableArrayList(
            "Aménagement d'examen", "Accessibilité", "Service d'assistance", "Autre"));

        colonneType.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getTypeAmenagementLabel()));
        colonneStatut.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getStatutLabel()));
        colonneDate.setCellValueFactory(data -> {
            var date = data.getValue().getDateSoumission();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.toLocalDate().toString() : "");
        });
        colonneDescription.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));

        tableauDemandes.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(DemandeAmenagement item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                switch (item.getStatut()) {
                    case ACCEPTEE: setStyle("-fx-background-color: #d4edda;"); break;
                    case REFUSEE:  setStyle("-fx-background-color: #f8d7da;"); break;
                    default:       setStyle("-fx-background-color: #fff3cd;"); break;
                }
            }
        });

        tableauDemandes.setItems(listeDemandes);
        labelConfirmation.setVisible(false);
        chargerDemandes();
        if (!SessionManager.isCommandeVocaleActivee()) {
            desactiverCommandesVocales();
        }

        // Assistance vocale sur le champ description
        champDescription.focusedProperty().addListener((obs, o, n) -> {
            if (n) demarrerAssistanceVocale(
                "Décrivez votre demande. Précisez la nature de votre situation et les aménagements souhaités.");
        });

        if (SessionManager.isCommandeVocaleActivee()) {
            demarrerWorkflowVocalAutomatique();
        }
    }

    private void demarrerWorkflowVocalAutomatique() {
        if (workflowVocalCompletDemarre) return;
        workflowVocalCompletDemarre = true;

        new Thread(() -> {
            AudioUtil.prononcerEtAttendre(
                "Formulaire de demande d'amenagement. Commande vocale activee. " +
                "Je vais remplir le formulaire avec vous automatiquement."
            );
            Platform.runLater(this::handleSaisieVocaleComplete);
        }, "Demande-AutoVoice").start();
    }

    @FXML private void handleRetour() {
        AudioUtil.prononcer("Retour au tableau de bord.");
        Stage stage = NavigationUtil.getStage(boutonSoumettre);
        NavigationUtil.naviguerVers(stage, "/fxml/student/DashboardEtudiant.fxml", "Mon espace étudiant");
    }

    // ── Saisie vocale (toggle start/stop) ────────────────────────

    @FXML
    private void handleSaisieVocale() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (!enEnregistrement) {
            try {
                micRecorder.start();
                enEnregistrement = true;
                if (labelEtatVocal != null) labelEtatVocal.setText("🔴 Enregistrement...");
                if (boutonVocal   != null) boutonVocal.setText("⏹  Arrêter");
                AudioUtil.prononcer("Enregistrement démarré. Parlez maintenant.");
            } catch (Exception e) {
                AudioUtil.jouerErreur();
                AudioUtil.prononcer("Impossible d'accéder au microphone.");
                System.err.println("Erreur démarrage micro : " + e.getMessage());
            }
            return;
        }

        // Stop
        File audioFile;
        try {
            audioFile = micRecorder.stop();
            enEnregistrement = false;
            if (labelEtatVocal != null) labelEtatVocal.setText("⏳ Transcription...");
            if (boutonVocal   != null) { boutonVocal.setText("🎤  Saisie vocale"); boutonVocal.setDisable(true); }
        } catch (Exception e) {
            enEnregistrement = false;
            AudioUtil.jouerErreur();
            System.err.println("Erreur arrêt micro : " + e.getMessage());
            return;
        }

        new Thread(() -> {
            try {
                String texte = OpenAIWhisperUtil.transcrire(audioFile);
                Platform.runLater(() -> {
                    if (texte != null && !texte.isBlank()) {
                        String actuel = champDescription.getText() != null ? champDescription.getText().trim() : "";
                        if (!actuel.isEmpty() && !actuel.endsWith(" ")) actuel += " ";
                        champDescription.setText(actuel + texte.trim());
                        champDescription.positionCaret(champDescription.getText().length());
                        AudioUtil.jouerSucces();
                        AudioUtil.prononcer("Transcription terminée.");
                    } else {
                        AudioUtil.jouerErreur();
                        AudioUtil.prononcer("Aucun texte reconnu. Veuillez réessayer.");
                    }
                    if (labelEtatVocal != null) labelEtatVocal.setText("");
                    if (boutonVocal   != null) boutonVocal.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    AudioUtil.jouerErreur();
                    AudioUtil.prononcer("Erreur lors de la transcription.");
                    if (labelEtatVocal != null) labelEtatVocal.setText("❌ Erreur transcription");
                    if (boutonVocal   != null) boutonVocal.setDisable(false);
                });
                System.err.println("Erreur transcription : " + ex.getMessage());
            }
        }).start();
    }

    // ── GAP 3 FIX: Saisie vocale complète (type + description) ──────────────────
    // Allows a fully voice-only user to fill both required fields without any click.

    @FXML
    private void handleSaisieVocaleComplete() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (boutonVocalComplet != null) boutonVocalComplet.setDisable(true);
        if (boutonVocal       != null) boutonVocal.setDisable(true);
        boutonSoumettre.setDisable(true);

        new Thread(() -> {
            try {
                // STEP 1 — Choose type via voice
                Platform.runLater(() -> {
                    if (labelEtatVocal != null)
                        labelEtatVocal.setText("🎤 Dites le type de demande...");
                });
                AudioUtil.prononcerEtAttendre(
                    "Étape un. Dites le type de votre demande. " +
                    "Les choix sont : Aménagement d'examen, Accessibilité, " +
                    "Service d'assistance, ou Autre. Parlez maintenant."
                );

                File audioType = micRecorder.start();
                pauseMs(5000);
                micRecorder.stop();
                String typeTranscrit = OpenAIWhisperUtil.transcrire(audioType);
                String typeFinal = choisirType(typeTranscrit);
                System.out.println("[VocalComplet-Demande] Type transcrit : \"" + typeTranscrit + "\" -> " + typeFinal);
                if (typeFinal == null) {
                    AudioUtil.jouerErreur();
                    AudioUtil.prononcer("Type de demande non reconnu. Veuillez relancer le formulaire vocal complet.");
                    Platform.runLater(() -> {
                        comboType.setValue(null);
                        if (labelEtatVocal != null)
                            labelEtatVocal.setText("Type non reconnu. Veuillez recommencer.");
                    });
                    return;
                }

                Platform.runLater(() -> {
                    comboType.setValue(typeFinal);
                    if (labelEtatVocal != null)
                        labelEtatVocal.setText("✅ Type : " + typeFinal);
                });
                AudioUtil.prononcerEtAttendre("Type sélectionné : " + typeFinal +
                    ". Étape deux. Décrivez maintenant votre demande. Parlez maintenant.");

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
                        "Description enregistrée. Formulaire prêt. " +
                        "Dites Soumettre pour envoyer la demande, ou dites Annuler."
                    );
                    if (ecouterConfirmationSoumission()) {
                        Platform.runLater(this::handleSoumettre);
                    } else {
                        AudioUtil.prononcer("La demande n'a pas été envoyée. Vous pouvez la modifier ou appuyer sur Soumettre.");
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
                System.err.println("[VocalComplet-Demande] " + ex.getMessage());
            } finally {
                Platform.runLater(() -> {
                    if (boutonVocalComplet != null) boutonVocalComplet.setDisable(false);
                    if (boutonVocal       != null) boutonVocal.setDisable(false);
                    boutonSoumettre.setDisable(false);
                });
            }
        }, "VocalComplet-Demande").start();
    }

    /** Match transcribed type text to the closest ComboBox option. */
    private String choisirType(String transcrit) {
        if (transcrit == null || transcrit.isBlank()) return null;
        String normalise = normaliserTexteVocal(transcrit);
        if (normalise.contains("examen") || normalise.contains("exam")) {
            return "Am\u00e9nagement d'examen";
        }
        if (normalise.contains("accessibilite") || normalise.contains("access") ||
            normalise.contains("acces") || normalise.contains("accessible") ||
            normalise.contains("mobilite") || normalise.contains("ascenseur") ||
            normalise.contains("rampe")) {
            return "Accessibilit\u00e9";
        }
        if (normalise.contains("assistance") || normalise.contains("service") ||
            normalise.contains("aide") || normalise.contains("accompagnement")) {
            return "Service d'assistance";
        }
        if (normalise.contains("autre")) {
            return "Autre";
        }
        if (!normalise.isBlank()) return null;
        String t = transcrit.toLowerCase();
        if (t.contains("examen") || t.contains("exam"))               return "Aménagement d'examen";
        if (t.contains("accessib"))                                    return "Accessibilité";
        if (t.contains("assistance") || t.contains("service"))        return "Service d'assistance";
        return "Autre";
    }

    private String normaliserTexteVocal(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        return normalized
            .replace("axessibilite", "accessibilite")
            .replace("accecibilite", "accessibilite")
            .replace("accesibilite", "accessibilite");
    }

    private boolean ecouterConfirmationSoumission() throws Exception {
        Platform.runLater(() -> {
            if (labelEtatVocal != null) labelEtatVocal.setText("Dites Soumettre pour envoyer...");
        });

        File audioConfirmation = micRecorder.start();
        pauseMs(4000);
        micRecorder.stop();

        String commande = OpenAIWhisperUtil.transcrire(audioConfirmation);
        String normalisee = normaliserTexteVocal(commande);
        System.out.println("[VocalComplet-Demande] Confirmation transcrite : \"" + commande + "\" -> " + normalisee);

        return normalisee.contains("soumettre") ||
            normalisee.contains("sousmettre") ||
            normalisee.contains("valider") ||
            normalisee.contains("envoyer") ||
            normalisee.equals("oui") ||
            normalisee.contains("confirmer");
    }

    private void pauseMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    @FXML
    private void handleSoumettre() {
        if (comboType.getValue() == null || champDescription.getText().trim().isEmpty()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez remplir le type et la description de la demande.");
            afficherConfirmation("⚠ Veuillez remplir tous les champs obligatoires.", false);
            return;
        }

        if (demandeEnEdition != null) {
            enregistrerModificationDemande();
            return;
        }

        DemandeAmenagement demande = new DemandeAmenagement();
        demande.setEtudiantId(SessionManager.getUtilisateur().getId());
        demande.setTypeAmenagement(convertirType(comboType.getValue()));
        demande.setDescription(champDescription.getText().trim());
        demande.setDocumentJoint(cheminDocument);

        if (demandeDAO.creer(demande)) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Votre demande a été soumise avec succès.");
            afficherConfirmation("✅ Votre demande a été soumise avec succès.", true);
            reinitialiserFormulaire();
            chargerDemandes();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Une erreur est survenue lors de la soumission. Veuillez réessayer.");
            afficherConfirmation("❌ Une erreur est survenue. Veuillez réessayer.", false);
        }
    }

    @FXML
    private void handleModifier() {
        DemandeAmenagement sel = tableauDemandes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AudioUtil.prononcer("Veuillez selectionner une demande a modifier.");
            afficherConfirmation("Veuillez selectionner une demande a modifier.", false);
            return;
        }
        if (sel.getStatut() != DemandeAmenagement.Statut.EN_ATTENTE) {
            AudioUtil.prononcer("Seules les demandes en attente peuvent etre modifiees.");
            afficherConfirmation("Seules les demandes en attente peuvent etre modifiees.", false);
            return;
        }

        demandeEnEdition = sel;
        comboType.setValue(sel.getTypeAmenagementLabel());
        champDescription.setText(sel.getDescription());
        cheminDocument = sel.getDocumentJoint();
        labelDocument.setText(cheminDocument == null || cheminDocument.isBlank()
            ? "Aucun fichier selectionne"
            : cheminDocument);
        boutonSoumettre.setText("Enregistrer les modifications");
        if (boutonAnnulerEdition != null) {
            boutonAnnulerEdition.setVisible(true);
            boutonAnnulerEdition.setManaged(true);
        }
        afficherConfirmation("Modification de la demande selectionnee.", true);
        champDescription.requestFocus();
    }

    @FXML
    private void handleAnnulerEdition() {
        demandeEnEdition = null;
        reinitialiserFormulaire();
        afficherConfirmation("", true);
        labelConfirmation.setVisible(false);
        AudioUtil.prononcer("Modification annulee.");
    }

    private void enregistrerModificationDemande() {
        DemandeAmenagement demande = new DemandeAmenagement();
        demande.setId(demandeEnEdition.getId());
        demande.setEtudiantId(SessionManager.getUtilisateur().getId());
        demande.setTypeAmenagement(convertirType(comboType.getValue()));
        demande.setDescription(champDescription.getText().trim());
        demande.setDocumentJoint(cheminDocument);

        if (demandeDAO.modifierParEtudiant(demande)) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Votre demande a ete modifiee avec succes.");
            afficherConfirmation("Votre demande a ete modifiee avec succes.", true);
            demandeEnEdition = null;
            reinitialiserFormulaire();
            chargerDemandes();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("La demande ne peut plus etre modifiee.");
            afficherConfirmation("La demande ne peut plus etre modifiee.", false);
            chargerDemandes();
        }
    }

    @FXML
    private void handleJoindreDocument() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Joindre un document justificatif");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.jpg", "*.png"));
        File fichier = fc.showOpenDialog(boutonSoumettre.getScene().getWindow());
        if (fichier != null) {
            cheminDocument = fichier.getAbsolutePath();
            labelDocument.setText("📎 " + fichier.getName());
            AudioUtil.prononcer("Document joint : " + fichier.getName());
        }
    }

    @FXML
    private void handleSupprimer() {
        DemandeAmenagement sel = tableauDemandes.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AudioUtil.prononcer("Veuillez sélectionner une demande à supprimer.");
            afficherConfirmation("⚠ Veuillez sélectionner une demande à supprimer.", false);
            return;
        }
        if (sel.getStatut() != DemandeAmenagement.Statut.EN_ATTENTE) {
            AudioUtil.prononcer("Seules les demandes en attente peuvent être supprimées.");
            afficherConfirmation("⚠ Seules les demandes en attente peuvent être supprimées.", false);
            return;
        }
        if (demandeDAO.supprimer(sel.getId())) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Demande supprimée.");
            chargerDemandes();
        }
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

    private void chargerDemandes() {
        listeDemandes.clear();
        listeDemandes.addAll(demandeDAO.trouverParEtudiant(SessionManager.getUtilisateur().getId()));
    }

    private void reinitialiserFormulaire() {
        comboType.setValue(null);
        champDescription.clear();
        cheminDocument = null;
        labelDocument.setText("Aucun fichier sélectionné");
        boutonSoumettre.setText("✅  Soumettre la demande");
        if (boutonAnnulerEdition != null) {
            boutonAnnulerEdition.setVisible(false);
            boutonAnnulerEdition.setManaged(false);
        }
    }

    private void afficherConfirmation(String message, boolean succes) {
        labelConfirmation.setText(message);
        labelConfirmation.setStyle(succes ? "-fx-text-fill: #28a745;" : "-fx-text-fill: #dc3545;");
        labelConfirmation.setVisible(true);
    }

    private TypeAmenagement convertirType(String label) {
        switch (label) {
            case "Aménagement d'examen": return TypeAmenagement.EXAMEN;
            case "Accessibilité":        return TypeAmenagement.ACCESSIBILITE;
            case "Service d'assistance": return TypeAmenagement.ASSISTANCE;
            default:                     return TypeAmenagement.AUTRE;
        }
    }
}
