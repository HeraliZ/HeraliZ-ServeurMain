package fr.heraliz.interfaces.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.interfaces.client.MainPetite;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Main et objets tenus plus petits en 1re personne (MainPetite) : la pile de dessin est rapetissée avant le dessin d'une
 * main (vide ou avec un objet) et rendue telle quelle après. require = 0 : si le rendu change, les mains gardent leur
 * taille, rien ne plante.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MainPetiteMixin {
    @Unique
    private boolean heraliz$rapetissee;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
    private void heraliz$avant(AbstractClientPlayer joueur, float partiel, float tangage, InteractionHand main, float balancement,
                               ItemStack objet, float sortie, PoseStack pile, MultiBufferSource tampons, int lumiere, CallbackInfo ci) {
        float e = MainPetite.echelle(objet);
        heraliz$rapetissee = e < 1.0F;
        if (heraliz$rapetissee) {
            MainPetite.poser(pile, joueur, main, e);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void heraliz$apres(AbstractClientPlayer joueur, float partiel, float tangage, InteractionHand main, float balancement,
                               ItemStack objet, float sortie, PoseStack pile, MultiBufferSource tampons, int lumiere, CallbackInfo ci) {
        if (heraliz$rapetissee) {
            heraliz$rapetissee = false;
            pile.popPose();
        }
    }
}
