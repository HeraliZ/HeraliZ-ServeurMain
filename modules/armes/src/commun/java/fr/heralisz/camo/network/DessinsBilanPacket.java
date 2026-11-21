package fr.heralisz.camo.network;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Joueur -> serveur (21/11, docs/SKINS_PROTEGES.md, étape 1) : le résultat de {@code /camos dessins} chez ce joueur, pour
 * le journal du serveur. {@code etat} : {@link #FAIT}, {@link #RIEN} (pas de copie reçue) ou {@link #ILLISIBLE}. Le
 * serveur ne le note que s'il a lui-même envoyé la demande à ce joueur (rien d'autre n'en dépend).
 */
public record DessinsBilanPacket(int etat, int prets, int echecs, int ms) {
   public static final int FAIT = 0, RIEN = 1, ILLISIBLE = 2;
   /** Traitement côté serveur (fil du serveur), branché par fr.heralisz.camo.serveur.PartieServeur. */
   public static final Crochet<CamoNetwork.Recu<DessinsBilanPacket>> RECEPTION_SERVEUR = new Crochet<>();

   public static void encode(DessinsBilanPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.etat);
      buf.writeVarInt(p.prets);
      buf.writeVarInt(p.echecs);
      buf.writeVarInt(p.ms);
   }

   public static DessinsBilanPacket decode(FriendlyByteBuf buf) {
      return new DessinsBilanPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
   }

   public static void handle(DessinsBilanPacket p, Supplier<NetworkEvent.Context> ctx) {
      NetworkEvent.Context c = ctx.get();
      c.enqueueWork(() -> RECEPTION_SERVEUR.accept(new CamoNetwork.Recu<>(p, c.getSender())));
      c.setPacketHandled(true);
   }
}
