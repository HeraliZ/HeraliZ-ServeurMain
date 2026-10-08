package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.registry.Snd;

/**
 * Intro d'une machine a sous, jouee sur son ecran quand le joueur s'y assoit (cote client seulement, rien n'est
 * tire) : le tube s'allume, la mascotte fait son entree (Zeus et sa foudre, le zombie, le loup qui souffle, le chien),
 * le titre s'affiche, puis la grille arrive colonne par colonne et la machine est prete. La camera cadre l'ecran
 * pendant l'intro. Un lancement coupe l'intro.
 */
final class Intro {
    private Intro() {
    }

    /** Duree de l'intro ; arrivee de la grille ; apparition du titre (ticks). */
    static final int LEN = 88, REVEAL = 62, TITLE = 44;
    /** Allumage du tube. */
    static final int ON = 8;

    /** Ticks depuis le debut de l'intro, ou -1 (pas d'intro : objet, partie en cours, intro finie). */
    static float time(float intro, boolean playing, boolean item) {
        if (item || playing || intro < 0 || intro >= LEN) return -1;
        return intro;
    }

    /** Cadrage de l'ecran par la camera pendant l'intro (0..1) : elle s'y pose, puis rend la main a la fin. */
    static float focus(float intro, boolean playing) {
        if (playing || intro < 0 || intro >= LEN) return 0;
        return Ease.inOutSine(Ease.clamp01(intro / 10f)) * (1 - Ease.inOutSine(Ease.range(intro, LEN - 14, LEN)));
    }

    /** Scene de l'intro (avant l'arrivee de la grille) ? */
    static boolean scene(float t) {
        return t >= 0 && t < REVEAL;
    }

    /**
     * Arrivee de la grille : decalage (en rangees, vers le haut) de la colonne "reel" ; 0 = a sa place. Les colonnes
     * tombent l'une apres l'autre et rebondissent un peu.
     */
    static float drop(float t, int reel, int rows) {
        if (t < 0) return 0;
        float s = t - REVEAL - reel * 2.4f;
        float h = rows + 1.6f;
        if (s < 0) return h;
        if (s >= 10) return 0;
        return h * (1 - Ease.outBounce(s / 10f));
    }

    /** La colonne vient de toucher sa place (pour un eclat, un son) : 0..1 juste apres l'impact. */
    static float landed(float t, int reel) {
        float s = t - REVEAL - reel * 2.4f - 4;
        return s >= 0 && s < 6 ? 1 - s / 6f : 0;
    }

    /** Fond noir du tube eteint, puis la ligne lumineuse qui s'ouvre (allumage d'un televiseur cathodique). */
    static void crtOn(Pose p, DrawList out, Mat dark, Mat flash, float t, float x0, float y0, float x1, float y1, float z, int tint) {
        if (t >= ON + 6) return;
        float cy = (y0 + y1) / 2;
        MeshBuilder b = new MeshBuilder();
        // tube eteint : noir qui s'ouvre au centre
        float open = Ease.outCubic(Ease.range(t, 2, ON));
        float hh = (y1 - y0) / 2 * open;
        if (open < 1) {
            BigWin.clipQuad(b, dark, x0, cy + hh, x1, y1, -1e9f, 1e9f, z + 0.02f, 0.05f, false);
            BigWin.clipQuad(b, dark, x0, y0, x1, cy - hh, -1e9f, 1e9f, z + 0.02f, 0.05f, false);
            out.solid(p, b.buildDynamic(), 0);
        }
        // ligne puis eclat blanc qui retombe
        MeshBuilder f = new MeshBuilder();
        float line = Ease.bell(t, 0.5f, ON + 2);
        float lh = Math.max(0.08f, hh);
        BigWin.clipQuad(f, flash, x0 - 1.5f, cy - lh, x1 + 1.5f, cy + lh, y0, y1, z + 0.03f, line, false);
        out.additive(p, f.buildDynamic(), tint, 0.9f);
    }

