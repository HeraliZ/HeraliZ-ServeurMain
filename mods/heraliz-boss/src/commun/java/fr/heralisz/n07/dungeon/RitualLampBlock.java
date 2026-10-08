package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bloc qui s'éveille avec le rituel (LIT) : le sanctuaire les allume de proche en proche, du plus proche au plus lointain, puis
 * les éteint quand le donjon se réinitialise. Gyrophares du labo, bouches de gaz, rigoles de braises, pierres runiques, poches
 * d'œufs.
 */
public class RitualLampBlock extends Block {
   public static final BooleanProperty LIT = BlockStateProperties.LIT;
   public final DungeonKind kind;
   private final VoxelShape shape;

   public RitualLampBlock(Properties props, DungeonKind kind, VoxelShape shape) {
      super(props);
      this.kind = kind;
      this.shape = shape;
      this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(LIT);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return shape;
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      if (!state.getValue(LIT)) return;
      double x = pos.getX() + 0.5, y = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), z = pos.getZ() + 0.5;
      switch (kind) {
         case N07 -> {
            if (r.nextInt(4) == 0) level.addParticle(ModParticles.RAD_EMBER.get(), x + (r.nextDouble() - 0.5), y - 0.2, z + (r.nextDouble() - 0.5), 0, -0.02, 0);
         }
      }
   }
}
