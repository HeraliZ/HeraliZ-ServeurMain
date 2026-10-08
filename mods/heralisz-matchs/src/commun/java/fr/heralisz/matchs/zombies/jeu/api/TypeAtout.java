package fr.heralisz.matchs.zombies.jeu.api;

import java.util.Locale;

/**
 * Les atouts (machines) du mode zombies (MATCHS_SPEC §4, ZOMBIES_V2_SPEC §1, ZOMBIES_V3_SPEC §2) : 4 de base +
 * {@link #RECHARGEMENT} (5e, optionnel sur une carte), et depuis The Giant (D-051) {@link #MULE_KICK} (3e arme) et
 * {@link #DEADSHOT} (tirs a la tete). {@link #prixCod} = prix de CoD (cartes a atouts melanges : le prix suit l'atout). L'identifiant ({@link #id()}) est celui du champ {@code atouts[].type} des metadonnees
 * de carte. Cles de traduction {@code zombies.atout.<id>} et {@code zombies.atout.<id>.desc}. Le bit
 * {@code 1 << ordinal()} sert de masque dans l'etat du HUD. Un joueur perd tous ses atouts quand il tombe a terre.
 */
public enum TypeAtout {
    /** +30 PV max (50 au lieu de 20) : 5 coups au lieu de 2, comme le Mastodonte de CoD (Jugger-Nog, 2500). */
    BLINDAGE("blindage", 0xE0473C, 2500),
    /** Reanimation 2x plus rapide ; en solo, se releve seul (3 fois par partie, prix divise par 3 : 500). Quick Revive. */
    REANIMATION("reanimation", 0x4FB8FF, 1500),
    /** +15 % de vitesse de deplacement (Stamin-Up, 2000). */
    VIGUEUR("vigueur", 0xF2C14E, 2000),
    /** +30 % de degats infliges aux zombies (Double Tap, 2000). */
    DOUBLE_IMPACT("double_impact", 0xB16CFF, 2000),
    /** Rechargement eclair : les armes rangees se rechargent seules depuis la reserve (Speed Cola, 3000). */
    RECHARGEMENT("rechargement", 0x58D68D, 3000),
    /** Coup de mule (Mule Kick, 4000) : une 3e arme (case 6 de la barre) ; elle est perdue quand le joueur tombe. */
    MULE_KICK("mule_kick", 0x7BC043, 4000),
    /** Tir d'elite (Deadshot, 1500) : degats des tirs a la tete x1,5 (regle {@code atouts.deadshot_tete}). */
    DEADSHOT("deadshot", 0x8F9AA6, 1500);

    private final String id;
    public final int couleur;
    /** Prix de CoD (Black Ops 3, The Giant). */
    public final int prixCod;

    TypeAtout(String id, int couleur, int prixCod) {
        this.id = id;
        this.couleur = couleur;
        this.prixCod = prixCod;
    }

    /** Identifiant des metadonnees de carte ("double_impact"). */
    public String id() {
        return id;
    }

    /** Cle de traduction du nom. */
    public String cle() {
        return "zombies.atout." + id;
    }

    /** Bit de masque. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Lecture tolerante par identifiant, null si inconnu. */
    public static TypeAtout parId(String id) {
        if (id == null) return null;
        String s = id.toLowerCase(Locale.ROOT);
        for (TypeAtout a : values()) if (a.id.equals(s) || a.name().equalsIgnoreCase(s)) return a;
        return null;
    }

    public static TypeAtout de(int ordinal) {
        TypeAtout[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : BLINDAGE;
    }
}
