package fr.heralisz.tags.catalogue;

import net.minecraft.world.item.Rarity;

/**
 * Rarete d'un tag : couleur du liseré dans la collection et couleur du nom de l'objet.
 */
public enum Rarete {
    COMMUN(0xB8BEC7, Rarity.COMMON),
    RARE(0x4FA3FF, Rarity.UNCOMMON),
    EPIQUE(0xB56BFF, Rarity.RARE),
    LEGENDAIRE(0xF2B544, Rarity.EPIC);

    /** Couleur RGB (sans alpha). */
    public final int couleur;
    /** Rarete vanilla equivalente (couleur du nom de l'objet). */
    public final Rarity vanilla;

    Rarete(int couleur, Rarity vanilla) {
        this.couleur = couleur;
        this.vanilla = vanilla;
    }

    /** Cle de traduction du libelle (rarete.heralisz_tags.commun ...). */
    public String cle() {
        return "rarete.heralisz_tags." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Lecture tolerante (casse, accents absents) ; inconnu = COMMUN. */
    public static Rarete depuis(String s) {
        if (s == null) return COMMUN;
        String u = s.trim().toUpperCase(java.util.Locale.ROOT);
        for (Rarete r : values()) if (r.name().equals(u)) return r;
        return COMMUN;
    }
}
