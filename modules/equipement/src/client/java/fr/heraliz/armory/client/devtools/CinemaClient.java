package fr.heraliz.armory.client.devtools;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.client.NvgClient;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tournage video (dev, -Dheraliz.armory.cinema=<fichier>) : rendu image par image a pas de temps fixe, quelle que soit la
 * vitesse de rendu (shaders sans carte graphique : plusieurs secondes par image).
 *
 * Fichier de scenario, une ligne par image ou par commande :
 *   cmd <commande>                         : commande envoyee au serveur avant le tournage (mise en scene)
 *   wait <ticks>                           : attente (chargement, compilation des shaders)
 *   f <x> <y> <z> <yaw> <pitch> <fov> <chauffe> [roulis] [mise au point] [| cmd1 ; cmd2 ...]
 *                                          : une image ; chauffe = images rendues sans capture avant (apres une coupe,
 *                                            le temps que l'anticrenelage temporel se recale) ; commandes envoyees avant ;
 *                                            mise au point = distance nette en blocs (profondeur de champ du shader,
 *                                            reglee par la luminosite : WB_DOF_FOCUS=-1, nette a 200 * luminosite^2).
 *   p <nom> <x> <y> <z> <yaw> <penche> <roulis> <accroupi> <lunettes> <tete xyz> <corps xyz> <bras d xyz> <bras g xyz>
 *     <jambe d xyz> <jambe g xyz>          : pose du mannequin de ce nom (CustomName) a partir de l'image precedente,
 *                                            jusqu'a la suivante qui le nomme ; angles en degres, accroupi 0..1,
 *                                            lunettes 0..1 (baissees) ou -1 (etat de l'objet).
 * L'horloge des animations du mod (lunettes) avance de 1/30 s par image capturee. Captures : screenshots/f00000.png...
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class CinemaClient {
    private CinemaClient() {}

    public static final String SCRIPT = System.getProperty("heraliz.armory.cinema", "");
    public static final boolean ACTIVE = !SCRIPT.isEmpty();
    private static final double FRAME_MS = 1000.0 / 30.0;

    private record Frame(double x, double y, double z, float yaw, float pitch, int fov, int warm, float roll, float focus, String[] cmds,
                         List<Pose> poses) {}

    /** Pose d'un mannequin nomme (animation pilotee par le scenario) : position des pieds, orientation, penche depuis les
     *  pieds, roulis, accroupissement, lunettes, et rotations des six parties (tete, corps, bras d/g, jambes d/g). */
    public record Pose(String name, double x, double y, double z, float yaw, float lean, float roll, float crouch, float nvg, float[] parts) {}

    private static final Map<String, Pose> POSES = new HashMap<>();

    /** Rendu de la lueur des objectifs pendant le tournage : 'eyes' (comme en jeu) ou un autre type compatible shaders ;
     *  'cycle' : un type different a chaque image (essais). */
    private static final String GLOW = System.getProperty("heraliz.armory.cinema.glow", "eyes");
    private static final String[] GLOW_MODES = {"eyes", "swirl", "emissive", "cutout", "beacon"};

    private static final List<String> SETUP = new ArrayList<>();
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static boolean loaded, started, done;
    private static int tick, setupIdx, waitTicks, frame, warmLeft = -1;
    private static boolean capture;
    private static long clockBase;
    private static ArmorStand cam;

    private static void load() {
        loaded = true;
        try {
            for (String line : Files.readAllLines(Path.of(SCRIPT), StandardCharsets.UTF_8)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("cmd ") || line.startsWith("wait ")) {
                    SETUP.add(line);
                } else if (line.startsWith("f ")) {
                    String[] parts = line.split("\\|", 2);
                    String[] v = parts[0].trim().split("\\s+");
                    String[] cmds = parts.length > 1 ? parts[1].split(";") : new String[0];
                    FRAMES.add(new Frame(Double.parseDouble(v[1]), Double.parseDouble(v[2]), Double.parseDouble(v[3]), Float.parseFloat(v[4]),
                            Float.parseFloat(v[5]), Integer.parseInt(v[6]), Integer.parseInt(v[7]), v.length > 8 ? Float.parseFloat(v[8]) : 0.0F,
                            v.length > 9 ? Float.parseFloat(v[9]) : 0.0F, cmds, new ArrayList<>()));
                } else if (line.startsWith("p ") && !FRAMES.isEmpty()) {
                    String[] v = line.split("\\s+");
                    float[] parts = new float[18];
                    for (int i = 0; i < 18; i++) parts[i] = Float.parseFloat(v[10 + i]);
                    FRAMES.get(FRAMES.size() - 1).poses().add(new Pose(v[1], Double.parseDouble(v[2]), Double.parseDouble(v[3]),
                            Double.parseDouble(v[4]), Float.parseFloat(v[5]), Float.parseFloat(v[6]), Float.parseFloat(v[7]),
                            Float.parseFloat(v[8]), Float.parseFloat(v[9]), parts));
                }
            }
        } catch (IOException e) {
            HeralizArmory.LOGGER.error("[cinema] scenario illisible {}", SCRIPT, e);
        }
        HeralizArmory.LOGGER.info("[cinema] {} commandes de mise en scene, {} images", SETUP.size(), FRAMES.size());
    }

    private static void cmd(Minecraft mc, String c) {
        c = c.strip();
        if (!c.isEmpty() && mc.player != null) mc.player.connection.sendCommand(c);
    }

    /** Mise en scene (ticks client) : commandes et attentes, puis demarrage du tournage. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ACTIVE || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!loaded) load();
        tick++;
        if (tick < 60 || started) return;
        if (tick == 60) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            cmd(mc, "gamerule sendCommandFeedback false");
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (setupIdx < SETUP.size()) {
            String s = SETUP.get(setupIdx++);
            if (s.startsWith("wait ")) {
                waitTicks = Integer.parseInt(s.substring(5).trim());
                return;
            }
            cmd(mc, s.substring(4));
        }
        started = true;
        clockBase = net.minecraft.Util.getMillis();
        NvgClient.clock = () -> clockBase + Math.round(frame * FRAME_MS);
        HeralizArmory.LOGGER.info("[cinema] tournage : {} images", FRAMES.size());
    }

    /** Avant chaque image : camera de l'image courante (et commandes de cette image). */
    @SubscribeEvent
    public static void onRender(TickEvent.RenderTickEvent event) {
        if (!ACTIVE || !started || done) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (event.phase == TickEvent.Phase.START) {
            if (frame >= FRAMES.size()) {
                done = true;
                HeralizArmory.LOGGER.info("[cinema] fin du tournage");
                mc.setCameraEntity(mc.player);
                mc.stop();
                return;
            }
            Frame f = FRAMES.get(frame);
            if (warmLeft < 0) {
                for (String c : f.cmds()) cmd(mc, c);
                for (Pose p : f.poses()) POSES.put(p.name(), p);
                warmLeft = f.warm();
            }
            place(mc, f);
            capture = warmLeft == 0;
        } else if (capture) {
            Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "f%05d.png", frame), mc.getMainRenderTarget(), c -> {});
            if (frame % 30 == 0) HeralizArmory.LOGGER.info("[cinema] image {} / {}", frame, FRAMES.size());
            frame++;
            warmLeft = -1;
            capture = false;
        } else {
            warmLeft--;
        }
    }

    /** Orientation imposee a la camera (la rotation de l'entite camera seule ne suffit pas pour le tangage). */
    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ACTIVE || !started || done || frame >= FRAMES.size()) return;
        Frame f = FRAMES.get(frame);
        event.setYaw(f.yaw());
        event.setPitch(f.pitch());
        event.setRoll(f.roll());
    }

    /** Pose imposee a ce mannequin pour l'image courante (null : pose de l'entite). */
    public static Pose pose(Entity e) {
        if (!ACTIVE || POSES.isEmpty()) return null;
        Component n = e.getCustomName();
        return n == null ? null : POSES.get(n.getString());
    }

    /** Type de rendu de la lueur verte des objectifs pendant le tournage. */
    public static RenderType glowType(ResourceLocation tex) {
        String mode = GLOW.equals("cycle") ? GLOW_MODES[frame % GLOW_MODES.length] : GLOW;
        return switch (mode) {
            case "swirl" -> RenderType.energySwirl(tex, 0.0F, 0.0F);
            case "emissive" -> RenderType.entityTranslucentEmissive(tex);
            case "cutout" -> RenderType.entityCutoutNoCull(tex);
            case "beacon" -> RenderType.beaconBeam(tex, false);
            default -> RenderType.eyes(tex);
        };
    }

    private static void place(Minecraft mc, Frame f) {
        if (cam == null) cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        double eh = cam.getEyeHeight();
        cam.setPos(f.x(), f.y() - eh, f.z());
        cam.xo = cam.xOld = f.x();
        cam.yo = cam.yOld = f.y() - eh;
        cam.zo = cam.zOld = f.z();
        cam.setYRot(f.yaw());
        cam.yRotO = f.yaw();
        cam.setXRot(f.pitch());
        cam.xRotO = f.pitch();
        cam.setYHeadRot(f.yaw());
        cam.yHeadRotO = f.yaw();
        mc.options.fov().set(f.fov());
        if (f.focus() > 0.0F) mc.options.gamma().set(Math.min(1.0, Math.sqrt(f.focus() / 200.0)));
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(cam);
    }
}
