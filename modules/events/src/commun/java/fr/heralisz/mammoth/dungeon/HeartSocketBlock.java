package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Réceptacle serti dans le rideau de glace : vide, puis (allumé) le cœur brûlant du dernier Gardien y bat. */
public class HeartSocketBlock extends TwoStateBlock {
   public HeartSocketBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.GLASS)
         .lightLevel(s -> s.getValue(LIT) ? 12 : 3), Block.box(0, 0, 0, 16, 16, 16));
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      if (!state.getValue(LIT) || r.nextInt(2) != 0) return;
      level.addParticle(fr.heralisz.mammoth.fx.Particles.CINDER.get(), pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.6, pos.getY() + 0.5 + (r.nextDouble() - 0.5) * 0.6,
         pos.getZ() + 0.2, 0, 0.02, -0.02);
   }

   @Override
   public String heroId() {
      return "hero_heart_socket";
   }
}
