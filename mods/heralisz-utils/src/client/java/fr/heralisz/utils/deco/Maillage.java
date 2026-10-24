package fr.heralisz.utils.deco;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.utils.HeraliszUtils;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
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

/**
 * Maillage d'un décor animé (assets/heralisz_utils/meshes/*.json, écrit par tools/gen_anim_meshes.py,
 * gen_faille_meshes.py et gen_anim_props.py) : des parties rigides, chacune portée par une autre partie (« parent » :
 * la nacelle par la grande roue, le crochet par le chariot de la grue), qui tournent autour d'un pivot (degrés par
 * tick), se balancent (balancier : amplitude, pulsation, gain du vent, roulis), coulissent (glisse : déplacement,
 * pulsation, déphasage), ou annulent la rotation de leur parent (contre : la nacelle reste droite) ; des sommets de
 * tissu qui flottent (poids w, onde qui court le long du tissu) ; des parties lumineuses (pleine lumière, clignotantes)
 * ou translucides et émissives, ou additives (faisceau du phare, halos des feux : s'ajoutent à la nuit). Le vent (0..1, rafales de la faille) élargit balancement et
 * flottement. Coordonnées en blocs, origine au centre du bloc, décor tourné vers le nord.
 */
@OnlyIn(Dist.CLIENT)
public final class Maillage {
    private static final Map<String, Maillage> CACHE = new HashMap<>();
    private static final Maillage VIDE = new Maillage(null, null, null, new Partie[0]);
    /** Normale de la partie en cours (fil du rendu seulement) : réutilisée, plus une matrice créée par partie et par image. */
    private static final Matrix3f NORMALE = new Matrix3f();

    private final RenderType type, typeTrans, typeAdd;
    private final Partie[] parties;
    private final Matrix4f[] mats;
    private final float[] angles;
    private float nuit = 1f;

    private Maillage(RenderType type, RenderType typeTrans, RenderType typeAdd, Partie[] parties) {
        this.type = type;
        this.typeTrans = typeTrans;
        this.typeAdd = typeAdd;
        this.parties = parties;
        this.mats = new Matrix4f[parties.length];
        this.angles = new float[parties.length];
        for (int i = 0; i < parties.length; i++) {
            mats[i] = new Matrix4f();
        }
    }

    public static Maillage get(String nom) {
        return CACHE.computeIfAbsent(nom, Maillage::charger);
    }

    /** Rechargement des ressources (F3+T) : les maillages seront relus. */
    public static void vider() {
        CACHE.clear();
    }

    private static Maillage charger(String nom) {
        ResourceLocation rl = new ResourceLocation(HeraliszUtils.MODID, "meshes/" + nom + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            HeraliszUtils.LOG.warn("Maillage introuvable : {}", rl);
            return VIDE;
        }
        try (Reader r = res.get().openAsReader()) {
            JsonObject o = GsonHelper.parse(r);
            ResourceLocation tex = new ResourceLocation(o.get("texture").getAsString());
            JsonArray ps = o.getAsJsonArray("parties");
            Partie[] parties = new Partie[ps.size()];
            boolean trans = false, add = false;
            for (int i = 0; i < parties.length; i++) {
                parties[i] = new Partie(ps.get(i).getAsJsonObject());
                trans |= parties[i].translucide;
                add |= parties[i].additif;
                if (parties[i].parent >= i) {
                    throw new IllegalStateException("partie " + i + " : parent " + parties[i].parent + " après elle");
                }
            }
            return new Maillage(RenderType.entityCutout(tex), trans ? RenderType.entityTranslucentEmissive(tex) : null,
                    add ? RenderType.eyes(tex) : null, parties);
        } catch (Exception e) {
            HeraliszUtils.LOG.error("Maillage illisible : {}", rl, e);
            return VIDE;
        }
    }

    /** t : temps en ticks (avec la fraction de l'image) ; phase : décalage propre à chaque exemplaire (degrés). */
    public void dessiner(PoseStack ps, MultiBufferSource buf, int light, float t, float phase) {
        dessiner(ps, buf, light, t, phase, 0f);
    }

    /** Idem, avec la force du vent (0..1) qui élargit balancement et flottement. */
    public void dessiner(PoseStack ps, MultiBufferSource buf, int light, float t, float phase, float vent) {
        dessiner(ps, buf, light, t, phase, vent, 1f);
    }

    /** Idem ; nuit (0 jour .. 1 nuit) : intensité des parties additives (faisceau du phare à peine visible le jour). */
    public void dessiner(PoseStack ps, MultiBufferSource buf, int light, float t, float phase, float vent, float nuit) {
        this.nuit = nuit;
        if (type == null) {
            return;
        }
        for (int i = 0; i < parties.length; i++) {                   // transformations (parents d'abord)
            Partie p = parties[i];
            Matrix4f m = mats[i];
            if (p.parent >= 0) {
                m.set(mats[p.parent]);
            } else {
                m.identity();
            }
            angles[i] = p.locale(m, t, phase, vent, p.parent >= 0 ? angles[p.parent] : 0f);
        }
        VertexConsumer vc = buf.getBuffer(type);                      // opaques, translucides, lumières (un tampon à la fois)
        for (int i = 0; i < parties.length; i++) {
            if (!parties[i].translucide && !parties[i].additif) {
                emettre(ps, vc, i, light, t, phase, vent);
            }
        }
        if (typeTrans != null) {
            VertexConsumer vt = buf.getBuffer(typeTrans);
            for (int i = 0; i < parties.length; i++) {
                if (parties[i].translucide) {
                    emettre(ps, vt, i, light, t, phase, vent);
                }
            }
        }
        if (typeAdd != null) {                                        // lumière additive : faisceau, halos des feux
            VertexConsumer va = buf.getBuffer(typeAdd);
            for (int i = 0; i < parties.length; i++) {
                if (parties[i].additif) {
                    emettre(ps, va, i, light, t, phase, vent);
                }
            }
        }
    }

