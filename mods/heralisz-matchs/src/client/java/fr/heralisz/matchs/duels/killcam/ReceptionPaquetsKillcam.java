package fr.heralisz.matchs.duels.killcam;

import fr.heralisz.matchs.reseau.MatchsNet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Réception côté client des paquets de PaquetsKillcam (corps d'origine des « handle » serveur -> client, docs/METHODE_CLIENT_SERVEUR.md
 * §6). Branchée par client/PartieClient.
 */
public final class ReceptionPaquetsKillcam {
    private ReceptionPaquetsKillcam() {
    }

    public static void recevoir(Object o) {
        if (o instanceof PaquetsKillcam.ClipS2C p) {
            fr.heralisz.matchs.duels.killcam.client.KillcamClient.recevoir(p.clip());
            return;
        }
        if (o instanceof PaquetsKillcam.ArretS2C p) {
            fr.heralisz.matchs.duels.killcam.client.KillcamClient.arreterDepuisServeur();
            return;
        }
    }
}
