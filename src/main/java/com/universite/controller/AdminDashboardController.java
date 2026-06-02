package com.universite.controller;

import com.universite.dao.DemandeAmenagementDAO;
import com.universite.dao.ReclamationDAO;
import com.universite.model.DemandeAmenagement;
import com.universite.model.DemandeAmenagement.Statut;
import com.universite.model.DemandeAmenagement.TypeAmenagement;
import com.universite.model.Reclamation;
import com.universite.util.AudioUtil;
import com.universite.util.NavigationUtil;
import com.universite.util.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AdminDashboardController {

    @FXML private Label labelAdmin;
    @FXML private Label labelEnAttente;
    @FXML private Label labelAcceptees;
    @FXML private Label labelRefusees;
    @FXML private Label labelTotalReclamations;
    @FXML private Label labelTotalDemandes;

    @FXML private ComboBox<String> comboFiltreStatut;
    @FXML private ComboBox<String> comboFiltreType;
    @FXML private DatePicker dateDu;
    @FXML private DatePicker dateAu;
    @FXML private javafx.scene.control.TextField champMotCle;

    @FXML private PieChart chartStatuts;
    @FXML private BarChart<String, Number> chartTypes;

    @FXML private TableView<DemandeAmenagement> tableauDemandes;
    @FXML private TableColumn<DemandeAmenagement, String> colNomEtudiant;
    @FXML private TableColumn<DemandeAmenagement, String> colType;
    @FXML private TableColumn<DemandeAmenagement, String> colStatut;
    @FXML private TableColumn<DemandeAmenagement, String> colDate;
    @FXML private TableColumn<DemandeAmenagement, String> colDocument;

    @FXML private ComboBox<String> comboNouveauStatut;
    @FXML private TextArea champCommentaire;
    @FXML private Button boutonMettreAJour;
    @FXML private ScrollPane scrollPane;

    private final DemandeAmenagementDAO demandeDAO = new DemandeAmenagementDAO();
    private final ReclamationDAO reclamationDAO = new ReclamationDAO();
    private final ObservableList<DemandeAmenagement> demandesAffichees =
        FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (SessionManager.getUtilisateur() != null && labelAdmin != null) {
            labelAdmin.setText("Connecte : " + SessionManager.getUtilisateur().getNomComplet());
        }

        initialiserTableau();
        initialiserFiltres();
        initialiserTraitement();
        chargerDonnees();

        AudioUtil.prononcer("Tableau de bord administrateur.");
    }

    private void initialiserTableau() {
        colNomEtudiant.setCellValueFactory(d ->
            new SimpleStringProperty(nullToEmpty(d.getValue().getNomEtudiant())));
        colType.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTypeAmenagementLabel()));
        colStatut.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatutLabel()));
        colDate.setCellValueFactory(d -> {
            var date = d.getValue().getDateSoumission();
            return new SimpleStringProperty(date != null ? date.toLocalDate().toString() : "");
        });
        if (colDocument != null) {
            colDocument.setCellValueFactory(d ->
                new SimpleStringProperty(nomFichier(d.getValue().getDocumentJoint())));
        }

        tableauDemandes.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(DemandeAmenagement item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    return;
                }
                switch (item.getStatut()) {
                    case ACCEPTEE: setStyle("-fx-background-color: #d4edda;"); break;
                    case REFUSEE: setStyle("-fx-background-color: #f8d7da;"); break;
                    default: setStyle("-fx-background-color: #fff3cd;"); break;
                }
            }
        });

        tableauDemandes.setItems(demandesAffichees);
    }

    private void initialiserFiltres() {
        comboFiltreStatut.setItems(FXCollections.observableArrayList(
            "Tous", "EN_ATTENTE", "ACCEPTEE", "REFUSEE"));
        comboFiltreStatut.setValue("Tous");

        comboFiltreType.setItems(FXCollections.observableArrayList(
            "Tous", "EXAMEN", "ACCESSIBILITE", "ASSISTANCE", "AUTRE"));
        comboFiltreType.setValue("Tous");
    }

    private void initialiserTraitement() {
        comboNouveauStatut.setItems(FXCollections.observableArrayList(
            "EN_ATTENTE", "ACCEPTEE", "REFUSEE"));
    }

    private void chargerDonnees() {
        List<DemandeAmenagement> toutes = demandeDAO.trouverToutes();
        appliquerDonnees(toutes);
    }

    private void appliquerDonnees(List<DemandeAmenagement> demandes) {
        demandesAffichees.setAll(demandes);
        mettreAJourKpis(demandes);
        mettreAJourGraphiques(demandes);
    }

    private void mettreAJourKpis(List<DemandeAmenagement> demandes) {
        long enAttente = demandes.stream().filter(d -> d.getStatut() == Statut.EN_ATTENTE).count();
        long acceptees = demandes.stream().filter(d -> d.getStatut() == Statut.ACCEPTEE).count();
        long refusees = demandes.stream().filter(d -> d.getStatut() == Statut.REFUSEE).count();
        int reclamations = reclamationDAO.trouverToutes().size();

        labelEnAttente.setText(String.valueOf(enAttente));
        labelAcceptees.setText(String.valueOf(acceptees));
        labelRefusees.setText(String.valueOf(refusees));
        labelTotalReclamations.setText(String.valueOf(reclamations));
        labelTotalDemandes.setText(String.valueOf(demandes.size()));
    }

    private void mettreAJourGraphiques(List<DemandeAmenagement> demandes) {
        long enAttente = demandes.stream().filter(d -> d.getStatut() == Statut.EN_ATTENTE).count();
        long acceptees = demandes.stream().filter(d -> d.getStatut() == Statut.ACCEPTEE).count();
        long refusees = demandes.stream().filter(d -> d.getStatut() == Statut.REFUSEE).count();

        chartStatuts.setData(FXCollections.observableArrayList(
            new PieChart.Data("En attente", enAttente),
            new PieChart.Data("Acceptees", acceptees),
            new PieChart.Data("Refusees", refusees)
        ));

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Demandes");
        for (TypeAmenagement type : TypeAmenagement.values()) {
            long total = demandes.stream().filter(d -> d.getTypeAmenagement() == type).count();
            serie.getData().add(new XYChart.Data<>(type.name(), total));
        }
        chartTypes.getData().setAll(serie);
    }

    @FXML
    private void handleVoirTableauDeBord() {
        chargerDonnees();
        if (scrollPane != null) scrollPane.setVvalue(0);
        AudioUtil.prononcer("Tableau de bord actualise.");
    }

    @FXML
    private void handleVoirDemandes() {
        if (scrollPane != null) scrollPane.setVvalue(0.75);
        AudioUtil.prononcer("Liste des demandes.");
    }

    @FXML
    private void handleVoirReclamations() {
        naviguerVers("/fxml/admin/GestionReclamations.fxml", "Gestion des reclamations");
    }

    @FXML
    private void handleVoirArchives() {
        naviguerVers("/fxml/admin/Archives.fxml", "Archives");
    }

    @FXML
    private void handleDeconnexion() {
        SessionManager.clear();
        naviguerVers("/fxml/auth/Login.fxml", "Connexion");
    }

    @FXML
    private void handleAppliquerFiltres() {
        List<DemandeAmenagement> filtrees = demandeDAO.trouverToutes().stream()
            .filter(this::filtrerParStatut)
            .filter(this::filtrerParType)
            .filter(this::filtrerParDate)
            .filter(this::filtrerParMotCle)
            .collect(Collectors.toList());
        appliquerDonnees(filtrees);
        AudioUtil.prononcer("Filtres appliques.");
    }

    @FXML
    private void handleReinitialiserFiltres() {
        comboFiltreStatut.setValue("Tous");
        comboFiltreType.setValue("Tous");
        dateDu.setValue(null);
        dateAu.setValue(null);
        champMotCle.clear();
        chargerDonnees();
        AudioUtil.prononcer("Filtres reinitialises.");
    }

    @FXML
    private void handleMettreAJour() {
        DemandeAmenagement selection = tableauDemandes.getSelectionModel().getSelectedItem();
        if (selection == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez selectionner une demande.");
            return;
        }

        String nouveauStatut = comboNouveauStatut.getValue();
        if (nouveauStatut == null || nouveauStatut.isBlank()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez choisir un statut.");
            return;
        }

        if (SessionManager.getUtilisateur() == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Session administrateur introuvable.");
            return;
        }

        Statut statut = Statut.valueOf(nouveauStatut);
        String commentaire = champCommentaire != null ? champCommentaire.getText().trim() : "";
        boolean ok = demandeDAO.mettreAJourStatut(
            selection.getId(),
            statut,
            SessionManager.getUtilisateur().getId(),
            commentaire.isEmpty() ? null : commentaire
        );

        if (ok) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Demande mise a jour.");
            if (champCommentaire != null) champCommentaire.clear();
            chargerDonnees();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Impossible de mettre a jour la demande.");
        }
    }

    @FXML
    private void handleArchiverDemande() {
        DemandeAmenagement selection = tableauDemandes.getSelectionModel().getSelectedItem();
        if (selection == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez selectionner une demande a archiver.");
            return;
        }
        if (selection.getStatut() == Statut.EN_ATTENTE) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Seules les demandes acceptees ou refusees peuvent etre archivees.");
            return;
        }

        if (demandeDAO.archiver(selection.getId())) {
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Demande archivee.");
            if (champCommentaire != null) champCommentaire.clear();
            chargerDonnees();
        } else {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Impossible d'archiver la demande.");
        }
    }

    @FXML
    private void handleOuvrirDocumentDemande() {
        DemandeAmenagement selection = tableauDemandes.getSelectionModel().getSelectedItem();
        if (selection == null) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Veuillez selectionner une demande.");
            return;
        }
        ouvrirDocument(selection.getDocumentJoint());
    }

    private boolean filtrerParStatut(DemandeAmenagement demande) {
        String statut = comboFiltreStatut.getValue();
        return statut == null || "Tous".equals(statut) || demande.getStatut().name().equals(statut);
    }

    private boolean filtrerParType(DemandeAmenagement demande) {
        String type = comboFiltreType.getValue();
        return type == null || "Tous".equals(type) || demande.getTypeAmenagement().name().equals(type);
    }

    private boolean filtrerParDate(DemandeAmenagement demande) {
        if (demande.getDateSoumission() == null) return true;
        LocalDate date = demande.getDateSoumission().toLocalDate();
        LocalDate debut = dateDu.getValue();
        LocalDate fin = dateAu.getValue();
        return (debut == null || !date.isBefore(debut))
            && (fin == null || !date.isAfter(fin));
    }

    private boolean filtrerParMotCle(DemandeAmenagement demande) {
        String motCle = champMotCle.getText();
        if (motCle == null || motCle.isBlank()) return true;

        String recherche = motCle.toLowerCase(Locale.ROOT).trim();
        return nullToEmpty(demande.getNomEtudiant()).toLowerCase(Locale.ROOT).contains(recherche)
            || nullToEmpty(demande.getDescription()).toLowerCase(Locale.ROOT).contains(recherche)
            || demande.getTypeAmenagementLabel().toLowerCase(Locale.ROOT).contains(recherche)
            || demande.getStatutLabel().toLowerCase(Locale.ROOT).contains(recherche);
    }

    private void naviguerVers(String fxml, String titre) {
        Stage stage = NavigationUtil.getStage(tableauDemandes);
        NavigationUtil.naviguerVers(stage, fxml, titre);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void ouvrirDocument(String chemin) {
        if (chemin == null || chemin.isBlank()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Aucun fichier joint pour cet element.");
            return;
        }

        File fichier = new File(chemin);
        if (!fichier.exists()) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Le fichier joint est introuvable.");
            return;
        }

        try {
            Desktop.getDesktop().open(fichier);
            AudioUtil.prononcer("Ouverture du fichier joint.");
        } catch (IOException | RuntimeException e) {
            AudioUtil.jouerErreur();
            AudioUtil.prononcer("Impossible d'ouvrir le fichier joint.");
        }
    }

    private String nomFichier(String chemin) {
        if (chemin == null || chemin.isBlank()) return "-";
        return new File(chemin).getName();
    }
}
