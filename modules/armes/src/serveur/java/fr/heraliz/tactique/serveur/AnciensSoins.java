package fr.heraliz.tactique.serveur;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/**
 * Anciens soins du contenu ArkaniaZ (geckolib222 : « Pansement de terrain », « Bandages », « Chiffons ») : sortis du loot,
 * mais encore dans des inventaires de la bêta. Leur effet ne passait pas par nos soins (un joueur à 0,5 cœur, à terre, ne
 * pouvait rien utiliser ; saignement mal soigné). Toutes les 5 s, ceux de l'inventaire d'un joueur deviennent le Bandage
 * de HeraliZ (25/10, même nombre), déplacés sur place, jamais copiés.
 */
public final class AnciensSoins {
   private AnciensSoins() {
   }

   /** Ancien objet -> soin de HeraliZ qui le remplace. */
   private static final Map<ResourceLocation, String> ANCIENS = Map.of(
         new ResourceLocation("geckolib", "field_dressing"), "heralisz_heal:bandage",
         new ResourceLocation("geckolib", "bandages"), "heralisz_heal:bandage",
         new ResourceLocation("geckolib", "rags"), "heralisz_heal:bandage");
   private static final ResourceLocation CONSOMMABLE = new ResourceLocation("heralisz_tactical", "consumable");

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(AnciensSoins::tick);
   }

   private static void tick(TickEvent.PlayerTickEvent e) {
      if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 37) return;
      Item conso = ForgeRegistries.ITEMS.getValue(CONSOMMABLE);
      if (conso == null) return;
      Inventory inv = p.getInventory();
      boolean change = false;
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         if (s.isEmpty()) continue;
         String nouveau = ANCIENS.get(ForgeRegistries.ITEMS.getKey(s.getItem()));
         if (nouveau == null) continue;
         ItemStack n = new ItemStack(conso, s.getCount());
         CompoundTag t = n.getOrCreateTag();
         t.putString("ConsumableId", nouveau);
         inv.setItem(i, n);
         change = true;
      }
      if (change) p.inventoryMenu.broadcastChanges();
   }
}
