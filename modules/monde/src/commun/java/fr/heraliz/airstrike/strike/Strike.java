package fr.heraliz.airstrike.strike;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A planned air strike: flight path, released objects and timings. Built on the server by {@link StrikePlanner} and
 * sent as-is to the clients which replay it visually.
 */
public final class Strike {
    public final int id;
    public final StrikeType type;
    /** Level game time at which the flight path starts (t = 0). */
    public final long startTick;
    public final FlightPath path;
    public final List<Drop> drops;
    public final Vec3 target;
    /** Entity id of the caller (for client-side HUD), -1 if none. */
    public final int callerEntityId;
    /** Strike time (ticks) at which the aircraft passes over the target. */
    public final double overflightTime;
    public final long seed;

    /** Server side only. */
    public UUID callerUuid;
    /**
     * Server side only (25/10) : identifiant de l'événement qui a lancé ce largage (« largage#12 », module
     * heraliz_evenements), donné à la caisse ; null pour un largage de joueur ou du staff.
     */
    public String evenement;

    public Strike(int id, StrikeType type, long startTick, FlightPath path, List<Drop> drops, Vec3 target,
                  int callerEntityId, double overflightTime, long seed) {
        this.id = id;
        this.type = type;
        this.startTick = startTick;
        this.path = path;
        List<Drop> sorted = new ArrayList<>(drops);
        sorted.sort((a, b) -> Double.compare(a.t1, b.t1));
        this.drops = Collections.unmodifiableList(sorted);
        this.target = target;
        this.callerEntityId = callerEntityId;
        this.overflightTime = overflightTime;
        this.seed = seed;
    }

    /** Strike time after which nothing visible remains. */
    public double endTime() {
        double end = path.duration;
        for (Drop d : drops) end = Math.max(end, d.t1 + 2);
        return end;
    }

    /** Game time of the first impact (for the HUD countdown). */
    public double firstImpactTime() {
        double best = Double.MAX_VALUE;
        for (Drop d : drops) {
            if (d.kind != OrdnanceKind.CLUSTER && d.kind != OrdnanceKind.CRATE) best = Math.min(best, d.t1);
        }
        return best == Double.MAX_VALUE ? overflightTime : best;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeByte(type.ordinal());
        buf.writeLong(startTick);
        path.write(buf);
        buf.writeVarInt(drops.size());
        for (Drop d : drops) d.write(buf);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        buf.writeVarInt(callerEntityId);
        buf.writeDouble(overflightTime);
        buf.writeLong(seed);
    }

    public static Strike read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        StrikeType type = StrikeType.byId(buf.readByte());
        long start = buf.readLong();
        FlightPath path = FlightPath.read(buf);
        int n = buf.readVarInt();
        List<Drop> drops = new ArrayList<>(n);
        for (int i = 0; i < n; i++) drops.add(Drop.read(buf));
        Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int caller = buf.readVarInt();
        double over = buf.readDouble();
        long seed = buf.readLong();
        return new Strike(id, type, start, path, drops, target, caller, over, seed);
    }
}
