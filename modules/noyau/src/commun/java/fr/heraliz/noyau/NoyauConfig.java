package fr.heraliz.noyau;

import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.noyau.pont.Crochet;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

/**
 * Réglages du noyau ({@code heraliz_noyau-server.toml}, copié par Forge depuis {@code defaultconfigs/} dans chaque monde).
 * Reprend les sections {@code [combat]} et {@code [carte]} de l'ancien {@code heralisz_matchs-moteur.toml}, mêmes clés.
 */
public final class NoyauConfig {
   private NoyauConfig() {}

   // 02/11 (audit point 32) : les commandes d'AuthMe passent maintenant par ces listes (événement Bukkit) : tout ce qu'il faut
   // pour se connecter y est (login, l, log, register, reg, captcha, totp, 2fa), sinon un joueur resté sur la carte ne
   // pourrait plus entrer son captcha ou son code de double authentification
   public static final List<String> COMMANDES_COMBAT_DEFAUT = List.of("login", "register", "l", "log", "reg", "captcha", "totp", "2fa",
         "msg", "tell", "w", "r", "me", "quetes", "journalieres", "pb", "saison", "extractions", "event");
   // 25/10 (tri des commandes) : /home remplace /sethome, /delhome, /homes ; /tpa remplace /tpaccept, /tpdeny
   public static final List<String> COMMANDES_CARTE_DEFAUT = List.of("tpa", "login", "register", "l", "log", "reg", "captcha", "totp", "2fa",
         "msg", "tell", "w", "r", "me", "quetes", "journalieres", "pb", "saison", "extractions", "event", "clan", "suicide", "home",
         "base", "purge", "coeur", "canal");

