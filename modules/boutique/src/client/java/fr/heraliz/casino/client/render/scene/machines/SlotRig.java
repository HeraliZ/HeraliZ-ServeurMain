package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;

import static fr.heraliz.casino.client.render.mesh.MeshBuilder.p;
import static fr.heraliz.casino.client.render.mesh.MeshBuilder.v;

/**
 * Montage de recuperation commun aux machines a sous (Rotten, Olympe, Loup, La Niche), dans l'esprit des tables du
 * bunker : l'ecran de jeu est un vieux televiseur cathodique (caisson arrondi, tube a l'arriere, boutons, haut-parleur,
 * adhesif) pose sur ce qu'on a trouve (futs, colonnes, bottes de paille, parpaings...) ; la commande est une caisse a
 * munitions couchee aux gros boutons ; une batterie de voiture alimente le tout. Chaque machine choisit ses pieces.
 * La fenetre du jeu garde ses cotes (WX0..WX1 x WY0..WY1, ecran en SZ, vitre en FZ) ; au-dessus, dans le meme tube et
 * derriere la meme vitre, le televiseur affiche le titre de la machine (WY1..PY1) : plus d'enseigne posee par-dessus.
 * <p>
 * Optimisation : des boites chanfreinees et quelques cylindres a peu de cotes ; tout est "cuit" une fois dans la
 * carrosserie figee (un appel de dessin pour l'atlas de la machine, un pour la feuille de recuperation).
 */
final class SlotRig {
    private SlotRig() {
    }

    static final float FZ = 14.2f, SZ = 13.65f;
    static final float WX0 = 1.9f, WX1 = 14.1f, WY0 = 18.6f, WY1 = 31.4f;
    /** Titre affiche par le televiseur au-dessus du jeu : hauteur, haut de l'image (le tube va de WY0 a PY1). */
    static final float TITLE_H = 4.2f, PY1 = WY1 + TITLE_H;
    /** Televiseur : caisson (x, bas, haut, arriere) et plan de la facade (devant la vitre). */
    static final float TV_X0 = 0.5f, TV_X1 = 15.5f, TV_Y0 = 16.8f, TV_Y1 = PY1 + 1.6f, TV_BACK = 9.8f, TV_FRONT = 14.75f;
    /** Pupitre incline (memes boutons qu'avant) : translate(8, 15.5, 14.8), rotateX(10). */
    static final float DECK_X = 8, DECK_Y = 15.5f, DECK_Z = 14.8f, DECK_TILT = 10;
    /** Voyant de marche du televiseur (petit pave lumineux). */
    static final float[] LED_AT = {14.55f, 17.7f};

    // =================================================================== televiseur
    /**
     * Televiseur complet : caisson arrondi, tube en deux gradins a l'arriere, facade autour de la fenetre, embrasure
     * noire, puis les details (haut-parleur, plaque de marque, boutons, adhesif) dans "kit" (feuille de recuperation).
     * @param tape adhesif en croix sur le coin haut gauche
     */
    static void tv(MeshBuilder b, Mat casing, MeshBuilder kit, boolean tape) {
        b.bevelBox(TV_X0, TV_Y0, TV_BACK, TV_X1, TV_Y1, FZ, 0.8f, casing, null, MeshBuilder.SKIP_PZ);
        b.bevelBox(2.2f, 18.0f, 6.0f, 13.8f, PY1 + 0.4f, TV_BACK + 0.1f, 1.2f, casing);
        b.bevelBox(4.4f, 20.2f, 3.9f, 11.6f, PY1 - 1.8f, 6.1f, 0.9f, casing);
        front(b, casing, TV_Y1);
        details(kit, tape);
    }

    /** Facade : quatre traverses autour de l'image (jeu et titre ; la basse, plus haute, porte les boutons), jusqu'a yTop. */
    static void front(MeshBuilder b, Mat casing, float yTop) {
        b.bevelBox(TV_X0, PY1, FZ - 0.05f, TV_X1, yTop, TV_FRONT, 0.3f, casing);
        b.bevelBox(TV_X0, TV_Y0, FZ - 0.05f, TV_X1, WY0, TV_FRONT, 0.3f, casing);
        b.bevelBox(TV_X0, WY0, FZ - 0.05f, WX0, PY1, TV_FRONT, 0.25f, casing);
        b.bevelBox(WX1, WY0, FZ - 0.05f, TV_X1, PY1, TV_FRONT, 0.25f, casing);
    }

