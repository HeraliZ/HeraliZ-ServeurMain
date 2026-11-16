package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.HeralizRecolte;
import fr.heraliz.recolte.entites.Arbre;
import fr.heraliz.recolte.entites.ArbreServeur;
import fr.heraliz.recolte.entites.EntitesRecolte;
import fr.heraliz.recolte.entites.GisementServeur;
import fr.heraliz.recolte.entites.RessourceEntite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Apparition des ressources (A_FAIRE §6, « Apparition » : le plus important pour les performances). Le serveur suit la
 * liste des places ({@link Places}) ; il ne charge JAMAIS un tronçon exprès et ne remplace jamais un bloc.
 * <ul>
 *    <li>Il manque une ressource (démarrage, récoltée, retirée) : une place est tirée au hasard dans la bordure du monde,
 *    sans lire un bloc ({@link Terrain#raisonSansBloc}), et attend son heure de réapparition.</li>
 *    <li>Une place en attente n'est contrôlée que si son tronçon et ceux qu'il faut pour les contrôles sont DÉJÀ chargés
 *    par un joueur, et qu'aucun joueur n'est à moins de {@code distance_joueurs} blocs (personne ne la voit surgir) ; au
 *    plus une place contrôlée par tick ; un tour de la liste toutes les 2 s au plus ; rien du tout tant qu'aucune place
 *    n'est prête. Refusée ({@link Terrain#controler}) : une autre place au hasard. Une place qui attend depuis
 *    {@code attente_max_minutes} un coin de carte où personne ne va est tirée ailleurs.</li>
 *    <li>Une entité enlevée autrement que par la récolte ({@code /kill}) libère sa place ; une entité sans place connue
 *    (copie, ancienne version, autre monde) n'entre pas dans le monde ; une place dont l'entité a disparu (arrêt brutal du
 *    serveur entre deux sauvegardes) est libérée après une minute de coin de carte chargé sans elle.</li>
 * </ul>
 * Fil du serveur seulement.
 */
public final class Apparition {
    private Apparition() {
    }

    /** Écart minimal entre deux débuts de tour de la liste quand des places attendent leur coin de carte (ms). */
    private static final long TOUR_MIN_MS = 2_000L;
    /** Une place posée dont l'entité manque dans son coin de carte chargé depuis ce temps est libérée (ms). */
    private static final long ABSENTE_MS = 60_000L;

    private static int curseur, curseurPosees, ticksMaintenance, examinees;
    private static long debutTourMs, prochainTravailMs, prochainePreteMs;
    /** Une place a changé pendant le tour : un nouveau tour tout de suite après. */
    private static boolean changement;
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

    // statistiques depuis le démarrage (/recolte info)
    static int controles, posees;
    static final int[] REFUS = new int[Terrain.RAISONS.length];

    // ------------------------------------------------------------------ cycle du serveur
    /** Serveur démarré : places lues, complétées selon le barème. */
    static void demarrer(MinecraftServer s) {
        remettreAZero();
        ajuster(s);
        Places pl = Places.de(s);
        int poseesListe = 0;
        for (Places.Place p : pl.liste()) {
            if (p.etat == Places.POSEE) {
                poseesListe++;
            }
        }
        HeralizRecolte.LOG.info("[recolte] {} place(s) de ressources, {} posee(s), dans {}", pl.liste().size(), poseesListe,
                monde(s).dimension().location());
    }

    /** Serveur arrêté : tout ce qui est gardé en mémoire est oublié (solo : un autre monde peut suivre). */
    static void arreter() {
        remettreAZero();
        Places.oublier();
        Zones.oublier();
    }

    private static void remettreAZero() {
        curseur = curseurPosees = ticksMaintenance = examinees = 0;
        debutTourMs = prochainTravailMs = 0L;
        prochainePreteMs = Long.MAX_VALUE;
        changement = false;
        controles = posees = 0;
        java.util.Arrays.fill(REFUS, 0);
    }

    /** Nouveaux réglages (fichier changé, /recharger recolte) : nombre de places selon le barème. */
    static void reglagesChanges(MinecraftServer s) {
        ajuster(s);
    }

    /** Une place a changé : la liste est revue au prochain tick. */
    private static void signaler() {
        changement = true;
        prochainTravailMs = 0L;
    }

    /** Le monde des ressources (l'overworld si celui du réglage n'existe pas). */
    static ServerLevel monde(MinecraftServer s) {
        ServerLevel l = s.getLevel(Reglages.get().monde);
        return l != null ? l : s.overworld();
    }

    /** Chaque tick (fin) : au plus une place contrôlée ; rien si aucune n'est prête. */
    static void tick(MinecraftServer s) {
        if (++ticksMaintenance >= 20) {
            ticksMaintenance = 0;
            maintenance(s);
        }
        long now = System.currentTimeMillis();
        if (now < prochainTravailMs) {
            return;
        }
        Places pl = Places.de(s);
        List<Places.Place> liste = pl.liste();
        if (curseur == 0) {
            debutTourMs = now;
            examinees = 0;
            prochainePreteMs = Long.MAX_VALUE;
            changement = false;
        }
        while (curseur < liste.size()) {
            Places.Place p = liste.get(curseur++);
            if (p.etat != Places.ATTENTE) {
                continue;
            }
            if (p.prete > now) {
                prochainePreteMs = Math.min(prochainePreteMs, p.prete);
                continue;
            }
            examinees++;
            examiner(s, monde(s), pl, p, now);
            return;
        }
        // fin du tour : le suivant quand une place sera prête, ou dans 2 s si certaines attendent leur coin de carte
        curseur = 0;
        prochainTravailMs = changement ? 0L : examinees > 0 ? debutTourMs + TOUR_MIN_MS : prochainePreteMs;
    }

    /** Une place prête : coin de carte chargé par un joueur, personne trop près, puis contrôles et pose. */
    private static void examiner(MinecraftServer s, ServerLevel l, Places pl, Places.Place p, long now) {
        Reglages r = Reglages.get();
        if (!Terrain.Lecteur.charge(l, p.x, p.z)) {
            if (now - Math.max(p.depuis, p.prete) > r.attenteMaxMs()) {
                tirer(l, pl, p, r);   // un coin où personne ne va : ailleurs (aucun tronçon chargé pour ça)
            }
            return;
        }
        double cx = p.x + 0.5, cz = p.z + 0.5;
        double pres = (double) r.distanceJoueurs * r.distanceJoueurs;
        // distance où un joueur garde un coin de carte chargé (distance de vue du serveur, diagonale comprise)
        double vue = (s.getPlayerList().getViewDistance() + 1) * 16.0 * 1.5;
        vue *= vue;
        boolean parUnJoueur = false;
        for (ServerPlayer j : l.players()) {
            double dx = j.getX() - cx, dz = j.getZ() - cz, d2 = dx * dx + dz * dz;
            if (d2 < pres) {
                return;   // quelqu'un la verrait apparaître : plus tard
            }
            if (d2 <= vue) {
                parUnJoueur = true;
            }
        }
        if (!parUnJoueur) {
            return;
        }
        Terrain.Lecteur lc = Terrain.Lecteur.prendre(l, p.x, p.z, Terrain.rayonTroncons(r));
        if (lc == null) {
            return;   // un tronçon voisin pas encore chargé : plus tard
        }
        controles++;
        double[] y = new double[1];
        int refus = Terrain.controler(l, lc, pl, p, r, y);
        if (refus >= 0) {
            REFUS[refus]++;
            tirer(l, pl, p, r);
            return;
        }
        if (poser(l, pl, p, p.x + 0.5, y[0], p.z + 0.5, l.getRandom().nextFloat() * 360.0F) != null) {
            posees++;
        } else {
            tirer(l, pl, p, r);
        }
    }

    /** Une place posée par seconde, à tour de rôle : son entité est-elle toujours là ? */
    private static void maintenance(MinecraftServer s) {
        Places pl = Places.de(s);
        List<Places.Place> liste = pl.liste();
        for (int n = liste.size(); n > 0; n--) {
            if (curseurPosees >= liste.size()) {
                curseurPosees = 0;
            }
            Places.Place p = liste.get(curseurPosees++);
            if (p.etat == Places.POSEE && p.entite != null) {
                verifierPosee(monde(s), p);
                return;
            }
        }
    }

    private static void verifierPosee(ServerLevel l, Places.Place p) {
        long now = System.currentTimeMillis();
        if (p.entite != null && l.isPositionEntityTicking(POS.set(p.x, p.y, p.z)) && l.getEntity(p.entite) == null) {
            if (p.absenteDepuis == 0L) {
                p.absenteDepuis = now;
            } else if (now - p.absenteDepuis > ABSENTE_MS) {
                HeralizRecolte.LOG.info("[recolte] {} absent de sa place {} {} {} : place liberee", Reglages.SORTES[p.sorte], p.x, p.y, p.z);
                liberer(l, p.entite, 0L);
            }
        } else {
            p.absenteDepuis = 0L;
        }
    }

    // ------------------------------------------------------------------ places
    /** Tire une nouvelle position pour la place, sans lire un bloc (essais du réglage ; sinon la dernière tirée). */
    static void tirer(ServerLevel l, Places pl, Places.Place p, Reglages r) {
        RandomSource rnd = l.getRandom();
        double[] b = Terrain.bornes(l, r);
        int x = p.x, z = p.z;
        for (int i = 0; i < r.essais; i++) {
            x = Mth.floor(Mth.lerp(rnd.nextDouble(), b[0], b[2]));
            z = Mth.floor(Mth.lerp(rnd.nextDouble(), b[1], b[3]));
            if (Terrain.raisonSansBloc(l, pl, p, x, z, r, b) < 0) {
                break;
            }
        }
        p.x = x;
        p.z = z;
        p.y = 0;
        p.depuis = System.currentTimeMillis();
        p.absenteDepuis = 0L;
        pl.setDirty();
        signaler();
    }

    /**
     * Pose l'entité de la place en (x, y, z) : créée par son type (version serveur), la place notée posée AVANT d'entrer
     * dans le monde (sinon elle n'y entrerait pas, {@link #arrivee}). Rend l'entité, ou null (refusée par un autre mod :
     * la place repasse en attente).
     */
    @Nullable
    static Entity poser(ServerLevel l, Places pl, Places.Place p, double x, double y, double z, float lacet) {
        Entity e = (p.sorte == Reglages.ARBRE ? EntitesRecolte.ARBRE.get() : EntitesRecolte.GISEMENT.get()).create(l);
        if (e instanceof ArbreServeur a) {
            a.preparer(p.variante);
        } else if (e instanceof GisementServeur g) {
            g.preparer(p.sorte - Reglages.PIERRE);
        } else {
            return null;
        }
        e.moveTo(x, y, z, lacet, 0.0F);
        pl.poser(p, e.getUUID(), Mth.floor(y));
        if (!l.addFreshEntity(e)) {
            pl.liberer(e.getUUID());
            return null;
        }
        signaler();
        return e;
    }

    /**
     * L'entité {@code entite} n'est plus là (récoltée, retirée, perdue) : sa place repart en attente, tirée ailleurs, prête
     * dans {@code delaiMs} ; une place du staff est retirée de la liste. Rien si cet UUID n'a pas de place.
     */
    public static void liberer(ServerLevel l, UUID entite, long delaiMs) {
        MinecraftServer s = l.getServer();
        Places pl = Places.de(s);
        Places.Place p = pl.liberer(entite);
        if (p == null) {
            return;
        }
        if (p.bonus) {
            pl.retirer(p);
            signaler();
            return;
        }
        p.prete = System.currentTimeMillis() + Math.max(0L, delaiMs);
        ServerLevel m = monde(s);
        if (p.sorte == Reglages.ARBRE) {
            p.variante = especeEquilibree(pl, p, m.getRandom());
        }
        tirer(m, pl, p, Reglages.get());
    }

    /** Une ressource enlevée autrement que par la récolte ({@code /kill}...) : sa place repart après le délai de réapparition. */
    public static void retiree(ServerLevel l, Entity e, int sorte) {
        liberer(l, e.getUUID(), Reglages.get().ressource(sorte).reapparitionMs());
    }

    /** Autant de places de chaque sorte que le barème en veut (les places du staff en plus). */
    static void ajuster(MinecraftServer s) {
        Reglages r = Reglages.get();
        ServerLevel l = monde(s);
        Places pl = Places.de(s);
        long now = System.currentTimeMillis();
        for (int sorte = 0; sorte < Reglages.SORTES.length; sorte++) {
            int voulu = r.ressource(sorte).aLaFois();
            int n = pl.compter(sorte);
            for (; n < voulu; n++) {
                Places.Place p = pl.nouvelle(sorte, sorte == Reglages.ARBRE ? especeEquilibree(pl, null, l.getRandom()) : 0, false);
                p.prete = now;
                tirer(l, pl, p, r);
            }
            if (n > voulu) {
                enlever(l, pl, sorte, n - voulu);
            }
        }
        signaler();
    }

    /** Retire {@code n} places de cette sorte (celles en attente d'abord) ; l'entité d'une place posée chargée part aussi. */
    private static void enlever(ServerLevel l, Places pl, int sorte, int n) {
        for (int passe = 0; passe < 2 && n > 0; passe++) {
            for (Places.Place p : new ArrayList<>(pl.liste())) {
                if (n <= 0) {
                    break;
                }
                if (p.sorte != sorte || p.bonus || (passe == 0) != (p.etat == Places.ATTENTE)) {
                    continue;
                }
                UUID u = p.entite;
                pl.retirer(p);
                n--;
                Entity e = u == null ? null : l.getEntity(u);
                if (e != null) {
                    e.discard();
                }
            }
        }
    }

    /** Espèce du prochain arbre : celle qui manque pour garder moitié bouleaux, moitié épicéas (au hasard à égalité). */
    private static int especeEquilibree(Places pl, @Nullable Places.Place sauf, RandomSource rnd) {
        int bouleaux = 0, epiceas = 0;
        for (Places.Place q : pl.liste()) {
            if (q != sauf && q.sorte == Reglages.ARBRE && !q.bonus) {
                if (q.variante == Arbre.EPICEA) {
                    epiceas++;
                } else {
                    bouleaux++;
                }
            }
        }
        return bouleaux == epiceas ? rnd.nextInt(2) : bouleaux < epiceas ? Arbre.BOULEAU : Arbre.EPICEA;
    }

    // ------------------------------------------------------------------ entités qui entrent dans le monde
    /**
     * Une ressource entre dans un monde (chargement de son coin de carte, ou pose) : seulement celles d'une place posée du
     * monde des ressources, et les arbres abattus jusqu'au départ de leur souche ; les autres n'y entrent pas.
     */
    static void arrivee(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof RessourceEntite r) || !(e.getLevel() instanceof ServerLevel l)) {
            return;
        }
        if (!acceptee(l, r)) {
            e.setCanceled(true);
            HeralizRecolte.LOG.info("[recolte] ressource sans place connue retiree en {} ({})", r.blockPosition().toShortString(),
                    l.dimension().location());
        }
    }

    private static boolean acceptee(ServerLevel l, RessourceEntite r) {
        MinecraftServer s = l.getServer();
        if (l != monde(s)) {
            return false;
        }
        Places.Place p = Places.de(s).parEntite(r.getUUID());
        if (p != null && p.etat == Places.POSEE) {
            return true;
        }
        return r instanceof ArbreServeur a && a.restePourLaSouche(l.getGameTime());
    }
}
