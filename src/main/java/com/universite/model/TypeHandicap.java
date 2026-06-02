package com.universite.model;

public enum TypeHandicap {
    HANDICAP_VISUEL("Handicap visuel"),
    HANDICAP_MOTEUR("Handicap moteur");

    private final String libelle;

    TypeHandicap(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
