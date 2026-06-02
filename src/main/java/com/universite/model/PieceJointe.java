package com.universite.model;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public class PieceJointe {

    private int id;
    private String nomFichier;
    private String typeMime;
    private String cheminAcces;

    public PieceJointe() {}

    public PieceJointe(String cheminAcces) {
        setCheminAcces(cheminAcces);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }

    public String getTypeMime() { return typeMime; }
    public void setTypeMime(String typeMime) { this.typeMime = typeMime; }

    public String getCheminAcces() { return cheminAcces; }
    public void setCheminAcces(String cheminAcces) {
        this.cheminAcces = cheminAcces;
        if (cheminAcces != null && !cheminAcces.isBlank() && (nomFichier == null || nomFichier.isBlank())) {
            this.nomFichier = new File(cheminAcces).getName();
        }
    }

    public void telecharger() {
        if (cheminAcces == null || cheminAcces.isBlank()) return;
        try {
            Desktop.getDesktop().open(new File(cheminAcces));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Impossible d'ouvrir la piece jointe.", e);
        }
    }
}
