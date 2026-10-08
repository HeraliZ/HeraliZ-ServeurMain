package com.mrcrayfish.backpacked.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mrcrayfish.backpacked.common.command.UnlockBackpackCommand;
import com.mrcrayfish.framework.api.event.ServerEvents;
import com.mrcrayfish.framework.event.IServerEvent.Starting;
import net.minecraft.commands.CommandSourceStack;

public class ModCommands {
   public static void init() {
      ServerEvents.STARTING.register((Starting)server -> {
         CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
         UnlockBackpackCommand.register(dispatcher);
      });
   }
}
