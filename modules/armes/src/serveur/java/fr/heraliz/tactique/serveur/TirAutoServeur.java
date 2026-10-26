package fr.heraliz.tactique.serveur;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Tir automatique par défaut (23/10, demande du propriétaire) : toute arme TaCZ qui arrive dans l'inventaire d'un joueur
 * (loot, kits des duels, du ClashWar et des Zombies, fabrication, /give) passe en mode AUTO si l'arme le permet, une
 * seule fois : la marque {@code HeralizTirAuto} est posée sur l'arme, le joueur peut ensuite changer de mode (G) sans
 * qu'on le lui reprenne. Relevé de l'inventaire deux fois par seconde.
 */
final class TirAutoServeur {
   static final String MARQUE = "HeralizTirAuto";

   private TirAutoServeur() {
   }

   static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(TirAutoServeur::tick);
   }

   private static void tick(TickEvent.PlayerTickEvent ev) {
      if (ev.phase != TickEvent.Phase.END || !(ev.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) {
         return;
      }
      Inventory inv = p.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         regler(inv.getItem(i));
      }
      regler(p.containerMenu.getCarried());
   }

   private static void regler(ItemStack st) {
      if (st.isEmpty() || !(st.getItem() instanceof IGun gun)) {
         return;
      }
      CompoundTag tag = st.getTag();
      if (tag != null && tag.getBoolean(MARQUE)) {
         return;
      }
      ResourceLocation id = gun.getGunId(st);
      List<FireMode> modes = TimelessAPI.getCommonGunIndex(id).map(c -> c.getGunData().getFireModeSet()).orElse(null);
      if (modes == null) {
         return;
      }
      if (modes.contains(FireMode.AUTO) && gun.getFireMode(st) != FireMode.AUTO) {
         gun.setFireMode(st, FireMode.AUTO);
      }
      st.getOrCreateTag().putBoolean(MARQUE, true);
   }
}
