package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.client.gameplay.LocalPlayerAim;
import fr.heraliz.tactique.client.couteaux.CouteauxClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Couteau TaCZ en main (fr.heraliz.tactique.couteaux.CoupsCouteau) : pas de visée (zoom) ; la touche Viser donne le coup
 * puissant (fr.heraliz.tactique.client.couteaux.CouteauxClient). Arrêter de viser reste toujours permis. Rien n'est
 * envoyé au serveur : il ne voit jamais le joueur viser avec un couteau. Noms de TaCZ (non obfusqués) : remap = false.
 */
@Mixin(value = LocalPlayerAim.class, remap = false)
public abstract class LocalPlayerAimCouteauMixin {
   @Shadow
   @Final
   private LocalPlayer player;

   @Inject(method = "aim(Z)V", at = @At("HEAD"), cancellable = true)
   private void heraliz$pasDeViseeAuCouteau(boolean viser, CallbackInfo ci) {
      if (viser && CouteauxClient.couteauEnMain(this.player)) {
         ci.cancel();
      }
   }
}
