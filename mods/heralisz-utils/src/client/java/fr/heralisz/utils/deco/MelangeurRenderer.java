package fr.heralisz.utils.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Pales du mélangeur : un petit modèle fixe (heralisz_utils:block/melangeur_pales) tourné autour de l'axe vertical,
 * 1,5 bloc sous le moteur, dans le liquide. Un seul modèle, un seul appel de dessin par mélangeur visible.
 */
@OnlyIn(Dist.CLIENT)
public class MelangeurRenderer implements BlockEntityRenderer<MelangeurBlockEntity> {
    public static final ResourceLocation PALES = new ResourceLocation(HeraliszUtils.MODID, "block/melangeur_pales");

    public MelangeurRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MelangeurBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(PALES);
        float angle = ((be.getLevel().getGameTime() % 360L) + partial) * 3.0f;
        ps.pushPose();
        ps.translate(0.5, -1.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(angle));
        ps.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(),
                buf.getBuffer(RenderType.cutout()), null, m, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
