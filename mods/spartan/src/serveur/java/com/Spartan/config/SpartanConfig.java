package com.Spartan.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration de Spartan (fichier config/spartan-common.toml du serveur).
 *
 * Type COMMON et non SERVER : Forge synchronise les configs SERVER vers chaque
 * client à la connexion. Or ce fichier contient l'URL du webhook Discord et les
 * seuils de détection, qui ne doivent jamais arriver chez un tricheur.
 * Les détections tournent côté serveur : seule la copie du serveur compte.
 */
public class SpartanConfig {
    public static final ForgeConfigSpec SPEC;

    // --- général ---
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue ALERT_ONLY;
    public static final ForgeConfigSpec.IntValue MIN_VL_ALERT;
    public static final ForgeConfigSpec.IntValue ACTION_VL;
    public static final ForgeConfigSpec.IntValue VL_DECAY_SECONDS;

    // --- alertes ---
    public static final ForgeConfigSpec.BooleanValue ALERT_INGAME;
    public static final ForgeConfigSpec.BooleanValue ALERT_LOGFILE;
    public static final ForgeConfigSpec.IntValue ALERT_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue DISCORD_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_WEBHOOK;
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_SANCTIONS_WEBHOOK;

    // --- preuves (boîte noire) ---
    public static final ForgeConfigSpec.BooleanValue EVIDENCE_ENABLED;
    public static final ForgeConfigSpec.IntValue EVIDENCE_TICKS;
    public static final ForgeConfigSpec.IntValue EVIDENCE_MAX_FILES;

    // --- détections ---
    public static final ForgeConfigSpec.BooleanValue CHECK_FLY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLY_HOVER_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLY_EXEMPT_KEYWORDS;
    public static final ForgeConfigSpec.DoubleValue FLY_TOLERANCE;
    public static final ForgeConfigSpec.DoubleValue FLY_GEAR_MIN_FALL_PER_SECOND;
    public static final ForgeConfigSpec.BooleanValue CHECK_VERTICAL;
    public static final ForgeConfigSpec.BooleanValue CHECK_NOFALL;
    public static final ForgeConfigSpec.BooleanValue NOFALL_CORRECT;
    public static final ForgeConfigSpec.BooleanValue CHECK_NOSLOW;
    public static final ForgeConfigSpec.DoubleValue NOSLOW_TOLERANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NOSLOW_IGNORED_ITEMS;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_TOLERANCE;
    public static final ForgeConfigSpec.BooleanValue CHECK_TIMER;
    public static final ForgeConfigSpec.BooleanValue TRANSACTIONS;
    public static final ForgeConfigSpec.BooleanValue CHECK_VELOCITY;
    public static final ForgeConfigSpec.BooleanValue CHECK_BADPACKETS;
    public static final ForgeConfigSpec.BooleanValue CHECK_SPEED;
    public static final ForgeConfigSpec.DoubleValue SPEED_TOLERANCE;
    public static final ForgeConfigSpec.BooleanValue CHECK_REACH;
    public static final ForgeConfigSpec.DoubleValue REACH_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CHECK_KILLAURA;
    public static final ForgeConfigSpec.DoubleValue KILLAURA_MAX_ANGLE;
    public static final ForgeConfigSpec.BooleanValue CHECK_AUTOCLICKER;
    public static final ForgeConfigSpec.IntValue AUTOCLICKER_MAX_CPS;
    public static final ForgeConfigSpec.BooleanValue CHECK_NUKER;
    public static final ForgeConfigSpec.IntValue NUKER_MAX_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue CHECK_FASTBREAK;
    public static final ForgeConfigSpec.DoubleValue FASTBREAK_RATIO;
    public static final ForgeConfigSpec.BooleanValue CHECK_EFFECTS;
    public static final ForgeConfigSpec.IntValue EFFECT_MAX_LEVEL;
    public static final ForgeConfigSpec.IntValue EFFECT_MAX_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_EFFECTS;

    // --- intégrité du client ---
    public static final ForgeConfigSpec.BooleanValue SILENT_CLIENT;
    public static final ForgeConfigSpec.BooleanValue CHECK_HANDSHAKE;
    public static final ForgeConfigSpec.IntValue HANDSHAKE_TIMEOUT;
    public static final ForgeConfigSpec.BooleanValue CHECK_CLIENT_INTEGRITY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_HARDENED_JVM;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_JAVA_AGENTS;
    public static final ForgeConfigSpec.BooleanValue CHECK_BUILD;
    public static final ForgeConfigSpec.BooleanValue CHECK_CHALLENGE;
    public static final ForgeConfigSpec.IntValue CHALLENGE_MIN_SECONDS;
    public static final ForgeConfigSpec.IntValue CHALLENGE_MAX_SECONDS;
    public static final ForgeConfigSpec.IntValue CHALLENGE_TIMEOUT_SECONDS;

    // --- launcher officiel ---
    public static final ForgeConfigSpec.BooleanValue LAUNCHER_TOKEN_REQUIRED;
    public static final ForgeConfigSpec.ConfigValue<String> LAUNCHER_SECRET;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_MODS_HASHES;

