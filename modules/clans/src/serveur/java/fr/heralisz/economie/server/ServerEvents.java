package fr.heralisz.economie.server;

import fr.heralisz.economie.EconomieConfig;
import fr.heralisz.economie.api.Compte;
import fr.heralisz.economie.network.EconomieNet;
import fr.heralisz.economie.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Evenements serveur : a la connexion, le compte est cree (solde de depart) ou son dernier nom mis a jour, puis le
 * client recoit les reglages et son solde (un operateur est aussi prevenu si la sauvegarde est bloquee) ; a la
 * deconnexion, la cadence de /herals payer est oubliee.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ComptesStore s = ComptesStore.get();
        if (s == null) return;
        Compte c = s.compte(p.getUUID(), p.getGameProfile().getName());
        envoyerTout(p, c.solde());
        // sauvegarde bloquee (fichier illisible au demarrage) : l'operateur qui arrive est prevenu tout de suite
        if (p.hasPermissions(2)) s.alerter(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        EconomieCommands.oublierCadence(e.getEntity().getUUID());
    }

    /** Reglages + solde (sans variation) : connexion, /eco recharger. */
    public static void envoyerTout(ServerPlayer p, long solde) {
        EconomieNet.toPlayer(p, new Packets.ReglagesS2C(EconomieConfig.NOM_SINGULIER.get(), EconomieConfig.NOM_PLURIEL.get(),
                EconomieConfig.SYMBOLE.get(), EconomieConfig.HUD_PERMANENT.get()));
        EconomieNet.toPlayer(p, new Packets.SoldeS2C(solde, 0, ""));
    }
}
