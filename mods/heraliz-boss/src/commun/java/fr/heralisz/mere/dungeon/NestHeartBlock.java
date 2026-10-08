package fr.heralisz.mere.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Le cœur du nid, maçonné sous l'autel (même aspect que la pierre de soie) : son entité de bloc mène toute la salle. */
public class NestHeartBlock extends BaseEntityBlock {
   public NestHeartBlock(Properties p) {
      super(p);
   }

   @Override
   public RenderShape getRenderShape(BlockState s) {
      return RenderShape.MODEL;
   }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
      return ModBlocks.NEST_BE.get().create(pos, st);                // par le type : version serveur si elle est là
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState st, BlockEntityType<T> type) {
      return level.isClientSide ? null : createTickerHelper(type, ModBlocks.NEST_BE.get(), NestHallBE::serverTick);
   }
}
