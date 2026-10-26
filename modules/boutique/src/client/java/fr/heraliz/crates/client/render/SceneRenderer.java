package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.client.render.scene.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rendu Minecraft d'une liste de commandes (DrawList) produite par un design : maillages opaques et leur calque
 * lumineux, maillages translucides et additifs, sprites lumineux. Chaque phase utilise un seul type de rendu
 * (sauf les sprites, regroupes par texture) pour limiter les changements d'etat.
 */
public final class SceneRenderer {
    private SceneRenderer() {
    }

    private static final Map<Sprite, ResourceLocation> SPRITES = new EnumMap<>(Sprite.class);

    static {
        for (Sprite s : Sprite.values()) SPRITES.put(s, HeralizCrates.id(s.path()));
    }

    public static ResourceLocation texture(String designId) {
        return HeralizCrates.id("textures/crate/" + designId + ".png");
    }

    public static ResourceLocation glowTexture(String designId) {
        return HeralizCrates.id("textures/crate/" + designId + "_glow.png");
    }

    /** Contexte d'une image. */
    public static final class Frame {
        public PoseStack pose;
        public MultiBufferSource buf;
        /** Repere du modele (pixels) -> repere de la pose. */
        public final Matrix4f model = new Matrix4f();
        /** Taille d'un pixel du modele dans le repere de la pose (1/16 pour un bloc). */
        public float unit = 1f / 16f;
        public int light = LightTexture.FULL_BRIGHT;
        public int overlay = OverlayTexture.NO_OVERLAY;
        /** Rendu d'objet (inventaire, main) : pas de sprites face camera ni de faisceaux. */
        public boolean item;
        /** Maillages fixes dessines depuis la carte graphique (caisses du monde, voir GpuScene). */
        public boolean gpu;
        /** Position monde de l'origine de la pose (orientation des faisceaux vers la camera). */
        public Vec3 origin = Vec3.ZERO;
        public ResourceLocation tex, glowTex;

        final Matrix4f m4 = new Matrix4f();
        final Matrix3f n3 = new Matrix3f();
        private final Matrix3f tmp = new Matrix3f();
        private final Vector3f v = new Vector3f();

        public Frame(PoseStack pose, MultiBufferSource buf, String designId) {
            this.pose = pose;
            this.buf = buf;
            this.tex = texture(designId);
            this.glowTex = glowTexture(designId);
        }

        void matrices(DrawList.Op o) {
            m4.set(pose.last().pose()).mul(model).mul(o.m);
            n3.set(pose.last().normal()).mul(model.normal(tmp));
            n3.mul(o.m.normal(tmp));
        }

        /** Point (pixels, repere de la commande) -> repere de la pose. */
        Vector3f point(DrawList.Op o, float x, float y, float z) {
            o.m.transformPosition(x, y, z, v);
            return model.transformPosition(v);
        }
    }

