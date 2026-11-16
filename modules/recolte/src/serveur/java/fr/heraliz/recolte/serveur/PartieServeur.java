package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Récolte (docs/METHODE_CLIENT_SERVEUR.md §4), chargée par {@code Parties.serveur} : branche
 * la logique des entités (coups, étapes, chute), l'apparition, les réglages et les commandes.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
    }
}
