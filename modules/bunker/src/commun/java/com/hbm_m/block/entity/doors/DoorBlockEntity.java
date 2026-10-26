package com.hbm_m.block.entity.doors;

import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.KeycardDoors;
import fr.heralisz.bunker.salle.SallesPont;
import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.blockentity.ModBlockEntities;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.block.entity.doors.variant.DoorModelType;
import com.hbm_m.block.entity.doors.variant.DoorSkin;
import com.hbm_m.interfaces.IMultiblockController;
import com.hbm_m.interfaces.IMultiblockPart;
import com.hbm_m.main.MainRegistry;
import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import com.hbm_m.platform.PlatformHooks;
import com.hbm_m.sound.ClientSoundBootstrap;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class DoorBlockEntity extends BlockEntity implements IMultiblockPart {
   /**
    * Liens vers l'affichage (délai d'animation, rechargement des chunks, choix de modèle par défaut des configs client) :
    * branchés par la partie client (docs/METHODE_CLIENT_SERVEUR.md §6). Sans partie client : rien, comme sur un serveur.
    */
   public interface ClientHooks {
      boolean enDelai(DoorBlockEntity porte);

      void ajouterDelai(DoorBlockEntity porte, long ms);

      void invaliderChunk(BlockPos pos);

      /** Sélection de modèle par défaut (configs client), ou null si le registre n'est pas prêt. */
      @Nullable
      DoorModelSelection selectionParDefaut(String declId);
   }

   public static volatile ClientHooks CLIENT;

   private static final String DOOR_LOOP_SOUND_FACTORY = "com.hbm_m.client.sound.DoorLoopSoundFactory";
   public byte state = 0;
   private int openTicks = 0;
   public long animStartTime = 0L;
   private boolean locked = false;
   private boolean lastRedstoneState = false;
   // Ouverture lancée par le signal (lecteur, salle à carte, redstone) : si le signal coupe pendant l'ouverture, la porte
   // se referme à la fin de son ouverture au lieu de rester ouverte pour toujours.
   private boolean ouverteParSignal = false;
   private DoorModelSelection modelSelection = DoorModelSelection.DEFAULT;
   private Object cachedModelData;
   private String doorDeclId;
   private BlockPos controllerPos = null;
   private PartRole partRole = PartRole.DEFAULT;
   private Set<Direction> allowedClimbSides = EnumSet.noneOf(Direction.class);
   private Object loopingSound;

   @OnlyIn(Dist.CLIENT)
   public void clearAnimationDelayClient() {
      this.cachedModelData = null;
   }

   public DoorBlockEntity(BlockPos pos, BlockState state, String doorDeclId) {
      super((BlockEntityType)ModBlockEntities.DOOR_ENTITY.get(), pos, state);
      this.doorDeclId = doorDeclId;
      DoorDecl decl = DoorDeclRegistry.getById(doorDeclId);
      this.modelSelection = decl != null ? decl.getDefaultModelSelection() : DoorModelSelection.DEFAULT;
   }

   public DoorBlockEntity(BlockPos pos, BlockState state) {
      this(pos, state, "large_vehicle_door");
   }

   public DoorModelSelection getModelSelection() {
      return this.modelSelection;
   }

   public void setModelSelection(DoorModelSelection selection) {
      if (!this.modelSelection.equals(selection)) {
         this.modelSelection = selection;
         this.setChanged();
         if (this.level != null && this.level.isClientSide) {
            this.cachedModelData = null;
            if (CLIENT != null) CLIENT.invaliderChunk(this.worldPosition);
         }

         this.syncToClient();
      }
   }

   public void setModelType(DoorModelType type) {
      DoorSkin skin = type.isLegacy() ? DoorSkin.DEFAULT : this.modelSelection.getSkin();
      this.setModelSelection(new DoorModelSelection(type, skin));
   }

   public void setSkin(DoorSkin skin) {
      if (this.modelSelection.isModern()) {
         this.setModelSelection(new DoorModelSelection(DoorModelType.MODERN, skin));
      }
   }

   public void toggleModelType() {
      DoorModelType newType = this.modelSelection.getModelType().isLegacy() ? DoorModelType.MODERN : DoorModelType.LEGACY;
      this.setModelType(newType);
   }

   public void resetToDefault() {
      if (this.level != null) {
         // registre des modèles (configs client) : vide sur un serveur, donc sélection par défaut
         DoorModelSelection defaultSelection = CLIENT != null ? CLIENT.selectionParDefaut(this.doorDeclId) : null;
         this.setModelSelection(defaultSelection != null ? defaultSelection : DoorModelSelection.DEFAULT);
      }
   }

   @Override
   public synchronized void setControllerPos(BlockPos pos) {
      this.controllerPos = pos;
      this.setChanged();
   }

   @Nullable
   @Override
   public BlockPos getControllerPos() {
      return this.controllerPos;
   }

   @Override
   public void setPartRole(PartRole role) {
      this.partRole = role;
      this.setChanged();
   }

   @Override
   public PartRole getPartRole() {
      return this.partRole;
   }

   public boolean isController() {
      return this.controllerPos != null && this.controllerPos.equals(this.worldPosition) || this.controllerPos == null;
   }

   @Nullable
   public DoorBlockEntity getController() {
      if (this.level == null) {
         return null;
      }

      if (this.controllerPos == null) {
         return this;
      }

      BlockEntity be = this.level.getBlockEntity(this.controllerPos);
      return be instanceof DoorBlockEntity ? (DoorBlockEntity)be : null;
   }

   public void onStructureFormed() {
      this.state = 0;
      this.openTicks = 0;
      this.animStartTime = System.currentTimeMillis();
      if (this.level != null && this.level.isClientSide) {
         this.initModelSelection(true);
      }

      this.syncToClient();
   }

   public void clearRemoved() {
      super.clearRemoved();
      if (this.level instanceof ServerLevel serverLevel && !serverLevel.isClientSide()) {
         BlockPos pos = this.getBlockPos();
         serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 1, () -> {
            if (serverLevel.isLoaded(pos)) {
               BlockState state = serverLevel.getBlockState(pos);
               if (state.getBlock() instanceof IMultiblockController controller) {
                  controller.getStructureHelper().attemptAutoRepair(serverLevel, pos, state, controller);
               }
            }
         }));
      }
   }

   public DoorDecl getDoorDecl() {
      if (this.doorDeclId != null && !this.doorDeclId.isEmpty()) {
         return DoorDeclRegistry.getById(this.doorDeclId);
      } else {
         return this.getBlockState().getBlock() instanceof DoorBlock db ? DoorDeclRegistry.getById(db.getDoorDeclId()) : DoorDecl.LARGE_VEHICLE_DOOR;
      }
   }

   public String getDoorDeclId() {
      return this.doorDeclId;
   }

   public Direction getFacing() {
      BlockState state = this.getBlockState();
      return state.hasProperty(DoorBlock.FACING) ? (Direction)state.getValue(DoorBlock.FACING) : Direction.NORTH;
   }

   public void checkRedstonePower() {
      if (this.level != null && !this.level.isClientSide) {
         BlockState blockState = this.getBlockState();
         if (blockState.getBlock() instanceof DoorBlock doorBlock) {
            MultiblockStructureHelper var8 = doorBlock.getStructureHelper();
            Direction facing = (Direction)blockState.getValue(DoorBlock.FACING);
            // HeralisZ : porte à carte -> seule compte l'activation d'un lecteur de sa couleur, ou sa salle à carte
            // (10 s pour entrer, puis ouverte tant que quelqu'un est dedans : SallesPont, décidé par le serveur)
            AccessLevel keycard = KeycardDoors.levelOf(doorBlock);
            if (keycard != null) {
               this.updateRedstoneState(SallesPont.tenueOuverte(this.level, this.worldPosition)
                  || KeycardDoors.readerGranted(this.level, KeycardDoors.parts(this), keycard));
               return;
            }
            boolean isPowered = this.level.hasNeighborSignal(this.worldPosition);
            if (!isPowered) {
               for (BlockPos partPos : var8.getAllPartPositions(this.worldPosition, facing)) {
                  if (this.level.hasNeighborSignal(partPos)) {
                     isPowered = true;
                     break;
                  }
               }
            }

            this.updateRedstoneState(isPowered);
         }
      }
   }

   private void updateRedstoneState(boolean powered) {
      if (powered != this.lastRedstoneState) {
         this.lastRedstoneState = powered;
         if (powered) {
            if (this.state == 0 || this.state == 2) {
               this.open();
               this.ouverteParSignal = true;
            }
         } else if (this.state == 1) {
            this.close();
         } else if (this.state == 3) {
            int openTime = this.getDoorDecl().getOpenTime();
            if (openTime <= 25) {
               this.close();
            }
         }

         this.setChanged();
      }
   }

   private int getServerOpenTime() {
      DoorDecl decl = this.getDoorDecl();
      return decl != null ? decl.getOpenTime() : 60;
   }

   public float getOpenProgress(float partialTick) {
      int openTime = this.getServerOpenTime();
      if (openTime > 0) {
         long currentTime = System.currentTimeMillis();
         long elapsedTime = currentTime - this.animStartTime;
         int totalTimeMs = openTime * 50;

         return switch (this.state) {
            case 0 -> 0.0F;
            case 1 -> 1.0F;
            case 2 -> Math.max(0.0F, 1.0F - (float)elapsedTime / totalTimeMs);
            case 3 -> Math.min(1.0F, (float)elapsedTime / totalTimeMs);
            default -> 0.0F;
         };
      } else {
         return this.state != 1 && this.state != 3 ? 0.0F : 1.0F;
      }
   }

   public float getOpenProgress() {
      return this.getOpenProgress(0.0F);
   }

   public byte getState() {
      return this.state;
   }

   public long getAnimStartTime() {
      return this.animStartTime;
   }

   public int getSkinIndex() {
      return 0;
   }

   public void open() {
      if (this.state == 0 || this.state == 2) {
         this.setState((byte)3);
      }
   }

   public void close() {
      if (this.state == 1 || this.state == 3) {
         this.setState((byte)2);
      }
   }

   public void toggle() {
      if (this.state != 2 && this.state != 3) {
         if (this.state == 0) {
            this.open();
         } else if (this.state == 1) {
            this.close();
         }
      }
   }

   public boolean isMoving() {
      return this.state == 2 || this.state == 3 ? true : this.level != null && this.level.isClientSide && CLIENT != null && CLIENT.enDelai(this);
   }

   private void setState(byte newState) {
      this.state = newState;
      this.animStartTime = System.currentTimeMillis();
      if (newState == 3) {
         this.openTicks = 0;
      } else if (newState == 2) {
         this.openTicks = this.getServerOpenTime();
      }

      if (this.level != null && !this.level.isClientSide) {
         boolean isMoving = newState == 2 || newState == 3;
         boolean isOpen = newState == 1;
         BlockState currentState = this.getBlockState();
         if (currentState.getBlock() instanceof DoorBlock) {
            boolean needsUpdate = false;
            BlockState newBlockState = currentState;
            if (currentState.hasProperty(DoorBlock.DOOR_MOVING) && (Boolean)currentState.getValue(DoorBlock.DOOR_MOVING) != isMoving) {
               newBlockState = (BlockState)newBlockState.setValue(DoorBlock.DOOR_MOVING, isMoving);
               needsUpdate = true;
            }

            if (currentState.hasProperty(DoorBlock.OPEN) && (newState == 0 || newState == 1) && (Boolean)currentState.getValue(DoorBlock.OPEN) != isOpen) {
               newBlockState = (BlockState)newBlockState.setValue(DoorBlock.OPEN, isOpen);
               needsUpdate = true;
            }

            if (needsUpdate) {
               this.level.setBlock(this.worldPosition, newBlockState, 3);
            }
         }
      }

      if (this.level != null && this.level.isClientSide) {
         this.cachedModelData = null;
         if (CLIENT != null) CLIENT.invaliderChunk(this.worldPosition);
      }

      this.syncToClient();
   }

   public boolean isOpen() {
      return this.state == 1;
   }

   public boolean isLocked() {
      return this.locked;
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
      this.syncToClient();
   }

   public static void serverTick(Level level, BlockPos pos, BlockState state, DoorBlockEntity be) {
      int openTime = be.getServerOpenTime();
      boolean shouldSync = false;
      if (be.state == 3) {
         be.openTicks++;
         if (be.openTicks >= openTime) {
            be.state = 1;
            be.openTicks = openTime;
            shouldSync = true;
            if (state.hasProperty(DoorBlock.OPEN)) {
               level.setBlock(pos, (BlockState)((BlockState)state.setValue(DoorBlock.DOOR_MOVING, false)).setValue(DoorBlock.OPEN, true), 3);
            }

            be.notifyNeighborsOfStateChange(level, pos);
            if (be.ouverteParSignal && !be.lastRedstoneState) be.close();
            be.ouverteParSignal = false;
         }
      } else if (be.state == 2) {
         be.openTicks--;
         if (be.openTicks <= 0) {
            be.state = 0;
            be.openTicks = 0;
            shouldSync = true;
            if (state.hasProperty(DoorBlock.OPEN)) {
               level.setBlock(pos, (BlockState)((BlockState)state.setValue(DoorBlock.DOOR_MOVING, false)).setValue(DoorBlock.OPEN, false), 3);
            }

            be.notifyNeighborsOfStateChange(level, pos);
         }
      }

      if (be.state == 2 || be.state == 3) {
         DoorDecl decl = be.getDoorDecl();
         if (decl != null) {
            decl.onTick(be);
         }
      }

      if (shouldSync) {
         be.syncToClient();
      }
   }

   private void notifyNeighborsOfStateChange(Level level, BlockPos controllerPos) {
      BlockState blockState = this.getBlockState();
      if (blockState.getBlock() instanceof DoorBlock doorBlock) {
         Direction var13 = (Direction)blockState.getValue(DoorBlock.FACING);
         MultiblockStructureHelper structureHelper = doorBlock.getStructureHelper();
         boolean isOpen = this.state != 0;
         BlockState controllerState = level.getBlockState(controllerPos);
         level.sendBlockUpdated(controllerPos, controllerState, controllerState, 2);
         level.updateNeighborsAt(controllerPos, controllerState.getBlock());
         level.getLightEngine().checkBlock(controllerPos);

         for (BlockPos partPos : structureHelper.getAllPartPositions(controllerPos, var13)) {
            BlockState partState = level.getBlockState(partPos);
            if (partState.hasProperty(UniversalMachinePartBlock.PASSABLE)) {
               boolean currentPassable = (Boolean)partState.getValue(UniversalMachinePartBlock.PASSABLE);
               if (currentPassable != isOpen) {
                  level.setBlock(partPos, (BlockState)partState.setValue(UniversalMachinePartBlock.PASSABLE, isOpen), 2);
               }
            }

            partState = level.getBlockState(partPos);
            level.sendBlockUpdated(partPos, partState, partState, 2);
            level.getLightEngine().checkBlock(partPos);
         }
      }
   }

   public DoorDecl getServerDoorDecl() {
      return DoorDeclRegistry.getById(this.doorDeclId);
   }

   private void handleNewState(byte oldState, byte newState) {
      if (oldState != newState) {
         if (this.isController()) {
            DoorDecl decl = this.getDoorDecl();
            if (decl != null) {
               if (oldState == 0 && newState == 3) {
                  this.handleSoundTransition(decl.getOpenSoundStart(), decl.getOpenSoundLoop(), decl.getSoundLoop2());
               } else if (oldState == 1 && newState == 2) {
                  this.handleSoundTransition(decl.getCloseSoundStart(), decl.getCloseSoundLoop(), decl.getSoundLoop2());
               } else if (oldState == 3 && newState == 1) {
                  this.handleSoundEnd(decl.getOpenSoundEnd());
               } else if (oldState == 2 && newState == 0) {
                  this.handleSoundEnd(decl.getCloseSoundEnd());
               } else {
                  ClientSoundBootstrap.stopSound(this.level, this.worldPosition);
               }
            }
         }
      }
   }

   private void handleSoundTransition(SoundEvent startSound, SoundEvent loopSound, SoundEvent loopSound2) {
      if (startSound != null) {
         ClientSoundBootstrap.playOneShotSound(this.level, this.worldPosition, startSound, this.getDoorDecl().getSoundVolume());
      }

      if (loopSound != null) {
         ClientSoundBootstrap.updateDoorSoundRaw(this.level, this.worldPosition, "loop1", true, () -> this.createLoopingSoundReflect(loopSound));
      }

      if (loopSound2 != null) {
         ClientSoundBootstrap.updateDoorSoundRaw(this.level, this.worldPosition, "loop2", true, () -> this.createLoopingSoundReflect(loopSound2));
      }
   }

   private void handleSoundEnd(SoundEvent endSound) {
      ClientSoundBootstrap.stopSpecificSound(this.level, this.worldPosition, "loop1");
      ClientSoundBootstrap.stopSpecificSound(this.level, this.worldPosition, "loop2");
      if (endSound != null) {
         ClientSoundBootstrap.playOneShotSound(this.level, this.worldPosition, endSound, this.getDoorDecl().getSoundVolume());
      }
   }

   private Object createLoopingSoundReflect(SoundEvent sound) {
      try {
         return Class.forName("com.hbm_m.client.sound.DoorLoopSoundFactory")
            .getMethod("create", DoorBlockEntity.class, SoundEvent.class)
            .invoke(null, this, sound);
      } catch (ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   public void setRemoved() {
      super.setRemoved();
      if (this.level != null && this.level.isClientSide) {
         ClientSoundBootstrap.stopSound(this.level, this.worldPosition);
      }
   }

   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putByte("state", this.state);
      tag.putInt("openTicks", this.openTicks);
      tag.putLong("animStartTime", this.animStartTime);
      tag.putString("doorDeclId", this.doorDeclId);
      tag.putBoolean("locked", this.locked);
      tag.putBoolean("redstoneState", this.lastRedstoneState);
      this.modelSelection.save(tag);
      if (this.controllerPos != null) {
         tag.putLong("controllerPos", this.controllerPos.asLong());
      }

      if (!this.allowedClimbSides.isEmpty()) {
         int mask = 0;

         for (Direction dir : this.allowedClimbSides) {
            mask |= 1 << dir.get3DDataValue();
         }

         tag.putInt("climbSides", mask);
      }

      tag.putString("partRole", this.partRole.name());
   }

   public void load(CompoundTag tag) {
      super.load(tag);
      byte oldState = this.state;
      this.state = tag.getByte("state");
      this.openTicks = tag.getInt("openTicks");
      this.animStartTime = tag.getLong("animStartTime");
      this.locked = tag.getBoolean("locked");
      this.lastRedstoneState = tag.getBoolean("redstoneState");
      boolean hadModelSelectionInNbt = tag.contains("modelType");
      if (hadModelSelectionInNbt) {
         this.modelSelection = DoorModelSelection.load(tag);
      } else {
         this.modelSelection = DoorModelSelection.DEFAULT;
      }

      this.cachedModelData = null;
      if (tag.contains("controllerPos")) {
         this.controllerPos = BlockPos.of(tag.getLong("controllerPos"));
      }

      if (tag.contains("doorDeclId")) {
         this.doorDeclId = tag.getString("doorDeclId");
      }

      if (tag.contains("partRole")) {
         try {
            this.partRole = PartRole.valueOf(tag.getString("partRole"));
         } catch (IllegalArgumentException e) {
            this.partRole = PartRole.DEFAULT;
         }
      }

      if (tag.contains("climbSides")) {
         int mask = tag.getInt("climbSides");
         this.allowedClimbSides.clear();

         for (Direction dir : Direction.values()) {
            if ((mask & 1 << dir.get3DDataValue()) != 0) {
               this.allowedClimbSides.add(dir);
            }
         }
      }

      if (this.level != null && this.level.isClientSide) {
         this.initModelSelection(!hadModelSelectionInNbt);
         this.handleNewState(oldState, this.state);
         if (this.state == 2 || this.state == 3) {
            this.animStartTime = System.currentTimeMillis();
         }

         if ((oldState == 2 || oldState == 3) && (this.state == 0 || this.state == 1)) {
            if (CLIENT != null) CLIENT.ajouterDelai(this, 500L);
         }
      }
   }

   public void initModelSelection(boolean applyConfigDefault) {
      if (applyConfigDefault && CLIENT != null) {
         {
            DoorModelSelection defaultSelection = CLIENT.selectionParDefaut(this.doorDeclId);
            if (defaultSelection != null && !defaultSelection.equals(DoorModelSelection.DEFAULT)) {
               this.modelSelection = defaultSelection;
               this.cachedModelData = null;
               MainRegistry.LOGGER.debug("Initialized model selection for door {}: {}", this.doorDeclId, defaultSelection);
            }
         }
      }
   }

   public CompoundTag getUpdateTag() {
      CompoundTag tag = super.getUpdateTag();
      this.saveAdditional(tag);
      return tag;
   }

   public void handleUpdateTag(CompoundTag tag) {
      this.load(tag);
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public int getOpenTicks() {
      return this.openTicks;
   }

   public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
      CompoundTag tag = PlatformHooks.getItemTag(pkt);
      if (tag != null) {
         byte prevState = this.state;
         DoorModelSelection prevSelection = this.modelSelection;
         this.load(tag);
         if (this.level != null && this.level.isClientSide) {
            boolean visibleChange = isVisibleStateChange(prevState, this.state) || !prevSelection.equals(this.modelSelection);
            if (visibleChange) {
               if (CLIENT != null) CLIENT.invaliderChunk(this.worldPosition);
            }
         }
      }
   }

   private static boolean isVisibleStateChange(byte oldS, byte newS) {
      boolean wasMoving = oldS == 2 || oldS == 3;
      boolean isMoving = newS == 2 || newS == 3;
      boolean wasOpen = oldS == 1;
      boolean isOpen = newS == 1;
      return wasMoving != isMoving || wasOpen != isOpen;
   }

   private void syncToClient() {
      if (this.level != null && !this.level.isClientSide && this.level instanceof ServerLevel serverLevel) {
         this.setChanged();
         ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);

         for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) < 4096.0) {
               player.connection.send(packet);
            }
         }
      }
   }

   public AABB getRenderBoundingBox() {
      double radius = 8.0;
      if (this.level != null && this.level.isClientSide) {
         DoorDecl decl = this.getDoorDecl();
         if (decl != null) {
            radius = decl.getRenderRadius();
         }
      }

      return new AABB(this.worldPosition).inflate(radius);
   }

   @Override
   public void setAllowedClimbSides(Set<Direction> sides) {
      this.allowedClimbSides = EnumSet.copyOf(sides);
      this.setChanged();
   }

   @Override
   public Set<Direction> getAllowedClimbSides() {
      return this.allowedClimbSides;
   }
}
