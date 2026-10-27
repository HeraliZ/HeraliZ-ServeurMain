package fr.heraliz.evenements.serveur;

import fr.heraliz.evenements.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Événements : déclare chaque événement au Directeur et branche le Directeur et les commandes.
 * Un nouvel événement s'ajoute ici (une ligne) et suit la liste de docs/EVENEMENTS.md.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        // fil d'écriture des fichiers des événements (attendu à l'arrêt du serveur)
        EcrituresFond.init();
        Directeur.enregistrer(new Largage());
        Directeur.enregistrer(new Helico());
        MinecraftForge.EVENT_BUS.register(Directeur.class);
        MinecraftForge.EVENT_BUS.register(Commandes.class);
    }
}
