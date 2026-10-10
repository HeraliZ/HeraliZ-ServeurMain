package fr.heraliz.loot;

import net.minecraft.world.item.ItemStack;

/**
 * Les 6 raretés des objets (choix du propriétaire) : couleur (fond des cases, cadre des coffres T7, infobulle), nom
 * affiché, temps d'identification dans un coffre T7 (ticks) — plus c'est rare, plus c'est long. L'ancienne rareté
 * « mythique » des tables est lue comme Unique.
 */
public enum Rarete {
    COMMUN("commun", "Commun", 0x9A9A9A, 20),
    PEU_COMMUN("peu_commun", "Peu commun", 0x5CBF4A, 30),
    RARE("rare", "Rare", 0x3F8CFF, 45),
    EPIQUE("epique", "Épique", 0xA64DFF, 70),
    LEGENDAIRE("legendaire", "Légendaire", 0xF2B83A, 110),
    UNIQUE("unique", "Unique", 0xFF3333, 220);

    public static final String CLE = "HeralizRarete";
    public final String id, nom;
    public final int couleur, duree;

    Rarete(String id, String nom, int couleur, int duree) {
        this.id = id;
        this.nom = nom;
        this.couleur = couleur;
        this.duree = duree;
    }

    /** Rareté d'après son identifiant (« mythique », ancien nom, = Unique) ; inconnu : Commun. */
    public static Rarete de(String id) {
        Rarete r = lire(id);
        return r == null ? COMMUN : r;
    }

    /** Rareté d'après son identifiant, null si inconnu. */
    public static Rarete lire(String id) {
        if (id == null) return null;
        if (id.equalsIgnoreCase("mythique")) return UNIQUE;
        for (Rarete r : values()) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    /** Rareté inscrite sur un objet de coffre (null : objet ordinaire). */
    public static Rarete de(ItemStack st) {
        return st.hasTag() && st.getTag().contains(CLE) ? de(st.getTag().getString(CLE)) : null;
    }

    public boolean haute() {
        return ordinal() >= LEGENDAIRE.ordinal();
    }
}
