package fr.heralisz.mere.dungeon;

import fr.heralisz.mere.fx.Particles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Sac d'œufs à deux états : terne au repos ; il palpite et luit (ambre) quand le nid s'éveille. */
public class AlertBlock extends Block {
   public static final BooleanProperty ALERT = BooleanProperty.create("alert");

   public AlertBlock(Properties p) {
      super(p);
      registerDefaultState(stateDefinition.any().setValue(ALERT, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(ALERT);
   }

   @Override
   public void animateTick(BlockState st, Level level, BlockPos pos, RandomSource r) {
      if (!st.getValue(ALERT) || r.nextInt(5) != 0) return;
      level.addParticle(Particles.AURORA.get(), pos.getX() + r.nextDouble(), pos.getY() + 1.05, pos.getZ() + r.nextDouble(), 0, 0.01, 0);
   }
}
