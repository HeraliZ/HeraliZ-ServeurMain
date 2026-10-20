package fr.heraliz.crac.build;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/** Server config of the building system (world/serverconfig/crac-building.toml). */
public final class BuildConfig {
    public static final ForgeConfigSpec SPEC;

    public static final IntValue maxPiecesWithoutClaim;
    public static final IntValue foundationMaxHeight;
    public static final IntValue verticalLoss;
    public static final IntValue cantileverLoss;
    public static final IntValue minStability;
    public static final IntValue demolishWindowSeconds;
    public static final DoubleValue reach;
    public static final BooleanValue twigMeleeDamage;
    public static final IntValue twigMeleeHit;
    public static final IntValue repairPercent;
    public static final DoubleValue repairSeconds;
    public static final BooleanValue repairBlockedDuringRaid;
    public static final BooleanValue upgradeHeals;
    public static final BooleanValue discardOldBuildings;

    private static final Map<Tier, IntValue> HP = new EnumMap<>(Tier.class);
    private static final Map<PieceKind, DoubleValue> HP_MULT = new EnumMap<>(PieceKind.class);
    private static final Map<PieceKind, ConfigValue<List<? extends String>>> PLACE_COST = new EnumMap<>(PieceKind.class);
    private static final Map<Tier, ConfigValue<List<? extends String>>> UPGRADE_COST = new EnumMap<>(Tier.class);
    private static final Map<PieceKind, DoubleValue> COST_MULT = new EnumMap<>(PieceKind.class);
    private static final Map<Tier, ConfigValue<List<? extends String>>> REPAIR_COST = new EnumMap<>(Tier.class);

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Rust-style building rules.").push("rules");
        maxPiecesWithoutClaim = b.comment("Pieces a building may have before a Claim Block (tool cupboard) is placed on it.",
                "1 = only the first foundation: everything else must be connected to a claimed building.")
            .defineInRange("maxPiecesWithoutClaim", 1, 1, 1000);
        foundationMaxHeight = b.comment("Max blocks between a foundation slab and the ground (filled with a solid skirt).")
            .defineInRange("foundationMaxHeight", 3, 0, 16);
        verticalLoss = b.comment("Stability lost per vertical support (floor on walls, wall on wall).").defineInRange("verticalLoss", 5, 1, 100);
        cantileverLoss = b.comment("Stability lost per floor hanging off a neighbouring floor.").defineInRange("cantileverLoss", 35, 1, 100);
        minStability = b.comment("Minimum stability (0-100) to place a piece.").defineInRange("minStability", 10, 1, 100);
        demolishWindowSeconds = b.comment("Seconds after placement / upgrade during which the owner can demolish a piece. -1 = always.")
            .defineInRange("demolishWindowSeconds", 600, -1, 1000000);
        reach = b.comment("Max distance between the player's eyes and a piece to build, upgrade or repair it.").defineInRange("reach", 8.0, 2.0, 32.0);
        discardOldBuildings = b.comment("Building data saved by an older version of the mod (v2 grid) cannot be used: by default the",
                "buildings of such a world are left untouched (not usable, nothing saved) and an error is logged. true = copy the old",
                "file aside (crac_buildings.dat.v2-<time>) and start with no buildings. The old structure blocks then clean themselves up.")
            .define("discardOldBuildings", false);
        b.pop();

        b.comment("Twig pieces can be broken by hand, like in Rust.").push("twig");
        twigMeleeDamage = b.define("meleeDamage", true);
        twigMeleeHit = b.comment("HP removed by one full-strength melee hit on a twig piece.").defineInRange("meleeHit", 3, 1, 1000);
        b.pop();

        b.comment("Health per tier, multiplied by the piece multiplier below.").push("health");
        int[] hp = {10, 250, 500, 1000, 2000};
        for (Tier t : Tier.values()) {
            HP.put(t, b.defineInRange(t.id, hp[t.ordinal()], 1, 1000000));
        }
        b.push("multiplier");
        for (PieceKind k : PieceKind.values()) {
            double m = switch (k) {
                case HALF_WALL, STAIRS_L, RAMP -> 0.5;
                case DOORWAY, DOUBLE_DOORWAY -> 0.6;
                case WINDOW -> 0.8;
                default -> 1.0;
            };
            HP_MULT.put(k, b.defineInRange(k.id, m, 0.05, 20.0));
        }
        b.pop();
        upgradeHeals = b.comment("If true, upgrading restores full health of the new tier.").define("upgradeHeals", true);
        b.pop();

