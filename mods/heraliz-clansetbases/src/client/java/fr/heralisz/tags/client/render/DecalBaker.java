package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.tags.HeraliszTags;
import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.network.Packets;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Cuisson d'un tag en decalque (§2.3, projection en boite facon Source).
 * <ol>
 *   <li>Repere du tag : N = normale de la face touchee ; U = « haut » (verticale pour un mur, cap du tagueur transmis
 *       par le serveur pour un sol / plafond), tourne du roulis choisi par le serveur ; R = U × N (droite du
 *       spectateur).</li>
 *   <li>Boite : taille × taille (u, v ∈ [−½ t, ½ t]) × profondeur 1,0 (w ∈ [−½, ½]) centree sur le point.</li>
 *   <li>Pour chaque bloc dont la boite monde croise la boite : chaque AABB de getShape (escaliers, dalles, barrieres,
 *       murets suivent le relief), chaque face dont la normale fait &lt; 70° avec N, non recouverte (bloc voisin plein
 *       ou autre AABB de la meme forme) : coins exprimes dans le repere du tag, decoupes aux 6 plans de la boite
 *       (Sutherland–Hodgman), UV = (u/t + ½, ½ − v/t) remappees dans la case de l'atlas, decalage 0,004 le long de
 *       la normale de la face, lumiere (uv2, lue par le shader du DecalRenderer) echantillonnee dans le bloc devant la
 *       face, ombrage directionnel du monde.</li>
 *   <li>Resultat : un VertexBuffer POSITION_COLOR_TEX_LIGHTMAP (triangles en eventail) et une copie native des sommets
 *       pour les lots, positions relatives a l'origine de la cellule de 32 blocs du point.</li>
 * </ol>
 * Un seul BufferBuilder et des tableaux de travail statiques : la cuisson n'alloue que le tampon GPU et ses bornes.
 * Doit etre appele sur le thread de rendu (le tampon est televerse immediatement). Toute exception laisse le
 * BufferBuilder pret pour la cuisson suivante (try/finally + discard).
 */
public final class DecalBaker {
    private DecalBaker() {
    }

    /** Angle maximal entre la normale d'une face et celle du tag (cosinus de 70°). */
    private static final double COS_LIMITE = Math.cos(Math.toRadians(70));
    /** Demi-profondeur de la boite (profondeur 1,0 ; +0,01 de tolerance pour les reliefs d'exactement un demi-bloc). */
    private static final double DEMI_PROFONDEUR = 0.51;
    /** Decalage le long de la normale de la face (anti z-fighting). */
    private static final double DECALAGE = 0.004;
    private static final int MAX_SOMMETS = 16;
    /** Nombre maximal d'AABB d'une forme de bloc prise en compte (au-dela : bloc ignore). */
    private static final int MAX_BOITES = 64;

    private static final BufferBuilder BUILDER = new BufferBuilder(32 * 1024);
    // polygones de travail (u, v, w) : entree, sortie, et les 8 coins d'une AABB
    private static final double[] POLY_A = new double[MAX_SOMMETS * 3], POLY_B = new double[MAX_SOMMETS * 3];
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos(), POS2 = new BlockPos.MutableBlockPos();

    /** Statistiques de la derniere cuisson (banc d'essai). */
    public static int derniersTriangles, derniersBlocs;
    public static long derniereDureeNs;

