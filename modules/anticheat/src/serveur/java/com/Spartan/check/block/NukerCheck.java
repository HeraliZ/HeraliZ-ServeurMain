package com.Spartan.check.block;

import com.Spartan.check.BlockCheck;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Détection de nuker (blocs cassés en masse), côté serveur.
 *
 * Un humain casse au plus un bloc par tick, même en minage instantané. Plusieurs
 * blocs cassés par le même joueur dans le même tick de jeu sont impossibles
 * sans triche.
 *
 * On ne compte QUE les blocs que le joueur a lui-même commencé à casser (clic) :
 * les mods de minage en chaîne (FTB Ultimine, abattage d'arbre…) cassent des
 * dizaines de blocs d'un coup pour le joueur, mais il n'a cliqué que le premier.
 * Un nuker, lui, doit « cliquer » chaque bloc.
 */
public class NukerCheck extends BlockCheck {
    private static final long CLICK_MEMORY_TICKS = 40;
    private static final long REPEAT_WINDOW_TICKS = 40;

    private final Map<UUID, long[]> perTick = new ConcurrentHashMap<>(); // [gameTime, count]
    private final Map<UUID, Map<Long, Long>> clicked = new ConcurrentHashMap<>(); // pos -> gameTime
    private final Map<UUID, Long> lastExceed = new ConcurrentHashMap<>();         // gameTime du dernier dépassement

    public NukerCheck() {
        super("Nuker");
    }

    @Override
    public void onStartBreaking(ServerPlayer p, BlockPos pos, Level level) {
        long now = level.getGameTime();
        Map<Long, Long> m = clicked.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>());
        m.values().removeIf(t -> now - t > CLICK_MEMORY_TICKS);
        m.put(pos.asLong(), now);
    }

    @Override
    public void onBreak(ServerPlayer p, BlockState state, BlockPos pos, Level level) {
        if (!SpartanConfig.CHECK_NUKER.get() || p.isCreative()) {
            return;
        }
        if (CheckManager.serverLagging(p)) {
            return; // serveur qui rame : plusieurs ticks client traités d'un coup
        }
        Map<Long, Long> m = clicked.get(p.getUUID());
        if (m == null || m.remove(pos.asLong()) == null) {
            return; // bloc cassé par un mod (minage en chaîne), pas par un clic du joueur
        }
        long gameTime = level.getGameTime();
        long[] s = perTick.computeIfAbsent(p.getUUID(), k -> new long[] {gameTime, 0});
        if (s[0] != gameTime) {
            s[0] = gameTime;
            s[1] = 0;
        }
        s[1]++;

        int max = SpartanConfig.NUKER_MAX_PER_TICK.get();
        if (s[1] > max + 1) {
            CheckManager.markViolation(); // au-delà du seuil : blocs annulés sans re-alerter
        } else if (s[1] == max + 1) {
            // Un dépassement isolé peut venir d'un à-coup réseau : on ne signale que s'il se répète.
            Long prev = lastExceed.put(p.getUUID(), gameTime);
            if (prev != null && gameTime - prev <= REPEAT_WINDOW_TICKS) {
                flag(p, String.format("%d blocs cliqués cassés dans le même tick, à répétition (max autorisé : %d)", s[1], max));
            } else {
                CheckManager.markViolation();
            }
        }
    }

    @Override
    public void clear(UUID uuid) {
        perTick.remove(uuid);
        clicked.remove(uuid);
        lastExceed.remove(uuid);
    }
}