    /**
     * Titre affiche par le televiseur (zone WY1..PY1 de l'image) : un quad de l'atlas de la machine, a dessiner en
     * lumineux comme le reste de l'ecran.
     */
    static void title(MeshBuilder b, Mat card) {
        b.face(card, v(0, 0, 1), p(WX0 + 0.12f, WY1 + 0.14f, SZ + 0.012f), p(WX1 - 0.12f, WY1 + 0.14f, SZ + 0.012f), p(WX1 - 0.12f, PY1 - 0.12f, SZ + 0.012f),
                p(WX0 + 0.12f, PY1 - 0.12f, SZ + 0.012f));
    }

    /**
     * Embrasure de l'ecran : parois sombres de la fenetre entre l'ecran et la vitre (ne mordent pas sur l'image),
     * de y0 a y1 (une borne qui s'allonge la refait a sa hauteur).
     */
    static void embrasure(MeshBuilder b, Mat dark, float y0, float y1) {
        embrasure(b, dark, y0, y1, true, true);
    }

    /** Idem ; bottom / top : avec la paroi du bas (en y0) / du haut (en y1). Sans bottom ni top : les deux parois laterales. */
    static void embrasure(MeshBuilder b, Mat dark, float y0, float y1, boolean bottom, boolean top) {
        if (!bottom && top) {
            b.face(dark, v(0, -1, 0), p(WX0, y1, SZ), p(WX0, y1, TV_FRONT), p(WX1, y1, TV_FRONT), p(WX1, y1, SZ));
            return;
        }
        b.face(dark, v(1, 0, 0), p(WX0, y0, SZ), p(WX0, y0, TV_FRONT), p(WX0, y1, TV_FRONT), p(WX0, y1, SZ));
        b.face(dark, v(-1, 0, 0), p(WX1, y0, TV_FRONT), p(WX1, y0, SZ), p(WX1, y1, SZ), p(WX1, y1, TV_FRONT));
        if (bottom) b.face(dark, v(0, 1, 0), p(WX0, y0, TV_FRONT), p(WX0, y0, SZ), p(WX1, y0, SZ), p(WX1, y0, TV_FRONT));
        if (top) b.face(dark, v(0, -1, 0), p(WX0, y1, SZ), p(WX0, y1, TV_FRONT), p(WX1, y1, TV_FRONT), p(WX1, y1, SZ));
    }

    /** Details de la facade (feuille de recuperation) : haut-parleur, plaque de marque, deux boutons, adhesif. */
    static void details(MeshBuilder k, boolean tape) {
        float[] nz = v(0, 0, 1);
        float z = TV_FRONT + 0.01f;
        k.face(Recup.SPEAKER, nz, p(1.2f, 17.15f, z), p(6.4f, 17.15f, z), p(6.4f, 18.3f, z), p(1.2f, 18.3f, z));
        k.face(Recup.LABEL, nz, p(7.0f, 17.25f, z), p(10.1f, 17.25f, z), p(10.1f, 18.2f, z), p(7.0f, 18.2f, z));
        for (float x : new float[]{11.6f, 12.85f}) {
            k.push().translate(x, 17.7f, TV_FRONT).rotateX(90);
            k.cylinder(0.42f, 0, 0.38f, 8, Recup.DARK, null, Recup.BRASS);
            k.pop();
        }
        k.bevelBox(LED_AT[0] - 0.32f, LED_AT[1] - 0.25f, TV_FRONT - 0.02f, LED_AT[0] + 0.32f, LED_AT[1] + 0.25f, TV_FRONT + 0.06f, 0.03f, Recup.DARK);
        if (tape) {
            for (int sd = -1; sd <= 1; sd += 2) {
                k.push().translate(1.6f, PY1 + 0.5f, TV_FRONT + 0.03f).rotateZ(sd * 38);
                k.box6(-1.9f, -0.32f, 0, 1.9f, 0.32f, 0.05f, null, null, null, null, null, Recup.TAPE);
                k.pop();
            }
        }
    }

