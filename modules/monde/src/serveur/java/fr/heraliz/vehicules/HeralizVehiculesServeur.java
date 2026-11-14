package fr.heraliz.vehicules;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie SERVEUR de {@link HeralizVehicules} (docs/METHODE_CLIENT_SERVEUR.md §5) : les écouteurs serveur que le
 * constructeur du @Mod inscrivait, dans l'ordre d'origine (réglages relus -> protection des blocs, objets retirés effacés,
 * règles des véhicules, commandes /vehicules), puis le suivi des véhicules posés (PV divisés, coffre retiré :
 * {@link SuiviVehicules}) et l'arrêt des véhicules sans conducteur (frein, moteur coupé : {@link ArretSansConducteur}).
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
        // ArretSansConducteur écoute un évènement du moteur des véhicules (VehicleMoveEvent) : inscrit au démarrage du
        // serveur, jamais pendant la construction des mods. Banc du 14/11 : s'y inscrire ici chargeait les classes de
        // Limitless Vehicle (AbstractVehicle) pendant que ce mod se construisait sur un autre fil ; le chargement échouait
        // (« Could not find parent AbstractVehicle ») et le serveur ne démarrait plus.
        MinecraftForge.EVENT_BUS.addListener(HeralizVehiculesServeur::auDemarrage);
    }

    private static boolean arretInscrit;

    private static void auDemarrage(ServerAboutToStartEvent e) {
        if (arretInscrit) return;
        arretInscrit = true;
        ArretSansConducteur.enregistrer(MinecraftForge.EVENT_BUS);
    }
}
