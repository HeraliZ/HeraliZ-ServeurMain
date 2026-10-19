package fr.heraliz.tactique.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tête d'un joueur qui se relève (19/10) : pendant que le corps se redresse, Minecraft remet d'un coup la tête droite
 * dans l'axe du corps encore penché (couché, il la tenait relevée de 45°). Ici, la tête garde le regard du joueur par
 * rapport au sol : relevée de l'inclinaison du corps (90° × swimAmount), jamais plus que les 45° de la pose couchée,
 * puis droite quand le corps l'est. Hors de l'eau seulement. Sans effet si le point manque (require = 0).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelTeteRelevee<T extends LivingEntity> {
   private static final float MAX_RELEVEE = -45.0F;

   @Shadow
   @Final
   public ModelPart head;

   @Shadow
   public float swimAmount;

   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"), require = 0)
   private void heraliz$teteRelevee(T e, float a, float b, float c, float d, float regard, CallbackInfo ci) {
      if (!(e instanceof Player) || this.swimAmount <= 0.0F || e.isVisuallySwimming() || e.isInWater() || e.isFallFlying()) return;
      this.head.xRot = Math.max(MAX_RELEVEE, regard - 90.0F * this.swimAmount) * Mth.DEG_TO_RAD;
   }
}
