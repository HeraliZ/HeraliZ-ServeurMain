package fr.heraliz.reanimation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.heraliz.reanimation.core.DownedManagerServeur;
import fr.heraliz.reanimation.entity.BotEntity;
import fr.heraliz.reanimation.entity.BotEntityServeur;
import fr.heraliz.reanimation.execution.ExecutionType;
import fr.heraliz.reanimation.execution.ExecutionUnlocks;
import fr.heraliz.reanimation.registry.ModEntities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.phys.Vec3;

public final class ReanimationCommand {
   private ReanimationCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> bot = (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal("bot")
            .executes(ctx -> spawnBot((CommandSourceStack)ctx.getSource(), BotEntity.Mode.PASSIVE)))
         .then(Commands.literal("clear").executes(ctx -> clearBots((CommandSourceStack)ctx.getSource())));

      for (BotEntity.Mode mode : BotEntity.Mode.values()) {
         bot.then(Commands.literal(mode.id()).executes(ctx -> spawnBot((CommandSourceStack)ctx.getSource(), mode)));
      }

      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("reanimation").then(execution()))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("down").requires(source -> source.hasPermission(2)))
                           .executes(ctx -> down((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getEntityOrException()))))
                        .then(
                           Commands.argument("targets", EntityArgument.entities())
                              .executes(ctx -> down((CommandSourceStack)ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("revive").requires(source -> source.hasPermission(2)))
                        .executes(ctx -> revive((CommandSourceStack)ctx.getSource(), List.of(((CommandSourceStack)ctx.getSource()).getEntityOrException()))))
                     .then(
                        Commands.argument("targets", EntityArgument.entities())
                           .executes(ctx -> revive((CommandSourceStack)ctx.getSource(), EntityArgument.getEntities(ctx, "targets")))
                     )
               ))
            .then(bot.requires(source -> source.hasPermission(2)))
      );
   }

   private static LiteralArgumentBuilder<CommandSourceStack> execution() {
      return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                           "execution"
                        )
                        .then(Commands.literal("menu").executes(ctx -> {
                           ExecutionUnlocks.sync(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), true);
                           return 1;
                        })))
                  )
                  .then(Commands.literal("list").executes(ctx -> listExecutions((CommandSourceStack)ctx.getSource()))))
               .then(
                  Commands.literal("select")
                     .then(
                        Commands.argument("id", StringArgumentType.word())
                           .suggests(ReanimationCommand::suggestIds)
                           .executes(ctx -> selectExecution((CommandSourceStack)ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)Commands.literal("unlock").requires(source -> source.hasPermission(2)))
                  .then(
                     Commands.argument("targets", EntityArgument.players())
                        .then(
                           Commands.argument("id", StringArgumentType.word())
                              .suggests(ReanimationCommand::suggestIds)
                              .executes(
                                 ctx -> unlock(
                                    (CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "id"), true
                                 )
                              )
                        )
                  )
            ))
         .then(
            ((LiteralArgumentBuilder)Commands.literal("lock").requires(source -> source.hasPermission(2)))
               .then(
                  Commands.argument("targets", EntityArgument.players())
                     .then(
                        Commands.argument("id", StringArgumentType.word())
                           .suggests(ReanimationCommand::suggestIds)
                           .executes(
                              ctx -> unlock(
                                 (CommandSourceStack)ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "id"), false
                              )
                           )
                     )
               )
         );
   }

   /**
    * Reponse d'une commande. Un joueur la recoit toujours en message direct ({@code sendSystemMessage}) : le monde a
    * {@code sendCommandFeedback=false}, qui masque {@code sendSuccess}. Console et blocs de commande : {@code sendSuccess}.
    */
   private static void reply(CommandSourceStack source, Supplier<Component> message, boolean broadcastToAdmins) {
      if (source.getEntity() instanceof ServerPlayer player) {
         player.sendSystemMessage(message.get());
      } else {
         source.sendSuccess(message, broadcastToAdmins);
      }
   }

   private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
      return SharedSuggestionProvider.suggest(Arrays.stream(ExecutionType.values()).map(t -> t.id), builder);
   }

   private static int listExecutions(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      Set<ExecutionType> owned = ExecutionUnlocks.owned(player);
      ExecutionType selected = ExecutionUnlocks.selected(player);
      reply(source, () -> Component.translatable("commands.reanimation.execution.list.header"), false);

      for (ExecutionType type : ExecutionType.values()) {
         String key = type == selected
            ? "commands.reanimation.execution.list.selected"
            : (owned.contains(type) ? "commands.reanimation.execution.list.owned" : "commands.reanimation.execution.list.locked");
         reply(source, () -> Component.translatable(key, new Object[]{type.displayName(), type.id}), false);
      }

      return owned.size();
   }

   private static int selectExecution(CommandSourceStack source, String id) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      ExecutionType type = ExecutionType.byId(id);
      if (type == null) {
         source.sendFailure(Component.translatable("commands.reanimation.execution.unknown", new Object[]{id}));
         return 0;
      } else if (!ExecutionUnlocks.owns(player, type)) {
         source.sendFailure(Component.translatable("commands.reanimation.execution.not_owned", new Object[]{type.displayName()}));
         return 0;
      } else {
         ExecutionUnlocks.select(player, type);
         ExecutionUnlocks.sync(player, false);
         reply(source, () -> Component.translatable("commands.reanimation.execution.selected", new Object[]{type.displayName()}), false);
         return 1;
      }
   }

   private static int unlock(CommandSourceStack source, Collection<ServerPlayer> players, String id, boolean unlock) {
      ExecutionType type = ExecutionType.byId(id);
      if (type == null) {
         source.sendFailure(Component.translatable("commands.reanimation.execution.unknown", new Object[]{id}));
         return 0;
      }

      int count = 0;

      for (ServerPlayer player : players) {
         if (unlock ? ExecutionUnlocks.unlock(player, type) : ExecutionUnlocks.lock(player, type)) {
            count++;
         }

         if (unlock) {
            player.sendSystemMessage(
               Component.translatable("message.reanimation.execution_unlocked", new Object[]{type.displayName(), Component.keybind("key.reanimation.menu")})
            );
         }

         ExecutionUnlocks.sync(player, false);
      }

      int n = count;
      reply(source, () -> Component.translatable(
            unlock ? "commands.reanimation.execution.unlocked" : "commands.reanimation.execution.locked", new Object[]{type.displayName(), n}
         ),
         true
      );
      return count;
   }

   private static int down(CommandSourceStack source, Collection<? extends Entity> targets) {
      int count = 0;

      for (Entity e : targets) {
         if (e instanceof LivingEntity living && living.isAlive() && DownedManagerServeur.canBeDowned(living) && !DownedManagerServeur.isDowned(living)) {
            DownedManagerServeur.down(living, (DamageSource)null);
            count++;
         }
      }

      int n = count;
      reply(source, () -> Component.translatable("commands.reanimation.down", new Object[]{n}), true);
      return count;
   }

   private static int revive(CommandSourceStack source, Collection<? extends Entity> targets) {
      int count = 0;

      for (Entity e : targets) {
         if (e instanceof LivingEntity living && DownedManagerServeur.reviveNow(living)) {
            count++;
         }
      }

      int n = count;
      reply(source, () -> Component.translatable("commands.reanimation.revive", new Object[]{n}), true);
      return count;
   }

   private static int spawnBot(CommandSourceStack source, BotEntity.Mode mode) throws CommandSyntaxException {
      ServerLevel level = source.getLevel();
      // créé par l'usine (ModEntities.BOT_USINE) : la version serveur, avec son IA et son sac
      if (!(((EntityType)ModEntities.BOT.get()).create(level) instanceof BotEntityServeur bot)) {
         return 0;
      }

      float yaw = source.getRotation().y;
      Vec3 at = source.getPosition().add(Vec3.directionFromRotation(0.0F, yaw).scale(2.5));
      bot.moveTo(at.x, at.y, at.z, yaw + 180.0F, 0.0F);
      bot.setYHeadRot(yaw + 180.0F);
      bot.setYBodyRot(yaw + 180.0F);
      bot.finalizeSpawn(level, level.getCurrentDifficultyAt(bot.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData)null, (CompoundTag)null);
      bot.setup(bot.skin(), mode);
      bot.giveKit();
      level.addFreshEntity(bot);
      reply(source, () -> Component.translatable("commands.reanimation.bot", new Object[]{bot.getDisplayName()}), true);
      return 1;
   }

   private static int clearBots(CommandSourceStack source) {
      List<Entity> bots = new ArrayList<>();

      for (Entity e : source.getLevel().getAllEntities()) {
         if (e instanceof BotEntity) {
            bots.add(e);
         }
      }

      bots.forEach(Entity::discard);
      int n = bots.size();
      reply(source, () -> Component.translatable("commands.reanimation.bot.clear", new Object[]{n}), true);
      return n;
   }
}
