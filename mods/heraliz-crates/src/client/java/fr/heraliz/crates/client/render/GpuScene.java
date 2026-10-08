package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.scene.DrawList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Animation des caisses sur la carte graphique. Chaque maillage fixe d'un design (la quasi-totalite des sommets) est
 * envoye une fois dans un VertexBuffer ; une image ne coute plus qu'une matrice et une couleur par piece, au lieu de
 * recalculer des dizaines de milliers de sommets sur le processeur. Seuls les maillages reconstruits a chaque image
 * (tentacules, trainees...) restent sur le tampon classique.
 * Desactive avec un pack de shaders (Oculus/Iris), comme les caisses figees.
 */
public final class GpuScene {
    private GpuScene() {
    }

    /**
     * Sommets envoyes a la carte graphique par image au plus (nouveaux maillages, caisses fraichement cuites) : la
     * premiere ouverture d'une caisse s'etale sur quelques images au lieu de figer une image.
     */
    private static final int UPLOAD_BUDGET = 60_000;
    private static int budget = UPLOAD_BUDGET;

    /** Debut d'une image (evenement de rendu). */
    public static void newFrame() {
        budget = UPLOAD_BUDGET;
        now = net.minecraft.Util.getMillis();
        if (now - lastSweep > 10_000) {
            lastSweep = now;
            sweep(TINTED);
            sweep(PLAIN);
        }
    }

    /** Libere la memoire graphique des caisses qui ne se sont pas ouvertes depuis une minute. */
    private static void sweep(Map<Mesh, Entry> map) {
        map.values().removeIf(e -> {
            if (now - e.used < IDLE_FREE_MS) return false;
            e.vb.close();
            return true;
        });
    }

    /** Reserve de quoi envoyer n sommets cette image ; false = a faire a l'image suivante. */
    static boolean spend(int vertices) {
        if (budget <= 0) return false;
        budget -= vertices;
        return true;
    }

    /** Tampon d'un maillage et dernier usage (ms) : liberes apres une minute sans ouverture de cette caisse. */
    private static final class Entry {
        final VertexBuffer vb;
        long used;

        Entry(VertexBuffer vb, long used) {
            this.vb = vb;
            this.used = used;
        }
    }

    private static final long IDLE_FREE_MS = 60_000;

    /** Maillages avec leurs couleurs de sommet (ombrage) / en blanc (calque lumineux, teinte par la commande). */
    private static final Map<Mesh, Entry> TINTED = new IdentityHashMap<>(), PLAIN = new IdentityHashMap<>();
    private static long now, lastSweep;

    private static ShaderInstance shader;
    private static final Matrix4f MV = new Matrix4f();
    private static final Vector3f L0 = new Vector3f(), L1 = new Vector3f(), TMP = new Vector3f();

    static boolean enabled() {
        return StaticCrate.useVbo();
    }

    /** Libere les tampons (rechargement des ressources). */
    static void clear() {
        for (Entry e : TINTED.values()) e.vb.close();
        for (Entry e : PLAIN.values()) e.vb.close();
        TINTED.clear();
        PLAIN.clear();
    }

    /** Tampon du maillage deja sur la carte graphique, ou null. */
    static VertexBuffer existing(Mesh mesh, boolean tinted) {
        Entry e = (tinted ? TINTED : PLAIN).get(mesh);
        return e == null ? null : e.vb;
    }

