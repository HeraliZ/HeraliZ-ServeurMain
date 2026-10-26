package fr.heraliz.crac.build.client;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.logic.Stability;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.ClientMirror;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Block occupants carry no stability, so the ghost would take every mirrored piece as fully stable. This recomputes
 * it over the mirror, at most twice a second and only after the mirror changed.
 */
@OnlyIn(Dist.CLIENT)
final class MirrorStability {
    private static final long MIN_GAP_MS = 500L;
    private static int seen = Integer.MIN_VALUE;
    private static long at;

    private MirrorStability() {
    }

    static void refresh(Stability.Params params) {
        int v = ClientMirror.version();
        long now = Util.getMillis();
        if (v == seen || now - at < MIN_GAP_MS) {
            return;
        }
        seen = v;
        at = now;
        PieceIndex view = ClientMirror.view();
        for (Building b : view.allBuildings()) {
            List<Piece> pieces = new ArrayList<>(b.pieces.size());
            for (long id : b.pieces) {
                Piece p = view.piece(id);
                if (p != null) {
                    pieces.add(p);
                }
            }
            if (!pieces.isEmpty()) {
                Stability.recompute(view, pieces, params);
            }
        }
    }
}
