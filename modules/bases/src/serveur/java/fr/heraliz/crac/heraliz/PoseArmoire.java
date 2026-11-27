package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import fr.heraliz.crac.item.ClaimBlockItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Pose de l'armoire par CRac lui-même (27/11, 3e retour du propriétaire : « toujours pas de TC quand on n'est pas op »).
 * <p>Journal du 27/11 : pour un joueur non op, le refus de WorldGuard sur le clic est levé ({@link PoseBasesBukkit}), mais
 * la pose n'arrive jamais jusqu'à CRac (aucune ligne « [CRac] armoire de … ») ; pour un op en créatif, elle passe. Le chemin
 * de la pose de Minecraft sur le serveur hybride (Mohist : événements Bukkit, états capturés) arrête l'armoire quelque part
 * entre les deux, sans trace.</p>
 * <p><b>Correction</b> : pour un joueur qui n'est pas en créatif, le clic droit avec une armoire en main est pris par CRac
 * (le clic de Minecraft est annulé) et l'armoire est posée au tick suivant par {@link ForgeHooks#onPlaceItemIntoWorld},
 * comme les objets posés en mode aventure ({@code C2SPlacePlacableTraitement}) : mêmes règles (événement de pose de Forge :
 * {@code ClaimBlockHandler}, clan, zones, une par clan…), même objet retiré de la main, même place (collée au mur,
 * ObjetAdossable). Chaque essai et son résultat au journal.</p>
 */
@EventBusSubscriber(modid = "crac")
public final class PoseArmoire {
    private PoseArmoire() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel)
            || p.getAbilities().instabuild || p.isSpectator()) {
            return;
        }
        if (!(e.getItemStack().getItem() instanceof ClaimBlockItem)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        InteractionHand main = e.getHand();
        BlockHitResult vise = e.getHitVec();
        MinecraftServer server = p.server;
        // au tick suivant : hors du traitement du clic de Minecraft (et de ses événements Bukkit)
        server.tell(new TickTask(server.getTickCount() + 1, () -> poser(p, main, vise)));
    }

    private static void poser(ServerPlayer p, InteractionHand main, BlockHitResult vise) {
        if (p.isRemoved() || !p.isAlive() || p.isSpectator() || !(p.level() instanceof ServerLevel)) {
            return;
        }
        ItemStack tenu = p.getItemInHand(main);
        if (!(tenu.getItem() instanceof ClaimBlockItem)) {
            return;
        }
        // portée du clic de Minecraft (le joueur a pu bouger d'un tick)
        if (p.getEyePosition().distanceToSqr(vise.getLocation()) > 6.0 * 6.0) {
            return;
        }
        boolean avant = p.getAbilities().mayBuild;
        p.getAbilities().mayBuild = true;
        try {
            InteractionResult r = ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(p, main, vise));
            Crac.LOGGER.info("[CRac] armoire de {} : pose prise par CRac ({})", p.getGameProfile().getName(), r);
        } catch (RuntimeException ex) {
            Crac.LOGGER.warn("[CRac] armoire de {} : pose impossible ({})", p.getGameProfile().getName(), ex.toString());
        } finally {
            p.getAbilities().mayBuild = avant;
        }
    }
}