    /** Tampon du maillage, envoye maintenant si le budget de l'image le permet ; null = dessin classique cette image. */
    private static VertexBuffer vbo(Mesh mesh, boolean tinted) {
        Map<Mesh, Entry> map = tinted ? TINTED : PLAIN;
        Entry e = map.get(mesh);
        if (e != null) {
            e.used = now;
            return e.vb;
        }
        if (!spend(mesh.vertexCount())) return null;
        float[] d = mesh.data();
        BufferBuilder bb = StaticCrate.uploader();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (int i = 0, o = 0; i < mesh.vertexCount(); i++, o += Mesh.STRIDE) {
            float r = tinted ? d[o + 8] : 1f, g = tinted ? d[o + 9] : 1f, b = tinted ? d[o + 10] : 1f;
            bb.vertex(d[o], d[o + 1], d[o + 2]).color(r, g, b, 1f).uv(d[o + 3], d[o + 4]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(d[o + 5], d[o + 6], d[o + 7]).endVertex();
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.end());
        VertexBuffer.unbind();
        map.put(mesh, new Entry(vb, now));
        return vb;
    }

    private static boolean gpuMesh(Mesh m) {
        return m != null && m.cacheable && !m.isEmpty();
    }

    // =================================================================== phases
    /** Pieces opaques (maillages fixes) : texture de l'atlas, eclairage du monde, ombrage des sommets. */
    static void solid(SceneRenderer.Frame f, DrawList list) {
        boolean any = false;
        for (int i = 0; i < list.size() && !any; i++) {
            DrawList.Op o = list.get(i);
            any = o.type == DrawList.SOLID && gpuMesh(o.mesh);
        }
        if (!any) return;
        RenderType rt = HzRenderTypes.solid(f.tex);
        if (!begin(rt)) return;
        try {
            for (int i = 0; i < list.size(); i++) {
                DrawList.Op o = list.get(i);
                if (o.type == DrawList.SOLID && gpuMesh(o.mesh)) draw(f, o, vbo(o.mesh, true), o.r, o.g, o.b, 1f, true);
            }
        } finally {
            end(rt);
        }
    }

    /**
     * Pieces translucides fixes (verre, liquides) : dessinees dans l'ordre du design, sans tri des faces, comme la caisse
     * au repos (le tri par image est reserve aux maillages reconstruits a chaque image).
     */
    static void translucent(SceneRenderer.Frame f, DrawList list) {
        boolean any = false;
        for (int i = 0; i < list.size() && !any; i++) {
            DrawList.Op o = list.get(i);
            any = o.type == DrawList.TRANSLUCENT && gpuMesh(o.mesh);
        }
        if (!any) return;
        RenderType rt = HzRenderTypes.translucent(f.tex);
        if (!begin(rt)) return;
        try {
            for (int i = 0; i < list.size(); i++) {
                DrawList.Op o = list.get(i);
                if (o.type == DrawList.TRANSLUCENT && gpuMesh(o.mesh)) draw(f, o, vbo(o.mesh, true), o.r, o.g, o.b, o.a, true);
            }
        } finally {
            end(rt);
        }
    }

    /** Calque lumineux des pieces opaques + pieces additives (meme texture, un seul passage). */
    static void glow(SceneRenderer.Frame f, DrawList list) {
        boolean any = false;
        for (int i = 0; i < list.size() && !any; i++) {
            DrawList.Op o = list.get(i);
            any = gpuMesh(o.mesh) && (o.type == DrawList.ADDITIVE || (o.type == DrawList.SOLID && o.glow > 0.003f));
        }
        if (!any) return;
        RenderType rt = HzRenderTypes.glow(f.glowTex);
        if (!begin(rt)) return;
        try {
            for (int i = 0; i < list.size(); i++) {
                DrawList.Op o = list.get(i);
                if (!gpuMesh(o.mesh)) continue;
                if (o.type == DrawList.SOLID && o.glow > 0.003f) {
                    Mesh lit = GlowFilter.glowOnly(o.mesh, f.glowTex);
                    if (lit.isEmpty()) continue;
                    float k = Math.min(1f, o.glow);
                    draw(f, o, vbo(lit, false), k, k, k, 1f, false);
                } else if (o.type == DrawList.ADDITIVE) {
                    draw(f, o, vbo(o.mesh, true), o.r, o.g, o.b, o.a, false);
                }
            }
        } finally {
            end(rt);
        }
    }

    // =================================================================== etat du shader
    /** Prepare le type de rendu et le shader une fois pour toutes les pieces du passage (comme VertexBuffer.drawWithShader). */
    private static boolean begin(RenderType rt) {
        rt.setupRenderState();
        shader = RenderSystem.getShader();
        if (shader == null) {
            rt.clearRenderState();
            return false;
        }
        for (int i = 0; i < 12; i++) shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(1f, 1f, 1f, 1f);
        if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        if (shader.SCREEN_SIZE != null) {
            Window w = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) w.getWidth(), (float) w.getHeight());
        }
        if (shader.LINE_WIDTH != null) shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        RenderSystem.setupShaderLights(shader);
        if (shader.LIGHT0_DIRECTION != null) read(shader.LIGHT0_DIRECTION.getFloatBuffer(), L0);
        if (shader.LIGHT1_DIRECTION != null) read(shader.LIGHT1_DIRECTION.getFloatBuffer(), L1);
        shader.apply();
        return true;
    }

    private static void read(FloatBuffer fb, Vector3f out) {
        out.set(fb.get(0), fb.get(1), fb.get(2));
    }

    /** Une piece : sa matrice, sa teinte et (si eclairee) les lumieres ramenees dans le repere du maillage. */
    private static void draw(SceneRenderer.Frame f, DrawList.Op o, VertexBuffer vb, float r, float g, float b, float a, boolean lit) {
        if (vb == null) return;
        f.matrices(o);
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(MV.set(RenderSystem.getModelViewMatrix()).mul(f.m4));
            shader.MODEL_VIEW_MATRIX.upload();
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(r, g, b, a);
            shader.COLOR_MODULATOR.upload();
        }
        if (lit && shader.LIGHT0_DIRECTION != null && shader.LIGHT1_DIRECTION != null) {
            // le shader eclaire avec la normale telle quelle : on tourne les lumieres vers le repere du maillage
            shader.LIGHT0_DIRECTION.set(f.n3.transformTranspose(TMP.set(L0)));
            shader.LIGHT0_DIRECTION.upload();
            shader.LIGHT1_DIRECTION.set(f.n3.transformTranspose(TMP.set(L1)));
            shader.LIGHT1_DIRECTION.upload();
        }
        vb.bind();
        vb.draw();
    }

    private static void end(RenderType rt) {
        VertexBuffer.unbind();
        if (shader != null) shader.clear();
        shader = null;
        rt.clearRenderState();
    }
}
