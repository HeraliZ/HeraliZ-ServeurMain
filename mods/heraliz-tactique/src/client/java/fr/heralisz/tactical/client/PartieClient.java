package fr.heralisz.tactical.client;

import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import java.util.function.Consumer;
import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.capability.CombatProperties;
import fr.heralisz.tactical.client.network.ReceptionClient;
import fr.heralisz.tactical.client.renderer.item.ConsumableItemRenderer;
import fr.heralisz.tactical.client.renderer.item.FlashShieldItemRenderer;
import fr.heralisz.tactical.client.renderer.item.MeleeItemRenderer;
import fr.heralisz.tactical.client.renderer.item.ThrowableItemRendererWrapper;
import fr.heralisz.tactical.item.ConsumableItem;
import fr.heralisz.tactical.item.FlashShieldItem;
import fr.heralisz.tactical.item.MeleeItem;
import fr.heralisz.tactical.item.ThrowableItem;
import fr.heraliz.tactique.pont.Partie;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client de heralisz_tactical (docs/METHODE_CLIENT_SERVEUR.md §4, §6) : branche les crochets du code commun sur le
 * code d'affichage d'origine (rendus en main des objets, sons d'attaque joués localement, paquets serveur -> client). Les
 * écouteurs client (rendus, HUD, entrées, animations) s'inscrivent seuls par leur @Mod.EventBusSubscriber(value = CLIENT).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      ReceptionClient.brancher();
      MeleeItem.RENDU_CLIENT.brancher(PartieClient::renduMelee);
      ConsumableItem.RENDU_CLIENT.brancher(PartieClient::renduConsommable);
      ThrowableItem.RENDU_CLIENT.brancher(PartieClient::renduLancer);
      FlashShieldItem.RENDU_CLIENT.brancher(PartieClient::renduBouclier);
      CombatProperties.SON_ATTAQUE_CLIENT.brancher(PartieClient::sonAttaque);
   }

   // MeleeItem.initializeClient (d'origine)
   static void renduMelee(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private MeleeItemRenderer renderer = null;

         public MeleeItemRenderer getCustomRenderer() {
            if (this.renderer == null) {
               this.renderer = new MeleeItemRenderer();
            }

            return this.renderer;
         }
      });
   }

   // ConsumableItem.initializeClient (d'origine)
   static void renduConsommable(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private ConsumableItemRenderer renderer = null;

         public ConsumableItemRenderer getCustomRenderer() {
            if (this.renderer == null) {
               this.renderer = new ConsumableItemRenderer();
            }

            return this.renderer;
         }
      });
   }

   // ThrowableItem.initializeClient (d'origine)
   static void renduLancer(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private ThrowableItemRendererWrapper renderer = null;

         public ThrowableItemRendererWrapper getCustomRenderer() {
            if (this.renderer == null) {
               this.renderer = new ThrowableItemRendererWrapper();
            }

            return this.renderer;
         }
      });
   }

   // FlashShieldItem.initializeClient (d'origine)
   static void renduBouclier(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         private final FlashShieldItemRenderer renderer = new FlashShieldItemRenderer();

         public FlashShieldItemRenderer getCustomRenderer() {
            return this.renderer;
         }

         public ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand, ItemStack itemStack) {
            return hand == InteractionHand.OFF_HAND ? ArmPose.EMPTY : ArmPose.CROSSBOW_HOLD;
         }
      });
   }

   // CombatProperties.preAttack, branche client : son de l'attaque (d'origine)
   static void sonAttaque(CombatProperties.SonAttaque s) {
      HeraliszTacticalClientAPI.getMeleeDisplay(s.stack())
         .ifPresent(
            display -> {
               if (display.getSounds().containsKey(s.action().getId())) {
                  s.joueur()
                     .level()
                     .playLocalSound(
                        s.origine().x,
                        s.origine().y,
                        s.origine().z,
                        SoundEvent.createVariableRangeEvent(display.getSounds().get(s.action().getId())),
                        SoundSource.PLAYERS,
                        1.0F,
                        1.0F,
                        false
                     );
               }
            }
         );
   }

   /** Ancien ThrowableItem.triggerAnimation (@OnlyIn(CLIENT), jamais appelé dans le mod) : gardé ici, côté client. */
   public static void triggerAnimation(ItemStack stack, String animationName) {
      if (IClientItemExtensions.of(stack.getItem()).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
         renderer.triggerAnimation(stack, animationName);
      }
   }
}
