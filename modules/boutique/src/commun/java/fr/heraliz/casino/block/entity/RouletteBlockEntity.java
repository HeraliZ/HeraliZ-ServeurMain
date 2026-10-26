package fr.heraliz.casino.block.entity;

import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.game.roulette.RouletteRules;
import fr.heraliz.casino.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Roulette : tout le monde mise sur le tapis pendant le compte a rebours, puis la bille est lancee.
 * Le cylindre tourne sans a-coup : au repos, angle = restAngle + 0.6 degre par tick depuis restTick ; pendant un
 * tour, angle = Roulette.wheelAngle(t, spinTicks, wheelStart).
 */
public class RouletteBlockEntity extends RoundBlockEntity {
    /** Vitesse de rotation au repos (degres / tick). */
    public static final float IDLE_SPEED = 0.6f;

    // ---- synchronise
    public int zeros = 2;
    public int number = -1;
    public int spinTicks = 220;
    public float wheelStart, ballStart;
    public float restAngle;
    public long restTick;
    public int[] history = new int[0];

    public RouletteBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.ROULETTE, pos, state);
        restAngle = Math.floorMod(pos.hashCode(), 360);
        wheelStart = restAngle;
    }

    RouletteRules rules() {
        return CasinoConfig.get().roulette;
    }

    @Override
    protected int resultTicks() {
        return 120;
    }

    @Override
    protected void writeSync(CompoundTag tag) {
        super.writeSync(tag);
        tag.putInt("Zeros", zeros);
        tag.putInt("Number", number);
        tag.putInt("SpinTicks", spinTicks);
        tag.putFloat("WheelStart", wheelStart);
        tag.putFloat("BallStart", ballStart);
        tag.putFloat("RestAngle", restAngle);
        tag.putLong("RestTick", restTick);
        tag.putIntArray("History", history);
    }

    @Override
    protected void readSync(CompoundTag tag) {
        super.readSync(tag);
        zeros = tag.contains("Zeros") ? tag.getInt("Zeros") : 2;
        number = tag.contains("Number") ? tag.getInt("Number") : -1;
        spinTicks = Math.max(40, tag.getInt("SpinTicks"));
        wheelStart = tag.getFloat("WheelStart");
        ballStart = tag.getFloat("BallStart");
        restAngle = tag.getFloat("RestAngle");
        restTick = tag.getLong("RestTick");
        history = tag.getIntArray("History");
    }
}
