package fr.heralisz.tactical.compat.player_animator;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import fr.heralisz.tactical.util.IPAAssetManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;

public class PlayerAnimatorIntegration {
   public static final ResourceLocation UPPER_LAYER = new ResourceLocation("heralisz_tactical", "upper");
   public static final ResourceLocation LOWER_LAYER = new ResourceLocation("heralisz_tactical", "lower");
   public static final ResourceLocation ROTATION_ANIMATION = new ResourceLocation("heralisz_tactical", "rotation");
   private static boolean initialized = false;

   public static void init() {
      if (!initialized) {
         initialized = true;

         for (PlayerAnimatorIntegration.AnimationLayer layer : PlayerAnimatorIntegration.AnimationLayer.values()) {
            if (!PlayerAnimatorIntegration.AnimationLayer.ROTATION.equals(layer)) {
               PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(layer.id, layer.priority, p -> new ModifierLayer());
            }
         }

         PlayerAnimationFactory.ANIMATION_DATA_FACTORY
            .registerFactory(ROTATION_ANIMATION, 42, player -> new ModifierLayer(null, new AbstractModifier[]{AdjustmentYRotModifier.getModifier(player)}));
         MinecraftForge.EVENT_BUS.register(MeleeAnimationListener.class);
         MinecraftForge.EVENT_BUS.register(IdleAnimationHandler.class);
      }
   }

   public static KeyframeAnimation getAnimation(ResourceLocation location, String name) {
      return IPAAssetManager.getAnimations(location, name).orElse(null);
   }

   public static void playIdleAnimation(
      AbstractClientPlayer player, ResourceLocation location, String name, PlayerAnimatorIntegration.AnimationLayer layer, int fadeInTicks
   ) {
      KeyframeAnimation animation = getAnimation(location, name);
      if (animation != null) {
         ModifierLayer<IAnimation> modifierLayer = getLayer(player, layer);
         if (modifierLayer != null) {
            IAnimation current = modifierLayer.getAnimation();
            if (current instanceof KeyframeAnimationPlayer kap
               && kap.isActive()
               && kap.getData().extraData.get("name") instanceof String currentName
               && name.equals(currentName)) {
               return;
            }

            KeyframeAnimationPlayer animPlayer = new KeyframeAnimationPlayer(animation, 0);
            animPlayer.getData().extraData.put("name", name);
            animPlayer.setFirstPersonMode(FirstPersonMode.DISABLED);
            IAnimation frozenCurrent = current instanceof KeyframeAnimationPlayer kap ? new PlayerAnimatorIntegration.FrozenAnimationSnapshot(kap) : current;
            PlayerAnimatorIntegration.ForcedFadeModifier fadeModifier = new PlayerAnimatorIntegration.ForcedFadeModifier(fadeInTicks, Ease.INOUTSINE);
            fadeModifier.setBeginAnimation(frozenCurrent);
            modifierLayer.addModifierLast(fadeModifier);
            modifierLayer.setAnimation(animPlayer);
         }
      }
   }

   public static void playAttackAnimation(
      AbstractClientPlayer player, ResourceLocation location, String name, PlayerAnimatorIntegration.AnimationLayer layer, int fadeInTicks
   ) {
      KeyframeAnimation animation = getAnimation(location, name);
      if (animation != null) {
         ModifierLayer<IAnimation> modifierLayer = getLayer(player, layer);
         if (modifierLayer != null) {
            KeyframeAnimationPlayer animPlayer = new KeyframeAnimationPlayer(animation, 0);
            animPlayer.getData().extraData.put("lr_attack_action", true);
            animPlayer.setFirstPersonMode(FirstPersonMode.DISABLED);
            modifierLayer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeInTicks, Ease.INOUTSINE), animPlayer);
         }
      }
   }

   private static ModifierLayer<IAnimation> getLayer(AbstractClientPlayer player, PlayerAnimatorIntegration.AnimationLayer layer) {
      return (ModifierLayer<IAnimation>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(layer.id);
   }

   public static boolean isAttackAnimationActive(AbstractClientPlayer player, PlayerAnimatorIntegration.AnimationLayer layer) {
      ModifierLayer<IAnimation> modifierLayer = getLayer(player, layer);
      return modifierLayer != null
            && modifierLayer.getAnimation() instanceof KeyframeAnimationPlayer kap
            && kap.getData().extraData.get("lr_attack_action") instanceof Boolean isAttack
            && isAttack
         ? kap.getCurrentTick() < kap.getData().endTick
         : false;
   }

   public static void stopAnimation(AbstractClientPlayer player, PlayerAnimatorIntegration.AnimationLayer layer, int fadeOutTicks) {
      ModifierLayer<IAnimation> modifierLayer = getLayer(player, layer);
      if (modifierLayer != null) {
         modifierLayer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeOutTicks, Ease.INOUTSINE), null);
      }
   }

   public static void enableRotationModifier(AbstractClientPlayer player, ResourceLocation location, int fadeInTicks) {
      ModifierLayer<IAnimation> rotationLayer = (ModifierLayer<IAnimation>)PlayerAnimationAccess.getPlayerAssociatedData(player).get(ROTATION_ANIMATION);
      if (rotationLayer != null && rotationLayer.getAnimation() == null) {
         rotationLayer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fadeInTicks, Ease.INOUTSINE), new EmptyActiveAnimation());
      }
   }

   public enum AnimationLayer {
      UPPER(PlayerAnimatorIntegration.UPPER_LAYER, 40),
      LOWER(PlayerAnimatorIntegration.LOWER_LAYER, 41),
      ROTATION(PlayerAnimatorIntegration.ROTATION_ANIMATION, 42);

      private final ResourceLocation id;
      private final int priority;

      AnimationLayer(ResourceLocation id, int priority) {
         this.id = id;
         this.priority = priority;
      }
   }

   private static class ForcedFadeModifier extends AbstractFadeModifier {
      private final Ease ease;

      protected ForcedFadeModifier(int length, Ease ease) {
         super(length);
         this.ease = ease;
      }

      protected float getAlpha(String modelName, TransformType type, float progress) {
         return this.ease.invoke(progress);
      }

      @NotNull
      public Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
         if (this.calculateProgress(tickDelta) > 1.0F) {
            return super.get3DTransform(modelName, type, tickDelta, value0);
         }

         Vec3f animatedVec = super.get3DTransform(modelName, type, tickDelta, value0);
         float a = this.getAlpha(modelName, type, this.calculateProgress(tickDelta));
         Vec3f source = this.beginAnimation != null ? this.beginAnimation.get3DTransform(modelName, type, tickDelta, value0) : value0;
         return animatedVec.scale(a).add(source.scale(1.0F - a));
      }
   }

   private static class FrozenAnimationSnapshot implements IAnimation {
      private final KeyframeAnimationPlayer source;

      public FrozenAnimationSnapshot(KeyframeAnimationPlayer source) {
         this.source = source;
      }

      public void tick() {
      }

      public boolean isActive() {
         return true;
      }

      @NotNull
      public Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
         return this.source.get3DTransform(modelName, type, 0.0F, value0);
      }

      public void setupAnim(float tickDelta) {
         this.source.setupAnim(0.0F);
      }
   }
}
