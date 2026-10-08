package fr.heralisz.utils.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Entité des décors animés : aucune donnée, aucun tick ; elle ne sert qu'à accrocher le rendu animé au bloc. */
public class AnimeBlockEntity extends BlockEntity {
    public AnimeBlockEntity(BlockPos p, BlockState s) {
        super(DecoBlocks.ANIME_BE.get(), p, s);
    }

    public Genre genre() {
        return getBlockState().getBlock() instanceof AnimeBlock a ? a.genre : Genre.DRAPEAU_BLANC;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(genre().rayon);
    }
}
