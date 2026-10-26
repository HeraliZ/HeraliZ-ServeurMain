package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.config.ClientConfig;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Carrosserie figee d'une machine : "cuite" une seule fois (eclairage compris) puis envoyee a la carte graphique
 * (VertexBuffer). Chaque image ne coute alors que trois appels de dessin, quel que soit le nombre de faces.
 * Si un pack de shaders (Oculus/Iris) est actif, les memes donnees sont envoyees par le tampon normal.
 */
public final class StaticModel {
    private StaticModel() {
    }

    /** Eclairage des entites de Minecraft (directions monde). */
    private static final Vector3f L0 = new Vector3f(0.2f, 1f, -0.7f).normalize();
    private static final Vector3f L1 = new Vector3f(-0.2f, 1f, 0.7f).normalize();

    private static final Map<String, Baked> CACHE = new HashMap<>();

    public static final class Baked {
        final String designId;
        /** Une partie par texture : l'atlas de la machine, puis ses feuilles (pieces de recuperation communes...). */
        final Part[] parts;
        /** Sprites figes, dessines a chaque image. */
        public final DrawList sprites = new DrawList();

        Baked(String id, Part[] parts) {
            this.designId = id;
            this.parts = parts;
        }

        void close() {
            for (Part p : parts) p.close();
        }
    }

    /** Maillages cuits qui partagent une texture (textures/machine/&lt;tex&gt;.png et son calque _glow). */
    static final class Part {
        final String tex;
        final float[] solid, trans, glow;
        final int nSolid, nTrans, nGlow;
        VertexBuffer vSolid, vTrans, vGlow;

        Part(String tex, Acc s, Acc t, Acc g) {
            this.tex = tex;
            this.solid = s.data();
            this.trans = t.data();
            this.glow = g.data();
            this.nSolid = s.n;
            this.nTrans = t.n;
            this.nGlow = g.n;
        }

        boolean uploaded() {
            return vSolid != null || vTrans != null || vGlow != null || nSolid + nTrans + nGlow == 0;
        }

        void upload() {
            vSolid = build(solid, nSolid);
            vTrans = build(trans, nTrans);
            vGlow = build(glow, nGlow);
        }

        void close() {
            if (vSolid != null) vSolid.close();
            if (vTrans != null) vTrans.close();
            if (vGlow != null) vGlow.close();
            vSolid = vTrans = vGlow = null;
        }
    }

    private static final class Acc {
        float[] d = new float[9 * 4096];
        int size, n;

        void put(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
            if (size + 9 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            d[size++] = x;
            d[size++] = y;
            d[size++] = z;
            d[size++] = u;
            d[size++] = v;
            d[size++] = Math.min(1f, r);
            d[size++] = Math.min(1f, g);
            d[size++] = Math.min(1f, b);
            d[size++] = Math.min(1f, a);
            n++;
        }

        float[] data() {
            return java.util.Arrays.copyOf(d, size);
        }

        void reset() {
            size = 0;
            n = 0;
        }
    }

    /**
     * Carrosserie seule (monde) ou carrosserie + parties animees au repos (objet).
     */
    public static synchronized Baked get(MachineDesign<?> d, float yRotDeg, boolean item) {
        String key = d.id + "|" + d.detail + "|" + Math.round(yRotDeg) + "|" + item;
        Baked b = CACHE.get(key);
        if (b == null) {
            b = bake(d, yRotDeg, item);
            CACHE.put(key, b);
        }
        return b;
    }

    public static synchronized void clear() {
        for (Baked b : CACHE.values()) b.close();
        CACHE.clear();
        clearSnapshots();
    }

