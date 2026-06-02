package com.universite.util;

import com.universite.model.DemandeAmenagement;
import com.universite.model.Utilisateur;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.tagging.StandardRoles;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDFExportUtil – Exporte les demandes d'aménagement en PDF accessible.
 *
 * Dépendance requise dans pom.xml / build.gradle :
 *   iText 7 Core  → com.itextpdf:itext7-core:7.2.5
 *   iText 7 Accessibility → com.itextpdf:pdfua:7.2.5
 *
 * Le PDF généré est balisé (tagged) pour la compatibilité avec les lecteurs
 * d'écran NVDA et JAWS.
 */
public class PDFExportUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PDFExportUtil() {}

    /**
     * Ouvre un sélecteur de fichier puis génère le PDF accessible.
     *
     * @param utilisateur L'étudiant concerné
     * @param demandes    La liste de ses demandes
     * @param parentWindow La fenêtre parente pour le FileChooser
     */
    public static void exporterDemandes(Utilisateur utilisateur,
                                        List<DemandeAmenagement> demandes,
                                        Window parentWindow) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Enregistrer le rapport PDF");
        fc.setInitialFileName("demandes_amenagement_" + utilisateur.getId() + ".pdf");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers PDF", "*.pdf"));
        File fichier = fc.showSaveDialog(parentWindow);

        if (fichier == null) return;

        try {
            genererPDF(utilisateur, demandes, fichier.getAbsolutePath());
            AudioUtil.jouerSucces();
            AudioUtil.prononcer("Votre rapport PDF a été exporté avec succès.");
            afficherAlerte("PDF exporté avec succès :\n" + fichier.getAbsolutePath());
        } catch (Exception e) {
            AudioUtil.jouerErreur();
            System.err.println("Erreur export PDF : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Génère un PDF balisé (tagged PDF) compatible NVDA/JAWS.
     * Implémentation utilisant iText 7 avec structure de balises d'accessibilité.
     */
    private static void genererPDF(Utilisateur utilisateur,
                                  List<DemandeAmenagement> demandes,
                                  String cheminFichier) throws Exception {
        PdfWriter writer = new PdfWriter(cheminFichier, new WriterProperties().addUAXmpMetadata());
        PdfDocument pdfDoc = new PdfDocument(writer);
        pdfDoc.setTagged();

        // Métadonnées d'accessibilité
        pdfDoc.getCatalog().setLang(new PdfString("fr-FR"));
        PdfDictionary prefs = new PdfDictionary();
        prefs.put(PdfName.DisplayDocTitle, PdfBoolean.TRUE);
        pdfDoc.getCatalog().put(PdfName.ViewerPreferences, prefs);
        pdfDoc.getDocumentInfo().setTitle("Rapport – Demandes d'Aménagement");

        try (Document document = new Document(pdfDoc, PageSize.A4)) {
            document.setMargins(40, 40, 40, 40);

            PdfFont fontTitre = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontTexte = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            Paragraph titre = new Paragraph("Rapport – Demandes d'Aménagement")
                .setFont(fontTitre)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER);
            titre.getAccessibilityProperties().setRole(StandardRoles.H1);
            document.add(titre);

            Paragraph infoEtudiant = new Paragraph()
                .setFont(fontTexte)
                .setFontSize(11)
                .add("Étudiant : " + utilisateur.getNomComplet() + "\n")
                .add("Email : " + utilisateur.getEmail() + "\n")
                .add("Date d'export : " + LocalDateTime.now().format(FMT));
            infoEtudiant.getAccessibilityProperties().setRole(StandardRoles.P);
            document.add(infoEtudiant);

            Paragraph section = new Paragraph("Liste des demandes")
                .setFont(fontTitre)
                .setFontSize(13)
                .setMarginTop(15);
            section.getAccessibilityProperties().setRole(StandardRoles.H2);
            document.add(section);

            Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 4, 3}))
                .useAllAvailableWidth();
            table.getAccessibilityProperties().setRole(StandardRoles.TABLE);

            table.addHeaderCell(creerCelluleEntete("Type", fontTitre));
            table.addHeaderCell(creerCelluleEntete("Statut", fontTitre));
            table.addHeaderCell(creerCelluleEntete("Date", fontTitre));
            table.addHeaderCell(creerCelluleEntete("Description", fontTitre));
            table.addHeaderCell(creerCelluleEntete("Commentaire admin", fontTitre));

            for (DemandeAmenagement d : demandes) {
                table.addCell(creerCelluleTexte(d.getTypeAmenagementLabel(), fontTexte));
                table.addCell(creerCelluleTexte(d.getStatutLabel(), fontTexte));
                String date = d.getDateSoumission() != null ? d.getDateSoumission().format(FMT) : "";
                table.addCell(creerCelluleTexte(date, fontTexte));
                table.addCell(creerCelluleTexte(d.getDescription(), fontTexte));
                String comm = d.getCommentaireAdmin() != null ? d.getCommentaireAdmin() : "–";
                table.addCell(creerCelluleTexte(comm, fontTexte));
            }

            document.add(table);
        }
    }

    private static Cell creerCelluleEntete(String texte, PdfFont fontTitre) {
        Cell cell = new Cell().add(new Paragraph(texte).setFont(fontTitre));
        cell.getAccessibilityProperties().setRole(StandardRoles.TH);
        return cell;
    }

    private static Cell creerCelluleTexte(String texte, PdfFont fontTexte) {
        String safe = texte != null ? texte : "";
        Cell cell = new Cell().add(new Paragraph(safe).setFont(fontTexte).setFontSize(10));
        cell.getAccessibilityProperties().setRole(StandardRoles.TD);
        return cell;
    }

    private static void afficherAlerte(String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Export PDF");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
