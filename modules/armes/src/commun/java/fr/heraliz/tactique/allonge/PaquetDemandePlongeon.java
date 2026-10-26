package fr.heraliz.tactique.allonge;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * Joueur -> serveur : « je plonge » (touche « Ramper » en se déplaçant, pendant un saut ou avec la touche de saut
 * enfoncée), avec les touches de déplacement tenues : {@code gauche} (1 gauche, -1 droite) et {@code avant} (1 avant,
 * -1 arrière), donc devant, derrière, sur le côté ou en diagonale (14/10). Le serveur décide seul
 * ({@link ReseauAllonge#PLONGEON_SERVEUR}, branché par la partie serveur) : il vérifie que le joueur se déplace bien dans
 * ce sens (positions reçues) et répond par {@link PaquetReponsePlongeon}.
 */
public record PaquetDemandePlongeon(byte gauche, byte avant) {
   /** La demande reçue par le serveur. */
   public record Demande(ServerPlayer joueur, int gauche, int avant) {
   }

   public static void encode(PaquetDemandePlongeon paquet, FriendlyByteBuf buf) {
      buf.writeByte(paquet.gauche);
      buf.writeByte(paquet.avant);
   }

   public static PaquetDemandePlongeon decode(FriendlyByteBuf buf) {
      byte g = (byte) Math.max(-1, Math.min(1, buf.readByte()));
      byte a = (byte) Math.max(-1, Math.min(1, buf.readByte()));
      return new PaquetDemandePlongeon(g, a);
   }

   /**
    * Direction horizontale (longueur 1) des touches tenues pour ce lacet, comme Minecraft déplace le joueur ; zéro si
    * aucune touche.
    */
   public static Vec3 direction(int gauche, int avant, float lacet) {
      double r = Math.toRadians(lacet);
      double sin = Math.sin(r);
      double cos = Math.cos(r);
      double x = gauche * cos - avant * sin;
      double z = avant * cos + gauche * sin;
      double n = Math.sqrt(x * x + z * z);
      return n < 1.0E-6D ? Vec3.ZERO : new Vec3(x / n, 0.0D, z / n);
   }

   public static void handle(PaquetDemandePlongeon paquet, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isServer()) {
         c.enqueueWork(() -> {
            ServerPlayer joueur = c.getSender();
            if (joueur != null) {
               ReseauAllonge.PLONGEON_SERVEUR.accept(new Demande(joueur, paquet.gauche, paquet.avant));
            }
         });
      }
      c.setPacketHandled(true);
   }
}
