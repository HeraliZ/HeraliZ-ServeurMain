package fr.heraliz.clashwar.tournoi.net;

import fr.heralisz.clans.model.Banniere;
import fr.heraliz.clashwar.tournoi.Tournois;
import fr.heraliz.clashwar.reseau.MatchsNet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Traitement serveur des paquets client -> serveur de CwPaquets (corps d'origine des « handle », docs/METHODE_CLIENT_SERVEUR.md
 * §6). Appelé par serveur/PartieServeur.
 */
public final class ReceptionCwPaquetsServeur {
    private ReceptionCwPaquetsServeur() {
    }

    /** Rend vrai si le paquet était l'un des siens. */
    public static boolean recevoir(MatchsNet.Recu r) {
        Object o = r.paquet();
        if (o instanceof CwPaquets.InscriptionC2S p) {
            inscriptionC2S(p, r.ctx());
            return true;
        }
        if (o instanceof CwPaquets.RetraitC2S p) {
            retraitC2S(p, r.ctx());
            return true;
        }
        return false;
    }

    /** Corps d'origine de CwPaquets.InscriptionC2S.handle. */
    private static void inscriptionC2S(CwPaquets.InscriptionC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) Tournois.inscrire(sp, p.equipes());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de CwPaquets.RetraitC2S.handle. */
    private static void retraitC2S(CwPaquets.RetraitC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) Tournois.retirer(sp);
            ctx.get().setPacketHandled(true);
    }
}
