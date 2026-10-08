package com.Spartan.check.block;

import com.Spartan.check.BlockCheck;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Détection de speedmine / fast-break, côté serveur.
 *
 * Le serveur sait quand le joueur commence à casser un bloc (clic gauche) et
 * quand le bloc casse. Il sait aussi combien de ticks ce bloc DOIT prendre pour
 * ce joueur précis : getDestroyProgress tient compte de l'outil, des
 * enchantements, de Célérité / Fatigue, de l'eau et du fait d'être en l'air.
 *
 * - Comptage identique à vanilla (ServerPlayerGameMode : progression × (ticks + 1)).
 * - Durée mesurée en ticks de jeu ET en temps réel, on garde la plus grande :
 *   si le serveur rame (moins de 20 TPS), le joueur honnête mine en temps réel
 *   et ne doit pas paraître trop rapide.
 * - Blocs de moins de 10 ticks ignorés : la gigue réseau y pèse trop.
 * - Signalé seulement après plusieurs blocs suspects d'affilée.
 *
 * Vanilla refuse déjà tout bloc cassé en moins de 70 % du temps ; un humain casse
 * autour de 100 %. Casser régulièrement entre les deux est le signe d'un speedmine.
 */
public class FastBreakCheck extends BlockCheck {
    private static final int MIN_EXPECTED_TICKS = 10;
    private static final int STREAK_TO_FLAG = 3;
    private static final double NANOS_PER_TICK = 50_000_000.0;

    private final Map<UUID, long[]> started = new ConcurrentHashMap<>(); // [posLong, gameTime, nanoTime]
    private final Map<UUID, Integer> streak = new ConcurrentHashMap<>();

    public FastBreakCheck() {
        super("FastBreak");
    }

    @Override
    public void onStartBreaking(ServerPlayer p, BlockPos pos, Level level) {
        started.put(p.getUUID(), new long[] {pos.asLong(), level.getGameTime(), System.nanoTime()});
    }

    @Override
    public void onBreak(ServerPlayer p, BlockState state, BlockPos pos, Level level) {
        if (!SpartanConfig.CHECK_FASTBREAK.get() || p.isCreative()) {
            return;
        }
        long[] s = started.remove(p.getUUID());
        if (s == null || s[0] != pos.asLong()) {
            return;
        }
        float perTick = state.getDestroyProgress(p, level, pos);
        if (perTick <= 0 || perTick >= 1) {
            return; // incassable ou instantané
        }
        int expected = (int) Math.ceil(1.0 / perTick);
        if (expected < MIN_EXPECTED_TICKS) {
            return;
        }
        double gameTicks = level.getGameTime() - s[1];
        double realTicks = (System.nanoTime() - s[2]) / NANOS_PER_TICK;
        double ticks = Math.max(gameTicks, realTicks);
        // Formule vanilla (ServerPlayerGameMode) : progression = vitesse × (ticks + 1).
        double ratio = perTick * (ticks + 1.0);

        if (ratio < SpartanConfig.FASTBREAK_RATIO.get()) {
            int n = streak.merge(p.getUUID(), 1, Integer::sum);
            if (n >= STREAK_TO_FLAG) {
                flag(p, String.format("bloc cassé en %.0f ticks au lieu de %d (%.0f %%), %d fois de suite",
                        ticks, expected, ratio * 100, n));
            }
        } else {
            streak.computeIfPresent(p.getUUID(), (k, v) -> v > 1 ? v - 1 : null);
        }
    }

    @Override
    public void clear(UUID uuid) {
        started.remove(uuid);
        streak.remove(uuid);
    }
}
