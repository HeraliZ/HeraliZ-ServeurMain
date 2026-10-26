package fr.heraliz.crac.build.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Latest health / stability answers from the server, by piece id. */
@OnlyIn(Dist.CLIENT)
public final class PieceInfoCache {
    private static final int MAX = 64;
    private static final Long2ObjectOpenHashMap<Entry> ENTRIES = new Long2ObjectOpenHashMap<>();

    public record Entry(S2CPieceInfo info, long receivedAt) {
        /** Demolish seconds left now, counting the time since the answer arrived; -1 = no limit. */
        public int demolishSecondsLeft() {
            if (this.info.demolishSeconds() < 0) {
                return -1;
            }
            return (int) Math.max(0, this.info.demolishSeconds() - (Util.getMillis() - this.receivedAt) / 1000L);
        }

        public boolean canDemolish() {
            return this.info.canDemolish() && this.demolishSecondsLeft() != 0;
        }
    }

    private PieceInfoCache() {
    }

    public static void put(S2CPieceInfo info) {
        if (!info.exists()) {
            ENTRIES.remove(info.pieceId());
            return;
        }
        if (ENTRIES.size() >= MAX && !ENTRIES.containsKey(info.pieceId())) {
            ENTRIES.clear();
        }
        ENTRIES.put(info.pieceId(), new Entry(info, Util.getMillis()));
    }

    @Nullable
    public static Entry get(long pieceId) {
        return ENTRIES.get(pieceId);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
