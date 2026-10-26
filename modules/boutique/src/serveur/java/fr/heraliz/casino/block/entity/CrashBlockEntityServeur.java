package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.crash.CrashGame;
import fr.heraliz.casino.game.crash.CrashRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static fr.heraliz.casino.block.MachinesServeur.RNG;

/**
 * Partie SERVEUR de {@link CrashBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new CrashBlockEntity(...) » côté serveur.
 */
public class CrashBlockEntityServeur extends CrashBlockEntity {
   public CrashBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    // ---- serveur (secret)
    private double crashPoint = 1.0;

    @Override
    protected int bettingTicks() {
        return rules().bettingSeconds * 20;
    }

    @Override
    protected boolean validSpot(int spot) {
        return spot == 0 || (spot >= 101 && spot <= 10_000_000);
    }

    @Override
    protected void onStart(ServerLevel level) {
        CrashRules r = rules();
        growth = r.growth;
        crashPoint = CrashGame.crashPoint(r, RNG);
        crashed = false;
        crashShown = 0;
        if (crashPoint <= 1.0) boom(now());
    }

    @Override
    protected boolean runningDone(long now) {
        return crashed && now >= crashTick + BOOM_TICKS;
    }

    @Override
    protected long payoutFor(Bet bet) {
        return 0; // les encaissements sont payes sur le moment
    }

    @Override
    protected void handleOther(ServerPlayer player, int action, long[] args) {
        if (action == ACTION_CASHOUT) cashOut(player);
    }

    private void cashOut(ServerPlayer player) {
        if (phase != RUNNING || crashed || !(level instanceof ServerLevel sl)) return;
        long m = CrashGame.multiplier100(growth, now() - phaseStart);
        if (m >= Math.round(crashPoint * 100)) return;
        boolean any = false;
        for (Bet b : bets) {
            if (b.player.equals(player.getUUID()) && b.extra == 0) {
                cash(sl, b, m);
                any = true;
            }
        }
        if (any) sync();
    }

    private void cash(ServerLevel level, Bet b, long m100) {
        b.extra = m100;
        payout(level, b.player, b.name, b.amount * m100 / 100, b.amount);
    }

    @Override
    public void tickServer(ServerLevel level) {
        if (phase == RUNNING && !crashed) {
            long m = CrashGame.multiplier100(growth, now() - phaseStart);
            long cp = Math.round(crashPoint * 100);
            boolean changed = false;
            for (Bet b : bets) {
                if (b.extra == 0 && b.spot > 0 && m >= b.spot && b.spot < cp) {
                    cash(level, b, b.spot);
                    changed = true;
                }
            }
            if (m >= cp) {
                boom(now());
                changed = true;
            }
            if (changed) sync();
        }
        super.tickServer(level);
    }

    private void boom(long now) {
        crashed = true;
        crashTick = now;
        crashShown = Math.round(crashPoint * 100);
        long[] h = new long[Math.min(12, history.length + 1)];
        h[0] = crashShown;
        System.arraycopy(history, 0, h, 1, h.length - 1);
        history = h;
    }

    /** Seules les mises non encaissees sont perdues (message) ; les autres ont deja ete payees. */
    @Override
    protected void settle(ServerLevel level) {
        Set<UUID> told = new HashSet<>();
        for (Bet b : bets) {
            if (b.extra != 0 || !told.add(b.player)) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(b.player);
            if (p != null) {
                msg(p, Component.translatable("message.heralizcasino.crash_lost", String.format(Locale.ROOT, "%.2f", crashShown / 100.0))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @Override
    public void onRemoved(ServerLevel level) {
        if (phase == RUNNING && !crashed) {
            // machine cassee en plein vol : les mises encore en jeu sont remboursees
            for (Bet b : bets) if (b.extra == 0) refund(level, b.player, b.amount);
            bets.clear();
            phase = IDLE;
            return;
        }
        super.onRemoved(level);
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        tag.putDouble("CrashPoint", crashPoint);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        if (tag.contains("CrashPoint")) crashPoint = tag.getDouble("CrashPoint");
    }
}
