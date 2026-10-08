package fr.heralisz.matchs.duels.killcam;

import fr.heralisz.matchs.reseau.MatchsNet;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Traitement serveur des paquets client -> serveur de PaquetsKillcam (corps d'origine des « handle », docs/METHODE_CLIENT_SERVEUR.md
 * §6). Appelé par serveur/PartieServeur.
 */
public final class ReceptionPaquetsKillcamServeur {
    private ReceptionPaquetsKillcamServeur() {
    }

    /** Rend vrai si le paquet était l'un des siens. */
    public static boolean recevoir(MatchsNet.Recu r) {
        Object o = r.paquet();
        if (o instanceof PaquetsKillcam.FinC2S p) {
            finC2S(p, r.ctx());
            return true;
        }
        return false;
    }

    /** Corps d'origine de PaquetsKillcam.FinC2S.handle. */
    private static void finC2S(PaquetsKillcam.FinC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer joueur = ctx.get().getSender();
            if (joueur != null) KillcamLogique.finie(joueur, p.passee());
            ctx.get().setPacketHandled(true);
    }
}
