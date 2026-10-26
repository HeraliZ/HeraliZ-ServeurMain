package fr.heraliz.clashwar.moteur.net;

import fr.heraliz.clashwar.moteur.api.Annonces;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.KillFeed;
import fr.heraliz.clashwar.moteur.api.Kit;
import fr.heraliz.clashwar.moteur.client.MoteurClient;
import fr.heraliz.clashwar.reseau.MatchsNet;
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
 * Réception côté client des paquets de PaquetsMoteur (corps d'origine des « handle » serveur -> client, docs/METHODE_CLIENT_SERVEUR.md
 * §6). Branchée par client/PartieClient.
 */
public final class ReceptionPaquetsMoteur {
    private ReceptionPaquetsMoteur() {
    }

    public static void recevoir(Object o) {
        if (o instanceof PaquetsMoteur.HudS2C p) {
            MoteurClient.bandeau(p.bandeau());
            return;
        }
        if (o instanceof PaquetsMoteur.AnnonceS2C p) {
            MoteurClient.annonce(p.annonce());
            return;
        }
        if (o instanceof PaquetsMoteur.CompteS2C p) {
            MoteurClient.compte(p.secondes(), p.titre());
            return;
        }
        if (o instanceof PaquetsMoteur.KillFeedS2C p) {
            MoteurClient.killFeed(p.ligne());
            return;
        }
        if (o instanceof PaquetsMoteur.SpectateurS2C p) {
            MoteurClient.spectateur(p.actif(), p.cible(), p.couleur(), p.nombre());
            return;
        }
        if (o instanceof PaquetsMoteur.ChoixOuvrirS2C p) {
            MoteurClient.choixOuvrir(p);
            return;
        }
        if (o instanceof PaquetsMoteur.ChoixEtatS2C p) {
            MoteurClient.choixEtat(p);
            return;
        }
        if (o instanceof PaquetsMoteur.EtatMatchS2C p) {
            MoteurClient.etatMatch(p.enMatch());
            return;
        }
    }
}
