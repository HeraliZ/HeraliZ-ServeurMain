package com.hbm_m.block;

import java.util.function.BiConsumer;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.block.entity.doors.DoorDeclRegistry;
import com.hbm_m.block.machines.TransitionSealBlock;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.blockentity.machines.TransitionSealBlockEntity;
import com.hbm_m.blockentity.machines.UniversalMachinePartBlockEntity;
import com.hbm_m.compat.ContraptionDoorState;
import com.hbm_m.interfaces.IDetonatable;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.interfaces.IMultiblockPart;
import com.hbm_m.item.ModItems;
import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class UniversalMachinePartBlock extends BaseEntityBlock implements IDetonatable {
   /** Client : bloc fantôme orphelin ajouté (vrai) ou retiré (faux) du rendu ; branché par la partie client. */
   public static volatile BiConsumer<BlockPos, Boolean> ORPHELIN_CLIENT;

   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty PASSABLE = BooleanProperty.create("passable");

   @Override
   public boolean onDetonate(Level level, BlockPos partPos, BlockState partState, Player player) {
      if (level.isClientSide) {
         return false;
      }

      if (level.getBlockEntity(partPos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos == null) {
            return false;
         }

         BlockState controllerState = level.getBlockState(controllerPos);
         return controllerState.getBlock() instanceof IDetonatable detonatable ? detonatable.onDetonate(level, controllerPos, controllerState, player) : false;
      } else {
         return false;
      }
   }

   public UniversalMachinePartBlock(Properties pProperties) {
      super(pProperties);
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(PASSABLE, false));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
      pBuilder.add(new Property[]{FACING, PASSABLE});
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
      return new UniversalMachinePartBlockEntity(pPos, pState);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide
         ? null
         : createTickerHelper(
            type,
            ModBlockEntities.UNIVERSAL_MACHINE_PART_BE.get(),
            UniversalMachinePartBlockEntity::tick
         );
   }

   public RenderShape getRenderShape(BlockState pState) {
      return RenderShape.INVISIBLE;
   }

   private boolean isOrphaned(BlockGetter level, BlockPos pos) {
      if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos == null) {
            return true;
         }

         BlockState controllerState = level.getBlockState(controllerPos);
         return !(controllerState.getBlock() instanceof IMultiblockController);
      } else {
         return false;
      }
   }

   public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      if (pLevel instanceof Level lvl && ContraptionDoorState.isContraptionWorld(lvl)) {
         BlockPos controllerPos = ContraptionDoorState.getControllerForPart(lvl, pPos);
         if (controllerPos != null) {
            VoxelShape masterShape = ContraptionDoorState.getShape(lvl, controllerPos);
            if (masterShape != null) {
               BlockPos vecToController = controllerPos.subtract(pPos);
               return masterShape.move(vecToController.getX(), vecToController.getY(), vecToController.getZ());
            }
         }

         return Shapes.block();
      } else if (!(pLevel.getBlockEntity(pPos) instanceof IMultiblockPart part)) {
         return Shapes.block();
      } else {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos == null) {
            return Shapes.block();
         } else {
            BlockState controllerState = pLevel.getBlockState(controllerPos);
            Block controllerBlock = controllerState.getBlock();
            if (!(controllerBlock instanceof IMultiblockController controller)) {
               return Shapes.block();
            } else {
               VoxelShape customShape = controller.getCustomMasterVoxelShape(controllerState);
               VoxelShape masterShape;
               if (customShape != null && !customShape.isEmpty()) {
                  masterShape = customShape;
               } else if (controllerBlock instanceof DoorBlock doorBlock) {
                  DoorDecl decl = DoorDeclRegistry.getById(doorBlock.getDoorDeclId());
                  if (decl != null && decl.isDynamicShape()) {
                     masterShape = doorBlock.getShape(controllerState, pLevel, controllerPos, pContext);
                  } else {
                     Direction facing = (Direction)controllerState.getValue(DoorBlock.FACING);
                     masterShape = controller.getStructureHelper().generateShapeFromParts(facing);
                  }
               } else {
                  Direction facing = (Direction)controllerState.getValue(HorizontalDirectionalBlock.FACING);
                  masterShape = controller.getStructureHelper().generateShapeFromParts(facing);
               }

               BlockPos vecToController = controllerPos.subtract(pPos);
               return masterShape.move(vecToController.getX(), vecToController.getY(), vecToController.getZ());
            }
         }
      }
   }

   public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
      if (pLevel instanceof Level lvl && ContraptionDoorState.isContraptionWorld(lvl)) {
         return Shapes.empty();
      } else if (pLevel.getBlockEntity(pPos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos == null) {
            return Shapes.block();
         }

         BlockState controllerState = pLevel.getBlockState(controllerPos);
         Block controllerBlock = controllerState.getBlock();
         if (controllerBlock instanceof IMultiblockController controller) {
            VoxelShape masterShape = controller.getCustomMasterVoxelShape(controllerState);
            if (masterShape != null && !masterShape.isEmpty()) {
               BlockPos vecToController = controllerPos.subtract(pPos);
               return masterShape.move(vecToController.getX(), vecToController.getY(), vecToController.getZ());
            }

            if (controllerBlock instanceof TransitionSealBlock) {
               return pLevel.getBlockEntity(controllerPos) instanceof TransitionSealBlockEntity sealBE && sealBE.isOpen()
                  ? TransitionSealBlock.getCellCollisionShape(pLevel, controllerPos, controllerState, pPos)
                  : Shapes.block();
            }

            if (controllerBlock instanceof DoorBlock doorBlock) {
               BlockEntity controllerBE = pLevel.getBlockEntity(controllerPos);
               DoorDecl decl = DoorDeclRegistry.getById(doorBlock.getDoorDeclId());
               if (decl != null && decl.getStructureDefinition() != null) {
                  DoorDecl.DoorStructureDefinition def = decl.getStructureDefinition();
                  Direction facing = (Direction)controllerState.getValue(DoorBlock.FACING);
                  BlockPos worldOffset = pPos.subtract(controllerPos);
                  BlockPos localOffset = MultiblockStructureHelper.rotateBack(worldOffset, facing);
                  boolean isOpen;
                  if (controllerBE instanceof DoorBlockEntity doorBE) {
                     isOpen = doorBE.getState() != 0;
                  } else {
                     isOpen = (Boolean)controllerState.getValue(DoorBlock.OPEN);
                  }

                  Map<BlockPos, VoxelShape> map = isOpen ? def.getOpenShapes() : def.getClosedShapes();
                  VoxelShape shape = map.get(localOffset);
                  if (shape != null) {
                     if (!shape.isEmpty()) {
                        return MultiblockStructureHelper.rotateShape(shape, facing);
                     }

                     return shape;
                  }
               }
            }

            MultiblockStructureHelper helper = controller.getStructureHelper();
            Direction facing = (Direction)controllerState.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos worldOffset = pPos.subtract(controllerPos);
            BlockPos localOffset = MultiblockStructureHelper.rotateBack(worldOffset, facing);
            BlockPos gridPos = localOffset.offset(helper.getControllerOffset());
            return helper.getSpecificCollisionShape(gridPos, facing);
         } else {
            return Shapes.block();
         }
      } else {
         return Shapes.block();
      }
   }

   public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
      return switch (type) {
         case LAND, AIR -> state.getValue(PASSABLE);
         default -> false;
      };
   }

   public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
      super.neighborChanged(pState, pLevel, pPos, pBlock, pFromPos, pIsMoving);
      MultiblockStructureHelper.onNeighborChangedForPart(pLevel, pPos, pFromPos);
      if (pLevel.isClientSide()) {
         BiConsumer<BlockPos, Boolean> orphelin = ORPHELIN_CLIENT;
         if (orphelin != null) orphelin.accept(pPos, this.isOrphaned(pLevel, pPos));
      }

      if (pLevel.getBlockEntity(pPos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos != null) {
            BlockEntity be = pLevel.getBlockEntity(controllerPos);
            if (be instanceof DoorBlockEntity doorBE) {
               doorBE.checkRedstonePower();
            }
         }
      }
   }

   public boolean isLadder(BlockState pState, LevelReader pLevel, BlockPos pPos, LivingEntity pEntity) {
      if (pLevel instanceof Level level && level.getBlockEntity(pPos) instanceof IMultiblockPart part) {
         PartRole role = part.getPartRole();
         if (role == PartRole.LADDER) {
            return true;
         }
      }

      return false;
   }

   public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
      if (level instanceof Level lvl && ContraptionDoorState.isContraptionWorld(lvl)) {
         return Shapes.empty();
      } else {
         if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
            BlockPos ctrlPos = part.getControllerPos();
            if (ctrlPos != null) {
               Block ctrlBlock = level.getBlockState(ctrlPos).getBlock();
               if (ctrlBlock instanceof DoorBlock || ctrlBlock instanceof TransitionSealBlock) {
                  return Shapes.empty();
               }
            }
         }

         return this.isFullBlockInGrid(level, pos) ? Shapes.block() : Shapes.empty();
      }
   }

   private boolean isFullBlockInGrid(BlockGetter level, BlockPos pos) {
      if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos != null) {
            BlockState controllerState = level.getBlockState(controllerPos);
            if (controllerState.getBlock() instanceof IMultiblockController controller) {
               MultiblockStructureHelper helper = controller.getStructureHelper();
               Direction facing = (Direction)controllerState.getValue(HorizontalDirectionalBlock.FACING);
               BlockPos worldOffset = pos.subtract(controllerPos);
               BlockPos localOffset = MultiblockStructureHelper.rotateBack(worldOffset, facing);
               BlockPos gridPos = localOffset.offset(helper.getControllerOffset());
               return helper.isFullBlock(gridPos, facing);
            }
         }
      }

      return true;
   }

   public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
      if (pLevel.getBlockEntity(pPos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos == null) {
            if (!pLevel.isClientSide()) {
               pLevel.setBlock(pPos, Blocks.AIR.defaultBlockState(), 3);
            }

            return InteractionResult.sidedSuccess(pLevel.isClientSide());
         } else {
            BlockState controllerState = pLevel.getBlockState(controllerPos);
            if (controllerState.getBlock() instanceof IMultiblockController) {
               BlockEntity ctrlBe = pLevel.getBlockEntity(controllerPos);
               return ctrlBe instanceof DoorBlockEntity && hasScrewdriver(pPlayer)
                  ? InteractionResult.sidedSuccess(pLevel.isClientSide())
                  : controllerState.use(pLevel, pPlayer, pHand, pHit.withPosition(controllerPos));
            }

            if (!pLevel.isClientSide()) {
               pLevel.setBlock(pPos, Blocks.AIR.defaultBlockState(), 3);
            }

            return InteractionResult.sidedSuccess(pLevel.isClientSide());
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   private static boolean hasScrewdriver(Player player) {
      return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.SCREWDRIVER.get()
         || player.getItemInHand(InteractionHand.OFF_HAND).getItem() == ModItems.SCREWDRIVER.get()
         || player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.SCREWDRIVER_DESH.get()
         || player.getItemInHand(InteractionHand.OFF_HAND).getItem() == ModItems.SCREWDRIVER_DESH.get();
   }

   public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
      if (!pState.is(pNewState.getBlock()) && pLevel.getBlockEntity(pPos) instanceof IMultiblockPart partBe) {
         BlockPos controllerPos = partBe.getControllerPos();
         if (controllerPos != null && !pLevel.isClientSide()) {
            BlockState controllerState = pLevel.getBlockState(controllerPos);
            if (controllerState.getBlock() instanceof IMultiblockController controller
               && !MultiblockStructureHelper.isDestroying()
               && !MultiblockStructureHelper.isRepairing()
               && controller.shouldDestroyOnPartRemoved()) {
               if (controllerState.hasProperty(HorizontalDirectionalBlock.FACING)) {
                  Direction facing = (Direction)controllerState.getValue(HorizontalDirectionalBlock.FACING);
                  controller.getStructureHelper().destroyStructure(pLevel, controllerPos, facing);
               }

               pLevel.destroyBlock(controllerPos, true);
            }
         }
      }

      super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
   }

   public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IMultiblockPart partBe) {
         BlockPos controllerPos = partBe.getControllerPos();
         if (controllerPos != null) {
            BlockState controllerState = level.getBlockState(controllerPos);
            if (controllerState.getBlock() instanceof IMultiblockController) {
               boolean dropController = !player.getAbilities().instabuild;
               level.destroyBlock(controllerPos, dropController);
            }
         }
      }

      super.playerWillDestroy(level, pos, state, player);
   }

   public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
      if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos != null) {
            BlockState controllerState = level.getBlockState(controllerPos);
            return controllerState.getBlock().getCloneItemStack(level, controllerPos, controllerState);
         }
      }

      return ItemStack.EMPTY;
   }

   public float getDestroyProgress(BlockState pState, Player pPlayer, BlockGetter pLevel, BlockPos pPos) {
      return this.isOrphaned(pLevel, pPos) ? 1.0F : super.getDestroyProgress(pState, pPlayer, pLevel, pPos);
   }

   public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
      return true;
   }

   public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
      return 1.0F;
   }

   public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
      return 0;
   }

   public boolean addLandingEffects(BlockState state1, ServerLevel level, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) {
      if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos != null) {
            BlockState controllerState = level.getBlockState(controllerPos);
            if (!controllerState.isAir()) {
               level.sendParticles(
                  new BlockParticleOption(ParticleTypes.BLOCK, controllerState),
                  entity.getX(),
                  entity.getY(),
                  entity.getZ(),
                  numberOfParticles,
                  0.0,
                  0.0,
                  0.0,
                  0.15
               );
               return true;
            }
         }
      }

      return false;
   }

   public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
      if (level.isClientSide && level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         BlockPos controllerPos = part.getControllerPos();
         if (controllerPos != null) {
            BlockState controllerState = level.getBlockState(controllerPos);
            if (!controllerState.isAir()) {
               if (level.random.nextFloat() < 0.1F) {
                  double x = entity.getX() + (level.random.nextDouble() - 0.5) * entity.getBbWidth();
                  double y = entity.getY() + 0.1;
                  double z = entity.getZ() + (level.random.nextDouble() - 0.5) * entity.getBbWidth();
                  level.addParticle(
                     new BlockParticleOption(ParticleTypes.BLOCK, controllerState),
                     x,
                     y,
                     z,
                     -entity.getDeltaMovement().x * 4.0,
                     1.5,
                     -entity.getDeltaMovement().z * 4.0
                  );
               }

               return true;
            }
         }
      }

      return false;
   }

   public boolean isSignalSource(BlockState state) {
      return true;
   }

   public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
      return resolveRadarRedPower(level, pos);
   }

   public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
      return this.getSignal(state, level, pos, side);
   }

   private static int resolveRadarRedPower(BlockGetter level, BlockPos pos) {
      if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
         if (part.getPartRole() != PartRole.ENERGY_CONNECTOR) {
            return 0;
         } else {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos == null) {
               return 0;
            } else {
               return 0;
            }
         }
      } else {
         return 0;
      }
   }
}
