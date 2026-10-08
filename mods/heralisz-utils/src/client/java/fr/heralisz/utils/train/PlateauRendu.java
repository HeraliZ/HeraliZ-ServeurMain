package fr.heralisz.utils.train;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * Plateau de la cinématique (rien que chez le client) : la vraie rame du train (modèles de ModeleTrain : voiture de
 * queue, voiture du joueur, locomotive) posée sur un plateau loin de tout, un tunnel sans fin qui défile autour d'elle
 * (sections de 48 blocs, lampes qui passent), puis le terminus : le tunnel débouche dans une grotte où la voie finit
 * contre un heurtoir. Décor en tampons du GPU, éclairage cuit dans la couleur des sommets, textures des blocs.
 * Repère : x le long de la voie (vers l'avant), y vers le haut (0 = dessus du rail), z en travers (vers la droite).
 */
@OnlyIn(Dist.CLIENT)
public final class PlateauRendu {
    // ---------------------------------------------------------------------------------------------- état (cinématique)
    public static boolean visible;
    /** Origine du plateau : dessus du rail, milieu de la voiture du joueur. */
    public static double ox, oy, oz;
    /** Mètres de tunnel parcourus (défilement) et position du train le long de x (plan large), avec les valeurs du tick
     *  précédent : le rendu interpole à l'image (fluide à n'importe quelle cadence). */
    public static double parcours, parcoursAvant, train, trainAvant;
    /** 0 : la rame file dans le tunnel sans fin ; 1 : plan large du terminus (décor fixe, la rame arrive). */
    public static int mode;
    /** Lumière (bloc, 0..15) de l'intérieur des voitures et de la caisse ; ouverture des portes de la voiture du
     *  joueur (cloison avant, intercirculation avant) ; phares (0..1) ; la loco disparaît dans le flash du choc. */
    public static float dedans = 11f, dehors = 3f, porteCloison, porteBout, phares = 1f;
    public static boolean locoVisible = true;
    public static final double LOCO = 22.1, QUEUE = -23.6;          // centres de la loco et de la voiture de queue
    /** Véhicule de tête de la cinématique : la motrice voyageurs (porte à l'arrière, salle, cabine de conduite à l'avant ;
     *  centre à TETE) ; ses portes (arrière 101, vestibule arrière 105, cabine 104) et sa lumière intérieure propre (les
     *  plafonniers de la salle grésillent). */
    public static final String TETE_MODELE = "automotrice";
    public static final double TETE = 23.6;
    public static float porteArriereTete, porteVestTete, porteCabine, dedansTete = 9f;
    /** Portes latérales de la voiture du joueur (0 avant gauche, 1 avant droite, 2 arrière gauche, 3 arrière droite) ;
     *  fauteuil pivotant du conducteur (degrés, groupe « fauteuil » de la motrice, porte 200 = pivot). */
    public static final float[] portesLaterales = new float[4], portesLaterales_queue = new float[4];
    /** Portes intérieures de la voiture de queue (100 intercirculation avant, 104 et 105 cloisons) et de celle du joueur
     *  (101 intercirculation arrière, 105 cloison arrière ; 100 et 104 : porteBout et porteCloison). */
    public static float porteAvQueue, cloisonAvQueue, cloisonArQueue, porteArJoueur, cloisonArJoueur;
    public static float fauteuil;
    /** Déraillement et choc (plan large) : pour la voiture de queue (0), celle du joueur (1) et la motrice (2) : décalage
     *  (x, y, z), lacet, roulis, tangage (degrés, autour du milieu de la caisse au niveau du rail) ; valeurs du tick
     *  précédent pour l'interpolation. */
    public static final float[][] rame = new float[3][6], rameO = new float[3][6];

    public static void rameFixe() {
        for (int i = 0; i < 3; i++) {
            java.util.Arrays.fill(rame[i], 0f);
            java.util.Arrays.fill(rameO[i], 0f);
        }
    }

    public static void rameTick() {
        for (int i = 0; i < 3; i++) System.arraycopy(rame[i], 0, rameO[i], 0, 6);
    }

    private static Matrix4f placer(Matrix4f base, double x, int i, float partial) {
        float[] a = rame[i], o = rameO[i];
        float[] v = new float[6];
        for (int k = 0; k < 6; k++) v[k] = o[k] + (a[k] - o[k]) * partial;
        return new Matrix4f(base).translate((float) x + v[0], v[1], v[2]).rotateY((float) Math.toRadians(-v[3]))
                .rotateX((float) Math.toRadians(v[4])).rotateZ((float) Math.toRadians(v[5]));
    }
    public static final double PORTAIL = 200, HEURTOIR = 52;         // terminus : sortie du tunnel ; heurtoir (après)
    public static final float SECTION = 48f;
    static final float SOL = -0.72f, TL = 4.3f, TH = 4.9f, TV = 7.2f;   // sol du tunnel, demi-largeur, piédroits, voûte

    private static VertexBuffer vboTunnel, vboTerminus, vboGare;
    /** Gare de la scène d'ouverture (mode 2) : quai à droite de x -55 à 35, entrée du tunnel en x 45. */
    public static final double GARE_QUAI0 = -55, GARE_QUAI1 = 35, GARE_TUNNEL = 45;
    public static final float QUAI = Reseau.PLANCHER - 0.06f;
    private static BufferBuilder bb;

    private PlateauRendu() {}

    /** Tampons à refaire (rechargement des ressources : l'atlas des blocs a changé). */
    public static void vider() {
        if (vboTunnel != null) vboTunnel.close();
        if (vboTerminus != null) vboTerminus.close();
        if (vboGare != null) vboGare.close();
        vboTunnel = vboTerminus = vboGare = null;
    }

    // ---------------------------------------------------------------------------------------------- rendu
    /** Appelé deux fois par image (TrainClient) : opaque après les entités, vitres et phares après le translucide. */
    public static void dessiner(Matrix4f pose, Matrix4f proj, Vec3 cam, float partial, boolean vitres) {
        if (!visible || TrainClient.shTrain == null) return;
        ShaderInstance sh = TrainClient.shTrain;
        double p = parcoursAvant + (parcours - parcoursAvant) * partial;
        double xt = trainAvant + (train - trainAvant) * partial;
        RenderSystem.setShader(() -> sh);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest();
        if (vitres) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        } else {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
        Matrix4f base = new Matrix4f(pose).translate((float) (ox - cam.x), (float) (oy - cam.y), (float) (oz - cam.z));
        if (!vitres) {
            RenderSystem.disableCull();
            decor(base, proj, sh, p);
            RenderSystem.enableCull();
        }
        // la rame : voiture de queue, voiture du joueur (portes animées), motrice (portes animées, sa propre lumière)
        Matrix4f mq = placer(base, xt + QUEUE, 0, partial);
        Matrix4f mv = placer(base, xt, 1, partial);
        vehicule("voiture_a", false, mq, proj, sh, vitres, true, true, false,
                new float[]{porteAvQueue, 0, 0, 0, cloisonAvQueue, cloisonArQueue}, p);
        vehicule("voiture_b", false, mv, proj, sh, vitres, false, false, true,
                new float[]{porteBout, porteArJoueur, 0, 0, porteCloison, cloisonArJoueur}, p);
        if (locoVisible) {
            Matrix4f ml = placer(base, xt + TETE, 2, partial);
            float d = dedans;
            dedans = dedansTete;
            vehicule(TETE_MODELE, false, ml, proj, sh, vitres, false, false, false,
                    new float[]{0, porteArriereTete, 0, 0, porteCabine, porteVestTete}, p);
            dedans = d;
            if (!vitres && ecartTete() < 0.02f) soufflet(new Matrix4f(base).translate((float) (xt + TETE), 0f, 0f), proj, sh);
        }
        if (!vitres && ecart(0) < 0.02f) soufflet(mv, proj, sh);
        VertexBuffer.unbind();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Un véhicule : tous ses groupes (portes ouvertes de portes[n], n = numéro - 100), ses bogies et ses essieux qui
     *  tournent d'après le parcours. */
    private static void vehicule(String modele, boolean loco, Matrix4f m, Matrix4f proj, ShaderInstance sh, boolean vitres,
                                 boolean feux, boolean soufAv, boolean soufAr, float[] portes, double p) {
        var lum = sh.safeGetUniform("Lumiere");
        ModeleTrain mt = ModeleTrain.get(modele);
        for (ModeleTrain.Groupe g : mt.groupes) {
            if (g.nature == ModeleTrain.FAISCEAU) {                        // phares : cônes additifs dans le noir
                if (vitres && phares > 0.02f) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    RenderSystem.setShaderColor(1f, 1f, 1f, phares);
                    lum.set(15f, 15f);
                    ModeleTrain.lier(g.texture);
                    g.vbo.bind();
                    g.vbo.drawWithShader(m, proj, sh);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.defaultBlendFunc();
                }
                continue;
            }
            boolean verre = g.nature == ModeleTrain.VERRE;
            if (verre != vitres) continue;
            if (g.nom.equals("feux") && !feux) continue;
            if (g.nom.equals("soufflet_av") && !soufAv) continue;
            if (g.nom.equals("soufflet_ar") && !soufAr) continue;
            switch (g.nature) {
                case ModeleTrain.INTERIEUR -> lum.set(dedans, 0f);
                case ModeleTrain.LUMIERE -> lum.set(15f, 15f);
                default -> lum.set(verre ? Math.max(3f, Math.min(dehors, 9f)) : dehors, 0f);
            }
            Matrix4f mg = m;
            if (g.porte >= 100 && g.porte < 200 && portes != null && g.porte - 100 < portes.length) {
                float o = Voyageurs.lisse(portes[g.porte - 100]);
                if (o > 0) mg = new Matrix4f(m).translate(g.gx * o, g.gy * o, g.gz * o);
            } else if (g.porte >= 0 && g.porte < 4 && visible && portes != null && (modele.equals("voiture_b") || modele.equals("voiture_a"))) {
                float o = Voyageurs.lisse((modele.equals("voiture_a") ? portesLaterales_queue : portesLaterales)[g.porte]);
                if (o > 0) mg = new Matrix4f(m).translate(g.gx * o, g.gy * o, g.gz * o);   // portes latérales (la gare)
            } else if (g.porte == 200 && fauteuil != 0f) {                         // fauteuil pivotant (glissement = axe)
                mg = new Matrix4f(m).translate(g.gx, g.gy, g.gz).rotateY((float) Math.toRadians(fauteuil)).translate(-g.gx, -g.gy, -g.gz);
            }
            ModeleTrain.lier(g.texture);
            g.vbo.bind();
            g.vbo.drawWithShader(mg, proj, sh);
        }
        if (vitres || sansRoues) return;
        lum.set(dehors, 0f);
        ModeleTrain bogie = ModeleTrain.get(loco ? "bogie_loco" : "bogie_voiture"), essieu = ModeleTrain.get("essieu");
        float angle = (float) (p / RameRendu.R_ROUE);
        double[] ecarts = loco ? new double[]{-1.9, 0, 1.9} : new double[]{-1.25, 1.25};
        for (double bx : loco ? new double[]{6.4, -6.4} : new double[]{8.0, -8.0}) {
            Matrix4f mb = new Matrix4f(m).translate((float) bx, 0f, 0f);
            for (ModeleTrain.Groupe g : bogie.groupes) {
                ModeleTrain.lier(g.texture);
                g.vbo.bind();
                g.vbo.drawWithShader(mb, proj, sh);
            }
            for (double ex : ecarts) {
                Matrix4f me = new Matrix4f(mb).translate((float) ex, RameRendu.R_ROUE, 0f).rotateZ(-angle);
                for (ModeleTrain.Groupe g : essieu.groupes) {
                    ModeleTrain.lier(g.texture);
                    g.vbo.bind();
                    g.vbo.drawWithShader(me, proj, sh);
                }
            }
        }
    }

    /** Les soufflets ne suivent pas une rame disloquée : on les retire dès que deux caisses voisines s'écartent. */
    private static float ecart(int i) {
        float s = 0;
        for (int k = 0; k < 6; k++) s += Math.abs(rame[i][k] - rame[i + 1][k]);
        return s;
    }

    private static float ecartTete() {
        return ecart(1);
    }

    private static final ResourceLocation TEX_SOUFFLET = new ResourceLocation(fr.heralisz.utils.HeraliszUtils.MODID, "textures/train/soufflet.png");
    private static VertexBuffer vboSoufflet;

    /** Soufflet fixe entre la voiture du joueur et celle de queue (rame en ligne droite). */
    private static void soufflet(Matrix4f mv, Matrix4f proj, ShaderInstance sh) {
        if (vboSoufflet == null) {
            BufferBuilder b = new BufferBuilder(1024);
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            float x0 = (float) (QUEUE + Voyageurs.VL + 0.14), x1 = -(Voyageurs.VL + 0.14f), w = 1.12f;
            float y0 = Reseau.PLANCHER - 0.16f, y1 = Reseau.PLANCHER + 2.6f + 0.34f, L = (x1 - x0) * 2f;
            float[][] f = {{x0, y0, -w, 0, 1}, {x0, y1, -w, 0, 0}, {x1, y1, -w, L, 0}, {x1, y0, -w, L, 1},
                    {x0, y0, w, 0, 1}, {x1, y0, w, L, 1}, {x1, y1, w, L, 0}, {x0, y1, w, 0, 0},
                    {x0, y1, -w, 0, 0}, {x0, y1, w, 0, 1}, {x1, y1, w, L, 1}, {x1, y1, -w, L, 0},
                    {x0, Reseau.PLANCHER - 0.01f, -w, 0, 1}, {x1, Reseau.PLANCHER - 0.01f, -w, L, 1},
                    {x1, Reseau.PLANCHER - 0.01f, w, L, 0.98f}, {x0, Reseau.PLANCHER - 0.01f, w, 0, 0.98f}};
            for (float[] s : f) b.vertex(s[0], s[1], s[2]).uv(s[3], s[4]).color(255, 255, 255, 255).normal(0, 1, 0).endVertex();
            vboSoufflet = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vboSoufflet.bind();
            vboSoufflet.upload(b.end());
        }
        sh.safeGetUniform("Lumiere").set(Math.max(dedans - 3f, 4f), 0f);
        RenderSystem.disableCull();
        ModeleTrain.lier(TEX_SOUFFLET);
        vboSoufflet.bind();
        vboSoufflet.drawWithShader(mv, proj, sh);
        RenderSystem.enableCull();
    }

    // ---------------------------------------------------------------------------------------------- épave du refuge
    /** Épave du train de la cinématique (CineDecor : réglée sur le serveur par /cinematique epave, sinon celle du refuge
     *  du jar) : chaque pièce = modèle, position (x, y, z de l'origine : dessus du rail, milieu), lacet et roulis (degrés). */
    private record Piece(String modele, boolean loco, double x, double y, double z, float lacet, float roulis) {}

    private static java.util.List<Piece> epave = java.util.List.of();
    /** Épave : les caisses couchées sont dessinées sans bogies ni essieux (arrachés au crash, ils traînent à côté) ;
     *  accrochés à une caisse sur le flanc, ils dépassaient en l'air ou dans la roche. */
    private static boolean sansRoues;
    private static java.util.List<fr.heralisz.utils.cine.CineDecor.Piece> source;

    private static java.util.List<Piece> epave() {
        var ep = fr.heralisz.utils.cine.CineDecor.client().epave();
        if (ep != source) {
            source = ep;
            java.util.List<Piece> l = new java.util.ArrayList<>();
            for (var p : ep) {
                String m = p.modele();
                l.add(new Piece(m, m.equals("loco") || m.equals("bogie_loco"), p.x(), p.y(), p.z(), p.lacet(), p.roulis()));
            }
            epave = l;
        }
        return epave;
    }

    /** L'épave couchée du refuge, dessinée pour tout le monde à moins de 120 blocs (lumière du monde à sa place). */
    public static void dessinerEpave(Matrix4f pose, Matrix4f proj, Vec3 cam, boolean vitres) {
        var l = Minecraft.getInstance().level;
        if (l == null || TrainClient.shTrain == null || epave().isEmpty() || !fr.heralisz.utils.cine.CineDecor.client().dans(l)) return;
        Piece p0 = epave.get(0);
        if (Math.abs(p0.x - cam.x) + Math.abs(p0.z - cam.z) > 160 || Math.abs(p0.y - cam.y) > 80) return;
        ShaderInstance sh = TrainClient.shTrain;
        RenderSystem.setShader(() -> sh);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest();
        if (vitres) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
        } else {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
        float sDedans = dedans, sDehors = dehors, sPhares = phares;
        for (Piece pc : epave) {
            var bp = net.minecraft.core.BlockPos.containing(pc.x, pc.y + 1.5, pc.z);
            // lumière du monde à sa place : blocs ou ciel (de jour en plein air, l'épave n'est pas noire)
            int ciel = l.getBrightness(net.minecraft.world.level.LightLayer.SKY, bp) - l.getSkyDarken();
            dehors = Math.max(Math.max(l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bp), ciel), 3);
            dedans = Math.max(dehors - 2, 2);
            phares = 0f;
            Matrix4f m = new Matrix4f(pose).translate((float) (pc.x - cam.x), (float) (pc.y - cam.y), (float) (pc.z - cam.z))
                    .rotateY((float) Math.toRadians(-pc.lacet)).rotateX((float) Math.toRadians(pc.roulis));
            if (pc.modele.startsWith("bogie")) {
                if (vitres) continue;
                sh.safeGetUniform("Lumiere").set(dehors, 0f);
                for (ModeleTrain.Groupe g : ModeleTrain.get(pc.modele).groupes) {
                    ModeleTrain.lier(g.texture);
                    g.vbo.bind();
                    g.vbo.drawWithShader(m, proj, sh);
                }
                continue;
            }
            sansRoues = true;
            vehicule(pc.modele, pc.loco, m, proj, sh, vitres, false, false, false, null, 0);
            sansRoues = false;
        }
        dedans = sDedans;
        dehors = sDehors;
        phares = sPhares;
        VertexBuffer.unbind();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void decor(Matrix4f base, Matrix4f proj, ShaderInstance sh, double p) {
        construire();
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        sh.safeGetUniform("Lumiere").set(15f, 0f);
        if (mode == 0) {
            double dec = -(p % SECTION);
            for (int k = -3; k <= 3; k++) {
                Matrix4f m = new Matrix4f(base).translate((float) (dec + k * SECTION), 0f, 0f);
                vboTunnel.bind();
                vboTunnel.drawWithShader(m, proj, sh);
            }
        } else if (mode == 2) {                                                     // la gare, de nuit
            vboGare.bind();
            vboGare.drawWithShader(base, proj, sh);
            for (int k = 0; k <= 2; k++) {
                Matrix4f m = new Matrix4f(base).translate((float) (GARE_TUNNEL + k * SECTION), 0f, 0f);
                vboTunnel.bind();
                vboTunnel.drawWithShader(m, proj, sh);
            }
        } else {
            for (int k = 1; k <= 4; k++) {
                Matrix4f m = new Matrix4f(base).translate((float) (PORTAIL - k * SECTION), 0f, 0f);
                vboTunnel.bind();
                vboTunnel.drawWithShader(m, proj, sh);
            }
            Matrix4f m = new Matrix4f(base).translate((float) PORTAIL, 0f, 0f);
            vboTerminus.bind();
            vboTerminus.drawWithShader(m, proj, sh);
        }
    }

    /** Lumière passante des lampes du tunnel pour un point de la rame (0..1) : l'intérieur s'éclaire quand une lampe
     *  passe devant les fenêtres. */
    public static float lampeProche(double xRame) {
        double best = 99;
        for (double lx : LAMPES_X) {
            double d = Math.abs(((xRame + parcours - lx) % SECTION + SECTION * 1.5) % SECTION - SECTION / 2);
            best = Math.min(best, d);
        }
        return (float) Math.max(0, 1 - best / 4.5);
    }

    // ---------------------------------------------------------------------------------------------- géométrie
    private static final double[] LAMPES_X = {6, 18, 30, 42};

    private static TextureAtlasSprite sp(String nom) {
        ResourceLocation r = nom.contains(":") ? new ResourceLocation(nom) : new ResourceLocation("minecraft", "block/" + nom);
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(r);
    }

    private interface Lum {
        float[] at(double x, double y, double z);
    }

    /** Bruit de valeur déterministe (0..1) pour les rugosités et le choix des matières. */
    private static double bruit(double x, double y, int graine) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        double a = h(xi, yi, graine), b = h(xi + 1, yi, graine), c = h(xi, yi + 1, graine), d = h(xi + 1, yi + 1, graine);
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static double h(int x, int y, int g) {
        long k = x * 374761393L + y * 668265263L + g * 1442695040888963407L;
        k = (k ^ (k >>> 13)) * 1274126177L;
        return ((k ^ (k >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Point de référence de l'orientation des faces : le décor se voit de l'intérieur (normale vers REF), un pavé du
     *  dehors (normale à l'opposé de REF, son centre). */
    private static final double[] REF = new double[3];
    private static boolean versRef = true;

    private static void ref(double x, double y, double z, boolean vers) {
        REF[0] = x;
        REF[1] = y;
        REF[2] = z;
        versRef = vers;
    }

    /** Quad a, b, c, d (dans l'ordre du contour), la matière tendue dessus, la lumière cuite à chaque sommet ; normale
     *  tournée selon REF (l'éclairage directionnel du shader en dépend). */
    private static void quad(TextureAtlasSprite s, double[] a, double[] b, double[] c, double[] d, Lum l) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
        float nx = (float) (uy * vz - uz * vy), ny = (float) (uz * vx - ux * vz), nz = (float) (ux * vy - uy * vx);
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double mx = (a[0] + c[0]) / 2 - REF[0], my = (a[1] + c[1]) / 2 - REF[1], mz = (a[2] + c[2]) / 2 - REF[2];
        if ((nx * mx + ny * my + nz * mz > 0) == versRef) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        double[][] P = {a, b, c, d};
        float[][] UV = {{s.getU0(), s.getV1()}, {s.getU1(), s.getV1()}, {s.getU1(), s.getV0()}, {s.getU0(), s.getV0()}};
        for (int i = 0; i < 4; i++) {
            float[] c3 = l.at(P[i][0], P[i][1], P[i][2]);
            bb.vertex((float) P[i][0], (float) P[i][1], (float) P[i][2]).uv(UV[i][0], UV[i][1])
                    .color(Math.min(1f, c3[0]), Math.min(1f, c3[1]), Math.min(1f, c3[2]), 1f).normal(nx, ny, nz).endVertex();
        }
    }

    /** Pavé aligné : x0..x1, y0..y1, z0..z1 (faces tournées vers le dehors). */
    private static void boite(TextureAtlasSprite s, double x0, double y0, double z0, double x1, double y1, double z1, Lum l) {
        double[] r = REF.clone();
        boolean v = versRef;
        ref((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, false);
        quad(s, new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y1, z0}, new double[]{x0, y1, z0}, l);
        quad(s, new double[]{x1, y0, z1}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1}, new double[]{x1, y1, z1}, l);
        quad(s, new double[]{x0, y0, z1}, new double[]{x0, y0, z0}, new double[]{x0, y1, z0}, new double[]{x0, y1, z1}, l);
        quad(s, new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x1, y1, z0}, l);
        quad(s, new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1}, l);
        quad(s, new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x1, y0, z0}, new double[]{x0, y0, z0}, l);
        ref(r[0], r[1], r[2], v);
    }

    /** Point du contour de la galerie dans la direction t (radians, depuis le milieu du sol) : piédroit ou voûte. */
    private static double[] tunnelEn(double t) {
        double c = Math.cos(t), s = Math.sin(t);
        if (Math.abs(c) > 1e-6) {
            double tw = TL / Math.abs(c), yw = tw * s;
            if (yw <= TH) return new double[]{Math.signum(c) * TL, SOL + yw};
        }
        double a = TV - TH;                                   // voûte : (z/TL)² + ((y - TH)/a)² = 1, rayon u depuis le sol
        double A = c * c / (TL * TL) + s * s / (a * a), B = -2 * s * TH / (a * a), C = TH * TH / (a * a) - 1;
        double u = (-B + Math.sqrt(Math.max(0, B * B - 4 * A * C))) / (2 * A);
        return new double[]{u * c, SOL + u * s};
    }

    /** Profil de la galerie (z, y) de la gauche du sol, par le piédroit, la voûte, jusqu'à la droite du sol. */
    private static double[][] profilTunnel() {
        java.util.List<double[]> pr = new java.util.ArrayList<>();
        for (double y = SOL; y < SOL + TH - 1e-6; y += 1.0) pr.add(new double[]{-TL, Math.min(y, SOL + TH)});
        int n = 12;
        for (int k = 0; k <= n; k++) {
            double a = Math.PI - Math.PI * k / n;
            pr.add(new double[]{TL * Math.cos(a), SOL + TH + (TV - TH) * Math.sin(a)});
        }
        for (double y = SOL + TH - 1.0; y > SOL - 1e-6; y -= 1.0) pr.add(new double[]{TL, Math.max(y, SOL)});
        pr.add(new double[]{TL, SOL});
        return pr.toArray(new double[0][]);
    }

    private static float[] teinte(double b, double rouge) {
        return new float[]{(float) (0.055 + b * 1.0 + rouge * 0.9), (float) (0.06 + b * 0.84 + rouge * 0.06), (float) (0.07 + b * 0.62 + rouge * 0.05)};
    }

    /** Avant la cinématique : tous les décors (tunnel, terminus, gare) et tous les modèles de la rame avec leurs textures
     *  (mipmaps comprises) ; sinon ils se construisent au moment où ils apparaissent, et l'image saute. */
    public static void precharger() {
        construire();
        for (String nom : new String[]{"voiture_a", "voiture_b", TETE_MODELE, "loco", "bogie_voiture", "bogie_loco", "essieu"}) {
            for (ModeleTrain.Groupe g : ModeleTrain.get(nom).groupes) {
                ModeleTrain.lier(g.texture);
            }
        }
        ModeleTrain.lier(TEX_SOUFFLET);
        VertexBuffer.unbind();
    }

    private static void construire() {
        if (vboTunnel != null) return;
        if (bb == null) bb = new BufferBuilder(1 << 18);
        // ------------------------------------------------------------------ section de tunnel (48 blocs)
        Lum lt = (x, y, z) -> {
            double b = 0;
            for (int i = 0; i < LAMPES_X.length; i++) {
                double lz = (i % 2 == 0 ? -1 : 1) * (TL - 0.25), ly = SOL + 4.3;
                double dx = ((x - LAMPES_X[i]) % SECTION + SECTION * 1.5) % SECTION - SECTION / 2;
                double d2 = dx * dx + (y - ly) * (y - ly) + (z - lz) * (z - lz);
                b += 1.25 / (1 + d2 * 0.07);
            }
            return teinte(Math.min(b, 1.3), 0);
        };
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        TextureAtlasSprite[] murs = {sp("stone_bricks"), sp("stone_bricks"), sp("cracked_stone_bricks"), sp("mossy_stone_bricks")};
        double[][] pr = profilTunnel();
        for (int x = 0; x < (int) SECTION; x++) {
            ref(x + 0.5, SOL + 3, 0, true);
            for (int k = 0; k + 1 < pr.length; k++) {
                TextureAtlasSprite s = murs[(int) (bruit(x * 0.6, k * 0.7, 3) * 3.99)];
                quad(s, new double[]{x, pr[k][1], pr[k][0]}, new double[]{x + 1, pr[k][1], pr[k][0]},
                        new double[]{x + 1, pr[k + 1][1], pr[k + 1][0]}, new double[]{x, pr[k + 1][1], pr[k + 1][0]}, lt);
            }
            for (double z = -TL; z < TL - 1e-6; z += 1.0) {                       // sol de gravier, toute la largeur :
                double z1 = Math.min(z + 1, TL);                                      // aucun jour sous la voie
                double y = (z1 <= -2.6 || z >= 2.6) ? SOL : SOL - 0.02;
                quad(sp("gravel"), new double[]{x, y, z}, new double[]{x + 1, y, z}, new double[]{x + 1, y, z1},
                        new double[]{x, y, z1}, lt);
            }
            if (x % 6 == 3) {                                                     // câbles le long des piédroits
                boite(sp("black_concrete"), x, SOL + 3.2, -TL + 0.02, x + 6, SOL + 3.28, -TL + 0.1, lt);
                boite(sp("black_concrete"), x, SOL + 3.0, TL - 0.1, x + 6, SOL + 3.08, TL - 0.02, lt);
            }
        }
        ref(SECTION / 2, SOL + 3, 0, true);
        voie(0, SECTION, lt);
        for (int i = 0; i < LAMPES_X.length; i++) {                               // lampes de paroi (allumées)
            double z = (i % 2 == 0 ? -1 : 1) * (TL - 0.2), x = LAMPES_X[i];
            Lum allume = (a, b2, c) -> new float[]{1.6f, 1.45f, 1.1f};
            boite(sp("redstone_lamp_on"), x - 0.22, SOL + 4.15, z - 0.14, x + 0.22, SOL + 4.45, z + 0.14, allume);
            boite(sp("iron_block"), x - 0.05, SOL + 4.45, z - 0.05, x + 0.05, SOL + 4.9, z + 0.05, lt);
        }
        vboTunnel = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vboTunnel.bind();
        vboTunnel.upload(bb.end());
        // ------------------------------------------------------------------ terminus : grotte, fin de voie, heurtoir
        double[][] lampes = {{6, SOL + 5.0, -6.5}, {14, SOL + 5.5, -11}, {24, SOL + 6.0, 11}, {33, SOL + 6.5, 12}, {44, SOL + 5.0, -10},
                {50, SOL + 4.0, 6}, {58, SOL + 4.0, -9}};
        Lum lg = (x, y, z) -> {
            double b = 0;
            for (double[] l : lampes) {
                double d2 = (x - l[0]) * (x - l[0]) + (y - l[1]) * (y - l[1]) + (z - l[2]) * (z - l[2]);
                b += 1.5 / (1 + d2 * 0.03);
            }
            double dr = (x - HEURTOIR - 0.6) * (x - HEURTOIR - 0.6) + (y - 2.6) * (y - 2.6) + z * z;
            return teinte(0.12 + Math.min(b, 1.2), 0.9 / (1 + dr * 0.09));
        };
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        TextureAtlasSprite[] roches = {sp("stone"), sp("andesite"), sp("tuff"), sp("deepslate"), sp("stone")};
        int nA = 26;
        double longueur = 68;
        java.util.function.BiFunction<Double, Integer, double[]> grotte = (x, k) -> {
            double a = Math.PI - Math.PI * k / nA;
            double r = 0.86 + 0.24 * bruit(x * 0.18, k * 0.45, 7) + 0.08 * bruit(x * 0.7, k * 1.3, 9);
            double fin = x > longueur - 14 ? 1 - (x - (longueur - 14)) / 16.0 : 1;  // le fond se resserre
            return new double[]{15 * Math.cos(a) * r * Math.max(0.3, fin), SOL + Math.max(0, 13 * Math.sin(a) * r * Math.max(0.45, fin))};
        };
        for (int x = 0; x < (int) longueur; x++) {
            ref(x + 0.5, SOL + 3, 0, true);
            for (int k = 0; k < nA; k++) {
                double[] a0 = grotte.apply((double) x, k), a1 = grotte.apply((double) x, k + 1);
                double[] b0 = grotte.apply((double) x + 1, k), b1 = grotte.apply((double) x + 1, k + 1);
                quad(roches[(int) (bruit(x * 0.3, k * 0.5, 11) * 4.99)], new double[]{x, a0[1], a0[0]}, new double[]{x + 1, b0[1], b0[0]},
                        new double[]{x + 1, b1[1], b1[0]}, new double[]{x, a1[1], a1[0]}, lg);
            }
            for (double z = -19; z < 19; z += 1.0) {                                 // sol : gravier, terre, éboulis (sous les parois)
                boolean sousVoie = Math.abs(z + 0.5) < 3.1 && x < HEURTOIR + 1;       // sous le ballast : un peu plus bas
                double y = sousVoie ? SOL - 0.02 : SOL, nb = bruit(x * 0.25, z * 0.25, 13);
                quad(sousVoie || nb < 0.45 ? sp("gravel") : nb < 0.75 ? sp("coarse_dirt") : sp("cobblestone"), new double[]{x, y, z},
                        new double[]{x + 1, y, z}, new double[]{x + 1, y, z + 1}, new double[]{x, y, z + 1}, lg);
            }
        }
        // mur du fond (éventail), vu de la grotte
        double[] centre = {longueur, SOL + 3, 0};
        ref(longueur - 10, SOL + 3, 0, true);
        for (int k = 0; k < nA; k++) {
            double[] a0 = grotte.apply(longueur, k), a1 = grotte.apply(longueur, k + 1);
            quad(roches[k % 5], new double[]{longueur, a0[1], a0[0]}, new double[]{longueur, a1[1], a1[0]}, centre, centre, lg);
        }
        // façade du portail : bandeau de pierre de taille autour de la galerie, puis la roche jusqu'aux parois
        ref(10, SOL + 3, 0, true);
        for (int k = 0; k < nA; k++) {
            double ta = Math.PI - Math.PI * k / nA, tb = Math.PI - Math.PI * (k + 1) / nA;
            double[] ia = tunnelEn(ta), ib = tunnelEn(tb);
            double[] ea = {ia[0] * 1.16, SOL + (ia[1] - SOL) * 1.13}, eb = {ib[0] * 1.16, SOL + (ib[1] - SOL) * 1.13};
            double[] ga = grotte.apply(0.0, k), gb = grotte.apply(0.0, k + 1);
            quad(sp(k == nA / 2 || k == nA / 2 - 1 ? "chiseled_stone_bricks" : "stone_bricks"), new double[]{0, ia[1], ia[0]},
                    new double[]{0, ib[1], ib[0]}, new double[]{0, eb[1], eb[0]}, new double[]{0, ea[1], ea[0]}, lg);
            quad(roches[k % 5], new double[]{0, ea[1], ea[0]}, new double[]{0, eb[1], eb[0]}, new double[]{0, gb[1], gb[0]},
                    new double[]{0, ga[1], ga[0]}, lg);
        }
        ref(HEURTOIR / 2, SOL + 3, 0, true);
        voie(0, HEURTOIR - 0.6, lg);
        // heurtoir : massif de béton, tampons d'acier, panneau rayé jaune et noir, feu rouge
        Lum feu = (a, b2, c) -> new float[]{1.8f, 0.25f, 0.2f};
        boite(sp("smooth_stone"), HEURTOIR, SOL, -1.4, HEURTOIR + 1.8, SOL + 1.75, 1.4, lg);
        boite(sp("gray_concrete"), HEURTOIR + 0.2, SOL + 1.75, -1.1, HEURTOIR + 1.6, SOL + 2.0, 1.1, lg);
        for (double zt : new double[]{-1.0, 1.0}) {
            boite(sp("iron_block"), HEURTOIR - 0.55, 0.62, zt - 0.17, HEURTOIR, 0.96, zt + 0.17, lg);
            boite(sp("black_concrete"), HEURTOIR - 0.62, 0.58, zt - 0.22, HEURTOIR - 0.55, 1.0, zt + 0.22, lg);
        }
        for (int i = 0; i < 6; i++) {
            double z0 = -1.4 + i * (2.8 / 6);
            quad(sp(i % 2 == 0 ? "yellow_concrete" : "black_concrete"), new double[]{HEURTOIR - 0.01, SOL + 1.0, z0},
                    new double[]{HEURTOIR - 0.01, SOL + 1.0, z0 + 2.8 / 6}, new double[]{HEURTOIR - 0.01, SOL + 1.7, z0 + 2.8 / 6},
                    new double[]{HEURTOIR - 0.01, SOL + 1.7, z0}, lg);
        }
        boite(sp("redstone_lamp_on"), HEURTOIR + 0.7, SOL + 2.0, -0.18, HEURTOIR + 1.06, SOL + 2.36, 0.18, feu);
        for (double[] l : lampes) {                                                // vieilles lampes de chantier
            Lum allume = (a, b2, c) -> new float[]{1.4f, 1.2f, 0.85f};
            boite(sp("redstone_lamp_on"), l[0] - 0.2, l[1] - 0.2, l[2] - 0.2, l[0] + 0.2, l[1] + 0.2, l[2] + 0.2, allume);
        }
        for (int i = 0; i < 9; i++) {                                              // éboulis, vieux matériel
            double x = 8 + bruit(i * 3.1, 1, 17) * 55, z = (bruit(i * 1.7, 2, 19) < 0.5 ? -1 : 1) * (5 + bruit(i, 3, 23) * 7);
            double s = 0.5 + bruit(i, 4, 29) * 1.1;
            boite(sp(i % 3 == 0 ? "barrel_side" : i % 3 == 1 ? "cobblestone" : "andesite"), x, SOL, z, x + s, SOL + s * 0.8, z + s, lg);
        }
        vboTerminus = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vboTerminus.bind();
        vboTerminus.upload(bb.end());
        construireGare();
        VertexBuffer.unbind();
    }

    // ---------------------------------------------------------------------------------------------- la gare (ouverture)
    /** Lampadaires du quai : x, allumé (les autres sont morts). */
    private static final double[][] REVERBERES = {{-48, 0}, {-36, 1}, {-24, 1}, {-12, 0}, {0, 1}, {12, 1}, {24, 0}};

    /** Gare de nuit de la scène d'ouverture : quai à droite (bande jaune, marquise sur poteaux, lampadaires dont certains
     *  morts, bancs), bâtiment de briques aux fenêtres crevées, mur de soutènement en face, ballast, portail du tunnel en
     *  x 45, et une enceinte noire tout autour qui cache le ciel (la nuit, la brume font le reste). Lumière : lune bleutée
     *  très faible et halos chauds des lampadaires allumés. */
    private static void construireGare() {
        Lum lg = (x, y, z) -> {
            double b = 0;
            for (double[] r : REVERBERES) {
                if (r[1] < 0.5) continue;
                double d2 = (x - r[0]) * (x - r[0]) + (y - 4.4) * (y - 4.4) + (z - 8.4) * (z - 8.4);
                b += 1.5 / (1 + d2 * 0.06);
            }
            b = Math.min(b, 1.25);
            return new float[]{(float) (0.11 + b * 1.0), (float) (0.13 + b * 0.82), (float) (0.19 + b * 0.55)};
        };
        Lum noirLum = (x, y, z) -> new float[]{0.015f, 0.017f, 0.025f};
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        double X0 = -230, X1 = GARE_TUNNEL, Z0 = -30, Z1 = 40, H = 45;
        // enceinte noire (le ciel caché) : vue de l'intérieur
        ref(-30, 10, 5, true);
        quad(sp("black_concrete"), new double[]{X0, SOL, Z0}, new double[]{X1 + 150, SOL, Z0}, new double[]{X1 + 150, H, Z0}, new double[]{X0, H, Z0}, noirLum);
        quad(sp("black_concrete"), new double[]{X0, SOL, Z1}, new double[]{X0, H, Z1}, new double[]{X1 + 150, H, Z1}, new double[]{X1 + 150, SOL, Z1}, noirLum);
        quad(sp("black_concrete"), new double[]{X0, SOL, Z0}, new double[]{X0, H, Z0}, new double[]{X0, H, Z1}, new double[]{X0, SOL, Z1}, noirLum);
        quad(sp("black_concrete"), new double[]{X0, H, Z0}, new double[]{X1 + 150, H, Z0}, new double[]{X1 + 150, H, Z1}, new double[]{X0, H, Z1}, noirLum);
        // sol : ballast et gravier, terre et herbe morte derrière le bâtiment
        for (double x = X0; x < X1 - 1e-6; x += 2.0) {
            for (double z = Z0; z < Z1 - 1e-6; z += 2.0) {
                if (z >= 2.6 && z < 9.0 && x >= GARE_QUAI0 && x < GARE_QUAI1) continue;      // sous le quai
                double nb = bruit(x * 0.2, z * 0.2, 31);
                String m = Math.abs(z + 1) < 3.6 ? "gravel" : z > 15 ? (nb < 0.5 ? "coarse_dirt" : "podzol_top") : nb < 0.55 ? "gravel" : "coarse_dirt";
                double y = Math.abs(z) < 2.6 ? SOL - 0.02 : SOL;
                quad(sp(m), new double[]{x, y, z}, new double[]{x + 2, y, z}, new double[]{x + 2, y, z + 2}, new double[]{x, y, z + 2}, lg);
            }
        }
        ref(-20, SOL + 3, 0, true);
        voie(X0, X1 + 150, lg);
        // le quai : dessus dallé, bordure de pierre de taille, bande jaune, rampe d'accès au bout
        for (double x = GARE_QUAI0; x < GARE_QUAI1 - 1e-6; x += 1.0) {
            for (double z = 2.6; z < 9.0 - 1e-6; z += 1.0) {
                double z1 = Math.min(z + 1, 9.0);
                ref(x + 0.5, QUAI + 3, z + 0.5, true);
                quad(sp(bruit(x * 0.5, z * 0.5, 33) < 0.7 ? "smooth_stone" : "polished_andesite"), new double[]{x, QUAI, z},
                        new double[]{x + 1, QUAI, z}, new double[]{x + 1, QUAI, z1}, new double[]{x, QUAI, z1}, lg);
            }
            quad(sp("yellow_concrete"), new double[]{x, QUAI + 0.006, 3.0}, new double[]{x + 1, QUAI + 0.006, 3.0},
                    new double[]{x + 1, QUAI + 0.006, 3.28}, new double[]{x, QUAI + 0.006, 3.28}, lg);
            ref(x + 0.5, SOL + 0.5, -2, true);
            quad(sp(bruit(x, 1, 34) < 0.8 ? "stone_bricks" : "cracked_stone_bricks"), new double[]{x, SOL, 2.6}, new double[]{x + 1, SOL, 2.6},
                    new double[]{x + 1, QUAI, 2.6}, new double[]{x, QUAI, 2.6}, lg);
        }
        ref(GARE_QUAI0 - 3, SOL, 5, true);
        quad(sp("stone_bricks"), new double[]{GARE_QUAI0, SOL, 2.6}, new double[]{GARE_QUAI0, QUAI, 2.6}, new double[]{GARE_QUAI0, QUAI, 9},
                new double[]{GARE_QUAI0, SOL, 9}, lg);
        for (double z = 2.6; z < 9 - 1e-6; z += 1.0) {                                // rampe d'accès (x -62 -> -55)
            ref(GARE_QUAI0 - 3, QUAI + 3, z + 0.5, true);
            quad(sp("polished_andesite"), new double[]{GARE_QUAI0 - 7, SOL, z}, new double[]{GARE_QUAI0, QUAI, z},
                    new double[]{GARE_QUAI0, QUAI, z + 1}, new double[]{GARE_QUAI0 - 7, SOL, z + 1}, lg);
        }
        // marquise sur poteaux
        for (double x = -30; x <= 25; x += 8) {
            boite(sp("deepslate_tiles"), x - 0.12, QUAI, 6.38, x + 0.12, 5.2, 6.62, lg);
        }
        boite(sp("gray_concrete"), -31, 5.2, 4.0, 26, 5.45, 9.6, lg);
        // bâtiment de briques : façade (portes et fenêtres crevées, noires), toit
        double fx0 = -18, fx1 = 14, fz = 9.6, hb = 7.5;
        ref(-2, 3, 5, true);
        for (double x = fx0; x < fx1 - 1e-6; x += 1.0) {
            for (double y = QUAI; y < hb - 1e-6; y += 1.0) {
                double y1 = Math.min(y + 1, hb);
                double c = ((x - fx0) % 4 + 4) % 4;
                boolean porte = (x == -2 || x == -1) && y < QUAI + 2.4;
                boolean fen = !porte && c >= 1 && c < 3 && y >= QUAI + 1.2 && y < QUAI + 3.2;
                String m = porte ? "black_concrete" : fen ? (bruit(x, y, 35) < 0.5 ? "black_stained_glass" : "black_concrete") : "bricks";
                quad(sp(m), new double[]{x, y, fz}, new double[]{x + 1, y, fz}, new double[]{x + 1, y1, fz}, new double[]{x, y1, fz},
                        m.equals("bricks") ? lg : noirLum);
            }
        }
        boite(sp("bricks"), fx0, QUAI, fz, fx0 + 0.01, hb, fz + 6, lg);
        boite(sp("bricks"), fx1 - 0.01, QUAI, fz, fx1, hb, fz + 6, lg);
        boite(sp("dark_oak_planks"), fx0 - 0.5, hb, fz - 0.6, fx1 + 0.5, hb + 0.3, fz + 6.5, lg);
        // lampadaires (morts ou allumés), bancs
        for (double[] r : REVERBERES) {
            boite(sp("polished_basalt_side"), r[0] - 0.08, QUAI, 8.32, r[0] + 0.08, 4.3, 8.48, lg);
            boite(sp("polished_basalt_side"), r[0] - 0.08, 4.3, 7.9, r[0] + 0.08, 4.42, 8.48, lg);
            Lum lampe = r[1] > 0.5 ? (a, b2, c) -> new float[]{1.6f, 1.4f, 1.0f} : noirLum;
            boite(sp(r[1] > 0.5 ? "redstone_lamp_on" : "redstone_lamp"), r[0] - 0.22, 4.05, 7.75, r[0] + 0.22, 4.3, 8.15, lampe);
        }
        for (double x : new double[]{-41, -17, 6, 18}) {
            boite(sp("dark_oak_planks"), x, QUAI + 0.45, 8.1, x + 1.8, QUAI + 0.55, 8.6, lg);
            boite(sp("dark_oak_planks"), x, QUAI + 0.55, 8.55, x + 1.8, QUAI + 1.05, 8.62, lg);
            boite(sp("iron_block"), x + 0.1, QUAI, 8.2, x + 0.2, QUAI + 0.45, 8.5, lg);
            boite(sp("iron_block"), x + 1.6, QUAI, 8.2, x + 1.7, QUAI + 0.45, 8.5, lg);
        }
        // mur de soutènement en face, sombre et moussu
        ref(-20, 3, 0, true);
        for (double x = X0; x < X1 + 4 - 1e-6; x += 2.0) {
            quad(sp(bruit(x * 0.3, 2, 36) < 0.6 ? "stone_bricks" : "mossy_stone_bricks"), new double[]{x, SOL, -9}, new double[]{x + 2, SOL, -9},
                    new double[]{x + 2, 6, -9}, new double[]{x, 6, -9}, lg);
        }
        // portail du tunnel (x 45) : façade de pierre autour de la galerie
        ref(GARE_TUNNEL - 10, SOL + 3, 0, true);
        int nA = 26;
        for (int k = 0; k < nA; k++) {
            double ta = Math.PI - Math.PI * k / nA, tb = Math.PI - Math.PI * (k + 1) / nA;
            double[] ia = tunnelEn(ta), ib = tunnelEn(tb);
            double[] ea = {ia[0] * 4, SOL + (ia[1] - SOL) * 2.2}, eb = {ib[0] * 4, SOL + (ib[1] - SOL) * 2.2};
            quad(sp(k % 6 == 0 ? "cracked_stone_bricks" : "stone_bricks"), new double[]{GARE_TUNNEL, ia[1], ia[0]},
                    new double[]{GARE_TUNNEL, ib[1], ib[0]}, new double[]{GARE_TUNNEL, eb[1], eb[0]}, new double[]{GARE_TUNNEL, ea[1], ea[0]}, lg);
        }
        vboGare = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vboGare.bind();
        vboGare.upload(bb.end());
    }


    /** La voie de x0 à x1 : prisme de ballast, traverses, deux files de rails (écartement du mod : Reseau.FILE). */
    private static void voie(double x0, double x1, Lum l) {
        TextureAtlasSprite ba = sp("heralisz_utils:block/voie_ballast"), tr = sp("heralisz_utils:block/voie_traverse"),
                ra = sp("heralisz_utils:block/voie_rail");
        float haut = -Reseau.RAIL, f = Reseau.FILE;
        for (double x = x0; x < x1 - 1e-6; x += 1.0) {
            double xb = Math.min(x + 1, x1);
            quad(ba, new double[]{x, haut, -2.05}, new double[]{xb, haut, -2.05}, new double[]{xb, haut, 2.05}, new double[]{x, haut, 2.05}, l);
            quad(ba, new double[]{x, SOL, -2.6}, new double[]{xb, SOL, -2.6}, new double[]{xb, haut, -2.05}, new double[]{x, haut, -2.05}, l);
            quad(ba, new double[]{x, haut, 2.05}, new double[]{xb, haut, 2.05}, new double[]{xb, SOL, 2.6}, new double[]{x, SOL, 2.6}, l);
        }
        for (double x = x0 + 0.3; x < x1 - 0.3; x += 0.65) {
            boite(tr, x - 0.13, haut, -1.7, x + 0.13, haut + 0.14, 1.7, l);
        }
        for (double z : new double[]{-f, f}) {
            for (double x = x0; x < x1 - 1e-6; x += 2.0) {
                double xb = Math.min(x + 2, x1);
                boite(ra, x, haut + 0.14, z - 0.05, xb, 0f, z + 0.05, l);
            }
        }
    }
}
