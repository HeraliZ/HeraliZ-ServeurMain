package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.game.plinko.PlinkoGame;
import fr.heraliz.casino.game.plinko.PlinkoRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import static fr.heraliz.casino.block.MachinesServeur.RNG;

/**
 * Partie SERVEUR de {@link PlinkoBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new PlinkoBlockEntity(...) » côté serveur.
 */
public class PlinkoBlockEntityServeur extends PlinkoBlockEntity {
   public PlinkoBlockEntityServeur(BlockPos pos, BlockState state) {
      super(pos, state);
   }


    @Override
    public void handleAction(ServerPlayer player, int action, long[] args) {
        if (action != ACTION_DROP || args.length < 2 || !(level instanceof ServerLevel sl)) return;
        PlinkoRules r = rules();
        long bet = args[0];
        int count = (int) Math.max(1, Math.min(10, args[1]));
        long min = minBet(), max = maxBet();
        if (bet < min || bet > max) {
            msg(player, Component.translatable("message.heralizcasino.bet_limits", Chips.format(min), Chips.format(max)).withStyle(ChatFormatting.RED));
            return;
        }
        int mine = 0;
        long now = now();
        for (Ball b : balls) if (b.player.equals(player.getUUID()) && !b.paid) mine++;
        count = Math.min(count, Math.min(r.maxBallsPerPlayer - mine, MAX_BALLS - balls.size()));
        if (count <= 0) {
            msg(player, Component.translatable("message.heralizcasino.plinko_full").withStyle(ChatFormatting.GOLD));
            return;
        }
        long total = bet * count;
        if (!ChipsServeur.take(player, total)) {
            msg(player, Component.translatable("message.heralizcasino.not_enough", Chips.format(total), Chips.format(Chips.count(player)))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        CasinoStats.get(sl.getServer()).wager(type, player.getUUID(), total);
        // les billes partent l'une apres l'autre depuis l'entonnoir (file d'attente commune)
        long next = now;
        for (Ball b : balls) next = Math.max(next, b.start + STAGGER);
        for (int i = 0; i < count; i++) {
            Ball b = new Ball();
            b.player = player.getUUID();
            b.name = player.getGameProfile().getName();
            b.bet = bet;
            b.path = RNG.nextInt(1 << PlinkoGame.ROWS);
            b.start = next + (long) i * STAGGER;
            b.multiplier = r.multipliers[PlinkoGame.bucket(b.path)];
            b.paid = false;
            balls.add(b);
        }
        multipliers = r.multipliers.clone();
        sync();
    }

    @Override
    protected void tickServer(ServerLevel level) {
        long now = now();
        boolean changed = false;
        for (Ball b : balls) {
            if (!b.paid && now >= b.start + PlinkoGame.DURATION - PlinkoGame.LAND / 2) {
                b.paid = true;
                payout(level, b.player, b.name, (long) Math.floor(b.bet * b.multiplier), b.bet);
                setChanged();
            }
        }
        if (balls.removeIf(b -> b.paid && now > b.start + PlinkoGame.DURATION + 40)) changed = true;
        if (changed) sync();
    }

    @Override
    public void onRemoved(ServerLevel level) {
        for (Ball b : balls) {
            if (!b.paid) {
                b.paid = true;
                payout(level, b.player, b.name, (long) Math.floor(b.bet * b.multiplier), b.bet);
            }
        }
        balls.clear();
    }

    @Override
    protected void writeServer(CompoundTag tag) {
        ListTag l = new ListTag();
        for (Ball b : balls) l.add(b.save(true));
        tag.put("BallsFull", l);
    }

    @Override
    protected void readServer(CompoundTag tag) {
        if (!tag.contains("BallsFull")) return;
        balls.clear();
        ListTag l = tag.getList("BallsFull", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                balls.add(Ball.load(l.getCompound(i)));
            } catch (Exception ignored) {
            }
        }
    }
}
