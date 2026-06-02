package com.universite.controller;

import com.universite.dao.DemandeAmenagementDAO;
import com.universite.dao.ReclamationDAO;
import com.universite.dao.NotificationDAO;
import com.universite.model.DemandeAmenagement;
import com.universite.model.DemandeAmenagement.Statut;
import com.universite.model.Notification;
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
import javafx.stage.Stage;

import java.io.File;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * EtudiantDashboardController – Tableau de bord étudiant.
 *
 * v5 GAP FIX: After TTS summary, automatically starts listening for a
 * spoken navigation command so motor-impaired users never need to click.
 *
 * Recognized commands (FR):
 *   "demandes"  / "nouvelle demande"  → FormulaireDemande
 *   "réclamation" / "réclamations"    → FormulaireReclamation
 *   "notifications"                   → open notification panel
 *   "déconnexion" / "déconnexion"     → logout
 *   "aide" / "menu"                   → repeat the menu options
 */
public class EtudiantDashboardController {

    @FXML private Label labelBienvenue;
    @FXML private Label labelNomEtudiant;
    @FXML private Label labelMesAttentes;
    @FXML private Label labelMesAcceptees;
    @FXML private Label labelMesRefusees;
    @FXML private Label labelMesReclamations;

    @FXML private javafx.scene.layout.VBox panneauNotifications;
    @FXML private ListView<String>          listeNotifications;

    @FXML private TableView<DemandeAmenagement>          tableauDerniersDemandes;
    @FXML private TableColumn<DemandeAmenagement, String> colType;
    @FXML private TableColumn<DemandeAmenagement, String> colStatut;
    @FXML private TableColumn<DemandeAmenagement, String> colDate;
    @FXML private TableColumn<DemandeAmenagement, String> colDesc;

    private final DemandeAmenagementDAO  demandeDAO     = new DemandeAmenagementDAO();
    private final ReclamationDAO         reclamationDAO = new ReclamationDAO();
    private final NotificationDAO        notifDAO       = new NotificationDAO();
    private final ObservableList<DemandeAmenagement> listeDemandes =
        FXCollections.observableArrayList();

    // GAP FIX: voice command infrastructure
    private final MicRecorder micRecorder       = new MicRecorder();
    private volatile boolean  ecoute            = false;   // true while mic is open
    private volatile boolean  dashboardActif    = true;    // false when navigating away

    // ── Initialisation ────────────────────────────────────────────

