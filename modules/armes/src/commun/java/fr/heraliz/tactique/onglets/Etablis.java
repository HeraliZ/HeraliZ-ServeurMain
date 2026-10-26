package fr.heraliz.tactique.onglets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Établis de TaCZ retirés de tous les onglets créatifs. TaCZ enregistre trois objets d'établi génériques,
 * {@code tacz:workbench_a}, {@code tacz:workbench_b} et {@code tacz:workbench_c} (tailles 1x1x1, 2x1x1 et 1x2x1, que les
 * gunpacks peuvent habiller), et range dans son onglet « Divers » (tacz:other) un exemplaire par établi déclaré par les
 * gunpacks (index/blocks). Chez nous, seule la table d'armurier ({@code tacz:gun_smith_table}) sert : les autres
 * s'affichaient sans nom (« block.tacz.workbench_a »...). On ne touche pas aux objets eux-mêmes (déjà posés ou en
 * inventaire, ils restent valables) : ils ne sont plus proposés dans l'inventaire créatif, recherche comprise.
 */
public final class Etablis {
   private static final Set<ResourceLocation> ETABLIS = Set.of(
      new ResourceLocation("tacz", "workbench_a"),
      new ResourceLocation("tacz", "workbench_b"),
      new ResourceLocation("tacz", "workbench_c")
   );

   private Etablis() {
   }

   /** Écouteur du bus du mod (HeralizTactique) : appelé pour chaque onglet au remplissage. */
   public static void retirer(BuildCreativeModeTabContentsEvent e) {
      List<ItemStack> etablis = new ArrayList<>();
      for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> en : e.getEntries()) {
         if (estEtabli(en.getKey())) {
            etablis.add(en.getKey());
         }
      }
      etablis.forEach(e.getEntries()::remove);
   }

   public static boolean estEtabli(ItemStack stack) {
      ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
      return id != null && ETABLIS.contains(id);
   }
}
