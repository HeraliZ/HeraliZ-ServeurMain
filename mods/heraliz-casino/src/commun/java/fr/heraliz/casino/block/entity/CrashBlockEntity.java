package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.crash.CrashRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * La Fusee (Crash) : le multiplicateur grimpe, chacun encaisse quand il veut ; a l'explosion, les mises non
 * encaissees sont perdues. Le point d'explosion reste secret (serveur) jusqu'a l'explosion.
 * Mise : case = encaissement automatique x100 (0 = aucun) ; extra = multiplicateur encaisse x100.
 */
public class CrashBlockEntity extends RoundBlockEntity {
    public static final int ACTION_CASHOUT = 2;
    /** Affichage de l'explosion avant le reglement. */
    public static final int BOOM_TICKS = 60;

    // ---- synchronise
    public boolean crashed;
    public long crashTick;
    /** Point d'explosion x100 (connu des clients seulement apres l'explosion). */
    public long crashShown;
    public double growth = 0.075;
    public long[] history = new long[0];

    public CrashBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.CRASH, pos, state);
    }

    CrashRules rules() {
        return CasinoConfig.get().crash;
    }

    @Override
    protected int resultTicks() {
        return 40;
    }

    /** Mise encore en jeu (non encaissee) du joueur. */
    public long inPlay(UUID player) {
        long t = 0;
        for (Bet b : bets) if (b.player.equals(player) && b.extra == 0) t += b.amount;
        return t;
    }

    /** Multiplicateur encaisse par le joueur (x100), 0 sinon. */
    public long cashedOut(UUID player) {
        for (Bet b : bets) if (b.player.equals(player) && b.extra != 0) return b.extra;
        return 0;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        super.writeSync(tag);
        tag.putBoolean("Crashed", crashed);
        tag.putLong("CrashTick", crashTick);
        tag.putLong("CrashShown", crashShown);
        tag.putDouble("Growth", growth);
        tag.putLongArray("History", history);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        super.readSync(tag);
        crashed = tag.getBoolean("Crashed");
        crashTick = tag.getLong("CrashTick");
        crashShown = tag.getLong("CrashShown");
        growth = tag.contains("Growth") ? tag.getDouble("Growth") : 0.075;
        history = tag.getLongArray("History");
    }
}
