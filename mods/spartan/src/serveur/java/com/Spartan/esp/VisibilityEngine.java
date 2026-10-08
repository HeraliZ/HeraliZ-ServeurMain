package com.Spartan.esp;

import com.Spartan.Spartan;
import com.Spartan.config.SpartanConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Anti-ESP / anti-wallhack côté serveur.
 *
 * Un cheat ESP affiche les joueurs que le serveur envoie au client, murs compris.
 * Ici, le serveur n'envoie plus un joueur à un autre tant qu'aucune ligne de vue
 * n'existe entre eux : derrière un mur, dans un bâtiment fermé, sous terre, le
 * tricheur n'a tout simplement pas l'information. Aucun client injecté ne peut
 * afficher ce qu'il n'a jamais reçu.
 *
 * Jamais d'invisibilité injuste :
 * - anticipation : on montre la cible dès qu'elle serait visible après un pas de
 *   côté, un saut ou le mouvement en cours de l'observateur pendant son ping ;
 * - une cible vue reste envoyée hideDelayTicks après avoir disparu (pas de clignotement) ;
 * - toujours visibles : coéquipiers, joueurs en surbrillance (Glowing), même
 *   véhicule, très proches ; spectateurs et créatifs voient tout ;
 * - bruit : un joueur qui tire (TaCZ) ou parle (Simple Voice Chat) est envoyé aux
 *   joueurs à portée d'oreille, sinon son tir / sa voix ne serait pas joué chez eux ;
 * - une paire cachée qui n'a pas pu être revérifiée à temps (serveur saturé) est
 *   montrée (comportement vanilla), jamais l'inverse.
 *
 * Performances (50-70 joueurs) : les lignes de vue sont calculées sur d'autres
 * cœurs (EspWorkers) pendant le tick suivant ; le thread serveur ne fait que lister
 * les paires et appliquer les décisions. Une paire dont aucun des deux joueurs n'a
 * bougé et sans bloc modifié entre eux n'est pas recalculée. Mode synchrone
 * (threads = 0) avec budget de temps par tick pour les petites machines.
 *
 * La décision est lue par vanilla via ServerPlayer.broadcastToPlayer (Mixin) et
 * appliquée immédiatement quand elle change. État : thread serveur uniquement.
 */
public final class VisibilityEngine {
    /** Paire visible : revérifiée tous les N ticks (pour la cacher à temps). */
    private static final int VISIBLE_INTERVAL = 4;
    /** Rien n'a bougé : le résultat est repris tel quel au plus N ticks de suite. */
    private static final int STILL_MAX_TICKS = 20;
    /** Déplacement en dessous duquel un joueur est considéré immobile (blocs). */
    private static final double STILL_EPSILON = 0.05;
    /** Paire plus listée (hors de portée, joueur parti) depuis N ticks : oubliée. */
    private static final int STALE_TICKS = 40;
    /** Coéquipiers : test refait tous les N ticks. */
    private static final int ALLY_REFRESH = 40;
    /** Au-delà (blocs/tick), le déplacement est une téléportation : pas de vitesse. */
    private static final double MAX_SPEED = 4.0;
    /** Blocs modifiés retenus par tick ; au-delà, tout est considéré comme modifié. */
    private static final int MAX_CHANGES = 256;
    /** Un lot de calcul ne doit pas déborder sur plus d'un tick. */
    private static final long BATCH_DEADLINE_NANOS = 45_000_000L;
    private static final long NEVER = -1_000_000L;

    private static final Long2ObjectOpenHashMap<Pair> PAIRS = new Long2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Snap> SNAPS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<double[]> LAST_POS = new Int2ObjectOpenHashMap<>();
    /** Joueurs qui parlent (thread de Simple Voice Chat) : uuid → {fin (nanos), portée (bits du double)}. */
    private static final Map<UUID, long[]> VOICE = new ConcurrentHashMap<>();
    /** Blocs modifiés depuis le dernier tick, par monde (rend « sales » les paires immobiles proches). */
    private static final Map<Level, LongArrayList> CHANGES = new IdentityHashMap<>();
    private static boolean changesOverflow;

