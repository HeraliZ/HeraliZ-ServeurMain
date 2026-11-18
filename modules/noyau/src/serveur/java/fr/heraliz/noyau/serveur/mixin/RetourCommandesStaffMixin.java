package fr.heraliz.noyau.serveur.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Réponses des commandes pour le staff (18/11, retour du propriétaire : « /pnj liste, /depart info, /heralizloot point liste :
 * aucun retour dans mon tchat »). Le monde garde la règle {@code sendCommandFeedback = false}, qui cache aux joueurs toutes
 * les réponses « réussi » des commandes ({@code CommandSourceStack.sendSuccess} ne passe que si {@code acceptsSuccess()}) ;
 * plusieurs de nos mods contournaient déjà la règle (réponses en message direct), pas tous. Désormais, un joueur op (niveau
 * 2 ou plus) voit les réponses de toutes les commandes qu'il tape ; pour les autres joueurs, la règle ne change pas.
 *
 * <p>18/11, soir : {@code acceptsSuccess} est déclarée par {@code Entity} (le joueur en hérite) : visée sur
 * {@code ServerPlayer}, l'injection ne trouvait rien et, la configuration étant obligatoire, le serveur ne démarrait plus.
 * Elle est maintenant dans une configuration facultative ({@code heraliz_noyau_profil.mixins.json}, {@code require = 0}) :
 * si sa cible change, elle ne s'applique pas, le serveur démarre quand même.
 */
@Mixin(Entity.class)
public abstract class RetourCommandesStaffMixin {
    @Inject(method = "acceptsSuccess", at = @At("HEAD"), cancellable = true, require = 0)
    private void heraliz$staffVoitLesReponses(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p && p.hasPermissions(2)) {
            cir.setReturnValue(true);
        }
    }
}
