package fr.heraliz.vehicules;

import net.minecraft.resources.ResourceLocation;
import org.ywzj.vehicle.api.YwzjVehicleAPI;
import org.ywzj.vehicle.custom.vehicle.BaseVehicleData;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Passagers des véhicules : ils prennent les dégâts comme à pied (11/11, demande du propriétaire : « sur un véhicule tu ne
 * prends pas les dégâts » ; le véhicule garde ses propres PV).
 * <ul>
 *   <li><b>Vraie cause</b> : Limitless Vehicle annule TOUS les dégâts ({@code LivingHurtEvent}) d'une créature assise dans
 *   un véhicule dont les données disent {@code protect_passenger}, vrai par défaut dans le moteur ; le pack réduit de Fuse
 *   ne le met à faux que pour le buggy. Moto, camion et fourgon : passagers invulnérables à tout (balles, coups,
 *   explosions, zombies, minigun de l'hélicoptère, feu…) ; le coup se voyait (recul, rougeur), sans perte de vie. Le
 *   moteur recopie ce choix dans le véhicule (champ public {@code protectPassenger}) à chaque initialisation : le serveur
 *   le remet selon le réglage {@code passagers_proteges} (faux par défaut) à l'arrivée du véhicule, puis une fois par
 *   seconde ({@link SuiviVehicules}). Pas de mixin : le jar du moteur reste intact.</li>
 *   <li><b>Balles</b> : un passager caché dans la boîte du véhicule (fourgon, cabine du camion, jambes et buste du motard)
 *   n'était jamais touché, même sans la règle ci-dessus : TaCZ ne prend que la première entité sur le trajet, la
 *   carrosserie. Avec {@code balles_traversent} (vrai par défaut), une balle dont le trajet passe par le corps d'un passager
 *   le touche lui, tête comprise, au lieu du véhicule (mixin serveur {@code TaczPassagersMixin}, jar de TaCZ intact) ;
 *   ailleurs, le véhicule prend la balle.</li>
 * </ul>
 */
public final class Passagers {
    private Passagers() {
    }

    /** Réglage {@code passagers_proteges} : règle d'origine du moteur (faux par défaut). */
    public static boolean proteges() {
        try {
            return VehiculesConfig.PASSAGERS_PROTEGES.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.PASSAGERS_PROTEGES.getDefault();
        }
    }

    /** Réglage {@code balles_traversent} : une balle qui passe par le corps d'un passager le touche (vrai par défaut). */
    public static boolean ballesTraversent() {
        try {
            return VehiculesConfig.BALLES_TRAVERSENT.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.BALLES_TRAVERSENT.getDefault();
        }
    }

    /**
     * Protection des passagers du véhicule selon le réglage : aucune (faux), ou celle des données du moteur (vrai). Ne touche
     * pas un débris d'épave (pas de passager).
     */
    static void appliquer(AbstractVehicle vehicule) {
        boolean voulu = proteges() && protectionMoteur(vehicule);
        if (vehicule.protectPassenger != voulu) vehicule.protectPassenger = voulu;
    }

    private static boolean protectionMoteur(AbstractVehicle vehicule) {
        ResourceLocation id = vehicule.getVehicleId();
        if (id == null) return false;
        return YwzjVehicleAPI.getVehicleDataManager().getVehicleData(id).map(BaseVehicleData::isProtectPassenger).orElse(false);
    }
}
