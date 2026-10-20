package fr.heraliz.crac;

import java.util.List;
import fr.heraliz.crac.claim.UpkeepResource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.registries.ForgeRegistries;

/** Server config (crac-server.toml). Building rules, health and costs live in {@link fr.heraliz.crac.build.BuildConfig}. */
public class Config {
    public static final Builder BUILDER = new Builder();
    public static final ForgeConfigSpec SPEC;

    // Static initializers run in source order: these blocks put the values defined inline below into their section.
    static {
        BUILDER.comment("Claims: protection inside and outside them, size per Claim Block tier, where they may be placed.").push("claims");
    }

    public static final IntValue claimViewRadius = BUILDER.comment("Distance (blocks) within which claim boundaries are synced to clients.")
        .defineInRange("viewRadius", 256, 32, 2048);
    public static final BooleanValue protectOutsideClaims = BUILDER.comment(
            "If true, non-creative players cannot place blocks anywhere outside their own group claim.",
            "Building pieces: only a new building's first foundation (the Claim Block's seat) may go outside a claim.",
            "Other groups' claims are always protected regardless of this setting."
        )
        .define("protectOutsideClaims", false);
    public static final BooleanValue allowBreakInClaim = BUILDER.comment(
            "If true, group members may break (and place) normal blocks inside their own claim.",
            "If false, breaking inside ANY claim is fully blocked; raiding still works via explosives on building pieces and placables."
        )
        .define("allowBreakInClaim", false);
    public static final IntValue claimRadiusL1 = BUILDER.comment("Claim horizontal radius (XZ) for a tier 1 Claim Block.")
        .defineInRange("radiusL1", 15, 1, 512);
    public static final IntValue claimRadiusL2 = BUILDER.comment("Claim horizontal radius (XZ) for a tier 2 Claim Block.")
        .defineInRange("radiusL2", 18, 1, 512);
    public static final IntValue claimRadiusL3 = BUILDER.comment("Claim horizontal radius (XZ) for a tier 3 Claim Block.")
        .defineInRange("radiusL3", 21, 1, 512);
    public static final IntValue claimYRadiusL1 = BUILDER.comment("Claim vertical half-height for tier 1.").defineInRange("yRadiusL1", 16, 1, 256);
    public static final IntValue claimYRadiusL2 = BUILDER.comment("Claim vertical half-height for tier 2.").defineInRange("yRadiusL2", 19, 1, 256);
    public static final IntValue claimYRadiusL3 = BUILDER.comment("Claim vertical half-height for tier 3.").defineInRange("yRadiusL3", 22, 1, 256);
    public static final ConfigValue<String> claimUpgradeItemL1L2 = BUILDER.comment("Item paid to upgrade a claim from tier 1 to 2.")
        .define("upgradeItemL1L2", "heraliz_composants:metal_renforce");
    public static final ConfigValue<String> claimUpgradeItemL2L3 = BUILDER.comment("Item paid to upgrade a claim from tier 2 to 3.")
        .define("upgradeItemL2L3", "heraliz_composants:metal_renforce");
    public static final IntValue claimUpgradeCostL1L2 = BUILDER.comment("How many upgradeItemL1L2 the tier 1 to 2 upgrade takes.")
        .defineInRange("upgradeCostL1L2", 10, 0, 100000);
    public static final IntValue claimUpgradeCostL2L3 = BUILDER.comment("How many upgradeItemL2L3 the tier 2 to 3 upgrade takes.")
        .defineInRange("upgradeCostL2L3", 25, 0, 100000);
    public static final IntValue claimSpacingMargin = BUILDER.comment(
            "Extra blocks of clearance forced between two groups' claims,",
            "on top of the gap needed for their footprints not to touch.",
            "Guarantees no block can ever fall inside two claims at once."
        )
        .defineInRange("spacingMargin", 4, 0, 512);
    public static final IntValue claimMinY = BUILDER.comment(
            "Lowest Y a Claim Block may be placed at. -64 (the overworld floor) means no limit;",
            "raise it (e.g. -40) to stop bases being anchored down in bedrock caves. Must not exceed maxY."
        )
        .defineInRange("minY", -64, -2048, 2048);
    public static final IntValue claimMaxY = BUILDER.comment("Highest Y a Claim Block may be placed at.", "Stops sky bases anchored out of reach.")
        .defineInRange("maxY", 200, -2048, 2048);

    static {
        BUILDER.pop();
        BUILDER.comment("Upkeep stocked in the Claim Block for the pieces of the claimed building.").push("upkeep");
    }

