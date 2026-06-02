package com.universite.model;

import com.universite.util.TexteUtil;

import java.time.LocalDateTime;

public class Reclamation {

    public enum Statut { EN_ATTENTE, EN_COURS, RESOLUE }

    private int id;
    private int etudiantId;
    private String nomEtudiant;
    private String sujet;
    private String description;
    private String documentJoint;
    private Statut statut;
    private LocalDateTime dateSoumission;
    private LocalDateTime dateResolution;
    private Integer adminId;
    private String reponseAdmin;

    public Reclamation() {
        this.statut = Statut.EN_ATTENTE;
        this.dateSoumission = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEtudiantId() { return etudiantId; }
    public void setEtudiantId(int etudiantId) { this.etudiantId = etudiantId; }

    public String getNomEtudiant() { return nomEtudiant; }
    public void setNomEtudiant(String nomEtudiant) { this.nomEtudiant = nomEtudiant; }

    public String getSujet() { return sujet; }
    public void setSujet(String sujet) { this.sujet = TexteUtil.reparerEncodage(sujet); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = TexteUtil.reparerEncodage(description); }

    public String getContenu() { return description; }
    public void setContenu(String contenu) { setDescription(contenu); }

    public LocalDateTime getDate() { return dateSoumission; }
    public void setDate(LocalDateTime date) { setDateSoumission(date); }

    public String getDocumentJoint() { return documentJoint; }
    public void setDocumentJoint(String documentJoint) { this.documentJoint = documentJoint; }

    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }

    public String getStatutLabel() {
        if (statut == null) return "";
        switch (statut) {
            case EN_ATTENTE: return "En attente";
            case EN_COURS:   return "En cours";
            case RESOLUE:    return "Résolue";
            default:         return statut.name();
        }
    }

    public LocalDateTime getDateSoumission() { return dateSoumission; }
    public void setDateSoumission(LocalDateTime dateSoumission) { this.dateSoumission = dateSoumission; }

    public LocalDateTime getDateResolution() { return dateResolution; }
    public void setDateResolution(LocalDateTime dateResolution) { this.dateResolution = dateResolution; }

    public Integer getAdminId() { return adminId; }
    public void setAdminId(Integer adminId) { this.adminId = adminId; }

    public String getReponseAdmin() { return reponseAdmin; }
    public void setReponseAdmin(String reponseAdmin) { this.reponseAdmin = TexteUtil.reparerEncodage(reponseAdmin); }

    public void soumettre() {
        this.statut = Statut.EN_ATTENTE;
        if (this.dateSoumission == null) this.dateSoumission = LocalDateTime.now();
    }

    public String consulter() {
        return sujet + " - " + getStatutLabel();
    }

    public void mettreAJourStatut(Statut statut) {
        this.statut = statut;
        if (statut == Statut.RESOLUE) this.dateResolution = LocalDateTime.now();
    }
}
