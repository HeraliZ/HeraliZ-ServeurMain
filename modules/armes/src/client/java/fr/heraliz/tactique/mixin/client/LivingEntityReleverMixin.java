package fr.heraliz.tactique.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Se relever (19/10) : hors de l'eau, Minecraft redresse le corps d'un joueur couché en 0,6 s (swimAmount - 0,09 par tick)
 * et le montre tout ce temps penché, tête qui part vers le haut. Chez le joueur seulement, un joueur qui se relève
 * (couché ou en train de se redresser, hors de l'eau) se redresse en 0,33 s (- 0,15 par tick) ; se coucher ne change pas.
 * Le serveur ne lit pas cette valeur (elle n'est que dessinée). Sans effet si le point manque (require = 0).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityReleverMixin {
   private static final float EN_PLUS = 0.06F;

   @Shadow
   private float swimAmount;

   @Inject(method = "updateSwimAmount", at = @At("TAIL"), require = 0)
   private void heraliz$releverPlusVite(CallbackInfo ci) {
      LivingEntity e = (LivingEntity) (Object) this;
      if (!(e instanceof Player) || !e.level().isClientSide || e.isVisuallySwimming() || e.isInWater()) return;
      this.swimAmount = Math.max(0.0F, this.swimAmount - EN_PLUS);
   }
}