    // --- correction instantanée ---
    public static final ForgeConfigSpec.BooleanValue MITIGATE_MOVEMENT;
    public static final ForgeConfigSpec.BooleanValue MITIGATE_COMBAT;
    public static final ForgeConfigSpec.IntValue COMBAT_LOCK_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MITIGATE_BLOCKS;
    public static final ForgeConfigSpec.IntValue BLOCK_LOCK_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MITIGATE_EFFECTS;

    // --- bans ---
    public static final ForgeConfigSpec.IntValue IP_BAN_HOURS;
    public static final ForgeConfigSpec.BooleanValue ALERT_SAME_IP;
    public static final ForgeConfigSpec.IntValue DATA_RETENTION_DAYS;

    // --- ressources (packs de texture / shaders) ---
    public static final ForgeConfigSpec.BooleanValue BLOCK_RESOURCE_PACKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_RESOURCE_PACKS;
    public static final ForgeConfigSpec.BooleanValue BLOCK_SHADERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_SHADERPACKS;

    // --- scan des mods ---
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLISTED_MODS;

    // --- contrôle d'intégrité du client (jars, injection) ---
    public static final ForgeConfigSpec.BooleanValue SCAN_ENABLED;
    public static final ForgeConfigSpec.IntValue SCAN_FIRST_MIN;
    public static final ForgeConfigSpec.IntValue SCAN_FIRST_MAX;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_MIN;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL_MAX;
    public static final ForgeConfigSpec.IntValue SCAN_TIMEOUT;
    public static final ForgeConfigSpec.IntValue SCAN_RENDER_SAMPLES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCAN_MIXIN_WATCH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCAN_CLASS_PROBES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCAN_PROCESS_PATTERNS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SCAN_NATIVE_PATTERNS;

    // --- anti-ESP / anti-wallhack ---
    public static final ForgeConfigSpec.BooleanValue ESP_ENABLED;
    public static final ForgeConfigSpec.IntValue ESP_THREADS;
    public static final ForgeConfigSpec.IntValue ESP_BUDGET_MICROS;
    public static final ForgeConfigSpec.IntValue ESP_HIDE_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue ESP_FAIL_OPEN_TICKS;
    public static final ForgeConfigSpec.DoubleValue ESP_MIN_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue ESP_PEEK;
    public static final ForgeConfigSpec.DoubleValue ESP_JUMP;
    public static final ForgeConfigSpec.BooleanValue ESP_ALLIES;
    public static final ForgeConfigSpec.BooleanValue ESP_BYPASS_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue ESP_STAFF_SEE_ALL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ESP_OPAQUE_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue ESP_REVEAL_SHOTS;
    public static final ForgeConfigSpec.IntValue ESP_SHOT_REVEAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue ESP_REVEAL_VOICE;
    public static final ForgeConfigSpec.IntValue ESP_VOICE_REVEAL_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Spartan AntiCheat — configuration serveur").push("general");
        ENABLED = b.comment("Active l'anti-cheat.").define("enabled", true);
        ALERT_ONLY = b.comment(
                "Mode alerte seulement : aucune sanction automatique, seulement des alertes.",
                "FORTEMENT recommandé au début, le temps de régler les seuils sur votre modpack.")
                .define("alertOnly", true);
        MIN_VL_ALERT = b.comment("Niveau de violation à partir duquel une alerte est émise.")
                .defineInRange("minViolationLevelToAlert", 1, 1, 1000);
        ACTION_VL = b.comment("Niveau de violation à partir duquel une sanction tombe (si alertOnly=false).")
                .defineInRange("violationLevelForAction", 10, 1, 1000);
        VL_DECAY_SECONDS = b.comment(
                "Décroissance : le niveau de violation perd 1 point tous les N secondes sans nouvelle violation.",
                "Évite qu'un faux positif rare s'accumule sur une longue session. 0 = pas de décroissance.")
                .defineInRange("violationDecaySeconds", 30, 0, 86400);
        b.pop();

        b.push("alerts");
        ALERT_INGAME = b.comment("Envoie les alertes aux membres du staff connectés (op niveau 2+).")
                .define("inGameStaff", true);
        ALERT_LOGFILE = b.comment("Écrit les alertes dans config/spartan/alerts.log.")
                .define("logFile", true);
        ALERT_COOLDOWN = b.comment("Délai minimum (secondes) entre deux alertes pour un même joueur+détection.")
                .defineInRange("cooldownSeconds", 5, 0, 3600);
        DISCORD_ENABLED = b.comment("Envoie les alertes vers un webhook Discord (salon privé admins).")
                .define("discordEnabled", false);
        DISCORD_WEBHOOK = b.comment("URL du webhook Discord. Laisser vide si discordEnabled=false.")
                .define("discordWebhookUrl", "");
        DISCORD_SANCTIONS_WEBHOOK = b.comment("Salon PUBLIC des sanctions (facultatif) : chaque ban y est annoncé avec le",
                        "pseudo, la durée et la raison seulement (jamais d'IP, de PC ni de détail de détection). Vide = rien.")
                .define("discordSanctionsWebhookUrl", "");
        b.pop();

