package fr.heralisz.bunker.serveur.fragments;

import fr.heralisz.bunker.keycard.AccessLevel;
import fr.heralisz.bunker.keycard.FragmentsPont;
import fr.heralisz.bunker.keycard.KeycardFragmentItem;
import fr.heralisz.bunker.keycard.KeycardRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Clic droit avec un fragment de carte d'accès : si les 4 numéros DIFFÉRENTS de cette carte sont dans l'inventaire du
 * joueur (inventaire et main gauche, jamais l'armure), un exemplaire de chacun est retiré, puis la carte est donnée.
 * Objets toujours retirés avant que la carte soit donnée, jamais copiés. Pas de place pour la carte : refusé, rien
 * n'est retiré. Sinon, la liste des numéros qui manquent.
 */
public final class AssemblageFragments implements FragmentsPont.Assemblage {
   private static final String[] NOMS_FR = {"Carte d'accès verte", "Carte d'accès bleue", "Carte d'accès rouge"};

   public static void brancher() {
      FragmentsPont.ASSEMBLAGE = new AssemblageFragments();
   }

   @Override
   public void assembler(ServerPlayer joueur, AccessLevel carte) {
      if (!joueur.isAlive() || joueur.isSpectator()) return;
      Inventory inv = joueur.getInventory();
      int[] cases = new int[KeycardFragmentItem.MORCEAUX];
      List<String> manquants = new ArrayList<>();
      for (int n = 1; n <= KeycardFragmentItem.MORCEAUX; n++) {
         cases[n - 1] = chercher(inv, KeycardRegistry.fragment(carte, n));
         if (cases[n - 1] < 0) manquants.add(String.valueOf(n));
      }
      if (!manquants.isEmpty()) {
         joueur.displayClientMessage(Component.translatableWithFallback("heralisz_bunker.fragment.manquants",
            "Fragments manquants : %s", String.join(", ", manquants)).withStyle(ChatFormatting.YELLOW), true);
         return;
      }
      // place pour la carte (une case vide de l'inventaire, ou une case qu'un fragment libère)
      boolean place = inv.getFreeSlot() >= 0;
      for (int c : cases) {
         if (c < inv.items.size() && inv.getItem(c).getCount() == 1) place = true;
      }
      if (!place) {
         joueur.displayClientMessage(Component.translatableWithFallback("heralisz_bunker.fragment.place",
            "Fais de la place dans ton inventaire pour la carte").withStyle(ChatFormatting.RED), true);
         return;
      }
      for (int c : cases) {
         if (inv.removeItem(c, 1).isEmpty()) return;   // impossible (vérifié juste avant) : rien n'est donné
      }
      ItemStack nouvelle = new ItemStack(KeycardRegistry.CARDS.get(carte).get());
      if (!inv.add(nouvelle) && !nouvelle.isEmpty()) {
         joueur.drop(nouvelle, false);                   // filet de sécurité : jamais perdue
      }
      joueur.level().playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.UI_STONECUTTER_TAKE_RESULT,
         SoundSource.PLAYERS, 0.9F, 1.2F);
      Component nom = Component.translatableWithFallback("item.heralisz_bunker.keycard_" + carte.id, NOMS_FR[carte.ordinal()])
         .withStyle(carte.color);
      joueur.displayClientMessage(Component.translatableWithFallback("heralisz_bunker.fragment.assemblee", "%s assemblée !", nom)
         .withStyle(ChatFormatting.GREEN), true);
   }

   /** Case (numéro du conteneur Inventory) d'un exemplaire de cet objet : inventaire puis main gauche ; -1 sinon. */
   private static int chercher(Inventory inv, Item item) {
      for (int i = 0; i < inv.items.size(); i++) {
         if (inv.items.get(i).is(item)) return i;
      }
      return inv.offhand.get(0).is(item) ? Inventory.SLOT_OFFHAND : -1;
   }
}
