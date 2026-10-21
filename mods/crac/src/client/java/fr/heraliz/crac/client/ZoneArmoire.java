package fr.heraliz.crac.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.network.S2CNearbyClaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Zone des armoires à outils, plan de construction en main (CRac 1.11.0, demande du propriétaire du 18/10) : le bord de
 * chaque zone proche (celle que l'armoire protège, un carré autour d'elle), verte pour la sienne, rouge pour celle des
 * autres. Les zones viennent du serveur ({@link ClientClaimCache}).
 * <p>CRac 1.15.0 (21/10) : la bordure est posée sur le terrain et ne suit plus la hauteur du joueur (elle montait et
 * descendait avec lui) : un liseré net au ras du sol, et au-dessus un voile de 2,5 blocs qui s'efface vers le haut ; chaque
 * point du bord prend la hauteur du sol des deux colonnes qui le touchent (la plus haute), en pente douce d'un bloc à
 * l'autre ; petits poteaux aux quatre coins. Hauteur lue dans la carte des hauteurs MOTION_BLOCKING du monde du joueur
 * (avec WORLD_SURFACE, la seule que le serveur lui envoie : MOTION_BLOCKING_NO_LEAVES y reste vide et la zone tombait sous
 * terre, CRac 1.15.3), bornée à la hauteur de la zone ; rien là où le terrain n'est pas chargé.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class ZoneArmoire {
    private static final int VERT = 0x5FD08A, ROUGE = 0xE86A5A;
    /** Hauteur du voile au-dessus du sol, hauteur des poteaux d'angle, liseré décollé du sol (pas de scintillement). */
    private static final double VOILE = 2.5, POTEAU = 3.0, DECOLLE = 0.03;
    /** Opacité du voile au sol et en haut. */
    private static final float BAS = 0.26F, HAUT = 0.0F;

    private ZoneArmoire() {
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null || !(p.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Level lvl = mc.level;
        // deux passes : Minecraft ne remplit qu'un tampon partagé à la fois (voiles puis lignes, jamais mêlés : plantage)
        VertexConsumer q = buffers.getBuffer(RenderType.debugQuads());
        dessiner(q, null, pose, m, lvl);
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer l = buffers.getBuffer(RenderType.lines());
        dessiner(null, l, pose, m, lvl);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** Toutes les zones proches : voiles si q, liseré et poteaux si l. */
    private static void dessiner(VertexConsumer q, VertexConsumer l, PoseStack pose, Matrix4f m, Level lvl) {
        for (S2CNearbyClaims.ClaimEntry c : ClientClaimCache.get()) {
            BlockPos a = c.center();
            int ymin = a.getY() - c.yRadius(), ymax = a.getY() + c.yRadius() + 1;
            int x0 = a.getX() - c.radius(), x1 = a.getX() + c.radius() + 1;
            int z0 = a.getZ() - c.radius(), z1 = a.getZ() + c.radius() + 1;
            int rgb = c.ownedByMe() ? VERT : ROUGE;
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            // les quatre côtés, parcourus dans le même sens ; dx/dz : pas le long du côté
            cote(q, l, pose, m, lvl, x0, z0, 1, 0, x1 - x0, ymin, ymax, r, g, b);
            cote(q, l, pose, m, lvl, x1, z0, 0, 1, z1 - z0, ymin, ymax, r, g, b);
            cote(q, l, pose, m, lvl, x1, z1, -1, 0, x1 - x0, ymin, ymax, r, g, b);
            cote(q, l, pose, m, lvl, x0, z1, 0, -1, z1 - z0, ymin, ymax, r, g, b);
            if (l == null) {
                continue;
            }
            for (int[] coin : new int[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                double y = sol(lvl, coin[0] - 1, coin[1] - 1, coin[0], coin[1], ymin, ymax);
                if (!Double.isNaN(y)) {
                    ligne(l, pose, coin[0], y, coin[1], coin[0], Math.min(y + POTEAU, ymax), coin[1], r, g, b);
                }
            }
        }
    }

    /**
     * Un côté de la zone, découpé bloc par bloc. Point de départ (sx, sz) sur l'arête des blocs, n pas de (dx, dz) ; la
     * hauteur d'un point d'arête est celle du sol le plus haut des colonnes qui l'entourent (dedans et dehors).
     */
    private static void cote(VertexConsumer q, VertexConsumer l, PoseStack pose, Matrix4f m, Level lvl, int sx, int sz,
                             int dx, int dz, int n, int ymin, int ymax, float r, float g, float b) {
        double yPrec = Double.NaN;
        int xPrec = 0, zPrec = 0;
        for (int i = 0; i <= n; i++) {
            int x = sx + dx * i, z = sz + dz * i;
            // les 4 colonnes autour du point de la grille (dedans et dehors)
            double y = sol(lvl, x - 1, z - 1, x, z, ymin, ymax);
            if (i > 0 && !Double.isNaN(y) && !Double.isNaN(yPrec)) {
                double a0 = yPrec + DECOLLE, a1 = y + DECOLLE;
                if (q != null) {
                    mur(q, m, xPrec, zPrec, x, z, a0, Math.min(a0 + VOILE, ymax), a1, Math.min(a1 + VOILE, ymax), r, g, b);
                }
                if (l != null) {
                    ligne(l, pose, xPrec, a0, zPrec, x, a1, z, r, g, b);
                }
            }
            yPrec = y;
            xPrec = x;
            zPrec = z;
        }
    }

    /** Sol le plus haut des colonnes [xa..xb] x [za..zb], borné à la zone ; NaN si le terrain n'est pas chargé. */
    private static double sol(Level lvl, int xa, int za, int xb, int zb, int ymin, int ymax) {
        int best = Integer.MIN_VALUE;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                if (!lvl.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                best = Math.max(best, lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
            }
        }
        if (best == Integer.MIN_VALUE) {
            return Double.NaN;
        }
        return Math.max(ymin, Math.min(best, ymax - 1));
    }

    /** Un pan du voile, vu des deux côtés, plus opaque en bas ; bas et haut peuvent pencher (a : début, b : fin). */
    private static void mur(VertexConsumer q, Matrix4f m, double xa, double za, double xb, double zb, double ya0, double ya1,
                            double yb0, double yb1, float r, float g, float b) {
        q.vertex(m, (float) xa, (float) ya0, (float) za).color(r, g, b, BAS).endVertex();
        q.vertex(m, (float) xb, (float) yb0, (float) zb).color(r, g, b, BAS).endVertex();
        q.vertex(m, (float) xb, (float) yb1, (float) zb).color(r, g, b, HAUT).endVertex();
        q.vertex(m, (float) xa, (float) ya1, (float) za).color(r, g, b, HAUT).endVertex();
        q.vertex(m, (float) xa, (float) ya1, (float) za).color(r, g, b, HAUT).endVertex();
        q.vertex(m, (float) xb, (float) yb1, (float) zb).color(r, g, b, HAUT).endVertex();
        q.vertex(m, (float) xb, (float) yb0, (float) zb).color(r, g, b, BAS).endVertex();
        q.vertex(m, (float) xa, (float) ya0, (float) za).color(r, g, b, BAS).endVertex();
    }

    private static void ligne(VertexConsumer l, PoseStack pose, double xa, double ya, double za, double xb, double yb, double zb,
                              float r, float g, float b) {
        float nx = (float) (xb - xa), ny = (float) (yb - ya), nz = (float) (zb - za);
        float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1e-6) {
            return;
        }
        PoseStack.Pose p = pose.last();
        l.vertex(p.pose(), (float) xa, (float) ya, (float) za).color(r, g, b, 0.85F).normal(p.normal(), nx / n, ny / n, nz / n).endVertex();
        l.vertex(p.pose(), (float) xb, (float) yb, (float) zb).color(r, g, b, 0.85F).normal(p.normal(), nx / n, ny / n, nz / n).endVertex();
    }
}
