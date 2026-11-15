package fr.heraliz.crac.api;

import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.BasesClans;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * API de CRac pour nos autres mods (côté serveur seulement, appelée par réflexion : commande /inspecter de HeraliZ Utils,
 * qui montre au staff tout le contenu d'une base ; /tpa de HeraliZ Boutique). Ne jamais renommer cette classe ni changer
 * ses signatures. Fil serveur.
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

    /**
     * HeraliZ (15/11, audit C1, demande du propriétaire) : le joueur est dans la zone de l'armoire de la base de SON clan
     * et n'a pas le droit de clan « Accepter un /tpa dans la base » ({@code Perm.BASE_TP_ACCEPTER}) : il ne peut pas y faire
     * venir quelqu'un par téléportation. Faux hors d'une base, dans la base d'un autre clan, pour le chef (il a tous les
     * droits) et sans HeraliZ Clans. Lu par réflexion par HeraliZ Boutique (/tpa, à l'acceptation et au départ).
     */
    public static boolean tpaRefuseeDansBase(ServerPlayer joueur) {
        if (joueur == null || !BasesClans.actif()) {
            return false;
        }
        ServerLevel level = joueur.serverLevel();
        Claim c = ClaimManager.getAt(level, joueur.blockPosition());
        if (c == null) {
            return false;
        }
        Group g = GroupData.get(level.getServer()).getById(c.groupId());
        return g != null && BasesClans.duClanSansDroit(level.getServer(), g, joueur.getUUID(), ClanAccess.Action.TP_ACCEPT);
    }
}
