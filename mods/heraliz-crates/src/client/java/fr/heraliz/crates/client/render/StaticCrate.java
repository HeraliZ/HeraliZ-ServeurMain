package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.client.render.mesh.Mesh;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.config.ClientConfig;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caisse au repos : le modele est fige dans sa pose de repos, "cuit" une seule fois (eclairage compris) puis
 * envoye a la carte graphique (VertexBuffer). Chaque image ne coute alors que trois appels de dessin, quel que
 * soit le nombre de faces ; rien n'est recalcule tant que la caisse ne s'ouvre pas.
 * <p>La cuisson des caisses posees dans le monde se fait en arriere-plan (pas de saccade en arrivant au spawn) ;
 * les donnees brutes sont liberees des qu'elles sont sur la carte graphique. Si un pack de shaders (Oculus/Iris)
 * est actif, les memes donnees sont envoyees par le tampon normal.
 */
public final class StaticCrate {
    private StaticCrate() {
    }

    /** Eclairage des entites de Minecraft (directions monde). */
    private static final Vector3f L0 = new Vector3f(0.2f, 1f, -0.7f).normalize();
    private static final Vector3f L1 = new Vector3f(-0.2f, 1f, 0.7f).normalize();

    private static final Map<String, Baked> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    /** Change a chaque rechargement des ressources : une cuisson lancee avant est jetee. */
    private static volatile int generation;
    private static final ExecutorService BAKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeralizCrates-Cuisson");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    /** Donnees cuites d'un design pour une orientation : positions en blocs (repere du bloc). */
    public static final class Baked {
        final String designId;
        /** x y z u v r g b a ; null une fois envoyees a la carte graphique. */
        float[] solid, trans, glow;
        final int nSolid, nTrans, nGlow;
        /** Sprites figes (halos, lueurs...) dessines a chaque image. */
        final DrawList sprites = new DrawList();
        /** Vrais objets du decor (DrawList.ITEM), immobiles. */
        final DrawList items = new DrawList();
        VertexBuffer vSolid, vTrans, vGlow;

        Baked(String id, Acc s, Acc t, Acc g) {
            this.designId = id;
            this.solid = s.data();
            this.trans = t.data();
            this.glow = g.data();
            this.nSolid = s.n;
            this.nTrans = t.n;
            this.nGlow = g.n;
        }

        public DrawList items() {
            return items;
        }

        public DrawList sprites() {
            return sprites;
        }

        void close() {
            if (vSolid != null) vSolid.close();
            if (vTrans != null) vTrans.close();
            if (vGlow != null) vGlow.close();
            vSolid = vTrans = vGlow = null;
        }
    }

