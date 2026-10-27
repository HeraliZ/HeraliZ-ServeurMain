package fr.heraliz.crac.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.network.S2CZonesInterdites;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Zones où l'on ne construit pas, plan de construction en main (HeraliZ, 27/10, demande du propriétaire) : les cases
 * interdites du masque du serveur ({@code ZonesConstruction}, envoyé par morceaux autour du joueur,
 * {@link S2CZonesInterdites}) prennent une légère teinte rouge au sol, et leur bord (là où on peut de nouveau construire)
 * un liseré rouge au ras du sol avec un petit voile, dans le style de la zone des armoires ({@link ZoneArmoire}). Autour
 * du joueur (24 blocs) et de l'endroit visé (12 blocs) ; la case visée, si elle est interdite, est plus marquée. Rien
 * plan rangé, en créatif, ou hors de la carte. Hauteurs du sol gardées 10 ticks (cartes des hauteurs du monde du joueur).
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class ZonesInterdites {
    private static final float R = 0xE8 / 255F, G = 0x4A / 255F, B = 0x3C / 255F;
    /** Teinte du sol, case visée plus marquée ; voile du bord (bas, haut) ; hauteur du voile ; sol décollé. */
    private static final float TEINTE = 0.16F, TEINTE_VISEE = 0.30F, VOILE_BAS = 0.30F;
    private static final double VOILE = 1.5, DECOLLE = 0.03;
    private static final int RAYON_JOUEUR = 24, RAYON_VISE = 12;

    private static volatile S2CZonesInterdites donnees;
    private static WeakReference<Level> monde = new WeakReference<>(null);

    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap SOLS = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private static long solsTick = Long.MIN_VALUE;
    private static WeakReference<Level> solsMonde = new WeakReference<>(null);

    private ZonesInterdites() {
    }

    public static void recevoir(S2CZonesInterdites p) {
        donnees = p.largeur() == 0 ? null : p;
        monde = new WeakReference<>(Minecraft.getInstance().level);
    }

    /** Les zones connues, seulement dans le monde où elles ont été reçues. */
    private static S2CZonesInterdites zones() {
        S2CZonesInterdites d = donnees;
        Minecraft mc = Minecraft.getInstance();
        return d != null && mc.level != null && monde.get() == mc.level ? d : null;
    }

    /** Le bloc (x, z) est-il dans une case interdite connue ? */
    public static boolean interdit(int x, int z) {
        S2CZonesInterdites d = zones();
        return d != null && d.interdit(Math.floorDiv(x - d.x0(), d.pas()), Math.floorDiv(z - d.z0(), d.pas()));
    }

    /** Même règle que le serveur (StructureService) : centre de la pièce, puis chacun de ses blocs. */
    public static boolean touche(PieceKey key, List<BlockPos> blocs) {
        if (zones() == null) {
            return false;
        }
        Vec3 c = key.centreWorld();
        if (interdit((int) Math.floor(c.x), (int) Math.floor(c.z))) {
            return true;
        }
        for (BlockPos q : blocs) {
            if (interdit(q.getX(), q.getZ())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        S2CZonesInterdites d = zones();
        if (p == null || d == null || p.getAbilities().instabuild || !(p.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            return;
        }
        Level lvl = mc.level;
        long t = lvl.getGameTime();
        if (solsMonde.get() != lvl || t - solsTick >= 10 || t < solsTick) {
            SOLS.clear();
            solsTick = t;
            solsMonde = new WeakReference<>(lvl);
        }
        // endroit visé (plan en main : la visée de Minecraft s'arrête au premier bloc)
        int vx = Integer.MIN_VALUE, vz = Integer.MIN_VALUE;
        HitResult h = mc.hitResult;
        if (h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK) {
            vx = bh.getBlockPos().getX();
            vz = bh.getBlockPos().getZ();
        }
        int vi = vx == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.floorDiv(vx - d.x0(), d.pas());
        int vj = vz == Integer.MIN_VALUE ? Integer.MIN_VALUE : Math.floorDiv(vz - d.z0(), d.pas());

        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        int px = p.getBlockX(), pz = p.getBlockZ();
        // deux passes, comme ZoneArmoire : teintes et voiles, puis liserés (un seul tampon partagé à la fois)
        VertexConsumer q = buffers.getBuffer(RenderType.debugQuads());
        dessiner(q, null, pose, m, lvl, d, px, pz, vx, vz, vi, vj);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer l = buffers.getBuffer(RenderType.lines());
        dessiner(null, l, pose, m, lvl, d, px, pz, vx, vz, vi, vj);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static boolean proche(int x, int z, int px, int pz, int vx, int vz) {
        long dx = x - px, dz = z - pz;
        if (dx * dx + dz * dz <= (long) RAYON_JOUEUR * RAYON_JOUEUR) {
            return true;
        }
        if (vx == Integer.MIN_VALUE) {
            return false;
        }
        long ex = x - vx, ez = z - vz;
        return ex * ex + ez * ez <= (long) RAYON_VISE * RAYON_VISE;
    }

    /** Le point de la case (cx, cz, côté pas) le plus proche de (x, z) est-il à rayon blocs au plus ? */
    private static boolean procheCase(int cx, int cz, int pas, int x, int z, int rayon) {
        long dx = x - Math.max(cx, Math.min(x, cx + pas - 1)), dz = z - Math.max(cz, Math.min(z, cz + pas - 1));
        return dx * dx + dz * dz <= (long) rayon * rayon;
    }

    private static void dessiner(VertexConsumer q, VertexConsumer l, PoseStack pose, Matrix4f m, Level lvl, S2CZonesInterdites d,
                                 int px, int pz, int vx, int vz, int vi, int vj) {
        int pas = d.pas();
        for (int j = 0; j < d.hauteur(); j++) {
            for (int i = 0; i < d.largeur(); i++) {
                if (!d.interdit(i, j)) continue;
                int cx = d.x0() + i * pas, cz = d.z0() + j * pas;
                // case entière trop loin du joueur et de l'endroit visé (son point le plus proche) : rien
                if (!procheCase(cx, cz, pas, px, pz, RAYON_JOUEUR)
                    && (vx == Integer.MIN_VALUE || !procheCase(cx, cz, pas, vx, vz, RAYON_VISE))) continue;
                if (q != null) {
                    float a = i == vi && j == vj ? TEINTE_VISEE : TEINTE;
                    for (int x = cx; x < cx + pas; x++) {
                        for (int z = cz; z < cz + pas; z++) {
                            if (!proche(x, z, px, pz, vx, vz)) continue;
                            double y = colonne(lvl, x, z);
                            if (!Double.isNaN(y)) {
                                sol(q, m, x, z, y + DECOLLE, a);
                            }
                        }
                    }
                }
                // bords vers une case permise (ou inconnue : hors de la fenêtre, pas de bord)
                if (i > 0 && !d.interdit(i - 1, j)) bord(q, l, pose, m, lvl, cx, cz, 0, 1, pas, px, pz, vx, vz);
                if (i < d.largeur() - 1 && !d.interdit(i + 1, j)) bord(q, l, pose, m, lvl, cx + pas, cz, 0, 1, pas, px, pz, vx, vz);
                if (j > 0 && !d.interdit(i, j - 1)) bord(q, l, pose, m, lvl, cx, cz, 1, 0, pas, px, pz, vx, vz);
                if (j < d.hauteur() - 1 && !d.interdit(i, j + 1)) bord(q, l, pose, m, lvl, cx, cz + pas, 1, 0, pas, px, pz, vx, vz);
            }
        }
    }

    /** Un bord de case, découpé bloc par bloc sur la grille (départ (sx, sz), n pas de (dx, dz)). */
    private static void bord(VertexConsumer q, VertexConsumer l, PoseStack pose, Matrix4f m, Level lvl, int sx, int sz, int dx, int dz,
                             int n, int px, int pz, int vx, int vz) {
        double yPrec = Double.NaN;
        for (int k = 0; k <= n; k++) {
            int x = sx + dx * k, z = sz + dz * k;
            double y = point(lvl, x, z);
            if (k > 0 && !Double.isNaN(y) && !Double.isNaN(yPrec) && proche(x - dx, z - dz, px, pz, vx, vz)) {
                int xa = x - dx, za = z - dz;
                double a0 = yPrec + DECOLLE, a1 = y + DECOLLE;
                if (q != null) {
                    voile(q, m, xa, za, x, z, a0, a1);
                }
                if (l != null) {
                    ligne(l, pose, xa, a0, za, x, a1, z);
                }
            }
            yPrec = y;
        }
    }

    /** Haut du sol de la colonne (x, z) ; NaN hors du terrain chargé. */
    private static double colonne(Level lvl, int x, int z) {
        if (!lvl.hasChunk(x >> 4, z >> 4)) {
            return Double.NaN;
        }
        return lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    /** Point (x, z) de la grille : sol le plus haut des 4 colonnes qui le touchent (gardé 10 ticks) ; NaN hors terrain. */
    private static double point(Level lvl, int xb, int zb) {
        long cle = (long) xb << 32 | (zb & 0xFFFFFFFFL);
        int best;
        if (SOLS.containsKey(cle)) {
            best = SOLS.get(cle);
        } else {
            best = Integer.MIN_VALUE;
            for (int x = xb - 1; x <= xb; x++) {
                for (int z = zb - 1; z <= zb; z++) {
                    if (lvl.hasChunk(x >> 4, z >> 4)) {
                        best = Math.max(best, lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                    }
                }
            }
            SOLS.put(cle, best);
        }
        return best == Integer.MIN_VALUE ? Double.NaN : best;
    }

    /** Teinte d'une colonne, posée sur son sol, visible des deux côtés. */
    private static void sol(VertexConsumer q, Matrix4f m, int x, int z, double y, float a) {
        float x0 = x, x1 = x + 1, z0 = z, z1 = z + 1, fy = (float) y;
        q.vertex(m, x0, fy, z0).color(R, G, B, a).endVertex();
        q.vertex(m, x0, fy, z1).color(R, G, B, a).endVertex();
        q.vertex(m, x1, fy, z1).color(R, G, B, a).endVertex();
        q.vertex(m, x1, fy, z0).color(R, G, B, a).endVertex();
        q.vertex(m, x1, fy, z0).color(R, G, B, a).endVertex();
        q.vertex(m, x1, fy, z1).color(R, G, B, a).endVertex();
        q.vertex(m, x0, fy, z1).color(R, G, B, a).endVertex();
        q.vertex(m, x0, fy, z0).color(R, G, B, a).endVertex();
    }

    /** Petit voile au-dessus d'un bout de bord, plus opaque en bas, vu des deux côtés. */
    private static void voile(VertexConsumer q, Matrix4f m, double xa, double za, double xb, double zb, double ya, double yb) {
        q.vertex(m, (float) xa, (float) ya, (float) za).color(R, G, B, VOILE_BAS).endVertex();
        q.vertex(m, (float) xb, (float) yb, (float) zb).color(R, G, B, VOILE_BAS).endVertex();
        q.vertex(m, (float) xb, (float) (yb + VOILE), (float) zb).color(R, G, B, 0F).endVertex();
        q.vertex(m, (float) xa, (float) (ya + VOILE), (float) za).color(R, G, B, 0F).endVertex();
        q.vertex(m, (float) xa, (float) (ya + VOILE), (float) za).color(R, G, B, 0F).endVertex();
        q.vertex(m, (float) xb, (float) (yb + VOILE), (float) zb).color(R, G, B, 0F).endVertex();
        q.vertex(m, (float) xb, (float) yb, (float) zb).color(R, G, B, VOILE_BAS).endVertex();
        q.vertex(m, (float) xa, (float) ya, (float) za).color(R, G, B, VOILE_BAS).endVertex();
    }

    private static void ligne(VertexConsumer l, PoseStack pose, double xa, double ya, double za, double xb, double yb, double zb) {
        float nx = (float) (xb - xa), ny = (float) (yb - ya), nz = (float) (zb - za);
        float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1e-6) {
            return;
        }
        PoseStack.Pose p = pose.last();
        l.vertex(p.pose(), (float) xa, (float) ya, (float) za).color(R, G, B, 0.9F).normal(p.normal(), nx / n, ny / n, nz / n).endVertex();
        l.vertex(p.pose(), (float) xb, (float) yb, (float) zb).color(R, G, B, 0.9F).normal(p.normal(), nx / n, ny / n, nz / n).endVertex();
    }
}
