package com.Spartan.check.combat;

import com.Spartan.check.CheckManager;
import com.Spartan.check.CombatCheck;
import com.Spartan.config.SpartanConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Détection d'autoclicker, côté serveur, sur deux signaux :
 * 1. trop de coups par seconde (au-delà de ce qu'un humain soutient) ;
 * 2. des coups trop RÉGULIERS : un humain a toujours de la variation entre ses
 *    clics, une machine tape à intervalle quasi constant.
 * S'abstient quand le serveur rame (coups reçus groupés, horodatage faussé).
 */
public class AutoClickerCheck extends CombatCheck {
    private static final int SAMPLE = 20;            // nombre de coups analysés
    private static final double MIN_STDDEV_MS = 8.0;  // en dessous : trop régulier
    // Le client n'envoie ses coups qu'une fois par tick (multiples de 50 ms) : un humain
    // régulier à 10 coups/s peut sembler « parfait ». On n'analyse donc la régularité
    // qu'au-delà de ~17 coups/s soutenus, où un humain varie forcément.
    private static final double MAX_MEAN_MS = 60.0;

    private static final long MIN_HUMAN_INTERVAL_MS = 5; // en dessous : livraison réseau groupée
    private final Map<UUID, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();

    public AutoClickerCheck() {
        super("AutoClicker");
    }

    @Override
    public void onAttack(ServerPlayer p, Entity target) {
        if (!SpartanConfig.CHECK_AUTOCLICKER.get()) {
            return;
        }
        if (CheckManager.serverLagging(p)) {
            // Serveur qui rame : les coups arrivent groupés, cadence et régularité faussées.
            hits.remove(p.getUUID());
            return;
        }
        long now = System.currentTimeMillis();
        // Un gel réseau (4G, Wi-Fi) retient les paquets puis les livre d'un coup : des
        // coups à ~0 ms d'intervalle qu'aucune main ne produit. On les écarte pour ne
        // pas confondre un à-coup réseau avec un autoclicker.
        Long prev = lastHit.put(p.getUUID(), now);
        if (prev != null && now - prev < MIN_HUMAN_INTERVAL_MS) {
            hits.remove(p.getUUID());
            return;
        }
        Deque<Long> q = hits.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        q.addLast(now);
        while (q.size() > SAMPLE) {
            q.removeFirst();
        }

        // 1. Cadence sur la dernière seconde.
        int cps = 0;
        for (Iterator<Long> it = q.descendingIterator(); it.hasNext(); ) {
            if (now - it.next() <= 1000) {
                cps++;
            } else {
                break;
            }
        }
        int maxCps = SpartanConfig.AUTOCLICKER_MAX_CPS.get();
        if (cps > maxCps) {
            flag(p, String.format("%d coups/s (max %d)", cps, maxCps));
            return;
        }

        // 2. Régularité des intervalles.
        if (q.size() < SAMPLE) {
            return;
        }
        long[] t = q.stream().mapToLong(Long::longValue).toArray();
        double sum = 0;
        for (int i = 1; i < t.length; i++) {
            sum += t[i] - t[i - 1];
        }
        double mean = sum / (t.length - 1);
        if (mean > MAX_MEAN_MS) {
            return;
        }
        double var = 0;
        for (int i = 1; i < t.length; i++) {
            double d = (t[i] - t[i - 1]) - mean;
            var += d * d;
        }
        double stddev = Math.sqrt(var / (t.length - 1));
        if (stddev < MIN_STDDEV_MS) {
            flag(p, String.format("clics trop réguliers : intervalle %.0f ms ± %.1f ms", mean, stddev));
        }
    }

    @Override
    public void clear(UUID uuid) {
        hits.remove(uuid);
        lastHit.remove(uuid);
    }
}