    /** Voyant de marche (atlas de la machine, tuile LED : a dessiner en lumineux). */
    static void led(MeshBuilder b, Mat led) {
        b.bevelBox(LED_AT[0] - 0.2f, LED_AT[1] - 0.13f, TV_FRONT + 0.05f, LED_AT[0] + 0.2f, LED_AT[1] + 0.13f, TV_FRONT + 0.1f, 0.02f, led);
    }

    /** Antenne "oreilles de lapin" posee sur le dessus du televiseur (deux brins, un tordu). */
    static void antenna(MeshBuilder k, float x, float z) {
        k.push().translate(x, TV_Y1, z);
        k.cylinder(0.75f, 0, 0.35f, 8, Recup.DARK, null, Recup.DARK);
        k.pop();
        float[][] a = {p(x - 0.2f, TV_Y1 + 0.3f, z), p(x - 1.8f, TV_Y1 + 3.4f, z - 0.3f), p(x - 3.0f, TV_Y1 + 6.2f, z - 0.6f)};
        float[][] c = {p(x + 0.2f, TV_Y1 + 0.3f, z), p(x + 1.6f, TV_Y1 + 3.0f, z + 0.2f), p(x + 2.0f, TV_Y1 + 4.6f, z + 0.9f), p(x + 3.2f, TV_Y1 + 5.4f, z + 1.0f)};
        k.tube(a, new float[]{0.07f, 0.06f, 0.05f}, 4, Recup.STEEL, false, true, 0, false);
        k.tube(c, new float[]{0.07f, 0.06f, 0.05f, 0.05f}, 4, Recup.STEEL, false, true, 0, false);
        for (float[] t : new float[][]{a[a.length - 1], c[c.length - 1]}) {
            k.push().translate(t[0], t[1], t[2]);
            k.sphere(0.13f, 5, 3, Recup.STEEL);
            k.pop();
        }
    }

    // =================================================================== pupitre
    /**
     * Pupitre incline : caisse a munitions couchee (couvercle = plaque des boutons, poignees, fermoirs, bande de danger),
     * trois petits boutons ; les deux gros (LANCER, BONUS) sont des maillages a part (ils s'enfoncent et brillent).
     */
    static void deck(MeshBuilder k, Mat box, Mat small) {
        k.push().translate(DECK_X, DECK_Y, DECK_Z).rotateX(DECK_TILT);
        k.bevelBox(-6.7f, -1.7f, -0.35f, 6.7f, 1.25f, 1.5f, 0.22f, box);
        k.bevelBox(-6.85f, 1.2f, -0.45f, 6.85f, 1.42f, 1.62f, 0.08f, box);
        for (int sd = -1; sd <= 1; sd += 2) {
            float x0 = sd < 0 ? -7.05f : 6.7f, x1 = sd < 0 ? -6.7f : 7.05f;
            k.bevelBox(x0, -0.55f, 0.1f, x1, 0.25f, 1.1f, 0.06f, Recup.RUST);
        }
        for (float x : new float[]{-4.7f, 4.7f}) k.bevelBox(x - 0.45f, -0.75f, 1.48f, x + 0.45f, 0.55f, 1.68f, 0.05f, Recup.STEEL);
        k.face(Recup.HAZARD, v(0, 0, 1), p(-3.6f, -1.35f, 1.51f), p(3.6f, -1.35f, 1.51f), p(3.6f, -0.55f, 1.51f), p(-3.6f, -0.55f, 1.51f));
        for (int i = 0; i < 3; i++) {
            float x = -1.9f + i * 1.9f;
            k.bevelBox(x - 0.7f, 1.42f, -0.15f, x + 0.7f, 1.75f, 1.0f, 0.12f, i == 1 ? Recup.STEEL : small);
        }
        k.pop();
        // equerres qui tiennent la caisse au meuble
        for (float x : new float[]{3.2f, 12.8f}) k.bevelBox(x - 0.18f, 11.0f, 13.6f, x + 0.18f, 14.3f, 14.5f, 0.04f, Recup.RUST);
    }

