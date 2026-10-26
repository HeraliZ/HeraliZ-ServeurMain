package fr.heraliz.reanimation.network;

import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.ExecutionUnlocks;
import fr.heraliz.reanimation.execution.Executions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Traitement serveur de {@link ActionPacket} (corps d'origine de ActionPacket.handle, sur le fil principal). */
public final class ActionPacketServeur {
   private ActionPacketServeur() {
   }

   public static void traiter(ActionPacket.Recu recu) {
      ServerPlayer player = recu.player();
      ActionPacket p = recu.paquet();
      if (player != null && p.action == 3) {
         if (p.target >= 0 && p.target < ExecutionType.values().length) {
            ExecutionType type = ExecutionType.byIndex(p.target);
            if (type.selectable() && ExecutionUnlocks.owns(player, type)) {
               ExecutionUnlocks.select(player, type);
               player.displayClientMessage(Component.translatable("message.reanimation.execution_equipped", new Object[]{type.displayName()}), true);
            }
         }

         ExecutionUnlocks.sync(player, false);
      } else if (player != null && p.action == 4) {
         ExecutionUnlocks.sync(player, false);
      } else if (player != null && player.isAlive() && !player.isSpectator()) {
         if (p.action == 0) {
            DownedManagerServeur.giveUp(player);
         } else if (p.action == 1) {
            DownedManagerServeur.cancelReviveBy(player);
            Executions.cancelBy(player);
         } else if (p.action == 2 && player.level().getEntity(p.target) instanceof LivingEntity body) {
            Executions.request(player, body);
         }
      }
   }
}
