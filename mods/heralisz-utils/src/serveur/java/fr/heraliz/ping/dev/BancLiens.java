package fr.heraliz.ping.dev;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import net.minecraft.server.level.ServerPlayer;

/**
 * Banc d'essai : acces directs au mod clans (client de dev seulement, jamais charge en jeu). Fonde un clan et y ajoute
 * des faux joueurs (groupes et parties de HeraliZ Matchs retires le 24/10 avec les matchs).
 */
final class BancLiens {
    private BancLiens() {
    }

    /** Fonde le clan [PING] (cout 0, banniere ambre) ; vrai si le joueur en a un ensuite. */
    static boolean fonderClan(ServerPlayer chef) {
        ClanStore s = ClanStore.get();
        if (s == null) return false;
        if (s.clanDe(chef.getUUID()) != null) return true;
        ClansConfig.COUT_CREATION.set(0L);
        return ClanService.creer(chef, "PING", "Banc des pings", "", Banniere.DEFAUT.avecCouleurA(0xF2A93B).avecCouleurB(0x8A4B12));
    }

    static void ajouterAuClan(ServerPlayer chef, ServerPlayer membre) {
        ClanStore s = ClanStore.get();
        Clan c = s.clanDe(chef.getUUID());
        long now = System.currentTimeMillis();
        c.membres.put(membre.getUUID(), new Membre(membre.getUUID(), membre.getGameProfile().getName(), Grade.MEMBRE, now, now, null));
        s.lien(membre.getUUID()).clan = c.id; // le lien joueur -> clan (c'est lui que lit ClansApi.clanDe)
        s.marquerDirty();
    }

    static void retirerDuClan(ServerPlayer chef, ServerPlayer membre) {
        ClanStore s = ClanStore.get();
        Clan c = s.clanDe(chef.getUUID());
        if (c == null) return;
        c.membres.remove(membre.getUUID());
        s.lien(membre.getUUID()).clan = null;
        s.marquerDirty();
    }
}
