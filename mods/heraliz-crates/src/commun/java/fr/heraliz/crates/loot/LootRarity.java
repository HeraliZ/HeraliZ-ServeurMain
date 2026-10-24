package fr.heraliz.crates.loot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Rarete d'une recompense : couleur, son de victoire et effets. MYTHIC (identifiant « mythic », garde dans les fichiers)
 * s'affiche « Unique » depuis Caisses 2.22.0, comme la 6e rarete de HeraliZ Loot.
 */
public enum LootRarity {
    COMMON("common", 0xD0D0D0, 0),
    UNCOMMON("uncommon", 0x5CFF6A, 0),
    RARE("rare", 0x4DA6FF, 1),
    EPIC("epic", 0xC45CFF, 1),
    LEGENDARY("legendary", 0xFFB42E, 2),
    MYTHIC("mythic", 0xFF2E4A, 2);

    public final String id;
    public final int color;
    /** 0 = son commun, 1 = son rare, 2 = son legendaire. */
    public final int soundTier;

    LootRarity(String id, int color, int soundTier) {
        this.id = id;
        this.color = color;
        this.soundTier = soundTier;
    }

    public float r() { return ((color >> 16) & 255) / 255f; }
    public float g() { return ((color >> 8) & 255) / 255f; }
    public float b() { return (color & 255) / 255f; }

    public MutableComponent displayName() {
        return Component.translatable("rarity.heralizcrates." + id).withStyle(s -> s.withColor(color));
    }

    public boolean atLeast(LootRarity other) {
        return ordinal() >= other.ordinal();
    }

    public static LootRarity byId(String id) {
        if (id != null) {
            String s = id.toLowerCase(Locale.ROOT).trim();
            for (LootRarity r : values()) {
                if (r.id.equals(s)) return r;
            }
            // alias francais
            switch (s) {
                case "commun": return COMMON;
                case "peu_commun": case "peu-commun": return UNCOMMON;
                case "epique": case "épique": return EPIC;
                case "legendaire": case "légendaire": return LEGENDARY;
                case "mythique": case "unique": return MYTHIC;
                default: break;
            }
        }
        return COMMON;
    }
}
