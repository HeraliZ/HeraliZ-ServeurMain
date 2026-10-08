package fr.heraliz.armory.client.devtools;

import fr.heraliz.armory.HeralizArmory;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Harnais client : pour chaque ensemble, l'equipe puis prend des vues avec une camera libre qui suit le joueur
 * (face, trois-quarts, dos, course, accroupi, nuit, premiere personne). Captures dans run/screenshots, puis quitte.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class HarnessClient {
    private HarnessClient() {}

    private static final boolean ENABLED = Boolean.getBoolean("heraliz.armory.harness");
    private static final String[] SETS = System.getProperty("heraliz.armory.harness.sets", "citadin").split(",");

    /** Vue : decalage camera (repere du joueur : x = droite du perso, z = devant), hauteur visee, mouvement. */
    private record Shot(String name, String command, double side, double up, double front, double lookY, String move, int ticks) {}

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int step = -1, phase, tick;
    private static boolean done;
    private static ArmorStand cam;

    private static void build() {
        String reset = "effect clear @s;kill @e[type=heraliz_armory:mannequin];";
        for (String token : SETS) {
            if (token.startsWith("nvg:")) {
                // lunettes de vision nocturne : relevees, puis baissees (vues externes), puis vue a la premiere personne de nuit
                String set = token.substring(4);
                SHOTS.add(new Shot(set + "_nvg_1_releve", reset + "armory equip " + set, 0.8, 1.75, 1.6, 1.55, "", 40));
                SHOTS.add(new Shot(set + "_nvg_2_baisse", "armory nvg", 0.8, 1.75, 1.6, 1.55, "", 30));
                SHOTS.add(new Shot(set + "_nvg_3_profil", null, 2.2, 1.7, 0.2, 1.55, "", 10));
                SHOTS.add(new Shot(set + "_nvg_4_nuit_vue", "time set 18000", 0, 0, 0, 0, "fp", 40));
                // de nuit, vue de l'exterieur : lueur verte des objectifs en service
                SHOTS.add(new Shot(set + "_nvg_4b_nuit_lueur", null, 0.4, 1.7, 3.2, 1.55, "", 30));
                SHOTS.add(new Shot(set + "_nvg_5_releve", "armory nvg;time set 6000", 0.8, 1.75, 1.6, 1.55, "", 30));
                continue;
            }
            if (token.startsWith("mannequin:")) {
                // mannequin habille (joueur invisible au meme endroit : la camera suit le joueur) + capture du menu
                String[] parts = token.substring(10).split("\\+");
                String set = "mannequin_" + parts[parts.length - 1];
                StringBuilder c = new StringBuilder(reset + "armory strip;effect give @s minecraft:invisibility 99999 0 true;armory mannequin "
                        + parts[0] + " here");
                for (int i = 1; i < parts.length; i++) c.append(";armory dress ").append(parts[i]);
                SHOTS.add(new Shot(set + "_1_face", c.toString(), 0, 1.25, 3.0, 1.0, "", 40));
                SHOTS.add(new Shot(set + "_2_trois_quarts", null, 2.0, 1.45, 2.3, 1.0, "", 10));
                SHOTS.add(new Shot(set + "_3_profil", null, 3.0, 1.2, 0.1, 1.0, "", 10));
                SHOTS.add(new Shot(set + "_4_dos", null, -1.0, 1.5, -2.8, 1.0, "", 10));
                SHOTS.add(new Shot(set + "_7_tete", null, 0.8, 1.75, 1.6, 1.55, "", 10));
                SHOTS.add(new Shot(set + "_9_menu", "armory mannequinmenu", 0, 0, 0, 0, "gui", 30));
                continue;
            }
            // "base+gilet" : la tenue de base puis la piece seule enfilee par-dessus (captures nommees d'apres la derniere)
            String[] parts = token.split("\\+");
            String set = parts[parts.length - 1];
            StringBuilder equip = new StringBuilder(reset + "armory equip " + parts[0]);
            for (int i = 1; i < parts.length; i++) equip.append(";armory wear ").append(parts[i]);
            SHOTS.add(new Shot(set + "_1_face", equip.toString(), 0, 1.25, 3.0, 1.0, "", 40));
            SHOTS.add(new Shot(set + "_2_trois_quarts", null, 2.0, 1.45, 2.3, 1.0, "", 10));
            SHOTS.add(new Shot(set + "_3_profil", null, 3.0, 1.2, 0.1, 1.0, "", 10));
            SHOTS.add(new Shot(set + "_4_dos", null, -1.0, 1.5, -2.8, 1.0, "", 10));
            SHOTS.add(new Shot(set + "_5_course", null, 2.2, 1.3, 1.8, 1.0, "sprint", 24));
            SHOTS.add(new Shot(set + "_6_accroupi", null, 2.0, 1.2, 2.2, 0.8, "sneak", 16));
            SHOTS.add(new Shot(set + "_7_tete", null, 0.8, 1.75, 1.6, 1.55, "", 10));
            SHOTS.add(new Shot(set + "_8_main", null, 0, 0, 0, 0, "fp", 16));
        }
        SHOTS.add(new Shot("fin_reset", reset.substring(0, reset.length() - 1), 0, 1.25, 3.0, 1.0, "", 4));
        String last = SETS[SETS.length - 1];
        last = last.startsWith("mannequin:") ? "mannequin_" + last.substring(10) : last.startsWith("nvg:") ? last.substring(4) : last;
        SHOTS.add(new Shot("nuit_" + last.substring(last.lastIndexOf('+') + 1), "time set 14000", 1.8, 1.4, 2.6, 1.0, "", 30));
        // mesure FPS : 40 supports d'armure en armure de fer vanilla, puis en ensemble Belier (memes slots vanilla)
        SHOTS.add(new Shot("perf_vide", "time set 6000", 0, 3.0, 9.0, 1.0, "perf", 220));
        SHOTS.add(new Shot("perf_fer", "#stands iron", 0, 3.0, 9.0, 1.0, "perf", 220));
        SHOTS.add(new Shot("perf_armurerie", "#stands belier", 0, 3.0, 9.0, 1.0, "perf", 220));
    }

    private static final List<Integer> FPS = new ArrayList<>();

    private static void stands(String kind) {
        cmd("kill @e[type=armor_stand]");
        String[] items = kind.equals("iron")
                ? new String[]{"minecraft:iron_boots", "minecraft:iron_leggings", "minecraft:iron_chestplate", "minecraft:iron_helmet"}
                : new String[]{"heraliz_armory:belier_feet", "heraliz_armory:belier_legs", "heraliz_armory:belier_chest", "heraliz_armory:belier_head"};
        StringBuilder nbt = new StringBuilder("{NoGravity:1b,ShowArms:1b,ArmorItems:[");
        for (int i = 0; i < 4; i++) nbt.append(i > 0 ? "," : "").append("{id:\"").append(items[i]).append("\",Count:1b}");
        nbt.append("]}");
        for (int i = 0; i < 40; i++) {
            double x = -5 + (i % 8) * 1.4, z = 4 + (i / 8) * 1.6;
            cmd(String.format(Locale.ROOT, "summon armor_stand %.1f ~ %.1f %s", x, z, nbt));
        }
    }

    private static void cmd(String c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.connection.sendCommand(c);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (SHOTS.isEmpty()) build();
        tick++;
        if (step < 0) {
            if (tick == 20) {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                cmd("gamerule sendCommandFeedback false");
                cmd("tp @s 0.5 ~ 0.5 0 0");
            }
            if (tick < 100) return;
            step = 0;
            phase = 0;
            tick = 0;
        }
        if (step >= SHOTS.size()) {
            done = true;
            mc.setCameraEntity(mc.player);
            mc.stop();
            return;
        }
        Shot s = SHOTS.get(step);
        boolean moving = s.move().equals("sprint") || s.move().equals("sneak");
        if (phase == 0) {
            mc.options.hideGui = true;
            if (s.command() != null && s.command().startsWith("#stands ")) stands(s.command().substring(8));
            else if (s.command() != null) for (String c : s.command().split(";")) cmd(c);
            if (s.move().equals("perf")) {
                mc.options.framerateLimit().set(260);
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
            }
            if (!moving) cmd("tp @s 0.5 ~ 0.5 0 0");
            if (s.move().equals("sprint")) cmd("tp @s 0.5 ~ 0.5 0 0");
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyShift.setDown(false);
            phase = 1;
            tick = 0;
        } else if (phase == 1) {
            if (s.move().equals("sprint")) {
                // fenetre sans focus sous Xvfb : on pousse directement le joueur (l'animation de course suit le deplacement)
                mc.player.setSprinting(true);
                double yaw = Math.toRadians(mc.player.getYRot());
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(-Math.sin(yaw) * 0.28, v.y, Math.cos(yaw) * 0.28);
            }
            if (s.move().equals("sneak")) mc.options.keyShift.setDown(true);
            if (s.move().equals("gui")) {
                mc.options.hideGui = false;
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            } else if (s.move().equals("fp")) {
                mc.options.hideGui = false;
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setXRot(10);
            } else {
                follow(mc, s);
            }
            if (s.move().equals("perf") && tick > 60 && tick % 20 == 0) FPS.add(mc.getFps());
            if (tick < s.ticks()) return;
            if (!FPS.isEmpty()) {
                HeralizArmory.LOGGER.info("[harness] PERF {} : moyenne {} fps, min {} fps", s.name(),
                        String.format(Locale.ROOT, "%.1f", FPS.stream().mapToInt(Integer::intValue).average().orElse(0)),
                        FPS.stream().mapToInt(Integer::intValue).min().orElse(0));
                FPS.clear();
            }
            Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), c -> {});
            HeralizArmory.LOGGER.info("[harness] capture {}", s.name());
            phase = 2;
            tick = 0;
        } else {
            if (s.move().equals("gui") && mc.player != null) mc.player.closeContainer();
            if (!s.move().equals("fp") && !s.move().equals("gui")) follow(mc, s);
            if (tick < 4) return;
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyShift.setDown(false);
            step++;
            phase = 0;
            tick = 0;
        }
    }

    /** Camera libre placee dans le repere du joueur (oriente vers +z, yaw 0). */
    private static void follow(Minecraft mc, Shot s) {
        if (cam == null) cam = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
        Vec3 p = mc.player.getPosition(1.0f);
        double yaw = Math.toRadians(mc.player.getYRot());
        // avant du joueur (yaw 0) = +z ; droite du perso = -x
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Vec3 eye = new Vec3(p.x + fx * s.front() + rx * s.side(), p.y + s.up(), p.z + fz * s.front() + rz * s.side());
        Vec3 target = new Vec3(p.x, p.y + s.lookY(), p.z);
        Vec3 d = target.subtract(eye);
        float camYaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float camPitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        double eh = cam.getEyeHeight();
        cam.setPos(eye.x, eye.y - eh, eye.z);
        cam.xo = cam.xOld = eye.x;
        cam.yo = cam.yOld = eye.y - eh;
        cam.zo = cam.zOld = eye.z;
        cam.setYRot(camYaw);
        cam.yRotO = camYaw;
        cam.setXRot(camPitch);
        cam.xRotO = camPitch;
        cam.setYHeadRot(camYaw);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(cam);
    }
}
