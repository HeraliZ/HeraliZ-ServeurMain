package fr.heralisz.matchs.duels.net;

import fr.heralisz.matchs.duels.Duels;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Réception côté client des paquets de PaquetsDuels (corps d'origine des « handle » serveur -> client, docs/METHODE_CLIENT_SERVEUR.md
 * §6). Branchée par client/PartieClient.
 */
public final class ReceptionPaquetsDuels {
    private ReceptionPaquetsDuels() {
    }

    public static void recevoir(Object o) {
        if (o instanceof PaquetsDuels.CatalogueS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.catalogue(p.catalogue());
            return;
        }
        if (o instanceof PaquetsDuels.ClassesS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.classes(p.classes(), p.derniere(), p.ouvrir());
            return;
        }
        if (o instanceof PaquetsDuels.EcranDuelS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.ecranDuel(p.ecran());
            return;
        }
        if (o instanceof PaquetsDuels.DefiS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.defi(p.defi(), p.idFini(), p.raison());
            return;
        }
        if (o instanceof PaquetsDuels.ChoixS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.choix(p);
            return;
        }
        if (o instanceof PaquetsDuels.RdS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.rd(p.vue());
            return;
        }
        if (o instanceof PaquetsDuels.ScoresS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.scores(p.tableau());
            return;
        }
        if (o instanceof PaquetsDuels.EffetS2C p) {
            fr.heralisz.matchs.duels.client.DuelsClient.effet(p.effet(), p.x(), p.y(), p.z());
            return;
        }
    }
}
