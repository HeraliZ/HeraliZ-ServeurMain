package fr.heraliz.vehicules;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.ywzj.vehicle.entity.misc.VehiclePart;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.vehicle.structure.VehicleCubeOBB;

import java.util.List;
import java.util.Locale;

/**
 * Endroit où un joueur sort d'un véhicule, vérifié par le serveur (14/11 : sortie avec la touche S'accroupir, « position
 * de sortie sûre »).
 * <p><b>Avant</b> : Limitless Vehicle choisit seul l'endroit, sans regarder les blocs ({@code AbstractVehicle.removePassenger}) :
 * la place de sortie prévue par le véhicule (aucune dans nos 4 véhicules), sinon la porte la plus proche (fourgon,
 * camion), sinon 1 bloc à côté d'un flanc et 1 bloc plus haut que le bas du véhicule (buggy, moto). Véhicule collé à un
 * mur : joueur posé dans le mur, ou de l'autre côté (mur d'une case : dans la base d'un autre clan) ; dans un garage :
 * dans le mur.
 * <p><b>Maintenant</b> (mixin serveur {@code VehiculeSortieMixin}, à la fin de {@code getDismountLocationForPassenger},
 * quand Minecraft demande l'endroit juste avant d'y poser le joueur) : l'endroit du moteur est gardé s'il est sûr, à
 * savoir le corps du joueur debout n'y touche aucun bloc et aucun bloc solide ne le sépare de la place du joueur (deux
 * lignes : depuis la place, et à la hauteur de la sortie) ; sinon le même endroit un demi-bloc ou un bloc plus haut
 * (pente, trottoir) ; sinon, dans l'ordre et avec les mêmes contrôles : l'autre flanc, le flanc de la sortie du moteur
 * (au milieu), l'arrière, l'avant ; sans aucune place sûre, sur le véhicule (règle de Minecraft pour toute monture).
 * Jamais de l'autre côté d'un mur. Rien n'est changé pour une sortie déjà sûre (le cas normal en terrain dégagé).
 * <p>Seulement au moment d'une sortie (aucun travail à chaque tick) ; réglage {@code sortie_sure}
 * ({@link VehiculesConfig#SORTIE_SURE}), à false : endroit du moteur tel quel. Le jar d'ywzj n'est pas touché.
 */
public final class SortieSure {
    /** Au-dessus des pieds (blocs) : hauteur des lignes « place du joueur -> sortie » qui ne doivent toucher aucun bloc. */
    private static final double HAUTEUR_LIGNE = 0.5;
    /** Sortie essayée plus haut quand le corps du joueur y touche un bloc (pente, trottoir, marche). */
    private static final double[] LEVEES = {0.0, 0.5, 1.0};
    /** Sortie à plus de 8 blocs de la place du joueur : pas une sortie à côté du véhicule (autre mécanisme du moteur). */
    private static final double DISTANCE_MAX = 8.0;

    private SortieSure() {
    }

    /** Réglage sortie_sure (vrai par défaut ; vrai aussi tant que les réglages ne sont pas chargés). */
    public static boolean actif() {
        try {
            return VehiculesConfig.SORTIE_SURE.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.SORTIE_SURE.getDefault();
        }
    }

    /**
     * Endroit où poser {@code passager} qui sort de {@code vehicule} : {@code sortie} (choisi par le moteur) s'il est sûr,
     * sinon une autre place autour du véhicule (voir la classe). {@code sortie} tel quel pour tout ce qui n'est pas un
     * joueur sortant d'un véhicule, côté serveur. Le joueur est encore à sa place dans le véhicule à cet instant.
     */
    public static Vec3 verifier(AbstractVehicle vehicule, LivingEntity passager, Vec3 sortie) {
        if (sortie == null || !(passager instanceof Player) || vehicule instanceof VehiclePart || !actif()) return sortie;
        Level level = vehicule.level();
        if (level.isClientSide()) return sortie;
        Vec3 place = passager.position();
        if (place.distanceToSqr(sortie) > DISTANCE_MAX * DISTANCE_MAX) return sortie;
        try {
            Vec3 sure = essayer(level, passager, place, sortie);
            if (sure != null) return sure;
            for (Vec3 autre : autres(vehicule)) {
                sure = essayer(level, passager, place, autre);
                if (sure != null) {
                    noter(vehicule, passager, sortie, sure, "autre place");
                    return sure;
                }
            }
            Vec3 dessus = new Vec3(vehicule.getX(), vehicule.getBoundingBox().maxY, vehicule.getZ());
            noter(vehicule, passager, sortie, dessus, "aucune place sure : sur le vehicule");
            return dessus;
        } catch (RuntimeException ex) {
            HeralizVehicules.LOG.warn("[vehicules] sortie de {} : verification impossible, endroit du moteur garde",
                    passager.getName().getString(), ex);
            return sortie;
        }
    }

