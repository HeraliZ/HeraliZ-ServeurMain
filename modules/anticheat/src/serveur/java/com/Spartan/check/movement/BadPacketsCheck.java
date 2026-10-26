package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.config.SpartanConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Paquets qu'un client vanilla n'envoie jamais :
 * - angle de vue vertical hors de [-90°, 90°] ;
 * - positions retenues alors que le client agit (blink, vol sans paquet de position) ;
 * - téléportation jamais confirmée malgré l'activité du client (il reste figé
 *   côté serveur et esquive les corrections) ;
 * - inondation de paquets ;
 * - pings ignorés pendant 30 s alors que le client bouge (un client vanilla dont le
 *   réseau descendant serait coupé aussi longtemps aurait été expulsé par le keepalive),
 *   réponses à des pings jamais envoyés ou dans le désordre.
 */
public class BadPacketsCheck extends MovementCheck {
    public BadPacketsCheck() {
        super("BadPackets");
    }

    @Override
    public void onMovePacket(ServerPlayer p, MovePacket m) {
        if (SpartanConfig.CHECK_BADPACKETS.get() && m.hasRot() && Math.abs(m.xRot()) > 90.0001f) {
            flag(p, String.format("angle de vue impossible (%.1f°)", m.xRot()));
        }
    }

    @Override
    public void onTickEnd(ServerPlayer p, MoveTickInfo info) {
        if (!SpartanConfig.CHECK_BADPACKETS.get()) {
            return;
        }
        if (info.withheldSeconds() > 0) {
            flag(p, String.format("aucune position pendant %.1f s alors que le client agit (blink)", info.withheldSeconds()));
        }
        if (info.unackedSeconds() > 0) {
            flag(p, String.format("téléportation non confirmée depuis %.0f s malgré l'activité du client", info.unackedSeconds()));
        }
        if (info.flooded()) {
            flag(p, "inondation de paquets : analyse interrompue");
        }
        if (info.ignoredPingsSeconds() > 0) {
            flag(p, String.format("ne répond plus aux pings depuis %.0f s alors qu'il bouge (latence simulée)",
                    info.ignoredPingsSeconds()));
        }
        if (info.forgedPongs() > 0) {
            flag(p, String.format("%d réponse(s) à des pings jamais envoyés ou dans le désordre", info.forgedPongs()));
        }
    }
}
