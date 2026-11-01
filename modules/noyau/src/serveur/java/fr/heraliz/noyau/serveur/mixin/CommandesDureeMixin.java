package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.diag.DernieresCommandes;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Durée de chaque commande (01/11, profileur) : deux lectures d'horloge autour de l'exécution d'une commande (console,
 * blocs de commande, commandes jouées par nos mods), nommée par l'événement de Forge (mots de la commande seulement), voir
 * {@link DernieresCommandes}. Les commandes passées par Bukkit (console de Mohist) prennent la variante à trois
 * arguments ajoutée par Mohist, si elle existe. Facultatif : une cible absente n'empêche rien.
 */
@Mixin(Commands.class)
public abstract class CommandesDureeMixin {
    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("HEAD"), require = 0)
    private void heraliz$debut(CallbackInfoReturnable<Integer> cir) {
        DernieresCommandes.debut(null);
    }

    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I", at = @At("RETURN"), require = 0)
    private void heraliz$fin(CallbackInfoReturnable<Integer> cir) {
        DernieresCommandes.fin();
    }

    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;Ljava/lang/String;)I", at = @At("HEAD"),
            require = 0, remap = false)
    private void heraliz$debutBukkit(CallbackInfoReturnable<Integer> cir) {
        DernieresCommandes.debut(null);
    }

    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;Ljava/lang/String;)I", at = @At("RETURN"),
            require = 0, remap = false)
    private void heraliz$finBukkit(CallbackInfoReturnable<Integer> cir) {
        DernieresCommandes.fin();
    }
}
