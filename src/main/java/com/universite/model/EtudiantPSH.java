package com.universite.model;

import java.time.LocalDate;

public class EtudiantPSH extends Utilisateur {

    private TypeHandicap typeHandicap;
    private String dossier;
    private LocalDate dateInscription;

    public EtudiantPSH() {
        setRole("ETUDIANT");
    }

    public TypeHandicap getTypeHandicap() { return typeHandicap; }
    public void setTypeHandicap(TypeHandicap typeHandicap) { this.typeHandicap = typeHandicap; }

    public String getDossier() { return dossier; }
    public void setDossier(String dossier) { this.dossier = dossier; }

    public LocalDate getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDate dateInscription) { this.dateInscription = dateInscription; }

    public String consulterStatut(Demande demande) {
        return demande != null ? demande.getStatutLabel() : "";
    }

    public PieceJointe telechargerPiece(String cheminAcces) {
        PieceJointe piece = new PieceJointe(cheminAcces);
        piece.telecharger();
        return piece;
    }
}