    @FXML
    public void initialize() {
        String nomComplet = SessionManager.getUtilisateur().getNomComplet();
        labelBienvenue.setText("Bonjour, " + nomComplet + " !");
        labelNomEtudiant.setText(nomComplet);

        colType.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                d.getValue().getTypeAmenagementLabel()));
        colStatut.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                d.getValue().getStatutLabel()));
        colDate.setCellValueFactory(d -> {
            var date = d.getValue().getDateSoumission();
            return new javafx.beans.property.SimpleStringProperty(
                date != null ? date.toLocalDate().toString() : "");
        });
        colDesc.setCellValueFactory(d ->
            new javafx.beans.property.SimpleStringProperty(
                d.getValue().getDescription()));

        tableauDerniersDemandes.setRowFactory(tv -> new TableRow<>() {
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

        tableauDerniersDemandes.setItems(listeDemandes);
        chargerStatistiques();
        chargerDerniersDemandes();
        chargerNotifications();

        // TTS summary first, then auto-start voice command listener
        // Background thread: TTS summary (blocking), then mic starts
        if (SessionManager.isCommandeVocaleActivee()) {
            new Thread(() -> {
                prononcerResumeDashboard(nomComplet);
                demarrerEcouteCommandes();
            }, "Dashboard-VoiceInit").start();
        }
    }

    // ── Résumé vocal ──────────────────────────────────────────────

    private void prononcerResumeDashboard(String nom) {
        String attentes     = labelMesAttentes     != null ? labelMesAttentes.getText()     : "0";
        String acceptees    = labelMesAcceptees    != null ? labelMesAcceptees.getText()    : "0";
        String refusees     = labelMesRefusees     != null ? labelMesRefusees.getText()     : "0";
        String reclamations = labelMesReclamations != null ? labelMesReclamations.getText() : "0";

        String resume = String.format(
            "Bonjour %s. Tableau de bord. " +
            "%s demande%s en attente, %s acceptée%s, %s refusée%s, %s réclamation%s. " +
            "Commandes vocales disponibles : dites Demandes, Réclamation, Notifications, ou Déconnexion.",
            nom,
            attentes,     parseInt(attentes)     != 1 ? "s" : "",
            acceptees,    parseInt(acceptees)    != 1 ? "s" : "",
            refusees,     parseInt(refusees)     != 1 ? "s" : "",
            reclamations, parseInt(reclamations) != 1 ? "s" : ""
        );
        AudioUtil.prononcerEtAttendre(resume);
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    // ── GAP FIX: Voice command listener ───────────────────────────

    /**
     * Starts a continuous voice command loop.
     * Records 4 seconds of audio, transcribes it, acts on the keyword,
     * then loops again unless the user has navigated away.
     */
    private void demarrerEcouteCommandes() {
        if (!SessionManager.isCommandeVocaleActivee()) return;
        if (ecoute || !dashboardActif) return;
        ecoute = true;

        new Thread(() -> {
            while (dashboardActif) {
                try {
                    // Brief silence so TTS doesn't get recorded
                    Thread.sleep(800);
                    if (!dashboardActif) break;

                    File audioFile = micRecorder.start();
                    Thread.sleep(4_000);          // listen for 4 s
                    micRecorder.stop();

                    if (!dashboardActif) break;

                    String texte = OpenAIWhisperUtil.transcrire(audioFile);
                    if (texte == null || texte.isBlank()) continue;

                    String cmd = texte.toLowerCase().trim();
                    String cmdNormalise = normaliserCommandeVocale(cmd);
                    System.out.println("[DashboardVoice] Commande : \"" + cmd + "\"");

                    if (estCommandeDemandes(cmdNormalise)) {
                        dashboardActif = false;
                        Platform.runLater(() -> handleVoirDemandes());
                        break;
                    } else if (estCommandeReclamations(cmdNormalise)) {
                        dashboardActif = false;
                        Platform.runLater(() -> handleVoirReclamations());
                        break;
                    } else if (estCommandeNotifications(cmdNormalise)) {
                        Platform.runLater(() -> handleVoirNotifications());
                        // continue listening
                    } else if (estCommandeDeconnexion(cmdNormalise)) {
                        dashboardActif = false;
                        Platform.runLater(() -> handleDeconnexion());
                        break;
                    } else if (estCommandeAide(cmdNormalise)) {
                        AudioUtil.prononcer(
                            "Commandes disponibles : Demandes, Réclamation, " +
                            "Notifications, Déconnexion.");
                    }
                    // unknown → loop and listen again

                } catch (Exception ex) {
                    System.err.println("[DashboardVoice] Erreur : " + ex.getMessage());
                    try { Thread.sleep(2_000); } catch (InterruptedException ignored) {}
                }
            }
            ecoute = false;
        }, "DashboardVoice-Thread").start();
    }

    private String normaliserCommandeVocale(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    private boolean estCommandeDemandes(String cmd) {
        return cmd.contains("demand") ||
            cmd.contains("dementhe") ||
            cmd.contains("demonde") ||
            cmd.contains("demonte") ||
            scoreSimilarite(cmd, "demandes") >= 70 ||
            scoreSimilarite(cmd, "demande") >= 70;
    }

    private boolean estCommandeReclamations(String cmd) {
        return cmd.contains("reclam") ||
            cmd.contains("roclam") ||
            cmd.contains("reclame") ||
            cmd.contains("reclamat") ||
            scoreSimilarite(cmd, "reclamation") >= 70 ||
            scoreSimilarite(cmd, "reclamations") >= 70;
    }

    private boolean estCommandeNotifications(String cmd) {
        return cmd.contains("notif") ||
            cmd.contains("notification") ||
            cmd.contains("notific") ||
            cmd.contains("notife") ||
            cmd.contains("notifs") ||
            scoreSimilarite(cmd, "notification") >= 70 ||
            scoreSimilarite(cmd, "notifications") >= 70;
    }

    private boolean estCommandeDeconnexion(String cmd) {
        return cmd.contains("deconnect") ||
            cmd.contains("deconnexion") ||
            cmd.contains("deconnection") ||
            cmd.contains("deconnecter") ||
            cmd.contains("quitter") ||
            cmd.contains("sortir") ||
            scoreSimilarite(cmd, "deconnexion") >= 72 ||
            scoreSimilarite(cmd, "deconnecter") >= 72;
    }

    private boolean estCommandeAide(String cmd) {
        return cmd.contains("aide") ||
            cmd.contains("menu") ||
            cmd.contains("option") ||
            scoreSimilarite(cmd, "aide") >= 75 ||
            scoreSimilarite(cmd, "menu") >= 75 ||
            scoreSimilarite(cmd, "options") >= 75;
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

    // ── Navigation ────────────────────────────────────────────────

    @FXML private void handleNouvelleDemande() {
        dashboardActif = false;
        AudioUtil.prononcer("Ouverture du formulaire de demande d'aménagement.");
        naviguerVers("/fxml/student/FormulaireDemande.fxml", "Mes Demandes");
    }

    @FXML private void handleVoirDemandes() {
        dashboardActif = false;
        AudioUtil.prononcer("Affichage de vos demandes d'aménagement.");
        naviguerVers("/fxml/student/FormulaireDemande.fxml", "Mes Demandes");
    }

    @FXML private void handleNouvelleReclamation() {
        dashboardActif = false;
        AudioUtil.prononcer("Ouverture du formulaire de réclamation.");
        naviguerVers("/fxml/student/FormulaireReclamation.fxml", "Mes Réclamations");
    }

    @FXML private void handleVoirReclamations() {
        dashboardActif = false;
        AudioUtil.prononcer("Affichage de vos réclamations.");
        naviguerVers("/fxml/student/FormulaireReclamation.fxml", "Mes Réclamations");
    }

    @FXML private void handleVoirNotifications() {
        panneauNotifications.setVisible(true);
        panneauNotifications.setManaged(true);
        ObservableList<String> items = listeNotifications.getItems();
        if (items == null || items.isEmpty()) {
            AudioUtil.prononcer("Vous n'avez aucune nouvelle notification.");
        } else {
            AudioUtil.prononcer("Vous avez " + items.size() + " notification" +
                (items.size() > 1 ? "s" : "") + ". " + String.join(". ", items));
        }
    }

    @FXML private void handleExporterPDF() {
        AudioUtil.prononcer("Export PDF de vos demandes en cours.");
        int etudiantId = SessionManager.getUtilisateur().getId();
        List<DemandeAmenagement> demandes = demandeDAO.trouverParEtudiant(etudiantId);
        com.universite.util.PDFExportUtil.exporterDemandes(
            SessionManager.getUtilisateur(), demandes,
            labelBienvenue.getScene().getWindow());
    }

    @FXML private void handleDeconnexion() {
        dashboardActif = false;
        AudioUtil.prononcer("Déconnexion en cours. Au revoir.");
        SessionManager.clear();
        naviguerVers("/fxml/auth/Login.fxml", "Connexion");
    }

    // ── Chargement données ────────────────────────────────────────

    private void chargerStatistiques() {
        int id = SessionManager.getUtilisateur().getId();
        List<DemandeAmenagement> toutes = demandeDAO.trouverParEtudiant(id);
        long attente      = toutes.stream().filter(d -> d.getStatut() == Statut.EN_ATTENTE).count();
        long acceptees    = toutes.stream().filter(d -> d.getStatut() == Statut.ACCEPTEE).count();
        long refusees     = toutes.stream().filter(d -> d.getStatut() == Statut.REFUSEE).count();
        long reclamations = reclamationDAO.trouverParEtudiant(id).size();

        labelMesAttentes.setText(String.valueOf(attente));
        labelMesAcceptees.setText(String.valueOf(acceptees));
        labelMesRefusees.setText(String.valueOf(refusees));
        labelMesReclamations.setText(String.valueOf(reclamations));
    }

    private void chargerDerniersDemandes() {
        listeDemandes.clear();
        List<DemandeAmenagement> demandes = demandeDAO.trouverParEtudiant(
            SessionManager.getUtilisateur().getId());
        listeDemandes.addAll(demandes.stream().limit(10).collect(Collectors.toList()));
    }

    private void chargerNotifications() {
        int userId = SessionManager.getUtilisateur().getId();
        List<Notification> notifs = notifDAO.trouverNonLues(userId);
        if (!notifs.isEmpty()) {
            List<String> messages = notifs.stream()
                .map(Notification::getMessage)
                .collect(Collectors.toList());
            listeNotifications.setItems(FXCollections.observableArrayList(messages));
            panneauNotifications.setVisible(true);
            panneauNotifications.setManaged(true);
            AudioUtil.jouerNotification();
            AudioUtil.prononcer(
                "Vous avez " + notifs.size() + " nouvelle" + (notifs.size() > 1 ? "s" : "") +
                " notification" + (notifs.size() > 1 ? "s" : "") + ". " +
                String.join(". ", messages)
            );
            notifDAO.marquerToutesCommeLues(userId);
        }
    }

    private void naviguerVers(String fxml, String titre) {
        Stage stage = (Stage) labelBienvenue.getScene().getWindow();
        NavigationUtil.naviguerVers(stage, fxml, titre);
    }
}
