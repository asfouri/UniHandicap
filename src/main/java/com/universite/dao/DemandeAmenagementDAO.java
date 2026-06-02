package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.DemandeAmenagement;
import com.universite.model.DemandeAmenagement.Statut;
import com.universite.model.DemandeAmenagement.TypeAmenagement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DemandeAmenagementDAO {

    public DemandeAmenagementDAO() {
        garantirColonnesArchive();
    }

    // ── Créer ─────────────────────────────────────────────────────

    public boolean creer(DemandeAmenagement d) {
        String sql = "INSERT INTO Demandes_Amenagement "
                   + "(etudiant_id, type_amenagement, description, document_joint) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, d.getEtudiantId());
            ps.setString(2, d.getTypeAmenagement().name());
            ps.setString(3, d.getDescription());
            ps.setString(4, d.getDocumentJoint());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur création demande : " + e.getMessage());
        }
        return false;
    }

    // ── Lire par étudiant ─────────────────────────────────────────

    public List<DemandeAmenagement> trouverParEtudiant(int etudiantId) {
        List<DemandeAmenagement> liste = new ArrayList<>();
        String sql = "SELECT d.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant "
                   + "FROM Demandes_Amenagement d "
                   + "JOIN Utilisateurs u ON d.etudiant_id = u.id "
                   + "WHERE d.etudiant_id = ? AND d.archivee = 0 ORDER BY d.date_soumission DESC";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, etudiantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) liste.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Erreur trouverParEtudiant : " + e.getMessage());
        }
        return liste;
    }

    // ── Lire toutes (Admin) ───────────────────────────────────────

    public List<DemandeAmenagement> trouverToutes() {
        List<DemandeAmenagement> liste = new ArrayList<>();
        String sql = "SELECT d.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant "
                   + "FROM Demandes_Amenagement d "
                   + "JOIN Utilisateurs u ON d.etudiant_id = u.id "
                   + "WHERE d.archivee = 0 "
                   + "ORDER BY d.date_soumission DESC";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) liste.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Erreur trouverToutes : " + e.getMessage());
        }
        return liste;
    }

    // ── Mettre à jour le statut (Admin) ──────────────────────────

    public boolean mettreAJourStatut(int id, Statut statut, int adminId, String commentaire) {
        String sql = "UPDATE Demandes_Amenagement "
                   + "SET statut = ?, admin_id = ?, commentaire_admin = ?, date_traitement = NOW() "
                   + "WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ps.setInt(2, adminId);
            ps.setString(3, commentaire);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur mettreAJourStatut : " + e.getMessage());
        }
        return false;
    }

    // ── Supprimer ─────────────────────────────────────────────────

    public boolean modifierParEtudiant(DemandeAmenagement d) {
        String sql = "UPDATE Demandes_Amenagement "
                   + "SET type_amenagement = ?, description = ?, document_joint = ? "
                   + "WHERE id = ? AND etudiant_id = ? AND statut = 'EN_ATTENTE'";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, d.getTypeAmenagement().name());
            ps.setString(2, d.getDescription());
            ps.setString(3, d.getDocumentJoint());
            ps.setInt(4, d.getId());
            ps.setInt(5, d.getEtudiantId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur modification demande : " + e.getMessage());
        }
        return false;
    }

    public boolean archiver(int id) {
        String sql = "UPDATE Demandes_Amenagement SET archivee = 1, date_archive = NOW() "
                   + "WHERE id = ? AND statut IN ('ACCEPTEE', 'REFUSEE')";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur archivage demande : " + e.getMessage());
        }
        return false;
    }

    public boolean supprimer(int id) {
        String sql = "DELETE FROM Demandes_Amenagement WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur suppression demande : " + e.getMessage());
        }
        return false;
    }

    // ── Statistiques ──────────────────────────────────────────────

    public int compterParStatut(Statut statut) {
        String sql = "SELECT COUNT(1) FROM Demandes_Amenagement WHERE statut = ? AND archivee = 0";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Erreur compterParStatut : " + e.getMessage());
        }
        return 0;
    }

    private void garantirColonnesArchive() {
        try (Statement st = DatabaseConfig.getConnection().createStatement()) {
            try { st.executeUpdate("ALTER TABLE Demandes_Amenagement ADD COLUMN archivee TINYINT(1) NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE Demandes_Amenagement ADD COLUMN date_archive DATETIME NULL"); }
            catch (SQLException ignored) {}
        } catch (SQLException e) {
            System.err.println("Erreur preparation archives demandes : " + e.getMessage());
        }
    }

    // ── Mapping ───────────────────────────────────────────────────

    private DemandeAmenagement mapRow(ResultSet rs) throws SQLException {
        DemandeAmenagement d = new DemandeAmenagement();
        d.setId(rs.getInt("id"));
        d.setEtudiantId(rs.getInt("etudiant_id"));
        try { d.setNomEtudiant(rs.getString("nom_etudiant")); } catch (SQLException ignored) {}
        d.setTypeAmenagement(TypeAmenagement.valueOf(rs.getString("type_amenagement")));
        d.setDescription(rs.getString("description"));
        d.setStatut(Statut.valueOf(rs.getString("statut")));
        d.setDocumentJoint(rs.getString("document_joint"));
        Timestamp ts1 = rs.getTimestamp("date_soumission");
        if (ts1 != null) d.setDateSoumission(ts1.toLocalDateTime());
        Timestamp ts2 = rs.getTimestamp("date_traitement");
        if (ts2 != null) d.setDateTraitement(ts2.toLocalDateTime());
        int adminId = rs.getInt("admin_id");
        if (!rs.wasNull()) d.setAdminId(adminId);
        d.setCommentaireAdmin(rs.getString("commentaire_admin"));
        return d;
    }
}
