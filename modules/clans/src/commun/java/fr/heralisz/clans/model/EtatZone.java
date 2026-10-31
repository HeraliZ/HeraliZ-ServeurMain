package fr.heralisz.clans.model;

/**
 * Etat d'une zone vu par un joueur present (barre de capture du HUD et etat du drapeau). Ajouter en dernier (ordinal
 * envoye sur le reseau).
 */
public enum EtatZone {
    NEUTRE, CAPTURE, CONTESTE, TENUE, DEFENSE, PROTEGEE, LIMITE,
    /**
     * 31/10 : le clan du joueur ne peut pas encore capturer (avant le niveau {@link Arbre#NIVEAU_CAPTURE}, ou sans le
     * premier palier de la branche Territoires) ; il ne capture ni ne conteste.
     */
    VERROUILLEE;

    public String libelle() {
        return "zone.etat." + name().toLowerCase();
    }

    public static EtatZone parOrdinal(int i) {
        EtatZone[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
