package fr.heraliz.loot.client.rendu.coffres;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Ease;
import fr.heraliz.loot.client.rendu.scene.Pose;

import static fr.heraliz.loot.client.rendu.coffres.AtlasT7.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.v;

/**
 * Largage spécial : module de ravitaillement sur palette (montants d'alu, toit d'acier, quatre panneaux composites
 * olive sanglés, balise stroboscopique). Fermé, la balise clignote (et le fumigène rouge fume, côté jeu). Ouverture :
 * la boucle des sangles saute, les quatre panneaux tombent à plat l'un après l'autre avec un rebond ; dedans, la
 * cargaison arrimée, éclairée par les bandeaux du toit.
 */
public final class Largage extends DesignCoffre {
    static final float YP = 2.0f, YT = 13.6f;
    /** Panneau avant : x 2.2..13.8, z 14.4..15.2 ; charnière en bas, côté extérieur. */
    static final float PZ0 = 14.4f, PZ1 = 15.2f;

    private Mesh structure, panneau, balise, bandeaux, cargaison, lavisCargo, voile, suspentes;
    /** Hauteur de départ de la descente (px) et hauteur de la voile au-dessus du toit. */
    static final float CHUTE = 260, VOILE_Y = YT + 1.4f + 36;
    /** Le modèle est dessiné dans un bloc puis agrandi (environ 2 m de côté), centré sur ses 2 x 2 blocs. */
    static final float K = 1.95f;

    private static Pose agrandir(Pose p) {
        return p.translate(16, 0, 0).scale(K).translate(-8, 0, -8);
    }

    public Largage() {
        super("t7_largage", 40);
    }

