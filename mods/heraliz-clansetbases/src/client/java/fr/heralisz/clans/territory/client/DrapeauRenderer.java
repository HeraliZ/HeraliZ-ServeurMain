package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.territory.DrapeauBlock;
import fr.heralisz.clans.territory.DrapeauBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Rendu du drapeau de territoire : mat (maillage drapeau_mat) + tissu qui flotte (drapeau_tissu, texture =
 * {@link TissuTexture} de la banniere du proprietaire, neutre sinon), a mi-mat (y - 2,0) pendant une capture ou une
 * contestation ; versions _loin au-dela de 48 blocs ; etiquette (nom de la zone + [TAG]) a y 5,7 a moins de 24
 * blocs. Aucun tick : tout vient du temps de jeu et de l'etat synchronise du bloc-entite.
 * <p>
 * Zone de capture (meme rendu que les zones d'extraction : panneaux verticaux lumineux, texture qui defile) : cercle
 * de rayonCapture blocs autour du mat, pose sur le sol (carte des hauteurs MOTION_BLOCKING du client, bornee a
 * +/- hauteurCapture du drapeau ; sol mis en cache sur le bloc-entite, relu toutes les 40 ticks). Couleur selon
 * l'etat : libre blanc, tenue par son clan vert, par un autre rouge, capture ou contestation orange. Visible a moins
 * de 48 blocs du mur, en fondu.
 */
@OnlyIn(Dist.CLIENT)
public class DrapeauRenderer implements BlockEntityRenderer<DrapeauBlockEntity> {
    private static final double LOIN2 = 48 * 48, ETIQUETTE2 = 24 * 24;
    private static final float MI_MAT = -2.0f;

    private static final ResourceLocation ZONE_TEX = new ResourceLocation(HeraliszClans.MODID, "textures/entity/zone_capture.png");
    private static final float ZONE_H = 1.6f;
    /** Distance (blocs) du mur au-dela de laquelle la zone n'est plus dessinee, et longueur du fondu avant. */
    private static final float ZONE_VUE = 48f, ZONE_FONDU = 12f;
    private static final int SOL_RAFRAICHI = 40;
    private static final float[] BLANC = rgb(0xE6E9EE), VERT = rgb(Ui.OK), ROUGE = rgb(Ui.DANGER), ORANGE = rgb(0xFF9A3C);

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

        if (!be.zoneId.isEmpty()) zoneCapture(be, level, pos, cam, ps, buf, t);
    }

    // ------------------------------------------------------------------ zone de capture
    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    /** Couleur du mur : capture ou contestation orange, tenue par son clan vert, par un autre rouge, libre blanc. */
    private static float[] couleur(DrapeauBlockEntity be) {
        if (be.etat == EtatZone.CAPTURE || be.etat == EtatZone.CONTESTE) return ORANGE;
        if (be.proprioTag.isEmpty()) return BLANC;
        ClanSnapshot moi = ClientClans.snapshot();
        return moi != null && be.proprioTag.equalsIgnoreCase(moi.tag()) ? VERT : ROUGE;
    }

    private static int segments(int rayon) {
        return Mth.clamp(Math.round(Mth.TWO_PI * rayon), 24, 128);
    }

    private void zoneCapture(DrapeauBlockEntity be, Level level, BlockPos pos, Camera cam, PoseStack ps, MultiBufferSource buf, float t) {
        int r = Math.max(1, be.rayonCapture);
        double dx = cam.getPosition().x - (pos.getX() + 0.5), dz = cam.getPosition().z - (pos.getZ() + 0.5);
        float dMur = Math.abs((float) Math.sqrt(dx * dx + dz * dz) - r);
        float force = Mth.clamp((ZONE_VUE - dMur) / ZONE_FONDU, 0f, 1f);
        if (force <= 0.01f) return;

        int n = segments(r);
        float[] sol = sol(be, level, pos, r, n);
        float[] c = couleur(be);
        float alpha = (0.55f + 0.15f * Mth.sin(t * 0.15f)) * force;
        float uScroll = t * 0.02f;

        PoseStack.Pose last = ps.last();
        Matrix4f m = last.pose();
        Matrix3f nm = last.normal();
        VertexConsumer vc = buf.getBuffer(RenderType.beaconBeam(ZONE_TEX, true));
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float g0 = sol[i] + 0.02f, g1 = sol[(i + 1) % n] + 0.02f;
            mur(vc, m, nm, 0.5f + Mth.cos(a0) * r, 0.5f + Mth.sin(a0) * r, g0,
                    0.5f + Mth.cos(a1) * r, 0.5f + Mth.sin(a1) * r, g1, c, alpha, uScroll + i * 0.5f);
        }
    }

    /**
     * Hauteur du sol (relative au drapeau) sous chaque point du cercle : carte MOTION_BLOCKING du client (la variante
     * NO_LEAVES est vide chez le joueur), bornee a +/- hauteurCapture ; sous un toit plus haut que la borne, premier
     * sol solide en descendant. Recalculee toutes les 40 ticks ou si le cercle change.
     */
    private static float[] sol(DrapeauBlockEntity be, Level level, BlockPos pos, int r, int n) {
        int h = Math.max(1, be.hauteurCapture);
        int cle = r * 1000 + h;
        long now = level.getGameTime();
        float[] sol = be.clientSol;
        if (sol != null && sol.length == n && be.clientSolCle == cle && now - be.clientSolTick < SOL_RAFRAICHI
                && now >= be.clientSolTick) return sol;
        if (sol == null || sol.length != n) sol = new float[n];
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int fy = pos.getY(), bas = fy - h, haut = fy + h;
        for (int i = 0; i < n; i++) {
            float a = Mth.TWO_PI * i / n;
            int x = Mth.floor(pos.getX() + 0.5 + Mth.cos(a) * r), z = Mth.floor(pos.getZ() + 0.5 + Mth.sin(a) * r);
            int y = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), haut + 1);
            int g = fy;
            for (int yy = y; yy > bas; yy--) {
                if (solide(level, mp.set(x, yy - 1, z)) && !solide(level, mp.set(x, yy, z))) {
                    g = yy;
                    break;
                }
            }
            if (y <= bas) g = bas;
            sol[i] = g - fy;
        }
        be.clientSol = sol;
        be.clientSolTick = now;
        be.clientSolCle = cle;
        return sol;
    }

    private static boolean solide(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && !s.getCollisionShape(level, p).isEmpty();
    }

    /** Panneau vertical entre (x0,z0) pose a g0 et (x1,z1) pose a g1, emis dans les deux sens (pas de culling). */
    private static void mur(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float z0, float g0,
                            float x1, float z1, float g1, float[] c, float a, float u0) {
        float u1 = u0 + 0.5f;
        sommet(vc, m, n, x0, g0 + ZONE_H, z0, c, a, u0, 0f);
        sommet(vc, m, n, x0, g0, z0, c, a, u0, 1f);
        sommet(vc, m, n, x1, g1, z1, c, a, u1, 1f);
        sommet(vc, m, n, x1, g1 + ZONE_H, z1, c, a, u1, 0f);

        sommet(vc, m, n, x1, g1 + ZONE_H, z1, c, a, u1, 0f);
        sommet(vc, m, n, x1, g1, z1, c, a, u1, 1f);
        sommet(vc, m, n, x0, g0, z0, c, a, u0, 1f);
        sommet(vc, m, n, x0, g0 + ZONE_H, z0, c, a, u0, 0f);
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float a, float u, float v) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
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

    /** Le mur de la zone depasse largement du chunk du drapeau : rendu meme si la section du mat est cachee. */
    @Override
    public boolean shouldRenderOffScreen(DrapeauBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
