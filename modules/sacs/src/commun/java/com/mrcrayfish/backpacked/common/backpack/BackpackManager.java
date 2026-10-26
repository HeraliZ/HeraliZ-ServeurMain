package com.mrcrayfish.backpacked.common.backpack;

import com.google.common.collect.ImmutableList;
import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageSyncBackpacks;
import com.mrcrayfish.backpacked.network.message.MessageUnlockBackpack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class BackpackManager {
   private static BackpackManager instance;
   private static final ResourceLocation FALLBACK_BACKPACK = new ResourceLocation("backpacked", "standard");
   private static final ResourceLocation MOUNTAIN_GREEN = new ResourceLocation("backpacked", "mountain_green");
   private Map<ResourceLocation, Backpack> loadedBackpacks = new HashMap<>();
   private Map<ResourceLocation, Backpack> clientBackpacks = new HashMap<>();

   public static BackpackManager instance() {
      if (instance == null) {
         instance = new BackpackManager();
      }

      return instance;
   }

   private BackpackManager() {
   }

   public void updateBackpacks(Map<ResourceLocation, Backpack> map) {
      this.loadedBackpacks = map;
   }

   public void updateClientBackpacks(List<Backpack> backpacks) {
      this.clientBackpacks.clear();
      backpacks.forEach(backpack -> this.clientBackpacks.put(backpack.getId(), backpack));
   }

   @Nullable
   public Backpack getBackpack(ResourceLocation id) {
      return this.loadedBackpacks.get(id);
   }

   public List<Backpack> getBackpacks() {
      return ImmutableList.copyOf(this.loadedBackpacks.values());
   }

   @Nullable
   public Backpack getClientBackpack(ResourceLocation id) {
      return this.clientBackpacks.get(id);
   }

   @Nullable
   public Backpack getClientBackpackOrDefault(String id) {
      Backpack backpack = this.clientBackpacks.get(ResourceLocation.tryParse(id));
      if (backpack != null) {
         return backpack;
      }

      // HeraliZ : plus de System.out ici (appele a chaque image pour chaque joueur dont le sac n'a pas de modele a son
      // nom : la console et le journal du jeu en etaient remplis, et l'ecriture se faisait sur le fil du rendu).
      ResourceLocation fallback = FALLBACK_BACKPACK;
      if (id.contains("green")) {
         fallback = MOUNTAIN_GREEN;
      }

      return this.clientBackpacks.get(fallback);
   }

   public List<Backpack> getClientBackpacks() {
      return ImmutableList.copyOf(this.clientBackpacks.values());
   }

   public void unlockBackpack(ServerPlayer player, ResourceLocation id) {
      if (!(Boolean)Config.SERVER.backpack.unlockAllCosmetics.get()) {
         if (this.loadedBackpacks.containsKey(id)) {
            UnlockManager.getTracker(player).ifPresent(impl -> {
               if (impl.unlockBackpack(id)) {
                  Network.getPlay().sendToPlayer(() -> player, new MessageUnlockBackpack(id));
               }
            });
         }
      }
   }

   public MessageSyncBackpacks getSyncMessage() {
      return new MessageSyncBackpacks(this.getBackpacks());
   }
}
