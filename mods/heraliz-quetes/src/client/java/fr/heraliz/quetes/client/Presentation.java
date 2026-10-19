package fr.heraliz.quetes.client;

import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Présentation d'un PNJ (Quêtes 1.8.0, demande du propriétaire du 19/10) : la caméra fait lentement le tour du PNJ
 * (le Bâtisseur, à la première arrivée au spawn après le tutoriel des bases), regard toujours sur lui, pendant que
 * le Commandant Varga parle à la radio ; ni bras ni interface, sauf la radio. Espace maintenu 1 s pour passer.
 * Même principe que le tour de la base à la fin du tutoriel (CRac).
 */
final class Presentation {
    /** Part du tour faite pendant la présentation (un demi-tour : lent et lisible). */
    private static final double TOUR = 0.5;
    private static final double HAUT = 1.6;

    private static ArmorStand camera;
    private static Vec3 centre;
    private static float rayon;
    private static int ticks, duree, passer;

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
        placer(0, true);
        mc.setCameraEntity(camera);
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
        double a = 2 * Math.PI * TOUR * t / duree;
        double x = centre.x + rayon * Math.cos(a), z = centre.z + rayon * Math.sin(a), y = centre.y + HAUT;
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
        if (ticks >= duree || passer > 20 || mc.getCameraEntity() != camera) {
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

    static void main(RenderHandEvent e) {
        if (camera != null) e.setCanceled(true);
    }

    /** Seule la radio de Varga (et le reste de HeraliZ Quêtes) reste à l'écran. */
    static void interface_(RenderGuiOverlayEvent.Pre e) {
        if (camera != null && !"heraliz_quetes".equals(e.getOverlay().id().getNamespace())) e.setCanceled(true);
    }
}
