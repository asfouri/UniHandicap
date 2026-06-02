package com.universite.model;

import java.time.LocalDateTime;

public class Demande extends DemandeAmenagement {

    public Demande() {
        super();
    }

    public TypeAmenagement getType() {
        return getTypeAmenagement();
    }

    public void setType(TypeAmenagement type) {
        setTypeAmenagement(type);
    }

    public LocalDateTime getDateCreation() {
        return getDateSoumission();
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        setDateSoumission(dateCreation);
    }

    public void soumettre() {
        setStatut(Statut.EN_ATTENTE);
        if (getDateSoumission() == null) {
            setDateSoumission(LocalDateTime.now());
        }
    }

    public void mettreAJourStatut(Statut statut) {
        setStatut(statut);
        setDateTraitement(LocalDateTime.now());
    }

    public void annuler() {
        setStatut(Statut.REFUSEE);
    }
}
