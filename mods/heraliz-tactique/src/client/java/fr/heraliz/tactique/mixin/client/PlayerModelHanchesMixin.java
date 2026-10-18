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
 * Recolle les jambes au torse juste APRÈS Player Animator (même point, priorité 2100 contre 2000), avant que Minecraft
 * ne recopie les jambes sur les calques du skin : voir {@link Hanches}. Sans effet si le point manque (require = 0).
 */
@Mixin(value = PlayerModel.class, priority = 2100)
public abstract class PlayerModelHanchesMixin<T extends LivingEntity> extends HumanoidModel<T> {
   public PlayerModelHanchesMixin(ModelPart part) {
      super(part);
   }

   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V", ordinal = 0),
      require = 0)
   private void heraliz$recollerJambes(T entite, float a, float b, float c, float d, float f, CallbackInfo ci) {
      Hanches.recoller(entite, this.body, this.rightLeg, this.leftLeg);
   }
}
