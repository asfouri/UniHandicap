package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.JournalAudit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JournalAuditDAO {

    public boolean enregistrer(JournalAudit j) {
        String sql = "INSERT INTO Journal_Audit "
                   + "(admin_id, action, entite, entite_id, ancienne_valeur, nouvelle_valeur) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, j.getAdminId());
            ps.setString(2, j.getAction());
            ps.setString(3, j.getEntite());
            ps.setInt(4, j.getEntiteId());
            ps.setString(5, j.getAncienneValeur());
            ps.setString(6, j.getNouvelleValeur());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur enregistrement audit : " + e.getMessage());
        }
        return false;
    }

    public List<JournalAudit> trouverTous() {
        List<JournalAudit> liste = new ArrayList<>();
        String sql = "SELECT j.*, CONCAT(u.nom, ' ', u.prenom) AS nom_admin "
                   + "FROM Journal_Audit j "
                   + "JOIN Utilisateurs u ON j.admin_id = u.id "
                   + "ORDER BY j.date_action DESC";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                JournalAudit j = new JournalAudit();
                j.setId(rs.getInt("id"));
                j.setAdminId(rs.getInt("admin_id"));
                j.setNomAdmin(rs.getString("nom_admin"));
                j.setAction(rs.getString("action"));
                j.setEntite(rs.getString("entite"));
                j.setEntiteId(rs.getInt("entite_id"));
                j.setAncienneValeur(rs.getString("ancienne_valeur"));
                j.setNouvelleValeur(rs.getString("nouvelle_valeur"));
                Timestamp ts = rs.getTimestamp("date_action");
                if (ts != null) j.setDateAction(ts.toLocalDateTime());
                liste.add(j);
            }
        } catch (SQLException e) {
            System.err.println("Erreur trouverTous audit : " + e.getMessage());
        }
        return liste;
    }
}