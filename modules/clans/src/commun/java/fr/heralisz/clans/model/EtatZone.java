package fr.heralisz.clans.model;

/**
 * Etat d'une zone vu par un joueur present (barre de capture du HUD et etat du drapeau).
 */
public enum EtatZone {
    NEUTRE, CAPTURE, CONTESTE, TENUE, DEFENSE, PROTEGEE, LIMITE;

    public String libelle() {
        return "zone.etat." + name().toLowerCase();
    }

    public static EtatZone parOrdinal(int i) {
        EtatZone[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
