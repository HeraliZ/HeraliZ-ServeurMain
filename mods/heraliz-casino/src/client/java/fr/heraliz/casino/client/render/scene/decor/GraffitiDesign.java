package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.mesh.Occlusion;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.Sprite;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;
import static fr.heraliz.casino.client.render.scene.decor.DecorAtlas.*;

/**
 * Graffitis qui indiquent la caisse (le Trou) et l'entree VIP (le Coffre), trois blocs sur deux :
 * <ul>
 * <li>CAISSE : bombe fluo verte et rose, nuage noir, tags autour ; un tube a lumiere noire visse au-dessus la fait
 * briller dans le noir et teinte le mur de violet ;</li>
 * <li>VIP : lettres chromees dorees, couronne, etincelles ; une lampe de tableau en laiton l'eclaire d'en haut.</li>
 * </ul>
 * Variantes (DecorNames.graffiti*) : fleche vers la droite, la gauche, le bas ou aucune ; a meme le mur ou sur un
 * panneau (tole rouillee / laque noire filetee d'or).
 */
public final class GraffitiDesign extends DecorDesign {
    private final boolean vip, panel;
    private final int arrow;
    /** Profondeur des decalcomanies (a meme le mur, ou sur le panneau). */
    private final float dz;

    private Mesh hardware = Mesh.EMPTY, hardwareS = Mesh.EMPTY, decals = Mesh.EMPTY, lamp = Mesh.EMPTY, wash = Mesh.EMPTY;
    private float[] lampLine;

    public GraffitiDesign(boolean vip, int mode) {
        super((vip ? "graffiti_vip_" : "graffiti_caisse_") + mode, vip ? 0xFFC840 : 0x7CFF3A);
        this.vip = vip;
        this.arrow = DecorNames.graffitiArrow(mode);
        this.panel = DecorNames.graffitiPanel(mode);
        this.dz = panel ? 1.0f : 0.07f;
        itemScale = 0.32f;
        itemCenter = new float[]{8, 15, 2};
    }

