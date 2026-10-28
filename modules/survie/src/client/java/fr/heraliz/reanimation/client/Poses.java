package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Poses {
   public static final float REACH = 0.1F;
   public static final float PULL = 0.86F;
   public static final int BEAT = 12;
   private static final float KNEEL_LEGS = 1.2F;
   private static final double KNEEL_DROP = 0.32;
   // bras d'un joueur à terre (radians) : au repos le long du corps, mains un peu vers l'avant ; tendu devant la tête
   private static final float ARM_REST_X = -0.25F;
   private static final float ARM_REST_Z = 0.12F;
   private static final float ARM_REACH_Z = 2.84F;
   private static final ArrayDeque<Boolean> RENDERING = new ArrayDeque<>();

   private Poses() {
   }

   public static float pump(float reviveTicks) {
      float c = reviveTicks % 12.0F / 12.0F;
      return c < 0.4F ? Mth.sin(c / 0.4F * (float) Math.PI) : 0.0F;
   }

   /**
    * Pose de Réanimation (à terre, fouille, réanimation, exécution). 14/10 : la remise à zéro des décalages (tête, torse,
    * jambes) n'est gardée que si une pose est appliquée ; sinon les valeurs d'avant sont rendues. Avant, elle effaçait
    * à chaque image, chez tous les joueurs, ce que Player Animator venait de poser (emotes, allongé de TaCZ, mêlée) :
    * torse et tête revenaient au centre, bras et jambes non (personnage « démembré »).
    */
   static boolean apply(HumanoidModel<?> m, LivingEntity e, float limbSwing, float limbAmount, float age) {
      // Cas de presque tous les joueurs à chaque image : ni scène d'exécution, ni état « à terre » reçu. Aucune pose ne
      // peut alors s'appliquer (poser rendrait faux et tout serait remis comme avant) : on ne touche à rien, sans la
      // copie des valeurs ni l'allocation d'avant.
      if (ExecutionScenes.aucuneScene() && ClientDowned.get(e.getId()) == null) {
         return false;
      }
      float[] avant = {m.head.x, m.head.z, m.head.zRot, m.body.x, m.body.z, m.body.zRot, m.rightLeg.x, m.leftLeg.x};
      boolean pose = poser(m, e, limbSwing, limbAmount, age);
      if (!pose) {
         m.head.x = avant[0];
         m.head.z = avant[1];
         m.head.zRot = avant[2];
         m.body.x = avant[3];
         m.body.z = avant[4];
         m.body.zRot = avant[5];
         m.rightLeg.x = avant[6];
         m.leftLeg.x = avant[7];
      }
      return pose;
   }

   private static boolean poser(HumanoidModel<?> m, LivingEntity e, float limbSwing, float limbAmount, float age) {
      m.head.x = 0.0F;
      m.head.z = 0.0F;
      m.head.zRot = 0.0F;
      m.body.x = 0.0F;
      m.body.z = 0.0F;
      m.body.zRot = 0.0F;
      m.rightLeg.x = -1.9F;
      m.leftLeg.x = 1.9F;
      if (e instanceof Player && RENDERING.isEmpty()) {
         return false;
      }

      if (ExecutionScenes.pose(m, e, age)) {
         return true;
      }

      ClientDowned.Info info = ClientDowned.get(e.getId());
      if (info == null) {
         return false;
      }

      float partial = age - e.tickCount;
      if (info.has(8)) {
         search(m, age);
         return true;
      }

      if (info.has(16)) {
         revive(m, info.reviveTicks(partial), info.reviveProgress(partial));
         return true;
      }

      if (info.has(1)) {
         lying(m, limbSwing, limbAmount);
         if (info.has(4)) {
            revived(m, info.reviveProgress(partial));
         }

         return true;
      } else {
         return false;
      }
   }

   private static void lying(HumanoidModel<?> m, float limbSwing, float limbAmount) {
      // Réglé sur la vraie reptation (crawlSpeed 0,35 x ramper vanilla : limbAmount ~0,09, soit 4 x les blocs par tick) :
      // mouvement plein dès qu'il rampe, un cycle (bras droit puis bras gauche) par bloc environ. Les anciens réglages
      // (x2,5 et x0,35) donnaient à cette vitesse un quart de mouvement et un cycle de 10 secondes.
      float moving = Mth.clamp(limbAmount * 15.0F, 0.0F, 1.0F);
      float stroke = Mth.sin(limbSwing * 1.6F);
      float kick = moving * stroke * 0.28F;
      m.rightLeg.xRot = 0.04F;
      m.leftLeg.xRot = 0.04F;
      m.rightLeg.yRot = 0.0F;
      m.leftLeg.yRot = 0.0F;
      m.rightLeg.zRot = 0.07F + Math.max(0.0F, kick);
      m.leftLeg.zRot = -0.07F - Math.max(0.0F, -kick);
      // Bras au repos : le long du corps, un peu écartés, mains légèrement vers l'avant (posées au sol quand il est à plat).
      // Avant : les deux bras tendus au-dessus de la tête (-3,05), ce qui faisait « bras levés » dès que le corps restait
      // droit. En rampant, un bras à la fois passe par le côté (rotation z, au ras du sol, jamais à travers) et va se
      // tendre devant la tête pour tirer le corps, puis revient le long du flanc ; immobile, plus aucun bras tendu.
      float reachRight = moving * smooth(Math.max(0.0F, stroke));
      float reachLeft = moving * smooth(Math.max(0.0F, -stroke));
      m.rightArm.xRot = ARM_REST_X;
      m.leftArm.xRot = ARM_REST_X;
      m.rightArm.yRot = 0.0F;
      m.leftArm.yRot = 0.0F;
      m.rightArm.zRot = Mth.lerp(reachRight, ARM_REST_Z, ARM_REACH_Z);
      m.leftArm.zRot = -Mth.lerp(reachLeft, ARM_REST_Z, ARM_REACH_Z);
      m.head.xRot = Math.min(m.head.xRot, -0.3F);
   }

   private static void revived(HumanoidModel<?> m, float p) {
      if (p > 0.86F) {
         // fin de la réanimation : la main droite va se poser au sol devant l'épaule pour se relever (par le côté, comme
         // en rampant)
         float k = smooth((p - 0.86F) / 0.13999999F);
         m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.35F);
         m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 2.64F);
      }
   }

   private static void search(HumanoidModel<?> m, float age) {
      float t = age * 0.42F;
      kneel(m, 0.4F + 0.05F * Mth.sin(age * 0.13F));
      m.rightArm.xRot = -0.75F + 0.3F * Mth.sin(t);
      m.leftArm.xRot = -0.75F + 0.3F * Mth.sin(t + 2.3F);
      m.rightArm.yRot = -0.2F + 0.15F * Mth.sin(t * 0.5F);
      m.leftArm.yRot = 0.2F + 0.15F * Mth.sin(t * 0.5F + 1.1F);
      m.rightArm.zRot = -0.12F + 0.1F * Mth.cos(t);
      m.leftArm.zRot = 0.12F - 0.1F * Mth.cos(t + 2.3F);
      m.head.xRot = 0.55F + 0.06F * Mth.sin(age * 0.19F);
      m.head.yRot = 0.3F * Mth.sin(age * 0.06F);
      m.head.zRot = 0.0F;
   }

   private static void revive(HumanoidModel<?> m, float ticks, float p) {
      m.head.yRot = 0.0F;
      m.head.zRot = 0.0F;
      m.rightArm.yRot = 0.0F;
      m.leftArm.yRot = 0.0F;
      if (p < 0.1F) {
         float k = smooth(p / 0.1F);
         kneel(m, 0.1F + 0.35F * k);
         m.rightArm.xRot = Mth.lerp(k, -0.2F, -0.62F);
         m.leftArm.xRot = Mth.lerp(k, -0.2F, -0.62F);
         m.rightArm.zRot = -0.28F * k;
         m.leftArm.zRot = 0.28F * k;
         m.head.xRot = 0.45F + 0.2F * k;
      } else if (p < 0.86F) {
         float push = pump(ticks);
         kneel(m, 0.45F + 0.2F * push);
         m.rightArm.xRot = -0.62F + 0.22F * push;
         m.leftArm.xRot = -0.62F + 0.22F * push;
         m.rightArm.zRot = -0.28F;
         m.leftArm.zRot = 0.28F;
         m.head.xRot = 0.65F + 0.1F * push;
      } else {
         float k = smooth((p - 0.86F) / 0.13999999F);
         kneel(m, Mth.lerp(k, 0.45F, 0.05F));
         m.rightArm.xRot = Mth.lerp(k, -0.62F, -1.25F);
         m.rightArm.zRot = Mth.lerp(k, -0.28F, 0.0F);
         m.leftArm.xRot = Mth.lerp(k, -0.62F, -0.3F);
         m.leftArm.zRot = Mth.lerp(k, 0.28F, 0.15F);
         m.head.xRot = Mth.lerp(k, 0.65F, 0.3F);
      }
   }

   private static void kneel(HumanoidModel<?> m, float lean) {
      float c = Mth.cos(lean);
      float s = Mth.sin(lean);
      m.body.xRot = lean;
      m.body.y = 12.0F - 12.0F * c;
      m.body.z = -12.0F * s;
      m.head.y = m.body.y;
      m.head.z = m.body.z;
      m.rightArm.y = m.body.y + 2.0F * c;
      m.rightArm.z = m.body.z + 2.0F * s;
      m.leftArm.y = m.rightArm.y;
      m.leftArm.z = m.rightArm.z;
      m.rightLeg.y = 12.0F;
      m.rightLeg.z = 0.0F;
      m.leftLeg.y = 12.0F;
      m.leftLeg.z = 0.0F;
      m.rightLeg.xRot = 1.2F;
      m.leftLeg.xRot = 1.2F;
      m.rightLeg.yRot = 0.08F;
      m.leftLeg.yRot = -0.08F;
      m.rightLeg.zRot = 0.1F;
      m.leftLeg.zRot = -0.1F;
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   static boolean pushTransform(LivingEntity e, PoseStack poseStack, float partialTick) {
      if (ExecutionScenes.push(e, poseStack, partialTick)) {
         return true;
      } else {
         ClientDowned.Info info = ClientDowned.get(e.getId());
         if (info == null) {
            return false;
         } else if (info.has(8) || info.has(16)) {
            poseStack.pushPose();
            poseStack.translate(0.0, -0.32, 0.0);
            return true;
         } else {
            boolean pushed = false;
            if (info.has(4)) {
               float p = info.reviveProgress(partialTick);
               if (!(p <= 0.1F) && !(p >= 0.86F)) {
                  float push = pump(info.reviveTicks(partialTick));
                  poseStack.pushPose();
                  pushed = true;
                  poseStack.translate(0.0, -0.035 * push, 0.0);
                  poseStack.scale(1.0F + 0.03F * push, 1.0F - 0.05F * push, 1.0F + 0.03F * push);
               }
            }

            if (layingFlat(e, info)) {
               if (!pushed) {
                  poseStack.pushPose();
                  pushed = true;
               }

               layFlat(e, poseStack, partialTick, info.fall(partialTick));
            }

            return pushed;
         }
      }
   }

   /**
    * Vrai quand un corps à terre (hors scène d'exécution) n'est pas dans la pose « nage » de Minecraft : le rendu vanilla
    * le dessinerait debout, c'est {@link #layFlat} qui le couche (filet de sécurité, voir PlayerPoseMixin). Sa plaque de nom
    * tournerait avec lui : ClientEvents la cache pendant ce temps.
    */
   static boolean layingFlat(Entity e) {
      ClientDowned.Info info = ClientDowned.get(e.getId());
      return info != null && !info.has(8) && !info.has(16) && e instanceof LivingEntity living && layingFlat(living, info)
         && !ExecutionScenes.involved(e);
   }

   private static boolean layingFlat(LivingEntity e, ClientDowned.Info info) {
      return info.has(1) && !e.isVisuallySwimming();
   }

   /**
    * Couche le corps comme PlayerRenderer.setupRotations couche un joueur qui rampe : autour de l'axe du corps, rotation
    * de -90° x {@code fall} (chute du corps, même vitesse que swimAmount) puis décalage (0, -1, 0,3). Le rendu vanilla
    * tourne encore de son côté tant que swimAmount redescend (pose « nage » perdue) : cette part est retirée ici, la
    * rotation n'est jamais appliquée deux fois. Appelé seulement hors pose « nage » : Minecraft n'ajoute alors pas son
    * décalage.
    */
   private static void layFlat(LivingEntity e, PoseStack ps, float partialTick, float fall) {
      if (fall <= 0.0F) {
         return;
      }

      float yaw = 180.0F - Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
      // rotation que le rendu vanilla (PlayerRenderer, BotRenderer) fera encore après nous
      float vanilla = e.getSwimAmount(partialTick) * (e instanceof Player && e.isInWater() ? -90.0F - e.getXRot() : -90.0F);
      ps.mulPose(Axis.YP.rotationDegrees(yaw));
      ps.mulPose(Axis.XP.rotationDegrees(-90.0F * fall));
      ps.translate(0.0F, -1.0F, 0.3F);
      ps.mulPose(Axis.XP.rotationDegrees(-vanilla));
      ps.mulPose(Axis.YP.rotationDegrees(-yaw));
   }

   static void beginPlayer(LivingEntity e, PoseStack poseStack, float partialTick) {
      RENDERING.push(pushTransform(e, poseStack, partialTick));
   }

   static void endPlayer(PoseStack poseStack) {
      Boolean pushed = RENDERING.poll();
      if (pushed != null && pushed) {
         poseStack.popPose();
      }
   }
}