    // =================================================================== phases
    /** Maillages opaques : texture de l'atlas, eclairage du monde, ombrage des sommets. */
    public static void solid(Frame f, DrawList list) {
        boolean gpu = f.gpu && GpuScene.enabled();
        if (gpu) GpuScene.solid(f, list);
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.SOLID || (gpu && onGpu(o.mesh, true))) continue;
            if (vc == null) vc = f.buf.getBuffer(HzRenderTypes.solid(f.tex));
            f.matrices(o);
            MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, 1f, f.light, f.overlay, true);
        }
    }

    /** Maillage deja dessine depuis la carte graphique pour cette image. */
    private static boolean onGpu(Mesh mesh, boolean tinted) {
        return mesh != null && mesh.cacheable && GpuScene.existing(mesh, tinted) != null;
    }

    /** Maillages translucides (verre, liquides, anneaux) : alpha de la texture x alpha de la commande. */
    public static void translucent(Frame f, DrawList list) {
        boolean gpu = f.gpu && GpuScene.enabled();
        if (gpu) {
            // les pieces opaques encore dans le tampon (maillages reconstruits) passent avant le verre
            flush(f, HzRenderTypes.solid(f.tex));
            GpuScene.translucent(f, list);
        }
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.TRANSLUCENT || (gpu && onGpu(o.mesh, true))) continue;
            if (vc == null) vc = f.buf.getBuffer(HzRenderTypes.translucent(f.tex));
            f.matrices(o);
            MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, o.a, f.light, f.overlay, true);
        }
    }

    /** Calque lumineux des maillages opaques + maillages additifs (meme texture : un seul lot). */
    public static void glow(Frame f, DrawList list) {
        boolean gpu = f.gpu && GpuScene.enabled();
        if (gpu) {
            flush(f, HzRenderTypes.translucent(f.tex));
            GpuScene.glow(f, list);
        }
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type == DrawList.SOLID && o.glow > 0.003f) {
                Mesh lit = GlowFilter.glowOnly(o.mesh, f.glowTex);
                if (lit.isEmpty() || (gpu && onGpu(lit, false))) continue;
                if (vc == null) vc = f.buf.getBuffer(HzRenderTypes.glow(f.glowTex));
                float k = Math.min(1f, o.glow);
                f.matrices(o);
                MeshRender.render(lit, f.m4, f.n3, vc, k, k, k, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            } else if (o.type == DrawList.ADDITIVE) {
                if (gpu && onGpu(o.mesh, true)) continue;
                if (vc == null) vc = f.buf.getBuffer(HzRenderTypes.glow(f.glowTex));
                f.matrices(o);
                MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, o.a, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true);
            }
        }
    }

    /** Sprites lumineux (halos, runes, faisceaux, rayons), regroupes par texture. */
    public static void sprites(Frame f, DrawList list) {
        long used = 0;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sprite != null && visible(f, o)) used |= 1L << o.sprite.ordinal();
        }
        if (used == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Quaternionf cam = mc.getEntityRenderDispatcher().cameraOrientation();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        for (Sprite s : Sprite.values()) {
            if ((used & (1L << s.ordinal())) == 0) continue;
            VertexConsumer vc = f.buf.getBuffer(HzRenderTypes.glow(SPRITES.get(s)));
            for (int i = 0; i < list.size(); i++) {
                DrawList.Op o = list.get(i);
                if (o.sprite != s || !visible(f, o)) continue;
                switch (o.type) {
                    case DrawList.QUAD -> quad(f, vc, o);
                    case DrawList.BILLBOARD -> billboard(f, vc, o, cam);
                    case DrawList.BEAM -> beam(f, vc, o, camPos);
                    case DrawList.RAYS -> rays(f, vc, o, cam);
                    default -> {
                    }
                }
            }
        }
    }

    /** Dessine tout de suite ce qui attend dans le tampon pour ce type (ordre de dessin avec la carte graphique). */
    private static void flush(Frame f, net.minecraft.client.renderer.RenderType type) {
        if (f.buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch(type);
    }

    private static boolean visible(Frame f, DrawList.Op o) {
        return switch (o.type) {
            case DrawList.QUAD -> true;
            case DrawList.BILLBOARD, DrawList.BEAM, DrawList.RAYS -> !f.item;
            default -> false;
        };
    }

    // =================================================================== sprites
    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, DrawList.Op o, float alpha) {
        vc.vertex(m, x, y, z).color(o.r, o.g, o.b, Math.min(1f, o.a * alpha)).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    /** Sprite dans le plan XY de la commande. */
    private static void quad(Frame f, VertexConsumer vc, DrawList.Op o) {
        f.matrices(o);
        Matrix4f m = f.m4;
        vert(vc, m, o.x, o.y, 0, 0, 1, o, 1);
        vert(vc, m, o.x1, o.y, 0, 1, 1, o, 1);
        vert(vc, m, o.x1, o.y1, 0, 1, 0, o, 1);
        vert(vc, m, o.x, o.y1, 0, 0, 0, o, 1);
    }

    /** Sprite toujours face a la camera. */
    private static void billboard(Frame f, VertexConsumer vc, DrawList.Op o, Quaternionf cam) {
        Vector3f c = f.point(o, o.x, o.y, o.z);
        float h = o.size * f.unit;
        PoseStack pose = f.pose;
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(cam);
        if (o.rot != 0) pose.mulPose(Axis.ZP.rotationDegrees(o.rot));
        Matrix4f m = pose.last().pose();
        vert(vc, m, -h, -h, 0, 0, 1, o, 1);
        vert(vc, m, h, -h, 0, 1, 1, o, 1);
        vert(vc, m, h, h, 0, 1, 0, o, 1);
        vert(vc, m, -h, h, 0, 0, 0, o, 1);
        pose.popPose();
    }

    /** Faisceau vertical tourne vers la camera, fondu vers le haut. */
    private static void beam(Frame f, VertexConsumer vc, DrawList.Op o, Vec3 camPos) {
        Vector3f c = f.point(o, o.x, o.y, o.z);
        double dx = camPos.x - (f.origin.x + c.x), dz = camPos.z - (f.origin.z + c.z);
        float yaw = (float) Math.atan2(dx, dz);
        float w = o.width * f.unit, len = o.len * f.unit;
        PoseStack pose = f.pose;
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(Axis.YP.rotation(yaw));
        Matrix4f m = pose.last().pose();
        vert(vc, m, -w, 0, 0, 0, 1, o, 1);
        vert(vc, m, w, 0, 0, 1, 1, o, 1);
        vert(vc, m, w * 0.6f, len, 0, 1, 0, o, 0);
        vert(vc, m, -w * 0.6f, len, 0, 0, 0, o, 0);
        pose.popPose();
    }

    /** Eventail de rayons face a la camera. */
    private static void rays(Frame f, VertexConsumer vc, DrawList.Op o, Quaternionf cam) {
        Vector3f c = f.point(o, o.x, o.y, o.z);
        float w = o.width * f.unit;
        PoseStack pose = f.pose;
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(cam);
        for (int i = 0; i < o.count; i++) {
            float len = o.len * f.unit * (i % 2 == 0 ? 1f : 0.62f);
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(o.rot + i * 360f / o.count));
            Matrix4f m = pose.last().pose();
            vert(vc, m, -w * 0.15f, 0, 0, 0, 1, o, 1);
            vert(vc, m, w * 0.15f, 0, 0, 1, 1, o, 1);
            vert(vc, m, w, len, 0, 1, 0, o, 0);
            vert(vc, m, -w, len, 0, 0, 0, o, 0);
            pose.popPose();
        }
        pose.popPose();
    }
}
