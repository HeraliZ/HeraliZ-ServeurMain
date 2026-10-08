package fr.heralisz.bases.bloc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coque des bunkers (obsidienne renforcee) : exterieur et cloisons entre salles. Incassable (durete -1), resistance
 * 3 600 000, ignoree par les explosions (meme celles qui appellent onBlockExploded directement), immobile pour les
 * pistons (PushReaction.BLOCK), indestructible par les entites (wither, dragon). Les regles de la dimension
 * ({@code server.Regles}) la retirent en plus de toute liste d'explosion.
 */
public class CoqueBlock extends Block {
    public CoqueBlock(Properties p) {
        super(p);
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        // rien : la coque ne cede jamais
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }
}