        b.push("evidence");
        EVIDENCE_ENABLED = b.comment("Enregistre une « boîte noire » des actions du joueur pour les alertes.")
                .define("enabled", true);
        EVIDENCE_TICKS = b.comment("Nombre de ticks conservés (20 ticks = 1 seconde).")
                .defineInRange("ticks", 200, 20, 2000);
        EVIDENCE_MAX_FILES = b.comment("Nombre max de fichiers de preuve conservés (les plus anciens sont supprimés).")
                .defineInRange("maxFiles", 500, 10, 100000);
        b.pop();

        b.push("checks");
        CHECK_FLY = b.comment("Détection de vol / hovering (côté serveur).").define("fly", true);
        FLY_HOVER_ITEMS = b.comment(
                "Identifiants exacts d'items qui permettent de VOLER sur place ou de monter (jetpacks…)",
                "de VOTRE modpack. Porté, tenu en main ou dans un slot Curios : Fly, Vertical et NoFall",
                "ignorent le joueur (Speed aussi tant qu'il est en l'air).")
                .defineList("flyHoverItems",
                        List.of("mekanism:jetpack", "mekanism:armored_jetpack"),
                        o -> o instanceof String);
        FLY_EXEMPT_KEYWORDS = b.comment(
                "Mots-clés d'items qui RALENTISSENT seulement la chute (parachute, planeur) : tout item dont",
                "l'identifiant contient un de ces mots. Porté, tenu ou en slot Curios : Fly tolère une descente",
                "très lente (flyGearMinFallPerSecond) mais jamais un vol stationnaire ni une montée. Un jetpack",
                "doit être mis dans flyHoverItems (identifiant exact), pas ici.")
                .defineList("flyExemptKeywords",
                        List.of("parachute", "glider"),
                        o -> o instanceof String);
        FLY_TOLERANCE = b.comment(
                "Fly : en l'air, un joueur suit la gravité de vanilla (trajectoire maximale recalculée depuis le",
                "dernier appui, reculs et explosions compris). Dépassement cumulé toléré sur ~1 s (blocs) avant",
                "signalement : planer, voler ou flotter le dépasse en moins d'une seconde ; un bloc fantôme",
                "corrigé avec le ping reste en dessous.")
                .defineInRange("flyToleranceBlocks", 1.0, 0.3, 5.0);
        FLY_GEAR_MIN_FALL_PER_SECOND = b.comment(
                "Descente minimale (blocs/s) avec un équipement de flyExemptKeywords (parachute, planeur).")
                .defineInRange("flyGearMinFallPerSecond", 0.05, 0.0, 5.0);
        CHECK_VERTICAL = b.comment(
                "Déplacements verticaux impossibles : Step (monter un bloc entier d'un coup), HighJump",
                "(sauter plus haut que possible), AirJump (re-sauter en plein air). Désactiver si un mod",
                "du modpack donne des doubles sauts ou du parkour (ex. ParCool).")
                .define("vertical", true);
        CHECK_NOFALL = b.comment("NoFall : se déclarer au sol en pleine chute pour éviter les dégâts.")
                .define("noFall", true);
        NOFALL_CORRECT = b.comment(
                "Le serveur refuse une déclaration « au sol » faite en plein air (aucun bloc ni entité sous le",
                "joueur) : les dégâts de chute s'appliquent normalement à l'atterrissage. NoFall ne sert plus à rien.")
                .define("noFallCorrection", true);
        VERTICAL_TOLERANCE = b.comment(
                "Montées impossibles (saut en l'air, saut trop haut, marche géante, escalade de mur) : dépassement",
                "cumulé toléré sur ~1 s (blocs). Une seule montée de plus de 0,35 bloc au-delà du maximum est",
                "signalée immédiatement.")
                .defineInRange("verticalToleranceBlocks", 0.4, 0.1, 5.0);
        CHECK_NOSLOW = b.comment(
                "NoSlow : courir à pleine vitesse en mangeant, buvant, se soignant, bandant un arc… (vanilla",
                "ralentit à 20 %). Vérifié seulement quand l'utilisation est certaine malgré le ping.")
                .define("noSlow", true);
        NOSLOW_TOLERANCE = b.comment("NoSlow : dépassement cumulé toléré sur ~1 s (blocs).")
                .defineInRange("noSlowToleranceBlocks", 0.5, 0.1, 5.0);
        NOSLOW_IGNORED_ITEMS = b.comment(
                "Objets qui ne ralentissent PAS dans votre modpack quand on les utilise (identifiants exacts).",
                "À compléter si un objet de mod déclenche NoSlow à tort.")
                .defineList("noSlowIgnoredItems", List.of(), o -> o instanceof String);
        CHECK_TIMER = b.comment(
                "Timer : jeu accéléré côté client (plus de 20 ticks par seconde). Avec les transactions, la",
                "cadence est bornée par l'horloge du serveur via les pings : ×2 vu en moins d'une seconde,",
                "×1,05 en une vingtaine de secondes, sans crédit à accumuler en restant immobile.")
                .define("timer", true);
        TRANSACTIONS = b.comment(
                "Transactions : un ping vanilla (ClientboundPingPacket) par tick et par joueur, auquel tout",
                "client répond. On sait alors EXACTEMENT quand le client a reçu un recul, une explosion ou un",
                "changement d'effet, quel que soit son lag : moins de faux positifs avec un mauvais réseau, et",
                "Timer / anti-recul précis. Identifiants 0x535xxxxx, ceux des autres mods/plugins sont ignorés.",
                "Quelques octets par joueur et par tick. À désactiver seulement si un autre plugin d'anticheat",
                "s'en plaint.")
                .define("transactions", true);
        CHECK_VELOCITY = b.comment(
                "Velocity (anti-recul / AntiKB) : le client a forcément appliqué un recul (il a répondu au ping",
                "qui le suivait) mais aucune de ses positions ne le montre, alors que rien ne le bloquait.",
                "Signalé quand au moins 4 des 8 derniers reculs jugés sont ignorés. Signalement seul (pas de",
                "renvoi). Nécessite les transactions.")
                .define("velocity", true);
        CHECK_BADPACKETS = b.comment(
                "Paquets impossibles : angle de vue hors limites, positions retenues (blink),",
                "téléportation jamais confirmée, inondation de paquets, pings ignorés 30 s alors que le",
                "client bouge (latence simulée), réponses à des pings jamais envoyés.")
                .define("badPackets", true);