    // Calcul sur le thread serveur : première vue d'une paire et mode synchrone.
    private static final LevelVoxels MAIN_VOXELS = new LevelVoxels();
    private static final Raycaster MAIN_RAYS = new Raycaster(MAIN_VOXELS);
    private static final Sight MAIN_SIGHT = new Sight(MAIN_RAYS);

    private static final List<Pair> DUE = new ArrayList<>();
    private static final List<Pair> CHANGED = new ArrayList<>();
    private static final Stats STATS = new Stats();
    private static final Comparator<Pair> PRIORITY = (a, b) -> {
        int ka = a.lastCheck == NEVER ? 0 : a.visible ? 2 : 1;
        int kb = b.lastCheck == NEVER ? 0 : b.visible ? 2 : 1;
        return ka != kb ? Integer.compare(ka, kb) : Double.compare(a.dist, b.dist);
    };

    private static EspWorkers workers;
    private static EspWorkers.Batch inFlight;
    private static boolean active;
    private static long tick;

    private VisibilityEngine() {
    }

    /** Une paire orientée : ce que l'observateur reçoit de la cible. */
    static final class Pair {
        final int observer;
        final int target;
        /** Décision appliquée au suivi vanilla : la cible est-elle envoyée à l'observateur ? */
        boolean visible = true;
        long lastSeen = NEVER;
        long lastCheck = NEVER;
        long lastComputed = NEVER;
        boolean lastResult;
        long nextCheck;
        long revealUntil = NEVER;
        long listed;
        boolean inFlight;
        double dist;
        boolean allied;
        long alliedAt = NEVER;
        // Positions lors du dernier calcul (paires immobiles).
        double ex;
        double ey;
        double ez;
        double tx;
        double ty;
        double tz;

        Pair(int observer, int target) {
            this.observer = observer;
            this.target = target;
        }
    }

    /** Instantané d'un joueur pour ce tick. */
    static final class Snap {
        ServerPlayer p;
        ServerLevel level;
        int id;
        Sight.Eye eye;
        Sight.Body body;
        double fx;
        double fz;
        boolean seesAll;
        boolean glowing;
        Entity root;
        int lookahead;
        int range;
    }

    /** Statistiques sur une fenêtre de 100 ticks (5 s). */
    static final class Stats {
        long ticks;
        long nanos;
        long maxNanos;
        long evals;
        long reused;
        long rays;
        long steps;
        long overruns;
        long failOpens;
        long firstLooks;
        long reveals;
        long hides;
        String last = "pas encore de mesure";
    }

