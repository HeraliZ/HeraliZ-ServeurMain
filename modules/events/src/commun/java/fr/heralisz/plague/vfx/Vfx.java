package fr.heralisz.plague.vfx;

import fr.heraliz.boss.pont.Crochet;
import fr.heralisz.plague.HeraliszPlague;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Effets visuels de la Mère : le serveur décide (où, quand, quelle taille), les clients proches dessinent. Un seul paquet
 * générique : type + position + direction + 2 tailles + durée + couleur + paramètre libre.
 */
public final class Vfx {
   public enum Type {
      /** Cercle d'alerte au sol qui se remplit jusqu'à l'impact (a = rayon). */
      CIRCLE,
      /** Couloir d'alerte au sol : a = longueur, b = largeur. */
      LINE,
      /** Cône d'alerte au sol : a = portée, b = angle total (degrés). */
      CONE,
      /** Onde de choc : anneau au sol + mur de poussière et de braises qui s'élargit jusqu'à a ; b = hauteur. */
      SHOCKWAVE,
      /** Éclair lumineux face caméra : a = taille. */
      FLASH,
      /** Fissure de braise au sol : a = longueur, b = largeur. */
      CRACK,
      /** Débris 3D : a = rayon, b = force, color = nombre, extra = id d'état de bloc, direction = poussée. */
      DEBRIS,
      /** Tremblement de caméra : a = intensité, b = portée. */
      SHAKE,
      /** Cinématique : extra = id de l'entité, b = type (0 réveil, 1 phase 2, 2 phase 3, 3 mort). */
      CINEMATIC,
      /** Écran du joueur visé : color 1 = coup encaissé (extra = force x100), sinon voile de cendre (a) / éclair (b). */
      SCREEN,
      /** Deux yeux qui s'allument (a = taille). */
      EYES,
      /** Rayon du vitrail : de pos (la rosace) selon direction jusqu'au sol ; a = largeur, b = intensité (0..1). */
      SHAFT,
      /** Mur de flammes le long d'un couloir : a = longueur, b = largeur. */
      FIREWALL,
      /** Gerbe d'étincelles (fer contre fer, balles sur le pavois) : direction = sens du jet, a = force. */
      SPARKS,
      /** Brûlure au sol (dalle roussie qui fume, puis s'efface) : a = rayon. */
      SCORCH,
      /** Pluie de braises et de cendres qui tombe de la voûte autour de pos : a = rayon. */
      ASHFALL,
      /** Plaque de toile gluante au sol : a = rayon. */
      WEB,
      /** Fil de soie : b = mode (0 visée rouge, 1 harpon / fil tendu, 2 fil de la descente, 3 victime emmaillotée), extra = entité visée
       *  (sinon jusqu'à pos + direction). */
      SILK
   }

   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszPlague.MODID, "vfx"), () -> "1", "1"::equals,
      "1"::equals);

   /** Dessin de l'effet reçu : branché par la partie client (VfxManager::receive) ; neutre sur un serveur dédié. */
   public static final Crochet<Packet> RECEPTION_CLIENT = new Crochet<>();

   public static void register() {
      CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
         java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));   // serveur -> joueurs seulement
   }

   public record Packet(Type type, double x, double y, double z, float dx, float dy, float dz, float a, float b, int duration, int color, int extra) {
      void encode(FriendlyByteBuf buf) {
         buf.writeByte(type.ordinal());
         buf.writeDouble(x);
         buf.writeDouble(y);
         buf.writeDouble(z);
         buf.writeFloat(dx);
         buf.writeFloat(dy);
         buf.writeFloat(dz);
         buf.writeFloat(a);
         buf.writeFloat(b);
         buf.writeVarInt(duration);
         buf.writeInt(color);
         buf.writeVarInt(extra);
      }

      static Packet decode(FriendlyByteBuf buf) {
         return new Packet(Type.values()[buf.readByte()], buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readVarInt());
      }

      void handle(Supplier<NetworkEvent.Context> ctx) {
         ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(this));
         ctx.get().setPacketHandled(true);
      }

      public Vec3 pos() {
         return new Vec3(x, y, z);
      }

      public Vec3 dir() {
         return new Vec3(dx, dy, dz);
      }
   }

   // ------------------------------------------------------------------ côté serveur
   /** Braise (ondes, fissures), or (éclairs), blanc chaud. */
   public static final int EMBER = 0xFF6A1E, GOLD = 0xFFD27A, WHITE = 0xFFF2DC;

   public static void send(ServerLevel level, Type t, Vec3 pos, Vec3 dir, float a, float b, int duration, int color, int extra) {
      Packet p = new Packet(t, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, a, b, duration, color, extra);
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 128, level.dimension())), p);
   }

   /** Coup encaissé par ce joueur : à-coup du champ de vision, voile rouge (force 0..1). */
   public static void hit(net.minecraft.world.entity.player.Player p, float strength) {
      if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return;
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Packet(Type.SCREEN, p.getX(), p.getY(), p.getZ(), 0, 0, 0, 0, 0, 0, 1, (int) (strength * 100)));
   }

   /** Voile de cendre / éclair blanc sur l'écran d'un joueur (a = cendre 0..1, b = éclair 0..1, durée = tenue). */
   public static void screen(net.minecraft.world.entity.player.Player p, float ash, float white, int hold) {
      if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return;
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Packet(Type.SCREEN, p.getX(), p.getY(), p.getZ(), 0, 0, 0, ash, white, hold, 0, 0));
   }

   public static void eyes(ServerLevel l, Vec3 at, float size, int dur) {
      send(l, Type.EYES, at, Vec3.ZERO, size, 0, dur, 0xFF4A12, 0);
   }

   public static void circle(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.CIRCLE, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void line(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.LINE, from, dir, len, width, dur, 0, 0);
   }

   public static void cone(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.CONE, from, dir, range, angle, dur, 0, 0);
   }

   public static void shockwave(ServerLevel l, Vec3 pos, float r, float h, int dur) {
      send(l, Type.SHOCKWAVE, pos, Vec3.ZERO, r, h, dur, EMBER, 0);
   }

   public static void flash(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.FLASH, pos, Vec3.ZERO, size, 0, dur, WHITE, 0);
   }

   public static void flash(ServerLevel l, Vec3 pos, float size, int dur, int color) {
      send(l, Type.FLASH, pos, Vec3.ZERO, size, 0, dur, color, 0);
   }

   public static void crack(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.CRACK, from, dir, len, width, dur, EMBER, 0);
   }

   public static void debris(ServerLevel l, Vec3 pos, float radius, float force, int count, BlockState state, Vec3 push) {
      send(l, Type.DEBRIS, pos, push, radius, force, 1, count, Block.getId(state));
   }

   public static void shake(ServerLevel l, Vec3 pos, float intensity, float range, int dur) {
      send(l, Type.SHAKE, pos, Vec3.ZERO, intensity, range, dur, 0, 0);
   }

   public static void cinematic(ServerLevel l, Vec3 pos, int entityId, int kind, int dur) {
      send(l, Type.CINEMATIC, pos, Vec3.ZERO, 0, kind, dur, 0, entityId);
   }

   /** Cinématique dont le repère (devant) est fixé par le serveur : pas d'attente de la rotation synchronisée du boss. */
   public static void cinematic(ServerLevel l, Vec3 pos, int entityId, int kind, int dur, Vec3 facing) {
      send(l, Type.CINEMATIC, pos, facing, 0, kind, dur, 0, entityId);
   }

   /** Rayon de la rosace vers un point du sol : largeur, intensité (0..1), durée. */
   public static void shaft(ServerLevel l, Vec3 window, Vec3 floor, float width, float intensity, int dur) {
      send(l, Type.SHAFT, window, floor.subtract(window), width, intensity, dur, 0, 0);
   }

   public static void firewall(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.FIREWALL, from, dir, len, width, dur, EMBER, 0);
   }

   public static void sparks(ServerLevel l, Vec3 at, Vec3 dir, float force) {
      send(l, Type.SPARKS, at, dir, force, 0, 1, GOLD, 0);
   }

   public static void scorch(ServerLevel l, Vec3 at, float r, int dur) {
      send(l, Type.SCORCH, at, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void ashfall(ServerLevel l, Vec3 at, float r, int dur) {
      send(l, Type.ASHFALL, at, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void web(ServerLevel l, Vec3 at, float r, int dur) {
      send(l, Type.WEB, at, Vec3.ZERO, r, 0, dur, 0xF2F0E8, 0);
   }

   /** Fil de soie de from jusqu'à l'entité entityId (mode : voir SILK). */
   public static void silk(ServerLevel l, Vec3 from, int entityId, int mode, int dur) {
      send(l, Type.SILK, from, Vec3.ZERO, 0, mode, dur, 0, entityId);
   }

   /** Fil de soie de from jusqu'au point to. */
   public static void silk(ServerLevel l, Vec3 from, int mode, int dur, Vec3 to) {
      send(l, Type.SILK, from, to.subtract(from), 0, mode, dur, 0, 0);
   }

   /** Rayon de lumière pâle qui tombe d'une faille de la voûte (couleur unie). */
   public static void beam(ServerLevel l, Vec3 top, Vec3 floor, float width, float intensity, int dur, int color) {
      send(l, Type.SHAFT, top, floor.subtract(top), width, intensity, dur, color, 0);
   }

   private Vfx() {}
}
