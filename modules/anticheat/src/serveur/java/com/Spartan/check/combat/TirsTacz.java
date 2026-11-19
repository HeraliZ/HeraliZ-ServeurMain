package com.Spartan.check.combat;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.MovePacket;
import com.Spartan.check.movement.MovementEngine;
import com.Spartan.config.SpartanConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;

/**
 * Tirs TaCZ analysés par le serveur (audit du 02/11, point 14 ; A326) : avant, seuls les coups de mêlée étaient
 * contrôlés. Trois indices, réglés dans {@code [tirs]}, toujours des ALERTES au staff (aucune sanction, aucun coup
 * annulé) avec les chiffres :
 * <ul>
 *   <li>taux de tirs à la tête anormal sur les dernières touches de JOUEURS à distance (aimbot) ;</li>
 *   <li>tirs à la tête juste après un grand mouvement de visée en moins de 100 ms (« coup sec » d'un aimbot) ; ignoré en
 *   véhicule, en train et juste après une téléportation (la visée y tourne sans le joueur) ;</li>
 *   <li>tirs refusés par TaCZ lui-même (cadence de l'arme dépassée, horloge des tirs trafiquée : « rapid fire »). 03/11
 *   (« 15 tirs refusés par TaCZ » au minigun, joueur honnête) : la vraie cause était un défaut de TaCZ corrigé dans
 *   Tactique (horloge des tirs recalée, fr.heraliz.tactique.tir.HorlogeTirs) ; ici, refus de cadence et refus d'horloge
 *   sont comptés à part, et l'alerte de cadence demande aussi une part des tirs refusés ({@code cooldownRatio}) : un
 *   « rapid fire » se fait refuser un tir sur deux ou plus, un joueur honnête presque jamais.</li>
 * </ul>
 * Les zombies et les PNJ ne comptent pas, ni les bots de charge. Un résumé de chaque session (tirs, touches, têtes,
 * refus) est écrit dans {@code config/spartan/tirs.log} pour régler les seuils.
 *
 * <p><b>Cadence réelle (19/11)</b>, la seule partie qui agit sur les tirs : TaCZ juge la cadence sur l'heure écrite par le
 * jeu du joueur et n'en vérifie la vraisemblance qu'à 300 ms près en avance, ~400 ms en retard ; un jeu modifié envoyait
 * d'un coup une rafale datée à la cadence de l'arme (jusqu'à 700 ms de tirs dans le même tick). Chaque tir est maintenant
 * daté aussi par son arrivée sur le réseau (observateur de Spartan), et une rafale arrivée d'un coup est ÉTALÉE à la
 * cadence de l'arme ({@link CadenceTirs}) : le tir est mis de côté et joué par TaCZ au bon moment, rien n'est perdu.
 * Tolérés sans rien faire : les à-coups du réseau ({@code rafaleToleranceMs}) et les tirs groupés par une coupure du réseau
 * (silence mesuré). Seul un tir qui devrait attendre plus de {@code rafaleAttenteMaxMs} (cadence soutenue plus rapide que
 * l'arme) est refusé. Alerte « Tirs » après {@code rafaleAlerte} tirs étalés ou refusés en 60 s.
 *
 * <p>TaCZ est lu par ses événements, sans dépendance de compilation (comme Noise) ; les refus viennent d'un Mixin
 * facultatif sur TaCZ ({@code TaczTirMixin}), la cadence réelle d'un autre ({@code TaczCadenceMixin}). TaCZ absent ou d'une
 * autre version : rien ne se passe, le journal le dit.
 */
