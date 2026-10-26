package fr.heralisz.n07.api;

import net.minecraft.util.Mth;

/**
 * Equilibrage par defaut du Sujet N-07 en mode Zombies (facon boss CoD) : PV selon le nombre de joueurs et la manche,
 * degats ramenes a la sante des joueurs Zombies (20 PV, 50 avec Juggernog). Valeurs a affiner en jeu ; tout est
 * remplacable par combat (EncounterSpec).
 * <ul>
 *    <li>Cible : 4 a 8 min en solo avec des armes Pack-a-Punch et des atouts (manche ~20) ; plus court a 4.</li>
 *    <li>Gros coups (charge, bloc lance, abattage au centre) : 22-26 PV = ~45-50 % d'un joueur Juggernog, un joueur sans
 *    Juggernog tombe (comme dans CoD). Coups moyens : 10-14. Petits : 1-4.</li>
 *    <li>Jamais un joueur Juggernog plein de vie tue d'un coup (plafond 48), sauf la criticite (MELTDOWN), tres annoncee
 *    (cercle de 24 blocs, alarme, 3,5 s) : jusqu'a 55 au contact du boss si l'on ne s'abrite pas.</li>
 * </ul>
 */
public final class ZombiesBalance {
   private ZombiesBalance() {}

   /** PV virtuels de reference : 1 joueur, manche 20, armes Pack-a-Punch. */
   public static final int BASE_HP = 26000;
   /** Part de PV en plus par joueur au-dela du premier. */
   public static final float HP_PER_EXTRA_PLAYER = 0.75F;
   /** Variation par manche autour de la manche 20 (+2,5 % par manche), bornee a [0,75 ; 1,75]. */
   public static final float HP_PER_ROUND = 0.025F;
   /** Facteur si les tirs sur le boss ne recoivent PAS le bonus du Pack-a-Punch cote jeu. */
   public static final float NO_PAP_FACTOR = 0.4F;
   /** Plafond des degats d'un coup sur un joueur (hors MELTDOWN). */
   public static final float MAX_HIT = 48F;

   public static int virtualHp(int players, int round, boolean papOnBossHits) {
      int n = Math.max(1, players);
      float rf = Mth.clamp(1 + HP_PER_ROUND * (round - 20), 0.75F, 1.75F);
      float pf = 1 + HP_PER_EXTRA_PLAYER * (n - 1);
      return Math.round(BASE_HP * rf * pf * (papOnBossHits ? 1F : NO_PAP_FACTOR));
   }

   /** Degats bruts retenus par tir (au-dela : rabotes) : une arme speciale ne fait pas fondre le boss. */
   public static float hitCap() {
      return 60F;
   }

   /** Au-dela de ces degats bruts par seconde, le surplus ne compte qu'a 25 %. */
   public static float dpsCap(int players) {
      return 700F + 500F * (Math.max(1, players) - 1);
   }

   /** Echelle des degats selon la manche : 1 jusqu'a la manche 20, puis +1 % par manche (max +20 %). */
   public static float damageScaleForRound(int round) {
      return Mth.clamp(1 + 0.01F * (round - 20), 1F, 1.2F);
   }

   /** Degats Zombies (PV) d'une attaque ; base = valeur de reference du boss (sert aux attaques degressives). */
   public static float damage(String attack, float base, int phase) {
      float p3 = phase >= 3 ? 1.1F : 1F;
      return switch (attack) {
         case "pool", "aura", "spray_hug" -> 2F;
         case "irradiated" -> 1F;
         case "drop", "beam" -> 4F;
         case "slam_column" -> 10F * p3;
         case "claw", "shell", "geyser" -> 12F * p3;
         case "arc" -> 12F * p3 * Mth.clamp(base / 16F, 0.3F, 1F);
         case "geyser_fist", "isotope", "pulse" -> 14F * p3;
         case "slam" -> 22F * p3;
         case "charge", "boulder" -> 24F * p3;
         // criticite : 34 x (1 - d/30), au moins 12 -> 25..55
         case "meltdown" -> Mth.clamp(25F + 30F * (base - 12F) / 22F, 25F, 55F);
         // pendant les cinematiques les joueurs sont proteges de toute facon
         case "p2_burst", "p3_nova" -> 0F;
         default -> Math.min(MAX_HIT, base * 0.5F);
      };
   }

   /** Remappage par defaut : table ci-dessus x scale, plafonne a maxHit (sauf MELTDOWN). */
   public static DamageRemap remap(float scale, float maxHit) {
      return (player, attack, base, current, phase) -> {
         float d = damage(attack, base, phase) * scale;
         return attack.equals("meltdown") ? d : Math.min(maxHit, d);
      };
   }
}
