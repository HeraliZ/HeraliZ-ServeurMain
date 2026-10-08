package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.reanimation.ClientConfig;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.ExecutionsCommun;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

final class Weapons {
   private static final Map<ExecutionType, ItemStack> FALLBACK = new EnumMap<>(ExecutionType.class);
   private static final Map<ExecutionType, String> FALLBACK_ID = new EnumMap<>(ExecutionType.class);

   private Weapons() {
   }

   static ItemStack of(ExecutionScenes.Scene s) {
      if (s.actor != null) {
         ItemStack held = ExecutionScenes.heldItem(s.actor);
         ExecutionType forHeld = ExecutionsCommun.forWeapon(held);
         if (forHeld == s.type || s.type.variant() && forHeld != null && forHeld.knife()) {
            return held;
         }
      }

      return fallback(s.type);
   }

   static void render(
      ExecutionScenes.Scene s, Knife k, ItemStack stack, @Nullable LivingEntity holder, PoseStack ps, MultiBufferSource buffers, int light, float time
   ) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      Runnable restore = k.blade == Blade.BUTTERFLY ? Weapons.Balisong.pose(stack, k.open) : null;

      try {
         mc.getItemRenderer()
            .renderStatic(holder, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, level, light, OverlayTexture.NO_OVERLAY, s.actorId);
      } finally {
         if (restore != null) {
            restore.run();
         }
      }

      KnifeTrail.sample(s, time, ps.last().pose(), k);
   }

   private static ItemStack fallback(ExecutionType type) {
      String id = defaultId(type);
      ItemStack cached = FALLBACK.get(type);
      if (cached == null || !id.equals(FALLBACK_ID.get(type)) || cached.is(Items.IRON_SWORD) && ModList.get().isLoaded("tacz")) {
         cached = make(id);
         FALLBACK.put(type, cached);
         FALLBACK_ID.put(type, id);
      }

      return cached;
   }

   private static String defaultId(ExecutionType type) {
      for (String entry : (List<String>)ClientConfig.DEFAULT_WEAPONS.get()) {
         int eq = entry.indexOf(61);
         if (eq > 0 && type.matches(entry.substring(0, eq).trim())) {
            return entry.substring(eq + 1).trim();
         }
      }

      return type.defaultWeapon != null ? type.defaultWeapon : "minecraft:iron_sword";
   }

   private static ItemStack make(String spec) {
      if (spec.startsWith("melee:")) {
         Item melee = (Item)ForgeRegistries.ITEMS.getValue(new ResourceLocation("heralisz_tactical", "melee"));
         ResourceLocation id = ResourceLocation.tryParse(spec.substring(6));
         if (melee != null && melee != Items.AIR && id != null) {
            ItemStack stack = new ItemStack(melee);
            stack.getOrCreateTag().putString("MeleeWeaponId", id.toString());
            return stack;
         } else {
            return new ItemStack(Items.IRON_SWORD);
         }
      } else {
         ResourceLocation rl = ResourceLocation.tryParse(spec);
         if (rl == null) {
            return new ItemStack(Items.IRON_SWORD);
         } else {
            Item plain = (Item)ForgeRegistries.ITEMS.getValue(rl);
            if (plain != null && plain != Items.AIR) {
               return new ItemStack(plain);
            } else {
               Item gun = (Item)ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", "modern_kinetic_gun"));
               if (gun != null && gun != Items.AIR && Weapons.Balisong.taczKnows(rl)) {
                  ItemStack stack = new ItemStack(gun);
                  stack.getOrCreateTag().putString("GunId", rl.toString());
                  return stack;
               } else {
                  return new ItemStack(Items.IRON_SWORD);
               }
            }
         }
      }
   }

   static final class Balisong {
      private static boolean ready;
      private static Method getGunDisplay;
      private static Method getGunModel;
      private static Method getNode;
      private static Field xRot;

      private Balisong() {
      }

      private static boolean init() {
         if (!ready) {
            ready = true;

            try {
               Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
               getGunDisplay = api.getMethod("getGunDisplay", ItemStack.class);
               getGunModel = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance").getMethod("getGunModel");
               getNode = Class.forName("com.tacz.guns.client.model.bedrock.BedrockModel").getMethod("getNode", String.class);
               xRot = Class.forName("com.tacz.guns.client.model.bedrock.BedrockPart").getField("xRot");
            } catch (ReflectiveOperationException | LinkageError e) {
               getGunDisplay = null;
            }
         }

         return getGunDisplay != null;
      }

      static boolean taczKnows(ResourceLocation gunId) {
         try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            return api.getMethod("getClientGunIndex", ResourceLocation.class).invoke(null, gunId) instanceof Optional<?> o && o.isPresent();
         } catch (ReflectiveOperationException | LinkageError e) {
            return false;
         }
      }

      @Nullable
      static Runnable pose(ItemStack stack, float open) {
         if (!(open >= 0.999F) && init()) {
            try {
               Object display = ((Optional)getGunDisplay.invoke(null, stack)).orElse(null);
               Object model = display == null ? null : getGunModel.invoke(display);
               if (model == null) {
                  return null;
               } else {
                  Object blade = getNode.invoke(model, "danger_grip_and_blade");
                  Object grip = getNode.invoke(model, "danger_grip");
                  if (blade != null && grip != null) {
                     float bladeWas = xRot.getFloat(blade);
                     float gripWas = xRot.getFloat(grip);
                     float closed = 1.0F - open;
                     float gripClosed = Math.min(1.0F, closed * 1.25F);
                     xRot.setFloat(blade, bladeWas + (float)Math.toRadians(-180.0) * closed);
                     xRot.setFloat(grip, gripWas + (float)Math.toRadians(-170.0) * gripClosed);
                     return () -> {
                        try {
                           xRot.setFloat(blade, bladeWas);
                           xRot.setFloat(grip, gripWas);
                        } catch (IllegalAccessException var5x) {
                        }
                     };
                  } else {
                     return null;
                  }
               }
            } catch (ReflectiveOperationException | RuntimeException e) {
               getGunDisplay = null;
               return null;
            }
         } else {
            return null;
         }
      }
   }
}