    private static Baked bake(MachineDesign<?> d, float yRotDeg, boolean item) {
        DrawList list = new DrawList();
        d.renderBody(list);
        if (item) d.renderAnimatedRaw(Scene.rest(true), d.newView(), list);
        // une partie par texture : l'atlas de la machine d'abord, puis chaque feuille utilisee
        java.util.List<String> sheets = new java.util.ArrayList<>();
        sheets.add(null);
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.mesh != null && o.sheet != null && !sheets.contains(o.sheet)) sheets.add(o.sheet);
        }
        Part[] parts = new Part[sheets.size()];
        for (int k = 0; k < parts.length; k++) {
            String sheet = sheets.get(k), tex = sheet == null ? d.atlas : SceneRenderer.sheetTexture(d.atlas, sheet);
            Acc solid = new Acc(), trans = new Acc(), glow = new Acc();
            bakeOps(list, tex, sheet, yRotDeg, solid, trans, glow);
            parts[k] = new Part(tex, solid, trans, glow);
        }
        Baked b = new Baked(d.atlas, parts);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).sprite != null) b.sprites.addCopy(list.get(i));
        }
        return b;
    }

    static void bakeOps(DrawList list, String atlas, float yRotDeg, Acc solid, Acc trans, Acc glow) {
        bakeOps(list, atlas, null, yRotDeg, solid, trans, glow);
    }

    /**
     * "Cuit" dans le repere du bloc, eclairage compris, les maillages d'une liste qui utilisent la feuille "sheet"
     * (null : l'atlas de la machine) ; tex = nom de cette texture (pour son calque lumineux).
     */
    static void bakeOps(DrawList list, String tex, String sheet, float yRotDeg, Acc solid, Acc trans, Acc glow) {
        Matrix4f model = new Matrix4f().translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRotDeg)).translate(-0.5f, 0, -0.5f)
                .scale(1f / 16f);
        Matrix3f modelN = model.normal(new Matrix3f());
        Matrix4f m = new Matrix4f();
        Matrix3f n = new Matrix3f(), tmp = new Matrix3f();
        Vector3f p = new Vector3f(), nv = new Vector3f();
        ResourceLocation glowTex = SceneRenderer.glowTexture(tex);
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sprite != null || o.mesh == null || !java.util.Objects.equals(o.sheet, sheet)) continue;
            m.set(model).mul(o.m);
            n.set(modelN).mul(o.m.normal(tmp));
            switch (o.type) {
                case DrawList.SOLID -> {
                    emit(o.mesh, m, n, solid, o.r, o.g, o.b, 1f, true, true, p, nv);
                    if (o.glow > 0.003f) {
                        Mesh lit = GlowFilter.glowOnly(o.mesh, glowTex);
                        float k = Math.min(1f, o.glow);
                        emit(lit, m, n, glow, k, k, k, 1f, false, false, p, nv);
                    }
                }
                case DrawList.TRANSLUCENT -> emit(o.mesh, m, n, trans, o.r, o.g, o.b, o.a, true, true, p, nv);
                case DrawList.ADDITIVE -> emit(o.mesh, m, n, glow, o.r * o.a, o.g * o.a, o.b * o.a, 1f, true, false, p, nv);
                default -> {
                }
            }
        }
    }

    // =================================================================== parties animees des machines eloignees
    /**
     * Optimisation : de loin, les parties animees d'une machine sont "cuites" dans ses propres tampons graphiques et
     * rafraichies quelques fois par seconde ; entre deux rafraichissements, chaque image ne coute que trois appels de
     * dessin par texture (atlas de la machine, puis chaque feuille a part : pieces de recuperation, jetons, roue...).
     * Les sprites et les textes restent dessines a chaque image.
     */
    public static final class Snapshot {
        final Acc solid = new Acc(), trans = new Acc(), glow = new Acc();
        VertexBuffer vSolid, vTrans, vGlow;
        int nSolid, nTrans, nGlow;
        /** Maillages des feuilles a part, cuits comme ceux de l'atlas. */
        final java.util.List<SheetPart> sheets = new java.util.ArrayList<>(2);
        /** Commandes encore dessinees a chaque image (sprites, textes). */
        public final DrawList overlay = new DrawList();
        public float builtAt = -1e9f;
        public Object design;
        public float yRot;
        long usedFrame;

        void close() {
            if (vSolid != null) vSolid.close();
            if (vTrans != null) vTrans.close();
            if (vGlow != null) vGlow.close();
            vSolid = vTrans = vGlow = null;
            for (SheetPart p : sheets) p.close();
            sheets.clear();
        }
    }

    /** Partie d'un instantane qui utilise une feuille de texture a part. */
    static final class SheetPart {
        final String sheet;
        String tex;
        final Acc solid = new Acc(), trans = new Acc(), glow = new Acc();
        VertexBuffer vSolid, vTrans, vGlow;
        int nSolid, nTrans, nGlow;

        SheetPart(String sheet) {
            this.sheet = sheet;
        }

        void close() {
            if (vSolid != null) vSolid.close();
            if (vTrans != null) vTrans.close();
            if (vGlow != null) vGlow.close();
            vSolid = vTrans = vGlow = null;
        }
    }

    private static final Map<Object, Snapshot> SNAPSHOTS = new HashMap<>();
    private static long frame;

    /** Instantane des parties animees d'une machine (cle : son bloc-entite). */
    public static Snapshot snapshot(Object key) {
        Snapshot s = SNAPSHOTS.computeIfAbsent(key, k -> new Snapshot());
        s.usedFrame = frame;
        return s;
    }

    /** Refait l'instantane a partir de la liste des parties animees. */
    public static void rebuild(Snapshot s, DrawList list, String atlas, float yRotDeg) {
        s.solid.reset();
        s.trans.reset();
        s.glow.reset();
        bakeOps(list, atlas, yRotDeg, s.solid, s.trans, s.glow);
        s.overlay.clear();
        for (SheetPart p : s.sheets) {
            p.solid.reset();
            p.trans.reset();
            p.glow.reset();
        }
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sprite != null || o.type == DrawList.TEXT) s.overlay.addCopy(o);
            if (o.mesh == null || o.sheet == null || o.sprite != null) continue;
            SheetPart part = null;
            for (SheetPart p : s.sheets) if (p.sheet.equals(o.sheet)) part = p;
            if (part == null) {
                part = new SheetPart(o.sheet);
                s.sheets.add(part);
            }
        }
        for (SheetPart p : s.sheets) {
            p.tex = SceneRenderer.sheetTexture(atlas, p.sheet);
            bakeOps(list, p.tex, p.sheet, yRotDeg, p.solid, p.trans, p.glow);
            p.vSolid = upload(p.vSolid, p.solid);
            p.vTrans = upload(p.vTrans, p.trans);
            p.vGlow = upload(p.vGlow, p.glow);
            p.nSolid = p.solid.n;
            p.nTrans = p.trans.n;
            p.nGlow = p.glow.n;
        }
        s.vSolid = upload(s.vSolid, s.solid);
        s.vTrans = upload(s.vTrans, s.trans);
        s.vGlow = upload(s.vGlow, s.glow);
        s.nSolid = s.solid.n;
        s.nTrans = s.trans.n;
        s.nGlow = s.glow.n;
    }

    /** Tampon de preparation reutilise pour tous les envois (pas d'allocation a chaque rafraichissement). */
    private static BufferBuilder uploader;

    private static VertexBuffer upload(VertexBuffer vb, Acc a) {
        if (a.n == 0) return vb;
        if (uploader == null) uploader = new BufferBuilder(1 << 18);
        BufferBuilder bb = uploader;
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        float[] d = a.d;
        for (int i = 0, o = 0; i < a.n; i++, o += 9) {
            bb.vertex(d[o], d[o + 1], d[o + 2]).color(d[o + 5], d[o + 6], d[o + 7], d[o + 8]).uv(d[o + 3], d[o + 4])
                    .uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
        if (vb == null) vb = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        vb.bind();
        vb.upload(bb.end());
        VertexBuffer.unbind();
        return vb;
    }

    /** Dessine l'instantane (meme pose que la carrosserie). */
    public static void draw(Snapshot s, String atlas, PoseStack pose) {
        drawVbo(s.vSolid, s.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(atlas)), pose);
        drawVbo(s.vTrans, s.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(atlas)), pose);
        drawVbo(s.vGlow, s.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(atlas)), pose);
        for (SheetPart p : s.sheets) {
            drawVbo(p.vSolid, p.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(p.tex)), pose);
            drawVbo(p.vTrans, p.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(p.tex)), pose);
            drawVbo(p.vGlow, p.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(p.tex)), pose);
        }
    }

    /** Idem, eclaire par la lumiere du monde (decors) ; le calque lumineux reste en plein eclat. */
    public static void drawLit(Snapshot s, String atlas, PoseStack pose, int light) {
        drawLit(s, atlas, pose, light, 1f);
    }

    /** Idem, calque lumineux attenue (machine dont la lumiere a ete baissee : glow de 0 a 1). */
    public static void drawLit(Snapshot s, String atlas, PoseStack pose, int light, float glow) {
        float[] c = lightColor(light), gc = glowTint(glow);
        drawVbo(s.vSolid, s.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(atlas)), pose, c);
        drawVbo(s.vTrans, s.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(atlas)), pose, c);
        drawVbo(s.vGlow, s.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(atlas)), pose, gc);
        for (SheetPart p : s.sheets) {
            drawVbo(p.vSolid, p.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(p.tex)), pose, c);
            drawVbo(p.vTrans, p.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(p.tex)), pose, c);
            drawVbo(p.vGlow, p.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(p.tex)), pose, gc);
        }
    }

    /** Teinte du calque lumineux (null : plein eclat). */
    private static float[] glowTint(float glow) {
        return glow >= 0.999f ? null : new float[]{glow, glow, glow};
    }

    /** Fin d'une image : les instantanes qui ne servent plus (machine detruite, trop loin, dechargee) sont liberes. */
    public static void endFrame() {
        frame++;
        if (frame % 120 != 0) return;
        SNAPSHOTS.values().removeIf(s -> {
            if (frame - s.usedFrame < 240) return false;
            s.close();
            return true;
        });
    }

    public static void clearSnapshots() {
        for (Snapshot s : SNAPSHOTS.values()) s.close();
        SNAPSHOTS.clear();
    }

    private static void emit(Mesh mesh, Matrix4f m, Matrix3f n, Acc out, float r, float g, float b, float a,
                             boolean vcol, boolean lit, Vector3f p, Vector3f nv) {
        float[] d = mesh.data();
        int count = mesh.vertexCount();
        for (int i = 0, o = 0; i < count; i++, o += Mesh.STRIDE) {
            m.transformPosition(d[o], d[o + 1], d[o + 2], p);
            float k = 1f;
            if (lit) {
                n.transform(d[o + 5], d[o + 6], d[o + 7], nv);
                if (nv.lengthSquared() > 1e-8f) nv.normalize();
                k = Math.min(1f, 0.4f + 0.6f * (Math.max(0f, nv.dot(L0)) + Math.max(0f, nv.dot(L1))));
            }
            float cr = r * k, cg = g * k, cb = b * k;
            if (vcol) {
                cr *= d[o + 8];
                cg *= d[o + 9];
                cb *= d[o + 10];
            }
            out.put(p.x, p.y, p.z, d[o + 3], d[o + 4], cr, cg, cb, a);
        }
    }

    // =================================================================== dessin
    public static boolean useVbo() {
        return ClientConfig.STATIC_VBO.get() && !ShaderPacks.inUse();
    }

    /** Dessine la carrosserie figee. La pose doit etre celle du bloc (ou de l'objet, deja mise a l'echelle). */
    public static void draw(Baked b, PoseStack pose, MultiBufferSource buf) {
        if (useVbo()) {
            try {
                for (Part pt : b.parts) {
                    if (!pt.uploaded()) pt.upload();
                    drawVbo(pt.vSolid, pt.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(pt.tex)), pose);
                    drawVbo(pt.vTrans, pt.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(pt.tex)), pose);
                    drawVbo(pt.vGlow, pt.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(pt.tex)), pose);
                }
                return;
            } catch (Throwable e) {
                HeralizCasino.LOGGER.warn("[HeralizCasino] Tampons graphiques indisponibles, rendu classique", e);
                ShaderPacks.forceFallback();
            }
        }
        Matrix4f m = pose.last().pose();
        for (Part pt : b.parts) {
            if (pt.nSolid > 0) immediate(pt.solid, pt.nSolid, buf.getBuffer(CasinoRenderTypes.staticSolid(SceneRenderer.texture(pt.tex))), m);
            if (pt.nTrans > 0) immediate(pt.trans, pt.nTrans, buf.getBuffer(CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(pt.tex))), m);
            if (pt.nGlow > 0) immediate(pt.glow, pt.nGlow, buf.getBuffer(CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(pt.tex))), m);
        }
    }

    /**
     * Carrosserie figee eclairee par la lumiere du monde (elements de decor : une affiche ne brille pas dans le noir).
     * Le calque lumineux reste en plein eclat. light = lumiere compactee (bloc, ciel) a la position de l'element.
     */
    public static void drawLit(Baked b, PoseStack pose, MultiBufferSource buf, int light) {
        drawLit(b, pose, buf, light, 1f);
    }

    /** Idem, calque lumineux attenue (machine dont la lumiere a ete baissee : glow de 0 a 1). */
    public static void drawLit(Baked b, PoseStack pose, MultiBufferSource buf, int light, float glow) {
        if (useVbo()) {
            try {
                // les sommets du tampon sont en plein eclat : on les module par la couleur de la lightmap
                float[] c = lightColor(light), gc = glowTint(glow);
                for (Part pt : b.parts) {
                    if (!pt.uploaded()) pt.upload();
                    drawVbo(pt.vSolid, pt.nSolid, CasinoRenderTypes.staticSolid(SceneRenderer.texture(pt.tex)), pose, c);
                    drawVbo(pt.vTrans, pt.nTrans, CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(pt.tex)), pose, c);
                    drawVbo(pt.vGlow, pt.nGlow, CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(pt.tex)), pose, gc);
                }
                return;
            } catch (Throwable e) {
                HeralizCasino.LOGGER.warn("[HeralizCasino] Tampons graphiques indisponibles, rendu classique", e);
                ShaderPacks.forceFallback();
            }
        }
        Matrix4f m = pose.last().pose();
        for (Part pt : b.parts) {
            if (pt.nSolid > 0) immediate(pt.solid, pt.nSolid, buf.getBuffer(CasinoRenderTypes.staticSolid(SceneRenderer.texture(pt.tex))), m, light);
            if (pt.nTrans > 0) immediate(pt.trans, pt.nTrans, buf.getBuffer(CasinoRenderTypes.staticTranslucent(SceneRenderer.texture(pt.tex))), m, light);
            if (pt.nGlow > 0) immediate(pt.glow, pt.nGlow, buf.getBuffer(CasinoRenderTypes.staticGlow(SceneRenderer.glowTexture(pt.tex))), m, LightTexture.FULL_BRIGHT,
                    Math.min(1f, glow));
        }
    }

    private static java.lang.reflect.Field lightPixels;
    private static boolean lightLooked;

    /** Couleur de la lightmap du jeu pour une lumiere compactee (bloc, ciel) : nuit, torches, vision nocturne compris. */
    static float[] lightColor(int packed) {
        int bl = LightTexture.block(packed), sk = LightTexture.sky(packed);
        if (!lightLooked) {
            lightLooked = true;
            try {
                lightPixels = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(LightTexture.class, "f_109871_");
            } catch (Throwable e) {
                lightPixels = null;
            }
        }
        if (lightPixels != null) {
            try {
                com.mojang.blaze3d.platform.NativeImage img = (com.mojang.blaze3d.platform.NativeImage) lightPixels.get(
                        net.minecraft.client.Minecraft.getInstance().gameRenderer.lightTexture());
                int c = img.getPixelRGBA(bl, sk);
                return new float[]{(c & 255) / 255f, ((c >> 8) & 255) / 255f, ((c >> 16) & 255) / 255f};
            } catch (Throwable ignored) {
                // lightmap illisible : approximation ci-dessous
            }
        }
        float k = 0.12f + 0.88f * Math.max(bl, sk) / 15f;
        return new float[]{k, k, k};
    }

    private static void immediate(float[] d, int count, VertexConsumer vc, Matrix4f m) {
        immediate(d, count, vc, m, LightTexture.FULL_BRIGHT);
    }

    private static void immediate(float[] d, int count, VertexConsumer vc, Matrix4f m, int light) {
        immediate(d, count, vc, m, light, 1f);
    }

    /** k : intensite des couleurs (calque lumineux attenue). */
    private static void immediate(float[] d, int count, VertexConsumer vc, Matrix4f m, int light, float k) {
        for (int i = 0, o = 0; i < count; i++, o += 9) {
            vc.vertex(m, d[o], d[o + 1], d[o + 2]).color(d[o + 5] * k, d[o + 6] * k, d[o + 7] * k, d[o + 8]).uv(d[o + 3], d[o + 4])
                    .uv2(light).endVertex();
        }
    }

    private static VertexBuffer build(float[] d, int count) {
        if (count == 0) return null;
        BufferBuilder bb = new BufferBuilder(count * 28 + 256);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        for (int i = 0, o = 0; i < count; i++, o += 9) {
            bb.vertex(d[o], d[o + 1], d[o + 2]).color(d[o + 5], d[o + 6], d[o + 7], d[o + 8]).uv(d[o + 3], d[o + 4])
                    .uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(bb.end());
        VertexBuffer.unbind();
        return vb;
    }

    private static void drawVbo(VertexBuffer vb, int count, RenderType type, PoseStack pose) {
        drawVbo(vb, count, type, pose, null);
    }

    /**
     * Dessine un tampon (couleur : teinte de la lumiere du monde, null = plein eclat). Pendant la passe des blocs-entites,
     * le dessin est differe a la fin de la passe et regroupe avec ceux du meme type : un seul reglage de rendu (texture,
     * shader, melange) pour toutes les machines qui partagent une texture, au lieu d'un par machine.
     */
    private static void drawVbo(VertexBuffer vb, int count, RenderType type, PoseStack pose, float[] color) {
        if (vb == null || count == 0) return;
        if (defer) {
            if (queued == queue.length) queue = java.util.Arrays.copyOf(queue, queue.length * 2);
            Queued q = queue[queued];
            if (q == null) q = queue[queued] = new Queued();
            queued++;
            q.vb = vb;
            q.type = type;
            q.mv.set(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
            q.tint = color != null;
            if (color != null) {
                q.r = color[0];
                q.g = color[1];
                q.b = color[2];
            }
            return;
        }
        type.setupRenderState();
        if (color != null) RenderSystem.setShaderColor(color[0], color[1], color[2], 1f);
        Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        vb.bind();
        vb.drawWithShader(mv, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        if (color != null) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        type.clearRenderState();
    }

    // =================================================================== dessin differe (regroupe par type)
    /** Active par CasinoBuffers pendant la passe des blocs-entites (jamais avec un pack de shaders). */
    static boolean defer;

    private static final class Queued {
        VertexBuffer vb;
        RenderType type;
        final Matrix4f mv = new Matrix4f();
        boolean tint;
        float r, g, b;
    }

    private static Queued[] queue = new Queued[64];
    private static int queued;

    static boolean hasDeferred() {
        return queued > 0;
    }

    private static final String[] SAMPLERS = new String[12];

    static {
        for (int i = 0; i < SAMPLERS.length; i++) SAMPLERS[i] = "Sampler" + i;
    }

    /**
     * Dessine les tampons differes d'une categorie (CasinoRenderTypes.CAT_*). Comme pour les troncons de terrain du jeu :
     * le type de rendu et le shader sont regles une fois par groupe, puis seules la matrice et la teinte changent.
     */
    static void flushDeferred(int cat) {
        for (int i = 0; i < queued; i++) {
            Queued first = queue[i];
            if (first.vb == null || CasinoRenderTypes.category(first.type) != cat) continue;
            RenderType t = first.type;
            t.setupRenderState();
            net.minecraft.client.renderer.ShaderInstance sh = RenderSystem.getShader();
            if (sh != null) {
                for (int s = 0; s < SAMPLERS.length; s++) sh.setSampler(SAMPLERS[s], RenderSystem.getShaderTexture(s));
                if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
                if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(first.mv);
                if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(1f, 1f, 1f, 1f);
                if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
                if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
                if (sh.FOG_COLOR != null) sh.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                RenderSystem.setupShaderLights(sh);
                sh.apply();
            }
            float lr = 1f, lg = 1f, lb = 1f;
            for (int j = i; j < queued; j++) {
                Queued q = queue[j];
                if (q.vb == null || q.type != t) continue;
                if (sh != null && !q.vb.isInvalid()) {
                    if (sh.MODEL_VIEW_MATRIX != null) {
                        sh.MODEL_VIEW_MATRIX.set(q.mv);
                        sh.MODEL_VIEW_MATRIX.upload();
                    }
                    float r = q.tint ? q.r : 1f, g = q.tint ? q.g : 1f, b = q.tint ? q.b : 1f;
                    if (sh.COLOR_MODULATOR != null && (r != lr || g != lg || b != lb)) {
                        sh.COLOR_MODULATOR.set(r, g, b, 1f);
                        sh.COLOR_MODULATOR.upload();
                        lr = r;
                        lg = g;
                        lb = b;
                    }
                    q.vb.bind();
                    q.vb.draw();
                }
                q.vb = null;
            }
            if (sh != null) sh.clear();
            VertexBuffer.unbind();
            t.clearRenderState();
        }
    }

    /** Fin de la passe : la file est videe (ce qui n'a pas ete dessine, en cas d'erreur, est abandonne). */
    static void endDeferred() {
        for (int i = 0; i < queued; i++) {
            queue[i].vb = null;
            queue[i].type = null;
        }
        queued = 0;
    }

    /** Detection d'un pack de shaders (Oculus / Iris) : les tampons graphiques directs y sont deconseilles. */
    public static final class ShaderPacks {
        private ShaderPacks() {
        }

        private static boolean fallback;
        private static Object api;
        private static java.lang.reflect.Method inUse;
        private static boolean looked;

        static void forceFallback() {
            fallback = true;
        }

        public static boolean inUse() {
            if (fallback) return true;
            if (!looked) {
                looked = true;
                try {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                } catch (Throwable ignored) {
                    api = null;
                }
            }
            if (api == null) return false;
            try {
                return (Boolean) inUse.invoke(api);
            } catch (Throwable e) {
                return true;
            }
        }
    }
}
