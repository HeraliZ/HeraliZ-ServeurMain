package fr.heraliz.armory.armor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import org.jetbrains.annotations.Nullable;

/**
 * Une piece d'un ensemble (genere par tools/build_all.py dans ArmorCatalog).
 * <p>Protections (0.8.2) : meme echelle que HeralisZ Armor, en % par type de degat ; les % des pieces portees
 * s'additionnent, plafonnes a 75 % (radiation : 100 %). Aucune armure vanilla. Voir {@link Protection}.
 *
 * @param set        identifiant de l'ensemble (citadin, sentinelle...)
 * @param piece      head, face, chest, vest, hands, legs, knees, feet
 * @param tier       palier 1 a 6, 7 = special (0 = mire de test)
 * @param durability durabilite
 * @param speed      modificateur de vitesse (ex. -0.05 = -5 %)
 * @param bullet     % contre les balles et projectiles
 * @param melee      % contre les coups des joueurs
 * @param bite       % contre les attaques des creatures (zombies...)
 * @param explosion  % contre les explosions
 * @param radiation  % contre l'irradiation
 * @param gas        la piece filtre les gaz (masque NRBC) : bloque les effets listes dans la config
 * @param nvg        casque avec lunettes de vision nocturne articulees (touche dediee : baisser / relever)
 * @param bonus      0.9.0 : bonus des combinaisons (« cold » : immunite au gel, « diving » : bouteille et palmes), sinon null
 */
public record PieceDef(String set, String piece, int tier, int durability, double speed, int bullet, int melee, int bite,
                       float explosion, int radiation, boolean gas, boolean nvg, @Nullable String bonus) implements Guard {

    /** Pieces des ensembles (catalogue genere par tools/build_all.py) : aucun bonus. */
    public PieceDef(String set, String piece, int tier, int durability, double speed, int bullet, int melee, int bite,
                    float explosion, int radiation, boolean gas, boolean nvg) {
        this(set, piece, tier, durability, speed, bullet, melee, bite, explosion, radiation, gas, nvg, null);
    }

    public String itemId() {
        return set + "_" + piece;
    }

    /** Combinaison integrale (0.9.0) : un seul objet, porte au torse, qui couvre tout le corps et se porte seul. */
    public boolean isSuit() {
        return "suit".equals(piece);
    }

    @Override
    public boolean wholeBody() {
        return isSuit();
    }

    @Override
    public boolean cold() {
        return "cold".equals(bonus);
    }

    /** Tenue de plongee : bouteille d'air et palmes. */
    public boolean diving() {
        return "diving".equals(bonus);
    }

    /**
     * Slot Curios ou null pour une piece d'armure vanilla. Visage et gilet partagent les slots de HeralisZ Armor
     * (« mask » = Visage, « belt » = Gilet) : un seul masque et un seul gilet portes, tous mods confondus.
     */
    @Nullable
    public String curioSlot() {
        return switch (piece) {
            case "face" -> "mask";
            case "vest" -> "belt";
            case "hands" -> "hands";
            case "knees" -> "knees";
            default -> null;
        };
    }

    /** Piece de tete (casque, visage) : seule a compter sur un tir a la tete, un quart sur les autres coups. */
    @Override
    public boolean protectsHead() {
        return "head".equals(piece) || "face".equals(piece);
    }

    @Nullable
    public ArmorItem.Type armorType() {
        return switch (piece) {
            case "head" -> ArmorItem.Type.HELMET;
            case "chest", "suit" -> ArmorItem.Type.CHESTPLATE;
            case "legs" -> ArmorItem.Type.LEGGINGS;
            case "feet" -> ArmorItem.Type.BOOTS;
            default -> null;
        };
    }

    @Nullable
    public EquipmentSlot equipmentSlot() {
        ArmorItem.Type t = armorType();
        return t == null ? null : t.getSlot();
    }

    public String texturePath() {
        return "textures/models/armor/" + set + ".png";
    }

    public String modelPath() {
        return "armor/" + set + "/" + piece + ".json";
    }
}