        CHECK_SPEED = b.comment("Détection de vitesse horizontale anormale (côté serveur).")
                .define("speed", true);
        SPEED_TOLERANCE = b.comment(
                "La vitesse maximale est recalculée à chaque tick avec la physique de vanilla : vitesse du",
                "joueur (effets, objets et armures de mods compris), sprint, sauts, glace, plafond bas, reculs et",
                "explosions avec leur valeur exacte, poussée des mobs. On ne peut pas accumuler d'élan en trichant",
                "un peu à chaque tick. Dépassement cumulé toléré sur ~1 s (blocs) avant signalement.")
                .defineInRange("speedToleranceBlocks", 0.5, 0.1, 5.0);

        CHECK_REACH = b.comment("Détection de portée d'attaque anormale (reach).").define("reach", true);
        REACH_DISTANCE = b.comment(
                "Portée max (blocs) entre l'œil de l'attaquant et la hitbox de la cible.",
                "Vanilla ~3.0. Une marge liée au ping du joueur est ajoutée automatiquement.")
                .defineInRange("reachDistance", 3.3, 2.5, 10.0);

        CHECK_KILLAURA = b.comment("Détection d'attaques hors du champ de vision (killaura).")
                .define("killaura", true);
        KILLAURA_MAX_ANGLE = b.comment("Angle max (degrés) entre le regard et la hitbox de la cible frappée.")
                .defineInRange("killauraMaxAngle", 60.0, 20.0, 180.0);

        CHECK_AUTOCLICKER = b.comment("Détection de clics trop rapides ou trop réguliers (autoclicker).")
                .define("autoclicker", true);
        AUTOCLICKER_MAX_CPS = b.comment("Nombre max de coups par seconde.")
                .defineInRange("autoclickerMaxCps", 20, 5, 100);

        CHECK_NUKER = b.comment("Détection de blocs cassés en masse (nuker).").define("nuker", true);
        NUKER_MAX_PER_TICK = b.comment("Nombre max de blocs cassés dans un même tick (un humain : 1).")
                .defineInRange("nukerMaxPerTick", 3, 2, 100);

        CHECK_FASTBREAK = b.comment("Détection de speedmine / fast-break (temps de minage trop court).")
                .define("fastbreak", true);
        FASTBREAK_RATIO = b.comment(
                "Signale si un bloc casse en moins de cette fraction du temps attendu (outil, enchantements",
                "et effets pris en compte), plusieurs fois de suite. Vanilla refuse déjà en dessous de 0.7.")
                .defineInRange("fastbreakRatio", 0.85, 0.5, 1.0);

        CHECK_EFFECTS = b.comment(
                "Détection d'effets illégaux (niveau ou durée impossibles). Le staff (op 2+) est ignoré.")
                .define("illegalEffects", true);
        EFFECT_MAX_LEVEL = b.comment("Niveau d'effet maximum autorisé (vanilla : II via potions/balises).")
                .defineInRange("effectMaxLevel", 5, 1, 255);
        EFFECT_MAX_MINUTES = b.comment("Durée maximum autorisée d'un effet, en minutes (Mauvais présage vanilla : 100).")
                .defineInRange("effectMaxMinutes", 120, 1, 100000);
        IGNORED_EFFECTS = b.comment(
                "Effets jamais signalés (ex : effets permanents donnés par l'armure d'un mod).")
                .defineList("ignoredEffects", List.of("minecraft:bad_omen", "minecraft:hero_of_the_village"),
                        o -> o instanceof String);
        b.pop();

