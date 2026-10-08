package fr.heraliz.crates.client;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tremblement de camera des joueurs proches d'une caisse qui s'ouvre (impacts de la choregraphie, Design.shake).
 * Pleine force a moins de 5 blocs, nul au-dela de 16 ; desactivable (config client cameraShake).
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID, value = Dist.CLIENT)
public final class CameraShake {
    private CameraShake() {
    }

    /** Source par caisse : {intensite 0..1, heure de jeu du relevé}. */
    private static final Map<BlockPos, float[]> SOURCES = new ConcurrentHashMap<>();

    public static void report(BlockPos pos, float intensity, long gameTime) {
        if (intensity <= 0.001f) {
            SOURCES.remove(pos);
            return;
        }
        SOURCES.put(pos.immutable(), new float[]{Math.min(1f, intensity), gameTime});
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (SOURCES.isEmpty() || !ClientConfig.CAMERA_SHAKE.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long gt = mc.level.getGameTime();
        float amp = 0;
        for (Iterator<Map.Entry<BlockPos, float[]>> it = SOURCES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, float[]> en = it.next();
            float[] v = en.getValue();
            if (gt - (long) v[1] > 2) {
                it.remove();
                continue;
            }
            BlockPos p = en.getKey();
            double d = Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5));
            float fall = (float) Math.max(0, Math.min(1, 1 - (d - 5) / 11));
            amp = Math.max(amp, v[0] * fall);
        }
        if (amp <= 0.001f) return;
        double t = gt + e.getPartialTick();
        e.setPitch(e.getPitch() + (float) (amp * 1.5 * (Math.sin(t * 2.9) + 0.5 * Math.sin(t * 7.3 + 1.1))));
        e.setYaw(e.getYaw() + (float) (amp * 1.1 * (Math.sin(t * 3.7 + 0.4) + 0.4 * Math.sin(t * 8.9))));
        e.setRoll(e.getRoll() + (float) (amp * 1.3 * Math.sin(t * 5.1 + 2.0)));
    }
}