public class TirsTacz extends MovementCheck {
    private static final Map<UUID, Etat> ETATS = new ConcurrentHashMap<>();
    private static TirsTacz instance;
    private static boolean branche;
    private static Field champTireur;
    /** Cadence réelle : retard apparent des tirs comparé sur cette durée (ms), assez pour une rafale. */
    static final double FENETRE_MS = 600;
    /** Cadence réelle : fenêtre de l'alerte (ms). */
    private static final long ALERTE_MS = 60_000L;
    /**
     * Cadence réelle : étalement (ms) au-delà duquel le tir compte pour l'alerte, plus la tolérance : retard le plus grand
     * que TaCZ accepte (~400 ms) + 50 ms. Un joueur honnête n'y arrive jamais.
     */
    private static final double ETALEMENT_HONNETE_MAX = 450;
    /** Visée forcée : la visée du tir (B) arrive au plus autant avant le tir (même tick du jeu). */
    private static final long VISEE_AVANT_NANOS = 80_000_000L;
    /** Visée forcée : la visée qui suit le tir (C) arrive au plus autant après lui (tick suivant du jeu). */
    private static final long VISEE_APRES_NANOS = 120_000_000L;
    /** Visée forcée : joueurs regardés jusqu'à cette distance (blocs). */
    private static final double VISEE_PORTEE = 128;
    /** Visée forcée : la visée du tir est sur le joueur (degrés au-delà de sa taille apparente). */
    private static final double VISEE_SUR_CIBLE = 2;
    /** Visée forcée : les visées d'avant et d'après sont loin de lui (degrés). */
    private static final double VISEE_HORS_CIBLE = 8;
    /** Visée forcée : fenêtre de l'alerte (ms). */
    private static final long VISEE_ALERTE_MS = 600_000L;
    /** Joueurs qui ont des tirs mis de côté (presque toujours vide). Fil du serveur. */
    private static final ArrayDeque<Etat> A_JOUER = new ArrayDeque<>();
    /** Un tir mis de côté est en train d'être joué : la cadence réelle ne le juge pas une seconde fois. */
    private static boolean enJeu;
    /** Arrivée du tir en cours (réutilisé, fil du serveur). */
    private static final long[] ARRIVEE = new long[2];
    private static Field champDonnees;
    private static Field champBase;
    private static MethodHandle operateur;
    private static MethodHandle tirer;
    private static Object refusCadence;
    private static Object misDeCote;
    private static long reconnues;
    private static long inconnues;
    private static boolean signale;
    /** Diagnostic des Mixins (journal une fois) : tirs et touches de joueurs vus, appels de la cadence réelle. */
    private static long tirsVus;
    private static long touchesVues;
    private static long cadencesVues;

    /** Rotations récentes du joueur (paquets de mouvement) et dernier tir. */
    private static final class Etat {
        final UUID id;
        final String nom;
        final StatsTirs stats;
        final long[] nanos = new long[8];
        final float[] yaw = new float[8];
        final float[] pitch = new float[8];
        int pos;
        boolean dernierTirSec;
        long dernierTirNanos;
        // cadence réelle (19/11)
        final CadenceTirs cadence = new CadenceTirs();
        long base = Long.MIN_VALUE;
        final ArrayDeque<Attente> attente = new ArrayDeque<>();
        boolean aJouer;
        final com.Spartan.check.Compteur etales = new com.Spartan.check.Compteur(64);
        double pireRetard;
        int etalesSession;
        int refusesSession;
        // visée forcée (19/11) : visées A et B du dernier tir, en attente de la visée C qui le suit
        boolean viseeEnAttente;
        long viseeTirNanos;
        float lacetA;
        float tangageA;
        float lacetB;
        float tangageB;
        final com.Spartan.check.Compteur viseesForcees = new com.Spartan.check.Compteur(32);
        int viseesSession;

        Etat(UUID id, String nom) {
            this.id = id;
            this.nom = nom;
            this.stats = new StatsTirs(SpartanConfig.TIRS_HISTORIQUE.get());
        }
    }

    /**
     * Tir mis de côté (cadence réelle) : ce que le jeu a envoyé, l'heure où il doit partir ({@code depart}, nanos) et l'heure
     * où TaCZ l'a reçu ({@code recuMs}) : au départ, son heure est recomptée d'autant pour que TaCZ le juge comme à sa
     * réception (même écart avec son horloge, même écart avec le tir précédent ou plus).
     */
    private record Attente(Supplier<Float> tangage, Supplier<Float> lacet, long heure, float charge, long depart,
                           long recuMs, ResourceKey<Level> monde) {
    }

    public TirsTacz() {
        super("Tirs", "tir"); // catégorie sans correction : jamais de coup annulé ni de renvoi
        instance = this;
    }