        b.push("integrity");
        SILENT_CLIENT = b.comment(
                "Détection silencieuse : tout ce qui vient du jeu du joueur (jars, injection, défis, handshake,",
                "launcher, packs, shaders…) donne une ALERTE au staff (Discord + en jeu) avec les preuves, mais",
                "JAMAIS d'expulsion ni de ban automatique, et rien n'est affiché au joueur. Le staff le surveille",
                "en spectateur puis décide. false = ancien comportement (expulsion si alertOnly=false).")
                .define("silentClientDetections", true);
        CHECK_HANDSHAKE = b.comment(
                "Un client honnête envoie toujours ses infos (mods, identifiant) juste après la connexion.",
                "S'il ne le fait pas dans le délai, son client a probablement été modifié.")
                .define("handshake", true);
        HANDSHAKE_TIMEOUT = b.comment(
                "Délai (secondes) pour recevoir les infos du client. Un client chargé de 100 mods peut",
                "rester figé longtemps juste après la connexion : ne pas descendre trop bas.")
                .defineInRange("handshakeTimeoutSeconds", 90, 10, 600);
        CHECK_CLIENT_INTEGRITY = b.comment(
                "Le client signale des traces d'injection (agents Java, threads anonymes).",
                "Best-effort : un client modifié peut ne rien signaler.")
                .define("clientIntegrity", true);
        ALLOWED_JAVA_AGENTS = b.comment(
                "Agents Java autorisés (fragment du chemin). Ex : authlib-injector utilisé par certains launchers.")
                .defineList("allowedJavaAgents", List.of("authlib-injector"), o -> o instanceof String);
        REQUIRE_HARDENED_JVM = b.comment(
                "Exige que le jeu soit lancé avec les protections anti-injection",
                "(-XX:+DisableAttachMechanism). Le launcher officiel les ajoute : n'activer qu'une",
                "fois le launcher distribué, sinon tous les joueurs seraient signalés. Voir docs/LAUNCHER.md.")
                .define("requireHardenedJvm", false);
        CHECK_BUILD = b.comment(
                "Vérifie que le mod du joueur est EXACTEMENT le même build que celui du serveur.",
                "Sinon : expulsion avec consigne de relancer le launcher (pas une alerte de triche).")
                .define("buildCheck", true);
        CHECK_CHALLENGE = b.comment(
                "Défi d'intégrité tournant : à intervalle aléatoire, le client doit prouver que son mod",
                "n'a pas été modifié. Chaque défi est différent (nombre, variante et classes aléatoires).")
                .define("challenge", true);
        CHALLENGE_MIN_SECONDS = b.comment("Intervalle minimum entre deux défis (secondes).")
                .defineInRange("challengeMinSeconds", 60, 10, 3600);
        CHALLENGE_MAX_SECONDS = b.comment("Intervalle maximum entre deux défis (secondes).")
                .defineInRange("challengeMaxSeconds", 240, 10, 7200);
        CHALLENGE_TIMEOUT_SECONDS = b.comment("Délai de réponse à un défi (secondes).")
                .defineInRange("challengeTimeoutSeconds", 20, 5, 120);
        b.pop();

