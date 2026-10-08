package fr.heralisz.utils.ascenseur;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.deco.Maillage;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Rendu de l'ascenseur : la cabine (entité), et au sommet le chevalement, la grande roue qui tourne d'autant que la
 * corde se déroule, la corde de la cabine (devant la roue, au-dessus de l'axe du puits) et celle du contrepoids
 * (derrière, 2 rayons plus loin), le contrepoids qui monte quand la cabine descend. Maillages de gen_faille_meshes.py.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AscenseurClient {
    static final float R_ROUE = 2.5f;
    static final float Y_ROUE = 2.6f;                       // centre de la roue au-dessus du centre du bloc machine
    private static final ResourceLocation CORDE = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/anim/corde_ascenseur.png");

    private AscenseurClient() {}

    @SubscribeEvent
    public static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(Ascenseur.MACHINE_BE.get(), MachineRenderer::new);
        e.registerEntityRenderer(Ascenseur.CABINE.get(), CabineRenderer::new);
    }

    /** Corde verticale de section carrée (0,07 bloc) entre deux points du repère courant. */
    static void corde(PoseStack ps, MultiBufferSource buf, float x, float y0, float z, float y1, int light) {
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(CORDE));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float w = 0.035f, lo = Math.min(y0, y1), hi = Math.max(y0, y1), v1 = (hi - lo) * 2f;
        float[][] faces = {{-w, -w, w, -w, 0, 0, -1}, {w, w, -w, w, 0, 0, 1}, {w, -w, w, w, 1, 0, 0}, {-w, w, -w, -w, -1, 0, 0}};
        for (float[] f : faces) {
            vc.vertex(m, x + f[0], lo, z + f[1]).color(255, 255, 255, 255).uv(0f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(n, f[4], f[5], f[6]).endVertex();
            vc.vertex(m, x + f[2], lo, z + f[3]).color(255, 255, 255, 255).uv(1f, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(n, f[4], f[5], f[6]).endVertex();
            vc.vertex(m, x + f[2], hi, z + f[3]).color(255, 255, 255, 255).uv(1f, 0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(n, f[4], f[5], f[6]).endVertex();
            vc.vertex(m, x + f[0], hi, z + f[1]).color(255, 255, 255, 255).uv(0f, 0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(n, f[4], f[5], f[6]).endVertex();
        }
    }

    /** Machine : chevalement, roue, cordes, contrepoids (repère tourné : avant = -Z vers les paliers). */
    public static final class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
        public MachineRenderer(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public void render(MachineBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            if (be.getLevel() == null) {
                return;
            }
            int[] pal = be.paliers();
            BlockPos p = be.getBlockPos();
            float base = p.getY() + 0.5f;
            Cabine c = be.cabineClient();
            float haut = pal.length > 0 ? pal[pal.length - 1] : p.getY() - 10;
            float bas = pal.length > 0 ? pal[0] : haut - 20;
            float deck = c != null ? (float) (Mth.lerp(partial, c.yOld, c.getY()) + Cabine.PONT) : haut;
            float angle = (float) Math.toDegrees((haut - deck) / R_ROUE);   // corde déroulée / rayon
            float contrepoids = bas + haut - deck + 1.5f;
            Direction f = be.facade();
            ps.pushPose();
            ps.translate(0.5, 0.5, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(180f - f.toYRot()));
            Maillage.get("ascenseur_tete").dessiner(ps, buf, light, 0f, 0f);
            Maillage.get("ascenseur_roue").dessiner(ps, buf, light, angle, 0f);
            int lumBas = c != null ? LevelRenderer.getLightColor(be.getLevel(), c.blockPosition().above()) : light;
            corde(ps, buf, 0f, Y_ROUE, 0f, (float) (deck - Cabine.PONT + Cabine.CADRE + 0.42f) - base, lumBas);
            corde(ps, buf, 0f, Y_ROUE, 2 * R_ROUE, contrepoids - base + 0.12f, light);
            ps.pushPose();
            ps.translate(0f, contrepoids - base, 2 * R_ROUE);
            Maillage.get("ascenseur_contrepoids").dessiner(ps, buf, light, 0f, 0f);
            ps.popPose();
            ps.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(MachineBlockEntity be) {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 192;
        }
    }

    /** Cabine : maillage tourné vers les paliers (version allégée au loin). */
    public static final class CabineRenderer extends EntityRenderer<Cabine> {
        public CabineRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 0f;
        }

        @Override
        public void render(Cabine c, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180f - c.facade().toYRot()));
            double d2 = entityRenderDispatcher.distanceToSqr(c);
            Maillage.get(d2 > 48 * 48 ? "ascenseur_cabine_loin" : "ascenseur_cabine").dessiner(ps, buf, light, 0f, 0f);
            ps.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(Cabine c) {
            return CORDE;
        }

        /** Lumière lue au-dessus du plancher (en bas du puits, la cabine touche le sol : elle serait noire). */
        @Override
        protected int getBlockLightLevel(Cabine c, BlockPos p) {
            return c.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p.above());
        }

        @Override
        protected int getSkyLightLevel(Cabine c, BlockPos p) {
            return c.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, p.above());
        }
    }
}
