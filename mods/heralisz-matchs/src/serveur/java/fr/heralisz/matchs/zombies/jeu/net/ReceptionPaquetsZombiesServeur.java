package fr.heralisz.matchs.zombies.jeu.net;

import fr.heralisz.matchs.zombies.jeu.ZombiesJeuLogique;

import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.ZombiesJeu;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Traitement serveur des paquets client -> serveur de PaquetsZombies (corps d'origine des « handle », docs/METHODE_CLIENT_SERVEUR.md
 * §6). Appelé par serveur/PartieServeur.
 */
public final class ReceptionPaquetsZombiesServeur {
    private ReceptionPaquetsZombiesServeur() {
    }

    /** Rend vrai si le paquet était l'un des siens. */
    public static boolean recevoir(MatchsNet.Recu r) {
        Object o = r.paquet();
        if (o instanceof PaquetsZombies.LancerC2S p) {
            lancerC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsZombies.ToucheC2S p) {
            toucheC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsZombies.FabriquerC2S p) {
            fabriquerC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsZombies.CoupC2S p) {
            coupC2S(p, r.ctx());
            return true;
        }
        return false;
    }

    /** Corps d'origine de PaquetsZombies.LancerC2S.handle. */
    private static void lancerC2S(PaquetsZombies.LancerC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ZombiesJeuLogique.lancer(sp, p.carte());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de PaquetsZombies.ToucheC2S.handle. */
    private static void toucheC2S(PaquetsZombies.ToucheC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ZombiesJeuLogique.touche(sp, p.enfoncee());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de PaquetsZombies.FabriquerC2S.handle. */
    private static void fabriquerC2S(PaquetsZombies.FabriquerC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ZombiesJeuLogique.fabriquer(sp, p.atelier(), p.recette());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de PaquetsZombies.CoupC2S.handle. */
    private static void coupC2S(PaquetsZombies.CoupC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) ZombiesJeuLogique.coup(sp);
            ctx.get().setPacketHandled(true);
    }
}
