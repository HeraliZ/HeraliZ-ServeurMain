package fr.heraliz.quetes.client;

import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Présentation d'un PNJ (Quêtes 1.8.0, demande du propriétaire du 19/10) : la caméra fait lentement le tour du PNJ
 * (le Bâtisseur, à la première arrivée au spawn après le tutoriel des bases), regard toujours sur lui, pendant que
 * le Commandant Varga parle à la radio ; ni bras ni interface, sauf la radio. Espace maintenu 1 s pour passer (1.10.4 : toute
 * la visite du Guide, indication en bas de l'écran ; caméra devant le visage du PNJ, d'après le lacet de son corps). La
 * caméra ne rentre jamais dans un bloc : elle s'arrête avant le premier mur ou plafond (le spawn est une grotte).
 * Même principe que le tour de la base à la fin du tutoriel (CRac).
 */
final class Presentation {
    /** Part du tour faite pendant la présentation (20/10 : 1/21 de tour, la caméra bouge à peine). */
    private static final double TOUR = 1.0 / 21;
    /** Hauteur de la caméra au-dessus des yeux du PNJ (bas : le spawn est une grotte). */
    private static final double HAUT = 0.5;
    /** Écart gardé avec un mur ou le plafond (blocs). */
    private static final double MARGE = 0.45;

    private static ArmorStand camera;
    private static Vec3 centre;
    private static float rayon;
    private static int ticks, duree, passer;
    /** Distance de la caméra au PNJ à l'image d'avant (rapprochée d'un coup devant un mur, éloignée en douceur). */
    private static double dist;
    /** Angle (autour du PNJ) de la direction de son regard : la caméra reste devant lui, jamais dans son dos. */
    private static double face;

    private Presentation() {
    }

    static void lancer(Reseau.Presentation m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        centre = new Vec3(m.x(), m.y() + 1.4, m.z());
        rayon = m.rayon();
        duree = m.ticks();
        ticks = 0;
        passer = 0;
        camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        dist = -1;
        face = 0;
        if (!Float.isNaN(m.angle())) {
            // point de la visite du Guide (28/10) : la caméra part du côté où se tenait le staff qui l'a posé
            face = m.angle();
            placer(0, true);
            mc.setCameraEntity(camera);
            return;
        }
        net.minecraft.world.entity.LivingEntity pnj = null;
        double mieux = 4;
        for (net.minecraft.world.entity.Entity en : mc.level.getEntities(mc.player,
                new net.minecraft.world.phys.AABB(m.x() - 2, m.y() - 1, m.z() - 2, m.x() + 2, m.y() + 3, m.z() + 2))) {
            if (en instanceof net.minecraft.world.entity.LivingEntity le && !(en instanceof net.minecraft.world.entity.player.Player)) {
                double d2 = en.distanceToSqr(m.x(), m.y(), m.z());
                if (d2 < mieux) {
                    mieux = d2;
                    pnj = le;
                }
            }
        }
        if (pnj != null) {
            // regard du PNJ (lacet du corps, comme Minecraft : x = -sin, z = cos) : la caméra se place devant son visage
            double r = Math.toRadians(pnj.yBodyRot);
            face = Math.atan2(Math.cos(r), -Math.sin(r));
        }
        placer(0, true);
        mc.setCameraEntity(camera);
    }

    /** Une présentation est en cours (les repères des PNJ se montrent alors même de près). */
    static boolean actif() {
        return camera != null;
    }

    static void vider() {
        camera = null;
    }

    private static void finir() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.player != null && mc.getCameraEntity() == camera) mc.setCameraEntity(mc.player);
        camera = null;
    }

    /** La caméra sur l'arc autour du PNJ, au tick {@code t}, en partant de devant lui. */
    private static void placer(int t, boolean net) {
        double a = face + 2 * Math.PI * TOUR * ((double) t / duree - 0.5);
        Vec3 voulu = new Vec3(centre.x + rayon * Math.cos(a), centre.y + HAUT, centre.z + rayon * Math.sin(a));
        // jamais dans un bloc : la caméra s'arrête avant le premier mur ou plafond entre le PNJ et sa place
        double libre = voulu.distanceTo(centre);
        BlockHitResult hit = camera.level().clip(new ClipContext(centre, voulu, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera));
        if (hit.getType() == HitResult.Type.BLOCK) libre = Math.max(0.6, hit.getLocation().distanceTo(centre) - MARGE);
        dist = dist < 0 || libre < dist ? libre : Math.min(libre, dist + 0.08);
        Vec3 pos = centre.add(voulu.subtract(centre).normalize().scale(dist));
        double x = pos.x, y = pos.y, z = pos.z;
        double dx = centre.x - x, dy = centre.y - y, dz = centre.z - z;
        float lacet = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float tangage = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        if (net) {
            camera.moveTo(x, y, z, lacet, tangage);
            return;
        }
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.setPos(x, y, z);
        camera.setYRot(camera.yRotO + Mth.wrapDegrees(lacet - camera.yRotO));
        camera.setXRot(tangage);
    }

    static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            camera = null;
            return;
        }
        ticks++;
        passer = mc.options.keyJump.isDown() ? passer + 1 : 0;
        if (passer > 20) {
            // Espace maintenu 1 s : toute la visite du Guide est passée (Quêtes 1.10.4), pas seulement ce PNJ
            Reseau.CANAL.sendToServer(new Reseau.Action(4, ""));
            finir();
        } else if (ticks >= duree || mc.getCameraEntity() != camera) {
            finir();
        } else {
            placer(ticks, false);
        }
    }

    /** Regard forcé sur le PNJ à chaque image (d'autres mods recalent la caméra sur le regard du joueur). */
    static void regard(ViewportEvent.ComputeCameraAngles e) {
        if (camera == null || centre == null) return;
        Vec3 c = e.getCamera().getPosition();
        double dx = centre.x - c.x, dy = centre.y - c.y, dz = centre.z - c.z;
        e.setYaw((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F);
        e.setPitch((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        e.setRoll(0F);
    }

    /** « Maintiens Espace pour passer » en bas de l'écran pendant la présentation. */
    static void aide(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        if (camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.network.chat.Component t = net.minecraft.network.chat.Component.translatable("heraliz_quetes.presentation.passer");
        int l = mc.font.width(t);
        int x = (e.getGuiGraphics().guiWidth() - l) / 2, y = e.getGuiGraphics().guiHeight() - 28;
        e.getGuiGraphics().fill(x - 6, y - 4, x + l + 6, y + 12, 0x88000000);
        e.getGuiGraphics().drawString(mc.font, t, x, y, passer > 0 ? 0xFFFFE08A : 0xFFE6E6E6, false);
    }

    static void main(RenderHandEvent e) {
        if (camera != null) e.setCanceled(true);
    }

    /** Seule la radio de Varga (et le reste de HeraliZ Quêtes) reste à l'écran. */
    static void interface_(RenderGuiOverlayEvent.Pre e) {
        if (camera != null && !"heraliz_quetes".equals(e.getOverlay().id().getNamespace())) e.setCanceled(true);
    }
}