    private static boolean ignore(Entity e) {
        return !(e instanceof ServerPlayer p) || p instanceof FakePlayer || p.getTags().contains("heraliz_bot");
    }

    private static Etat etat(ServerPlayer p) {
        return ETATS.computeIfAbsent(p.getUUID(), u -> new Etat(u, p.getName().getString()));
    }

    @Override
    public void onMovePacket(ServerPlayer player, MovePacket packet) {
        if (!packet.hasRot() || !SpartanConfig.TIRS_ENABLED.get()) {
            return;
        }
        Etat e = ETATS.get(player.getUUID());
        if (e == null) {
            return; // jamais tiré : rien à garder
        }
        if (e.viseeEnAttente) {
            // visée forcée (19/11) : première visée après le tir (C), comparée à celles d'avant (A, B)
            e.viseeEnAttente = false;
            if (packet.nanos() - e.viseeTirNanos <= VISEE_APRES_NANOS
                    && ViseeForcee.allerRetour(e.lacetA, e.tangageA, e.lacetB, e.tangageB, packet.yRot(), packet.xRot(),
                    SpartanConfig.TIRS_VISEE_COUP.get(), SpartanConfig.TIRS_VISEE_RETOUR.get())) {
                viseeForcee(player, e, packet.yRot(), packet.xRot());
            }
        }
        e.nanos[e.pos] = packet.nanos();
        e.yaw[e.pos] = packet.yRot();
        e.pitch[e.pos] = packet.xRot();
        e.pos = (e.pos + 1) % e.nanos.length;
    }

    /**
     * Tir de TaCZ à sa place dans le flux (19/11, visée forcée) : les deux dernières visées reçues avant lui (A, B) sont
     * gardées ; la visée suivante (C) décide ({@link #onMovePacket}). Rien si B n'est pas arrivée juste avant le tir (le
     * joueur ne bougeait pas la souris), en véhicule, juste après une téléportation, en créatif ou spectateur.
     */
    @Override
    public void onShot(ServerPlayer player, long nanos) {
        if (!SpartanConfig.TIRS_ENABLED.get() || !SpartanConfig.TIRS_VISEE_FORCEE.get() || ignore(player)
                || player.isPassenger() || player.isCreative() || player.isSpectator()
                || CheckManager.isInGrace(player.getUUID())) {
            return;
        }
        Etat e = etat(player);
        int n = e.nanos.length;
        int b = (e.pos + n - 1) % n;
        int a = (e.pos + n - 2) % n;
        if (e.nanos[a] == 0 || e.nanos[b] == 0 || nanos - e.nanos[b] > VISEE_AVANT_NANOS) {
            e.viseeEnAttente = false;
            return;
        }
        e.viseeEnAttente = true;
        e.viseeTirNanos = nanos;
        e.lacetA = e.yaw[a];
        e.tangageA = e.pitch[a];
        e.lacetB = e.yaw[b];
        e.tangageB = e.pitch[b];
    }

