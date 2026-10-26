package fr.heraliz.crac.api;

import fr.heraliz.crac.build.logic.Topology;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Pour le banc d'essai (bots de charge, module banc), appelée PAR RÉFLEXION : ne jamais renommer cette classe ni changer
 * ces signatures. Côté serveur seulement. Les bots bâtissent comme des joueurs (vraies pièces de CRac, mêmes règles de
 * pose), sans coût ni armoire ; tout est effacé à la fin du test.
 */
public final class BancApi {
    private BancApi() {
    }

    private static final Tier[] PALIERS = {Tier.TWIG, Tier.WOOD, Tier.STONE, Tier.METAL};

    /**
     * Petite base de bot posée sur le sol à {@code pieds} (position des pieds, sur le terrain) : 2 fondations, les murs
     * autour avec une porte, le toit ; palier tiré de {@code graine} (brindilles à métal). Nombre de pièces posées (0 si
     * la place ne convient pas : pente, bâtiment voisin, zone interdite…).
     */
    public static int construire(ServerLevel level, BlockPos pieds, UUID owner, long graine) {
        Tier tier = PALIERS[(int) Math.floorMod(graine, PALIERS.length)];
        BlockPos origine = new BlockPos(pieds.getX(), pieds.getY() + 1, pieds.getZ());
        PieceKey f = new PieceKey(PieceKind.FOUNDATION, origine, LPoint.ORIGIN, 0, 0, 0);
        if (!StructureService.placeAdmin(level, f, tier, owner).ok()) {
            return 0;
        }
        int n = 1;
        int partage = (int) Math.floorMod(graine >> 3, 4);
        PieceKey s = Topology.across(f, partage, PieceKind.FOUNDATION, f.h());
        boolean deux = StructureService.placeAdmin(level, s, tier, owner).ok();
        if (deux) {
            n++;
        }
        int top = f.top();
        List<PieceKey> murs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (deux && i == partage) {
                continue;
            }
            murs.add(Topology.wallOn(f, i, i == (partage + 2) % 4 ? PieceKind.DOORWAY : PieceKind.WALL, top, 0));
        }
        if (deux) {
            for (int i = 0; i < 4; i++) {
                murs.add(Topology.wallOn(s, i, PieceKind.WALL, top, 0));
            }
        }
        int haut = -1;
        for (PieceKey m : murs) {
            StructureService.Outcome o = StructureService.placeAdmin(level, m, tier, owner);
            if (o.ok()) {
                n++;
                haut = m.top();
            }
        }
        if (haut >= 0) {
            for (PieceKey sol : deux ? List.of(f, s) : List.of(f)) {
                if (StructureService.placeAdmin(level, PieceKey.on(PieceKind.FLOOR, sol.origin(), sol.footprint(), haut, 0), tier, owner).ok()) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Efface tout ce que {@code owner} a bâti, dans tous les mondes (fin d'un test de charge). Pièces retirées. */
    public static int effacer(MinecraftServer server, UUID owner) {
        return fr.heraliz.crac.bases.Tutoriel.effacerConstructions(server, owner);
    }

    /** Pièces de CRac posées dans le monde (tous mondes), pour les rapports du banc. */
    public static int pieces(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) {
            n += StructureService.store(l).index.allPieces().size();
        }
        return n;
    }
}
