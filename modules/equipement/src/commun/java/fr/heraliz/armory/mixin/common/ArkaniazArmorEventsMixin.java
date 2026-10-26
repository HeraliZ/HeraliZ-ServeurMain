package fr.heraliz.armory.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coupe l'ancien gestionnaire d'armures ArkaniaZ du jar geckolib222 (repris de HeralisZ Armor, 0.9.0). A chaque tick il
 * retirait resistance au feu, vitesse et anti-zombie a tout joueur ne portant pas SES objets (effets de potion compris) :
 * le camouflage des ghillies (antizombie) et les potions des joueurs seraient perdus.
 */
@Mixin(targets = "software.bernie.example.client.handler.ArmorEventHandler", remap = false)
public abstract class ArkaniazArmorEventsMixin {
    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void heraliz$disable(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
