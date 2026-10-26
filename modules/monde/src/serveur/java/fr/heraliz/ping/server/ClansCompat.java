package fr.heraliz.ping.server;

import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lien avec HeraliZ Clans (charge seulement si le mod est present : voir {@link Compat}). Fil serveur. */
final class ClansCompat {
    private ClansCompat() {
    }

    /**
     * Ajoute a {@code out} les membres connectes du clan du joueur (lui compris) et rend l'accent du clan (couleur de sa
     * banniere, eclaircie si trop sombre), ou -1 s'il n'a pas de clan.
     */
    static int membres(ServerPlayer p, List<ServerPlayer> out) {
        try {
            if (!ClansApi.disponible()) return -1;
            Optional<UUID> clan = ClansApi.clanDe(p.getUUID());
            if (clan.isEmpty()) return -1;
            ClanInfo info = ClansApi.infos(clan.get()).orElse(null);
            if (info == null) return -1;
            PlayerList liste = p.server.getPlayerList();
            for (UUID m : info.enLigne()) {
                ServerPlayer d = liste.getPlayer(m);
                if (d != null) out.add(d);
            }
            return info.banniere().accent() & 0xFFFFFF;
        } catch (LinkageError ex) {
            Compat.couperClans(ex);
            return -1;
        }
    }

    /** Tag du clan du joueur (« [TAG] »), ou null. */
    static String tag(ServerPlayer p) {
        try {
            Optional<UUID> clan = ClansApi.clanDe(p.getUUID());
            return clan.flatMap(ClansApi::infos).map(ClanInfo::tagCrochets).orElse(null);
        } catch (LinkageError ex) {
            Compat.couperClans(ex);
            return null;
        }
    }
}
