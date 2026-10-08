package fr.heralisz.mammoth.vfx;

import fr.heraliz.boss.pont.Crochet;
import fr.heralisz.mammoth.HeraliszMammoth;
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
 * Effets visuels d'Ymirak : le serveur décide (où, quand, quelle taille), les clients proches dessinent. Un seul paquet
 * générique : type + position + direction + 2 tailles + durée + couleur + paramètre libre.
 */
public final class Vfx {
   public enum Type {
      /** Cercle d'alerte au sol qui se remplit jusqu'à l'impact (a = rayon). */
      CIRCLE,
      /** Couloir d'alerte au sol (charge, coup de trompe) : a = longueur, b = largeur. */
      LINE,
      /** Cône d'alerte au sol : a = portée, b = angle total (degrés). */
      CONE,
      /** Onde de choc : anneau au sol + mur de givre qui s'élargit jusqu'à a ; b = hauteur. */
      SHOCKWAVE,
      /** Éclair lumineux face caméra : a = taille. */
      FLASH,
      /** Givre qui s'étend au sol : a = rayon. */
      FROST,
      /** Pics de glace qui percent le sol puis éclatent : a = taille. */
      SPIKES,
      /** Stalactite qui tombe de la voûte sur la position : a = taille, durée = chute. */
      ICICLE,
      /** Fissure lumineuse au sol : a = longueur, b = largeur. */
      CRACK,
      /** Traînée de glace d'une charge : a = longueur, b = largeur. */
      TRAIL,
      /** Débris 3D : a = rayon, b = force, color = nombre, extra = id d'état de bloc, direction = poussée. */
      DEBRIS,
      /** Tremblement de caméra : a = intensité, b = portée. */
      SHAKE,
      /** Cinématique : extra = id de l'entité, b = type (0 apparition, 1 phase 2, 2 phase 3, 3 mort). */
      CINEMATIC,
      /** Bloc de glace lancé en cloche de pos à pos + direction pendant la durée : a = taille. */
      BOULDER,
      /** Souffle de blizzard : de pos selon direction, a = portée, b = ouverture (degrés). */
      BREATH,
      /** Tourbillon d'aspiration au sol : a = rayon. */
      VORTEX,
      /** Cage de pics de glace autour d'un point, qui tient toute la durée puis éclate : a = rayon. */
      CAGE,
      /** Nova de givre : sphère qui enfle jusqu'au rayon a puis se dissipe. */
      NOVA,
      /** Givre sur l'écran du joueur visé : a = intensité ajoutée (0..1), b = voile blanc (0..1), durée = tenue avant de fondre. */
      SCREEN,
      /** Colonne de lumière qui tombe de la voûte : pos = pied, a = rayon, b = hauteur. */
      BEAM,
      /** Le Cœur de l'Hiver qui s'élève : de pos à pos + direction pendant la durée, a = taille ; il éclate à la fin. */
      HEART,
      /** Deux yeux qui s'allument : lueur rouge qui s'enclenche d'un coup, tient, puis s'éteint (a = taille). */
      EYES
   }

   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszMammoth.MODID, "vfx"), () -> "1", "1"::equals,
      "1"::equals);

   /** Dessin de l'effet reçu : branché par la partie client (VfxManager::receive) ; neutre sur un serveur dédié. */
   public static final Crochet<Packet> RECEPTION_CLIENT = new Crochet<>();

   public static void register() {
      CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle);
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
   public static final int FROSTC = 0x8AD8FF, WHITE = 0xE6F6FF;

   public static void send(ServerLevel level, Type t, Vec3 pos, Vec3 dir, float a, float b, int duration, int color, int extra) {
      Packet p = new Packet(t, pos.x, pos.y, pos.z, (float) dir.x, (float) dir.y, (float) dir.z, a, b, duration, color, extra);
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, 112, level.dimension())), p);
   }

   /** Givre qui envahit l'écran d'un joueur (remplace l'écran de neige vanilla). */
   public static void screen(net.minecraft.world.entity.player.Player p, float amount, float white, int hold) {
      if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return;
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Packet(Type.SCREEN, p.getX(), p.getY(), p.getZ(), 0, 0, 0, amount, white, hold, 0, 0));
   }

   /** Coup encaissé par ce joueur : écran fendu de glace et à-coup du champ de vision (force 0..1). */
   public static void hit(net.minecraft.world.entity.player.Player p, float strength) {
      if (!(p instanceof net.minecraft.server.level.ServerPlayer sp)) return;
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new Packet(Type.SCREEN, p.getX(), p.getY(), p.getZ(), 0, 0, 0, 0, 0, 0, 1, (int) (strength * 100)));
   }

   public static void eyes(ServerLevel l, Vec3 at, float size, int dur) {
      send(l, Type.EYES, at, Vec3.ZERO, size, 0, dur, 0xFF2A18, 0);
   }

   public static void beam(ServerLevel l, Vec3 foot, float r, float h, int dur) {
      send(l, Type.BEAM, foot, Vec3.ZERO, r, h, dur, 0xDDF4FF, 0);
   }

   public static void heart(ServerLevel l, Vec3 from, Vec3 to, float size, int dur) {
      send(l, Type.HEART, from, to.subtract(from), size, 0, dur, 0x9ADCFF, 0);
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
      send(l, Type.SHOCKWAVE, pos, Vec3.ZERO, r, h, dur, FROSTC, 0);
   }

   public static void flash(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.FLASH, pos, Vec3.ZERO, size, 0, dur, WHITE, 0);
   }

   public static void frost(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.FROST, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void spikes(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.SPIKES, pos, Vec3.ZERO, size, 0, dur, 0, 0);
   }

   public static void icicle(ServerLevel l, Vec3 pos, float size, int dur) {
      send(l, Type.ICICLE, pos, Vec3.ZERO, size, 0, dur, 0, 0);
   }

   public static void crack(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.CRACK, from, dir, len, width, dur, FROSTC, 0);
   }

   public static void trail(ServerLevel l, Vec3 from, Vec3 dir, float len, float width, int dur) {
      send(l, Type.TRAIL, from, dir, len, width, dur, 0, 0);
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

   public static void boulder(ServerLevel l, Vec3 from, Vec3 to, float size, int dur) {
      send(l, Type.BOULDER, from, to.subtract(from), size, 0, dur, 0, 0);
   }

   public static void breath(ServerLevel l, Vec3 from, Vec3 dir, float range, float angle, int dur) {
      send(l, Type.BREATH, from, dir, range, angle, dur, 0, 0);
   }

   public static void vortex(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.VORTEX, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void cage(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.CAGE, pos, Vec3.ZERO, r, 0, dur, 0, 0);
   }

   public static void nova(ServerLevel l, Vec3 pos, float r, int dur) {
      send(l, Type.NOVA, pos, Vec3.ZERO, r, 0, dur, FROSTC, 0);
   }

   private Vfx() {}
}
