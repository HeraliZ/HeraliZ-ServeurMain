package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/** Totem d'os de la tribu : ses runes s'allument de proche en proche quand le cor sonne sur l'autel (bloc rituel). */
public class BoneTotemBlock extends TwoStateBlock {
   public BoneTotemBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .sound(SoundType.BONE_BLOCK).lightLevel(s -> s.getValue(LIT) ? 9 : 0), Block.box(4, 0, 4, 12, 16, 12));
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      if (!state.getValue(LIT)) return;
      double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
      if (r.nextInt(2) == 0) {
         level.addParticle(fr.heralisz.mammoth.fx.Particles.AURORA.get(), x + (r.nextDouble() - 0.5) * 0.3, y + 1.05 + r.nextDouble() * 0.2, z + (r.nextDouble() - 0.5) * 0.3,
            0, 0.015, 0);
      }
      if (r.nextInt(3) == 0) level.addParticle(fr.heralisz.mammoth.fx.Particles.FLAKE.get(), x + (r.nextDouble() - 0.5), y + r.nextDouble() * 1.4, z + (r.nextDouble() - 0.5), 0, 0.02, 0);
   }

   @Override
   public String heroId() {
      return "hero_bone_totem";
   }
}
