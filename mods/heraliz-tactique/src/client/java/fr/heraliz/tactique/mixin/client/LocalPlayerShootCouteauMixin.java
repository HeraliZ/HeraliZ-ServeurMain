package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.client.gameplay.LocalPlayerShoot;
import fr.heraliz.tactique.client.couteaux.CouteauxClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Couteau TaCZ en main (fr.heraliz.tactique.couteaux.CoupsCouteau) : la touche Tirer ne tire plus (balle d'un tick qui ne
 * touchait rien, chargeur d'une munition introuvable), elle donne le coup rapide ({@link CouteauxClient#attaquer}).
 * Le tir n'est jamais « réussi » : la touche de TaCZ redemande un tir à chaque tick tant qu'elle est tenue, d'où un coup
 * dès que la cadence le permet. Même place que le « BetterMelee » de TaCZ Addon (début de {@code shoot}), qui ne vise que
 * les armes de sa propre liste. Noms de TaCZ (non obfusqués) : remap = false.
 */
@Mixin(value = LocalPlayerShoot.class, remap = false)
public abstract class LocalPlayerShootCouteauMixin {
   @Shadow
   @Final
   private LocalPlayer player;

   @Inject(method = "shoot()Lcom/tacz/guns/api/entity/ShootResult;", at = @At("HEAD"), cancellable = true)
   private void heraliz$coupDeCouteau(CallbackInfoReturnable<ShootResult> cir) {
      if (CouteauxClient.couteauEnMain(this.player)) {
         CouteauxClient.attaquer(false);
         cir.setReturnValue(ShootResult.IS_MELEE);
      }
   }
}
