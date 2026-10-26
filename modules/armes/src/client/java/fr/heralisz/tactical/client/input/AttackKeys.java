package fr.heralisz.tactical.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import fr.heralisz.tactical.api.melee.MeleeAction;
import fr.heralisz.tactical.capability.CombatPropertiesProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/**
 * Attaques des armes de mêlée : attaque normale sur la touche « Attaquer » de Minecraft (clic gauche), attaque / action
 * spéciale sur la touche « Utiliser » (clic droit).
 * <p>
 * HeraliZ : il y avait ici deux touches à part (« Attaque normale », « Attaque / action spéciale »), copies exactes
 * d'Attaquer et d'Utiliser, affichées en rouge, en double, dans les Commandes. Elles ne sont plus enregistrées : on lit
 * directement les touches de Minecraft, sans jamais consommer leurs clics (Minecraft en a besoin).
 * <ul>
 *    <li>touche maintenue (attaque normale) : comme avant, à chaque tick, en jeu (aucun écran ouvert, ce que faisait le
 *    contexte « en jeu » de l'ancienne touche) ;</li>
 *    <li>appui (une attaque par appui) : lu dans l'événement de la souris ou du clavier qui a appuyé sur la touche de
 *    Minecraft, aux mêmes conditions que Minecraft compte un clic (en jeu, sans écran ni chargement).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "heralisz_tactical", value = Dist.CLIENT)
public class AttackKeys {
   @SubscribeEvent
   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.START) {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (player != null && !player.isSpectator() && mc.gameMode != null) {
            ItemStack stack = player.getMainHandItem();
            if (mc.screen == null && mc.options.keyAttack.isDown()) {
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

   /** Appui d'un bouton de la souris (Attaquer et Utiliser sont sur la souris par défaut). */
   @SubscribeEvent
   public static void onMouse(InputEvent.MouseButton.Post event) {
      if (event.getAction() == GLFW.GLFW_PRESS) {
         appui(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
      }
   }

   /** Appui d'une touche du clavier (si le joueur a mis Attaquer ou Utiliser sur le clavier). */
   @SubscribeEvent
   public static void onKey(InputEvent.Key event) {
      if (event.getAction() == GLFW.GLFW_PRESS) {
         appui(InputConstants.getKey(event.getKey(), event.getScanCode()));
      }
   }

   private static void appui(InputConstants.Key touche) {
      Minecraft mc = Minecraft.getInstance();
      // Minecraft ne compte un clic qu'en jeu : aucun écran ouvert, aucun écran de chargement
      if (mc.screen != null || mc.getOverlay() != null) {
         return;
      }
      if (mc.options.keyAttack.isActiveAndMatches(touche)) {
         attaquer(mc, MeleeAction.LEFT, "attack_left");
      }
      if (mc.options.keyUse.isActiveAndMatches(touche)) {
         attaquer(mc, MeleeAction.RIGHT, "attack_right");
      }
   }

   private static void attaquer(Minecraft mc, MeleeAction action, String animation) {
      LocalPlayer player = mc.player;
      if (player != null && !player.isSpectator() && mc.gameMode != null) {
         ItemStack stack = player.getMainHandItem();
         player.getCapability(CombatPropertiesProvider.CAPABILITY).ifPresent(combatProperties -> {
            if (combatProperties.preAttack(action, player.getEyePosition(), player.getLookAngle())) {
               mc.gameMode.ensureHasSentCarriedItem();
               if (IClientItemExtensions.of(stack).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
                  renderer.triggerAnimation(stack, animation);
                  player.swing(InteractionHand.MAIN_HAND);
               }
            }
         });
      }
   }
}
