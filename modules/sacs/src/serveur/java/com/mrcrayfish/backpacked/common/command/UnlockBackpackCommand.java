package com.mrcrayfish.backpacked.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrcrayfish.backpacked.common.backpack.Backpack;
import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.common.command.arguments.BackpackArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class UnlockBackpackCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("unlockbackpack").requires(source -> source.hasPermission(2)))
            .then(Commands.argument("backpack", BackpackArgument.backpacks()).executes(context -> {
               Backpack backpack = (Backpack)context.getArgument("backpack", Backpack.class);
               BackpackManager.instance().unlockBackpack(((CommandSourceStack)context.getSource()).getPlayerOrException(), backpack.getId());
               return 1;
            }))
      );
   }
}
