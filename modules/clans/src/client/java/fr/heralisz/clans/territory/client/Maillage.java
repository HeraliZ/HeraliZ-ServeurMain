package fr.heralisz.clans.territory.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.clans.HeraliszClans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maillage d'un decor anime (assets/heralisz_clans/meshes/*.json, ecrit par tools/clans/gen_meshes.py) : copie du
 * Maillage de HeraliZ Utils (deco/Maillage.java) pour ce mod — parties rigides qui tournent autour d'un pivot (degres
 * par tick), sommets de tissu qui flottent (poids w, onde qui court le long du tissu). Coordonnees en blocs, origine au
 * centre du bloc, decor tourne vers le nord. En plus de l'original : {@link #dessiner(PoseStack, MultiBufferSource,
 * int, float, float, RenderType)} pour imposer la texture (tissu du drapeau = banniere du clan). Cache vide au
 * rechargement des ressources.
 */
@OnlyIn(Dist.CLIENT)
public final class Maillage {
    private static final Map<String, Maillage> CACHE = new HashMap<>();
    private static final Maillage VIDE = new Maillage(null, new Partie[0]);

    private final RenderType type;
    private final Partie[] parties;

    private Maillage(RenderType type, Partie[] parties) {
        this.type = type;
        this.parties = parties;
    }

    public static Maillage get(String nom) {
        return CACHE.computeIfAbsent(nom, Maillage::charger);
    }

    /** Rechargement des ressources (F3+T) : les maillages seront relus. */
    public static void vider() {
        CACHE.clear();
    }

    private static Maillage charger(String nom) {
        ResourceLocation rl = HeraliszClans.id("meshes/" + nom + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            HeraliszClans.LOG.warn("[clans] maillage introuvable : {}", rl);
            return VIDE;
        }
        try (Reader r = res.get().openAsReader()) {
            JsonObject o = GsonHelper.parse(r);
            RenderType type = RenderType.entityCutoutNoCull(new ResourceLocation(o.get("texture").getAsString()));
            JsonArray ps = o.getAsJsonArray("parties");
            Partie[] parties = new Partie[ps.size()];
            for (int i = 0; i < parties.length; i++) parties[i] = new Partie(ps.get(i).getAsJsonObject());
            return new Maillage(type, parties);
        } catch (Exception e) {
            HeraliszClans.LOG.error("[clans] maillage illisible : {}", rl, e);
            return VIDE;
        }
    }

    /** t : temps en ticks (avec la fraction de l'image) ; phase : decalage propre a chaque exemplaire (degres). */
    public void dessiner(PoseStack ps, MultiBufferSource buf, int light, float t, float phase) {
        dessiner(ps, buf, light, t, phase, null);
    }

    /** Idem avec une texture imposee (null = celle du fichier). */
    public void dessiner(PoseStack ps, MultiBufferSource buf, int light, float t, float phase, @Nullable RenderType texture) {
        RenderType rt = texture != null ? texture : type;
        if (rt == null) return;
        VertexConsumer vc = buf.getBuffer(rt);
        for (Partie p : parties) p.dessiner(ps, vc, light, t, phase);
    }

    private static final class Partie {
        private final float px, py, pz, ax, ay, az, vitesse;
        private final boolean flotte;
        private final float amp, freq, onde, dx, dy, dz;
        private final float[] v, n;
        private final int quads;
        private final float[] q = new float[12];
        private final Quaternionf rot = new Quaternionf();

        Partie(JsonObject o) {
            float[] pv = floats(o.getAsJsonArray("pivot")), ax3 = floats(o.getAsJsonArray("axe"));
            px = pv[0];
            py = pv[1];
            pz = pv[2];
            ax = ax3[0];
            ay = ax3[1];
            az = ax3[2];
            vitesse = o.get("vitesse").getAsFloat();
            v = floats(o.getAsJsonArray("v"));
            n = floats(o.getAsJsonArray("n"));
            quads = n.length / 3;
            flotte = o.has("flottement");
            float[] f = flotte ? floats(o.getAsJsonArray("flottement")) : new float[6];
            amp = f[0];
            freq = f[1];
            onde = f[2];
            dx = f[3];
            dy = f[4];
            dz = f[5];
        }

        private static float[] floats(JsonArray a) {
            float[] out = new float[a.size()];
            for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
            return out;
        }

        void dessiner(PoseStack ps, VertexConsumer vc, int light, float t, float phase) {
            ps.pushPose();
            if (vitesse != 0) {
                ps.translate(px, py, pz);
                ps.mulPose(rot.identity().rotationAxis((float) Math.toRadians(vitesse * t + phase), ax, ay, az));
                ps.translate(-px, -py, -pz);
            }
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            Matrix3f nm = pose.normal();
            float ph = phase * 0.0174533f;
            for (int i = 0; i < quads; i++) {
                int b = i * 24;
                float nx = n[i * 3], ny = n[i * 3 + 1], nz = n[i * 3 + 2];
                boolean bouge = false;
                for (int k = 0; k < 4; k++) {
                    int o = b + k * 6;
                    float x = v[o], y = v[o + 1], z = v[o + 2], w = v[o + 5];
                    if (flotte && w != 0) {
                        float d = w * amp * Mth.sin(freq * t - onde * (x + 0.7f * z + 0.3f * y) + ph);
                        x += dx * d;
                        y += dy * d;
                        z += dz * d;
                        bouge = true;
                    }
                    q[k * 3] = x;
                    q[k * 3 + 1] = y;
                    q[k * 3 + 2] = z;
                }
                if (bouge) {                                   // normale du tissu deforme : (p2 - p0) x (p3 - p1)
                    float ux = q[6] - q[0], uy = q[7] - q[1], uz = q[8] - q[2];
                    float wx = q[9] - q[3], wy = q[10] - q[4], wz = q[11] - q[5];
                    float cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
                    float l = Mth.sqrt(cx * cx + cy * cy + cz * cz);
                    if (l > 1e-6f) {
                        nx = cx / l;
                        ny = cy / l;
                        nz = cz / l;
                    }
                }
                for (int k = 0; k < 4; k++) {
                    int o = b + k * 6;
                    vc.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(255, 255, 255, 255).uv(v[o + 3], v[o + 4])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, nx, ny, nz).endVertex();
                }
            }
            ps.popPose();
        }
    }
}
