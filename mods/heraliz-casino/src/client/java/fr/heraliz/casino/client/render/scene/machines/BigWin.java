package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import net.minecraft.client.resources.language.I18n;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Annonce des gros gains des machines a sous : la mascotte (zombie, Zeus, loup) surgit du bas de l'ecran et rugit,
 * ses mains se plaquent sur la vitre, le palier s'affiche ("GROS GAIN", "MEGA GAIN", "GAIN EPIQUE", "GAIN MAXIMUM")
 * et le montant defile.
 */
final class BigWin {
    private BigWin() {
    }

    /** Cles des paliers (traduits a l'affichage, langue choisie par le joueur). */
    static final String[] LABELS = {"", "overlay.heralizcasino.big", "heralizcasino.bigwin.mega", "heralizcasino.bigwin.epique", "heralizcasino.slot.gain_max"};

    /** Assombrissement des symboles pendant l'annonce (1 = normal). */
    static float dim(float t, float len) {
        if (t < 0 || t > len) return 1;
        return 1 - 0.7f * Ease.clamp01(t / 6f) * (1 - Ease.range(t, len - 8, len));
    }

    /**
     * Mascotte : buste (images "calme" et "rugit") qui surgit du bas de la fenetre [clipY0, clipY1], et deux mains
     * (hand, retournee pour la droite) plaquees sur la vitre en z = glassZ. t = ticks depuis le debut, len = duree.
     */
    static void mascot(Pose p, DrawList out, float t, float len, Mat calm, Mat roar, Mat hand, float cx, float cy, float size,
                       float clipY0, float clipY1, float z, float glassZ, float handSize, float[] hands) {
        mascot(p, out, t, len, calm, roar, hand, cx, cy, size, 1f, clipY0, clipY1, z, glassZ, handSize, hands);
    }

    /** Idem avec une image non carree (aspect = largeur / hauteur). */
    static void mascot(Pose p, DrawList out, float t, float len, Mat calm, Mat roar, Mat hand, float cx, float cy, float size, float aspect,
                       float clipY0, float clipY1, float z, float glassZ, float handSize, float[] hands) {
        if (t < 0 || t > len) return;
        float rise = Ease.outBack(Ease.clamp01(t / 9f), 1.4f);
        float sink = Ease.inCubic(Ease.range(t, len - 9, len));
        float y = cy - size * 1.15f * (1 - rise) - size * 1.15f * sink;
        boolean roaring = t > 10 && t < len - 14 && ((int) (t / 6)) % 4 != 3;
        float shake = roaring ? (Ease.hash((int) (t * 2) * 7 + 3) - 0.5f) * 0.22f : 0;
        float breathe = 1 + 0.03f * (float) Math.sin(t * 0.5f) + (roaring ? 0.04f : 0);
        float h = size * breathe, w = h * aspect;
        MeshBuilder b = new MeshBuilder();
        clipQuad(b, roaring ? roar : calm, cx - w / 2 + shake, y - h / 2, cx + w / 2 + shake, y + h / 2, clipY0, clipY1, z, 1f, false);
        out.solid(p, b.buildDynamic(), roaring ? 0.75f : 0.55f);
        if (hand == null || hands == null) return;
        MeshBuilder hb = new MeshBuilder();
        for (int k = 0; k < 2; k++) {
            float at = 8 + k * 5;
            if (t < at || t > len - 5) continue;
            float slam = Ease.clamp01((t - at) / 3f);
            float hs = handSize * (1.7f - 0.7f * Ease.outCubic(slam));
            float slide = Ease.inQuad(Ease.range(t, len - 20, len - 5)) * handSize * 1.2f;
            float jitter = slam >= 1 && roaring ? (Ease.hash((int) t * 13 + k) - 0.5f) * 0.12f : 0;
            float hx = hands[k * 2] + jitter, hy = hands[k * 2 + 1] - slide;
            float a = slam < 1 ? 0.4f + 0.6f * slam : 1 - Ease.range(t, len - 10, len - 5) * 0.5f;
            clipQuad(hb, hand, hx - hs / 2, hy - hs / 2, hx + hs / 2, hy + hs / 2, -1e9f, 1e9f, glassZ, a, k == 1);
        }
        out.solid(p, hb.buildDynamic(), 0.25f);
    }

    /** Palier et montant qui defile ; tier 1..4. */
    static void text(Pose p, DrawList out, float t, float len, int tier, long amount, float cx, float yTitle, float yAmount, float z, float width,
                     int titleColor, int amountColor) {
        if (t < 0 || t > len || tier <= 0) return;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        float a = Ease.clamp01(t / 4f) * (1 - Ease.range(t, len - 6, len));
        float pop = Ease.outBack(Ease.clamp01((t - 4) / 7f), 2.4f);
        float beat = 1 + 0.06f * (float) Math.max(0, Math.sin(t * 0.8f));
        String label = I18n.get(LABELS[Math.min(4, tier)]);
        out.text(p, label, cx, yTitle, z, MachineDesign.fit(label, 1.9f, width, true) * pop * beat, titleColor, a, flags, 0x000000);
        float k = Ease.outCubic(Ease.clamp01((t - 6) / (len * 0.6f)));
        long shown = (long) Math.floor(amount * k);
        String txt = MachineDesign.fmt(shown);
        out.text(p, txt, cx, yAmount, z, MachineDesign.fit(txt, 2.3f, width * 0.65f, true) * Math.max(0.2f, pop), amountColor, a, flags, 0x2A0A00);
    }

    /**
     * Voile sombre sur l'image (gain total de fin de bonus : le total ressort, les textes des bandeaux restent nets car
     * le voile n'ecrit pas la profondeur). m : une tuile opaque quelconque de l'atlas ; a : opacite.
     */
    static void shade(Pose p, DrawList out, Mat m, float x0, float y0, float x1, float y1, float z, float a) {
        if (a <= 0.01f) return;
        MeshBuilder b = new MeshBuilder();
        clipQuad(b, m, x0, y0, x1, y1, -1e9f, 1e9f, z, 1f, false);
        out.translucent(p, b.buildDynamic(), 0x000000, a);
    }

    /** Opacite du voile de fin de bonus. */
    static final float SHADE = 0.5f;

    /** Quad texture decoupe verticalement a [cy0, cy1] ; flipX retourne l'image. */
    static void clipQuad(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float cy0, float cy1, float z, float tint, boolean flipX) {
        float yb = Math.max(y0, cy0), yt = Math.min(y1, cy1);
        if (yt <= yb) return;
        float h = y1 - y0;
        float vb = (y1 - yb) / h, vt = (y1 - yt) / h;
        float u0 = flipX ? 1 : 0, u1 = flipX ? 0 : 1;
        float[][] pts = {p(x0, yb, z), p(x1, yb, z), p(x1, yt, z), p(x0, yt, z)};
        float[][] uv = {{m.u(u0), m.v(vb)}, {m.u(u1), m.v(vb)}, {m.u(u1), m.v(vt)}, {m.u(u0), m.v(vt)}};
        b.color(tint);
        b.poly(pts, uv, null, v(0, 0, 1));
        b.color(1);
    }
}
