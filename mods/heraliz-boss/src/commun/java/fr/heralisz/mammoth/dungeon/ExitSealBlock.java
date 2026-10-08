package fr.heralisz.mammoth.dungeon;

import fr.heralisz.mammoth.entity.Mammoth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Glace qui mure un passage secret. Incassable tant qu'Ymirak vit (ou dort) dans les parages ; s'il n'y a plus de boss
 * vivant, elle se casse à la main comme de la glace (secours si l'effondrement n'a pas eu lieu).
 */
public class ExitSealBlock extends Block {
   public ExitSealBlock(Properties p) {
      super(p);
   }

   @Override
   @SuppressWarnings("deprecation")
   public float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
      if (getter instanceof Level level) {
         boolean boss = !level.getEntitiesOfClass(Mammoth.class, new AABB(pos).inflate(128), m -> m.isAlive()).isEmpty();
         if (!boss) return player.getDigSpeed(net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState(), pos) / 0.5F / 30F;
      }
      return 0F;
   }
}
