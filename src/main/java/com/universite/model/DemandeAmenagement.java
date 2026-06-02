package com.universite.model;

import com.universite.util.TexteUtil;

import java.time.LocalDateTime;

public class DemandeAmenagement {

    public enum Statut { EN_ATTENTE, ACCEPTEE, REFUSEE }
    public enum TypeAmenagement { EXAMEN, ACCESSIBILITE, ASSISTANCE, AUTRE }

    private int id;
    private int etudiantId;
    private String nomEtudiant;          // jointure pour affichage
    private TypeAmenagement typeAmenagement;
    private String description;
    private Statut statut;
    private String documentJoint;        // chemin fichier
    private LocalDateTime dateSoumission;
    private LocalDateTime dateTraitement;
    private Integer adminId;
    private String commentaireAdmin;

    public DemandeAmenagement() {
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

    public TypeAmenagement getTypeAmenagement() { return typeAmenagement; }
    public void setTypeAmenagement(TypeAmenagement typeAmenagement) { this.typeAmenagement = typeAmenagement; }

    public String getTypeAmenagementLabel() {
        if (typeAmenagement == null) return "";
        switch (typeAmenagement) {
            case EXAMEN:        return "Aménagement d'examen";
            case ACCESSIBILITE: return "Accessibilité";
            case ASSISTANCE:    return "Service d'assistance";
            default:            return "Autre";
        }
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = TexteUtil.reparerEncodage(description); }

    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }

    public String getStatutLabel() {
        if (statut == null) return "";
        switch (statut) {
            case EN_ATTENTE: return "En attente";
            case ACCEPTEE:   return "Acceptée";
            case REFUSEE:    return "Refusée";
            default:         return statut.name();
        }
    }

    public String getDocumentJoint() { return documentJoint; }
    public void setDocumentJoint(String documentJoint) { this.documentJoint = documentJoint; }

    public LocalDateTime getDateSoumission() { return dateSoumission; }
    public void setDateSoumission(LocalDateTime dateSoumission) { this.dateSoumission = dateSoumission; }

    public LocalDateTime getDateTraitement() { return dateTraitement; }
    public void setDateTraitement(LocalDateTime dateTraitement) { this.dateTraitement = dateTraitement; }

    public Integer getAdminId() { return adminId; }
    public void setAdminId(Integer adminId) { this.adminId = adminId; }

    public String getCommentaireAdmin() { return commentaireAdmin; }
    public void setCommentaireAdmin(String commentaireAdmin) { this.commentaireAdmin = TexteUtil.reparerEncodage(commentaireAdmin); }
}
