package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commandes admin (OP niveau 2) pour les zones d'extraction fixes de la map :
 * <pre>
 * /extraction create &lt;nom&gt; [rayon] [secondes]  crée une zone permanente à ta position
 * /extraction remove &lt;nom&gt;                     supprime une zone
 * /extraction list                              liste les zones
 * </pre>
 */
@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtractionCommand {
    private static final SimpleCommandExceptionType NAME_TAKEN =
            new SimpleCommandExceptionType(Component.literal("Une zone porte déjà ce nom."));
    private static final SimpleCommandExceptionType UNKNOWN_ZONE =
            new SimpleCommandExceptionType(Component.literal("Aucune zone avec ce nom."));

    private ExtractionCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("extraction")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("nom", StringArgumentType.word())
                                .executes(ctx -> create(ctx, ExtractionConfig.RADIUS.get().floatValue(), ExtractionConfig.DURATION_SECONDS.get()))
                                .then(Commands.argument("rayon", FloatArgumentType.floatArg(1.0F, 64.0F))
                                        .executes(ctx -> create(ctx, FloatArgumentType.getFloat(ctx, "rayon"), ExtractionConfig.DURATION_SECONDS.get()))
                                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 3600))
                                                .executes(ctx -> create(ctx, FloatArgumentType.getFloat(ctx, "rayon"),
                                                        IntegerArgumentType.getInteger(ctx, "secondes")))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("nom", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ExtractionZones.get(ctx.getSource().getServer()).all().stream().map(ExtractionZones.Zone::name), builder))
                                .executes(ExtractionCommand::remove)))
                .then(Commands.literal("list")
                        .executes(ExtractionCommand::list)));
    }

    private static int create(CommandContext<CommandSourceStack> ctx, float radius, int seconds) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "nom");
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        if (zones.contains(name)) {
            throw NAME_TAKEN.create();
        }
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        ExtractionTerminalEntityServeur zone = (ExtractionTerminalEntityServeur) ModRegistry.EXTRACTION_TERMINAL_ENTITY.get().create(level);
        zone.setupFixed(name, seconds * 20, radius);
        zone.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(zone);
        zones.add(new ExtractionZones.Zone(name, level.dimension().location(), pos, radius, seconds));

        source.sendSuccess(() -> Component.literal("Zone d'extraction « " + name + " » créée : rayon " + radius
                + " blocs, " + ExtractionTerminalItem.formatTime(seconds) + " à tenir.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "nom");
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        ExtractionZones.Zone zone = zones.get(name);
        if (zone == null) {
            throw UNKNOWN_ZONE.create();
        }
        zones.remove(name);

        // si la zone est chargée, on la retire tout de suite ; sinon elle se supprimera au chargement
        ServerLevel level = source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, zone.dimension()));
        if (level != null) {
            AABB around = new AABB(zone.pos(), zone.pos()).inflate(2.0D);
            for (ExtractionTerminalEntityServeur entity : level.getEntitiesOfClass(ExtractionTerminalEntityServeur.class, around,
                    e -> e.isFixed() && name.equals(e.getZoneName()))) {
                entity.discard();
            }
        }
        source.sendSuccess(() -> Component.literal("Zone d'extraction « " + name + " » supprimée.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        if (zones.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucune zone d'extraction fixe.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Zones d'extraction (" + zones.all().size() + ") :").withStyle(ChatFormatting.GOLD), false);
        for (ExtractionZones.Zone zone : zones.all()) {
            source.sendSuccess(() -> Component.literal(String.format(" - %s : %s %d %d %d, rayon %.1f, %s",
                    zone.name(), zone.dimension(), (int) Math.floor(zone.pos().x), (int) Math.floor(zone.pos().y),
                    (int) Math.floor(zone.pos().z), zone.radius(), ExtractionTerminalItem.formatTime(zone.durationSeconds())))
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return zones.all().size();
    }
}
