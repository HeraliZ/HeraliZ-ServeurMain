package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rendu Minecraft d'une liste de commandes (DrawList) produite par un modele : maillages opaques et leur calque
 * lumineux, maillages translucides et additifs, sprites lumineux, textes. Chaque phase utilise un seul type de rendu.
 */
public final class SceneRenderer {
    private SceneRenderer() {
    }

    private static final Map<Sprite, ResourceLocation> SPRITES = new EnumMap<>(Sprite.class);

    static {
        for (Sprite s : Sprite.values()) SPRITES.put(s, HeralizCasino.id(s.path()));
    }

    public static ResourceLocation texture(String designId) {
        return HeralizCasino.id("textures/machine/" + designId + ".png");
    }

    public static ResourceLocation glowTexture(String designId) {
        return HeralizCasino.id("textures/machine/" + designId + "_glow.png");
    }

    /** Feuilles de texture supplementaires d'une machine ({couleur, calque lumineux}), par "machine/feuille". */
    private static final Map<String, ResourceLocation[]> SHEETS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String[] NO_SHEET = new String[0];

    private static ResourceLocation[] sheet(String designId, String sheet) {
        return SHEETS.computeIfAbsent(designId + "/" + sheet, k -> new ResourceLocation[]{texture(sheetTexture(designId, sheet)), glowTexture(sheetTexture(designId, sheet))});
    }

    /**
     * Nom de la texture d'une feuille : &lt;machine&gt;_&lt;feuille&gt;, ou, si le nom commence par "/", une feuille commune
     * a plusieurs machines ("/recup" : textures/machine/recup.png, les pieces de recuperation).
     */
    public static String sheetTexture(String designId, String sheet) {
        return sheet.startsWith("/") ? sheet.substring(1) : designId + "_" + sheet;
    }

