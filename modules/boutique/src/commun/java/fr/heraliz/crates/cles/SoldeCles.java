package fr.heraliz.crates.cles;

import fr.heraliz.crates.crate.CrateType;

/**
 * Clés de caisse du joueur, telles que le serveur les a envoyées (paquet {@code Packets.Solde}) : lues par l'écran
 * d'ouverture des caisses et par l'onglet « Mes clés » de la boutique. Depuis le 25/10 les clés ne sont plus des objets :
 * elles sont comptées sur le compte du joueur, côté serveur ({@code cles/ComptesCles}) ; ce tableau n'est qu'un affichage,
 * jamais une preuve (le serveur revérifie tout à chaque ouverture).
 */
public final class SoldeCles {
    private SoldeCles() {
    }

    private static final int[] SOLDE = new int[CrateType.values().length];
    /** Faux quand le compte de clés du joueur est indisponible (fichier illisible côté serveur). */
    private static boolean disponible = true;
    /** Augmente à chaque paquet reçu (les écrans voient qu'il a changé). */
    private static int version;

    /** Clés possédées pour cette caisse (affichage). */
    public static int de(CrateType type) {
        return type == null ? 0 : SOLDE[type.ordinal()];
    }

    public static boolean disponible() {
        return disponible;
    }

    public static int version() {
        return version;
    }

    /** Appelé à la réception du paquet (fil principal du jeu). */
    public static void recevoir(java.util.Map<String, Integer> cles, boolean dispo) {
        java.util.Arrays.fill(SOLDE, 0);
        for (java.util.Map.Entry<String, Integer> e : cles.entrySet()) {
            CrateType t = CrateType.byId(e.getKey());
            if (t != null) SOLDE[t.ordinal()] = Math.max(0, e.getValue());
        }
        disponible = dispo;
        version++;
    }
}
