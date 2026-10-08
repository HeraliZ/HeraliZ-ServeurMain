package fr.heralisz.utils.cine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Une goule de la cinématique (rien que chez le client, ce n'est pas une entité) : la variante (texture et calque des
 * yeux, textures/cine/goule_<variante>.png), la position dans le repère du plateau (PlateauRendu : x le long de la voie,
 * y au-dessus du rail, z vers la droite), l'orientation du corps, et les angles de chaque articulation ajoutés à la pose
 * de repos du squelette (GouleModele). Tout est retenu au tick précédent et interpolé à l'image : les gestes sont
 * fluides à n'importe quelle cadence. La tête peut s'écraser contre une vitre (ecrase) et s'arracher : elle est alors
 * dessinée seule, à sa propre place (teteLibre).
 */
@OnlyIn(Dist.CLIENT)
final class Goule {
    final String variante;
    ResourceLocation tex, yeux;
    double x, y, z, xo, yo, zo;
    /** Lacet (degrés, comme les entités : 0 = vers +z), tangage du corps autour des pieds (90 = couché sur le dos vers
     *  l'avant), roulis. */
    float lacet, tangage, roulis, lacetO, tangageO, roulisO;
    final Map<String, float[]> ang = new HashMap<>(), angO = new HashMap<>(), dec = new HashMap<>(), decO = new HashMap<>();
    float ecrase, ecraseO, machoire, machoireO;
    final Set<String> caches = new HashSet<>();
    boolean visible = true, teteLibre;
    double tx, ty, tz, txo, tyo, tzo;
    float tLacet, tTangage, tRoulis, tLacetO, tTangageO, tRoulisO;
    /** Compteur libre pour la mise en scène (ex. : ticks depuis qu'une goule de la horde a lâché le train). */
    int compteur;
    /** Lumière (bloc, 0..15) ; -1 : celle des acteurs de la cinématique. */
    int lumiere = -1;

    Goule(String variante) {
        this.variante = variante;
        texture(variante);
    }

    void texture(String v) {
        tex = new ResourceLocation(HeraliszUtils.MODID, "textures/cine/goule_" + v + ".png");
        yeux = new ResourceLocation(HeraliszUtils.MODID, "textures/cine/goule_" + v + "_yeux.png");
    }

    /** Yeux rouges qui luisent (la horde) : en plus du calque, deux lueurs qui percent la brume (CineActeurs). */
    boolean rouges;
    /** Position des yeux (repère du plateau) à l'image courante : gauche x, y, z, droit x, y, z ; valide si yeuxOk. */
    final double[] yeuxPos = new double[6];
    boolean yeuxOk;

    void yeuxRouges() {
        rouges = true;
        String v = tex.getPath().replace("textures/cine/goule_", "").replace(".png", "");
        yeux = new ResourceLocation(HeraliszUtils.MODID, "textures/cine/goule_" + v + "_yeux_rouges.png");
    }

    /** Fin de tick : l'état courant devient l'état « d'avant » de l'interpolation. */
    void tick() {
        xo = x;
        yo = y;
        zo = z;
        lacetO = lacet;
        tangageO = tangage;
        roulisO = roulis;
        ecraseO = ecrase;
        machoireO = machoire;
        txo = tx;
        tyo = ty;
        tzo = tz;
        tLacetO = tLacet;
        tTangageO = tTangage;
        tRoulisO = tRoulis;
        ang.forEach((k, v) -> {
            float[] o = angO.computeIfAbsent(k, kk -> new float[3]);
            System.arraycopy(v, 0, o, 0, 3);
        });
        dec.forEach((k, v) -> {
            float[] o = decO.computeIfAbsent(k, kk -> new float[3]);
            System.arraycopy(v, 0, o, 0, 3);
        });
    }

    /** Pose immédiate (coupe de plan) : pas d'interpolation depuis l'état précédent. */
    void figer() {
        tick();
    }

    void place(double nx, double ny, double nz, float nlacet) {
        x = nx;
        y = ny;
        z = nz;
        lacet = nlacet;
    }

    /** Angles (radians) d'une articulation, ajoutés à sa pose de repos. */
    void a(String os, float rx, float ry, float rz) {
        float[] v = ang.computeIfAbsent(os, k -> new float[3]);
        v[0] = rx;
        v[1] = ry;
        v[2] = rz;
    }

    /** Décalage (unités du modèle de la partie parente) d'une articulation. */
    void d(String os, float dx, float dy, float dz) {
        float[] v = dec.computeIfAbsent(os, k -> new float[3]);
        v[0] = dx;
        v[1] = dy;
        v[2] = dz;
    }

