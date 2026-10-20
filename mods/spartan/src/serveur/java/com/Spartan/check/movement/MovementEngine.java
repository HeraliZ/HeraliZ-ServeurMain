package com.Spartan.check.movement;

import com.Spartan.Spartan;
import com.Spartan.check.CheckManager;
import com.Spartan.check.Mitigation;
import com.Spartan.check.movement.model.Physics;
import com.Spartan.config.SpartanConfig;
import com.Spartan.evidence.EvidenceRecorder;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

/**
 * Moteur de mouvement : rejoue, à chaque tick serveur, les paquets que le client
 * a réellement envoyés (observés par PacketObserver) et alimente les détections.
 *
 * Pourquoi par paquets et pas par tick :
 * - chaque position du client est vue, y compris celles que vanilla rejette et
 *   les rafales après un lag (une position = un tick du client) ;
 * - les téléportations sont suivies exactement : envoi (avec sa destination
 *   absolue), puis confirmation par identifiant. Seule une confirmation d'un
 *   identifiant réellement envoyé repositionne la référence, sur la destination
 *   choisie par le serveur : provoquer une correction vanilla ou envoyer de
 *   fausses confirmations ne fait ni sauter un déplacement, ni effacer les preuves ;
 * - la cadence des paquets (Timer), les positions retenues (blink) et les
 *   téléportations jamais confirmées deviennent mesurables.
 */
public final class MovementEngine {
    /** Activité du client sans aucune position depuis 2 s : un client vanilla en envoie au moins une par seconde. */
    private static final long WITHHELD_GAP_NANOS = 2_000_000_000L;
    /** Une position qui suit l'activité de près = rafale normale après un lag. */
    private static final long WITHHELD_BURST_NANOS = 200_000_000L;
    /**
     * Positions retenues : signalé seulement si la position qui finit par arriver est à plus de ceci de la
     * dernière (un blink déplace le joueur). Un joueur immobile (AFK, menu, caméra d'une cinématique ou d'un
     * tour de base : le jeu n'envoie alors plus sa position) ne bouge pas : jamais signalé.
     */
    private static final double WITHHELD_MIN_DISTANCE = 1.5;
    /** Téléportation non confirmée depuis 5 s alors que le client envoie des mouvements. */
    private static final long UNACKED_NANOS = 5_000_000_000L;
    private static final int UNACKED_MOVES = 20;
    /** Notre renvoi n'a jamais été envoyé (canal fermé…) : on reprend l'analyse. */
    private static final long OWN_SETBACK_TIMEOUT_NANOS = 2_000_000_000L;
    /** Vitesses imposées gardées au plus autant (le client les applique à réception, avec son ping). */
    private static final int MAX_IMPULSES = 32;
    /**
     * Un état ou une vitesse imposée reste possible jusqu'à la réponse du client à notre ping
     * (Transactions), mais jamais plus longtemps que ceci : un client qui ne répond plus ne
     * garde pas une exemption indéfiniment.
     */
    private static final long HOLD_CAP_NANOS = 5_000_000_000L;
    /** Pings sans réponse depuis 30 s alors que le client bouge : vanilla aurait été expulsé (keepalive). */
    private static final long IGNORED_PINGS_NANOS = 30_000_000_000L;
    private static final int IGNORED_PINGS_MOVES = 200;
    /** Doublons de position d'un clic droit (isUseDuplicate) espacés d'au moins ceci (clic maintenu : 200 ms). */
    private static final long DUPLICATE_SPACING_NANOS = 100_000_000L;
    /** Au-delà, une téléportation rend l'historique de positions au sol périmé. */
    private static final double RELOCATION = 16.0;
    private static final int MAX_TRACKED_TELEPORTS = 16;

    private static final Map<UUID, MoveInbox> INBOXES = new ConcurrentHashMap<>();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private MovementEngine() {
    }

