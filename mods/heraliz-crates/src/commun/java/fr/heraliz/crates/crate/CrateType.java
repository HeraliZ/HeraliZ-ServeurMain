package fr.heraliz.crates.crate;

import fr.heraliz.crates.loot.LootRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Les caisses du serveur : quatre caisses de boss (Sujet N-07, Sylvorn, Mere des Ruines, Porteur de Peste), la caisse de
 * vote, la caisse de quetes, la caisse d'evenement, la caisse Gemme (les couteaux aux finitions rubis/saphir/emeraude/
 * perle noire) et les caisses d'objets (camos, stickers, chantier, medicale, largage).
 * glow = couleur principale (lumiere, particules, nom), accent = couleur secondaire.
 * L'identifiant est aussi celui du design 3D (voir client.render.scene.Designs).
 */
public enum CrateType {
    SUJET_N07("sujet_n07", 0x7CFF2A, 0xE8FF6A, LootRarity.EPIC),
    SYLVORN("sylvorn", 0x9BE35A, 0xE8FFB0, LootRarity.EPIC),
    MERE_DES_RUINES("mere_des_ruines", 0xFF3048, 0xFF9AA8, LootRarity.LEGENDARY),
    PORTEUR_DE_PESTE("porteur_de_peste", 0xC6E23A, 0xF0FF9A, LootRarity.EPIC),
    VOTE("vote", 0x3FA9FF, 0xBFE4FF, LootRarity.RARE),
    QUETE("quete", 0xFFC43A, 0xFFE9A8, LootRarity.RARE),
    EVENT("event", 0xFF4FD8, 0xB89CFF, LootRarity.EPIC),
    GEMME("gemme", 0x9FE8FF, 0xFFD36A, LootRarity.MYTHIC),
    CAMO("camo", 0xFF3FA4, 0x2FE0FF, LootRarity.EPIC),
    STICKER("sticker", 0x7AF0FF, 0xFF4FD8, LootRarity.EPIC),
    BUILD("build", 0xFFB020, 0x6FD8FF, LootRarity.EPIC),
    TRAUMA("trauma", 0x3CFF8A, 0xFF2A3A, LootRarity.EPIC),
    LARGAGE("largage", 0xFF5A1A, 0x9FD85A, LootRarity.EPIC);

    /** Autres noms acceptes par les commandes. */
    private static final Map<String, CrateType> ALIASES = Map.ofEntries(
            Map.entry("n07", SUJET_N07), Map.entry("sujet", SUJET_N07), Map.entry("sujetn07", SUJET_N07),
            Map.entry("mere", MERE_DES_RUINES), Map.entry("araignee", MERE_DES_RUINES),
            Map.entry("peste", PORTEUR_DE_PESTE), Map.entry("porteur", PORTEUR_DE_PESTE),
            Map.entry("quetes", QUETE), Map.entry("quest", QUETE), Map.entry("quests", QUETE),
            Map.entry("evenement", EVENT), Map.entry("events", EVENT),
            Map.entry("gemmes", GEMME), Map.entry("gem", GEMME), Map.entry("gems", GEMME), Map.entry("geode", GEMME), Map.entry("camos", CAMO), Map.entry("skins", CAMO), Map.entry("peinture", CAMO), Map.entry("atelier", CAMO), Map.entry("stickers", STICKER), Map.entry("booster", STICKER), Map.entry("holo", STICKER), Map.entry("chantier", BUILD), Map.entry("construction", BUILD), Map.entry("base", BUILD), Map.entry("bunker", BUILD), Map.entry("medical", TRAUMA), Map.entry("medic", TRAUMA), Map.entry("medicale", TRAUMA), Map.entry("soin", TRAUMA), Map.entry("soins", TRAUMA), Map.entry("hopital", TRAUMA), Map.entry("airdrop", LARGAGE), Map.entry("ravitaillement", LARGAGE), Map.entry("supply", LARGAGE), Map.entry("drop", LARGAGE), Map.entry("largages", LARGAGE));

    public final String id;
    public final int glow;
    public final int accent;
    public final LootRarity tier;

    CrateType(String id, int glow, int accent, LootRarity tier) {
        this.id = id;
        this.glow = glow;
        this.accent = accent;
        this.tier = tier;
    }

    public float r() { return ((glow >> 16) & 255) / 255f; }
    public float g() { return ((glow >> 8) & 255) / 255f; }
    public float b() { return (glow & 255) / 255f; }
    public float ar() { return ((accent >> 16) & 255) / 255f; }
    public float ag() { return ((accent >> 8) & 255) / 255f; }
    public float ab() { return (accent & 255) / 255f; }

    public String blockId() { return id + "_crate"; }
    public String keyId() { return id + "_key"; }

    public MutableComponent crateName() {
        return Component.translatable("block.heralizcrates." + blockId()).withStyle(s -> s.withColor(glow));
    }

    public MutableComponent keyName() {
        return Component.translatable("item.heralizcrates." + keyId()).withStyle(s -> s.withColor(glow));
    }

    @Nullable
    public static CrateType byId(String id) {
        if (id == null) return null;
        String s = Normalizer.normalize(id.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('-', '_').replace(' ', '_');
        for (CrateType t : values()) {
            if (t.id.equals(s) || t.blockId().equals(s) || t.keyId().equals(s)) return t;
        }
        CrateType alias = ALIASES.get(s.replace("_", ""));
        if (alias != null) return alias;
        return ALIASES.get(s);
    }
}
