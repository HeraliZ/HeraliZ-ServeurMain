package fr.heraliz.crac.build.world;

import fr.heraliz.crac.build.logic.PlacementEnv;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** {@link PlacementEnv} backed by a real level (server or client). */
public record LevelEnv(Level level) implements PlacementEnv {
    @Override
    public boolean isFree(BlockPos pos) {
        // above the build limit the level answers "void air", where no block can be written
        if (this.level.isOutsideBuildHeight(pos) || !this.level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState s = this.level.getBlockState(pos);
        if (s.getBlock() instanceof StructureBlock) {
            return false;
        }
        // HeraliZ : fleurs, pousses, champignons et herbes hautes ne gênent pas une pièce : elle les remplace
        return s.isAir() || s.canBeReplaced() || s.getBlock() instanceof BushBlock;
    }

    @Override
    public boolean isStructure(BlockPos pos) {
        return this.level.getBlockState(pos).getBlock() instanceof StructureBlock;
    }

    @Override
    public boolean holdsOldData(BlockPos pos) {
        return this.level.getBlockEntity(pos) instanceof StructureBlockEntity be && be.hasLegacyData();
    }

    /**
     * Sturdy terrain. Deployables never count, so a foundation cannot be set on a crate, a workbench or a chest that
     * is then picked up: placables (and the Claim Block) and any block with a block entity.
     */
    @Override
    public boolean isGround(BlockPos pos) {
        BlockState s = this.level.getBlockState(pos);
        if (s.getBlock() instanceof StructureBlock || s.isAir() || s.canBeReplaced() || s.hasBlockEntity()
            || PlacableSet.canPlace(this.level, s.getBlock())) {
            return false;
        }
        return s.isFaceSturdy(this.level, pos, Direction.UP);
    }

    @Override
    public boolean isTerrain(BlockPos pos) {
        BlockState s = this.level.getBlockState(pos);
        if (s.getBlock() instanceof StructureBlock || s.isAir() || s.canBeReplaced() || s.hasBlockEntity()
            || PlacableSet.canPlace(this.level, s.getBlock())) {
            return false;
        }
        return s.isCollisionShapeFullBlock(this.level, pos);
    }

    @Override
    public boolean isAdossable(BlockPos pos) {
        return this.level.getBlockState(pos).getBlock() instanceof fr.heraliz.crac.block.Adosse.Adossable;
    }

    @Override
    public boolean entityIn(AABB box) {
        return !this.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }
}
