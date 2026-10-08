package fr.heralisz.bases.server;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.BaseVue;
import fr.heralisz.bases.model.EntreeJour;
import fr.heralisz.bases.model.PillableVue;
import fr.heralisz.bases.model.Pillage;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.monde.Chantier;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.monde.Modele;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Envois aux ecrans (BASES_SPEC §6) : etat de la base de mon clan ({@link BaseVue}), plan du bunker, liste des bases
 * pillables. Calculs a la demande (ouverture d'ecran, action, changement d'etat) : rien n'est envoye en continu.
 */
public final class Sync {
    private Sync() {
    }

    /** Droits de base -> Perm du mod clans (meme ordre que {@link BaseVue.Droit}). */
    private static final Perm[] DROITS = {Perm.BASE_ENTRER, Perm.BASE_CONSTRUIRE, Perm.BASE_CASSER, Perm.BASE_COFFRES,
            Perm.BASE_POSER_COFFRES, Perm.BASE_PORTES, Perm.BASE_GERER};

    /** Etat de la base de mon clan, tel que l'ecran BASE l'affiche. */
    public static BaseVue vue(ServerPlayer p) {
        long cout = BasesConfig.COUT_CREATION.get();
        Optional<UUID> clan = ClansApi.clanDe(p.getUUID());
        Optional<ClanInfo> ci = clan.flatMap(ClansApi::infos);
        if (ci.isEmpty()) return BaseVue.sansClan(cout, Fenetre.vue());
        ClanInfo c = ci.get();
        int perms = 0;
        for (int i = 0; i < DROITS.length; i++) if (ClansApi.aPerm(p.getUUID(), c.id(), DROITS[i])) perms |= 1 << i;
        BasesStore s = BaseService.store();
        Base b = s == null ? null : s.base(c.id());
        Modele m = Modele.get();
        List<BaseVue.SalleVue> salles = new ArrayList<>();
        if (m != null) {
            for (Plan.Salle sa : m.plan.salles) {
                salles.add(new BaseVue.SalleVue(sa.id(), b != null && b.salleOuverte(sa), sa.depart(), BaseService.cout(sa)));
            }
        }
        float prog = 1f;
        if (b != null && !b.prete()) {
            Chantier.Travail t = Chantier.travail(b.parcelle);
            prog = t == null ? 0f : t.progression();
        }
        List<String> attaquants = b == null ? List.of() : BasesApi.pillage().attaquants(b);
        int pillards = b == null ? 0 : BasesApi.pillage().pillards(b);
        List<Pillage> pillages = b == null ? List.of() : b.pillages.subList(0, Math.min(5, b.pillages.size()));
        boolean dedans = b != null && Grille.estBunkers(p.level()) && Grille.parcelle(p.getBlockX(), p.getBlockZ()) == b.parcelle;
        return new BaseVue(c.id(), c.tag(), c.nom(), c.banniere(), perms, b != null, b == null ? Base.Etat.PRETE : b.etat, prog, cout,
                ClansApi.soldeBanque(c.id()), salles, Fenetre.vue(), attaquants, pillards, new ArrayList<>(pillages), dedans,
                m == null ? 0 : m.empreinte);
    }

    public static void etat(ServerPlayer p) {
        try {
            BasesNet.toPlayer(p, new Packets.EtatBaseS2C(vue(p)));
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] etat de base impossible a envoyer a {}", p.getGameProfile().getName(), ex);
        }
    }

    public static void plan(ServerPlayer p) {
        Modele m = Modele.get();
        BasesNet.toPlayer(p, new Packets.PlanS2C(m == null ? null : m.plan));
    }

    /** Etat a tous les membres en ligne du clan. */
    public static void clan(UUID clan) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ClansApi.infos(clan).ifPresent(ci -> {
            for (UUID u : ci.enLigne()) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) etat(p);
            }
        });
    }

    /**
     * Liste par defaut de l'ecran PILLER : bases pretes des autres clans, avec la raison d'un refus (fenetre fermee,
     * entree du jour deja utilisee, base pleine). Le module pillage peut la modifier
     * ({@link fr.heralisz.bases.api.PillageHooks#pillables}).
     */
    public static List<PillableVue> pillablesParDefaut(ServerPlayer p) {
        BasesStore s = BaseService.store();
        List<PillableVue> l = new ArrayList<>();
        if (s == null) return l;
        UUID mon = ClansApi.clanDe(p.getUUID()).orElse(null);
        boolean ouverte = Fenetre.ouverte();
        EntreeJour ej = s.entreeJour(p.getUUID());
        boolean entreeUtilisee = ej != null && ej.jour().equals(Fenetre.jour());
        int max = BasesConfig.PILLARDS_MAX.get();
        for (Base b : s.bases()) {
            if (!b.prete() || b.clan.equals(mon)) continue;
            Optional<ClanInfo> ci = ClansApi.infos(b.clan);
            if (ci.isEmpty()) continue;
            ClanInfo c = ci.get();
            int pillards = BasesApi.pillage().pillards(b);
            boolean deja = entreeUtilisee && b.clan.equals(ej.base());
            String raison = "";
            if (!ouverte) raison = "gui.heralisz_bases.raison.fenetre";
            else if (mon == null) raison = "gui.heralisz_bases.raison.sans_clan";
            else if (entreeUtilisee) raison = "gui.heralisz_bases.raison.entree_utilisee";
            else if (pillards >= max) raison = "gui.heralisz_bases.raison.pleine";
            Banniere ban = c.banniere() == null ? Banniere.DEFAUT : c.banniere();
            l.add(new PillableVue(c.id(), c.tag(), c.nom(), ban, c.membres().size(), c.enLigne().size(), raison.isEmpty(), raison, deja,
                    pillards));
        }
        // en ligne d'abord, puis tag
        l.sort((a, b) -> a.enLigne() != b.enLigne() ? Integer.compare(b.enLigne(), a.enLigne()) : a.tag().compareToIgnoreCase(b.tag()));
        return l;
    }

    public static void pillables(ServerPlayer p) {
        List<PillableVue> l = pillablesParDefaut(p);
        try {
            l = BasesApi.pillage().pillables(p, l);
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] module pillage : liste en erreur", ex);
        }
        BasesNet.toPlayer(p, new Packets.PillablesS2C(l, Fenetre.vue()));
    }
}