    private static long key(int observer, int target) {
        return ((long) observer << 32) | (target & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ décision (Mixin)

    /**
     * Appelé par vanilla (ServerPlayer.broadcastToPlayer, via Mixin) chaque fois qu'il
     * se demande s'il doit envoyer {@code target} à {@code observer}.
     * @return true pour NE PAS l'envoyer
     */
    public static boolean hides(ServerPlayer target, ServerPlayer observer) {
        if (!active || target == observer || observer.isSpectator() || !target.server.isSameThread()) {
            return false;
        }
        Pair pr = PAIRS.get(key(observer.getId(), target.getId()));
        if (pr == null) {
            pr = firstLook(observer, target);
        }
        if (!pr.visible && tick - pr.lastCheck > SpartanConfig.ESP_FAIL_OPEN_TICKS.get()) {
            return false; // pas revérifiée à temps : on montre
        }
        return !pr.visible;
    }

    /** Paire jamais vue (entrée dans la portée, connexion, téléportation) : décision immédiate. */
    private static Pair firstLook(ServerPlayer observer, ServerPlayer target) {
        Snap o = SNAPS.get(observer.getId());
        if (o == null || o.p != observer) {
            o = snap(observer, false);
        }
        Snap t = SNAPS.get(target.getId());
        if (t == null || t.p != target) {
            t = snap(target, false);
        }
        Pair pr = new Pair(o.id, t.id);
        pr.listed = tick;
        pr.dist = distance(o, t);
        if (alwaysVisible(o, t, pr)) {
            pr.lastSeen = tick;
            pr.lastCheck = tick;
            pr.nextCheck = tick + VISIBLE_INTERVAL;
        } else {
            MAIN_VOXELS.reset();
            computeNow(pr, o, t);
            pr.visible = tick - pr.lastSeen <= SpartanConfig.ESP_HIDE_DELAY_TICKS.get();
        }
        PAIRS.put(key(o.id, t.id), pr);
        STATS.firstLooks++;
        return pr;
    }

    // ------------------------------------------------------------------ tick

    /** Fin de chaque tick serveur. */
    public static void tick(MinecraftServer server) {
        tick++;
        if (!SpartanConfig.ENABLED.get() || !SpartanConfig.ESP_ENABLED.get()) {
            if (active) {
                shutdown();
            }
            return;
        }
        active = true;
        long start = System.nanoTime();
        long rays0 = MAIN_RAYS.rays();
        long steps0 = MAIN_RAYS.steps();
        Occluders.refresh();
        MAIN_VOXELS.reset();
        configureWorkers();
        int viewRange = server.getPlayerList().getViewDistance() * 16;

        // 1. Résultats du lot calculé pendant ce tick par les autres cœurs.
        collect();

        // 2. Instantanés des joueurs.
        SNAPS.clear();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                SNAPS.put(p.getId(), snap(p, true));
            }
        }
        if (tick % 200 == 0) {
            IntIterator ids = LAST_POS.keySet().iterator();
            while (ids.hasNext()) {
                if (!SNAPS.containsKey(ids.nextInt())) {
                    ids.remove();
                }
            }
        }
        voiceReveals(server);

        // 3. Paires à portée de suivi vanilla : décision immédiate, résultat repris, ou calcul à planifier.
        DUE.clear();
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            LongArrayList changes = CHANGES.get(level);
            int n = players.size();
            for (int i = 0; i < n; i++) {
                Snap o = SNAPS.get(players.get(i).getId());
                for (int j = 0; j < n; j++) {
                    Snap t = SNAPS.get(players.get(j).getId());
                    if (i == j || o == null || t == null) {
                        continue;
                    }
                    double dx = o.fx - t.fx;
                    double dz = o.fz - t.fz;
                    double range = Math.min(t.range, viewRange) + 2.0;
                    if (dx * dx + dz * dz > range * range) {
                        continue;
                    }
                    long k = key(o.id, t.id);
                    Pair pr = PAIRS.get(k);
                    if (pr == null) {
                        pr = new Pair(o.id, t.id);
                        PAIRS.put(k, pr);
                    }
                    pr.listed = tick;
                    pr.dist = distance(o, t);
                    if (alwaysVisible(o, t, pr)) {
                        pr.lastSeen = tick;
                        pr.lastCheck = tick;
                        pr.nextCheck = tick + VISIBLE_INTERVAL;
                    } else if (!pr.inFlight && tick >= pr.nextCheck) {
                        if (unchanged(pr, o, t, changes)) {
                            reuse(pr); // rien n'a bougé entre eux : le dernier résultat tient
                        } else {
                            DUE.add(pr);
                        }
                    }
                }
            }
        }
        CHANGES.clear();
        changesOverflow = false;

