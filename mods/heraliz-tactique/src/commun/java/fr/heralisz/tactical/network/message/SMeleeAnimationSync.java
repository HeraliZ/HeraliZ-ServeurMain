package fr.heralisz.tactical.network.message;

import fr.heraliz.tactique.pont.Crochet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import fr.heralisz.tactical.api.melee.MeleeAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent.Context;

public class SMeleeAnimationSync {
   /** Fusion (heraliz_tactique) : traitement côté client (code d'origine), branché par fr.heralisz.tactical.client.PartieClient. */
   public static final Crochet<SMeleeAnimationSync> RECEPTION_CLIENT = new Crochet<>();

   private final int playerId;
   private final MeleeAction state;
   private final int actionCount;
   private final ResourceLocation animationId;

   public SMeleeAnimationSync(int playerId, MeleeAction state, int actionCount, @Nullable ResourceLocation animationId) {
      this.playerId = playerId;
      this.state = state;
      this.actionCount = actionCount;
      this.animationId = animationId;
   }

   public int playerId() {
      return this.playerId;
   }

   public MeleeAction state() {
      return this.state;
   }

   public int actionCount() {
      return this.actionCount;
   }

   @Nullable
   public ResourceLocation animationId() {
      return this.animationId;
   }

   public static void encode(SMeleeAnimationSync msg, FriendlyByteBuf buf) {
      buf.writeInt(msg.playerId);
      buf.writeEnum(msg.state);
      buf.writeVarInt(msg.actionCount);
      buf.writeBoolean(msg.animationId != null);
      if (msg.animationId != null) {
         buf.writeResourceLocation(msg.animationId);
      }
   }

   public static SMeleeAnimationSync decode(FriendlyByteBuf buf) {
      int playerId = buf.readInt();
      MeleeAction state = (MeleeAction)buf.readEnum(MeleeAction.class);
      int actionCount = buf.readVarInt();
      ResourceLocation animationId = buf.readBoolean() ? buf.readResourceLocation() : null;
      return new SMeleeAnimationSync(playerId, state, actionCount, animationId);
   }

   public static void handle(SMeleeAnimationSync msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      if (context.getDirection().getReceptionSide().isClient()) {
         context.enqueueWork(() -> RECEPTION_CLIENT.accept(msg));
      }

      context.setPacketHandled(true);
   }
}
