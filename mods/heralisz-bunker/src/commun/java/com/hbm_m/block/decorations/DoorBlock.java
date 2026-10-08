package com.hbm_m.block.decorations;

import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.KeycardDoors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.hbm_m.block.ModBlocks;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.block.entity.doors.DoorDeclRegistry;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.compat.ContraptionDoorState;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.item.ModItems;
import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DoorBlock extends BaseEntityBlock implements IMultiblockController {
   public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final EnumProperty<PartRole> PART_ROLE = EnumProperty.create("part_role", PartRole.class);
   public static final BooleanProperty DOOR_MOVING = BooleanProperty.create("door_moving");
   public static final BooleanProperty OPEN = BooleanProperty.create("open");
   private final Map<Direction, VoxelShape> shapeCache = new EnumMap<>(Direction.class);
   private final String doorDeclId;
   private final MultiblockStructureHelper structureHelper;

   public DoorBlock(Properties properties, String doorDeclId) {
      super(properties);
      this.doorDeclId = doorDeclId;
      DoorDecl decl = DoorDeclRegistry.getById(doorDeclId);
      Supplier<BlockState> phantomSupplier = () -> ((Block)ModBlocks.UNIVERSAL_MACHINE_PART.get()).defaultBlockState();
      if (decl != null && decl.getStructureDefinition() != null) {
         DoorDecl.DoorStructureDefinition def = decl.getStructureDefinition();
         Map<BlockPos, Supplier<BlockState>> structureMap = new HashMap<>();

         for (BlockPos localPos : def.getClosedShapes().keySet()) {
            if (!localPos.equals(BlockPos.ZERO)) {
               structureMap.put(localPos, phantomSupplier);
            }
         }

         this.structureHelper = new MultiblockStructureHelper(
            structureMap, phantomSupplier, null, null, def.getClosedShapes(), def.getClosedShapes(), BlockPos.ZERO
         );
      } else {
         Map<BlockPos, Supplier<BlockState>> structureMap = createStructureForDoor(doorDeclId);
         this.structureHelper = new MultiblockStructureHelper(structureMap, phantomSupplier);
      }

      this.registerDefaultState(
         (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH))
                  .setValue(PART_ROLE, PartRole.DEFAULT))
               .setValue(DOOR_MOVING, false))
            .setValue(OPEN, false)
      );
   }

   private static Map<BlockPos, Supplier<BlockState>> createStructureForDoor(String doorDeclId) {
      Map<BlockPos, Supplier<BlockState>> structureMap = new HashMap<>();
      Supplier<BlockState> phantomSupplier = () -> ((Block)ModBlocks.UNIVERSAL_MACHINE_PART.get()).defaultBlockState();
      int[] dimensions = getDoorDimensions(doorDeclId);
      int offsetX = dimensions[0];
      int offsetY = dimensions[1];
      int offsetZ = dimensions[2];
      int sizeX = dimensions[3];
      int sizeY = dimensions[4];
      int sizeZ = dimensions[5];

      for (int x = offsetX; x <= offsetX + sizeX; x++) {
         for (int y = offsetY; y <= offsetY + sizeY; y++) {
            for (int z = offsetZ; z <= offsetZ + sizeZ; z++) {
               if (x != 0 || y != 0 || z != 0) {
                  structureMap.put(new BlockPos(x, y, z), phantomSupplier);
               }
            }
         }
      }

      return structureMap;
   }

   public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
      return false;
   }

   public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
      return switch (type) {
         case LAND, AIR -> state.getValue(OPEN);
         default -> false;
      };
   }

   public static int[] getDoorDimensions(String doorDeclId) {
      DoorDecl decl = DoorDeclRegistry.getById(doorDeclId);
      return decl != null ? decl.getDimensions() : new int[]{0, 0, 0, 0, 1, 0};
   }

   @Override
   public MultiblockStructureHelper getStructureHelper() {
      return this.structureHelper;
   }

   @Override
   public PartRole getPartRole(BlockPos localOffset) {
      DoorDecl decl = DoorDeclRegistry.getById(this.doorDeclId);
      if (decl != null && decl.getStructureDefinition() != null) {
         PartRole role = decl.getStructureDefinition().getRoles().get(localOffset);
         if (role != null) {
            return role;
         }
      }

      return PartRole.DEFAULT;
   }

   public String getDoorDeclId() {
      return this.doorDeclId;
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new DoorBlockEntity(pos, state, this.doorDeclId);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide
         ? null
         : createTickerHelper(
            type,
            ModBlockEntities.DOOR_ENTITY.get(),
            DoorBlockEntity::serverTick
         );
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hasScrewdriver(player)) {
         return InteractionResult.sidedSuccess(level.isClientSide);
      }

      AccessLevel keycard = KeycardDoors.levelOf(this);
      if (keycard != null) {
         // HeralisZ : porte à carte, ne s'ouvre qu'avec un lecteur de sa couleur
         if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("msg.heralisz_bunker.door_locked",
               Component.translatable("keycard.heralisz_bunker." + keycard.id).withStyle(keycard.color)).withStyle(ChatFormatting.GRAY), true);
         } else {
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.6F);
         }
         return InteractionResult.sidedSuccess(level.isClientSide);
      }
      if (!level.isClientSide && level.getBlockEntity(pos) instanceof DoorBlockEntity doorBE) {
         doorBE.toggle();
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   private static boolean hasScrewdriver(Player player) {
      return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.SCREWDRIVER.get()
         || player.getItemInHand(InteractionHand.OFF_HAND).getItem() == ModItems.SCREWDRIVER.get()
         || player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.SCREWDRIVER_DESH.get()
         || player.getItemInHand(InteractionHand.OFF_HAND).getItem() == ModItems.SCREWDRIVER_DESH.get();
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (level instanceof Level lvl && ContraptionDoorState.isContraptionWorld(lvl)) {
         VoxelShape cached = ContraptionDoorState.getShape(lvl, pos);
         if (cached != null) {
            return cached;
         }
      }

      boolean isOpen;
      if (level.getBlockEntity(pos) instanceof DoorBlockEntity doorBE) {
         isOpen = doorBE.getState() != 0;
      } else {
         isOpen = (Boolean)state.getValue(OPEN);
      }

      if (isOpen) {
         DoorDecl decl = DoorDeclRegistry.getById(this.doorDeclId);
         if (decl != null && decl.getStructureDefinition() != null) {
            VoxelShape openShape = decl.getStructureDefinition().getOpenShapes().get(BlockPos.ZERO);
            if (openShape != null && !openShape.isEmpty()) {
               return MultiblockStructureHelper.rotateShape(openShape, (Direction)state.getValue(FACING));
            }
         }

         return Shapes.empty();
      } else {
         return this.structureHelper.getSpecificCollisionShape(BlockPos.ZERO, (Direction)state.getValue(FACING));
      }
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (level instanceof Level lvl && ContraptionDoorState.isContraptionWorld(lvl)) {
         VoxelShape cached = ContraptionDoorState.getShape(lvl, pos);
         if (cached != null) {
            return cached;
         }
      }

      DoorDecl decl = DoorDeclRegistry.getById(this.doorDeclId);
      return decl != null && decl.isDynamicShape() && level.getBlockEntity(pos) instanceof DoorBlockEntity doorBE
         ? this.generateDynamicFullShape(state, level, pos, doorBE)
         : this.shapeCache.computeIfAbsent((Direction)state.getValue(FACING), facing -> this.getStructureHelper().generateShapeFromParts(facing));
   }

   private VoxelShape generateDynamicFullShape(BlockState state, BlockGetter level, BlockPos pos, DoorBlockEntity doorBE) {
      Direction facing = (Direction)state.getValue(FACING);
      DoorDecl decl = doorBE.getDoorDecl();
      if (decl != null && decl.getStructureDefinition() != null) {
         VoxelShape combined = Shapes.empty();
         boolean isOpen = doorBE.getState() != 0;
         Map<BlockPos, VoxelShape> currentMap = isOpen ? decl.getStructureDefinition().getOpenShapes() : decl.getStructureDefinition().getClosedShapes();

         for (Entry<BlockPos, VoxelShape> entry : currentMap.entrySet()) {
            VoxelShape partShape = entry.getValue();
            if (!partShape.isEmpty()) {
               BlockPos relativePos = entry.getKey();
               BlockPos rotatedPos = MultiblockStructureHelper.rotate(relativePos, facing);
               VoxelShape rotatedShape = MultiblockStructureHelper.rotateShape(partShape, facing);
               combined = Shapes.or(combined, rotatedShape.move(rotatedPos.getX(), rotatedPos.getY(), rotatedPos.getZ()));
            }
         }

         return combined.optimize();
      } else {
         return Shapes.empty();
      }
   }

   public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
      super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
      if (!level.isClientSide) {
         if (level.getBlockEntity(pos) instanceof DoorBlockEntity doorBE) {
            doorBE.checkRedstonePower();
         }
      }
   }

   public RenderShape getRenderShape(BlockState state) {
      DoorDecl decl = DoorDeclRegistry.getById(this.doorDeclId);
      return decl != null && decl.getColladaAnimationSource() != null ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
   }

   @Override
   public VoxelShape getCustomMasterVoxelShape(BlockState state) {
      return Shapes.empty();
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING, PART_ROLE, DOOR_MOVING, OPEN});
   }

   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
      if (!level.isClientSide && !oldState.is(this)) {
         BlockPos core = this.placeMultiblockStructure(level, pos, state);
         if (core == null) {
            return;
         }

         if (level.getBlockEntity(core) instanceof DoorBlockEntity doorBE) {
            doorBE.setControllerPos(core);
            doorBE.onStructureFormed();
         }
      }

      super.onPlace(state, level, pos, oldState, isMoving);
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
      if (!state.is(newState.getBlock()) && !level.isClientSide) {
         Direction facing = (Direction)state.getValue(FACING);
         this.structureHelper.destroyStructure(level, pos, facing);
      }

      super.onRemove(state, level, pos, newState, isMoving);
   }

   public int getLightBlock(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return 0;
   }

   public float getShadeBrightness(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return 1.0F;
   }

   public boolean propagatesSkylightDown(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return true;
   }

   public VoxelShape getVisualShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      return Shapes.empty();
   }

   public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return Shapes.empty();
   }

   public VoxelShape getBlockSupportShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
      return Shapes.empty();
   }
}
