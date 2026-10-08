package com.mrcrayfish.backpacked.core;

import com.mrcrayfish.backpacked.data.pickpocket.TraderPickpocketing;
import com.mrcrayfish.backpacked.data.unlock.UnlockTracker;
import com.mrcrayfish.framework.api.sync.SyncedClassKey;
import com.mrcrayfish.framework.api.sync.SyncedDataKey;
import com.mrcrayfish.framework.api.sync.SyncedDataKey.SyncMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

public class ModSyncedDataKeys {
   public static final SyncedDataKey<Player, UnlockTracker> UNLOCK_TRACKER = SyncedDataKey.builder(SyncedClassKey.PLAYER, UnlockTracker.SERIALIZER)
      .id(new ResourceLocation("backpacked", "unlock_tracker"))
      .defaultValueSupplier(UnlockTracker::new)
      .syncMode(SyncMode.SELF_ONLY)
      .saveToFile()
      .build();
   public static final SyncedDataKey<WanderingTrader, TraderPickpocketing> TRADER_PICKPOCKETING = SyncedDataKey.builder(
         SyncedClassKey.WANDERING_TRADER, TraderPickpocketing.SERIALIZER
      )
      .id(new ResourceLocation("backpacked", "trader_pickpocketing"))
      .defaultValueSupplier(TraderPickpocketing::new)
      .syncMode(SyncMode.TRACKING_ONLY)
      .saveToFile()
      .build();
}
