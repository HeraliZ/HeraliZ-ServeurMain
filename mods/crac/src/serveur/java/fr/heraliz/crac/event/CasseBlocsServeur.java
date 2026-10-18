package fr.heraliz.crac.event;

import fr.heraliz.crac.claim.ClaimManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Dernier recours de {@link CasseBlocs} au serveur : une casse par un joueur en survie est refusée, sauf le bloc de
 * revendication et les accès de pillards à l'intérieur d'une base (hors base, une échelle de la carte ne se casse pas).
 */
@EventBusSubscriber(modid = "crac")
public final class CasseBlocsServeur {
    private CasseBlocsServeur() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void casse(BreakEvent e) {
        // objet adossé (armoire, caisse) visé dans une case de mur : c'est lui qu'on casse, jamais le mur (1.11.0)
        if (e.getState().getBlock() instanceof fr.heraliz.crac.build.world.StructureBlock
            && e.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
            fr.heraliz.crac.block.Adosse.Voisin o = fr.heraliz.crac.block.Adosse.vise(e.getLevel(), e.getPos(), sp,
                e.getState().getCollisionShape(e.getLevel(), e.getPos()));
            if (o != null) {
                e.setCanceled(true);
                sp.gameMode.destroyBlock(o.pos());
                return;
            }
        }
        if (CasseBlocs.libre(e.getPlayer())) {
            return;
        }
        // carrière du monde des bases (1.5.0) : ses blocs naturels se cassent, puis repoussent (bases.CarrieresServeur)
        if (fr.heraliz.crac.bases.Carrieres.permet(e.getPlayer().level(), e.getPos(), e.getState())) {
            return;
        }
        if (CasseBlocs.exception(e.getState())) {
            boolean accesHorsBase = e.getState().is(CasseBlocs.ACCES_RAID) && e.getLevel() instanceof ServerLevel sl
                && ClaimManager.getAt(sl, e.getPos()) == null;
            if (!accesHorsBase) {
                return;
            }
        }
        e.setCanceled(true);
    }
}
