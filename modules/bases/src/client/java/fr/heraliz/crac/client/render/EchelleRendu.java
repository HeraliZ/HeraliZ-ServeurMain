package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.crac.block.EchelleBlock;
import fr.heraliz.crac.block.entity.EchelleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Échelle de raid dessinée à sa vraie place (pose libre du 05/11) : les trois parties du modèle (bas, milieu, haut, celui
 * d'avant, tourné vers le nord), le milieu de leur dos posé sur le point d'appui de la surface, tournées vers le grimpeur
 * (n'importe quel angle : murs en biais compris). Échelle d'avant le 05/11 : même place qu'avant (dos au mur de sa case).
 * Rien d'autre par image que ces trois modèles déjà cuits.
 */
public class EchelleRendu implements BlockEntityRenderer<EchelleBlockEntity> {
    /** Milieu du dos du modèle tourné vers le nord (px) : x 8, z 14 (2 px du bord, moitié du mur dessinée par la voisine). */
    private static final float DOS_X = 8F / 16F, DOS_Z = 14F / 16F;

    public EchelleRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(EchelleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState s = be.getBlockState();
        if (level == null || !(s.getBlock() instanceof EchelleBlock)) {
            return;
        }
        double[] p = be.place();
        // rotation autour de y qui envoie le nord (0, -1) sur la direction de l'échelle (nx, nz)
        float angle = (float) Math.toDegrees(Math.atan2(-p[2], -p[3]));
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer rendu = mc.getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        BlockPos bas = be.getBlockPos();
        for (int i = 0; i < EchelleBlock.HAUTEUR; i++) {
            BlockState partie = s.setValue(EchelleBlock.FACING, Direction.NORTH).setValue(EchelleBlock.PARTIE, i);
            BakedModel modele = mc.getBlockRenderer().getBlockModel(partie);
            ps.pushPose();
            ps.translate(p[0], i, p[1]);
            ps.mulPose(Axis.YP.rotationDegrees(angle));
            ps.translate(-DOS_X, 0, -DOS_Z);
            int lumiere = LevelRenderer.getLightColor(level, bas.above(i));
            rendu.renderModel(ps.last(), vc, partie, modele, 1F, 1F, 1F, lumiere, overlay, ModelData.EMPTY, RenderType.cutout());
            ps.popPose();
        }
    }

    /** Une échelle se voit de loin (étage d'une base pendant un raid). */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