    /**
     * Aller-retour de la visée autour d'un tir : confirmé seulement si la visée du tir (B) était sur un joueur que les
     * visées d'avant et d'après (A, C) ne visaient pas. Compté, noté dans tirs.log ; alerte au N-ième en 10 min.
     */
    private static void viseeForcee(ServerPlayer p, Etat e, float lacetC, float tangageC) {
        Vec3 oeil = p.getEyePosition();
        ServerPlayer vise = null;
        double distance = 0;
        for (ServerPlayer t : p.serverLevel().players()) {
            if (t == p || t.isSpectator() || !t.isAlive() || t.distanceToSqr(p) > VISEE_PORTEE * VISEE_PORTEE) {
                continue;
            }
            AABB b = t.getBoundingBox();
            if (ViseeForcee.ecartBoite(oeil.x, oeil.y, oeil.z, e.lacetB, e.tangageB, b.minX, b.minY, b.minZ, b.maxX, b.maxY,
                    b.maxZ) <= VISEE_SUR_CIBLE
                    && ViseeForcee.ecartBoite(oeil.x, oeil.y, oeil.z, e.lacetA, e.tangageA, b.minX, b.minY, b.minZ, b.maxX,
                    b.maxY, b.maxZ) >= VISEE_HORS_CIBLE
                    && ViseeForcee.ecartBoite(oeil.x, oeil.y, oeil.z, lacetC, tangageC, b.minX, b.minY, b.minZ, b.maxX,
                    b.maxY, b.maxZ) >= VISEE_HORS_CIBLE) {
                vise = t;
                distance = Math.sqrt(t.distanceToSqr(p));
                break;
            }
        }
        if (vise == null) {
            return;
        }
        e.viseesSession++;
        double saut = ViseeForcee.ecart(e.lacetA, e.tangageA, e.lacetB, e.tangageB);
        double retour = ViseeForcee.ecart(e.lacetA, e.tangageA, lacetC, tangageC);
        String detail = String.format("visée tournée de %.0f° sur %s (%.0f blocs) pour le tir, puis revenue à %.1f° de la visée"
                + " d'avant au paquet suivant", saut, vise.getName().getString(), distance, retour);
        if (SpartanConfig.TIRS_JOURNAL.get()) {
            AlertManager.journal("tirs.log", e.nom + " (" + e.id + ") : visée forcée ? " + detail);
        }
        int n = e.viseesForcees.ajouter(System.currentTimeMillis(), VISEE_ALERTE_MS);
        if (n >= SpartanConfig.TIRS_VISEE_ALERTE.get()) {
            e.viseesForcees.vider();
            instance.flag(p, n + " tirs en 10 min avec la visée tournée sur un joueur seulement le temps du tir puis rendue"
                    + " (aimbot discret, « silent aim ») ; le dernier : " + detail + ". Session : " + e.viseesSession);
        }
    }

    /** Plus grand mouvement de visée (degrés) entre la visée actuelle et celles des 100 dernières ms. */
    private static float mouvementRecent(Etat e, ServerPlayer p, long maintenant) {
        float y = p.getYRot();
        float x = p.getXRot();
        float max = 0;
        for (int i = 0; i < e.nanos.length; i++) {
            if (e.nanos[i] == 0 || maintenant - e.nanos[i] > 100_000_000L) {
                continue;
            }
            float dy = Mth.wrapDegrees(y - e.yaw[i]);
            float dx = x - e.pitch[i];
            max = Math.max(max, (float) Math.sqrt(dy * dy + dx * dx));
        }
        return max;
    }

    // ------------------------------------------------------------------ événements de TaCZ

    private static void tir(ServerPlayer p) {
        if (!SpartanConfig.TIRS_ENABLED.get()) {
            return;
        }
        if (++tirsVus == 300 && cadencesVues == 0 && tirer != null && SpartanConfig.TIRS_CADENCE.get()) {
            Spartan.LOGGER.warn("[Spartan] Cadence réelle des tirs inactive : 300 tirs de joueurs sans passer par Spartan "
                    + "(Mixin TaczCadenceMixin non appliqué : TaCZ d'une autre version ?). Les tirs sont jugés par TaCZ seul.");
        }
        Etat e = etat(p);
        e.stats.tir(System.currentTimeMillis());
        long now = System.nanoTime();
        boolean neutre = p.isPassenger() || CheckManager.isInGrace(p.getUUID());
        e.dernierTirSec = !neutre && mouvementRecent(e, p, now) >= SpartanConfig.TIRS_COUP_SEC_DEGRES.get();
        e.dernierTirNanos = now;
    }

