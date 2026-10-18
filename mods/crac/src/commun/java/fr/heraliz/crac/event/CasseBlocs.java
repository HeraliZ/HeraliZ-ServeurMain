package fr.heraliz.crac.event;

import fr.heraliz.crac.bases.Carrieres;
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
 * casser. Dans le monde des bases, les blocs naturels d'une carrière se cassent ({@link Carrieres}, 1.5.0).
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

    /** Le bloc vraiment visé : dans une case de mur, l'objet adossé qu'on vise (armoire, caisse ; Adosse, 1.11.0). */
    private static BlockState vise(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, BlockState s, Player p) {
        if (p != null && s.getBlock() instanceof fr.heraliz.crac.build.world.StructureBlock) {
            fr.heraliz.crac.block.Adosse.Voisin o = fr.heraliz.crac.block.Adosse.vise(level, pos, p,
                s.getCollisionShape(level, pos));
            if (o != null) {
                return o.state();
            }
        }
        return s;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clic(LeftClickBlock e) {
        BlockState s = vise(e.getLevel(), e.getPos(), e.getLevel().getBlockState(e.getPos()), e.getEntity());
        if (libre(e.getEntity()) || exception(s) || Carrieres.permet(e.getLevel(), e.getPos(), s)) {
            return;
        }
        e.setCanceled(true);
    }

    /** Vitesse de casse nulle (rien n'avance, même si un clic passait) : l'événement annulé rend -1. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void vitesse(PlayerEvent.BreakSpeed e) {
        BlockState s = e.getPosition().isPresent() ? vise(e.getEntity().level(), e.getPosition().get(), e.getState(), e.getEntity()) : e.getState();
        if (libre(e.getEntity()) || exception(s)) {
            return;
        }
        // carrière du monde des bases (1.5.0) : ses blocs naturels se cassent à la vitesse normale
        if (e.getPosition().isPresent() && Carrieres.permet(e.getEntity().level(), e.getPosition().get(), e.getState())) {
            return;
        }
        e.setCanceled(true);
    }
}
