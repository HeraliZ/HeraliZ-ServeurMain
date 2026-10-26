package fr.heralisz.utils.ascenseur;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** On ne descend pas de la cabine en marche (Maj ne fait rien) ; un joueur qui se déconnecte est libéré. */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID)
public final class AscenseurEvenements {
    private AscenseurEvenements() {}

    @SubscribeEvent
    public static void monture(EntityMountEvent e) {
        // côté serveur seulement : le client applique ce que le serveur décide (sinon il garderait le passager à bord)
        if (!e.getLevel().isClientSide && e.isDismounting() && e.getEntityBeingMounted() instanceof CabineServeur c && !c.descentePermise()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity().getVehicle() instanceof CabineServeur c) {
            c.liberer(e.getEntity());
        }
    }
}
