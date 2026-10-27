package com.hbm_m.block.entity.doors;

import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.block.entity.doors.variant.DoorSkin;
import com.hbm_m.interfaces.IDoorAnimator;
import com.hbm_m.multiblock.MultiblockStructureHelper;
import com.hbm_m.multiblock.PartRole;
import com.hbm_m.sound.ModSounds;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public abstract class DoorDecl {
   protected DoorDecl.DoorStructureDefinition structureDefinition;
   private Map<String, AABB> cachedPartAABBs = new HashMap<>();
   public static final DoorDecl LARGE_VEHICLE_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         builder.addSymbol('X', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('O', Shapes.empty(), PartRole.DEFAULT);
         builder.addSymbol('H', Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 10.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('M', Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0), PartRole.DEFAULT);
         String[] closed = new String[]{"XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXXXXX", "XXXCXXX"};
         String[] open = new String[]{"XTTTTTX", "HOOOOOG", "HOOOOOG", "HOOOOOG", "HOOOOOG", "HMMMMMG"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "large_vehicle_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("doorLeft".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, 3.0F * this.getNormTime(openTicks));
         } else if ("doorRight".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, -3.0F * this.getNormTime(openTicks));
         } else {
            super.getTranslation(partName, openTicks, child, trans);
         }
      }

      @Override
      public boolean isDynamicShape() {
         return true;
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{1.0, 0.0, 0.0, 3.5}, {-1.0, 0.0, 0.0, 3.5}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[0][];
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getCloseSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl ROUND_AIRLOCK_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         builder.addSymbol('O', Shapes.empty(), PartRole.DEFAULT);
         builder.addSymbol('H', Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 10.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('M', Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0), PartRole.DEFAULT);
         String[] closed = new String[]{"####", "####", "####", "#C##"};
         String[] open = new String[]{"HTTG", "HOOG", "HOOG", "HMMG"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "round_airlock_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("doorLeft".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, 1.5F * this.getNormTime(openTicks));
         } else if ("doorRight".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, -1.5F * this.getNormTime(openTicks));
         } else {
            super.getTranslation(partName, openTicks, child, trans);
         }
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, 0.0, 1.0, 2.0001}, {0.0, 0.0, -1.0, 2.0001}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{0, 0, 0, -2, 4, 2}, {0, 0, 0, 3, 4, 2}};
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getCloseSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl FIRE_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('#', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         builder.addSymbol('O', Shapes.empty(), PartRole.DEFAULT);
         builder.addSymbol('H', Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('M', Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0), PartRole.DEFAULT);
         String[] closed = new String[]{"####", "####", "#C##"};
         String[] open = new String[]{"HTTG", "HOOG", "HMMG"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "fire_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 160;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if (!"door".equals(partName) && "frame".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, 0.0F);
         } else {
            this.set(trans, 0.0F, 3.0F * this.getNormTime(openTicks), 0.0F);
         }
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, -1.0, 0.0, 3.0001}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{-1, 0, 0, 3, 4, 1}};
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.WGH_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.WGH_START.get();
      }

      @Override
      public SoundEvent getSoundLoop2() {
         return (SoundEvent)ModSounds.ALARM_6.get();
      }

      @Override
      public float getSoundVolume() {
         return 1.5F;
      }
   };
   public static final DoorDecl SLIDING_BLAST_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('#', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         builder.addSymbol('O', Shapes.empty(), PartRole.DEFAULT);
         String[] closed = new String[]{"#######", "#######", "#######", "###C###"};
         String[] open = new String[]{"#######", "#OOOOO#", "#OOOOO#", "#OOOOO#"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"Frame", "LeftDoor", "RightDoor", "LeftLock", "RightLock"};
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "sliding_blast_door");
      }

      @Override
      public ResourceLocation getColladaAnimationSource() {
         return new ResourceLocation("heralisz_bunker", "models/block/doors/sliding_blast_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 24;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         float progress = this.getNormTime(openTicks, 0.0F, 24.0F);
         if ("LeftDoor".equals(partName) || "LeftLock".equals(partName)) {
            this.set(trans, 0.0F, 0.0F, 3.0F * progress);
         } else if (!"RightDoor".equals(partName) && !"RightLock".equals(partName)) {
            super.getTranslation(partName, openTicks, child, trans);
         } else {
            this.set(trans, 0.0F, 0.0F, -3.0F * progress);
         }
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans, @Nullable DoorModelSelection selection) {
         if (selection != null && !selection.isLegacy()) {
            float t = openTicks;
            float doorProg = Math.max(0.0F, Math.min(1.0F, (t - 7.0F) / 17.0F));
            float maxOpen = 2.125F;
            if ("LeftDoor".equals(partName) || "LeftLock".equals(partName)) {
               this.set(trans, 0.0F, 0.0F, maxOpen * doorProg);
            } else if (!"RightDoor".equals(partName) && !"RightLock".equals(partName)) {
               super.getTranslation(partName, openTicks, child, trans, selection);
            } else {
               this.set(trans, 0.0F, 0.0F, -maxOpen * doorProg);
            }
         } else {
            this.getTranslation(partName, openTicks, child, trans);
         }
      }

      @Override
      public void getOrigin(String partName, float[] orig, @Nullable DoorModelSelection selection) {
         if (selection == null || selection.isLegacy() || !"LeftLock".equals(partName) && !"RightLock".equals(partName)) {
            super.getOrigin(partName, orig, selection);
         } else {
            this.set(orig, 0.0F, 1.8125F, 0.0F);
         }
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot, @Nullable DoorModelSelection selection) {
         if (selection != null && !selection.isLegacy() && ("LeftLock".equals(partName) || "RightLock".equals(partName))) {
            float t = openTicks;
            float lockProg = Math.max(0.0F, Math.min(1.0F, t / 4.0F));
            this.set(rot, 90.0F + 90.0F * lockProg, 0.0F, 0.0F);
         } else {
            super.getRotation(partName, openTicks, rot, selection);
         }
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, 0.0, 1.0, 2.5}, {0.0, 0.0, -1.0, 2.5}};
      }

      @Override
      public int getBakedModelRotationOffsetY() {
         return -90;
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_OPENED.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_OPENING.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_SHUT.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl SLIDING_SEAL_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape thinDoorShape = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
         builder.addSymbol('#', thinDoorShape, PartRole.DEFAULT);
         builder.addSymbol('C', thinDoorShape, PartRole.CONTROLLER);
         String[] closed = new String[]{"#", "C"};
         String[] open = new String[]{" ", " "};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "sliding_seal_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 20;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if (partName.startsWith("door")) {
            this.set(trans, 0.0F, 0.0F, this.getNormTime(openTicks));
         } else {
            this.set(trans, 0.0F, 0.0F, 0.0F);
         }
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, 0.0, -1.0, 0.5001}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{0, 0, 0, 1, 2, 2}};
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.METAL_STOP_1.get();
      }

      @Override
      public SoundEvent getOpenSoundStart() {
         return (SoundEvent)ModSounds.DOOR_MOVE_2.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl SECURE_ACCESS_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         builder.addSymbol('X', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('O', Shapes.empty(), PartRole.DEFAULT);
         builder.addSymbol('H', Block.box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 6.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('M', Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0), PartRole.DEFAULT);
         String[] closed = new String[]{"XXXXX", "MMMMM", "MMMMM", "MMMMM", "XXCXX"};
         String[] open = new String[]{"TTTTT", "OOOOO", "OOOOO", "OOOOO", "XXXXX"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "secure_access_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public int getOpenTime() {
         return 120;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if (!partName.equals("base")) {
            this.set(trans, 0.0F, 3.5F * this.getNormTime(openTicks), 0.0F);
         } else {
            super.getTranslation(partName, openTicks, child, trans);
         }
      }

      @Override
      public boolean isDynamicShape() {
         return true;
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, -1.0, 0.0, 5.0}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{-2, 1, 0, 4, 5, 1}};
      }

      @Override
      public SoundEvent getCloseSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl QE_SLIDING = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape thinDoorShape = Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0);
         builder.addSymbol('#', thinDoorShape, PartRole.DEFAULT);
         builder.addSymbol('C', thinDoorShape, PartRole.CONTROLLER);
         String[] closed = new String[]{"##", "C#"};
         String[] open = new String[]{"  ", "  "};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "qe_sliding_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public int getOpenTime() {
         return 10;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if (partName.startsWith("left")) {
            this.set(trans, 0.0F, 0.0F, 0.99F * this.getNormTime(openTicks));
         } else if (partName.startsWith("right")) {
            this.set(trans, 0.0F, 0.0F, -0.99F * this.getNormTime(openTicks));
         }
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{0, 0, 0, 2, 2, 2}};
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_OPENED.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_SHUT.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.SLIDING_DOOR_OPENING.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl QE_CONTAINMENT = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape thinDoorShape = Block.box(0.0, 0.0, 4.0, 16.0, 16.0, 12.0);
         builder.addSymbol('#', thinDoorShape, PartRole.DEFAULT);
         builder.addSymbol('C', thinDoorShape, PartRole.CONTROLLER);
         builder.addSymbol('H', Block.box(0.0, 0.0, 4.0, 4.0, 16.0, 12.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(12.0, 0.0, 4.0, 16.0, 16.0, 12.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 6.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         builder.addSymbol('M', Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0), PartRole.DEFAULT);
         String[] closed = new String[]{"###", "###", "#C#"};
         String[] open = new String[]{"TTT", "H G", "HMG"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "qe_containment_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public int getOpenTime() {
         return 160;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if (!"door".equals(partName) && !"decal".equals(partName)) {
            super.getTranslation(partName, openTicks, child, trans);
         } else {
            this.set(trans, 0.0F, 3.0F * this.getNormTime(openTicks), 0.0F);
         }
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, -1.0, 0.0, 3.0001}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{-1, 0, 0, 3, 3, 1}};
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.WGH_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.WGH_START.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl WATER_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape thinDoorShape = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
         builder.addSymbol('#', thinDoorShape, PartRole.DEFAULT);
         builder.addSymbol('C', thinDoorShape, PartRole.CONTROLLER);
         builder.addSymbol('H', Block.box(0.0, 0.0, 6.0, 8.0, 16.0, 10.0), PartRole.DEFAULT);
         builder.addSymbol('G', Block.box(11.0, 0.0, 6.0, 16.0, 16.0, 10.0), PartRole.DEFAULT);
         builder.addSymbol('T', Block.box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0), PartRole.DEFAULT);
         String[] closed = new String[]{"###", "###", "#C#"};
         String[] open = new String[]{"TTT", "H G", "H G"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "water_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("bolt".equals(partName)) {
            float boltProgress = Math.min(1.0F, openTicks / 30.0F);
            this.set(trans, 0.0F, 0.0F, 0.4F * this.smoothstep(boltProgress));
         } else {
            this.set(trans, 0.0F, 0.0F, 0.0F);
         }
      }

      @Override
      public void getOrigin(String partName, float[] orig) {
         if ("door".equals(partName) || "bolt".equals(partName)) {
            this.set(orig, 0.125F, 1.5F, 1.18F);
         } else if ("spinny_upper".equals(partName)) {
            this.set(orig, 0.041499F, 2.43569F, -0.587849F);
         } else if ("spinny_lower".equals(partName)) {
            this.set(orig, 0.041499F, 0.571054F, -0.587849F);
         } else {
            super.getOrigin(partName, orig);
         }
      }

      @Override
      public void getOrigin(String partName, float[] orig, @Nullable DoorModelSelection selection) {
         if (selection != null && selection.isModern()) {
            if ("spinny_upper".equals(partName)) {
               this.set(orig, 0.0F, 2.28125F, -0.40625F);
               return;
            }

            if ("spinny_lower".equals(partName)) {
               this.set(orig, 0.0F, 0.71875F, -0.40625F);
               return;
            }
         }

         this.getOrigin(partName, orig);
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot) {
         if (partName.startsWith("spinny")) {
            float spinnyProgress = Math.min(1.0F, openTicks / 30.0F);
            this.set(rot, this.smoothstep(spinnyProgress) * 360.0F, 0.0F, 0.0F);
         } else if (!"door".equals(partName) && !"bolt".equals(partName)) {
            super.getRotation(partName, openTicks, rot);
         } else {
            float doorProgress = Math.max(0.0F, Math.min(1.0F, (openTicks - 30.0F) / 30.0F));
            this.set(rot, 0.0F, this.smoothstep(doorProgress) * -134.0F, 0.0F);
         }
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot, @Nullable DoorModelSelection selection) {
         if (selection != null && selection.isModern() && partName.startsWith("spinny")) {
            float boltProgress = Math.min(1.0F, openTicks / 30.0F);
            this.set(rot, this.smoothstep(boltProgress) * 360.0F, 0.0F, 0.0F);
         } else {
            this.getRotation(partName, openTicks, rot);
         }
      }

      @Override
      public boolean doesRender(String partName, boolean child) {
         return child || !partName.startsWith("spinny");
      }

      @Override
      public String[] getChildren(String partName) {
         return "door".equals(partName) ? new String[]{"spinny_lower", "spinny_upper"} : super.getChildren(partName);
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{1, 0, 0, -3, 3, 2}};
      }

      private float smoothstep(float t) {
         return t * t * (3.0F - 2.0F * t);
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_START.get();
      }

      @Override
      public SoundEvent getOpenSoundStart() {
         return (SoundEvent)ModSounds.LEVER_1.get();
      }

      @Override
      public SoundEvent getCloseSoundStart() {
         return null;
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.LEVER_1.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl SILO_HATCH = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('#', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         String[] closed = new String[]{"#####", "#####", "##C##", "#####", "#####"};
         String[] open = new String[]{"#####", "#   #", "#   #", "#   #", "#####"};
         this.defineStructure(builder.parseHorizontal(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "silo_hatch");
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public boolean remoteControllable() {
         return true;
      }

      @Override
      public int getBlockOffset() {
         return 2;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("Hatch".equals(partName)) {
            float smoothTime = this.smoothstep(this.getNormTime(openTicks, 0.0F, 10.0F));
            this.set(trans, 0.0F, 0.25F * smoothTime, 0.0F);
         }
      }

      @Override
      public boolean isDynamicShape() {
         return true;
      }

      @Override
      public void getOrigin(String partName, float[] orig) {
         if ("Hatch".equals(partName)) {
            this.set(orig, 0.0F, 0.875F, -1.875F);
         }
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot) {
         if ("Hatch".equals(partName)) {
            float smoothTime = this.smoothstep(this.getNormTime(openTicks, 20.0F, 100.0F));
            this.set(rot, smoothTime * -240.0F, 0.0F, 0.0F);
         }
      }

      private float smoothstep(float t) {
         return t * t * (3.0F - 2.0F * t);
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_START.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_STOP.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl SILO_HATCH_LARGE = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         builder.addSymbol('#', Shapes.block(), PartRole.DEFAULT);
         builder.addSymbol('C', Shapes.block(), PartRole.CONTROLLER);
         String[] closed = new String[]{"#######", "#######", "#######", "###C###", "#######", "#######", "#######"};
         String[] open = new String[]{"#######", "##   ##", "#     #", "#     #", "#     #", "##   ##", "#######"};
         this.defineStructure(builder.parseHorizontal(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "silo_hatch_large");
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"frame", "door"};
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public boolean remoteControllable() {
         return true;
      }

      @Override
      public int getBlockOffset() {
         return 3;
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("Hatch".equals(partName)) {
            float smoothTime = this.smoothstep(this.getNormTime(openTicks, 0.0F, 10.0F));
            this.set(trans, 0.0F, 0.25F * smoothTime, 0.0F);
         }
      }

      @Override
      public boolean isDynamicShape() {
         return true;
      }

      @Override
      public void getOrigin(String partName, float[] orig) {
         if ("Hatch".equals(partName)) {
            this.set(orig, 0.0F, 0.875F, -2.875F);
         }
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot) {
         if ("Hatch".equals(partName)) {
            float smoothTime = this.smoothstep(this.getNormTime(openTicks, 20.0F, 100.0F));
            this.set(rot, smoothTime * -240.0F, 0.0F, 0.0F);
         }
      }

      private float smoothstep(float t) {
         return t * t * (3.0F - 2.0F * t);
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_STOP.get();
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_START.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.DOOR_WGH_BIG_STOP.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl CARGO_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape thin = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
         builder.addSymbol('#', thin, PartRole.DEFAULT);
         builder.addSymbol('C', thin, PartRole.CONTROLLER);
         String[] closed = new String[]{"###", "###", "#C#"};
         String[] open = new String[]{"###", "   ", "   "};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "cargo_door");
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"Frame", "DoorTop", "DoorBot"};
      }

      @Override
      public int getOpenTime() {
         return 60;
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.DEFAULT);
      }

      @Override
      public AABB getBlockBound(int x, int y, int z, boolean open, boolean forCollision) {
         if (!open) {
            return new AABB(0.0, 0.0, 0.375, 1.0, 1.0, 0.625);
         } else if (y > 1) {
            return new AABB(0.0, 0.25, 0.375, 1.0, 1.0, 0.625);
         } else {
            return y == 0 ? new AABB(0.0, 0.0, 0.375, 1.0, forCollision ? 0.0 : 0.125, 0.625) : super.getBlockBound(x, y, z, open, forCollision);
         }
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         float norm = this.getNormTime(openTicks, 0.0F, this.getOpenTime());
         if ("DoorBot".equals(partName)) {
            float t = this.smoothstep(norm);
            this.set(trans, 0.0F, 2.0F * t, 0.0F);
         } else if ("DoorTop".equals(partName)) {
            float t = this.smoothstep(this.getNormTime(openTicks, this.getOpenTime() * 0.5F, this.getOpenTime()));
            this.set(trans, 0.0F, 1.0F * t, 0.0F);
         } else {
            super.getTranslation(partName, openTicks, child, trans);
         }
      }

      private float smoothstep(float t) {
         return t * t * (3.0F - 2.0F * t);
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{-1, -1, 0, 3, 3, 1}};
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public SoundEvent getCloseSoundLoop() {
         return (SoundEvent)ModSounds.GARAGE_MOVE.get();
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return (SoundEvent)ModSounds.GARAGE_STOP.get();
      }

      @Override
      public float getSoundVolume() {
         return 2.0F;
      }
   };
   public static final DoorDecl VAULT_DOOR = new DoorDecl() {
      {
         DoorDecl.DoorStructureDefinition.Builder builder = DoorDecl.DoorStructureDefinition.create();
         VoxelShape DoorShape = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
         builder.addSymbol('#', DoorShape, PartRole.DEFAULT);
         builder.addSymbol('C', DoorShape, PartRole.CONTROLLER);
         String[] closed = new String[]{"#####", "#####", "#####", "#####", "##C##"};
         String[] open = new String[]{"#####", "#...#", "#...#", "#...#", "#####"};
         this.defineStructure(builder.parseVertical(closed, open, 'C'));
      }

      @Override
      public ResourceLocation getBlockId() {
         return new ResourceLocation("heralisz_bunker", "vault_door");
      }

      @Override
      public DoorModelSelection getDefaultModelSelection() {
         return DoorModelSelection.modern(DoorSkin.of("vault_door_skin_101"));
      }

      @Override
      public String[] getPartNames() {
         return new String[]{"Label", "Frame", "Door"};
      }

      @Override
      public int getOpenTime() {
         return 120;
      }

      @Override
      public boolean hasSkins() {
         return true;
      }

      @Override
      public int getSkinCount() {
         return 3;
      }

      @Override
      public AABB getBlockBound(int x, int y, int z, boolean open, boolean forCollision) {
         return open && y != 0 ? super.getBlockBound(x, y, z, open, forCollision) : new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      }

      @Override
      public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
         if ("Frame".equals(partName)) {
            super.getTranslation(partName, openTicks, child, trans);
         } else {
            float pull = this.getVaultPull(openTicks);
            float slideDist = this.getVaultSlide(openTicks) * 5.0F;
            if (!"Door".equals(partName) && !"Label".equals(partName)) {
               this.set(trans, -pull, 0.0F, slideDist);
            } else {
               double diameter = 4.25;
               double circumference = diameter * Math.PI;
               float rollDeg = (float)(360.0 * slideDist / circumference);
               double rollRad = Math.toRadians(rollDeg);
               float yComp = (float)(slideDist * Math.sin(rollRad));
               float zComp = (float)(slideDist * Math.cos(rollRad));
               this.set(trans, -pull, yComp, zComp);
            }
         }
      }

      @Override
      public void getOrigin(String partName, float[] orig) {
         if (!"Door".equals(partName) && !"Label".equals(partName)) {
            super.getOrigin(partName, orig);
         } else {
            this.set(orig, 0.0F, 2.5F, 0.0F);
         }
      }

      @Override
      public void getOrigin(String partName, float[] orig, @Nullable DoorModelSelection selection) {
         this.getOrigin(partName, orig);
      }

      @Override
      public void getRotation(String partName, float openTicks, float[] rot) {
         if (!"Door".equals(partName) && !"Label".equals(partName)) {
            super.getRotation(partName, openTicks, rot);
         } else {
            float slide = this.getVaultSlide(openTicks);
            double diameter = 4.25;
            double circumference = diameter * Math.PI;
            float slideScaled = slide * 5.0F;
            float rollDeg = (float)(360.0 * slideScaled / circumference);
            this.set(rot, rollDeg, 0.0F, 0.0F);
         }
      }

      private float getVaultPull(float openTicks) {
         if (openTicks <= 40.0F) {
            float t = this.getNormTime(openTicks, 0.0F, 40.0F);
            return (float)((1.0 - Math.cos(t * Math.PI)) / 2.0);
         } else {
            return 1.0F;
         }
      }

      private float getVaultSlide(float openTicks) {
         return openTicks <= 40.0F ? 0.0F : this.getNormTime(openTicks, 40.0F, 120.0F);
      }

      @Override
      public double[][] getClippingPlanes() {
         return new double[][]{{0.0, -1.0, 0.0, 3.0001}};
      }

      @Override
      public int[][] getDoorOpenRanges() {
         return new int[][]{{-1, 1, 0, 3, 3, 2}};
      }

      @Override
      public SoundEvent getOpenSoundStart() {
         return null;
      }

      @Override
      public SoundEvent getOpenSoundEnd() {
         return null;
      }

      @Override
      public SoundEvent getOpenSoundLoop() {
         return null;
      }

      @Override
      public SoundEvent getCloseSoundStart() {
         return null;
      }

      @Override
      public SoundEvent getCloseSoundEnd() {
         return null;
      }

      @Override
      public SoundEvent getCloseSoundLoop() {
         return null;
      }

      @Override
      public float getSoundVolume() {
         return 1.0F;
      }

      @Override
      public void onTick(DoorBlockEntity door) {
         Level level = door.getLevel();
         if (level != null && !level.isClientSide()) {
            BlockPos pos = door.getBlockPos();
            int openTicks = door.getOpenTicks();
            if (door.state == 3) {
               if (openTicks == 1) {
                  level.playSound(null, pos, (SoundEvent)ModSounds.VAULT_SCRAPE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
               }

               if (openTicks >= 45 && openTicks <= 115 && (openTicks - 45) % 10 == 0) {
                  level.playSound(null, pos, (SoundEvent)ModSounds.VAULT_THUD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
               }
            } else if (door.state == 2) {
               if (openTicks == 30) {
                  level.playSound(null, pos, (SoundEvent)ModSounds.VAULT_SCRAPE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
               }

               if (openTicks >= 45 && openTicks <= 115 && (openTicks - 45) % 10 == 0) {
                  level.playSound(null, pos, (SoundEvent)ModSounds.VAULT_THUD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
               }
            }
         }
      }
   };

   public abstract int getOpenTime();

   public int[][] getDoorOpenRanges() {
      return new int[0][];
   }

   protected void defineStructure(DoorDecl.DoorStructureDefinition definition) {
      this.structureDefinition = definition;
   }

   public DoorDecl.DoorStructureDefinition getStructureDefinition() {
      return this.structureDefinition;
   }

   public void onTick(DoorBlockEntity door) {
   }

   @Nullable
   public VoxelShape getDynamicOutline(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, float progress) {
      return progress > 0.9F ? Shapes.empty() : null;
   }

   public int[] getDimensions() {
      if (this.structureDefinition != null) {
         return this.structureDefinition.getDimensions();
      }

      return switch (this.getBlockId().getPath()) {
         case "large_vehicle_door" -> new int[]{-3, 0, 0, 6, 5, 0};
         case "round_airlock_door" -> new int[]{-1, 0, 0, 3, 3, 0};
         case "fire_door" -> new int[]{-1, 0, 0, 3, 2, 0};
         case "sliding_blast_door" -> new int[]{-3, 0, 0, 6, 3, 0};
         case "sliding_seal_door" -> new int[]{0, 0, 0, 0, 1, 0};
         case "secure_access_door" -> new int[]{-2, 0, 0, 4, 4, 0};
         case "qe_sliding_door" -> new int[]{0, 0, 0, 1, 1, 0};
         case "qe_containment_door" -> new int[]{-1, 0, 0, 2, 2, 0};
         case "water_door" -> new int[]{-1, 0, 0, 2, 2, 0};
         case "silo_hatch" -> new int[]{-2, 0, -2, 4, 0, 4};
         case "silo_hatch_large" -> new int[]{-3, 0, -3, 6, 0, 6};
         case "cargo_door" -> new int[]{-1, -1, 0, 3, 3, 1};
         default -> new int[]{0, 0, 0, 0, 1, 0};
      };
   }

   public boolean isDynamicShape() {
      return false;
   }

   public abstract ResourceLocation getBlockId();

   public abstract String[] getPartNames();

   public DoorModelSelection getDefaultModelSelection() {
      return DoorModelSelection.DEFAULT;
   }

   @Nullable
   public ResourceLocation getColladaAnimationSource() {
      return null;
   }

   public boolean isColladaAnimationInverted() {
      return false;
   }

   public void setPartAABBs(Map<String, AABB> aabbs) {
      this.cachedPartAABBs = new HashMap<>(aabbs);
   }

   @Nullable
   public AABB getPartAABB(String partName) {
      return this.cachedPartAABBs.get(partName);
   }

   public Map<String, AABB> getAllPartAABBs() {
      return Collections.unmodifiableMap(this.cachedPartAABBs);
   }

   public boolean hasPartAABBs() {
      return !this.cachedPartAABBs.isEmpty();
   }

   public int getBlockOffset() {
      return 0;
   }

   public boolean remoteControllable() {
      return false;
   }

   public boolean hasSkins() {
      return false;
   }

   public int getSkinCount() {
      return 0;
   }

   public boolean isLadder(boolean open) {
      return false;
   }

   public AABB getBlockBound(int x, int y, int z, boolean open, boolean forCollision) {
      return open ? new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0) : new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
   }

   @Nullable
   public SoundEvent getOpenSoundStart() {
      return null;
   }

   @Nullable
   public SoundEvent getOpenSoundEnd() {
      return null;
   }

   @Nullable
   public SoundEvent getOpenSoundLoop() {
      return null;
   }

   @Nullable
   public SoundEvent getCloseSoundStart() {
      return this.getOpenSoundStart();
   }

   @Nullable
   public SoundEvent getCloseSoundEnd() {
      return this.getOpenSoundEnd();
   }

   @Nullable
   public SoundEvent getCloseSoundLoop() {
      return this.getOpenSoundLoop();
   }

   @Nullable
   public SoundEvent getSoundLoop2() {
      return null;
   }

   public float getSoundVolume() {
      return 1.0F;
   }

   public double getRenderRadius() {
      return 8.0;
   }

   public Component getLockedMessage() {
      return Component.translatable("door.locked");
   }

   public void doOffsetTransform(IDoorAnimator animator) {
   }

   public int getBakedModelRotationOffsetY() {
      return 0;
   }

   public double[][] getClippingPlanes() {
      return new double[0][];
   }

   public void getTranslation(String partName, float openTicks, boolean child, float[] trans) {
      trans[0] = 0.0F;
      trans[1] = 0.0F;
      trans[2] = 0.0F;
   }

   public void getTranslation(String partName, float openTicks, boolean child, float[] trans, @Nullable DoorModelSelection selection) {
      this.getTranslation(partName, openTicks, child, trans);
   }

   public void getOrigin(String partName, float[] orig) {
      orig[0] = 0.0F;
      orig[1] = 0.0F;
      orig[2] = 0.0F;
   }

   public void getOrigin(String partName, float[] orig, @Nullable DoorModelSelection selection) {
      this.getOrigin(partName, orig);
   }

   public void getRotation(String partName, float openTicks, float[] rot) {
      rot[0] = 0.0F;
      rot[1] = 0.0F;
      rot[2] = 0.0F;
   }

   public void getRotation(String partName, float openTicks, float[] rot, @Nullable DoorModelSelection selection) {
      this.getRotation(partName, openTicks, rot);
   }

   public boolean doesRender(String partName, boolean child) {
      return true;
   }

   public String[] getChildren(String partName) {
      return new String[0];
   }

   public String[] getChildren(String partName, @Nullable DoorModelSelection selection) {
      return this.getChildren(partName);
   }

   protected float getNormTime(float time) {
      return this.getNormTime(time, 0.0F, this.getOpenTime());
   }

   protected float getNormTime(float time, float min, float max) {
      return max - min == 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, (time - min) / (max - min)));
   }

   protected void set(float[] array, float x, float y, float z) {
      array[0] = x;
      array[1] = y;
      array[2] = z;
   }

   protected double[] getDoorCenter() {
      int[] dims = this.getDimensions();
      int offsetX = dims[0];
      int offsetZ = dims[2];
      int sizeX = dims[3];
      int sizeZ = dims[5];
      double centerX = (offsetX + offsetX + sizeX) / 2.0;
      double centerZ = (offsetZ + offsetZ + sizeZ) / 2.0;
      return new double[]{centerX, centerZ};
   }

   protected static AABB rotateAABB(AABB bb, Direction facing) {
      double[][] pts = new double[][]{{bb.minX, bb.minZ}, {bb.maxX, bb.minZ}, {bb.minX, bb.maxZ}, {bb.maxX, bb.maxZ}};
      double minX = Double.POSITIVE_INFINITY;
      double minZ = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      double maxZ = Double.NEGATIVE_INFINITY;

      for (double[] p : pts) {
         double x = p[0];
         double z = p[1];
         double rx;
         double rz;
         switch (facing) {
            case SOUTH:
               rx = -x;
               rz = -z;
               break;
            case WEST:
               rx = z;
               rz = -x;
               break;
            case EAST:
               rx = -z;
               rz = x;
               break;
            default:
               rx = x;
               rz = z;
         }

         if (rx < minX) {
            minX = rx;
         }

         if (rx > maxX) {
            maxX = rx;
         }

         if (rz < minZ) {
            minZ = rz;
         }

         if (rz > maxZ) {
            maxZ = rz;
         }
      }

      return new AABB(minX, bb.minY, minZ, maxX, bb.maxY, maxZ);
   }

   public static class DoorStructureDefinition {
      private final Map<BlockPos, VoxelShape> closedShapes = new HashMap<>();
      private final Map<BlockPos, VoxelShape> openShapes = new HashMap<>();
      private final Map<BlockPos, PartRole> roles = new HashMap<>();
      private int minX;
      private int minY;
      private int minZ;
      private int maxX;
      private int maxY;
      private int maxZ;
      private BlockPos controllerOffset = BlockPos.ZERO;
      // HeralisZ : forme entière de la porte (toutes ses cases, tournée), gardée par sens et par état ouvert / fermé :
      // elle ne dépend que de ces deux choses et était refaite à chaque appel (contour, visée, chaque bloc de la porte)
      private final AtomicReferenceArray<VoxelShape> fullShapes = new AtomicReferenceArray<>(8);

      private DoorStructureDefinition() {
      }

      /** HeralisZ : forme entière de la porte pour ce sens, ouverte (vrai) ou fermée (faux). */
      public VoxelShape getFullShape(Direction facing, boolean open) {
         int horizontal = facing.get2DDataValue();
         if (horizontal < 0) {
            return this.buildFullShape(facing, open);
         }

         int index = horizontal * 2 + (open ? 1 : 0);
         VoxelShape shape = this.fullShapes.get(index);
         if (shape == null) {
            shape = this.buildFullShape(facing, open);
            if (!this.fullShapes.compareAndSet(index, null, shape)) {
               shape = this.fullShapes.get(index);
            }
         }

         return shape;
      }

      private VoxelShape buildFullShape(Direction facing, boolean open) {
         VoxelShape combined = Shapes.empty();
         Map<BlockPos, VoxelShape> currentMap = open ? this.openShapes : this.closedShapes;

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
      }

      public static DoorDecl.DoorStructureDefinition.Builder create() {
         return new DoorDecl.DoorStructureDefinition.Builder();
      }

      public Map<BlockPos, VoxelShape> getClosedShapes() {
         return this.closedShapes;
      }

      public Map<BlockPos, VoxelShape> getOpenShapes() {
         return this.openShapes;
      }

      public Map<BlockPos, PartRole> getRoles() {
         return this.roles;
      }

      public BlockPos getControllerOffset() {
         return this.controllerOffset;
      }

      public int[] getDimensions() {
         return new int[]{this.minX, this.minY, this.minZ, this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ};
      }

      public static class Builder {
         private final Map<Character, VoxelShape> symbolShapes = new HashMap<>();
         private final Map<Character, PartRole> symbolRoles = new HashMap<>();

         public Builder() {
            this.addSymbol(' ', Shapes.empty(), PartRole.DEFAULT);
            this.addSymbol('#', Shapes.block(), PartRole.DEFAULT);
         }

         public DoorDecl.DoorStructureDefinition.Builder addSymbol(char symbol, VoxelShape shape, PartRole role) {
            this.symbolShapes.put(symbol, shape);
            this.symbolRoles.put(symbol, role);
            return this;
         }

         public DoorDecl.DoorStructureDefinition.Builder addSymbol(char symbol, VoxelShape shape) {
            return this.addSymbol(symbol, shape, PartRole.DEFAULT);
         }

         public DoorDecl.DoorStructureDefinition parseVertical(String[] closedPattern, String[] openPattern, char controllerChar) {
            return this.parse(closedPattern, openPattern, controllerChar, true);
         }

         public DoorDecl.DoorStructureDefinition parseHorizontal(String[] closedPattern, String[] openPattern, char controllerChar) {
            return this.parse(closedPattern, openPattern, controllerChar, false);
         }

         private DoorDecl.DoorStructureDefinition parse(String[] closed, String[] open, char ctrlChar, boolean isVertical) {
            DoorDecl.DoorStructureDefinition def = new DoorDecl.DoorStructureDefinition();
            int ctrlRow = -1;
            int ctrlCol = -1;

            for (int r = 0; r < closed.length; r++) {
               int idx = closed[r].indexOf(ctrlChar);
               if (idx != -1) {
                  ctrlRow = r;
                  ctrlCol = idx;
                  break;
               }
            }

            int cR = ctrlRow == -1 ? 0 : ctrlRow;
            int cC = ctrlCol == -1 ? 0 : ctrlCol;
            def.minX = Integer.MAX_VALUE;
            def.minY = Integer.MAX_VALUE;
            def.minZ = Integer.MAX_VALUE;
            def.maxX = Integer.MIN_VALUE;
            def.maxY = Integer.MIN_VALUE;
            def.maxZ = Integer.MIN_VALUE;
            int height = closed.length;

            for (int row = 0; row < height; row++) {
               String line = closed[row];
               String openLine = open != null && row < open.length ? open[row] : null;

               for (int col = 0; col < line.length(); col++) {
                  char cSym = line.charAt(col);
                  if (cSym != ' ') {
                     int x = col - cC;
                     int y;
                     int z;
                     if (isVertical) {
                        y = height - 1 - row - (height - 1 - cR);
                        z = 0;
                     } else {
                        y = 0;
                        z = row - cR;
                     }

                     BlockPos pos = new BlockPos(x, y, z);
                     def.minX = Math.min(def.minX, x);
                     def.maxX = Math.max(def.maxX, x);
                     def.minY = Math.min(def.minY, y);
                     def.maxY = Math.max(def.maxY, y);
                     def.minZ = Math.min(def.minZ, z);
                     def.maxZ = Math.max(def.maxZ, z);
                     VoxelShape shape = this.symbolShapes.getOrDefault(cSym, Shapes.block());
                     def.closedShapes.put(pos, shape);
                     PartRole role = this.symbolRoles.getOrDefault(cSym, PartRole.DEFAULT);
                     if (cSym == ctrlChar) {
                        role = PartRole.CONTROLLER;
                     }

                     def.roles.put(pos, role);
                     if (openLine != null && col < openLine.length()) {
                        char oSym = openLine.charAt(col);
                        def.openShapes.put(pos, this.symbolShapes.getOrDefault(oSym, Shapes.empty()));
                     } else {
                        def.openShapes.put(pos, Shapes.empty());
                     }
                  }
               }
            }

            def.controllerOffset = new BlockPos(-def.minX, -def.minY, -def.minZ);
            return def;
         }
      }
   }
}