    /**
     * Titre et sous-titre de l'intro (au centre de la grille) : ils sautent, puis montent et s'effacent pendant que les
     * premieres colonnes de la grille tombent.
     */
    static void title(Pose p, DrawList out, float t, String title, String sub, float cx, float cy, float z, float width, int color, int subColor) {
        if (t < TITLE || t >= REVEAL + 10) return;
        int flags = DrawList.T_CENTER | DrawList.T_OUTLINE | DrawList.T_BOLD;
        float leave = Ease.range(t, REVEAL, REVEAL + 10);
        float a = Ease.clamp01((t - TITLE) / 3f) * (1 - leave);
        float pop = Ease.outBack(Ease.clamp01((t - TITLE) / 7f), 2.6f);
        float beat = 1 + 0.04f * (float) Math.sin(t * 0.7f);
        float y = cy + 2.5f * Ease.inCubic(leave);
        out.text(p, title, cx, y + 0.9f, z, MachineDesign.fit(title, 1.6f, width, true) * pop * beat, color, a, flags, 0x000000);
        float sa = a * Ease.clamp01((t - TITLE - 6) / 4f);
        if (sa > 0.02f) out.text(p, sub, cx, y - 0.9f, z, MachineDesign.fit(sub, 0.7f, width, true), subColor, sa, flags, 0x000000);
    }

    /** Sons communs : allumage du tube, arrivee de la grille. */
    static void sounds(FxSink fx, float t, int reels, float x0, float cw, float y, float z) {
        if (at(t, 3)) fx.sound(Snd.SWITCH, (x0 + x0 + reels * cw) / 2, y, z, 0.6f, 1.6f);
        for (int r = 0; r < reels; r++) {
            if (at(t, (int) Math.floor(REVEAL + r * 2.4f + 4))) fx.sound(Snd.REEL_STOP, x0 + cw * (r + 0.5f), y, z, 0.45f, 1.1f + r * 0.06f);
        }
        if (at(t, TITLE)) fx.sound(Snd.BONUS, (x0 + x0 + reels * cw) / 2, y, z, 0.8f, 1.3f);
    }

    /** Evenement ponctuel : vrai pendant le tick k. */
    static boolean at(float t, int k) {
        return t >= k && t < k + 1;
    }

    /**
     * Quad texture decoupe au rectangle (cx0, cy0) - (cx1, cy1) (la fenetre de l'ecran : rien ne deborde sur le
     * cadre du televiseur) ; flipX retourne l'image.
     */
    static void quad(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float cx0, float cy0, float cx1, float cy1, float z, float tint,
                     boolean flipX) {
        float xa = Math.max(x0, cx0), xb = Math.min(x1, cx1), ya = Math.max(y0, cy0), yb = Math.min(y1, cy1);
        if (xb <= xa || yb <= ya || tint <= 0.005f) return;
        float w = x1 - x0, h = y1 - y0;
        float ua = (xa - x0) / w, ub = (xb - x0) / w;
        if (flipX) {
            ua = 1 - ua;
            ub = 1 - ub;
        }
        float vBot = (y1 - ya) / h, vTop = (y1 - yb) / h;
        float[][] pts = {MeshBuilder.p(xa, ya, z), MeshBuilder.p(xb, ya, z), MeshBuilder.p(xb, yb, z), MeshBuilder.p(xa, yb, z)};
        float[][] uv = {{m.u(ua), m.v(vBot)}, {m.u(ub), m.v(vBot)}, {m.u(ub), m.v(vTop)}, {m.u(ua), m.v(vTop)}};
        b.color(tint);
        b.poly(pts, uv, null, MeshBuilder.v(0, 0, 1));
        b.color(1);
    }

    /** Quad tourne de deg degres autour de son centre (sans decoupe : a garder dans la fenetre). */
    static void spin(MeshBuilder b, Mat m, float cx, float cy, float w, float h, float deg, float z, float tint) {
        if (tint <= 0.005f) return;
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), sn = (float) Math.sin(a), hw = w / 2, hh = h / 2;
        float[][] pts = {MeshBuilder.p(cx - hw * c + hh * sn, cy - hw * sn - hh * c, z), MeshBuilder.p(cx + hw * c + hh * sn, cy + hw * sn - hh * c, z),
                MeshBuilder.p(cx + hw * c - hh * sn, cy + hw * sn + hh * c, z), MeshBuilder.p(cx - hw * c - hh * sn, cy - hw * sn + hh * c, z)};
        float[][] uv = {{m.u(0), m.v(1)}, {m.u(1), m.v(1)}, {m.u(1), m.v(0)}, {m.u(0), m.v(0)}};
        b.color(tint);
        b.poly(pts, uv, null, MeshBuilder.v(0, 0, 1));
        b.color(1);
    }

    /** Petit tremblement de l'image (impact) : decalage horizontal qui s'amortit entre "from" et "from + len". */
    static float shake(float t, float from, float len, float amp) {
        if (t < from || t >= from + len) return 0;
        return (Ease.hash((int) (t * 3) + 17) - 0.5f) * 2 * amp * (1 - (t - from) / len);
    }
}