    private static void touche(ServerPlayer tireur, Player cible, boolean tete) {
        if (!SpartanConfig.TIRS_ENABLED.get() || cible == tireur) {
            return;
        }
        if (++touchesVues == 50 && com.Spartan.check.movement.LatenceCheck.appelsRecul() == 0
                && SpartanConfig.TIRS_RECUL_MESURE.get()) {
            Spartan.LOGGER.warn("[Spartan] Recul des hitbox au ping mesuré inactif : 50 joueurs touchés sans que TaCZ lise la "
                    + "latence de Spartan (Mixin TaczReculMixin non appliqué : TaCZ d'une autre version ?).");
        }
        Etat e = etat(tireur);
        boolean sec = tete && e.dernierTirSec && System.nanoTime() - e.dernierTirNanos < 2_000_000_000L;
        double distance = tireur.distanceTo(cible);
        e.stats.touche(tete, distance, sec);
        StatsTirs s = e.stats;
        double distMin = SpartanConfig.TIRS_DISTANCE_MIN.get();
        int loin = s.touchesLoin(distMin);
        int tetes = s.tetesLoin(distMin);
        if (loin >= SpartanConfig.TIRS_TOUCHES_MIN.get() && tetes >= loin * SpartanConfig.TIRS_RATIO_TETE.get()) {
            instance.flag(tireur, String.format("%d tirs à la tête sur les %d dernières touches de joueurs à plus de %.0f blocs"
                            + " (%d %%, seuil %d %%) : aimbot ? Session : %s", tetes, loin, distMin, Math.round(100.0 * tetes / loin),
                    Math.round(100 * SpartanConfig.TIRS_RATIO_TETE.get()), s.resume()));
            s.oublierTouches();
            return;
        }
        int secs = s.coupsSecs();
        if (secs >= SpartanConfig.TIRS_COUPS_SECS_MAX.get()) {
            instance.flag(tireur, String.format("%d tirs à la tête juste après un mouvement de visée de plus de %.0f° en moins"
                            + " de 100 ms, sur les %d dernières touches de joueurs : aimbot ? Session : %s", secs,
                    SpartanConfig.TIRS_COUP_SEC_DEGRES.get(), s.touches(), s.resume()));
            s.oublierTouches();
        }
    }

    /** Résultat d'un tir chez TaCZ (TaczTirMixin, fil du serveur). */
    public static void resultat(Object tir, Object resultat) {
        if (!(resultat instanceof Enum<?> r) || !SpartanConfig.TIRS_ENABLED.get() || instance == null) {
            return;
        }
        String nom = r.name();
        boolean cadence = nom.equals("COOL_DOWN");
        if (!cadence && !nom.equals("NETWORK_FAIL")) {
            return;
        }
        Object tireur = tireur(tir);
        if (!(tireur instanceof Entity) || ignore((Entity) tireur)) {
            return;
        }
        ServerPlayer p = (ServerPlayer) tireur;
        Etat e = etat(p);
        long fenetre = SpartanConfig.TIRS_REFUS_SECONDES.get() * 1000L;
        long maintenant = System.currentTimeMillis();
        int n = e.stats.refus(cadence, maintenant, fenetre);
        if (cadence) {
            int acceptes = e.stats.tirsRecents(maintenant, fenetre);
            double part = (double) n / Math.max(1, n + acceptes);
            if (n >= SpartanConfig.TIRS_REFUS_CADENCE.get() && part >= SpartanConfig.TIRS_REFUS_PART.get()) {
                instance.flag(p, String.format("%d tirs refusés par TaCZ pour cadence en %d s (%d %% des tirs, %d acceptés) :"
                        + " cadence plus rapide que celle de l'arme (rapid fire) ?", n, fenetre / 1000, Math.round(100 * part), acceptes));
                e.stats.oublierRefus(true);
            }
        } else if (n >= SpartanConfig.TIRS_REFUS_HORLOGE.get()) {
            instance.flag(p, String.format("%d tirs refusés par TaCZ pour l'horloge en %d s : horloge des tirs trafiquée ?"
                    + " (aussi en cas de très mauvaise connexion)", n, fenetre / 1000));
            e.stats.oublierRefus(false);
        }
    }

