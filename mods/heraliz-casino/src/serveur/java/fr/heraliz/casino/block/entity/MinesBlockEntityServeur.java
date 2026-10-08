package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.game.mines.MinesRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.block.MachinesServeur.rnd;

/**
 * Partie SERVEUR de {@link MinesBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new MinesBlockEntity(...) » côté serveur.
 */
public class MinesBlockEntityServeur extends MinesBlockEntity {
   public MinesBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    // ---- serveur
    private int mineMask;
    private long lastAction;

    @Override
    public void handleAction(ServerPlayer p, int action, long[] args) {
        if (!(level instanceof ServerLevel sl)) return;
        long now = now();
        switch (action) {
            case ACTION_START -> {
                if (args.length < 2) return;
                if (!free(now)) {
                    msg(p, Component.translatable("message.heralizcasino.occupied", playerName).withStyle(ChatFormatting.GOLD));
                    return;
                }
                int m = (int) Math.max(1, Math.min(24, args[1]));
                if (!takeBet(p, args[0])) return;
                bet = args[0];
                mines = m;
                mineMask = 0;
                int placed = 0;
                while (placed < m) {
                    int t = rnd(MinesRules.TILES);
                    if ((mineMask & (1 << t)) == 0) {
                        mineMask |= 1 << t;
                        placed++;
                    }
                }
                revealed = 0;
                java.util.Arrays.fill(tileTicks, 0);
                boomTile = -1;
                mineShown = 0;
                mult100 = 100;
                win = 0;
                player = p.getUUID();
                playerName = p.getGameProfile().getName();
                phase = PLAYING;
                startTick = now;
                lastAction = now;
                sync();
            }
            case ACTION_REVEAL -> {
                if (args.length < 1 || phase != PLAYING || !p.getUUID().equals(player)) return;
                int t = (int) args[0];
                if (t < 0 || t >= MinesRules.TILES || (revealed & (1 << t)) != 0) return;
                lastAction = now;
                tileTicks[t] = now;
                if ((mineMask & (1 << t)) != 0) {
                    boomTile = t;
                    finish(sl, BOOM, now);
                    return;
                }
                revealed |= 1 << t;
                mult100 = Math.round(rules().multiplier(mines, revealedCount()) * 100);
                if (revealedCount() >= MinesRules.TILES - mines) {
                    finish(sl, CASHED, now);
                    return;
                }
                sync();
            }
            case ACTION_CASHOUT -> {
                if (phase != PLAYING || !p.getUUID().equals(player) || revealedCount() == 0) return;
                finish(sl, CASHED, now);
            }
            default -> {
            }
        }
    }

    private void finish(ServerLevel sl, int end, long now) {
        phase = end;
        endTick = now;
        mineShown = mineMask;
        win = end == CASHED ? bet * mult100 / 100 : 0;
        sync();
        payout(sl, player, playerName, win, bet);
    }

    @Override
    protected void tickServer(ServerLevel level) {
        if (phase == PLAYING && now() - lastAction > rules().idleSeconds * 20L) {
            if (revealedCount() > 0) {
                finish(level, CASHED, now());
            } else {
                // partie jamais commencee : mise rendue
                phase = IDLE;
                refund(level, player, bet);
                sync();
            }
        }
    }

    @Override
    public void onRemoved(ServerLevel level) {
        if (phase == PLAYING) {
            if (revealedCount() > 0) finish(level, CASHED, now());
            else refund(level, player, bet);
        }
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        tag.putInt("MineMask", mineMask);
        tag.putLong("LastAction", lastAction);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        mineMask = tag.getInt("MineMask");
        lastAction = tag.getLong("LastAction");
    }
}
