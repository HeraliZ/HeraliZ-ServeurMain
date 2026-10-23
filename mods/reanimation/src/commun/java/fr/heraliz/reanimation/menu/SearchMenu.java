package fr.heraliz.reanimation.menu;

import fr.heraliz.reanimation.core.DownedManager;
import fr.heraliz.reanimation.registry.ModMenus;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fouille d'un joueur a terre : son equipement, son inventaire et sa barre rapide (cases 0 a 40), l'inventaire du
 * fouilleur (41 a 76), puis, s'il porte Curios, une colonne de ses emplacements Curios (masque, gilet, gants...) a gauche
 * de l'ecran (77 et suivantes). Les cases Curios sont branchees cote serveur sur les vrais emplacements de la victime
 * ({@code compat.CuriosFouille}, partie serveur) : les objets sont deplaces, jamais copies. Chez le joueur, elles ne
 * sont qu'un affichage (le serveur decide de tout).
 */
public class SearchMenu extends AbstractContainerMenu {
   /**
    * Objets à vie de HeraliZ Tactique (couteaux, skins à vie : {@code fr.heraliz.tactique.avie.ObjetsAVie.estAVie}, lu par
    * réflexion) : ils restent à leur propriétaire, on ne les prend pas en le fouillant (21/10). Sans Tactique : rien.
    */
   private static volatile java.lang.reflect.Method aVie;
   private static volatile boolean aVieCherche;

   static boolean estAVie(ItemStack stack) {
      if (stack.isEmpty()) return false;
      if (!aVieCherche) {
         try {
            aVie = Class.forName("fr.heraliz.tactique.avie.ObjetsAVie").getMethod("estAVie", ItemStack.class);
         } catch (ReflectiveOperationException | LinkageError ex) {
            aVie = null;
         }
         aVieCherche = true;
      }
      java.lang.reflect.Method m = aVie;
      if (m == null) return false;
      try {
         return Boolean.TRUE.equals(m.invoke(null, stack));
      } catch (ReflectiveOperationException | RuntimeException ex) {
         return false;
      }
   }

   /** Case de l'inventaire de la victime : un objet à vie ne se prend pas. */
   private static final class SlotVictime extends Slot {
      SlotVictime(Container c, int index, int x, int y) {
         super(c, index, x, y);
      }

      @Override
      public boolean mayPickup(Player joueur) {
         return !estAVie(this.getItem()) && super.mayPickup(joueur);
      }
   }

   public static final int TARGET_SIZE = 41;
   public static final int FIRST_BACKPACK = 5;
   public static final int WIDTH = 176;
   public static final int EQUIP_Y = 20;
   public static final int OFFHAND_X = 98;
   public static final int MAIN_Y = 50;
   public static final int HOTBAR_Y = 108;
   public static final int INV_LABEL_Y = 132;
   public static final int INV_Y = 144;
   public static final int INV_HOTBAR_Y = 202;
   public static final int HEIGHT = 226;
   /** Premiere case Curios du menu (apres les 41 cases de la victime et les 36 du fouilleur). */
   public static final int FIRST_CURIOS = 77;
   /** Colonne Curios : x de la colonne la plus proche de l'inventaire (les suivantes vont vers la gauche), y, hauteur. */
   public static final int CURIOS_X = -28;
   public static final int CURIOS_Y = 20;
   public static final int CURIOS_PER_COLUMN = 11;
   public static final int CURIOS_MAX = 33;
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final ResourceLocation[] ARMOR_ICONS = new ResourceLocation[]{
      InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
   };
   private final Container target;
   private final int targetId;
   @Nullable
   private final LivingEntity owner;
   private final Accessoires curios;

