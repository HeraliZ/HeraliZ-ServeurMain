package fr.heralisz.matchs.moteur.net;

import fr.heralisz.matchs.moteur.api.Annonces;
import fr.heralisz.matchs.moteur.api.Hud;
import fr.heralisz.matchs.moteur.api.KillFeed;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.groupes.GroupesService;
import fr.heralisz.matchs.moteur.kits.ChoixService;
import fr.heralisz.matchs.moteur.spectateur.Spectateurs;
import fr.heralisz.matchs.reseau.MatchsNet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Traitement serveur des paquets client -> serveur de PaquetsMoteur (corps d'origine des « handle », docs/METHODE_CLIENT_SERVEUR.md
 * §6). Appelé par serveur/PartieServeur.
 */
public final class ReceptionPaquetsMoteurServeur {
    private ReceptionPaquetsMoteurServeur() {
    }

    /** Rend vrai si le paquet était l'un des siens. */
    public static boolean recevoir(MatchsNet.Recu r) {
        Object o = r.paquet();
        if (o instanceof PaquetsMoteur.SpectateurC2S p) {
            spectateurC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsMoteur.GroupeC2S p) {
            groupeC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsMoteur.ChoixC2S p) {
            choixC2S(p, r.ctx());
            return true;
        }
        return false;
    }

    /** Corps d'origine de PaquetsMoteur.SpectateurC2S.handle. */
    private static void spectateurC2S(PaquetsMoteur.SpectateurC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = PaquetsMoteur.emetteur(ctx);
            if (sp != null) Spectateurs.changer(sp, p.sens());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de PaquetsMoteur.GroupeC2S.handle. */
    private static void groupeC2S(PaquetsMoteur.GroupeC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = PaquetsMoteur.emetteur(ctx);
            if (sp != null) GroupesService.actionEcran(sp, p.action(), p.cible());
            ctx.get().setPacketHandled(true);
    }

    /** Corps d'origine de PaquetsMoteur.ChoixC2S.handle. */
    private static void choixC2S(PaquetsMoteur.ChoixC2S p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = PaquetsMoteur.emetteur(ctx);
            if (sp != null) ChoixService.choisir(sp, p.kit());
            ctx.get().setPacketHandled(true);
    }
}
