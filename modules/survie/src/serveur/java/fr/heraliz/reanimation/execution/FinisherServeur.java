package fr.heraliz.reanimation.execution;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * Finisher à débloquer, partie serveur (26/10) : clic droit avec l'objet ({@link ObjetFinisher}) = l'exécution entre dans
 * la collection du joueur ({@link ExecutionUnlocks#unlock}, gardée dans ses données persistantes), l'objet est consommé, le
 * menu des exécutions est mis à jour chez lui. Déjà possédée (ou accessible à tous) : rien n'est consommé.
 */
@EventBusSubscriber(modid = "reanimation", bus = Bus.FORGE)
public final class FinisherServeur {
   private FinisherServeur() {
   }

   @SubscribeEvent
   public static void onClic(PlayerInteractEvent.RightClickItem e) {
      ItemStack st = e.getItemStack();
      if (!(st.getItem() instanceof ObjetFinisher) || !(e.getEntity() instanceof ServerPlayer p)) return;
      e.setCanceled(true);
      ExecutionType t = ObjetFinisher.type(st);
      if (t == null) return;
      Component nom = Component.translatable("execution.reanimation." + t.id);
      if (ExecutionUnlocks.owns(p, t) || !ExecutionUnlocks.unlock(p, t)) {
         p.displayClientMessage(Component.translatableWithFallback("message.reanimation.finisher.deja",
            "Tu as déjà ce finisher : %s", nom).withStyle(ChatFormatting.GOLD), true);
         return;
      }
      st.shrink(1);
      ExecutionUnlocks.sync(p, false);
      p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
      p.sendSystemMessage(Component.translatableWithFallback("message.reanimation.finisher.ajoute",
         "Finisher ajouté à ta collection : %s (touche = pour l'équiper)", nom).withStyle(ChatFormatting.GREEN));
   }
}
