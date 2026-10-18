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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Zone des armoires à outils, plan de construction en main (CRac 1.11.0, demande du propriétaire du 18/10) : le bord de
 * chaque zone proche (celle que l'armoire protège, un carré autour d'elle) en bande translucide à hauteur du joueur, verte
 * pour la sienne, rouge pour celle des autres. Les zones viennent du serveur ({@link ClientClaimCache}).
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class ZoneArmoire {
    private static final int VERT = 0x5FD08A, ROUGE = 0xE86A5A;
    /** Hauteur de la bande : de 1 bloc sous les pieds à 4 au-dessus (bornée à la hauteur de la zone). */
    private static final int SOUS = 1, DESSUS = 4;

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
        int py = p.blockPosition().getY();
        for (S2CNearbyClaims.ClaimEntry c : ClientClaimCache.get()) {
            BlockPos a = c.center();
            double y0 = Math.max(a.getY() - c.yRadius(), py - SOUS), y1 = Math.min(a.getY() + c.yRadius() + 1, py + DESSUS);
            if (y1 <= y0) {
                continue;
            }
            double x0 = a.getX() - c.radius(), x1 = a.getX() + c.radius() + 1;
            double z0 = a.getZ() - c.radius(), z1 = a.getZ() + c.radius() + 1;
            int rgb = c.ownedByMe() ? VERT : ROUGE;
            float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
            VertexConsumer q = buffers.getBuffer(RenderType.debugQuads());
            mur(q, m, x0, z0, x1, z0, y0, y1, r, g, b);
            mur(q, m, x1, z0, x1, z1, y0, y1, r, g, b);
            mur(q, m, x1, z1, x0, z1, y0, y1, r, g, b);
            mur(q, m, x0, z1, x0, z0, y0, y1, r, g, b);
            VertexConsumer l = buffers.getBuffer(RenderType.lines());
            for (double y : new double[]{y0 + 0.02, y1 - 0.02}) {
                ligne(l, pose, x0, y, z0, x1, y, z0, r, g, b);
                ligne(l, pose, x1, y, z0, x1, y, z1, r, g, b);
                ligne(l, pose, x1, y, z1, x0, y, z1, r, g, b);
                ligne(l, pose, x0, y, z1, x0, y, z0, r, g, b);
            }
            for (double[] coin : new double[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                ligne(l, pose, coin[0], y0, coin[1], coin[0], y1, coin[1], r, g, b);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.debugQuads());
        buffers.endBatch(RenderType.lines());
    }

    /** Un pan de la bande, vu des deux côtés, plus opaque en bas. */
    private static void mur(VertexConsumer q, Matrix4f m, double xa, double za, double xb, double zb, double y0, double y1,
                            float r, float g, float b) {
        float bas = 0.22F, haut = 0.03F;
        q.vertex(m, (float) xa, (float) y0, (float) za).color(r, g, b, bas).endVertex();
        q.vertex(m, (float) xb, (float) y0, (float) zb).color(r, g, b, bas).endVertex();
        q.vertex(m, (float) xb, (float) y1, (float) zb).color(r, g, b, haut).endVertex();
        q.vertex(m, (float) xa, (float) y1, (float) za).color(r, g, b, haut).endVertex();
        q.vertex(m, (float) xa, (float) y1, (float) za).color(r, g, b, haut).endVertex();
        q.vertex(m, (float) xb, (float) y1, (float) zb).color(r, g, b, haut).endVertex();
        q.vertex(m, (float) xb, (float) y0, (float) zb).color(r, g, b, bas).endVertex();
        q.vertex(m, (float) xa, (float) y0, (float) za).color(r, g, b, bas).endVertex();
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
