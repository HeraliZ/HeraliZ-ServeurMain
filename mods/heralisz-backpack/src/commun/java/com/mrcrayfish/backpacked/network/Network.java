package com.mrcrayfish.backpacked.network;

import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.network.message.MessageBackpackCosmetics;
import com.mrcrayfish.backpacked.network.message.MessageEntityBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import com.mrcrayfish.backpacked.network.message.MessageOpenCustomisation;
import com.mrcrayfish.backpacked.network.message.MessageRequestCustomisation;
import com.mrcrayfish.backpacked.network.message.MessageSyncBackpacks;
import com.mrcrayfish.backpacked.network.message.MessageSyncUnlockTracker;
import com.mrcrayfish.backpacked.network.message.MessageSyncVillagerBackpack;
import com.mrcrayfish.backpacked.network.message.MessageUnlockBackpack;
import com.mrcrayfish.framework.api.FrameworkAPI;
import com.mrcrayfish.framework.api.network.FrameworkNetwork;
import com.mrcrayfish.framework.api.network.MessageDirection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;

public class Network {
   public static final FrameworkNetwork PLAY = FrameworkAPI.createNetworkBuilder(new ResourceLocation("backpacked", "play"), 1)
      .registerHandshakeMessage(MessageSyncBackpacks.class, local -> List.of(Pair.of("Backpacks", BackpackManager.instance().getSyncMessage())))
      .registerPlayMessage(MessageOpenBackpack.class, MessageDirection.PLAY_SERVER_BOUND)
      .registerPlayMessage(MessageEntityBackpack.class, MessageDirection.PLAY_SERVER_BOUND)
      .registerPlayMessage(MessageBackpackCosmetics.class, MessageDirection.PLAY_SERVER_BOUND)
      .registerPlayMessage(MessageSyncUnlockTracker.class, MessageDirection.PLAY_CLIENT_BOUND)
      .registerPlayMessage(MessageUnlockBackpack.class, MessageDirection.PLAY_CLIENT_BOUND)
      .registerPlayMessage(MessageRequestCustomisation.class, MessageDirection.PLAY_SERVER_BOUND)
      .registerPlayMessage(MessageOpenCustomisation.class, MessageDirection.PLAY_CLIENT_BOUND)
      .registerPlayMessage(MessageSyncVillagerBackpack.class, MessageDirection.PLAY_CLIENT_BOUND)
      .build();

   public static void init() {
   }

   public static FrameworkNetwork getPlay() {
      return PLAY;
   }
}
