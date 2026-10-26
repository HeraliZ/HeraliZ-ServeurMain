package fr.heraliz.noyau.serveur.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Table des identifiants écrite dans level.dat (25/10, banc de charge) : Forge la reconstruisait en entier à CHAQUE
 * sauvegarde du monde (« Gathering id map for writing to world save »), 190 à 300 ms mesurés sur le fil serveur avec nos
 * dizaines de milliers d'objets et de blocs. Sur un serveur dédié, les registres ne changent plus une fois le monde chargé :
 * la table faite à la première sauvegarde est gardée et resservie. Seule la version « pour le disque » est gardée ; celle
 * envoyée aux joueurs n'est pas touchée.
 */
@Mixin(value = RegistryManager.class, remap = false)
public abstract class InstantaneRegistresMixin {
    @Unique
    private Map<ResourceLocation, ForgeRegistry.Snapshot> heraliz$pourLeDisque;
    /** Le serveur pour lequel la table a été faite (jeu solo : un serveur par monde ouvert, ses identifiants peuvent changer). */
    @Unique
    private Object heraliz$serveur;

    @Inject(method = "takeSnapshot", at = @At("HEAD"), cancellable = true)
    private void heraliz$resservir(boolean pourLeDisque, CallbackInfoReturnable<Map<ResourceLocation, ForgeRegistry.Snapshot>> cir) {
        if (pourLeDisque && this.heraliz$pourLeDisque != null && (Object) this == RegistryManager.ACTIVE
                && this.heraliz$serveur == net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()) {
            cir.setReturnValue(this.heraliz$pourLeDisque);
        }
    }

    @Inject(method = "takeSnapshot", at = @At("RETURN"))
    private void heraliz$garder(boolean pourLeDisque, CallbackInfoReturnable<Map<ResourceLocation, ForgeRegistry.Snapshot>> cir) {
        Object srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (pourLeDisque && (Object) this == RegistryManager.ACTIVE && srv != null && this.heraliz$serveur != srv) {
            this.heraliz$pourLeDisque = cir.getReturnValue();
            this.heraliz$serveur = srv;
        }
    }
}
