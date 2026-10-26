package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.armor.PieceDef;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Rendu d'une piece Curios (cagoule, gilet, gants) : meme modele en relief, pose copiee du joueur, translucide (verre). */
public class CurioPieceRenderer implements ICurioRenderer {
    private final PieceDef def;

    public CurioPieceRenderer(PieceDef def) {
        this.def = def;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                                                                          RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
                                                                          float limbSwing, float limbSwingAmount, float partialTicks,
                                                                          float ageInTicks, float netHeadYaw, float headPitch) {
        ArmorModels.Variants v = ArmorModels.INSTANCE.get(def);
        if (v == null || !(parent.getModel() instanceof HumanoidModel parentModel)) return;
        HumanoidModel<LivingEntity> model = ArmorModels.INSTANCE.modelFor(def, slotContext.entity());
        parentModel.copyPropertiesTo((HumanoidModel) model);
        model.setAllVisible(true);
        // 0.9.0 : superposition avec une ghillie (gilet agrandi par-dessus, genouilleres cachees sous une integrale...)
        if (!GearLayering.fitPiece(slotContext.entity(), def, model)) return;
        try {
            VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(buffers, v.renderType(), false, stack.hasFoil());
            model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        } finally {
            if (model instanceof MeshArmorModel mesh) mesh.clearFit();
        }
    }
}
