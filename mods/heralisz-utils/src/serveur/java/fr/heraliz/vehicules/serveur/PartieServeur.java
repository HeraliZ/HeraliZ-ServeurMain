package fr.heraliz.vehicules.serveur;

import fr.heralisz.utils.pont.Partie;
import fr.heraliz.vehicules.HeralizVehiculesServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_vehicules (jar fusionné HeraliZ Utils, docs/METHODE_CLIENT_SERVEUR.md §4) : règles des
 * véhicules (dimensions interdites, tag de combat, descente avant un changement de dimension, explosions sans casse de
 * blocs, essence obligatoire), objets du moteur retirés effacés, commandes /vehicules, coffre retiré
 * (SuiviVehicules ; ouverture du coffre refusée par le mixin serveur VehiculeSansCoffreMixin). Écouteurs dans l'ordre
 * d'origine.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        HeralizVehiculesServeur.init(busMod);
    }
}
