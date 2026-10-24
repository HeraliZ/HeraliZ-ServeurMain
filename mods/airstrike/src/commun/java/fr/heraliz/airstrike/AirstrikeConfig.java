package fr.heraliz.airstrike;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server configuration ({@code serverconfig/airstrike-server.toml} inside each world). Synced to clients.
 */
public final class AirstrikeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_RANGE;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue STRIKE_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_TOTAL;
    public static final ForgeConfigSpec.BooleanValue CONSUME_PAYLOAD;
    public static final ForgeConfigSpec.BooleanValue TARGET_MARKER;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_NEARBY;

    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue NAPALM_PLACES_FIRE;
    public static final ForgeConfigSpec.DoubleValue BOMB_POWER;
    public static final ForgeConfigSpec.DoubleValue PRECISION_POWER;
    public static final ForgeConfigSpec.DoubleValue BOMBLET_POWER;
    public static final ForgeConfigSpec.DoubleValue GUN_DAMAGE;
    public static final ForgeConfigSpec.IntValue GUN_PASSES;
    public static final ForgeConfigSpec.IntValue GUN_ROUNDS;
    public static final ForgeConfigSpec.IntValue BOMB_COUNT;
    public static final ForgeConfigSpec.IntValue BOMBLET_COUNT;
    public static final ForgeConfigSpec.BooleanValue DEAFENING;

    public static final ForgeConfigSpec.IntValue SMOKE_SECONDS;
    public static final ForgeConfigSpec.IntValue GAS_SECONDS;
    public static final ForgeConfigSpec.IntValue NAPALM_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SMOKE_BLINDS;
    public static final ForgeConfigSpec.BooleanValue SMOKE_BREAKS_AGGRO;
    public static final ForgeConfigSpec.IntValue NOISE_ATTRACTS_MONSTERS;

    public static final ForgeConfigSpec.DoubleValue JET_ALTITUDE;
    public static final ForgeConfigSpec.DoubleValue JET_SPEED;
    public static final ForgeConfigSpec.DoubleValue CARGO_ALTITUDE;
    public static final ForgeConfigSpec.DoubleValue CARGO_SPEED;
    public static final ForgeConfigSpec.BooleanValue FLARES;

    public static final ForgeConfigSpec.ConfigValue<String> SUPPLY_LOOT_TABLE;
    public static final ForgeConfigSpec.IntValue SUPPLY_SMOKE_SECONDS;
    public static final ForgeConfigSpec.IntValue SUPPLY_DESPAWN_MINUTES;

    // ---- hélicoptère de patrouille (événement, 24/10)
    public static final ForgeConfigSpec.BooleanValue HELICO_ACTIF;
    public static final ForgeConfigSpec.BooleanValue HELICO_AUTO;
    public static final ForgeConfigSpec.IntValue HELICO_PREMIER_DELAI;
    public static final ForgeConfigSpec.IntValue HELICO_INTERVALLE_MIN;
    public static final ForgeConfigSpec.IntValue HELICO_INTERVALLE_MAX;
    public static final ForgeConfigSpec.IntValue HELICO_JOUEURS_MIN;
    public static final ForgeConfigSpec.IntValue HELICO_DUREE;
    public static final ForgeConfigSpec.BooleanValue HELICO_ANNONCES;
    public static final ForgeConfigSpec.DoubleValue HELICO_VIE;
    public static final ForgeConfigSpec.DoubleValue HELICO_VITESSE;
    public static final ForgeConfigSpec.IntValue HELICO_HAUTEUR;
    public static final ForgeConfigSpec.IntValue HELICO_HAUTEUR_COMBAT;
    public static final ForgeConfigSpec.DoubleValue HELICO_DEGATS;
    public static final ForgeConfigSpec.IntValue HELICO_RAFALE;
    public static final ForgeConfigSpec.IntValue HELICO_CADENCE;
    public static final ForgeConfigSpec.IntValue HELICO_PAUSE_MIN;
    public static final ForgeConfigSpec.IntValue HELICO_PAUSE_MAX;
    public static final ForgeConfigSpec.DoubleValue HELICO_DISPERSION;
    public static final ForgeConfigSpec.IntValue HELICO_PORTEE_TIR;
    public static final ForgeConfigSpec.IntValue HELICO_MEMOIRE;
    public static final ForgeConfigSpec.IntValue HELICO_VISEE;
    public static final ForgeConfigSpec.BooleanValue HELICO_TIRER_BOTS;
    public static final ForgeConfigSpec.DoubleValue HELICO_PUISSANCE_CRASH;
    public static final ForgeConfigSpec.IntValue HELICO_FEU_MIN;
    public static final ForgeConfigSpec.IntValue HELICO_FEU_MAX;
    public static final ForgeConfigSpec.IntValue HELICO_EPAVE_MINUTES;
    public static final ForgeConfigSpec.IntValue HELICO_FOUILLE;
    public static final ForgeConfigSpec.ConfigValue<String> HELICO_COFFRE_T7;
    public static final ForgeConfigSpec.ConfigValue<String> HELICO_TABLE_T6;
    public static final ForgeConfigSpec.IntValue HELICO_PORTEE_SUIVI;

    /** Version of the defaults the file was last upgraded to (see {@link #migrate()}). */
    public static final ForgeConfigSpec.IntValue CONFIG_VERSION;
    private static final int CURRENT_VERSION = 3;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Designator (binoculars)").push("designator");
        MAX_RANGE = b.comment("Maximum designation range in blocks.").defineInRange("maxRange", 150, 16, 1024);
        COOLDOWN_SECONDS = b.comment("Cooldown of the binoculars after calling a strike (seconds).")
                .defineInRange("cooldownSeconds", 30, 0, 3600);
        STRIKE_DELAY_SECONDS = b.comment("Delay between the designation and the aircraft passing over the target (seconds).")
                .defineInRange("strikeDelaySeconds", 12, 6, 120);
        MAX_ACTIVE_PER_PLAYER = b.comment("Maximum number of simultaneous strikes called by one player.")
                .defineInRange("maxActivePerPlayer", 1, 1, 16);
        MAX_ACTIVE_TOTAL = b.comment("Maximum number of simultaneous strikes on the server.")
                .defineInRange("maxActiveTotal", 6, 1, 64);
        CONSUME_PAYLOAD = b.comment("Consume one payload item per strike (never in creative).").define("consumePayload", true);
        TARGET_MARKER = b.comment("Pop a coloured signal smoke on the target when a strike is called (warns everybody).")
                .define("targetMarker", true);
        ANNOUNCE_NEARBY = b.comment("Warn players close to the target (60 blocks) with a radio message.")
                .define("announceNearby", false);
        b.pop();

        // power by cartridge level: Light (strafing run, smoke) < Heavy (bombing, cluster, gas) < Special (guided bomb, napalm)
        b.comment("Damage").push("damage");
        DESTROY_BLOCKS = b.comment("Bombs destroy blocks (set to false for base-protected servers).").define("destroyBlocks", true);
        NAPALM_PLACES_FIRE = b.comment("Napalm places fire blocks (only if destroyBlocks is true).").define("napalmPlacesFire", true);
        BOMB_POWER = b.comment("Explosion power of each bomb of a bombing run (TNT = 4).").defineInRange("bombPower", 3.5, 0.0, 12.0);
        PRECISION_POWER = b.comment("Explosion power of the guided bomb.").defineInRange("precisionPower", 7.0, 0.0, 16.0);
        BOMBLET_POWER = b.comment("Explosion power of each cluster bomblet.").defineInRange("bombletPower", 1.8, 0.0, 8.0);
        GUN_DAMAGE = b.comment("Damage of each cannon round (strafing run).").defineInRange("gunDamage", 4.0, 0.0, 100.0);
        GUN_PASSES = b.comment("Strafing passes of a gun run (1, or 2 with a turn back over the target).")
                .defineInRange("gunPasses", 1, 1, 2);
        GUN_ROUNDS = b.comment("Cannon rounds fired by each strafing pass.").defineInRange("gunRounds", 60, 10, 200);
        BOMB_COUNT = b.comment("Bombs dropped by a bombing run.").defineInRange("bombCount", 6, 1, 8);
        BOMBLET_COUNT = b.comment("Bomblets released by the cluster bomb.").defineInRange("bombletCount", 16, 4, 64);
        DEAFENING = b.comment("Players close to an explosion hear their ears ring and the world muffled for a few seconds.")
                .define("deafening", true);
        b.pop();

        b.comment("Area effects").push("areas");
        SMOKE_SECONDS = b.comment("Duration of the smoke screen (seconds).").defineInRange("smokeSeconds", 30, 5, 600);
        GAS_SECONDS = b.comment("Duration of the toxic gas cloud (seconds).").defineInRange("gasSeconds", 25, 5, 600);
        NAPALM_SECONDS = b.comment("Duration of the napalm fire (seconds).").defineInRange("napalmSeconds", 20, 2, 120);
        SMOKE_BLINDS = b.comment("Players inside the smoke screen are blinded.").define("smokeBlinds", true);
        SMOKE_BREAKS_AGGRO = b.comment("Monsters lose their target when it hides inside the smoke.").define("smokeBreaksAggro", true);
        NOISE_ATTRACTS_MONSTERS = b.comment("Explosions and cannon fire attract idle monsters (zombies...) within this radius, 0 = off.")
                .defineInRange("noiseAttractsMonsters", 48, 0, 256);
        b.pop();

        b.comment("Aircraft").push("aircraft");
        JET_ALTITUDE = b.comment("Altitude of the attack jet above the target (blocks).").defineInRange("jetAltitude", 44.0, 20.0, 200.0);
        JET_SPEED = b.comment("Speed of the attack jet (blocks per tick).").defineInRange("jetSpeed", 3.0, 0.5, 8.0);
        CARGO_ALTITUDE = b.comment("Altitude of the cargo plane above the target (blocks).").defineInRange("cargoAltitude", 56.0, 20.0, 200.0);
        CARGO_SPEED = b.comment("Speed of the cargo plane (blocks per tick).").defineInRange("cargoSpeed", 1.9, 0.5, 6.0);
        FLARES = b.comment("Aircraft release decoy flares on their way out.").define("flares", true);
        b.pop();

        b.comment("Supply drop").push("supply");
        SUPPLY_LOOT_TABLE = b.comment("Loot table used to fill the supply crate.")
                .define("lootTable", "airstrike:chests/supply_drop");
        SUPPLY_SMOKE_SECONDS = b.comment("Signal smoke duration once the crate has landed (seconds).")
                .defineInRange("signalSmokeSeconds", 90, 0, 3600);
        SUPPLY_DESPAWN_MINUTES = b.comment("Crates disappear after this many minutes (0 = never).")
                .defineInRange("despawnMinutes", 20, 0, 10000);
        b.pop();

        b.comment("Patrol helicopter event (/helico): flies over the map, fires back at whoever shoots it, crashes with its loot.")
                .push("helico");
        HELICO_ACTIF = b.comment("Helicopter event enabled (automatic launches and /helico lancer).").define("actif", true);
        HELICO_AUTO = b.comment("Launch automatically on a timer (otherwise only /helico lancer).").define("auto", true);
        HELICO_PREMIER_DELAI = b.comment("Minutes after the server start before the first automatic helicopter.")
                .defineInRange("premierDelaiMinutes", 60, 1, 10000);
        HELICO_INTERVALLE_MIN = b.comment("Minimum minutes between two automatic helicopters (counted from the end of the previous one).")
                .defineInRange("intervalleMinMinutes", 120, 5, 10000);
        HELICO_INTERVALLE_MAX = b.comment("Maximum minutes between two automatic helicopters.")
                .defineInRange("intervalleMaxMinutes", 180, 5, 10000);
        HELICO_JOUEURS_MIN = b.comment("Players in survival on the map (overworld) needed for an automatic launch.")
                .defineInRange("joueursMin", 5, 0, 500);
        HELICO_DUREE = b.comment("Minutes of patrol before the helicopter leaves the map if it is not shot down.")
                .defineInRange("dureeMinutes", 20, 1, 600);
        HELICO_ANNONCES = b.comment("Tell the players on the map when it arrives, is shot down or leaves.").define("annonces", true);
        HELICO_VIE = b.comment("Health of the helicopter (TaCZ bullets and explosions; melee does nothing).")
                .defineInRange("vie", 1500.0, 10.0, 100000.0);
        HELICO_VITESSE = b.comment("Patrol speed (blocks per tick).").defineInRange("vitesse", 0.55, 0.1, 2.0);
        HELICO_HAUTEUR = b.comment("Patrol height above the ground (blocks).").defineInRange("hauteur", 34, 12, 120);
        HELICO_HAUTEUR_COMBAT = b.comment("Height above its target while it attacks (blocks).").defineInRange("hauteurCombat", 24, 8, 120);
        HELICO_DEGATS = b.comment("Damage of each machine gun round (a real projectile: armour protects).")
                .defineInRange("degatsBalle", 3.0, 0.0, 100.0);
        HELICO_RAFALE = b.comment("Rounds per burst.").defineInRange("ballesParRafale", 10, 1, 100);
        HELICO_CADENCE = b.comment("Ticks between two rounds of a burst.").defineInRange("cadenceTicks", 2, 1, 20);
        HELICO_PAUSE_MIN = b.comment("Minimum ticks between two bursts.").defineInRange("pauseMinTicks", 40, 5, 600);
        HELICO_PAUSE_MAX = b.comment("Maximum ticks between two bursts.").defineInRange("pauseMaxTicks", 70, 5, 600);
        HELICO_DISPERSION = b.comment("Spread of the rounds (degrees).").defineInRange("dispersionDegres", 2.5, 0.0, 30.0);
        HELICO_PORTEE_TIR = b.comment("Range of the machine gun (blocks).").defineInRange("porteeTir", 90, 16, 256);
        HELICO_MEMOIRE = b.comment("Seconds the helicopter keeps hunting a player who shot it.").defineInRange("memoireSecondes", 30, 5, 600);
        HELICO_VISEE = b.comment("A player aiming at it with a gun for this many seconds becomes a target too (0 = off).")
                .defineInRange("viseeSecondes", 4, 0, 60);
        HELICO_TIRER_BOTS = b.comment("Also fire at the load test bots (/bots).").define("tirerSurBots", false);
        HELICO_PUISSANCE_CRASH = b.comment("Power of the crash explosion (hurts players, never breaks blocks).")
                .defineInRange("puissanceCrash", 4.0, 0.0, 10.0);
        HELICO_FEU_MIN = b.comment("Minimum seconds the wreck burns (crates and parts locked meanwhile).").defineInRange("feuMinSecondes", 60, 0, 3600);
        HELICO_FEU_MAX = b.comment("Maximum seconds the wreck burns.").defineInRange("feuMaxSecondes", 150, 0, 3600);
        HELICO_EPAVE_MINUTES = b.comment("Minutes the wreck, its crates and the T7 chest stay once the fire is out.")
                .defineInRange("epaveMinutes", 10, 1, 600);
        HELICO_FOUILLE = b.comment("Seconds of searching (right click held, standing still) to take a part from the wreck.")
                .defineInRange("fouilleSecondes", 8, 1, 120);
        HELICO_COFFRE_T7 = b.comment("HeraliZ Loot T7 chest left at the crash (t7_coffre_armes, t7_largage, t7_coffre_fort...).")
                .define("coffreT7", "t7_coffre_armes");
        HELICO_TABLE_T6 = b.comment("HeraliZ Loot table of the two helicopter crates.").define("tableT6", "t6");
        HELICO_PORTEE_SUIVI = b.comment("Players this close (blocks) see and hear the helicopter, even beyond their view distance.")
                .defineInRange("porteeSuivi", 500, 64, 2000);
        b.pop();

        CONFIG_VERSION = b.comment("Internal, do not edit: lets new versions of the mod update values left at their old defaults.")
                .defineInRange("configVersion", 1, 1, 1000);

        SPEC = b.build();
    }

    private AirstrikeConfig() {
    }

    /**
     * Brings an existing config file to the current defaults: values the admin never touched (still equal to an
     * older default) are replaced, customised values are kept. Called once the server config is loaded.
     */
    public static void migrate() {
        int version = CONFIG_VERSION.get();
        if (version >= CURRENT_VERSION) return;
        if (version < 2) {
            upgrade(MAX_RANGE, 320, 150);
            upgrade(SMOKE_SECONDS, 45, 30);
            upgrade(GAS_SECONDS, 40, 30);
            upgrade(NAPALM_SECONDS, 14, 30);
            upgrade(PRECISION_POWER, 7.0, 9.5);
        }
        if (version < 3) {
            // cartridge levels: power brought in line with the level of each strike
            upgrade(BOMB_POWER, 4.5, 3.5);
            upgrade(PRECISION_POWER, 9.5, 7.0);
            upgrade(BOMBLET_POWER, 2.0, 1.8);
            upgrade(GUN_DAMAGE, 9.0, 4.0);
            upgrade(BOMB_COUNT, 8, 6);
            upgrade(BOMBLET_COUNT, 24, 16);
            upgrade(GAS_SECONDS, 30, 25);
            upgrade(NAPALM_SECONDS, 30, 20);
        }
        CONFIG_VERSION.set(CURRENT_VERSION);
        SPEC.save();
        AirstrikeMod.LOGGER.info("Airstrike config upgraded from version {} to {}", version, CURRENT_VERSION);
    }

    private static <T> void upgrade(ForgeConfigSpec.ConfigValue<T> value, T oldDefault, T newDefault) {
        if (oldDefault.equals(value.get())) value.set(newDefault);
    }
}