    /** État par joueur, utilisé uniquement sur le thread serveur. */
    private static final class State {
        Vec3 ref;                 // dernière position revendiquée (référence des déplacements)
        boolean refGround;        // sol réel à la référence
        boolean afterTeleport;    // la référence est une destination de téléportation
        boolean reseedNext;       // destination inconnue : la prochaine position devient la référence
        long lastMoveNanos;
        long lastPosNanos;
        boolean skipCharge;       // prochain paquet = réponse à une téléportation (hors cadence)
        long withheldSince;       // activité sans position après un long silence
        double withheldReport;
        final Map<Integer, Vec3> sent = new LinkedHashMap<>(); // téléportations envoyées → destination
        int latestId;
        boolean pending;
        long pendingSince;
        int movesSincePending;
        boolean unackedReported;
        long ownSetbackAt;        // notre renvoi : positions périmées ignorées jusqu'à confirmation
        final ArrayDeque<Impulse> impulses = new ArrayDeque<>();
        final MotionTracker motion = new MotionTracker();
        final Transactions tx = new Transactions();
        // États que le client peut encore appliquer (il les apprend avec son ping) : valeur la plus
        // permissive, tenue jusqu'à ce qu'il ait répondu au ping qui suit leur changement.
        final Held special = new Held();     // 1 = mode spécial (vol, élytre, monture…)
        final Held speed = new Held();       // vitesse de déplacement (sprint supposé)
        final Held jump = new Held();        // vitesse d'un saut (Saut amélioré)
        final Held lightness = new Held();   // −gravité (une gravité plus faible est plus permissive)
        final Held slowFall = new Held();    // 1 = Chute lente
        long specialUntil;        // fin (+ marge) du dernier mode spécial : positions attendues ensuite
        boolean stableUse;        // le joueur utilise un objet de façon certaine (ralentissement attendu)
        boolean useChanged;       // le client a relâché / changé d'objet dans ce lot de paquets
        int movesSincePong;
        long duplicateNanos;      // dernier doublon de position (clic droit d'un client 1.17+), voir isUseDuplicate
        boolean ignoredReported;
        int forgedPongs;
        boolean flooded;
        boolean installed;
        long positions;           // statistiques pour /spartan status
        long teleports;
    }

    /**
     * Valeur maximale qu'un client peut encore utiliser : quand elle baisse côté serveur,
     * l'ancienne reste valable jusqu'à ce que le client ait répondu au ping suivant (il a
     * alors reçu le changement), ou, sans réponses, pendant la marge de latence.
     */
    private static final class Held {
        private double value = Double.NaN;
        private long seq;
        private long until;

        double observe(double current, Transactions tx, long now, long margin) {
            if (Double.isNaN(value) || current >= value || released(tx, now)) {
                value = current;
                seq = tx.upcoming() + 1; // le paquet du changement peut partir au tick suivant
                until = now + margin;
            }
            return value;
        }

        double value() {
            return Double.isNaN(value) ? 0 : value;
        }

        private boolean released(Transactions tx, long now) {
            if (!tx.live()) {
                return now > until;
            }
            return tx.acked(seq) || now > until + HOLD_CAP_NANOS;
        }
    }

    /**
     * Vitesse imposée par le serveur (recul, explosion, propulsion de mod). Le client l'applique
     * en traitant le paquet, donc entre sa réponse au ping qui la précède et sa réponse au
     * ping qui la suit : possible pour chaque position entre ces deux pongs, et la première
     * position après le second est la dernière qui puisse la contenir.
     */
    private static final class Impulse {
        final long sentNanos;
        final long untilNanos;  // repli sans réponses aux pings : fenêtre de temps
        final double vx;
        final double vy;
        final double vz;
        final boolean additive;
        final long ackSeq;      // premier de nos pings parti après elle
        boolean closed;
        // Ce que le client a fait pendant la fenêtre (détection anti-recul).
        Vec3 start;
        boolean startGround;
        double along;
        double rise = Double.NEGATIVE_INFINITY;
        boolean judgeable = true;

        Impulse(long sentNanos, long untilNanos, double vx, double vy, double vz, boolean additive, long ackSeq) {
            this.sentNanos = sentNanos;
            this.untilNanos = untilNanos;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.additive = additive;
            this.ackSeq = ackSeq;
        }

        double horizontal() {
            return Math.sqrt(vx * vx + vz * vz);
        }

        boolean active(Transactions tx, long nanos) {
            if (closed) {
                return false;
            }
            if (!tx.live()) {
                return sentNanos <= nanos && nanos <= untilNanos;
            }
            return tx.acked(ackSeq - 1) && nanos - sentNanos <= HOLD_CAP_NANOS;
        }
    }

