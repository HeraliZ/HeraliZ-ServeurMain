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

   public static final List<String> COMMANDES_COMBAT_DEFAUT = List.of("login", "register", "l", "reg", "msg", "tell", "w", "r",
         "me", "quetes", "journalieres", "pb", "saison", "extractions", "event", "report");
   // 25/10 (tri des commandes) : /home remplace /sethome, /delhome, /homes ; /tpa remplace /tpaccept, /tpdeny
   public static final List<String> COMMANDES_CARTE_DEFAUT = List.of("tpa", "login", "register", "l", "reg",
         "msg", "tell", "w", "r", "me", "quetes", "journalieres", "pb", "saison", "extractions", "event", "clan", "suicide", "report", "home",
         "base", "purge", "coeur", "ping");

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
   public static final ForgeConfigSpec.DoubleValue FAIM_FACTEUR;
   /** Vitesse de la faim, relue avec les réglages (FaimMixin de la partie serveur). */
   public static volatile float faimFacteur = 0.35F;
   public static final ForgeConfigSpec.BooleanValue SUCCES_ACTIFS;
   /** Succès de Minecraft suivis, relu avec les réglages (SuccesMixin de la partie serveur). */
   public static volatile boolean succesActifs = false;
   public static final ForgeConfigSpec.BooleanValue LEVEL_DAT_FOND;
   /** level.dat compressé et écrit à côté du fil serveur, relu avec les réglages (LevelDatFond de la partie serveur). */
   public static volatile boolean levelDatEnFond = true;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MONDES_SANS_SPAWN;
   public static final List<String> MONDES_SANS_SPAWN_DEFAUT = List.of("crac:bases", "crac:tuto", "heraliz_clashwar:arenes");
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

   public static final List<String> RECHARGER_DEFAUT = List.of("quete recharger", "heraliz reload", "casino reload",
         "rarete recharger", "heralizloot tables", "saison recharger", "territoire recharger", "purge recharger",
         "zones recharger", "clashwar recharger", "event recharger", "spartan officiel");

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
      RECHARGER_COMMANDES = b.comment("/recharger (24/10) : commandes de rechargement des modules jouées l'une après l'autre, sans",
            "redémarrer ni déconnecter personne (quêtes, marchands, recycleur, caisses, casino, raretés, tables de loot,",
            "saison, territoires, Purge, zones dangereuses, ClashWar, modpack officiel de l'anticheat). Les réglages en .toml",
            "se relisent seuls quand leur fichier change.")
            .defineListAllowEmpty(List.of("commandes"), () -> RECHARGER_DEFAUT, o -> o instanceof String);
      b.pop();
      b.push("survie");
      FAIM_FACTEUR = b.comment("Vitesse de la faim (25/10) : la fatigue de Minecraft (course, sauts, coups, vie qui remonte) est",
            "multipliée par ce nombre. 1 : comme Minecraft ; 0,35 (28/10) : la barre de faim dure environ trois fois plus longtemps.")
            .defineInRange("faim_facteur", 0.35, 0.1, 2.0);
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
            "libérée après le démarrage (27/10) : ils se chargent quand un joueur y va. Ne pas y mettre le monde du spawn.")
            .defineListAllowEmpty(List.of("mondes_sans_spawn"), () -> MONDES_SANS_SPAWN_DEFAUT, o -> o instanceof String);
      SPAWN_JAMAIS_PREPARE = b.comment("Mondes de mondes_sans_spawn (01/11) : true = leur zone de départ n'est même plus préparée au démarrage",
            "(Mohist chargeait encore 441 tronçons par monde, voire les générait, avant de les libérer) ni remise par /setworldspawn.",
            "false : libérée seulement après le démarrage, comme avant. La carte et le monde du spawn gardent la leur (réapparitions",
            "sans lit, arrivées au spawn).")
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
      b.pop();
      b.push("monde");
      TICS_ALEATOIRES = b.comment("Tics aléatoires (01/11, choix du propriétaire : carte figée) : gamerule randomTickSpeed de CHAQUE monde, réglée",
            "au chargement du monde (mondes créés plus tard compris), après le démarrage et quand ce fichier change, par l'API du jeu",
            "comme /gamerule. 0 : plus aucun tic aléatoire (herbe, feuilles, neige, glace, cultures, lave… ne changent plus seuls) ;",
            "3 : valeur de Minecraft ; -1 : ne pas toucher (la valeur déjà enregistrée dans le monde reste).")
            .defineInRange("tics_aleatoires", 0, -1, 4096);
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
      succesActifs = SUCCES_ACTIFS.get();
      levelDatEnFond = LEVEL_DAT_FOND.get();
      spawnJamaisPrepare = SPAWN_JAMAIS_PREPARE.get();
      joueursProchesGrille = JOUEURS_PROCHES_GRILLE.get();
      porteeSuiviRapide = PORTEE_SUIVI_RAPIDE.get();
      entitesParTroncon = ENTITES_PAR_TRONCON.get();
      paquetsTronconsPartages = PAQUETS_TRONCONS_PARTAGES.get();
      suiviParTroncon = SUIVI_PAR_TRONCON.get();
      suiviVoirRapide = SUIVI_VOIR_RAPIDE.get();
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
