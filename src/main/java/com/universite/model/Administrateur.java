package com.universite.model;

public class Administrateur extends Utilisateur {

    private String niveauAcces;

    public Administrateur() {
        setRole("ADMIN");
    }

    public String getNiveauAcces() { return niveauAcces; }
    public void setNiveauAcces(String niveauAcces) { this.niveauAcces = niveauAcces; }

    public void validerDemande(Demande demande) {
        if (demande != null) demande.setStatut(DemandeAmenagement.Statut.ACCEPTEE);
    }

    public void refuserDemande(Demande demande) {
        if (demande != null) demande.setStatut(DemandeAmenagement.Statut.REFUSEE);
    }

    public boolean filtrerDemandes(Demande demande, DemandeAmenagement.Statut statut) {
        return demande != null && (statut == null || demande.getStatut() == statut);
    }

    public void traiterReclamation(Reclamation reclamation, Reclamation.Statut statut, String reponseAdmin) {
        if (reclamation == null) return;
        reclamation.setStatut(statut);
        reclamation.setReponseAdmin(reponseAdmin);
    }

    public String genererRapport() {
        return "Rapport admin";
    }

    public void supprimerCompte(Utilisateur utilisateur) {
        if (utilisateur != null) utilisateur.setActif(false);
    }
}
