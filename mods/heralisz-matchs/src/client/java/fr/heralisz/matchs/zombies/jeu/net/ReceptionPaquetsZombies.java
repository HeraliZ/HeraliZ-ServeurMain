package fr.heralisz.matchs.zombies.jeu.net;

import fr.heralisz.matchs.reseau.MatchsNet;
import fr.heralisz.matchs.zombies.jeu.ZombiesJeu;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import fr.heralisz.matchs.zombies.jeu.client.ZombiesJeuClient;
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
 * Réception côté client des paquets de PaquetsZombies (corps d'origine des « handle » serveur -> client, docs/METHODE_CLIENT_SERVEUR.md
 * §6). Branchée par client/PartieClient.
 */
public final class ReceptionPaquetsZombies {
    private ReceptionPaquetsZombies() {
    }

    public static void recevoir(Object o) {
        if (o instanceof PaquetsZombies.EtatS2C p) {
            ZombiesJeuClient.etat(p);
            return;
        }
        if (o instanceof PaquetsZombies.InviteS2C p) {
            ZombiesJeuClient.invite(p);
            return;
        }
        if (o instanceof PaquetsZombies.EvenementS2C p) {
            ZombiesJeuClient.evenement(p);
            return;
        }
        if (o instanceof PaquetsZombies.OuvrirS2C p) {
            ZombiesJeuClient.ouvrir(p);
            return;
        }
        if (o instanceof PaquetsZombies.FinS2C p) {
            ZombiesJeuClient.fin(p);
            return;
        }
        if (o instanceof PaquetsZombies.ObjectifS2C p) {
            ZombiesJeuClient.objectif(p);
            return;
        }
        if (o instanceof PaquetsZombies.IntroS2C p) {
            ZombiesJeuClient.intro(p);
            return;
        }
        if (o instanceof PaquetsZombies.AtelierS2C p) {
            ZombiesJeuClient.atelier(p);
            return;
        }
        if (o instanceof PaquetsZombies.LieuS2C p) {
            ZombiesJeuClient.lieu(p);
            return;
        }
    }
}
