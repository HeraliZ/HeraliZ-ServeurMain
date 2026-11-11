package fr.heraliz.loot.client.mixin;

import fr.heraliz.loot.client.NomsRarete;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Noms des armes, accessoires et munitions de TaCZ sans les couleurs écrites dans les packs d'armes (11/11) : leurs
 * fichiers de langue colorent beaucoup de noms avec des codes § (« §6Viseur Vudu », « §dAK22 », « §aBalle de 9mm »), une
 * autre échelle que nos raretés, qui passait devant la couleur de rareté HeraliZ dans tous les écrans (marchands,
 * recycleur, atelier, caisses, réparation…). Le texte du pack reste (gras compris) ; la couleur vient de la rareté HeraliZ
 * (infobulle et nom en main : {@link NomsRarete#nom}) ou de l'écran. TaCZ est un mod d'un autre auteur : son jar et les
 * fichiers des packs ne sont pas touchés. Méthode {@code Item.getName} redéfinie par ses objets (nom de développement et
 * nom du jeu, remap = false) ; @Pseudo : TaCZ n'est pas dans la fabrication de ce module ; require = 0 : si TaCZ change,
 * ses noms gardent leurs couleurs, rien ne plante. Joueur seulement (config de mixins du jar -client et -complet).
 */
@Pseudo
@Mixin(targets = {"com.tacz.guns.api.item.gun.AbstractGunItem", "com.tacz.guns.item.AttachmentItem",
        "com.tacz.guns.item.AmmoItem"}, remap = false)
public abstract class NomsTaczMixin {
    @Inject(method = {"getName(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;",
            "m_7626_(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;"},
            at = @At("RETURN"), cancellable = true, require = 0)
    private void heraliz$sansCouleursDuPack(ItemStack objet, CallbackInfoReturnable<Component> cir) {
        Component sans = NomsRarete.sansCouleursDuPack(cir.getReturnValue());
        if (sans != null) cir.setReturnValue(sans);
    }
}
