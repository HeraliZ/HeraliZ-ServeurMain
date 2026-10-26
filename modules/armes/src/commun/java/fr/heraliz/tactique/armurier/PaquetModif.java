package fr.heraliz.tactique.armurier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Op -> serveur : un changement fait dans le Registre de l'armurier. ENREGISTRER : la recette entière (id vide = nouvelle
 * recette) ; SUPPRIMER et REVENIR (au fichier) : seulement l'id ; OUVRIR : renvoyer la liste. Le serveur revérifie les
 * droits (op 4) et chaque valeur. Lecture bornée (tailles et nombre de lignes).
 */
public record PaquetModif(byte action, String id, String onglet, ItemStack resultat, int quantite, List<Ligne> lignes) {
   public static final byte OUVRIR = 0, ENREGISTRER = 1, SUPPRIMER = 2, REVENIR = 3;
   public static final int MAX_LIGNES = 12;

   /** Une ligne demandée : ingrédient d'origine gardé (JSON) ou objet choisi dans la barre ({@code objet}). */
   public record Ligne(String ingredient, ItemStack objet, int quantite) {
   }

   public record Recue(ServerPlayer joueur, PaquetModif paquet) {
   }

   public static PaquetModif simple(byte action, String id) {
      return new PaquetModif(action, id, "", ItemStack.EMPTY, 0, List.of());
   }

   public static void encode(PaquetModif p, FriendlyByteBuf buf) {
      buf.writeByte(p.action);
      buf.writeUtf(p.id, 256);
      buf.writeUtf(p.onglet, 64);
      buf.writeItem(p.resultat);
      buf.writeVarInt(p.quantite);
      buf.writeVarInt(p.lignes.size());
      for (Ligne l : p.lignes) {
         buf.writeUtf(l.ingredient, 4096);
         buf.writeItem(l.objet);
         buf.writeVarInt(l.quantite);
      }
   }

   public static PaquetModif decode(FriendlyByteBuf buf) {
      byte action = buf.readByte();
      String id = buf.readUtf(256), onglet = buf.readUtf(64);
      ItemStack res = buf.readItem();
      int q = buf.readVarInt();
      int n = buf.readVarInt();
      if (n < 0 || n > MAX_LIGNES) throw new IllegalArgumentException("trop de lignes");
      List<Ligne> l = new ArrayList<>(n);
      for (int i = 0; i < n; i++) l.add(new Ligne(buf.readUtf(4096), buf.readItem(), buf.readVarInt()));
      return new PaquetModif(action, id, onglet, res, q, l);
   }

   public static void handle(PaquetModif p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isServer()) {
         c.enqueueWork(() -> {
            ServerPlayer j = c.getSender();
            if (j != null) Armurier.MODIF_SERVEUR.accept(new Recue(j, p));
         });
      }
      c.setPacketHandled(true);
   }
}
