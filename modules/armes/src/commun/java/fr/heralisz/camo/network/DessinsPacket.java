package fr.heralisz.camo.network;

import fr.heralisz.camo.DessinsCopie;
import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Serveur -> joueur (21/11, docs/SKINS_PROTEGES.md, étape 1) : un morceau de la copie des dessins des skins
 * ({@link DessinsCopie}), envoyé à la connexion. {@code total} : octets compressés de toute la copie ; {@code fichiers} et
 * {@code brut} : nombre de fichiers et octets une fois décompressés (pour le journal) ; {@code empreinte} : celle des
 * fichiers, vérifiée par le jeu quand il ouvre la copie. Traité sur le fil du réseau sans rien toucher du jeu : les octets
 * sont seulement gardés en mémoire (fr.heralisz.camo.client.DessinsRecus), jamais écrits sur le disque.
 */
public record DessinsPacket(int partie, int parties, int total, int fichiers, int brut, String empreinte, byte[] octets) {
   /** Traitement chez le joueur (fil du réseau), branché par fr.heralisz.camo.client.PartieClient. */
   public static final Crochet<DessinsPacket> RECEPTION_CLIENT = new Crochet<>();

   public static void encode(DessinsPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.partie);
      buf.writeVarInt(p.parties);
      buf.writeVarInt(p.total);
      buf.writeVarInt(p.fichiers);
      buf.writeVarInt(p.brut);
      buf.writeUtf(p.empreinte, 64);
      buf.writeByteArray(p.octets);
   }

   public static DessinsPacket decode(FriendlyByteBuf buf) {
      int partie = buf.readVarInt();
      int parties = buf.readVarInt();
      int total = buf.readVarInt();
      int fichiers = buf.readVarInt();
      int brut = buf.readVarInt();
      String empreinte = buf.readUtf(64);
      byte[] octets = buf.readByteArray(DessinsCopie.TAILLE_PARTIE);
      return new DessinsPacket(partie, parties, total, fichiers, brut, empreinte, octets);
   }

   public static void handle(DessinsPacket p, Supplier<NetworkEvent.Context> ctx) {
      // fil du réseau : les octets sont seulement rangés (aucun objet du jeu touché, rien de compilé)
      RECEPTION_CLIENT.accept(p);
      ctx.get().setPacketHandled(true);
   }
}
