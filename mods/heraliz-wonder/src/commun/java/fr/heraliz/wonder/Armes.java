package fr.heraliz.wonder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Les Wonder Weapons (ids TaCZ du gunpack {@code heraliz_wonder} ; les 3 premieres viennent de Fuse, ids inchanges : la
 * boite et l'arme de la carte du mode zombies les connaissent ; le Thundergun est fait maison, 1.0.2 ; le Winter's Howl et le
 * Paralyzer, 1.1.0, ont un numero ici pour le refus hors zombies et le nom du Pack-a-Punch ; le Jet Gun, le Blundergat /
 * Acid Gat et le Sliquifier, 1.1.0, se reconnaissent eux-memes par leur id dans leur paquet) et leur niveau de
 * Pack-a-Punch (NBT du mode zombies de HeraliZ Matchs, lu sans en
 * dependre). Aucune allocation : l'id est compare tel quel.
 */
public final class Armes {
    private Armes() {
    }

    public static final byte AUCUNE = 0, RAYON = 1, MK2 = 2, DG2 = 3, TONNERRE = 4, HURLEMENT = 5, PARALYSEUR = 6;

    public static final String ID_RAYON = HeralizWonder.MODID + ":raygun_bo6";
    public static final String ID_MK2 = HeralizWonder.MODID + ":ray_gun_mk2";
    public static final String ID_DG2 = HeralizWonder.MODID + ":wonder_waffle";
    /** Thundergun (1.0.2) ; Pack-a-Punch : Zeus Cannon. */
    public static final String ID_TONNERRE = HeralizWonder.MODID + ":thundergun";
    /** Winter's Howl (1.1.0) ; Pack-a-Punch : Winter's Fury (code : fr.heraliz.wonder.wintershowl). */
    public static final String ID_HURLEMENT = HeralizWonder.MODID + ":winters_howl";
    /** Paralyzer (1.1.0) ; Pack-a-Punch : Petrifier (code : fr.heraliz.wonder.paralyzer). */
    public static final String ID_PARALYSEUR = HeralizWonder.MODID + ":paralyzer";

    /** Cle NBT de l'id d'arme TaCZ ({@code IGun#getGunId}). */
    public static final String NBT_GUN_ID = "GunId";
    /** Marqueur Pack-a-Punch du mode zombies ({@code ZombiesApi.NBT_PAP}) : niveau 1 s'il est seul. */
    public static final String NBT_PAP = "HeraliszZbPap";
    /** Niveau de Pack-a-Punch 1..3 ({@code ZombiesApi.NBT_PAP_NIVEAU}). */
    public static final String NBT_PAP_NIVEAU = "HeraliszZbPapNiveau";
    public static final int PAP_MAX = 3;

    /** Type de Wonder Weapon d'un id d'arme TaCZ. */
    public static byte type(@Nullable ResourceLocation gunId) {
        if (gunId == null || !HeralizWonder.MODID.equals(gunId.getNamespace())) return AUCUNE;
        return switch (gunId.getPath()) {
            case "raygun_bo6" -> RAYON;
            case "ray_gun_mk2" -> MK2;
            case "wonder_waffle" -> DG2;
            case "thundergun" -> TONNERRE;
            case "winters_howl" -> HURLEMENT;
            case "paralyzer" -> PARALYSEUR;
            default -> AUCUNE;
        };
    }

    /** Type de Wonder Weapon d'une chaine « espace:chemin ». */
    public static byte type(@Nullable String gunId) {
        if (gunId == null) return AUCUNE;
        return switch (gunId) {
            case ID_RAYON -> RAYON;
            case ID_MK2 -> MK2;
            case ID_DG2 -> DG2;
            case ID_TONNERRE -> TONNERRE;
            case ID_HURLEMENT -> HURLEMENT;
            case ID_PARALYSEUR -> PARALYSEUR;
            default -> AUCUNE;
        };
    }

    /** Type de Wonder Weapon de la pile (arme TaCZ), {@link #AUCUNE} sinon. */
    public static byte type(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return AUCUNE;
        CompoundTag t = s.getTag();
        return t == null ? AUCUNE : type(t.getString(NBT_GUN_ID));
    }

    /** Niveau de Pack-a-Punch de la pile : 0 (normale) a 3. */
    public static int pap(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        CompoundTag t = s.getTag();
        if (t == null) return 0;
        int n = t.getInt(NBT_PAP_NIVEAU);
        if (n > 0) return Math.min(n, PAP_MAX);
        return t.getBoolean(NBT_PAP) ? 1 : 0;
    }

    /** Niveau de Pack-a-Punch de l'arme tenue si c'est bien l'arme {@code type}, 0 sinon. */
    public static int papTenue(@Nullable net.minecraft.world.entity.LivingEntity e, byte type) {
        if (e == null) return 0;
        ItemStack s = e.getMainHandItem();
        return type(s) == type ? pap(s) : 0;
    }

    public static String nom(byte type) {
        return switch (type) {
            case RAYON -> "pistolet a rayons";
            case MK2 -> "Mk II";
            case DG2 -> "Wunderwaffe";
            case TONNERRE -> "Thundergun";
            case HURLEMENT -> "Winter's Howl";
            case PARALYSEUR -> "Paralyzer";
            default -> "?";
        };
    }
}
