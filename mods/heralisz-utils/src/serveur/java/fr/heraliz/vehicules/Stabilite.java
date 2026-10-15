package fr.heraliz.vehicules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeConfigSpec;
import org.joml.Vector3f;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.entity.vehicle.WheeledVehicle;
import org.ywzj.vehicle.vehicle.PhysicsEngine;
import org.ywzj.vehicle.vehicle.structure.VehicleCubeOBB;

import java.util.ArrayList;
import java.util.List;

/**
 * Véhicules à roues stables et maniables (15/10, choix des propriétaires : « au moindre petit obstacle ils font des bonds
 * et se retournent »). Le moteur de Limitless Vehicle ({@code org.ywzj.vehicle.vehicle.PhysicsEngine}, côté serveur
 * seulement : les joueurs ne font que lisser ce que le serveur envoie) n'est pas touché ; les mixins serveur
 * {@code VehiculeStableMixin} et {@code VehiculeConduiteMixin} appellent ces méthodes, pour les véhicules à roues
 * ({@link WheeledVehicle} : moto, buggy, fourgon, camion).
 * <p>Ce que le moteur faisait, et ce qui change (section {@code physique} de {@link VehiculesConfig}) :
 * <ul>
 *   <li><b>Retournements</b> : à chaque tick, il décale le centre de gravité de (changement de vitesse × hauteur × 8)
 *   avant de chercher si le véhicule bascule. Un choc contre une dalle (vitesse 0,6 → 0) le déplaçait de près de 10 blocs
 *   vers l'avant : le véhicule pivotait sur son nez à 17° par tick. Ici le décalage est réduit ({@code inertie}) et borné
 *   à une part de la plus petite dimension du véhicule ({@code bascule_max}) : un choc seul ne retourne plus un véhicule
 *   posé à plat ; une pente trop forte ou un vide le font toujours basculer, moins vite ({@code rotation_max}).</li>
 *   <li><b>Petits obstacles</b> : tout ce qui dépasse la garde au sol arrêtait net le véhicule (vitesse vers l'avant mise
 *   à 0), puis il était hissé dessus d'un coup. Un obstacle de {@code petite_marche} au plus (dalle, neige, chemin) n'est
 *   plus un mur : le véhicule est hissé dessus (montée du moteur, {@code climb}) sans perdre sa vitesse ni prendre de
 *   dégâts de choc.</li>
 *   <li><b>Bonds</b> : les contacts avec le sol (appui en pente, véhicule enfoncé dans un bloc) ajoutaient jusqu'à 0,1 bloc
 *   par tick de vitesse vers le haut, plusieurs fois par tick. Ils ne peuvent plus dépasser {@code montee_max} (une vitesse
 *   vers le haut déjà là, d'une explosion par exemple, est gardée).</li>
 *   <li><b>Maniabilité</b> : à pleine vitesse, le moteur ne corrigeait plus que 1 % de la glissade par tick (virages sur la
 *   glace) : {@code adherence} ; et le volant du camion tournait en 20 ticks : {@code braquage_min}.</li>
 * </ul>
 * Réglage {@code stable} à false : moteur d'origine. Rien n'a été essayé en jeu.
 */
public final class Stabilite {
    /** Au-delà de cette inclinaison (degrés), un obstacle bas reste un mur (le véhicule n'est plus à plat). */
    private static final float INCLINAISON_MAX = 20f;
    /** En dessous (blocs), l'obstacle est le sol où le véhicule est déjà posé : pas de montée. */
    private static final double MONTEE_UTILE = 0.1;

    private Stabilite() {
    }

    private static double valeur(ForgeConfigSpec.DoubleValue reglage) {
        try {
            return reglage.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return reglage.getDefault();
        }
    }

    /** Correction active (réglage stable, vrai par défaut ; vrai aussi tant que les réglages ne sont pas chargés). */
    public static boolean actif() {
        try {
            return VehiculesConfig.PHYSIQUE_STABLE.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return true;
        }
    }

    private static boolean concerne(PhysicsEngine moteur) {
        return moteur.vehicle instanceof WheeledVehicle && actif();
    }