        b.comment("Launcher officiel : jeton de lancement signé + empreinte du dossier mods.",
                "Voir docs/LAUNCHER.md pour l'intégration côté launcher.")
                .push("launcher");
        LAUNCHER_SECRET = b.comment(
                "Secret partagé avec le launcher (ou son serveur d'authentification) pour signer les jetons.",
                "Vide = vérification désactivée. Au moins 32 caractères aléatoires. NE JAMAIS LE PUBLIER.")
                .define("launcherSecret", "");
        LAUNCHER_TOKEN_REQUIRED = b.comment(
                "false : un jeton absent/invalide donne une ALERTE seulement.",
                "true  : connexion REFUSÉE sans jeton valide du launcher HeraliZ (CurseForge, autre launcher,",
                "jeton de plus de 6 h), même en détection silencieuse : c'est une règle d'accès. Activer une fois le",
                "launcher distribué à tous les joueurs. Des mods différents du modpack restent une simple alerte.")
                .define("tokenRequired", false);
        ALLOWED_MODS_HASHES = b.comment(
                "Empreintes de dossier mods autorisées (vide = non vérifié). L'empreinte de chaque joueur",
                "est écrite dans la console à sa connexion : ajoutez ici celle(s) de votre modpack officiel.")
                .defineList("allowedModsHashes", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("Correction instantanée : le tricheur détecté ne profite pas de sa triche.",
                "Indépendant de alertOnly : ne bannit personne, annule seulement l'avantage.")
                .push("mitigation");
        MITIGATE_MOVEMENT = b.comment("Fly / Speed : renvoie le joueur à sa dernière position au sol.")
                .define("movementSetback", true);
        MITIGATE_COMBAT = b.comment("Reach / KillAura / AutoClicker : annule le coup.")
                .define("cancelCombat", true);
        COMBAT_LOCK_SECONDS = b.comment("Après une détection de combat, coups bloqués pendant N secondes (0 = non).")
                .defineInRange("combatLockSeconds", 3, 0, 300);
        MITIGATE_BLOCKS = b.comment("Nuker / FastBreak : le bloc n'est pas cassé.")
                .define("cancelBlocks", true);
        BLOCK_LOCK_SECONDS = b.comment("Après une détection de minage, casse bloquée pendant N secondes (0 = non).")
                .defineInRange("blockLockSeconds", 3, 0, 300);
        MITIGATE_EFFECTS = b.comment("IllegalEffect : retire l'effet illégal immédiatement.")
                .define("removeIllegalEffects", true);
        b.pop();

        b.push("bans");
        IP_BAN_HOURS = b.comment(
                "Durée max (heures) du ban d'IP qui accompagne un /sban. 0 = ne jamais bannir l'IP.",
                "Une IP est souvent partagée (4G, box familiale, lycée) : un ban d'IP long bannit des innocents.",
                "Le ban de compte et le ban matériel, eux, gardent la durée demandée.")
                .defineInRange("ipBanHours", 72, 0, 87600);
        ALERT_SAME_IP = b.comment(
                "Alerte le staff quand un joueur se connecte avec l'IP d'un joueur banni (sans l'expulser).")
                .define("alertSameIp", true);
        DATA_RETENTION_DAYS = b.comment(
                "RGPD : les IP et identifiants matériels des joueurs NON bannis sont supprimés",
                "après N jours sans connexion. Les joueurs bannis sont conservés tant que le ban est actif.")
                .defineInRange("playerDataRetentionDays", 180, 7, 3650);
        b.pop();

        b.push("resources");
        BLOCK_RESOURCE_PACKS = b.comment(
                "Interdit les packs de ressources (textures) ajoutés par le joueur.",
                "Le pack du serveur reste autorisé. Pour IMPOSER le pack du serveur, mettez aussi",
                "require-resource-pack=true dans server.properties.")
                .define("blockCustomResourcePacks", true);
        ALLOWED_RESOURCE_PACKS = b.comment("Packs de ressources autorisés en plus de ceux du serveur et du jeu.")
                .defineList("allowedResourcePacks", List.of(), o -> o instanceof String);
        BLOCK_SHADERS = b.comment(
                "Interdit les shaders (Iris / Oculus / OptiFine). Détection best-effort côté client.",
                "Le plus sûr : ne pas inclure de mod de shaders dans le modpack officiel — le launcher",
                "empêche alors d'en ajouter un.")
                .define("blockShaders", true);
        ALLOWED_SHADERPACKS = b.comment("Shaderpacks autorisés (identifiant en minuscules). Vide = aucun.")
                .defineList("allowedShaderpacks", List.of(), o -> o instanceof String);
        b.pop();

        b.push("modscan");
        BLACKLISTED_MODS = b.comment(
                "Mods interdits détectés à la connexion (identifiants en minuscules).",
                "ATTENTION : best-effort. Un client modifié peut cacher ses mods. À compléter.")
                .defineList("blacklistedMods",
                        List.of("wurst", "meteor", "meteorclient", "impact", "aristois", "future",
                                "inertia", "liquidbounce", "sigma", "rise", "novoline", "doomsday",
                                "vape", "autoaim", "cheatutils", "xray", "xraymod"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Contrôle d'intégrité du client : à la connexion puis à intervalle aléatoire, le jeu du",
                "joueur envoie ce qu'il a VRAIMENT chargé (jars, modules, configs Mixin, agents Java, code",
                "d'origine inconnue, DLL injectées…). Comparé au client de référence (/spartan reference learn).",
                "Alerte silencieuse au staff, jamais d'expulsion (voir integrity.silentClientDetections).")
                .push("scan");
        SCAN_ENABLED = b.comment("Active le contrôle d'intégrité du client.").define("enabled", true);
        SCAN_FIRST_MIN = b.comment("Premier contrôle après la connexion : délai minimum (secondes).")
                .defineInRange("firstScanMinSeconds", 10, 3, 600);
        SCAN_FIRST_MAX = b.comment("Premier contrôle après la connexion : délai maximum (secondes).")
                .defineInRange("firstScanMaxSeconds", 30, 3, 600);
        SCAN_INTERVAL_MIN = b.comment("Contrôles suivants : intervalle aléatoire minimum (secondes).")
                .defineInRange("intervalMinSeconds", 180, 30, 86400);
        SCAN_INTERVAL_MAX = b.comment("Contrôles suivants : intervalle aléatoire maximum (secondes).")
                .defineInRange("intervalMaxSeconds", 480, 30, 86400);
        SCAN_TIMEOUT = b.comment("Délai de réponse d'un contrôle (secondes). Deux absences de suite = alerte.")
                .defineInRange("responseTimeoutSeconds", 90, 15, 600);
        SCAN_RENDER_SAMPLES = b.comment(
                "Échantillons de la pile du thread de rendu par contrôle (~50 ms chacun) : un ESP injecté",
                "s'exécute à chaque image et finit par y apparaître. 0 = désactivé.")
                .defineInRange("renderSamples", 40, 0, 200);
        SCAN_MIXIN_WATCH = b.comment(
                "Classes sensibles du jeu : le contrôle relève chaque Mixin qui les modifie (rendu = ESP / X-ray,",
                "mouvement = fly / speed / NoSlow, réseau, souris = aimbot…). Une config Mixin inconnue qui en",
                "touche une est un indice fort. Ajoutez les classes de TaCZ ou d'autres mods à protéger.")
                .defineList("mixinWatchClasses", List.of(
                        "net.minecraft.client.renderer.LevelRenderer",
                        "net.minecraft.client.renderer.GameRenderer",
                        "net.minecraft.client.renderer.LightTexture",
                        "net.minecraft.client.renderer.FogRenderer",
                        "net.minecraft.client.renderer.ItemBlockRenderTypes",
                        "net.minecraft.client.renderer.ScreenEffectRenderer",
                        "net.minecraft.client.renderer.entity.EntityRenderer",
                        "net.minecraft.client.renderer.entity.LivingEntityRenderer",
                        "net.minecraft.client.renderer.entity.EntityRenderDispatcher",
                        "net.minecraft.client.renderer.entity.player.PlayerRenderer",
                        "net.minecraft.client.renderer.culling.Frustum",
                        "net.minecraft.client.renderer.block.BlockRenderDispatcher",
                        "net.minecraft.client.renderer.block.ModelBlockRenderer",
                        "net.minecraft.client.renderer.block.LiquidBlockRenderer",
                        "net.minecraft.client.player.LocalPlayer",
                        "net.minecraft.client.player.KeyboardInput",
                        "net.minecraft.client.multiplayer.MultiPlayerGameMode",
                        "net.minecraft.client.multiplayer.ClientPacketListener",
                        "net.minecraft.client.multiplayer.ClientLevel",
                        "net.minecraft.client.Minecraft",
                        "net.minecraft.client.MouseHandler",
                        "net.minecraft.client.KeyboardHandler",
                        "net.minecraft.client.Timer",
                        "net.minecraft.client.gui.Gui",
                        "net.minecraft.network.Connection",
                        "net.minecraft.world.entity.Entity",
                        "net.minecraft.world.entity.LivingEntity",
                        "net.minecraft.world.entity.player.Player",
                        "net.minecraft.world.level.block.Block",
                        "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase"), o -> o instanceof String);
        SCAN_CLASS_PROBES = b.comment(
                "Classes de clients de triche connus, cherchées dans le jeu du joueur. Cette liste n'est",
                "jamais dans le jar : les développeurs de cheats ne la voient pas. À compléter avec les",
                "classes trouvées dans les preuves des tricheurs attrapés.")
                .defineList("classProbes", List.of(
                        "baritone.api.BaritoneAPI",
                        "net.wurstclient.WurstClient",
                        "meteordevelopment.meteorclient.MeteorClient",
                        "meteordevelopment.orbit.EventBus",
                        "net.ccbluex.liquidbounce.LiquidBounce",
                        "pro.mikey.xray.XRay"), o -> o instanceof String);
        SCAN_PROCESS_PATTERNS = b.comment(
                "Programmes de triche / d'injection : signalés s'ils tournent sur le PC du joueur (morceau du",
                "nom de l'exécutable, minuscules). Seuls les programmes correspondants sont envoyés au serveur.")
                .defineList("processPatterns", List.of(
                        "cheatengine", "processhacker", "systeminformer", "x64dbg", "x32dbg", "ollydbg",
                        "injector", "xenos", "recaf", "jd-gui", "bytecode-viewer"), o -> o instanceof String);
        SCAN_NATIVE_PATTERNS = b.comment(
                "Morceaux de noms de DLL suspectes chargées dans le jeu (une DLL chargée depuis le dossier",
                "temporaire, Téléchargements ou le Bureau est signalée dans tous les cas).")
                .defineList("nativePatterns", List.of("inject", "cheat", "hack", "aimbot", "xray", "vape"),
                        o -> o instanceof String);
        b.pop();

        b.comment("Anti-ESP / anti-wallhack : le serveur n'envoie plus un joueur à un autre tant qu'aucune ligne",
                "de vue n'existe entre eux (murs, bâtiments, sous-sols). Un ESP ou un wallhack, même injecté et",
                "indétectable, n'a alors rien à afficher. Les cas où le jeu a besoin du joueur sont gérés : tirs TaCZ",
                "et voix (Simple Voice Chat) le montrent aux joueurs à portée d'oreille, coéquipiers toujours visibles.",
                "Coût mesurable avec /spartan esp.")
                .push("antiEsp");
        ESP_ENABLED = b.comment("Active l'anti-ESP.").define("enabled", true);
        ESP_THREADS = b.comment(
                "Threads de calcul des lignes de vue, en parallèle du tick du serveur (le thread principal ne",
                "fait que préparer et appliquer : quasi aucun lag). -1 = automatique (1 thread par 4 cœurs, max 4),",
                "0 = calcul sur le thread principal avec budgetMicrosPerTick (petites machines).")
                .defineInRange("threads", -1, -1, 16);
        ESP_BUDGET_MICROS = b.comment(
                "Uniquement si threads = 0 : temps de calcul maximal par tick (microsecondes ; un tick dure 50 000).",
                "Au-delà, les vérifications restantes attendent le tick suivant ; une paire cachée non revérifiée",
                "à temps est montrée (jamais d'invisibilité injuste).")
                .defineInRange("budgetMicrosPerTick", 3000, 200, 40000);
        ESP_HIDE_DELAY_TICKS = b.comment(
                "Un joueur aperçu reste envoyé autant de ticks après avoir disparu derrière un obstacle",
                "(20 = 1 s) : évite les clignotements en courant le long des fenêtres ou des poteaux.")
                .defineInRange("hideDelayTicks", 20, 2, 200);
        ESP_FAIL_OPEN_TICKS = b.comment(
                "Paire cachée non revérifiée depuis autant de ticks (serveur saturé) : montrée.")
                .defineInRange("failOpenTicks", 10, 2, 100);
        ESP_MIN_DISTANCE = b.comment("En dessous de cette distance (blocs), toujours envoyé.")
                .defineInRange("minDistance", 2.5, 0.0, 16.0);
        ESP_PEEK = b.comment(
                "Anticipation des « peek » : un joueur est envoyé dès qu'un pas de côté de cette longueur (blocs)",
                "le rendrait visible, pour qu'il apparaisse à temps à l'angle d'un mur malgré le ping.")
                .defineInRange("peekBlocks", 0.6, 0.0, 2.0);
        ESP_JUMP = b.comment("Idem pour un saut (hauteur d'œil supplémentaire testée, blocs).")
                .defineInRange("jumpBlocks", 0.6, 0.0, 1.5);
        ESP_ALLIES = b.comment(
                "Coéquipiers toujours visibles : équipes vanilla, et mod de clan via IMC (voir README).")
                .define("revealAllies", true);
        ESP_BYPASS_CREATIVE = b.comment("Les joueurs en créatif voient tout le monde (les spectateurs aussi, toujours).")
                .define("creativeSeesAll", true);
        ESP_STAFF_SEE_ALL = b.comment(
                "Le staff (op 2+) voit tout le monde même en survie. Laisser false : le staff observe en spectateur.")
                .define("staffSeesAll", false);
        ESP_OPAQUE_BLOCKS = b.comment(
                "Blocs non pleins qui cachent aussi (forme de collision : une porte fermée cache, ouverte non).",
                "Tags (#minecraft:doors) ou identifiants (mod:bloc). Le verre, les feuilles, les vitres et les",
                "clôtures ne cachent jamais ; les blocs pleins et opaques (y compris de mods) cachent toujours.")
                .defineList("opaqueBlocks", List.of("#minecraft:doors"), o -> o instanceof String);
        ESP_REVEAL_SHOTS = b.comment(
                "TaCZ : un joueur qui tire (recharge, arme la culasse…) est envoyé aux joueurs à portée du son,",
                "sinon ils n'entendraient pas son tir. Ne désactiver qu'en connaissance de cause.")
                .define("revealOnShot", true);
        ESP_SHOT_REVEAL_TICKS = b.comment("Durée (ticks) pendant laquelle un tireur reste envoyé après un son.")
                .defineInRange("shotRevealTicks", 40, 5, 400);
        ESP_REVEAL_VOICE = b.comment(
                "Simple Voice Chat : un joueur qui parle en proximité est envoyé aux joueurs à portée de voix,",
                "sinon ils ne l'entendraient pas. Les groupes fermés ne révèlent rien.")
                .define("revealOnVoice", true);
        ESP_VOICE_REVEAL_TICKS = b.comment("Durée (ticks) pendant laquelle un joueur reste envoyé après avoir parlé.")
                .defineInRange("voiceRevealTicks", 20, 5, 400);
        b.pop();

        SPEC = b.build();
    }

    /**
     * Un signal venant du jeu du joueur peut-il l'expulser ? Jamais en détection silencieuse
     * (par défaut), sinon seulement hors mode alerte.
     */
    public static boolean mayKickForClientSignal() {
        return !SILENT_CLIENT.get() && !ALERT_ONLY.get();
    }

    public static List<String> getScanClassProbes() {
        List<String> out = new ArrayList<>();
        for (Object o : SCAN_CLASS_PROBES.get()) {
            out.add(String.valueOf(o).trim()); // noms de classes : la casse compte
        }
        return out;
    }

    public static List<String> getScanMixinWatch() {
        List<String> out = new ArrayList<>();
        for (Object o : SCAN_MIXIN_WATCH.get()) {
            out.add(String.valueOf(o).trim());
        }
        return out;
    }

    public static List<String> getScanProcessPatterns() {
        return lowerList(SCAN_PROCESS_PATTERNS.get());
    }

    public static List<String> getScanNativePatterns() {
        return lowerList(SCAN_NATIVE_PATTERNS.get());
    }

    public static List<String> getBlacklistedMods() {
        return lowerList(BLACKLISTED_MODS.get());
    }

    public static List<String> getAllowedResourcePacks() {
        return lowerList(ALLOWED_RESOURCE_PACKS.get());
    }

    public static List<String> getAllowedShaderpacks() {
        return lowerList(ALLOWED_SHADERPACKS.get());
    }

    public static List<String> getNoSlowIgnoredItems() {
        return lowerList(NOSLOW_IGNORED_ITEMS.get());
    }

    public static List<String> getFlyHoverItems() {
        return lowerList(FLY_HOVER_ITEMS.get());
    }

    public static List<String> getFlyExemptKeywords() {
        return lowerList(FLY_EXEMPT_KEYWORDS.get());
    }

    public static List<String> getIgnoredEffects() {
        return lowerList(IGNORED_EFFECTS.get());
    }

    public static List<String> getAllowedModsHashes() {
        return lowerList(ALLOWED_MODS_HASHES.get());
    }

    public static List<String> getAllowedJavaAgents() {
        return lowerList(ALLOWED_JAVA_AGENTS.get());
    }

    private static List<String> lowerList(List<? extends String> in) {
        List<String> out = new ArrayList<>();
        for (Object o : in) {
            out.add(String.valueOf(o).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * Valeur secrète (webhook, secret du launcher) : vide si absente ou si c'est encore la marque « __SPARTAN_…__ » du
     * fichier du dépôt (remplacée par les secrets GitHub à la mise en ligne).
     */
    public static String secret(ForgeConfigSpec.ConfigValue<String> v) {
        String s = v.get();
        return s == null || s.isBlank() || s.startsWith("__SPARTAN_") ? "" : s.trim();
    }
}
