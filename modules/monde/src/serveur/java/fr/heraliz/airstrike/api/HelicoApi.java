package fr.heraliz.airstrike.api;

import fr.heraliz.airstrike.entity.HelicopterServeur;
import fr.heraliz.airstrike.entity.HelicopterWreckServeur;
import fr.heraliz.airstrike.helico.Veille;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * API de l'hélicoptère militaire pour HeraliZ Événements (côté serveur, fil serveur seulement ; docs/EVENEMENTS.md §4.4).
 * Le module des événements décide QUAND il part (cycle, conditions, butin, annonces) ; ce module-ci le fait voler, tirer,
 * tomber et brûler, et le prévient de chaque étape par un {@link Suivi}. Ne jamais renommer ces classes ni changer ces
 * signatures sans changer le module des événements.
 */
public final class HelicoApi {
    private HelicoApi() {
    }

    /** Réglages d'un vol (lus dans {@code config/heraliz_evenements/helico.json} par le module des événements). */
    public static final class Reglages {
        /** Points de vie. */
        public float pv = 600f;
        /** Explosions (grenades, roquettes…) et poutre de queue. */
        public float multExplosifs = 2f, multQueue = 1.25f;
        /** Sans coup reçu depuis ce temps (s), il regagne ce pourcentage de ses PV max par seconde, jusqu'au maximum. */
        public int regenApresS = 30;
        public float regenPctParS = 1.5f;
        /** Vitesse de croisière (blocs par tick) ; en combat × 0,75. */
        public double vitesse = 0.45;
        /** Altitude au-dessus du terrain (blocs), tirée entre les deux à chaque point de patrouille. */
        public int altitudeMin = 30, altitudeMax = 45;
        /**
         * Patrouille (secondes, tirée entre les deux à chaque hélicoptère) ; attaqué, il ne part qu'après
         * {@code resteApresAttaqueS} sans coup reçu ; au bout de {@code volMaxS}, il part quoi qu'il arrive.
         */
        public int patrouilleMinS = 300, patrouilleMaxS = 600, resteApresAttaqueS = 60, volMaxS = 1500;
        /**
         * Portée de tir et de poursuite (blocs). Le serveur n'envoie les entités des mods qu'à 64 blocs des joueurs
         * (spigot.yml, entity-tracking-range.other) : au-delà, personne ne le voit ni ne peut le viser.
         */
        public int porteeTir = 60, poursuite = 96;
        /** Rayon et hauteur du cercle autour de la cible. */
        public int rayonCercle = 40, altitudeCombat = 22;
        /** Dégâts d'un coup du minigun, coups par rafale, pause entre deux rafales (s), alerte avant la rafale (ticks). */
        public float degatsCoup = 2.5f;
        public int coupsRafale = 8, pauseMinS = 3, pauseMaxS = 5, alerteTicks = 24;
        /** Un agresseur sans nouveau tir depuis ce temps est oublié (secondes). */
        public int oubliAgresseursS = 60;
        /** Durée du feu de l'épave (secondes, tirée entre les deux) ; épave, feu et fumée retirés ce temps après le feu (s). */
        public int feuMinS = 120, feuMaxS = 210, retraitS = 900;
        /** Point de crash : jamais à moins de tant de blocs d'une base de clan (CRac) ni du point d'apparition de la carte. */
        public int loinDesBases = 24, loinDuSpawn = 64;
        /** Recherche du point de crash : rayon fin (pas de 4 blocs) puis large (pas de 8). */
        public int rechercheProche = 96, rechercheLoin = 200;
        /** Impact : dégâts au plus près, rayon (blocs). */
        public float crashDegats = 8f;
        public int crashRayon = 6;

        /** Plus longue vie possible d'un hélicoptère et de son épave (secondes), marge comprise : garde-fou en heure réelle. */
        public int vieMaxS() {
            return Veille.ATTENTE_DEPART_S + volMaxS + Veille.SORTIE_MAX_S + 10 + feuMaxS + retraitS + 300;
        }
    }

