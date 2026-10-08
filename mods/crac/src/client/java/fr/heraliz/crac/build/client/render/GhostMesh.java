package fr.heraliz.crac.build.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a not-yet-placed piece (building plan preview): the whole piece mesh at its world position, translucent and
 * tinted with {@code argb}. A depth-only pass first keeps the hologram a single clean layer.
 */
@OnlyIn(Dist.CLIENT)
public final class GhostMesh {
    private GhostMesh() {
    }

    /**
     * @param pose    pose stack of RenderLevelStageEvent (world space, camera NOT subtracted yet)
     * @param camera  camera position, subtracted here
     * @param state   piece state bits (door open, window insert), see {@code Piece}
     * @param variant neighbour variant (roof sectors, wall posts)
     * @param argb    tint, e.g. 0x8040A0FF for a valid placement, 0x80FF4040 for an invalid one
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera, PieceKey key, Tier tier, int state, long variant, int argb) {
        PieceBakery.GhostQuads mesh = PieceBakery.ghost(key, tier, state, variant);
        if (mesh.quads == 0) {
            return;
        }
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        // the mesh is relative to the anchor point: the translation is the only float difference from the baked piece
        BlockPos o = key.origin();
        double ax = o.getX() + key.anchor().toX() / 16.0;
        double ay = o.getY() + key.baseY() / 16.0;
        double az = o.getZ() + key.anchor().toZ() / 16.0;
        pose.pushPose();
        pose.translate(ax - camera.x, ay - camera.y, az - camera.z);
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        if (buffers instanceof MultiBufferSource.BufferSource bs) {
            emit(bs.getBuffer(Types.DEPTH), m, nm, mesh, r, g, b, a);
            bs.endBatch(Types.DEPTH);
            emit(bs.getBuffer(Types.COLOR), m, nm, mesh, r, g, b, a);
            bs.endBatch(Types.COLOR);
        } else {
            emit(buffers.getBuffer(Types.COLOR_DEPTH), m, nm, mesh, r, g, b, a);
        }
        pose.popPose();
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, PieceBakery.GhostQuads mesh, float r, float g, float b, float a) {
        float[] d = mesh.data;
        int light = LightTexture.FULL_BRIGHT;
        for (int q = 0; q < mesh.quads; q++) {
            for (int k = 0; k < 4; k++) {
                int o = q * 24 + k * 6;
                float s = d[o + 5];
                vc.vertex(m, d[o], d[o + 1], d[o + 2])
                    .color(r * s, g * s, b * s, a)
                    .uv(d[o + 3], d[o + 4])
                    .uv2(light)
                    .normal(nm, 0, 1, 0)
                    .endVertex();
            }
        }
    }

    private static final class Types extends RenderType {
        static final RenderType DEPTH = create("crac_ghost_depth", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLightmapState(LIGHTMAP)
                .setWriteMaskState(DEPTH_WRITE)
                .setCullState(CULL)
                .createCompositeState(false));
        static final RenderType COLOR = create("crac_ghost", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLightmapState(LIGHTMAP)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(CULL)
                .createCompositeState(false));
        static final RenderType COLOR_DEPTH = create("crac_ghost_single", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .setLightmapState(LIGHTMAP)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(CULL)
                .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
