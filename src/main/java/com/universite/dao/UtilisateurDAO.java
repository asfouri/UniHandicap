package com.universite.dao;

import com.universite.config.DatabaseConfig;
import com.universite.model.Utilisateur;
import com.universite.util.HashUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UtilisateurDAO {

    // ── Authentification ──────────────────────────────────────────

    public Utilisateur authentifier(String email, String motDePasse) {
        String sql = "SELECT * FROM Utilisateurs WHERE email = ? AND mot_de_passe = ? AND actif = 1";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, HashUtil.sha256(motDePasse));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Erreur authentification : " + e.getMessage());
        }
        return null;
    }

    // ── Création ──────────────────────────────────────────────────

    public boolean creer(Utilisateur u) {
        String sql = "INSERT INTO Utilisateurs (nom, prenom, email, mot_de_passe, role) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, u.getNom());
            ps.setString(2, u.getPrenom());
            ps.setString(3, u.getEmail());
            ps.setString(4, HashUtil.sha256(u.getMotDePasse()));
            ps.setString(5, u.getRole());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erreur création utilisateur : " + e.getMessage());
        }
        return false;
    }

    // ── Lecture ───────────────────────────────────────────────────

    public Utilisateur trouverParId(int id) {
        String sql = "SELECT * FROM Utilisateurs WHERE id = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Erreur trouverParId : " + e.getMessage());
        }
        return null;
    }

    public List<Utilisateur> trouverTous() {
        List<Utilisateur> liste = new ArrayList<>();
        String sql = "SELECT * FROM Utilisateurs WHERE actif = 1 ORDER BY nom";
        try (Statement st = DatabaseConfig.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) liste.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Erreur trouverTous : " + e.getMessage());
        }
        return liste;
    }

    public boolean emailExiste(String email) {
        String sql = "SELECT COUNT(1) FROM Utilisateurs WHERE email = ?";
        try (PreparedStatement ps = DatabaseConfig.getConnection().prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            System.err.println("Erreur emailExiste : " + e.getMessage());
        }
        return false;
    }

    // ── Mapping ResultSet → Objet ─────────────────────────────────

    private Utilisateur mapRow(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();
        u.setId(rs.getInt("id"));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email"));
        u.setRole(rs.getString("role"));
        u.setActif(rs.getBoolean("actif"));
        Timestamp ts = rs.getTimestamp("date_creation");
        if (ts != null) u.setDateCreation(ts.toLocalDateTime());
        return u;
    }
}
