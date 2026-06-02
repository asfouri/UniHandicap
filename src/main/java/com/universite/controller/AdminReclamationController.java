package com.universite.controller;

import com.universite.dao.ReclamationDAO;
import com.universite.model.Reclamation;
import com.universite.model.Reclamation.Statut;
import com.universite.util.AudioUtil;
import com.universite.util.NavigationUtil;
import com.universite.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/**
 * AdminReclamationController – Gestion des réclamations (côté admin).
 * TTS annonce la page et accompagne les actions.
 */
public class AdminReclamationController {

    @FXML private TableView<Reclamation>           tableauReclamations;
    @FXML private TableColumn<Reclamation, String> colEtudiant;
    @FXML private TableColumn<Reclamation, String> colSujet;
    @FXML private TableColumn<Reclamation, String> colStatut;
    @FXML private TableColumn<Reclamation, String> colDate;
    @FXML private TableColumn<Reclamation, String> colDocument;
    @FXML private TableColumn<Reclamation, String> colDesc;

    @FXML private ComboBox<String> comboFiltreStatut;
    @FXML private ComboBox<String> comboNouveauStatut;
    @FXML private TextArea         champReponse;
    @FXML private Label            labelConfirmation;
    @FXML private Label            labelAdmin;

    private final ReclamationDAO reclamationDAO = new ReclamationDAO();
    private final ObservableList<Reclamation> listeReclamations = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (labelAdmin != null)
            labelAdmin.setText("Connecté : " + SessionManager.getUtilisateur().getNomComplet());

        colEtudiant.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(r.getValue().getNomEtudiant()));
        colSujet.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(r.getValue().getSujet()));
        colStatut.setCellValueFactory(r ->
            new javafx.beans.property.SimpleStringProperty(r.getValue().getStatutLabel()));
        colDate.setCellValueFactory(r -> {
            var date = r.getValue().getDateSoumission();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.toLocalDate().toString() : "");
        });
        if (colDesc != null) {
            colDesc.setCellValueFactory(r ->
                new javafx.beans.property.SimpleStringProperty(r.getValue().getDescription()));
        }
        if (colDocument != null) {
            colDocument.setCellValueFactory(r ->
                new javafx.beans.property.SimpleStringProperty(nomFichier(r.getValue().getDocumentJoint())));
        }

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

        if (comboFiltreStatut != null && comboFiltreStatut.getValue() == null) {
            comboFiltreStatut.setValue("Tous");
        }
        if (comboNouveauStatut != null && comboNouveauStatut.getItems().isEmpty()) {
            comboNouveauStatut.setItems(FXCollections.observableArrayList("EN_ATTENTE", "EN_COURS", "RESOLUE"));
        }
        tableauReclamations.setItems(listeReclamations);
        if (labelConfirmation != null) labelConfirmation.setVisible(false);
        chargerReclamations();

        // TTS à l'arrivée
        AudioUtil.prononcer(
            "Gestion des réclamations. " + listeReclamations.size() +
            " réclamation" + (listeReclamations.size() != 1 ? "s" : "") +
            ". Sélectionnez une réclamation pour la traiter."
        );
    }

    @FXML
    private void handleMettreAJour() {
        Reclamation sel = tableauReclamations.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AudioUtil.prononcer("Veuillez sélectionner une réclamation.");
            return;
        }
        String nouveauStatut = comboNouveauStatut != null ? comboNouveauStatut.getValue() : null;
        String reponse       = champReponse != null ? champReponse.getText().trim() : "";

        if (nouveauStatut == null) {
            AudioUtil.prononcer("Veuillez choisir un statut.");
            return;
        }

        try {
            int adminId = SessionManager.getUtilisateur().getId();
            Statut statut = Statut.valueOf(nouveauStatut);
            sel.setStatut(statut);
            sel.setReponseAdmin(reponse.isEmpty() ? null : reponse);
            if (reclamationDAO.mettreAJourStatut(sel.getId(), statut, adminId, reponse.isEmpty() ? null : reponse)) {
                AudioUtil.jouerSucces();
                AudioUtil.prononcer("Réclamation mise à jour : " + nouveauStatut.toLowerCase().replace("_", " ") + ".");
                if (labelConfirmation != null) {
                    labelConfirmation.setText("✅ Réclamation mise à jour.");
                    labelConfirmation.setVisible(true);
                }
                if (champReponse != null) champReponse.clear();
                chargerReclamations();
            }
        } catch (IllegalArgumentException e) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Statut invalide.");
        }
    }

    @FXML
    private void handleActualiser() {
        chargerReclamations();
        AudioUtil.prononcer("Liste actualisee.");
    }

    @FXML
    private void handleArchiverReclamation() {
        Reclamation sel = tableauReclamations.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez selectionner une reclamation a archiver.");
            return;
        }
        if (sel.getStatut() != Statut.RESOLUE) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Seules les reclamations resolues peuvent etre archivees.");
            return;
        }

        if (reclamationDAO.archiver(sel.getId())) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Reclamation archivee.");
            if (champReponse != null) champReponse.clear();
            chargerReclamations();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Impossible d'archiver la reclamation.");
        }
    }

    @FXML
    private void handleOuvrirDocument() {
        Reclamation sel = tableauReclamations.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez selectionner une reclamation.");
            return;
        }
        ouvrirDocument(sel.getDocumentJoint());
    }

    @FXML
    private void handleRetour() {
        AudioUtil.prononcer("Retour au tableau de bord administrateur.");
        Stage stage = NavigationUtil.getStage(tableauReclamations);
        NavigationUtil.naviguerVers(stage, "/fxml/admin/DashboardAdmin.fxml", "Tableau de bord – Administration");
    }

    private void chargerReclamations() {
        listeReclamations.clear();
        String filtre = comboFiltreStatut != null ? comboFiltreStatut.getValue() : null;
        for (Reclamation reclamation : reclamationDAO.trouverToutes()) {
            if (filtre == null || "Tous".equals(filtre) || reclamation.getStatut().name().equals(filtre)) {
                listeReclamations.add(reclamation);
            }
        }
    }

    private void ouvrirDocument(String chemin) {
        if (chemin == null || chemin.isBlank()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Aucun fichier joint pour cette reclamation.");
            if (labelConfirmation != null) {
                labelConfirmation.setText("Aucun fichier joint pour cette reclamation.");
                labelConfirmation.setVisible(true);
            }
            return;
        }

        File fichier = new File(chemin);
        if (!fichier.exists()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Le fichier joint est introuvable.");
            if (labelConfirmation != null) {
                labelConfirmation.setText("Fichier introuvable : " + chemin);
                labelConfirmation.setVisible(true);
            }
            return;
        }

        try {
            Desktop.getDesktop().open(fichier);
            AudioUtil.prononcer("Ouverture du fichier joint.");
        } catch (IOException | RuntimeException e) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Impossible d'ouvrir le fichier joint.");
            if (labelConfirmation != null) {
                labelConfirmation.setText("Impossible d'ouvrir le fichier : " + e.getMessage());
                labelConfirmation.setVisible(true);
            }
        }
    }

    private String nomFichier(String chemin) {
        if (chemin == null || chemin.isBlank()) return "-";
        return new File(chemin).getName();
    }
}
