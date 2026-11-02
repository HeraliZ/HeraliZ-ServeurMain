package com.Spartan.Client;

import com.Spartan.Networks.CaptureRequestPacket;
import com.Spartan.Networks.ChallengePacket;
import com.Spartan.Networks.ChallengeResponsePacket;
import com.Spartan.Networks.GardienDefiPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ScanRequestPacket;
import com.Spartan.integrity.Fingerprint;
import com.Spartan.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de Spartan (jar -client et -complet, sur un client physique) : répond aux défis et exécute les contrôles
 * d'intégrité demandés par le serveur. Les écouteurs du jeu (ClientEvents) s'inscrivent d'eux-mêmes.
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        SecretsEnvironnement.effacer();
        ModMessages.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
    }

    private static void recevoir(Object paquet) {
        if (paquet instanceof ChallengePacket c) {
            ModMessages.sendToServer(new ChallengeResponsePacket(c.nonce(),
                    Fingerprint.answer(c.nonce(), c.variant(), c.indices())));
        } else if (paquet instanceof ScanRequestPacket s) {
            ClientScanService.onRequest(s);
        } else if (paquet instanceof CaptureRequestPacket c) {
            CaptureEcran.prendre(c.captureId());
        } else if (paquet instanceof GardienDefiPacket d) {
            Gardien.defi(d.defi());
        }
    }
}
