package fr.heraliz.ping.dev;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.matchs.moteur.api.Equipe;
import fr.heralisz.matchs.moteur.api.Groupe;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Banc d'essai : acces directs aux mods clans et matchs (client de dev seulement, jamais charge en jeu). Fonde un clan,
 * y ajoute des faux joueurs, forme un groupe, ouvre une partie a deux equipes avec un spectateur.
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

    static void groupe(List<UUID> membres) {
        Groupes.definir(null, new Groupe(UUID.randomUUID(), membres.get(0), membres));
    }

    static void finGroupe(UUID membre) {
        Groupe g = Groupes.de(membre);
        if (g != null) Groupes.definir(g, null);
    }

    /** Partie de test : equipe A (rouge), equipe B (bleue), spectateurs ; aucun tick, aucune regle. */
    static final class PartieTest extends Partie {
        final Equipe a = new Equipe("a", "Equipe A", 0xE0454B), b = new Equipe("b", "Equipe B", 0x3F7FE0);

        PartieTest(MinecraftServer serveur) {
            super("ping-banc", "duel", serveur);
            ajouterEquipe(a);
            ajouterEquipe(b);
            phase(Phase.COMBAT);
        }

        @Override
        public void tick() {
        }

        @Override
        public void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source) {
        }

        @Override
        public void surDeconnexion(ServerPlayer joueur) {
        }
    }

    static PartieTest ouvrirPartie(MinecraftServer serveur, ServerPlayer a1, ServerPlayer a2, ServerPlayer b1, ServerPlayer spectateur) {
        PartieTest p = new PartieTest(serveur);
        MatchApi.enregistrer(p);
        p.a.ajouter(a1.getUUID());
        p.a.ajouter(a2.getUUID());
        p.a.ajouter(spectateur.getUUID());
        p.a.eliminer(spectateur.getUUID());
        p.b.ajouter(b1.getUUID());
        p.spectateurs().add(spectateur.getUUID());
        for (ServerPlayer j : List.of(a1, a2, b1, spectateur)) MatchApi.rejoindre(j, p);
        return p;
    }

    static void fermerPartie(PartieTest p, List<ServerPlayer> joueurs) {
        for (ServerPlayer j : joueurs) MatchApi.quitter(j);
        p.fermer();
    }

    static int couleurEquipeA(PartieTest p) {
        return p.a.couleur();
    }
}
