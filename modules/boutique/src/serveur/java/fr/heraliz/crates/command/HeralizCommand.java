package fr.heraliz.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.heraliz.crates.cles.ComptesCles;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.CrateLoot;
import fr.heraliz.crates.loot.LootEntry;
import fr.heraliz.crates.loot.LootManager;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.menu.PreviewMenu;
import fr.heraliz.crates.opening.CaseOpening;
import fr.heraliz.crates.opening.LootEditing;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * /heraliz ...
 *   preview <coffre>                          (tout le monde)
 *   givekey <joueurs> <coffre> [nombre]       (op) clés ajoutées au compte du joueur (25/10 : plus d'objet)
 *   keyall <coffre> [nombre]                  (op)
 *   cles voir <joueur>                        (op) clés du compte
 *   cles retirer <joueurs> <coffre> <nombre>  (op) correction du staff
 *   givecrate <joueurs> <coffre> [nombre]     (op)
 *   reload                                    (op)
 *   loot add <coffre> <poids> [rarete]        (op) ajoute l'objet tenu en main
 *   loot list <coffre>                        (op)
 *   loot remove <coffre> <index>              (op)
 *   edit <coffre>                             (op) editeur de butin (objets, raretes, chances)
 *   editor                                    (op) donne l'outil d'edition des caisses
 *   interface <joueurs> <coffre>              (op) ouvre l'interface d'ouverture sans caisse (PNJ, menus...)
 */
public final class HeralizCommand {
    private HeralizCommand() {
    }

    private static final DynamicCommandExceptionType UNKNOWN_CRATE = new DynamicCommandExceptionType(
            id -> Component.translatable("command.heralizcrates.unknown_crate", id));

    private static final SuggestionProvider<CommandSourceStack> CRATES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(CrateType.values()).map(t -> t.id), builder);

    private static final SuggestionProvider<CommandSourceStack> RARITIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(LootRarity.values()).map(r -> r.id), builder);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("heraliz")
                .then(Commands.literal("preview")
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(c -> {
                                    PreviewMenu.open(c.getSource().getPlayerOrException(), crate(c));
                                    return 1;
                                })))
                .then(Commands.literal("givekey").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(c -> giveKeys(c, EntityArgument.getPlayers(c, "targets"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                                .executes(c -> giveKeys(c, EntityArgument.getPlayers(c, "targets"),
                                                        IntegerArgumentType.getInteger(c, "amount")))))))
                .then(Commands.literal("keyall").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(c -> giveKeys(c, c.getSource().getServer().getPlayerList().getPlayers(), 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                        .executes(c -> giveKeys(c, c.getSource().getServer().getPlayerList().getPlayers(),
                                                IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("cles").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("voir")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> clesVoir(c, EntityArgument.getPlayer(c, "target")))))
                        .then(Commands.literal("retirer")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 6400))
                                                        .executes(c -> clesRetirer(c, EntityArgument.getPlayers(c, "targets"),
                                                                IntegerArgumentType.getInteger(c, "amount"))))))))
                .then(Commands.literal("givecrate").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(c -> giveCrates(c, EntityArgument.getPlayers(c, "targets"), 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> giveCrates(c, EntityArgument.getPlayers(c, "targets"),
                                                        IntegerArgumentType.getInteger(c, "amount")))))))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2))
                        .executes(HeralizCommand::reload))
                .then(Commands.literal("edit").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                .executes(c -> {
                                    LootEditing.open(c.getSource().getPlayerOrException(), crate(c));
                                    return 1;
                                })))
                .then(Commands.literal("editor").requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            giveStacks(c.getSource().getPlayerOrException(), new ItemStack(ModRegistry.CRATE_EDITOR.get()), 1);
                            return 1;
                        }))
                .then(Commands.literal("interface").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(c -> {
                                            CrateType type = crate(c);
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
                                            for (ServerPlayer p : players) CaseOpening.openScreen(p, type, null);
                                            return players.size();
                                        }))))
                .then(Commands.literal("loot").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .then(Commands.argument("weight", IntegerArgumentType.integer(1, 100000))
                                                .executes(c -> lootAdd(c, "common"))
                                                .then(Commands.argument("rarity", StringArgumentType.word()).suggests(RARITIES)
                                                        .executes(c -> lootAdd(c, StringArgumentType.getString(c, "rarity")))))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(HeralizCommand::lootList)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(HeralizCommand::lootRemove))))
                        // butin changé en jeu effacé : la caisse reprend le fichier de config/ (celui du dépôt)
                        .then(Commands.literal("fichier")
                                .then(Commands.argument("crate", StringArgumentType.word()).suggests(CRATES)
                                        .executes(c -> {
                                            CrateType type = crate(c);
                                            boolean ok = LootManager.revenirAuFichier(type);
                                            c.getSource().sendSuccess(() -> ok
                                                    ? Component.translatableWithFallback("heralizcrates.commande.fichier",
                                                    "%s : butin changé en jeu effacé, retour au fichier du serveur.", type.crateName())
                                                    : Component.translatableWithFallback("heralizcrates.commande.fichier_rien",
                                                    "%s : aucun butin changé en jeu.", type.crateName()), true);
                                            return ok ? 1 : 0;
                                        })))));
    }

    private static CrateType crate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "crate");
        CrateType t = CrateType.byId(id);
        if (t == null) throw UNKNOWN_CRATE.create(id);
        return t;
    }

    private static int giveKeys(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int amount) throws CommandSyntaxException {
        CrateType type = crate(c);
        // 25/10 : plus d'objet ; les clés vont sur le compte du joueur (ComptesCles), écrit tout de suite. Même commande
        // qu'avant (le site, AzLink, la boutique, les caisses et les bons la jouent) ; rend le nombre de joueurs crédités
        // (0 = rien donné : la boutique rembourse)
        String source = "commande " + c.getSource().getTextName();
        int n = 0;
        for (ServerPlayer p : players) {
            if (ComptesCles.crediter(p.server, p.getUUID(), p.getGameProfile().getName(), type, amount, source)) {
                n++;
                p.displayClientMessage(Component.translatable("message.heralizcrates.key_received", amount, type.keyName())
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                c.getSource().sendFailure(Component.translatableWithFallback("command.heralizcrates.cles.refus",
                        "Clés NON données à %s : son compte de clés est indisponible (voir le journal du serveur).", p.getGameProfile().getName()));
            }
        }
        final int faits = n;
        if (faits > 0) c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.givekey", amount, type.keyName(), faits), true);
        return faits;
    }

    /** /heraliz cles voir <joueur> : les clés de son compte. */
    private static int clesVoir(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        java.util.Map<CrateType, Integer> m = ComptesCles.tout(p.getUUID());
        String nom = p.getGameProfile().getName();
        if (!ComptesCles.ouvert(p.getUUID())) {
            c.getSource().sendFailure(Component.translatableWithFallback("command.heralizcrates.cles.ferme",
                    "Compte de clés de %s fermé : fichier illisible (heraliz_crates/cles/), à réparer à la main.", nom));
            return 0;
        }
        if (m.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatableWithFallback("command.heralizcrates.cles.aucune", "%s n'a aucune clé.", nom), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("command.heralizcrates.cles.titre", "Clés de %s :", nom), false);
        m.forEach((t, n) -> c.getSource().sendSuccess(() -> Component.literal(" " + n + " × ").withStyle(ChatFormatting.WHITE).append(t.keyName()), false));
        return m.size();
    }

    /** /heraliz cles retirer <joueurs> <caisse> <nombre> : retire des clés du compte (correction du staff). */
    private static int clesRetirer(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int amount) throws CommandSyntaxException {
        CrateType type = crate(c);
        int n = 0;
        for (ServerPlayer p : players) {
            int a = Math.min(amount, ComptesCles.solde(p.getUUID(), type));
            if (a > 0 && ComptesCles.consommer(p.server, p.getUUID(), type, a, "retrait par " + c.getSource().getTextName())) {
                n++;
                final int pris = a;
                c.getSource().sendSuccess(() -> Component.translatableWithFallback("command.heralizcrates.cles.retirees", "%s × %s retirée(s) à %s.",
                        String.valueOf(pris), type.keyName(), p.getGameProfile().getName()), true);
            } else {
                c.getSource().sendFailure(Component.translatableWithFallback("command.heralizcrates.cles.rien", "%s n'a pas de %s à retirer.",
                        p.getGameProfile().getName(), type.keyName()));
            }
        }
        return n;
    }

    private static int giveCrates(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int amount) throws CommandSyntaxException {
        CrateType type = crate(c);
        Item item = ModRegistry.crateItem(type);
        for (ServerPlayer p : players) {
            giveStacks(p, new ItemStack(item), amount);
        }
        int n = players.size();
        c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.givecrate", amount, type.crateName(), n), true);
        return n;
    }

    private static void giveStacks(ServerPlayer p, ItemStack proto, int amount) {
        int max = proto.getMaxStackSize();
        while (amount > 0) {
            int k = Math.min(max, amount);
            ItemStack s = proto.copy();
            s.setCount(k);
            ItemHandlerHelper.giveItemToPlayer(p, s);
            amount -= k;
        }
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        int errors = LootManager.reload();
        if (errors == 0) {
            c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.reload_ok").withStyle(ChatFormatting.GREEN), true);
        } else {
            c.getSource().sendFailure(Component.translatable("command.heralizcrates.reload_errors", errors));
        }
        return 1;
    }

    private static int lootAdd(CommandContext<CommandSourceStack> c, String rarityId) throws CommandSyntaxException {
        CrateType type = crate(c);
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.heralizcrates.hold_item"));
            return 0;
        }
        LootEntry e = new LootEntry(String.valueOf(ForgeRegistries.ITEMS.getKey(held.getItem())), held.getCount(),
                IntegerArgumentType.getInteger(c, "weight"), LootRarity.byId(rarityId));
        if (held.hasTag()) {
            e.nbt = held.getTag().toString();
        }
        CrateLoot loot = LootManager.get(type);
        loot.entries.add(e);
        LootManager.save(type);
        Component name = held.getDisplayName();
        c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.loot_added", name, type.crateName(),
                String.format(Locale.ROOT, "%.2f%%", loot.chance(e))), true);
        return 1;
    }

    private static int lootList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CrateType type = crate(c);
        CrateLoot loot = LootManager.get(type);
        c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.loot_header", type.crateName(), loot.entries.size()), false);
        int i = 1;
        for (LootEntry e : loot.entries) {
            final int idx = i++;
            ItemStack s = e.displayStack();
            Component name = s.isEmpty() ? Component.literal(e.item).withStyle(ChatFormatting.RED) : s.getDisplayName();
            c.getSource().sendSuccess(() -> Component.literal(" " + idx + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(name).append(Component.literal(" x" + e.count + "  ").withStyle(ChatFormatting.WHITE))
                    .append(e.rarity.displayName())
                    .append(Component.literal("  ").append(Component.translatableWithFallback("heralizcrates.commande.poids", "(poids %s, %s)",
                                    String.valueOf(e.weight), String.format(Locale.ROOT, "%.2f%%", loot.chance(e))))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return loot.entries.size();
    }

    private static int lootRemove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CrateType type = crate(c);
        int index = IntegerArgumentType.getInteger(c, "index");
        CrateLoot loot = LootManager.get(type);
        if (index < 1 || index > loot.entries.size()) {
            c.getSource().sendFailure(Component.translatable("command.heralizcrates.bad_index", loot.entries.size()));
            return 0;
        }
        LootEntry removed = loot.entries.remove(index - 1);
        LootManager.save(type);
        c.getSource().sendSuccess(() -> Component.translatable("command.heralizcrates.loot_removed", removed.item, type.crateName()), true);
        return 1;
    }
}