    void raz() {
        ang.clear();
        dec.clear();
    }

    private static float l(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private float[] interp(Map<String, float[]> cur, Map<String, float[]> old, String k, float p) {
        float[] c = cur.get(k);
        if (c == null) {
            return null;
        }
        float[] o = old.getOrDefault(k, c);
        return new float[]{l(o[0], c[0], p), l(o[1], c[1], p), l(o[2], c[2], p)};
    }

    /** Pose les angles de cette goule sur le modèle partagé. */
    private void poser(GouleModele m, float p) {
        float ech = l(ecraseO, ecrase, p), mach = l(machoireO, machoire, p);
        for (Map.Entry<String, GouleModele.Os> en : m.os.entrySet()) {
            String k = en.getKey();
            GouleModele.Os o = en.getValue();
            var part = o.part();
            float[] a = interp(ang, angO, k, p), dd = interp(dec, decO, k, p);
            part.x = o.px() + (dd != null ? dd[0] : 0);
            part.y = o.py() + (dd != null ? dd[1] : 0);
            part.z = o.pz() + (dd != null ? dd[2] : 0);
            part.xRot = o.rx() + (a != null ? a[0] : 0);
            part.yRot = o.ry() + (a != null ? a[1] : 0);
            part.zRot = o.rz() + (a != null ? a[2] : 0);
            part.xScale = part.yScale = part.zScale = o.echelle();
            part.visible = !caches.contains(k) && (o.variantes() == null || o.variantes().contains(variante));
        }
        GouleModele.Os tete = m.os.get("tete"), mac = m.os.get("machoire");
        if (tete != null && ech > 0) {
            tete.part().zScale = tete.echelle() * (1f - 0.3f * ech);           // le visage écrasé sur la vitre
            tete.part().xScale = tete.echelle() * (1f + 0.08f * ech);
        }
        if (mac != null) {
            mac.part().xRot += mach * 0.95f;                                      // la mâchoire qui se décroche
        }
    }

    /** Position (repère du plateau) du pivot d'une articulation avec la pose actuelle (la tête : sa base, au cou). */
    Vec3 point(String nom) {
        GouleModele m = GouleModele.get();
        poser(m, 1f);
        java.util.ArrayDeque<GouleModele.Os> chaine = new java.util.ArrayDeque<>();
        for (GouleModele.Os o = m.os.get(nom); o != null; o = o.parent() != null ? m.os.get(o.parent()) : null) {
            chaine.push(o);
        }
        PoseStack ps = new PoseStack();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(180f - lacet));
        ps.mulPose(Axis.XP.rotationDegrees(tangage));
        ps.mulPose(Axis.ZP.rotationDegrees(roulis));
        ps.scale(-0.5f, -0.5f, 0.5f);
        ps.translate(0, -4.0, 0);
        for (GouleModele.Os o : chaine) {
            o.part().translateAndRotate(ps);
        }
        org.joml.Vector3f v = ps.last().pose().transformPosition(new org.joml.Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    /** Cinématique inverse d'un bras : cherche les angles de l'épaule (x, z) et du coude (x) qui posent le poignet sur la
     *  cible (descente par coordonnées, pas qui rétrécit) ; part des angles actuels. */
    void bras(String cote, Vec3 cible) {
        String ep = "bras_" + cote, co = "avantbras_" + cote, po = "main_" + cote;
        float[] e = ang.computeIfAbsent(ep, k -> new float[3]), c = ang.computeIfAbsent(co, k -> new float[3]);
        float[][] vars = {e, e, c};
        int[] idx = {0, 2, 0};
        double best = point(po).distanceToSqr(cible);
        float pas = 0.4f;
        for (int it = 0; it < 60 && pas > 0.004f; it++) {
            boolean mieux = false;
            for (int i = 0; i < 3; i++) {
                for (float sgn : new float[]{1f, -1f}) {
                    float old = vars[i][idx[i]];
                    vars[i][idx[i]] = old + sgn * pas;
                    if (i == 2) {
                        vars[i][idx[i]] = Mth.clamp(vars[i][idx[i]], -2.6f, 0f);    // le coude ne plie que dans un sens
                    }
                    double d = point(po).distanceToSqr(cible);
                    if (d < best) {
                        best = d;
                        mieux = true;
                    } else {
                        vars[i][idx[i]] = old;
                    }
                }
            }
            if (!mieux) {
                pas *= 0.5f;
            }
        }
    }

    /** Déplace tout le corps pour que le pivot de l'articulation tombe sur la cible. */
    void amener(String nom, Vec3 cible) {
        Vec3 d = cible.subtract(point(nom));
        x += d.x;
        y += d.y;
        z += d.z;
    }

    /** Dessine la goule ; (ox, oy, oz) : origine du plateau, cam : position de la caméra. */
    void dessiner(PoseStack ps, MultiBufferSource src, double ox, double oy, double oz, Vec3 cam, float p, int lum) {
        dessiner(ps, src, ox, oy, oz, cam, p, lum, true);
    }

    /** modele = false : hors de vue (dans le brouillard, derrière la caméra) : on ne dessine pas le corps, mais on calcule
     *  toujours où sont les yeux (les lueurs rouges percent la brume). */
    void dessiner(PoseStack ps, MultiBufferSource src, double ox, double oy, double oz, Vec3 cam, float p, int lum, boolean modele) {
        if (!visible && !teteLibre || !modele && !rouges && !teteLibre) {
            return;
        }
        GouleModele m = GouleModele.get();
        poser(m, p);
        int light = net.minecraft.client.renderer.LightTexture.pack(lumiere >= 0 ? lumiere : lum, 0);
        GouleModele.Os tete = m.os.get("tete");
        boolean libre = teteLibre && tete != null;
        if (libre) {
            tete.part().visible = false;
        }
        if (visible && modele) {                                                  // le corps (parti : la tête reste seule)
            ps.pushPose();
            ps.translate(ox + l((float) xo, (float) x, p) - cam.x, oy + l((float) yo, (float) y, p) - cam.y, oz + l((float) zo, (float) z, p) - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(p, lacetO, lacet)));
            ps.mulPose(Axis.XP.rotationDegrees(l(tangageO, tangage, p)));
            ps.mulPose(Axis.ZP.rotationDegrees(l(roulisO, roulis, p)));
            ps.scale(-0.5f, -0.5f, 0.5f);
            ps.translate(0, -4.0, 0);
            m.racine.render(ps, src.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            m.racine.render(ps, src.getBuffer(RenderType.eyes(yeux)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        if (rouges) {                                                             // où sont les yeux, pour les lueurs
            PoseStack q = new PoseStack();
            q.translate(l((float) xo, (float) x, p), l((float) yo, (float) y, p), l((float) zo, (float) z, p));
            q.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(p, lacetO, lacet)));
            q.mulPose(Axis.XP.rotationDegrees(l(tangageO, tangage, p)));
            q.mulPose(Axis.ZP.rotationDegrees(l(roulisO, roulis, p)));
            q.scale(-0.5f, -0.5f, 0.5f);
            q.translate(0, -4.0, 0);
            java.util.ArrayDeque<GouleModele.Os> chaine = new java.util.ArrayDeque<>();
            for (GouleModele.Os o = m.os.get("tete"); o != null; o = o.parent() != null ? m.os.get(o.parent()) : null) {
                chaine.push(o);
            }
            for (GouleModele.Os o : chaine) {
                o.part().translateAndRotate(q);
            }
            for (int i = 0; i < 2; i++) {                                         // yeux : sur la face, à 4x (texels)
                org.joml.Vector3f v = q.last().pose().transformPosition(new org.joml.Vector3f((i == 0 ? -5.2f : 5.0f) / 16f,
                        -12.4f / 16f, -14.3f / 16f));
                yeuxPos[i * 3] = v.x;
                yeuxPos[i * 3 + 1] = v.y;
                yeuxPos[i * 3 + 2] = v.z;
            }
            yeuxOk = !teteLibre;
        }
        if (libre) {                                                              // la tête arrachée, seule
            var part = tete.part();
            part.visible = true;
            part.x = part.y = part.z = 0;
            part.xRot = part.yRot = part.zRot = 0;
            ps.pushPose();
            ps.translate(ox + l((float) txo, (float) tx, p) - cam.x, oy + l((float) tyo, (float) ty, p) - cam.y,
                    oz + l((float) tzo, (float) tz, p) - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(p, tLacetO, tLacet)));
            ps.mulPose(Axis.XP.rotationDegrees(l(tTangageO, tTangage, p)));
            ps.mulPose(Axis.ZP.rotationDegrees(l(tRoulisO, tRoulis, p)));
            ps.scale(-0.5f, -0.5f, 0.5f);
            part.render(ps, src.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