    /**
     * Cuit le decalque d'un tag recu. Retourne null si rien n'est visible (tag inconnu, aucune face dans la boite).
     * Le cap du tagueur ({@code p.cap()}, envoye par le serveur au moment de la pose) oriente les tags au sol /
     * plafond : tous les spectateurs et la recuisson F3+T obtiennent la meme orientation.
     *
     * @param level monde client
     * @param p     paquet du tag
     */
    public static Decal cuire(Level level, Packets.TagS2C p) {
        long debut = System.nanoTime();
        TagDef def = TagCatalog.get(p.tagId());
        if (def == null || level == null) return null;
        Vec3 point = p.point();
        Direction face = p.face();
        double taille = def.taille();
        double h = taille / 2.0;

        // ---- repere du tag
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 haut0;
        if (face.getAxis() == Direction.Axis.Y) {
            // sol / plafond : le haut de l'image est la direction horizontale du regard du tagueur (nord si le cap
            // n'est pas un nombre)
            double yaw = Math.toRadians(Float.isFinite(p.cap()) ? p.cap() : 0f);
            haut0 = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            if (haut0.lengthSqr() < 1e-6) haut0 = new Vec3(0, 0, -1);
            else haut0 = haut0.normalize();
            if (face == Direction.DOWN) haut0 = haut0.scale(-1); // vu d'en dessous, l'image reste lisible
        } else {
            haut0 = new Vec3(0, 1, 0);
        }
        Vec3 droite0 = haut0.cross(n);
        double a = Math.toRadians(p.angle());
        Vec3 u = haut0.scale(Math.cos(a)).add(droite0.scale(Math.sin(a))).normalize();
        Vec3 r = u.cross(n).normalize();

        // ---- boite monde englobante
        double ex = Math.abs(r.x) * h + Math.abs(u.x) * h + Math.abs(n.x) * DEMI_PROFONDEUR;
        double ey = Math.abs(r.y) * h + Math.abs(u.y) * h + Math.abs(n.y) * DEMI_PROFONDEUR;
        double ez = Math.abs(r.z) * h + Math.abs(u.z) * h + Math.abs(n.z) * DEMI_PROFONDEUR;
        int x0 = (int) Math.floor(point.x - ex), x1 = (int) Math.floor(point.x + ex);
        int y0 = (int) Math.floor(point.y - ey), y1 = (int) Math.floor(point.y + ey);
        int z0 = (int) Math.floor(point.z - ez), z1 = (int) Math.floor(point.z + ez);

        float[] uv = def.uv();
        float pas = uv[2] - uv[0];
        double minX = point.x, minY = point.y, minZ = point.z, maxX = point.x, maxY = point.y, maxZ = point.z;
        // origine de la cellule de 32 blocs : les sommets lui sont relatifs (lots du DecalRenderer)
        double origX = DecalLot.origine(point.x), origY = DecalLot.origine(point.y), origZ = DecalLot.origine(point.z);
        int triangles = 0, blocs = 0;

        // tout echec (getShape d'un bloc tiers qui jette...) laisse le BUILDER propre : sinon la cuisson suivante
        // echouerait sur « Already building! » jusqu'au redemarrage
        BufferBuilder.RenderedBuffer rendu;
        BUILDER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        try {
            for (int bx = x0; bx <= x1; bx++) {
                for (int by = y0; by <= y1; by++) {
                    for (int bz = z0; bz <= z1; bz++) {
                        POS.set(bx, by, bz);
                        BlockState etat = level.getBlockState(POS);
                        if (etat.isAir() || etat.getRenderShape() == RenderShape.INVISIBLE) continue;
                        VoxelShape forme = etat.getShape(level, POS);
                        if (forme.isEmpty()) continue;
                        List<AABB> boites = forme.toAabbs();
                        if (boites.size() > MAX_BOITES) continue; // forme pathologique d'un bloc tiers : ignoree
                        blocs++;
                        for (int ib = 0; ib < boites.size(); ib++) {
                            AABB b = boites.get(ib);
                            for (Direction d : Direction.values()) {
                                Vec3 nf = Vec3.atLowerCornerOf(d.getNormal());
                                if (nf.dot(n) < COS_LIMITE) continue;
                                // face recouverte ? (par un voisin plein si elle est au bord du bloc, ou une autre AABB)
                                if (recouverte(level, POS, etat, forme, boites, ib, b, d)) continue;
                                // coins de la face en monde → repere du tag (u, v, w)
                                int ns = coinsFace(b, d, bx, by, bz, point, r, u, n, POLY_A);
                                // decoupe aux 6 plans de la boite
                                ns = decouper(POLY_A, ns, 0, -h, +1, POLY_B);
                                if (ns < 3) continue;
                                ns = decouper(POLY_B, ns, 0, h, -1, POLY_A);
                                if (ns < 3) continue;
                                ns = decouper(POLY_A, ns, 1, -h, +1, POLY_B);
                                if (ns < 3) continue;
                                ns = decouper(POLY_B, ns, 1, h, -1, POLY_A);
                                if (ns < 3) continue;
                                ns = decouper(POLY_A, ns, 2, -DEMI_PROFONDEUR, +1, POLY_B);
                                if (ns < 3) continue;
                                ns = decouper(POLY_B, ns, 2, DEMI_PROFONDEUR, -1, POLY_A);
                                if (ns < 3) continue;
                                // lumiere : bloc devant la face (centre de la face + ½ normale)
                                double cx = (b.minX + b.maxX) / 2 + bx + nf.x * (b.maxX - b.minX) / 2;
                                double cy = (b.minY + b.maxY) / 2 + by + nf.y * (b.maxY - b.minY) / 2;
                                double cz = (b.minZ + b.maxZ) / 2 + bz + nf.z * (b.maxZ - b.minZ) / 2;
                                int lumiere = lumiere(level, cx + nf.x * 0.5, cy + nf.y * 0.5, cz + nf.z * 0.5, POS, d);
                                float ombre = level.getShade(d, true);
                                int gris = Math.round(255 * ombre);
                                // sommets : repere du tag → monde relatif a l'origine de la cellule, + decalage le long de la
                                // normale de la face
                                double dx = nf.x * DECALAGE + point.x - origX, dy = nf.y * DECALAGE + point.y - origY, dz = nf.z * DECALAGE + point.z - origZ;
                                for (int i = 1; i + 1 < ns; i++) {
                                    for (int k = 0; k < 3; k++) {
                                        int idx = k == 0 ? 0 : k == 1 ? i : i + 1;
                                        double pu = POLY_A[idx * 3], pv = POLY_A[idx * 3 + 1], pw = POLY_A[idx * 3 + 2];
                                        double wx = pu * r.x + pv * u.x + pw * n.x;
                                        double wy = pu * r.y + pv * u.y + pw * n.y;
                                        double wz = pu * r.z + pv * u.z + pw * n.z;
                                        float tu = (float) (uv[0] + (pu / taille + 0.5) * pas);
                                        float tv = (float) (uv[1] + (0.5 - pv / taille) * pas);
                                        BUILDER.vertex((float) (wx + dx), (float) (wy + dy), (float) (wz + dz))
                                                .color(gris, gris, gris, 255).uv(tu, tv).uv2(lumiere).endVertex();
                                        minX = Math.min(minX, point.x + wx);
                                        minY = Math.min(minY, point.y + wy);
                                        minZ = Math.min(minZ, point.z + wz);
                                        maxX = Math.max(maxX, point.x + wx);
                                        maxY = Math.max(maxY, point.y + wy);
                                        maxZ = Math.max(maxZ, point.z + wz);
                                    }
                                    triangles++;
                                }
                            }
                        }
                    }
                }
            }
            derniersTriangles = triangles;
            derniersBlocs = blocs;
            if (triangles == 0) {
                BUILDER.end().release();
                derniereDureeNs = System.nanoTime() - debut;
                return null;
            }
            rendu = BUILDER.end();
        } finally {
            if (BUILDER.building()) BUILDER.discard();
        }
        // copie native des sommets pour les lots (liberee par Decal.close), puis tampon individuel
        ByteBuffer src = rendu.vertexBuffer();
        ByteBuffer sommets = MemoryUtil.memAlloc(src.remaining());
        MemoryUtil.memCopy(src, sommets);
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendu);
        VertexBuffer.unbind();
        derniereDureeNs = System.nanoTime() - debut;
        AABB bornes = new AABB(minX - 0.05, minY - 0.05, minZ - 0.05, maxX + 0.05, maxY + 0.05, maxZ + 0.05);
        if (HeraliszTags.LOGGER.isDebugEnabled()) {
            HeraliszTags.LOGGER.debug("[HeraliZ Tags] decalque #{} {} : {} triangles, {} blocs, {} µs", p.id(), p.tagId(), triangles, blocs, derniereDureeNs / 1000);
        }
        return new Decal(vb, sommets, bornes, point.x, point.y, point.z, origX, origY, origZ, DecalLot.cle(point.x, point.y, point.z), triangles);
    }

    /** Vrai si on n'est pas sur le thread de rendu (la cuisson doit alors etre differee). */
    public static boolean horsThreadRendu() {
        return !RenderSystem.isOnRenderThread();
    }

    /**
     * Face recouverte : au bord du bloc avec un voisin dont la face opposee est pleine, ou dont le point juste devant
     * tombe dans une autre AABB de la meme forme (faces internes des murets, barrieres...).
     */
    private static boolean recouverte(Level level, BlockPos pos, BlockState etat, VoxelShape forme, List<AABB> boites, int ib, AABB b, Direction d) {
        double eps = 1e-4;
        boolean auBord = switch (d) {
            case DOWN -> b.minY < eps;
            case UP -> b.maxY > 1 - eps;
            case NORTH -> b.minZ < eps;
            case SOUTH -> b.maxZ > 1 - eps;
            case WEST -> b.minX < eps;
            case EAST -> b.maxX > 1 - eps;
        };
        if (auBord) {
            POS2.setWithOffset(pos, d);
            BlockState voisin = level.getBlockState(POS2);
            if (!voisin.isAir() && Block.isFaceFull(voisin.getShape(level, POS2), d.getOpposite())) return true;
            // la face au bord ne peut pas etre couverte par une autre AABB de la meme forme
            return false;
        }
        // point juste devant le centre de la face, en coordonnees locales
        double qx = (b.minX + b.maxX) / 2, qy = (b.minY + b.maxY) / 2, qz = (b.minZ + b.maxZ) / 2;
        double e = 0.002;
        switch (d) {
            case DOWN -> qy = b.minY - e;
            case UP -> qy = b.maxY + e;
            case NORTH -> qz = b.minZ - e;
            case SOUTH -> qz = b.maxZ + e;
            case WEST -> qx = b.minX - e;
            case EAST -> qx = b.maxX + e;
        }
        for (int i = 0; i < boites.size(); i++) {
            if (i == ib) continue;
            AABB o = boites.get(i);
            if (qx >= o.minX && qx <= o.maxX && qy >= o.minY && qy <= o.maxY && qz >= o.minZ && qz <= o.maxZ) return true;
        }
        return false;
    }

    /** Lumiere (uv2) du bloc au point donne ; si ce bloc est opaque, celle du voisin de la face. */
    private static int lumiere(BlockAndTintGetter level, double x, double y, double z, BlockPos bloc, Direction d) {
        POS2.set(Math.floor(x), Math.floor(y), Math.floor(z));
        BlockState s = level.getBlockState(POS2);
        if (s.isSolidRender(level, POS2)) POS2.setWithOffset(bloc, d);
        return LevelRenderer.getLightColor(level, POS2);
    }

    /**
     * Ecrit les 4 coins de la face d de l'AABB b (bloc bx, by, bz) dans le repere du tag (u, v, w), relativement au
     * point d'impact. Retourne 4.
     */
    private static int coinsFace(AABB b, Direction d, int bx, int by, int bz, Vec3 point, Vec3 r, Vec3 u, Vec3 n, double[] out) {
        double x0 = b.minX + bx, x1 = b.maxX + bx, y0 = b.minY + by, y1 = b.maxY + by, z0 = b.minZ + bz, z1 = b.maxZ + bz;
        switch (d) {
            case UP -> coins4(out, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, point, r, u, n);
            case DOWN -> coins4(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, point, r, u, n);
            case NORTH -> coins4(out, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, point, r, u, n);
            case SOUTH -> coins4(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, point, r, u, n);
            case WEST -> coins4(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, point, r, u, n);
            case EAST -> coins4(out, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, point, r, u, n);
        }
        return 4;
    }

    private static void coins4(double[] out, double ax, double ay, double az, double bx, double by, double bz,
                               double cx, double cy, double cz, double dx, double dy, double dz, Vec3 p, Vec3 r, Vec3 u, Vec3 n) {
        repere(out, 0, ax, ay, az, p, r, u, n);
        repere(out, 1, bx, by, bz, p, r, u, n);
        repere(out, 2, cx, cy, cz, p, r, u, n);
        repere(out, 3, dx, dy, dz, p, r, u, n);
    }

    private static void repere(double[] out, int i, double x, double y, double z, Vec3 p, Vec3 r, Vec3 u, Vec3 n) {
        double dx = x - p.x, dy = y - p.y, dz = z - p.z;
        out[i * 3] = dx * r.x + dy * r.y + dz * r.z;
        out[i * 3 + 1] = dx * u.x + dy * u.y + dz * u.z;
        out[i * 3 + 2] = dx * n.x + dy * n.y + dz * n.z;
    }

    /**
     * Decoupe de Sutherland–Hodgman par un plan axial du repere du tag : garde les points tels que
     * signe * (coord[axe] − limite) ≥ 0. Retourne le nombre de sommets ecrits dans out.
     */
    private static int decouper(double[] in, int n, int axe, double limite, int signe, double[] out) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double di = signe * (in[i * 3 + axe] - limite), dj = signe * (in[j * 3 + axe] - limite);
            boolean ini = di >= 0, inj = dj >= 0;
            if (ini) {
                if (m < MAX_SOMMETS) {
                    out[m * 3] = in[i * 3];
                    out[m * 3 + 1] = in[i * 3 + 1];
                    out[m * 3 + 2] = in[i * 3 + 2];
                    m++;
                }
            }
            if (ini != inj && m < MAX_SOMMETS) {
                double t = di / (di - dj);
                out[m * 3] = in[i * 3] + (in[j * 3] - in[i * 3]) * t;
                out[m * 3 + 1] = in[i * 3 + 1] + (in[j * 3 + 1] - in[i * 3 + 1]) * t;
                out[m * 3 + 2] = in[i * 3 + 2] + (in[j * 3 + 2] - in[i * 3 + 2]) * t;
                m++;
            }
        }
        return m;
    }
}