    /** Feuilles supplementaires utilisees par les maillages de la liste (aucune dans le cas courant). */
    private static String[] sheets(DrawList list) {
        String[] out = NO_SHEET;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sheet == null || o.mesh == null) continue;
            boolean seen = false;
            for (String s : out) seen |= s.equals(o.sheet);
            if (seen) continue;
            out = java.util.Arrays.copyOf(out, out.length + 1);
            out[out.length - 1] = o.sheet;
        }
        return out;
    }

    private static boolean on(DrawList.Op o, String sheet) {
        return sheet == null ? o.sheet == null : sheet.equals(o.sheet);
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
        /** Eclat des parties lumineuses, halos et faisceaux (0 a 1) : machine dont la lumiere a ete baissee. */
        public float glow = 1f;
        /** Rendu d'objet (inventaire, main) : pas de sprites face camera ni de textes. */
        public boolean item;
        /** Position monde de l'origine de la pose (orientation des faisceaux vers la camera). */
        public Vec3 origin = Vec3.ZERO;
        /** Distance de la camera (blocs) : les details illisibles de loin (textes, petits eclats) sont omis. */
        public float dist;
        public ResourceLocation tex, glowTex;
        public final String designId;

        final Matrix4f m4 = new Matrix4f();
        final Matrix3f n3 = new Matrix3f();
        private final Matrix3f tmp = new Matrix3f();
        private final Vector3f v = new Vector3f();

        public Frame(PoseStack pose, MultiBufferSource buf, String designId) {
            this.pose = pose;
            this.buf = buf;
            this.designId = designId;
            this.tex = texture(designId);
            this.glowTex = glowTexture(designId);
        }

        void matrices(DrawList.Op o) {
            m4.set(pose.last().pose()).mul(model).mul(o.m);
            n3.set(pose.last().normal()).mul(model.normal(tmp));
            n3.mul(o.m.normal(tmp));
        }

        Vector3f point(DrawList.Op o, float x, float y, float z) {
            o.m.transformPosition(x, y, z, v);
            return model.transformPosition(v);
        }
    }

    /** Toutes les phases dans l'ordre. */
    public static void all(Frame f, DrawList list) {
        solid(f, list);
        translucent(f, list);
        glow(f, list);
        sprites(f, list);
        text(f, list);
    }

    // =================================================================== phases
    public static void solid(Frame f, DrawList list) {
        solid(f, list, null, f.tex);
        for (String s : sheets(list)) solid(f, list, s, sheet(f.designId, s)[0]);
    }

    private static void solid(Frame f, DrawList list, String sheet, ResourceLocation tex) {
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.SOLID || !on(o, sheet)) continue;
            if (vc == null) vc = f.buf.getBuffer(CasinoRenderTypes.solid(tex));
            f.matrices(o);
            MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, 1f, f.light, f.overlay, true);
        }
    }

    public static void translucent(Frame f, DrawList list) {
        translucent(f, list, null, f.tex);
        for (String s : sheets(list)) translucent(f, list, s, sheet(f.designId, s)[0]);
    }

    private static void translucent(Frame f, DrawList list, String sheet, ResourceLocation tex) {
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.TRANSLUCENT || !on(o, sheet)) continue;
            if (vc == null) vc = f.buf.getBuffer(CasinoRenderTypes.translucent(tex));
            f.matrices(o);
            MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, o.a, f.light, f.overlay, true);
        }
    }

    public static void glow(Frame f, DrawList list) {
        glow(f, list, null, f.glowTex);
        for (String s : sheets(list)) glow(f, list, s, sheet(f.designId, s)[1]);
    }

    private static void glow(Frame f, DrawList list, String sheet, ResourceLocation glowTex) {
        VertexConsumer vc = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.mesh == null || !on(o, sheet)) continue;
            if (o.type == DrawList.SOLID && o.glow > 0.003f) {
                Mesh lit = GlowFilter.glowOnly(o.mesh, glowTex);
                if (lit.isEmpty()) continue;
                if (vc == null) vc = f.buf.getBuffer(CasinoRenderTypes.glow(glowTex));
                float k = Math.min(1f, o.glow) * f.glow;
                f.matrices(o);
                MeshRender.render(lit, f.m4, f.n3, vc, k, k, k, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            } else if (o.type == DrawList.ADDITIVE) {
                if (vc == null) vc = f.buf.getBuffer(CasinoRenderTypes.glow(glowTex));
                f.matrices(o);
                MeshRender.render(o.mesh, f.m4, f.n3, vc, o.r, o.g, o.b, o.a * f.glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, true);
            }
        }
    }

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
            VertexConsumer vc = f.buf.getBuffer(CasinoRenderTypes.glow(SPRITES.get(s)));
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

    /** Au-dela, les petits eclats lumineux ne sont plus dessines (moins d'un pixel a l'ecran). */
    public static final float SPRITE_FAR = 24, SPRITE_SMALL = 0.9f;
    /** Au-dela de TEXT_FAR blocs, les textes ne sont plus lisibles : omis ; au-dela de TEXT_OUTLINE, sans contour. */
    public static final float TEXT_FAR = 20, TEXT_OUTLINE = 9;

    private static boolean visible(Frame f, DrawList.Op o) {
        return switch (o.type) {
            case DrawList.QUAD -> true;
            case DrawList.BILLBOARD -> !f.item && (f.dist <= SPRITE_FAR || o.size >= SPRITE_SMALL);
            case DrawList.BEAM, DrawList.RAYS -> !f.item;
            default -> false;
        };
    }

    /** Textes : police du jeu, plein eclat, legerement decolles de la surface. */
    public static void text(Frame f, DrawList list) {
        if (f.item || f.dist > TEXT_FAR) return;
        Font font = Minecraft.getInstance().font;
        Quaternionf cam = null;
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.TEXT || o.text == null) continue;
            PoseStack pose = f.pose;
            pose.pushPose();
            float k = o.size / 7f;
            int a = Math.max(5, Math.min(255, (int) (o.a * 255)));
            int color = (a << 24) | ((int) (o.r * 255) << 16) | ((int) (o.g * 255) << 8) | (int) (o.b * 255);
            FormattedCharSequence seq = (o.flags & DrawList.T_BOLD) != 0
                    ? Component.literal(o.text).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText()
                    : FormattedCharSequence.forward(o.text, Style.EMPTY);
            float w = font.width(seq);
            float x0 = (o.flags & DrawList.T_CENTER) != 0 ? -w / 2f : (o.flags & DrawList.T_RIGHT) != 0 ? -w : 0;
            if ((o.flags & DrawList.T_BILLBOARD) != 0) {
                Vector3f c = f.point(o, o.x, o.y, o.z);
                pose.translate(c.x, c.y, c.z);
                if (cam == null) cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
                pose.mulPose(cam);
                float s = k * f.unit;
                pose.scale(-s, -s, s);
            } else {
                pose.mulPoseMatrix(new Matrix4f(f.model).mul(o.m));
                pose.translate(o.x, o.y, o.z);
                pose.scale(k, -k, k);
            }
            float y0 = -3.5f;
            Matrix4f m = pose.last().pose();
            if ((o.flags & DrawList.T_OUTLINE) != 0 && f.dist <= TEXT_OUTLINE) {
                font.drawInBatch8xOutline(seq, x0, y0, color, (a << 24) | (o.outline & 0xFFFFFF), m, f.buf, LightTexture.FULL_BRIGHT);
            } else {
                font.drawInBatch(seq, x0, y0, color, (o.flags & DrawList.T_SHADOW) != 0, m, f.buf, Font.DisplayMode.POLYGON_OFFSET, 0,
                        LightTexture.FULL_BRIGHT);
            }
            pose.popPose();
        }
    }

    // =================================================================== sprites
    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, DrawList.Op o, float alpha) {
        vc.vertex(m, x, y, z).color(o.r, o.g, o.b, Math.min(1f, o.a * alpha)).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0, 1, 0).endVertex();
    }

    private static void quad(Frame f, VertexConsumer vc, DrawList.Op o) {
        f.matrices(o);
        Matrix4f m = f.m4;
        vert(vc, m, o.x, o.y, 0, 0, 1, o, f.glow);
        vert(vc, m, o.x1, o.y, 0, 1, 1, o, f.glow);
        vert(vc, m, o.x1, o.y1, 0, 1, 0, o, f.glow);
        vert(vc, m, o.x, o.y1, 0, 0, 0, o, f.glow);
    }

    private static void billboard(Frame f, VertexConsumer vc, DrawList.Op o, Quaternionf cam) {
        Vector3f c = f.point(o, o.x, o.y, o.z);
        float h = o.size * f.unit;
        PoseStack pose = f.pose;
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(cam);
        if (o.rot != 0) pose.mulPose(Axis.ZP.rotationDegrees(o.rot));
        Matrix4f m = pose.last().pose();
        vert(vc, m, -h, -h, 0, 0, 1, o, f.glow);
        vert(vc, m, h, -h, 0, 1, 1, o, f.glow);
        vert(vc, m, h, h, 0, 1, 0, o, f.glow);
        vert(vc, m, -h, h, 0, 0, 0, o, f.glow);
        pose.popPose();
    }

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
        vert(vc, m, -w, 0, 0, 0, 1, o, f.glow);
        vert(vc, m, w, 0, 0, 1, 1, o, f.glow);
        vert(vc, m, w * 0.6f, len, 0, 1, 0, o, 0);
        vert(vc, m, -w * 0.6f, len, 0, 0, 0, o, 0);
        pose.popPose();
    }

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
            vert(vc, m, -w * 0.15f, 0, 0, 0, 1, o, f.glow);
            vert(vc, m, w * 0.15f, 0, 0, 1, 1, o, f.glow);
            vert(vc, m, w, len, 0, 1, 0, o, 0);
            vert(vc, m, -w, len, 0, 0, 0, o, 0);
            pose.popPose();
        }
        pose.popPose();
    }
}
