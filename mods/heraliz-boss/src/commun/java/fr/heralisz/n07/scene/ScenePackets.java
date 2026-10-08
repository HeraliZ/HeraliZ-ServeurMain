package fr.heralisz.n07.scene;

import fr.heraliz.boss.pont.Crochet;
import fr.heralisz.n07.api.scene.Scene;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/** Paquets des scenes (sur le canal heralisz_n07:vfx) : debut / fin de scene, appui « passer », etat du vote. */
public final class ScenePackets {
   private static SimpleChannel channel;
   /** Réception côté client (ScenePlayer.start / stop / votes) : branchée par la partie client, neutre sinon. */
   public static final Crochet<Start> START_CLIENT = new Crochet<>();
   public static final Crochet<Stop> STOP_CLIENT = new Crochet<>();
   public static final Crochet<Votes> VOTES_CLIENT = new Crochet<>();
   /** Réception côté serveur de l'appui « passer » (SceneRunner.onHold) : branchée par la partie serveur, neutre sinon. */
   public static final Crochet<HoldRecu> HOLD_SERVEUR = new Crochet<>();

   /** Appui reçu par le serveur : le joueur qui l'envoie et le contenu du paquet Hold. */
   public record HoldRecu(ServerPlayer player, int run, boolean on) {}

   private ScenePackets() {}

   public static void register(SimpleChannel ch) {
      channel = ch;
      ch.registerMessage(1, Start.class, Start::encode, Start::decode, Start::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      ch.registerMessage(2, Stop.class, Stop::encode, Stop::decode, Stop::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      ch.registerMessage(3, Hold.class, Hold::encode, Hold::decode, Hold::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      ch.registerMessage(4, Votes.class, Votes::encode, Votes::decode, Votes::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void send(ServerPlayer p, Object msg) {
      channel.send(PacketDistributor.PLAYER.with(() -> p), msg);
   }

   public static void send(Collection<ServerPlayer> ps, Object msg) {
      for (ServerPlayer p : ps) send(p, msg);
   }

   /** Client -> serveur. */
   public static void sendToServer(Object msg) {
      channel.sendToServer(msg);
   }

   /** Debut d'une scene : numero, donnees, mode de passage. */
   public record Start(int run, Scene scene, int skipMode) {
      void encode(FriendlyByteBuf b) {
         b.writeVarInt(run);
         b.writeVarInt(skipMode);
         scene.write(b);
      }

      static Start decode(FriendlyByteBuf b) {
         int run = b.readVarInt(), mode = b.readVarInt();
         return new Start(run, Scene.read(b), mode);
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> START_CLIENT.accept(this));
         ctx.get().setPacketHandled(true);
      }
   }

   public record Stop(int run) {
      void encode(FriendlyByteBuf b) {
         b.writeVarInt(run);
      }

      static Stop decode(FriendlyByteBuf b) {
         return new Stop(b.readVarInt());
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> STOP_CLIENT.accept(this));
         ctx.get().setPacketHandled(true);
      }
   }

   /** Appui maintenu (ou relache) sur « passer » ; run = -1 : cinematique du boss. */
   public record Hold(int run, boolean on) {
      void encode(FriendlyByteBuf b) {
         b.writeVarInt(run + 1);
         b.writeByte(on ? 1 : 0);
      }

      static Hold decode(FriendlyByteBuf b) {
         return new Hold(b.readVarInt() - 1, b.readByte() != 0);
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ServerPlayer p = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (p != null) HOLD_SERVEUR.accept(new HoldRecu(p, run, on));
         });
         ctx.get().setPacketHandled(true);
      }
   }

   /** Etat du vote pour l'invite : appuis, appuis necessaires ; run = -1 : cinematique du boss. */
   public record Votes(int run, int holding, int needed) {
      void encode(FriendlyByteBuf b) {
         b.writeVarInt(run + 1);
         b.writeVarInt(holding);
         b.writeVarInt(needed);
      }

      static Votes decode(FriendlyByteBuf b) {
         return new Votes(b.readVarInt() - 1, b.readVarInt(), b.readVarInt());
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> VOTES_CLIENT.accept(this));
         ctx.get().setPacketHandled(true);
      }
   }
}