        // 4. Calcul : lot envoyé aux autres cœurs, ou sur ce thread dans le budget.
        int done = 0;
        if (!DUE.isEmpty()) {
            DUE.sort(PRIORITY);
            if (workers != null) {
                dispatch();
            } else {
                long budget = SpartanConfig.ESP_BUDGET_MICROS.get() * 1000L;
                for (Pair pr : DUE) {
                    if ((done & 7) == 7 && System.nanoTime() - start > budget) {
                        STATS.overruns++;
                        break;
                    }
                    Snap o = SNAPS.get(pr.observer);
                    Snap t = SNAPS.get(pr.target);
                    if (o != null && t != null) {
                        computeNow(pr, o, t);
                        done++;
                    }
                }
            }
        }

        // 5. Décisions, puis application immédiate de celles qui changent.
        int hideDelay = SpartanConfig.ESP_HIDE_DELAY_TICKS.get();
        int failOpen = SpartanConfig.ESP_FAIL_OPEN_TICKS.get();
        CHANGED.clear();
        int hidden = 0;
        ObjectIterator<Pair> it = PAIRS.values().iterator();
        while (it.hasNext()) {
            Pair pr = it.next();
            if (tick - pr.listed > STALE_TICKS) {
                it.remove();
                continue;
            }
            boolean vis = tick - pr.lastSeen <= hideDelay || tick < pr.revealUntil;
            if (!vis && tick - pr.lastCheck > failOpen) {
                vis = true;
                STATS.failOpens++;
            }
            if (vis != pr.visible) {
                pr.visible = vis;
                CHANGED.add(pr);
                if (!vis) {
                    STATS.hides++;
                }
            }
            if (!pr.visible) {
                hidden++;
            }
        }
        for (Pair pr : CHANGED) {
            apply(pr);
        }