   public static final ForgeConfigSpec SPEC;
   public static final ForgeConfigSpec.IntValue TAG_COMBAT_S;
   public static final ForgeConfigSpec.BooleanValue MORT_DECONNEXION;
   public static final ForgeConfigSpec.BooleanValue REFUS_COMMANDES;
   public static final ForgeConfigSpec.BooleanValue OPS_EXEMPTES;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_COMBAT;
   public static final ForgeConfigSpec.BooleanValue CARTE_ACTIF;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_CARTE;
   public static final ForgeConfigSpec.BooleanValue DIAG_AUTO;
   public static final ForgeConfigSpec.IntValue DIAG_SEUIL_MS;
   public static final ForgeConfigSpec.BooleanValue DIAG_ANNONCER;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECHARGER_COMMANDES;
   public static final ForgeConfigSpec.IntValue REDEMARRAGE_CODE;
   public static final ForgeConfigSpec.IntValue TICS_ALEATOIRES;
   /** Gamerule randomTickSpeed de chaque monde (-1 : ne pas toucher), relue avec les réglages (TicsAleatoires du serveur). */
   public static volatile int ticsAleatoires = 0;
   /** Tronçons forcés (/forceload) de chaque monde retirés au démarrage (TronconsForces du serveur, 02/11). */
   public static final ForgeConfigSpec.BooleanValue RETIRER_TRONCONS_FORCES;
   public static final ForgeConfigSpec.DoubleValue FAIM_FACTEUR;
   /** Vitesse de la faim, relue avec les réglages (FaimMixin de la partie serveur). */
   public static volatile float faimFacteur = 0.35F;
   // Bruits de pas (03/11) : lus par la partie serveur seulement (PasEntendus) ; ce fichier n'est jamais envoyé aux joueurs
   public static final ForgeConfigSpec.BooleanValue PAS_ACTIF;
   public static final ForgeConfigSpec.IntValue PAS_PORTEE;
   public static final ForgeConfigSpec.DoubleValue PAS_VOLUME_JOUEURS;
   public static final ForgeConfigSpec.DoubleValue PAS_VOLUME_MONSTRES;
   /** Pas des autres joueurs et des monstres réglés par le serveur, relu avec les réglages. */
   public static volatile boolean pasActif = true;
   /** Distance (blocs) jusqu'à laquelle on entend le pas d'un autre joueur. */
   public static volatile int pasPortee = 32;
   /** Volume du pas d'un autre joueur, tout près, par rapport à Minecraft. */
   public static volatile float pasVolumeJoueurs = 2.0F;
   /** Volume du pas d'un monstre par rapport à avant (sa distance ne change pas). */
   public static volatile float pasVolumeMonstres = 2.0F;
   public static final ForgeConfigSpec.BooleanValue SUCCES_ACTIFS;
   /** Succès de Minecraft suivis, relu avec les réglages (SuccesMixin de la partie serveur). */
   public static volatile boolean succesActifs = false;
   public static final ForgeConfigSpec.BooleanValue LEVEL_DAT_FOND;
   /** level.dat compressé et écrit à côté du fil serveur, relu avec les réglages (LevelDatFond de la partie serveur). */
   public static volatile boolean levelDatEnFond = true;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MONDES_SANS_SPAWN;
   public static final List<String> MONDES_SANS_SPAWN_DEFAUT = List.of("crac:tuto", "heraliz_clashwar:arenes", "heralisz:spawn");
   public static final ForgeConfigSpec.BooleanValue SPAWN_JAMAIS_PREPARE;
   /** Zone de départ des mondes de mondes_sans_spawn jamais préparée (ni au démarrage, ni par /setworldspawn). */
   public static volatile boolean spawnJamaisPrepare = true;
   // « Minecraft allégé » (01/11) : chaque réglage coupe une optimisation des mixins serveur (heraliz_noyau_allege.mixins.json)
   public static final ForgeConfigSpec.BooleanValue JOUEURS_PROCHES_GRILLE;
   /** Joueurs proches des tronçons (apparitions, tics aléatoires) cherchés dans une grille faite une fois par tick. */
   public static volatile boolean joueursProchesGrille = true;
   public static final ForgeConfigSpec.BooleanValue PORTEE_SUIVI_RAPIDE;
   /** Portée de suivi d'une entité sans passager calculée sans parcourir ses passagers (aucun objet créé). */
   public static volatile boolean porteeSuiviRapide = true;
   public static final ForgeConfigSpec.BooleanValue ENTITES_PAR_TRONCON;
   /** Entités d'un tronçon envoyé à un joueur cherchées dans un index par tronçon au lieu de toutes les entités du monde. */
   public static volatile boolean entitesParTroncon = true;
   public static final ForgeConfigSpec.BooleanValue PAQUETS_TRONCONS_PARTAGES;
   /** Paquet d'un tronçon fabriqué une fois par tick et redonné aux joueurs qui le reçoivent dans ce tick. */
   public static volatile boolean paquetsTronconsPartages = true;
   // « Minecraft allégé », étape 2 (01/11)
   public static final ForgeConfigSpec.BooleanValue SUIVI_PAR_TRONCON;
   /** Suivi des entités par les joueurs : seules les entités proches (index par case) ou déjà vues sont jugées. */
   public static volatile boolean suiviParTroncon = true;
   public static final ForgeConfigSpec.BooleanValue SUIVI_VOIR_RAPIDE;
   /** Test de Bukkit (canSee) du suivi lu directement quand le joueur n'a aucune visibilité inversée. */
   public static volatile boolean suiviVoirRapide = true;
   public static final ForgeConfigSpec.BooleanValue TRONCONS_SANS_TICKET;
   /** Tronçon déjà complet rendu par getChunk sans ticket UNKNOWN (comme Paper). */
   public static volatile boolean tronconsSansTicket = true;
   public static final ForgeConfigSpec.BooleanValue TRONCONS_ACTIFS_LISTE;
   /** tickChunks parcourt la liste des tronçons qui peuvent tourner au lieu de tous les tronçons chargés. */
   public static volatile boolean tronconsActifsListe = true;
   public static final ForgeConfigSpec.BooleanValue MENUS_UNE_COMPARAISON;
   /** broadcastChanges : la 2e comparaison d'une case est sautée quand elle repose la même question que la 1re. */
   public static volatile boolean menusUneComparaison = true;

