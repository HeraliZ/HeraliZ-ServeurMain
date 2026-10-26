package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mat;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;

import static fr.heraliz.loot.client.rendu.conteneurs.AtlasCivil.*;
import static fr.heraliz.loot.client.rendu.conteneurs.AtlasUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.KitUsure.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT3bAtlas.*;
import static fr.heraliz.loot.client.rendu.conteneurs.LotT3bKit.*;
import static fr.heraliz.loot.client.rendu.mesh.MeshBuilder.p;

/**
 * Lot T3b — commissariats, casernes de pompiers, secours, chasseurs. Tout est abandonné et pillé depuis des mois :
 * peinture écaillée, rouille, boue, portes forcées, contenus renversés. Échelle réelle (1 px = 6,25 cm, joueur 1,8 m),
 * dos au mur (z = 0), rien à gauche (x >= 0) ; les objets longs s'étendent vers +X, les hauts vers +Y.
 */
public final class LotT3b {
    private LotT3b() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t3_casier_police", "t3", "metal", "Casier de commissariat", "Police station locker", LotT3b::casierPolice);
        Conteneurs.ajouter("t3_bureau_police", "t3", "metal", "Bureau de police", "Police desk", LotT3b::bureauPolice);
        Conteneurs.ajouter("t3_cage_scelles", "t3", "metal", "Armoire à scellés", "Evidence cage", LotT3b::cageScelles);
        Conteneurs.ajouter("t3_boucliers", "t3", "plastique", "Équipement anti-émeute", "Riot gear", LotT3b::boucliers);
        Conteneurs.verre("t3_boucliers", LotT3b::boucliersVerre);
        Conteneurs.ajouter("t3_sac_secours", "t3", "tissu", "Sac de secours", "Rescue bag", LotT3b::sacSecours);
        Conteneurs.ajouter("t3_malle_pompier", "t3", "metal", "Malle de pompier", "Firefighter trunk", LotT3b::mallePompier);
        Conteneurs.ajouter("t3_extincteurs", "t3", "metal", "Extincteurs", "Fire extinguishers", LotT3b::extincteurs);
        Conteneurs.ajouter("t3_brancard", "t3", "tissu", "Brancard", "Stretcher", LotT3b::brancard);
        Conteneurs.ajouter("t3_coffre_pickup", "t3", "metal", "Coffre de pick-up", "Pickup truck toolbox", LotT3b::coffrePickup);
        Conteneurs.ajouter("t3_glaciere_gibier", "t3", "plastique", "Glacière de chasse", "Hunting cooler", LotT3b::glaciereGibier);
        Conteneurs.ajouter("t3_ratelier_chasse", "t3", "bois", "Râtelier de chasse", "Hunting gun rack", LotT3b::ratelierChasse);
        Conteneurs.ajouter("t3_caisse_pieges", "t3", "bois", "Caisse de pièges", "Trap crate", LotT3b::caissePieges);
        Conteneurs.ajouter("t3_caisse_cartouches", "t3", "bois", "Caisse de cartouches", "Shotgun shell crate", LotT3b::caisseCartouches);
        Conteneurs.ajouter("t3_sacs_appats", "t3", "tissu", "Sacs d'appâts", "Bait bags", LotT3b::sacsAppats);
        Conteneurs.ajouter("t3_trousse_soins", "t3", "tissu", "Trousse de soins", "First aid kit", LotT3b::trousseSoins);
        Conteneurs.ajouter("t3_sac_tactique", "t3", "tissu", "Sac tactique", "Tactical backpack", LotT3b::sacTactique);
        Conteneurs.ajouter("t3_caisse_douane", "t3", "bois", "Caisse saisie en douane", "Seized customs crate", LotT3b::caisseDouane);
        Conteneurs.ajouter("t3_jerricans_essence", "t3", "metal", "Jerricans d'essence", "Fuel jerry cans", LotT3b::jerricansEssence);
        Conteneurs.ajouter("t3_caisse_securite_civile", "t3", "plastique", "Caisse de la sécurité civile", "Civil defence case", LotT3b::caisseSecuriteCivile);
        Conteneurs.ajouter("t3_mirador_coffre", "t3", "plastique", "Coffre de mirador", "Hunting stand chest", LotT3b::miradorCoffre);
        Conteneurs.collision("t3_mirador_coffre", new float[]{3.0f, 0, 5.2f, 13.0f, 6.2f, 12.0f}, new float[]{3.2f, 0, 0.2f, 12.8f, 30.0f, 5.4f});
    }

    // =================================================================== 1. casier de commissariat

    /** Porte de casier (charnières à droite), face arrière en z, face avant en z + 0,28. */
    static void porteCasier(MeshBuilder b, float xa, float xb, float ya, float yh, float z, boolean gauche) {
        float zf = z + 0.28f, cx = (xa + xb) / 2, w = xb - xa;
        b.bevelBox(xa, ya, z, xb, yh, zf, 0.1f, TOLE_GRISE);
        decal(b, PERSIENNES, 'z', cx, yh - 2.3f, zf, w * 0.64f, 3.0f);
        decal(b, PERSIENNES, 'z', cx, ya + 2.3f, zf, w * 0.64f, 3.0f);
        decal(b, NUMEROS.sub(gauche ? 0 : 0.5f, 0, gauche ? 0.5f : 1, 1), 'z', cx, yh - 4.9f, zf, 1.5f, 0.75f);
        coulure(b, 'z', cx - 0.9f, yh - 4.1f, zf, 0.9f, gauche ? 0 : 2);
        coulure(b, 'z', xa + 0.68f, 12.4f, zf, 0.7f, 1);
        // porte-étiquette
        b.bevelBox(cx - 1.5f, 17.3f, zf - 0.02f, cx + 1.5f, 18.25f, zf + 0.08f, 0.03f, CHROME_PIQUE);
        decal(b, gauche ? PLAQUE_NOM : ETIQUETTE_DECHIREE, 'z', cx, 17.78f, zf + 0.08f, 2.7f, 0.72f);
        // poignée à levier et moraillon, côté opposé aux charnières
        b.bevelBox(xa + 0.45f, 12.6f, zf - 0.02f, xa + 0.9f, 15.4f, zf + 0.12f, 0.04f, METAL_NU);
        b.bevelBox(xa + 0.5f, 13.1f, zf + 0.1f, xa + 0.85f, 15.0f, zf + 0.5f, 0.1f, CHROME_PIQUE);
        // charnières
        for (float y : new float[]{ya + 1.6f, (ya + yh) / 2 - 0.6f, yh - 2.8f}) gond(b, xb + 0.06f, y, z + 0.14f, 1.2f, METAL_NU);
        boue(b, 'z', cx, ya, zf, w - 0.5f, 2.4f, gauche ? 0 : 1);
    }

    /**
     * Double vestiaire en tôle (2 x 39 cm, 48 cm de fond, 1,8 m) dos au mur. Porte de gauche cadenassée ; celle de
     * droite forcée (moraillon tordu, griffures) et grande ouverte : veste d'uniforme sur son cintre, casquette sur
     * l'étagère, un brodequin au fond, l'autre tombé devant. Carton d'archives sur le dessus, impacts sur le flanc.
     */
    static void casierPolice(MeshBuilder b) {
        float x0 = 1.8f, xm = 8.0f, x1 = 14.2f, z0 = 0.3f, z1 = 8.0f, yb = 1.1f, h = 28.8f, bv = 0.2f;
        // socle en retrait, sombre
        b.color(0.5f);
        b.bevelBox(x0 + 0.4f, 0, z0 + 0.5f, x1 - 0.4f, yb + 0.05f, z1 - 0.7f, 0.1f, TOLE_GRISE);
        b.color(1);
        // deux caissons : celui de droite ouvert (sans face avant), intérieur sale
        b.bevelBox(x0, yb, z0, xm, h, z1, bv, TOLE_GRISE);
        b.bevelBox(xm, yb, z0, x1, h, z1, bv, TOLE_GRISE, null, MeshBuilder.SKIP_PZ);
        float ix0 = xm + bv, ix1 = x1 - bv, iy0 = yb + bv, iy1 = h - bv;
        b.color(0.84f, 0.84f, 0.82f);
        interieur(b, ix0, iy0, z0 + 0.4f, ix1, iy1, z1, TOLE_GRISE, INTERIEUR_SALE);
        // étagère à chapeau et tringle
        b.bevelBox(ix0 - 0.05f, 22.4f, z0 + 0.35f, ix1 + 0.05f, 22.62f, z1 - 0.5f, 0.05f, TOLE_GRISE);
        b.bevelBox(ix0 - 0.05f, 22.05f, z1 - 0.78f, ix1 + 0.05f, 22.7f, z1 - 0.44f, 0.05f, TOLE_GRISE);
        b.color(1);
        float jx = (xm + x1) / 2, jy = 21.3f, jz = 4.0f;
        b.push().translate(ix0 - 0.05f, jy, jz).rotateZ(-90);
        b.cylinder(0.14f, 0, ix1 - ix0 + 0.1f, 8, CHROME_PIQUE, null, null);
        b.pop();
        // cintre et veste d'uniforme vue de face
        b.tube(new float[][]{p(jx - 2.3f, jy - 1.2f, jz), p(jx, jy - 0.35f, jz), p(jx + 2.3f, jy - 1.2f, jz)}, rayons(3, 0.09f), 6, AtlasCivil.WALNUT, true, true, 0, false);
        b.tube(new float[][]{p(jx, jy - 0.35f, jz), p(jx, jy + 0.2f, jz + 0.05f), p(jx, jy + 0.3f, jz - 0.22f), p(jx, jy + 0.08f, jz - 0.32f)}, rayons(4, 0.06f), 5, METAL_NU, false, true, 0, false);
        b.push().translate(jx, jy - 1.0f, jz);
        float[][] veste = {{-2.45f, -10.6f}, {2.45f, -10.6f}, {2.65f, -1.3f}, {2.35f, -0.25f}, {0.75f, 0.1f}, {-0.75f, 0.1f}, {-2.35f, -0.25f}, {-2.65f, -1.3f}};
        b.slab(veste, 1.9f, 0.55f, true, SERGE_MARINE, SERGE_MARINE, SERGE_MARINE);
        for (int s = -1; s <= 1; s += 2) {
            b.tube(new float[][]{p(s * 2.45f, -1.0f, 0.2f), p(s * 2.6f, -4.5f, 0.55f), p(s * 2.35f, -8.65f, 0.7f)},
                    new float[]{0.62f, 0.56f, 0.5f}, 9, SERGE_MARINE, false, false, 0, false);
            b.color(0.6f);
            b.tube(new float[][]{p(s * 2.33f, -8.6f, 0.7f), p(s * 2.3f, -9.25f, 0.72f)}, rayons(2, 0.53f), 9, SERGE_MARINE, false, true, 0, false);
            b.color(1);
            // épaulette
            b.push().translate(s * 1.6f, -0.45f, 0).rotateZ(s * 14);
            b.bevelBox(-0.75f, -0.12f, -0.45f, 0.75f, 0.12f, 0.45f, 0.06f, SERGE_MARINE);
            b.pop();
        }
        b.tube(new float[][]{p(-0.95f, -0.15f, 0.55f), p(0, -1.0f, 1.12f), p(0.95f, -0.15f, 0.55f)}, rayons(3, 0.22f), 6, SERGE_MARINE, true, true, 0, false);
        b.bevelBox(-0.07f, -10.4f, 0.9f, 0.07f, -1.1f, 1.02f, 0.02f, NOIR_MAT);
        decal(b, ECUSSON, 'z', -1.35f, -2.9f, 0.95f, 1.2f, 1.2f);
        decal(b, S_POLICE, 'z', 1.35f, -3.0f, 0.95f, 1.7f, 0.42f);
        b.pop();
        // casquette sur l'étagère
        b.push().translate(jx + 0.7f, 22.62f, 3.4f).rotateY(-22).scale(1, 1, 1.12f);
        b.lathe(new float[][]{{1.38f, 0.02f}, {1.44f, 0.55f}, {1.78f, 1.12f}, {1.84f, 1.28f}, {1.2f, 1.36f}, {0.01f, 1.38f}}, 16, 0, 360, SERGE_MARINE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.41f, 0.04f}, {1.47f, 0.52f}}, 16, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
        b.push().translate(0, 0.28f, 1.7f).rotateX(16).scale(1, 0.18f, 1);
        b.sphereSection(1.25f, 12, 4, 0, 90, NOIR_MAT);
        b.pop();
        b.push().translate(0, 0.42f, 1.5f).rotateX(90);
        b.cylinder(0.26f, 0, 0.1f, 8, CHROME_PIQUE, null, CHROME_PIQUE);
        b.pop();
        b.pop();
        // brodequin resté au fond, l'autre tombé devant
        b.push().translate(xm + 1.6f, iy0, z0 + 1.2f).rotateY(8);
        ranger(b);
        b.pop();
        b.push().translate(10.4f, 0.82f, 11.6f).rotateY(-75).rotateZ(90);
        ranger(b);
        b.pop();
        // portes : gauche fermée et cadenassée, droite forcée et ouverte
        porteCasier(b, x0 + 0.32f, xm - 0.32f, yb + 0.35f, h - 0.35f, z1 - 0.08f, true);
        cadenas(b, x0 + 1.05f, 16.3f, z1 + 0.26f, false);
        b.bevelBox(x0 + 0.42f, 15.6f, z1 + 0.18f, x0 + 1.6f, 16.5f, z1 + 0.28f, 0.03f, METAL_NU);
        decal(b, GRIFFURES, 'z', x0 + 3.9f, 14.6f, z1 + 0.2f, 2.0f, 1.0f, 70);
        float hx = x1 - 0.32f, hz = z1 + 0.06f;
        b.push().translate(hx, 0, hz).rotateY(108).translate(-hx, 0, -hz);
        porteCasier(b, xm + 0.32f, x1 - 0.32f, yb + 0.35f, h - 0.35f, z1 - 0.08f, false);
        decal(b, GRIFFURES, 'z', xm + 3.2f, 16.9f, z1 + 0.2f, 2.0f, 1.0f, 95);
        // moraillon tordu
        b.push().translate(xm + 0.6f, 16.2f, z1 + 0.25f).rotateZ(35).rotateY(-25);
        b.bevelBox(-0.3f, -0.45f, 0, 0.9f, 0.45f, 0.1f, 0.03f, METAL_NU);
        b.pop();
        // intérieur de porte : avis de recherche scotché
        b.color(0.85f);
        decal(b, AVIS_RECHERCHE, 'Z', (xm + x1) / 2, 20.0f, z1 - 0.08f, 3.6f, 3.6f, 3);
        b.color(1);
        b.box((xm + x1) / 2 - 1.6f, 21.5f, z1 - 0.32f, (xm + x1) / 2 - 0.9f, 21.9f, z1 - 0.26f, SCOTCH);
        b.box((xm + x1) / 2 + 0.9f, 21.6f, z1 - 0.32f, (xm + x1) / 2 + 1.6f, 22.0f, z1 - 0.26f, SCOTCH);
        b.pop();
        // carton d'archives sur le dessus
        b.push().translate(5.0f, h, 4.0f).rotateY(-7);
        b.bevelBox(-2.3f, 0.01f, -2.6f, 2.3f, 2.5f, 2.6f, 0.12f, CARTON_MOUILLE);
        decal(b, ETIQUETTE_DECHIREE, 'z', -0.4f, 1.3f, 2.6f, 2.6f, 1.2f);
        scotchY(b, -0.4f, -2.62f, -0.4f, 2.62f, 2.5f, 0.7f, SCOTCH_BRUN);
        b.pop();
        // flancs : impacts, coulures, boue, auréole
        decal(b, IMPACTS, 'x', x0, 17.5f, 4.6f, 4.4f, 2.2f, -8);
        decal(b, AUREOLE, 'x', x0, 8.0f, 3.2f, 3.4f, 3.4f);
        coulure(b, 'x', x0, h - 0.5f, 6.4f, 1.6f, 0);
        coulure(b, 'X', x1, h - 0.5f, 2.6f, 1.8f, 1);
        coulure(b, 'X', x1, 15.0f, 6.0f, 1.2f, 2);
        boue(b, 'x', x0, yb, 4.2f, 7.2f, 2.6f, 1);
        boue(b, 'X', x1, yb, 4.2f, 7.2f, 2.2f, 0);
    }

    // =================================================================== 2. bureau de police

    /** Téléphone de bureau beige (22 x 20 cm), combiné décroché posé à côté, cordon en spirale. */
    static void telephone(MeshBuilder b) {
        b.color(0.86f, 0.8f, 0.66f);
        b.bevelBox(-1.75f, 0, -1.6f, 1.75f, 0.75f, 1.6f, 0.28f, PLASTIQUE_USE);
        b.push().translate(0, 0.7f, -0.1f).rotateX(14);
        b.bevelBox(-1.6f, 0, -1.4f, 1.6f, 0.55f, 1.25f, 0.22f, PLASTIQUE_USE);
        b.pop();
        // fourche du combiné (vide)
        for (float x : new float[]{-1.3f, 1.3f}) b.bevelBox(x - 0.32f, 1.1f, -1.55f, x + 0.32f, 1.75f, -0.75f, 0.14f, PLASTIQUE_USE);
        b.color(1);
        b.push().translate(0, 1.25f, 0.0f).rotateX(14);
        decal(b, CLAVIER_TEL, 'y', 0.15f, 0.02f, 0.25f, 1.7f, 1.7f);
        b.pop();
        // combiné couché à côté
        b.color(0.86f, 0.8f, 0.66f);
        b.push().translate(3.1f, 0.4f, 0.6f).rotateY(-70);
        b.bevelBox(-1.9f, -0.3f, -0.26f, 1.9f, 0.18f, 0.26f, 0.15f, PLASTIQUE_USE);
        for (int s = -1; s <= 1; s += 2) b.bevelBox(s * 1.9f - 0.65f, -0.36f, -0.5f, s * 1.9f + 0.65f, 0.36f, 0.5f, 0.22f, PLASTIQUE_USE);
        b.pop();
        b.color(1);
        // cordon en spirale
        float[][] pts = new float[26][];
        float ax = 1.75f, az = 0.6f, bx = 2.6f, bz = 2.3f;
        for (int k = 0; k < pts.length; k++) {
            float t = k / (float) (pts.length - 1);
            double a = t * Math.PI * 2 * 6;
            pts[k] = p(ax + (bx - ax) * t + (float) Math.cos(a) * 0.14f, 0.22f + (float) Math.sin(a) * 0.14f + (float) Math.sin(t * Math.PI) * 0.3f, az + (bz - az) * t);
        }
        b.tube(pts, rayons(pts.length, 0.05f), 3, NOIR_MAT, false, false, 0, false);
    }

    /** Tampon encreur (poignée de bois) et sa boîte d'encre ouverte. */
    static void tampon(MeshBuilder b) {
        b.bevelBox(-1.0f, 0, -0.7f, 1.0f, 0.28f, 0.7f, 0.1f, NOIR_MAT);
        b.color(0.35f, 0.42f, 0.7f);
        b.box(-0.82f, 0.2f, -0.52f, 0.82f, 0.31f, 0.52f, AtlasCivil.INNER);
        b.color(1);
        b.push().translate(1.9f, 0, 0.3f).rotateY(25);
        b.bevelBox(-0.62f, 0, -0.42f, 0.62f, 0.32f, 0.42f, 0.06f, CAOUTCHOUC_USE);
        b.bevelBox(-0.66f, 0.3f, -0.46f, 0.66f, 0.5f, 0.46f, 0.06f, AtlasCivil.WALNUT);
        b.translate(0, 0.48f, 0);
        b.lathe(new float[][]{{0.22f, 0}, {0.16f, 0.5f}, {0.15f, 1.1f}, {0.32f, 1.5f}, {0.3f, 1.8f}, {0.01f, 1.9f}}, 10, 0, 360, AtlasCivil.WALNUT, 1, 1, 0, 0, 0, false);
        b.pop();
    }

    /** Mug de café sec (8 cm). */
    static void mug(MeshBuilder b) {
        b.lathe(new float[][]{{0.55f, 0}, {0.66f, 0.06f}, {0.68f, 1.45f}, {0.6f, 1.5f}}, 14, 0, 360, AtlasCivil.WHITE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.6f, 1.48f}, {0.58f, 0.2f}}, 14, 0, 360, AtlasCivil.WHITE, 1, 1, 0, 0, 0, true);
        b.push().translate(0, 0.35f, 0);
        b.color(0.4f, 0.28f, 0.18f);
        b.disc(0.58f, 0, 14, INTERIEUR_SALE, true);
        b.color(1);
        b.pop();
        b.push().translate(0.68f, 0.75f, 0).rotateX(90);
        b.torus(0.35f, 0.08f, 10, 4, -90, 90, AtlasCivil.WHITE);
        b.pop();
    }

    /** Menottes ouvertes (acier piqué). */
    static void menottes(MeshBuilder b) {
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 0.75f, 0.06f, 0);
            b.torus(0.48f, 0.07f, 12, 4, 0, 300, CHROME_PIQUE);
            b.pop();
        }
        b.tube(new float[][]{p(-0.25f, 0.06f, 0), p(0, 0.08f, 0.1f), p(0.25f, 0.06f, 0)}, rayons(3, 0.06f), 4, CHROME_PIQUE, false, false, 0, false);
    }

    /**
     * Bureau métallique de commissariat (1,4 x 0,7 x 0,75 m) sur deux blocs : plateau en linoléum, caisson de trois
     * tiroirs (celui du haut arraché à moitié : papiers et menottes), voile de fond criblé d'impacts. Dessus : pile de
     * dossiers, téléphone décroché, tampon, procès-verbal, mug. Papiers piétinés au sol.
     */
    static void bureauPolice(MeshBuilder b) {
        float x0 = 4.6f, x1 = 27.4f, z0 = 0.4f, z1 = 11.0f, ht = 12.0f, ep = 0.7f, xp = x1 - 7.2f, yt = ht + 0.08f;
        b.bevelBox(x0 - 0.2f, ht - ep, z0, x1 + 0.2f, ht, z1 + 0.35f, 0.15f, TOLE_GRISE);
        b.bevelBox(x0 + 0.1f, ht - 0.02f, z0 + 0.3f, x1 - 0.1f, yt, z1 + 0.05f, 0.04f, LINO);
        // piétement : joue gauche, caisson à droite, voile de fond
        b.color(0.6f);
        b.bevelBox(x0 - 0.1f, 0, z0 + 0.2f, x0 + 0.75f, 0.45f, z1 + 0.1f, 0.1f, TOLE_GRISE);
        b.bevelBox(xp + 0.25f, 0, z0 + 0.5f, x1 - 0.25f, 0.55f, z1 - 0.3f, 0.1f, TOLE_GRISE);
        b.color(1);
        b.bevelBox(x0, 0.42f, z0 + 0.3f, x0 + 0.62f, ht - ep + 0.02f, z1, 0.1f, TOLE_GRISE);
        b.bevelBox(xp, 0.52f, z0 + 0.3f, x1, ht - ep + 0.02f, z1, 0.12f, TOLE_GRISE);
        b.bevelBox(x0 + 0.55f, 4.2f, z0 + 0.45f, xp + 0.05f, ht - ep + 0.02f, z0 + 0.75f, 0.08f, TOLE_GRISE);
        decal(b, IMPACTS, 'z', 12.6f, 8.0f, z0 + 0.75f, 5.0f, 2.5f, 6);
        decal(b, GRIFFURES, 'x', x0, 6.0f, 6.5f, 3.6f, 1.8f, -12);
        // tiroirs du caisson
        float cx = (xp + x1) / 2;
        float[][] tiroirs = {{0.95f, 4.9f}, {5.1f, 7.85f}, {8.05f, 10.95f}};
        for (int k = 0; k < 3; k++) {
            float ya = tiroirs[k][0], yh = tiroirs[k][1], dz = k == 2 ? 4.6f : (k == 1 ? 0.35f : 0);
            if (dz > 1) {
                b.box(xp + 0.3f, ya + 0.05f, z1 - 0.02f, x1 - 0.3f, yh - 0.05f, z1 + 0.04f, INTERIEUR_SALE);
                b.bevelBox(xp + 0.55f, ya + 0.25f, z1 - 1.5f, xp + 0.75f, yh - 0.6f, z1 + dz, 0.05f, TOLE_GRISE);
                b.bevelBox(x1 - 0.75f, ya + 0.25f, z1 - 1.5f, x1 - 0.55f, yh - 0.6f, z1 + dz, 0.05f, TOLE_GRISE);
                b.box(xp + 0.62f, ya + 0.2f, z1 - 1.5f, x1 - 0.62f, ya + 0.35f, z1 + dz, INTERIEUR_SALE);
                b.box(xp + 0.85f, ya + 0.36f, z1 + 0.4f, x1 - 0.85f, ya + 0.9f, z1 + dz - 0.5f, PAPIER);
                b.push().translate(cx - 0.6f, ya + 0.9f, z1 + 2.4f).rotateY(30);
                menottes(b);
                b.pop();
                b.push().translate(cx + 1.4f, ya + 0.9f, z1 + 1.4f).rotateY(-12).rotateZ(-4).scale(0.55f, 1, 0.62f);
                dossier(b, 0.88f, 0.55f, 0.52f);
                b.pop();
            }
            float zf = z1 + dz;
            b.bevelBox(xp + 0.25f, ya, zf - 0.05f, x1 - 0.25f, yh, zf + 0.3f, 0.1f, TOLE_GRISE);
            float yp = yh - 0.75f;
            b.tube(new float[][]{p(cx - 1.4f, yp, zf + 0.28f), p(cx - 1.4f, yp, zf + 0.75f), p(cx + 1.4f, yp, zf + 0.75f), p(cx + 1.4f, yp, zf + 0.28f)},
                    rayons(4, 0.12f), 6, CHROME_PIQUE, false, false, 0, false);
            b.bevelBox(cx - 0.9f, ya + 0.35f, zf + 0.28f, cx + 0.9f, ya + 0.95f, zf + 0.36f, 0.03f, CHROME_PIQUE);
            decal(b, ETIQUETTE_DECHIREE, 'z', cx, ya + 0.65f, zf + 0.36f, 1.6f, 0.5f);
            coulure(b, 'z', cx + 1.4f, yp - 0.2f, zf + 0.3f, 0.7f, k);
        }
        // tiroir plumier sous le plateau, à peine sorti
        b.bevelBox(x0 + 0.9f, ht - ep - 1.25f, z1 - 0.4f + 1.1f, xp - 0.4f, ht - ep - 0.05f, z1 - 0.1f + 1.1f, 0.08f, TOLE_GRISE);
        b.box(x0 + 1.0f, ht - ep - 1.1f, z1 - 0.06f, xp - 0.5f, ht - ep - 0.1f, z1 - 0.02f, INTERIEUR_SALE);
        b.bevelBox(x0 + 1.2f, ht - ep - 1.2f, z1 - 1.5f, xp - 0.7f, ht - ep - 1.05f, z1 + 0.75f, 0.03f, TOLE_GRISE);
        // dessus : dossiers, téléphone, tampon, procès-verbal, mug
        float[][] dos = {{0.86f, 0.74f, 0.5f}, {0.6f, 0.76f, 0.58f}, {0.88f, 0.6f, 0.58f}, {0.58f, 0.66f, 0.86f}, {0.86f, 0.74f, 0.5f}};
        for (int k = 0; k < dos.length; k++) {
            b.push().translate(14.6f + (k % 2) * 0.3f, yt + 0.05f + k * 0.18f, 4.6f + (k % 3) * 0.2f).rotateY(-6 + k * 5.5f);
            dossier(b, dos[k][0], dos[k][1], dos[k][2]);
            b.pop();
        }
        b.push().translate(15.4f, yt + 0.05f + 5 * 0.18f + 0.01f, 4.9f).rotateY(-34);
        dossier(b, 0.58f, 0.66f, 0.86f);
        b.pop();
        b.push().translate(7.0f, yt, 5.2f).rotateY(18);
        telephone(b);
        b.pop();
        decal(b, PV, 'y', 20.6f, yt, 7.6f, 3.4f, 4.4f, 14);
        b.push().translate(22.4f, yt, 3.6f).rotateY(-15);
        tampon(b);
        b.pop();
        b.push().translate(25.4f, yt, 8.6f);
        mug(b);
        b.pop();
        decal(b, AUREOLE, 'y', 24.0f, yt, 9.4f, 1.6f, 1.6f);
        // au sol : papiers, dossier ouvert tombé, PV piétiné
        jonchee(b, 13.0f, 11.6f, 21.0f, 14.6f, 7, PAPIER, 1.8f, 31);
        b.push().translate(9.4f, 0.01f, 13.4f).rotateY(40);
        b.color(0.86f, 0.74f, 0.5f);
        b.bevelBox(-2.55f, 0, -1.9f, 0, 0.08f, 1.9f, 0.03f, CHEMISE);
        b.push().rotateZ(8);
        b.bevelBox(0.05f, 0.05f, -1.9f, 2.6f, 0.13f, 1.9f, 0.03f, CHEMISE);
        b.pop();
        b.color(1);
        b.pop();
        decal(b, PV, 'y', 5.0f, 0, 13.4f, 3.0f, 3.8f, -28);
        decal(b, BOUE_1, 'y', 24.6f, 0, 12.8f, 3.0f, 1.6f, -20);
        boue(b, 'z', cx, 0.55f, z1 + 0.3f, 6.4f, 0.7f, 1);
        boue(b, 'x', x0 - 0.1f, 0.45f, 6.0f, 9.0f, 2.0f, 0);
        coulure(b, 'X', x1, ht - ep - 0.3f, 4.0f, 1.4f, 2);
    }

    // =================================================================== 3. armoire à scellés

    /** Porte grillagée (cadre en tube carré) dans le plan z, de xa à xb, ya à yh. */
    static void porteGrille(MeshBuilder b, float xa, float xb, float ya, float yh, float z) {
        float t = 0.42f, e = 0.32f;
        b.bevelBox(xa, ya, z, xa + t, yh, z + e, 0.08f, TOLE_GRISE);
        b.bevelBox(xb - t, ya, z, xb, yh, z + e, 0.08f, TOLE_GRISE);
        b.bevelBox(xa + t - 0.04f, ya, z, xb - t + 0.04f, ya + t, z + e, 0.08f, TOLE_GRISE);
        b.bevelBox(xa + t - 0.04f, yh - t, z, xb - t + 0.04f, yh, z + e, 0.08f, TOLE_GRISE);
        b.bevelBox(xa + t - 0.04f, (ya + yh) / 2 - 0.2f, z + 0.04f, xb - t + 0.04f, (ya + yh) / 2 + 0.2f, z + e - 0.04f, 0.06f, TOLE_GRISE);
        float zm = z + e / 2;
        grillageRect(b, false, zm, xa + t, xb - t, ya + t, yh - t, 1);
    }

    /**
     * Armoire grillagée de pièces saisies (90 x 50 x 180 cm) : ossature de cornières, côtés et portes en grillage soudé,
     * trois étagères de sachets étiquetés et de cartons de scellés. Cadenas forcé pendu au moraillon, porte de droite
     * entrebâillée, sachets tombés devant.
     */
    static void cageScelles(MeshBuilder b) {
        float x0 = 1.2f, x1 = 14.8f, z0 = 0.3f, z1 = 8.3f, h = 28.6f, t = 0.6f, xm = (x0 + x1) / 2;
        for (float[] c : new float[][]{{x0, z0}, {x1 - t, z0}, {x0, z1 - t}, {x1 - t, z1 - t}}) b.bevelBox(c[0], 0, c[1], c[0] + t, h, c[1] + t, 0.1f, TOLE_GRISE);
        for (float y : new float[]{0.35f, h - 0.75f}) {
            b.bevelBox(x0 + t - 0.05f, y, z0 + 0.05f, x1 - t + 0.05f, y + 0.6f, z0 + 0.45f, 0.08f, TOLE_GRISE);
            b.bevelBox(x0 + t - 0.05f, y, z1 - 0.45f, x1 - t + 0.05f, y + 0.6f, z1 - 0.05f, 0.08f, TOLE_GRISE);
            b.bevelBox(x0 + 0.05f, y, z0 + t - 0.05f, x0 + 0.45f, y + 0.6f, z1 - t + 0.05f, 0.08f, TOLE_GRISE);
            b.bevelBox(x1 - 0.45f, y, z0 + t - 0.05f, x1 - 0.05f, y + 0.6f, z1 - t + 0.05f, 0.08f, TOLE_GRISE);
        }
        b.bevelBox(x0 - 0.1f, h - 0.02f, z0 - 0.05f, x1 + 0.1f, h + 0.3f, z1 + 0.1f, 0.12f, TOLE_GRISE);
        b.box(x0 + t, 0.3f, z0 + t, x1 - t, 0.62f, z1 - t, TOLE_GRISE);
        // fond plein (sombre), côtés grillagés
        b.color(0.7f);
        b.box(x0 + t - 0.02f, 0.95f, z0 + 0.2f, x1 - t + 0.02f, h - 0.75f, z0 + 0.32f, TOLE_GRISE);
        b.color(1);
        for (float x : new float[]{x0 + 0.25f, x1 - 0.25f}) {
            grillageRect(b, true, x, z0 + t, z1 - t, 0.95f, h - 0.75f, x < xm ? -1 : 1);
        }
        // étagères et leur contenu
        float[] et = {8.2f, 15.4f, 22.2f};
        for (float y : et) {
            b.bevelBox(x0 + t - 0.05f, y - 0.22f, z0 + 0.3f, x1 - t + 0.05f, y, z1 - 0.7f, 0.05f, TOLE_GRISE);
            b.bevelBox(x0 + t - 0.05f, y - 0.7f, z1 - 0.95f, x1 - t + 0.05f, y + 0.07f, z1 - 0.64f, 0.05f, TOLE_GRISE);
        }
        // bas : fusil emballé couché, carton de scellés
        b.push().translate(7.2f, 0.62f, 2.6f).rotateY(4);
        b.bevelBox(-5.6f, 0, -0.85f, 5.6f, 1.1f, 0.85f, 0.4f, SACHET);
        decal(b, ETIQ_SCELLE, 'z', -2.4f, 0.55f, 0.85f, 1.8f, 0.9f);
        b.color(0.75f, 0.22f, 0.18f);
        b.bevelBox(3.6f, -0.04f, -0.9f, 3.9f, 1.14f, 0.9f, 0.03f, PLASTIQUE_USE);
        b.color(1);
        b.pop();
        b.push().translate(5.4f, 1.74f, 3.2f).rotateY(-8);
        b.bevelBox(-2.6f, 0, -1.9f, 2.6f, 3.4f, 1.9f, 0.12f, CARTON_MOUILLE);
        decal(b, ETIQ_SCELLE, 'z', 0.4f, 1.8f, 1.9f, 2.4f, 1.2f);
        scotchArete(b, -0.6f, 3.4f, 1.9f, 1.4f, 1.2f, 0.7f, SCOTCH_BRUN);
        b.pop();
        // étagère 1 : sachets debout
        float[][] s1 = {{3.4f, 2.2f, 3.0f, -6}, {5.9f, 2.0f, 2.6f, 4}, {8.4f, 2.4f, 3.2f, -3}, {11.6f, 2.2f, 2.8f, 9}};
        for (float[] q : s1) {
            b.push().translate(q[0], et[0], 3.6f).rotateY(q[3]);
            sachet(b, q[1], q[2], 0.7f, true);
            b.pop();
        }
        // étagère 2 : pile de sachets plats, petit carton
        for (int k = 0; k < 4; k++) {
            b.push().translate(4.6f + (k % 2) * 0.3f, et[1] + 0.35f + k * 0.62f, 3.8f).rotateY(-8 + k * 7).rotateX(-90).translate(0, -1.6f, 0);
            sachet(b, 3.0f, 3.2f, 0.6f, false);
            b.pop();
        }
        b.push().translate(10.6f, et[1], 3.2f).rotateY(6);
        b.bevelBox(-2.0f, 0, -1.6f, 2.0f, 2.6f, 1.6f, 0.1f, CARTON_MOUILLE);
        decal(b, ETIQ_SCELLE, 'z', 0, 1.3f, 1.6f, 2.2f, 1.1f, 3);
        b.pop();
        // étagère 3 : carton, sachet
        b.push().translate(5.0f, et[2], 3.4f).rotateY(-4);
        b.bevelBox(-2.8f, 0, -2.0f, 2.8f, 3.6f, 2.0f, 0.12f, CARTON_MOUILLE);
        decal(b, ETIQUETTE_DECHIREE, 'z', 0, 2.0f, 2.0f, 3.0f, 1.4f);
        b.pop();
        b.push().translate(10.8f, et[2], 3.6f).rotateY(9);
        sachet(b, 2.6f, 3.0f, 0.8f, true);
        b.pop();
        // portes : gauche fermée (cadenas forcé pendu), droite entrebâillée
        float zp = z1 - 0.02f;
        porteGrille(b, x0 + t + 0.06f, xm - 0.06f, 1.0f, h - 0.85f, zp);
        b.bevelBox(xm - 1.1f, 14.0f, zp + 0.3f, xm - 0.2f, 15.4f, zp + 0.42f, 0.04f, METAL_NU);
        b.push().translate(xm - 0.65f, 13.9f, zp + 0.42f).rotateZ(18);
        cadenas(b, 0, 0, 0, true);
        b.pop();
        decal(b, GRIFFURES, 'z', xm - 2.4f, 16.8f, zp + 0.32f, 2.2f, 1.0f, 15);
        float hx = x1 - t - 0.06f;
        b.push().translate(hx, 0, zp).rotateY(34).translate(-hx, 0, -zp);
        porteGrille(b, xm + 0.06f, x1 - t - 0.06f, 1.0f, h - 0.85f, zp);
        b.bevelBox(xm + 0.2f, 14.0f, zp + 0.3f, xm + 0.9f, 15.4f, zp + 0.42f, 0.04f, METAL_NU);
        b.pop();
        for (float y : new float[]{3.0f, 14.0f, 24.5f}) {
            gond(b, x1 - t + 0.1f, y, zp + 0.18f, 1.2f, METAL_NU);
            gond(b, x0 + t - 0.1f, y, zp + 0.18f, 1.2f, METAL_NU);
        }
        // au sol : sachets tombés
        b.push().translate(4.6f, 0.38f, 11.0f).rotateY(28).rotateX(-90).translate(0, -1.4f, 0);
        sachet(b, 2.4f, 2.8f, 0.7f, true);
        b.pop();
        b.push().translate(9.4f, 0.34f, 12.2f).rotateY(-50).rotateX(-90).translate(0, -1.2f, 0);
        sachet(b, 2.0f, 2.4f, 0.6f, false);
        b.pop();
        // rouille, boue, avis collé sur le flanc
        for (float x : new float[]{x0 + t / 2, x1 - t / 2}) {
            coulure(b, 'z', x, h - 1.0f, z1, 0.6f, (int) x);
            coulure(b, 'z', x, 9.0f, z1, 0.5f, (int) x + 1);
        }
        boue(b, 'z', x0 + t / 2, 0, z1, 0.6f, 2.0f, 0);
        boue(b, 'z', x1 - t / 2, 0, z1, 0.6f, 2.0f, 1);
        coulure(b, 'x', x0, h - 0.4f, z0 + 0.3f, 0.5f, 2);
    }

    // =================================================================== 4. équipement anti-émeute

    static final float BW = 7.8f, BH = 14.4f, BC = 0.55f, BE = 0.09f;

    /** Bombé du bouclier (vers +Z) à l'abscisse x (repère du bouclier, x de -BW/2 à BW/2). */
    static float bombe(float x) {
        float u = 2 * x / BW;
        return BC * (1 - u * u);
    }

    /** Boucliers : {x du pied, inclinaison (degrés), rotation autour de Y (degrés)}. */
    static final float[][] BOUCLIERS = {{4.25f, 14.5f, 0}, {11.5f, 19.0f, -9}};

    /** Repère d'un bouclier : pied centré en x, penché en arrière, son point le plus en arrière à z = 0,05. */
    static void repereBouclier(MeshBuilder b, float[] q) {
        float a = q[1], r = q[2];
        float s = (float) Math.sin(Math.toRadians(a)), c = (float) Math.cos(Math.toRadians(a));
        float zMin = -BH * s - (BE + 0.16f) * c;
        float dx = (float) Math.abs(Math.sin(Math.toRadians(r))) * BW / 2;
        float z = -zMin + dx + 0.05f;
        b.translate(q[0], 0.2f * c + 0.2f * s, z).rotateY(r).rotateX(-a);
    }

    /** Parties opaques des boucliers (jonc de caoutchouc, poignée, brassard), casque et tonfa. */
    static void boucliers(MeshBuilder b) {
        for (float[] q : BOUCLIERS) {
            b.push();
            repereBouclier(b, q);
            int n = 13;
            java.util.List<float[]> bord = new java.util.ArrayList<>();
            for (int i = 0; i <= n; i++) {
                float x = -BW / 2 + BW * i / n;
                bord.add(p(x, 0, bombe(x) - BE / 2));
            }
            for (int i = n; i >= 0; i--) {
                float x = -BW / 2 + BW * i / n;
                bord.add(p(x, BH, bombe(x) - BE / 2));
            }
            bord.add(p(-BW / 2, 0, -BE / 2));
            float[][] chemin = bord.toArray(new float[0][]);
            b.tube(chemin, rayons(chemin.length, 0.16f), 5, CAOUTCHOUC_USE, false, false, 0, false);
            // poignée verticale et brassard au dos
            b.tube(new float[][]{p(-0.4f, 6.2f, BC - 0.15f), p(-0.4f, 6.2f, BC - 1.0f), p(-0.4f, 8.4f, BC - 1.0f), p(-0.4f, 8.4f, BC - 0.15f)}, rayons(4, 0.2f), 6, NOIR_MAT, false, false, 0, false);
            b.bevelBox(-1.6f, 10.0f, BC - 0.85f, 1.6f, 10.9f, BC - 0.25f, 0.12f, SANGLE_NOIRE);
            for (float x : new float[]{-1.4f, 1.4f}) b.bevelBox(x - 0.2f, 9.8f, BC - 0.4f, x + 0.2f, 11.1f, BC - 0.15f, 0.05f, METAL_NU);
            b.pop();
        }
        // casque anti-émeute posé au sol, visière relevée
        b.push().translate(11.6f, 2.15f, 10.6f).rotateY(-35).rotateX(-12);
        b.push().scale(1, 1.08f, 1.16f);
        b.sphereSection(1.95f, 18, 7, 0, 96, NOIR_MAT);
        b.pop();
        b.push().translate(0, -0.2f, 0).scale(1.0f, 1, 1.16f);
        b.torus(1.95f, 0.14f, 18, 4, 0, 360, CAOUTCHOUC_USE);
        b.pop();
        b.lathe(new float[][]{{1.95f, -0.15f}, {2.05f, -1.15f}, {2.1f, -1.6f}}, 9, 200, 340, CUIR_NOIR, 1, 1.16f, 0, 0, 0, false);
        for (int s = -1; s <= 1; s += 2) {
            b.push().translate(s * 1.95f, 0.4f, 0.2f).rotateZ(s * -90);
            b.cylinder(0.42f, 0, 0.22f, 10, NOIR_MAT, null, NOIR_MAT);
            b.pop();
        }
        b.tube(new float[][]{p(-1.8f, -0.2f, 0.6f), p(-1.2f, -1.6f, 1.2f), p(0, -1.9f, 1.4f), p(1.2f, -1.6f, 1.2f), p(1.8f, -0.2f, 0.6f)}, rayons(5, 0.09f), 4, SANGLE_NOIRE, false, false, 0, false);
        b.pop();
        // tonfa au sol
        b.push().translate(5.6f, 0.26f, 11.4f).rotateY(-12).rotateZ(-90);
        b.cylinder(0.24f, -4.6f, 4.6f, 10, NOIR_MAT, NOIR_MAT, NOIR_MAT);
        b.pop();
        double ang = Math.toRadians(12);
        b.push().translate(5.6f - 2.6f * (float) Math.cos(ang), 0.26f, 11.4f + 2.6f * (float) Math.sin(ang)).rotateY(-12 + 90).rotateZ(-90);
        b.cylinder(0.2f, 0.2f, 2.0f, 8, NOIR_MAT, null, NOIR_MAT);
        b.translate(0, 2.0f, 0);
        b.sphere(0.25f, 8, 5, NOIR_MAT);
        b.pop();
        decal(b, BOUE_2, 'y', 7.0f, 0, 6.0f, 6.0f, 2.4f, 4);
    }

    /** Polycarbonate des boucliers (fêlé sur le premier) et visière du casque : passe translucide. */
    static void boucliersVerre(MeshBuilder b) {
        int n = 10, m = 6;
        for (int s = 0; s < BOUCLIERS.length; s++) {
            b.push();
            repereBouclier(b, BOUCLIERS[s]);
            for (int face = 0; face < 2; face++) {
                float dz = face == 0 ? 0 : -BE;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < m; j++) {
                        float xa = -BW / 2 + BW * i / n, xb = -BW / 2 + BW * (i + 1) / n, ya = BH * j / m, yb = BH * (j + 1) / m;
                        float[][] pts = {p(xa, ya, bombe(xa) + dz), p(xb, ya, bombe(xb) + dz), p(xb, yb, bombe(xb) + dz), p(xa, yb, bombe(xa) + dz)};
                        float ua = (float) i / n, ub = (float) (i + 1) / n;
                        if (face == 1) {
                            ua = 1 - ua;
                            ub = 1 - ub;
                        }
                        float va = 1 - (float) j / m, vb = 1 - (float) (j + 1) / m;
                        float[][] uv = {{BOUCLIER.u(ua), BOUCLIER.v(va)}, {BOUCLIER.u(ub), BOUCLIER.v(va)}, {BOUCLIER.u(ub), BOUCLIER.v(vb)}, {BOUCLIER.u(ua), BOUCLIER.v(vb)}};
                        float na = 8 * BC * xa / (BW * BW), nb = 8 * BC * xb / (BW * BW);
                        float sg = face == 0 ? 1 : -1;
                        float[][] nr = {MeshBuilder.v(sg * na, 0, sg), MeshBuilder.v(sg * nb, 0, sg), MeshBuilder.v(sg * nb, 0, sg), MeshBuilder.v(sg * na, 0, sg)};
                        b.poly(pts, uv, nr, MeshBuilder.v(sg * (na + nb) / 2, 0, sg));
                    }
                }
            }
            if (s == 0) {
                b.push().translate(1.4f, 9.6f, bombe(1.4f)).rotateY((float) Math.toDegrees(Math.atan(8 * BC * 1.4f / (BW * BW))) * -1);
                decal(b, VERRE_BRISE, 'z', 0, 0, 0.02f, 2.4f, 2.4f, 20);
                b.pop();
            }
            b.pop();
        }
        b.push().translate(11.6f, 2.15f, 10.6f).rotateY(-35).rotateX(-12);
        b.lathe(new float[][]{{2.12f, 0.9f}, {2.16f, 1.6f}, {2.0f, 2.3f}, {1.7f, 2.85f}}, 12, 25, 155, POLYCARB, 1, 1.16f, 0, 0, 0, false);
        b.lathe(new float[][]{{2.12f, 0.9f}, {2.16f, 1.6f}, {2.0f, 2.3f}, {1.7f, 2.85f}}, 12, 25, 155, POLYCARB, 1, 1.16f, 0, 0, 0, true);
        b.pop();
    }

    // =================================================================== 5. sac de secours

    /** Contour octogonal (rectangle w x d chanfreiné de c), sens trigonométrique, centré. */
    static float[][] octogone(float w, float d, float c) {
        float x = w / 2, z = d / 2;
        return new float[][]{{x, -z + c}, {x, z - c}, {x - c, z}, {-x + c, z}, {-x, z - c}, {-x, -z + c}, {-x + c, -z}, {x - c, -z}};
    }

    /** Bande qui ceinture une boîte chanfreinée (x0..x1, z0..z1, chanfrein bv) de y à y + h, décollée de 0,05. */
    static void ceinture(MeshBuilder b, float x0, float z0, float x1, float z1, float bv, float y, float h, Mat m) {
        b.push().translate((x0 + x1) / 2, y + h / 2, (z0 + z1) / 2).rotateX(90);
        b.slab(octogone(x1 - x0 + 0.4f, z1 - z0 + 0.4f, bv + 0.12f), h, 0.03f, false, m, null, m);
        b.pop();
    }

    /** Rouleau de bande (gaze) couché le long de X local. */
    static void rouleau(MeshBuilder b, float r, float l) {
        b.push().rotateZ(-90);
        b.cylinder(r, -l / 2, l / 2, 10, BANDAGE, BANDAGE, BANDAGE);
        b.pop();
    }

    /**
     * Sac d'intervention de secouriste (60 x 35 x 33 cm) ouvert : rabat relevé contre le mur, bandes réfléchissantes,
     * poche avant à croix verte, lettrage SECOURS. Dedans : rouleaux de bande, compresses, ballon insufflateur bleu,
     * garrot. Bande déroulée et compresses par terre, bandoulière traînant au sol, déchirure rafistolée au scotch.
     */
    static void sacSecours(MeshBuilder b) {
        float x0 = 3.0f, x1 = 12.6f, z0 = 1.0f, z1 = 6.6f, hb = 5.2f, bv = 0.8f;
        b.bevelBox(x0, 0, z0, x1, hb, z1, bv, TOILE_SECOURS, null, MeshBuilder.SKIP_PY);
        b.color(0.6f);
        interieurHaut(b, x0 + bv, hb - 1.1f, z0 + bv, x1 - bv, hb, z1 - bv, TOILE_SECOURS, INTERIEUR_SALE);
        b.color(1);
        ceinture(b, x0, z0, x1, z1, bv, 0.7f, 0.55f, BANDE_REFL);
        ceinture(b, x0, z0, x1, z1, bv, 3.3f, 0.55f, BANDE_REFL);
        // poche avant, croix, lettrage, fermeture
        b.bevelBox(x0 + 1.2f, 0.5f, z1 - 0.3f, x1 - 1.2f, 2.95f, z1 + 0.75f, 0.35f, TOILE_SECOURS);
        decal(b, CROIX_SECOURS, 'z', x0 + 2.4f, 1.75f, z1 + 0.75f, 1.4f, 1.4f);
        decal(b, L_SECOURS, 'z', 8.5f, 1.75f, z1 + 0.75f, 4.6f, 1.15f);
        b.bevelBox(x0 + 1.5f, 2.55f, z1 + 0.7f, x1 - 1.5f, 2.72f, z1 + 0.8f, 0.03f, NOIR_MAT);
        b.bevelBox(x1 - 2.2f, 2.3f, z1 + 0.75f, x1 - 1.7f, 2.75f, z1 + 0.95f, 0.06f, METAL_NU);
        decal(b, CROIX_SECOURS, 'X', x1, 2.2f, (z0 + z1) / 2, 1.5f, 1.5f);
        // poignées de bout et bandoulière tombée au sol
        for (int s = -1; s <= 1; s += 2) {
            float x = s < 0 ? x0 - 0.02f : x1 + 0.02f;
            b.tube(new float[][]{p(x, 4.4f, z0 + 1.6f), p(x + s * 0.7f, 4.6f, z0 + 1.9f), p(x + s * 0.7f, 4.6f, z1 - 1.9f), p(x, 4.4f, z1 - 1.6f)}, rayons(4, 0.16f), 5, SANGLE_NOIRE, false, false, 0, false);
        }
        bandeSol(b, new float[][]{p(x1 + 0.05f, 0.9f, z1 - 1.2f), p(x1 + 0.7f, 0.06f, z1 + 0.4f), p(x1 + 0.4f, 0.04f, z1 + 2.6f), p(x1 - 2.0f, 0.04f, z1 + 4.0f), p(x1 - 5.0f, 0.04f, z1 + 3.6f), p(x1 - 7.2f, 0.04f, z1 + 4.6f)}, 0.6f, SANGLE_NOIRE);
        // contenu : rouleaux, compresses, insufflateur, garrot
        float yc = hb - 1.1f;
        for (int k = 0; k < 3; k++) {
            b.push().translate(x0 + 2.0f + k * 1.35f, yc + 0.6f, z0 + 1.8f + (k % 2) * 0.5f).rotateY(80 + k * 12);
            rouleau(b, 0.6f, 1.5f);
            b.pop();
        }
        b.push().translate(x0 + 2.6f, yc, z1 - 1.8f).rotateY(-8);
        b.bevelBox(-1.3f, 0, -0.55f, 1.3f, 1.6f, 0.55f, 0.06f, AtlasCivil.WHITE);
        decal(b, BOITE_PANSEMENTS, 'z', 0, 0.8f, 0.55f, 1.5f, 1.5f);
        b.pop();
        b.push().translate(x1 - 2.6f, yc + 1.0f, (z0 + z1) / 2 + 0.2f).rotateZ(28).rotateY(20);
        b.color(0.32f, 0.5f, 0.78f);
        b.lathe(new float[][]{{0.01f, -2.2f}, {0.7f, -2.0f}, {1.05f, -1.2f}, {1.1f, 0}, {0.9f, 0.9f}, {0.4f, 1.3f}, {0.3f, 1.8f}, {0.01f, 1.85f}}, 12, 0, 360, PLASTIQUE_USE, 1, 1, 0.03f, 0.6f, 4, false);
        b.color(0.85f, 0.88f, 0.86f);
        b.push().translate(0, 1.85f, 0);
        b.lathe(new float[][]{{0.3f, 0}, {0.75f, 0.35f}, {0.9f, 0.8f}, {0.01f, 0.82f}}, 12, 0, 360, PLASTIQUE_USE, 1, 0.8f, 0, 0, 0, false);
        b.pop();
        b.color(1);
        b.pop();
        b.tube(new float[][]{p(x0 + 4.6f, hb - 0.2f, z1 - 1.2f), p(x0 + 5.6f, hb + 0.5f, z1 - 1.4f), p(x0 + 6.4f, hb + 0.2f, z1 - 0.9f), p(x0 + 6.8f, hb - 0.4f, z1 - 0.95f)}, rayons(4, 0.16f), 5, SANGLE_NOIRE, false, true, 0, false);
        b.color(0.75f, 0.2f, 0.16f);
        b.bevelBox(x0 + 6.5f, hb - 0.6f, z1 - 1.25f, x0 + 7.2f, hb + 0.1f, z1 - 0.7f, 0.08f, PLASTIQUE_USE);
        b.color(1);
        // rabat relevé (charnière à l'arrière du sac), poches en filet à l'intérieur
        b.push().translate(0, hb - 0.1f, z0 + 0.35f).rotateX(-78);
        b.bevelBox(x0 - 0.05f, -0.65f, 0, x1 + 0.05f, 0, z1 - z0 - 0.2f, 0.3f, TOILE_SECOURS);
        b.color(0.75f);
        b.box(x0 + 0.7f, -0.7f, 0.6f, x1 - 0.7f, -0.66f, z1 - z0 - 0.8f, TOILE_SECOURS);
        b.color(1);
        for (float x = x0 + 1.4f; x < x1 - 1.0f; x += 2.2f) {
            b.push().translate(x + 0.6f, -0.95f, 2.4f).rotateX(90);
            b.cylinder(0.32f, -0.9f, 0.9f, 8, x < 8 ? BANDAGE : AtlasCivil.WHITE, null, null);
            b.pop();
            b.bevelBox(x, -1.05f, 2.0f, x + 1.2f, -0.73f, 2.6f, 0.05f, SANGLE_NOIRE);
        }
        b.push().translate(x1 - 2.2f, -0.78f, 3.7f).rotateY(30);
        b.bevelBox(-1.2f, -0.12f, -0.14f, 0.4f, 0.0f, 0.14f, 0.04f, CHROME_PIQUE);
        b.push().translate(0.4f, -0.06f, 0);
        b.torus(0.32f, 0.07f, 8, 4, 0, 360, NOIR_MAT);
        b.pop();
        b.pop();
        b.pop();
        // au sol : bande déroulée, compresses
        b.push().translate(x0 - 0.8f, 0.55f, z1 + 2.6f).rotateY(20);
        rouleau(b, 0.5f, 1.4f);
        b.pop();
        bandeSol(b, new float[][]{p(x0 - 0.4f, 0.13f, z1 + 2.4f), p(x0 + 1.0f, 0.13f, z1 + 2.8f), p(x0 + 2.6f, 0.13f, z1 + 3.0f), p(x0 + 4.6f, 0.14f, z1 + 4.2f), p(x0 + 5.6f, 0.13f, z1 + 5.4f)}, 1.3f, BANDAGE);
        b.push().translate(x1 + 1.2f, 0.01f, z1 + 1.0f).rotateY(-25);
        b.bevelBox(-0.9f, 0, -0.65f, 0.9f, 0.22f, 0.65f, 0.05f, AtlasCivil.WHITE);
        decal(b, BOITE_PANSEMENTS, 'y', 0, 0.22f, 0, 1.6f, 1.2f);
        b.pop();
        // usure : boue, auréole, déchirure scotchée
        boue(b, 'z', 8.0f, 0, z1 + 0.75f, 6.6f, 1.2f, 0);
        boue(b, 'x', x0, 0, (z0 + z1) / 2, 3.6f, 1.6f, 1);
        decal(b, AUREOLE, 'x', x0, 2.4f, (z0 + z1) / 2 - 0.4f, 2.4f, 2.4f);
        b.push().translate(x0 - 0.02f, 2.2f, z0 + 1.6f).rotateY(-90);
        b.box(-0.9f, -0.4f, 0.03f, 0.9f, 0.4f, 0.09f, SCOTCH);
        b.box(-0.4f, -0.9f, 0.1f, 0.4f, 0.9f, 0.16f, SCOTCH);
        b.pop();
    }

    // =================================================================== 6. malle de pompier

    /** Hache de pompier (90 cm) couchée à plat le long de X local, fer en x = L (tranchant vers +Z, pic vers -Z). */
    static void hache(MeshBuilder b, float L) {
        b.push().rotateZ(-90);
        b.cylinder(0.26f, 0, L - 1.0f, 8, AtlasCivil.WALNUT, AtlasCivil.WALNUT, null);
        b.cylinder(0.3f, 0, 2.6f, 8, NOIR_MAT, null, null);
        b.pop();
        b.bevelBox(L - 1.5f, -0.34f, -0.55f, L - 0.3f, 0.34f, 0.55f, 0.1f, ROUGE_POMPIER);
        b.push().translate(0, 0.13f, 0).rotateX(90);
        b.prism(new float[][]{{L - 1.45f, 0.4f}, {L - 0.35f, 0.4f}, {L - 0.05f, 2.6f}, {L - 1.75f, 2.6f}}, 0.26f, ROUGE_POMPIER, ROUGE_POMPIER);
        b.prism(new float[][]{{L - 1.25f, -0.4f}, {L - 0.55f, -0.4f}, {L - 0.9f, -2.3f}}, 0.26f, ROUGE_POMPIER, ROUGE_POMPIER);
        b.pop();
        b.bevelBox(L - 1.76f, -0.1f, 2.38f, L - 0.04f, 0.1f, 2.64f, 0.04f, METAL_NU);
    }

    /** Lance d'incendie (laiton terni, poignée noire) couchée le long de X local. */
    static void lance(MeshBuilder b) {
        b.push().rotateZ(-90);
        b.lathe(new float[][]{{0.55f, 0}, {0.55f, 0.6f}, {0.42f, 0.7f}, {0.4f, 2.2f}, {0.32f, 2.4f}, {0.28f, 3.4f}, {0.2f, 3.6f}, {0.01f, 3.62f}}, 10, 0, 360, AtlasCivil.BRASS, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{0.47f, 0.9f}, {0.5f, 1.4f}, {0.47f, 1.9f}}, 10, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
        b.pop();
        b.push().translate(1.4f, -0.3f, 0).rotateZ(-15);
        b.bevelBox(-0.25f, -1.4f, -0.22f, 0.25f, 0, 0.22f, 0.08f, NOIR_MAT);
        b.pop();
    }

    /**
     * Malle à outils des pompiers (92 x 50 x 46 cm) en tôle rouge écaillée, lettrage POMPIERS usé, couvercle relevé
     * retenu par sa chaînette. Dedans : bâche pliée, hache, lance, rouleau de tuyau à raccord laiton, gants.
     */
    static void mallePompier(MeshBuilder b) {
        float x0 = 0.8f, x1 = 15.2f, z0 = 1.3f, z1 = 9.1f, hb = 6.0f, bv = 0.22f, d = z1 - z0;
        b.color(0.45f);
        for (float x : new float[]{x0 + 1.2f, x1 - 2.4f}) b.bevelBox(x, 0, z0 + 0.3f, x + 1.2f, 0.4f, z1 - 0.3f, 0.08f, ROUGE_POMPIER);
        b.color(1);
        b.bevelBox(x0, 0.38f, z0, x1, hb, z1, bv, ROUGE_POMPIER, null, MeshBuilder.SKIP_PY);
        b.color(0.55f);
        interieurHaut(b, x0 + bv, 0.9f, z0 + bv, x1 - bv, hb, z1 - bv, ROUGE_POMPIER, INTERIEUR_SALE);
        b.color(1);
        // nervures embouties (avant et côtés), cornières, poignées
        for (float y : new float[]{1.6f, 4.6f}) {
            b.bevelBox(x0 + 0.9f, y, z1 - 0.1f, x1 - 0.9f, y + 0.35f, z1 + 0.25f, 0.1f, ROUGE_POMPIER);
            b.bevelBox(x0 - 0.25f, y, z0 + 0.9f, x0 + 0.1f, y + 0.35f, z1 - 0.9f, 0.1f, ROUGE_POMPIER);
            b.bevelBox(x1 - 0.1f, y, z0 + 0.9f, x1 + 0.25f, y + 0.35f, z1 - 0.9f, 0.1f, ROUGE_POMPIER);
        }
        for (float x : new float[]{x0 - 0.08f, x1 - 0.82f}) {
            for (float z : new float[]{z0 - 0.08f, z1 - 0.82f}) b.bevelBox(x, 0.36f, z, x + 0.9f, 1.3f, z + 0.9f, 0.15f, METAL_NU);
        }
        KitCivil.poigneeU(b, x1 + 0.08f, 4.2f, (z0 + z1) / 2, 2.6f, 1, METAL_NU);
        KitCivil.poigneeU(b, x0 - 0.08f, 4.2f, (z0 + z1) / 2, 2.6f, -1, METAL_NU);
        decal(b, L_POMPIERS, 'z', 8.0f, 3.25f, z1, 9.2f, 2.3f);
        // moraillon ouvert et vis
        b.bevelBox(7.5f, hb - 1.2f, z1 - 0.02f, 8.5f, hb - 0.2f, z1 + 0.15f, 0.04f, METAL_NU);
        for (float x : new float[]{x0 + 1.0f, x1 - 1.0f}) {
            boulon(b, x, hb - 0.5f, z1, 0.14f);
            coulure(b, 'z', x, hb - 0.55f, z1, 0.9f, (int) x);
        }
        // contenu : bâche, rouleau de tuyau, hache, lance, gants
        b.bevelBox(x0 + 0.3f, 0.9f, z0 + 0.3f, x1 - 0.3f, 3.0f, z1 - 0.3f, 0.5f, AtlasLoot.TARP);
        b.push().translate(4.2f, 3.0f + 1.7f, 4.4f).rotateZ(90).rotateY(14);
        b.color(0.86f, 0.82f, 0.74f);
        b.cylinder(1.7f, -0.6f, 0.6f, 16, TISSU_USE, TISSU_USE, TISSU_USE);
        b.color(1);
        for (float y : new float[]{-0.62f, 0.62f}) {
            b.push().translate(0, y, 0);
            b.torus(1.05f, 0.04f, 14, 3, 0, 360, INTERIEUR_SALE);
            b.pop();
        }
        b.push().translate(1.6f, 0, 0.2f).rotateZ(-90);
        b.lathe(new float[][]{{0.5f, 0}, {0.62f, 0.2f}, {0.62f, 0.9f}, {0.45f, 1.1f}, {0.45f, 1.5f}}, 10, 0, 360, AtlasCivil.BRASS, 1, 1, 0, 0, 0, false);
        b.pop();
        b.pop();
        b.push().translate(x0 + 1.0f, 3.4f, z1 - 1.6f).rotateY(12).rotateZ(4);
        hache(b, 13.2f);
        b.pop();
        b.push().translate(9.0f, 3.5f, 3.2f).rotateY(-25);
        lance(b);
        b.pop();
        b.color(0.72f, 0.6f, 0.38f);
        b.push().translate(11.6f, 3.0f, 5.6f).rotateY(30);
        b.blob(1.3f, 0.35f, 0.75f, 0.6f, 0.05f, 0.8f, 3, 10, CUIR_USE);
        b.pop();
        b.color(1);
        // couvercle relevé (charnière arrière) et sa chaînette
        b.push().translate(0, hb, z0 + 0.05f).rotateX(-80);
        b.bevelBox(x0 - 0.12f, -0.02f, -0.1f, x1 + 0.12f, 1.1f, d + 0.12f, 0.2f, ROUGE_POMPIER);
        b.color(0.55f);
        b.box(x0 + 0.3f, -0.08f, 0.4f, x1 - 0.3f, -0.02f, d - 0.3f, INTERIEUR_SALE);
        b.color(1);
        b.push().translate(8.0f, -0.08f, d / 2).rotateX(90);
        decal(b, ETIQUETTE_DECHIREE, 'z', 0, 0, 0, 3.2f, 1.6f, 4);
        b.pop();
        b.pop();
        b.tube(new float[][]{p(x1 - 0.6f, hb - 0.3f, z0 + 1.6f), p(x1 - 0.5f, hb + 1.4f, z0 + 2.0f), p(x1 - 0.6f, hb + 2.9f, z0 + 0.75f)}, rayons(3, 0.07f), 4, METAL_NU, false, false, 0, false);
        // usure : boue, griffures, éclaboussures
        boue(b, 'z', 8.0f, 0.38f, z1, 13.0f, 2.2f, 0);
        boue(b, 'x', x0, 0.38f, (z0 + z1) / 2, 6.0f, 1.8f, 1);
        decal(b, GRIFFURES, 'z', 13.7f, 3.2f, z1, 1.6f, 0.8f, -10);
        decal(b, AUREOLE, 'X', x1, 3.0f, 3.6f, 2.6f, 2.6f);
    }

    // =================================================================== 7. extincteurs

    /** Extincteur à eau de 6 L (55 cm), pied en y = 0, axe Y local, étiquette vers +Z. */
    static void extincteur(MeshBuilder b, boolean flexible) {
        b.lathe(new float[][]{{1.0f, 0.3f}, {1.22f, 0.45f}, {1.28f, 0.8f}, {1.28f, 6.8f}, {1.18f, 7.4f}, {0.92f, 7.85f}, {0.5f, 8.1f}, {0.36f, 8.15f}}, 18, 0, 360, ROUGE_POMPIER, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.0f, 0}, {1.33f, 0.05f}, {1.34f, 0.6f}, {1.2f, 0.66f}}, 18, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
        bandeCyl(b, ETIQ_EXTINCTEUR, 1.37f, 2.2f, 5.6f, 32, 148, 8);
        bandeCyl(b, BOUE_1, 1.39f, 0.62f, 1.7f, 160, 380, 10);
        bandeCyl(b, GRIFFURES, 1.37f, 6.0f, 6.7f, 150, 200, 3);
        bandeCyl(b, COULURE_3, 1.37f, 5.9f, 7.2f, 202, 214, 2);
        b.cylinder(0.36f, 8.1f, 8.7f, 10, METAL_NU, null, null);
        b.bevelBox(-0.5f, 8.6f, -0.45f, 0.5f, 9.4f, 0.45f, 0.12f, NOIR_MAT);
        b.bevelBox(-0.35f, 9.35f, -1.8f, 0.35f, 9.7f, 0.4f, 0.1f, NOIR_MAT);
        b.push().translate(0, 9.6f, 0.3f).rotateX(-14);
        b.bevelBox(-0.25f, 0, -2.0f, 0.25f, 0.3f, 0, 0.1f, CHROME_PIQUE);
        b.pop();
        b.push().translate(0.5f, 9.0f, 0).rotateZ(-90);
        b.cylinder(0.32f, 0, 0.18f, 10, CHROME_PIQUE, null, AtlasCivil.WHITE);
        b.pop();
        if (flexible) {
            b.tube(new float[][]{p(-0.5f, 9.0f, 0.1f), p(-1.0f, 8.9f, 0.5f), p(-1.55f, 7.8f, 0.7f), p(-1.55f, 4.0f, 0.75f), p(-1.45f, 2.6f, 0.8f)}, rayons(5, 0.17f), 6, NOIR_MAT, false, false, 0, false);
            b.push().translate(-1.45f, 2.6f, 0.8f).rotateZ(180);
            b.lathe(new float[][]{{0.2f, 0}, {0.26f, 0.2f}, {0.16f, 0.9f}, {0.01f, 0.92f}}, 8, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
            b.pop();
        }
    }

    /**
     * Deux extincteurs de 6 L (l'un debout contre le mur, l'autre renversé, flexible arraché) et un seau à sable rouge
     * à moitié renversé. Étiquettes jaunies, peinture écaillée, rouille, sable répandu.
     */
    static void extincteurs(MeshBuilder b) {
        b.push().translate(4.0f, 0, 2.1f).rotateY(-12);
        extincteur(b, true);
        b.pop();
        b.push().translate(9.6f, 1.42f, 9.6f).rotateY(-28).rotateZ(90).rotateY(23);
        extincteur(b, false);
        b.pop();
        b.tube(new float[][]{p(4.6f, 0.17f, 12.4f), p(6.0f, 0.17f, 13.6f), p(7.8f, 0.17f, 13.2f), p(8.8f, 0.17f, 12.0f)}, rayons(4, 0.17f), 6, NOIR_MAT, true, true, 0, false);
        decal(b, AUREOLE, 'y', 5.2f, 0, 11.0f, 3.2f, 2.4f, 20);
        // seau à sable
        b.push().translate(12.3f, 0, 3.4f);
        b.lathe(new float[][]{{1.85f, 0}, {2.0f, 0.2f}, {2.45f, 4.6f}, {2.6f, 4.78f}}, 20, 0, 360, ROUGE_POMPIER, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{2.48f, 4.7f}, {1.95f, 0.4f}}, 20, 0, 360, ROUGE_POMPIER, 1, 1, 0, 0, 0, true);
        b.disc(1.85f, 0.02f, 20, ROUGE_POMPIER, false);
        b.push().translate(0, 4.78f, 0);
        b.torus(2.58f, 0.1f, 20, 4, 0, 360, ROUGE_POMPIER);
        b.pop();
        b.push().translate(0, 3.2f, 0).scale(1, 0.18f, 1);
        b.sphereSection(2.3f, 18, 4, 0, 90, SABLE);
        b.pop();
        bandeCone(b, L_SABLE, 2.24f, 2.42f, 2.1f, 3.7f, 50, 130, 6);
        bandeCone(b, BOUE_2, 2.07f, 2.18f, 0.25f, 1.25f, 30, 150, 8);
        bandeCone(b, COULURE_2, 2.43f, 2.55f, 3.6f, 4.62f, 98, 112, 2);
        bandeCone(b, COULURE_1, 2.3f, 2.5f, 2.6f, 4.62f, 58, 68, 2);
        b.pop();
        b.tube(new float[][]{p(12.3f - 2.6f, 4.4f, 3.4f), p(12.3f - 2.4f, 5.4f, 2.6f), p(12.3f, 5.8f, 1.5f), p(12.3f + 2.4f, 5.4f, 2.6f), p(12.3f + 2.6f, 4.4f, 3.4f)}, rayons(5, 0.08f), 4, METAL_NU, false, false, 0, false);
        // sable répandu devant
        b.push().translate(12.6f, 0, 8.0f).scale(1.7f, 0.22f, 1.1f);
        b.sphereSection(1.0f, 12, 3, 0, 90, SABLE);
        b.pop();
        debris(b, 12.4f, 8.2f, 2.4f, 10, SABLE, 41);
    }

    // =================================================================== 8. brancard

    /**
     * Brancard pliant (2 m) posé au sol le long du mur, sur deux blocs : hampes d'aluminium rapprochées, toile orange
     * roulée entre elles et sanglée, poignées caoutchouc, étriers. Couverture grise roulée dessus, tache de sang séché,
     * sac de premiers secours tombé devant.
     */
    static void brancard(MeshBuilder b) {
        float yh = 1.55f, za = 2.0f, zb = 2.75f, zc = (za + zb) / 2;
        for (float z : new float[]{za, zb}) {
            b.push().translate(0.4f, yh, z).rotateZ(-90);
            b.cylinder(0.22f, 0, 31.2f, 10, CHROME_PIQUE, CAOUTCHOUC_USE, CAOUTCHOUC_USE);
            b.cylinder(0.3f, 0, 2.0f, 10, CAOUTCHOUC_USE, null, null);
            b.cylinder(0.3f, 29.2f, 31.2f, 10, CAOUTCHOUC_USE, null, null);
            b.pop();
            for (float x : new float[]{4.4f, 26.4f}) {
                b.tube(new float[][]{p(x, yh, z), p(x, 0.14f, z + 0.15f), p(x + 1.3f, 0.14f, z + 0.15f), p(x + 1.3f, yh, z)}, rayons(4, 0.13f), 6, CHROME_PIQUE, false, false, 0, false);
            }
        }
        // écarteurs repliés le long des hampes
        for (float x : new float[]{3.0f, 26.0f}) b.bevelBox(x, yh + 0.2f, za - 0.15f, x + 2.8f, yh + 0.38f, zb + 0.15f, 0.05f, METAL_NU);
        // toile roulée
        b.push().translate(3.2f, yh + 0.15f, zc).rotateZ(-90);
        b.color(0.82f, 0.5f, 0.32f);
        b.lathe(new float[][]{{0.4f, 0}, {0.8f, 0.3f}, {0.95f, 1.2f}, {1.0f, 12.0f}, {0.95f, 24.0f}, {0.8f, 24.9f}, {0.4f, 25.2f}}, 14, 0, 360, AtlasCivil.ORANGE, 1, 0.9f, 0.06f, 0.5f, 7, false);
        b.color(1);
        b.pop();
        // sangles et boucles
        for (float x : new float[]{8.0f, 16.0f, 24.0f}) {
            b.push().translate(x, yh + 0.15f, zc).rotateZ(90).scale(1, 1, 0.92f);
            b.torus(1.03f, 0.06f, 14, 3, 0, 360, SANGLE_NOIRE);
            b.pop();
            b.bevelBox(x - 0.3f, yh + 0.65f, zc + 0.85f, x + 0.3f, yh + 1.1f, zc + 1.12f, 0.05f, METAL_NU);
        }
        // couverture roulée sanglée dessus
        b.push().translate(13.0f, yh + 1.7f, zc - 0.1f).rotateY(-4).rotateZ(-90);
        b.color(0.55f, 0.55f, 0.52f);
        b.lathe(new float[][]{{0.01f, -3.0f}, {0.55f, -2.95f}, {0.72f, -2.6f}, {0.75f, 2.6f}, {0.6f, 2.95f}, {0.01f, 3.0f}}, 12, 0, 360, TISSU_USE, 1, 1, 0.05f, 0.7f, 3, false);
        b.color(1);
        b.pop();
        // sang séché et boue sur la toile
        decal(b, SANG_2, 'y', 20.0f, yh + 1.18f, zc, 3.0f, 0.9f, 4);
        decal(b, BOUE_1, 'z', 10.0f, yh - 0.35f, zc + 0.96f, 6.0f, 0.7f);
        decal(b, AUREOLE, 'y', 6.5f, yh + 1.18f, zc, 1.6f, 0.9f);
        // sac de premiers secours tombé devant
        b.push().translate(24.6f, 1.45f, 7.6f).rotateY(-18).rotateZ(90);
        b.lathe(new float[][]{{0.01f, -3.2f}, {1.0f, -3.1f}, {1.4f, -2.6f}, {1.5f, 0}, {1.4f, 2.6f}, {1.0f, 3.1f}, {0.01f, 3.2f}}, 16, 0, 360, TOILE_SECOURS, 0.95f, 1.15f, 0.04f, 0.5f, 11, false);
        b.lathe(new float[][]{{1.53f, -1.6f}, {1.53f, -1.1f}}, 16, 0, 360, BANDE_REFL, 0.96f, 1.16f, 0, 0, 0, false);
        b.lathe(new float[][]{{1.53f, 1.1f}, {1.53f, 1.6f}}, 16, 0, 360, BANDE_REFL, 0.96f, 1.16f, 0, 0, 0, false);
        b.pop();
        decal(b, CROIX_SECOURS, 'y', 24.4f, 2.95f, 7.6f, 1.2f, 1.2f, -18);
        b.tube(new float[][]{p(22.0f, 2.6f, 7.0f), p(21.0f, 0.18f, 8.6f), p(23.0f, 0.16f, 11.0f), p(26.4f, 0.18f, 10.4f), p(27.0f, 2.4f, 8.4f)}, rayons(5, 0.15f), 4, SANGLE_NOIRE, false, false, 0, false);
        jonchee(b, 2.0f, 4.0f, 30.0f, 8.0f, 7, FEUILLES, 1.4f, 17);
    }

    // =================================================================== 9. coffre de pick-up

    /**
     * Panneau plat cabossé (face +Z au plan z) : grille de triangles, enfoncements (cx, cy, rayon, profondeur) ; le
     * bord reste au plan z (raccord avec le chanfrein d'une boîte sans face avant).
     */
    static void panneauCabosse(MeshBuilder b, Mat m, float x0, float y0, float x1, float y1, float z, int nx, int ny, float[][] creux) {
        float[][][] g = new float[nx + 1][ny + 1][];
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                float x = x0 + (x1 - x0) * i / nx, y = y0 + (y1 - y0) * j / ny, dz = 0;
                if (i > 0 && i < nx && j > 0 && j < ny) {
                    for (float[] c : creux) {
                        float d = (float) Math.hypot(x - c[0], (y - c[1]) * 1.3f) / c[2];
                        if (d < 1) dz -= c[3] * (1 - d * d) * (1 - d * d);
                    }
                }
                g[i][j] = p(x, y, z + dz);
            }
        }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                b.flatOut(m, p((x0 + x1) / 2, (y0 + y1) / 2, z - 50), g[i][j], g[i + 1][j], g[i + 1][j + 1]);
                b.flatOut(m, p((x0 + x1) / 2, (y0 + y1) / 2, z - 50), g[i][j], g[i + 1][j + 1], g[i][j + 1]);
            }
        }
    }

    /**
     * Coffre de benne de pick-up en aluminium larmé (1,5 m, forme « crossover » : ailes posées sur les ridelles), tombé
     * du plateau : face avant cabossée, couvercle entrouvert par un pied-de-biche resté coincé, mousse et boue.
     */
    static void coffrePickup(MeshBuilder b) {
        float z0 = 0.5f, z1 = 8.5f, bv = 0.25f;
        b.bevelBox(7.0f, 0, z0, 25.0f, 4.6f, z1, bv, ALU_LARME, null, MeshBuilder.SKIP_PZ);
        panneauCabosse(b, ALU_LARME, 7.0f + bv, bv, 25.0f - bv, 4.6f - bv, z1, 16, 5, new float[][]{{11.5f, 3.3f, 1.8f, 0.5f}, {19.5f, 3.0f, 1.3f, 0.3f}});
        b.bevelBox(4.0f, 4.4f, z0, 28.0f, 7.4f, z1, bv, ALU_LARME, new Mat[]{null, null, null, INTERIEUR_SALE, null, null}, 0);
        // joint, serrures à palette
        b.bevelBox(4.3f, 7.36f, z1 - 0.45f, 27.7f, 7.5f, z1 - 0.1f, 0.04f, CAOUTCHOUC_USE);
        for (float x : new float[]{10.0f, 22.0f}) {
            b.bevelBox(x - 1.0f, 5.3f, z1 - 0.05f, x + 1.0f, 6.5f, z1 + 0.22f, 0.12f, CHROME_PIQUE);
            b.push().translate(x, 5.9f, z1 + 0.2f).rotateX(90);
            b.cylinder(0.26f, 0, 0.12f, 10, CHROME_PIQUE, null, AtlasCivil.DARK);
            b.pop();
        }
        // boulons de fixation sous les ailes
        for (float x : new float[]{5.0f, 27.0f}) {
            for (float z : new float[]{2.5f, 6.5f}) {
                b.push().translate(x, 2.6f, z);
                b.cylinder(0.14f, 0, 1.85f, 6, METAL_NU, null, null);
                b.pop();
            }
        }
        // couvercle entrouvert (charnière à l'arrière) et pied-de-biche coincé à l'avant
        b.push().translate(0, 7.45f, z0 - 0.15f).rotateX(-14);
        b.bevelBox(3.8f, 0, 0, 28.2f, 0.45f, z1 - z0 + 0.3f, 0.15f, ALU_LARME);
        b.bevelBox(3.8f, -0.75f, z1 - z0 + 0.05f, 28.2f, 0.05f, z1 - z0 + 0.3f, 0.1f, ALU_LARME);
        decal(b, FEUILLES.asFit(), 'y', 9.0f, 0.45f, 3.4f, 3.0f, 2.0f, 20);
        decal(b, MOUSSE_VERTE, 'y', 26.0f, 0.45f, 1.8f, 3.0f, 1.5f, -90);
        b.pop();
        b.tube(new float[][]{p(14.6f, 7.0f, z1 - 1.2f), p(15.0f, 7.7f, z1 + 0.2f), p(15.9f, 9.4f, z1 + 2.2f), p(16.3f, 10.0f, z1 + 2.3f), p(16.6f, 10.2f, z1 + 1.9f)},
                rayons(5, 0.17f), 6, AtlasCivil.PAINT_RED, false, true, 0, false);
        // usure : boue, oxydation, mousse, éraflures, autocollant arraché
        boue(b, 'z', 16.0f, 0, z1, 17.0f, 1.6f, 0);
        boue(b, 'x', 7.0f, 0, 4.5f, 7.0f, 2.0f, 1);
        boue(b, 'X', 25.0f, 0, 4.5f, 7.0f, 2.0f, 0);
        decal(b, MOUSSE_VERTE, 'z', 26.5f, 4.9f, z1, 2.6f, 1.2f);
        decal(b, GRIFFURES, 'z', 19.0f, 6.2f, z1, 3.4f, 1.6f, -6);
        decal(b, AUREOLE, 'X', 28.0f, 5.9f, 3.0f, 2.4f, 2.4f);
        decal(b, ETIQUETTE_DECHIREE, 'z', 6.2f, 6.2f, z1, 2.6f, 1.3f);
        jonchee(b, 3.0f, 9.2f, 29.0f, 13.0f, 9, FEUILLES, 1.5f, 23);
    }

    // =================================================================== 10. glacière de chasse

    /** Couteau de chasse (lame acier, garde, manche de bois) à plat le long de X local. */
    static void couteau(MeshBuilder b) {
        b.push().rotateX(90).translate(0, 0, -0.05f);
        b.prism(new float[][]{{0, -0.3f}, {2.5f, -0.25f}, {3.1f, 0.15f}, {2.6f, 0.32f}, {0, 0.3f}}, 0.1f, CHROME_PIQUE, METAL_NU);
        b.pop();
        b.bevelBox(-0.2f, -0.06f, -0.55f, 0, 0.12f, 0.55f, 0.04f, AtlasCivil.BRASS);
        b.bevelBox(-2.2f, -0.12f, -0.32f, -0.2f, 0.2f, 0.32f, 0.12f, NOYER_CROSSE);
    }

    /**
     * Grande glacière de chasseur (90 x 45 x 45 cm) : cuve olive, couvercle camouflé entrouvert, poignées de corde,
     * loquets de caoutchouc. Filet de sang séché qui a coulé du couvercle, boue au pied, couteau oublié dessus.
     */
    static void glaciereGibier(MeshBuilder b) {
        float x0 = 1.3f, x1 = 14.7f, z0 = 0.8f, z1 = 8.0f, yb = 0.4f, hc = 5.6f;
        for (float x : new float[]{x0 + 0.6f, x1 - 1.8f}) {
            for (float z : new float[]{z0 + 0.6f, z1 - 1.8f}) b.bevelBox(x, 0, z, x + 1.2f, yb + 0.1f, z + 1.2f, 0.12f, CAOUTCHOUC_USE);
        }
        b.bevelBox(x0, yb, z0, x1, hc, z1, 0.6f, OLIVE_PL);
        // tenons moulés et poignées de corde aux deux bouts
        for (int s = -1; s <= 1; s += 2) {
            float xs = s < 0 ? x0 : x1;
            for (float z : new float[]{z0 + 1.6f, z1 - 1.6f}) b.bevelBox(xs - 0.45f, 3.8f, z - 0.45f, xs + 0.45f, 5.2f, z + 0.45f, 0.2f, OLIVE_PL);
            float xr = xs + s * 0.55f;
            b.tube(new float[][]{p(xr, 4.6f, z0 + 1.6f), p(xr + s * 0.3f, 3.2f, z0 + 2.4f), p(xr + s * 0.35f, 2.8f, (z0 + z1) / 2), p(xr + s * 0.3f, 3.2f, z1 - 2.4f), p(xr, 4.6f, z1 - 1.6f)},
                    rayons(5, 0.16f), 6, AtlasLoot.ROPE, true, true, 0, false);
            b.push().translate(xr + s * 0.35f, 2.8f, (z0 + z1) / 2).rotateX(90);
            b.cylinder(0.32f, -1.0f, 1.0f, 8, OLIVE_PL, OLIVE_PL, OLIVE_PL);
            b.pop();
        }
        // couvercle camouflé, entrouvert à l'avant
        b.push().translate(0, hc, z0 + 0.2f).rotateX(-3.5f);
        b.bevelBox(x0 - 0.12f, 0, -0.32f, x1 + 0.12f, 1.5f, z1 - z0 - 0.08f, 0.5f, CAMO_CHASSE);
        b.bevelBox(x0 + 1.0f, 1.45f, 0.8f, x1 - 1.0f, 1.62f, z1 - z0 - 1.2f, 0.12f, CAMO_CHASSE);
        decal(b, GRIFFURES, 'y', 4.0f, 1.62f, 3.6f, 3.0f, 1.4f, 25);
        decal(b, AUREOLE, 'y', 11.0f, 1.62f, 2.8f, 2.6f, 2.6f);
        b.push().translate(9.2f, 1.62f, 4.6f).rotateY(-24);
        couteau(b);
        b.pop();
        b.pop();
        b.box(x0 + 0.7f, hc - 0.02f, z1 - 0.7f, x1 - 0.7f, hc + 0.36f, z1 - 0.62f, INTERIEUR_SALE);
        // loquets en T, bouchon de vidange
        for (float x : new float[]{4.0f, 12.0f}) {
            b.bevelBox(x - 0.45f, 3.9f, z1 - 0.05f, x + 0.45f, 6.4f, z1 + 0.25f, 0.12f, CAOUTCHOUC_USE);
            b.bevelBox(x - 0.9f, 6.2f, z1 + 0.0f, x + 0.9f, 6.75f, z1 + 0.3f, 0.12f, CAOUTCHOUC_USE);
        }
        b.push().translate(x0 + 1.6f, 1.4f, z1).rotateX(90);
        b.cylinder(0.5f, 0, 0.35f, 10, NOIR_MAT, null, NOIR_MAT);
        b.pop();
        // sang séché qui a coulé, boue, mousse, scotch sur une fêlure
        decal(b, SANG_2, 'z', 8.2f, 3.6f, z1, 2.6f, 2.2f, -80);
        boue(b, 'z', 8.0f, yb, z1, 14.0f, 2.6f, 1);
        boue(b, 'x', x0, yb, 4.4f, 6.4f, 2.2f, 0);
        boue(b, 'X', x1, yb, 4.4f, 6.4f, 2.2f, 1);
        scotchZ(b, 1.6f, 3.6f, 3.4f, 2.4f, z1, 0.7f, SCOTCH);
        jonchee(b, 1.0f, 8.6f, 15.0f, 12.0f, 6, FEUILLES, 1.3f, 5);
    }

    // =================================================================== 11. râtelier de chasse

    /** Crosse (profil de côté, épaisseur 0,62 px) le long de Y local, talon en y = 0, busc vers -Z. */
    static void crosse(MeshBuilder b) {
        b.push().rotateY(-90);
        b.slab(new float[][]{{-0.55f, 0}, {1.65f, 0}, {0.75f, 5.4f}, {0.55f, 6.1f}, {-0.2f, 6.1f}, {-0.35f, 5.0f}}, 0.62f, 0.18f, false, NOYER_CROSSE, NOYER_CROSSE, NOYER_CROSSE);
        b.pop();
        b.bevelBox(-0.33f, -0.22f, -0.55f, 0.33f, 0.03f, 1.65f, 0.08f, CAOUTCHOUC_USE);
    }

    /** Fusil de chasse juxtaposé (1,15 m) debout le long de Y local, canons en z = 0. */
    static void fusilChasse(MeshBuilder b) {
        crosse(b);
        b.bevelBox(-0.3f, 6.0f, -0.32f, 0.3f, 8.4f, 0.62f, 0.08f, ACIER_BRUNI);
        b.tube(new float[][]{p(0, 6.4f, 0.62f), p(0, 6.5f, 1.25f), p(0, 7.5f, 1.25f), p(0, 7.8f, 0.62f)}, rayons(4, 0.07f), 4, ACIER_BRUNI, false, false, 0, false);
        b.bevelBox(-0.28f, 8.4f, 0.1f, 0.28f, 12.0f, 0.62f, 0.14f, NOYER_CROSSE);
        for (float x : new float[]{-0.17f, 0.17f}) {
            b.push().translate(x, 8.3f, 0);
            b.cylinder(0.17f, 0, 10.1f, 8, ACIER_BRUNI, null, AtlasCivil.DARK);
            b.pop();
        }
    }

    /** Carabine à lunette (1,15 m) debout le long de Y local, canon en z = 0, lunette vers -Z. */
    static void carabine(MeshBuilder b) {
        crosse(b);
        b.push().translate(0, 6.0f, 0.05f);
        b.cylinder(0.32f, 0, 2.8f, 10, ACIER_BRUNI, ACIER_BRUNI, ACIER_BRUNI);
        b.pop();
        b.push().translate(0.3f, 7.6f, 0.05f).rotateZ(-70);
        b.cylinder(0.08f, 0, 0.8f, 6, ACIER_BRUNI, null, null);
        b.translate(0, 0.8f, 0);
        b.sphere(0.18f, 8, 4, ACIER_BRUNI);
        b.pop();
        b.bevelBox(-0.28f, 8.6f, 0.15f, 0.28f, 13.4f, 0.66f, 0.14f, NOYER_CROSSE);
        b.push().translate(0, 8.6f, 0.05f);
        b.cylinder(0.14f, 0, 10.4f, 8, ACIER_BRUNI, null, AtlasCivil.DARK);
        b.pop();
        b.tube(new float[][]{p(0, 6.3f, 0.6f), p(0, 6.4f, 1.15f), p(0, 7.3f, 1.15f), p(0, 7.6f, 0.6f)}, rayons(4, 0.07f), 4, ACIER_BRUNI, false, false, 0, false);
        // lunette et colliers
        b.push().translate(0, 6.4f, -0.72f);
        b.lathe(new float[][]{{0.01f, 0}, {0.36f, 0.02f}, {0.36f, 0.9f}, {0.22f, 1.5f}, {0.22f, 3.6f}, {0.42f, 4.4f}, {0.42f, 5.0f}, {0.01f, 5.02f}}, 10, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
        b.pop();
        for (float y : new float[]{8.4f, 9.6f}) b.bevelBox(-0.12f, y, -0.6f, 0.12f, y + 0.4f, 0.0f, 0.04f, NOIR_MAT);
        // bretelle de cuir qui pend
        b.tube(new float[][]{p(0, 3.2f, 1.2f), p(0.2f, 6.0f, 2.6f), p(0.2f, 9.0f, 2.2f), p(0, 11.6f, 0.75f)}, rayons(4, 0.13f), 4, CUIR_USE, false, false, 0, false);
    }

    /** Ruban vertical (cartouchière) qui suit un chemin, face vers +Z. */
    static void rubanFace(MeshBuilder b, float[][] pts, float w, Mat m) {
        int n = pts.length;
        float[][] g = new float[n][], d = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] a = pts[Math.max(0, i - 1)], c = pts[Math.min(n - 1, i + 1)];
            float tx = c[0] - a[0], ty = c[1] - a[1], l = (float) Math.hypot(tx, ty);
            float nx = ty / l * w / 2, ny = -tx / l * w / 2;
            g[i] = p(pts[i][0] + nx, pts[i][1] + ny, pts[i][2]);
            d[i] = p(pts[i][0] - nx, pts[i][1] - ny, pts[i][2]);
        }
        for (int i = 0; i < n - 1; i++) b.face(m, MeshBuilder.v(0, 0, 1), g[i], g[i + 1], d[i + 1], d[i]);
    }

    /**
     * Râtelier de chasse en noyer (70 cm, 1,3 m) : socle à encoches feutrées, montants, traverse haute. Un fusil
     * juxtaposé et une carabine à lunette, une place vide (arme volée) ; cartouchière pendue à la cheville.
     */
    static void ratelierChasse(MeshBuilder b) {
        float x0 = 2.2f, x1 = 13.8f, z0 = 0.35f;
        b.bevelBox(x0, 0, z0, x1, 1.6f, 5.4f, 0.15f, AtlasCivil.WALNUT);
        for (float x : new float[]{5.0f, 8.0f, 11.0f}) b.box(x - 0.85f, 1.6f, 2.0f, x + 0.85f, 1.66f, 4.6f, AtlasCivil.INNER);
        for (float x : new float[]{x0, x1 - 1.0f}) b.bevelBox(x, 1.55f, z0, x + 1.0f, 20.4f, z0 + 1.0f, 0.12f, AtlasCivil.WALNUT);
        b.bevelBox(x0 + 0.95f, 1.55f, z0 + 0.1f, x1 - 0.95f, 6.2f, z0 + 0.5f, 0.06f, AtlasCivil.WALNUT);
        b.bevelBox(x0 - 0.05f, 17.4f, z0 + 0.06f, x1 + 0.05f, 18.8f, 1.95f, 0.15f, AtlasCivil.WALNUT);
        for (float x : new float[]{5.0f, 8.0f, 11.0f}) b.box(x - 0.55f, 17.5f, 1.95f, x + 0.55f, 18.7f, 2.01f, AtlasCivil.INNER);
        b.bevelBox(x0 - 0.2f, 20.35f, z0 + 0.1f, x1 + 0.2f, 21.2f, z0 + 1.4f, 0.2f, AtlasCivil.WALNUT);
        // armes : pied dans l'encoche, canon contre la traverse
        b.push().translate(5.0f, 1.62f, 3.55f).rotateX(-4.6f).rotateY(4);
        fusilChasse(b);
        b.pop();
        b.push().translate(11.0f, 1.62f, 3.55f).rotateX(-4.6f).rotateY(-6);
        carabine(b);
        b.pop();
        // cheville et cartouchière
        b.push().translate(3.0f, 14.6f, z0 + 1.0f).rotateX(90);
        b.cylinder(0.2f, 0, 1.0f, 8, AtlasCivil.WALNUT, null, AtlasCivil.WALNUT);
        b.pop();
        float zc = z0 + 1.55f;
        float[][] ceinture = {p(2.3f, 8.6f, zc), p(2.45f, 12.0f, zc), p(2.6f, 14.2f, zc), p(3.0f, 14.85f, zc + 0.03f), p(3.4f, 14.2f, zc + 0.07f), p(3.6f, 11.6f, zc + 0.07f), p(3.5f, 9.4f, zc + 0.07f)};
        rubanFace(b, ceinture, 0.95f, CUIR_USE);
        for (int k = 0; k < 6; k++) {
            float y = 9.2f + k * 0.85f;
            b.push().translate(2.45f - 0.55f, y, zc + 0.22f);
            cartouche(b, AtlasCivil.PAINT_RED, false);
            b.pop();
            if (k < 4) {
                b.push().translate(3.55f - 0.55f, y + 0.4f, zc + 0.29f);
                cartouche(b, k == 1 ? AtlasCivil.PLASTIC_GREEN : AtlasCivil.PAINT_RED, false);
                b.pop();
            }
        }
        // usure : coulures, boue, mousse, place vide poussiéreuse
        boue(b, 'z', 8.0f, 0, 5.4f, 11.0f, 1.4f, 0);
        decal(b, AUREOLE, 'y', 8.0f, 1.66f, 3.3f, 1.6f, 1.6f);
        decal(b, MOUSSE_VERTE, 'z', 12.6f, 2.0f, z0 + 1.0f, 1.4f, 0.8f);
        decal(b, GRIFFURES, 'z', 8.0f, 18.1f, 1.95f, 3.0f, 1.2f, 4);
        jonchee(b, 1.0f, 6.0f, 15.0f, 10.0f, 5, FEUILLES, 1.3f, 9);
    }

    // =================================================================== 12. caisse de pièges

    /** Piège à mâchoires (35 cm) rouillé, tendu (mâchoires à plat) ou fermé (mâchoires dressées), centré en 0. */
    static void piege(MeshBuilder b, boolean tendu) {
        b.bevelBox(-3.0f, 0, -0.22f, 3.0f, 0.16f, 0.22f, 0.05f, ROUILLE);
        for (int s = -1; s <= 1; s += 2) {
            b.bevelBox(s * 1.25f - 0.15f, 0.06f, -0.15f, s * 1.25f + 0.15f, 0.55f, 0.15f, 0.04f, ROUILLE);
            b.tube(new float[][]{p(s * 1.3f, 0.26f, -0.38f), p(s * 2.95f, 0.26f, -0.14f), p(s * 3.15f, 0.26f, 0), p(s * 2.95f, 0.26f, 0.14f), p(s * 1.3f, 0.26f, 0.38f)}, rayons(5, 0.1f), 5, ROUILLE, false, false, 0, false);
        }
        if (tendu) {
            b.push().translate(0, 0.26f, 0);
            b.torus(1.25f, 0.09f, 10, 4, 0, 180, ROUILLE);
            b.torus(1.25f, 0.09f, 10, 4, 180, 360, ROUILLE);
            b.pop();
            b.push().translate(0, 0.16f, 0);
            b.cylinder(0.55f, 0, 0.08f, 10, ROUILLE, null, ROUILLE);
            b.pop();
            b.bevelBox(-0.06f, 0.2f, 0.4f, 0.06f, 0.3f, 1.25f, 0.02f, ROUILLE);
        } else {
            for (int s = -1; s <= 1; s += 2) {
                b.push().translate(0, 0.3f, s * 0.07f).rotateX(-90);
                b.torus(1.25f, 0.09f, 10, 4, 0, 180, ROUILLE);
                b.pop();
            }
        }
    }

    /** Couronne de corde (3 tours) posée à plat. */
    static void couronneCorde(MeshBuilder b, float r) {
        for (int k = 0; k < 3; k++) {
            b.push().translate(0.08f * k, 0.18f + k * 0.3f, -0.05f * k);
            b.torus(r - k * 0.08f, 0.17f, 16, 4, 0, 360, AtlasLoot.ROPE);
            b.pop();
        }
    }

    /**
     * Caisse de trappeur en planches (60 x 40 x 30 cm) ouverte : pièges à mâchoires rouillés fermés sur la paille,
     * chaînes qui pendent par-dessus le bord ; un piège tendu au sol devant, couvercle cloué jeté par terre, corde.
     */
    static void caissePieges(MeshBuilder b) {
        float cx0 = 2.0f, cz0 = 0.8f, L = 9.6f, W = 6.4f, H = 4.8f;
        caissePlanches(b, cx0, 0, cz0, L, W, H, BOIS_GRIS, 12);
        b.box(cx0 + 0.46f, 2.6f, cz0 + 0.46f, cx0 + L - 0.46f, 2.7f, cz0 + W - 0.46f, PAILLE);
        b.push().translate(cx0 + 4.4f, 2.7f, cz0 + 2.6f).rotateY(12);
        piege(b, false);
        b.pop();
        b.push().translate(cx0 + 5.2f, 2.95f, cz0 + 4.3f).rotateY(-20).rotateZ(6);
        piege(b, false);
        b.pop();
        b.chain(new float[][]{p(cx0 + 2.0f, 3.0f, cz0 + 3.0f), p(cx0 + 1.4f, 4.9f, cz0 + W - 0.3f), p(cx0 + 1.2f, 3.4f, cz0 + W + 0.25f), p(cx0 + 1.0f, 1.6f, cz0 + W + 0.35f)}, 0.55f, 0.36f, 0.08f, ROUILLE, 6, 3);
        b.chain(new float[][]{p(cx0 + 8.0f, 3.1f, cz0 + 3.6f), p(cx0 + L - 0.2f, 4.9f, cz0 + 3.2f), p(cx0 + L + 0.3f, 3.0f, cz0 + 3.0f)}, 0.55f, 0.36f, 0.08f, ROUILLE, 6, 3);
        // piège tendu au sol et sa chaîne vers un piquet
        b.push().translate(5.2f, 0.01f, 11.0f).rotateY(-14);
        piege(b, true);
        b.pop();
        b.chain(new float[][]{p(2.3f, 0.12f, 11.7f), p(1.4f, 0.12f, 12.6f), p(1.2f, 0.12f, 13.8f)}, 0.55f, 0.36f, 0.08f, ROUILLE, 6, 3);
        b.push().translate(1.2f, 0.26f, 13.9f).rotateZ(-70);
        b.cylinder(0.16f, -0.2f, 2.2f, 6, ROUILLE, null, ROUILLE);
        b.pop();
        // couvercle jeté au sol (planches et traverses, clous qui dépassent)
        b.push().translate(11.0f, 0.66f, 10.6f).rotateY(28).rotateZ(4);
        for (int k = 0; k < 3; k++) b.bevelBox(-3.0f, 0, -2.1f + k * 1.42f, 3.0f, 0.4f, -2.1f + k * 1.42f + 1.32f, 0.07f, BOIS_GRIS);
        for (float x : new float[]{-2.4f, 2.0f}) b.bevelBox(x, -0.42f, -2.0f, x + 0.5f, 0.02f, 2.0f, 0.07f, BOIS_GRIS);
        for (float z : new float[]{-1.4f, 0, 1.4f}) {
            b.push().translate(-2.15f, 0.4f, z);
            b.cylinder(0.05f, 0, 0.5f, 4, ROUILLE, null, ROUILLE);
            b.pop();
        }
        b.pop();
        // corde
        b.push().translate(13.5f, 0, 3.6f);
        couronneCorde(b, 1.5f);
        b.pop();
        b.tube(new float[][]{p(14.6f, 0.4f, 4.4f), p(15.0f, 0.18f, 6.0f), p(14.0f, 0.18f, 7.2f), p(12.8f, 0.18f, 7.0f)}, rayons(4, 0.17f), 5, AtlasLoot.ROPE, false, true, 0, false);
        // usure : rouille qui a coulé sur les planches, boue
        coulure(b, 'z', cx0 + 1.4f, H - 0.2f, cz0 + W, 1.2f, 0);
        coulure(b, 'X', cx0 + L, H - 0.2f, cz0 + 3.1f, 1.4f, 1);
        boue(b, 'z', cx0 + L / 2, 0, cz0 + W, L - 0.8f, 1.8f, 0);
        decal(b, S_CHASSE, 'z', cx0 + 5.6f, 1.6f, cz0 + W, 4.4f, 1.1f);
    }

    // =================================================================== 13. caisse de cartouches

    static final float[][] TEINTES_CART = {{0.78f, 0.3f, 0.26f}, {0.36f, 0.6f, 0.36f}, {0.36f, 0.48f, 0.74f}, {0.86f, 0.72f, 0.3f}, {0.62f, 0.62f, 0.62f}};

    /** Boîte de 25 cartouches (11 x 11 x 7 cm) couchée, teinte k, imprimé dessus ; ouverte : culots visibles. */
    static void boiteCartouches(MeshBuilder b, int k, boolean ouverte) {
        float[] c = TEINTES_CART[Math.floorMod(k, TEINTES_CART.length)];
        b.color(c[0], c[1], c[2]);
        if (!ouverte) {
            b.bevelBox(-0.88f, 0, -0.88f, 0.88f, 1.1f, 0.88f, 0.05f, CHEMISE);
            decal(b, BOITE_CART, 'y', 0, 1.1f, 0, 1.7f, 1.0f, 90);
            decal(b, BOITE_CART, 'z', 0, 0.55f, 0.88f, 1.7f, 0.9f);
        } else {
            b.bevelBox(-0.88f, 0, -0.88f, 0.88f, 0.9f, 0.88f, 0.05f, CHEMISE);
            b.push().translate(0, 0.9f, -0.88f).rotateX(-115);
            b.bevelBox(-0.88f, -0.04f, 0, 0.88f, 0.04f, 1.76f, 0.02f, CHEMISE);
            b.pop();
        }
        b.color(1);
        if (ouverte) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (i == 2 && j == 0) continue;
                    b.push().translate(-0.55f + i * 0.55f, 0.92f, -0.55f + j * 0.55f);
                    b.cylinder(0.24f, -0.04f, 0.02f, 8, AtlasCivil.BRASS, null, AtlasCivil.BRASS);
                    b.pop();
                }
            }
        }
    }

    /**
     * Caisse en planches (60 x 42 x 25 cm) pleine de boîtes de cartouches de chasse aux couleurs passées, couvercle
     * glissé de travers ; boîtes empilées à côté, une boîte crevée, cartouches et douilles vides au sol.
     */
    static void caisseCartouches(MeshBuilder b) {
        float cx0 = 1.0f, cz0 = 0.6f, L = 9.6f, W = 6.8f, H = 4.0f;
        caissePlanches(b, cx0, 0, cz0, L, W, H, AtlasLoot.WOOD, 7);
        b.box(cx0 + 0.42f, 2.4f, cz0 + 0.42f, cx0 + L - 0.42f, 2.46f, cz0 + W - 0.42f, INTERIEUR_SALE);
        int k = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 3; j++) {
                k++;
                if (k == 4 || k == 9) continue;
                b.push().translate(cx0 + 0.4f + 1.05f + i * 2.1f, 2.47f, cz0 + 0.4f + 1.0f + j * 1.95f).rotateY((k * 37) % 9 - 4);
                boiteCartouches(b, k, k == 7);
                b.pop();
            }
        }
        // couvercle glissé de travers sur la caisse
        b.push().translate(8.6f, H + 0.01f, 4.6f).rotateY(-8);
        for (int q = 0; q < 3; q++) b.bevelBox(-L / 2, 0.42f, -W / 2 + q * W / 3 + 0.05f, L / 2, 0.82f, -W / 2 + (q + 1) * W / 3 - 0.05f, 0.07f, AtlasLoot.WOOD);
        for (float x : new float[]{-L / 2 + 0.8f, L / 2 - 1.3f}) b.bevelBox(x, 0, -W / 2 + 0.6f, x + 0.5f, 0.44f, W / 2 - 0.6f, 0.06f, AtlasLoot.WOOD);
        clous(b, -L / 2 + 0.5f, L / 2 - 0.5f, 0.62f, W / 2 + 0.0f, 2);
        b.pop();
        decal(b, S_CHASSE, 'z', cx0 + L / 2, 1.8f, cz0 + W, 4.6f, 1.15f);
        // à côté : pile de boîtes, une boîte crevée, cartouches et douilles
        for (int q = 0; q < 3; q++) {
            b.push().translate(13.4f + (q % 2) * 0.15f, q * 1.11f, 2.4f).rotateY(q * 9 - 6);
            boiteCartouches(b, q + 2, false);
            b.pop();
        }
        b.push().translate(12.6f, 0, 7.6f).rotateY(35);
        boiteCartouches(b, 0, true);
        b.pop();
        float[][] cart = {{11.0f, 9.4f, 20, 0}, {12.4f, 10.2f, 75, 0}, {9.6f, 10.8f, -30, 1}, {13.8f, 9.6f, 140, 1}, {7.4f, 11.6f, 60, 1}, {10.6f, 12.2f, 10, 1}, {14.2f, 11.4f, -60, 0}};
        for (float[] q : cart) {
            b.push().translate(q[0], 0.33f, q[1]).rotateY(q[2]);
            cartouche(b, q[3] > 0 ? AtlasCivil.PAINT_RED : AtlasCivil.PLASTIC_GREEN, q[3] > 0);
            b.pop();
        }
        boue(b, 'z', cx0 + L / 2, 0, cz0 + W, L - 0.6f, 1.6f, 1);
        coulure(b, 'z', cx0 + 0.6f, H - 0.3f, cz0 + W, 1.0f, 2);
        decal(b, AUREOLE, 'X', cx0 + L, 2.0f, cz0 + 3.0f, 2.6f, 2.6f);
    }

    // =================================================================== 14. sacs d'appâts

    /**
     * Sacs de maïs en polypropylène tissé (25 kg) : deux empilés contre le mur, un troisième éventré par terre, son
     * grain répandu en tas ; seau de maïs à côté. Sacs tachés, boue, feuilles.
     */
    static void sacsAppats(MeshBuilder b) {
        b.push().translate(6.8f, 1.32f, 3.5f).rotateY(3);
        b.blob(5.0f, 1.25f, 3.0f, 0.35f, 0.05f, 0.35f, 2, 16, SAC_TISSE);
        b.pop();
        b.push().translate(7.0f, 3.6f, 3.6f).rotateY(-7);
        b.blob(4.8f, 1.15f, 2.9f, 0.35f, 0.05f, 0.35f, 5, 16, SAC_TISSE);
        decal(b, ETIQ_MAIS, 'y', 0.4f, 1.22f, 0.1f, 3.8f, 1.9f);
        b.pop();
        decal(b, ETIQ_MAIS, 'z', 6.4f, 1.3f, 6.62f, 3.0f, 1.5f, 2);
        // sac éventré au sol et tas de grain
        b.push().translate(6.0f, 0.96f, 10.2f).rotateY(28);
        b.color(0.92f, 0.9f, 0.84f);
        b.blob(4.6f, 0.85f, 2.5f, 0.45f, 0.12f, 0.45f, 9, 16, SAC_TISSE);
        b.color(1);
        b.pop();
        b.push().translate(9.4f, 0, 11.6f).scale(2.6f, 0.75f, 1.9f);
        b.sphereSection(1.0f, 14, 4, 0, 90, MAIS);
        b.pop();
        debris(b, 9.6f, 12.0f, 3.6f, 26, MAIS, 77);
        // seau de maïs
        b.push().translate(14.0f, 0, 3.0f);
        b.color(0.5f, 0.58f, 0.46f);
        b.lathe(new float[][]{{1.4f, 0}, {1.5f, 0.15f}, {1.85f, 3.5f}, {1.98f, 3.62f}}, 18, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.86f, 3.56f}, {1.48f, 0.3f}}, 18, 0, 360, PLASTIQUE_USE, 1, 1, 0, 0, 0, true);
        b.disc(1.4f, 0.02f, 18, PLASTIQUE_USE, false);
        b.color(1);
        b.push().translate(0, 2.9f, 0).scale(1, 0.25f, 1);
        b.sphereSection(1.8f, 16, 4, 0, 90, MAIS);
        b.pop();
        bandeCone(b, BOUE_2, 1.55f, 1.68f, 0.2f, 1.2f, 40, 140, 8);
        b.pop();
        b.tube(new float[][]{p(14.0f - 1.9f, 3.3f, 3.0f), p(14.0f - 1.4f, 4.6f, 3.4f), p(14.0f, 5.0f, 3.6f), p(14.0f + 1.4f, 4.6f, 3.4f), p(14.0f + 1.9f, 3.3f, 3.0f)}, rayons(5, 0.08f), 4, METAL_NU, false, false, 0, false);
        // usure : boue, feuilles

        jonchee(b, 1.0f, 7.0f, 15.0f, 14.0f, 8, FEUILLES, 1.4f, 43);
    }

    // =================================================================== 15. trousse de soins

    /** Moitié de trousse (coque souple 30 x 20 x 5 cm), face intérieure vers +Y local, charnière en z = 0. */
    static void moitieTrousse(MeshBuilder b, boolean fond) {
        b.color(0.92f, 0.62f, 0.42f);
        b.bevelBox(-2.4f, -0.8f, 0, 2.4f, 0, 3.2f, 0.32f, AtlasCivil.ORANGE);
        b.color(1);
        b.box(-2.05f, -0.02f, 0.3f, 2.05f, 0.03f, 2.9f, AtlasCivil.INNER);
        b.bevelBox(-2.4f, -0.12f, -0.02f, 2.4f, 0.06f, 0.12f, 0.03f, NOIR_MAT);
        if (fond) {
            b.bevelBox(-2.0f, 0.03f, 0.6f, 2.0f, 0.22f, 1.0f, 0.05f, SANGLE_NOIRE);
            b.bevelBox(-2.0f, 0.03f, 1.9f, 2.0f, 0.22f, 2.3f, 0.05f, SANGLE_NOIRE);
            b.push().translate(-1.1f, 0.4f, 1.45f);
            rouleau(b, 0.38f, 1.3f);
            b.pop();
            b.push().translate(0.9f, 0.07f, 1.5f).rotateY(-8);
            b.bevelBox(-0.9f, 0, -0.75f, 0.9f, 0.35f, 0.75f, 0.05f, AtlasCivil.WHITE);
            decal(b, BOITE_PANSEMENTS, 'y', 0, 0.35f, 0, 1.5f, 1.2f);
            b.pop();
        } else {
            decal(b, CROIX_SECOURS, 'y', -1.1f, 0.03f, 1.6f, 1.3f, 1.3f);
            b.bevelBox(0.0f, 0.03f, 0.5f, 2.0f, 0.15f, 2.7f, 0.04f, SANGLE_NOIRE);
            b.push().translate(1.0f, 0.2f, 1.6f).rotateY(70);
            b.bevelBox(-1.0f, 0, -0.1f, 0.5f, 0.1f, 0.1f, 0.03f, CHROME_PIQUE);
            b.push().translate(0.5f, 0.05f, 0);
            b.torus(0.28f, 0.06f, 8, 3, 0, 360, NOIR_MAT);
            b.pop();
            b.pop();
        }
    }

    /**
     * Trousse de premiers secours orange de la sécurité civile, ouverte à plat sur une caisse de bois : compresses,
     * bande, ciseaux. Seconde trousse fermée tombée au sol, bande déroulée jusqu'au sol, gants jetés.
     */
    static void trousseSoins(MeshBuilder b) {
        float x0 = 2.2f, x1 = 13.8f, z0 = 0.8f, z1 = 7.6f, h = 5.6f;
        b.bevelBox(x0, 0.4f, z0, x1, h, z1, 0.14f, AtlasLoot.WOOD);
        for (float x : new float[]{x0 + 1.0f, x1 - 2.2f}) b.bevelBox(x, 0, z0 + 0.3f, x + 1.2f, 0.42f, z1 - 0.3f, 0.06f, AtlasLoot.WOOD);
        for (float x : new float[]{x0 - 0.05f, x1 - 0.95f}) b.bevelBox(x, 0.35f, z1 - 0.1f, x + 1.0f, h + 0.02f, z1 + 0.25f, 0.08f, AtlasLoot.WOOD);
        Kit.poigneeCorde(b, x1 + 0.05f, 3.6f, (z0 + z1) / 2, 2.6f, 1);
        Kit.poigneeCorde(b, x0 - 0.05f, 3.6f, (z0 + z1) / 2, 2.6f, -1);
        clous(b, x0 + 0.45f, x0 + 0.45f, 1.0f, z1 + 0.25f, 1);
        clous(b, x1 - 0.45f, x1 - 0.45f, h - 0.6f, z1 + 0.25f, 1);
        // trousse ouverte : fond à plat, rabat rabattu en arrière
        b.push().translate(8.0f, h + 0.8f, 3.8f).rotateY(-6);
        moitieTrousse(b, true);
        b.push().rotateX(20).rotateY(180);
        moitieTrousse(b, false);
        b.pop();
        decal(b, L_SECOURS, 'z', 0, -0.4f, 3.2f, 3.4f, 0.5f);
        b.pop();
        // bande qui pend du bord et file au sol, trousse fermée tombée, gants
        bandeSol(b, new float[][]{p(9.6f, h + 0.06f, 5.2f), p(10.0f, h + 0.06f, z1 - 0.1f), p(10.1f, h - 0.6f, z1 + 0.35f), p(10.2f, 2.0f, z1 + 0.42f), p(10.4f, 0.06f, z1 + 1.4f), p(11.6f, 0.05f, z1 + 3.4f)}, 1.1f, BANDAGE);
        b.push().translate(4.4f, 1.62f, 11.0f).rotateY(24);
        b.color(0.92f, 0.62f, 0.42f);
        b.bevelBox(-2.4f, -1.6f, -1.6f, 2.4f, 0, 1.6f, 0.5f, AtlasCivil.ORANGE);
        b.color(1);
        decal(b, L_SECU_CIVILE, 'y', 0.4f, 0, -0.6f, 3.6f, 0.6f);
        decal(b, CROIX_SECOURS, 'y', -1.4f, 0, 0.6f, 1.2f, 1.2f);
        KitCivil.glissiere(b, -2.0f, 2.0f, -0.8f, 1.6f);
        b.pop();
        b.color(0.4f, 0.55f, 0.85f);
        for (int k = 0; k < 2; k++) {
            b.push().translate(12.8f + k * 0.9f, 0.25f, 10.6f + k * 0.6f).rotateY(40 + k * 50);
            b.blob(1.0f, 0.2f, 0.55f, 0.7f, 0.15f, 1.2f, 21 + k, 8, PLASTIQUE_USE);
            b.pop();
        }
        b.color(1);
        // usure : rouille des clous, boue, auréoles
        coulure(b, 'z', x0 + 0.45f, 1.1f, z1 + 0.25f, 0.6f, 1);
        coulure(b, 'z', x1 - 0.45f, h - 0.5f, z1 + 0.25f, 0.7f, 2);
        boue(b, 'z', 8.0f, 0.4f, z1, 9.0f, 2.0f, 0);
        decal(b, AUREOLE, 'z', 5.0f, 3.2f, z1, 2.6f, 2.6f);
        decal(b, CROIX, 'X', x1, 3.0f, 3.0f, 2.4f, 2.4f, 8);
    }

    // =================================================================== 16. sac tactique

    /**
     * Sac à dos tactique noir de 35 L (52 x 35 x 22 cm) penché contre le mur : rangées MOLLE, poche administrative à
     * écusson POLICE, poche basse zippée, porte-gourde, sangles de compression ; une bretelle qui traîne, un accroc.
     */
    static void sacTactique(MeshBuilder b) {
        b.push().translate(8.0f, 0.4f, 3.75f).rotateY(-8).rotateX(-12);
        b.bevelBox(-2.8f, 0, -1.8f, 2.8f, 8.4f, 1.8f, 0.9f, CORDURA);
        b.bevelBox(-2.5f, 7.3f, -1.5f, 2.5f, 8.75f, 1.5f, 0.7f, CORDURA);
        for (int k = 0; k < 5; k++) {
            float y = 1.4f + k * 0.95f;
            b.bevelBox(-2.2f, y, 1.74f, 2.2f, y + 0.32f, 1.88f, 0.05f, SANGLE_NOIRE);
        }
        // poche basse zippée et ses rangées
        b.bevelBox(-2.2f, 0.9f, 1.6f, 2.2f, 4.1f, 2.85f, 0.5f, CORDURA);
        for (int k = 0; k < 2; k++) b.bevelBox(-1.9f, 1.5f + k * 1.0f, 2.8f, 1.9f, 1.8f + k * 1.0f, 2.94f, 0.05f, SANGLE_NOIRE);
        KitCivil.glissiere(b, -1.8f, 1.8f, 3.75f, 2.7f);
        // poche administrative, velcro et écusson
        b.bevelBox(-1.8f, 4.9f, 1.6f, 1.8f, 7.3f, 2.55f, 0.35f, CORDURA);
        b.color(0.6f);
        b.box(-1.2f, 5.55f, 2.52f, 1.2f, 6.95f, 2.6f, SANGLE_NOIRE);
        b.color(1);
        decal(b, ECUSSON, 'z', 0, 6.25f, 2.6f, 1.5f, 1.5f);
        // porte-gourde, sangles de compression, poignée
        b.push().translate(3.35f, 0.5f, 0.2f);
        b.cylinder(0.85f, 0, 3.4f, 10, CORDURA, CORDURA, AtlasCivil.DARK);
        b.pop();
        for (float y : new float[]{2.6f, 5.8f}) {
            b.bevelBox(-2.95f, y, -1.0f, -2.75f, y + 0.4f, 1.2f, 0.05f, SANGLE_NOIRE);
            b.bevelBox(-3.05f, y - 0.1f, 0.5f, -2.85f, y + 0.5f, 1.0f, 0.06f, NOIR_MAT);
        }
        b.bevelBox(2.75f, 6.2f, -1.0f, 2.95f, 6.6f, 1.2f, 0.05f, SANGLE_NOIRE);
        b.tube(new float[][]{p(-1.0f, 8.7f, -0.2f), p(-0.8f, 9.3f, -0.1f), p(0.8f, 9.3f, -0.1f), p(1.0f, 8.7f, -0.2f)}, rayons(4, 0.2f), 5, SANGLE_NOIRE, false, false, 0, false);
        // accroc : mousse qui sort
        decal(b, MOUSSE_JAUNE, 'z', 1.6f, 0.6f, 2.85f, 0.9f, 0.5f, 20);
        decal(b, BOUE_2, 'z', 0, 1.2f, 2.85f, 4.0f, 0.9f);
        b.pop();
        // bretelle qui traîne au sol
        bandeSol(b, new float[][]{p(5.6f, 2.0f, 1.3f), p(4.8f, 0.6f, 3.4f), p(4.4f, 0.14f, 5.6f), p(5.4f, 0.14f, 7.6f), p(7.2f, 0.15f, 8.4f)}, 0.9f, SANGLE_NOIRE);
        b.bevelBox(6.8f, 0, 8.0f, 7.6f, 0.25f, 8.8f, 0.05f, NOIR_MAT);
        decal(b, BOUE_1, 'y', 8.0f, 0, 8.0f, 6.0f, 2.0f, -6);
        jonchee(b, 3.0f, 6.0f, 14.0f, 11.0f, 5, FEUILLES, 1.3f, 61);
    }

    // =================================================================== 17. caisse saisie en douane

    /** Cartouche de cigarettes (27 x 9 x 5,5 cm) couchée, imprimé dessus. */
    static void cartoucheTabac(MeshBuilder b) {
        b.bevelBox(-2.15f, 0, -0.7f, 2.15f, 0.88f, 0.7f, 0.06f, AtlasCivil.WHITE);
        decal(b, CARTOUCHE_TABAC, 'y', -1.0f, 0.88f, 0, 1.3f, 1.3f);
        decal(b, CARTOUCHE_TABAC, 'y', 1.0f, 0.88f, 0, 1.3f, 1.3f);
    }

    /**
     * Caisse de bois saisie par la douane (80 x 60 x 50 cm) : pochoirs SAISIE et DOUANE, ruban de scellé arraché, plomb
     * pendu, couvercle forcé (une planche relevée, une autre arrachée et cassée au sol). Paille et cartouches de
     * cigarettes ; pied-de-biche abandonné devant.
     */
    static void caisseDouane(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 0.6f, z1 = 10.0f, h = 8.0f, d = z1 - z0;
        for (float z : new float[]{z0 + 1.0f, z1 - 2.0f}) b.bevelBox(x0 + 0.3f, 0, z, x1 - 0.3f, 0.52f, z + 1.0f, 0.06f, AtlasLoot.WOOD);
        b.bevelBox(x0, 0.5f, z0, x1, h, z1, 0.12f, AtlasLoot.WOOD, null, MeshBuilder.SKIP_PY);
        b.color(0.55f);
        interieurHaut(b, x0 + 0.12f, 6.4f, z0 + 0.12f, x1 - 0.12f, h, z1 - 0.12f, AtlasLoot.WOOD, PAILLE);
        b.color(1);
        // cadre de liteaux sur l'avant et les côtés
        b.bevelBox(x0 - 0.05f, 0.5f, z1 - 0.05f, x0 + 1.0f, h, z1 + 0.35f, 0.08f, AtlasLoot.WOOD);
        b.bevelBox(x1 - 1.0f, 0.5f, z1 - 0.05f, x1 + 0.05f, h, z1 + 0.35f, 0.08f, AtlasLoot.WOOD);
        b.bevelBox(x0 + 0.95f, h - 1.0f, z1 - 0.05f, x1 - 0.95f, h, z1 + 0.32f, 0.08f, AtlasLoot.WOOD);
        b.bevelBox(x0 + 0.95f, 0.5f, z1 - 0.05f, x1 - 0.95f, 1.5f, z1 + 0.32f, 0.08f, AtlasLoot.WOOD);
        for (int s = -1; s <= 1; s += 2) {
            float xa = s < 0 ? x0 - 0.35f : x1 - 0.05f, xb = s < 0 ? x0 + 0.05f : x1 + 0.35f;
            b.bevelBox(xa, 0.5f, z0, xb, h, z0 + 1.0f, 0.08f, AtlasLoot.WOOD);
            b.bevelBox(xa, 0.5f, z1 - 1.0f, xb, h, z1, 0.08f, AtlasLoot.WOOD);
        }
        decal(b, L_SAISIE, 'z', 8.0f, 4.4f, z1, 9.2f, 2.3f, -3);
        decal(b, L_DOUANE, 'X', x1, 4.4f, (z0 + z1) / 2, 6.8f, 1.7f);
        clous(b, x0 + 0.5f, x0 + 0.5f, h - 0.5f, z1 + 0.35f, 1);
        clous(b, x1 - 0.5f, x1 - 0.5f, h - 0.5f, z1 + 0.35f, 1);
        // couvercle : deux planches clouées, une relevée, la dernière arrachée
        for (int k = 0; k < 2; k++) b.bevelBox(x0 - 0.05f, h, z0 + k * 2.4f, x1 + 0.05f, h + 0.5f, z0 + k * 2.4f + 2.32f, 0.08f, AtlasLoot.WOOD);
        b.push().translate(0, h + 0.5f, z0 + 4.8f).rotateX(-28);
        b.bevelBox(x0 - 0.05f, -0.5f, 0.02f, x1 + 0.05f, 0, 2.32f, 0.08f, AtlasLoot.WOOD);
        for (float x : new float[]{x0 + 0.5f, x1 - 0.5f}) {
            b.push().translate(x, -0.5f, 1.2f).rotateZ(180);
            b.cylinder(0.05f, 0, 0.7f, 4, ROUILLE, null, ROUILLE);
            b.pop();
        }
        decal(b, GRIFFURES, 'z', 7.0f, -0.25f, 2.32f, 3.0f, 0.45f);
        b.pop();
        // contenu : cartouches de cigarettes dans la paille
        float[][] cig = {{4.6f, 7.6f, 10, 0}, {8.9f, 7.9f, -14, 0}, {11.6f, 7.3f, 30, 0}, {6.4f, 8.5f, 70, 18}};
        for (float[] q : cig) {
            b.push().translate(q[0], 6.42f + (q[3] > 0 ? 0.5f : 0), q[1]).rotateY(q[2]).rotateZ(q[3]);
            cartoucheTabac(b);
            b.pop();
        }
        // ruban de scellé : un bout encore collé, l'autre arraché qui pend ; plomb
        scotchY(b, 3.2f, z0 + 0.3f, 3.2f, z0 + 4.7f, h + 0.5f, 0.9f, BANDE_SCELLE);
        b.push().translate(3.2f, h - 0.3f, z1 + 0.37f).rotateZ(8);
        b.face(BANDE_SCELLE, MeshBuilder.v(0, 0, 1), p(-0.45f, -2.6f, 0.02f), p(0.45f, -2.6f, 0.02f), p(0.45f, 0.8f, 0.02f), p(-0.45f, 0.8f, 0.02f));
        b.pop();
        b.tube(new float[][]{p(12.2f, h - 0.3f, z1 + 0.37f), p(12.3f, h - 1.4f, z1 + 0.45f), p(12.2f, h - 2.2f, z1 + 0.45f)}, rayons(3, 0.04f), 3, METAL_NU, false, false, 0, false);
        b.push().translate(12.2f, h - 2.45f, z1 + 0.45f).rotateX(90);
        b.cylinder(0.32f, -0.1f, 0.1f, 8, METAL_NU, METAL_NU, METAL_NU);
        b.pop();
        // planche arrachée cassée au sol, cartouche tombée, pied-de-biche
        b.push().translate(6.6f, 0.26f, 12.6f).rotateY(-12);
        b.bevelBox(-4.4f, -0.25f, -1.16f, 3.4f, 0.25f, 1.16f, 0.08f, AtlasLoot.WOOD);
        b.push().translate(3.4f, 0, -1.0f);
        b.prism(new float[][]{{0, -0.25f}, {1.5f, -0.1f}, {0.9f, 0.05f}, {0, 0.25f}}, 2.0f, AtlasLoot.WOOD, AtlasLoot.WOOD);
        b.pop();
        for (float x : new float[]{-3.9f, 2.9f}) {
            b.push().translate(x, 0.25f, 0.4f);
            b.cylinder(0.05f, 0, 0.6f, 4, ROUILLE, null, ROUILLE);
            b.pop();
        }
        b.pop();
        b.push().translate(12.6f, 0.01f, 12.0f).rotateY(48);
        cartoucheTabac(b);
        b.pop();
        b.tube(new float[][]{p(2.0f, 0.2f, 14.4f), p(6.0f, 0.2f, 15.0f), p(9.4f, 0.2f, 15.2f), p(9.9f, 0.3f, 14.8f), p(9.8f, 0.65f, 14.3f)}, rayons(5, 0.2f), 6, AtlasCivil.PAINT_RED, true, true, 0, false);
        // usure : coulures sous les clous, boue, fissure
        coulure(b, 'z', x0 + 0.5f, h - 0.6f, z1 + 0.35f, 0.8f, 0);
        coulure(b, 'z', x1 - 0.5f, h - 0.6f, z1 + 0.35f, 0.8f, 1);
        boue(b, 'z', 8.0f, 1.5f, z1, 10.0f, 1.6f, 0);
        decal(b, FISSURE, 'x', x0, 4.0f, 5.4f, 3.0f, 3.0f);
        decal(b, MOUSSE_VERTE, 'x', x0, 1.2f, 5.0f, 3.0f, 1.0f);
    }

    // =================================================================== 18. jerricans d'essence

    /** Jerrican de 20 L (47 x 35 x 16,5 cm) debout : base en y = 0, grandes faces vers ±Z, bouchon à gauche. */
    static void jerrican(MeshBuilder b, boolean bouchon) {
        b.bevelBox(-2.8f, 0, -1.3f, 2.8f, 7.4f, 1.3f, 0.45f, ROUGE_POMPIER);
        b.bevelBox(-2.86f, 0.25f, -0.1f, 2.86f, 7.15f, 0.1f, 0.05f, ROUGE_POMPIER);
        for (int s = -1; s <= 1; s += 2) {
            for (int a = -1; a <= 1; a += 2) {
                float r = a > 0 ? 0.12f : 0.17f;
                b.push().translate(0, 3.6f, s * 1.3f).rotateZ(a * 38);
                b.bevelBox(-2.85f, -0.2f, s > 0 ? -0.06f - (a > 0 ? 0 : 0.05f) : -r, 2.85f, 0.2f, s > 0 ? r : 0.06f + (a > 0 ? 0 : 0.05f), 0.07f, ROUGE_POMPIER);
                b.pop();
            }
        }
        for (float x : new float[]{-1.4f, 0, 1.4f}) b.bevelBox(x - 0.18f, 7.2f, -0.18f, x + 0.18f, 8.3f, 0.18f, 0.06f, ROUGE_POMPIER);
        b.tube(new float[][]{p(-1.9f, 8.2f, 0), p(1.9f, 8.2f, 0)}, rayons(2, 0.22f), 8, ROUGE_POMPIER, true, true, 0, false);
        b.push().translate(-2.05f, 7.2f, 0);
        b.cylinder(0.5f, 0, 0.55f, 10, ROUGE_POMPIER, null, bouchon ? null : INTERIEUR_SALE);
        if (bouchon) {
            b.cylinder(0.62f, 0.5f, 0.85f, 10, METAL_NU, null, METAL_NU);
            b.bevelBox(0.2f, 0.6f, -0.15f, 1.6f, 0.79f, 0.15f, 0.05f, METAL_NU);
        }
        b.pop();
        decal(b, L_ESSENCE, 'z', 0.6f, 6.4f, 1.3f, 2.6f, 0.65f);
        coulure(b, 'z', -1.75f, 6.9f, 1.3f, 0.8f, 1);
        coulure(b, 'Z', 1.75f, 6.9f, -1.3f, 0.8f, 2);
        boue(b, 'z', 0, 0.45f, 1.3f, 4.6f, 1.3f, 0);
    }

    /** Entonnoir de tôle (20 cm) d'axe Y local, pointe en y = 0. */
    static void entonnoir(MeshBuilder b) {
        b.lathe(new float[][]{{0.28f, 0}, {0.3f, 1.6f}, {1.7f, 3.3f}, {1.78f, 3.4f}}, 14, 0, 360, GALVA_ROUILLE, 1, 1, 0, 0, 0, false);
        b.lathe(new float[][]{{1.7f, 3.38f}, {0.26f, 1.65f}}, 14, 0, 360, INTERIEUR_SALE, 1, 1, 0, 0, 0, true);
        b.push().translate(1.8f, 2.8f, 0).rotateZ(90);
        b.torus(0.45f, 0.07f, 8, 3, 0, 180, GALVA_ROUILLE);
        b.pop();
    }

    /**
     * Trois jerricans de 20 L en tôle rouge (deux debout contre le mur, un couché), entonnoir planté dans l'un ;
     * rouille, coulures, taches d'essence au sol.
     */
    static void jerricansEssence(MeshBuilder b) {
        b.push().translate(3.6f, 0, 1.6f).rotateY(4);
        jerrican(b, true);
        b.pop();
        b.push().translate(10.4f, 0, 2.4f).rotateY(-9);
        jerrican(b, false);
        b.pop();
        b.push().translate(10.4f - 2.05f * (float) Math.cos(Math.toRadians(9)), 7.75f, 2.4f - 2.05f * (float) Math.sin(Math.toRadians(9))).rotateZ(-6);
        entonnoir(b);
        b.pop();
        b.push().translate(7.6f, 1.49f, 13.2f).rotateY(-22).rotateX(-90);
        jerrican(b, true);
        b.pop();
        decal(b, CHIFFON, 'y', 5.0f, 0, 7.6f, 4.6f, 3.6f, 30);
        decal(b, CHIFFON, 'y', 12.4f, 0, 6.8f, 2.6f, 2.2f, -20);
        decal(b, AUREOLE, 'y', 13.2f, 0.02f, 11.4f, 3.0f, 2.4f);
    }

    // =================================================================== 19. caisse de la sécurité civile

    /**
     * Caisse étanche orange de la sécurité civile (80 x 50 x 40 cm), couvercle relevé (mousse grise), nervures,
     * loquets : couvertures de survie emballées, lampes torches ; une couverture dépliée et froissée déborde jusqu'au sol.
     */
    static void caisseSecuriteCivile(MeshBuilder b) {
        float x0 = 1.6f, x1 = 14.4f, z0 = 1.4f, z1 = 9.0f, h = 5.2f, bv = 0.4f;
        for (float x : new float[]{x0 + 1.0f, x1 - 2.4f}) b.bevelBox(x, 0, z0 + 0.4f, x + 1.4f, 0.35f, z1 - 0.4f, 0.1f, NOIR_MAT);
        b.bevelBox(x0, 0.32f, z0, x1, h, z1, bv, ORANGE_SC, null, MeshBuilder.SKIP_PY);
        b.color(0.42f);
        interieurHaut(b, x0 + bv, 1.0f, z0 + bv, x1 - bv, h, z1 - bv, PLASTIQUE_USE, PLASTIQUE_USE);
        b.color(1);
        for (float x : new float[]{x0 + 1.4f, x0 + 3.4f, x1 - 3.9f, x1 - 1.9f}) b.bevelBox(x, 0.6f, z1 - 0.1f, x + 0.5f, h - 0.7f, z1 + 0.18f, 0.12f, ORANGE_SC);
        b.bevelBox(x0 - 0.12f, h - 0.7f, z0 - 0.12f, x1 + 0.12f, h - 0.15f, z1 + 0.12f, 0.15f, ORANGE_SC, null, MeshBuilder.SKIP_PY);
        decal(b, L_SECU_CIVILE, 'z', 8.0f, 2.6f, z1, 4.6f, 0.95f);
        for (float x : new float[]{4.0f, 12.0f}) {
            b.bevelBox(x - 0.7f, h - 1.6f, z1 + 0.1f, x + 0.7f, h + 0.1f, z1 + 0.42f, 0.1f, NOIR_MAT);
            b.bevelBox(x - 0.5f, h - 1.3f, z1 + 0.4f, x + 0.5f, h - 0.6f, z1 + 0.6f, 0.08f, NOIR_MAT);
        }
        KitCivil.poigneeU(b, x1 + 0.12f, 3.6f, (z0 + z1) / 2, 2.8f, 1, NOIR_MAT);
        KitCivil.poigneeU(b, x0 - 0.12f, 3.6f, (z0 + z1) / 2, 2.8f, -1, NOIR_MAT);
        // contenu : piles de couvertures de survie, lampes
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 4; k++) {
                if (i == 1 && k == 3) continue;
                b.push().translate(4.0f + i * 3.4f, 1.0f + k * 0.3f + 0.01f, 3.6f).rotateY((i * 7 + k * 13) % 11 - 5);
                b.bevelBox(-1.4f, 0, -1.0f, 1.4f, 0.28f, 1.0f, 0.08f, SURVIE_OR);
                b.pop();
            }
        }
        for (int k = 0; k < 3; k++) {
            b.push().translate(4.0f + k * 2.4f, 2.4f + (k == 1 ? 0.3f : 0), 6.9f - k * 0.2f).rotateY(80 + k * 25).rotateZ(-90);
            b.cylinder(0.42f, -1.6f, 1.0f, 10, k == 1 ? AtlasCivil.YELLOW : NOIR_MAT, NOIR_MAT, null);
            b.lathe(new float[][]{{0.42f, 1.0f}, {0.62f, 1.6f}, {0.62f, 2.0f}}, 10, 0, 360, NOIR_MAT, 1, 1, 0, 0, 0, false);
            b.push().translate(0, 1.99f, 0);
            b.disc(0.6f, 0, 10, CHROME_PIQUE, true);
            b.pop();
            b.pop();
        }
        // couverture dépliée qui déborde par l'avant jusqu'au sol (deux faces)
        MeshBuilder.SurfaceFn drap = (u, v) -> {
            float x = 5.6f + u * 4.8f;
            float y, z;
            if (v < 0.3f) {
                float t = v / 0.3f;
                y = 2.4f + t * 3.0f;
                z = 5.4f + t * (z1 + 0.25f - 5.4f);
            } else {
                float t = (v - 0.3f) / 0.7f;
                y = 5.4f - t * 5.33f + (float) Math.sin(t * Math.PI) * 0.3f;
                z = z1 + 0.25f + (float) Math.sin(t * Math.PI / 2) * 3.6f;
            }
            float pli = (float) Math.sin(u * 17.0f + v * 5.0f) * 0.22f + (float) Math.sin(u * 7.0f - v * 11.0f) * 0.15f;
            return p(x + pli * 0.4f, Math.max(0.03f, y + pli * (v > 0.85f ? 0.1f : 0.5f)), z + pli);
        };
        b.surface(drap, 8, 10, SURVIE_OR, 1.4f, 1.6f, false, MeshBuilder.Orient.KEEP);
        b.surface(drap, 8, 10, SURVIE_OR, 1.4f, 1.6f, false, MeshBuilder.Orient.FLIP);
        // couvercle relevé (mousse grise dedans)
        b.push().translate(0, h, z0 + 0.05f).rotateX(-76);
        b.bevelBox(x0 - 0.15f, -0.02f, -0.1f, x1 + 0.15f, 1.2f, z1 - z0 + 0.15f, 0.35f, ORANGE_SC);
        b.box(x0 + 0.3f, -0.4f, 0.4f, x1 - 0.3f, -0.02f, z1 - z0 - 0.3f, AtlasLoot.FOAM);
        b.pop();
        // usure : boue, éraflures, scotch sur une fêlure, auréole
        boue(b, 'z', 8.0f, 0.32f, z1, 12.0f, 1.8f, 1);
        boue(b, 'X', x1, 0.32f, (z0 + z1) / 2, 6.0f, 1.6f, 0);
        decal(b, GRIFFURES, 'X', x1, 3.1f, 5.2f, 2.6f, 1.2f, 12);
        decal(b, FISSURE, 'x', x0, 2.6f, 4.0f, 2.2f, 2.2f);
        b.push().translate(x0 - 0.02f, 2.6f, 4.0f).rotateY(-90);
        b.box(-1.1f, -0.35f, 0.2f, 1.1f, 0.35f, 0.26f, SCOTCH);
        b.pop();
    }

    // =================================================================== 20. coffre de mirador

    /** Barreau de bois cloué entre deux montants, à la hauteur y (z au pied de l'échelle). */
    static float zEchelle(float y) {
        return 4.6f - y * 0.132f;
    }

    /**
     * Coffre étanche olive (60 x 40 x 30 cm) cadenassé au pied d'une échelle de mirador sciée (montants de bois grisé
     * appuyés contre le mur, un montant cassé, un barreau pendant) ; feuilles, mousse, morceau de filet de camouflage.
     */
    static void miradorCoffre(MeshBuilder b) {
        float pente = (float) Math.toDegrees(Math.atan(0.132f));
        // montants (ronds de 7 cm), sciés en haut ; celui de droite cassé plus bas
        float[][] montants = {{4.0f, 30.4f}, {12.0f, 27.2f}};
        for (float[] m : montants) {
            b.push().translate(m[0], 0.08f, zEchelle(0)).rotateX(-pente);
            b.cylinder(0.55f, 0, m[1], 8, BOIS_GRIS, BOIS_GRIS, BOIS_GRIS);
            b.pop();
        }
        float yh = 27.2f * (float) Math.cos(Math.toRadians(pente));
        for (int k = 0; k < 3; k++) {
            b.push().translate(12.0f + (k - 1) * 0.25f, yh - 0.1f, zEchelle(yh) + (k - 1) * 0.15f).rotateZ((k - 1) * 12).rotateX(-pente);
            b.prism(new float[][]{{-0.18f, 0}, {0.18f, 0}, {0.02f, 1.2f + k * 0.5f}}, 0.3f, BOIS_GRIS, BOIS_GRIS);
            b.pop();
        }
        // barreaux cloués (un qui pend, un manquant)
        for (int k = 0; k < 6; k++) {
            float y = 3.6f + k * 4.6f;
            if (k == 4) continue;
            float z = zEchelle(y) + 0.45f;
            if (k == 2) {
                b.push().translate(4.0f, y, z).rotateZ(-38);
                b.bevelBox(0, -0.3f, 0, 8.4f, 0.3f, 0.45f, 0.06f, BOIS_GRIS);
                b.pop();
                continue;
            }
            b.bevelBox(3.4f, y - 0.3f, z, 12.6f, y + 0.3f, z + 0.45f, 0.06f, BOIS_GRIS);
            for (float x : new float[]{4.0f, 12.0f}) KitUsure.boulon(b, x, y, z + 0.45f, 0.1f);
        }
        // coffre étanche olive
        float cx0 = 3.2f, cx1 = 12.8f, cz0 = 5.6f, cz1 = 12.0f, ch = 4.8f;
        b.bevelBox(cx0, 0, cz0, cx1, ch - 1.4f, cz1, 0.5f, OLIVE_PL);
        b.bevelBox(cx0 - 0.1f, ch - 1.45f, cz0 - 0.1f, cx1 + 0.1f, ch - 1.0f, cz1 + 0.1f, 0.15f, OLIVE_PL);
        b.bevelBox(cx0, ch - 1.02f, cz0, cx1, ch, cz1, 0.45f, OLIVE_PL);
        for (float x : new float[]{cx0 + 1.6f, cx1 - 2.6f}) {
            b.bevelBox(x, ch - 2.4f, cz1 - 0.05f, x + 1.0f, ch - 0.6f, cz1 + 0.3f, 0.12f, NOIR_MAT);
            b.bevelBox(x + 0.15f, ch - 1.0f, cz1 + 0.25f, x + 0.85f, ch - 0.55f, cz1 + 0.45f, 0.08f, NOIR_MAT);
        }
        b.bevelBox(7.4f, ch - 2.2f, cz1 - 0.05f, 8.6f, ch - 1.05f, cz1 + 0.25f, 0.08f, NOIR_MAT);
        cadenas(b, 8.0f, ch - 1.9f, cz1 + 0.28f, false);
        b.tube(new float[][]{p(6.6f, ch - 0.05f, 8.8f), p(6.9f, ch + 0.5f, 8.8f), p(9.1f, ch + 0.5f, 8.8f), p(9.4f, ch - 0.05f, 8.8f)}, rayons(4, 0.2f), 6, NOIR_MAT, false, false, 0, false);
        b.push().translate(cx1 - 1.4f, ch, cz0 + 1.2f);
        b.cylinder(0.4f, -0.05f, 0.15f, 10, NOIR_MAT, null, NOIR_MAT);
        b.pop();
        // morceau de filet de camouflage jeté sur un coin
        float yc = ch + 0.08f;
        b.face(AtlasLoot.CAMO_NET, MeshBuilder.v(0, 1, 0), p(cx0 - 0.2f, yc, cz0 + 0.8f), p(cx0 + 3.6f, yc, cz0 + 0.4f), p(cx0 + 3.2f, yc, cz0 + 4.2f), p(cx0 - 0.2f, yc, cz0 + 4.6f));
        b.face(AtlasLoot.CAMO_NET, MeshBuilder.v(-1, 0.3f, 0), p(cx0 - 0.2f, yc, cz0 + 4.6f), p(cx0 - 0.2f, yc, cz0 + 0.8f), p(cx0 - 0.7f, 0.05f, cz0 + 0.6f), p(cx0 - 0.8f, 0.05f, cz0 + 5.2f));
        // usure : boue, éraflures, coulure sous le moraillon, feuilles
        boue(b, 'z', 8.0f, 0, cz1, 9.0f, 2.2f, 0);
        boue(b, 'X', cx1, 0, (cz0 + cz1) / 2, 6.0f, 1.8f, 1);
        decal(b, GRIFFURES, 'y', 9.6f, ch, 10.4f, 2.4f, 1.1f, 30);
        coulure(b, 'z', 8.0f, ch - 2.2f, cz1 + 0.25f, 0.7f, 2);
        decal(b, AUREOLE, 'y', 11.0f, ch, 7.0f, 2.0f, 2.0f);
        decal(b, MOUSSE_VERTE, 'x', cx0, 0.9f, 8.8f, 4.4f, 1.6f);
        jonchee(b, 0.6f, 1.0f, 15.4f, 15.0f, 16, FEUILLES, 1.6f, 99);
    }
}
