package fr.heraliz.crates.client;

import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CrateBlockEntity;
import fr.heraliz.crates.client.render.CrateRenderer;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.client.render.scene.FxSink;
import fr.heraliz.crates.registry.Snd;
import fr.heraliz.crates.config.ClientConfig;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.particle.SparkOptions;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Effets cote client a chaque tick pendant une ouverture : particules et sons de la choregraphie decrite par le design,
 * convertis du repere du modele vers le monde. Une caisse au repos ne fait rien.
 */
public final class CrateClientTicker {
    private CrateClientTicker() {
    }

    private static final Random RANDOM = new Random();
    private static final WorldFx FX = new WorldFx();

    public static void tick(Level level, BlockPos pos, BlockState state, CrateBlockEntity be) {
        // au repos, une caisse ne fait strictement rien (ni particules, ni sons, ni calcul)
        // animations dans le monde coupees (CrateRenderer.ANIMATIONS_MONDE) : ni particules, ni sons, ni secousse
        if (!CrateRenderer.ANIMATIONS_MONDE || !be.isActive() || !(state.getBlock() instanceof CrateBlock)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        boolean near = d2 < 48 * 48;
        long gt = level.getGameTime();
        Design d = Designs.get(be.getCrateType().id);
        FX.set(level, pos, CrateRenderer.yRot(state), (float) (double) ClientConfig.PARTICLES.get() * density(be, mc, d2));

        CrateState s = new CrateState();
        s.time = gt % CrateRenderer.CLOCK_PERIOD;
        s.clock = s.time;
        s.seed = 0;
        {
            long t = gt - be.getStartTick();
            if (be.clientSeenStart != be.getStartTick()) {
                be.clientSeenStart = be.getStartTick();
                be.clientLastTick = t <= 4 ? -1 : t - 1;
            }
            s.active = true;
            s.t = Math.max(0, t);
            s.ringSize = be.getRing().size();
            s.winIndex = be.getWinIndex();
            LootRarity r = be.getWinRarity();
            s.rarityRgb = r.color;
            s.rarityTier = r.soundTier;
            java.util.List<LootRarity> rr = be.getRingRarity();
            s.ringRgb = new int[rr.size()];
            s.ringRank = new int[rr.size()];
            for (int i = 0; i < rr.size(); i++) {
                s.ringRgb[i] = rr.get(i).color;
                s.ringRank[i] = rr.get(i).ordinal();
            }
            if (near && d.isBuilt()) {
                long from = Math.max(be.clientLastTick + 1, t - 3);
                for (long k = Math.max(0, from); k <= t; k++) {
                    CrateState c = s.copy();
                    c.t = k;
                    c.time = k;
                    d.seqFx(c, (int) k, FX, RANDOM);
                }
            }
            be.clientLastTick = t;
            if (d.isBuilt()) CameraShake.report(pos, d.shake(s), gt);
        }
    }

    /**
     * Densite des particules selon la distance : pleine pour le joueur qui ouvre et jusqu'a 8 blocs, puis de moins en
     * moins (plusieurs caisses qui s'ouvrent en meme temps au spawn ne noient pas l'ecran ni la carte graphique).
     */
    private static float density(CrateBlockEntity be, Minecraft mc, double d2) {
        if (mc.player.getUUID().equals(be.getOpener())) return 1f;
        double d = Math.sqrt(d2);
        if (d <= 8) return 1f;
        if (d >= 24) return 0.15f;
        return (float) (1 - 0.85 * (d - 8) / 16);
    }

    /** Emission dans le monde : repere du modele (px) -> monde, orientation de la caisse, densite des reglages. */
    private static final class WorldFx implements FxSink {
        private Level level;
        private BlockPos pos;
        private float yRot, density, cos, sin;

        void set(Level level, BlockPos pos, float yRot, float density) {
            this.level = level;
            this.pos = pos;
            this.yRot = yRot;
            this.density = density;
            double r = Math.toRadians(yRot);
            this.cos = (float) Math.cos(r);
            this.sin = (float) Math.sin(r);
        }

        @Override
        public void particle(int kind, float x, float y, float z, float vx, float vy, float vz, int rgb, float size, int life) {
            if (density <= 0 || (density < 1 && RANDOM.nextFloat() > density)) return;
            float[] l = CrateRenderer.toLocal(x, y, z, yRot);
            float wx = (vx * cos + vz * sin) / 16f, wz = (-vx * sin + vz * cos) / 16f;
            level.addParticle(SparkOptions.of(rgb, size / 16f, kind, life), pos.getX() + l[0], pos.getY() + l[1], pos.getZ() + l[2],
                    wx, vy / 16f, wz);
        }

        @Override
        public void sound(Snd sound, float x, float y, float z, float volume, float pitch) {
            float[] l = CrateRenderer.toLocal(x, y, z, yRot);
            level.playLocalSound(pos.getX() + l[0], pos.getY() + l[1], pos.getZ() + l[2], ModRegistry.sound(sound), SoundSource.BLOCKS,
                    volume, pitch, false);
        }
    }
}
