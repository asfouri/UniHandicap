package com.universite.controller;

import com.universite.dao.ArchivesDAO;
import com.universite.model.DemandeAmenagement;
import com.universite.model.Reclamation;
import com.universite.util.AudioUtil;
import com.universite.util.NavigationUtil;
import com.universite.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class ArchivesController {

    @FXML private ComboBox<String> comboStatutDemande;
    @FXML private ComboBox<String> comboTypeDemande;
    @FXML private DatePicker dateDuDemande;
    @FXML private DatePicker dateAuDemande;
    @FXML private TextField champMotCleDemande;
    @FXML private TableView<DemandeAmenagement> tableDemandes;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeId;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeEtudiant;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeType;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeStatut;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeDate;
    @FXML private TableColumn<DemandeAmenagement, String> colDemandeDesc;

    @FXML private ComboBox<String> comboStatutReclamation;
    @FXML private DatePicker dateDuReclamation;
    @FXML private DatePicker dateAuReclamation;
    @FXML private TextField champMotCleReclamation;
    @FXML private TableView<Reclamation> tableReclamations;
    @FXML private TableColumn<Reclamation, String> colReclamationId;
    @FXML private TableColumn<Reclamation, String> colReclamationEtudiant;
    @FXML private TableColumn<Reclamation, String> colReclamationSujet;
    @FXML private TableColumn<Reclamation, String> colReclamationStatut;
    @FXML private TableColumn<Reclamation, String> colReclamationDate;
    @FXML private TableColumn<Reclamation, String> colReclamationDesc;

    private final ArchivesDAO archivesDAO = new ArchivesDAO();
    private final ObservableList<DemandeAmenagement> demandesArchivees = FXCollections.observableArrayList();
    private final ObservableList<Reclamation> reclamationsArchivees = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        initialiserFiltres();
        initialiserTableDemandes();
        initialiserTableReclamations();
        handleRechercherDemandes();
        handleRechercherReclamations();
        AudioUtil.prononcer("Archives administrateur.");
    }

    private void initialiserFiltres() {
        comboStatutDemande.setItems(FXCollections.observableArrayList("Tous", "ACCEPTEE", "REFUSEE"));
        comboStatutDemande.setValue("Tous");
        comboTypeDemande.setItems(FXCollections.observableArrayList("Tous", "EXAMEN", "ACCESSIBILITE", "ASSISTANCE", "AUTRE"));
        comboTypeDemande.setValue("Tous");
        comboStatutReclamation.setItems(FXCollections.observableArrayList("Tous", "RESOLUE"));
        comboStatutReclamation.setValue("Tous");
    }

    private void initialiserTableDemandes() {
        colDemandeId.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));
        colDemandeEtudiant.setCellValueFactory(d -> new SimpleStringProperty(nullToEmpty(d.getValue().getNomEtudiant())));
        colDemandeType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTypeAmenagementLabel()));
        colDemandeStatut.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatutLabel()));
        colDemandeDate.setCellValueFactory(d -> {
            var date = d.getValue().getDateSoumission();
            return new SimpleStringProperty(date != null ? date.toLocalDate().toString() : "");
        });
        colDemandeDesc.setCellValueFactory(d -> new SimpleStringProperty(nullToEmpty(d.getValue().getDescription())));
        tableDemandes.setItems(demandesArchivees);
    }

    private void initialiserTableReclamations() {
        colReclamationId.setCellValueFactory(r -> new SimpleStringProperty(String.valueOf(r.getValue().getId())));
        colReclamationEtudiant.setCellValueFactory(r -> new SimpleStringProperty(nullToEmpty(r.getValue().getNomEtudiant())));
        colReclamationSujet.setCellValueFactory(r -> new SimpleStringProperty(nullToEmpty(r.getValue().getSujet())));
        colReclamationStatut.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().getStatutLabel()));
        colReclamationDate.setCellValueFactory(r -> {
            var date = r.getValue().getDateSoumission();
            return new SimpleStringProperty(date != null ? date.toLocalDate().toString() : "");
        });
        colReclamationDesc.setCellValueFactory(r -> new SimpleStringProperty(nullToEmpty(r.getValue().getDescription())));
        tableReclamations.setItems(reclamationsArchivees);
    }

    @FXML
    private void handleRetour() {
        AudioUtil.prononcer("Retour au tableau de bord administrateur.");
        Stage stage = NavigationUtil.getStage(tableDemandes != null ? tableDemandes : tableReclamations);
        NavigationUtil.naviguerVers(stage, "/fxml/admin/DashboardAdmin.fxml", "Tableau de bord - Administration");
    }

    @FXML
    private void handleRechercherDemandes() {
        String statut = comboStatutDemande != null ? comboStatutDemande.getValue() : "Tous";
        String type = comboTypeDemande != null ? comboTypeDemande.getValue() : "Tous";
        String motCle = champMotCleDemande != null ? champMotCleDemande.getText().trim() : "";
        demandesArchivees.setAll(archivesDAO.rechercherDemandes(
            statut,
            type,
            dateDuDemande != null ? dateDuDemande.getValue() : null,
            dateAuDemande != null ? dateAuDemande.getValue() : null,
            motCle.isBlank() ? null : motCle
        ));
        AudioUtil.prononcer(demandesArchivees.size() + " demandes archivees trouvees.");
    }

    @FXML
    private void handleRechercherReclamations() {
        String statut = comboStatutReclamation != null ? comboStatutReclamation.getValue() : "Tous";
        String motCle = champMotCleReclamation != null ? champMotCleReclamation.getText().trim() : "";
        reclamationsArchivees.setAll(archivesDAO.rechercherReclamations(
            statut,
            dateDuReclamation != null ? dateDuReclamation.getValue() : null,
            dateAuReclamation != null ? dateAuReclamation.getValue() : null,
            motCle.isBlank() ? null : motCle
        ));
        AudioUtil.prononcer(reclamationsArchivees.size() + " reclamations archivees trouvees.");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
