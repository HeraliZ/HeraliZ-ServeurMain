package fr.heraliz.reanimation.compat;

import fr.heraliz.reanimation.ReanimationMod;
import fr.heraliz.reanimation.menu.SearchMenu;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Lien souple avec Curios pour la fouille d'un joueur a terre : ses emplacements Curios (masque, gilet, gants, genouillere,
 * sac a dos, ghillie, brassard...) branches sur un {@link Container} que le menu de fouille montre a gauche de l'ecran.
 * Les cases lisent et ecrivent directement les emplacements de la victime : un objet pris est DEPLACE (meme pile, sac a
 * dos avec son contenu, numero anti-duplication de Spartan compris), jamais copie. Depot : validateurs de Curios
 * ({@code CuriosApi.isStackValid}, {@code ICurio.canEquip}) et regle des combinaisons de l'Armurerie si elle est la (par
 * reflexion). Sans Curios (ou API changee) : aucune case, la fouille reste comme avant. La classe qui touche a l'API
 * ({@link Api}) n'est chargee que si le mod est present.
 */
public final class CuriosFouille {
   private static Boolean present;
   private static boolean casse;

   private CuriosFouille() {
   }

   private static boolean present() {
      if (present == null) {
         present = ModList.get() != null && ModList.get().isLoaded("curios");
      }

      return present && !casse;
   }

   /** Emplacements Curios de la victime pour le menu de fouille (Accessoires.AUCUN sans Curios ou pour un bot). */
   public static SearchMenu.Accessoires de(LivingEntity cible) {
      if (!(cible instanceof Player) || !present()) {
         return SearchMenu.Accessoires.AUCUN;
      }

      try {
         return Api.de(cible);
      } catch (LinkageError | RuntimeException e) {
         coupe(e);
         return SearchMenu.Accessoires.AUCUN;
      }
   }

   private static void coupe(Throwable e) {
      if (!casse) {
         casse = true;
         ReanimationMod.LOGGER.warn("[fouille] lien avec Curios coupe : {}", e.toString());
      }
   }

   // ------------------------------------------------------------------ regle des combinaisons de l'Armurerie
   private static boolean combinaisonsCherchees;
   @Nullable
   private static Method combinaisons;

   /** SuitRules.mayEquipCurio de l'Armurerie (combinaison integrale portee = certains emplacements fermes) ; vrai sans elle. */
   private static boolean combinaisonPermet(LivingEntity cible, String id, ItemStack stack) {
      if (!combinaisonsCherchees) {
         combinaisonsCherchees = true;

         try {
            combinaisons = Class.forName("fr.heraliz.armory.suit.SuitRules")
               .getMethod("mayEquipCurio", LivingEntity.class, String.class, ItemStack.class);
         } catch (ReflectiveOperationException | LinkageError e) {
            combinaisons = null;
         }
      }

      if (combinaisons == null) {
         return true;
      }

      try {
         return Boolean.TRUE.equals(combinaisons.invoke(null, cible, id, stack));
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return false;
      }
   }

   // ------------------------------------------------------------------ API de Curios (chargee seulement si Curios est la)
   private static final class Api {
      private static final ResourceLocation ICONE_DEFAUT = new ResourceLocation("curios", "slot/empty_curio_slot");

      /** Une case : emplacement Curios (identifiant, numero) et ses piles vivantes. */
      private record Case(String id, int index, top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler piles, int ordre, ResourceLocation icone) {
         boolean valide() {
            return this.index >= 0 && this.index < this.piles.getSlots();
         }
      }

