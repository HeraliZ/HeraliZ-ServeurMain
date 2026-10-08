package fr.heralisz.tags.serveur;

import fr.heralisz.tags.server.TagCommands;
import fr.heralisz.tags.server.TagStore;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * Cycle de vie serveur des tags (methodes d'origine de HeraliszTags, inscrites sur le bus Forge par la partie serveur) :
 * stockage, commandes, sauvegarde et expiration des tags poses, etat envoye a la connexion.
 */
public final class EvenementsServeur {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        TagStore.charger(e.getServer());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        TagStore.arreter();
        fr.heralisz.tags.server.TagService.reinitialiser();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        TagCommands.register(e.getDispatcher());
    }

    /** Sauvegarde automatique (60 s) et expiration des tags poses. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        TagStore.tick();
        fr.heralisz.tags.server.TagService.tick(e.getServer());
    }

    /** A la connexion : le joueur recoit ses tags debloques et sa roue. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) fr.heralisz.tags.server.TagService.envoyerEtat(sp);
    }

    /** A la deconnexion : cadences et limiteurs du joueur oublies. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        fr.heralisz.tags.server.TagService.oublier(e.getEntity().getUUID());
    }
}
