package fr.heraliz.tactique.mixin.client;

import fr.heraliz.tactique.client.corps.Hanches;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Relève le torse et les jambes posés par Minecraft juste AVANT Player Animator (même point : première recopie des calques du skin,
 * priorité 900 contre 2000), pour {@link Hanches}. Sans effet si le point manque (require = 0).
 */
@Mixin(value = PlayerModel.class, priority = 900)
public abstract class PlayerModelTorseAvantMixin<T extends LivingEntity> extends HumanoidModel<T> {
   public PlayerModelTorseAvantMixin(ModelPart part) {
      super(part);
   }

   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V", ordinal = 0),
      require = 0)
   private void heraliz$torseAvant(T entite, float a, float b, float c, float d, float f, CallbackInfo ci) {
      Hanches.avant(this.body, this.rightLeg, this.leftLeg);
   }
}