    @Override
    protected void build() {
        MeshBuilder hw = new MeshBuilder(), hs = new MeshBuilder();
        // ---- support
        if (panel && !vip) {
            // plaque de tole rouillee vissee de travers, un coin replie
            hw.push().translate(8, 14.5f, 0).rotateZ(1.4f);
            hw.bevelBox(-23, -13.5f, 0, 23, 13.5f, 0.75f, 0.15f, RUST);
            for (float[] c : new float[][]{{-21.5f, -12}, {21.5f, -12}, {-21.5f, 12}, {21.5f, 12}, {0, 12.2f}}) {
                hw.push().translate(c[0], c[1], 0.75f).rotateX(90);
                hw.rivet(0.45f, 0.25f, STEEL);
                hw.pop();
            }
            hw.pop();
        } else if (panel) {
            // panneau de laque noire, cadre de laiton
            hs.push().translate(8, 14.5f, 0);
            hs.bevelBox(-22.5f, -13.5f, 0, 22.5f, 13.5f, 0.85f, 0.2f, LACQUER);
            hs.pop();
            hw.push().translate(8, 14.5f, 0);
            hw.bevelBox(-23, -14, 0.3f, 23, -12.9f, 1.4f, 0.3f, POLISHED);
            hw.bevelBox(-23, 12.9f, 0.3f, 23, 14, 1.4f, 0.3f, POLISHED);
            hw.bevelBox(-23, -14, 0.3f, -21.9f, 14, 1.4f, 0.3f, POLISHED);
            hw.bevelBox(21.9f, -14, 0.3f, 23, 14, 1.4f, 0.3f, POLISHED);
            for (float[] c : new float[][]{{-22.45f, -13.45f}, {22.45f, -13.45f}, {-22.45f, 13.45f}, {22.45f, 13.45f}}) {
                hw.push().translate(c[0], c[1], 1.4f).rotateX(90);
                hw.rivet(0.7f, 0.4f, POLISHED);
                hw.pop();
            }
            hw.pop();
        }
        // ---- eclairage
        MeshBuilder lp = new MeshBuilder();
        if (!vip) {
            // reglette a lumiere noire : boitier d'alu, tube violet, cable qui descend le long du mur
            hw.bevelBox(-9, 28.4f, 0, 25, 30.4f, 1.3f, 0.25f, STEEL);
            for (float x : new float[]{-8.6f, 23.6f}) hw.bevelBox(x, 27.6f, 0.4f, x + 1.0f, 29.2f, 2.8f, 0.15f, DARK);
            hw.tube(new float[][]{p(25, 29.4f, 0.5f), p(27.5f, 28.6f, 0.4f), p(28.6f, 20, 0.35f), p(28.2f, 9, 0.35f), p(29.2f, 0.4f, 0.35f)},
                    new float[]{0.16f, 0.16f, 0.16f, 0.16f, 0.16f}, 5, CABLE, true, true, 0, false);
            for (float y : new float[]{22, 12}) tape(hw, TAPE, 28.5f, y, 0.55f, 2.6f, 0.8f, 75);
            lp.push().translate(-7.6f, 28.6f, 1.9f).rotateZ(-90);
            lp.cylinder(0.62f, 0, 31.2f, 12, UV_TUBE, UV_TUBE, UV_TUBE);
            lp.pop();
            lampLine = new float[]{-7.6f, 23.6f, 28.6f, 1.9f};
        } else {
            // lampe de tableau en laiton : platine, bras courbe, reflecteur demi-cylindrique, tube d'ampoules
            hw.push().translate(8, 30.6f, 0);
            hw.bevelBox(-2.2f, -0.9f, 0, 2.2f, 0.9f, 0.6f, 0.2f, POLISHED);
            hw.pop();
            hw.tube(new float[][]{p(8, 30.6f, 0.5f), p(8, 31.3f, 1.8f), p(8, 31.2f, 3.4f), p(8, 30.7f, 4.3f)}, new float[]{0.32f, 0.3f, 0.3f, 0.3f}, 8,
                    POLISHED, true, true, 0, false);
            // reflecteur : couvre le dessus et l'avant, ouvert vers le bas et le mur (angles du tour : 90 = avant)
            hw.push().translate(-0.5f, 29.9f, 4.6f).rotateZ(-90);
            hw.lathe(new float[][]{{1.45f, 0}, {1.45f, 17}}, 12, 40, 220, POLISHED, 1, 1, 0, 0, 0, false);
            hw.lathe(new float[][]{{1.38f, 0}, {1.38f, 17}}, 12, 40, 220, ENAMEL_IN, 1, 1, 0, 0, 0, true);
            hw.pop();
            lp.push().translate(0.2f, 29.7f, 4.5f).rotateZ(-90);
            lp.cylinder(0.42f, 0, 15.6f, 10, BULB_ON, BULB_ON, BULB_ON);
            lp.pop();
            lampLine = new float[]{0.5f, 15.5f, 29.4f, 4.4f};
        }
        Mesh[] m = bakePair(hw, hs, 0.3f, 2.0f, 0.4f, Float.NaN);
        hardware = m[0];
        hardwareS = m[1];
        lamp = lp.build();
        // ---- la peinture (feuille des salles), sur le mur ou sur le panneau
        MeshBuilder d = new MeshBuilder();
        if (panel && !vip) d.push().translate(8, 14.5f, 0).rotateZ(1.4f).translate(-8, -14.5f, 0);
        else d.push();
        float tw = vip ? 34 : 44, th = vip ? 17 : 44 * 256f / 768f;
        float ty = arrow == 3 ? 15.5f : arrow == 2 ? 21.6f : 19.4f;
        d.decalZ(vip ? GRAF_VIP : GRAF_CAISSE, 8, ty, dz, tw, th);
        Mat arrowMat = vip ? GRAF_ARROW_VIP : GRAF_ARROW;
        if (arrow == 0 || arrow == 1) {
            float dir = arrow == 0 ? 1 : -1;
            d.decalZ(arrow == 0 ? arrowMat : arrowMat.sub(1, 0, 0, 1), 8 + dir * 7, 6.4f, dz + 0.01f, 22, 11);
        } else if (arrow == 2) {
            d.push().translate(8, 7.2f, dz + 0.01f).rotateZ(-90);
            d.decalZ(arrowMat, 0, 0, 0, 13.5f, 6.8f);
            d.pop();
        }
        if (vip) {
            d.push().translate(21.5f, ty + th * 0.5f + 0.4f, dz + 0.02f).rotateZ(-14);
            d.decalZ(GRAF_CROWN, 0, 0, 0, 8, 8);
            d.pop();
            d.decalZ(GRAF_SPARK, -9.5f, ty + 6, dz + 0.02f, 4.2f, 4.2f);
            d.decalZ(GRAF_SPARK, 25.5f, ty - 5.5f, dz + 0.02f, 3.2f, 3.2f);
            d.decalZ(GRAF_SPARK, -4.5f, ty - 8.5f, dz + 0.02f, 2.4f, 2.4f);
        }
        d.pop();
        decals = d.build();
        // ---- lumiere sur le mur (additif) : forte sous la lampe, s'efface vers le bas
        MeshBuilder w = new MeshBuilder();
        int from = w.mark();
        float top = vip ? 29.6f : 28.2f;
        washGrid(w, -16, -1, 32, top, 0.12f, 12, 8);
        w.shade(from, (x, y, z, nx, ny, nz, rgb) -> {
            float k = Ease.clamp01((y + 1) / (top + 1));
            float dx = (x - 8) / 24f;
            k = k * k * (1 - dx * dx);
            rgb[0] *= k;
            rgb[1] *= k;
            rgb[2] *= k;
        });
        wash = w.build();
    }

    @Override
    protected void body(Pose p, DrawList out) {
        solid2(p, out, hardware, hardwareS, 0);
    }

    /** Intensite de la lampe : lumiere noire qui hesite un peu ; ampoules stables. */
    private float lampLevel(Scene s, DecorView v) {
        if (s.item) return 1;
        if (!v.on) return 0;
        float t = s.time + s.seed * 613;
        if (vip) return 0.97f + 0.03f * (float) Math.sin(t * 3.1f);
        float c = t % 690;
        if (c > 672) return Ease.hash((int) t * 7 + 3) > 0.4f ? 1f : 0.1f;
        return 0.9f + 0.1f * (float) Math.sin(t * 5.7f);
    }

    @Override
    protected void animate(Scene s, DecorView v, Pose p, DrawList out) {
        float l = lampLevel(s, v);
        // la peinture : fluo sous la lumiere noire, doree sous la lampe ; sinon simplement eclairee par la salle
        sheetSolid(p, out, decals, l > 0.05f ? (vip ? 0.85f : 0.95f) * l : 0);
        if (vip) out.solid(p, lamp, l > 0.05f ? l : 0);
        else sheetSolid(p, out, lamp, l > 0.05f ? l : 0);
        if (s.item || l <= 0.05f) return;
        int col = vip ? 0xFFD890 : 0x9A40FF;
        out.additive(p, wash, col, (vip ? 0.16f : 0.2f) * l);
        float x0 = lampLine[0], x1 = lampLine[1], y = lampLine[2], z = lampLine[3];
        for (int k = 0; k < 5; k++) {
            float x = x0 + (x1 - x0) * (k + 0.5f) / 5;
            out.billboard(p, Sprite.HALO, x, y, z, 3.0f * l, 0, col, 0.3f * l);
        }
    }
}
