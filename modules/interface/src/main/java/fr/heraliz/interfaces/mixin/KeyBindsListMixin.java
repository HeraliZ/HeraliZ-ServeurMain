package fr.heraliz.interfaces.mixin;

import fr.heraliz.interfaces.client.Touches;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Écran Options &gt; Commandes : la liste des touches est construite sans les touches cachées par HeraliZ
 * ({@link Touches#cachee}). Seule la lecture de {@code options.keyMappings} faite par la liste est filtrée : les touches
 * restent enregistrées dans le jeu (leurs mods les lisent toujours) et « Tout réinitialiser » les remet à leur valeur
 * par défaut (sans touche). Une catégorie dont toutes les touches sont cachées disparaît avec elles. Si la liste
 * changeait (autre version du jeu), l'injection est sautée sans erreur ({@code require = 0}) : les touches s'affichent
 * toutes, comme avant.
 */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
    @Redirect(method = "<init>", require = 0,
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyMappings:[Lnet/minecraft/client/KeyMapping;",
                    opcode = Opcodes.GETFIELD))
    private KeyMapping[] heraliz$sansTouchesCachees(Options options) {
        return Touches.visibles(options.keyMappings);
    }
}
