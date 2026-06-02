package com.universite.model;

import java.time.LocalDateTime;

public class JournalAudit {

    private int id;
    private int adminId;
    private String nomAdmin;
    private String action;          // ex: MODIFICATION_STATUT
    private String entite;          // ex: DEMANDE, RECLAMATION
    private int entiteId;
    private String ancienneValeur;
    private String nouvelleValeur;
    private LocalDateTime dateAction;

    public JournalAudit() {
        this.dateAction = LocalDateTime.now();
    }

    public JournalAudit(int adminId, String action, String entite, int entiteId,
                         String ancienneValeur, String nouvelleValeur) {
        this();
        this.adminId = adminId;
        this.action = action;
        this.entite = entite;
        this.entiteId = entiteId;
        this.ancienneValeur = ancienneValeur;
        this.nouvelleValeur = nouvelleValeur;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAdminId() { return adminId; }
    public void setAdminId(int adminId) { this.adminId = adminId; }

    public String getNomAdmin() { return nomAdmin; }
    public void setNomAdmin(String nomAdmin) { this.nomAdmin = nomAdmin; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntite() { return entite; }
    public void setEntite(String entite) { this.entite = entite; }

    public int getEntiteId() { return entiteId; }
    public void setEntiteId(int entiteId) { this.entiteId = entiteId; }

    public String getAncienneValeur() { return ancienneValeur; }
    public void setAncienneValeur(String ancienneValeur) { this.ancienneValeur = ancienneValeur; }

    public String getNouvelleValeur() { return nouvelleValeur; }
    public void setNouvelleValeur(String nouvelleValeur) { this.nouvelleValeur = nouvelleValeur; }

    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
}
