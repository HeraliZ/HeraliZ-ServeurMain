package com.Spartan.integrity;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.Networks.ChallengePacket;
import com.Spartan.Networks.ModMessages;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Défi d'intégrité tournant (la « graine mobile »).
 *
 * 1. À la connexion, le client annonce son identifiant de build. S'il diffère
 *    de celui du serveur, ce n'est pas forcément de la triche (version pas à
 *    jour) : le joueur est invité à relancer le launcher.
 * 2. Ensuite, à intervalle ALÉATOIRE, le serveur envoie un défi : un nombre
 *    aléatoire, une variante de calcul aléatoire et TOUTES les classes du mod
 *    dans un ordre aléatoire. Le client doit renvoyer l'empreinte de SES classes
 *    calculée ainsi. Le serveur calcule la bonne réponse sur sa propre copie
 *    du jar : une seule classe modifiée suffit à changer la réponse.
 * 3. Mauvaise réponse → mod client modifié. Pas de réponse → mod bloqué ou
 *    désactivé. Chaque défi est différent : rejouer une ancienne réponse ne
 *    sert à rien.
 *
 * Le délai se mesure à la fois en temps réel (horloge monotone) ET en ticks
 * serveur : un serveur figé (sauvegarde, GC) ne fait expirer personne. Une
 * réponse juste arrivée après l'échéance efface ce dépassement (simple retard).
 */
public final class ChallengeManager {
    private static final SecureRandom RNG = new SecureRandom();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final int VARIANTS = 8;

    private ChallengeManager() {
    }

    private static final class State {
        boolean buildOk;
        boolean buildMismatch; // build différent accepté (buildCheck=false) : non vérifiable
        long nextAt;
        long pendingNonce;
        byte[] expected;
        long deadline;
        int sentTick;
        long lateNonce;      // dernier défi expiré : une réponse juste tardive reste acceptée
        byte[] lateExpected;
        int timeouts;
        int passed;
        int failed;
        long lastAnswerAt;
        boolean extended; // délai déjà prolongé une fois pour ce défi
    }

    /** Précharge l'empreinte au démarrage du serveur (pas d'I/O au premier joueur). */
    public static void warmUp() {
        Spartan.LOGGER.info("[Spartan] Empreinte d'intégrité : {} classes du jar joueur ({}), build {}",
                Fingerprint.classCount(), Fingerprint.fromEmbeddedCopy() ? "copie embarquée" : "ce jar",
                Fingerprint.buildId());
    }