    /** Tireur d'un LivingEntityShoot de TaCZ (champ {@code shooter}), ou null. */
    private static Object tireur(Object tir) {
        try {
            if (champTireur == null) {
                Field f = tir.getClass().getDeclaredField("shooter");
                f.setAccessible(true);
                champTireur = f;
            }
            return champTireur.get(tir);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Base de l'horloge des tirs de TaCZ pour ce tireur ({@code data.baseTimestamp}, heure du serveur au dernier échange
     * d'horloge), ou {@link Long#MIN_VALUE} si illisible.
     */
    private static long base(Object tir) {
        try {
            if (champDonnees == null) {
                Field f = tir.getClass().getDeclaredField("data");
                f.setAccessible(true);
                champDonnees = f;
            }
            Object donnees = champDonnees.get(tir);
            if (donnees == null) {
                return Long.MIN_VALUE;
            }
            if (champBase == null) {
                champBase = donnees.getClass().getField("baseTimestamp");
            }
            return champBase.getLong(donnees);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return Long.MIN_VALUE;
        }
    }

    // ------------------------------------------------------------------ cadence réelle (19/11)

    /**
     * Juste avant que TaCZ juge la cadence d'un tir reçu d'un joueur (TaczCadenceMixin, fil du serveur).
     *
     * @return null : TaCZ juge le tir comme d'habitude ; sinon le résultat à rendre à la place (tir mis de côté, joué plus
     *         tard à la cadence de l'arme ; ou refusé pour cadence)
     */
    public static Object cadence(Object tir, Supplier<Float> tangage, Supplier<Float> lacet, long heure, float charge) {
        cadencesVues++;
        if (enJeu || instance == null || tirer == null || !SpartanConfig.TIRS_ENABLED.get() || !SpartanConfig.TIRS_CADENCE.get()) {
            return null;
        }
        if (!(tireur(tir) instanceof ServerPlayer p) || ignore(p) || p.isCreative() || p.isSpectator()
                || !p.server.isSameThread()) {
            return null;
        }
        if (!MovementEngine.arriveeTir(p.getUUID(), heure, ARRIVEE)) {
            if (++inconnues >= 200 && reconnues == 0 && !signale) {
                signale = true;
                Spartan.LOGGER.warn("[Spartan] Cadence réelle des tirs inactive : l'arrivée des tirs de TaCZ n'est jamais "
                        + "reconnue sur le réseau (TaCZ d'une autre version que 1.1.8 ?). Les tirs sont jugés par TaCZ seul.");
            }
            return null; // arrivée inconnue : TaCZ juge seul, comme avant
        }
        reconnues++;
        long maintenantMs = System.currentTimeMillis();
        long base = base(tir);
        if (base != Long.MIN_VALUE) {
            long ecart = maintenantMs - base - heure;
            if (ecart < -300 || ecart > 400) {
                return null; // TaCZ le refuse lui-même et recale son horloge (NETWORK_FAIL) : rien à ajouter
            }
        }
        Etat e = etat(p);
        if (base != e.base) {
            e.base = base; // horloge des tirs de TaCZ recalée (connexion, changement de monde, coupure) : on repart de zéro
            e.cadence.oublier();
        }
        double etaler = e.cadence.tir(ARRIVEE[0] / 1e6, heure, ARRIVEE[1] / 1e6,
                SpartanConfig.TIRS_RAFALE_TOLERANCE.get(), FENETRE_MS, SpartanConfig.TIRS_RAFALE_ATTENTE.get());
        if (etaler == CadenceTirs.REFUSE) {
            e.refusesSession++;
            compterEtalement(p, e, -1);
            return refusCadence; // cadence soutenue plus rapide que l'arme : comme un refus de cadence de TaCZ
        }
        long maintenant = System.nanoTime();
        long depart = ARRIVEE[0] + Math.round(etaler * 1e6);
        if (!e.attente.isEmpty()) {
            depart = Math.max(depart, e.attente.peekLast().depart()); // l'ordre des tirs est gardé
        } else if (depart <= maintenant) {
            return null; // rien à attendre (ou déjà attendu : serveur en retard sur le réseau)
        }
        e.attente.addLast(new Attente(tangage, lacet, heure, charge, depart, maintenantMs, p.level().dimension()));
        if (!e.aJouer) {
            e.aJouer = true;
            A_JOUER.addLast(e);
        }
        e.etalesSession++;
        // seul un étalement qu'aucun jeu honnête ne peut avoir compte pour l'alerte : TaCZ refuse lui-même un tir en
        // retard de plus de ~400 ms (NETWORK_FAIL), donc un réseau qui se vide ne fait jamais étaler plus de 400 ms moins
        // la tolérance (marge de 50 ms en plus)
        if (etaler > ETALEMENT_HONNETE_MAX - SpartanConfig.TIRS_RAFALE_TOLERANCE.get()) {
            compterEtalement(p, e, etaler);
        }
        return misDeCote;
    }

    /** Un tir étalé ({@code retardMs}) ou refusé (-1) : alerte au N-ième en 60 s. */
    private static void compterEtalement(ServerPlayer p, Etat e, double retardMs) {
        long maintenant = System.currentTimeMillis();
        if (retardMs > e.pireRetard) {
            e.pireRetard = retardMs;
        }
        int n = e.etales.ajouter(maintenant, ALERTE_MS);
        if (n >= SpartanConfig.TIRS_RAFALE_ALERTE.get()) {
            instance.flag(p, String.format("%d tirs arrivés d'un coup alors que leurs heures suivent la cadence de l'arme, "
                            + "étalés de plus de %.0f ms (impossible pour un jeu honnête) ou refusés, en 60 s (jusqu'à %.0f ms "
                            + "d'étalement ; avance des heures annoncées sur leur arrivée : %.0f ms, tolérance %d ms) : rafale "
                            + "« rapid fire » par heures de tir trafiquées ? Session : %d étalé(s), %d refusé(s)", n,
                    ETALEMENT_HONNETE_MAX - SpartanConfig.TIRS_RAFALE_TOLERANCE.get(), e.pireRetard,
                    e.cadence.derniereDette(), SpartanConfig.TIRS_RAFALE_TOLERANCE.get(), e.etalesSession, e.refusesSession));
            e.etales.vider();
            e.pireRetard = 0;
        }
    }

    /** Fin de chaque tick serveur : tirs mis de côté dont l'heure est venue, joués par TaCZ (rien à faire presque toujours). */
    public static void jouerEtales(MinecraftServer server) {
        if (A_JOUER.isEmpty()) {
            return;
        }
        long maintenant = System.nanoTime();
        Iterator<Etat> it = A_JOUER.iterator();
        while (it.hasNext()) {
            Etat e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.id);
            while (!e.attente.isEmpty() && e.attente.peekFirst().depart() <= maintenant) {
                Attente a = e.attente.pollFirst();
                if (p != null && p.isAlive() && !p.hasDisconnected() && p.level().dimension().equals(a.monde())) {
                    jouer(p, a);
                }
            }
            if (p == null || p.hasDisconnected() || e.attente.isEmpty()) {
                e.attente.clear();
                e.aJouer = false;
                it.remove();
            }
        }
    }

    /** Fait tirer TaCZ avec ce qui avait été reçu, heure recomptée du temps passé de côté. */
    private static void jouer(ServerPlayer p, Attente a) {
        long heure = a.heure() + Math.max(0L, System.currentTimeMillis() - a.recuMs());
        enJeu = true;
        try {
            Object op = operateur.invoke(p);
            Object resultat = tirer.invoke(op, a.tangage(), a.lacet(), heure, a.charge());
            if (resultat == null) {
                Spartan.LOGGER.debug("[Spartan] tir étalé de {} sans résultat", p.getName().getString());
            }
        } catch (Throwable t) {
            Spartan.LOGGER.warn("[Spartan] Tir étalé de {} non joué ({}).", p.getName().getString(), t.toString());
        } finally {
            enJeu = false;
        }
    }

    /** /spartan status : la cadence réelle pour ce joueur. */
    public static String describe(UUID uuid) {
        if (!SpartanConfig.TIRS_CADENCE.get()) {
            return "tirs : cadence réelle coupée ([tirs] cadenceReelle)";
        }
        Etat e = ETATS.get(uuid);
        String global = tirer == null ? " (TaCZ non relié)" : reconnues == 0 && inconnues > 0 ? " (arrivées des tirs non reconnues)" : "";
        return e == null ? "tirs : aucun tir depuis la connexion" + global
                : "tirs : " + e.stats.resume() + " ; cadence réelle : " + e.etalesSession + " tir(s) étalé(s), "
                        + e.refusesSession + " refusé(s), " + e.attente.size() + " en attente" + global;
    }

    /** Démarrage du serveur : branche les événements de TaCZ (une fois). */
    @SuppressWarnings("unchecked")
    public static void brancher() {
        if (branche || !ModList.get().isLoaded("tacz")) {
            return;
        }
        branche = true;
        try {
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Class<? extends Event> tirEv = (Class<? extends Event>) Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            MethodHandle tireur = l.findVirtual(tirEv, "getShooter", MethodType.methodType(LivingEntity.class));
            MethodHandle coteTir = l.findVirtual(tirEv, "getLogicalSide", MethodType.methodType(LogicalSide.class));
            Class<? extends Event> toucheEv = (Class<? extends Event>) Class.forName(
                    "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Post");
            MethodHandle attaquant = l.findVirtual(toucheEv, "getAttacker", MethodType.methodType(LivingEntity.class));
            MethodHandle cible = l.findVirtual(toucheEv, "getHurtEntity", MethodType.methodType(Entity.class));
            MethodHandle tete = l.findVirtual(toucheEv, "isHeadShot", MethodType.methodType(boolean.class));
            MethodHandle coteTouche = l.findVirtual(toucheEv, "getLogicalSide", MethodType.methodType(LogicalSide.class));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) tirEv, ev -> {
                try {
                    if (coteTir.invoke(ev) == LogicalSide.SERVER && tireur.invoke(ev) instanceof ServerPlayer p && !ignore(p)) {
                        tir(p);
                    }
                } catch (Throwable t) {
                    // tir illisible : ignoré
                }
            });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class<Event>) toucheEv, ev -> {
                try {
                    if (coteTouche.invoke(ev) == LogicalSide.SERVER && attaquant.invoke(ev) instanceof ServerPlayer p && !ignore(p)
                            && cible.invoke(ev) instanceof Player c && !ignore(c)) {
                        touche(p, c, (boolean) tete.invoke(ev));
                    }
                } catch (Throwable t) {
                    // touche illisible : ignorée
                }
            });
            Spartan.LOGGER.info("[Spartan] Tirs TaCZ analysés (taux de tirs à la tête, coups secs, tirs refusés par TaCZ).");
        } catch (Throwable t) {
            Spartan.LOGGER.warn("[Spartan] Tirs TaCZ non analysés : TaCZ non reconnu ({}).", t.toString());
        }
        brancherCadence();
    }

    /** Cadence réelle (19/11) : de quoi faire tirer TaCZ plus tard (IGunOperator.shoot) et ses résultats de tir. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void brancherCadence() {
        try {
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Class<?> resultat = Class.forName("com.tacz.guns.api.entity.ShootResult");
            MethodHandle de = l.findStatic(op, "fromLivingEntity", MethodType.methodType(op, LivingEntity.class));
            MethodHandle tir = l.findVirtual(op, "shoot",
                    MethodType.methodType(resultat, Supplier.class, Supplier.class, long.class, float.class));
            refusCadence = Enum.valueOf((Class) resultat, "COOL_DOWN");
            misDeCote = Enum.valueOf((Class) resultat, "FORGE_EVENT_CANCEL");
            operateur = de;
            tirer = tir; // en dernier : la cadence réelle ne s'applique qu'avec tout ce qu'il faut
            Spartan.LOGGER.info("[Spartan] Cadence réelle des tirs TaCZ active (rafales envoyées d'un coup étalées à la cadence de l'arme).");
        } catch (Throwable t) {
            tirer = null;
            Spartan.LOGGER.warn("[Spartan] Cadence réelle des tirs inactive : TaCZ non reconnu ({}).", t.toString());
        }
    }

    /** Déconnexion : une ligne dans tirs.log si le joueur a tiré. */
    @Override
    public void clear(UUID uuid) {
        Etat e = ETATS.remove(uuid);
        if (e != null) {
            e.attente.clear(); // tirs mis de côté : perdus avec la déconnexion (retiré de A_JOUER au tick suivant)
        }
        if (e != null && e.stats.tirs() > 0 && SpartanConfig.TIRS_JOURNAL.get()) {
            String cadence = e.etalesSession + e.refusesSession == 0 ? ""
                    : ", cadence réelle : " + e.etalesSession + " tir(s) étalé(s), " + e.refusesSession + " refusé(s)";
            AlertManager.journal("tirs.log", e.nom + " (" + uuid + ") : " + e.stats.resume() + cadence);
        }
    }

    /** Une grâce (téléportation…) ne remet pas les statistiques à zéro. */
    @Override
    public void onGrace(UUID uuid) {
    }
}