      static SearchMenu.Accessoires de(LivingEntity cible) {
         var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(cible).resolve().orElse(null);
         if (handler == null) {
            return SearchMenu.Accessoires.AUCUN;
         }

         List<Case> cases = new ArrayList<>();

         for (Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> e : handler.getCurios().entrySet()) {
            String id = e.getKey();
            var sh = e.getValue();
            var piles = sh.getStacks();
            var type = top.theillusivec4.curios.api.CuriosApi.getSlot(id, cible.level()).orElse(null);
            int ordre = type != null ? type.getOrder() : 1000;
            ResourceLocation icone = type != null && type.getIcon() != null ? type.getIcon() : ICONE_DEFAUT;

            // emplacements visibles (ceux de l'ecran de Curios) ; un emplacement cache n'est montre que s'il est rempli
            for (int i = 0; i < piles.getSlots(); i++) {
               if (sh.isVisible() || !piles.getStackInSlot(i).isEmpty()) {
                  cases.add(new Case(id, i, piles, ordre, icone));
               }
            }
         }

         if (cases.isEmpty()) {
            return SearchMenu.Accessoires.AUCUN;
         }

         cases.sort(Comparator.comparingInt(Case::ordre).thenComparing(Case::id).thenComparingInt(Case::index));
         if (cases.size() > SearchMenu.CURIOS_MAX) {
            cases = new ArrayList<>(cases.subList(0, SearchMenu.CURIOS_MAX));
         }

         List<Case> liste = List.copyOf(cases);
         String[] ids = new String[liste.size()];
         ResourceLocation[] icones = new ResourceLocation[liste.size()];

         for (int k = 0; k < liste.size(); k++) {
            ids[k] = liste.get(k).id();
            icones[k] = liste.get(k).icone();
         }

         return new SearchMenu.Accessoires(new Conteneur(liste), ids, icones, (k, stack) -> accepte(cible, liste, k, stack));
      }

      private static boolean accepte(LivingEntity cible, List<Case> liste, int k, ItemStack stack) {
         if (stack.isEmpty() || k < 0 || k >= liste.size()) {
            return false;
         }

         Case c = liste.get(k);
         if (!c.valide()) {
            return false;
         }

         try {
            var ctx = new top.theillusivec4.curios.api.SlotContext(c.id(), cible, c.index(), false, true);
            return top.theillusivec4.curios.api.CuriosApi.isStackValid(ctx, stack)
               && top.theillusivec4.curios.api.CuriosApi.getCurio(stack).map(curio -> curio.canEquip(ctx)).orElse(true)
               && combinaisonPermet(cible, c.id(), stack);
         } catch (LinkageError | RuntimeException e) {
            coupe(e);
            return false;
         }
      }
   }

   /**
    * Les cases Curios de la victime vues comme un conteneur : lecture et ecriture directes de ses emplacements (Curios
    * remarque seul le changement et le synchronise, equipement / retrait compris). Une case devenue invalide (taille de
    * l'emplacement changee pendant la fouille) reste vide et refuse tout.
    */
   private static final class Conteneur implements Container {
      private final List<Api.Case> cases;

      Conteneur(List<Api.Case> cases) {
         this.cases = cases;
      }

      public int getContainerSize() {
         return this.cases.size();
      }

      public boolean isEmpty() {
         for (int k = 0; k < this.cases.size(); k++) {
            if (!this.getItem(k).isEmpty()) {
               return false;
            }
         }

         return true;
      }

      public ItemStack getItem(int k) {
         if (k < 0 || k >= this.cases.size()) {
            return ItemStack.EMPTY;
         }

         Api.Case c = this.cases.get(k);
         return c.valide() ? c.piles().getStackInSlot(c.index()) : ItemStack.EMPTY;
      }

      public ItemStack removeItem(int k, int nombre) {
         ItemStack pile = this.getItem(k);
         if (pile.isEmpty() || nombre <= 0) {
            return ItemStack.EMPTY;
         }

         ItemStack pris = pile.split(nombre);
         this.setItem(k, pile.isEmpty() ? ItemStack.EMPTY : pile);
         return pris;
      }

      public ItemStack removeItemNoUpdate(int k) {
         ItemStack pile = this.getItem(k);
         this.setItem(k, ItemStack.EMPTY);
         return pile;
      }

      public void setItem(int k, ItemStack stack) {
         if (k < 0 || k >= this.cases.size()) {
            return;
         }

         Api.Case c = this.cases.get(k);
         if (c.valide()) {
            c.piles().setStackInSlot(c.index(), stack);
         }
      }

      public void setChanged() {
      }

      public boolean stillValid(Player player) {
         return true;
      }

      public void clearContent() {
         for (int k = 0; k < this.cases.size(); k++) {
            this.setItem(k, ItemStack.EMPTY);
         }
      }
   }
}
