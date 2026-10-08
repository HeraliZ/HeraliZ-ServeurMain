package fr.heralisz.bunker.keycard;

import fr.heralisz.bunker.pont.Crochet;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Serveur -> joueurs à moins de 10 blocs : « tel joueur glisse une carte dans tel lecteur » -> même cinématique pour tous. */
public final class KeycardNetwork {
   /** Réception du passage de carte côté client (KeycardClientHooks.onSwipe) : branchée par la partie client. */
   public static final Crochet<SwipePacket> RECEPTION_CLIENT = new Crochet<>();

   private static final String VERSION = "2";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(HeraliszBunker.MODID, "keycard"), () -> VERSION, VERSION::equals, VERSION::equals);

   public static void init() {
      CHANNEL.registerMessage(0, SwipePacket.class, SwipePacket::encode, SwipePacket::decode, SwipePacket::handle);
   }

   /** Rayon dans lequel tous les joueurs voient la cinématique. */
   public static final double CINEMATIC_RADIUS = 10.0;

   public static void sendSwipe(ServerPlayer swiper, BlockPos reader, Direction facing, boolean granted, @Nullable DoorBlockEntity door) {
      DoorShot shot = DoorShot.NONE;
      if (door != null) {
         BlockPos min = door.getBlockPos(), max = door.getBlockPos();
         for (BlockPos p : KeycardDoors.parts(door)) {
            min = new BlockPos(Math.min(min.getX(), p.getX()), Math.min(min.getY(), p.getY()), Math.min(min.getZ(), p.getZ()));
            max = new BlockPos(Math.max(max.getX(), p.getX()), Math.max(max.getY(), p.getY()), Math.max(max.getZ(), p.getZ()));
         }
         shot = new DoorShot(true, min, max, door.getFacing(), door.getDoorDecl().getOpenTime());
      }
      SwipePacket packet = new SwipePacket(swiper.getId(), reader, facing, granted, shot);
      for (ServerPlayer p : swiper.serverLevel().players()) {
         if (p.distanceToSqr(Vec3.atCenterOf(reader)) <= CINEMATIC_RADIUS * CINEMATIC_RADIUS) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
         }
      }
   }

   /** Porte à filmer : emprise (blocs min / max), orientation, durée d'ouverture (ticks). */
   public record DoorShot(boolean present, BlockPos min, BlockPos max, Direction facing, int openTicks) {
      static final DoorShot NONE = new DoorShot(false, BlockPos.ZERO, BlockPos.ZERO, Direction.NORTH, 0);
   }

   public record SwipePacket(int entityId, BlockPos reader, Direction facing, boolean granted, DoorShot door) {
      static void encode(SwipePacket p, FriendlyByteBuf buf) {
         buf.writeVarInt(p.entityId);
         buf.writeBlockPos(p.reader);
         buf.writeEnum(p.facing);
         buf.writeBoolean(p.granted);
         buf.writeBoolean(p.door.present());
         buf.writeBlockPos(p.door.min());
         buf.writeBlockPos(p.door.max());
         buf.writeEnum(p.door.facing());
         buf.writeVarInt(p.door.openTicks());
      }

      static SwipePacket decode(FriendlyByteBuf buf) {
         return new SwipePacket(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBoolean(),
            new DoorShot(buf.readBoolean(), buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readVarInt()));
      }

      static void handle(SwipePacket p, Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(p));
         ctx.get().setPacketHandled(true);
      }
   }

   private KeycardNetwork() {}
}
