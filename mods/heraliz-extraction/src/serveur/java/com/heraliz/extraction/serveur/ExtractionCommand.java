package com.heraliz.extraction.serveur;

import com.heraliz.extraction.ExtractionConfig;
import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Commandes admin (OP niveau 2) pour les zones d'extraction fixes de la map :
 * <pre>
 * /extraction create &lt;nom&gt; [rayon] [secondes]  crée une zone à ta position (elle entre dans la rotation)
 * /extraction remove &lt;nom&gt;                     supprime une zone
 * /extraction list                              liste les zones (mode, ouverte ou non)
 * /extraction open &lt;nom&gt; [minutes]             ouvre une zone tout de suite
 * /extraction close &lt;nom&gt;                      ferme une zone tout de suite
 * /extraction mode &lt;nom&gt; rotation|permanent|closed
 * /extraction label &lt;nom&gt; &lt;nom affiché&gt;       nom montré aux joueurs (« Aérodrome nord »)
 * /extraction tp &lt;nom&gt;                         va voir une zone
 * /extraction rotation [next]                   état de la rotation ; next = ferme les zones ouvertes, d'autres ouvrent
 * </pre>
 * Pour tous les joueurs : {@code /extractions}, les extractions ouvertes, à quelle distance et dans quelle direction.
 */
