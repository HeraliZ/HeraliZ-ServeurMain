package fr.heraliz.casino.block;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.data.CasinoStats;
import fr.heraliz.casino.data.PendingPayouts;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.WinPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Partie serveur commune a toutes les machines (docs/METHODE_CLIENT_SERVEUR.md) : generateur des tirages, tick du
 * serveur (lumiere reglee par les operateurs), mises, remboursements, gains, places des joueurs. Corps d'origine de
 * MachineBlockEntity, tels quels (« level » -> be.getLevel(), « worldPosition » -> be.getBlockPos()).
 */
public final class MachinesServeur {
    private MachinesServeur() {
    }

    /** Generateur des tirages (imprevisible). */
    public static final SecureRandom RNG = new SecureRandom();

    public static int rnd(int bound) {
        return RNG.nextInt(bound);
    }

    public static double rndDouble() {
        return RNG.nextDouble();
    }

    /** Branche la logique serveur des machines (PartieServeur.init). */
    public static void installer() {
        MachineBlockEntity.moteur = new MachineBlockEntity.Moteur() {
            @Override
            public void serverTick(ServerLevel level, BlockPos pos, BlockState state, MachineBlockEntity be) {
                MachinesServeur.serverTick(level, pos, state, be);
            }

            @Override
            public boolean takeBet(MachineBlockEntity be, ServerPlayer p, long amount) {
                return MachinesServeur.takeBet(be, p, amount);
            }

            @Override
            public void refund(MachineBlockEntity be, ServerLevel level, UUID player, long amount) {
                MachinesServeur.refund(be, level, player, amount);
            }

            @Override
            public void payout(MachineBlockEntity be, ServerLevel level, UUID player, String name, long amount, long wager) {
                MachinesServeur.payout(be, level, player, name, amount, wager);
            }

            @Override
            public boolean seat(MachineBlockEntity be, ServerPlayer p, MachineBlockEntity.Spot s) {
                return MachinesServeur.seat(be, p, s);
            }

            @Override
            public void unseat(MachineBlockEntity be, @Nullable UUID id) {
                MachinesServeur.unseat(be, id);
            }
        };
    }

    // =================================================================== cycle de vie
    static void serverTick(ServerLevel sl, BlockPos pos, BlockState state, MachineBlockEntity be) {
        // lumiere reglee par les operateurs : appliquee aux machines posees (une verification par seconde)
        if ((sl.getGameTime() + (pos.hashCode() & 15)) % 20 == 0 && state.getBlock() instanceof MachineBlock mb) {
            BlockState lit = mb.withConfiguredLight(state);
            if (lit != state) sl.setBlock(pos, lit, 3);
        }
        be.tickServer(sl);
    }