        long dt = System.nanoTime() - start;
        STATS.ticks++;
        STATS.nanos += dt;
        STATS.maxNanos = Math.max(STATS.maxNanos, dt);
        STATS.evals += done;
        STATS.rays += MAIN_RAYS.rays() - rays0;
        STATS.steps += MAIN_RAYS.steps() - steps0;
        if (STATS.ticks >= 100) {
            closeWindow(hidden);
        }
    }

    /** Threads de calcul selon la config (0 = thread serveur, -1 = automatique). */
    private static void configureWorkers() {
        int wanted = SpartanConfig.ESP_THREADS.get();
        if (wanted < 0) {
            wanted = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4));
        }
        int current = workers == null ? 0 : workers.threads();
        if (wanted == current) {
            return;
        }
        if (workers != null) {
            if (inFlight != null) {
                inFlight.cancel = true;
                releaseInFlight();
            }
            workers.shutdown();
            workers = null;
        }
        if (wanted > 0) {
            workers = new EspWorkers(wanted);
            Spartan.LOGGER.info("[Spartan] Anti-ESP : lignes de vue calculées sur {} thread(s) séparé(s).", wanted);
        }
    }

    private static void dispatch() {
        if (inFlight != null) {
            return; // le lot précédent n'est pas fini : ces paires attendront
        }
        EspWorkers.Job[] jobs = new EspWorkers.Job[DUE.size()];
        int n = 0;
        for (Pair pr : DUE) {
            Snap o = SNAPS.get(pr.observer);
            Snap t = SNAPS.get(pr.target);
            if (o == null || t == null) {
                continue;
            }
            remember(pr, o, t);
            pr.inFlight = true;
            // +1 tick : le résultat ne sera appliqué qu'au tick suivant.
            jobs[n++] = new EspWorkers.Job(pr, o.level, o.level.getMinSection(), o.level.getSectionsCount(),
                    o.eye, t.body, o.lookahead + 1);
        }
        if (n == 0) {
            return;
        }
        if (n < jobs.length) {
            jobs = Arrays.copyOf(jobs, n);
        }
        inFlight = workers.submit(jobs, tick, SpartanConfig.ESP_PEEK.get(), SpartanConfig.ESP_JUMP.get(),
                System.nanoTime() + BATCH_DEADLINE_NANOS);
    }

    /** Récupère les tranches finies du lot en cours. */
    private static void collect() {
        EspWorkers.Batch b = inFlight;
        if (b == null) {
            return;
        }
        boolean all = true;
        for (int k = 0; k < b.slices; k++) {
            if (b.collected[k]) {
                continue;
            }
            if (b.finished.get(k) == 0) {
                all = false;
                continue;
            }
            b.collected[k] = true;
            STATS.rays += b.rays[k];
            STATS.steps += b.steps[k];
            for (int i = k; i < b.jobs.length; i += b.slices) {
                Pair pr = (Pair) b.jobs[i].pair();
                pr.inFlight = false;
                if (b.done[i]) {
                    result(pr, b.seen[i], b.tick);
                    STATS.evals++;
                } else {
                    STATS.overruns++;
                }
            }
        }
        if (all) {
            inFlight = null;
        } else if (tick - b.tick > 2) {
            b.cancel = true; // les threads abandonnent le reste au prochain point de contrôle
        }
    }

    /** Lot abandonné (changement de config, arrêt) : ses paires redeviennent libres. */
    private static void releaseInFlight() {
        if (inFlight != null) {
            for (EspWorkers.Job j : inFlight.jobs) {
                ((Pair) j.pair()).inFlight = false;
            }
            inFlight = null;
        }
    }

    /** Calcul immédiat sur le thread serveur. */
    private static void computeNow(Pair pr, Snap o, Snap t) {
        remember(pr, o, t);
        MAIN_VOXELS.use(o.level, o.level.getMinSection(), o.level.getSectionsCount());
        boolean seen;
        try {
            seen = MAIN_SIGHT.canSee(o.eye, t.body, o.lookahead, SpartanConfig.ESP_PEEK.get(), SpartanConfig.ESP_JUMP.get());
        } catch (RuntimeException e) {
            seen = true;
        }
        result(pr, seen, tick);
    }

    /** Positions au moment du calcul (pour reconnaître une paire immobile). */
    private static void remember(Pair pr, Snap o, Snap t) {
        pr.ex = o.eye.x();
        pr.ey = o.eye.y();
        pr.ez = o.eye.z();
        pr.tx = t.body.cx();
        pr.ty = t.body.cy();
        pr.tz = t.body.cz();
    }

    private static void result(Pair pr, boolean seen, long at) {
        pr.lastCheck = Math.max(pr.lastCheck, at);
        pr.lastComputed = at;
        pr.lastResult = seen;
        if (seen) {
            pr.lastSeen = Math.max(pr.lastSeen, at);
        }
        boolean shown = tick - pr.lastSeen <= SpartanConfig.ESP_HIDE_DELAY_TICKS.get();
        pr.nextCheck = tick + (shown ? VISIBLE_INTERVAL : hiddenInterval(pr.dist));
    }

    /** Le dernier résultat calculé reste valable : rien n'a bougé entre eux. */
    private static void reuse(Pair pr) {
        pr.lastCheck = tick;
        if (pr.lastResult) {
            pr.lastSeen = tick;
        }
        boolean shown = tick - pr.lastSeen <= SpartanConfig.ESP_HIDE_DELAY_TICKS.get();
        pr.nextCheck = tick + (shown ? VISIBLE_INTERVAL : hiddenInterval(pr.dist));
        STATS.reused++;
    }

    /**
     * Ni l'observateur ni la cible n'ont bougé depuis le dernier calcul, aucun bloc n'a
     * changé entre eux, et ce calcul date de moins d'une seconde.
     */
    private static boolean unchanged(Pair pr, Snap o, Snap t, LongArrayList changes) {
        if (pr.lastComputed == NEVER || tick - pr.lastComputed > STILL_MAX_TICKS || changesOverflow) {
            return false;
        }
        if (Math.abs(pr.ex - o.eye.x()) > STILL_EPSILON || Math.abs(pr.ey - o.eye.y()) > STILL_EPSILON
                || Math.abs(pr.ez - o.eye.z()) > STILL_EPSILON || Math.abs(pr.tx - t.body.cx()) > STILL_EPSILON
                || Math.abs(pr.ty - t.body.cy()) > STILL_EPSILON || Math.abs(pr.tz - t.body.cz()) > STILL_EPSILON) {
            return false;
        }
        if (changes == null || changes.isEmpty()) {
            return true;
        }
        double m = SpartanConfig.ESP_PEEK.get() + 2.0;
        double minX = Math.min(o.eye.x(), t.body.minX()) - m;
        double minY = Math.min(o.eye.y(), t.body.minY()) - m;
        double minZ = Math.min(o.eye.z(), t.body.minZ()) - m;
        double maxX = Math.max(o.eye.x(), t.body.maxX()) + m;
        double maxY = Math.max(o.eye.y(), t.body.maxY()) + m;
        double maxZ = Math.max(o.eye.z(), t.body.maxZ()) + m;
        for (int i = 0; i < changes.size(); i++) {
            long p = changes.getLong(i);
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);
            if (x + 1 >= minX && x <= maxX && y + 1 >= minY && y <= maxY && z + 1 >= minZ && z <= maxZ) {
                return false;
            }
        }
        return true;
    }

    /** Un bloc a changé (porte ouverte, mur cassé…) : appelé par ServerLevel.sendBlockUpdated (Mixin). */
    public static void onBlockChanged(Level level, BlockPos pos) {
        if (!active || changesOverflow) {
            return;
        }
        LongArrayList list = CHANGES.computeIfAbsent(level, k -> new LongArrayList());
        if (list.size() >= MAX_CHANGES) {
            changesOverflow = true;
            return;
        }
        list.add(pos.asLong());
    }

    /** Cible cachée : revérifiée d'autant plus souvent qu'elle est proche. */
    private static int hiddenInterval(double dist) {
        if (dist < 16) {
            return 1;
        }
        if (dist < 32) {
            return 2;
        }
        if (dist < 64) {
            return 3;
        }
        return dist < 128 ? 4 : 6;
    }

    /** Cas où la cible est toujours envoyée, sans calcul de ligne de vue. */
    private static boolean alwaysVisible(Snap o, Snap t, Pair pr) {
        if (o.seesAll || t.glowing || pr.dist < SpartanConfig.ESP_MIN_DISTANCE.get()) {
            return true;
        }
        if (o.root == t.root && o.root != o.p) {
            return true; // même véhicule
        }
        if (SpartanConfig.ESP_ALLIES.get()) {
            if (tick - pr.alliedAt >= ALLY_REFRESH) {
                pr.alliedAt = tick;
                pr.allied = Allies.allied(o.p, t.p);
            }
            return pr.allied;
        }
        return false;
    }

    private static void apply(Pair pr) {
        Snap o = SNAPS.get(pr.observer);
        Snap t = SNAPS.get(pr.target);
        if (o != null && t != null) {
            TrackerAccess.refresh(o.p, t.p);
        }
    }

    private static double distance(Snap o, Snap t) {
        double dx = o.eye.x() - t.body.cx();
        double dy = o.eye.y() - t.body.cy();
        double dz = o.eye.z() - t.body.cz();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Snap snap(ServerPlayer p, boolean remember) {
        Snap s = new Snap();
        s.p = p;
        s.level = p.serverLevel();
        s.id = p.getId();
        Vec3 pos = p.position();
        double vx = 0;
        double vy = 0;
        double vz = 0;
        double[] last = LAST_POS.get(s.id);
        if (last != null) {
            vx = pos.x - last[0];
            vy = pos.y - last[1];
            vz = pos.z - last[2];
            if (vx * vx + vy * vy + vz * vz > MAX_SPEED * MAX_SPEED) {
                vx = 0;
                vy = 0;
                vz = 0;
            }
        }
        if (remember) {
            if (last == null) {
                LAST_POS.put(s.id, new double[] {pos.x, pos.y, pos.z});
            } else {
                last[0] = pos.x;
                last[1] = pos.y;
                last[2] = pos.z;
            }
        }
        Vec3 eye = p.getEyePosition();
        s.eye = new Sight.Eye(eye.x, eye.y, eye.z, vx, vy, vz, p.onGround());
        AABB box = p.getBoundingBox();
        s.root = p.getRootVehicle();
        if (s.root != p) {
            box = box.minmax(s.root.getBoundingBox()); // passager : visible dès que son véhicule l'est
        }
        s.body = new Sight.Body(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, vx, vy, vz);
        s.fx = pos.x;
        s.fz = pos.z;
        s.lookahead = Math.max(1, Math.min(8, Math.round(Math.max(0, p.latency) / 50f) + 1));
        s.seesAll = p.isSpectator()
                || (SpartanConfig.ESP_BYPASS_CREATIVE.get() && p.isCreative())
                || (SpartanConfig.ESP_STAFF_SEE_ALL.get() && p.hasPermissions(2));
        s.glowing = p.isCurrentlyGlowing();
        int range = TrackerAccess.effectiveRange(p);
        s.range = range > 0 ? range : 512;
        return s;
    }

    // ------------------------------------------------------------------ bruits

    /**
     * Un bruit lié à une entité (tir TaCZ…) : elle est montrée tout de suite aux joueurs
     * à moins de {@code radius} blocs, pendant {@code ticks}. Sinon leur client ignorerait
     * le son (TaCZ ne joue un tir que si le tireur existe chez le client). Thread serveur.
     */
    public static void reveal(Entity source, double radius, int ticks) {
        if (!active || !(source instanceof ServerPlayer src) || !(src.level() instanceof ServerLevel level)
                || !src.server.isSameThread()) {
            return;
        }
        double r2 = radius * radius;
        for (ServerPlayer obs : level.players()) {
            if (obs == src || obs.distanceToSqr(src) > r2) {
                continue;
            }
            long k = key(obs.getId(), src.getId());
            Pair pr = PAIRS.get(k);
            if (pr == null) {
                pr = new Pair(obs.getId(), src.getId());
                pr.listed = tick;
                PAIRS.put(k, pr);
            }
            pr.revealUntil = Math.max(pr.revealUntil, tick + ticks);
            if (!pr.visible) {
                pr.visible = true;
                STATS.reveals++;
                TrackerAccess.refresh(obs, src);
            }
        }
    }

    /** Simple Voice Chat : un joueur parle (appelé depuis le thread réseau de la voix). */
    public static void noteVoice(UUID speaker, double range) {
        long until = System.nanoTime() + 250_000_000L;
        VOICE.put(speaker, new long[] {until, Double.doubleToLongBits(range)});
    }

    private static void voiceReveals(MinecraftServer server) {
        if (VOICE.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        int ticks = SpartanConfig.ESP_VOICE_REVEAL_TICKS.get();
        boolean on = SpartanConfig.ESP_REVEAL_VOICE.get();
        VOICE.entrySet().removeIf(e -> {
            long[] v = e.getValue();
            if (now > v[0]) {
                return true;
            }
            ServerPlayer p = on ? server.getPlayerList().getPlayer(e.getKey()) : null;
            if (p != null) {
                reveal(p, Double.longBitsToDouble(v[1]), ticks);
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Déconnexion, mort, changement de dimension : l'état du joueur repart de zéro. */
    public static void forget(ServerPlayer p) {
        int id = p.getId();
        PAIRS.values().removeIf(pr -> pr.observer == id || pr.target == id);
        LAST_POS.remove(id);
        SNAPS.remove(id);
        VOICE.remove(p.getUUID());
    }

    /** Désactivé (config) : tout ce qui était caché est renvoyé. */
    private static void shutdown() {
        active = false;
        if (inFlight != null) {
            inFlight.cancel = true;
            releaseInFlight();
        }
        for (Pair pr : PAIRS.values()) {
            if (!pr.visible) {
                pr.visible = true;
                apply(pr);
            }
        }
        PAIRS.clear();
        SNAPS.clear();
        LAST_POS.clear();
        CHANGES.clear();
    }

    /** Arrêt du serveur : rien ne doit survivre (serveur intégré). */
    public static void reset() {
        active = false;
        if (inFlight != null) {
            inFlight.cancel = true;
            inFlight = null;
        }
        if (workers != null) {
            workers.shutdown();
            workers = null;
        }
        PAIRS.clear();
        SNAPS.clear();
        LAST_POS.clear();
        VOICE.clear();
        CHANGES.clear();
        ChunkTable.clear();
    }

    /** Chunk chargé (thread serveur) : lisible par les threads de calcul. */
    public static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        ChunkTable.onLoad(event);
    }

    public static void onChunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        ChunkTable.onUnload(event);
    }

    /** Tags de blocs rechargés. */
    public static void onTagsUpdated() {
        Occluders.invalidate();
    }

    public static boolean isActive() {
        return active;
    }

    // ------------------------------------------------------------------ diagnostic

    private static void closeWindow(int hidden) {
        Stats s = STATS;
        double avgMs = s.nanos / 1e6 / s.ticks;
        s.last = String.format("%d paires suivies, %d cachées | thread serveur : %.2f ms/tick en moyenne (max %.2f), "
                        + "calcul : %s | %.0f calculs/s (%.0f repris sans calcul), %.0f rayons/s (%.0f blocs/rayon) | "
                        + "%d premières vues, %d cachées, %d montrées par bruit, %d montrées faute de temps, "
                        + "%d non calculées à temps",
                PAIRS.size(), hidden, avgMs, s.maxNanos / 1e6,
                workers == null ? "sur le thread serveur" : workers.threads() + " thread(s) séparé(s)",
                s.evals * 20.0 / s.ticks, s.reused * 20.0 / s.ticks, s.rays * 20.0 / s.ticks,
                s.rays == 0 ? 0.0 : (double) s.steps / s.rays, s.firstLooks, s.hides, s.reveals, s.failOpens,
                s.overruns);
        s.ticks = 0;
        s.nanos = 0;
        s.maxNanos = 0;
        s.evals = 0;
        s.reused = 0;
        s.rays = 0;
        s.steps = 0;
        s.overruns = 0;
        s.failOpens = 0;
        s.firstLooks = 0;
        s.reveals = 0;
        s.hides = 0;
    }

    /** /spartan esp : coût et activité (fenêtre des 5 dernières secondes). */
    public static String describe() {
        if (!active) {
            return "Anti-ESP inactif (antiEsp.enabled=false).";
        }
        return "Anti-ESP actif : " + STATS.last;
    }

    /** /spartan esp <joueur> : ce que ce joueur reçoit des autres. */
    public static List<String> describeObserver(ServerPlayer observer) {
        List<String> hidden = new ArrayList<>();
        List<String> shown = new ArrayList<>();
        for (Pair pr : PAIRS.values()) {
            if (pr.observer != observer.getId()) {
                continue;
            }
            Snap t = SNAPS.get(pr.target);
            String name = t == null ? "#" + pr.target : t.p.getName().getString();
            Boolean sent = t == null ? null : TrackerAccess.isSent(observer, t.p);
            String check = sent == null || sent == pr.visible ? "" : sent ? " [ENVOYÉ quand même]" : " [non envoyé]";
            String entry = String.format("%s (%.0f m)%s", name, pr.dist, check);
            (pr.visible ? shown : hidden).add(entry);
        }
        List<String> out = new ArrayList<>();
        out.add(observer.getName().getString() + " — caché(s) : " + (hidden.isEmpty() ? "aucun" : String.join(", ", hidden)));
        out.add("envoyé(s) : " + (shown.isEmpty() ? "aucun" : String.join(", ", shown)));
        return out;
    }
}
