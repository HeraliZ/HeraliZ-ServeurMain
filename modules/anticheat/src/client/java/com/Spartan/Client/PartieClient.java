package com.Spartan.Client;

import com.Spartan.Networks.CaptureRequestPacket;
import com.Spartan.Networks.ChallengePacket;
import com.Spartan.Networks.ChallengeResponsePacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ScanRequestPacket;
import com.Spartan.integrity.Fingerprint;
import com.Spartan.pont.Partie;
import com.Spartan.scan.CollecteLocale;
import com.Spartan.scan.collect.ScanCollector;
import java.util.Set;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de Spartan (jar -client et -complet, sur un client physique) : répond aux défis et exécute les contrôles
 * d'intégrité demandés par le serveur. Les écouteurs du jeu (ClientEvents) s'inscrivent d'eux-mêmes.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ModMessages.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
        // /spartan selfscan en solo (jar -complet) : le contrôle tourne dans ce jeu
        CollecteLocale.COLLECTEUR = (mods, jeu, thread, watch, probes, process, natives) -> ScanCollector.collect(
                new ScanCollector.Context(0, mods, jeu, null, thread, 10, false, Set.of(), watch, probes, process, natives));
    }

    private static void recevoir(Object paquet) {
        if (paquet instanceof ChallengePacket c) {
            ModMessages.sendToServer(new ChallengeResponsePacket(c.nonce(),
                    Fingerprint.answer(c.nonce(), c.variant(), c.indices())));
        } else if (paquet instanceof ScanRequestPacket s) {
            ClientScanService.onRequest(s);
        } else if (paquet instanceof CaptureRequestPacket c) {
            CaptureEcran.prendre(c.captureId());
        }
    }
}
