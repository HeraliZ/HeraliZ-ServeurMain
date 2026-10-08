package fr.heralisz.matchs.zombies.jeu.api;

import java.util.Locale;

/**
 * Bonus laches au hasard par les zombies (MATCHS_SPEC §4). Cles de traduction {@code zombies.bonus.<id>} (nom) et
 * {@code zombies.bonus.<id>.desc}. Couleur d'accent pour le HUD et le rendu du bonus au sol.
 */
public enum TypeBonus {
    /** Recharge toutes les armes de tous les joueurs (chargeur + reserve). */
    MUNITIONS_MAX(0, 0x6FD3FF),
    /** Les zombies meurent en un coup pendant 30 s. */
    MORT_INSTANTANEE(30, 0xFF5A5A),
    /** Tous les gains de points doubles pendant 30 s. */
    POINTS_DOUBLES(30, 0xFFD24A),
    /** Tue tous les zombies presents (sans points de kill) et donne +400 a chacun. */
    ATOMIQUE(0, 0xFF9A3C),
    /** Repare toutes les fenetres et donne +200 a chacun. */
    CHARPENTIER(0, 0xC08A55);

    /** Duree en secondes (0 = effet immediat). */
    public final int secondes;
    /** Couleur RGB. */
    public final int couleur;

    TypeBonus(int secondes, int couleur) {
        this.secondes = secondes;
        this.couleur = couleur;
    }

    /** Identifiant en minuscules ("points_doubles"). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Cle de traduction du nom. */
    public String cle() {
        return "zombies.bonus." + id();
    }

    /** Lecture tolerante par identifiant ou nom, null si inconnu. */
    public static TypeBonus parId(String id) {
        if (id == null) return null;
        for (TypeBonus b : values()) if (b.id().equalsIgnoreCase(id) || b.name().equalsIgnoreCase(id)) return b;
        return null;
    }

    public static TypeBonus de(int ordinal) {
        TypeBonus[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : MUNITIONS_MAX;
    }
}