   /**
    * Commandes en plus jouées par /recharger : vide (02/11, audit point 36). Les modules HeraliZ inscrivent eux-mêmes leur
    * relecture ({@link fr.heraliz.noyau.api.Rechargements}) : leurs anciennes sous-commandes (quete recharger, heraliz
    * reload, casino reload, armurier recharger...) n'existent plus, armurier recharger comprise.
    */
   public static final List<String> RECHARGER_DEFAUT = List.of();

   // 02/11 (audit, points 34 et 36) : liste des commandes allégée (~500 noms dans le panneau du Fondateur, ~1 000 dans le chat)
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_RETIREES;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMANDES_CACHEES;
   /**
    * Commandes des mods des autres auteurs et de Minecraft qui ne servent pas ici, retirées pour de bon (console comprise).
    * Gardées (nos outils, le banc, les scripts de capture et le site s'en servent) : tp, gamemode, time, kill, give, clear,
    * effect, summon, gamerule, weather, difficulty, execute, fill, setblock, item, damage, stop, msg, tell, w, me, say,
    * forge, config... ; /curios est seulement cachée (/wipe tape « curios clear »).
    */
   public static final List<String> RETIREES_DEFAUT = List.of(
         // mods des autres auteurs : /kiwi dev_env_rules tue toutes les entités chargées et met keepInventory (audit point 34)
         "tacz", "kiwi", "coro", "modernfix", "mfsrc", "ywzj_vehicle", "matmossettings", "tctcoresettings",
         // Minecraft
         "debug", "jfr", "perf", "datapack", "function", "schedule", "trigger", "return", "bossbar", "team", "teammsg", "tm",
         "advancement", "recipe", "fillbiome", "place", "forceload", "spreadplayers", "ride", "loot", "seed",
         "defaultgamemode", "setidletimeout", "experience", "xp", "enchant", "attribute");
   /**
    * Noms cachés de la liste des commandes (complétion du chat, panneau du staff) mais toujours utilisables si on les tape :
    * autres noms d'une même commande (WorldEdit, LiteBans, AuthMe, WorldGuard, LuckPerms, KauriVPN, Bukkit) et commandes
    * de plugins sans usage ici. Lu dans les jars des plugins le 02/11 (plugin.yml, annotations de WorldEdit et WorldGuard).
    */
   public static final List<String> CACHEES_DEFAUT = List.of(
         // mods : /curios reste pour /wipe (curios clear), doublon de /inspecter pour le staff
         "curios",
         // Bukkit, Spigot
         "about", "ver", "pl", "timings",
         // AuthMe : autres noms ; email (aucun serveur de mail), verification, unregister, logout (audit point 32)
         "log", "2fa", "changepass", "cp", "email", "verification", "unregister", "unreg", "logout",
         // LuckPerms, KauriVPN (antivpn), ConsoleSpamFix (console seulement)
         "perm", "perms", "permission", "permissions", "kaurivpn", "kvpn", "vpn", "avpn", "csf",
         // WorldGuard
         "regions", ";", "stoplag", "haltactivity", "slay", "stack",
         // LiteBans : autres noms (le staff peut toujours taper /namehist...)
         "lban", "lmute", "lwarn", "lkick", "lipban", "banip", "ban-ip", "tempipban", "tempbanip", "iptempban", "lipmute",
         "muteip", "tempipmute", "tempmuteip", "iptempmute", "lunban", "unbanip", "ipunban", "tempunban", "pardon", "pardonip",
         "pardon-ip", "lunmute", "tempunmute", "unmuteip", "ipunmute", "lunwarn", "removewarnings", "rmwarnings", "rmwarn",
         "ltempban", "tban", "tmpban", "ltempmute", "lcheckban", "chkban", "lcheckmute", "chkmute", "lcheckwarn", "chkwarn",
         "lbanlist", "listbans", "lmutelist", "listmutes", "lwarnlist", "listwarns", "lhistory", "hist", "lwarnings",
         "checkwarn", "warnlist", "listwarn", "warninglist", "listwarnings", "lprunehistory", "lstaffhistory", "lshistory",
         "staffhist", "shist", "shistory", "lstaffrollback", "liphistory", "iphist", "lnamehistory", "namehist", "luuid",
         "lgeoip", "geo", "ldupeip", "alts", "checkalts", "listalts", "lipreport", "llockdown", "lbans",
         // LiteBans : commandes sans usage ici
         "ipmute", "checkmute", "mutelist", "prunehistory", "staffrollback", "lockdown", "geoip", "lastuuid", "ipreport",
         // WorldEdit : autres noms (« /re » = //re...)
         "biomels", "/gmask", "/toggleplace", "/searchitem", "/l", "/search", "/gen", "/g", "/genbiome", "/gb", "/undo",
         "/redo", "/clearhistory", "!", "asc", "desc", "j", "/re", "/rep", "/middle", "/outline", "/desel", "/deselect",
         "/restore", "unbind", "/selwand", "/navwand", "flood", "/lrbuild", ",", "/material", "/fixlava", "/fixwater",
         "/removeabove", "/removebelow", "/removenear", "/replacenear", "/snow", "/thaw", "/green", "/ex", "/ext",
         "/extinguish", "ex", "ext", "rem", "rement", "/calc", "/eval", "/evaluate", "/solve", "schem", "/schematic",
         "/schem", "snap", "pickaxe", "sp", "br", "/brush", "/br", "we",
         // WorldEdit : sauvegardes (aucun dossier réglé) et scripts (rien d'installé)
         "snapshot", "restore", "cs", ".s");

