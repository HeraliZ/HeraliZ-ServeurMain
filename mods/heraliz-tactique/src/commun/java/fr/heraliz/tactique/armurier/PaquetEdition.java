package fr.heraliz.tactique.armurier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur -> op : toutes les recettes de la Table d'armurier, telles qu'appliquées (fichier + changements en jeu), avec
 * les recettes supprimées en jeu (pour pouvoir les remettre). {@code choisie} : recette à montrer (après un
 * enregistrement), vide sinon.
 */
public record PaquetEdition(List<Recette> recettes, String choisie) {
   public static final byte FICHIER = 0, MODIFIEE = 1, AJOUTEE = 2, SUPPRIMEE = 3;

   /** Une ligne de « Ce qu'elle demande » : objet montré, quantité, ingrédient d'origine (JSON) gardé tel quel. */
   public record Entree(ItemStack icone, int quantite, String ingredient) {
   }

   public record Recette(String id, String onglet, byte etat, ItemStack resultat, List<Entree> entrees) {
   }

   public static void encode(PaquetEdition p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.recettes.size());
      for (Recette r : p.recettes) {
         buf.writeUtf(r.id);
         buf.writeUtf(r.onglet);
         buf.writeByte(r.etat);
         buf.writeItem(r.resultat);
         buf.writeVarInt(r.entrees.size());
         for (Entree e : r.entrees) {
            buf.writeItem(e.icone);
            buf.writeVarInt(e.quantite);
            buf.writeUtf(e.ingredient);
         }
      }
      buf.writeUtf(p.choisie);
   }

   public static PaquetEdition decode(FriendlyByteBuf buf) {
      int n = Math.min(buf.readVarInt(), 4096);
      List<Recette> l = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         String id = buf.readUtf(256), onglet = buf.readUtf(256);
         byte etat = buf.readByte();
         ItemStack res = buf.readItem();
         int m = Math.min(buf.readVarInt(), 64);
         List<Entree> e = new ArrayList<>(m);
         for (int k = 0; k < m; k++) e.add(new Entree(buf.readItem(), buf.readVarInt(), buf.readUtf(4096)));
         l.add(new Recette(id, onglet, etat, res, e));
      }
      return new PaquetEdition(l, buf.readUtf(256));
   }

   public static void handle(PaquetEdition p, Supplier<NetworkEvent.Context> contexte) {
      NetworkEvent.Context c = contexte.get();
      if (c.getDirection().getReceptionSide().isClient()) c.enqueueWork(() -> Armurier.EDITION_CLIENT.accept(p));
      c.setPacketHandled(true);
   }
}
