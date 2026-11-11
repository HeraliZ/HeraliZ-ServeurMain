package fr.heraliz.loot.client.mixin;

import fr.heraliz.loot.client.NomsRarete;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Nom affiché au-dessus de la barre d'objets quand on change d'objet en main (11/11, retour du propriétaire : la lunette, le
 * sniper, les composants… n'y avaient pas la couleur de leur rareté) : Minecraft le colore avec SA rareté (blanc pour les
 * armes de TaCZ, rose pour les composants III) ; il prend ici la règle de l'infobulle ({@link NomsRarete#nom}). Seul le nom
 * passé à {@code Component.empty().append(nom)} change : position, fondu, fond et italique d'un objet renommé restent ceux
 * de Minecraft. Corps de la méthode : dans la version de Forge à deux arguments (décalage des barres) ou, sans elle, dans
 * celle de Minecraft ; les deux sont visées. require = 0 : si la cible change, le nom garde ses couleurs, rien ne plante.
 */
@Mixin(Gui.class)
public abstract class NomEnMainMixin {
    @Shadow
    private ItemStack lastToolHighlight;

    @ModifyArg(method = {"renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
            "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;append(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0)
    private Component heraliz$nomEnCouleur(Component nom) {
        return NomsRarete.nom(this.lastToolHighlight, nom);
    }
}
