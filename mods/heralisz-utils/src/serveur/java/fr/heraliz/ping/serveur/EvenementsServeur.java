package fr.heraliz.ping.serveur;

import fr.heraliz.ping.server.PingCommandes;
import fr.heraliz.ping.server.PingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Evenements serveur de HeraliZ Ping (repris tels quels de l'ancien @Mod HeralizPing) : commandes, tick serveur,
 * connexion, deconnexion, arret. Inscrits par {@link PartieServeur} sur le bus Forge, a la place qu'ils avaient dans le
 * constructeur du mod (pas de @Mod.EventBusSubscriber : l'ordre d'inscription reste celui d'origine).
 */
public final class EvenementsServeur {
    private EvenementsServeur() {
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        PingCommandes.enregistrer(e.getDispatcher());
    }

    /** Rien a faire tant qu'aucun ping n'est actif : {@link PingService#tick} sort tout de suite. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) PingService.tick(e.getServer());
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PingService.connexion(p);
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PingService.deconnexion(p);
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        PingService.vider();
    }
}
