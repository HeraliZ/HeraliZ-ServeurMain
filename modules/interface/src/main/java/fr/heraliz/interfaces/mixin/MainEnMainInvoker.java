package fr.heraliz.interfaces.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accès au rendu d'une main en 1re personne (main nue réduite, voir client/MainNue). */
@Mixin(ItemInHandRenderer.class)
public interface MainEnMainInvoker {
    @Invoker("renderArmWithItem")
    void heraliz$renderArmWithItem(AbstractClientPlayer joueur, float partialTick, float tangage, InteractionHand main, float balancement,
                                   ItemStack objet, float equipement, PoseStack pose, MultiBufferSource tampons, int lumiere);
}
