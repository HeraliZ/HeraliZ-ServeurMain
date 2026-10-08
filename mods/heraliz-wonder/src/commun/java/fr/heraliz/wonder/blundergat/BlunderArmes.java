package fr.heraliz.wonder.blundergat;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Blundergat (Call of Duty : Black Ops II, Mob of the Dead ; Pack-a-Punch « The Sweeper ») et Acid Gat (kit de l'etabli ;
 * Pack-a-Punch « Vitriolic Withering ») : ids TaCZ du gunpack {@code heraliz_wonder}, reconnaissance sans allocation.
 * Volontairement a part de {@link Armes} (aucune constante partagee a renumeroter a l'integration) ; le niveau de
 * Pack-a-Punch est lu par {@link Armes#pap} (NBT du mode zombies).
 */
public final class BlunderArmes {
    private BlunderArmes() {
    }

    public static final byte AUCUNE = 0, BLUNDERGAT = 1, ACIDGAT = 2;

    public static final String ID_BLUNDERGAT = HeralizWonder.MODID + ":blundergat";
    public static final String ID_ACIDGAT = HeralizWonder.MODID + ":acidgat";

    /** Type de degats des explosions d'acide (dans {@code #minecraft:is_explosion} : le mode zombies applique le Pack-a-Punch). */
    public static final ResourceKey<DamageType> ACIDE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(HeralizWonder.MODID, "acide"));

    public static byte type(@Nullable ResourceLocation gunId) {
        if (gunId == null || !HeralizWonder.MODID.equals(gunId.getNamespace())) return AUCUNE;
        return switch (gunId.getPath()) {
            case "blundergat" -> BLUNDERGAT;
            case "acidgat" -> ACIDGAT;
            default -> AUCUNE;
        };
    }

    public static byte type(@Nullable String gunId) {
        if (gunId == null) return AUCUNE;
        return switch (gunId) {
            case ID_BLUNDERGAT -> BLUNDERGAT;
            case ID_ACIDGAT -> ACIDGAT;
            default -> AUCUNE;
        };
    }

    public static byte type(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) return AUCUNE;
        CompoundTag t = s.getTag();
        return t == null ? AUCUNE : type(t.getString(Armes.NBT_GUN_ID));
    }

    /** Niveau de Pack-a-Punch de l'arme tenue si c'est bien l'arme {@code type}, 0 sinon. */
    public static int papTenue(@Nullable LivingEntity e, byte type) {
        if (e == null) return 0;
        ItemStack s = e.getMainHandItem();
        return type(s) == type ? Armes.pap(s) : 0;
    }

    public static String nom(byte type) {
        return type == ACIDGAT ? "Acid Gat" : type == BLUNDERGAT ? "Blundergat" : "?";
    }
}
