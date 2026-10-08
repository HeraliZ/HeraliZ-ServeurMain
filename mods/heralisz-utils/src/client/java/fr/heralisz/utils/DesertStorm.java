package fr.heralisz.utils;

import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Désert de la map (biome heralisz:desert) : brume orange-brun permanente, et régulièrement une tempête de sable
 * (brouillard très épais, poussière qui vole avec le vent). Le cycle suit l'heure du monde (synchronisée), donc tous les
 * joueurs voient la même tempête au même moment ; /time set 11500 en déclenche une (essais). Rien côté serveur : c'est une ambiance (l'armure anti-tempête viendra s'y brancher).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class DesertStorm {
    private static final ResourceLocation DESERT = new ResourceLocation("heralisz", "desert");
    private static final ResourceLocation CLIFFS = new ResourceLocation("heralisz", "falaises_rouges");
    private static final int CYCLE = 20 * 60 * 14;          // une tempête toutes les 14 minutes
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.80f, 0.58f, 0.36f), 1.6f);
    private static final DustParticleOptions DUST_DARK = new DustParticleOptions(new Vector3f(0.62f, 0.42f, 0.25f), 1.2f);
    private static float haze;                               // 0 hors du désert, 1 au cœur du désert
    private static float storm;                              // force de la tempête en cours (0..1)

    private DesertStorm() {}

    /** Force de la tempête à ce moment du cycle : calme, montée en 1 min, 3 min de tempête, retombée. */
    public static float stormAt(long dayTime) {
        float t = (Math.floorMod(dayTime, CYCLE)) / (float) CYCLE;
        float up = Mth.clamp((t - 0.62f) / 0.07f, 0, 1);
        float down = Mth.clamp((0.98f - t) / 0.07f, 0, 1);
        return Math.min(up, down);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.player == null || mc.isPaused()) return;
        BlockPos p = mc.player.blockPosition();
        var biome = mc.level.getBiome(p).unwrapKey().map(k -> k.location()).orElse(null);
        float target = DESERT.equals(biome) ? 1f : CLIFFS.equals(biome) ? 0.5f : 0f;
        // sous la roche (cité du Mont Noir, grottes, bunkers) : pas de tempête ni de brume de sable
        if (mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) > p.getY() + 4) {
            target = 0f;
        }
        haze += (target - haze) * 0.015f;
        storm = stormAt(mc.level.getDayTime());
        float k = haze * storm;
        if (k < 0.05f) return;
        RandomSource r = mc.level.random;
        int n = (int) (k * 14);
        for (int i = 0; i < n; i++) {
            double x = mc.player.getX() + (r.nextDouble() - 0.5) * 36;
            double y = mc.player.getY() + r.nextDouble() * 8 - 1;
            double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 36;
            mc.level.addParticle(r.nextFloat() < 0.7f ? DUST : DUST_DARK, x, y, z, 0.6 * k, -0.01, 0.2 * k);
        }
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        if (haze < 0.01f || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = e.getFarPlaneDistance();
        float want = Mth.lerp(storm, 140f, 26f);             // brume légère, ou tempête (on ne voit plus à 25 blocs)
        float f = Mth.lerp(haze, far, Math.min(far, want));
        e.setFarPlaneDistance(f);
        e.setNearPlaneDistance(Mth.lerp(haze, e.getNearPlaneDistance(), f * (0.05f + 0.25f * (1 - storm))));
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor e) {
        if (haze < 0.01f) return;
        float k = haze * (0.55f + 0.45f * storm);
        float j = 1f - 0.86f * fr.heralisz.utils.ambiance.Ambiance.nuit;   // la nuit, la poussière est sombre
        e.setRed(Mth.lerp(k, e.getRed(), 0.74f * j));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.52f * j));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.31f * j));
    }
}
