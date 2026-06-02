package com.universite.model;

import java.time.LocalDateTime;

public class Notification {

    private int id;
    private int utilisateurId;
    private String message;
    private boolean lue;
    private LocalDateTime dateCreation;

    public Notification() {}

    public Notification(int utilisateurId, String message) {
        this.utilisateurId = utilisateurId;
        this.message = message;
        this.lue = false;
        this.dateCreation = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUtilisateurId() { return utilisateurId; }
    public void setUtilisateurId(int utilisateurId) { this.utilisateurId = utilisateurId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isLue() { return lue; }
    public void setLue(boolean lue) { this.lue = lue; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
}
