package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.ExecutionsCommun;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

interface Choreography {
   CoupDeGrace.State at(float var1, int var2, float var3, Vector3f var4, float var5);

   void poseActor(HumanoidModel<?> var1, CoupDeGrace.State var2, float var3);

   void poseVictim(HumanoidModel<?> var1, CoupDeGrace.State var2, float var3);

   CoupDeGrace.Shot shot(CoupDeGrace.State var1);

   Vector3f end(int var1);

   Vector3f corpse(int var1);

   default void tick(ExecutionScenes.Scene s, int before, int now) {
   }

   default void renderExtras(PoseStack ps, MultiBufferSource buffers, CoupDeGrace.State s, float age, Choreography.Extras extras) {
   }

   default boolean soulRises() {
      return true;
   }

   default float reach() {
      return 0.75F;
   }

   default float victimAlpha(CoupDeGrace.State s) {
      return 1.0F;
   }

   default float actorAlpha(CoupDeGrace.State s) {
      return 1.0F;
   }

   static Choreography of(ExecutionType type) {
      return switch (type) {
         case COUP_DE_GRACE -> CoupDeGrace.INSTANCE;
         case KARAMBIT -> KarambitDance.INSTANCE;
         case SEA_GLEAM -> SeaGleamExecution.INSTANCE;
         case BUTTERFLY -> ButterflyExecution.INSTANCE;
         case M9 -> M9Execution.INSTANCE;
         case HOME_RUN -> HomeRunExecution.INSTANCE;
         case FRAPPE_ORBITALE -> FrappeOrbitale.INSTANCE;
         case CHUTE_METEORE -> ChuteMeteore.INSTANCE;
         case COMBO_AERIEN -> ComboAerien.INSTANCE;
         case ARRACHE_AME -> ArracheAme.INSTANCE;
         case MILLE_COUPES -> MilleCoupes.INSTANCE;
         case SINGULARITE -> Singularite.INSTANCE;
         case COURROUX_CIEL -> CourrouxCiel.INSTANCE;
         case SOUFFLE_DRAGON -> SouffleDragon.INSTANCE;
         case ZERO_ABSOLU -> ZeroAbsolu.INSTANCE;
         case DANSE_OMBRES -> DanseOmbres.INSTANCE;
         case MAIN_JOKER -> MainJoker.INSTANCE;
         case FAILLE -> Faille.INSTANCE;
         case ONDE_CELESTE -> OndeCeleste.INSTANCE;
         case APESANTEUR -> Apesanteur.INSTANCE;
         case MARIONNETTISTE -> Marionnettiste.INSTANCE;
         case FREESTYLE_COMBO -> FreestyleCombo.INSTANCE;
         case TEMPETE_SAKURA -> TempeteSakura.INSTANCE;
         case TAG_FINAL -> TagFinal.INSTANCE;
         case JUGEMENT_RUNIQUE -> JugementRunique.INSTANCE;
         case CADEAU_SURPRISE -> CadeauSurprise.INSTANCE;
         case SUCRE_D_ORGE -> SucreOrge.INSTANCE;
         case BONHOMME_DE_NEIGE -> BonhommeNeige.INSTANCE;
         case LAMES_FANTOMES, PANTIN -> KnifeFinisher.of(type, null);
      };
   }

   static Choreography of(ExecutionScenes.Scene s) {
      if (s.type.variant()) {
         ExecutionType knife = s.actor != null ? ExecutionsCommun.forWeapon(ExecutionScenes.heldItem(s.actor)) : null;
         return KnifeFinisher.of(s.type, KnifeFinisher.bladeOf(knife));
      } else {
         return of(s.type);
      }
   }

   record Extras(
      PlayerModel<LivingEntity> soulModel,
      long gameTime,
      float partialTick,
      boolean preview,
      Quaternionf facing,
      int light,
      ResourceLocation actorSkin,
      boolean actorSlim,
      ResourceLocation victimSkin,
      boolean victimSlim
   ) {
      Extras(PlayerModel<LivingEntity> soulModel, long gameTime, float partialTick, boolean preview) {
         this(soulModel, gameTime, partialTick, preview, new Quaternionf(), 15728880, FxTex.HOLOGRAM, false, FxTex.HOLOGRAM, false);
      }
   }
}
