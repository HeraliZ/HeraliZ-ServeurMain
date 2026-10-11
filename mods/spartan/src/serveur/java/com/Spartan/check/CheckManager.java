package com.Spartan.check;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.movement.MovePacket;
import com.Spartan.check.movement.MoveSample;
import com.Spartan.check.movement.MoveTickInfo;
import com.Spartan.check.movement.VelocityOutcome;
import com.Spartan.config.SpartanConfig;
import com.Spartan.evidence.EvidenceRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Registre des détections et comptabilité des niveaux de violation (VL).
 *
 * - Le VL s'accumule par joueur et par détection, mais DÉCROÎT avec le temps
 *   (anti-accumulation de faux positifs sur une longue session).
 * - Les alertes sont soumises à un cooldown (anti-spam disque / Discord / chat).
 * - Une fenêtre de grâce après tp / respawn / connexion évite les faux flags
 *   dus aux rubberbands et aux grands sauts de position.
 * - Sanction automatique seulement si alertOnly=false.
 */
public class CheckManager {
    private static final List<Check> ALL = new ArrayList<>();
    private static final List<MovementCheck> MOVEMENT = new ArrayList<>();
    private static final List<CombatCheck> COMBAT = new ArrayList<>();
    private static final List<BlockCheck> BLOCK = new ArrayList<>();

    private static final Map<UUID, Map<String, VlState>> VIOLATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GRACE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> LAST_ATTACK = new ConcurrentHashMap<>(); // [gameTime, targetId]

    /**
     * Vrai si une détection a signalé pendant l'événement en cours (attaque, casse).
     * Tous les événements arrivent sur le thread serveur : un simple champ suffit.
     */
    private static boolean currentViolation = false;

    private CheckManager() {
    }

    private static final class VlState {
        int vl;
        long lastFlag;
        long lastAlert;
    }

    public static void register(Check check) {
        ALL.add(check);
        if (check instanceof MovementCheck m) {
            MOVEMENT.add(m);
        }
        if (check instanceof CombatCheck c) {
            COMBAT.add(c);
        }
        if (check instanceof BlockCheck b) {
            BLOCK.add(b);
        }
    }

    public static List<Check> getChecks() {
        return List.copyOf(ALL);
    }

    /**
     * Le serveur rame-t-il ? (temps moyen d'un tick > 55 ms, soit moins de ~18 TPS)
     * Quand il rame, les paquets du joueur arrivent par paquets : les mesures de
     * cadence et de vitesse par tick deviennent fausses. Les détections sensibles
     * au temps s'abstiennent.
     */
    public static boolean serverLagging(ServerPlayer p) {
        return p.server.getAverageTickTime() > 55f;
    }

    /** Accorde une fenêtre de grâce (ms) : les détections sont suspendues. */
    public static void grantGrace(UUID uuid, long millis) {
        GRACE_UNTIL.put(uuid, System.currentTimeMillis() + millis);
        Mitigation.invalidate(uuid); // l'ancienne position sûre n'a plus de sens
    }

