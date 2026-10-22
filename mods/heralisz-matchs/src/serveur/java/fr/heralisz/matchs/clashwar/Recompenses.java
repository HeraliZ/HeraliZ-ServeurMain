package fr.heralisz.matchs.clashwar;

import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Coffre;
import fr.heralisz.matchs.moteur.api.MatchApi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Recompenses et statistiques de fin d'edition (MATCHS_SPEC §2.6) : jetons verses a la banque du clan (1er, 2e, 3e et
 * 4e, d'apres le match pour la 3e place ; montants en configuration), une cle de caisse d'event par joueur de l'equipe gagnante
 * (donnee apres le retour du Coffre, ou a la prochaine connexion), stats « ClashWar joues / gagnes » de chaque clan
 * participant ({@link ClansApi#ajouterClashWar}, une fois par clan) et lignes du journal de clan.
 */
public final class Recompenses {
    private Recompenses() {
    }

    static void distribuer(Tournoi t) {
        Tableau tab = t.tableau;
        if (tab == null || !tab.termine()) return;
        CwConfig cfg = CwConfig.get();
        Tableau.EquipeT champion = tab.equipe(tab.champion());
        String edition = "#" + t.edition;
        if (ClansApi.disponible()) {
            // banque des clans
            // podium : 1er, 2e, 3e (match pour la 3e place), 4e
            int[] podium = tab.podium();
            crediter(champion, cfg.recompenseVainqueur, "clashwar_victoire");
            if (podium[1] >= 0) crediter(tab.equipe(podium[1]), cfg.recompenseFinaliste, "clashwar_finale");
            if (podium[2] >= 0) crediter(tab.equipe(podium[2]), cfg.recompenseTroisieme, "clashwar_troisieme");
            if (podium[3] >= 0) crediter(tab.equipe(podium[3]), cfg.recompenseDemi, "clashwar_demi");
            for (int i = 0; i < podium.length; i++) {
                if (podium[i] >= 0) CwClassement.place(tab.equipe(podium[i]), i + 1);
            }
            CwClassement.sauver(t.serveur());
            // stats et journal : une fois par clan participant
            Set<UUID> clans = new LinkedHashSet<>();
            for (Tableau.EquipeT e : tab.equipes()) clans.add(e.clan);
            for (UUID c : clans) {
                boolean gagne = c.equals(champion.clan);
                ClansApi.ajouterClashWar(c, gagne);
                if (gagne) ClansApi.journal(c, "clashwar.journal.victoire", champion.libelle(), edition, t.format.nom);
                else ClansApi.journal(c, "clashwar.journal.elimine." + meilleurTour(tab, c), edition, t.format.nom);
            }
            saison(tab, podium, clans);
        } else {
            HeraliszMatchs.LOG.warn("[clashwar] mod clans indisponible : ni banque ni stats");
        }
        // cles de caisse (donnees au retour du Coffre)
        if (cfg.clesParJoueur > 0 && !cfg.cleEvent.isBlank()) {
            for (UUID u : champion.joueurs) {
                if (!t.mannequins.contains(u)) CwEtat.CLES.merge(u, cfg.clesParJoueur, Integer::sum);
            }
            CwEtat.sauver(t.serveur());
        }
    }

    /** Clans differents au moins pour que l'edition compte dans la saison (pas de tournoi entre deux clans complices). */
    static final int SAISON_CLANS_MIN = 4;

    /**
     * Saison des clans (HeraliZ Clans 3.6.0, {@code SaisonApi.event}, par reflexion : la copie de Clans de libs/ ne l'a pas) :
     * podium dans l'ordre, participation pour les autres clans ; bareme (et x2 du ClashWar) chez Clans.
     */
    private static void saison(Tableau tab, int[] podium, Set<UUID> clans) {
        if (clans.size() < SAISON_CLANS_MIN) {
            HeraliszMatchs.LOG.info("[clashwar] {} clan(s) seulement : pas de points de saison (au moins {})", clans.size(), SAISON_CLANS_MIN);
            return;
        }
        java.util.List<UUID> ordre = new java.util.ArrayList<>();
        for (int i : podium) {
            if (i >= 0) ordre.add(tab.equipe(i).clan);
        }
        try {
            Class.forName("fr.heralisz.clans.saison.SaisonApi")
                    .getMethod("event", String.class, java.util.List.class, java.util.Collection.class)
                    .invoke(null, "clashwar", ordre, clans);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // HeraliZ Clans sans saison : rien
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            HeraliszMatchs.LOG.warn("[clashwar] points de saison non donnes : {}", e.toString());
        }
    }

    private static void crediter(Tableau.EquipeT e, long montant, String motif) {
        if (montant <= 0) return;
        long verse = ClansApi.crediterBanque(e.clan, montant, motif);
        HeraliszMatchs.LOG.info("[clashwar] {} : +{} jetons ({})", e.libelle(), verse, motif);
    }

    /** Suffixe du meilleur tour atteint par une equipe du clan (cle du journal). */
    private static String meilleurTour(Tableau tab, UUID clan) {
        int meilleur = 0;
        for (List<Tableau.Rencontre> tour : tab.tours()) {
            for (Tableau.Rencontre r : tour) {
                if ((r.a >= 0 && tab.equipe(r.a).clan.equals(clan)) || (r.b >= 0 && tab.equipe(r.b).clan.equals(clan))) {
                    // le match pour la 3e place se joue entre demi-finalistes
                    meilleur = Math.max(meilleur, r == tab.troisieme() ? r.tour - 1 : r.tour);
                }
            }
        }
        return tab.cleTour(meilleur);
    }

    /**
     * Donne les cles en attente du joueur s'il est libre (pas en match, pas de Coffre en cours). Inventaire plein = au
     * sol a ses pieds.
     */
    static void donnerCles(ServerPlayer p) {
        Integer n = CwEtat.CLES.get(p.getUUID());
        if (n == null || n <= 0) return;
        if (MatchApi.enMatch(p) || Coffre.aUnDepot(p.getUUID())) return;
        String id = CwConfig.get().cleEvent;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            HeraliszMatchs.LOG.warn("[clashwar] cle d'event {} introuvable : {} cle(s) gardee(s) pour {}", id, n, p.getGameProfile().getName());
            return;
        }
        CwEtat.CLES.remove(p.getUUID());
        ItemStack pile = new ItemStack(item, n);
        if (!p.getInventory().add(pile)) p.drop(pile, false);
        Annonces.chat(p, Component.translatable("clashwar.chat.cles", n, item.getDescription()));
        CwEtat.sauver(p.server);
    }
}
