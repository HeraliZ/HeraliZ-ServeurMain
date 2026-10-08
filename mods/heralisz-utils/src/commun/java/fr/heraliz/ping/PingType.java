package fr.heraliz.ping;

import javax.annotation.Nullable;

/**
 * Les 6 types de ping, dans l'ordre de la roue (sens horaire depuis le haut). Chaque type a une icone de l'atlas
 * ({@code nom}), une couleur nette et distincte des autres, et un son : « alerte » pour Ennemi et Danger, « ping » pour
 * le reste. L'identifiant (0 a 5) est celui des paquets ; {@link #AUTO} = au serveur de choisir (appui bref).
 */
public enum PingType {
    ENNEMI("ennemi", 0xFF4D4D, true),
    DANGER("danger", 0xFF9B2F, true),
    LOOT("loot", 0xFFD447, false),
    ALLER("aller", 0x4FE08A, false),
    DEFENDRE("defendre", 0x5B8CFF, false),
    AIDE("aide", 0xE46BFF, false);

    /** Type « automatique » d'un appui bref : le serveur le deduit de la cible (voir server/Contexte). */
    public static final int AUTO = 7;
    private static final PingType[] PAR_ID = values();

    /** Nom court : icone de l'atlas et cle de langue {@code heraliz_ping.type.<nom>}. */
    public final String nom;
    /** Couleur RGB 24 bits. */
    public final int couleur;
    /** Son d'alerte (sinon le carillon doux). */
    public final boolean alerte;

    PingType(String nom, int couleur, boolean alerte) {
        this.nom = nom;
        this.couleur = couleur;
        this.alerte = alerte;
    }

    public int id() {
        return ordinal();
    }

    public String cle() {
        return "heraliz_ping.type." + nom;
    }

    @Nullable
    public static PingType parId(int id) {
        return id >= 0 && id < PAR_ID.length ? PAR_ID[id] : null;
    }

    public static int nombre() {
        return PAR_ID.length;
    }
}
