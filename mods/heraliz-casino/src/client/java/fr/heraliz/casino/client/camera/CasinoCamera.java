package fr.heraliz.casino.client.camera;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.MachinePartBlock;
import fr.heraliz.casino.client.Views;
import fr.heraliz.casino.client.render.MachineRenderer;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Camera cinematique : quand le joueur joue sur une machine, la vue glisse doucement des yeux du joueur vers un plan
 * choisi par le modele de la machine (tapis de la roulette, roue, ecran de la fusee...), suit l'action, puis revient.
 * Le joueur ne bouge pas : seule la camera se deplace (position et angles remplaces a chaque image).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class CasinoCamera {
    private CasinoCamera() {
    }

    private static final long ENTER_MS = 800, EXIT_MS = 550;
    /** Camera#setPosition(double, double, double) (protegee). */
    private static final MethodHandle SET_POSITION;
    /**
     * Camera#setRotation(float, float) (protegee) : l'evenement Forge ne change que les angles, pas le quaternion
     * d'orientation qui sert aux textes et sprites tournes vers la camera (et aux particules).
     */
    private static final MethodHandle SET_ROTATION;
    /** Camera#detached : le joueur local est dessine (la camera n'est plus dans sa tete). */
    private static final java.lang.reflect.Field DETACHED;

    static {
        MethodHandle h = null;
        try {
            Method m = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90584_", double.class, double.class, double.class);
            h = MethodHandles.lookup().unreflect(m);
        } catch (Throwable e) {
            HeralizCasino.LOGGER.error("[HeralizCasino] Camera cinematique indisponible", e);
        }
        SET_POSITION = h;
        MethodHandle rot = null;
        try {
            Method m = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90572_", float.class, float.class);
            rot = MethodHandles.lookup().unreflect(m);
        } catch (Throwable e) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] Orientation de la camera cinematique indisponible", e);
        }
        SET_ROTATION = rot;
        java.lang.reflect.Field f = null;
        try {
            f = ObfuscationReflectionHelper.findField(Camera.class, "f_90560_");
        } catch (Throwable e) {
            HeralizCasino.LOGGER.warn("[HeralizCasino] Affichage du joueur par la camera cinematique indisponible", e);
        }
        DETACHED = f;
    }

    /** Le plan en cours montre le joueur local (duels). */
    private static boolean showPlayer;

    private static MachineBlockEntity target;
    private static int mode = MachineDesign.CAM_MENU;
    private static boolean active, exiting;
    private static long enterAt, exitAt;
    private static float exitFrom;
    private static CameraType savedType;

    // plan vise (monde), lisse dans le temps
    private static double sx, sy, sz;
    private static float syaw, spitch, sfov = 70;
    private static boolean hasShot;
    private static long lastNanos;
    private static final MachineDesign.Shot SHOT = new MachineDesign.Shot();

    // valeurs de l'image en cours (servent a viser avec la souris)
    private static float blend;
    private static double camX, camY, camZ;
    private static float camYaw, camPitch, camFov = 70;

    /**
     * Zoom du joueur ("la tete dans l'ecran") : la camera s'avance jusqu'a ce que l'image de l'ecran de la machine
     * remplisse toute la hauteur de la vue. Avancee animee (ZOOM_MS) ; zoomP = progression 0..1 ; immersive = part de
     * ce cadrage dans le plan (zoom ou intro).
     */
    private static final float ZOOM_MS = 700;
    private static boolean zoomOn;
    private static float zoomP, immersive;
    /** Machine filmee : cadrage impose par son intro (0..1) ; ecran de fin de partie affiche (0..1). */
    private static float focus, summary;
    private static long zoomNanos;

    // =================================================================== commandes
    /** Fait glisser la camera vers la machine (sans effet si elle y est deja). */
    public static void enter(MachineBlockEntity be) {
        if (SET_POSITION == null) return;
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (active && !exiting && target == be) return;
        if (!active) {
            savedType = mc.options.getCameraType();
            hasShot = false;
            blend = 0;
            enterAt = now;
            zoomP = 0;
        } else {
            // retour pendant la sortie (ou autre machine) : on repart de l'etat courant sans a-coup
            enterAt = now - (long) (ENTER_MS * inverseEase(blend));
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        target = be;
        active = true;
        exiting = false;
        mode = MachineDesign.CAM_MENU;
    }

    /** Retour doux vers les yeux du joueur. */
    public static void exit() {
        if (!active || exiting) return;
        exiting = true;
        exitAt = Util.getMillis();
        exitFrom = blend;
    }

    public static void setMode(int m) {
        mode = m;
    }

    /** Zoom du joueur sur l'ecran de la machine (la camera s'y avance, ou en revient, en 0,7 s). */
    public static void setZoom(boolean on) {
        zoomOn = on;
    }

    public static boolean zoom() {
        return zoomOn;
    }

    public static boolean isActive() {
        return active;
    }

    /** Machine filmee (null si la camera est sur les yeux du joueur ou y revient). */
    public static MachineBlockEntity target() {
        return active && !exiting ? target : null;
    }

    /** La camera est (ou va) sur cette machine. */
    public static boolean isOn(BlockPos pos) {
        return active && !exiting && target != null && target.getBlockPos().equals(pos);
    }

    /** Proportion de la vue cinematique dans l'image (0 = yeux du joueur, 1 = plan de la machine). */
    public static float blend() {
        return blend;
    }

    /** Arret immediat (un autre plan du mod prend la camera : expulsion par les videurs). */
    public static void abort() {
        if (active) stop();
    }

    /**
     * Pose la camera a un endroit et dans une direction donnes, pour les autres plans du mod (appele pendant
     * ViewportEvent.ComputeCameraAngles ; l'appelant fixe aussi les angles de l'evenement).
     * @param detached le joueur local est dessine (la camera n'est plus dans sa tete)
     * @return false si la camera ne peut pas etre deplacee sur cette installation
     */
    public static boolean place(Camera cam, double x, double y, double z, float yaw, float pitch, boolean detached) {
        if (SET_POSITION == null) return false;
        try {
            SET_POSITION.invoke(cam, x, y, z);
            if (SET_ROTATION != null) SET_ROTATION.invoke(cam, yaw, pitch);
            if (detached && DETACHED != null) DETACHED.setBoolean(cam, true);
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    private static void stop() {
        active = false;
        exiting = false;
        target = null;
        hasShot = false;
        showPlayer = false;
        blend = 0;
        if (savedType != null) {
            Minecraft.getInstance().options.setCameraType(savedType);
            savedType = null;
        }
    }

    private static float inverseEase(float b) {
        // Ease.inOutSine inverse : b = (1 - cos(pi t)) / 2
        return (float) (Math.acos(1 - 2 * Mth.clamp(b, 0, 1)) / Math.PI);
    }

    // =================================================================== image
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || target == null || target.isRemoved() || target.getLevel() != mc.level) {
            stop();
            return;
        }
        long now = Util.getMillis();
        if (exiting) {
            float t = (now - exitAt) / (float) EXIT_MS;
            if (t >= 1) {
                stop();
                return;
            }
            blend = exitFrom * (1 - Ease.inOutSine(t));
        } else {
            blend = Ease.inOutSine(Math.min(1f, (now - enterAt) / (float) ENTER_MS));
        }
        updateShot(mc, event.renderTickTime);
    }

    /** Plan demande par le modele (repere machine) -> monde, puis lissage. */
    private static void updateShot(Minecraft mc, float partial) {
        MachineDesign<?> d = Designs.get(target.type.id);
        if (!d.isBuilt()) return;
        BlockState st = target.getBlockState();
        if (!(st.getBlock() instanceof MachineBlock)) return;
        float yRot = MachineRenderer.yRot(st);
        Scene s = MachineRenderer.scene(target, mc.level, partial, yRot, mc.gameRenderer.getMainCamera().getPosition());
        Object view = Views.view(target, s);
        d.cameraRaw(view, s, mode, SHOT);
        showPlayer = d.showsLocalPlayerRaw(view, s);
        // zoom du joueur, intro de la machine : la camera s'avance (en ligne droite, acceleree puis freinee) jusqu'a ce
        // que l'image de l'ecran remplisse toute la hauteur de la vue
        long zn = System.nanoTime();
        float zdt = zoomNanos == 0 ? 0 : Math.min(0.1f, Math.max(0, (zn - zoomNanos) / 1e9f));
        zoomNanos = zn;
        float[] rect = d.screenRectRaw(view, s);
        zoomP = Mth.clamp(zoomP + (zoomOn && rect != null ? 1 : -1) * zdt * 1000 / ZOOM_MS, 0, 1);
        focus = d.screenFocusRaw(view, s);
        summary = d.summaryRaw(view, s);
        float zk = rect == null ? 0 : Math.max(Ease.inOutSine(zoomP), focus);
        immersive = zk;
        if (zk > 0.001f) fitScreen(mc, rect, zk);
        BlockPos pos = target.getBlockPos();
        float[] e = MachineRenderer.toLocal(SHOT.ex, SHOT.ey, SHOT.ez, yRot);
        float[] t = MachineRenderer.toLocal(SHOT.tx, SHOT.ty, SHOT.tz, yRot);
        double tx = pos.getX() + t[0], ty = pos.getY() + t[1], tz = pos.getZ() + t[2];
        double[] eye = clearEye(mc.level, pos, tx, ty, tz, pos.getX() + e[0], pos.getY() + e[1], pos.getZ() + e[2]);
        double dx = tx - eye[0], dy = ty - eye[1], dz = tz - eye[2];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        long nanos = System.nanoTime();
        if (!hasShot) {
            sx = eye[0];
            sy = eye[1];
            sz = eye[2];
            syaw = yaw;
            spitch = pitch;
            sfov = SHOT.fov;
            hasShot = true;
        } else {
            float dt = Math.min(0.1f, Math.max(0, (nanos - lastNanos) / 1e9f));
            float k = 1 - (float) Math.exp(-dt * 4.5);
            sx += (eye[0] - sx) * k;
            sy += (eye[1] - sy) * k;
            sz += (eye[2] - sz) * k;
            syaw += Mth.wrapDegrees(yaw - syaw) * k;
            spitch += (pitch - spitch) * k;
            sfov += (SHOT.fov - sfov) * k;
        }
        lastNanos = nanos;
    }

    /** Part du cadrage "ecran plein" dans le plan (zoom du joueur, intro) ; au-dela de 0,5 l'interface se range. */
    public static float immersive() {
        return active && !exiting ? immersive : 0;
    }

    /** Intro de la machine filmee en cours (0..1). */
    public static float focus() {
        return active && !exiting ? focus : 0;
    }

    /** La machine filmee affiche son ecran de fin de partie (gain total d'un bonus), 0..1. */
    public static float summary() {
        return active && !exiting ? summary : 0;
    }

    /**
     * Plan qui cadre l'image de l'ecran (rect = {x0, y0, x1, y1, z}, face a +Z, repere du modele) sur toute la hauteur
     * de la vue (l'interface du bas se range pendant ce cadrage), sans rien en couper. Melange au plan demande par le
     * modele selon k (0..1).
     */
    private static void fitScreen(Minecraft mc, float[] rect, float k) {
        float fov = 40;
        double tan = Math.tan(Math.toRadians(fov) / 2);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double guiH = Math.max(120, mc.getWindow().getGuiScaledHeight());
        // dock garde (encaissement de la fusee, achat du bonus) : l'image s'arrete au-dessus de lui
        boolean dock = mc.screen instanceof fr.heraliz.casino.client.screen.MachineScreen<?> ms && ms.reservesDock();
        double top = 5 / guiH, bottom = (dock ? 64 : 5) / guiH;
        double band = Math.max(0.3, 1 - top - bottom);
        float w = rect[2] - rect[0], h = rect[3] - rect[1];
        double dist = Math.max(h * 1.01 / (2 * tan * band), w * 1.02 / (2 * tan * aspect));
        double viewH = 2 * dist * tan;
        // le centre de l'image de l'ecran se place au milieu de la bande libre (mesure depuis le haut de la vue)
        double c = top + band / 2;
        float cx = (rect[0] + rect[2]) / 2, cy = (rect[1] + rect[3]) / 2;
        float ty = (float) (cy + (c - 0.5) * viewH), ez = rect[4] + (float) dist;
        SHOT.set(Mth.lerp(k, SHOT.ex, cx), Mth.lerp(k, SHOT.ey, ty), Mth.lerp(k, SHOT.ez, ez), Mth.lerp(k, SHOT.tx, cx), Mth.lerp(k, SHOT.ty, ty),
                Mth.lerp(k, SHOT.tz, rect[4]), Mth.lerp(k, SHOT.fov, fov));
    }

    /**
     * La camera ne traverse pas les murs : en partant de la machine vers l'oeil, on s'arrete avant le premier bloc
     * etranger (les blocs de la machine elle-meme sont ignores).
     */
    private static double[] clearEye(Level level, BlockPos master, double tx, double ty, double tz, double ex, double ey, double ez) {
        double dx = ex - tx, dy = ey - ty, dz = ez - tz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3) return new double[]{ex, ey, ez};
        int steps = (int) Math.ceil(len / 0.2);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double x = tx + dx * f, y = ty + dy * f, z = tz + dz * f;
            mp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            BlockState st = level.getBlockState(mp);
            if (st.isAir() || mp.equals(master)) continue;
            if (st.getBlock() instanceof MachinePartBlock && master.equals(MachinePartBlock.masterPos(mp, st))) continue;
            VoxelShape shape = st.getCollisionShape(level, mp);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                if (box.move(mp).inflate(0.1).contains(x, y, z)) {
                    double back = Math.max(0.15, f - 0.35 / len);
                    return new double[]{tx + dx * back, ty + dy * back, tz + dz * back};
                }
            }
        }
        return new double[]{ex, ey, ez};
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!active || !hasShot || !event.usedConfiguredFov()) return;
        double f = event.getFOV() + (sfov - event.getFOV()) * blend;
        event.setFOV(f);
        camFov = (float) f;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || !hasShot || SET_POSITION == null) return;
        Camera cam = event.getCamera();
        Vec3 p = cam.getPosition();
        double x = p.x + (sx - p.x) * blend, y = p.y + (sy - p.y) * blend, z = p.z + (sz - p.z) * blend;
        float yaw = event.getYaw() + Mth.wrapDegrees(syaw - event.getYaw()) * blend;
        float pitch = event.getPitch() + (spitch - event.getPitch()) * blend;
        try {
            SET_POSITION.invoke(cam, x, y, z);
            if (SET_ROTATION != null) SET_ROTATION.invoke(cam, yaw, pitch);
        } catch (Throwable e) {
            return;
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(event.getRoll() * (1 - blend));
        if (showPlayer && blend > 0.35f && DETACHED != null) {
            try {
                DETACHED.setBoolean(cam, true);
            } catch (Throwable ignored) {
            }
        }
        camX = x;
        camY = y;
        camZ = z;
        camYaw = yaw;
        camPitch = pitch;
    }

    /** Pas de contour de bloc vise (le joueur regarde la machine) pendant le plan cinematique. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (active && blend > 0.02f) event.setCanceled(true);
    }

    /** Pas de main ni d'objet tenu devant la camera pendant le plan cinematique. */
    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active && blend > 0.02f) event.setCanceled(true);
    }

    // =================================================================== visee a la souris
    /**
     * Rayon passant par le curseur (repere monde) : {ox, oy, oz, dx, dy, dz}. Coordonnees de la souris dans
     * l'interface (0..largeur, 0..hauteur).
     */
    public static double[] mouseRay(double mx, double my, int guiW, int guiH) {
        Minecraft mc = Minecraft.getInstance();
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double nx = mx / guiW * 2 - 1, ny = 1 - my / guiH * 2;
        double th = Math.tan(Math.toRadians(camFov) / 2);
        double yaw = Math.toRadians(camYaw), pitch = Math.toRadians(camPitch);
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        // avant, droite (= -gauche), haut de la camera
        double fx = -sy * cp, fy = -sp, fz = cy * cp;
        double rx = -cy, ry = 0, rz = -sy;
        double ux = -sp * sy, uy = cp, uz = sp * cy;
        double a = nx * th * aspect, b = ny * th;
        double dx = fx + rx * a + ux * b, dy = fy + ry * a + uy * b, dz = fz + rz * a + uz * b;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[]{camX, camY, camZ, dx / l, dy / l, dz / l};
    }

    /** Rayon de la souris exprime dans le repere du modele de la machine (px) : {ox, oy, oz, dx, dy, dz}. */
    public static float[] mouseRayModel(MachineBlockEntity be, double mx, double my, int guiW, int guiH) {
        double[] r = mouseRay(mx, my, guiW, guiH);
        BlockPos pos = be.getBlockPos();
        float yRot = MachineRenderer.yRot(be.getBlockState());
        float[] o = MachineRenderer.toModel((float) (r[0] - pos.getX()), (float) (r[1] - pos.getY()), (float) (r[2] - pos.getZ()), yRot);
        float[] q = MachineRenderer.toModel((float) (r[0] + r[3] - pos.getX()), (float) (r[1] + r[4] - pos.getY()), (float) (r[2] + r[5] - pos.getZ()), yRot);
        return new float[]{o[0], o[1], o[2], q[0] - o[0], q[1] - o[1], q[2] - o[2]};
    }
}
