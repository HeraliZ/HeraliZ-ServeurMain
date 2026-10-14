package fr.heraliz.crac.event;

import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Les joueurs ne cassent aucun bloc en survie (choix du propriétaire, 14/10) : ils ne peuvent même pas commencer
 * (pas de fissures). Avant, casser un bloc qui revenait ensuite permettait, accroupi, de se glisser dedans. Des deux
 * côtés : chez le joueur, le clic gauche sur un bloc ne démarre rien (pas d'avancée, pas de fissure) ; au serveur, le
 * même refus (paquet d'un jeu modifié compris), et la casse refusée en dernier recours (serveur.CasseBlocsServeur).
 * <p>Libres : créatif, spectateur, staff (permission 2). Exceptions (les règles de CRac décident ensuite) : le bloc de
 * revendication (son groupe le retire) et les échelles et trappes posées par des pillards dans une base
 * ({@code #crac:raid_access}, retirées par les membres). Les objets posés de CRac se ramassent par leur touche, sans les
 * casser.
 */
@EventBusSubscriber(modid = "crac")
public final class CasseBlocs {
    public static final TagKey<Block> ACCES_RAID = BlockTags.create(new ResourceLocation("crac", "raid_access"));

    private CasseBlocs() {
    }

    /** Joueur qui casse librement : créatif, spectateur, staff. */
    public static boolean libre(Player p) {
        return p == null || p.isCreative() || p.isSpectator() || p.hasPermissions(2);
    }

    /** Bloc que les règles de CRac laissent casser (bloc de revendication, accès de pillards). */
    public static boolean exception(BlockState s) {
        return s.is(ModBlocks.CLAIM_BLOCK.get()) || s.is(ACCES_RAID);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clic(LeftClickBlock e) {
        if (libre(e.getEntity()) || exception(e.getLevel().getBlockState(e.getPos()))) {
            return;
        }
        e.setCanceled(true);
    }

    /** Vitesse de casse nulle (rien n'avance, même si un clic passait) : l'événement annulé rend -1. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void vitesse(PlayerEvent.BreakSpeed e) {
        if (libre(e.getEntity()) || exception(e.getState())) {
            return;
        }
        e.setCanceled(true);
    }
}