    /**
     * {@code point}, ou un demi-bloc ou un bloc plus haut : le premier où le corps du joueur debout ne touche aucun bloc et
     * qu'aucun bloc ne sépare de la place du joueur ; null s'il n'y en a pas. {@code point} lui-même s'il convient.
     */
    private static Vec3 essayer(Level level, LivingEntity passager, Vec3 place, Vec3 point) {
        EntityDimensions corps = passager.getDimensions(Pose.STANDING);
        if (!level.getWorldBorder().isWithinBounds(point.x, point.z)) return null;
        for (double levee : LEVEES) {
            Vec3 p = levee == 0.0 ? point : point.add(0.0, levee, 0.0);
            if (libre(level, passager, corps.makeBoundingBox(p)) && degage(level, passager, place, p)) return p;
        }
        return null;
    }

    /** Aucun bloc dans {@code corps} (comme les sorties sûres des montures de Minecraft). */
    private static boolean libre(Level level, LivingEntity passager, AABB corps) {
        for (VoxelShape forme : level.getBlockCollisions(passager, corps)) {
            if (!forme.isEmpty()) return false;
        }
        return true;
    }

    /**
     * Rien de solide entre la place du joueur et la sortie {@code p} : ni sur la ligne qui part de sa place, ni sur la ligne
     * à la hauteur de la sortie (un mur plus bas que la place, sous la ligne du haut, est vu par celle du bas).
     */
    private static boolean degage(Level level, LivingEntity passager, Vec3 place, Vec3 p) {
        Vec3 arrivee = p.add(0.0, HAUTEUR_LIGNE, 0.0);
        return ligneLibre(level, passager, place.add(0.0, HAUTEUR_LIGNE, 0.0), arrivee)
                && ligneLibre(level, passager, new Vec3(place.x, arrivee.y, place.z), arrivee);
    }

    private static boolean ligneLibre(Level level, LivingEntity passager, Vec3 de, Vec3 a) {
        return level.clip(new ClipContext(de, a, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, passager)).getType()
                == HitResult.Type.MISS;
    }

    /**
     * Autres places, dans l'ordre : l'autre flanc, le flanc de la sortie du moteur (au milieu), l'arrière, l'avant ; à 1 bloc
     * de la caisse et 1 bloc plus haut que le bas du véhicule, calculées comme la sortie du moteur (demi-taille de la caisse
     * principale, {@code relativeRotPos} : véhicule tourné, penché ou incliné compris).
     */
    private static List<Vec3> autres(AbstractVehicle vehicule) {
        VehicleCubeOBB cube = vehicule.getMainCubeOBB();
        if (cube == null) return List.of();
        Vector3f demi = cube.obb().extents();
        Vec3 base = vehicule.position();
        double flanc = demi.x + 1.0;
        double bout = demi.z + 1.0;
        return List.of(
                vehicule.relativeRotPos(base.add(-flanc, 1.0, 0.0), false),
                vehicule.relativeRotPos(base.add(flanc, 1.0, 0.0), false),
                vehicule.relativeRotPos(base.add(0.0, 1.0, -bout), false),
                vehicule.relativeRotPos(base.add(0.0, 1.0, bout), false));
    }

    /** Journal du serveur (rare : seulement quand l'endroit du moteur n'était pas sûr), pour le staff. */
    private static void noter(AbstractVehicle vehicule, LivingEntity passager, Vec3 sortie, Vec3 sure, String quoi) {
        HeralizVehicules.LOG.info("[vehicules] sortie de {} ({}) : endroit du moteur pas sur ({}), {} ({})",
                passager.getName().getString(), vehicule.getVehicleId(), coords(sortie), quoi, coords(sure));
    }

    private static String coords(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", v.x, v.y, v.z);
    }
}
