package fr.heraliz.clashwar.tournoi.net;

import fr.heralisz.clans.model.Banniere;
import fr.heraliz.clashwar.reseau.MatchsNet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Réception côté client des paquets de CwPaquets (corps d'origine des « handle » serveur -> client, docs/METHODE_CLIENT_SERVEUR.md
 * §6). Branchée par client/PartieClient.
 */
public final class ReceptionCwPaquets {
    private ReceptionCwPaquets() {
    }

    public static void recevoir(Object o) {
        if (o instanceof CwPaquets.TableauS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.tableau(p.vue());
            return;
        }
        if (o instanceof CwPaquets.RencontreS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.rencontre(p);
            return;
        }
        if (o instanceof CwPaquets.InscriptionS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.inscription(p);
            return;
        }
        if (o instanceof CwPaquets.ToastS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.toast(p);
            return;
        }
        if (o instanceof CwPaquets.ZoneS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.zone(p);
            return;
        }
        if (o instanceof CwPaquets.OuvrirS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.ouvrir(p.ecran());
            return;
        }
        if (o instanceof CwPaquets.MatchHudS2C p) {
            fr.heraliz.clashwar.tournoi.client.HudMatchCw.recevoir(p);
            return;
        }
        if (o instanceof CwPaquets.FinMatchS2C p) {
            fr.heraliz.clashwar.tournoi.client.HudMatchCw.fin(p);
            return;
        }
        if (o instanceof CwPaquets.PodiumS2C p) {
            fr.heraliz.clashwar.tournoi.client.HudPodium.recevoir(p);
            return;
        }
        if (o instanceof CwPaquets.ClassementS2C p) {
            fr.heraliz.clashwar.tournoi.client.CwClient.classement(p);
            return;
        }
    }
}
