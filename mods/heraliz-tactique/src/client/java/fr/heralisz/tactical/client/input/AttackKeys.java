package fr.heralisz.tactical.client.input;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent.MouseButton.Post;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "heralisz_tactical", value = Dist.CLIENT)
public class AttackKeys {
   public static final KeyMapping NORMAL_ATTACK = new KeyMapping(
      "key.heralisz_tactical.normal_attack.desc", KeyConflictContext.IN_GAME, KeyModifier.NONE, Type.MOUSE, 0, "key.category.heralisz_tactical"
   );
   public static final KeyMapping SPECIAL_ATTACK = new KeyMapping(
      "key.heralisz_tactical.sp_attack.desc", KeyConflictContext.IN_GAME, KeyModifier.NONE, Type.MOUSE, 1, "key.category.heralisz_tactical"
   );

   @SubscribeEvent
   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.START) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && !player.isSpectator() && mc.gameMode != null) {
            ItemStack stack = player.getMainHandItem();
            if (NORMAL_ATTACK.isDown()) {
               player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(combatProperties -> {
                  if (combatProperties.getCoolDownTick() <= 0) {
                     if (combatProperties.preAttack(MeleeAction.LEFT, player.getEyePosition(), player.getLookAngle())) {
                        mc.gameMode.ensureHasSentCarriedItem();
                        if (IClientItemExtensions.of(stack).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
                           renderer.triggerAnimation(stack, "attack_left");
                           player.swing(InteractionHand.MAIN_HAND);
                        }
                     }
                  }
               });
            }
         }
      }
   }

   @SubscribeEvent
   public static void onNormalAttack(Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && !player.isSpectator() && mc.gameMode != null) {
         ItemStack stack = player.getMainHandItem();

         while (NORMAL_ATTACK.consumeClick()) {
            player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(combatProperties -> {
               if (combatProperties.preAttack(MeleeAction.LEFT, player.getEyePosition(), player.getLookAngle())) {
                  mc.gameMode.ensureHasSentCarriedItem();
                  if (IClientItemExtensions.of(stack).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
                     renderer.triggerAnimation(stack, "attack_left");
                     player.swing(InteractionHand.MAIN_HAND);
                  }
               }
            });
         }
      }
   }

   @SubscribeEvent
   public static void onSpAttack(Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && !player.isSpectator() && mc.gameMode != null) {
         ItemStack stack = player.getMainHandItem();

         while (SPECIAL_ATTACK.consumeClick()) {
            player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(combatProperties -> {
               if (combatProperties.preAttack(MeleeAction.RIGHT, player.getEyePosition(), player.getLookAngle())) {
                  mc.gameMode.ensureHasSentCarriedItem();
                  if (IClientItemExtensions.of(stack).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
                     renderer.triggerAnimation(stack, "attack_right");
                     player.swing(InteractionHand.MAIN_HAND);
                  }
               }
            });
         }
      }
   }
}
