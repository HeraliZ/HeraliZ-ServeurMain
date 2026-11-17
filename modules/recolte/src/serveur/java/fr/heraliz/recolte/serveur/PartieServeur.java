package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.entites.ArbreServeur;
import fr.heraliz.recolte.entites.EntitesRecolte;
import fr.heraliz.recolte.entites.GisementServeur;
import fr.heraliz.recolte.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Récolte (docs/METHODE_CLIENT_SERVEUR.md §4), chargée par {@code Parties.serveur} : branche
 * la logique des entités (coups, étapes, chute), l'apparition, les réglages et les commandes.
 */
public final class PartieServeur implements Partie {
    /** Ticks entre deux coups d'œil à la date de recolte.json (5 s ; la date elle-même est relevée sur un autre fil). */
    private static final int SURVEILLANCE = 100;
    private static int ticks;

    @Override
    public void init(IEventBus busMod) {
        EntitesRecolte.GISEMENT_USINE.serveur(GisementServeur::new);
        EntitesRecolte.ARBRE_USINE.serveur(ArbreServeur::new);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::demarrage);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::demarre);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::arret);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::tick);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::depart);
        // une ressource sans place connue n'entre pas dans le monde : refusée avant que les autres mods ne la voient
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, EntityJoinLevelEvent.class, Apparition::arrivee);
        MinecraftForge.EVENT_BUS.addListener(PartieServeur::commandes);
    }

    /** Avant le chargement des mondes : le barème est lu (les ressources chargées avec la carte en ont besoin). */
    private static void demarrage(ServerAboutToStartEvent e) {
        ticks = 0;
        Reglages.charger();
    }

    private static void demarre(ServerStartedEvent e) {
        Apparition.demarrer(e.getServer());
    }

    private static void arret(ServerStoppedEvent e) {
        Apparition.arreter();
        Coups.vider();
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
            return;
        }
        if (++ticks >= SURVEILLANCE) {
            ticks = 0;
            if (Reglages.surveiller()) {
                Apparition.reglagesChanges(e.getServer());
            }
        }
        Apparition.tick(e.getServer());
    }

    private static void depart(PlayerEvent.PlayerLoggedOutEvent e) {
        Coups.oublier(e.getEntity().getUUID());
    }

    private static void commandes(RegisterCommandsEvent e) {
        Commandes.enregistrer(e);
    }
}