    public static void onBuild(ServerPlayer p, String clientBuild) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        String mine = Fingerprint.buildId();
        if (SpartanConfig.CHECK_BUILD.get() && !mine.equals(clientBuild) && ConformiteStricte.actif()) {
            // Mode strict : un Spartan d'une autre version ne peut pas répondre aux défis (jar modifié, ou mise à jour pas
            // encore faite d'un côté) : refusé avec la consigne de relancer, jamais laissé sans défi.
            ConformiteStricte.refuser(p, "Spartan d'une autre version que le serveur (client " + shortId(clientBuild)
                    + ", serveur " + shortId(mine) + ") : jar modifié ou pas à jour", ConformiteStricte.consigneVersion());
            return;
        }
        if (SpartanConfig.CHECK_BUILD.get() && !mine.equals(clientBuild) && SpartanConfig.SILENT_CLIENT.get()) {
            // Détection silencieuse : on le laisse jouer, le staff est prévenu (jar modifié ou pas à jour).
            CheckManager.flag("Integrity", p, "build de Spartan différent du serveur (client " + shortId(clientBuild)
                    + ", serveur " + shortId(mine) + ") : jar modifié ou pas à jour, défis impossibles");
        } else if (SpartanConfig.CHECK_BUILD.get() && !mine.equals(clientBuild)) {
            AlertManager.info(p.server, "Version différente",
                    p.getName().getString() + " utilise une autre version de Spartan (client "
                            + shortId(clientBuild) + ", serveur " + shortId(mine) + "). Expulsé avec consigne de mise à jour.",
                    AlertManager.COLOR_INFO);
            p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.version",
                    "Votre version de Spartan n'est pas celle du serveur.\n"
                            + "Relancez le jeu depuis le launcher officiel pour la mettre à jour."));
            return;
        }
        // Défis seulement si le build est IDENTIQUE : un build différent (buildCheck=false)
        // ne peut pas répondre, ce serait accuser un joueur honnête mal à jour. Mais le
        // staff doit savoir que ce client échappe aux défis (jar modifié ou pas à jour).
        s.buildOk = mine.equals(clientBuild);
        s.buildMismatch = !s.buildOk;
        if (s.buildMismatch && !SpartanConfig.CHECK_BUILD.get()) {
            AlertManager.info(p.server, "Version différente",
                    p.getName().getString() + " utilise une autre version de Spartan (client "
                            + shortId(clientBuild) + ", serveur " + shortId(mine)
                            + ") : buildCheck=false, il n'est pas soumis aux défis d'intégrité.",
                    AlertManager.COLOR_INFO);
        }
        // premier défi rapidement (mode strict : dans les premières secondes, un jeu modifié ne joue pas longtemps)
        s.nextAt = Util.getMillis() + (ConformiteStricte.actif() ? randomMillis(2, 6) : randomMillis(10, 30));
    }

    /** Appelé à chaque tick serveur pour chaque joueur. */
    public static void tick(ServerPlayer p) {
        if (!SpartanConfig.CHECK_CHALLENGE.get()) {
            return;
        }
        State s = STATES.get(p.getUUID());
        if (s == null || !s.buildOk) {
            return;
        }
        long now = Util.getMillis();

        if (s.expected != null) {
            if (now > s.deadline && p.server.getTickCount() - s.sentTick >= timeoutTicks()) {
                // Première échéance : on prolonge une fois, en silence (lag, chargement).
                // Une réponse correcte, même tardive, est alors acceptée normalement.
                if (!s.extended) {
                    s.extended = true;
                    s.deadline = now + timeoutMillis(p);
                    s.sentTick = p.server.getTickCount();
                    return;
                }
                s.lateNonce = s.pendingNonce;
                s.lateExpected = s.expected;
                s.expected = null;
                s.timeouts++;
                // Un seul dépassement peut être un gel du client (sa réponse tardive, si
                // elle est juste, l'efface) : on ne signale qu'au 2e de suite.
                if (s.timeouts >= 3) {
                    // trois défis de suite sans réponse, chacun prolongé une fois : pas un simple gel du jeu
                    ConformiteStricte.refuser(p, "aucune réponse au défi d'intégrité (" + s.timeouts
                            + " fois de suite) : Spartan du jeu bloqué ou retiré");
                }
                if (s.timeouts >= 2) {
                    CheckManager.flag("Integrity", p, "aucune réponse au défi d'intégrité (" + s.timeouts
                            + " fois de suite) : mod client bloqué ou désactivé ?");
                    if (SpartanConfig.mayKickForClientSignal()) {
                        p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.non_conforme", "[Spartan] Client non conforme."));
                        return;
                    }
                }
                s.nextAt = now + randomMillis(20, 60);
            }
            return;
        }
        if (now >= s.nextAt) {
            send(p, s, now);
        }
    }

    private static void send(ServerPlayer p, State s, long now) {
        int count = Fingerprint.classCount();
        if (count == 0) {
            return; // empreinte indisponible : on n'accuse personne
        }
        long nonce = RNG.nextLong();
        int variant = RNG.nextInt(VARIANTS);
        int[] idx = coverAll(count);
        s.pendingNonce = nonce;
        s.expected = Fingerprint.expected(nonce, variant, idx);
        s.extended = false;
        s.deadline = now + timeoutMillis(p);
        s.sentTick = p.server.getTickCount();
        ModMessages.sendToPlayer(new ChallengePacket(nonce, variant, idx), p);
    }

    public static void onResponse(ServerPlayer p, long nonce, byte[] answer) {
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        if (s.lateExpected != null && nonce == s.lateNonce) {
            // Réponse au défi précédent, arrivée après l'échéance.
            boolean ok = MessageDigest.isEqual(s.lateExpected, answer);
            s.lateExpected = null;
            if (ok) {
                s.timeouts = Math.max(0, s.timeouts - 1); // c'était du retard, pas un blocage
                s.passed++;
                s.lastAnswerAt = Util.getMillis();
            } else {
                fail(p, s);
            }
            return;
        }
        if (s.expected == null || nonce != s.pendingNonce) {
            return; // réponse non sollicitée ou périmée : ignorée
        }
        boolean ok = MessageDigest.isEqual(s.expected, answer);
        s.expected = null;
        s.lateExpected = null;
        s.lastAnswerAt = Util.getMillis();
        s.nextAt = Util.getMillis() + randomMillis(
                SpartanConfig.CHALLENGE_MIN_SECONDS.get(), SpartanConfig.CHALLENGE_MAX_SECONDS.get());
        if (ok) {
            s.timeouts = 0;
            s.passed++;
            return;
        }
        fail(p, s);
    }

    private static void fail(ServerPlayer p, State s) {
        s.failed++;
        CheckManager.flag("Integrity", p, "réponse fausse au défi d'intégrité : mod client modifié");
        ConformiteStricte.refuser(p, "réponse fausse au défi d'intégrité : Spartan du jeu modifié");
        if (SpartanConfig.mayKickForClientSignal()) {
            p.connection.disconnect(Component.translatableWithFallback("spartan.expulsion.non_conforme", "[Spartan] Client non conforme."));
        }
    }

    /** Résumé lisible pour /spartan status. */
    public static String describe(UUID uuid) {
        State s = STATES.get(uuid);
        if (s == null) {
            return "build : pas encore reçu";
        }
        String last = s.lastAnswerAt == 0 ? "jamais"
                : "il y a " + (Util.getMillis() - s.lastAnswerAt) / 1000 + " s";
        return "build : " + (s.buildOk ? "validé" : s.buildMismatch ? "différent (non vérifié, sans défis)" : "refusé")
                + " | défis réussis : " + s.passed + ", échoués : " + s.failed
                + ", sans réponse : " + s.timeouts + " | dernière réponse : " + last
                + (s.expected != null ? " | défi en cours" : "");
    }

    /** Le build a-t-il été validé pour ce joueur ? (pour le contrôle de handshake) */
    public static boolean isBuildValidated(UUID uuid) {
        State s = STATES.get(uuid);
        return s != null && s.buildOk;
    }

    /** Numéro de version de Spartan reçu du jeu du joueur (validation du jeu). */
    public static boolean buildRecu(UUID uuid) {
        return STATES.containsKey(uuid);
    }

    /** Défis réussis depuis la connexion (validation du jeu). */
    public static int reussis(UUID uuid) {
        State s = STATES.get(uuid);
        return s == null ? 0 : s.passed;
    }

    /** Défis à la réponse fausse depuis la connexion (validation du jeu). */
    public static int echoues(UUID uuid) {
        State s = STATES.get(uuid);
        return s == null ? 0 : s.failed;
    }

    public static void clear(UUID uuid) {
        STATES.remove(uuid);
    }

    /** Délai de réponse, allongé si le serveur rame (ticks plus longs que 50 ms). */
    private static long timeoutMillis(ServerPlayer p) {
        double lag = Math.max(1.0, p.server.getAverageTickTime() / 50.0);
        return (long) (SpartanConfig.CHALLENGE_TIMEOUT_SECONDS.get() * 1000L * lag);
    }

    /** Le même délai, compté en ticks serveur (indépendant du lag). */
    private static int timeoutTicks() {
        return SpartanConfig.CHALLENGE_TIMEOUT_SECONDS.get() * 20;
    }

    /**
     * TOUTES les classes du mod, dans un ordre tiré au hasard à chaque défi : une seule
     * classe modifiée (détection neutralisée, scan qui ment…) change la réponse, quel que
     * soit le défi. Avant, quelques classes au hasard seulement étaient vérifiées.
     */
    static int[] coverAll(int count) {
        int n = Math.min(count, ChallengePacket.MAX_INDICES);
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        return idx;
    }

    private static long randomMillis(int minSeconds, int maxSeconds) {
        int lo = Math.min(minSeconds, maxSeconds);
        int hi = Math.max(minSeconds, maxSeconds);
        return (lo + RNG.nextInt(hi - lo + 1)) * 1000L;
    }

    private static String shortId(String id) {
        return id == null ? "?" : id.substring(0, Math.min(8, id.length()));
    }
}
