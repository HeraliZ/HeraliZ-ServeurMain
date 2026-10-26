package fr.heraliz.casino.event;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachinePartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Evenements communs : regles appliquees des deux cotes (le client predit le meme resultat que le serveur). Les
 * evenements du serveur seul (commandes, chargement des reglages, connexion) sont dans serveur/CommonEventsServeur.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID)
public final class CommonEvents {
    private CommonEvents() {
    }

    /**
     * Pas question de se lever (accroupi) en pleine partie : cartes distribuees, mines payees, duel commence, rouleaux
     * lances. Un joueur qui se deconnecte, meurt, ou que la machine fait lever elle-meme, part normalement.
     */
    @SubscribeEvent
    public static void onDismount(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (!event.isDismounting()) return;
        // tenu par les videurs : on ne leur echappe pas (decision du serveur ; le client suit ce qu'il lui envoie)
        if (event.getEntityBeingMounted() instanceof fr.heraliz.casino.entity.EscortEntity escort) {
            if (!escort.isRemoved() && !escort.level().isClientSide && !fr.heraliz.casino.entity.EscortEntity.allowDismount
                    && event.getEntityMounting() instanceof ServerPlayer held && held.isAlive() && !held.isRemoved() && !held.hasDisconnected()) {
                event.setCanceled(true);
            }
            return;
        }
        if (fr.heraliz.casino.block.MachineBlockEntity.allowDismount) return;
        if (!(event.getEntityBeingMounted() instanceof fr.heraliz.casino.entity.SeatEntity seat) || seat.isRemoved()) return;
        if (!(event.getEntityMounting() instanceof net.minecraft.world.entity.player.Player p) || !p.isAlive() || p.isRemoved()) return;
        if (p instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        net.minecraft.core.BlockPos pos = seat.machine();
        if (pos == null || !(p.level().getBlockEntity(pos) instanceof fr.heraliz.casino.block.MachineBlockEntity be) || !be.locks(p.getUUID())) return;
        event.setCanceled(true);
        if (!p.level().isClientSide) p.displayClientMessage(Component.translatable("message.heralizcasino.locked").withStyle(ChatFormatting.GOLD), true);
    }

    /** Autorise le clic droit sur une machine meme accroupi / avec un objet en main. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof MachineBlock || block instanceof MachinePartBlock) {
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.DENY);
        }
    }

    /** Tablette du Directeur, accroupi, clic gauche sur un bloc : coin A de la zone du casino (le bloc n'est pas casse). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        net.minecraft.world.entity.player.Player p = event.getEntity();
        if (!p.isShiftKeyDown() || !(p.getMainHandItem().getItem() instanceof fr.heraliz.casino.item.DirectorTabletItem)) return;
        event.setCanceled(true);
        if (p instanceof ServerPlayer sp && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            fr.heraliz.casino.item.DirectorTabletItem.corner(sp, 0, event.getPos());
        }
    }
}
