package fr.heraliz.vehicules;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de {@link HeralizVehicules} (docs/METHODE_CLIENT_SERVEUR.md §5) : les écouteurs serveur que le
 * constructeur du @Mod inscrivait, dans l'ordre d'origine (réglages relus -> protection des blocs, objets retirés effacés,
 * règles des véhicules, commandes /vehicules), puis le suivi des véhicules posés (coffre retiré :
 * {@link SuiviVehicules}).
 */
public final class HeralizVehiculesServeur {
    private HeralizVehiculesServeur() {
    }

    /** Appelé par serveur.PartieServeur, pendant le constructeur du @Mod (à la place d'origine des écouteurs). */
    public static void init(IEventBus modBus) {
        modBus.addListener(Regles::onConfig);
        ContenuRetireServeur.enregistrer(MinecraftForge.EVENT_BUS);
        Regles.enregistrer(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.addListener(Commandes::onRegister);
        SuiviVehicules.enregistrer(MinecraftForge.EVENT_BUS);
    }
}
