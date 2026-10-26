package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.item.MachineItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Rendu 3D des machines en tant qu'objets (inventaire, main, sol, cadre) : modele fige et cuit.
 */
public class MachineItemRenderer extends BlockEntityWithoutLevelRenderer {
    public MachineItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (stack.getItem() instanceof fr.heraliz.casino.item.DecorItem decor) {
            DecorRenderer.renderItem(decor.type, ctx, pose, buf, light);
            return;
        }
        if (!(stack.getItem() instanceof MachineItem item)) return;
        MachineDesign<?> d = ctx == ItemDisplayContext.GUI ? Designs.getLod(item.type.id) : Designs.get(item.type.id);
        StaticModel.Baked baked = StaticModel.get(d, 0, true);
        float sc = d.itemScale;
        pose.pushPose();
        pose.translate(0.5f, ctx == ItemDisplayContext.GUI ? 0.02f : 0f, 0.5f);
        pose.scale(sc, sc, sc);
        pose.translate(-0.5f, 0, -0.5f);
        StaticModel.draw(baked, pose, buf);
        pose.popPose();
    }
}
