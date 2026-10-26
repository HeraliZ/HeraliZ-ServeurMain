package fr.heraliz.usure.serveur;

import fr.heralisz.utils.pont.Partie;
import fr.heraliz.usure.Contenu;
import fr.heraliz.usure.HeralizUsure;
import fr.heraliz.usure.HeralizUsureServeur;
import fr.heraliz.usure.KitReparationServeur;
import fr.heraliz.usure.ReglesServeur;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de heraliz_usure (jar fusionné HeraliZ Utils, docs/METHODE_CLIENT_SERVEUR.md §4) : kits qui réparent
 * (usine, branchée ici pendant le constructeur @Mod, avant les registres), règles du serveur vues par le code commun,
 * puis les écouteurs serveur dans l'ordre d'origine (tirs, réglages, conversion Gun Durability, commandes) et le paquet
 * de l'écran d'administration.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Contenu.KIT_USINE.serveur(KitReparationServeur::new);
        // comme à l'origine (valeur par défaut ReglesServeur::get) ; la partie client, chargée après, la remplace
        HeralizUsure.reglesClient(ReglesServeur::get);
        HeralizUsureServeur.init();
    }
}