   /** Réglages relus : la partie serveur met à jour ses listes de commandes. */
   public static final Crochet<Void> APPLIQUES = new Crochet<>();

   static {
      ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
      b.push("combat");
      TAG_COMBAT_S = b.comment("Duree du tag de combat en secondes (degats infliges ou recus d'un autre joueur)")
            .defineInRange("tag_secondes", 15, 1, 300);
      MORT_DECONNEXION = b.comment("Deconnexion pendant le tag de combat = mort (le corps reste ; jamais a l'arret du serveur)")
            .define("mort_deconnexion", true);
      REFUS_COMMANDES = b.comment("Refuser au joueur tague (ou a terre) toute commande absente de commandes_permises_combat")
            .define("refus_commandes", true);
      OPS_EXEMPTES = b.comment("Les operateurs (niveau 2) tagues gardent toutes leurs commandes")
            .define("operateurs_exemptes", false);
      COMMANDES_COMBAT = b.comment("Seules commandes permises au joueur tague ou a terre (racine en minuscules, sans / ni espace de noms)")
            .defineListAllowEmpty(List.of("commandes_permises_combat"), () -> COMMANDES_COMBAT_DEFAUT, o -> o instanceof String);
      b.pop();
      b.push("carte");
      CARTE_ACTIF = b.comment("Sur la carte (minecraft:overworld), en survie ou aventure, hors staff (niveau 2) : refuser toute commande absente de commandes_permises")
            .define("actif", true);
      COMMANDES_CARTE = b.comment("Seules commandes permises sur la carte (racine en minuscules, sans / ni espace de noms)")
            .defineListAllowEmpty(List.of("commandes_permises"), () -> COMMANDES_CARTE_DEFAUT, o -> o instanceof String);
      b.pop();
      b.push("diag");
      DIAG_AUTO = b.comment("Rapport automatique à chaque blocage du serveur (/diag, 24/10). Ne coûte rien tant que tout va bien :",
            "un fil surveille la durée du tick et ne relève la pile du serveur que pendant un tick trop long.")
            .define("auto", true);
      DIAG_SEUIL_MS = b.comment("Durée d'un tick (en ms) à partir de laquelle on parle de blocage. Un tick normal : 50 ms au plus.")
            .defineInRange("seuil_ms", 250, 60, 60000);
      DIAG_ANNONCER = b.comment("Prévenir les opérateurs connectés (niveau 4) à chaque blocage, dans le chat (une fois par 30 s au plus).")
            .define("annoncer", true);
      b.pop();
      b.push("recharger");
      RECHARGER_COMMANDES = b.comment("/recharger [module] relit les données de tous les modules (ou d'un seul), sans redémarrer ni déconnecter",
            "personne : quêtes, marchands, recycleur, caisses, casino, raretés, tables de loot, saison, territoires, clans, hérals,",
            "Purge, coffres des bases, zones dangereuses, ClashWar, événements, dégâts des armes, modpack officiel de l'anticheat",
            "(02/11 : chaque module s'inscrit lui-même ; leurs sous-commandes « recharger » n'existent plus). Ici : commandes EN PLUS",
            "jouées ensuite (vide par défaut). Les réglages en .toml se relisent seuls quand leur fichier change.")
            .defineListAllowEmpty(List.of("commandes"), () -> RECHARGER_DEFAUT, o -> o instanceof String);
      b.pop();
      b.push("commandes");
      COMMANDES_RETIREES = b.comment("Commandes retirées pour de bon (02/11) : Minecraft et mods, console comprise (racine en minuscules, sans /",
            "ni espace de noms). Appliqué au démarrage et quand ce fichier change ; une commande enlevée de la liste ne revient",
            "qu'au redémarrage. Jamais une commande de plugin (Bukkit) : celles-là, voir cachees.")
            .defineListAllowEmpty(List.of("retirees"), () -> RETIREES_DEFAUT, o -> o instanceof String);
      COMMANDES_CACHEES = b.comment("Noms cachés de la liste des commandes (complétion du chat, panneau du staff), toujours utilisables si on les",
            "tape (02/11) : autres noms des plugins (WorldEdit, LiteBans...) et commandes sans usage. Serveur Bukkit (Mohist)",
            "seulement ; relu à chaque envoi de la liste (connexion, changement de droits).")
            .defineListAllowEmpty(List.of("cachees"), () -> CACHEES_DEFAUT, o -> o instanceof String);
      b.pop();
      b.push("survie");
      FAIM_FACTEUR = b.comment("Vitesse de la faim (25/10) : la fatigue de Minecraft (course, sauts, coups, vie qui remonte) est",
            "multipliée par ce nombre. 1 : comme Minecraft ; 0,35 (28/10) : la barre de faim dure environ trois fois plus longtemps.")
            .defineInRange("faim_facteur", 0.35, 0.1, 2.0);
      b.pop();
      b.push("pas");
      PAS_ACTIF = b.comment("Bruits de pas (03/11, retour du propriétaire : « on n'entend pas assez les pas des ennemis »). Le pas d'un joueur",
            "est joué par son propre jeu pour lui-même (jamais touché ici) et envoyé par le serveur aux AUTRES joueurs ; celui d'un",
            "zombie est envoyé par le serveur à tous. true = réglages ci-dessous ; false : comme Minecraft (pas d'un autre joueur",
            "entendu à 16 blocs au plus).")
            .define("actif", true);
      PAS_PORTEE = b.comment("Distance (blocs) jusqu'à laquelle on entend le pas d'un autre joueur, de moins en moins fort avec la",
            "distance (Minecraft : 16). Un joueur accroupi reste silencieux, comme dans Minecraft. Vanish du staff respecté.")
            .defineInRange("portee_joueurs", 32, 8, 64);
      PAS_VOLUME_JOUEURS = b.comment("Volume du pas d'un autre joueur tout près, par rapport à Minecraft (1 : comme Minecraft ; 2 : deux fois",
            "plus fort). Ses propres pas ne changent pas.")
            .defineInRange("volume_joueurs", 2.0, 0.25, 4.0);
      PAS_VOLUME_MONSTRES = b.comment("Volume du pas des zombies et des autres monstres, par rapport à avant (1 : comme avant, soit la moitié",
            "de Minecraft, réglée le 24/10 dans le pack de ressources des joueurs ; 2 : comme Minecraft). Leur distance ne change",
            "pas : 11 blocs pour les zombies.")
            .defineInRange("volume_monstres", 2.0, 0.25, 4.0);
      b.pop();
      b.push("succes");
      SUCCES_ACTIFS = b.comment("Succès (« progrès ») de Minecraft (27/10, perf) : false = ni suivis ni enregistrés. Ils étaient vérifiés à",
            "chaque changement d'inventaire (chaque tir de TaCZ), environ 1 % du serveur à 90 joueurs, et ne servent pas ici (quêtes,",
            "saison et statistiques sont à part ; notifications déjà cachées chez les joueurs). Relu à l'arrivée de chaque joueur.")
            .define("actifs", false);
      b.pop();
      b.push("perf");
      LEVEL_DAT_FOND = b.comment("level.dat (27/10) : true = compressé et écrit à côté du fil serveur à chaque sauvegarde automatique (son",
            "contenu est toujours relevé par Minecraft) ; à l'arrêt du serveur, écrit tout de suite. false : comme Minecraft.")
            .define("level_dat_en_fond", true);
      MONDES_SANS_SPAWN = b.comment("Mondes dont la zone de départ (2 209 tronçons gardés chargés par Mohist, même sans personne) est",
            "libérée après le démarrage (27/10) : ils se chargent quand un joueur y va. Jamais la carte (réapparitions sans lit).",
            "02/11 : crac:bases retiré (ancien monde des bases fermé, plus créé) ; à remettre ici s'il est rouvert. 02/11 : le monde",
            "du spawn (heralisz:spawn) ajouté ; ses deux points d'arrivée (réveil, /spawn) restent gardés par leurs propres tickets.")
            .defineListAllowEmpty(List.of("mondes_sans_spawn"), () -> MONDES_SANS_SPAWN_DEFAUT, o -> o instanceof String);
      SPAWN_JAMAIS_PREPARE = b.comment("Mondes de mondes_sans_spawn (01/11) : true = leur zone de départ n'est même plus préparée au démarrage",
            "(Mohist chargeait encore 441 tronçons par monde, voire les générait, avant de les libérer) ni remise par /setworldspawn.",
            "false : libérée seulement après le démarrage, comme avant. La carte garde la sienne (réapparitions sans lit).")
            .define("spawn_jamais_prepare", true);
      JOUEURS_PROCHES_GRILLE = b.comment("Minecraft allégé (01/11) : à chaque tick, Minecraft cherche pour chaque tronçon actif s'il y a un joueur",
            "(non spectateur) à moins de 128 blocs, en parcourant TOUS les joueurs du monde. true = les joueurs sont rangés une fois",
            "par tick dans une grille de 128 blocs et seuls les voisins sont regardés (même réponse, même règle que Minecraft et",
            "Mohist, mob-spawn-range de spigot.yml compris). false : comme Minecraft.")
            .define("joueurs_proches_grille", true);
      PORTEE_SUIVI_RAPIDE = b.comment("Minecraft allégé (01/11) : la portée de suivi d'une entité (à quelle distance les joueurs la voient) était",
            "calculée pour chaque entité, chaque joueur qui bouge et chaque tick en parcourant ses passagers par un flux Java",
            "(une dizaine d'objets créés à chaque fois). true = sans passager, la boucle ne parcourt rien et ne crée rien (même",
            "portée). false : comme Minecraft.")
            .define("portee_suivi_rapide", true);
      ENTITES_PAR_TRONCON = b.comment("Minecraft allégé (01/11) : pour chaque tronçon envoyé à un joueur, Minecraft parcourait TOUTES les entités",
            "suivies du monde pour trouver celles du tronçon (~170 tronçons x 4 000 entités à chaque arrivée). true = un index par",
            "tronçon, refait dès qu'une entité change de tronçon, apparaît ou disparaît (mêmes entités, même ordre). false : comme",
            "Minecraft.")
            .define("entites_par_troncon", true);
      PAQUETS_TRONCONS_PARTAGES = b.comment("Minecraft allégé (01/11) : quand plusieurs joueurs reçoivent le même tronçon dans le même tick (groupe",
            "extrait ou téléporté ensemble), son paquet n'est fabriqué qu'une fois (mêmes octets ; oublié dès qu'un bloc, une entité",
            "de bloc, la lumière ou les biomes du tronçon changent). false : un paquet par joueur, comme Minecraft.")
            .define("paquets_troncons_partages", true);
      SUIVI_PAR_TRONCON = b.comment("Minecraft allégé, étape 2 (01/11) : à chaque paquet de mouvement d'un joueur, Minecraft juge TOUTES les entités",
            "suivies du monde pour lui (77 ms par tick au banc à 150 joueurs, surtout les balles de TaCZ). true = seules celles des",
            "cases de 32 blocs à portée et celles qu'il voit déjà sont jugées, avec la décision de Minecraft refaite à l'identique",
            "(vanish, anti-ESP de Spartan, passagers, spectateurs compris) ; pareil pour une entité ou un joueur qui change de",
            "section. Une paire sur 8 192 est rejugée par Minecraft, et tout est comparé à Minecraft toutes les 60 s : un écart",
            "coupe jusqu'au redémarrage. false : comme Minecraft.")
            .define("suivi_par_troncon", true);
      SUIVI_VOIR_RAPIDE = b.comment("Minecraft allégé, étape 2 (01/11) : test de Bukkit « ce joueur peut-il voir cette entité ? » (vanish), posé par",
            "Mohist pour chaque entité à portée. true = pour un joueur à qui aucun plugin ne cache ni ne montre d'entité, la réponse",
            "est lue directement sur l'entité (même réponse ; le vrai test sinon, et une fois sur 4 096 pour comparer). false : le",
            "vrai test de Bukkit à chaque fois.")
            .define("suivi_voir_rapide", true);
      TRONCONS_SANS_TICKET = b.comment("Minecraft allégé, étape 2 (01/11) : un tronçon déjà complet demandé sur le fil du serveur (getChunk) est rendu",
            "tel quel, sans le ticket « unknown » d'un tick que Minecraft pose à chaque fois (comme Paper ; même tronçon rendu).",
            "Seule différence : un tronçon que plus rien ne tient (aucun joueur, aucun autre ticket) redescend 1 à 2 ticks plus",
            "tôt. false : comme Minecraft.")
            .define("troncons_sans_ticket", true);
      TRONCONS_ACTIFS_LISTE = b.comment("Minecraft allégé, étape 2 (01/11) : à chaque tick, Minecraft parcourt TOUS les tronçons chargés (~23 000) pour",
            "trouver ceux qui tournent. true = liste des tronçons qui peuvent tourner, tenue à jour quand leur niveau change (mêmes",
            "tronçons, Minecraft les mélange ensuite au hasard comme avant ; comparée à Minecraft toutes les 60 s). false : comme",
            "Minecraft.")
            .define("troncons_actifs_liste", true);
      MENUS_UNE_COMPARAISON = b.comment("Minecraft allégé, étape 2 (01/11) : à chaque tick, Minecraft compare deux fois chaque case du menu ouvert de",
            "chaque joueur à ses copies (NBT complet des armes, sacs, armures). true = quand les deux copies sont le même objet et",
            "que la 1re comparaison a dit « pareil », la 2e (la même question) est sautée. false : comme Minecraft.")
            .define("menus_une_comparaison", true);
      b.pop();
      b.push("monde");
      TICS_ALEATOIRES = b.comment("Tics aléatoires (01/11, choix du propriétaire : carte figée) : gamerule randomTickSpeed de CHAQUE monde, réglée",
            "au chargement du monde (mondes créés plus tard compris), après le démarrage et quand ce fichier change, par l'API du jeu",
            "comme /gamerule. 0 : plus aucun tic aléatoire (herbe, feuilles, neige, glace, cultures, lave… ne changent plus seuls) ;",
            "3 : valeur de Minecraft ; -1 : ne pas toucher (la valeur déjà enregistrée dans le monde reste).")
            .defineInRange("tics_aleatoires", 0, -1, 4096);
      RETIRER_TRONCONS_FORCES = b.comment("Tronçons forcés (02/11) : true = au démarrage, une fois, tous les tronçons forcés de chaque monde sont",
            "retirés par l'API du jeu, comme « /forceload remove all » (commande retirée, voir [commandes] retirees), et le journal",
            "dit combien et où. Les 13 laissés sur la carte par les scripts de pose des décors du 24/10 y gardaient chargés à vie",
            "environ 2 000 tronçons, même serveur vide. Aucun de nos mods n'en pose ; les tickets des autres mods (Forge) et des",
            "plugins ne sont pas touchés. false : on n'y touche pas.")
            .define("retirer_troncons_forces", true);
      b.pop();
      b.push("redemarrage");
      REDEMARRAGE_CODE = b.comment("/redemarrage : code de sortie de Java à la fin (0 : arrêt normal ; une valeur autre que 0 fait",
            "redémarrer le serveur par le panel si son redémarrage après plantage est activé). Sinon : tâche « restart » du panel.")
            .defineInRange("code_sortie", 0, 0, 255);
      b.pop();
      SPEC = b.build();
   }

