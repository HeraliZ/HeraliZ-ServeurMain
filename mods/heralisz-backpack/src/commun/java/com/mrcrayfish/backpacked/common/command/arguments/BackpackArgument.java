package com.mrcrayfish.backpacked.common.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mrcrayfish.backpacked.common.backpack.Backpack;
import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

public class BackpackArgument implements ArgumentType<Backpack> {
   public static BackpackArgument backpacks() {
      return new BackpackArgument();
   }

   public Backpack parse(StringReader reader) throws CommandSyntaxException {
      ResourceLocation id = ResourceLocation.read(reader);
      return BackpackManager.instance().getBackpacks().stream().filter(backpack -> backpack.getId().equals(id)).findFirst().orElse(null);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      BackpackManager.instance().getClientBackpacks().forEach(backpack -> builder.suggest(backpack.getId().toString()));
      return builder.buildFuture();
   }
}
