package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.Reclamation;
import com.universite.model.Reclamation.Statut;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReclamationDAO {

    public ReclamationDAO() {
        garantirColonnesArchive();
        garantirColonneDocumentJoint();
    }

    public boolean creer(Reclamation r) {
        String sql = "INSERT INTO Reclamations (etudiant_id, sujet, description, document_joint) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, r.getEtudiantId());
            ps.setString(2, r.getSujet());
            ps.setString(3, r.getDescription());
            ps.setString(4, r.getDocumentJoint());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur création réclamation : " + e.getMessage());
        }
        return false;
    }

    public List<Reclamation> trouverParEtudiant(int etudiantId) {
        List<Reclamation> liste = new ArrayList<>();
        String sql = "SELECT r.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant "
                   + "FROM Reclamations r "
                   + "JOIN Utilisateurs u ON r.etudiant_id = u.id "
                   + "WHERE r.etudiant_id = ? AND r.archivee = 0 ORDER BY r.date_soumission DESC";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, etudiantId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) liste.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Erreur trouverParEtudiant réclamation : " + e.getMessage());
        }
        return liste;
    }

    public List<Reclamation> trouverToutes() {
        List<Reclamation> liste = new ArrayList<>();
        String sql = "SELECT r.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant "
                   + "FROM Reclamations r "
                   + "JOIN Utilisateurs u ON r.etudiant_id = u.id "
                   + "WHERE r.archivee = 0 "
                   + "ORDER BY r.date_soumission DESC";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) liste.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Erreur trouverToutes réclamations : " + e.getMessage());
        }
        return liste;
    }

    public boolean mettreAJourStatut(int id, Statut statut, int adminId, String reponse) {
        String sql = "UPDATE Reclamations SET statut = ?, admin_id = ?, reponse_admin = ?, "
                   + "date_resolution = CASE WHEN ? = 'RESOLUE' THEN NOW() ELSE NULL END "
                   + "WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ps.setInt(2, adminId);
            ps.setString(3, reponse);
            ps.setString(4, statut.name());
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur mettreAJourStatut réclamation : " + e.getMessage());
        }
        return false;
    }

    public boolean modifierParEtudiant(Reclamation r) {
        String sql = "UPDATE Reclamations SET sujet = ?, description = ?, document_joint = ? "
                   + "WHERE id = ? AND etudiant_id = ? AND statut = 'EN_ATTENTE'";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, r.getSujet());
            ps.setString(2, r.getDescription());
            ps.setString(3, r.getDocumentJoint());
            ps.setInt(4, r.getId());
            ps.setInt(5, r.getEtudiantId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur modification reclamation : " + e.getMessage());
        }
        return false;
    }

    public boolean archiver(int id) {
        String sql = "UPDATE Reclamations SET archivee = 1, date_archive = NOW() "
                   + "WHERE id = ? AND statut = 'RESOLUE'";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur archivage reclamation : " + e.getMessage());
        }
        return false;
    }

    public boolean supprimer(int id) {
        String sql = "DELETE FROM Reclamations WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur suppression réclamation : " + e.getMessage());
        }
        return false;
    }

    public int compterParStatut(Statut statut) {
        String sql = "SELECT COUNT(1) FROM Reclamations WHERE statut = ? AND archivee = 0";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Erreur compterParStatut réclamation : " + e.getMessage());
        }
        return 0;
    }

    private void garantirColonnesArchive() {
        try (Statement st = DatabaseConfig.getConnection().createStatement()) {
            try { st.executeUpdate("ALTER TABLE Reclamations ADD COLUMN archivee TINYINT(1) NOT NULL DEFAULT 0"); }
            catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE Reclamations ADD COLUMN date_archive DATETIME NULL"); }
            catch (SQLException ignored) {}
        } catch (SQLException e) {
            System.err.println("Erreur preparation archives reclamations : " + e.getMessage());
        }
    }

    private void garantirColonneDocumentJoint() {
        try (Statement st = DatabaseConfig.getConnection().createStatement()) {
            try { st.executeUpdate("ALTER TABLE Reclamations ADD COLUMN document_joint VARCHAR(500) NULL"); }
            catch (SQLException ignored) {}
        } catch (SQLException e) {
            System.err.println("Erreur preparation document reclamations : " + e.getMessage());
        }
    }

    private Reclamation mapRow(ResultSet rs) throws SQLException {
        Reclamation r = new Reclamation();
        r.setId(rs.getInt("id"));
        r.setEtudiantId(rs.getInt("etudiant_id"));
        try { r.setNomEtudiant(rs.getString("nom_etudiant")); } catch (SQLException ignored) {}
        r.setSujet(rs.getString("sujet"));
        r.setDescription(rs.getString("description"));
        try { r.setDocumentJoint(rs.getString("document_joint")); } catch (SQLException ignored) {}
        r.setStatut(Statut.valueOf(rs.getString("statut")));
        Timestamp ts1 = rs.getTimestamp("date_soumission");
        if (ts1 != null) r.setDateSoumission(ts1.toLocalDateTime());
        Timestamp ts2 = rs.getTimestamp("date_resolution");
        if (ts2 != null) r.setDateResolution(ts2.toLocalDateTime());
        int adminId = rs.getInt("admin_id");
        if (!rs.wasNull()) r.setAdminId(adminId);
        r.setReponseAdmin(rs.getString("reponse_admin"));
        return r;
    }
}