   static void enregistrer(IEventBus modBus) {
      ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "heraliz_noyau-server.toml");
      modBus.addListener((ModConfigEvent.Loading e) -> appliquer(e.getConfig()));
      modBus.addListener((ModConfigEvent.Reloading e) -> appliquer(e.getConfig()));
   }

   private static void appliquer(ModConfig c) {
      if (c.getSpec() != SPEC) return;
      CombatTag.dureeMs(TAG_COMBAT_S.get() * 1000L);
      faimFacteur = (float) (double) FAIM_FACTEUR.get();
      pasActif = PAS_ACTIF.get();
      pasPortee = PAS_PORTEE.get();
      pasVolumeJoueurs = (float) (double) PAS_VOLUME_JOUEURS.get();
      pasVolumeMonstres = (float) (double) PAS_VOLUME_MONSTRES.get();
      succesActifs = SUCCES_ACTIFS.get();
      levelDatEnFond = LEVEL_DAT_FOND.get();
      spawnJamaisPrepare = SPAWN_JAMAIS_PREPARE.get();
      joueursProchesGrille = JOUEURS_PROCHES_GRILLE.get();
      porteeSuiviRapide = PORTEE_SUIVI_RAPIDE.get();
      entitesParTroncon = ENTITES_PAR_TRONCON.get();
      paquetsTronconsPartages = PAQUETS_TRONCONS_PARTAGES.get();
      suiviParTroncon = SUIVI_PAR_TRONCON.get();
      suiviVoirRapide = SUIVI_VOIR_RAPIDE.get();
      tronconsSansTicket = TRONCONS_SANS_TICKET.get();
      tronconsActifsListe = TRONCONS_ACTIFS_LISTE.get();
      menusUneComparaison = MENUS_UNE_COMPARAISON.get();
      ticsAleatoires = TICS_ALEATOIRES.get();
      if (APPLIQUES.branche()) APPLIQUES.accept(null);
   }

   /** Valeur d'un réglage, ou la valeur par défaut si les réglages ne sont pas encore chargés. */
   public static boolean vrai(ForgeConfigSpec.BooleanValue v, boolean defaut) {
      try {
         return v.get();
      } catch (IllegalStateException e) {
         return defaut;
      }
   }
}
