package fr.heraliz.reanimation.execution;

import fr.heraliz.reanimation.ReanimationConfig;
import fr.heraliz.reanimation.network.Network;
import fr.heraliz.reanimation.network.UnlocksPacket;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ExecutionUnlocks {
   private static final String KEY = "reanimation_executions";

   private ExecutionUnlocks() {
   }

   private static CompoundTag data(Player player) {
      CompoundTag root = player.getPersistentData();
      CompoundTag persisted = root.getCompound("PlayerPersisted");
      root.put("PlayerPersisted", persisted);
      CompoundTag mine = persisted.getCompound("reanimation_executions");
      persisted.put("reanimation_executions", mine);
      return mine;
   }

   public static boolean isFree(ExecutionType type) {
      if (type.group() != ExecutionType.Group.CLASSIC && (Boolean)ReanimationConfig.FREE_FINISHERS.get()) {
         return true;
      }

      for (String id : (List<String>)ReanimationConfig.FREE_EXECUTIONS.get()) {
         if (type.matches(id)) {
            return true;
         }
      }

      return false;
   }

   public static Set<ExecutionType> owned(Player player) {
      Set<ExecutionType> owned = new LinkedHashSet<>();
      ListTag list = data(player).getList("Unlocked", 8);

      for (ExecutionType type : ExecutionType.values()) {
         if (isFree(type)) {
            owned.add(type);
         }

         for (int i = 0; i < list.size(); i++) {
            if (type.matches(list.getString(i))) {
               owned.add(type);
            }
         }
      }

      return owned;
   }

   public static boolean owns(Player player, ExecutionType type) {
      return owned(player).contains(type);
   }

   public static boolean unlock(Player player, ExecutionType type) {
      CompoundTag data = data(player);
      ListTag list = data.getList("Unlocked", 8);

      for (int i = 0; i < list.size(); i++) {
         if (type.matches(list.getString(i))) {
            return false;
         }
      }

      list.add(StringTag.valueOf(type.id));
      data.put("Unlocked", list);
      return true;
   }

   public static boolean lock(Player player, ExecutionType type) {
      CompoundTag data = data(player);
      ListTag list = data.getList("Unlocked", 8);
      boolean removed = list.removeIf(tag -> type.matches(tag.getAsString()));
      data.put("Unlocked", list);
      return removed;
   }

   @Nullable
   public static ExecutionType selected(Player player) {
      Set<ExecutionType> owned = owned(player);
      ExecutionType chosen = ExecutionType.byId(data(player).getString("Selected"));
      if (chosen != null && owned.contains(chosen)) {
         return chosen;
      }

      for (ExecutionType type : owned) {
         if (type.selectable()) {
            return type;
         }
      }

      return owned.isEmpty() ? null : owned.iterator().next();
   }

   /** 1.8.1 : chaque couteau joue toujours sa propre execution (l'ancienne commande « execution switch » est retiree). */
   public static ExecutionType knifeExecution(Player player, ExecutionType knife) {
      return knife;
   }

   @Nullable
   public static ExecutionType switchKnife(Player player) {
      CompoundTag data = data(player);
      int v = (data.getInt("Knife") + 1) % (ExecutionType.KNIFE_VARIANTS.length + 1);
      data.putInt("Knife", v);
      return v == 0 ? null : ExecutionType.KNIFE_VARIANTS[v - 1];
   }

   public static void select(Player player, ExecutionType type) {
      data(player).putString("Selected", type.id);
   }

   public static void sync(ServerPlayer player, boolean open) {
      long owned = 0L;

      for (ExecutionType type : owned(player)) {
         owned |= 1L << type.ordinal();
      }

      ExecutionType selected = selected(player);
      Network.sendTo(player, new UnlocksPacket(owned, selected == null ? -1 : selected.ordinal(), open));
   }
}
