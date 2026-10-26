package fr.heraliz.clansetbases.onglet;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Un onglet créatif partagé par plusieurs mods du même jar, découpé en groupes (un par boss ; pour Tactique : Mêlée, Soins,
 * Grenades, Camos). Le contenu de chaque groupe est fourni par son mod ; côté client, le panneau vertical
 * (client.onglet.PanneauOnglet) liste les groupes et n'affiche que les objets du groupe choisi.
 * <pre>
 *    // dans le constructeur @Mod d'un boss (commun)
 *    HeralizClansetbases.ONGLET.ajouter("mammoth", "onglet.heraliz_clansetbases.mammoth", () -&gt; new ItemStack(WAR_HORN.get()), out -&gt; {
 *       out.accept(WAR_HORN.get());
 *       ...
 *    });
 * </pre>
 * Ce fichier est commun (l'onglet est un registre, enregistré des deux côtés) mais ne contient aucun code d'affichage.
 */
public final class OngletGroupe {
   /** Un groupe : identifiant, clé de traduction du nom, icône, et ce qu'il met dans l'onglet. */
   public record Groupe(String id, String cleNom, Supplier<ItemStack> icone, Consumer<CreativeModeTab.Output> contenu) {
      public Component nom() {
         return Component.translatable(cleNom);
      }
   }

   private final List<Groupe> groupes = new ArrayList<>();
   /** Objet -> groupe, relevé à chaque construction du contenu de l'onglet (fil client). */
   private final Map<Item, String> groupeDe = new IdentityHashMap<>();

   /** Ajoute un groupe (à appeler pendant la construction des mods, avant que l'onglet soit rempli). */
   public synchronized void ajouter(String id, String cleNom, Supplier<ItemStack> icone, Consumer<CreativeModeTab.Output> contenu) {
      groupes.add(new Groupe(id, cleNom, icone, contenu));
   }

   public synchronized List<Groupe> groupes() {
      return Collections.unmodifiableList(new ArrayList<>(groupes));
   }

   /** Groupe de l'objet (null : objet hors de cet onglet, ou onglet pas encore rempli). */
   public String groupeDe(ItemStack stack) {
      return groupeDe.get(stack.getItem());
   }

   /** Remplit l'onglet : tous les groupes dans l'ordre d'ajout, en notant le groupe de chaque objet. */
   public void remplir(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output sortie) {
      groupeDe.clear();
      for (Groupe g : groupes()) {
         g.contenu().accept((stack, visibilite) -> {
            groupeDe.putIfAbsent(stack.getItem(), g.id());
            sortie.accept(stack, visibilite);
         });
      }
   }

   /** Fabrique l'onglet (à enregistrer par le mod porteur, sous son espace de noms). */
   public CreativeModeTab construire(Component titre, Supplier<ItemStack> icone) {
      return CreativeModeTab.builder().title(titre).icon(icone).displayItems(this::remplir).build();
   }
}