    @Override
    protected void build() {
        // ------------------------------------------------------------ palette, montants, toit, cargaison (fixe)
        MeshBuilder s = new MeshBuilder();
        for (float z : new float[]{1.0f, 7.2f, 13.4f}) s.bevelBox(0.6f, 0, z, 15.4f, 0.9f, z + 1.6f, 0.2f, DARK);
        s.bevelBox(0.4f, 0.9f, 0.4f, 15.6f, YP, 15.6f, 0.3f, GUNMETAL);
        for (float[] c : new float[][]{{0.6f, 0.6f}, {13.8f, 0.6f}, {0.6f, 13.8f}, {13.8f, 13.8f}}) {
            s.bevelBox(c[0], YP, c[1], c[0] + 1.6f, YT, c[1] + 1.6f, 0.25f, ALU);
        }
        s.bevelBox(0.4f, YT, 0.4f, 15.6f, YT + 1.3f, 15.6f, 0.4f, GUNMETAL);
        // sangles croisées sur le toit, boucle centrale
        s.bevelBox(7.2f, YT + 1.3f, 0.3f, 8.8f, YT + 1.42f, 15.7f, 0.03f, STRAP);
        s.bevelBox(0.3f, YT + 1.32f, 7.2f, 15.7f, YT + 1.45f, 8.8f, 0.03f, STRAP);
        // plaque d'aération et marquage sur les chants du toit
        decal(s, VENTS_SLOT, 8, YT + 0.65f, 15.62f, 6.0f, 1.0f);
        // cargaison : caisse longue, caisse de munitions, module graphite
        s.bevelBox(3.0f, YP, 4.4f, 13.0f, YP + 2.8f, 11.6f, 0.35f, GRAPHITE);
        decal(s, LOGO, 8, YP + 1.4f, 11.62f, 4.0f, 1.0f);
        s.bevelBox(3.4f, YP + 2.8f, 5.0f, 7.6f, YP + 6.0f, 11.0f, 0.3f, OLIVE);
        s.bevelBox(3.2f, YP + 6.0f, 5.6f, 7.8f, YP + 6.35f, 10.4f, 0.1f, GUNMETAL);
        decal(s, T7, 5.5f, YP + 4.4f, 11.02f, 2.2f, 1.1f);
        s.bevelBox(8.4f, YP + 2.8f, 5.2f, 12.6f, YP + 7.2f, 10.8f, 0.35f, GRAPHITE);
        s.bevelBox(8.7f, YP + 6.2f, 10.8f, 12.3f, YP + 6.45f, 10.9f, 0.04f, LED_RED);
        // sangles d'arrimage de la cargaison
        s.bevelBox(5.0f, YP, 4.2f, 5.6f, YP + 6.1f, 11.8f, 0.05f, STRAP);
        s.bevelBox(10.2f, YP, 4.2f, 10.8f, YP + 7.3f, 11.8f, 0.05f, STRAP);
        structure = cuire(s, 0);

        // ------------------------------------------------------------ panneau (avant), réutilisé pour les 4 côtés
        MeshBuilder pn = new MeshBuilder();
        pn.bevelBox(2.2f, YP, PZ0, 13.8f, YT, PZ1, 0.25f, OLIVE);
        // cadre d'alu sur la face extérieure
        float z = PZ1, e = 0.5f;
        pn.bevelBox(2.4f, YP + 0.2f, z, 13.6f, YP + 0.2f + e, z + 0.15f, 0.05f, ALU);
        pn.bevelBox(2.4f, YT - 0.2f - e, z, 13.6f, YT - 0.2f, z + 0.15f, 0.05f, ALU);
        pn.bevelBox(2.4f, YP + 0.72f, z, 2.4f + e, YT - 0.72f, z + 0.15f, 0.05f, ALU);
        pn.bevelBox(13.6f - e, YP + 0.72f, z, 13.6f, YT - 0.72f, z + 0.15f, 0.05f, ALU);
        // sangle verticale, marquages
        pn.bevelBox(7.2f, YP, z, 8.8f, YT, z + 0.2f, 0.03f, STRAP);
        pn.push().translate(8, 9.2f, z + 0.13f);
        pn.rivet(0.55f, 0.25f, CHROME);
        pn.pop();
        decal(pn, WARN, 8, 11.6f, z + 0.16f, 9.0f, 2.25f);
        decal(pn, LOGO, 4.9f, 4.0f, z + 0.01f, 3.6f, 0.9f);
        decal(pn, T7, 11.4f, 4.0f, z + 0.01f, 1.8f, 0.9f);
        // face intérieure : nervures
        for (float y : new float[]{5.0f, 8.0f, 11.0f}) pn.bevelBox(3.0f, y, PZ0 - 0.3f, 13.0f, y + 0.6f, PZ0, 0.08f, OLIVE);
        panneau = cuire(pn, Float.NaN);

        // ------------------------------------------------------------ balise stroboscopique
        MeshBuilder b = new MeshBuilder();
        b.push().translate(8, YT + 1.42f, 8);
        b.cylinder(1.5f, 0, 0.5f, 20, DARK, null, DARK);
        b.translate(0, 0.5f, 0);
        b.sphereSection(1.15f, 18, 6, 0, 90, LED_RED);
        b.pop();
        balise = b.build();
        // bandeaux sous le toit
        MeshBuilder l = new MeshBuilder();
        l.bevelBox(2.4f, YT - 0.25f, 2.6f, 13.6f, YT, 3.0f, 0.04f, LED_WHITE);
        l.bevelBox(2.4f, YT - 0.25f, 13.0f, 13.6f, YT, 13.4f, 0.04f, LED_WHITE);
        bandeaux = l.build();
        MeshBuilder lv = new MeshBuilder();
        lavis(lv, LED_WHITE, v(0, 1, 0), new float[][]{p(3.0f, YP + 2.83f, 4.4f), p(13.0f, YP + 2.83f, 4.4f), p(13.0f, YP + 2.83f, 11.6f), p(3.0f, YP + 2.83f, 11.6f)}, 0.5f, 0.5f, 0.5f, 0.5f);
        lavis(lv, LED_WHITE, v(0, 0, 1), new float[][]{p(3.0f, YP, 11.63f), p(13.0f, YP, 11.63f), p(13.0f, YP + 2.8f, 11.63f), p(3.0f, YP + 2.8f, 11.63f)}, 0.15f, 0.15f, 0.4f, 0.4f);
        lavisCargo = lv.build();

        // ------------------------------------------------------------ parachute : voile en coupole, huit suspentes
        MeshBuilder pv = new MeshBuilder();
        pv.push().translate(8, VOILE_Y, 8);
        pv.lathe(new float[][]{{22, 0}, {21.2f, 3}, {18.6f, 6.6f}, {14, 9.3f}, {8, 10.9f}, {0.01f, 11.4f}}, 32, 0, 360, PARACHUTE, 1, 1, 0, 0, 0, false);
        pv.pop();
        voile = pv.build();
        MeshBuilder sp = new MeshBuilder();
        float[][] coins = {{1.4f, 1.4f}, {14.6f, 1.4f}, {14.6f, 14.6f}, {1.4f, 14.6f}};
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * (k + 0.5) / 8;
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            float[] c = coins[cx >= 0 ? (cz >= 0 ? 2 : 1) : (cz >= 0 ? 3 : 0)];
            sp.tube(new float[][]{p(8 + cx * 21.6f, VOILE_Y + 0.2f, 8 + cz * 21.6f), p(c[0], YT + 1.3f, c[1])}, new float[]{0.08f, 0.08f}, 4, DARK, false, false, 0, false);
        }
        suspentes = sp.build();
    }

    /** Le largage descend du ciel sous son parachute, se balance, se pose ; la voile s'affaisse et glisse au sol. */
    @Override
    public void dessinerApparition(float f, float h, boolean disparait, Pose p, DrawList out) {
        if (disparait) {
            super.dessinerApparition(f, h, true, p, out);
            return;
        }
        float chute = seg(f, 0, 0.8f);
        float y = CHUTE * (1 - Ease.outQuad(chute));
        float balance = (1 - chute) * 7f;
        p.push();
        agrandir(p);
        p.push().translate(0, y, 0);
        autour(p, 8, VOILE_Y, 8, 'z', balance * (float) Math.sin(h * 0.11f));
        autour(p, 8, VOILE_Y, 8, 'x', balance * 0.6f * (float) Math.cos(h * 0.09f));
        dessinerInterne(0, h, true, p, out);
        float g = seg(f, 0.8f, 1f);
        if (g < 0.999f) {
            p.push().translate(26 * g, -VOILE_Y * 0.85f * Ease.inQuad(g), 0);
            p.translate(8, VOILE_Y, 8).scale(1 + 0.2f * g, 1 - 0.95f * g, 1 + 0.2f * g).translate(-8, -VOILE_Y, -8);
            out.solid(p, voile, 0);
            if (g < 0.05f) out.solid(p, suspentes, 0);
            p.pop();
        }
        p.pop();
        p.pop();
    }

    @Override
    public float[] bornes() {
        return new float[]{0.4f, 0, -15.6f, 31.6f, 29.6f, 15.6f};
    }

    @Override
    public void dessiner(float t, float h, boolean ferme, Pose p, DrawList out) {
        p.push();
        agrandir(p);
        dessinerInterne(t, h, ferme, p, out);
        p.pop();
    }

    private void dessinerInterne(float t, float h, boolean ferme, Pose p, DrawList out) {
        out.solid(p, structure, 0);
        // balise : éclats brefs tant que c'est fermé, rapides pendant l'ouverture, puis fixe et douce
        float flash;
        if (ferme) flash = (h % 16) < 2.5f ? 1f : 0.08f;
        else if (t < 10) flash = (h % 4) < 2 ? 1f : 0.1f;
        else flash = 0.35f;
        out.solid(p, balise, flash);
        float lum = seg(t, 14, 30);
        if (lum > 0.01f) {
            out.solid(p, bandeaux, lum);
            out.additive(p, lavisCargo, 0xFFF4EA, 0.28f * lum);
        }
        // panneaux : avant, droite, arrière, gauche ; chute avec rebond
        for (int k = 0; k < 4; k++) {
            float f = Ease.outBounce(seg(t, 4 + k * 2.5f, 18 + k * 2.5f));
            p.push();
            autour(p, 8, 0, 8, 'y', k * 90f);
            autour(p, 0, YP, PZ1, 'x', 97f * f);
            out.solid(p, panneau, 0);
            p.pop();
        }
    }

    @Override
    public float[] centreObjet() {
        return new float[]{8, 8, 8};
    }

    @Override
    public float echelleObjet() {
        return 0.6f;
    }
}
