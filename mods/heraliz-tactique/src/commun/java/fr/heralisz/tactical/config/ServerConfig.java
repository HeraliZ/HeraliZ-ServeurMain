package fr.heralisz.tactical.config;

import fr.heraliz.tactique.allonge.Allonge;
import fr.heraliz.tactique.couteaux.CoupsCouteau;
import fr.heraliz.tactique.melee.PorteeMelee;
import fr.heraliz.tactique.son.SonLointain;
import fr.heraliz.tactique.vitres.Vitres;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class ServerConfig {
   public static IntValue FLASH_SHIELD_MAX_DURABILITY;
   public static IntValue FLASH_SHIELD_COOLDOWN;
   public static DoubleValue CROUCHING_INIT_SPEED_PERCENT;
   public static IntValue MELEE_MAX_TARGET_PER_PACKET;
   /** HeraliZ : portée maximale de toutes les armes de corps à corps de Tactique (fr.heraliz.tactique.melee.PorteeMelee). */
   public static DoubleValue MELEE_PORTEE_MAX;
   /** HeraliZ : s'allonger avec la touche « Ramper » de TaCZ (fr.heraliz.tactique.allonge.Allonge). */
   public static DoubleValue DELAI_RALLONGER;
   public static DoubleValue SANS_TIR_EN_SE_COUCHANT;
   public static BooleanValue PLONGEON;
   public static DoubleValue PLONGEON_VITESSE;
   public static DoubleValue PLONGEON_HAUTEUR;
   public static DoubleValue DELAI_PLONGEON;
   /** HeraliZ : vitres cassées par les balles de TaCZ, qui repoussent (fr.heraliz.tactique.vitres.Vitres). */
   public static BooleanValue VITRES_CASSER;
   public static IntValue VITRES_DELAI_MINUTES;
   public static ConfigValue<List<? extends String>> VITRES_DIMENSIONS;
   public static IntValue VITRES_RAYON_SPAWN;
   public static BooleanValue VITRES_CLAIMS_CRAC;
   public static ConfigValue<List<? extends String>> VITRES_BLOCS_PROTEGES;
   public static ConfigValue<List<? extends String>> VITRES_BLOCS_EN_PLUS;
   /** HeraliZ : tirs silencieux entendus de loin, étouffés et d'un endroit flou (fr.heraliz.tactique.son.SonLointain). */
   public static BooleanValue SON_LOINTAIN;
   public static DoubleValue SON_LOINTAIN_VOLUME;
   public static DoubleValue SON_LOINTAIN_FLOU;
   /** HeraliZ : couteaux TaCZ frappés comme les armes de mêlée de LR (fr.heraliz.tactique.couteaux.CoupsCouteau). */
   public static BooleanValue COUTEAUX;
   public static DoubleValue COUTEAUX_DEGATS;
   public static DoubleValue COUTEAUX_MULTIPLICATEUR_LOURD;
   public static IntValue COUTEAUX_CADENCE_RAPIDE;
   public static IntValue COUTEAUX_CADENCE_LOURD;
   public static DoubleValue COUTEAUX_ANGLE_RAPIDE;
   public static DoubleValue COUTEAUX_ANGLE_LOURD;

   public static ForgeConfigSpec init() {
      Builder builder = new Builder();
      builder.push("flash shield");
      FLASH_SHIELD_MAX_DURABILITY = builder.comment("max durability of flash shield").defineInRange("flash_shield_max_durability", 450, 1, 32767);
      FLASH_SHIELD_COOLDOWN = builder.comment("cooldown of flash shield in ticks").defineInRange("flash_shield_cooldown", 200, 20, 32767);
      builder.pop();
      builder.push("throwable");
      CROUCHING_INIT_SPEED_PERCENT = builder.comment("modifier of initial speed when crouching, 1.0 means no change")
         .defineInRange("flash_shield_cooldown", 0.5, 0.01, 2.0);
      builder.pop();
      builder.push("melee");
      MELEE_MAX_TARGET_PER_PACKET = builder.comment("maximum number of targets can contains in melee attack request packet")
         .defineInRange("melee_max_target", 32, 1, 512);
      // HeraliZ : envoyé aux joueurs à la connexion (ils choisissent leurs cibles avec la même portée que le serveur)
      MELEE_PORTEE_MAX = builder.comment(
            "Portée maximale (en blocs) de toutes les armes de corps à corps de HeraliZ Tactique : armes de mêlée (coups",
            "rapides, coups puissants, balayages et combos de chaque arme) et couteaux TaCZ (le coup du bouclier flash va à",
            "2,5). Mesurée des yeux du joueur au point le plus proche de la cible. Une arme dont la fiche",
            "(data/heralisz_melee/index/melee) va plus loin est ramenée à cette valeur ; une arme plus courte garde la sienne.",
            "Au-delà d'environ 3,6, l'anticheat Spartan (reachDistance 3,3 + marge) annule le coup, bloque les coups du",
            "joueur 3 s et alerte le staff : 3, comme un coup de Minecraft.")
         .defineInRange("portee_max", PorteeMelee.DEFAUT, 1.0, 6.0);
      builder.pop();
      // HeraliZ : config serveur envoyée par Forge aux joueurs à la connexion (le joueur et le serveur lisent les mêmes valeurs)
      builder.push("allonge");
      DELAI_RALLONGER = builder.comment(
            "Délai (en secondes) avant de pouvoir se rallonger, compté à partir du moment où le joueur s'est relevé",
            "(touche, saut, autre objet en main, eau...). Le serveur compte 0,5 s de moins (décalage du réseau).")
         .defineInRange("delai_rallonger", Allonge.DEFAUT_DELAI_RALLONGER, 0.0, 60.0);
      SANS_TIR_EN_SE_COUCHANT = builder.comment(
            "Durée (en secondes) sans pouvoir tirer pendant que le joueur se couche (passage debout -> allongé ;",
            "l'animation de Minecraft dure 0,6 s). Le serveur compte 0,2 s de moins (décalage du réseau).")
         .defineInRange("duree_sans_tir_couche", Allonge.DEFAUT_SANS_TIR, 0.0, 5.0);
      PLONGEON = builder.comment(
            "Plongeon façon Call of Duty : en se déplaçant (devant, derrière, côté), touche « Ramper » pendant un saut (jusqu'à 0,45 s après) ou avec la",
            "touche de saut enfoncée : le joueur est lancé en avant, corps à plat, et retombe allongé. Avec ou sans arme en",
            "main. L'élan est décidé et donné par le serveur (vitesse de Minecraft envoyée au joueur, suivie par Spartan).",
            "Il compte comme « se coucher » : pas de tir pendant duree_sans_tir_couche depuis son début, et il respecte",
            "delai_rallonger. Les dégâts de chute restent ceux de Minecraft. false : pas de plongeon.")
         .define("plongeon", Allonge.DEFAUT_PLONGEON);
      PLONGEON_VITESSE = builder.comment(
            "Vitesse donnée par le plongeon (dans le sens du déplacement), en blocs par tick (un sprint va à 0,28) : 0,7 lance le joueur",
            "d'environ 2,5 blocs depuis le sol, de 3,5 à 4,5 blocs depuis un saut.")
         .defineInRange("plongeon_vitesse", Allonge.DEFAUT_PLONGEON_VITESSE, 0.1, 1.5);
      PLONGEON_HAUTEUR = builder.comment(
            "Petit élan vers le haut d'un plongeon depuis le sol, en blocs par tick (un saut vaut 0,42) : 0,15 fait monter",
            "le corps d'environ 0,2 bloc. En l'air (juste après un saut), le plongeon ne donne aucun élan vers le haut.",
            "Au-dessus de 0,15, un plongeon lancé avec un saut peut passer par-dessus une clôture ou un muret (1,5 bloc).")
         .defineInRange("plongeon_hauteur", Allonge.DEFAUT_PLONGEON_HAUTEUR, 0.0, 0.3);
      DELAI_PLONGEON = builder.comment(
            "Délai (en secondes) entre deux plongeons, compté depuis le début du précédent (delai_rallonger compte aussi).",
            "Le serveur compte 0,5 s de moins (décalage du réseau).")
         .defineInRange("delai_plongeon", Allonge.DEFAUT_DELAI_PLONGEON, 0.0, 120.0);
      builder.pop();
      // HeraliZ : vitres cassées par les balles, qui repoussent (lu par le serveur seulement)
      builder.push("vitres");
      VITRES_CASSER = builder.comment(
            "Les balles des armes TaCZ cassent le verre (verre, verre coloré ou teinté, vitres et vitres colorées : la même règle",
            "que TaCZ) et les blocs de blocs_en_plus. La balle casse la vitre et s'arrête dessus ; la vitre repousse seule, au",
            "même endroit et dans le même état. Le verre gorgé d'eau ne casse pas (l'eau se répandrait). Le réglage",
            "DestroyGlass de tacz-common.toml doit rester à false : c'est HeraliZ qui casse (TaCZ ne regarde aucune zone",
            "protégée et dérange les blocs voisins). Pour savoir pourquoi une vitre ne casse pas : /vitres pourquoi (staff).",
            "false : comme avant (les blocs de verre laissent passer les balles, les vitres les arrêtent, rien ne casse).")
         .define("casser", true);
      VITRES_DELAI_MINUTES = builder.comment(
            "Délai (en minutes) avant qu'une vitre cassée repousse, compté en temps réel : une vitre dont le délai est passé",
            "pendant un arrêt du serveur repousse au redémarrage. Si quelqu'un se tient à sa place (ou du feu, de la neige,",
            "de l'eau), elle attend et réessaie. Un bloc posé à sa place, la vitre reposée à la main comprise : la main",
            "l'emporte, elle ne repousse pas. Les vitres à remettre sont gardées dans les données du monde",
            "(data/heraliz_vitres.dat, une liste par dimension).")
         .defineInRange("delai_repousse_minutes", Vitres.DEFAUT_DELAI_MINUTES, 1, 10080);
      VITRES_DIMENSIONS = builder.comment(
            "Dimensions où le verre ne casse jamais (spawn, arènes des matchs, zombies, bunkers des clans) : les balles le",
            "traversent ou s'y arrêtent comme avant.")
         .defineList("dimensions_protegees", Vitres.DEFAUT_DIMENSIONS, o -> o instanceof String);
      VITRES_RAYON_SPAWN = builder.comment(
            "Rayon (en blocs) autour du point d'apparition du monde (dimension minecraft:overworld) où le verre ne casse pas,",
            "sur toute la hauteur. 0 : aucun. Le spawn des joueurs est un monde à part (heralisz:spawn, dans",
            "dimensions_protegees) ; sur la carte, le point d'apparition est au milieu d'une ville (-435 1118 sur la carte v3) :",
            "avec 64, le verre de cette ville ne cassait jamais.")
         .defineInRange("rayon_spawn", Vitres.DEFAUT_RAYON_SPAWN, 0, 100000);
      VITRES_CLAIMS_CRAC = builder.comment("Le verre ne casse pas dans les claims de CRac (bases des groupes).")
         .define("proteger_claims_crac", true);
      VITRES_BLOCS_PROTEGES = builder.comment("Blocs de verre qui ne cassent jamais (identifiants, par exemple \"minecraft:tinted_glass\").")
         .defineList("blocs_proteges", List.of(), o -> o instanceof String);
      VITRES_BLOCS_EN_PLUS = builder.comment(
            "Autres blocs qui cassent comme une vitre (mêmes zones protégées, même délai) : \"bloc\" (il disparaît) ou",
            "\"bloc > bloc cassé\" (il devient sa version cassée, même orientation, puis redevient lui-même). Les balles de",
            "TaCZ s'y arrêtent là où ils cassent, même s'ils sont dans PassThroughBlocks. cfm:vb_4 : fenêtre à croisée grise",
            "des meubles, posée sur la carte ; cfm:vb_5 : la même, vitre brisée (les cfm:vb_5 posés à la main ne bougent pas).")
         .defineList("blocs_en_plus", Vitres.DEFAUT_BLOCS_EN_PLUS, o -> o instanceof String);
      builder.pop();
      // HeraliZ : tirs silencieux entendus de loin (lu par le serveur seulement)
      builder.push("silencieux");
      SON_LOINTAIN = builder.comment(
            "Un tir silencieux s'entend net et à sa vraie place jusqu'à la portée nette de son silencieux (10 à 20 blocs,",
            "outils/silencieux.py). Au-delà, jusqu'à la portée d'un tir normal (DefaultGunFireSoundDistance de",
            "tacz-common.toml), les autres joueurs l'entendent plus grave, plus faible, venu d'un endroit flou autour du",
            "tireur : la direction à peu près, pas l'endroit. Le serveur n'envoie que cette position floue.",
            "false : au-delà de la portée nette, on n'entend rien.")
         .define("son_lointain", SonLointain.DEFAUT_ACTIF);
      SON_LOINTAIN_VOLUME = builder.comment(
            "Volume du son lointain juste après la portée nette (comme les volumes de TaCZ, le jeu le met au carré : 0,35",
            "donne 0,12, un tir normal à 20 blocs 0,54). Il baisse ensuite jusqu'à zéro à la portée d'un tir normal.")
         .defineInRange("volume_lointain", SonLointain.DEFAUT_VOLUME, 0.0, 1.0);
      SON_LOINTAIN_FLOU = builder.comment(
            "Flou de l'endroit d'où vient le son lointain, en part de la distance au tireur (0,5 : à 100 blocs, il vient",
            "d'un point jusqu'à 50 blocs à côté du tireur ; au plus 96 blocs). Le point reste le même 30 s pour un même",
            "tireur et un même joueur. 0 : vraie place (déconseillé : elle serait envoyée au jeu du joueur).")
         .defineInRange("flou_lointain", SonLointain.DEFAUT_FLOU, 0.0, 1.0);
      builder.pop();
      // HeraliZ : couteaux TaCZ (envoyé aux joueurs à la connexion : ils lisent la même cadence que le serveur)
      builder.push("couteaux");
      COUTEAUX = builder.comment(
            "Couteaux TaCZ (Knife CT et T, karambits, papillons, M9 ; fiche technique arkaniaz:knife_data) frappés comme les",
            "armes de mêlée de HeraliZ Tactique (LR), réglés comme la Dague tactique : touche Tirer (clic gauche) = coup",
            "rapide, touche tenue = coups enchaînés ; touche Viser (clic droit) = coup puissant ; touche « Mêlée » de TaCZ (V) =",
            "coup rapide. Le serveur décide des cibles et des dégâts. false : comme avant (clic gauche sans effet, clic droit",
            "pour viser, seul le coup de TaCZ sur V : 13 dégâts, 1,4 bloc).")
         .define("actif", CoupsCouteau.DEFAUT_ACTIF);
      COUTEAUX_DEGATS = builder.comment(
            "Dégâts ajoutés à l'attaque du joueur (1 à mains nues, plus les effets Force / Faiblesse) pour un coup rapide :",
            "5,5 donne 6,5 dégâts (3,25 cœurs), comme la Dague tactique. Coup critique (en retombant d'un saut) : x1,5.")
         .defineInRange("degats", CoupsCouteau.DEFAUT_DEGATS, 0.0, 100.0);
      COUTEAUX_MULTIPLICATEUR_LOURD = builder.comment(
            "Dégâts du coup puissant (clic droit) : ceux du coup rapide multipliés par cette valeur (1,5 : 9,75 dégâts).")
         .defineInRange("multiplicateur_lourd", CoupsCouteau.DEFAUT_MULTIPLICATEUR_LOURD, 1.0, 10.0);
      COUTEAUX_CADENCE_RAPIDE = builder.comment(
            "Ticks (20 par seconde) avant le coup suivant, après un coup rapide (9 : 0,45 s, comme la Dague tactique).")
         .defineInRange("cadence_rapide", CoupsCouteau.DEFAUT_CADENCE_RAPIDE, CoupsCouteau.CADENCE_MIN, 200);
      COUTEAUX_CADENCE_LOURD = builder.comment(
            "Ticks avant le coup suivant, après un coup puissant (18 : 0,9 s, comme la Dague tactique). Au moins 7 : le",
            "coup puissant ne touche qu'après sa préparation (0,25 s).")
         .defineInRange("cadence_lourd", CoupsCouteau.DEFAUT_CADENCE_LOURD, CoupsCouteau.CADENCE_MIN_LOURD, 400);
      COUTEAUX_ANGLE_RAPIDE = builder.comment(
            "Ouverture (en degrés) du cône du coup rapide devant le regard, moitié de chaque côté (65, comme la Dague",
            "tactique). Tous les êtres vivants dans le cône, à portée ([melee] portee_max) et en vue, sont touchés.")
         .defineInRange("angle_rapide", CoupsCouteau.DEFAUT_ANGLE_RAPIDE, 5.0, 180.0);
      COUTEAUX_ANGLE_LOURD = builder.comment("Ouverture (en degrés) du cône du coup puissant (75, comme la Dague tactique).")
         .defineInRange("angle_lourd", CoupsCouteau.DEFAUT_ANGLE_LOURD, 5.0, 180.0);
      builder.pop();
      return builder.build();
   }
}
