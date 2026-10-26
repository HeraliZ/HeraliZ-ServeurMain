package fr.heralisz.utils.ambiance;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Ciel du monde principal : même structure que le ciel vanilla (fond, lueur du lever/coucher, soleil, lune, disque
 * sombre sous l'horizon) avec, en plus :
 * <ul>
 * <li>couleur plus terne le jour, plus grise par temps couvert, très sombre la nuit (même teinte que le brouillard) ;</li>
 * <li>~6 000 étoiles de tailles et de couleurs variées (bleutées, blanches, jaunes, orangées), qui scintillent — plus
 * près de l'horizon, où elles rougissent et s'éteignent dans l'air épais — dont une partie se resserre le long de la voie
 * lactée (bande texturée, nuages d'étoiles et rift de poussière) ;</li>
 * <li>les nuages qui passent (voir {@link Ambiance#nuages}) cachent les étoiles, la voie lactée, puis la lune et le
 * soleil : certaines nuits sont étoilées, d'autres noires ; la pleine lune pâlit les étoiles et la voie lactée ;</li>
 * <li>halo autour de la lune (plus large quand le ciel est voilé), étoiles filantes de temps en temps.</li>
 * </ul>
 * Tout est en tampons statiques construits une fois (deux appels de dessin pour le ciel étoilé) : coût négligeable.
 */
public final class Ciel {
    static ShaderInstance shEtoiles;
    static ShaderInstance shVoie;
    static ShaderInstance shNuages;
    private static final ResourceLocation TEX_NUAGES = new ResourceLocation(HeraliszUtils.MODID, "textures/environment/nuages.png");
    /** Altitude de la couche de nuages (au-dessus des plus hauts sommets de la map). */
    private static final float ALTITUDE = 250f;
    private static VertexBuffer plan;
    private static final ResourceLocation SOLEIL = new ResourceLocation(HeraliszUtils.MODID, "textures/environment/soleil.png");
    private static final ResourceLocation LUNE = new ResourceLocation("textures/environment/moon_phases.png");
    private static final ResourceLocation TEX_VOIE = new ResourceLocation(HeraliszUtils.MODID, "textures/environment/voie_lactee.png");
    private static final ResourceLocation TEX_HALO = new ResourceLocation(HeraliszUtils.MODID, "textures/environment/halo_lune.png");
    /** Normale du plan de la voie lactée (repère du ciel, qui tourne avec l'heure). */
    private static final Vector3f PLAN = new Vector3f(0.42f, 0.33f, 0.85f).normalize();
    private static final float R = 100f;

    private static VertexBuffer disque;
    private static VertexBuffer disqueSombre;
    private static VertexBuffer etoiles;
    private static VertexBuffer voie;
    private static boolean enPanne;
    private static final Matrix4f ROT = new Matrix4f();
    private static final float[] TEINTE = new float[3];

    // étoile filante en cours
    private static final RandomSource HASARD = RandomSource.create();
    private static long debutFilante = -1;
    private static long prochaineFilante;
    private static final Vector3f F_DEP = new Vector3f();
    private static final Vector3f F_DIR = new Vector3f();
    private static float fDuree;
    private static float fLong;
    private static float fEclat;

    private Ciel() {}

    /** Dessine le ciel ; false = laisser vanilla le faire (autre dimension, Oculus, erreur). */
    static boolean dessiner(ClientLevel level, float pt, PoseStack ps, Camera camera, Matrix4f proj, boolean isFoggy, Runnable setupFog) {
        if (enPanne || !Ambiance.actif || shEtoiles == null || FiltreImage.irisActif()) {
            return false;                                            // ciel vanilla
        }
        try {
            if (disque == null) {
                construire();
            }
            rendu(level, pt, ps, camera, proj, isFoggy, setupFog);
            return true;
        } catch (Throwable t) {
            enPanne = true;
            HeraliszUtils.LOG.error("Ciel HeraliZ désactivé, retour au ciel vanilla", t);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            return false;
        }
    }

    private static void rendu(ClientLevel level, float pt, PoseStack ps, Camera camera, Matrix4f proj, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        if (isFoggy) {
            return;
        }
        FogType ft = camera.getFluidInCamera();
        if (ft == FogType.POWDER_SNOW || ft == FogType.LAVA || aveugle(camera)) {
            return;
        }
        Vec3 sc = level.getSkyColor(camera.getPosition(), pt);
        TEINTE[0] = (float) sc.x;
        TEINTE[1] = (float) sc.y;
        TEINTE[2] = (float) sc.z;
        Ambiance.teinte(TEINTE);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(TEINTE[0], TEINTE[1], TEINTE[2], 1f);
        ShaderInstance pos = GameRenderer.getPositionShader();
        disque.bind();
        disque.drawWithShader(ps.last().pose(), proj, pos);
        VertexBuffer.unbind();
        RenderSystem.enableBlend();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        float[] lever = level.effects().getSunriseColor(level.getTimeOfDay(pt), pt);
        if (lever != null) {                                           // lueur du lever/coucher, ternie par les nuages
            float a = lever[3] * (1f - 0.55f * Ambiance.nuages);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(90f));
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(level.getSunAngle(pt)) < 0f ? 180f : 0f));
            ps.mulPose(Axis.ZP.rotationDegrees(90f));
            Matrix4f m = ps.last().pose();
            bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            bb.vertex(m, 0f, 100f, 0f).color(lever[0], lever[1] * 0.97f, lever[2] * 0.95f, a).endVertex();
            for (int j = 0; j <= 16; j++) {
                float f = j * Mth.TWO_PI / 16f;
                bb.vertex(m, Mth.sin(f) * 120f, Mth.cos(f) * 120f, -Mth.cos(f) * 40f * lever[3]).color(lever[0], lever[1], lever[2], 0f).endVertex();
            }
            BufferUploader.drawWithShader(bb.end());
            ps.popPose();
        }

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableCull();
        float sansPluie = 1f - level.getRainLevel(pt);
        float heure = level.getTimeOfDay(pt) * 360f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-90f));
        ps.mulPose(Axis.XP.rotationDegrees(heure));
        Matrix4f m = ps.last().pose();
        ROT.identity().rotate(Axis.YP.rotationDegrees(-90f)).rotate(Axis.XP.rotationDegrees(heure));

        // étoiles et voie lactée (sans brouillard)
        float clair = 1f - Ambiance.nuages;
        float vis = Ambiance.nuitEtoiles * sansPluie * clair * Mth.lerp(Ambiance.lune, 1f, 0.72f);
        if (vis > 0.003f && shEtoiles != null) {
            FogRenderer.setupNoFog();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            float vv = vis * Mth.lerp(Ambiance.lune, 1f, 0.35f) * Ambiance.lisse((0.45f - Ambiance.nuages) / 0.45f);
            if (vv > 0.003f && shVoie != null) {
                RenderSystem.setShaderTexture(0, TEX_VOIE);
                shVoie.safeGetUniform("Visibilite").set(vv);
                shVoie.safeGetUniform("RotCiel").set(ROT);
                voie.bind();
                voie.drawWithShader(m, proj, shVoie);
                VertexBuffer.unbind();
            }
            shEtoiles.safeGetUniform("Visibilite").set(vis);
            shEtoiles.safeGetUniform("RotCiel").set(ROT);
            etoiles.bind();
            etoiles.drawWithShader(m, proj, shEtoiles);
            VertexBuffer.unbind();
            setupFog.run();
        }

        // soleil, caché par temps couvert
        float solA = sansPluie * (1f - 0.9f * Ambiance.nuages);
        if (solA > 0.01f) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, solA);
            RenderSystem.setShaderTexture(0, SOLEIL);
            quad(bb, m, 34f, 100f, 0f, 0f, 1f, 1f);                     // soleil rond (texture du mod)
        }
        // lune : halo (plus large quand le ciel est voilé), puis le disque ; cachée quand c'est couvert
        float luneA = sansPluie * (1f - 0.93f * Ambiance.nuages);
        if (luneA > 0.01f) {
            float voile = Math.min(1f, Ambiance.nuages * 2.4f);
            float haloA = luneA * Ambiance.nuitEtoiles * (0.22f + 0.6f * voile) * (0.25f + 0.75f * Ambiance.lune);
            if (haloA > 0.005f) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderColor(0.92f, 0.95f, 1f, haloA);
                RenderSystem.setShaderTexture(0, TEX_HALO);
                quad(bb, m, 46f + 26f * voile, -100f, 0f, 0f, 1f, 1f);
            }
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, luneA);
            RenderSystem.setShaderTexture(0, LUNE);
            int k = level.getMoonPhase();
            int l = k % 4;
            int i1 = k / 4 % 2;
            quadLune(bb, m, 20f, (l + 1) / 4f, (i1 + 1) / 2f, l / 4f, i1 / 2f);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ps.popPose();

        // étoile filante (repère du monde)
        if (vis > 0.3f && AmbianceConfig.filantes()) {
            FogRenderer.setupNoFog();
            filante(bb, ps.last().pose(), vis);
            setupFog.run();
        }
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        // sous l'horizon (vanilla) : disque sombre
        RenderSystem.setShaderColor(0f, 0f, 0f, 1f);
        Minecraft mc = Minecraft.getInstance();
        double d0 = mc.player != null ? mc.player.getEyePosition(pt).y - level.getLevelData().getHorizonHeight(level) : 1;
        if (d0 < 0.0D) {
            ps.pushPose();
            ps.translate(0f, 12f, 0f);
            disqueSombre.bind();
            disqueSombre.drawWithShader(ps.last().pose(), proj, pos);
            VertexBuffer.unbind();
            ps.popPose();
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
    }

    /**
     * Couche de nuages doux (remplace les nuages cubiques vanilla) : un grand plan à 250 de haut, deux couches de bruit
     * qui dérivent avec le vent d'ouest (heure du monde : les mêmes nuages pour tous), couverture = nébulosité de
     * l'ambiance (rares par beau temps, ciel bouché quand c'est couvert), teinte vanilla des nuages (jour, nuit, orage)
     * rosie au lever et au coucher. Un seul carré dessiné : coût négligeable.
     */
    static boolean nuages(ClientLevel level, float pt, PoseStack ps, double camX, double camY, double camZ, Matrix4f proj) {
        if (enPanne || !Ambiance.actif || shNuages == null || FiltreImage.irisActif()) {
            return false;
        }
        try {
            if (plan == null) {
                BufferBuilder bb = Tesselator.getInstance().getBuilder();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                float s = 1f;                                    // carré unité, étiré par le shader (Rayon)
                bb.vertex(-s, 0, -s).endVertex();
                bb.vertex(s, 0, -s).endVertex();
                bb.vertex(s, 0, s).endVertex();
                bb.vertex(-s, 0, s).endVertex();
                plan = televerser(bb.end());
            }
            Minecraft mc = Minecraft.getInstance();
            float rayon = Math.min(1150f, Math.max(320f, mc.options.getEffectiveRenderDistance() * 16f * 1.7f));
            double t = (level.getGameTime() + pt) / 20.0;
            double vx = 1.4 * t;
            double vz = 0.35 * t;
            Vec3 cc = level.getCloudColor(pt);
            float r = (float) cc.x;
            float g = (float) cc.y;
            float b = (float) cc.z;
            float[] lever = level.effects().getSunriseColor(level.getTimeOfDay(pt), pt);
            if (lever != null) {
                float k = lever[3] * 0.5f;
                r = Mth.lerp(k, r, lever[0]);
                g = Mth.lerp(k, g, lever[1] * 0.92f);
                b = Mth.lerp(k, b, lever[2] * 0.88f);
            }
            float lum = Mth.lerp(Ambiance.nuages, 1f, 0.86f);
            ps.pushPose();
            ps.translate(0f, (float) (ALTITUDE - camY), 0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.setShaderTexture(0, TEX_NUAGES);
            shNuages.safeGetUniform("Decalages").set((float) frac((camX + vx) / 512.0), (float) frac((camZ + vz) / 512.0),
                    (float) frac((camX + vx * 1.35) / 190.0), (float) frac((camZ + vz * 1.35) / 190.0));
            shNuages.safeGetUniform("Teinte").set(r * lum, g * lum, b * lum);
            shNuages.safeGetUniform("Couverture").set(Ambiance.nuages);
            shNuages.safeGetUniform("Rayon").set(rayon);
            plan.bind();
            plan.drawWithShader(ps.last().pose(), proj, shNuages);
            VertexBuffer.unbind();
            ps.popPose();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return true;
        } catch (Throwable t) {
            enPanne = true;
            HeraliszUtils.LOG.error("Nuages HeraliZ désactivés, retour aux nuages vanilla", t);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return false;
        }
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static boolean aveugle(Camera camera) {
        return camera.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS));
    }

    /** Carré horizontal (soleil : y = +100, lune et halo : y = -100) dans le repère du ciel. */
    private static void quad(BufferBuilder bb, Matrix4f m, float s, float y, float u0, float v0, float u1, float v1) {
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -s, y, -s).uv(u0, v0).endVertex();
        bb.vertex(m, s, y, -s).uv(u1, v0).endVertex();
        bb.vertex(m, s, y, s).uv(u1, v1).endVertex();
        bb.vertex(m, -s, y, s).uv(u0, v1).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    /** Lune : même ordre de sommets que vanilla (sinon la phase est retournée). */
    private static void quadLune(BufferBuilder bb, Matrix4f m, float s, float u1, float v1, float u0, float v0) {
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -s, -100f, s).uv(u1, v1).endVertex();
        bb.vertex(m, s, -100f, s).uv(u0, v1).endVertex();
        bb.vertex(m, s, -100f, -s).uv(u0, v0).endVertex();
        bb.vertex(m, -s, -100f, -s).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    /** Étoile filante : toutes les 25 s à 2 min les nuits dégagées, une traînée qui file 0,5 à 1,1 s puis s'éteint. */
    private static void filante(BufferBuilder bb, Matrix4f m, float vis) {
        long now = System.nanoTime();
        if (debutFilante < 0) {
            if (prochaineFilante == 0) {
                prochaineFilante = now + (long) ((15 + HASARD.nextFloat() * 50) * 1e9);
            }
            if (now < prochaineFilante) {
                return;
            }
            float az = HASARD.nextFloat() * Mth.TWO_PI;
            float el = (25f + HASARD.nextFloat() * 45f) * Mth.DEG_TO_RAD;
            F_DEP.set(Mth.cos(el) * Mth.cos(az), Mth.sin(el), Mth.cos(el) * Mth.sin(az));
            // direction de chute : surtout vers le bas, un peu de côté
            Vector3f cote = new Vector3f(-Mth.sin(az), 0f, Mth.cos(az)).mul(HASARD.nextFloat() * 2f - 1f);
            F_DIR.set(0f, -1f, 0f).add(cote);
            F_DIR.sub(new Vector3f(F_DEP).mul(F_DIR.dot(F_DEP))).normalize();
            fDuree = 0.5f + HASARD.nextFloat() * 0.6f;
            fLong = (9f + HASARD.nextFloat() * 16f) * Mth.DEG_TO_RAD;
            fEclat = 0.55f + HASARD.nextFloat() * 0.45f;
            debutFilante = now;
        }
        float t = (now - debutFilante) / 1e9f / fDuree;
        if (t >= 1f) {
            debutFilante = -1;
            prochaineFilante = now + (long) ((25 + HASARD.nextFloat() * 95) * 1e9);
            return;
        }
        float a = fLong * t;
        float aQueue = Math.max(0f, a - fLong * 0.4f);
        Vector3f tete = point(a);
        Vector3f queue = point(aQueue);
        Vector3f sens = new Vector3f(tete).sub(queue);
        if (sens.lengthSquared() < 1e-6f) {
            return;
        }
        Vector3f large = new Vector3f(sens).cross(tete).normalize().mul(0.22f);
        float eclat = Mth.sin(Mth.PI * t) * vis * fEclat;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, queue.x - large.x * 0.2f, queue.y - large.y * 0.2f, queue.z - large.z * 0.2f).color(0.8f, 0.85f, 1f, 0f).endVertex();
        bb.vertex(m, queue.x + large.x * 0.2f, queue.y + large.y * 0.2f, queue.z + large.z * 0.2f).color(0.8f, 0.85f, 1f, 0f).endVertex();
        bb.vertex(m, tete.x + large.x, tete.y + large.y, tete.z + large.z).color(1f, 0.97f, 0.92f, eclat).endVertex();
        bb.vertex(m, tete.x - large.x, tete.y - large.y, tete.z - large.z).color(1f, 0.97f, 0.92f, eclat).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static Vector3f point(float a) {
        return new Vector3f(F_DEP).mul(Mth.cos(a)).add(new Vector3f(F_DIR).mul(Mth.sin(a))).normalize().mul(R);
    }

    // ---------------------------------------------------------------------------------------------- tampons statiques

    private static void construire() {
        disque = disque(16f);
        disqueSombre = disque(-16f);
        etoiles = construireEtoiles();
        voie = construireVoie();
    }

    /** Disque du ciel vanilla (éventail de rayon 512 à la hauteur y). */
    private static VertexBuffer disque(float y) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        float f = Math.signum(y) * 512f;
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        bb.vertex(0, y, 0).endVertex();
        for (int i = -180; i <= 180; i += 45) {
            bb.vertex(f * Mth.cos(i * Mth.DEG_TO_RAD), y, 512f * Mth.sin(i * Mth.DEG_TO_RAD)).endVertex();
        }
        return televerser(bb.end());
    }

    private static VertexBuffer televerser(BufferBuilder.RenderedBuffer rb) {
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rb);
        VertexBuffer.unbind();
        return vb;
    }

    private static VertexBuffer construireEtoiles() {
        RandomSource r = RandomSource.create(20261001L);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        Vector3f d = new Vector3f();
        // étoiles de tout le ciel : beaucoup de faibles, quelques brillantes
        for (int i = 0; i < 2800; i++) {
            surSphere(r, d);
            float mag = (float) Math.pow(r.nextFloat(), 3.2);
            etoile(bb, r, d, 0.15f + 0.45f * mag, 0.32f + 0.68f * mag);
        }
        // étoiles faibles serrées le long de la voie lactée
        Vector3f e1 = new Vector3f(PLAN).cross(0f, 1f, 0f).normalize();
        Vector3f e2 = new Vector3f(PLAN).cross(e1).normalize();
        for (int i = 0; i < 3300; i++) {
            float phi = r.nextFloat() * Mth.TWO_PI;
            float lat = (float) (r.nextGaussian() * 0.11);
            float cl = Mth.cos(lat);
            d.set(e1).mul(Mth.cos(phi) * cl).add(e2.x * Mth.sin(phi) * cl, e2.y * Mth.sin(phi) * cl, e2.z * Mth.sin(phi) * cl)
                    .add(PLAN.x * Mth.sin(lat), PLAN.y * Mth.sin(lat), PLAN.z * Mth.sin(lat)).normalize();
            float mag = (float) Math.pow(r.nextFloat(), 4.0);
            etoile(bb, r, d, 0.11f + 0.16f * mag, 0.16f + 0.40f * mag);
        }
        return televerser(bb.end());
    }

    private static void surSphere(RandomSource r, Vector3f d) {
        do {
            d.set(r.nextFloat() * 2f - 1f, r.nextFloat() * 2f - 1f, r.nextFloat() * 2f - 1f);
        } while (d.lengthSquared() > 1f || d.lengthSquared() < 0.01f);
        d.normalize();
    }

    /** Une étoile : petit carré tourné face au centre ; couleur = teinte × éclat, alpha = phase du scintillement. */
    private static void etoile(BufferBuilder bb, RandomSource r, Vector3f d, float s, float eclat) {
        float t = r.nextFloat();
        float cr;
        float cg;
        float cb;
        if (t < 0.18f) {
            cr = 0.72f; cg = 0.82f; cb = 1f;                         // bleutée
        } else if (t < 0.62f) {
            cr = 0.95f; cg = 0.96f; cb = 1f;                         // blanche
        } else if (t < 0.88f) {
            cr = 1f; cg = 0.93f; cb = 0.80f;                         // jaune pâle
        } else {
            cr = 1f; cg = 0.78f; cb = 0.58f;                         // orangée
        }
        float phase = r.nextFloat();
        Vector3f a = Math.abs(d.y) < 0.9f ? new Vector3f(0f, 1f, 0f) : new Vector3f(1f, 0f, 0f);
        Vector3f u = new Vector3f(d).cross(a).normalize();
        Vector3f v = new Vector3f(d).cross(u).normalize();
        float th = r.nextFloat() * Mth.TWO_PI;
        Vector3f u2 = new Vector3f(u).mul(Mth.cos(th)).add(new Vector3f(v).mul(Mth.sin(th))).mul(s);
        Vector3f v2 = new Vector3f(v).mul(Mth.cos(th)).sub(new Vector3f(u).mul(Mth.sin(th))).mul(s);
        float cx = d.x * R;
        float cy = d.y * R;
        float cz = d.z * R;
        bb.vertex(cx - u2.x - v2.x, cy - u2.y - v2.y, cz - u2.z - v2.z).color(cr * eclat, cg * eclat, cb * eclat, phase).uv(0f, 0f).endVertex();
        bb.vertex(cx + u2.x - v2.x, cy + u2.y - v2.y, cz + u2.z - v2.z).color(cr * eclat, cg * eclat, cb * eclat, phase).uv(1f, 0f).endVertex();
        bb.vertex(cx + u2.x + v2.x, cy + u2.y + v2.y, cz + u2.z + v2.z).color(cr * eclat, cg * eclat, cb * eclat, phase).uv(1f, 1f).endVertex();
        bb.vertex(cx - u2.x + v2.x, cy - u2.y + v2.y, cz - u2.z + v2.z).color(cr * eclat, cg * eclat, cb * eclat, phase).uv(0f, 1f).endVertex();
    }

    /** Bande de la voie lactée : anneau de 96 × 4 carrés sur le grand cercle du plan, ±17° de large. */
    private static VertexBuffer construireVoie() {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        Vector3f e1 = new Vector3f(PLAN).cross(0f, 1f, 0f).normalize();
        Vector3f e2 = new Vector3f(PLAN).cross(e1).normalize();
        int n = 96;
        int bandes = 4;
        float w = 0.30f;
        for (int i = 0; i < n; i++) {
            float p0 = i * Mth.TWO_PI / n;
            float p1 = (i + 1) * Mth.TWO_PI / n;
            for (int k = 0; k < bandes; k++) {
                float b0 = -w + 2f * w * k / bandes;
                float b1 = -w + 2f * w * (k + 1) / bandes;
                sommetVoie(bb, e1, e2, p0, b0, i / (float) n, k / (float) bandes);
                sommetVoie(bb, e1, e2, p1, b0, (i + 1) / (float) n, k / (float) bandes);
                sommetVoie(bb, e1, e2, p1, b1, (i + 1) / (float) n, (k + 1) / (float) bandes);
                sommetVoie(bb, e1, e2, p0, b1, i / (float) n, (k + 1) / (float) bandes);
            }
        }
        return televerser(bb.end());
    }

    private static void sommetVoie(BufferBuilder bb, Vector3f e1, Vector3f e2, float phi, float lat, float u, float v) {
        float cl = Mth.cos(lat);
        float sl = Mth.sin(lat);
        float x = (e1.x * Mth.cos(phi) + e2.x * Mth.sin(phi)) * cl + PLAN.x * sl;
        float y = (e1.y * Mth.cos(phi) + e2.y * Mth.sin(phi)) * cl + PLAN.y * sl;
        float z = (e1.z * Mth.cos(phi) + e2.z * Mth.sin(phi)) * cl + PLAN.z * sl;
        bb.vertex(x * R * 1.02f, y * R * 1.02f, z * R * 1.02f).color(1f, 1f, 1f, 1f).uv(u, v).endVertex();
    }
}
