package fr.heraliz.casino.client.zone;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.net.ZoneCutscenePacket;
import fr.heraliz.casino.net.ZoneDialogPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * La zone du casino cote client : quand un videur arrete le joueur, son regard glisse vers le visage du videur (avec un
 * leger zoom) pendant que celui-ci lui parle ; l'expulsion d'un joueur fauche est un plan de cinema ({@link ZoneCinema}).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class ZoneClient {
    private ZoneClient() {
    }

    /** Hauteur des yeux du videur au-dessus de ses pieds. */
    private static final double GUARD_EYES = GuardRenderer.EYES;

    /** Videur qui parle au joueur (identifiant d'entite, -1 : aucun). */
    private static int guardId = -1;
    /** Part du zoom sur le videur (0 : vue normale, 1 : dialogue en cours). */
    private static float zoom;
    private static long lastNanos;

    /** Un videur arrete le joueur (ou lui repond) : dialogue et regard tourne vers lui. */
    public static void dialog(ZoneDialogPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ZoneCinema.active()) return;
        guardId = p.guard();
        mc.setScreen(new EntranceScreen(p));
    }

    /** Debut de l'expulsion du joueur par les videurs. */
    public static void cutscene(ZoneCutscenePacket p) {
        ZoneCinema.start(p);
    }

    /** Le dialogue avec un videur est ouvert. */
    public static boolean talking() {
        return Minecraft.getInstance().screen instanceof EntranceScreen;
    }

    /** Le dialogue ouvert est celui de ce videur (sa replique est dans le bandeau : pas de bulle en plus). */
    public static boolean talkingTo(int guard) {
        return guard == guardId && talking();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Mth.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
        lastNanos = now;
        LocalPlayer p = mc.player;
        Entity guard = p != null && mc.level != null && guardId >= 0 && talking() ? mc.level.getEntity(guardId) : null;
        if (guard != null && (guard.isRemoved() || guard.distanceToSqr(p) > 24 * 24)) guard = null;
        // zoom d'autant plus leger que le videur est pres (de face a un pas, il remplit deja l'ecran)
        float goal = guard != null ? Mth.clamp(((float) Math.sqrt(guard.distanceToSqr(p)) - 1.5f) / 2f, 0f, 1f) : 0;
        zoom += (goal - zoom) * (1 - (float) Math.exp(-dt * 6));
        if (goal == 0 && zoom < 0.003f) zoom = 0;
        if (guard == null || mc.isPaused()) return;
        // le regard du joueur glisse vers le visage du videur (meme effet qu'un mouvement de souris)
        float partial = event.renderTickTime;
        Vec3 eye = p.getEyePosition(partial), g = guard.getPosition(partial);
        double dx = g.x - eye.x, dy = g.y + GUARD_EYES - eye.y, dz = g.z - eye.z;
        if (dx * dx + dz * dz < 0.04) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float k = 1 - (float) Math.exp(-dt * 7);
        float dYaw = Mth.wrapDegrees(yaw - p.getYRot()) * k, dPitch = (pitch - p.getXRot()) * k;
        p.turn(dYaw / 0.15, dPitch / 0.15);
    }

    /** Pas de main (ni d'objet tenu) devant le videur pendant qu'il parle. */
    @SubscribeEvent
    public static void onHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (talking()) event.setCanceled(true);
    }

    /** Leger zoom sur le videur pendant qu'il parle. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (zoom > 0 && event.usedConfiguredFov()) event.setFOV(event.getFOV() * (1 - 0.2 * zoom));
    }
}
