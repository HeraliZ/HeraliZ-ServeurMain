package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Luminaire suspendu : le bloc est posé là où naît la lumière, la suspension est dessinée jusqu'à la voûte. POWER : 0 éteint,
 * 1 faible, 2 allumé ; les ampoules électriques grésillent et coupent vraiment la lumière (côté serveur), surtout quand le
 * donjon s'éveille.
 */
public class LampBlock extends PropBlock {
   public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 2);
   private static final VoxelShape SMALL = Block.box(4, 1, 4, 12, 16, 12);

   public LampBlock(PropKind kind) {
      super(kind, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable()
         .sound(SoundType.LANTERN).lightLevel(s -> level(kind, s.getValue(POWER))));
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 2));
   }

   public static int level(PropKind k, int power) {
      return power >= 2 ? k.light : power == 1 ? k.light * 2 / 5 : 0;
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(POWER);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SMALL;
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }

   @Override
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? createTickerHelper(type, DungeonRegistry.PROP_BE.get(), PropBlockEntity::clientTick)
         : kind.electric() ? createTickerHelper(type, DungeonRegistry.PROP_BE.get(), PropBlockEntity::serverTick) : null;
   }
}