    public static final DoubleValue upkeepHours = BUILDER.comment(
            "Hours a FULL upkeep supply (perPiece items for every piece of the claimed building) sustains the claim."
        )
        .defineInRange("hours", 24.0, 0.1, 8760.0);
    public static final ConfigValue<String> upkeepItemWood = BUILDER.comment("Upkeep item paid for Twig and Wood pieces.")
        .define("itemWood", "heraliz_composants:planches");
    public static final ConfigValue<String> upkeepItemStone = BUILDER.comment("Upkeep item paid for Stone pieces.")
        .define("itemStone", "heraliz_composants:pierres_taillees");
    public static final ConfigValue<String> upkeepItemMetal = BUILDER.comment("Upkeep item paid for Sheet Metal pieces.")
        .define("itemMetal", "heraliz_composants:fragments_metal");
    public static final ConfigValue<String> upkeepItemArmored = BUILDER.comment("Upkeep item paid for Armored pieces.")
        .define("itemArmored", "heraliz_composants:metal_renforce");
    public static final IntValue upkeepPerPieceWood = BUILDER.comment("Items of itemWood per Twig or Wood piece for a full supply.")
        .defineInRange("perPieceWood", 1, 0, 100000);
    public static final IntValue upkeepPerPieceStone = BUILDER.comment("Items of itemStone per Stone piece for a full supply.")
        .defineInRange("perPieceStone", 1, 0, 100000);
    public static final IntValue upkeepPerPieceMetal = BUILDER.comment("Items of itemMetal per Sheet Metal piece for a full supply.")
        .defineInRange("perPieceMetal", 1, 0, 100000);
    public static final IntValue upkeepPerPieceArmored = BUILDER.comment("Items of itemArmored per Armored piece for a full supply.")
        .defineInRange("perPieceArmored", 1, 0, 100000);
    public static final IntValue upkeepSlotCapacity = BUILDER.comment("Max items stockpiled per upkeep slot. Withdrawals are limited to one stack at a time.")
        .defineInRange("slotCapacity", 2000, 64, 100000);

    static {
        BUILDER.pop();
        BUILDER.comment("Placable blocks (vanilla blocks with HP) placed and picked up in adventure mode.").push("placables");
    }

    public static final DoubleValue adventureReach = BUILDER.comment("Reach distance for adventure-mode placable block placement/pickup.")
        .defineInRange("reach", 5.0, 1.0, 16.0);
    public static final IntValue adventureCooldownMs = BUILDER.comment("Cooldown (ms) between adventure-mode place/pickup actions.")
        .defineInRange("cooldownMs", 500, 0, 5000);
    public static final BooleanValue placablesNeedSupport = BUILDER.comment(
            "If true, placable blocks inside claims must stand on something: a full top face below",
            "(ground, foundation, floor) or another placable. Stacked placables fall when what holds them is destroyed."
        )
        .define("needSupport", true);

    static {
        BUILDER.pop();
    }

    public static final IntValue maxGroupSize;
    public static final IntValue groupNameMaxLength;
    public static final BooleanValue raidBlockEnabled;
    public static final IntValue raidBlockSeconds;
    public static final IntValue raidBlockExtraRadius;
    public static final BooleanValue raidBlockBuilding;
    public static final BooleanValue raidBlockUpgrading;
    public static final BooleanValue raidBlockMobExplosions;
    public static final IntValue c4Damage;
    public static final DoubleValue c4FuseSeconds;
    public static final DoubleValue c4BlockRadius;
    public static final IntValue explosiveChargeDamage;
    public static final DoubleValue explosiveChargeFuseSeconds;
    public static final DoubleValue explosiveChargeBlockRadius;
    public static final IntValue genericExplosionDamage;
    public static final IntValue genericExplosionRadius;
    public static final IntValue genericExplosionMaxTargets;
    public static final BooleanValue raidBreaksBlocks;
    public static final BooleanValue zombiesAttackBuilds;
    public static final IntValue zombieDamage;
    public static final IntValue zombieAttackCooldown;
    public static final DoubleValue zombieReach;
    public static final IntValue zombieSearchRadius;
    public static final BooleanValue zombiesPathToBuilds;
    public static final BooleanValue zombiesOnlyWhenClaimed;
    public static final BooleanValue decayEnabled;
    public static final BooleanValue decayInactivity;
    public static final BooleanValue decayUnclaimed;
    public static final BooleanValue decayPlacables;
    public static final DoubleValue decayDays;
    public static final IntValue decayHpPerMinute;
    public static final IntValue decayGraceMinutes;
    public static final BooleanValue autoRepairEnabled;
    public static final IntValue repairHpPerMinute;
    /** HeraliZ integration ([heraliz] section). */
    public static final ConfigValue<List<? extends String>> blockedDimensions;
    public static final BooleanValue combatBlocksBuilding;
    public static final ConfigValue<List<? extends String>> heralizC4Ids;
    public static final BooleanValue clansAccess;
    public static final BooleanValue cracRulesOutsideClaims;
    public static final BooleanValue onlyC4Damages;
    public static final ConfigValue<List<? extends String>> protectedDimensions;

