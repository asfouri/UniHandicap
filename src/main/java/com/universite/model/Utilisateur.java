package com.universite.model;

import java.time.LocalDateTime;

public class Utilisateur {

    private int id;
    private String nom;
    private String prenom;
    private String email;
    private String motDePasse;
    private String role;           // "ETUDIANT" ou "ADMIN"
    private LocalDateTime dateCreation;
    private boolean actif;

    public Utilisateur() {}

    public Utilisateur(int id, String nom, String prenom, String email, String role) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
        this.role = role;
    }

    // ── Getters & Setters ─────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getNomComplet() { return prenom + " " + nom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isEtudiant() { return "ETUDIANT".equals(role); }

    public void creerCompte() {
        this.actif = true;
        if (this.dateCreation == null) this.dateCreation = LocalDateTime.now();
    }

    public void modifierCompte(String nom, String prenom, String email) {
        this.nom = nom;
        this.prenom = prenom;
        this.email = email;
    }

    public void seConnecter() {
        this.actif = true;
    }

    public void seDeconnecter() {
        this.actif = false;
    }

    public String consulter() {
        return toString();
    }

    @Override
    public String toString() {
        return "Utilisateur{id=" + id + ", nom='" + getNomComplet() + "', role='" + role + "'}";
    }
}
