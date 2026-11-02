package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.check.movement.model.LatenceSuivi;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Latence trafiquée (« faux lag », audit du 02/11, point 14) : la latence keepalive de Minecraft, dont TaCZ se sert pour
 * remonter le temps des hitbox et Spartan pour ses marges, comparée aux réponses aux transactions (voir LatenceSuivi).
 * Alerte seulement (catégorie « reseau » : aucun renvoi). Nécessite les transactions.
 *
 * <p>Quel que soit le réglage de l'alerte, les marges de Spartan ne suivent plus une latence keepalive plus grande que la
 * latence mesurée ({@link #effective}) : un faux lag n'élargit plus les exemptions.
 */
public class LatenceCheck extends MovementCheck {
    private static final Map<UUID, LatenceSuivi> SUIVIS = new ConcurrentHashMap<>();

    public LatenceCheck() {
        super("Latence", "reseau");
    }

    /** Latence à utiliser pour les marges (ms). */
    public static int effective(ServerPlayer p) {
        int l = Math.max(0, p.latency);
        LatenceSuivi s = SUIVIS.get(p.getUUID());
        return s == null || !SpartanConfig.TRANSACTIONS.get() ? l : s.effective(l);
    }

    @Override
    public void onPong(ServerPlayer player, long sentNanos, long nanos) {
        SUIVIS.computeIfAbsent(player.getUUID(), u -> new LatenceSuivi()).pong(nanos - sentNanos);
    }

    /** Une fois par seconde (appelé à chaque tick du joueur hors grâce). */
    @Override
    public void check(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        LatenceSuivi s = SUIVIS.get(player.getUUID());
        if (s == null) {
            return;
        }
        int n = s.seconde(Math.max(0, player.latency), SpartanConfig.FAKE_LAG_MARGIN_MS.get(), SpartanConfig.FAKE_LAG_MIN_MS.get());
        if (SpartanConfig.CHECK_FAKE_LAG.get() && n >= SpartanConfig.FAKE_LAG_SAMPLES.get()) {
            s.remettre();
            flag(player, String.format("latence annoncée à Minecraft %d ms (dernière mesure keepalive) alors que le jeu répond "
                            + "aux transactions en %d ms au plus depuis 30 s, %d mesures de suite : réponses keepalive retenues"
                            + " (faux lag, « backtrack » avec TaCZ) ?", s.derniereMesure(), s.reference(), n));
        }
    }

    /** Une grâce (téléportation, réapparition) ne change rien à la latence : l'historique est gardé. */
    @Override
    public void onGrace(UUID uuid) {
    }

    @Override
    public void clear(UUID uuid) {
        SUIVIS.remove(uuid);
    }
}
