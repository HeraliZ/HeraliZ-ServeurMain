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
   public static final ForgeConfigSpec.DoubleValue FAIM_FACTEUR;
   /** Vitesse de la faim, relue avec les réglages (FaimMixin de la partie serveur). */
   public static volatile float faimFacteur = 0.5F;
   public static final ForgeConfigSpec.BooleanValue SUCCES_ACTIFS;
   /** Succès de Minecraft suivis, relu avec les réglages (SuccesMixin de la partie serveur). */
   public static volatile boolean succesActifs = false;
   public static final ForgeConfigSpec.BooleanValue LEVEL_DAT_FOND;
   /** level.dat compressé et écrit à côté du fil serveur, relu avec les réglages (LevelDatFond de la partie serveur). */
   public static volatile boolean levelDatEnFond = true;
   public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MONDES_SANS_SPAWN;
   public static final List<String> MONDES_SANS_SPAWN_DEFAUT = List.of("crac:bases", "heraliz_clashwar:arenes");

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
            "multipliée par ce nombre. 1 : comme Minecraft ; 0,5 : la barre de faim dure deux fois plus longtemps.")
            .defineInRange("faim_facteur", 0.5, 0.1, 2.0);
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