    private void emettre(PoseStack ps, VertexConsumer vc, int i, int light, float t, float phase, float vent) {
        Partie p = parties[i];
        if (!p.allumee(t, phase)) {
            return;
        }
        ps.pushPose();
        ps.last().pose().mul(mats[i]);
        ps.last().normal().mul(NORMALE.set(mats[i]));
        int col = p.additif ? (int) (255 * (0.12f + 0.88f * nuit)) : 255;
        p.dessiner(ps, vc, p.lumiere ? LightTexture.FULL_BRIGHT : light, t, phase, vent, col);
        ps.popPose();
    }

    private static final class Partie {
        private final float px, py, pz, ax, ay, az, vitesse;
        private final int parent;
        private final boolean contre, lumiere, translucide, additif;
        private final float[] balancier, glisse, clignote;
        private final boolean flotte;
        private final float amp, freq, onde, dx, dy, dz;
        private final float[] v, n;
        private final int quads;
        private final float[] q = new float[12];

        Partie(JsonObject o) {
            float[] pv = floats(o.getAsJsonArray("pivot")), ax3 = floats(o.getAsJsonArray("axe"));
            px = pv[0];
            py = pv[1];
            pz = pv[2];
            ax = ax3[0];
            ay = ax3[1];
            az = ax3[2];
            vitesse = o.get("vitesse").getAsFloat();
            parent = o.has("parent") ? o.get("parent").getAsInt() : -1;
            contre = o.has("contre") && o.get("contre").getAsBoolean();
            lumiere = o.has("lumiere") && o.get("lumiere").getAsBoolean();
            translucide = o.has("translucide") && o.get("translucide").getAsBoolean();
            additif = o.has("additif") && o.get("additif").getAsBoolean();
            balancier = o.has("balancier") ? floats(o.getAsJsonArray("balancier")) : null;
            glisse = o.has("glisse") ? floats(o.getAsJsonArray("glisse")) : null;
            clignote = o.has("clignote") ? floats(o.getAsJsonArray("clignote")) : null;
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
            for (int i = 0; i < out.length; i++) {
                out[i] = a.get(i).getAsFloat();
            }
            return out;
        }

        /** Mouvement propre multiplié dans m (déjà la transformation du parent) ; renvoie l'angle de rotation propre
         *  (degrés) que ses enfants « contre » annulent. */
        float locale(Matrix4f m, float t, float phase, float vent, float angleParent) {
            if (glisse != null) {                                     // coulisse (chariot de grue, tige de pompe)
                float s = Mth.sin(glisse[3] * t + (glisse.length > 4 ? glisse[4] : 0f) * 0.0174533f + phase * 0.0174533f);
                m.translate(glisse[0] * s, glisse[1] * s, glisse[2] * s);
            }
            float ang = vitesse != 0 ? vitesse * t + phase : 0f;
            float rot = ang - (contre ? angleParent : 0f);
            float bal = 0f, roulis = 0f;
            if (balancier != null) {                                  // va-et-vient (lanterne, balancier de pompe, flèche)
                bal = (balancier[0] + balancier[2] * vent) * Mth.sin(balancier[1] * t + phase * 0.0174533f)
                        + 0.35f * balancier[2] * vent * Mth.sin(balancier[1] * 2.3f * t);
                roulis = bal * (balancier.length > 3 ? balancier[3] : 0.4f) * Mth.cos(balancier[1] * 0.7f * t);
            }
            if (rot != 0f || bal != 0f) {
                m.translate(px, py, pz);
                if (rot + bal != 0f) {
                    m.rotate((float) Math.toRadians(rot + bal), ax, ay, az);
                }
                if (roulis != 0f) {
                    m.rotate((float) Math.toRadians(roulis), az, ay, ax);
                }
                m.translate(-px, -py, -pz);
            }
            return ang;
        }

        /** Feu clignotant : allumé une part `durée` de chaque période (ticks), décalé par exemplaire. */
        boolean allumee(float t, float phase) {
            if (clignote == null) {
                return true;
            }
            float per = clignote[0];
            float x = (t + (clignote.length > 2 ? clignote[2] : 0f) + phase / 360f * per) % per;
            return x < clignote[1];
        }

        void dessiner(PoseStack ps, VertexConsumer vc, int light, float t, float phase, float vent, int col) {
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            Matrix3f nm = pose.normal();
            float ph = phase * 0.0174533f;
            int alpha = translucide ? 200 : 255;
            for (int i = 0; i < quads; i++) {
                int b = i * 24;
                float nx = n[i * 3], ny = n[i * 3 + 1], nz = n[i * 3 + 2];
                boolean bouge = false;
                for (int k = 0; k < 4; k++) {
                    int o = b + k * 6;
                    float x = v[o], y = v[o + 1], z = v[o + 2], w = v[o + 5];
                    if (flotte && w != 0) {
                        float d = w * amp * (1f + 2.5f * vent) * Mth.sin(freq * (1f + 0.8f * vent) * t - onde * (x + 0.7f * z + 0.3f * y) + ph);
                        x += dx * d;
                        y += dy * d;
                        z += dz * d;
                        bouge = true;
                    }
                    q[k * 3] = x;
                    q[k * 3 + 1] = y;
                    q[k * 3 + 2] = z;
                }
                if (bouge) {                                   // normale du tissu déformé : (p2 - p0) x (p3 - p1)
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
                    vc.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(col, col, col, alpha).uv(v[o + 3], v[o + 4])
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, nx, ny, nz).endVertex();
                }
            }
        }
    }
}
