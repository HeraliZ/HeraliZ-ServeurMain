package com.hbm_m.multiblock;

import com.hbm_m.api.energy.EnergyNetworkManager;
import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.block.entity.doors.DoorDeclRegistry;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.interfaces.IMultiblockPart;
import com.hbm_m.interfaces.IMultiblockSidedIO;
import com.hbm_m.main.MainRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MultiblockStructureHelper {
   private static final ThreadLocal<Boolean> IS_DESTROYING = ThreadLocal.withInitial(() -> false);
   private static final ThreadLocal<Boolean> IS_REPAIRING = ThreadLocal.withInitial(() -> false);
   private final Map<BlockPos, Supplier<BlockState>> structureMap;
   private final Supplier<BlockState> phantomBlockState;
   private final Map<Character, PartRole> symbolRoleMap;
   private final Map<BlockPos, Character> positionSymbolMap;
   // HeralisZ : lue par le fil du serveur et par celui du rendu (jeu solo) : une HashMap pouvait lever une erreur
   private final Map<Direction, VoxelShape> shapeCache = new ConcurrentHashMap<>();
   // HeralisZ : formes tournées gardées (SUD, OUEST, EST), clé = la forme d'origine elle-même (VoxelShape ne redéfinit
   // pas equals : comparaison par objet). rotateShape est appelée à chaque collision d'entité et à chaque balle, et
   // refaisait la forme boîte par boîte. Les formes d'origine viennent des définitions de structure (elles vivent
   // tout le jeu) : la table reste petite ; plafond de sécurité au cas où.
   private static final int ROTATIONS_MAX = 2048;
   private static final Map<VoxelShape, VoxelShape[]> ROTATIONS = new ConcurrentHashMap<>();
   private final EnumMap<Direction, AABB> localRenderBoundsCache = new EnumMap<>(Direction.class);
   private final Map<BlockPos, Set<Direction>> ladderLocalDirections = new HashMap<>();
   private final Map<BlockPos, Set<Direction>> energyLocalDirections = new HashMap<>();
   private final Map<BlockPos, Set<Direction>> fluidLocalDirections = new HashMap<>();
   private Set<Direction> controllerEnergyLocalDirections = Collections.emptySet();
   private Set<Direction> controllerFluidLocalDirections = Collections.emptySet();
   private boolean controllerFluidSidesFromStructure = false;
   private final Map<BlockPos, VoxelShape> partShapes;
   private final Map<BlockPos, VoxelShape> collisionShapes;
   private final BlockPos controllerOffset;
   private int placementOffset;
   private final Set<BlockPos> partOffsets;
   private static final Direction[] LADDER_TUPLE_ORDER = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final Direction[] ENERGY_TUPLE_ORDER = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
   };
   private static final Direction[] FLUID_TUPLE_ORDER = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN
   };
   private final Set<Block> replaceableBlocks = Set.of(
      Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.SNOW, Blocks.VINE, Blocks.WATER, Blocks.LAVA
   );

   public static boolean isDestroying() {
      return IS_DESTROYING.get();
   }

   public static boolean isRepairing() {
      return IS_REPAIRING.get();
   }

   public MultiblockStructureHelper(Map<BlockPos, Supplier<BlockState>> structureMap, Supplier<BlockState> phantomBlockState) {
      this(structureMap, phantomBlockState, null, null, null, null, BlockPos.ZERO);
   }

   public MultiblockStructureHelper(
      Map<BlockPos, Supplier<BlockState>> structureMap, Supplier<BlockState> phantomBlockState, Map<Character, PartRole> symbolRoleMap
   ) {
      this(structureMap, phantomBlockState, symbolRoleMap, null, null, null, BlockPos.ZERO);
   }

   public MultiblockStructureHelper(
      Map<BlockPos, Supplier<BlockState>> structureMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> symbolRoleMap,
      Map<BlockPos, Character> positionSymbolMap,
      BlockPos controllerOffset
   ) {
      this(structureMap, phantomBlockState, symbolRoleMap, positionSymbolMap, null, null, controllerOffset);
   }

   public MultiblockStructureHelper(
      Map<BlockPos, Supplier<BlockState>> structureMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> symbolRoleMap,
      Map<BlockPos, Character> positionSymbolMap,
      Map<BlockPos, VoxelShape> partShapes,
      Map<BlockPos, VoxelShape> collisionShapes,
      BlockPos controllerOffset
   ) {
      this.structureMap = Collections.unmodifiableMap(structureMap);
      this.phantomBlockState = phantomBlockState;
      this.symbolRoleMap = symbolRoleMap != null ? Collections.unmodifiableMap(symbolRoleMap) : Collections.emptyMap();
      this.positionSymbolMap = positionSymbolMap != null ? Collections.unmodifiableMap(positionSymbolMap) : Collections.emptyMap();
      this.controllerOffset = controllerOffset != null ? controllerOffset : BlockPos.ZERO;
      this.partShapes = partShapes != null ? Collections.unmodifiableMap(partShapes) : Collections.emptyMap();
      this.collisionShapes = collisionShapes != null ? Collections.unmodifiableMap(collisionShapes) : Collections.emptyMap();
      this.partOffsets = Collections.unmodifiableSet(structureMap.keySet());
      this.placementOffset = computePlacementOffsetFromStructure(this.partOffsets, this.controllerOffset);
   }

   private static int computePlacementOffsetFromStructure(Set<BlockPos> partOffsets, BlockPos controllerOffset) {
      int minZ = controllerOffset.getZ();

      for (BlockPos offset : partOffsets) {
         minZ = Math.min(minZ, offset.getZ());
      }

      return controllerOffset.getZ() - minZ;
   }

   public static MultiblockStructureHelper createFromLayersWithRoles(
      String[][] layers,
      Map<Character, Supplier<BlockState>> symbolMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> roleMap,
      Map<Character, VoxelShape> shapeMap,
      Map<Character, VoxelShape> collisionMap,
      Map<Character, boolean[]> ladderSideMap,
      Map<Character, boolean[]> energySideMap,
      Map<Character, boolean[]> fluidSideMap
   ) {
      Map<BlockPos, Supplier<BlockState>> structureMap = new HashMap<>();
      Map<BlockPos, Character> positionSymbolMap = new HashMap<>();
      Map<BlockPos, Set<Direction>> ladderDirs = new HashMap<>();
      Map<BlockPos, Set<Direction>> energyDirs = new HashMap<>();
      Map<BlockPos, Set<Direction>> fluidDirs = new HashMap<>();
      Map<BlockPos, VoxelShape> specificPartShapes = new HashMap<>();
      Map<BlockPos, VoxelShape> specificCollisionShapes = new HashMap<>();
      List<BlockPos> foundControllerPositions = new ArrayList<>();
      if (roleMap != null && !roleMap.isEmpty()) {
         boolean hasControllerRole = roleMap.containsValue(PartRole.CONTROLLER);
         if (!hasControllerRole) {
            throw new IllegalArgumentException("roleMap must contain a symbol with the role PartRole.CONTROLLER");
         }

         if (layers != null && layers.length != 0) {
            int maxDepth = 0;
            int maxWidth = 0;

            for (String[] layer : layers) {
               if (layer != null) {
                  maxDepth = Math.max(maxDepth, layer.length);

                  for (String row : layer) {
                     if (row != null) {
                        maxWidth = Math.max(maxWidth, row.length());
                     }
                  }
               }
            }

            int centerX = (maxWidth - 1) / 2;
            int centerZ = (maxDepth - 1) / 2;

            for (int y = 0; y < layers.length; y++) {
               String[] layer = layers[y];
               if (layer != null) {
                  for (int z = 0; z < layer.length; z++) {
                     String row = layer[z];
                     if (row != null) {
                        for (int gridX = 0; gridX < row.length(); gridX++) {
                           char symbol = row.charAt(gridX);
                           PartRole role = roleMap.get(symbol);
                           int relX = gridX - centerX;
                           int relZ = z - centerZ;
                           BlockPos pos = new BlockPos(relX, y, relZ);
                           if (role != null) {
                              if (role == PartRole.CONTROLLER) {
                                 foundControllerPositions.add(pos);
                              } else {
                                 Supplier<BlockState> partSupplier = phantomBlockState;
                                 if (symbolMap != null) {
                                    Supplier<BlockState> mapped = symbolMap.get(symbol);
                                    if (mapped != null) {
                                       partSupplier = mapped;
                                    }
                                 }

                                 structureMap.put(pos, partSupplier);
                                 if (role == PartRole.LADDER && ladderSideMap != null && ladderSideMap.containsKey(symbol)) {
                                    ladderDirs.put(pos, ladderTupleToLocalSet(symbol, ladderSideMap.get(symbol)));
                                 }

                                 if ((role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR)
                                    && energySideMap != null
                                    && energySideMap.containsKey(symbol)) {
                                    energyDirs.put(pos, energyTupleToLocalSet(symbol, energySideMap.get(symbol)));
                                 }

                                 if ((role == PartRole.FLUID_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR)
                                    && fluidSideMap != null
                                    && fluidSideMap.containsKey(symbol)) {
                                    fluidDirs.put(pos, fluidTupleToLocalSet(symbol, fluidSideMap.get(symbol)));
                                 }
                              }

                              positionSymbolMap.put(pos, symbol);
                              if (shapeMap != null && shapeMap.containsKey(symbol)) {
                                 specificPartShapes.put(pos, shapeMap.get(symbol));
                                 if (collisionMap != null && collisionMap.containsKey(symbol)) {
                                    specificCollisionShapes.put(pos, collisionMap.get(symbol));
                                 } else {
                                    specificCollisionShapes.put(pos, Shapes.empty());
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (foundControllerPositions.isEmpty()) {
               throw new IllegalArgumentException(
                  "Structure definition error: NO controller found! You must designate exactly one symbol as PartRole.CONTROLLER."
               );
            }

            if (foundControllerPositions.size() > 1) {
               StringBuilder sb = new StringBuilder();
               sb.append("Structure definition error: Multiple controllers found (");
               sb.append(foundControllerPositions.size());
               sb.append("). There can only be ONE controller per structure! Found at: ");

               for (int i = 0; i < foundControllerPositions.size(); i++) {
                  BlockPos p = foundControllerPositions.get(i);
                  sb.append(String.format("[%d, %d, %d]", p.getX(), p.getY(), p.getZ()));
                  if (i < foundControllerPositions.size() - 1) {
                     sb.append(", ");
                  }
               }

               throw new IllegalArgumentException(sb.toString());
            } else {
               BlockPos finalControllerPos = foundControllerPositions.get(0);
               MultiblockStructureHelper helper = new MultiblockStructureHelper(
                  structureMap, phantomBlockState, roleMap, positionSymbolMap, specificPartShapes, specificCollisionShapes, finalControllerPos
               );
               helper.ladderLocalDirections.putAll(ladderDirs);
               helper.energyLocalDirections.putAll(energyDirs);
               helper.fluidLocalDirections.putAll(fluidDirs);
               Character controllerSymbol = positionSymbolMap.get(finalControllerPos);
               if (controllerSymbol != null) {
                  if (energySideMap != null && energySideMap.containsKey(controllerSymbol)) {
                     helper.controllerEnergyLocalDirections = energyTupleToLocalSet(controllerSymbol, energySideMap.get(controllerSymbol));
                  }

                  if (fluidSideMap != null && fluidSideMap.containsKey(controllerSymbol)) {
                     helper.controllerFluidLocalDirections = fluidTupleToLocalSet(controllerSymbol, fluidSideMap.get(controllerSymbol));
                     helper.controllerFluidSidesFromStructure = true;
                  }
               }

               helper.placementOffset = computePlacementOffset(finalControllerPos, positionSymbolMap);
               return helper;
            }
         } else {
            throw new IllegalArgumentException("layers cannot be null or empty");
         }
      } else {
         throw new IllegalArgumentException("roleMap cannot be null or empty");
      }
   }

   private static int computePlacementOffset(BlockPos controllerPos, Map<BlockPos, Character> occupiedCells) {
      int minZ = 0;

      for (BlockPos gridPos : occupiedCells.keySet()) {
         BlockPos rel = gridPos.subtract(controllerPos);
         minZ = Math.min(minZ, rel.getZ());
      }

      return -minZ;
   }

   public MultiblockStructureHelper withPlacementOffset(int offset) {
      this.placementOffset = offset;
      return this;
   }

   private static BlockPos getMinPos(BlockPos p1, BlockPos p2) {
      return new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
   }

   private static BlockPos getMaxPos(BlockPos p1, BlockPos p2) {
      return new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
   }

   public boolean attemptAutoRepair(Level level, BlockPos currentCtrlPos, BlockState state, IMultiblockController controller) {
      if (level.isClientSide) {
         return false;
      }

      if (level.getNearestPlayer(currentCtrlPos.getX() + 0.5, currentCtrlPos.getY() + 0.5, currentCtrlPos.getZ() + 0.5, 64.0, false) == null) {
         return false;
      }

      Direction facing = (Direction)state.getValue(HorizontalDirectionalBlock.FACING);
      Map<BlockPos, BlockPos> expectedPosToGrid = new HashMap<>();

      for (Entry<BlockPos, Supplier<BlockState>> entry : this.structureMap.entrySet()) {
         BlockPos gridPos = entry.getKey();
         BlockPos expectedPos = this.getRotatedPos(currentCtrlPos, gridPos, facing);
         if (!expectedPos.equals(currentCtrlPos)) {
            expectedPosToGrid.put(expectedPos, gridPos);
         }
      }

      Set<BlockPos> expectedCurrentPositions = expectedPosToGrid.keySet();
      BlockPos newCtrlPos = currentCtrlPos;
      BlockPos oldOffset = controller.getLegacyControllerOffset();
      Set<BlockPos> expectedLegacyPositions = new HashSet<>();
      if (oldOffset != null) {
         for (BlockPos gridPos : this.structureMap.keySet()) {
            BlockPos offsetFromOld = gridPos.subtract(oldOffset);
            BlockPos expectedPos = currentCtrlPos.offset(rotate(offsetFromOld, facing));
            if (!expectedPos.equals(currentCtrlPos)) {
               expectedLegacyPositions.add(expectedPos);
            }
         }
      }

      int minX = currentCtrlPos.getX();
      int minY = currentCtrlPos.getY();
      int minZ = currentCtrlPos.getZ();
      int maxX = currentCtrlPos.getX();
      int maxY = currentCtrlPos.getY();
      int maxZ = currentCtrlPos.getZ();

      for (BlockPos pos : expectedCurrentPositions) {
         minX = Math.min(minX, pos.getX());
         minY = Math.min(minY, pos.getY());
         minZ = Math.min(minZ, pos.getZ());
         maxX = Math.max(maxX, pos.getX());
         maxY = Math.max(maxY, pos.getY());
         maxZ = Math.max(maxZ, pos.getZ());
      }

      for (BlockPos pos : expectedLegacyPositions) {
         minX = Math.min(minX, pos.getX());
         minY = Math.min(minY, pos.getY());
         minZ = Math.min(minZ, pos.getZ());
         maxX = Math.max(maxX, pos.getX());
         maxY = Math.max(maxY, pos.getY());
         maxZ = Math.max(maxZ, pos.getZ());
      }

      int minChunkX = minX >> 4;
      int maxChunkX = maxX >> 4;
      int minChunkZ = minZ >> 4;
      int maxChunkZ = maxZ >> 4;

      for (int cx = minChunkX - 1; cx <= maxChunkX + 1; cx++) {
         for (int cz = minChunkZ - 1; cz <= maxChunkZ + 1; cz++) {
            if (!level.hasChunk(cx, cz)) {
               return false;
            }
         }
      }

      Set<BlockPos> existingPhantoms = new HashSet<>();
      Block phantomBlock = this.phantomBlockState.get().getBlock();
      if (!(level instanceof ServerLevel serverLevel)) {
         return false;
      } else {
         for (int isPerfect = minChunkX; isPerfect <= maxChunkX; isPerfect++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
               LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(isPerfect, cz);
               if (chunk == null) {
                  return false;
               }

               int chunkMinX = Math.max(minX, isPerfect << 4);
               int chunkMaxX = Math.min(maxX, (isPerfect << 4) + 15);
               int chunkMinZ = Math.max(minZ, cz << 4);
               int chunkMaxZ = Math.min(maxZ, (cz << 4) + 15);

               for (int x = chunkMinX; x <= chunkMaxX; x++) {
                  for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                     for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState posState = chunk.getBlockState(pos);
                        if ((posState.is(phantomBlock) || posState.getBlock() instanceof UniversalMachinePartBlock)
                           && chunk.getBlockEntity(pos) instanceof IMultiblockPart part
                           && currentCtrlPos.equals(part.getControllerPos())) {
                           existingPhantoms.add(pos);
                        }
                     }
                  }
               }
            }
         }

         boolean isPerfect = existingPhantoms.size() == expectedCurrentPositions.size() && existingPhantoms.containsAll(expectedCurrentPositions);
         if (isPerfect) {
            for (BlockPos pos : expectedCurrentPositions) {
               if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
                  BlockPos gridPos = expectedPosToGrid.get(pos);
                  if (part.getPartRole() != this.resolvePartRole(gridPos, controller)) {
                     isPerfect = false;
                     break;
                  }
               }
            }
         }

         if (isPerfect) {
            MultiblockFrameHelper.updateFrameForController(level, currentCtrlPos);
            return true;
         }

         boolean noNewPartsNeeded = existingPhantoms.containsAll(expectedCurrentPositions);
         if (noNewPartsNeeded) {
            IS_REPAIRING.set(true);

            try {
               EnergyNetworkManager energyManager = EnergyNetworkManager.get((ServerLevel)level);

               for (BlockPos oldPos : existingPhantoms) {
                  if (!expectedCurrentPositions.contains(oldPos) && !oldPos.equals(currentCtrlPos)) {
                     if (level.getBlockEntity(oldPos) instanceof IMultiblockPart part) {
                        PartRole role = part.getPartRole();
                        if (role.canReceiveEnergy() || role.canSendEnergy()) {
                           energyManager.removeNode(oldPos);
                        }
                     }

                     level.removeBlock(oldPos, false);
                  }
               }

               boolean rolesChanged = false;

               for (BlockPos pos : expectedCurrentPositions) {
                  BlockEntity be = level.getBlockEntity(pos);
                  if (be instanceof IMultiblockPart part) {
                     BlockPos gridPos = expectedPosToGrid.get(pos);
                     PartRole expectedRole = this.resolvePartRole(gridPos, controller);
                     if (part.getPartRole() != expectedRole) {
                        rolesChanged = true;
                        part.setPartRole(expectedRole);
                        if (expectedRole != PartRole.LADDER) {
                           part.setAllowedClimbSides(Collections.emptySet());
                        } else {
                           Set<Direction> localSides = this.ladderLocalDirections.get(gridPos);
                           EnumSet<Direction> worldSides = EnumSet.noneOf(Direction.class);
                           if (localSides != null && !localSides.isEmpty()) {
                              for (Direction localDir : localSides) {
                                 worldSides.add(rotateLocalDirectionToWorld(localDir, facing));
                              }
                           } else {
                              worldSides.add(Direction.NORTH);
                              worldSides.add(Direction.SOUTH);
                              worldSides.add(Direction.EAST);
                              worldSides.add(Direction.WEST);
                           }

                           part.setAllowedClimbSides(worldSides);
                        }

                        if (expectedRole != PartRole.ENERGY_CONNECTOR && expectedRole != PartRole.UNIVERSAL_CONNECTOR) {
                           part.setAllowedEnergySides(Collections.emptySet());
                           energyManager.removeNode(pos);
                        } else {
                           Set<Direction> localEnergy = this.energyLocalDirections.get(gridPos);
                           EnumSet<Direction> worldEnergy = EnumSet.noneOf(Direction.class);
                           if (localEnergy != null && !localEnergy.isEmpty()) {
                              for (Direction localDir : localEnergy) {
                                 worldEnergy.add(rotateLocalDirectionToWorld(localDir, facing));
                              }
                           } else {
                              Collections.addAll(worldEnergy, Direction.values());
                           }

                           part.setAllowedEnergySides(worldEnergy);
                           energyManager.addNode(pos);
                        }

                        if (expectedRole != PartRole.FLUID_CONNECTOR && expectedRole != PartRole.UNIVERSAL_CONNECTOR) {
                           part.setAllowedFluidSides(Collections.emptySet());
                        } else {
                           Set<Direction> localFluid = this.fluidLocalDirections.get(gridPos);
                           EnumSet<Direction> worldFluid = EnumSet.noneOf(Direction.class);
                           if (localFluid != null && !localFluid.isEmpty()) {
                              for (Direction localDir : localFluid) {
                                 worldFluid.add(rotateLocalDirectionToWorld(localDir, facing));
                              }
                           } else {
                              Collections.addAll(worldFluid, Direction.values());
                           }

                           part.setAllowedFluidSides(worldFluid);
                        }

                        be.setChanged();
                     }
                  }
               }

               this.applyControllerSideConfig(level, currentCtrlPos, facing);
               MultiblockFrameHelper.updateFrameForController(level, currentCtrlPos);
               if (rolesChanged) {
                  level.sendBlockUpdated(currentCtrlPos, state, state, 3);
               }

               return true;
            } finally {
               IS_REPAIRING.set(false);
            }
         } else {
            if (oldOffset != null && !existingPhantoms.isEmpty()) {
               int legacyMatches = 0;
               int currentMatches = 0;

               for (BlockPos phantomPos : existingPhantoms) {
                  if (expectedLegacyPositions.contains(phantomPos)) {
                     legacyMatches++;
                  }

                  if (expectedCurrentPositions.contains(phantomPos)) {
                     currentMatches++;
                  }
               }

               if (legacyMatches > currentMatches) {
                  BlockPos localShift = this.controllerOffset.subtract(oldOffset);
                  BlockPos worldShift = rotate(localShift, facing);
                  newCtrlPos = currentCtrlPos.offset(worldShift);
               }
            }

            Set<BlockPos> finalExpectedPositions = new HashSet<>();

            for (BlockPos gridPos : this.structureMap.keySet()) {
               finalExpectedPositions.add(this.getRotatedPos(newCtrlPos, gridPos, facing));
            }

            for (BlockPos targetPos : finalExpectedPositions) {
               if (!targetPos.equals(newCtrlPos)
                  && !targetPos.equals(currentCtrlPos)
                  && !existingPhantoms.contains(targetPos)
                  && !this.isBlockReplaceable(level.getBlockState(targetPos))) {
                  return false;
               }
            }

            IS_REPAIRING.set(true);

            try {
               EnergyNetworkManager energyManager = EnergyNetworkManager.get((ServerLevel)level);

               for (BlockPos oldPos : existingPhantoms) {
                  if (!oldPos.equals(currentCtrlPos) && !oldPos.equals(newCtrlPos)) {
                     if (level.getBlockEntity(oldPos) instanceof IMultiblockPart part) {
                        PartRole role = part.getPartRole();
                        if (role.canReceiveEnergy() || role.canSendEnergy()) {
                           energyManager.removeNode(oldPos);
                        }
                     }

                     level.removeBlock(oldPos, false);
                  }
               }

               if (!newCtrlPos.equals(currentCtrlPos)) {
                  BlockEntity oldBE = level.getBlockEntity(currentCtrlPos);
                  CompoundTag nbt = null;
                  if (oldBE != null) {
                     nbt = oldBE.saveWithFullMetadata();
                     level.removeBlockEntity(currentCtrlPos);
                  }

                  level.removeBlock(currentCtrlPos, false);
                  level.setBlock(newCtrlPos, state, 3);
                  if (nbt != null) {
                     nbt.putInt("x", newCtrlPos.getX());
                     nbt.putInt("y", newCtrlPos.getY());
                     nbt.putInt("z", newCtrlPos.getZ());
                     BlockEntity newBE = level.getBlockEntity(newCtrlPos);
                     if (newBE != null) {
                        newBE.load(nbt);
                     }
                  }

                  level.updateNeighborsAt(currentCtrlPos, state.getBlock());
                  level.updateNeighborsAt(newCtrlPos, state.getBlock());
               } else {
                  this.placeStructure(level, newCtrlPos, facing, controller);
               }

               return true;
            } finally {
               IS_REPAIRING.set(false);
            }
         }
      }
   }

   private static EnumSet<Direction> ladderTupleToLocalSet(char symbol, boolean[] tuple) {
      if (tuple != null && tuple.length == LADDER_TUPLE_ORDER.length) {
         EnumSet<Direction> set = EnumSet.noneOf(Direction.class);

         for (int i = 0; i < LADDER_TUPLE_ORDER.length; i++) {
            if (tuple[i]) {
               set.add(LADDER_TUPLE_ORDER[i]);
            }
         }

         return set;
      } else {
         throw new IllegalArgumentException("ladderSideMap['" + symbol + "'] must be boolean[4] (north,south,west,east)");
      }
   }

   private static EnumSet<Direction> energyTupleToLocalSet(char symbol, boolean[] tuple) {
      if (tuple != null && tuple.length == ENERGY_TUPLE_ORDER.length) {
         EnumSet<Direction> set = EnumSet.noneOf(Direction.class);

         for (int i = 0; i < ENERGY_TUPLE_ORDER.length; i++) {
            if (tuple[i]) {
               set.add(ENERGY_TUPLE_ORDER[i]);
            }
         }

         return set;
      } else {
         throw new IllegalArgumentException("energySideMap['" + symbol + "'] must be boolean[6] (north,south,west,east,up,down)");
      }
   }

   private static EnumSet<Direction> fluidTupleToLocalSet(char symbol, boolean[] tuple) {
      if (tuple != null && tuple.length == FLUID_TUPLE_ORDER.length) {
         EnumSet<Direction> set = EnumSet.noneOf(Direction.class);

         for (int i = 0; i < FLUID_TUPLE_ORDER.length; i++) {
            if (tuple[i]) {
               set.add(FLUID_TUPLE_ORDER[i]);
            }
         }

         return set;
      } else {
         throw new IllegalArgumentException("fluidSideMap['" + symbol + "'] must be boolean[6] (north,south,west,east,up,down)");
      }
   }

   private static Direction rotateLocalDirectionToWorld(Direction localDir, Direction facing) {
      BlockPos rotatedVec = rotate(new BlockPos(localDir.getNormal()), facing);
      return Direction.getNearest(rotatedVec.getX(), rotatedVec.getY(), rotatedVec.getZ());
   }

   public static MultiblockStructureHelper createFromLayersWithRoles(
      String[][] layers,
      Map<Character, Supplier<BlockState>> symbolMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> roleMap,
      Map<Character, VoxelShape> shapeMap,
      Map<Character, VoxelShape> collisionMap
   ) {
      return createFromLayersWithRoles(layers, symbolMap, phantomBlockState, roleMap, shapeMap, collisionMap, null, null, null);
   }

   public static MultiblockStructureHelper createFromLayersWithRolesAndSides(
      String[][] layers,
      Map<Character, Supplier<BlockState>> symbolMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> roleMap,
      Map<Character, boolean[]> ladderSideMap,
      Map<Character, boolean[]> energySideMap
   ) {
      return createFromLayersWithRoles(layers, symbolMap, phantomBlockState, roleMap, null, null, ladderSideMap, energySideMap, null);
   }

   public static MultiblockStructureHelper createFromLayersWithRolesAndSides(
      String[][] layers,
      Map<Character, Supplier<BlockState>> symbolMap,
      Supplier<BlockState> phantomBlockState,
      Map<Character, PartRole> roleMap,
      Map<Character, boolean[]> ladderSideMap,
      Map<Character, boolean[]> energySideMap,
      Map<Character, boolean[]> fluidSideMap
   ) {
      return createFromLayersWithRoles(layers, symbolMap, phantomBlockState, roleMap, null, null, ladderSideMap, energySideMap, fluidSideMap);
   }

   public static MultiblockStructureHelper createFromLayersWithRoles(
      String[][] layers, Map<Character, Supplier<BlockState>> symbolMap, Supplier<BlockState> phantomBlockState, Map<Character, PartRole> roleMap
   ) {
      return createFromLayersWithRoles(layers, symbolMap, phantomBlockState, roleMap, null, null, null, null, null);
   }

   public BlockPos getControllerOffset() {
      return this.controllerOffset;
   }

   public int getPlacementOffset() {
      return this.placementOffset;
   }

   public VoxelShape getSpecificPartShape(BlockPos gridPos, Direction facing) {
      return this.partShapes.containsKey(gridPos) ? rotateShape(this.partShapes.get(gridPos), facing) : Shapes.block();
   }

   public VoxelShape getSpecificCollisionShape(BlockPos gridPos, Direction facing) {
      return this.collisionShapes.containsKey(gridPos) ? rotateShape(this.collisionShapes.get(gridPos), facing) : Shapes.block();
   }

   public boolean isFullBlock(BlockPos gridPos, Direction facing) {
      VoxelShape shape = this.getSpecificCollisionShape(gridPos, facing);
      if (shape.isEmpty()) {
         return false;
      }

      AABB bounds = shape.bounds();
      return bounds.minX <= 0.001
         && bounds.minY <= 0.001
         && bounds.minZ <= 0.001
         && bounds.maxX >= 0.999
         && bounds.maxY >= 0.999
         && bounds.maxZ >= 0.999;
   }

   public PartRole resolvePartRole(BlockPos gridPos, IMultiblockController controller) {
      Character symbol = this.positionSymbolMap.get(gridPos);
      return symbol != null && this.symbolRoleMap.containsKey(symbol) ? this.symbolRoleMap.get(symbol) : controller.getPartRole(gridPos);
   }

   private boolean isBlockReplaceable(BlockState state) {
      return this.replaceableBlocks.contains(state.getBlock())
         ? true
         : state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS);
   }

   public boolean checkPlacement(Level level, BlockPos controllerPos, Direction facing, Player player) {
      List<BlockPos> obstructions = new ArrayList<>();

      for (BlockPos relativePos : this.structureMap.keySet()) {
         BlockPos worldPos = this.getRotatedPos(controllerPos, relativePos, facing);
         if (!worldPos.equals(controllerPos)) {
            BlockState existingState = level.getBlockState(worldPos);
            if (!this.isBlockReplaceable(existingState)) {
               obstructions.add(worldPos);
            }
         }
      }

      if (!obstructions.isEmpty()) {
         player.displayClientMessage(Component.translatable("chat.hbm_m.structure.obstructed"), true);
         return false;
      } else {
         return true;
      }
   }

   public boolean checkPlacementFromFacade(Level level, BlockPos facadePos, Direction facing, Player player) {
      return this.checkPlacement(level, MultiblockPlacement.getCorePos(facadePos, facing, this), facing, player);
   }

   public Set<BlockPos> getPartOffsets() {
      return this.partOffsets;
   }

   public synchronized void placeStructure(Level level, BlockPos controllerPos, Direction facing, IMultiblockController controller) {
      if (!level.isClientSide) {
         List<BlockPos> energyConnectorPositions = new ArrayList<>();
         List<BlockPos> allPlacedPositions = new ArrayList<>();
         EnergyNetworkManager energyManager = EnergyNetworkManager.get((ServerLevel)level);

         for (Entry<BlockPos, Supplier<BlockState>> entry : this.structureMap.entrySet()) {
            BlockPos gridPos = entry.getKey();
            BlockPos worldPos = this.getRotatedPos(controllerPos, gridPos, facing);
            if (!worldPos.equals(controllerPos)) {
               BlockState partState = (BlockState)this.phantomBlockState.get().setValue(HorizontalDirectionalBlock.FACING, facing);
               level.setBlock(worldPos, partState, 2);
               allPlacedPositions.add(worldPos);
               if (level.getBlockEntity(worldPos) instanceof IMultiblockPart partBe) {
                  partBe.setControllerPos(controllerPos);
                  PartRole role = this.resolvePartRole(gridPos, controller);
                  if (controller instanceof DoorBlock doorBlock) {
                     String declId = doorBlock.getDoorDeclId();
                     DoorDecl decl = DoorDeclRegistry.getById(declId);
                     if (decl != null && decl.getStructureDefinition() != null) {
                        PartRole definedRole = decl.getStructureDefinition().getRoles().get(gridPos);
                        if (definedRole != null) {
                           role = definedRole;
                        }
                     }
                  }

                  partBe.setPartRole(role);
                  if (role == PartRole.LADDER) {
                     Set<Direction> localSides = this.ladderLocalDirections.get(gridPos);
                     EnumSet<Direction> worldSides = EnumSet.noneOf(Direction.class);
                     if (localSides != null && !localSides.isEmpty()) {
                        for (Direction localDir : localSides) {
                           worldSides.add(rotateLocalDirectionToWorld(localDir, facing));
                        }
                     } else {
                        worldSides.add(Direction.NORTH);
                        worldSides.add(Direction.SOUTH);
                        worldSides.add(Direction.EAST);
                        worldSides.add(Direction.WEST);
                     }

                     partBe.setAllowedClimbSides(worldSides);
                  }

                  if (role == PartRole.ENERGY_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR) {
                     Set<Direction> localEnergy = this.energyLocalDirections.get(gridPos);
                     EnumSet<Direction> worldEnergy = EnumSet.noneOf(Direction.class);
                     if (localEnergy != null && !localEnergy.isEmpty()) {
                        for (Direction localDir : localEnergy) {
                           worldEnergy.add(rotateLocalDirectionToWorld(localDir, facing));
                        }
                     } else {
                        Collections.addAll(worldEnergy, Direction.values());
                     }

                     partBe.setAllowedEnergySides(worldEnergy);
                     energyConnectorPositions.add(worldPos);
                     energyManager.addNode(worldPos);
                  }

                  if (role == PartRole.FLUID_CONNECTOR || role == PartRole.UNIVERSAL_CONNECTOR) {
                     Set<Direction> localFluid = this.fluidLocalDirections.get(gridPos);
                     EnumSet<Direction> worldFluid = EnumSet.noneOf(Direction.class);
                     if (localFluid != null && !localFluid.isEmpty()) {
                        for (Direction localDir : localFluid) {
                           worldFluid.add(rotateLocalDirectionToWorld(localDir, facing));
                        }
                     } else {
                        Collections.addAll(worldFluid, Direction.values());
                     }

                     partBe.setAllowedFluidSides(worldFluid);
                  }

                  if (role == PartRole.UNIVERSAL_CONNECTOR) {
                  }
               }
            }
         }

         MainRegistry.LOGGER
            .info(
               "Player {} placed multiblock at {} with {} parts: {}",
               new Object[]{controller, controllerPos, allPlacedPositions.size(), this.formatPositions(allPlacedPositions)}
            );
         this.applyControllerSideConfig(level, controllerPos, facing);
         MultiblockFrameHelper.updateFrameForController(level, controllerPos);

         for (BlockPos connectorPos : energyConnectorPositions) {
            for (Direction dir : Direction.values()) {
               BlockPos wirePos = connectorPos.relative(dir);
               BlockState wireState = level.getBlockState(wirePos);
               if (false) {
                  level.updateNeighborsAt(wirePos, wireState.getBlock());
               }
            }
         }
      }
   }

   private void applyControllerSideConfig(Level level, BlockPos controllerPos, Direction facing) {
      if (level.getBlockEntity(controllerPos) instanceof IMultiblockSidedIO sided) {
         EnumSet worldEnergy = EnumSet.noneOf(Direction.class);
         if (this.controllerEnergyLocalDirections != null && !this.controllerEnergyLocalDirections.isEmpty()) {
            for (Direction localDir : this.controllerEnergyLocalDirections) {
               worldEnergy.add(rotateLocalDirectionToWorld(localDir, facing));
            }

            sided.setAllowedEnergySides(worldEnergy);
         }

         EnumSet<Direction> worldFluid = EnumSet.noneOf(Direction.class);
         if (this.controllerFluidSidesFromStructure) {
            for (Direction localDir : this.controllerFluidLocalDirections) {
               worldFluid.add(rotateLocalDirectionToWorld(localDir, facing));
            }

            sided.setAllowedFluidSidesFromMultiblockStructure(worldFluid);
         }
      }
   }

   private String formatPositions(List<BlockPos> positions) {
      if (positions.isEmpty()) {
         return "[]";
      }

      StringBuilder sb = new StringBuilder("[");

      for (int i = 0; i < positions.size(); i++) {
         BlockPos pos = positions.get(i);
         sb.append(String.format("(%d,%d,%d)", pos.getX(), pos.getY(), pos.getZ()));
         if (i < positions.size() - 1) {
            sb.append(", ");
         }
      }

      sb.append("]");
      return sb.toString();
   }

   public void destroyStructure(Level level, BlockPos controllerPos, Direction facing) {
      if (!level.isClientSide && !IS_DESTROYING.get()) {
         IS_DESTROYING.set(true);

         try {
            EnergyNetworkManager energyManager = EnergyNetworkManager.get((ServerLevel)level);

            for (BlockPos gridPos : this.structureMap.keySet()) {
               BlockPos worldPos = this.getRotatedPos(controllerPos, gridPos, facing);
               if (level.getBlockState(worldPos).getBlock() instanceof UniversalMachinePartBlock) {
                  if (level.getBlockEntity(worldPos) instanceof IMultiblockPart part) {
                     PartRole role = part.getPartRole();
                     if (role.canReceiveEnergy() || role.canSendEnergy()) {
                        energyManager.removeNode(worldPos);
                     }
                  }

                  level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3);
               }
            }
         } finally {
            IS_DESTROYING.set(false);
         }
      }
   }

   public Map<BlockPos, Supplier<BlockState>> getStructureMap() {
      return this.structureMap;
   }

   public boolean isTopRingPart(BlockPos localOffset) {
      return MultiblockFrameHelper.isTopRingPart(this, localOffset);
   }

   public List<BlockPos> getTopRingWorldPositions(BlockPos controllerPos, Direction facing) {
      return MultiblockFrameHelper.getTopRingWorldPositions(this, controllerPos, facing);
   }

   public boolean computeFrameVisible(Level level, BlockPos controllerPos, Direction facing) {
      return MultiblockFrameHelper.computeFrameVisible(level, this, controllerPos, facing);
   }

   public static void updateFrameForController(Level level, BlockPos controllerPos) {
      MultiblockFrameHelper.updateFrameForController(level, controllerPos);
   }

   public static void onNeighborChangedForPart(Level level, BlockPos partPos, BlockPos changedPos) {
      MultiblockFrameHelper.onNeighborChangedForPart(level, partPos, changedPos);
   }

   public static BlockPos rotateBack(BlockPos pos, Direction facing) {
      return switch (facing) {
         case SOUTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
         case WEST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
         case EAST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
         default -> pos;
      };
   }

   public AABB getLocalRenderBoundingBox(Direction facing) {
      AABB cached = this.localRenderBoundsCache.get(facing);
      if (cached != null) {
         return cached;
      }

      int minX = 0;
      int minY = 0;
      int minZ = 0;
      int maxX = 0;
      int maxY = 0;
      int maxZ = 0;

      for (BlockPos gridPos : this.structureMap.keySet()) {
         BlockPos relative = gridPos.subtract(this.controllerOffset);
         BlockPos rotated = rotate(relative, facing);
         int x = rotated.getX();
         int y = rotated.getY();
         int z = rotated.getZ();
         if (x < minX) {
            minX = x;
         }

         if (x > maxX) {
            maxX = x;
         }

         if (y < minY) {
            minY = y;
         }

         if (y > maxY) {
            maxY = y;
         }

         if (z < minZ) {
            minZ = z;
         }

         if (z > maxZ) {
            maxZ = z;
         }
      }

      AABB result = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
      this.localRenderBoundsCache.put(facing, result);
      return result;
   }

   public AABB getRenderBoundingBox(BlockPos controllerWorldPos, Direction facing, double inflate) {
      AABB local = this.getLocalRenderBoundingBox(facing);
      AABB moved = local.move(controllerWorldPos.getX(), controllerWorldPos.getY(), controllerWorldPos.getZ());
      return inflate > 0.0 ? moved.inflate(inflate) : moved;
   }

   public VoxelShape generateShapeFromParts(Direction facing) {
      return this.shapeCache.computeIfAbsent(facing, f -> {
         VoxelShape finalShape = Shapes.empty();
         VoxelShape controllerShape = this.partShapes.getOrDefault(this.controllerOffset, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
         controllerShape = rotateShape(controllerShape, f);
         finalShape = Shapes.or(finalShape, controllerShape);

         for (BlockPos gridPos : this.structureMap.keySet()) {
            BlockPos relative = gridPos.subtract(this.controllerOffset);
            BlockPos rotatedPos = rotate(relative, f);
            VoxelShape rawShape = this.partShapes.getOrDefault(gridPos, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
            VoxelShape rotatedShape = rotateShape(rawShape, f);
            VoxelShape placedShape = rotatedShape.move(rotatedPos.getX(), rotatedPos.getY(), rotatedPos.getZ());
            finalShape = Shapes.or(finalShape, placedShape);
         }

         return finalShape.optimize();
      });
   }

   public static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
      if (facing == Direction.NORTH) {
         return shape;
      }

      // HeralisZ : forme tournée gardée (même géométrie qu'avant, simplifiée une fois pour toutes)
      int index = switch (facing) {
         case SOUTH -> 0;
         case WEST -> 1;
         case EAST -> 2;
         default -> -1;
      };
      if (index < 0) {
         return buildRotatedShape(shape, facing);
      }

      VoxelShape[] rotated = ROTATIONS.get(shape);
      if (rotated == null) {
         rotated = new VoxelShape[]{
            buildRotatedShape(shape, Direction.SOUTH).optimize(),
            buildRotatedShape(shape, Direction.WEST).optimize(),
            buildRotatedShape(shape, Direction.EAST).optimize()
         };
         if (ROTATIONS.size() >= ROTATIONS_MAX) {
            ROTATIONS.clear();
         }

         VoxelShape[] existing = ROTATIONS.putIfAbsent(shape, rotated);
         if (existing != null) {
            rotated = existing;
         }
      }

      return rotated[index];
   }

   private static VoxelShape buildRotatedShape(VoxelShape shape, Direction facing) {
      VoxelShape[] buffer = new VoxelShape[]{Shapes.empty()};
      shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
         double newMinX = minX;
         double newMinZ = minZ;
         double newMaxX = maxX;
         double newMaxZ = maxZ;
         switch (facing) {
            case SOUTH:
               newMinX = 1.0 - maxX;
               newMaxX = 1.0 - minX;
               newMinZ = 1.0 - maxZ;
               newMaxZ = 1.0 - minZ;
               break;
            case WEST:
               newMinX = minZ;
               newMaxX = maxZ;
               newMinZ = 1.0 - maxX;
               newMaxZ = 1.0 - minX;
               break;
            case EAST:
               newMinX = 1.0 - maxZ;
               newMaxX = 1.0 - minZ;
               newMinZ = minX;
               newMaxZ = maxX;
         }

         buffer[0] = Shapes.joinUnoptimized(buffer[0], Shapes.box(newMinX, minY, newMinZ, newMaxX, maxY, newMaxZ), BooleanOp.OR);
      });
      return buffer[0];
   }

   public BlockPos getRotatedPos(BlockPos controllerWorldPos, BlockPos partLocalPos, Direction facing) {
      BlockPos offsetFromController = partLocalPos.subtract(this.controllerOffset);
      return controllerWorldPos.offset(rotate(offsetFromController, facing));
   }

   public static BlockPos rotate(BlockPos pos, Direction facing) {
      return switch (facing) {
         case SOUTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
         case WEST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
         case EAST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
         default -> pos;
      };
   }

   public List<BlockPos> getAllPartPositions(BlockPos controllerPos, Direction facing) {
      List<BlockPos> positions = new ArrayList<>();

      for (BlockPos localOffset : this.structureMap.keySet()) {
         BlockPos worldPos = this.getRotatedPos(controllerPos, localOffset, facing);
         positions.add(worldPos);
      }

      return positions;
   }
}
