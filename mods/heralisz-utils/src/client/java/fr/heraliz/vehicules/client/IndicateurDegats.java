package fr.heraliz.vehicules.client;

import fr.heraliz.vehicules.PointsDeVie;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.ywzj.vehicle.client.gui.VehicleHitIndicatorOverlay;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * Plus d'affichage des dégâts faits à un véhicule (choix des propriétaires). Quand un joueur touche un véhicule, Limitless
 * Vehicle (moteur des véhicules) affiche chez lui, pendant 3 secondes :
 * <ul>
 *   <li>un panneau sur le côté de l'écran (aux 7/8 de la largeur, sous le milieu) : « TOUCHÉ / DÉGÂTS / CRITIQUE », les
 *   dégâts faits et une petite vue 3D du véhicule avec la trajectoire des balles en rouge (calque
 *   {@code ywzj_vehicle:vehicle_hit_indicator}) : le calque n'est plus dessiné ;</li>
 *   <li>la barre de vie du véhicule touché, au-dessus de lui, quand on ne le regarde plus (calque {@code ywzj_vehicle:vehicle},
 *   tirée de la même liste de coups) : la liste des coups est vidée à chaque image, avant les calques du moteur.</li>
 * </ul>
 * Restent : les impacts de balles sur la carrosserie et la barre de vie d'un véhicule qu'on regarde (réglage serveur
 * {@code showVehicleInfoDistance} de ywzj_vehicle-server.toml, 0 pour la retirer à tous).
 * Rien côté serveur : l'affichage est décidé chez le joueur (réglage {@code hitIndicator} de son
 * ywzj_vehicle-common.toml, que le serveur n'envoie pas, et qui couperait aussi les impacts).
 * <p>
 * Aussi : PV d'un véhicule qui arrive chez le joueur ramenés tout de suite au maximum du serveur ({@link PointsDeVie}, même
 * calcul) : le moteur lui donne d'abord ses anciens PV (300/300), et la barre de vie aurait affiché un faux « -240 » le
 * temps que les PV du serveur (60/60) arrivent. Affichage seulement : les PV restent ceux du serveur.
 */
public final class IndicateurDegats {
    /** Calque du panneau des dégâts (nom donné par le moteur à l'inscription de ses calques). */
    static final ResourceLocation PANNEAU = new ResourceLocation("ywzj_vehicle", "vehicle_hit_indicator");

    private IndicateurDegats() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(IndicateurDegats::avantHud);
        MinecraftForge.EVENT_BUS.addListener(IndicateurDegats::avantCalque);
        MinecraftForge.EVENT_BUS.addListener(IndicateurDegats::aLArrivee);
    }

    /** Avant tous les calques de l'image : plus de coups en mémoire (barre de vie du véhicule touché, panneau). */
    private static void avantHud(RenderGuiEvent.Pre e) {
        if (!VehicleHitIndicatorOverlay.events.isEmpty()) VehicleHitIndicatorOverlay.events.clear();
    }

    private static void avantCalque(RenderGuiOverlayEvent.Pre e) {
        if (PANNEAU.equals(e.getOverlay().id())) e.setCanceled(true);
    }

    /**
     * Véhicule reçu du serveur : Forge l'ajoute au monde AVANT de lui passer ses données (le moteur l'initialise juste
     * après, avec ses anciens PV) ; le réglage est donc fait juste après, dans la file du jeu (avant son prochain tick, où
     * le moteur retient les PV de départ de la barre de vie).
     */
    private static void aLArrivee(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() || !(e.getEntity() instanceof AbstractVehicle v)) return;
        Minecraft.getInstance().tell(() -> {
            Entity ent = v;
            if (!ent.isRemoved()) PointsDeVie.ajuster(v);
        });
    }
}
