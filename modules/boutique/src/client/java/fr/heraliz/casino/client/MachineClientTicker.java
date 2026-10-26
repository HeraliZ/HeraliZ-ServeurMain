package fr.heraliz.casino.client;

import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.RoundBlockEntity;
import fr.heraliz.casino.client.render.MachineRenderer;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.particle.SparkOptions;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Effets cote client a chaque tick : particules et sons decrits par le modele de la machine,
 * convertis du repere du modele vers le monde. Les ticks manques (petit lag) sont rattrapes.
 */
public final class MachineClientTicker {
    private MachineClientTicker() {
    }

    private static final Random RANDOM = new Random();
    private static final WorldFx FX = new WorldFx();

    public static void tick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        if (!fr.heraliz.casino.client.render.RenderProfile.on) {
            run(level, pos, state, be);
            return;
        }
        long t0 = System.nanoTime();
        run(level, pos, state, be);
        fr.heraliz.casino.client.render.RenderProfile.fxNanos += System.nanoTime() - t0;
        fr.heraliz.casino.client.render.RenderProfile.fxCalls++;
    }

    private static void run(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        if (!(state.getBlock() instanceof MachineBlock)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        long gt = level.getGameTime();
        if (d2 > 40 * 40) {
            be.fxLastTick = gt;
            return;
        }
        // depart d'un tour ou le joueur a mise : la camera part sur la machine
        if (be instanceof RoundBlockEntity r && r.phase == RoundBlockEntity.RUNNING && r.phaseStart != be.clientWatched
                && mc.screen == null && r.totalOf(mc.player.getUUID()) > 0) {
            be.clientWatched = r.phaseStart;
            ClientHooks.watch(be);
        }
        if (be instanceof fr.heraliz.casino.block.entity.RpsBlockEntity rps) fr.heraliz.casino.client.render.DuelPoses.track(rps);
        if (be instanceof fr.heraliz.casino.block.entity.DuelBlockEntity du && du.phase == fr.heraliz.casino.block.entity.DuelBlockEntity.PLAYING
                && du.phaseStart != be.clientWatched && mc.screen == null && du.sideOf(mc.player.getUUID()) != 0) {
            be.clientWatched = du.phaseStart;
            ClientHooks.watch(be);
        }
        MachineDesign<?> d = Designs.get(be.type.id);
        if (!d.isBuilt()) return;
        float yRot = MachineRenderer.yRot(state);
        FX.set(level, pos, yRot, (float) (double) ClientConfig.PARTICLES.get(), (float) (double) ClientConfig.MACHINE_VOLUME.get());
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long from = be.fxLastTick == Long.MIN_VALUE ? gt : Math.max(be.fxLastTick + 1, gt - 3);
        for (long k = from; k <= gt; k++) {
            Scene s = MachineRenderer.scene(be, level, 0f, yRot, cam);
            s.set(k, 0f);
            Object view = Views.view(be, s);
            d.tickFxRaw(s, view, FX, RANDOM);
        }
        be.fxLastTick = gt;
    }

    /** Emission dans le monde : repere du modele (px) -> monde, orientation de la machine, densite des reglages. */
    static final class WorldFx implements FxSink {
        private Level level;
        private BlockPos pos;
        private float yRot, density, volume, cos, sin;

        void set(Level level, BlockPos pos, float yRot, float density, float volume) {
            this.level = level;
            this.pos = pos;
            this.yRot = yRot;
            this.density = density;
            this.volume = volume;
            double r = Math.toRadians(yRot);
            this.cos = (float) Math.cos(r);
            this.sin = (float) Math.sin(r);
        }

        @Override
        public void particle(int kind, float x, float y, float z, float vx, float vy, float vz, int rgb, float size, int life) {
            if (density <= 0 || (density < 1 && RANDOM.nextFloat() > density)) return;
            float[] l = MachineRenderer.toLocal(x, y, z, yRot);
            float wx = (vx * cos + vz * sin) / 16f, wz = (-vx * sin + vz * cos) / 16f;
            level.addParticle(SparkOptions.of(rgb, size / 16f, kind, life), pos.getX() + l[0], pos.getY() + l[1], pos.getZ() + l[2],
                    wx, vy / 16f, wz);
        }

        @Override
        public void sound(Snd sound, float x, float y, float z, float vol, float pitch) {
            if (volume <= 0) return;
            float[] l = MachineRenderer.toLocal(x, y, z, yRot);
            level.playLocalSound(pos.getX() + l[0], pos.getY() + l[1], pos.getZ() + l[2], ModRegistry.sound(sound), SoundSource.BLOCKS,
                    vol * volume, pitch, false);
        }
    }
}
