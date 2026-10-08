package fr.heraliz.crates.event;

import java.util.function.BiConsumer;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CratePartBlock;
import fr.heraliz.crates.crate.CrateType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import fr.heraliz.crates.item.CrateEditorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Evenements communs (serveur + client) : le clic droit sur une caisse (prédit par le client). Le reste est dans
 * ServerEvents (src/serveur).
 */
@Mod.EventBusSubscriber(modid = HeralizCrates.MODID)
public final class CommonEvents {
    private CommonEvents() {
    }

    /**
     * Clic droit sur une caisse : avec l'outil d'edition, ouvre l'editeur de butin (operateurs) ; sinon le clic va
     * toujours a la caisse, meme accroupi avec un objet en main (cle, interface d'ouverture).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof CrateBlock) && !(block instanceof CratePartBlock)) return;
        if (event.getItemStack().getItem() instanceof CrateEditorItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (event.getEntity() instanceof ServerPlayer sp && event.getHand() == InteractionHand.MAIN_HAND) {
                BlockState master = block instanceof CratePartBlock ? event.getLevel().getBlockState(CratePartBlock.masterPos(pos, state)) : state;
                if (master.getBlock() instanceof CrateBlock cb && EDITEUR != null) EDITEUR.accept(sp, cb.type);
            }
            return;
        }
        event.setUseBlock(Event.Result.ALLOW);
    }

    /** Éditeur de butin (serveur : opening/LootEditing.open) : branché par la partie serveur. */
    public static volatile BiConsumer<ServerPlayer, CrateType> EDITEUR;
}
