package fr.heraliz.crac.api;

import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * API de CRac pour nos autres mods (côté serveur seulement, appelée par réflexion : commande /inspecter de HeraliZ Utils,
 * qui montre au staff tout le contenu d'une base). Ne jamais renommer cette classe ni changer ses signatures. Fil serveur.
 */
public final class BaseCracApi {
    private BaseCracApi() {
    }

    /**
     * Base du groupe d'un joueur (connecté ou non) : {monde (ServerLevel), bloc de revendication (BlockPos), rayon (Integer),
     * rayon en hauteur (Integer), nom du groupe (String)} ; null s'il n'a ni groupe ni Claim Block posé.
     */
    public static Object[] base(MinecraftServer server, UUID joueur) {
        if (server == null || joueur == null) {
            return null;
        }
        Group g = GroupData.get(server).getByMember(joueur);
        if (g == null) {
            return null;
        }
        for (ServerLevel l : server.getAllLevels()) {
            Claim c = ClaimData.get(l).getByGroup(g.id());
            if (c != null) {
                return new Object[]{l, c.anchor(), c.radius(), c.yRadius(), g.name()};
            }
        }
        return null;
    }
}
