package fr.heralisz.n07.mixin;

import fr.heralisz.n07.client.Ritual;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gestes du joueur pendant un rituel d'invocation (bras tendu vers la fente, mains sur la vanne...), vus par tout le monde. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
   private void heraliszBosses$ritual(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch,
                                      CallbackInfo ci) {
      if (entity instanceof Player) Ritual.pose(entity, (HumanoidModel<?>) (Object) this);
   }
}
