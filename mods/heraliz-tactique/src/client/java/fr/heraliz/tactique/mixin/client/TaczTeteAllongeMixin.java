package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.compat.playeranimator.animation.AdjustmentYRotModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tête des joueurs allongés (animation 3e personne de TaCZ, 14/10) : TaCZ ajoute tout le regard vertical du joueur à la
 * tête de l'animation, sans limite. À plat ventre, regarder un peu vers le haut renversait la tête en arrière (« il
 * regarde le ciel »). Allongé ou en train de se relever (corps encore penché, hors de l'eau ; 19/10 : en se relevant
 * aussi, la tête partait vers le ciel) : regard ajouté limité à -35° / +30°.
 * Méthode propre à TaCZ : remap = false ; sans effet si TaCZ change (require = 0).
 */
@Mixin(value = AdjustmentYRotModifier.class, remap = false)
public abstract class TaczTeteAllongeMixin {
   private static final float HAUT_MAX = -35.0F, BAS_MAX = 30.0F;

   @Shadow
   @Final
   private Player player;

   @Inject(method = "apply(Ljava/lang/String;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true, require = 0)
   private void heraliz$teteAllonge(String partie, CallbackInfoReturnable<Optional<AdjustmentModifier.PartModifier>> cir) {
      if (!"head".equals(partie) || cir.getReturnValue() == null || cir.getReturnValue().isEmpty()) return;
      Player p = this.player;
      float pt = Minecraft.getInstance().getFrameTime();
      if (p.isSwimming() || p.isInWater() || p.getSwimAmount(pt) <= 0.0F) return;
      float regard = Mth.wrapDegrees(Mth.lerp(pt, p.xRotO, p.getXRot()));
      float limite = Mth.clamp(regard, HAUT_MAX, BAS_MAX);
      cir.setReturnValue(Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(limite * Mth.DEG_TO_RAD, 0.0F, 0.0F), Vec3f.ZERO)));
   }
}
