package fr.heraliz.crac.event;

import fr.heraliz.crac.placable.TableArmurier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Table d'armurier visée contre un mur de base (retours du 03/11, {@link TableArmurier}) : le joueur ne la pose pas chez lui
 * devant le mur (Minecraft l'aurait mise dans la case libre, à 14 px du mur) ; le serveur la pose dans la rangée du mur
 * ({@code placable/TablePose}) et le joueur la reçoit. Le geste du bras reste. En aventure, le clic passe déjà par le
 * paquet de pose de CRac ({@link AdventureBuildHandler}).
 */
@EventBusSubscriber(
    modid = "crac",
    value = {Dist.CLIENT}
)
public final class TableArmurierClient {
    private TableArmurierClient() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void clic(RightClickBlock e) {
        Player p = e.getEntity();
        // un abonné Dist.CLIENT voit aussi les évènements du serveur intégré : seulement le clic du joueur de ce jeu
        if (!e.getLevel().isClientSide() || p != Minecraft.getInstance().player || !p.getAbilities().mayBuild
            || e.getUseItem() == Event.Result.DENY) {
            return;
        }
        if (e.getItemStack().getItem() instanceof BlockItem bi && TableArmurier.estTable(bi.getBlock())
            && TableArmurier.dansMur(e.getLevel(), p, e.getHitVec()) != null) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
