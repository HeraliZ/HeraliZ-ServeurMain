package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Rendu des objets Curios portes par une entite non-joueur (le mannequin) : Curios n'ajoute sa couche qu'aux joueurs.
 * Meme logique que Curios (objet cosmetique prioritaire, slot masque = rien), via l'API publique.
 */
public class MannequinCuriosLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private final RenderLayerParent<T, M> parent;

    public MannequinCuriosLayer(RenderLayerParent<T, M> parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().forEach((id, h) -> {
            var stacks = h.getStacks();
            var cosmetics = h.getCosmeticStacks();
            var renders = h.getRenders();
            for (int i = 0; i < stacks.getSlots(); i++) {
                boolean visible = i < renders.size() && renders.get(i);
                ItemStack stack = ItemStack.EMPTY;
                boolean cosmetic = false;
                if (h.hasCosmetic() && i < cosmetics.getSlots()) {
                    stack = cosmetics.getStackInSlot(i);
                    cosmetic = !stack.isEmpty();
                }
                if (stack.isEmpty() && visible) stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                final ItemStack st = stack;
                final SlotContext ctx = new SlotContext(id, entity, i, cosmetic, visible);
                CuriosRendererRegistry.getRenderer(st.getItem()).ifPresent(r -> {
                    pose.pushPose();
                    r.render(st, ctx, pose, parent, buffers, light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
                    pose.popPose();
                });
            }
        }));
    }
}