    /** Connexion : branche l'observateur réseau du joueur. */
    public static void onLogin(ServerPlayer p) {
        MoveInbox inbox = new MoveInbox();
        State state = new State();
        INBOXES.put(p.getUUID(), inbox);
        STATES.put(p.getUUID(), state);
        state.installed = PacketObserver.install(p, inbox);
        if (!state.installed) {
            Spartan.LOGGER.error("[Spartan] Observateur réseau non installé pour {} : mouvement non analysé",
                    p.getName().getString());
        }
    }

    public static void clear(UUID uuid) {
        INBOXES.remove(uuid);
        STATES.remove(uuid);
    }

    /** Notre propre renvoi vient d'être envoyé. */
    public static void onOwnSetback(UUID uuid) {
        State s = STATES.get(uuid);
        if (s != null) {
            s.ownSetbackAt = System.nanoTime();
            s.motion.reset();
        }
    }

    /** Dernière position revendiquée par le client, ou null. */
    public static Vec3 lastClaim(UUID uuid) {
        State s = STATES.get(uuid);
        return s == null ? null : s.ref;
    }

    /** Résumé lisible pour /spartan status. */
    public static String describe(UUID uuid) {
        State s = STATES.get(uuid);
        if (s == null) {
            return "mouvement : non suivi";
        }
        if (!s.installed) {
            return "mouvement : observateur réseau NON installé";
        }
        String last = s.lastPosNanos == 0 ? "jamais"
                : String.format("il y a %.1f s", (System.nanoTime() - s.lastPosNanos) / 1e9);
        return "mouvement : " + s.positions + " positions analysées, " + s.teleports
                + " téléportations confirmées, dernière position " + last
                + (s.pending ? " | téléportation en attente" : "");
    }

