package fr.heraliz.vehicules;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pas de sortie volontaire d'un véhicule lancé (14/11, choix du propriétaire) : au-dessus de {@code sortie_vitesse_max_kmh}
 * (15 km/h par défaut, {@link VehiculesConfig#SORTIE_VITESSE_MAX_KMH} ; 0 : pas de limite), le serveur refuse la demande
 * de sortie, pour le conducteur comme pour les passagers, et le joueur lit « Trop vite pour sortir : ralentis sous
 * 15 km/h » au-dessus de sa barre (le chiffre est celui du réglage).
 * <ul>
 *   <li><b>Sortie volontaire</b> : la demande de sortie du moteur des véhicules (paquet {@code ClientVehicleAction},
 *   {@code leaveVehicle}), envoyée par la touche S'accroupir ({@code client.SortieAccroupi}) ou par la touche « Sortir »
 *   du moteur. Contrôlée par le mixin serveur {@code VehiculeSortieMixin}, au début de
 *   {@code AbstractVehicle.onClientVehicleAction}, seul endroit où le serveur reçoit cette demande : c'est lui qui
 *   décide, quoi que le jeu du joueur envoie.</li>
 *   <li><b>Jamais pour une sortie forcée</b> : mise à terre, mort, téléportation, véhicule détruit, déconnexion,
 *   changement de monde passent par {@code stopRiding} directement, sans ce paquet ; ils ne sont pas touchés.</li>
 *   <li><b>Vitesse</b> : vitesse au sol (horizontale) du véhicule où le joueur est assis, celle qu'il aura au prochain
 *   tick (le moteur avance chaque véhicule de {@code getDeltaMovement()} au début de son tick), en km/h comme le moteur
 *   l'affiche pour ses hélicoptères (blocs par tick × 72). Nos 4 véhicules montent à 58 km/h.</li>
 *   <li>Touche gardée enfoncée : le jeu redemande la sortie toutes les 10 ticks ; le joueur sort dès que le véhicule
 *   est assez lent, et le message n'est répété qu'une fois toutes les {@value #ESPACE_MESSAGES} ticks au plus.</li>
 * </ul>
 */
public final class SortieVitesse {
    /** Blocs par tick -> km/h : 20 ticks par seconde × 3,6. */
    static final double KMH_PAR_BLOC_TICK = 72.0;
    /** Au plus un message de refus toutes les 30 ticks (1,5 s) par joueur, même touche enfoncée. */
    static final long ESPACE_MESSAGES = 30;
    /** Heure (temps de jeu du monde) du dernier message de refus, par joueur ; oublié avec le joueur. */
    private static final Map<ServerPlayer, Long> DERNIER_MESSAGE = new WeakHashMap<>();

    private SortieVitesse() {
    }

    /** Réglage sortie_vitesse_max_kmh (15 par défaut, aussi tant que les réglages ne sont pas chargés ; 0 : pas de limite). */
    public static int seuilKmh() {
        try {
            return VehiculesConfig.SORTIE_VITESSE_MAX_KMH.get();
        } catch (IllegalStateException pasEncoreCharge) {
            return VehiculesConfig.SORTIE_VITESSE_MAX_KMH.getDefault();
        }
    }

    /** Vitesse au sol (horizontale) du véhicule, en km/h. */
    public static double vitesseKmh(AbstractVehicle vehicule) {
        Vec3 v = vehicule.getDeltaMovement();
        return Math.sqrt(v.x * v.x + v.z * v.z) * KMH_PAR_BLOC_TICK;
    }

    /**
     * Demande de sortie volontaire de {@code joueur} : permise si son véhicule roule au plus au seuil (ou sans seuil) ;
     * sinon refusée, avec le message dans la barre d'action (une fois toutes les 1,5 s au plus).
     */
    public static boolean permise(Player joueur) {
        if (!(joueur instanceof ServerPlayer sp) || !(sp.getVehicle() instanceof AbstractVehicle vehicule)) return true;
        int seuil = seuilKmh();
        if (seuil <= 0 || vitesseKmh(vehicule) <= seuil) return true;
        long maintenant = sp.level().getGameTime();
        Long dernier = DERNIER_MESSAGE.get(sp);
        if (dernier == null || maintenant - dernier >= ESPACE_MESSAGES || maintenant < dernier) {
            DERNIER_MESSAGE.put(sp, maintenant);
            sp.displayClientMessage(Component.translatableWithFallback("heraliz_vehicules.sortie.trop_vite",
                    "Trop vite pour sortir : ralentis sous %s km/h", seuil), true);
        }
        return false;
    }
}
