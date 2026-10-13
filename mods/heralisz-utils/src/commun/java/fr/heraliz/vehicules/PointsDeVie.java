package fr.heraliz.vehicules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.ywzj.vehicle.api.YwzjVehicleAPI;
import org.ywzj.vehicle.custom.vehicle.BaseVehicleData;
import org.ywzj.vehicle.entity.misc.VehiclePart;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.Optional;

/**
 * Points de vie des véhicules divisés par 5 (choix des propriétaires ; réglage serveur {@code pv_pourcent}, 20 par défaut).
 * <p>Limitless Vehicle lit le maximum dans les données du véhicule (moto 100, buggy et fourgon 300, camion 500) une seule
 * fois, à la pose, puis le garde dans l'entité (NBT « MaxHealth »). Le SERVEUR ramène chaque véhicule au maximum voulu
 * (données du moteur × pv_pourcent / 100) à son arrivée dans le monde puis à chaque contrôle (partie serveur,
 * {@code SuiviVehicules}) : un véhicule neuf l'a avant que les joueurs ne le voient bouger, un véhicule déjà posé (ancien
 * maximum) y passe en gardant son pourcentage de vie (200/300 devient 40/60). Sans effet si le maximum est déjà le bon
 * (rien n'est divisé deux fois). Les PV restent décidés par le serveur (donnée synchronisée du moteur) ; le jeu du joueur
 * fait le même calcul à l'arrivée d'un véhicule, seulement pour ne pas afficher de faux dégâts (« -240 ») le temps que les
 * PV du serveur arrivent ({@code client.IndicateurDegats}).
 * <p>Pas touchés : les débris d'une épave ({@code VehiclePart}, leurs PV sont ceux de la pièce) ; les PV des pièces
 * (roues…) ; le seuil de blindage du moteur (un coup sous le seuil n'enlève que 0,1 PV).
 */
public final class PointsDeVie {
    private PointsDeVie() {
    }

    /** Maximum voulu, en pour cent de celui du moteur (réglage du serveur, envoyé aux joueurs à la connexion). */
    public static int pourcent() {
        try {
            return VehiculesConfig.PV_POURCENT.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.PV_POURCENT.getDefault();
        }
    }

    /** PV max du moteur pour ce véhicule (données du véhicule), ou -1 s'il n'en a pas. */
    public static float maxMoteur(AbstractVehicle vehicule) {
        ResourceLocation id = vehicule.getVehicleId();
        if (id == null) return -1;
        Optional<BaseVehicleData> donnees = YwzjVehicleAPI.getVehicleDataManager().getVehicleData(id);
        return donnees.isPresent() ? donnees.get().getMaxHealth() : -1;
    }

    /**
     * Ramène le véhicule à son maximum voulu, en gardant son pourcentage de vie. Rien pour un débris d'épave ou un véhicule
     * pas encore initialisé par le moteur (PV négatifs : posé à l'instant, réglé au passage suivant).
     */
    public static void ajuster(AbstractVehicle vehicule) {
        if (vehicule instanceof VehiclePart) return;
        float max = vehicule.getMaxHealth();
        float pv = vehicule.getHealth();
        if (max <= 0 || pv < 0) return;
        float base = maxMoteur(vehicule);
        if (base <= 0) return;
        float cible = base * pourcent() / 100f;
        if (Math.abs(max - cible) < 0.01f) return;
        vehicule.setMaxHealth(cible);
        vehicule.setHealth(pv * cible / max);
        Entity e = vehicule;
        if (!e.level().isClientSide()) {
            HeralizVehicules.LOG.info("[vehicules] {} : PV {}/{} -> {}/{}", vehicule.getVehicleId(),
                    Math.round(pv), Math.round(max), Math.round(vehicule.getHealth()), Math.round(cible));
        }
    }
}
