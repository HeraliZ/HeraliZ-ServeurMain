package fr.heralisz.utils.train;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.utils.HeraliszUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * La voie, dessinée par le mod (des courbes de 70 à 1000 blocs de rayon ne se font pas avec des blocs) : ballast en
 * prisme (il cache les marches des blocs du remblai), traverses de bois, selles, deux rails profilés, platelage des
 * passages à niveau. Découpée en tronçons de 32 points du tracé, chacun dans un tampon de sommets (comme un morceau de
 * chunk : format BLOCK, shader « cutout » du terrain, lumière du monde lue à chaque pas, textures de l'atlas des blocs) ;
 * les tronçons proches sont construits à la demande, rafraîchis en continu (lumière), libérés au loin.
 * <p>
 * Raccords (11/10) : un tronçon est construit sur les points du tracé eux-mêmes (un pas par segment, deux dans les
 * courbes serrées d'après le rayon du segment), la texture repart à chaque point, et la lumière d'un coin est celle de
 * SON bout de la voie (avant : les deux coins d'un même bout prenaient chacun la lumière d'un bout différent, d'où des
 * marches de lumière en dents de scie sur les rails et le ballast). Deux tronçons voisins ont donc exactement le même
 * repère, la même texture et, après reconstruction du voisin si la lumière a changé entre leurs constructions, la
 * même lumière à leur raccord. Le pied du ballast passe en douceur du talus au pont (avant : il sautait de 3,9 à 2,25
 * blocs de l'axe, en laissant un trou) ; les bouts du platelage et d'une portion cachée sont fermés.
 * <p>
 * Retouches du staff (/train voie, Retouches) : genre dessiné de chaque point, ou rien ; elles ne changent pas le tracé.
 */
public final class VoieRendu {
    private static final int TRONCON = 32;
    private static final double TRAVERSE = 0.65;          // entraxe des traverses
    /** Ballast : demi-largeur du dessus ; pied du talus hors pont (demi-largeur, profondeur sous le dessus) ; pied sur
     *  un pont (demi-largeur ; la profondeur va jusqu'au tablier). */
    private static final double HAUT = 2.05, BAS_SOL = 3.9, PROF_SOL = 1.45, BAS_PONT = 2.25;
    /** Demi-largeur et hauteur (au-dessus du ballast) du platelage des passages à niveau. */
    private static final double PLATELAGE = 2.5, H_PLATELAGE = Reseau.RAIL - 0.01;
    private static final Map<Integer, Troncon> TRONCONS = new HashMap<>();
    private static int rafraichi;
    private static ClientLevel monde;
    /** Retouches reçues du serveur (pour le réseau nommé) et genre dessiné de chaque point qui en découle (null : aucune). */
    private static String retouchesReseau = "";
    private static List<Retouches.Retouche> retouches = List.of();
    private static byte[] table;
    private static Reseau tableReseau;
    private static boolean tableFaite;

    private VoieRendu() {}

    private static final class Troncon {
        VertexBuffer vbo;
        final AABB boite;
        final BlockPos origine;
        long construit = -1;
        int montages;
        /** Lumière au premier et au dernier point lors de la dernière construction ; à refaire (raccord du voisin). */
        int lumDebut = -1, lumFin = -1;
        boolean refaire;

        Troncon(AABB boite, BlockPos origine) {
            this.boite = boite;
            this.origine = origine;
        }
    }

    public static void vider() {
        TRONCONS.values().forEach(t -> {
            if (t.vbo != null) t.vbo.close();
        });
        TRONCONS.clear();
    }

    /** Retouches de la voie envoyées par le serveur (paquet Train.Voie) : la voie est redessinée si elles changent. */
    public static void retouches(String reseau, List<Retouches.Retouche> liste) {
        if (reseau.equals(retouchesReseau) && liste.equals(retouches)) return;
        retouchesReseau = reseau;
        retouches = List.copyOf(liste);
        tableFaite = false;
        Minecraft.getInstance().execute(VoieRendu::vider);
    }

    /** Déconnexion : les retouches de ce serveur sont oubliées. */
    public static void oublier() {
        retouchesReseau = "";
        retouches = List.of();
        tableFaite = false;
        table = null;
        vider();
    }

    private static byte[] table(Reseau r) {
        if (!tableFaite || tableReseau != r) {
            tableReseau = r;
            tableFaite = true;
            table = retouchesReseau.equals(Reseau.nom()) ? Retouches.table(r, retouches) : null;
        }
        return table;
    }

    /** Appelé à l'étape « après les blocs cutout » : même état de rendu que les chunks. */
    public static void dessiner(PoseStack ps, Matrix4f proj, Vec3 cam, Frustum frustum) {
        Reseau r = Reseau.get();
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.level == null) return;
        if (mc.level != monde) {
            vider();
            monde = mc.level;
        }
        double portee = mc.options.getEffectiveRenderDistance() * 16.0;
        int nt = (r.n + TRONCON - 1) / TRONCON;
        long maintenant = mc.level.getGameTime();
        int constructions = 0;
        RenderType rt = RenderType.cutout();
        boolean pret = false;
        ShaderInstance sh = null;
        for (int k = 0; k < nt; k++) {
            Troncon t = TRONCONS.get(k);
            if (t == null) {
                t = boite(r, k);
                TRONCONS.put(k, t);
            }
            double d = Math.sqrt(t.boite.distanceToSqr(cam));
            if (d > portee + 48) {
                if (t.vbo != null) {
                    t.vbo.close();
                    t.vbo = null;
                    t.construit = -1;
                    t.montages = 0;
                }
                continue;
            }
            if (d > portee || !frustum.isVisible(t.boite)) continue;
            // refait 2 fois peu après le premier montage (la lumière des chunks arrive après eux), puis en tourniquet,
            // et tout de suite si un voisin a vu une autre lumière à leur raccord
            boolean perime = t.construit < 0 || t.refaire || t.montages < 3 && maintenant - t.construit > 30L * t.montages
                    || maintenant - t.construit > 100 && rafraichi == k;
            if (perime && constructions < 2 && charge(mc.level, t.boite)) {
                construire(r, k, t, mc.level);
                t.construit = maintenant;
                t.montages++;
                constructions++;
                raccorder(k, t, nt);
            }
            if (t.vbo == null) continue;
            if (!pret) {
                rt.setupRenderState();
                sh = RenderSystem.getShader();
                if (sh == null) {
                    rt.clearRenderState();
                    return;
                }
                for (int i = 0; i < 12; ++i) sh.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
                if (sh.MODEL_VIEW_MATRIX != null) sh.MODEL_VIEW_MATRIX.set(ps.last().pose());
                if (sh.PROJECTION_MATRIX != null) sh.PROJECTION_MATRIX.set(proj);
                if (sh.COLOR_MODULATOR != null) sh.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
                if (sh.FOG_START != null) sh.FOG_START.set(RenderSystem.getShaderFogStart());
                if (sh.FOG_END != null) sh.FOG_END.set(RenderSystem.getShaderFogEnd());
                if (sh.FOG_COLOR != null) sh.FOG_COLOR.set(RenderSystem.getShaderFogColor());
                if (sh.FOG_SHAPE != null) sh.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
                if (sh.TEXTURE_MATRIX != null) sh.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
                if (sh.GAME_TIME != null) sh.GAME_TIME.set(RenderSystem.getShaderGameTime());
                RenderSystem.setupShaderLights(sh);
                sh.apply();
                pret = true;
            }
            if (sh.CHUNK_OFFSET != null) {
                sh.CHUNK_OFFSET.set((float) (t.origine.getX() - cam.x), (float) (t.origine.getY() - cam.y), (float) (t.origine.getZ() - cam.z));
                sh.CHUNK_OFFSET.upload();
            }
            t.vbo.bind();
            t.vbo.draw();
        }
        rafraichi = (rafraichi + 1) % Math.max(1, nt);
        if (pret) {
            if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0f, 0f, 0f);
            sh.clear();
            VertexBuffer.unbind();
            rt.clearRenderState();
        }
    }

    /** Un tronçon vient d'être construit : un voisin construit avec une autre lumière au point qu'ils partagent est à
     *  refaire (sinon une marche de lumière reste au raccord jusqu'à son prochain tour). */
    private static void raccorder(int k, Troncon t, int nt) {
        Troncon av = TRONCONS.get(Math.floorMod(k - 1, nt)), ap = TRONCONS.get(Math.floorMod(k + 1, nt));
        if (av != null && av != t && av.vbo != null && av.lumFin != t.lumDebut) av.refaire = true;
        if (ap != null && ap != t && ap.vbo != null && ap.lumDebut != t.lumFin) ap.refaire = true;
    }

    private static boolean charge(ClientLevel lvl, AABB b) {
        int x0 = ((int) Math.floor(b.minX)) >> 4, x1 = ((int) Math.floor(b.maxX)) >> 4;
        int z0 = ((int) Math.floor(b.minZ)) >> 4, z1 = ((int) Math.floor(b.maxZ)) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (!lvl.getChunkSource().hasChunk(cx, cz)) return false;
        return true;
    }

    private static Troncon boite(Reseau r, int k) {
        int i0 = k * TRONCON, i1 = Math.min(r.n, i0 + TRONCON);
        double x0 = 1e9, y0 = 1e9, z0 = 1e9, x1 = -1e9, y1 = -1e9, z1 = -1e9;
        for (int i = i0; i <= i1; i++) {
            int j = i % r.n;
            x0 = Math.min(x0, r.x[j]);
            y0 = Math.min(y0, r.y[j]);
            z0 = Math.min(z0, r.z[j]);
            x1 = Math.max(x1, r.x[j]);
            y1 = Math.max(y1, r.y[j]);
            z1 = Math.max(z1, r.z[j]);
        }
        return new Troncon(new AABB(x0 - 4, y0 - 2, z0 - 4, x1 + 4, y1 + 1, z1 + 4),
                BlockPos.containing(r.x[i0], r.y[i0], r.z[i0]));
    }

    // ------------------------------------------------------------------------------------------------ géométrie

    private static TextureAtlasSprite sprite(String nom) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(HeraliszUtils.MODID, "block/" + nom));
    }

    private static BufferBuilder tampon;

    /** Repère local en un point du tracé : P (dessus du ballast), T (tangente horizontale), N (à droite), lumière, et le
     *  pied du ballast à cet endroit (demi-largeur, profondeur sous le dessus). */
    private static final class Repere {
        double px, py, pz, tx, tz, nx, nz, bas, prof;
        int lumiere;
    }

    private static boolean pont(Reseau r, byte[] tab, int i) {
        return Retouches.genre(r, tab, i) == Reseau.PONT;
    }

    /** Part de pont autour du point i (les deux segments de chaque côté) : 1 au milieu d'un pont, 0 loin de tout pont. */
    private static double pontPoint(Reseau r, byte[] tab, int i) {
        int n = 0;
        for (int d = -2; d <= 1; d++) if (pont(r, tab, i + d)) n++;
        return n / 4.0;
    }

    /** Même calcul pour n'importe quelle abscisse (interpolée entre les deux points du segment) : il ne dépend que de
     *  sv, donc deux tronçons voisins trouvent le même pied au point qu'ils partagent. */
    private static double pontNess(Reseau r, byte[] tab, double sv) {
        double m = r.mod(sv);
        int i = r.segment(m);
        double f = (m - r.s[i]) / Math.max(1e-9, r.s[i + 1] - r.s[i]);
        f = Math.max(0, Math.min(1, f));
        return pontPoint(r, tab, i) * (1 - f) + pontPoint(r, tab, i + 1) * f;
    }

    private static void repere(Reseau r, byte[] tab, double sv, Repere o, ClientLevel lvl) {
        double[] p = new double[3], a = new double[3], b = new double[3];
        r.point(sv, p);
        r.point(sv + 0.75, a);
        r.point(sv - 0.75, b);
        double tx = a[0] - b[0], tz = a[2] - b[2], l = Math.sqrt(tx * tx + tz * tz);
        o.px = p[0];
        o.py = p[1];
        o.pz = p[2];
        o.tx = tx / l;
        o.tz = tz / l;
        o.nx = -o.tz;
        o.nz = o.tx;
        // pied du ballast : talus sur le sol, flancs droits jusqu'au tablier sur un pont, en douceur entre les deux
        double w = pontNess(r, tab, sv);
        double profPont = p[1] - Math.floor(p[1] - 0.25);
        o.bas = BAS_SOL + (BAS_PONT - BAS_SOL) * w;
        o.prof = PROF_SOL + (profPont - PROF_SOL) * w;
        o.lumiere = lumiere(lvl, p[0], p[1], p[2], o.nx, o.nz);
    }

    /** Lumière d'un point de la voie : le maximum sur l'axe et les deux rails, à 0,8 et 1,8 au-dessus du ballast (un
     *  bloc plein voisin ne l'éteint plus). Lumière du ciel encore nulle sous un ciel ouvert (chunk pas encore
     *  éclairé par le client) : pleine (15), le tronçon sera refait de toute façon. */
    private static int lumiere(ClientLevel lvl, double x, double y, double z, double nx, double nz) {
        int bloc = 0, ciel = 0;
        for (double lat : new double[]{0, -Reseau.FILE, Reseau.FILE}) {
            for (double h : new double[]{0.8, 1.8}) {
                BlockPos bp = BlockPos.containing(x + nx * lat, y + h, z + nz * lat);
                bloc = Math.max(bloc, lvl.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bp));
                ciel = Math.max(ciel, lvl.getBrightness(net.minecraft.world.level.LightLayer.SKY, bp));
            }
        }
        if (ciel == 0 && lvl.canSeeSky(BlockPos.containing(x, y + 1.8, z))) ciel = 15;
        return net.minecraft.client.renderer.LightTexture.pack(bloc, ciel);
    }

    private static void construire(Reseau r, int k, Troncon t, ClientLevel lvl) {
        if (tampon == null) tampon = new BufferBuilder(1 << 16);
        BufferBuilder bb = tampon;
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        byte[] tab = table(r);
        int i0 = k * TRONCON, i1 = Math.min(r.n, i0 + TRONCON);
        double ox = t.origine.getX(), oy = t.origine.getY(), oz = t.origine.getZ();
        TextureAtlasSprite ballast = sprite("voie_ballast"), traverse = sprite("voie_traverse"), rail = sprite("voie_rail"),
                platelage = sprite("voie_passage");
        t.refaire = false;
        Repere a = new Repere(), b = new Repere();
        repere(r, tab, r.s[i0], a, lvl);
        t.lumDebut = a.lumiere;
        // ballast (ou platelage) et rails : un pas par segment du tracé, deux dans les courbes serrées ; la texture
        // couvre exactement un segment (v de 0 à 16), quel que soit le tronçon
        byte avant = Retouches.genre(r, tab, i0 - 1);
        for (int i = i0; i < i1; i++) {
            byte g = Retouches.genre(r, tab, i);
            if (g != avant) bouts(bb, a, avant, g, ox, oy, oz, ballast, platelage);
            int sous = r.rayon[i] < 200 ? 2 : 1;
            double sa = r.s[i], sb = r.s[i + 1];
            for (int m = 1; m <= sous; m++) {
                repere(r, tab, m == sous ? sb : sa + (sb - sa) * m / sous, b, lvl);
                if (g != Retouches.CACHE) {
                    float v0 = 16f * (m - 1) / sous, v1 = 16f * m / sous;
                    if (g == Reseau.PASSAGE) {
                        platelage(bb, a, b, ox, oy, oz, platelage, v0, v1);
                    } else {
                        prisme(bb, a, b, ox, oy, oz, ballast, v0, v1);
                    }
                    for (int c = -1; c <= 1; c += 2) railProfile(bb, a, b, c * Reseau.FILE, ox, oy, oz, rail, v0, v1);
                }
                Repere x = a;
                a = b;
                b = x;
            }
            avant = g;
        }
        t.lumFin = a.lumiere;
        // traverses (et selles) sur une grille fixe de l'abscisse (j × entraxe, sans cumul d'arrondis) : chacune est
        // dessinée par un seul tronçon, sans trou ni doublon aux raccords
        double s0 = r.s[i0], s1 = r.s[i1];
        Repere q = new Repere();
        for (long j = (long) Math.ceil(s0 / TRAVERSE); j * TRAVERSE < s1; j++) {
            double sv = j * TRAVERSE;
            if (i1 == r.n && r.longueur - sv < TRAVERSE / 2) break;          // la boucle se referme : celle de s = 0 suffit
            byte g = Retouches.genre(r, tab, r.segment(r.mod(sv)));
            if (g == Reseau.PASSAGE || g == Retouches.CACHE) continue;
            repere(r, tab, sv, q, lvl);
            boite(bb, q, 0, 1.7f, 0.13f, -0.05f, 0.14f, ox, oy, oz, traverse, 0xFFFFFFFF);
            for (int c = -1; c <= 1; c += 2) {
                boite(bb, q, c * Reseau.FILE, 0.19f, 0.10f, 0.14f, 0.165f, ox, oy, oz, rail, 0xFF8A8A8A);
            }
        }
        BufferBuilder.RenderedBuffer rb = bb.end();
        if (t.vbo == null) t.vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        t.vbo.bind();
        t.vbo.upload(rb);
        VertexBuffer.unbind();
    }

    /** Ombrage vanilla selon la normale (dessus 1, dessous 0,5, nord/sud 0,8, est/ouest 0,6). */
    private static float ombre(float nx, float ny, float nz) {
        return (ny >= 0 ? ny * ny : ny * ny * 0.5f) + nx * nx * 0.6f + nz * nz * 0.8f;
    }

    private static void sommet(BufferBuilder bb, double x, double y, double z, double ox, double oy, double oz, float sh, int argb,
                               float u, float v, int lum, float nx, float ny, float nz) {
        int rr = (int) (((argb >> 16) & 255) * sh), gg = (int) (((argb >> 8) & 255) * sh), bl = (int) ((argb & 255) * sh);
        bb.vertex((float) (x - ox), (float) (y - oy), (float) (z - oz)).color(rr, gg, bl, 255).uv(u, v).uv2(lum).normal(nx, ny, nz).endVertex();
    }

    /** Quadrilatère : 4 coins (x,y,z) dans l'ordre, normale, uv dans le sprite (0..16), lumière de chaque coin. */
    private static void quad(BufferBuilder bb, double[] q, double ox, double oy, double oz, TextureAtlasSprite sp, float[] uv,
                             int l0, int l1, int l2, int l3, int argb) {
        double ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
        double vx = q[9] - q[0], vy = q[10] - q[1], vz = q[11] - q[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9) return;
        float fx = (float) (nx / l), fy = (float) (ny / l), fz = (float) (nz / l);
        float sh = ombre(fx, fy, fz);
        int[] lum = {l0, l1, l2, l3};
        for (int c = 0; c < 4; c++) {
            sommet(bb, q[c * 3], q[c * 3 + 1], q[c * 3 + 2], ox, oy, oz, sh, argb, sp.getU(uv[c * 2]), sp.getV(uv[c * 2 + 1]), lum[c], fx, fy, fz);
        }
    }

    /** Quadrilatère dont les deux premiers coins sont au bout a de la voie et les deux derniers au bout b. */
    private static void quadAB(BufferBuilder bb, double[] q, double ox, double oy, double oz, TextureAtlasSprite sp, float[] uv,
                               Repere a, Repere b, int argb) {
        quad(bb, q, ox, oy, oz, sp, uv, a.lumiere, a.lumiere, b.lumiere, b.lumiere, argb);
    }

    private static double[] pt(Repere r, double lat, double h) {
        return new double[]{r.px + r.nx * lat, r.py + h, r.pz + r.nz * lat};
    }

    private static double[] q4(double[] a, double[] b, double[] c, double[] d) {
        return new double[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]};
    }

    /** Prisme de ballast entre deux repères : dessus en trois bandes (texture non étirée), flancs jusqu'au pied de chaque
     *  repère (talus en pente sur le sol, flancs droits posés sur le tablier d'un pont). v0, v1 : texture le long. */
    private static void prisme(BufferBuilder bb, Repere a, Repere b, double ox, double oy, double oz, TextureAtlasSprite sp,
                               float v0, float v1) {
        double[] lats = {-HAUT, -0.7, 0.7, HAUT};
        for (int k = 0; k < 3; k++) {
            float u0 = (float) (k * 16 / 3.0), u1 = (float) ((k + 1) * 16 / 3.0);
            quadAB(bb, q4(pt(a, lats[k], 0), pt(a, lats[k + 1], 0), pt(b, lats[k + 1], 0), pt(b, lats[k], 0)), ox, oy, oz, sp,
                    new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, a, b, 0xFFFFFFFF);
        }
        // flanc droit (lat > 0) puis gauche
        quadAB(bb, q4(pt(a, HAUT, 0), pt(a, a.bas, -a.prof), pt(b, b.bas, -b.prof), pt(b, HAUT, 0)), ox, oy, oz, sp,
                new float[]{0, v0, 16, v0, 16, v1, 0, v1}, a, b, 0xFFE0E0E0);
        quadAB(bb, q4(pt(b, -HAUT, 0), pt(b, -b.bas, -b.prof), pt(a, -a.bas, -a.prof), pt(a, -HAUT, 0)), ox, oy, oz, sp,
                new float[]{0, v1, 16, v1, 16, v0, 0, v0}, b, a, 0xFFE0E0E0);
    }

    /** Passage à niveau : platelage entre et de part et d'autre des rails, au ras du champignon. */
    private static void platelage(BufferBuilder bb, Repere a, Repere b, double ox, double oy, double oz, TextureAtlasSprite sp,
                                  float v0, float v1) {
        double h = H_PLATELAGE;
        double[][] bandes = {{-PLATELAGE, -Reseau.FILE - 0.05}, {-Reseau.FILE + 0.05, Reseau.FILE - 0.05}, {Reseau.FILE + 0.05, PLATELAGE}};
        for (double[] w : bandes) {
            quadAB(bb, q4(pt(a, w[0], h), pt(a, w[1], h), pt(b, w[1], h), pt(b, w[0], h)), ox, oy, oz, sp,
                    new float[]{0, v0, 16, v0, 16, v1, 0, v1}, a, b, 0xFFFFFFFF);
        }
    }

    /** Raccord entre deux genres au repère x (avant : genre du segment qui précède, apres : celui qui suit) : on ferme
     *  le bout de ce qui est dessiné d'un côté et pas (ou moins haut) de l'autre — coupe du ballast face à un passage à
     *  niveau ou à une portion cachée, chant du platelage face au ballast ou à une portion cachée. */
    private static void bouts(BufferBuilder bb, Repere x, byte avant, byte apres, double ox, double oy, double oz,
                              TextureAtlasSprite ballast, TextureAtlasSprite planches) {
        bout(bb, x, avant, apres, -1, ox, oy, oz, ballast, planches);
        bout(bb, x, apres, avant, 1, ox, oy, oz, ballast, planches);
    }

    /** Bout du côté (genre g) qui regarde vers l'autre (genre autre) ; sens -1 : g est avant x, la face regarde vers
     *  l'avant (+T) ; sens 1 : g est après x, la face regarde vers l'arrière (-T). */
    private static void bout(BufferBuilder bb, Repere x, byte g, byte autre, int sens, double ox, double oy, double oz,
                             TextureAtlasSprite ballast, TextureAtlasSprite planches) {
        if (g == Retouches.CACHE) return;
        double dx = -sens * x.tx, dz = -sens * x.tz;
        if (g == Reseau.PASSAGE) {
            if (autre == Reseau.PASSAGE) return;
            double[] q = q4(pt(x, -PLATELAGE, H_PLATELAGE), pt(x, PLATELAGE, H_PLATELAGE), pt(x, PLATELAGE, 0), pt(x, -PLATELAGE, 0));
            face(bb, q, dx, dz, ox, oy, oz, planches, new float[]{0, 0, 16, 0, 16, 2, 0, 2}, x.lumiere);
        } else if (autre == Reseau.PASSAGE || autre == Retouches.CACHE) {
            double[] q = q4(pt(x, -HAUT, 0), pt(x, HAUT, 0), pt(x, x.bas, -x.prof), pt(x, -x.bas, -x.prof));
            face(bb, q, dx, dz, ox, oy, oz, ballast, new float[]{4, 0, 12, 0, 16, 8, 0, 8}, x.lumiere);
        }
    }

    /** Quadrilatère tourné vers (dx, 0, dz) : l'ordre des coins est inversé s'il le faut (faces arrière non dessinées). */
    private static void face(BufferBuilder bb, double[] q, double dx, double dz, double ox, double oy, double oz, TextureAtlasSprite sp,
                             float[] uv, int lum) {
        double ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
        double vx = q[9] - q[0], vy = q[10] - q[1], vz = q[11] - q[2];
        double nx = uy * vz - uz * vy, nz = ux * vy - uy * vx;
        if (nx * dx + nz * dz < 0) {
            q = new double[]{q[9], q[10], q[11], q[6], q[7], q[8], q[3], q[4], q[5], q[0], q[1], q[2]};
            uv = new float[]{uv[6], uv[7], uv[4], uv[5], uv[2], uv[3], uv[0], uv[1]};
        }
        quad(bb, q, ox, oy, oz, sp, uv, lum, lum, lum, lum, 0xFFE0E0E0);
    }

    /** Un rail (axe à lat) : champignon (dessus poli), âme, patin. Profil balayé entre deux repères ; ua, ub : texture le
     *  long du rail aux deux bouts. */
    private static void railProfile(BufferBuilder bb, Repere a, Repere b, double lat, double ox, double oy, double oz, TextureAtlasSprite sp,
                                    float ua, float ub) {
        double pied = 0.165, ame0 = 0.185, tete0 = 0.275, top = Reseau.RAIL;
        double wp = 0.075, wa = 0.016, wt = 0.036;
        // dessus du champignon (bande claire du sprite : v 0..3)
        quadAB(bb, q4(pt(a, lat - wt, top), pt(a, lat + wt, top), pt(b, lat + wt, top), pt(b, lat - wt, top)), ox, oy, oz, sp,
                new float[]{0, 0, 16, 0, 16, 3, 0, 3}, a, b, 0xFFFFFFFF);
        // flancs du champignon
        side(bb, a, b, lat + wt, tete0, top, ox, oy, oz, sp, 3, 6, true, ua, ub);
        side(bb, a, b, lat - wt, tete0, top, ox, oy, oz, sp, 3, 6, false, ua, ub);
        // dessous du champignon (ombre)
        quadAB(bb, q4(pt(a, lat + wt, tete0), pt(a, lat - wt, tete0), pt(b, lat - wt, tete0), pt(b, lat + wt, tete0)), ox, oy, oz, sp,
                new float[]{0, 6, 16, 6, 16, 8, 0, 8}, a, b, 0xFF707070);
        // âme
        side(bb, a, b, lat + wa, ame0, tete0, ox, oy, oz, sp, 8, 12, true, ua, ub);
        side(bb, a, b, lat - wa, ame0, tete0, ox, oy, oz, sp, 8, 12, false, ua, ub);
        // patin : dessus et chants
        quadAB(bb, q4(pt(a, lat - wp, ame0), pt(a, lat + wp, ame0), pt(b, lat + wp, ame0), pt(b, lat - wp, ame0)), ox, oy, oz, sp,
                new float[]{0, 12, 16, 12, 16, 15, 0, 15}, a, b, 0xFFFFFFFF);
        side(bb, a, b, lat + wp, pied, ame0, ox, oy, oz, sp, 15, 16, true, ua, ub);
        side(bb, a, b, lat - wp, pied, ame0, ox, oy, oz, sp, 15, 16, false, ua, ub);
    }

    private static void side(BufferBuilder bb, Repere a, Repere b, double lat, double y0, double y1, double ox, double oy, double oz,
                             TextureAtlasSprite sp, float v0, float v1, boolean droite, float ua, float ub) {
        if (droite) {
            quadAB(bb, q4(pt(a, lat, y1), pt(a, lat, y0), pt(b, lat, y0), pt(b, lat, y1)), ox, oy, oz, sp,
                    new float[]{ua, v0, ua, v1, ub, v1, ub, v0}, a, b, 0xFFFFFFFF);
        } else {
            quadAB(bb, q4(pt(b, lat, y1), pt(b, lat, y0), pt(a, lat, y0), pt(a, lat, y1)), ox, oy, oz, sp,
                    new float[]{ub, v0, ub, v1, ua, v1, ua, v0}, b, a, 0xFFFFFFFF);
        }
    }

    /** Boîte orientée sur le repère : centre latéral lat, demi-longueur (en travers) dl, demi-largeur (le long) dw,
     *  de h0 à h1 au-dessus du ballast. Dessus + 4 côtés. */
    private static void boite(BufferBuilder bb, Repere r, double lat, float dl, float dw, float h0, float h1, double ox, double oy, double oz,
                              TextureAtlasSprite sp, int argb) {
        double[][] c = new double[8][];
        int idx = 0;
        for (int hy = 0; hy < 2; hy++) {
            for (int[] s : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                double l = lat + s[0] * dl, w = s[1] * dw;
                c[idx++] = new double[]{r.px + r.nx * l + r.tx * w, r.py + (hy == 0 ? h0 : h1), r.pz + r.nz * l + r.tz * w};
            }
        }
        int lu = r.lumiere;
        // dessus (4,5,6,7) vu d'en haut, côtés
        quad(bb, q4(c[7], c[6], c[5], c[4]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 16, 0, 16}, lu, lu, lu, lu, argb);
        quad(bb, q4(c[4], c[5], c[1], c[0]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 3, 0, 3}, lu, lu, lu, lu, argb);
        quad(bb, q4(c[6], c[7], c[3], c[2]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 3, 0, 3}, lu, lu, lu, lu, argb);
        quad(bb, q4(c[5], c[6], c[2], c[1]), ox, oy, oz, sp, new float[]{0, 0, 3, 0, 3, 3, 0, 3}, lu, lu, lu, lu, argb);
        quad(bb, q4(c[7], c[4], c[0], c[3]), ox, oy, oz, sp, new float[]{0, 0, 3, 0, 3, 3, 0, 3}, lu, lu, lu, lu, argb);
    }
}
