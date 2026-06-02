package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.Notification;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public boolean creer(Notification n) {
        String sql = "INSERT INTO Notifications (utilisateur_id, message) VALUES (?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, n.getUtilisateurId());
            ps.setString(2, n.getMessage());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur création notification : " + e.getMessage());
        }
        return false;
    }

    public List<Notification> trouverNonLues(int utilisateurId) {
        List<Notification> liste = new ArrayList<>();
        String sql = "SELECT * FROM Notifications WHERE utilisateur_id = ? AND lue = 0 ORDER BY date_creation DESC";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, utilisateurId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Notification n = new Notification();
                n.setId(rs.getInt("id"));
                n.setUtilisateurId(rs.getInt("utilisateur_id"));
                n.setMessage(rs.getString("message"));
                n.setLue(rs.getBoolean("lue"));
                Timestamp ts = rs.getTimestamp("date_creation");
                if (ts != null) n.setDateCreation(ts.toLocalDateTime());
                liste.add(n);
            }
        } catch (SQLException e) {
            System.err.println("Erreur trouverNonLues : " + e.getMessage());
        }
        return liste;
    }

    public boolean marquerCommeLue(int notificationId) {
        String sql = "UPDATE Notifications SET lue = 1 WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur marquerCommeLue : " + e.getMessage());
        }
        return false;
    }

    public void marquerToutesCommeLues(int utilisateurId) {
        String sql = "UPDATE Notifications SET lue = 1 WHERE utilisateur_id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, utilisateurId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur marquerToutesCommeLues : " + e.getMessage());
        }
    }
}