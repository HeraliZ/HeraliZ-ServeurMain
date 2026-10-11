package fr.heralisz.plague.entity;

import fr.heralisz.plague.sound.Sounds;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Le Porteur de Peste : un soldat d'une unité NBC envoyée contenir l'épidémie, fondu dans sa combinaison. Masque à gaz greffé
 * dans le crâne, bonbonnes de peste dans le dos, bras droit monstrueux, crochet de boucher dans la main gauche.
 *  Phase 1 : griffe, écrasement, grenades à gaz (nappes qui stagnent), crochet (accroche le tireur exposé et le ramène ; s'il se
 *            cache, le crochet mord le mur et le Porteur s'y hisse), jet de bile, charge, vacarme sur ses bonbonnes qui réveille
 *            les infectés autour des joueurs.
 *  Phase 2 : les bonbonnes éclatent, des tumeurs percent la combinaison ; aura toxique ; essaims de mouches qui traquent les
 *            tireurs, pustules arrachées et lancées en éventail.
 *  Phase 3 : il arrache son masque (Patient Zéro) ; pluie de spores sur toute l'arène, vague de pandémie qu'il faut sauter,
 *            bond sur le tireur le plus éloigné ; les infectés éclatent en gaz.
 *  Mort : à genoux, une dernière bouffée d'air, la moisissure le recouvre et une colonie de champignons luminescents éclot sur
 *  lui ; la dépouille reste 5 minutes.
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.1, §5.5) : actions et constantes, attributs, animations, HUD, plans des
 * cinématiques, moisissure et champignons lus par le rendu. L'IA, les attaques, la mort scénarisée, le butin et la salle sont
 * dans PlagueBearerServeur (créé par HeraliszPlague.PLAGUE_BEARER_USINE dans les mondes serveur).
 */
public class PlagueBearer extends HeraliszBoss<PlagueBearer.Action> {
   public enum Action implements HeraliszBoss.Act {
      NONE(0), SPAWN(200), PHASE2(156), PHASE3(176), CLAW(28), SLAM(36), GRENADES(46), HOOK(30), HAUL(40), GRAPPLE(36), BILE(64),
      CHARGE(60), CLANG(56), SWARM(50), PUSTULES(44), SPORES(60), PANDEMIC(56), LEAP(56);
      final int ticks;

      Action(int t) {
         this.ticks = t;
      }

      @Override
      public int ticks() {
         return ticks;
      }
   }

   public static final float RS = 1.45F;
   /** Cinématiques. */
   public static final int SPAWN_K = 0, PHASE2_K = 1, PHASE3_K = 2, DEATH_K = 3, ENTRY_K = 4, OFFER_K = 5;
   /** La dépouille reste 5 minutes. */
   public static final int REMAINS = 6000;
   /** Mort : à genoux (30), dernière bouffée d'air (66-76), la moisissure le gagne (MOLD_AT), les champignons éclosent (BLOOM_AT),
    * butin (140), dépouille (170). */
   public static final int DEATH_TICKS = 230, MOLD_AT = 136, MOLD_TICKS = 40, BLOOM_AT = 156;
   /** Vert-jaune du gaz, vert pâle des spores. */
   public static final int GAS = 0x9ACD32, SPORE = 0xC8FF8A;

   public PlagueBearer(EntityType<? extends PlagueBearer> type, Level level) {
      super(type, level, Action.class, "plague_bearer");
      this.setMaxUpStep(1.5F);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 14.0).add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 56.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   @Override
   protected RawAnimation moveAnimation(boolean moving) {
      return RawAnimation.begin().thenLoop(moving ? (phase() == 3 ? "animation.plague_bearer.run" : "animation.plague_bearer.walk")
         : "animation.plague_bearer.idle");
   }

   // ------------------------------------------------------------------ HUD
   /** Clé de traduction du nom (traduite par l'affichage, chez le joueur). */
   public String hudName() {
      return "hud.heralisz_plague.name";
   }

   public int hudTop(int ph) {
      return ph == 3 ? 0xD8FF7A : ph == 2 ? 0xB8E04A : 0x9CC23A;
   }

   public int hudBottom(int ph) {
      return ph == 3 ? 0x3A5A0A : 0x2E4A0E;
   }

   public int hudNameColor() {
      return 0xE8F0C8;
   }

   @Override
   public int accentColor() {
      return phase() == 3 ? 0xC8FF6A : 0x9ACD32;
   }

