package fr.heralisz.utils.train;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.utils.HeraliszUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
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
 * passages à niveau. Découpée en tronçons de 32 blocs, chacun dans un tampon de sommets (comme un morceau de chunk :
 * format BLOCK, shader « cutout » du terrain, lumière du monde lue à chaque pas, textures de l'atlas des blocs) ; les
 * tronçons proches sont construits à la demande, rafraîchis en continu (lumière), libérés au loin.
 */
public final class VoieRendu {
    private static final int TRONCON = 32;
    private static final float TRAVERSE = 0.65f;          // entraxe des traverses
    private static final Map<Integer, Troncon> TRONCONS = new HashMap<>();
    private static int rafraichi;
    private static ClientLevel monde;

    private VoieRendu() {}

    private static final class Troncon {
        VertexBuffer vbo;
        final AABB boite;
        final BlockPos origine;
        long construit = -1;
        int montages;

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
            // refait 2 fois peu après le premier montage (la lumière des chunks arrive après eux), puis en tourniquet
            boolean perime = t.construit < 0 || t.montages < 3 && maintenant - t.construit > 30L * t.montages
                    || maintenant - t.construit > 100 && rafraichi == k;
            if (perime && constructions < 2 && charge(mc.level, t.boite)) {
                construire(r, k, t, mc.level);
                t.construit = maintenant;
                t.montages++;
                constructions++;
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

    /** Repère local en un point du tracé : P (dessus du ballast), T (tangente horizontale), N (à droite). */
    private static final class Repere {
        double px, py, pz, tx, tz, nx, nz;
        int lumiere;
        byte genre;
    }

    private static void repere(Reseau r, double sv, Repere o, ClientLevel lvl) {
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
        o.genre = r.genre(sv);
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
        int i0 = k * TRONCON;
        double s0 = r.s[i0], s1 = r.s[Math.min(r.n, i0 + TRONCON)];
        double ox = t.origine.getX(), oy = t.origine.getY(), oz = t.origine.getZ();
        TextureAtlasSprite ballast = sprite("voie_ballast"), traverse = sprite("voie_traverse"), rail = sprite("voie_rail"),
                platelage = sprite("voie_passage");
        Repere a = new Repere(), b = new Repere();
        // ballast et rails : pas de 1 bloc (0,5 dans les courbes serrées)
        int npas = (int) Math.ceil((s1 - s0) / (r.rayon(s0 + (s1 - s0) / 2) < 200 ? 0.5 : 1.0));
        double ds = (s1 - s0) / npas;
        repere(r, s0, a, lvl);
        for (int m = 0; m < npas; m++) {
            repere(r, s0 + (m + 1) * ds, b, lvl);
            if (a.genre == Reseau.PASSAGE) {
                platelage(bb, a, b, ox, oy, oz, platelage);
            } else {
                prisme(bb, a, b, ox, oy, oz, ballast);
            }
            for (int c = -1; c <= 1; c += 2) railProfile(bb, a, b, c * Reseau.FILE, ox, oy, oz, rail);
            Repere x = a;
            a = b;
            b = x;
        }
        // traverses (et selles), alignées sur une grille de l'abscisse : pas de raccord visible entre tronçons
        double premiere = Math.ceil(s0 / TRAVERSE) * TRAVERSE;
        Repere q = new Repere();
        for (double sv = premiere; sv < s1; sv += TRAVERSE) {
            repere(r, sv, q, lvl);
            if (q.genre == Reseau.PASSAGE) continue;
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

    /** Quadrilatère : 4 coins (x,y,z) dans l'ordre, normale, uv dans le sprite (0..16). */
    private static void quad(BufferBuilder bb, double[] q, double ox, double oy, double oz, TextureAtlasSprite sp, float[] uv,
                             int lum0, int lum1, int argb) {
        double ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
        double vx = q[9] - q[0], vy = q[10] - q[1], vz = q[11] - q[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9) return;
        float fx = (float) (nx / l), fy = (float) (ny / l), fz = (float) (nz / l);
        float sh = ombre(fx, fy, fz);
        int[] lum = {lum0, lum1, lum1, lum0};
        for (int c = 0; c < 4; c++) {
            sommet(bb, q[c * 3], q[c * 3 + 1], q[c * 3 + 2], ox, oy, oz, sh, argb, sp.getU(uv[c * 2]), sp.getV(uv[c * 2 + 1]), lum[c], fx, fy, fz);
        }
    }

    private static double[] pt(Repere r, double lat, double h) {
        return new double[]{r.px + r.nx * lat, r.py + h, r.pz + r.nz * lat};
    }

    private static double[] q4(double[] a, double[] b, double[] c, double[] d) {
        return new double[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]};
    }

    /** Prisme de ballast entre deux repères : dessus en trois bandes (texture non étirée), flancs en pente. Sur un pont,
     *  flancs droits posés sur le tablier. */
    private static void prisme(BufferBuilder bb, Repere a, Repere b, double ox, double oy, double oz, TextureAtlasSprite sp) {
        boolean pont = a.genre == Reseau.PONT;
        double haut = 2.05, bas = pont ? 2.25 : 3.9, prof = pont ? (a.py - Math.floor(a.py - 0.25)) : 1.45;
        double[] lats = {-haut, -0.7, 0.7, haut};
        float vlen = 16f;
        for (int k = 0; k < 3; k++) {
            float u0 = (float) (k * 16 / 3.0), u1 = (float) ((k + 1) * 16 / 3.0);
            quad(bb, q4(pt(a, lats[k], 0), pt(a, lats[k + 1], 0), pt(b, lats[k + 1], 0), pt(b, lats[k], 0)), ox, oy, oz, sp,
                    new float[]{u0, 0, u1, 0, u1, vlen, u0, vlen}, a.lumiere, b.lumiere, 0xFFFFFFFF);
        }
        double profB = pont ? (b.py - Math.floor(b.py - 0.25)) : 1.45;
        // flanc droit (lat > 0) puis gauche
        quad(bb, q4(pt(a, haut, 0), pt(a, bas, -prof), pt(b, bas, -profB), pt(b, haut, 0)), ox, oy, oz, sp,
                new float[]{0, 0, 16, 0, 16, 16, 0, 16}, a.lumiere, b.lumiere, 0xFFE0E0E0);
        quad(bb, q4(pt(b, -haut, 0), pt(b, -bas, -profB), pt(a, -bas, -prof), pt(a, -haut, 0)), ox, oy, oz, sp,
                new float[]{0, 0, 16, 0, 16, 16, 0, 16}, b.lumiere, a.lumiere, 0xFFE0E0E0);
    }

    /** Passage à niveau : platelage entre et de part et d'autre des rails, au ras du champignon. */
    private static void platelage(BufferBuilder bb, Repere a, Repere b, double ox, double oy, double oz, TextureAtlasSprite sp) {
        double h = Reseau.RAIL - 0.01;
        double[][] bandes = {{-2.5, -Reseau.FILE - 0.05}, {-Reseau.FILE + 0.05, Reseau.FILE - 0.05}, {Reseau.FILE + 0.05, 2.5}};
        for (double[] w : bandes) {
            quad(bb, q4(pt(a, w[0], h), pt(a, w[1], h), pt(b, w[1], h), pt(b, w[0], h)), ox, oy, oz, sp,
                    new float[]{0, 0, 16, 0, 16, 16, 0, 16}, a.lumiere, b.lumiere, 0xFFFFFFFF);
        }
    }

    /** Un rail (axe à lat) : champignon (dessus poli), âme, patin. Profil balayé entre deux repères. */
    private static void railProfile(BufferBuilder bb, Repere a, Repere b, double lat, double ox, double oy, double oz, TextureAtlasSprite sp) {
        double pied = 0.165, ame0 = 0.185, tete0 = 0.275, top = Reseau.RAIL;
        double wp = 0.075, wa = 0.016, wt = 0.036;
        // dessus du champignon (bande claire du sprite : v 0..3)
        quad(bb, q4(pt(a, lat - wt, top), pt(a, lat + wt, top), pt(b, lat + wt, top), pt(b, lat - wt, top)), ox, oy, oz, sp,
                new float[]{0, 0, 16, 0, 16, 3, 0, 3}, a.lumiere, b.lumiere, 0xFFFFFFFF);
        // flancs du champignon
        side(bb, a, b, lat + wt, tete0, top, ox, oy, oz, sp, 3, 6, true);
        side(bb, a, b, lat - wt, tete0, top, ox, oy, oz, sp, 3, 6, false);
        // dessous du champignon (ombre)
        quad(bb, q4(pt(a, lat + wt, tete0), pt(a, lat - wt, tete0), pt(b, lat - wt, tete0), pt(b, lat + wt, tete0)), ox, oy, oz, sp,
                new float[]{0, 6, 16, 6, 16, 8, 0, 8}, a.lumiere, b.lumiere, 0xFF707070);
        // âme
        side(bb, a, b, lat + wa, ame0, tete0, ox, oy, oz, sp, 8, 12, true);
        side(bb, a, b, lat - wa, ame0, tete0, ox, oy, oz, sp, 8, 12, false);
        // patin : dessus et chants
        quad(bb, q4(pt(a, lat - wp, ame0), pt(a, lat + wp, ame0), pt(b, lat + wp, ame0), pt(b, lat - wp, ame0)), ox, oy, oz, sp,
                new float[]{0, 12, 16, 12, 16, 15, 0, 15}, a.lumiere, b.lumiere, 0xFFFFFFFF);
        side(bb, a, b, lat + wp, pied, ame0, ox, oy, oz, sp, 15, 16, true);
        side(bb, a, b, lat - wp, pied, ame0, ox, oy, oz, sp, 15, 16, false);
    }

    private static void side(BufferBuilder bb, Repere a, Repere b, double lat, double y0, double y1, double ox, double oy, double oz,
                             TextureAtlasSprite sp, float v0, float v1, boolean droite) {
        double[] q = droite ? q4(pt(a, lat, y1), pt(a, lat, y0), pt(b, lat, y0), pt(b, lat, y1))
                : q4(pt(b, lat, y1), pt(b, lat, y0), pt(a, lat, y0), pt(a, lat, y1));
        quad(bb, q, ox, oy, oz, sp, new float[]{0, v0, 0, v1, 16, v1, 16, v0}, droite ? a.lumiere : b.lumiere, droite ? b.lumiere : a.lumiere, 0xFFFFFFFF);
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
        quad(bb, q4(c[7], c[6], c[5], c[4]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 16, 0, 16}, lu, lu, argb);
        quad(bb, q4(c[4], c[5], c[1], c[0]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 3, 0, 3}, lu, lu, argb);
        quad(bb, q4(c[6], c[7], c[3], c[2]), ox, oy, oz, sp, new float[]{0, 0, 16, 0, 16, 3, 0, 3}, lu, lu, argb);
        quad(bb, q4(c[5], c[6], c[2], c[1]), ox, oy, oz, sp, new float[]{0, 0, 3, 0, 3, 3, 0, 3}, lu, lu, argb);
        quad(bb, q4(c[7], c[4], c[0], c[3]), ox, oy, oz, sp, new float[]{0, 0, 3, 0, 3, 3, 0, 3}, lu, lu, argb);
    }
}