    /** Accumulateur de sommets (9 flottants par sommet). */
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
    }

    /** Etat de la pose de repos (la meme que le debut de l'animation d'ouverture). */
    public static CrateState restState(boolean item) {
        CrateState s = new CrateState();
        s.time = 0;
        s.seed = 0;
        s.item = item;
        // caisse posee : les parties animees au repos sont dessinees a part (Design.idle) ; icone : tout est fige
        s.frozen = !item;
        s.runes = false;
        s.beams = false;
        return s;
    }

    private static String key(Design d, float yRotDeg, boolean item) {
        return d.id + "|" + d.detail + "|" + Math.round(yRotDeg) + "|" + item;
    }

    /** Version cuite si elle est prete ; sinon la cuisson part en arriere-plan et on renvoie null (rien a dessiner). */
    @Nullable
    public static Baked getAsync(Design d, float yRotDeg) {
        return getAsync(d, yRotDeg, false);
    }

    @Nullable
    public static Baked getAsync(Design d, float yRotDeg, boolean item) {
        String key = key(d, yRotDeg, item);
        Baked b = CACHE.get(key);
        if (b != null || !d.isBuilt()) return b;
        if (PENDING.add(key)) {
            int gen = generation;
            BAKER.execute(() -> {
                try {
                    Baked done = bake(d, yRotDeg, item);
                    if (gen == generation) CACHE.put(key, done);
                } catch (Throwable e) {
                    HeralizCrates.LOGGER.error("[HeralizCrates] Cuisson de la caisse {} impossible", d.id, e);
                } finally {
                    PENDING.remove(key);
                }
            });
        }
        return null;
    }

    /** Libere les tampons graphiques (rechargement des ressources). */
    public static void clear() {
        generation++;
        for (Baked b : CACHE.values()) b.close();
        CACHE.clear();
        GpuScene.clear();
    }

    private static Baked bake(Design d, float yRotDeg, boolean item) {
        DrawList list = new DrawList();
        // le meme design peut etre anime au meme moment sur le fil de rendu
        synchronized (d) {
            d.render(restState(item), list);
        }
        Matrix4f model = new Matrix4f().translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRotDeg)).translate(-0.5f, 0, -0.5f)
                .scale(1f / 16f);
        Matrix3f modelN = model.normal(new Matrix3f());
        Acc solid = new Acc(), trans = new Acc(), glow = new Acc();
        Matrix4f m = new Matrix4f();
        Matrix3f n = new Matrix3f();
        Vector3f p = new Vector3f(), nv = new Vector3f();
        net.minecraft.resources.ResourceLocation glowTex = SceneRenderer.glowTexture(d.id);
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sprite != null || o.mesh == null) continue;
            m.set(model).mul(o.m);
            n.set(modelN).mul(o.m.normal(new Matrix3f()));
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
        Baked b = new Baked(d.id, solid, trans, glow);
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.sprite != null) b.sprites.addCopy(o);
            else if (o.type == DrawList.ITEM) b.items.addCopy(o);
        }
        return b;
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
    /** Tampons graphiques actives (reglage client, desactives automatiquement avec un pack de shaders). */
    public static boolean useVbo() {
        return ClientConfig.STATIC_VBO.get() && !ShaderPacks.inUse();
    }

    /** Dessine la caisse figee. La pose doit etre celle du bloc (ou de l'objet, deja mise a l'echelle). */
    public static void draw(Baked b, PoseStack pose, MultiBufferSource buf) {
        RenderType tSolid = HzRenderTypes.staticSolid(SceneRenderer.texture(b.designId));
        RenderType tTrans = HzRenderTypes.staticTranslucent(SceneRenderer.texture(b.designId));
        RenderType tGlow = HzRenderTypes.staticGlow(SceneRenderer.glowTexture(b.designId));
        if (useVbo()) {
            try {
                if (b.vSolid == null && b.vTrans == null && b.vGlow == null && b.solid != null) {
                    // envoi etale : pas plus d'un certain nombre de sommets par image (arrivee au spawn)
                    if (!GpuScene.spend(b.nSolid + b.nTrans + b.nGlow)) return;
                    upload(b);
                }
                drawVbo(b.vSolid, b.nSolid, tSolid, pose);
                drawVbo(b.vTrans, b.nTrans, tTrans, pose);
                drawVbo(b.vGlow, b.nGlow, tGlow, pose);
                return;
            } catch (Throwable e) {
                HeralizCrates.LOGGER.warn("[HeralizCrates] Tampons graphiques indisponibles, rendu classique", e);
                ShaderPacks.forceFallback();
            }
        }
        if (b.solid == null) {
            // donnees deja liberees (pack de shaders active entre-temps) : on recuit
            CACHE.values().remove(b);
            b.close();
            return;
        }
        Matrix4f m = pose.last().pose();
        immediate(b.solid, b.nSolid, buf.getBuffer(tSolid), m);
        if (b.nTrans > 0) immediate(b.trans, b.nTrans, buf.getBuffer(tTrans), m);
        if (b.nGlow > 0) immediate(b.glow, b.nGlow, buf.getBuffer(tGlow), m);
    }

    private static void immediate(float[] d, int count, VertexConsumer vc, Matrix4f m) {
        for (int i = 0, o = 0; i < count; i++, o += 9) {
            vc.vertex(m, d[o], d[o + 1], d[o + 2]).color(d[o + 5], d[o + 6], d[o + 7], d[o + 8]).uv(d[o + 3], d[o + 4])
                    .uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
    }

    private static void upload(Baked b) {
        b.vSolid = build(b.solid, b.nSolid);
        b.vTrans = build(b.trans, b.nTrans);
        b.vGlow = build(b.glow, b.nGlow);
        // les sommets vivent maintenant sur la carte graphique
        b.solid = b.trans = b.glow = null;
    }

    /** Tampon d'envoi partage (fil de rendu uniquement) : un seul bloc de memoire native, reutilise. */
    private static BufferBuilder uploader;

    static BufferBuilder uploader() {
        if (uploader == null) uploader = new BufferBuilder(1 << 16);
        return uploader;
    }

    private static VertexBuffer build(float[] d, int count) {
        if (count == 0) return null;
        BufferBuilder bb = uploader();
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

    private static final Matrix4f MV = new Matrix4f();

    private static void drawVbo(VertexBuffer vb, int count, RenderType type, PoseStack pose) {
        if (vb == null || count == 0) return;
        type.setupRenderState();
        MV.set(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        vb.bind();
        vb.drawWithShader(MV, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        type.clearRenderState();
    }

    /** Detection d'un pack de shaders (Oculus / Iris) : les tampons graphiques directs y sont deconseilles. */
    public static final class ShaderPacks {
        private ShaderPacks() {
        }

        private static boolean fallback;
        private static Object api;
        private static java.lang.reflect.Method inUse;
        private static boolean looked;
        /** Reponse gardee une seconde : la question est posee a chaque caisse dessinee. */
        private static boolean cached;
        private static long checkedAt = Long.MIN_VALUE;

        static void forceFallback() {
            fallback = true;
        }

        public static boolean inUse() {
            if (fallback) return true;
            long now = net.minecraft.Util.getMillis();
            if (now - checkedAt < 1000) return cached;
            checkedAt = now;
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
            if (api == null) return cached = false;
            try {
                return cached = (Boolean) inUse.invoke(api);
            } catch (Throwable e) {
                return cached = true;
            }
        }
    }
}
