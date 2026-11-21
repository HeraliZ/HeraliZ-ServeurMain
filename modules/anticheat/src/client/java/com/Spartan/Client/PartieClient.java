package com.Spartan.Client;

import com.Spartan.Networks.CaptureRequestPacket;
import com.Spartan.Networks.ChallengePacket;
import com.Spartan.Networks.ChallengeResponsePacket;
import com.Spartan.Networks.GardienDefiPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ScanRequestPacket;
import com.Spartan.Networks.TouchesPacket;
import com.Spartan.Spartan;
import com.Spartan.integrity.Fingerprint;
import com.Spartan.pont.Partie;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de Spartan (jar -client et -complet, sur un client physique) : répond aux défis et exécute les contrôles
 * d'intégrité demandés par le serveur. Les écouteurs du jeu (ClientEvents) s'inscrivent d'eux-mêmes.
 */
public final class PartieClient implements Partie {
    /**
     * Réponses aux défis d'intégrité (fin du 21/11, audit a8 P7) : calculées sur un fil à part, plus sur le fil du jeu, qui
     * dessine aussi les images (petit à-coup toutes les 1 à 4 minutes). Même calcul, même réponse : les classes de Spartan
     * sont lues sans être initialisées (comme le contrôle d'intégrité, déjà fait sur un fil à part), les tables de
     * l'empreinte sont protégées ({@code synchronized}), et la réponse part du fil du jeu, comme avant. Un défi après
     * l'autre, dans l'ordre d'arrivée.
     */
    private static final ExecutorService DEFIS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Spartan-Defi");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    @Override
    public void init(IEventBus busMod) {
        SecretsEnvironnement.effacer();
        ModMessages.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
    }

    private static void recevoir(Object paquet) {
        if (paquet instanceof ChallengePacket c) {
            DEFIS.execute(() -> repondre(c));
        } else if (paquet instanceof ScanRequestPacket s) {
            ClientScanService.onRequest(s);
        } else if (paquet instanceof CaptureRequestPacket c) {
            CaptureEcran.prendre(c.captureId());
        } else if (paquet instanceof GardienDefiPacket d) {
            Gardien.defi(d.defi());
        } else if (paquet instanceof TouchesPacket t) {
            TouchesSuspectes.surveiller(t);
        }
    }

    /** Fil des défis : calcule la réponse, puis l'envoie depuis le fil du jeu (si le joueur est toujours connecté). */
    private static void repondre(ChallengePacket c) {
        byte[] reponse;
        try {
            reponse = Fingerprint.answer(c.nonce(), c.variant(), c.indices());
        } catch (Throwable t) {
            // comme avant sur le fil du jeu : pas de réponse (le serveur compte un retard)
            Spartan.LOGGER.warn("[Spartan] Défi d'intégrité : réponse impossible ({})", t.toString());
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().getConnection() != null) {
                ModMessages.sendToServer(new ChallengeResponsePacket(c.nonce(), reponse));
            }
        });
    }
}