    public static int claimRadius(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> claimRadiusL1.get();
            case 2 -> claimRadiusL2.get();
            default -> claimRadiusL3.get();
        };
    }

    public static int claimYRadius(int tier) {
        return switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> claimYRadiusL1.get();
            case 2 -> claimYRadiusL2.get();
            default -> claimYRadiusL3.get();
        };
    }

    public static Item upkeepItem(UpkeepResource res) {
        String id = switch (res) {
            case WOOD -> upkeepItemWood.get();
            case STONE -> upkeepItemStone.get();
            case METAL -> upkeepItemMetal.get();
            case ARMORED -> upkeepItemArmored.get();
        };
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? Items.AIR : item;
    }

    public static int upkeepPerPiece(UpkeepResource res) {
        return switch (res) {
            case WOOD -> upkeepPerPieceWood.get();
            case STONE -> upkeepPerPieceStone.get();
            case METAL -> upkeepPerPieceMetal.get();
            case ARMORED -> upkeepPerPieceArmored.get();
        };
    }

    static {
        BUILDER.push("group");
        maxGroupSize = BUILDER.comment("Maximum number of members in a group.").defineInRange("maxGroupSize", 7, 1, 64);
        groupNameMaxLength = BUILDER.comment("Maximum length of a group name.").defineInRange("groupNameMaxLength", 24, 3, 64);
        BUILDER.pop();
        BUILDER.push("raidBlock");
        raidBlockEnabled = BUILDER.comment(
                "If true, explosive damage to a building piece (C4, charges, explosions) starts raid block for its claim.",
                "Mobs and decay never trigger it."
            )
            .define("enabled", true);
        raidBlockSeconds = BUILDER.comment("Raid block duration in seconds. Default 300 = 5 minutes.").defineInRange("seconds", 300, 1, 86400);
        raidBlockExtraRadius = BUILDER.comment("Extra radius (blocks) beyond the claim radius that the raid block overlay covers.")
            .defineInRange("extraRadius", 10, 0, 128);
        raidBlockBuilding = BUILDER.comment(
                "If true, placing new pieces, and placing, picking up or mining placables, is blocked in a claim under raid block."
            )
            .define("blockBuilding", true);
        raidBlockUpgrading = BUILDER.comment("If true, upgrading pieces is blocked in a claim under raid block.").define("blockUpgrading", true);
        raidBlockMobExplosions = BUILDER.comment(
                "If true, explosions caused by mobs (creepers, ghast fireballs, withers) also start raid block.",
                "If false they still damage pieces, but only player explosives lock the owners out."
            )
            .define("mobExplosions", false);
        BUILDER.pop();
        BUILDER.push("c4");
        c4Damage = BUILDER.comment("HP damage dealt by one C4 charge to the single piece it is stuck to.").defineInRange("damage", 300, 1, 100000);
        c4FuseSeconds = BUILDER.comment("C4 fuse time in seconds.").defineInRange("fuseSeconds", 7.5, 0.5, 300.0);
        c4BlockRadius = BUILDER.comment(
                "Radius (blocks) around the block a C4 is stuck to in which it breaks ordinary blocks inside claims",
                "(see explosions.raidBreaksBlocks). Only when it is stuck to such a block, not to a piece or a placable.",
                "0 = only that block."
            )
            .defineInRange("blockRadius", 1.0, 0.0, 4.0);
        explosiveChargeDamage = BUILDER.comment(
                "HP damage dealt by one Explosive Charge to the piece/placable it is stuck to and to each other one next to it",
                "that the blast can see (once per piece, never through the panel it is stuck to)."
            )
            .defineInRange("chargeDamage", 130, 1, 100000);
        explosiveChargeFuseSeconds = BUILDER.comment("Explosive Charge fuse time in seconds.").defineInRange("chargeFuseSeconds", 3.0, 0.5, 300.0);
        explosiveChargeBlockRadius = BUILDER.comment(
                "Radius (blocks) around the block an Explosive Charge is stuck to in which it breaks ordinary blocks inside claims",
                "(see explosions.raidBreaksBlocks). 0 = only that block."
            )
            .defineInRange("chargeBlockRadius", 0.0, 0.0, 4.0);
        BUILDER.pop();
        BUILDER.push("explosions");
        genericExplosionDamage = BUILDER.comment(
                "HP damage a TNT-sized generic explosion (power 4) deals to every building piece and placable block",
                "it can see in range, once per piece. Scaled by the blast's power (creeper 3, bed 5, end crystal 6...).",
                "Nothing behind a wall is hit. Keep it below what C4 and charges deal per TNT used to craft them",
                "(C4: 100 per TNT, charge: 65 per TNT), so that crafted raid tools stay the efficient way in."
            )
            .defineInRange("genericDamage", 50, 0, 100000);
        genericExplosionRadius = BUILDER.comment(
                "How far from a TNT-sized blast a piece or placable can be and still take damage, scaled by the blast's power.",
                "1 means only the block hit and the ones touching it. Smaller keeps blasts surgical."
            )
            .defineInRange("genericRadius", 2, 0, 32);
        genericExplosionMaxTargets = BUILDER.comment(
                "Cap on how many pieces/placables one blast may damage, nearest first.", "0 means no cap: everything in sight inside the radius is hit."
            )
            .defineInRange("genericMaxTargets", 3, 0, 256);
        raidBreaksBlocks = BUILDER.comment(
                "If true, raid explosives break ordinary blocks inside claims, so a claimed house, bunker or cave wall of",
                "vanilla blocks is no raid shield: C4 and Explosive Charges in their block radius, TNT and other blasts no mob",
                "caused as vanilla would (obsidian resists TNT, not C4).",
                "Never broken this way: natural terrain (#crac:raid_terrain, unless a player put it there), the ground a",
                "foundation stands on, building pieces, placables (they have HP) and the Claim Block. Breaking into a claim",
                "starts its raid block.",
                "Outside claims CRac leaves the block list of a blast to the other mods (only its own blocks are kept out)."
            )
            .define("raidBreaksBlocks", true);
        BUILDER.pop();
        BUILDER.push("zombies");
        zombiesAttackBuilds = BUILDER.comment("If true, hostile mobs chew on building pieces blocking their way.",
                "HeraliZ: off (only C4 damage pieces, see heraliz.onlyC4Damages).").define("attackBuilds", false);
        zombieDamage = BUILDER.comment(
                "HP a single mob hit takes off a piece.", "Deliberately tiny — a horde should be a slow siege, not a demolition crew."
            )
            .defineInRange("damage", 2, 0, 10000);
        zombieAttackCooldown = BUILDER.comment("Ticks between one mob's swings at a piece.").defineInRange("attackCooldownTicks", 20, 1, 1200);
        zombieReach = BUILDER.comment("How far a mob can reach a piece to hit it.").defineInRange("reach", 2.0, 0.5, 8.0);
        zombieSearchRadius = BUILDER.comment("How far a mob looks for a piece to break through.").defineInRange("searchRadius", 12, 1, 64);
        zombiesPathToBuilds = BUILDER.comment("If true, mobs walk towards the piece they picked instead of", "only hitting what they happen to bump into.")
            .define("pathToBuilds", true);
        zombiesOnlyWhenClaimed = BUILDER.comment("If true, only buildings with a Claim Block are attacked,", "so scrap huts out in the wild are left alone.")
            .define("onlyClaimedBases", true);
        BUILDER.pop();
        BUILDER.push("decay");
        decayEnabled = BUILDER.comment(
                "If true, buildings of inactive groups (or whose upkeep is not paid) slowly lose HP.",
                "If false nothing decays, unpaid upkeep included."
            )
            .define("enabled", true);
        decayInactivity = BUILDER.comment(
                "If true, the base of a group whose members stay away for 'days' decays even with its upkeep paid.",
                "False is the Rust rule: only unpaid upkeep (or no Claim Block) makes a base decay."
            )
            .define("inactivity", false);
        decayUnclaimed = BUILDER.comment(
                "If true, buildings without a Claim Block and placable blocks NOT covered by any claim also decay (immediately, no inactivity delay)."
            )
            .define("decayUnclaimed", true);
        decayPlacables = BUILDER.comment(
                "If false, placable blocks (crates, chests, workstations...) never decay,",
                "no matter what happens to the claim around them. Building pieces are unaffected.",
                "Placables that were NEVER inside a claim are always left alone regardless."
            )
            .define("decayPlacables", true);
        decayDays = BUILDER.comment(
                "Days without any group member online before inactivity decay starts.",
                "Counted in server uptime (game time): days the server is down do not count."
            )
            .defineInRange("days", 3.0, 0.01, 365.0);
        decayHpPerMinute = BUILDER.comment("HP lost per minute by each piece of a decaying building.").defineInRange("hpPerMinute", 10, 1, 100000);
        decayGraceMinutes = BUILDER.comment(
                "Pieces placed or upgraded less than this many minutes ago do not decay,",
                "so a fresh twig foundation survives until its Claim Block is placed."
            )
            .defineInRange("graceMinutes", 30, 0, 100000);
        autoRepairEnabled = BUILDER.comment("If true, damaged pieces slowly self-repair while the claim is active and not raid-blocked.")
            .define("autoRepair", true);
        repairHpPerMinute = BUILDER.comment("HP regenerated per minute by each damaged piece.").defineInRange("repairHpPerMinute", 15, 1, 100000);
        BUILDER.pop();
        // The admin console password lives in config/crac-admin.toml (AdminConfig): this file is sent to every client.
        BUILDER.comment("HeraliZ: where CRac works, combat tag, the HeraliZ C4 (heralisz_tactical) and the clans.").push("heraliz");
        blockedDimensions = BUILDER.comment(
                "Dimensions where nothing of CRac can be placed (building pieces, Claim Block, sleeping bags) and where CRac",
                "leaves explosions alone: the match maps and the clan bunkers of heralisz_bases."
            )
            .defineList("blockedDimensions", List.of("heralisz_matchs:arenes", "heralisz_matchs:zombies", "heralisz_bases:bunkers"),
                o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        combatBlocksBuilding = BUILDER.comment(
                "If true, a combat-tagged player (persistentData long heralisz:combat_jusqua, set by HeraliZ Matchs) cannot place,",
                "upgrade, repair or demolish pieces, place a Claim Block or pick up a placable until the tag ends."
            )
            .define("combatBlocksBuilding", true);
        heralizC4Ids = BUILDER.comment(
                "ThrowableId of the heralisz_tactical explosives that count as a C4: the piece they are stuck to takes c4.damage",
                "(like the CRac C4: 1 for wood, 2 for stone, 4 for sheet metal, 7 for armored) instead of a generic blast."
            )
            .defineList("c4ThrowableIds", List.of("heralisz_grenade:c4"), o -> o instanceof String s && !s.isBlank());
        clansAccess = BUILDER.comment(
                "If true and HeraliZ Clans is installed, the clan of a group's owner gets into its claim like the group itself,",
                "each member by the BASE_* permissions of his rank (build, doors, chests, Claim Block)."
            )
            .define("clansAccess", true);
        cracRulesOutsideClaims = BUILDER.comment(
                "If false (HeraliZ), CRac leaves everything outside claims to vanilla and the other mods: blasts keep their block",
                "list (only CRac's own blocks are kept out), a chest, a furnace or a barrel there is a plain block of the map (no HP)",
                "and adventure players only pick up their own sleeping bags there.",
                "True gives CRac's original rules: no world block breaks outside claims, placables have HP and can be picked up",
                "everywhere."
            )
            .define("cracRulesOutsideClaims", false);
        onlyC4Damages = BUILDER.comment(
                "If true (HeraliZ), only C4 damage building pieces and placables: the HeraliZ C4 (c4ThrowableIds), the CRac C4",
                "and the CRac Explosive Charge. Grenades, TNT and every other explosion, mobs (zombies), melee hits, tools, fire,",
                "projectiles and vehicles do nothing to them, and other blasts break nothing inside claims.",
                "Upkeep decay of the Claim Block (decay section) still applies."
            )
            .define("onlyC4Damages", true);
        protectedDimensions = BUILDER.comment(
                "HeraliZ: dimensions where NO explosion breaks any block (C4, grenades, TNT, creepers, airstrikes, other mods...),",
                "e.g. the map while it is being built: [\"minecraft:overworld\", \"heralisz:spawn\"]. Blasts still hurt players",
                "and mobs, and C4 still damage CRac pieces. Empty = off."
            )
            .defineList("protectedDimensions", List.of(), o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