    // =================================================================== pieces
    /** Batterie de voiture posee en (x, y, z), face marquee "12V POWER" vers +Z, tournee de yaw degres. */
    static void battery(MeshBuilder k, float x, float y, float z, float yaw) {
        k.push().translate(x, y, z).rotateY(yaw);
        k.bevelBox(-2.0f, 0, -1.3f, 2.0f, 2.8f, 1.3f, 0.2f, Recup.DARK, new Mat[]{null, null, null, null, null, Recup.BATTERY}, 0);
        for (int sd = -1; sd <= 1; sd += 2) {
            k.push().translate(sd * 1.2f, 2.8f, -0.4f);
            k.cylinder(0.28f, 0, 0.42f, 6, sd < 0 ? Recup.RED : Recup.DARK, null, sd < 0 ? Recup.RED : Recup.DARK);
            k.pop();
        }
        k.pop();
    }

    /** Cable (noir ou rouge) entre deux points, qui pend un peu. */
    static void cable(MeshBuilder k, float[] a, float[] b, float sag, boolean red) {
        BunkerKit.cable(k, a, b, sag, 0.16f, red ? Recup.RED : Recup.CABLE);
    }

    /** Cable (noir ou rouge) qui suit un chemin (il contourne les pieces). */
    static void cable(MeshBuilder k, float[][] path, boolean red) {
        float[] radii = new float[path.length];
        java.util.Arrays.fill(radii, 0.16f);
        k.tube(path, radii, 5, red ? Recup.RED : Recup.CABLE, true, true, 0, false);
    }

    /** Fut debout (feuille de recuperation) : deux cerclages et le couvercle, a peu de cotes. */
    static void drum(MeshBuilder k, float x, float y, float z, Mat skin, float yaw, int segs) {
        k.push().translate(x, y, z).rotateY(yaw);
        k.cylinder(Recup.DRUM_R, 0, Recup.DRUM_H, segs, skin, Recup.RUST, Recup.RUST);
        for (float t : new float[]{0.04f, 0.5f, 0.96f}) {
            k.push().translate(0, Recup.DRUM_H * t, 0);
            k.torus(Recup.DRUM_R + 0.02f, 0.13f, segs, 3, 0, 360, Recup.RUST);
            k.pop();
        }
        k.push().translate(Recup.DRUM_R * 0.55f, Recup.DRUM_H, 0);
        k.cylinder(0.36f, 0, 0.16f, 6, Recup.RUST, null, Recup.RUST);
        k.pop();
        k.pop();
    }

    /** Caisse de planches : faces laterales marquees (decal), dessus et dessous en planches, montants d'angle. */
    static void crate(MeshBuilder k, float x0, float y0, float z0, float x1, float y1, float z1, Mat side) {
        k.bevelBox(x0, y0, z0, x1, y1, z1, 0.15f, Recup.WOOD, new Mat[]{side, side, null, null, null, side}, 0);
        float w = 0.45f, t = 0.1f;
        for (float x : new float[]{x0 - t, x1 + t - w}) {
            for (float z : new float[]{z0 - t, z1 + t - w}) k.bevelBox(x, y0, z, x + w, y1, z + w, 0.04f, Recup.WOOD);
        }
    }

    /**
     * Guirlande d'ampoules entre deux points : le fil dans "wire" (recuperation), le verre des ampoules dans "base"
     * (carrosserie figee de l'atlas de la machine, teinte choisie par l'appelant) et une coque a peine plus grosse dans
     * "glow" (tuile lumineuse "led") : seul ce calque additif est redessine a chaque image.
     */
    static void garland(MeshBuilder wire, MeshBuilder base, MeshBuilder glow, Mat led, float[] a, float[] b, float sag, int n) {
        BunkerKit.cable(wire, a, b, sag, 0.05f, Recup.CABLE);
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            float x = a[0] + (b[0] - a[0]) * t, y = a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), z = a[2] + (b[2] - a[2]) * t;
            base.push().translate(x, y - 0.32f, z);
            base.sphere(0.24f, 6, 4, led);
            base.pop();
            glow.push().translate(x, y - 0.32f, z);
            glow.sphere(0.27f, 6, 4, led);
            glow.pop();
        }
    }
}