   private static final float[][][] CINE = {
      // réveil (10 s) : la salle noyée de gaz, de haut ; travelling bas à travers la brume ; gros plan sur le masque éteint ; puis
      // les verres s'allument, il se relève et abat sa griffe, rugissement
      {{0, 30, 14, 12, 1}, {0.2F, 26, 12, 10, 1.5F}, {0.22F, 18, -8, 1.2F, 1.5F, 1}, {0.42F, 13, -6, 1.4F, 2}, {0.44F, 5.5F, 2.5F, 2.4F, 2.2F, 1}, {0.53F, 5, 2.2F, 2.4F, 2.3F}, {0.536F, 12, 5, 2.2F, 2.2F, 1}, {0.584F, 6.5F, 2.5F, 3.2F, 2.6F}, {0.753F, 8, 3.5F, 4.2F, 4.4F}, {0.857F, 10, 6, 3, 3.8F}, {1, 12, 7, 3.5F, 4.2F}},
      // phase 2 (7,8 s) : de trois quarts arrière, les bonbonnes enflent ; gros plan sur les valves qui sifflent ; elles explosent
      {{0, -7, -8, 5, 4.5F}, {0.38F, -6, -9, 5.5F, 4.6F}, {0.4F, -3.5F, -4, 6.5F, 5.5F, 1}, {0.5F, -3, -3.6F, 6.6F, 5.6F}, {0.53F, 7, -9, 4, 4.5F, 1}, {1, 10, -7, 3.5F, 4.5F}},
      // phase 3 (8,8 s) : il agrippe son masque ; gros plan de profil pendant qu'il tire ; arraché ; recul large sur le rugissement
      {{0, 7, 3, 4.6F, 4.4F}, {0.2F, 6, 2.5F, 4.6F, 4.4F}, {0.22F, 4, -1.5F, 4.8F, 4.6F, 1}, {0.56F, 3.8F, -1.3F, 4.7F, 4.6F}, {0.58F, 5.2F, 2, 4.5F, 4.4F, 1}, {0.656F, 7, 4, 4.2F, 4.6F}, {0.75F, 10, 6, 3.5F, 4.4F, 1}, {1, 14, 8, 4.5F, 4.4F}},
      // mort (12,5 s) : il tombe à genoux ; gros plan, il étouffe ; de profil ; la moisissure le gagne ; recul lent
      {{0, 11, 5, 3, 2.6F}, {0.28F, 8, 7, 2.5F, 2.6F}, {0.3F, 4.5F, 2, 1.6F, 2.2F, 1}, {0.5F, 4.2F, 1.8F, 1.7F, 2.4F}, {0.52F, 9, -5, 2, 2.6F, 1}, {0.667F, 6.5F, 3.5F, 2.4F, 3.6F}, {0.8F, 10, -7, 3, 3}, {1, 14, -10, 5, 3.2F}},
      // entrée (15 s) — repère : le rideau métallique, devant = vers la station. Derrière l'équipe, le rideau qui se lève dans un
      // nuage de gaz ; on avance avec eux dans le sas, sous les jets de décontamination ; ils débouchent : la station noyée de
      // gaz, et au fond, dans le bassin, la silhouette agenouillée
      {{0, -9, 1.5F, 2.6F, 3F, 0, 1, 0}, {0.12F, -8, 1.2F, 2.5F, 3.5F, 0, 1, 0}, {0.2F, -5, 0.6F, 2.2F, 2.6F, 0, 6, 0},
         {0.38F, 4, 0.4F, 2.2F, 2.4F, 0, 14, 0}, {0.5F, 12, -3, 3.5F, 3F, 0, 26, 0}, {0.6F, 16, -5, 4, 2F, 0, 32, 0},
         {0.62F, 14, 3, 2.6F, 3F, 1, -4, 0}, {0.76F, 14, 3.4F, 2.8F, 3.2F, 0, -4, 0}, {0.8F, 20, 6, 6, 1F, 1, 32, 0}, {1, 24, 8, 7, 1F, 0, 32, 0}},
      // offrande (10 s) — repère : la vanne de purge, devant = vers le rideau. Le volant posé sur la vanne ; il tourne ; la
      // pression monte dans les tuyaux jusqu'au rideau soudé
      {{0, -3, 2, 2.2F, 1.4F}, {0.3F, -3.4F, 2.4F, 2.3F, 1.6F}, {0.45F, -4, 3, 3, 2.4F, 0, 5, 0}, {0.75F, -3, 3.5F, 3, 3F, 0, 8, 0},
         {1, -2, 4, 3.2F, 3.5F, 0, 10, 0}}
   };

   @Override
   public float[][] cinematicKeys(int kind) {
      return CINE[Mth.clamp(kind, 0, CINE.length - 1)];
   }

   /** Clés de traduction {bandeau, titre, sous-titre} ; le HUD les traduit chez le joueur. */
   @Override
   public String[] cinematicTitles(int kind) {
      return switch (kind) {
         case PHASE2_K -> new String[] {"title.heralisz_plague.p2.top", "title.heralisz_plague.p2.name", "title.heralisz_plague.p2.sub"};
         case PHASE3_K -> new String[] {"title.heralisz_plague.p3.top", "title.heralisz_plague.p3.name", "title.heralisz_plague.p3.sub"};
         case ENTRY_K -> new String[] {"title.heralisz_plague.entry.top", "title.heralisz_plague.entry.name", "title.heralisz_plague.entry.sub"};
         case OFFER_K -> new String[] {"", "", ""};
         case DEATH_K -> new String[] {"title.heralisz_plague.death.top", "hud.heralisz_plague.name", "title.heralisz_plague.death.sub"};
         default -> new String[] {"title.heralisz_plague.spawn.top", "hud.heralisz_plague.name", "title.heralisz_plague.spawn.sub"};
      };
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN_K ? 0.86F : kind == DEATH_K ? 0.6F : kind == ENTRY_K ? 0.08F : kind == OFFER_K ? 2F : 0.6F;
   }

   public boolean cinematicAnchored(int kind) {
      return true;
   }

   public net.minecraft.sounds.SoundEvent music() {
      return Sounds.MUSIC_PLAGUE_BEARER.get();
   }

   // ------------------------------------------------------------------ mort : à genoux, dernière bouffée, moisissure, champignons
   /** Progression de la moisissure sur le corps (0 → 1), lue par le rendu. */
   public float mold(float partial) {
      if (!this.isDeadOrDying()) return 0;
      return Mth.clamp((this.deathTime + partial - MOLD_AT) / MOLD_TICKS, 0, 1);
   }

   /** Croissance des champignons de la mort (0 → 1), lue par le rendu. */
   public float bloom(float partial) {
      if (!this.isDeadOrDying()) return 0;
      return Mth.clamp((this.deathTime + partial - BLOOM_AT) / 44F, 0, 1);
   }
}