    // =================================================================== mises et gains
    /**
     * Prend une mise : limites de la machine, jetons suffisants. Envoie le message d'erreur au joueur.
     * @return true si la mise est encaissee
     */
    public static boolean takeBet(MachineBlockEntity be, ServerPlayer p, long amount) {
        long min = be.minBet(), max = be.maxBet();
        if (amount < min || amount > max) {
            MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.bet_limits", Chips.format(min), Chips.format(max)).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!ChipsServeur.take(p, amount)) {
            MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.not_enough", Chips.format(amount), Chips.format(Chips.count(p)))
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (be.getLevel() instanceof ServerLevel sl) CasinoStats.get(sl.getServer()).wager(be.type, p.getUUID(), amount);
        return true;
    }

    /** Rembourse une mise (partie annulee) : ne compte pas comme un gain. */
    public static void refund(MachineBlockEntity be, ServerLevel level, UUID player, long amount) {
        if (amount <= 0) return;
        CasinoStats.get(level.getServer()).cancel(be.type, player, amount);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        if (p != null) {
            ChipsServeur.give(p, amount);
            MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.refund", Chips.format(amount)).withStyle(ChatFormatting.YELLOW));
        } else {
            PendingPayouts.get(level.getServer()).add(player, amount);
        }
    }

    /**
     * Verse un gain (en jetons ; au prochain login si le joueur est deconnecte) avec messages, bandeau et annonce.
     * @param wager mise correspondante (pour le multiplicateur affiche)
     */
    public static void payout(MachineBlockEntity be, ServerLevel level, UUID player, String name, long amount, long wager) {
        CasinoStats.get(level.getServer()).paid(be.type, player, amount);
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
        float mult = wager > 0 ? (float) amount / wager : 0f;
        boolean quiet = be.quietResults();
        if (amount > 0) {
            if (p != null) {
                ChipsServeur.give(p, amount);
                if (quiet) {
                    // l'ecran de la machine a deja tout montre (ni bandeau de gain, ni message)
                } else if (amount > wager) {
                    CasinoNet.sendTo(p, new WinPacket(amount, mult, be.type.ordinal()));
                    MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.win", Chips.format(amount), MachineBlockEntity.mult(mult)).withStyle(ChatFormatting.GOLD));
                } else if (amount == wager) {
                    MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.push", Chips.format(amount)).withStyle(ChatFormatting.YELLOW));
                } else {
                    MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.partial", Chips.format(amount), MachineBlockEntity.mult(mult), Chips.format(wager))
                            .withStyle(ChatFormatting.GRAY));
                }
            } else {
                PendingPayouts.get(level.getServer()).add(player, amount);
            }
            CasinoConfig.Settings cfg = CasinoConfig.get();
            if (amount - wager >= cfg.general.announceMinProfit && mult >= cfg.general.announceMinMultiplier) {
                Component c = Component.translatable("message.heralizcasino.announce", name, Chips.format(amount), be.type.displayName(), MachineBlockEntity.mult(mult))
                        .withStyle(ChatFormatting.GOLD);
                level.getServer().getPlayerList().broadcastSystemMessage(c, false);
            }
        } else if (p != null && !quiet) {
            MachineBlockEntity.msg(p, Component.translatable("message.heralizcasino.lose", Chips.format(wager)).withStyle(ChatFormatting.GRAY));
        }
    }

    // =================================================================== places des joueurs
    /**
     * Installe le joueur a une place de la machine (chaise, tabouret, place debout).
     * @return false si le joueur est trop loin ou deja pris ailleurs
     */
    public static boolean seat(MachineBlockEntity be, ServerPlayer p, MachineBlockEntity.Spot s) {
        BlockPos worldPosition = be.getBlockPos();
        if (!(be.getLevel() instanceof ServerLevel sl) || !be.isNear(p) || p.isSleeping()) return false;
        if (p.getVehicle() instanceof fr.heraliz.casino.entity.SeatEntity cur && worldPosition.equals(cur.machine())) return true;
        if (p.isPassenger()) p.stopRiding();
        net.minecraft.core.Direction facing = be.getBlockState().getValue(MachineBlock.FACING);
        net.minecraft.world.phys.Vec3 at = fr.heraliz.casino.machine.Footprint.toWorld(worldPosition, facing, s.x(), s.y(), s.z());
        net.minecraft.world.phys.Vec3 exit = fr.heraliz.casino.machine.Footprint.toWorld(worldPosition, facing, s.exitX(), s.y(), s.exitZ());
        float yaw = fr.heraliz.casino.machine.Footprint.toWorldYaw(facing, s.lookX(), s.lookZ());
        fr.heraliz.casino.entity.SeatEntity seat = fr.heraliz.casino.entity.SeatEntity.create(sl, worldPosition, at, exit, yaw, s.range(), s.feet(), s.sit());
        if (!p.startRiding(seat, true)) {
            seat.discard();
            return false;
        }
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        return true;
    }

    /** Le joueur quitte sa place sur cette machine (s'il en a une). */
    public static void unseat(MachineBlockEntity be, @Nullable UUID id) {
        ServerPlayer p = be.player(id);
        if (p != null && p.getVehicle() instanceof fr.heraliz.casino.entity.SeatEntity s && be.getBlockPos().equals(s.machine())) {
            MachineBlockEntity.allowDismount = true;
            try {
                p.stopRiding();
            } finally {
                MachineBlockEntity.allowDismount = false;
            }
        }
    }
}
