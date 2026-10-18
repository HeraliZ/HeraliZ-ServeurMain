package fr.heraliz.casino.client.zone;

import com.mojang.math.Axis;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.client.camera.CasinoCamera;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.entity.EscortEntity;
import fr.heraliz.casino.entity.GuardEntity;
import fr.heraliz.casino.net.ZoneCutscenePacket;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * L'expulsion d'un joueur vue comme une scene de film : bandes noires, et une camera qui change de plan a chaque temps
 * de la scene.
 * <ol>
 * <li>par-dessus l'epaule du joueur : les deux videurs foncent sur lui ;</li>
 * <li>plan serre de trois quarts : ils l'attrapent ;</li>
 * <li>travelling arriere devant le joueur qu'on pousse, puis plan haut, de dos, vers la porte ;</li>
 * <li>dehors, face a la porte : il est jete vers la camera, atterrit a plat ventre ; les videurs s'epoussettent.</li>
 * </ol>
 * Les temps de la scene viennent du serveur (phase de la monture du joueur) : tous les clients voient la meme chose,
 * seul le joueur expulse a la camera de cinema.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class ZoneCinema implements IGuiOverlay {
    public static final ZoneCinema OVERLAY = new ZoneCinema();

    private ZoneCinema() {
    }

    private static final long BLEND_IN_MS = 600, BLEND_OUT_MS = 700;
    /** Ticks de la scene de fin (joueur a terre, videurs sur le pas de la porte) avant de rendre la vue au joueur. */
    private static final int OUTRO = 54;
    /**
     * Ticks a plat ventre apres la chute (comme le serveur, {@code Ejection.after}). La pose forcee du serveur n'est pas
     * envoyee au jeu du joueur, qui recalcule la sienne : sans ca, il se voyait debout des l'atterrissage.
     */
    private static final int PRONE = 38;
    private static boolean prone;
    private static final int SHOT_WAIT = 0, SHOT_HELD = 1, SHOT_FRONT = 2, SHOT_BACK = 3, SHOT_DOOR = 4;

    private static boolean active, exiting, rode, landed;
    private static int escortId = -1, guardA = -1, guardB = -1, reason;
    private static Vec3 door = Vec3.ZERO;
    /** Direction de l'exterieur, et cote de la porte ou se place la camera du dernier plan. */
    private static double nx, nz, side = 1;
    private static int age, landedAt;
    private static byte phase;
    private static int phaseAge;
    private static long startMs, exitMs;
    private static float exitFrom;
    @Nullable
    private static CameraType savedType;

    // plan en cours (monde), lisse dans le temps
    private static int shot = -1;
    private static double ex, ey, ez, tx, ty, tz;
    private static float fov = 60;
    /** Direction du joueur figee au debut du plan serre ; cote de la camera du plan de dos. */
    private static double f0x, f0z = 1, backSide = 1;
    private static long lastNanos;
    private static float blend;

    // =================================================================== commandes
    public static boolean active() {
        return active;
    }

    /** Plan en cours (tests automatiques), -1 hors de la scene. */
    public static int shot() {
        return active ? shot : -1;
    }

    /** Les repliques de ce videur sont affichees en sous-titres (sa bulle n'est pas dessinee). */
    public static boolean subtitles(int guard) {
        return active && (guard == guardA || guard == guardB);
    }

    public static void start(ZoneCutscenePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) mc.setScreen(null);
        CasinoCamera.abort();
        if (!active) savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        escortId = p.escort();
        guardA = p.g1();
        guardB = p.g2();
        reason = p.reason();
        door = new Vec3(p.doorX(), p.doorY(), p.doorZ());
        nx = p.nx();
        nz = p.nz();
        active = true;
        exiting = false;
        rode = false;
        landed = false;
        age = 0;
        landedAt = 0;
        phase = EscortEntity.WAIT;
        phaseAge = 0;
        shot = -1;
        blend = 0;
        startMs = Util.getMillis();
        side = pickSide(mc);
    }

    /** Retour doux a la vue du joueur. */
    private static void exit() {
        if (exiting) return;
        exiting = true;
        exitMs = Util.getMillis();
        exitFrom = blend;
    }

    private static void stop() {
        relever();
        active = false;
        exiting = false;
        shot = -1;
        blend = 0;
        if (savedType != null) {
            Minecraft.getInstance().options.setCameraType(savedType);
            savedType = null;
        }
    }

    // =================================================================== deroulement
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        age++;
        if (exiting) return;
        Entity e = mc.level.getEntity(escortId);
        if (e instanceof EscortEntity esc && mc.player.getVehicle() == e) {
            rode = true;
            phase = esc.phase();
            phaseAge = esc.phaseAge;
        } else if (rode && !landed) {
            // lache en plein vol : il atterrit ; lache avant : l'expulsion a ete interrompue
            if (phase == EscortEntity.THROWN) {
                landed = true;
                landedAt = age;
            } else {
                exit();
            }
        } else if (!rode && age > 60) {
            exit();
        }
        if (landed && age - landedAt < PRONE) {
            mc.player.addTag(EscortPoses.RESTE_ALLONGE);
            mc.player.setForcedPose(Pose.SWIMMING);
            prone = true;
        } else {
            relever();
        }
        if ((landed && age - landedAt >= OUTRO) || age > 700) exit();
    }

    /** Fin de la pose a plat ventre (seulement si c'est la scene qui l'a mise). */
    private static void relever() {
        if (!prone) return;
        prone = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.removeTag(EscortPoses.RESTE_ALLONGE);
            mc.player.setForcedPose(null);
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }
        long now = Util.getMillis();
        if (exiting) {
            float t = (now - exitMs) / (float) BLEND_OUT_MS;
            if (t >= 1) {
                stop();
                return;
            }
            blend = exitFrom * (1 - Ease.inOutSine(t));
        } else {
            blend = Ease.inOutSine(Math.min(1f, (now - startMs) / (float) BLEND_IN_MS));
        }
        updateShot(mc, mc.player, event.renderTickTime);
    }

    // =================================================================== plans
    /** Camera du dernier plan : dehors, face a la porte, un peu de cote. */
    private static Vec3 doorEye(double s) {
        return new Vec3(door.x + nx * 7.2 - nz * 1.5 * s, door.y + 1.35, door.z + nz * 7.2 + nx * 1.5 * s);
    }

    /** Cote de la porte le plus degage pour la camera du dernier plan. */
    private static double pickSide(Minecraft mc) {
        Vec3 from = new Vec3(door.x + nx * 0.8, door.y + 1.35, door.z + nz * 0.8);
        double best = -1, pick = 1;
        for (double s : new double[]{1, -1}) {
            double d = clip(mc, from, doorEye(s)).distanceTo(from);
            if (d > best + 0.05) {
                best = d;
                pick = s;
            }
        }
        return pick;
    }

    /** La camera ne traverse pas les murs : en partant du sujet, elle s'arrete avant le premier bloc. */
    private static Vec3 clip(Minecraft mc, Vec3 target, Vec3 eye) {
        if (mc.level == null || mc.player == null) return eye;
        BlockHitResult hit = mc.level.clip(new ClipContext(target, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return eye;
        Vec3 d = hit.getLocation().subtract(target);
        double len = d.length();
        if (len < 1e-3) return target;
        return target.add(d.scale(Math.max(0.25, len - 0.35) / len));
    }

    private static void updateShot(Minecraft mc, LocalPlayer p, float partial) {
        Vec3 at = p.getPosition(partial);
        Entity e = mc.level.getEntity(escortId);
        float face = e instanceof EscortEntity esc ? esc.face() : p.yBodyRot;
        double fx = -Math.sin(Math.toRadians(face)), fz = Math.cos(Math.toRadians(face));
        // droite du joueur
        double rx = -fz, rz = fx;
        float t = phaseAge + partial;
        int want;
        Vec3 eye, target;
        float wantFov;
        if (landed || phase >= EscortEntity.WINDUP) {
            want = SHOT_DOOR;
            eye = doorEye(side);
            if (landed) {
                // le joueur a terre au premier plan, les videurs sur le pas de la porte derriere lui
                target = new Vec3(Mth.lerp(0.3, at.x, door.x), at.y + 0.45, Mth.lerp(0.3, at.z, door.z));
                wantFov = 50;
            } else if (phase == EscortEntity.WINDUP) {
                target = new Vec3(door.x - nx * 0.5, door.y + 1.25, door.z - nz * 0.5);
                wantFov = 56;
            } else {
                target = at.add(0, 0.9, 0);
                wantFov = 58;
            }
        } else if (phase == EscortEntity.MARCH && t < 46) {
            // travelling arriere : la camera recule devant le joueur qu'on pousse
            want = SHOT_FRONT;
            eye = new Vec3(at.x + fx * 3.3 + rx * 0.9, at.y + 1.4, at.z + fz * 3.3 + rz * 0.9);
            target = at.add(0, 1.2, 0);
            wantFov = 58;
        } else if (phase == EscortEntity.MARCH) {
            // plan haut, de trois quarts arriere : la porte approche ; du cote le plus degage
            want = SHOT_BACK;
            target = new Vec3(at.x + fx * 0.9, at.y + 1.1, at.z + fz * 0.9);
            if (shot != SHOT_BACK) {
                double best = -1;
                for (double s : new double[]{1, -1}) {
                    Vec3 c = new Vec3(at.x - fx * 2.7 + rx * 2.3 * s, at.y + 2.8, at.z - fz * 2.7 + rz * 2.3 * s);
                    double d = clip(mc, target, c).distanceTo(target);
                    if (d > best + 0.05) {
                        best = d;
                        backSide = s;
                    }
                }
            }
            eye = new Vec3(at.x - fx * 2.7 + rx * 2.3 * backSide, at.y + 2.8, at.z - fz * 2.7 + rz * 2.3 * backSide);
            wantFov = 62;
        } else if (phase == EscortEntity.HELD && t >= 6) {
            // plan serre de trois quarts, une fois les videurs de chaque cote (le joueur pivote : le cadre reste fixe)
            want = SHOT_HELD;
            if (shot != SHOT_HELD) {
                f0x = fx;
                f0z = fz;
            }
            eye = new Vec3(at.x + f0x * 2.5 - f0z * 1.6, at.y + 1.45, at.z + f0z * 2.5 + f0x * 1.6);
            target = at.add(0, 1.3, 0);
            wantFov = 50;
        } else {
            // par-dessus l'epaule : les videurs arrivent en courant
            want = SHOT_WAIT;
            Entity a = mc.level.getEntity(guardA), b = mc.level.getEntity(guardB);
            Vec3 mid = a != null && b != null ? a.getPosition(partial).add(b.getPosition(partial)).scale(0.5)
                    : new Vec3(at.x + fx * 4, at.y, at.z + fz * 4);
            eye = new Vec3(at.x - fx * 2.5 + rx * 1.0, at.y + 1.85, at.z - fz * 2.5 + rz * 1.0);
            target = new Vec3(Mth.lerp(0.6, at.x, mid.x), Mth.lerp(0.6, at.y + 1.4, mid.y + 1.2), Mth.lerp(0.6, at.z, mid.z));
            wantFov = 62 - 6 * Math.min(1f, (age + partial) / 50f);
        }
        eye = clip(mc, target, eye);
        long nanos = System.nanoTime();
        if (shot != want) {
            // changement de plan : coupe franche
            shot = want;
            ex = eye.x;
            ey = eye.y;
            ez = eye.z;
            tx = target.x;
            ty = target.y;
            tz = target.z;
            fov = wantFov;
        } else {
            float dt = Mth.clamp((nanos - lastNanos) / 1e9f, 0f, 0.1f);
            float ke = 1 - (float) Math.exp(-dt * 4.5), kt = 1 - (float) Math.exp(-dt * 7);
            ex += (eye.x - ex) * ke;
            ey += (eye.y - ey) * ke;
            ez += (eye.z - ez) * ke;
            tx += (target.x - tx) * kt;
            ty += (target.y - ty) * kt;
            tz += (target.z - tz) * kt;
            fov += (wantFov - fov) * ke;
        }
        lastNanos = nanos;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!active || shot < 0 || !event.usedConfiguredFov()) return;
        event.setFOV(event.getFOV() + (fov - event.getFOV()) * blend);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || shot < 0) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        double x = c.x + (ex - c.x) * blend, y = c.y + (ey - c.y) * blend, z = c.z + (ez - c.z) * blend;
        float by = event.getYaw() + Mth.wrapDegrees(yaw - event.getYaw()) * blend;
        float bp = event.getPitch() + (pitch - event.getPitch()) * blend;
        float roll = event.getRoll() * (1 - blend);
        if (landed) {
            // secousse du choc
            float k = age - landedAt + (float) event.getPartialTick();
            if (k < 9) {
                float a = (1 - k / 9f) * blend;
                roll += Mth.sin(k * 2.4f) * 2.6f * a;
                y += Mth.sin(k * 3.1f) * 0.05f * a;
            }
        }
        if (!CasinoCamera.place(cam, x, y, z, by, bp, blend > 0.3f)) return;
        event.setYaw(by);
        event.setPitch(bp);
        event.setRoll(roll);
    }

    /** Pas de contour de bloc vise, de main devant la camera ni de coup donne pendant la scene. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (active && blend > 0.02f) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent event) {
        if (active && blend > 0.02f) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active || exiting) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** Le joueur ne marche pas (ni ne rampe, une fois a terre) tant que la scene n'est pas finie. */
    @SubscribeEvent
    public static void onMove(MovementInputUpdateEvent event) {
        if (!active || exiting) return;
        Input in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    // =================================================================== bandes noires et titres
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Font font = mc.font;
        int bar = Math.round(h * 0.125f * blend);
        float t = age + partial;
        if (landed) {
            // eclair du choc
            float k = age - landedAt + partial;
            if (k < 3) g.fill(0, 0, w, h, ((int) (70 * (1 - k / 3f)) << 24) | 0xFFFFFF);
        }
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        boolean staff = reason != 0;
        // ---- titre d'ouverture : le mot tombe, puis s'efface
        if (t > 8 && t < 76 && !landed) {
            float in = Mth.clamp((t - 8) / 6f, 0, 1), out = Mth.clamp((76 - t) / 10f, 0, 1);
            float slam = 1 - in * in * (3 - 2 * in);
            float s = Math.min(3f, w / 110f) * (1 + 0.5f * slam);
            Component title = Component.translatable(staff ? "overlay.heralizcasino.eject.title_staff" : "overlay.heralizcasino.eject.title")
                    .withStyle(st -> st.withBold(true));
            stamp(g, font, title, w / 2f, h * 0.24f, s, 0xE8443A, Math.min(1, in * 2) * out, -3f);
            Component sub = Component.translatable(staff ? "overlay.heralizcasino.eject.sub_staff" : "overlay.heralizcasino.eject.sub");
            stamp(g, font, sub, w / 2f, h * 0.24f + 8 * s, 1f, 0xF2E6CC, Mth.clamp((t - 16) / 6f, 0, 1) * out, 0);
        }
        // ---- a l'atterrissage
        if (landed) {
            float k = age - landedAt + partial;
            if (k < 48) {
                float in = Mth.clamp(k / 5f, 0, 1), out = Mth.clamp((48 - k) / 9f, 0, 1);
                float slam = 1 - in * in * (3 - 2 * in);
                float s = Math.min(3.4f, w / 100f) * (1 + 0.7f * slam);
                Component title = Component.translatable("overlay.heralizcasino.eject.out").withStyle(st -> st.withBold(true));
                stamp(g, font, title, w / 2f, h * 0.25f, s, 0xFFB040, Math.min(1, in * 2) * out, -4f);
                Component sub = Component.translatable(staff ? "overlay.heralizcasino.eject.out_sub_staff" : "overlay.heralizcasino.eject.out_sub");
                stamp(g, font, sub, w / 2f, h * 0.25f + 8 * s, 1f, 0xF2E6CC, Mth.clamp((k - 8) / 6f, 0, 1) * out, 0);
            }
        }
        // ---- sous-titre : le videur qui parle
        GuardEntity speaker = null;
        int best = GuardEntity.SPEECH_TICKS;
        for (int id : new int[]{guardA, guardB}) {
            if (mc.level.getEntity(id) instanceof GuardEntity ge && ge.speechAge < best && !ge.speech().getString().isEmpty()) {
                best = ge.speechAge;
                speaker = ge;
            }
        }
        if (speaker != null && bar >= 14) {
            Component name = Component.translatable("overlay.heralizcasino.eject.speaker").withStyle(st -> st.withBold(true).withColor(0xFFB040));
            Component line = name.copy().append(Component.literal("   ")).append(speaker.speech().copy().withStyle(st -> st.withBold(false).withColor(0xFFFFFF)));
            float alpha = Mth.clamp(best / 3f, 0, 1) * Mth.clamp((GuardEntity.SPEECH_TICKS - best) / 8f, 0, 1);
            float s = Math.min(1f, (w - 24f) / Math.max(1, font.width(line)));
            stamp(g, font, line, w / 2f, h - bar / 2f, s, 0xFFFFFF, alpha, 0);
        }
    }

    /** Texte centre, ombre, a l'echelle et a l'opacite donnees. */
    private static void stamp(GuiGraphics g, Font font, Component c, float cx, float cy, float scale, int rgb, float alpha, float rot) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a < 8) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        if (rot != 0) g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        g.pose().scale(scale, scale, 1);
        float x = -font.width(c) / 2f;
        // l'ombre n'a de sens que pour un texte d'une seule couleur (les couleurs du style l'emportent sur la teinte)
        if (rgb != 0xFFFFFF) g.drawString(font, c.getVisualOrderText(), x + 0.7f, -3.3f, a << 24, false);
        g.drawString(font, c.getVisualOrderText(), x, -4f, (a << 24) | rgb, false);
        g.pose().popPose();
    }
}
