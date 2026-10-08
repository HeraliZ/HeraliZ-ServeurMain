package fr.heraliz.loot;

import net.minecraft.world.item.ItemStack;

/** Raretés du loot : couleur du cadre, nom affiché, temps d'identification (ticks) — plus c'est rare, plus c'est long. */
public enum Rarete {
    COMMUN("commun", "Commun", 0x9A9A9A, 20),
    PEU_COMMUN("peu_commun", "Peu commun", 0x5CBF4A, 30),
    RARE("rare", "Rare", 0x3F8CFF, 45),
    EPIQUE("epique", "Épique", 0xA64DFF, 70),
    LEGENDAIRE("legendaire", "Légendaire", 0xFF9B1F, 110),
    MYTHIQUE("mythique", "Mythique", 0xFF3333, 160),
    UNIQUE("unique", "Unique", 0xFFD84A, 220);

    public static final String CLE = "HeralizRarete";
    public final String id, nom;
    public final int couleur, duree;

    Rarete(String id, String nom, int couleur, int duree) {
        this.id = id;
        this.nom = nom;
        this.couleur = couleur;
        this.duree = duree;
    }

    public static Rarete de(String id) {
        for (Rarete r : values()) {
            if (r.id.equalsIgnoreCase(id)) {
                return r;
            }
        }
        return COMMUN;
    }

    /** Rareté inscrite sur un objet de coffre (null : objet ordinaire). */
    public static Rarete de(ItemStack st) {
        return st.hasTag() && st.getTag().contains(CLE) ? de(st.getTag().getString(CLE)) : null;
    }

    public boolean haute() {
        return ordinal() >= LEGENDAIRE.ordinal();
    }
}
