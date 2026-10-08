package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.ReanimationMod;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

final class CameraAccess {
   private static final Method SET_POSITION = find();

   private CameraAccess() {
   }

   private static Method find() {
      for (Method m : Camera.class.getDeclaredMethods()) {
         if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == Vec3.class && m.getReturnType() == void.class) {
            try {
               m.setAccessible(true);
               return m;
            } catch (RuntimeException var5) {
               break;
            }
         }
      }

      ReanimationMod.LOGGER.warn("Camera position setter not found: no camera moves");
      return null;
   }

   static boolean available() {
      return SET_POSITION != null;
   }

   static void move(Camera camera, Vec3 position) {
      if (SET_POSITION != null) {
         try {
            SET_POSITION.invoke(camera, position);
         } catch (ReflectiveOperationException var3) {
         }
      }
   }
}
