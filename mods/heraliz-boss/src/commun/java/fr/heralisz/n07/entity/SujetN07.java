package fr.heralisz.n07.entity;

import fr.heralisz.n07.sound.BossSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * Sujet N-07, « le Chimiste Irradié » : ce qui reste du chimiste en chef d'un laboratoire militaire après l'expérience. Brute
 * voûtée, bras droit monstrueux aux crochets qui raclent le sol, respirateur industriel, cuve de liquide radioactif sanglée dans
 * le dos. Pensé pour un combat aux armes à feu : il harcèle les tireurs à distance et récompense la mise à couvert.
 *  Phase 1 : griffe, fissure (abattage), mortier d'acide tiré par la vanne de sa cuve (flaques), bloc de béton arraché et lancé
 *            sur le tireur le plus éloigné, charge, onde radioactive au contact.
 *  Phase 2 : la cuve explose et le liquide le recouvre, des os lui percent les épaules : isotopes instables arrachés à ses
 *            tumeurs qui dérivent vers les tireurs (on peut les abattre en vol), geysers sous les pieds des joueurs, aspersion.
 *  Phase 3 : il arrache son respirateur et se plante le tuyau de la cuve dans le cœur : rayon (arrêté par les murs), arc
 *            radioactif qui saute de joueur en joueur, criticité (il faut se mettre à couvert), charges en série.
 *  Mort : à genoux, il contient son cœur qui s'emballe, se cambre en hurlant ; la fusion le vitrifie : il reste 5 minutes,
 *  statue de trinitite verte et crépitante, et son cœur libère le butin.
 * <p>
 * Partie COMMUNE (docs/METHODE_CLIENT_SERVEUR.md §5.5) : état synchronisé, repères du corps, HUD, cinématiques, animations.
 * IA, attaques, phases, dégâts, mort scénarisée, butin, sons : SujetN07Serveur (créé par l'usine HeraliszN07.SUJET_N07_USINE).
 */
public class SujetN07 extends HeraliszBoss<SujetN07.Action> {
   public enum Action implements HeraliszBoss.Act {
      NONE(0), SPAWN(240), PHASE2(148), PHASE3(176), CLAW(28), SLAM(40), MORTAR(52), HURL(48), CHARGE(60), PULSE(44), ISOTOPES(52),
      GEYSERS(48), SPRAY(56), BEAM(84), ARC(40), MELTDOWN(100);
      private final int ticks;

      Action(int t) {
         this.ticks = t;
      }

      @Override
      public int ticks() {
         return ticks;
      }
   }

   /** Échelle du rendu (toutes les positions du corps en dépendent). */
   public static final float RS = 2.0F;
   /** Instants clés (ticks d'action), partagés avec le rendu. */
   /** Évasion de la cuve de confinement : les yeux s'allument, deux coups contre le verre, la cuve explose, il retombe dans le
    * déluge, sa cuve dorsale se remplit du liquide, il se redresse et rugit. */
   public static final int SPAWN_PRE = 100, SPAWN_EYES = SPAWN_PRE + 14, SPAWN_HIT1 = SPAWN_PRE + 36, SPAWN_HIT2 = SPAWN_PRE + 50,
      SPAWN_BREAK = SPAWN_PRE + 60, SPAWN_LAND = SPAWN_PRE + 72, SPAWN_ROAR = SPAWN_PRE + 108;
   public static final int SPAWN_FILL0 = SPAWN_PRE + 74, SPAWN_FILL1 = SPAWN_PRE + 98, P2_BURST = 71, P2_BONES = 82, P3_MASK = 62, P3_STAB = 116,
      BEAM_FIRE = 26, BEAM_END = 72, MELT_BOOM = 70;
   /** Mort : à genoux (32), il hurle (74), la fusion le vitrifie (VITRIFY), butin (150), statue (DEATH_TICKS). */
   public static final int DEATH_TICKS = 260, VITRIFY = 148, VITRIFY_TICKS = 14;
   public static final int RAD = 0x7CFF3A, HOT = 0xE8FFC8;

   static final EntityDataAccessor<Vector3f> BEAM_END_POS = SynchedEntityData.defineId(SujetN07.class, EntityDataSerializers.VECTOR3);

   public SujetN07(EntityType<? extends SujetN07> type, Level level) {
      super(type, level, Action.class, "sujet_n07");
      this.setMaxUpStep(1.5F);
      this.xpReward = 600;
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0).add(Attributes.ARMOR, 14.0).add(Attributes.ARMOR_TOUGHNESS, 8.0)
         .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 56.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(BEAM_END_POS, new Vector3f());
   }

   @Override
   protected RawAnimation moveAnimation(boolean moving) {
      return RawAnimation.begin().thenLoop(moving ? (phase() == 3 ? "animation.sujet_n07.run" : "animation.sujet_n07.walk") : "animation.sujet_n07.idle");
   }

   // ------------------------------------------------------------------ repères du corps (modèle x RS, en blocs)
   Vec3 at(double front, double right, double up) {
      return this.position().add(fwd().scale(front)).add(right().scale(right)).add(0, up, 0);
   }

   /** Respirateur (phases 1-2) ou gorge à nu (phase 3) : d'où part le rayon. */
   public Vec3 mouth() {
      return phase() == 3 ? at(1.9, 0, 5.0) : at(2.1, 0, 5.2);
   }

   public Vec3 core() {
      return at(1.25, 0, 3.9);
   }

   public Vec3 leftHand() {
      return at(0.8, -1.9, 2.1);
   }

   /** Extrémité du rayon (synchronisée pour le rendu). */
   public Vec3 beamEnd() {
      Vector3f v = this.entityData.get(BEAM_END_POS);
      return new Vec3(v.x, v.y, v.z);
   }

   public boolean beamFiring() {
      int t = actionTime();
      return action() == Action.BEAM && t >= BEAM_FIRE && t < BEAM_END;
   }

   // ------------------------------------------------------------------ BossInfo
   @Override
   public String hudName() {
      return "SUJET N-07";
   }

   @Override
   public int hudTop(int ph) {
      return ph == 3 ? 0xE8FF9A : ph == 2 ? 0xB8FF6A : 0x7CFF3A;
   }

   @Override
   public int hudBottom(int ph) {
      return ph == 3 ? 0x2E6A0A : 0x1D4A0A;
   }

   @Override
   public int hudNameColor() {
      return 0xE0F0D0;
   }

   @Override
   public int accentColor() {
      return phase() == 3 ? 0xD8FF8A : RAD;
   }

   @Override
   public SoundEvent music() {
      // combat pilote : le jeu joue sa propre musique
      return (flags() & FLAG_NO_MUSIC) != 0 ? null : BossSounds.MUSIC_N07.get();
   }

   private static final float[][][] CINE = {
      // apparition (12 s) : le labo plongé dans le noir, l'alarme tourne (très large, en hauteur) ; derrière la cuve, sa
      // silhouette qui dérive dans le liquide ; gros plan sur le masque éteint ; puis l'évasion (de loin dans la pénombre,
      // rapproché sur le masque qui s'allume, large pour l'impact et le rugissement ; on reste hors du verre de la cuve)
      {{0, 17, 12, 6, 3F}, {0.12F, 15, 11, 5.5F, 3.5F}, {0.14F, 12, -10, 2.5F, 4F, 1}, {0.26F, 11, -9, 2.8F, 4.5F}, {0.28F, 9, 8, 6, 5.4F, 1}, {0.4F, 8.5F, 7.5F, 6, 5.4F}, {0.417F, 14, 10, 3, 3, 1}, {0.58F, 9, 7, 4.2F, 4.4F}, {0.615F, 4.5F, 4.5F, 5.4F, 5}, {0.738F, 5, -6, 4.5F, 3.6F}, {0.825F, 10, -9, 3, 3.4F}, {0.883F, 9, 9, 3.4F, 4.6F}, {1, 13, 11, 4.4F, 5}},
      // phase 2 (7,4 s) : de trois-quarts arrière, il plaque la main sur la cuve qui bout ; gros plan sur le verre qui se fêle ;
      // large quand elle explose ; de face, les os lui percent les épaules ; contre-plongée sur le hurlement
      {{0, -9, -9, 6.5F, 5.5F}, {0.25F, -8, -10, 7F, 6F}, {0.27F, -3, -3.5F, 6.5F, 6F, 1}, {0.46F, -2.6F, -3.2F, 6.6F, 6.2F},
         {0.48F, -12, -13, 7.5F, 5.5F, 1}, {0.56F, -13, -14, 8, 5.5F}, {0.58F, 6, 3, 6, 6F, 1}, {0.64F, 5.5F, 2.5F, 6, 6.2F},
         {0.66F, 9, -7, 2, 6F, 1}, {1, 12, -8, 2.5F, 5.6F}},
      // phase 3 (8,8 s) : gros plan, il agrippe et arrache son respirateur ; il rugit gueule à nu ; de côté, il ramène le tuyau ;
      // sur le cœur quand il se le plante ; recul large sur la nova
      {{0, 7, 3, 5.6F, 5.2F}, {0.32F, 5.5F, 2, 5.4F, 5.2F}, {0.34F, 4.5F, -1, 5.6F, 5.6F, 1}, {0.5F, 4.2F, -0.8F, 5.5F, 5.6F},
         {0.52F, 8, -6, 4, 4.5F, 1}, {0.64F, 7.5F, -6.5F, 4.2F, 4.4F}, {0.66F, 5, 2.5F, 4.2F, 4F, 1}, {0.8F, 5.5F, 2.5F, 4F, 4F},
         {0.82F, 13, 7, 4.5F, 4.6F, 1}, {1, 17, 9, 6, 4.6F}},
      // mort (13 s) : il tombe à genoux (large) ; gros plan sur les mains plaquées sur le cœur qui s'emballe ; de profil, il tend
      // un bras vers nous ; bas, de face, il hurle et la fusion le fige ; recul lent autour de la statue de verre
      {{0, 12, 6, 3F, 3F}, {0.15F, 9, 7, 2.6F, 3.2F}, {0.17F, 4, 2, 1.6F, 2.4F, 1}, {0.38F, 3.6F, 1.8F, 1.6F, 2.5F},
         {0.4F, 7, -5, 2, 2.6F, 1}, {0.5F, 6.5F, -4.5F, 2.2F, 2.8F}, {0.52F, 7, 3.5F, 1.2F, 4F, 1}, {0.6F, 8, 4, 2.2F, 4.2F},
         {0.8F, 11, -6, 3.5F, 3.6F}, {1, 16, -10, 5, 3.2F}}
   };

   @Override
   public float[][] cinematicKeys(int kind) {
      return CINE[Mth.clamp(kind, 0, 3)];
   }

   @Override
   public String[] cinematicTitles(int kind) {
      // cles de traduction (fr_fr / en_us) ; le HUD les traduit
      String k = switch (kind) {
         case PHASE2 -> "phase2";
         case PHASE3 -> "phase3";
         case DEATH -> "death";
         default -> "spawn";
      };
      String base = "cine.heralisz_n07." + k + ".";
      return new String[] {base + "top", base + "title", base + "sub"};
   }

   @Override
   public float cinematicTitleAt(int kind) {
      return kind == SPAWN ? 0.87F : kind == DEATH ? 0.6F : kind == PHASE3 ? 0.66F : 0.6F;
   }

   @Override
   public boolean cinematicAnchored(int kind) {
      return true;
   }

   // ------------------------------------------------------------------ mort : fusion, il se vitrifie
   /** Progression de la vitrification (0 → 1), lue par le rendu. */
   public float vitrify(float partial) {
      if (!this.isDeadOrDying()) return 0;
      return Mth.clamp((this.deathTime + partial - VITRIFY) / VITRIFY_TICKS, 0, 1);
   }
}
