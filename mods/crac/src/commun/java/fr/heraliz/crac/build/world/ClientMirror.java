package fr.heraliz.crac.build.world;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import net.minecraft.core.BlockPos;

/**
 * Client-side copy of the pieces around the player, rebuilt from the occupants of loaded structure block entities.
 * Lets the ghost preview run the same placement rules as the server. Common-safe: no client-only classes.
 */
public final class ClientMirror {
    private static final UUID NOBODY = new UUID(0L, 0L);
    private static PieceIndex index = new PieceIndex();
    private static final Long2ObjectOpenHashMap<List<Occupant>> BY_BLOCK = new Long2ObjectOpenHashMap<>();
    private static final Long2IntOpenHashMap BLOCK_COUNT = new Long2IntOpenHashMap();
    private static int version;

    private ClientMirror() {
    }

    public static PieceIndex view() {
        return index;
    }

    /** Changes every time the mirrored pieces change (to know when derived data such as stability is stale). */
    public static int version() {
        return version;
    }

    public static synchronized void clear() {
        version++;
        index = new PieceIndex();
        BY_BLOCK.clear();
        BLOCK_COUNT.clear();
    }

    /** A structure block entity was loaded or updated on the client. */
    public static synchronized void update(BlockPos pos, List<Occupant> occupants) {
        version++;
        long key = pos.asLong();
        List<Occupant> old = BY_BLOCK.remove(key);
        if (old != null) {
            for (Occupant o : old) {
                Piece p = index.piece(o.pieceId());
                if (p != null) {
                    index.removeBlock(key, p);
                    if (BLOCK_COUNT.addTo(o.pieceId(), -1) <= 1) {
                        BLOCK_COUNT.remove(o.pieceId());
                        index.removePiece(p);
                    }
                }
            }
        }
        if (occupants.isEmpty()) {
            return;
        }
        BY_BLOCK.put(key, occupants);
        for (Occupant o : occupants) {
            Piece p = index.piece(o.pieceId());
            if (p == null) {
                ensureBuilding(o);
                p = new Piece(o.pieceId(), o.key(), o.buildingId(), o.tier(), 1, NOBODY, 0L, null);
                index.addPiece(p);
            } else if (p.buildingId != o.buildingId()) {
                // the piece was split off into another building
                index.removePiece(p);
                ensureBuilding(o);
                p.buildingId = o.buildingId();
                index.addPiece(p);
            }
            p.tier = o.tier();
            p.state = o.state();
            p.variant = o.variant();
            // stability is not synced: the building UI recomputes it over the mirror when needed
            p.stability = 100;
            index.addBlock(key, p);
            BLOCK_COUNT.addTo(o.pieceId(), 1);
        }
    }

    public static synchronized void remove(BlockPos pos) {
        update(pos, List.of());
    }

    private static void ensureBuilding(Occupant o) {
        if (index.building(o.buildingId()) == null) {
            index.putBuilding(new Building(o.buildingId(), o.key().origin()));
        }
    }
}
