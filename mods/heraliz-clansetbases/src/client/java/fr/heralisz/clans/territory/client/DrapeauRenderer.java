package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.territory.DrapeauBlock;
import fr.heralisz.clans.territory.DrapeauBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu du drapeau de territoire : mat (maillage drapeau_mat) + tissu qui flotte (drapeau_tissu, texture =
 * {@link TissuTexture} de la banniere du proprietaire, neutre sinon), a mi-mat (y - 2,0) pendant une capture ou une
 * contestation ; versions _loin au-dela de 48 blocs ; etiquette (nom de la zone + [TAG]) a y 5,7 a moins de 24
 * blocs. Aucun tick : tout vient du temps de jeu et de l'etat synchronise du bloc-entite.
 */
@OnlyIn(Dist.CLIENT)
public class DrapeauRenderer implements BlockEntityRenderer<DrapeauBlockEntity> {
    private static final double LOIN2 = 48 * 48, ETIQUETTE2 = 24 * 24;
    private static final float MI_MAT = -2.0f;

    private final BlockEntityRendererProvider.Context ctx;

    public DrapeauRenderer(BlockEntityRendererProvider.Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void render(DrapeauBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockPos pos = be.getBlockPos();
        Camera cam = ctx.getBlockEntityRenderDispatcher().camera;
        if (cam == null) return;
        double d2 = cam.getPosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5);
        boolean loin = d2 > LOIN2;
        float t = (float) (level.getGameTime() % 200000L) + partialTick;
        float phase = ((pos.getX() * 31 + pos.getZ() * 17) & 1023) * 0.35f;
        int lumiere = LevelRenderer.getLightColor(level, pos.above(3));
        BlockState state = be.getBlockState();
        float rot = state.hasProperty(DrapeauBlock.FACING) ? 180 - state.getValue(DrapeauBlock.FACING).toYRot() : 0;

        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        Maillage.get(loin ? "drapeau_mat_loin" : "drapeau_mat").dessiner(ps, buf, lumiere, t, phase);
        ps.pushPose();
        if (be.miMat()) ps.translate(0, MI_MAT, 0);
        Maillage.get(loin ? "drapeau_tissu_loin" : "drapeau_tissu").dessiner(ps, buf, lumiere, t, phase, TissuTexture.type(be.banniere));
        ps.popPose();
        ps.popPose();

        if (d2 < ETIQUETTE2 && !be.zoneNom.isEmpty()) etiquette(be, ps, buf, cam, lumiere);
    }

    /** Nom de la zone + [TAG] en face de la camera, comme une plaque de nom (texte cache en memoire sur le BE). */
    private void etiquette(DrapeauBlockEntity be, PoseStack ps, MultiBufferSource buf, Camera cam, int lumiere) {
        String cle = be.zoneNom + "|" + be.proprioTag;
        if (!(be.clientCache instanceof Component) || !cle.equals(be.clientCle)) {
            String s = be.proprioTag.isEmpty() ? be.zoneNom : be.zoneNom + "  [" + be.proprioTag + "]";
            be.clientCache = Ui.lit(s, Ui.BOLD);
            be.clientCle = cle;
        }
        Component txt = (Component) be.clientCache;
        Font font = ctx.getFont();
        ps.pushPose();
        ps.translate(0.5, 5.7, 0.5);
        ps.mulPose(cam.rotation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        float x = -font.width(txt) / 2f;
        int fond = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255) << 24;
        int couleur = Ui.argb(Ui.TEXT, 255);
        font.drawInBatch(txt, x, 0, couleur, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, fond, lumiere);
        font.drawInBatch(txt, x, 0, couleur, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, lumiere);
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