   public SearchMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
      // ordre de lecture = ordre d'ecriture du serveur (identifiant de la victime, puis ses emplacements Curios)
      this(id, playerInventory, new SimpleContainer(41), buf.readVarInt(), (LivingEntity)null, Accessoires.lire(buf));
   }

   public SearchMenu(int id, Inventory playerInventory, Container target, LivingEntity owner) {
      this(id, playerInventory, target, owner.getId(), owner, Accessoires.AUCUN);
   }

   /** Cote serveur, avec les emplacements Curios de la victime (Accessoires.AUCUN sans Curios). */
   public SearchMenu(int id, Inventory playerInventory, Container target, LivingEntity owner, Accessoires curios) {
      this(id, playerInventory, target, owner.getId(), owner, curios);
   }

   private SearchMenu(int id, Inventory playerInventory, Container target, int targetId, @Nullable final LivingEntity owner, Accessoires curios) {
      super((MenuType)ModMenus.SEARCH.get(), id);
      checkContainerSize(target, 41);
      this.target = target;
      this.targetId = targetId;
      this.owner = owner;
      this.curios = curios;
      final Player viewer = playerInventory.player;

      for (int i = 0; i < 4; i++) {
         final EquipmentSlot type = ARMOR[i];
         this.addSlot((new Slot(target, 39 - i, 8 + i * 18, 20) {
            public boolean mayPlace(ItemStack stack) {
               return stack.canEquip(type, (Entity)(owner != null ? owner : viewer));
            }

            public int getMaxStackSize() {
               return 1;
            }

            public boolean mayPickup(Player joueur) {
               return !estAVie(this.getItem()) && super.mayPickup(joueur);
            }
         }).setBackground(InventoryMenu.BLOCK_ATLAS, ARMOR_ICONS[i]));
      }

      this.addSlot(new SlotVictime(target, 40, 98, 20).setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new SlotVictime(target, 9 + row * 9 + col, 8 + col * 18, 50 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new SlotVictime(target, col, 8 + col * 18, 108));
      }

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 144 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 202));
      }

      // emplacements Curios de la victime : une colonne a gauche de l'ecran (11 par colonne, colonnes vers la gauche)
      for (int k = 0; k < curios.taille(); k++) {
         final int index = k;
         int colonne = k / CURIOS_PER_COLUMN;
         int rang = k % CURIOS_PER_COLUMN;
         this.addSlot((new Slot(curios.conteneur(), k, CURIOS_X - colonne * 18, CURIOS_Y + rang * 18) {
            public boolean mayPlace(ItemStack stack) {
               return curios.accepte().test(index, stack);
            }

            // un objet à vie reste à son propriétaire, aussi dans ses emplacements Curios
            public boolean mayPickup(Player joueur) {
               return !estAVie(this.getItem()) && super.mayPickup(joueur);
            }

            public int getMaxStackSize() {
               return 1;
            }
         }).setBackground(InventoryMenu.BLOCK_ATLAS, curios.icones()[k]));
      }
   }

   /** Nombre d'emplacements Curios montres (0 sans Curios). */
   public int curiosCount() {
      return this.curios.taille();
   }

   /** Identifiant Curios (« mask », « back »...) de la k-ieme case Curios, vide sinon. */
   public String curiosId(int k) {
      return k >= 0 && k < this.curios.ids().length ? this.curios.ids()[k] : "";
   }

   public int targetId() {
      return this.targetId;
   }

   @Nullable
   public LivingEntity owner() {
      return this.owner;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (!slot.hasItem()) {
         return ItemStack.EMPTY;
      }

      if (!slot.mayPickup(player)) {
         return ItemStack.EMPTY;
      }

      ItemStack stack = slot.getItem();
      ItemStack copy = stack.copy();
      if (index < 41 || index >= FIRST_CURIOS) {
         // case de la victime (inventaire ou Curios) -> inventaire du fouilleur
         if (!this.moveItemStackTo(stack, 41, FIRST_CURIOS, true)) {
            return ItemStack.EMPTY;
         }
      } else {
         boolean moved = false;

         for (int i = 0; i < 4 && !moved; i++) {
            if (!((Slot)this.slots.get(i)).hasItem() && ((Slot)this.slots.get(i)).mayPlace(stack)) {
               moved = this.moveItemStackTo(stack, i, i + 1, false);
            }
         }

         // puis un emplacement Curios libre qui l'accepte (validateurs de Curios, decides par le serveur)
         for (int i = FIRST_CURIOS; i < this.slots.size() && !moved; i++) {
            if (!((Slot)this.slots.get(i)).hasItem() && ((Slot)this.slots.get(i)).mayPlace(stack)) {
               moved = this.moveItemStackTo(stack, i, i + 1, false);
            }
         }

         if (!moved && !this.moveItemStackTo(stack, 5, 41, false)) {
            return ItemStack.EMPTY;
         }
      }

      if (stack.isEmpty()) {
         slot.set(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }

      if (stack.getCount() == copy.getCount()) {
         return ItemStack.EMPTY;
      }

      slot.onTake(player, stack);
      return copy;
   }

   public boolean stillValid(Player player) {
      return this.owner == null || DownedManager.isSearching(player, this.owner);
   }

   public void removed(Player player) {
      super.removed(player);
      if (this.owner != null && !player.level().isClientSide) {
         DownedManager.searchClosed(this.owner, player);
      }
   }

   /**
    * Emplacements Curios montres a la fouille : le conteneur (cote serveur, branche sur les vrais emplacements de la
    * victime ; chez le joueur, une simple copie d'affichage), l'identifiant et l'icone de chaque case, et la regle de
    * depot (validateurs de Curios cote serveur).
    */
   public record Accessoires(Container conteneur, String[] ids, ResourceLocation[] icones, BiPredicate<Integer, ItemStack> accepte) {
      public static final Accessoires AUCUN = new Accessoires(new SimpleContainer(0), new String[0], new ResourceLocation[0], (k, s) -> false);
      private static final ResourceLocation ICONE_DEFAUT = new ResourceLocation("curios", "slot/empty_curio_slot");

      public int taille() {
         return Math.min(this.ids.length, Math.min(this.icones.length, this.conteneur.getContainerSize()));
      }

      /** Ecrit le plan des cases (serveur -> joueur, a l'ouverture). */
      public void ecrire(FriendlyByteBuf buf) {
         int n = Math.min(this.taille(), CURIOS_MAX);
         buf.writeVarInt(n);

         for (int k = 0; k < n; k++) {
            buf.writeUtf(this.ids[k] == null ? "" : this.ids[k], 64);
            buf.writeResourceLocation(this.icones[k] == null ? ICONE_DEFAUT : this.icones[k]);
         }
      }

      /** Lit le plan des cases chez le joueur : cases d'affichage, remplies par la synchronisation du menu. */
      public static Accessoires lire(FriendlyByteBuf buf) {
         int n = Math.max(0, Math.min(buf.readVarInt(), CURIOS_MAX));
         String[] ids = new String[n];
         ResourceLocation[] icones = new ResourceLocation[n];

         for (int k = 0; k < n; k++) {
            ids[k] = buf.readUtf(64);
            icones[k] = buf.readResourceLocation();
         }

         // chez le joueur, aucun depot predit : le serveur decide (validateurs de Curios) et renvoie le resultat
         return n == 0 ? AUCUN : new Accessoires(new SimpleContainer(n), ids, icones, (k, s) -> false);
      }
   }
}
