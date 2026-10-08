package fr.heralisz.bases.dev;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Banc d'essai seulement (inscrit par {@code HeraliszBases} si la propriete {@code heralisz.bases.harness} existe) :
 * imite le train de HeraliZ Utils pour reproduire le bug du 01/10. Une entite portant le tag
 * {@value #TAG} refuse que son passager descende ({@code EntityMountEvent} annule), et un joueur assis dessus ne peut ni
 * poser, ni casser, ni cliquer sur un bloc — exactement comme {@code fr.heralisz.utils.train.Train} pour un
 * {@code Voyageur}. Le banc verifie que nos teleportations le font quand meme descendre et qu'il construit ensuite.
 */
public final class VehiculeCollant {
    private VehiculeCollant() {
    }

    public static final String TAG = "heralisz_banc_collant";

    private static boolean collant(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    private static boolean aBord(Entity p) {
        return p != null && collant(p.getVehicle());
    }

    @SubscribeEvent
    public static void monture(EntityMountEvent e) {
        if (!e.getLevel().isClientSide() && e.isDismounting() && collant(e.getEntityBeingMounted())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void casse(BlockEvent.BreakEvent e) {
        if (aBord(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void pose(BlockEvent.EntityPlaceEvent e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void clicDroit(PlayerInteractEvent.RightClickBlock e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void clicGauche(PlayerInteractEvent.LeftClickBlock e) {
        if (aBord(e.getEntity())) e.setCanceled(true);
    }
}
