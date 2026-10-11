package fr.heraliz.loot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Raretés des objets, pour le staff (op 2), en tenant l'objet en main :
 * <ul><li>/rarete commun|peu_commun|rare|epique|legendaire|unique — donne cette rareté à cette sorte d'objet (pour une
 * arme, un accessoire ou une munition de TaCZ : à cette arme précise) ;</li>
 * <li>/rarete aucune — pas de rareté propre (celle par défaut s'applique) ;</li>
 * <li>/rarete effacer — oublie le choix fait en jeu pour cet objet (celui de raretes.json revient) ;</li>
 * <li>/rarete voir (ou /rarete) — montre sa rareté ;</li>
 * <li>/rarete defaut &lt;niveau|aucune&gt; — rareté des objets qui n'en ont pas ;</li>
 * <li>/rarete recharger — relit config/heraliz_loot/raretes.json et raretes_jeu.json.</li></ul>
 * Les choix faits en jeu vont dans raretes_jeu.json (jamais écrasé par la mise en ligne) et sont envoyés aussitôt à tous
 * les joueurs ({@link RaretesServeur}). Réponses en message direct.
 */
final class CommandesRarete {
    private CommandesRarete() {
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> racine = Commands.literal("rarete").requires(s -> s.hasPermission(2))
                .executes(c -> voir(c.getSource()))
                .then(Commands.literal("voir").executes(c -> voir(c.getSource())))
                .then(Commands.literal("recharger").executes(c -> {
                    boolean ok = RaretesServeur.charger();
                    RaretesServeur.envoyerATous();
                    if (ok) c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.rarete.relues",
                            "Raretés relues et envoyées aux joueurs."), false);
                    else c.getSource().sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.illisible",
                            "Un fichier des raretés est illisible : gardé en .bad et ignoré (voir la console)."));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("aucune").executes(c -> definir(c.getSource(), null)))
                .then(Commands.literal("effacer").executes(c -> effacer(c.getSource())));
        LiteralArgumentBuilder<CommandSourceStack> defaut = Commands.literal("defaut");
        defaut.then(Commands.literal("aucune").executes(c -> definirDefaut(c.getSource(), null)));
        for (Rarete r : Rarete.values()) {
            racine.then(Commands.literal(r.id).executes(c -> definir(c.getSource(), r)));
            defaut.then(Commands.literal(r.id).executes(c -> definirDefaut(c.getSource(), r)));
        }
        d.register(racine.then(defaut));
    }

    private static MutableComponent nom(Rarete r) {
        return r == null ? Component.translatableWithFallback("heraliz_loot.rarete.aucune", "aucune").withStyle(ChatFormatting.GRAY)
                : r.texte().setStyle(Style.EMPTY.withColor(TextColor.fromRgb(r.couleur)));
    }

    private static MutableComponent parDefaut() {
        return Component.translatableWithFallback("heraliz_loot.cmd.rarete.origine.defaut", "(rareté par défaut)");
    }

    private static int voir(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.en_jeu", "À faire en jeu, l'objet en main."));
            return 0;
        }
        ItemStack st = p.getMainHandItem();
        RaretesObjets.Cle cle = RaretesObjets.cle(st);
        if (cle == null) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.objet_en_main", "Tiens un objet en main."));
            return 0;
        }
        Rarete propre = RaretesObjets.propre(st), affichee = RaretesObjets.de(st);
        Component origine = propre == null ? parDefaut()
                : RaretesServeur.choisieEnJeu(cle) ? Component.translatableWithFallback("heraliz_loot.cmd.rarete.origine.jeu", "(choisie en jeu)")
                : Component.literal("(raretes.json)");
        src.sendSuccess(() -> Component.literal(cle.id() + " : ").append(nom(affichee))
                .append(Component.literal(" ").append(origine).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int definir(CommandSourceStack src, Rarete r) {
        ServerPlayer p = src.getPlayer();
        if (p == null) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.en_jeu", "À faire en jeu, l'objet en main."));
            return 0;
        }
        RaretesObjets.Cle cle = RaretesObjets.cle(p.getMainHandItem());
        if (cle == null) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.objet_en_main", "Tiens un objet en main."));
            return 0;
        }
        RaretesServeur.definir(cle, r);
        src.sendSuccess(() -> Component.literal(cle.id() + " → ").append(nom(r == null ? RaretesObjets.table().defaut() : r))
                .append(r == null ? Component.literal(" ").append(parDefaut()).withStyle(ChatFormatting.GRAY) : Component.empty()), false);
        return 1;
    }

    private static int effacer(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        RaretesObjets.Cle cle = p == null ? null : RaretesObjets.cle(p.getMainHandItem());
        if (cle == null) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.objet_en_main", "Tiens un objet en main."));
            return 0;
        }
        if (!RaretesServeur.effacer(cle)) {
            src.sendFailure(Component.translatableWithFallback("heraliz_loot.cmd.rarete.aucun_choix", "Aucun choix fait en jeu pour %s.", cle.id()));
            return 0;
        }
        Rarete r = RaretesObjets.de(p.getMainHandItem());
        src.sendSuccess(() -> Component.literal(cle.id() + " → ").append(nom(r)).append(Component.literal(" (raretes.json)").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int definirDefaut(CommandSourceStack src, Rarete r) {
        RaretesServeur.definirDefaut(r);
        src.sendSuccess(() -> Component.translatableWithFallback("heraliz_loot.cmd.rarete.defaut", "Rareté par défaut : %s", nom(r)), false);
        return 1;
    }
}
