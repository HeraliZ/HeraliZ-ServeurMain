package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Boue toxique des cuves de la station de quarantaine : on s'y enfonce, elle ralentit et empoisonne, elle bouillonne. */
public class SludgeBlock extends Block {
   private static final VoxelShape FLOOR = box(0, 0, 0, 16, 10, 16);

   public SludgeBlock(Properties props) {
      super(props);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return FLOOR;
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }

   /** Des deux côtés : on s'y enfonce. Le poison est appliqué par SludgeBlockServeur (usine DungeonRegistry.SLUDGE_USINE). */
   @Override
   @SuppressWarnings("deprecation")
   public void entityInside(BlockState state, Level level, BlockPos pos, Entity e) {
      e.makeStuckInBlock(state, new Vec3(0.6, 0.5, 0.6));
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      if (!level.getBlockState(pos.above()).isAir()) return;
      if (r.nextInt(4) == 0) {
         level.addParticle(ModParticles.ACID_SPLASH.get(), pos.getX() + r.nextDouble(), pos.getY() + 1.0, pos.getZ() + r.nextDouble(), 0, 0.04, 0);
      }
      if (r.nextInt(10) == 0) {
         level.addParticle(ModParticles.TOXIC_SMOKE.get(), pos.getX() + r.nextDouble(), pos.getY() + 1.0, pos.getZ() + r.nextDouble(), 0, 0.02, 0);
      }
   }
}
