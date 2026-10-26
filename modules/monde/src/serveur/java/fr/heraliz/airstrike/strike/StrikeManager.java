package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.AirstrikeConfig;
import fr.heraliz.airstrike.entity.EffectCloudEntity;
import fr.heraliz.airstrike.entity.EffectCloudEntityServeur;
import fr.heraliz.airstrike.network.Network;
import fr.heraliz.airstrike.network.StrikePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side bookkeeping of the strikes in flight: schedules the impacts, keeps the target area loaded and
 * replays the strikes to players who join late.
 */
public final class StrikeManager {
    /** Keeps the target area ticking while a strike (and its clouds) plays out. Expires on its own. */
    public static final TicketType<Integer> TICKET = TicketType.create("airstrike", Integer::compareTo, 20 * 150);

    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    static final class Active {
        final Strike strike;
        final List<Event> events = new ArrayList<>();
        int cursor;
        boolean releaseAnnounced;

        Active(Strike strike) {
            this.strike = strike;
        }
    }

    record Event(double time, boolean release, Drop drop) {
    }

    private StrikeManager() {
    }

    public static int countActive(@Nullable UUID caller) {
        int n = 0;
        for (List<Active> list : ACTIVE.values()) {
            for (Active a : list) {
                if (caller == null || caller.equals(a.strike.callerUuid)) n++;
            }
        }
        return n;
    }

    public static List<Strike> strikes(ServerLevel level) {
        List<Strike> out = new ArrayList<>();
        for (Active a : ACTIVE.getOrDefault(level.dimension(), List.of())) out.add(a.strike);
        return out;
    }

    /** Plans and starts a strike. Returns null if nothing could be planned. */
    public static Strike launch(ServerLevel level, StrikeType type, Vec3 target, Vec3 from, @Nullable ServerPlayer caller) {
        int id = nextId++;
        long seed = level.random.nextLong();
        Strike strike = StrikePlanner.plan(level, id, type, target, from, caller == null ? -1 : caller.getId(), seed);
        strike.callerUuid = caller == null ? null : caller.getUUID();

        Active active = new Active(strike);
        for (Drop d : strike.drops) {
            if (d.kind == OrdnanceKind.CRATE) active.events.add(new Event(d.t0, true, d));
            else active.events.add(new Event(d.t1, false, d));
        }
        active.events.sort((a, b) -> Double.compare(a.time, b.time));
        ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(active);

        ChunkPos cp = new ChunkPos((int) Math.floor(target.x) >> 4, (int) Math.floor(target.z) >> 4);
        level.getChunkSource().addRegionTicket(TICKET, cp, type == StrikeType.SUPPLY ? 5 : 4, id);

        if (AirstrikeConfig.TARGET_MARKER.get()) {
            int seconds = AirstrikeConfig.STRIKE_DELAY_SECONDS.get() + 12;
            EffectCloudEntity marker = EffectCloudEntityServeur.create(level, EffectCloudEntity.MARKER,
                    new Vec3(target.x, StrikePlanner.groundY(level, target.x, target.z, target.y), target.z),
                    1.0f, type == StrikeType.SUPPLY ? seconds * 20 + 400 : seconds * 20, type.markerColor);
            level.addFreshEntity(marker);
        }

        StrikePacket packet = new StrikePacket(strike);
        double r2 = Network.VISUAL_RANGE * Network.VISUAL_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(target) < r2 || p == caller) Network.sendTo(p, packet);
        }

        if (AirstrikeConfig.ANNOUNCE_NEARBY.get()) {
            for (ServerPlayer p : level.players()) {
                if (p != caller && p.distanceToSqr(target) < 60 * 60) {
                    p.displayClientMessage(Component.translatable("msg.airstrike.incoming").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
        }
        return strike;
    }

    public static void tick(ServerLevel level) {
        List<Active> list = ACTIVE.get(level.dimension());
        if (list == null || list.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Active> it = list.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            double t = now - a.strike.startTick;
            while (a.cursor < a.events.size() && a.events.get(a.cursor).time <= t) {
                Event e = a.events.get(a.cursor++);
                try {
                    if (e.release) {
                        StrikeEffects.release(level, a.strike, e.drop);
                    } else {
                        StrikeEffects.impact(level, a.strike, e.drop);
                    }
                } catch (Exception ex) {
                    fr.heraliz.airstrike.AirstrikeMod.LOGGER.error("Airstrike effect failed", ex);
                }
                if (!a.releaseAnnounced) {
                    a.releaseAnnounced = true;
                    announceRelease(level, a.strike);
                }
            }
            if (a.cursor >= a.events.size() && t > a.strike.endTime() + 5) {
                it.remove();
            }
        }
    }

    private static void announceRelease(ServerLevel level, Strike strike) {
        if (strike.callerUuid == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(strike.callerUuid);
        if (p == null) return;
        String key = switch (strike.type) {
            case SUPPLY -> "msg.airstrike.crate_away";
            case GUN_RUN -> "msg.airstrike.guns";
            case SMOKE, GAS -> "msg.airstrike.canisters_away";
            default -> "msg.airstrike.bombs_away";
        };
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD), true);
    }

    /** Sends the strikes currently in flight to a player that just arrived in the level. */
    public static void syncTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Active a : ACTIVE.getOrDefault(level.dimension(), List.of())) {
            if (player.distanceToSqr(a.strike.target) < Network.VISUAL_RANGE * Network.VISUAL_RANGE) {
                Network.sendTo(player, new StrikePacket(a.strike));
            }
        }
    }

    public static int clear() {
        int n = countActive(null);
        ACTIVE.clear();
        return n;
    }
}
