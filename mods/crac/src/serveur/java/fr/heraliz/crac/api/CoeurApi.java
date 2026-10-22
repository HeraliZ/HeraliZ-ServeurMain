package fr.heraliz.crac.api;

import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.heraliz.BasesClans;
import fr.heraliz.crac.heraliz.CoeursClans;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Pour HeraliZ Clans (saison des clans), appelée PAR RÉFLEXION : ne jamais renommer cette classe ni changer ces
 * signatures (CLAUDE.md §5). Côté serveur seulement.
 */
public final class CoeurApi {
    private CoeurApi() {
    }

    /**
     * Le clan a son cœur posé DANS sa base (zone de son armoire) : sans ça, il ne marque aucun point de saison et n'est pas
     * classé. Faux sans base, sans cœur, cœur repris ({@code /coeur deplacer}) ou armoire perdue.
     */
    public static boolean clanAUnCoeur(UUID clan) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || clan == null) {
            return false;
        }
        UUID groupe = BasesClans.groupeDuClan(server, clan);
        if (groupe == null) {
            return false;
        }
        GlobalPos g = CoeursClans.coeur(server, groupe);
        if (g == null) {
            return false;
        }
        ServerLevel level = server.getLevel(g.dimension());
        Claim c = level == null ? null : ClaimManager.getAt(level, g.pos());
        return c != null && groupe.equals(c.groupId());
    }
}
