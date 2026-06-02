package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.DemandeAmenagement;
import com.universite.model.Reclamation;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ArchivesDAO {

    private final DemandeAmenagementDAO demandeDAO = new DemandeAmenagementDAO();
    private final ReclamationDAO reclamationDAO = new ReclamationDAO();

    public List<DemandeAmenagement> rechercherDemandes(String statut,
                                                      String typeAmenagement,
                                                      LocalDate dateDu,
                                                      LocalDate dateAu,
                                                      String motCle) {
        List<DemandeAmenagement> liste = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT d.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant ")
           .append("FROM Demandes_Amenagement d ")
           .append("JOIN Utilisateurs u ON d.etudiant_id = u.id ")
           .append("WHERE d.archivee = 1 ");

        List<Object> params = new ArrayList<>();

        if (statut != null && !statut.isBlank() && !"Tous".equals(statut)) {
            sql.append("AND d.statut = ? ");
            params.add(statut);
        }

        if (typeAmenagement != null && !typeAmenagement.isBlank() && !"Tous".equals(typeAmenagement)) {
            sql.append("AND d.type_amenagement = ? ");
            params.add(typeAmenagement);
        }

        if (dateDu != null) {
            sql.append("AND d.date_soumission >= ? ");
            params.add(Timestamp.valueOf(dateDu.atStartOfDay()));
        }

        if (dateAu != null) {
            sql.append("AND d.date_soumission < ? ");
            params.add(Timestamp.valueOf(dateAu.plusDays(1).atStartOfDay()));
        }

        if (motCle != null && !motCle.isBlank()) {
            sql.append("AND (d.description LIKE ? OR d.commentaire_admin LIKE ?) ");
            String like = "%" + motCle.trim() + "%";
            params.add(like);
            params.add(like);
        }

        sql.append("ORDER BY d.date_soumission DESC");

        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                } else {
                    ps.setObject(i + 1, p);
                }
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                liste.add(mapDemande(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur recherche demandes (archives) : " + e.getMessage());
        }

        return liste;
    }

    public List<Reclamation> rechercherReclamations(String statut,
                                                   LocalDate dateDu,
                                                   LocalDate dateAu,
                                                   String motCle) {
        List<Reclamation> liste = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.*, CONCAT(u.nom, ' ', u.prenom) AS nom_etudiant ")
           .append("FROM Reclamations r ")
           .append("JOIN Utilisateurs u ON r.etudiant_id = u.id ")
           .append("WHERE r.archivee = 1 ");

        List<Object> params = new ArrayList<>();

        if (statut != null && !statut.isBlank() && !"Tous".equals(statut)) {
            sql.append("AND r.statut = ? ");
            params.add(statut);
        }

        if (dateDu != null) {
            sql.append("AND r.date_soumission >= ? ");
            params.add(Timestamp.valueOf(dateDu.atStartOfDay()));
        }

        if (dateAu != null) {
            sql.append("AND r.date_soumission < ? ");
            params.add(Timestamp.valueOf(dateAu.plusDays(1).atStartOfDay()));
        }

        if (motCle != null && !motCle.isBlank()) {
            sql.append("AND (r.sujet LIKE ? OR r.description LIKE ? OR r.reponse_admin LIKE ?) ");
            String like = "%" + motCle.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append("ORDER BY r.date_soumission DESC");

        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                } else {
                    ps.setObject(i + 1, p);
                }
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                liste.add(mapReclamation(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erreur recherche réclamations (archives) : " + e.getMessage());
        }

        return liste;
    }

    private DemandeAmenagement mapDemande(ResultSet rs) throws SQLException {
        DemandeAmenagement d = new DemandeAmenagement();
        d.setId(rs.getInt("id"));
        d.setEtudiantId(rs.getInt("etudiant_id"));
        d.setNomEtudiant(rs.getString("nom_etudiant"));
        d.setTypeAmenagement(DemandeAmenagement.TypeAmenagement.valueOf(rs.getString("type_amenagement")));
        d.setDescription(rs.getString("description"));
        d.setStatut(DemandeAmenagement.Statut.valueOf(rs.getString("statut")));
        d.setDocumentJoint(rs.getString("document_joint"));

        Timestamp tsSoum = rs.getTimestamp("date_soumission");
        if (tsSoum != null) d.setDateSoumission(tsSoum.toLocalDateTime());

        Timestamp tsTrait = rs.getTimestamp("date_traitement");
        if (tsTrait != null) d.setDateTraitement(tsTrait.toLocalDateTime());

        int adminId = rs.getInt("admin_id");
        if (!rs.wasNull()) d.setAdminId(adminId);

        d.setCommentaireAdmin(rs.getString("commentaire_admin"));
        return d;
    }

    private Reclamation mapReclamation(ResultSet rs) throws SQLException {
        Reclamation r = new Reclamation();
        r.setId(rs.getInt("id"));
        r.setEtudiantId(rs.getInt("etudiant_id"));
        r.setNomEtudiant(rs.getString("nom_etudiant"));
        r.setSujet(rs.getString("sujet"));
        r.setDescription(rs.getString("description"));
        try { r.setDocumentJoint(rs.getString("document_joint")); } catch (SQLException ignored) {}
        r.setStatut(Reclamation.Statut.valueOf(rs.getString("statut")));

        Timestamp tsSoum = rs.getTimestamp("date_soumission");
        if (tsSoum != null) r.setDateSoumission(tsSoum.toLocalDateTime());

        Timestamp tsRes = rs.getTimestamp("date_resolution");
        if (tsRes != null) r.setDateResolution(tsRes.toLocalDateTime());

        int adminId = rs.getInt("admin_id");
        if (!rs.wasNull()) r.setAdminId(adminId);

        r.setReponseAdmin(rs.getString("reponse_admin"));
        return r;
    }
}