@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExtractionCommand {
    private static final SimpleCommandExceptionType NAME_TAKEN =
            new SimpleCommandExceptionType(Component.translatableWithFallback("heraliz_extraction.commande.nom_pris",
                    "Une zone porte déjà ce nom."));
    private static final SimpleCommandExceptionType UNKNOWN_ZONE =
            new SimpleCommandExceptionType(Component.translatableWithFallback("heraliz_extraction.commande.zone_inconnue",
                    "Aucune zone avec ce nom."));
    private static final SimpleCommandExceptionType UNKNOWN_MODE =
            new SimpleCommandExceptionType(Component.translatableWithFallback("heraliz_extraction.commande.mode_inconnu",
                    "Mode inconnu : rotation, permanent ou closed."));
    private static final SimpleCommandExceptionType NO_DIMENSION =
            new SimpleCommandExceptionType(Component.translatableWithFallback("heraliz_extraction.commande.pas_de_dimension",
                    "La dimension de cette zone n'existe pas sur ce serveur."));

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
                                .executes(ctx -> create(ctx, ExtractionConfig.RADIUS.get().floatValue(), 0))
                                .then(Commands.argument("rayon", FloatArgumentType.floatArg(1.0F, 64.0F))
                                        .executes(ctx -> create(ctx, FloatArgumentType.getFloat(ctx, "rayon"), 0))
                                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 3600))
                                                .executes(ctx -> create(ctx, FloatArgumentType.getFloat(ctx, "rayon"),
                                                        IntegerArgumentType.getInteger(ctx, "secondes")))))))
                .then(Commands.literal("remove")
                        .then(zoneArgument().executes(ExtractionCommand::remove)))
                .then(Commands.literal("list")
                        .executes(ExtractionCommand::list))
                .then(Commands.literal("open")
                        .then(zoneArgument()
                                .executes(ctx -> open(ctx, Rotation.randomDuration()))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                        .executes(ctx -> open(ctx, (long) IntegerArgumentType.getInteger(ctx, "minutes") * Rotation.TICKS_PER_MINUTE)))))
                .then(Commands.literal("close")
                        .then(zoneArgument().executes(ExtractionCommand::close)))
                .then(Commands.literal("mode")
                        .then(zoneArgument()
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(ExtractionZones.Mode.values()).map(m -> m.id), builder))
                                        .executes(ExtractionCommand::mode))))
                .then(Commands.literal("label")
                        .then(zoneArgument()
                                .then(Commands.argument("texte", StringArgumentType.greedyString())
                                        .executes(ExtractionCommand::label))))
                .then(Commands.literal("tp")
                        .then(zoneArgument().executes(ExtractionCommand::tp)))
                .then(Commands.literal("rotation")
                        .executes(ExtractionCommand::rotation)
                        .then(Commands.literal("next")
                                .executes(ExtractionCommand::next))));

        dispatcher.register(Commands.literal("extractions")
                .executes(ExtractionCommand::forPlayers));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> zoneArgument() {
        return Commands.argument("nom", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ExtractionZones.get(ctx.getSource().getServer()).all().stream().map(ExtractionZones.Zone::name), builder));
    }

    private static ExtractionZones.Zone zone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ExtractionZones.Zone zone = ExtractionZones.get(ctx.getSource().getServer()).get(StringArgumentType.getString(ctx, "nom"));
        if (zone == null) {
            throw UNKNOWN_ZONE.create();
        }
        return zone;
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

        ExtractionZones.Zone zone = new ExtractionZones.Zone(name, level.dimension().location(), pos, radius, seconds);
        zones.add(zone);
        Rotation.place(level, zone);

        String time = ExtractionTerminalItem.formatTime(zone.holdSeconds());
        source.sendSuccess(() -> (seconds == 0
                ? Component.translatableWithFallback("heraliz_extraction.commande.creee_reglage",
                "Zone d'extraction « %s » créée : rayon %s blocs, %s à tenir (réglage zones.durationSeconds). Elle entre dans la rotation ; /extraction label %s <nom affiché> pour la nommer.",
                name, String.valueOf(radius), time, name)
                : Component.translatableWithFallback("heraliz_extraction.commande.creee",
                "Zone d'extraction « %s » créée : rayon %s blocs, %s à tenir. Elle entre dans la rotation ; /extraction label %s <nom affiché> pour la nommer.",
                name, String.valueOf(radius), time, name)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ExtractionZones.Zone zone = zone(ctx);
        String name = zone.name();
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        boolean wasOpen = zones.isOpen(zone, Rotation.now(source.getServer()));
        zones.remove(name);

        // si la zone est chargée, on la retire tout de suite ; sinon elle se supprimera au chargement
        ServerLevel level = Rotation.level(source.getServer(), zone);
        if (level != null) {
            AABB around = new AABB(zone.pos(), zone.pos()).inflate(2.0D);
            for (ExtractionTerminalEntityServeur entity : level.getEntitiesOfClass(ExtractionTerminalEntityServeur.class, around,
                    e -> e.isFixed() && name.equals(e.getZoneName()))) {
                entity.discard();
            }
        }
        if (wasOpen) {
            Rotation.sync(source.getServer());
        }
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.supprimee",
                "Zone d'extraction « %s » supprimée.", name).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ExtractionZones zones = ExtractionZones.get(server);
        if (zones.all().isEmpty()) {
            source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.liste.vide",
                    "Aucune zone d'extraction fixe.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        long now = Rotation.now(server);
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.liste.titre",
                "Zones d'extraction (%s) :", String.valueOf(zones.all().size())).withStyle(ChatFormatting.GOLD), false);
        for (ExtractionZones.Zone zone : zones.all()) {
            MutableComponent line = Component.literal(" ").append(Component.translatableWithFallback(
                            "heraliz_extraction.commande.liste.ligne", "- %s « %s » : %s %s %s %s, rayon %s, %s à tenir, %s —",
                            zone.name(), zone.displayName(), zone.dimension().toString(),
                            String.valueOf((int) Math.floor(zone.pos().x)), String.valueOf((int) Math.floor(zone.pos().y)),
                            String.valueOf((int) Math.floor(zone.pos().z)), String.format(Locale.ROOT, "%.1f", zone.radius()),
                            ExtractionTerminalItem.formatTime(zone.holdSeconds()), zone.mode().text()))
                    .append(" ").withStyle(ChatFormatting.GRAY);
            line.append(state(zones, zone, now));
            source.sendSuccess(() -> line, false);
        }
        return zones.all().size();
    }

    /** « ouverte (12 min) », « toujours ouverte », « fermée ». */
    private static Component state(ExtractionZones zones, ExtractionZones.Zone zone, long now) {
        if (!zones.isOpen(zone, now)) {
            return Component.translatableWithFallback("heraliz_extraction.etat.fermee", "fermée").withStyle(ChatFormatting.DARK_GRAY);
        }
        long left = zones.remainingTicks(zone, now);
        return (left < 0L ? Component.translatableWithFallback("heraliz_extraction.etat.toujours_ouverte", "toujours ouverte")
                : Component.translatableWithFallback("heraliz_extraction.etat.ouverte", "ouverte (%s)", Rotation.formatMinutes(left)))
                .withStyle(ChatFormatting.GREEN);
    }

    private static int open(CommandContext<CommandSourceStack> ctx, long durationTicks) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ExtractionZones.Zone zone = zone(ctx);
        if (zone.mode() == ExtractionZones.Mode.PERMANENT) {
            source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.deja_permanente",
                    "« %s » est permanente : elle est déjà toujours ouverte.", zone.name()).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        Rotation.open(source.getServer(), zone, durationTicks);
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.ouverte",
                "Zone « %s » ouverte pour %s.", zone.name(), Rotation.formatMinutes(durationTicks)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int close(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ExtractionZones.Zone zone = zone(ctx);
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        if (zone.mode() == ExtractionZones.Mode.PERMANENT) {
            source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.permanente_fermer",
                    "« %s » est permanente : /extraction mode %s rotation ou closed d'abord.", zone.name(), zone.name())
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        if (!zones.isOpen(zone, Rotation.now(source.getServer()))) {
            source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.deja_fermee",
                    "« %s » est déjà fermée.", zone.name()).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        Rotation.close(source.getServer(), zone);
        source.sendSuccess(() -> (zone.mode() == ExtractionZones.Mode.ROTATION && ExtractionConfig.ROTATION_ENABLED.get()
                ? Component.translatableWithFallback("heraliz_extraction.commande.fermee_rotation",
                "Zone « %s » fermée : la rotation en ouvre une autre.", zone.name())
                : Component.translatableWithFallback("heraliz_extraction.commande.fermee", "Zone « %s » fermée.", zone.name()))
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ExtractionZones.Zone zone = zone(ctx);
        String id = StringArgumentType.getString(ctx, "mode");
        ExtractionZones.Mode mode = Arrays.stream(ExtractionZones.Mode.values()).filter(m -> m.id.equals(id)).findFirst()
                .orElseThrow(UNKNOWN_MODE::create);
        ExtractionZones zones = ExtractionZones.get(server);
        long now = Rotation.now(server);
        boolean wasOpen = zones.isOpen(zone, now);
        ExtractionZones.Zone changed = zone.withMode(mode);
        zones.add(changed);
        if (mode == ExtractionZones.Mode.PERMANENT && zones.openUntil(zone.name()) > 0L) {
            zones.close(zone.name(), now); // plus de minuterie : elle est ouverte pour de bon
        } else if (mode == ExtractionZones.Mode.CLOSED && wasOpen) {
            zones.close(zone.name(), now);
        }
        if (wasOpen != zones.isOpen(changed, now)) {
            Rotation.sync(server);
        }
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.mode", "Zone « %s » : %s.",
                zone.name(), mode.text()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int label(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ExtractionZones.Zone zone = zone(ctx);
        String text = StringArgumentType.getString(ctx, "texte").trim();
        if (text.length() > 48) {
            text = text.substring(0, 48);
        }
        ExtractionZones zones = ExtractionZones.get(source.getServer());
        zones.add(zone.withLabel(text));
        if (zones.isOpen(zone, Rotation.now(source.getServer()))) {
            Rotation.sync(source.getServer());
        }
        String shown = text;
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.nommee", "Zone « %s » affichée « %s ».",
                zone.name(), shown).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ExtractionZones.Zone zone = zone(ctx);
        ServerLevel level = Rotation.level(source.getServer(), zone);
        if (level == null) {
            throw NO_DIMENSION.create();
        }
        player.teleportTo(level, zone.pos().x, zone.pos().y, zone.pos().z, player.getYRot(), player.getXRot());
        return 1;
    }

    private static int rotation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ExtractionZones zones = ExtractionZones.get(server);
        long now = Rotation.now(server);
        long pool = zones.all().stream().filter(z -> z.mode() == ExtractionZones.Mode.ROTATION).count();
        boolean on = ExtractionConfig.ROTATION_ENABLED.get();
        source.sendSuccess(() -> (on
                ? Component.translatableWithFallback("heraliz_extraction.commande.rotation",
                "Rotation : %s zone(s) ouvertes à la fois parmi %s, %s à %s min chacune.",
                ExtractionConfig.ROTATION_OPEN.get() + (ExtractionConfig.ROTATION_OPEN_MAX.get() > ExtractionConfig.ROTATION_OPEN.get()
                        ? "-" + ExtractionConfig.ROTATION_OPEN_MAX.get() : ""), String.valueOf(pool),
                String.valueOf(ExtractionConfig.ROTATION_MIN_MINUTES.get()), String.valueOf(ExtractionConfig.ROTATION_MAX_MINUTES.get()))
                : Component.translatableWithFallback("heraliz_extraction.commande.rotation_coupee",
                "Rotation coupée (rotation.enabled = false) : seules les ouvertures à la main et les zones permanentes comptent."))
                .withStyle(ChatFormatting.GOLD), false);
        int open = 0;
        for (ExtractionZones.Zone zone : zones.all()) {
            if (zones.isOpen(zone, now)) {
                open++;
                MutableComponent line = Component.literal(" - " + zone.displayName() + " (" + zone.name() + ") : ").withStyle(ChatFormatting.GRAY);
                line.append(state(zones, zone, now));
                source.sendSuccess(() -> line, false);
            }
        }
        if (open == 0) {
            source.sendSuccess(() -> Component.literal(" ").append(Component.translatableWithFallback(
                    "heraliz_extraction.commande.aucune_ouverte", "Aucune zone ouverte.")).withStyle(ChatFormatting.GRAY), false);
        }
        return open;
    }

    private static int next(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        ExtractionZones zones = ExtractionZones.get(server);
        long now = Rotation.now(server);
        List<ExtractionZones.Zone> open = new ArrayList<>();
        for (ExtractionZones.Zone zone : zones.all()) {
            if (zone.mode() == ExtractionZones.Mode.ROTATION && zones.isOpen(zone, now)) {
                open.add(zone);
            }
        }
        for (ExtractionZones.Zone zone : open) {
            Rotation.close(server, zone);
        }
        Rotation.tick(server);
        source.sendSuccess(() -> Component.translatableWithFallback("heraliz_extraction.commande.suivantes",
                "%s zone(s) fermée(s), la rotation a ouvert les suivantes.", String.valueOf(open.size()))
                .withStyle(ChatFormatting.GREEN), true);
        return open.size();
    }

    /** /extractions : les extractions ouvertes de la dimension du joueur, distance et direction. */
    private static int forPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ExtractionZones zones = ExtractionZones.get(server);
        long now = Rotation.now(server);
        List<ExtractionZones.Zone> open = new ArrayList<>();
        for (ExtractionZones.Zone zone : zones.all()) {
            if (zones.isOpen(zone, now) && zone.dimension().equals(player.level().dimension().location())) {
                open.add(zone);
            }
        }
        open.sort((a, b) -> Double.compare(a.pos().distanceToSqr(player.position()), b.pos().distanceToSqr(player.position())));
        if (open.isEmpty()) {
            player.sendSystemMessage(Component.translatableWithFallback("heraliz_extraction.joueurs.aucune",
                    "Aucune extraction ouverte pour l'instant.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.translatableWithFallback("heraliz_extraction.joueurs.titre",
                "Extractions ouvertes (%s) :", String.valueOf(open.size())).withStyle(ChatFormatting.GOLD));
        for (ExtractionZones.Zone zone : open) {
            long left = zones.remainingTicks(zone, now);
            player.sendSystemMessage(Component.literal(" • ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(zone.displayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" — ").append(Rotation.where(player, zone)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" · ")
                            .append(left < 0L
                                    ? Component.translatableWithFallback("heraliz_extraction.etat.toujours_ouverte", "toujours ouverte")
                                    : Component.translatableWithFallback("heraliz_extraction.joueurs.ferme_dans", "ferme dans %s",
                                    Rotation.formatMinutes(left)))
                            .append(" · ")
                            .append(Component.translatableWithFallback("heraliz_extraction.joueurs.a_tenir", "%s s à tenir",
                                    String.valueOf(zone.holdSeconds())))
                            .withStyle(ChatFormatting.GRAY)));
        }
        return open.size();
    }
}