    public static boolean isInGrace(UUID uuid) {
        Long until = GRACE_UNTIL.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Contrôles par tick : effets, détections de combat différées. Pendant la grâce
     * (saut de position), l'état des détections de mouvement est réinitialisé.
     */
    public static void runTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean grace = isInGrace(uuid);
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                return; // expulsé par une détection : ne plus recréer d'état pour lui
            }
            if (grace) {
                c.onGrace(uuid);
            } else {
                safely(c, () -> c.check(player));
            }
        }
        for (CombatCheck c : COMBAT) {
            if (player.hasDisconnected()) {
                return;
            }
            safely(c, () -> c.tick(player));
        }
    }

    /** Un déplacement du client. @return true si une détection a signalé. */
    public static boolean runSample(ServerPlayer player, MoveSample sample) {
        currentViolation = false;
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                break;
            }
            safely(c, () -> c.onSample(player, sample));
        }
        return currentViolation;
    }

    /** Un paquet de mouvement du client (cadence, rotation). */
    public static void runMovePacket(ServerPlayer player, MovePacket packet) {
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                return;
            }
            safely(c, () -> c.onMovePacket(player, packet));
        }
    }

    /** Réponse du client à l'un de nos pings (horloge du client, voir TimerCheck). */
    public static void runPong(ServerPlayer player, long sentNanos, long nanos) {
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                return;
            }
            safely(c, () -> c.onPong(player, sentNanos, nanos));
        }
    }

    /** Recul appliqué par le client, jugé par le moteur (anti-recul). */
    public static void runVelocity(ServerPlayer player, VelocityOutcome outcome) {
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                return;
            }
            safely(c, () -> c.onVelocity(player, outcome));
        }
    }

    /** Constats du moteur de mouvement en fin de tick. */
    public static void runTickEnd(ServerPlayer player, MoveTickInfo info) {
        for (MovementCheck c : MOVEMENT) {
            if (player.hasDisconnected()) {
                return;
            }
            safely(c, () -> c.onTickEnd(player, info));
        }
    }

    /** @return true si une détection a signalé ce coup (il doit alors être annulé). */
    public static boolean onAttack(ServerPlayer attacker, Entity target) {
        currentViolation = false;
        // Même tick, autre cible : coup multi-cibles d'un mod de combat.
        long gameTime = attacker.level().getGameTime();
        long[] last = LAST_ATTACK.put(attacker.getUUID(), new long[] {gameTime, target.getId()});
        boolean secondary = last != null && last[0] == gameTime && last[1] != target.getId();
        for (CombatCheck c : COMBAT) {
            if (!secondary || c.runsOnSecondaryTargets()) {
                safely(c, () -> c.onAttack(attacker, target));
            }
        }
        return currentViolation;
    }

    /** @return true si une détection a signalé cette casse (elle doit alors être annulée). */
    public static boolean onBlockBreak(ServerPlayer player, BlockState state, BlockPos pos, Level level) {
        currentViolation = false;
        for (BlockCheck c : BLOCK) {
            safely(c, () -> c.onBreak(player, state, pos, level));
        }
        return currentViolation;
    }

    /**
     * Marque l'événement en cours comme illégitime sans émettre d'alerte
     * (ex. : 4e bloc d'un nuker déjà signalé au 3e).
     */
    public static void markViolation() {
        currentViolation = true;
    }

    /** Réinitialise l'état des détections de mouvement (après un renvoi en arrière). */
    public static void resetMovementState(UUID uuid) {
        for (MovementCheck c : MOVEMENT) {
            c.onGrace(uuid);
        }
    }

    public static void onStartBreaking(ServerPlayer player, BlockPos pos, Level level) {
        for (BlockCheck c : BLOCK) {
            safely(c, () -> c.onStartBreaking(player, pos, level));
        }
    }

    /** Une détection qui plante ne doit jamais faire tomber le serveur. */
    private static void safely(Check c, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            Spartan.LOGGER.error("[Spartan] Erreur dans la détection " + c.getName(), e);
        }
    }

    public static void flag(Check check, ServerPlayer player, String details) {
        currentViolation = true;
        record(check.getName(), player, details, true); // détection serveur : preuve de mouvement utile
        if (!player.hasDisconnected()) {
            Mitigation.onFlag(check, player);
        }
    }

    /**
     * Variante par nom, pour les signaux déclarés par le client (intégrité, handshake,
     * launcher…) : pas de preuve de mouvement (elle n'aurait aucun rapport, et un client
     * qui se reconnecte en boucle saturerait le dossier de preuves).
     */
    public static void flag(String checkName, ServerPlayer player, String details) {
        record(checkName, player, details, false);
    }

    /**
     * Compte une violation sans alerte ni expulsion (le contrôle d'intégrité envoie
     * lui-même son rapport détaillé) : visible dans /spartan vl.
     * @return le nouveau VL
     */
    public static int recordSilent(String checkName, UUID uuid) {
        return bump(uuid, checkName, System.currentTimeMillis()).vl;
    }

    private static VlState bump(UUID uuid, String checkName, long now) {
        VlState state = VIOLATIONS
                .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(checkName, k -> new VlState());

        // Décroissance temporelle depuis le dernier flag.
        int decaySec = SpartanConfig.VL_DECAY_SECONDS.get();
        if (decaySec > 0 && state.lastFlag > 0) {
            int dec = (int) ((now - state.lastFlag) / 1000L / decaySec);
            if (dec > 0) {
                state.vl = Math.max(0, state.vl - dec);
            }
        }
        state.vl++;
        state.lastFlag = now;
        return state;
    }

    private static void record(String checkName, ServerPlayer player, String details, boolean withEvidence) {
        long now = System.currentTimeMillis();
        VlState state = bump(player.getUUID(), checkName, now);
        int vl = state.vl;

        if (vl < SpartanConfig.MIN_VL_ALERT.get()) {
            return;
        }

        // Cooldown d'alerte : on n'écrit la preuve et n'alerte qu'au plus une fois par fenêtre.
        long cooldownMs = SpartanConfig.ALERT_COOLDOWN.get() * 1000L;
        if (now - state.lastAlert >= cooldownMs) {
            state.lastAlert = now;
            String evidence = withEvidence ? EvidenceRecorder.dump(player, checkName) : null;
            AlertManager.dispatch(player, checkName, vl, details, evidence);
        }

        // Signal venant du jeu du joueur (withEvidence=false) : jamais d'expulsion en détection
        // silencieuse ; le staff le surveille et décide.
        boolean mayKick = withEvidence ? !SpartanConfig.ALERT_ONLY.get() : SpartanConfig.mayKickForClientSignal();
        if (mayKick && vl >= SpartanConfig.ACTION_VL.get()) {
            player.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.controle",
                    "[Spartan] Expulsé : %s", checkName));
        }
    }

    /** VL courant d'un joueur pour chaque détection (pour la commande /spartan). */
    public static Map<String, Integer> getViolations(UUID uuid) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        Map<String, VlState> m = VIOLATIONS.get(uuid);
        if (m != null) {
            m.forEach((k, v) -> out.put(k, v.vl));
        }
        return out;
    }

    public static void clear(UUID uuid) {
        VIOLATIONS.remove(uuid);
        GRACE_UNTIL.remove(uuid);
        LAST_ATTACK.remove(uuid);
        for (Check c : ALL) {
            c.clear(uuid);
        }
    }
}
