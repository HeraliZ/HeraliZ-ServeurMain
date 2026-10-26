package fr.heraliz.loot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commandes (op) des coffres T7 :
 * <ul><li>/heralizloot ouvrir &lt;x y z&gt; — ouvre le coffre comme un clic droit ;</li>
 * <li>/heralizloot reinitialiser [rayon] — remet à neuf les coffres autour de soi ;</li>
 * <li>/heralizloot apparaitre &lt;type&gt; &lt;x y z&gt; [présence en s] — fait apparaître un coffre temporaire ;</li>
 * <li>/heralizloot point ajouter &lt;nom&gt; &lt;type&gt; &lt;evenement|auto&gt; [présence s] [délai min s] [délai max s]
 * — point d'apparition là où l'on se tient, face avant dans la direction du regard ;</li>
 * <li>/heralizloot point declencher &lt;nom&gt; — le coffre du point apparaît (lecteur de carte, boss, plugin...) ;</li>
 * <li>/heralizloot point retirer &lt;nom&gt;, /heralizloot point liste.</li></ul>
 * Rotation du loot des conteneurs (zones, zone ... info|retirer, proche) : {@link CommandesRotation}.
 */
final class Commandes {
    private Commandes() {}

    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) -> SharedSuggestionProvider.suggest(
            TypeCoffre.TOUS.keySet().stream().map(n -> n.substring(3)), b);
    private static final SuggestionProvider<CommandSourceStack> POINTS = (c, b) -> SharedSuggestionProvider.suggest(
            java.util.stream.StreamSupport.stream(PointsT7.get(c.getSource().getLevel()).tous().spliterator(), false).map(p -> p.nom), b);

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(CommandesRotation.ajouter(Commands.literal("heralizloot").requires(s -> s.hasPermission(2)))
                .then(Commands.literal("ouvrir").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    if (p.level().getBlockEntity(pos) instanceof CoffreBlockEntityServeur be) {
                        be.ouvrir(p);
                        return 1;
                    }
                    c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.t7.absent", "Pas de coffre T7 ici"));
                    return 0;
                })))
                .then(ButinZombies.commandes())
                .then(Commands.literal("tables").executes(c -> {
                    // 24/10 : tables relues (config/heraliz_loot/coffres/ par-dessus celles du jar), sans redémarrer
                    int n = TablesCoffres.charger();
                    c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.tables",
                            "%s tables de loot relues.", String.valueOf(n)), true);
                    return n;
                }))
                .then(Commands.literal("reinitialiser").executes(c -> reinit(c.getSource(), 24))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(1, 128))
                                .executes(c -> reinit(c.getSource(), IntegerArgumentType.getInteger(c, "rayon")))))
                .then(Commands.literal("apparaitre").then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> apparaitre(c, -1))
                                .then(Commands.argument("presence", IntegerArgumentType.integer(5, 86400))
                                        .executes(c -> apparaitre(c, IntegerArgumentType.getInteger(c, "presence")))))))
                .then(Commands.literal("point")
                        .then(Commands.literal("ajouter").then(Commands.argument("nom", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                                        .then(mode("evenement", false)).then(mode("auto", true)))))
                        .then(Commands.literal("declencher").then(Commands.argument("nom", StringArgumentType.word()).suggests(POINTS)
                                .executes(c -> {
                                    int n = PointsT7.declencher(c.getSource().getLevel(), StringArgumentType.getString(c, "nom"));
                                    if (n == 0) c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.point.rien",
                                            "Rien n'apparaît (point inconnu, coffre déjà là ou place prise)"));
                                    return n;
                                })))
                        .then(Commands.literal("retirer").then(Commands.argument("nom", StringArgumentType.word()).suggests(POINTS)
                                .executes(c -> {
                                    PointsT7.Point p = PointsT7.get(c.getSource().getLevel()).retirer(StringArgumentType.getString(c, "nom"));
                                    if (p == null) {
                                        c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.point.inconnu", "Point inconnu"));
                                        return 0;
                                    }
                                    c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.point.retire", "Point %s retiré", p.nom), true);
                                    return 1;
                                })))
                        .then(Commands.literal("liste").executes(c -> liste(c.getSource())))));
    }

    /** Argument de mode : présence, délais (secondes) facultatifs. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mode(String nom, boolean auto) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> max = Commands.argument("delai_max", IntegerArgumentType.integer(10, 864000))
                .executes(c -> ajouter(c, auto, entier(c, "presence"), entier(c, "delai_min"), entier(c, "delai_max")));
        RequiredArgumentBuilder<CommandSourceStack, Integer> min = Commands.argument("delai_min", IntegerArgumentType.integer(10, 864000))
                .executes(c -> ajouter(c, auto, entier(c, "presence"), entier(c, "delai_min"), entier(c, "delai_min"))).then(max);
        RequiredArgumentBuilder<CommandSourceStack, Integer> pres = Commands.argument("presence", IntegerArgumentType.integer(5, 86400))
                .executes(c -> ajouter(c, auto, entier(c, "presence"), -1, -1)).then(min);
        return Commands.literal(nom).executes(c -> ajouter(c, auto, -1, -1, -1)).then(pres);
    }

    private static int entier(CommandContext<CommandSourceStack> c, String n) {
        return IntegerArgumentType.getInteger(c, n);
    }

    private static int apparaitre(CommandContext<CommandSourceStack> c, int presenceS) throws CommandSyntaxException {
        String type = PointsT7.type(StringArgumentType.getString(c, "type"));
        if (type == null) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.type_inconnu", "Type inconnu"));
            return 0;
        }
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
        Direction face = Direction.fromYRot(c.getSource().getRotation().y).getOpposite();
        int presence = presenceS < 0 ? PointsT7.presenceDefaut(type) : presenceS * 20;
        if (!PointsT7.apparaitre(c.getSource().getLevel(), pos, face, type, presence, "")) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.place_prise", "Place prise"));
            return 0;
        }
        return 1;
    }

    private static int ajouter(CommandContext<CommandSourceStack> c, boolean auto, int presenceS, int minS, int maxS) {
        String type = PointsT7.type(StringArgumentType.getString(c, "type"));
        if (type == null) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.type_inconnu", "Type inconnu"));
            return 0;
        }
        PointsT7.Point p = new PointsT7.Point();
        p.nom = StringArgumentType.getString(c, "nom");
        p.type = type;
        p.pos = BlockPos.containing(c.getSource().getPosition());
        p.face = Direction.fromYRot(c.getSource().getRotation().y);
        p.auto = auto;
        p.presence = presenceS < 0 ? PointsT7.presenceDefaut(type) : presenceS * 20;
        p.delaiMin = (minS < 0 ? 1800 : minS) * 20;
        p.delaiMax = Math.max(p.delaiMin, (maxS < 0 ? 3600 : maxS) * 20);
        ServerLevel l = c.getSource().getLevel();
        if (!PointsT7.get(l).ajouter(p)) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.point.existe", "Ce nom de point existe déjà"));
            return 0;
        }
        c.getSource().sendSuccess(() -> auto
                ? Component.translatableWithFallback("heraliz_loot.cmd.point.ajoute_auto",
                        "Point %1$s (%2$s, auto, présence %3$s s, réapparition %4$s-%5$s s)",
                        p.nom, type.substring(3), p.presence / 20, p.delaiMin / 20, p.delaiMax / 20)
                : Component.translatableWithFallback("heraliz_loot.cmd.point.ajoute", "Point %1$s (%2$s, événement, présence %3$s s)",
                        p.nom, type.substring(3), p.presence / 20), true);
        return 1;
    }

    private static int liste(CommandSourceStack s) {
        int n = 0;
        for (PointsT7.Point p : PointsT7.get(s.getLevel()).tous()) {
            n++;
            Component mode = p.auto ? Component.translatableWithFallback("heraliz_loot.cmd.point.mode.auto", "auto")
                    : Component.translatableWithFallback("heraliz_loot.cmd.point.mode.evenement", "événement");
            s.sendSuccess(() -> p.present()
                    ? Component.translatableWithFallback("heraliz_loot.cmd.point.ligne_present", "%1$s : %2$s en %3$s, %4$s — présent",
                            p.nom, p.type.substring(3), p.pos.toShortString(), mode)
                    : Component.translatableWithFallback("heraliz_loot.cmd.point.ligne", "%1$s : %2$s en %3$s, %4$s",
                            p.nom, p.type.substring(3), p.pos.toShortString(), mode), false);
        }
        if (n == 0) s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.point.aucun", "Aucun point d'apparition"), false);
        return n;
    }

    private static int reinit(CommandSourceStack s, int r) {
        ServerLevel l = s.getLevel();
        BlockPos o = BlockPos.containing(s.getPosition());
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-r, -r, -r), o.offset(r, r, r))) {
            if (l.getBlockEntity(p) instanceof CoffreBlockEntityServeur be) {
                be.reinitialiser();
                n++;
            }
        }
        int fin = n;
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.reinitialise", "%s coffre(s) remis à neuf", fin), true);
        return n;
    }
}
