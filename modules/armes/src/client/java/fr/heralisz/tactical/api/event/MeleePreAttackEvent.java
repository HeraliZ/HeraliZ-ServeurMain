package fr.heralisz.tactical.api.event;

import javax.annotation.Nullable;
import fr.heralisz.tactical.api.melee.MeleeAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.eventbus.api.Event;

@OnlyIn(Dist.CLIENT)
public class MeleePreAttackEvent extends Event {
   private final int playerId;
   private final MeleeAction state;
   private final int actionCount;
   private final ResourceLocation animationId;
   private AbstractClientPlayer player;

   public MeleePreAttackEvent(int playerId, MeleeAction state, int actionCount, @Nullable ResourceLocation animationId) {
      this.playerId = playerId;
      this.state = state;
      this.actionCount = actionCount;
      this.animationId = animationId;
      if (playerId > 0 && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getEntity(playerId) instanceof AbstractClientPlayer p) {
         this.player = p;
      }
   }

   @Nullable
   public AbstractClientPlayer getPlayer() {
      return this.player;
   }

   public int getPlayerId() {
      return this.playerId;
   }

   public MeleeAction getState() {
      return this.state;
   }

   public int getActionCount() {
      return this.actionCount;
   }

   @Nullable
   public ResourceLocation getAnimationId() {
      return this.animationId;
   }
}