    /**
     * Points de contact passés au calcul des chocs ({@code motionByImpact}) : sans ceux de l'avant (en marche avant) ou de
     * l'arrière (en marche arrière) qui touchent un obstacle bas, et le véhicule est hissé sur l'obstacle (comme le fait
     * la montée du moteur, {@code climb}, mais sans l'arrêt net qui la précédait). La liste d'origine n'est pas modifiée.
     * Les flancs ne sont jamais concernés : longer un trottoir de dalles ne fait pas monter dessus.
     */
    public static List<VehicleCubeOBB.CubePoint> sansPetitesMarches(PhysicsEngine moteur, List<VehicleCubeOBB.CubePoint> points) {
        if (points == null || points.isEmpty() || !concerne(moteur)) return points;
        double marche = valeur(VehiculesConfig.PETITE_MARCHE);
        AbstractVehicle vehicule = moteur.vehicle;
        if (marche <= 0 || !vehicule.onGround()
                || Math.abs(vehicule.getXRot()) > INCLINAISON_MAX || Math.abs(vehicule.getZRot()) > INCLINAISON_MAX) {
            return points;
        }
        VehicleCubeOBB cube = vehicule.getMainCubeOBB();
        if (cube == null) return points;
        Vec3 avant = vehicule.getLookAngle();
        double sens = moteur.velocity.x * avant.x + moteur.velocity.z * avant.z;
        if (Math.abs(sens) < 0.01) return points;
        VehicleCubeOBB.CubeFace face = sens > 0 ? VehicleCubeOBB.CubeFace.FRONT : VehicleCubeOBB.CubeFace.BACK;
        float demiHauteur = cube.obb().extents().y;
        List<VehicleCubeOBB.CubePoint> gardes = null;
        double monter = 0;
        for (int i = 0; i < points.size(); i++) {
            VehicleCubeOBB.CubePoint point = points.get(i);
            double hauteur = point.cubeFace() == face ? hauteurObstacle(vehicule, point, demiHauteur) : Double.NaN;
            if (hauteur <= marche) {
                if (gardes == null) gardes = new ArrayList<>(points.subList(0, i));
                monter = Math.max(monter, hauteur);
            } else if (gardes != null) {
                gardes.add(point);
            }
        }
        if (gardes == null) return points;
        if (monter > MONTEE_UTILE) vehicule.setPos(vehicule.getX(), vehicule.getY() + monter, vehicule.getZ());
        return gardes;
    }

    /** Hauteur du haut du bloc touché par ce point au-dessus du dessous du véhicule (NaN si inconnue). */
    private static double hauteurObstacle(AbstractVehicle vehicule, VehicleCubeOBB.CubePoint point, float demiHauteur) {
        VehicleCubeOBB.CubePointContext contexte = point.cubePointContext;
        if (contexte == null) return Double.NaN;
        Vec3 coin = contexte.blockPos();
        BlockState etat = contexte.blockState();
        if (coin == null || etat == null) return Double.NaN;
        VoxelShape forme = etat.getCollisionShape(vehicule.level(), BlockPos.containing(coin));
        double haut = coin.y + (forme.isEmpty() ? 1.0 : forme.max(Direction.Axis.Y));
        double dessous = point.cachedWorldPos().y - (point.obbLocalPos().y + demiHauteur);
        return haut - dessous;
    }

    /** Après le calcul des chocs : la vitesse vers le haut ne dépasse plus {@code montee_max} (ni celle d'avant). */
    public static void plafonnerMontee(PhysicsEngine moteur, float vitesseAvant) {
        if (!concerne(moteur)) return;
        float plafond = Math.max(vitesseAvant, (float) valeur(VehiculesConfig.MONTEE_MAX));
        if (moteur.velocity.y > plafond) moteur.velocity.y = plafond;
    }

    /**
     * Avant le calcul de la bascule : le moteur lit l'accélération du tick comme (velocity − velocityO) ; velocityO est
     * recalé pour que le décalage du centre de gravité qu'il en tire soit réduit et borné. velocityO est recopié de
     * velocity au début de chaque tick ({@code beginTick}) et ne sert qu'ici : rien d'autre ne change.
     */
    public static void adoucirBascule(PhysicsEngine moteur) {
        if (!concerne(moteur)) return;
        moteur.maxRotV = (float) valeur(VehiculesConfig.ROTATION_MAX);
        VehicleCubeOBB cube = moteur.vehicle.getMainCubeOBB();
        if (cube == null) return;
        Vector3f acceleration = new Vector3f(moteur.velocity).sub(moteur.velocityO);
        float facteur = (float) valeur(VehiculesConfig.INERTIE);
        float decalage = acceleration.length() * (float) (cube.height * 8.0) * facteur;
        float maximum = (float) (valeur(VehiculesConfig.BASCULE_MAX) * Math.min(cube.getWidth(), cube.getDepth()));
        if (decalage > maximum && decalage > 1.0E-6f) facteur *= maximum / decalage;
        moteur.velocityO.set(new Vector3f(moteur.velocity).sub(acceleration.mul(facteur)));
    }

    /** Volant : jamais plus lent que {@code braquage_min} (le moteur relit turnStep à chaque tick). */
    public static void braquage(WheeledVehicle vehicule) {
        if (!actif()) return;
        float minimum = (float) valeur(VehiculesConfig.BRAQUAGE_MIN);
        if (vehicule.turnStep < minimum) vehicule.turnStep = minimum;
    }

    /** Plancher de l'adhérence à pleine vitesse (constante 0.01 de {@code WheeledVehicle.tickMove}). */
    public static double adherence(double moteur) {
        return actif() ? Math.max(moteur, valeur(VehiculesConfig.ADHERENCE)) : moteur;
    }
}
