package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Brasero de la tribu : éteint au repos, il flambe de feu bleu tant qu'Ymirak est éveillé (feu d'alerte). */
public class TribeBrazierBlock extends TwoStateBlock {
   public TribeBrazierBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.METAL)
         .lightLevel(s -> s.getValue(LIT) ? 15 : 13), Block.box(2, 0, 2, 14, 14, 14));
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
      if (!state.getValue(LIT)) {                                         // au repos : feu rouge de la tribu, braises
         if (r.nextInt(2) == 0) level.addParticle(fr.heralisz.mammoth.fx.Particles.CINDER.get(), x + (r.nextDouble() - 0.5) * 0.5, y + 1.0 + r.nextDouble() * 0.3,
            z + (r.nextDouble() - 0.5) * 0.5, 0, 0.04, 0);
         if (r.nextInt(30) == 0) level.playLocalSound(x, y + 1, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.8F, false);
         return;
      }
      for (int i = 0; i < 3; i++) {
         level.addParticle(fr.heralisz.mammoth.fx.Particles.AURORA.get(), x + (r.nextDouble() - 0.5) * 0.5, y + 0.9 + r.nextDouble() * 0.3, z + (r.nextDouble() - 0.5) * 0.5,
            0, 0.03 + r.nextDouble() * 0.03, 0);
      }
      if (r.nextInt(3) == 0) level.addParticle(fr.heralisz.mammoth.fx.Particles.MIST.get(), x, y + 1.3, z, 0, 0.04, 0);
      if (r.nextInt(20) == 0) level.playLocalSound(x, y + 1, z, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.8F, 0.6F + r.nextFloat() * 0.3F, false);
   }

   @Override
   public String heroId() {
      return "hero_tribe_brazier";
   }
}