    /** Dégâts d'un joueur, avec le clan qu'il avait AU MOMENT des coups (null : sans clan). */
    public record Contribution(UUID joueur, String nom, @Nullable UUID clan, double degats) {
    }

    /**
     * Hélicoptère abattu : contributions, point de crash (null s'il a explosé en l'air faute de point valide : pas d'épave),
     * sol où poser la boîte noire dans ce cas (dernier sol valide survolé, sinon le sol le plus proche ; null : aucun),
     * et « essai » (un joueur en créatif ou en spectateur l'a touché : boîte noire sans points).
     */
    public record Abattu(String id, Vec3 ou, @Nullable BlockPos crash, @Nullable BlockPos sol, boolean essai,
                         List<Contribution> contributions) {
        public boolean enLAir() {
            return crash == null;
        }
    }

    /** État d'un hélicoptère pour le staff ({@code /event helico info}). */
    public record Etat(String phase, float pv, float pvMax, Vec3 position, boolean essai, List<String> agresseurs,
                       List<Contribution> contributions) {
    }

    /** Ce que le module des événements apprend, dans l'ordre. Appelé sur le fil serveur. */
    public interface Suivi {
        /** Abattu. */
        default void abattu(Abattu a) {
        }

        /** Écrasé au sol en {@code ou} (annonce du secteur). */
        default void impact(String id, Vec3 ou) {
        }

        /** Le feu de l'épave est éteint : place au butin, près de {@code ou} (sol de l'épave). */
        default void feuEteint(String id, BlockPos ou) {
        }

        /** Fin de la patrouille : il part vers le bord le plus proche. */
        default void depart(String id) {
        }

        /** Plus rien de cet hélicoptère dans le monde : « parti », « epave_retiree », « en_l_air », « perdu », « annule ». */
        default void fini(String id, String cause) {
        }
    }

    /**
     * Lance un hélicoptère {@code id} (« helico#12 ») sur la carte (overworld) : depuis {@code depart} (staff), sinon depuis
     * le bord de la carte le plus proche d'un joueur tiré au hasard, à 56 blocs au moins en dedans. Rien n'est chargé sur
     * le fil du serveur : le tronçon d'arrivée est demandé par un ticket et l'hélicoptère apparaît quand il est prêt (au
     * plus {@code Veille.ATTENTE_DEPART_S} secondes, sinon {@code fini("perdu")}). {@code clanDe} : clan d'un joueur (null :
     * aucun). Rend le point d'arrivée, ou null si c'est impossible (autre monde, même numéro déjà en cours).
     */
    @Nullable
    public static Vec3 lancer(ServerLevel level, String id, Reglages reglages, @Nullable Vec3 depart,
                              @Nullable Function<UUID, UUID> clanDe, Suivi suivi) {
        return Veille.lancer(level, id, reglages, depart, clanDe, suivi);
    }

    /** Retire l'hélicoptère {@code id} (en attente, en vol ou en chute) et son épave, sans rien donner. */
    public static boolean annuler(String id) {
        boolean d = Veille.annulerDepart(id);
        boolean h = HelicopterServeur.retirer(id);
        boolean e = HelicopterWreckServeur.retirer(id);
        return d || h || e;
    }

    /** L'hélicoptère {@code id} attend son tronçon, vole ou tombe, ou son épave est encore là. */
    public static boolean enCours(String id) {
        return Veille.enAttente(id) || HelicopterServeur.enVol(id) != null || HelicopterWreckServeur.epave(id) != null;
    }

    /** État de l'hélicoptère en vol (null s'il n'y en a pas). */
    @Nullable
    public static Etat etat(String id) {
        HelicopterServeur h = HelicopterServeur.enVol(id);
        return h == null ? null : h.etat();
    }
}
