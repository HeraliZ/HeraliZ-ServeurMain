package fr.heralisz.utils.serveur.mixin;

import fr.heralisz.utils.diag.SuiviEntites;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Diagnostic du serveur (fr.heralisz.utils.diag) : début et fin du tick de chaque entité, pour savoir quelle entité
 * tournait pendant un tick long et, pendant /diag profil, le temps passé par type d'entité.
 * <p>
 * Cible : {@code Level.guardEntityTick(Consumer, Entity)} de Minecraft 1.20.1, par son nom SRG {@code m_46653_} (nom du
 * jeu en service : la config de mixins du serveur n'a pas de table de correspondance, remap = false, comme
 * VehiculeSansCoffreMixin) ; types de la signature : mêmes noms de classes en jeu. require = 0 : si la méthode change
 * (autre version, Mohist), rien ne plante, le diagnostic le dit (« mesure par type indisponible »).
 */
@Mixin(targets = "net.minecraft.world.level.Level", remap = false)
public abstract class DiagTickEntiteMixin {
    @Inject(method = "m_46653_(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), require = 0)
    private void heraliszDiag$debutTickEntite(Consumer<?> action, Entity entite, CallbackInfo ci) {
        SuiviEntites.debut(entite);
    }

    @Inject(method = "m_46653_(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"), require = 0)
    private void heraliszDiag$finTickEntite(Consumer<?> action, Entity entite, CallbackInfo ci) {
        SuiviEntites.fin(entite);
    }
}