        b.comment("Costs. Format \"modid:item=count\" or \"#tag=count\". An empty list [] makes the action free.",
            "An entry naming an unknown item or an empty tag blocks the action (logged at server start), it is never skipped.").push("cost");
        b.comment("Twig cost to place each piece (HeraliZ: brindilles of heraliz_composants, 19/10).").push("place");
        for (PieceKind k : PieceKind.values()) {
            int sticks = switch (k) {
                case TRI_FOUNDATION, TRI_FLOOR, TRI_ROOF, HALF_WALL -> 8;
                case DOORWAY, WINDOW -> 12;
                case STAIRS_L -> 24;
                default -> 16;
            };
            PLACE_COST.put(k, b.defineList(k.id, List.of("heraliz_composants:brindilles=" + sticks), BuildConfig::isCostEntry));
        }
        b.pop();
        b.comment("Cost to upgrade a full piece to each tier; multiplied per piece below (rounded up).").push("upgrade");
        UPGRADE_COST.put(Tier.WOOD, b.defineList("wood", List.of("heraliz_composants:planches=5", "heraliz_composants:clou=5"), BuildConfig::isCostEntry));
        UPGRADE_COST.put(Tier.STONE, b.defineList("stone", List.of("heraliz_composants:pierres_taillees=5", "heraliz_composants:sac_ciment=5"), BuildConfig::isCostEntry));
        UPGRADE_COST.put(Tier.METAL, b.defineList("metal", List.of("heraliz_composants:fragments_metal=5", "heraliz_composants:equerres_acier=5"), BuildConfig::isCostEntry));
        UPGRADE_COST.put(Tier.ARMORED, b.defineList("armored", List.of("heraliz_composants:metal_renforce=5", "heraliz_composants:fil_souder=5"), BuildConfig::isCostEntry));
        b.push("multiplier");
        for (PieceKind k : PieceKind.values()) {
            // 19/10 (choix du propriétaire) : toutes les pièces au même prix d'amélioration
            COST_MULT.put(k, b.defineInRange(k.id, 1.0, 0.0, 20.0));
        }
        b.pop();
        b.pop();
        b.comment("Items consumed by one repair, per tier.").push("repair");
        REPAIR_COST.put(Tier.TWIG, b.defineList("twig", List.of("heraliz_composants:brindilles=2"), BuildConfig::isCostEntry));
        REPAIR_COST.put(Tier.WOOD, b.defineList("wood", List.of("heraliz_composants:planches=1"), BuildConfig::isCostEntry));
        REPAIR_COST.put(Tier.STONE, b.defineList("stone", List.of("heraliz_composants:pierres_taillees=1"), BuildConfig::isCostEntry));
        REPAIR_COST.put(Tier.METAL, b.defineList("metal", List.of("heraliz_composants:fragments_metal=1"), BuildConfig::isCostEntry));
        REPAIR_COST.put(Tier.ARMORED, b.defineList("armored", List.of("heraliz_composants:metal_renforce=1"), BuildConfig::isCostEntry));
        b.pop();
        b.pop();

        b.push("repair");
        repairPercent = b.comment("Percent of max health restored by one repair.").defineInRange("percent", 20, 1, 100);
        repairSeconds = b.comment("Seconds of holding the hammer on a piece to complete one repair.").defineInRange("seconds", 3.0, 0.2, 60.0);
        repairBlockedDuringRaid = b.comment("If true, repairing is blocked while the claim is under raid block.").define("blockedDuringRaid", true);
        b.pop();
        SPEC = b.build();
    }

    private BuildConfig() {
    }

    public static Stability.Params stability() {
        return new Stability.Params(verticalLoss.get(), cantileverLoss.get(), minStability.get());
    }

    public static int maxHp(Tier tier, PieceKind kind) {
        return Math.max(1, (int) Math.round(HP.get(tier).get() * HP_MULT.get(kind).get()));
    }

    public static List<? extends String> placeCost(PieceKind kind) {
        return PLACE_COST.get(kind).get();
    }

    /** Raw upgrade cost entries for a full piece; scale with {@link #costMultiplier}. Twig has none. */
    public static List<? extends String> upgradeCost(Tier tier) {
        ConfigValue<List<? extends String>> v = UPGRADE_COST.get(tier);
        return v == null ? List.of() : v.get();
    }

    public static double costMultiplier(PieceKind kind) {
        return COST_MULT.get(kind).get();
    }

    public static List<? extends String> repairCost(Tier tier) {
        return REPAIR_COST.get(tier).get();
    }

    private static boolean isCostEntry(Object value) {
        if (!(value instanceof String entry)) {
            return false;
        }
        int sep = entry.indexOf('=');
        if (sep <= 0 || sep == entry.length() - 1) {
            return false;
        }
        try {
            return Integer.parseInt(entry.substring(sep + 1).trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