    /** Appelé à chaque tick serveur (fin du tick joueur). */
    public static void tick(ServerPlayer p) {
        UUID uuid = p.getUUID();
        State s = STATES.get(uuid);
        MoveInbox inbox = INBOXES.get(uuid);
        if (s == null || inbox == null) {
            return;
        }
        long now = System.nanoTime();
        long margin = latencyMargin(p);
        boolean specialNow = isSpecialNow(p);
        if (specialNow) {
            s.specialUntil = now + margin;
        }
        s.special.observe(specialNow ? 1 : 0, s.tx, now, margin);
        s.slowFall.observe(p.hasEffect(MobEffects.SLOW_FALLING) ? 1 : 0, s.tx, now, margin);
        s.jump.observe(Physics.BASE_JUMP + p.getJumpBoostPower(), s.tx, now, margin);
        s.lightness.observe(-p.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get()), s.tx, now, margin);
        updateSpeedAndUse(p, s, now, margin);
        s.useChanged = false;
        s.impulses.removeIf(i -> i.closed || now - i.sentNanos > HOLD_CAP_NANOS + margin);
        if (inbox.takeOverflow()) {
            s.flooded = true;
            s.reseedNext = true;
        }
        boolean grace = CheckManager.isInGrace(uuid);
        MoveInbox.Event e;
        while ((e = inbox.poll()) != null) {
            if (p.hasDisconnected()) {
                return;
            }
            switch (e.kind()) {
                case TELEPORT_SENT -> teleportSent(s, e);
                case ACK -> ack(p, s, e);
                case IMPULSE -> impulse(p, s, e);
                case ACTION -> activityWithoutPosition(s, e.nanos(), grace);
                case MOVE -> move(p, s, e, grace);
                case PING -> s.tx.onWritten(e.id(), e.nanos());
                case PONG -> pong(p, s, e);
                case USE_CHANGE -> s.useChanged = true;
                default -> {
                }
            }
        }
        if (!p.hasDisconnected()) {
            endOfTick(p, s, System.nanoTime());
        }
        if (!p.hasDisconnected() && s.installed && SpartanConfig.TRANSACTIONS.get()) {
            // Fin du tick du joueur : tout ce qui lui a été envoyé jusqu'ici précède ce ping.
            p.connection.send(new ClientboundPingPacket(s.tx.next()));
        }
    }

    private static void pong(ServerPlayer p, State s, MoveInbox.Event e) {
        switch (s.tx.onPong(e.id(), e.nanos())) {
            case OK -> {
                s.movesSincePong = 0;
                s.ignoredReported = false;
                long sent = s.tx.writtenNanos(s.tx.ponged());
                if (sent != 0) {
                    CheckManager.runPong(p, sent, e.nanos());
                }
            }
            case FORGED -> s.forgedPongs++;
            default -> {
            }
        }
    }

    private static void teleportSent(State s, MoveInbox.Event e) {
        s.sent.put(e.id(), e.target());
        while (s.sent.size() > MAX_TRACKED_TELEPORTS) {
            Iterator<Integer> it = s.sent.keySet().iterator();
            it.next();
            it.remove();
        }
        s.latestId = e.id();
        if (!s.pending) {
            s.pending = true;
            s.pendingSince = e.nanos();
            s.movesSincePending = 0;
            s.unackedReported = false;
        }
    }

    private static void ack(ServerPlayer p, State s, MoveInbox.Event e) {
        if (!s.sent.containsKey(e.id())) {
            return; // identifiant jamais envoyé (ou trop ancien) : ignoré, aucune incidence
        }
        Vec3 target = s.sent.get(e.id());
        // Cette téléportation et les précédentes sont réglées.
        Iterator<Integer> it = s.sent.keySet().iterator();
        while (it.hasNext()) {
            int id = it.next();
            it.remove();
            if (id == e.id()) {
                break;
            }
        }
        if (e.id() == s.latestId) {
            s.pending = false;
            s.ownSetbackAt = 0;
        }
        s.teleports++;
        s.skipCharge = true; // le client répond par une position hors cadence
        if (target == null) {
            s.reseedNext = true;
            return;
        }
        if (s.ref != null && target.distanceTo(s.ref) > RELOCATION) {
            Mitigation.invalidate(p.getUUID()); // historique de sol d'un autre endroit
        }
        s.ref = target;
        s.refGround = MovementEnv.groundUnder(p, MovementEnv.boxAt(p, target));
        s.afterTeleport = true;
        s.reseedNext = false;
        EvidenceRecorder.recordTeleport(p, target);
    }

    private static void move(ServerPlayer p, State s, MoveInbox.Event e, boolean grace) {
        s.lastMoveNanos = e.nanos();
        s.movesSincePong++;
        if (s.pending) {
            s.movesSincePending++;
        }
        boolean duplicate = isUseDuplicate(s, e);
        boolean charged = !s.skipCharge && !duplicate;
        if (!duplicate) {
            s.skipCharge = false;
        }
        CheckManager.runMovePacket(p, new MovePacket(e.nanos(), e.hasPos(), e.hasRot(), e.yRot(), e.xRot(),
                e.onGround(), charged));
        if (p.hasDisconnected()) {
            return;
        }
        if (duplicate) {
            s.duplicateNanos = e.nanos();
            return; // rotation seulement : ni tick (Timer), ni pas de mouvement (Speed, Vertical, Fly)
        }
        if (!e.hasPos()) {
            activityWithoutPosition(s, e.nanos(), grace);
            return;
        }
        if (s.withheldSince != 0) {
            if (e.nanos() - s.withheldSince > WITHHELD_BURST_NANOS && s.ref != null && !s.reseedNext
                    && s.ownSetbackAt == 0 && !s.pending
                    && s.ref.distanceTo(new Vec3(e.x(), e.y(), e.z())) > WITHHELD_MIN_DISTANCE) {
                s.withheldReport = Math.max(s.withheldReport, (e.nanos() - expectedSince(s)) / 1e9);
            }
            s.withheldSince = 0;
        }
        long gap = s.lastPosNanos == 0 ? 0 : e.nanos() - s.lastPosNanos;
        s.lastPosNanos = e.nanos();
        Vec3 to = new Vec3(e.x(), e.y(), e.z());

        if (s.ref == null || s.reseedNext || grace) {
            impulsesUnjudged(s, e.nanos());
            reseed(p, s, to);
            return;
        }
        if (s.ownSetbackAt != 0) {
            impulsesUnjudged(s, e.nanos());
            return; // envoyée avant que le client ait reçu notre renvoi : périmée
        }
        if (!MovementEnv.loaded(p, to) || !MovementEnv.loaded(p, s.ref)) {
            impulsesUnjudged(s, e.nanos());
            reseed(p, s, to); // collisions inconnues : aucune conclusion
            return;
        }
        MoveSample sample = new MoveSample(p, s.ref, to, Physics.ticksCovered(gap), e.nanos(), e.onGround(),
                s.refGround, s.afterTeleport, s.special.value() >= 1, activeImpulse(s, e.nanos()),
                s.jump.value(), -s.lightness.value(), s.slowFall.value() >= 1);
        sample.setMotion(motion(p, s, sample));
        afterImpulses(p, s, sample);
        s.afterTeleport = false;
        s.positions++;
        boolean violated = CheckManager.runSample(p, sample);
        if (p.hasDisconnected()) {
            return;
        }
        EvidenceRecorder.recordSample(p, sample, e.hasRot() ? e.yRot() : p.getYRot(), e.hasRot() ? e.xRot() : p.getXRot());
        if (s.ownSetbackAt != 0) {
            return; // renvoyé pendant cet échantillon : la référence viendra de la confirmation
        }
        s.ref = to;
        s.refGround = sample.groundTo();
        if (!violated && sample.groundTo() && !sample.special()) {
            Mitigation.recordGround(p, to, e.nanos());
        }
    }

    /**
     * Doublon de position des clients 1.17+ : en utilisant un objet (clic droit : soin, nourriture, bouclier…),
     * le client renvoie sa dernière position exacte avec sa rotation, en plus du paquet du tick
     * (MultiPlayerGameMode.useItem). Compté comme un vrai pas, il faisait croire à un arrêt net puis à une
     * accélération ou à une montée impossible (fausses alertes Speed, Vertical, Fly, Timer et renvois en
     * arrière). Au plus un toutes les {@link #DUPLICATE_SPACING_NANOS} (un clic droit maintenu en envoie un
     * toutes les 200 ms) : un tricheur ne peut pas s'en servir pour rester immobile en l'air sans être jugé.
     */
    private static boolean isUseDuplicate(State s, MoveInbox.Event e) {
        // (réponse à une téléportation exclue : elle aussi répète une position, et garde son traitement)
        return e.hasPos() && e.hasRot() && s.ref != null && !s.reseedNext && !s.skipCharge && s.ownSetbackAt == 0
                && e.x() == s.ref.x && e.y() == s.ref.y && e.z() == s.ref.z
                && (s.duplicateNanos == 0 || e.nanos() - s.duplicateNanos > DUPLICATE_SPACING_NANOS);
    }

    private static void reseed(ServerPlayer p, State s, Vec3 to) {
        s.motion.reset();
        s.ref = to;
        s.refGround = MovementEnv.loaded(p, to) && MovementEnv.groundUnder(p, MovementEnv.boxAt(p, to));
        s.reseedNext = false;
        s.afterTeleport = false;
    }

    /**
     * Rotation seule ou action (coup, clic, casse) : le client tourne. S'il n'a envoyé
     * aucune position depuis 2 s alors qu'il n'est ni passager ni spectateur, il retient
     * ses positions (blink / vol sans paquet de position).
     */
    private static void activityWithoutPosition(State s, long nanos, boolean grace) {
        if (grace || s.lastPosNanos == 0 || s.withheldSince != 0) {
            return;
        }
        if (nanos - expectedSince(s) > WITHHELD_GAP_NANOS) {
            s.withheldSince = nanos;
        }
    }

    /** Depuis quand une position est attendue : dernière position, ou fin d'un mode spécial (passager…). */
    private static long expectedSince(State s) {
        return Math.max(s.lastPosNanos, s.specialUntil);
    }

    private static void endOfTick(ServerPlayer p, State s, long now) {
        double withheld = s.withheldReport;
        s.withheldReport = 0;
        double unacked = 0;
        if (s.pending && !s.unackedReported && now - s.pendingSince > UNACKED_NANOS
                && s.movesSincePending >= UNACKED_MOVES) {
            unacked = (now - s.pendingSince) / 1e9;
            s.unackedReported = true;
        }
        boolean flooded = s.flooded;
        s.flooded = false;
        if (s.ownSetbackAt != 0 && !s.pending && now - s.ownSetbackAt > OWN_SETBACK_TIMEOUT_NANOS) {
            s.ownSetbackAt = 0;
            s.reseedNext = true;
        }
        double ignoredPings = 0;
        long oldest = s.tx.oldestUnansweredNanos();
        if (s.tx.live() && oldest != 0 && !s.ignoredReported && now - oldest > IGNORED_PINGS_NANOS
                && s.movesSincePong >= IGNORED_PINGS_MOVES) {
            ignoredPings = (now - oldest) / 1e9;
            s.ignoredReported = true;
        }
        int forged = s.forgedPongs;
        s.forgedPongs = 0;
        if (withheld > 0 || unacked > 0 || flooded || ignoredPings > 0 || forged > 0) {
            CheckManager.runTickEnd(p, new MoveTickInfo(withheld, unacked, flooded, ignoredPings, forged));
        }
    }

    /** Vitesse imposée par le serveur : elle précède, dans le flux, le prochain de nos pings. */
    private static void impulse(ServerPlayer p, State s, MoveInbox.Event e) {
        s.impulses.addLast(new Impulse(e.nanos(), e.nanos() + latencyMargin(p), e.x(), e.y(), e.z(), e.additive(),
                s.tx.written() + 1));
        while (s.impulses.size() > MAX_IMPULSES) {
            s.impulses.removeFirst();
        }
    }

    private static boolean activeImpulse(State s, long nanos) {
        for (Impulse i : s.impulses) {
            if (i.active(s.tx, nanos)) {
                return true;
            }
        }
        return false;
    }

    /** Position non analysée pendant la fenêtre d'une vitesse imposée : pas de jugement anti-recul. */
    private static void impulsesUnjudged(State s, long nanos) {
        for (Impulse i : s.impulses) {
            if (i.active(s.tx, nanos)) {
                i.judgeable = false;
                closeIfAcked(s, i);
            }
        }
    }

    private static boolean closeIfAcked(State s, Impulse i) {
        if (s.tx.live() && s.tx.acked(i.ackSeq)) {
            i.closed = true; // première position après la réponse : la dernière qui pouvait la contenir
        }
        return i.closed;
    }

    /**
     * Après une position analysée : ce que le client a fait de chaque vitesse imposée
     * possible, fermeture de celles qu'il a forcément appliquées, puis jugement anti-recul.
     */
    private static void afterImpulses(ServerPlayer p, State s, MoveSample m) {
        MotionTracker.Result r = m.motion();
        Impulse only = null;
        int active = 0;
        for (Impulse i : s.impulses) {
            if (!i.active(s.tx, m.nanos())) {
                continue;
            }
            active++;
            only = i;
            if (i.start == null) {
                i.start = m.from();
                i.startGround = m.groundFrom();
            }
            double h = i.horizontal();
            if (h > 1e-6) {
                i.along = Math.max(i.along, (m.dx() * i.vx + m.dz() * i.vz) / h);
            }
            i.rise = Math.max(i.rise, m.dy());
            if (r == null || r.exempt() || r.ticks() > 1) {
                i.judgeable = false; // mode non modélisé ou ticks sans envoi : rien de sûr
            }
        }
        if (active == 1 && r != null && r.usedImpulse()) {
            only.closed = true; // appliquée à cette position : elle ne peut plus resservir
            return;
        }
        for (Impulse i : s.impulses) {
            if (!i.closed && i.start != null && closeIfAcked(s, i)) {
                judgeVelocity(p, i);
            }
        }
    }

    /**
     * Anti-recul : le client a répondu au ping qui suit le recul, donc l'a appliqué, mais
     * aucune de ses positions ne le montre alors que rien ne le bloquait. Le joueur peut
     * freiner de son entrée (0,13 bloc/tick au sol, 0,03 en l'air) et, au sol, d'un saut en
     * sprint vers l'attaquant (0,2 : le « jump reset » est légitime) : on exige la moitié
     * du reste.
     */
    private static void judgeVelocity(ServerPlayer p, Impulse i) {
        if (!i.judgeable || i.additive) {
            return;
        }
        double h = i.horizontal();
        double expectH = h - (i.startGround ? 0.35 : 0.05);
        boolean checkH = expectH >= 0.15;
        boolean checkV = i.vy >= 0.3;
        if (!checkH && !checkV) {
            return;
        }
        var box = MovementEnv.boxAt(p, i.start);
        if (checkH) {
            double dx = i.vx / h * expectH;
            double dz = i.vz / h * expectH;
            checkH = p.level().noCollision(p, box.move(dx, 0, dz)) && p.level().noCollision(p, box.move(dx, 0.3, dz))
                    && MovementEnv.pushers(p, box) == 0;
        }
        if (checkV) {
            checkV = !MovementEnv.headBlocked(p, box, 0.45);
        }
        if (!checkH && !checkV) {
            return;
        }
        boolean ignoredH = checkH && i.along < expectH * 0.5;
        boolean ignoredV = checkV && i.rise < 0.05;
        CheckManager.runVelocity(p, new VelocityOutcome(checkH ? h : 0, i.along, checkV ? i.vy : 0, i.rise,
                ignoredH || ignoredV));
    }

    /**
     * Vitesse de déplacement maximale récente (le client reçoit les changements avec son
     * ping : on garde la plus haute un aller-retour durant) et utilisation certaine d'un
     * objet : commencée depuis plus d'un aller-retour et pas sur le point de finir.
     */
    private static void updateSpeedAndUse(ServerPlayer p, State s, long now, long margin) {
        double attr = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double sprint = (p.isSprinting() ? attr / 1.3 : attr) * 1.3; // sprint toujours supposé possible
        s.speed.observe(sprint, s.tx, now, margin);
        int rtt = (int) Math.ceil(Math.max(0, p.latency) / 50.0);
        s.stableUse = p.isUsingItem() && !p.isPassenger()
                && p.getTicksUsingItem() >= 3 + rtt
                && p.getUseItemRemainingTicks() >= 3 + (rtt + 1) / 2
                && !NoSlowCheck.ignored(p.getUseItem());
    }

    /** Enveloppes de mouvement pour ce déplacement (exemptions : modes que vanilla traite à part). */
    private static MotionTracker.Result motion(ServerPlayer p, State s, MoveSample m) {
        if (m.special() || m.fluid() || m.climbable() || m.slowing() || m.platform() || m.piston()
                || m.hoverGear() || m.afterTeleport() || m.slowFalling() || m.gravity() <= 0.005
                || (m.descentGear() && !m.groundTo())) {
            return s.motion.exempt(m);
        }
        double impulseH = 0;
        double pushH = 0;
        double impulseUp = Double.NaN; // une vitesse imposée REMPLACE la vitesse verticale, même par 0 (plongeon en l'air)
        double pushUp = 0;
        for (Impulse i : s.impulses) {
            if (!i.active(s.tx, m.nanos())) {
                continue;
            }
            double h = i.horizontal();
            if (i.additive) {
                pushH += h;
                pushUp += Math.max(0, i.vy);
            } else {
                impulseH = Math.max(impulseH, h);
                impulseUp = Double.isNaN(impulseUp) ? i.vy : Math.max(impulseUp, i.vy);
            }
        }
        boolean exact = MovementEnv.onSurface(p, m.boxFrom());
        double crowd = Math.min(0.6, 0.075 * MovementEnv.pushers(p, m.boxFrom()));
        boolean bouncy = m.groundFrom() && MovementEnv.bouncyUnder(p, m.from());
        boolean ceiling = m.groundFrom() && MovementEnv.headBlocked(p, m.boxFrom(), 0.06);
        MotionTracker.Inputs in = new MotionTracker.Inputs(exact, MovementEnv.frictionUnder(p, m.from()),
                s.speed.value(), s.stableUse && !s.useChanged, impulseH, pushH, impulseUp, pushUp, crowd, bouncy,
                ceiling, MovementEnv.roughNear(p, m.boxFrom()) || MovementEnv.roughNear(p, m.boxTo()));
        return s.motion.step(m, in);
    }

    /**
     * Mode de déplacement spécial (le client se déplace selon d'autres règles) :
     * vol autorisé, spectateur, élytre, riptide, monture/véhicule, lévitation,
     * mort, sommeil, changement de dimension.
     */
    private static boolean isSpecialNow(ServerPlayer p) {
        return p.getAbilities().mayfly
                || p.getAbilities().flying
                || p.isSpectator()
                || p.isFallFlying()
                || p.isAutoSpinAttack()
                || p.getVehicle() != null
                || p.hasEffect(MobEffects.LEVITATION)
                || p.isDeadOrDying()
                || p.isSleeping()
                || p.isChangingDimension();
    }

    /**
     * Le client applique les changements d'état avec un aller-retour réseau de
     * retard : on prolonge les exemptions d'autant (300 ms + 1,5 × ping, max 3 s).
     */
    private static long latencyMargin(ServerPlayer p) {
        long ms = 300 + Math.min(2700, (long) (Math.max(0, p.latency) * 1.5));
        return ms * 1_000_000L;
    }
}
