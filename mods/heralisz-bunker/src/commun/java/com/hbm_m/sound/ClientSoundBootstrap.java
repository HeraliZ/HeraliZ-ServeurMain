package com.hbm_m.sound;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientSoundBootstrap {
   private static final String CLIENT_SOUND_MANAGER = "com.hbm_m.sound.ClientSoundManager";

   private ClientSoundBootstrap() {
   }

   private static Class<?> managerClass() throws ClassNotFoundException {
      return Class.forName("com.hbm_m.sound.ClientSoundManager");
   }

   private static boolean isClientSide(Level level) {
      return level != null && level.isClientSide();
   }

   public static void playOneShotSound(Level level, BlockPos pos, SoundEvent sound, float volume) {
      if (isClientSide(level) && sound != null) {
         try {
            Method m = managerClass().getMethod("playOneShotSound", BlockPos.class, SoundEvent.class, float.class);
            m.invoke(null, pos, sound, volume);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static void stopSound(Level level, BlockPos pos) {
      if (isClientSide(level)) {
         try {
            Method m = managerClass().getMethod("stopSound", BlockPos.class);
            m.invoke(null, pos);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static void stopSpecificSound(Level level, BlockPos pos, String soundType) {
      if (isClientSide(level)) {
         try {
            Method m = managerClass().getMethod("stopSpecificSound", BlockPos.class, String.class);
            m.invoke(null, pos, soundType);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static void updateDoorSoundRaw(Level level, BlockPos pos, String soundType, boolean isMoving, Supplier<?> loopSoundSupplier) {
      if (isClientSide(level)) {
         try {
            Method m = managerClass().getMethod("updateDoorSoundRaw", BlockPos.class, String.class, boolean.class, Supplier.class);
            m.invoke(null, pos, soundType, isMoving, loopSoundSupplier);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public static void updateSound(BlockEntity be, boolean shouldBePlaying, Supplier<?> soundSupplier) {
      if (be != null && be.getLevel() != null && be.getLevel().isClientSide()) {
         try {
            Method m = managerClass().getMethod("updateSound", BlockEntity.class, boolean.class, Supplier.class);
            m.invoke(null, be, shouldBePlaying, soundSupplier);
         } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
         }
      }
   }
}
