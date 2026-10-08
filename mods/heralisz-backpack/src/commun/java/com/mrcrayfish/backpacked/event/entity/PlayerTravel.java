package com.mrcrayfish.backpacked.event.entity;

import com.mrcrayfish.backpacked.common.MovementType;
import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.server.level.ServerPlayer;

public interface PlayerTravel extends IFrameworkEvent {
   void handle(ServerPlayer var1, double var2, MovementType var4);
}
