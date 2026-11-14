package fr.heraliz.vehicules;

import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import org.ywzj.vehicle.api.event.VehicleMoveEvent;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;
import org.ywzj.vehicle.vehicle.PhysicsEngine;

/**
 * Véhicule sans conducteur : il freine tout seul jusqu'à l'arrêt et coupe son moteur (14/11, choix du propriétaire).
 * <p><b>Avant</b> : quand la place du conducteur se vidait (sortie, mort, mise à terre, déconnexion, téléportation),
 * Limitless Vehicle lâchait toutes les commandes ({@code WheeledVehicle.tickMove} : {@code controlUnit.reset()}) : le
 * véhicule continuait sur sa lancée, freiné seulement par le frottement des roues, et son moteur restait allumé,
 * consommant l'essence sans fin, même garé.
 * <p><b>Maintenant</b>, à chaque tick du serveur et pour chaque véhicule à roues sans conducteur (événement
 * {@link VehicleMoveEvent} du moteur, envoyé à la fin du tick de chaque véhicule, après sa physique ; aucun mixin) :
 * <ul>
 *   <li><b>moteur coupé</b> ({@code toggleEngine(false)}, comme la touche Moteur du véhicule) : il s'arrête de tourner en
 *   5 secondes au plus (puissance qui retombe, essence consommée de moins en moins), puis plus aucune essence. Le
 *   conducteur qui remonte le redémarre comme d'habitude (le moteur rallume à la montée sur la place du conducteur) ;</li>
 *   <li><b>frein</b>, roues au sol : la vitesse au sol perd chaque tick ce que le frein du véhicule lui ôte quand le
 *   conducteur freine (force de freinage du véhicule ÷ sa masse, essence comprise, comme le calcule le moteur), jusqu'à
 *   l'arrêt (la vitesse qu'il aura au tick suivant : le moteur avance chaque véhicule de {@code getDeltaMovement()} au
 *   début de son tick). En l'air, rien (comme le frein du moteur).</li>
 * </ul>
 * Réglage {@code arret_sans_conducteur} ({@link VehiculesConfig#ARRET_SANS_CONDUCTEUR}) ; à false : comportement du
 * moteur d'origine. Coût : quelques tests par véhicule chargé et par tick, rien de plus tant qu'un conducteur est à bord.
 */
public final class ArretSansConducteur {
    /** En dessous (blocs par tick, 0,36 km/h), le frein arrête net le véhicule ; plus lent, il n'est plus touché. */
    private static final double ARRET = 0.005;

    private ArretSansConducteur() {
    }

    static void enregistrer(IEventBus bus) {
        bus.addListener(ArretSansConducteur::onMouvement);
    }

    /** Réglage arret_sans_conducteur (vrai par défaut ; vrai aussi tant que les réglages ne sont pas chargés). */
    public static boolean actif() {
        try {
            return VehiculesConfig.ARRET_SANS_CONDUCTEUR.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.ARRET_SANS_CONDUCTEUR.getDefault();
        }
    }

    private static void onMouvement(VehicleMoveEvent e) {
        AbstractVehicle vehicule = e.getVehicle();
        // monde du serveur seulement (en solo, le jeu et le serveur partagent les évènements : la copie du véhicule chez le
        // joueur suit le serveur, on n'y touche pas)
        if (vehicule.level().isClientSide()) return;
        if (!(vehicule instanceof WheeledVehicle roues) || vehicule.getDriver() != null || !actif()) return;
        if (vehicule.isEngineOn()) vehicule.toggleEngine(false);
        if (!vehicule.onGround()) return;
        Vec3 vitesse = vehicule.getDeltaMovement();
        double carre = vitesse.x * vitesse.x + vitesse.z * vitesse.z;
        if (carre < ARRET * ARRET) return; // arrêté (restes de la physique : laissés au moteur, rien d'écrit à chaque tick)
        double sol = Math.sqrt(carre);
        double reste = sol - frein(roues);
        if (reste <= ARRET) {
            vehicule.setDeltaMovement(0.0, vitesse.y, 0.0);
        } else {
            double k = reste / sol;
            vehicule.setDeltaMovement(vitesse.x * k, vitesse.y, vitesse.z * k);
        }
    }

    /** Vitesse ôtée par tick par le frein du véhicule (blocs par tick), comme dans {@code WheeledVehicle.tickMove}. */
    static double frein(WheeledVehicle vehicule) {
        PhysicsEngine moteur = vehicule.physicsEngine;
        float masse = moteur == null || moteur.physicsInfo == null ? 1.0f : moteur.physicsInfo.mass;
        return vehicule.brakeForce / Math.max(masse, 0.001f);
    }
}
