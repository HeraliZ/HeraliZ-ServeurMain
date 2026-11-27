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
        ApparitionVehicules.enregistrer(MinecraftForge.EVENT_BUS);   // 25/11 : véhicules qui apparaissent un peu partout
        CoffresVehicules.enregistrer(MinecraftForge.EVENT_BUS);      // 27/11 : coffre des véhicules (Maj + clic droit)
        // ArretSansConducteur (évènement VehicleMoveEvent du moteur) et SortieSure (sortie jamais dans un mur) : inscrits au
        // démarrage du serveur, quand tous les mods sont construits ; ils ne chargent ainsi aucune classe de Limitless
        // Vehicle pendant sa construction (banc du 14/11)
        MinecraftForge.EVENT_BUS.addListener(HeralizVehiculesServeur::auDemarrage);
    }

    private static boolean arretInscrit;

    private static void auDemarrage(ServerAboutToStartEvent e) {
        if (arretInscrit) return;
        arretInscrit = true;
        ArretSansConducteur.enregistrer(MinecraftForge.EVENT_BUS);
        SortieSure.enregistrer(MinecraftForge.EVENT_BUS);
        CoffresVehicules.enregistrerMoteur(MinecraftForge.EVENT_BUS); // coups sur les véhicules : coffre d'une épave au sol
    }
}
