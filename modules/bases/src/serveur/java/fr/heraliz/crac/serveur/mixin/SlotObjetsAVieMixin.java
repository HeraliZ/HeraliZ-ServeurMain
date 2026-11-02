package fr.heraliz.crac.serveur.mixin;

import fr.heraliz.crac.bases.ObjetsAVieBases;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Objets à vie dans les coffres des bases (HeraliZ, 02/11, choix du propriétaire) : voir
 * {@link ObjetsAVieBases#peutPrendre}. Minecraft demande {@code Slot.mayPickup} avant toute sortie d'un objet d'une case
 * (clic, Maj + clic, touches 1 à 9, double clic, jet) : pour une case d'un conteneur posé dans une base, un objet à vie
 * n'y sort qu'avec ceux qui peuvent ouvrir ses coffres ; le pilleur le voit, mais il reste dans le conteneur (le serveur
 * remet l'écran du joueur à jour). Serveur seulement, seulement au clic ; sans ce mixin (facultatif), rien ne change.
 */
@Mixin(Slot.class)
public abstract class SlotObjetsAVieMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void crac$objetAVieDeLaBase(Player joueur, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = (Slot) (Object) this;
        if (slot.container instanceof BlockEntity be && joueur instanceof ServerPlayer sp && !ObjetsAVieBases.peutPrendre(sp, be, slot.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
