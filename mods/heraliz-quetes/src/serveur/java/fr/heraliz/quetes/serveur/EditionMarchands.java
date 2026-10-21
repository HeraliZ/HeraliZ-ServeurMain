package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.HeralizQuetes;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventes et prix des marchands modifiés EN JEU par le staff (Registre du marchand, commande {@code /marchand}). Gardés à
 * la racine du serveur ({@code heraliz_quetes/marchands_jeu.json}), hors de {@code config/} : la mise en ligne ne les
 * écrase jamais. Pour un PNJ présent dans ce fichier, sa clé « marchand » ENTIÈRE (articles ou règles de rachat) vient
 * d'ici et non plus de son fichier {@code pnj/<id>.json} ; {@code /marchand reinitialiser <pnj>} revient au fichier.
 */
final class EditionMarchands {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final List<String> RARETES = List.of("commun", "peu_commun", "rare", "epique", "legendaire", "mythique");
    private static final List<String> NOMS_RARETES = List.of("Commun", "Peu commun", "Rare", "Épique", "Légendaire", "Mythique");
    private static final int PAR_PAGE = 10;
    private static Path fichier;
    /** Marchands modifiés en jeu, par identifiant de PNJ (même format que la clé « marchand » des fichiers). */
    private static final Map<String, JsonObject> MODIFS = new LinkedHashMap<>();

    private EditionMarchands() {
    }

    static void demarrer(Path racineServeur) {
        fichier = racineServeur.resolve("heraliz_quetes").resolve("marchands_jeu.json");
        MODIFS.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) if (e.getValue().isJsonObject()) MODIFS.put(e.getKey(), e.getValue().getAsJsonObject());
        } catch (Exception ex) {
            // illisible : jamais écrasé, mis de côté (comme les autres données du mod)
            LOG.error("[heraliz_quetes] {} illisible, mis de côté : {}", fichier, ex.getMessage());
            try {
                Files.move(fichier, fichier.resolveSibling("marchands_jeu.json.illisible"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
            }
        }
    }

    /** Remplace le marchand des PNJ modifiés en jeu (appelé à chaque lecture des fichiers). */
    static void appliquer(Map<String, Defs.Pnj> pnj, List<String> erreurs) {
        for (Map.Entry<String, JsonObject> e : MODIFS.entrySet()) {
            Defs.Pnj def = pnj.get(e.getKey());
            if (def == null) continue;
            Defs.Marchand m = Defs.marchand(e.getValue());
            if (def.marchand != null) m.bouton = def.marchand.bouton;
            def.marchand = m;
        }
    }

    private static void enregistrer(Defs.Pnj def) {
        MODIFS.put(def.id, versJson(def.marchand));
        sauver();
        Marchands.oublierCache();
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        MODIFS.forEach(o::add);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("marchands_jeu.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOG.error("[heraliz_quetes] écriture de {} impossible : {}", fichier, ex.getMessage());
        }
    }

    private static JsonObject versJson(Defs.Marchand m) {
        JsonObject o = new JsonObject();
        o.addProperty("type", m.rachat ? "rachat" : "achat");
        if (m.rachat) {
            JsonArray l = new JsonArray();
            for (Defs.Regle r : m.regles) {
                JsonObject j = new JsonObject();
                j.addProperty("tag", r.tag());
                JsonObject prix = new JsonObject();
                r.prix().forEach(prix::addProperty);
                j.add("prix", prix);
                l.add(j);
            }
            o.add("rachat", l);
        } else {
            JsonArray l = new JsonArray();
            for (Defs.ArticleDef a : m.articles) {
                JsonObject j = new JsonObject();
                j.addProperty("objet", a.objet());
                if (!a.nbt().isEmpty()) j.addProperty("nbt", a.nbt());
                j.addProperty("prix", a.prix());
                l.add(j);
            }
            o.add("articles", l);
        }
        return o;
    }

    // ------------------------------------------------------------------ affichage dans le chat
    private static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    private static MutableComponent bouton(MutableComponent texte, String commande, boolean executer, MutableComponent aide, ChatFormatting couleur) {
        return Component.literal("[").append(texte).append("]").withStyle(Style.EMPTY.withColor(couleur)
                .withClickEvent(new ClickEvent(executer ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, commande))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, aide)));
    }

    /** La fiche du marchand dans le chat du staff : chaque prix cliquable, page par page. */
    static void montrer(ServerPlayer p, String id, int page) {
        Defs.Pnj def = Catalogue.pnj(id);
        if (def == null || def.marchand == null) {
            p.sendSystemMessage(t("heraliz_quetes.marchand.pas_marchand", "« %s » n'est pas un marchand.", id).withStyle(ChatFormatting.RED));
            return;
        }
        Defs.Marchand m = def.marchand;
        String nom = def.nom.vide() ? id : def.nom.toString();
        p.sendSystemMessage(Component.literal("━━ ").withStyle(ChatFormatting.GOLD)
                .append(t(m.rachat ? "heraliz_quetes.marchand.titre_rachat" : "heraliz_quetes.marchand.titre_achat",
                        m.rachat ? "%s · rachat (modifié en jeu : %s)" : "%s · vente (modifié en jeu : %s)", nom,
                        t(MODIFS.containsKey(id) ? "heraliz_quetes.marchand.oui" : "heraliz_quetes.marchand.non",
                                MODIFS.containsKey(id) ? "oui" : "non")).withStyle(ChatFormatting.GOLD)));
        if (m.rachat) {
            for (int i = 0; i < m.regles.size(); i++) {
                Defs.Regle r = m.regles.get(i);
                p.sendSystemMessage(Component.literal((i + 1) + ". " + r.tag()).withStyle(ChatFormatting.YELLOW));
                MutableComponent ligne = Component.literal("   ");
                for (String rar : RARETES) {
                    long v = r.prix().getOrDefault(rar, 0L);
                    ligne.append(bouton(t("heraliz_quetes.marchand.rarete." + rar, NOMS_RARETES.get(RARETES.indexOf(rar))).append(" " + v),
                            "/marchand rachat " + id + " " + (i + 1) + " " + rar + " ", false,
                            t("heraliz_quetes.marchand.aide_prix", "Cliquer pour changer ce prix (0 = ne le rachète pas)"), ChatFormatting.AQUA)).append(" ");
                }
                p.sendSystemMessage(ligne);
            }
        } else {
            int pages = Math.max(1, (m.articles.size() + PAR_PAGE - 1) / PAR_PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int i = page * PAR_PAGE; i < Math.min(m.articles.size(), (page + 1) * PAR_PAGE); i++) {
                Defs.ArticleDef a = m.articles.get(i);
                ItemStack s = Marchands.objet(a, 1);
                MutableComponent nomObjet = (s.isEmpty() ? Component.literal(a.objet()).withStyle(ChatFormatting.RED) : s.getHoverName().copy())
                        .withStyle(st -> st.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(a.objet() + (a.nbt().isEmpty() ? "" : " " + a.nbt())))));
                p.sendSystemMessage(Component.literal((i + 1) + ". ").withStyle(ChatFormatting.GRAY).append(nomObjet)
                        .append(Component.literal(" · " + Ponts.nombre(a.prix()) + " ").withStyle(ChatFormatting.YELLOW))
                        .append(bouton(t("heraliz_quetes.marchand.prix", "Prix"), "/marchand prix " + id + " " + (i + 1) + " ", false,
                                t("heraliz_quetes.marchand.aide_prix_achat", "Cliquer pour changer le prix (en hérals)"), ChatFormatting.AQUA))
                        .append(" ")
                        .append(bouton(t("heraliz_quetes.marchand.retirer", "Retirer"), "/marchand retirer " + id + " " + (i + 1), false,
                                t("heraliz_quetes.marchand.aide_retirer", "Cliquer puis Entrée pour ne plus le vendre"), ChatFormatting.RED)));
            }
            MutableComponent bas = Component.literal("");
            if (page > 0) bas.append(bouton(Component.literal("◀"), "/marchand voir " + id + " " + page, true,
                    t("heraliz_quetes.marchand.precedente", "Page précédente"), ChatFormatting.WHITE)).append(" ");
            bas.append(Component.literal((page + 1) + "/" + pages + " ").withStyle(ChatFormatting.GRAY));
            if (page < pages - 1) bas.append(bouton(Component.literal("▶"), "/marchand voir " + id + " " + (page + 2), true,
                    t("heraliz_quetes.marchand.suivante", "Page suivante"), ChatFormatting.WHITE)).append(" ");
            bas.append(bouton(t("heraliz_quetes.marchand.ajouter", "Ajouter l'objet en main"), "/marchand ajouter " + id + " ", false,
                    t("heraliz_quetes.marchand.aide_ajouter", "Prendre l'objet à vendre en main, cliquer, taper le prix"), ChatFormatting.GREEN));
            p.sendSystemMessage(bas);
        }
        if (MODIFS.containsKey(id)) p.sendSystemMessage(bouton(t("heraliz_quetes.marchand.reinitialiser", "Revenir au fichier du serveur"),
                "/marchand reinitialiser " + id, false,
                t("heraliz_quetes.marchand.aide_reinitialiser", "Efface les changements faits en jeu pour ce marchand"), ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ commandes
    private static final SuggestionProvider<CommandSourceStack> MARCHANDS = (c, b) ->
            SharedSuggestionProvider.suggest(Catalogue.tous().stream().filter(p -> p.marchand != null).map(p -> p.id), b);
    private static final SuggestionProvider<CommandSourceStack> RAR = (c, b) -> SharedSuggestionProvider.suggest(RARETES, b);

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("marchand").requires(s -> s.hasPermission(2))
                .then(Commands.literal("registre").executes(EditionMarchands::registre))
                .then(Commands.literal("voir").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .executes(c -> voir(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> voir(c, IntegerArgumentType.getInteger(c, "page"))))))
                .then(Commands.literal("prix").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1))
                                .then(Commands.argument("prix", LongArgumentType.longArg(1)).executes(EditionMarchands::prix)))))
                .then(Commands.literal("retirer").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(EditionMarchands::retirer))))
                .then(Commands.literal("ajouter").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .then(Commands.argument("prix", LongArgumentType.longArg(1)).executes(EditionMarchands::ajouter))))
                .then(Commands.literal("rachat").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .then(Commands.argument("regle", IntegerArgumentType.integer(1))
                                .then(Commands.argument("rarete", StringArgumentType.word()).suggests(RAR)
                                        .then(Commands.argument("prix", LongArgumentType.longArg(0)).executes(EditionMarchands::rachat))))))
                .then(Commands.literal("reinitialiser").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .executes(EditionMarchands::reinitialiser))));
    }

    private static int erreur(CommandContext<CommandSourceStack> c, String cle, String fr, Object... args) {
        c.getSource().sendFailure(t(cle, fr, args));
        return 0;
    }

    private static Defs.Pnj marchand(CommandContext<CommandSourceStack> c) {
        Defs.Pnj def = Catalogue.pnj(StringArgumentType.getString(c, "pnj"));
        if (def == null || def.marchand == null) {
            erreur(c, "heraliz_quetes.marchand.pas_marchand", "« %s » n'est pas un marchand.", StringArgumentType.getString(c, "pnj"));
            return null;
        }
        return def;
    }

    /** Après un changement : on l'enregistre, on le note dans le journal et on remontre la fiche. */
    private static int fait(CommandContext<CommandSourceStack> c, Defs.Pnj def, int numero, String quoi) {
        enregistrer(def);
        LOG.info("[heraliz_quetes] marchand {} modifié par {} : {}", def.id, c.getSource().getTextValue(), quoi);
        c.getSource().sendSuccess(() -> t("heraliz_quetes.marchand.fait", "Enregistré : %s", quoi).withStyle(ChatFormatting.GREEN), false);
        if (c.getSource().getEntity() instanceof ServerPlayer p) montrer(p, def.id, Math.max(0, numero - 1) / PAR_PAGE);
        return 1;
    }

    private static int registre(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack s = new ItemStack(HeralizQuetes.REGISTRE.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        c.getSource().sendSuccess(() -> t("heraliz_quetes.marchand.registre_donne",
                "Registre du marchand : clic droit sur un marchand pour voir et changer ses ventes et ses prix."), false);
        return 1;
    }

    private static int voir(CommandContext<CommandSourceStack> c, int page) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        montrer(p, StringArgumentType.getString(c, "pnj"), page - 1);
        return 1;
    }

    private static int prix(CommandContext<CommandSourceStack> c) {
        Defs.Pnj def = marchand(c);
        if (def == null) return 0;
        int n = IntegerArgumentType.getInteger(c, "numero");
        long prix = LongArgumentType.getLong(c, "prix");
        if (def.marchand.rachat || n > def.marchand.articles.size())
            return erreur(c, "heraliz_quetes.marchand.numero", "Pas d'article n°%s chez ce marchand.", n);
        Defs.ArticleDef a = def.marchand.articles.get(n - 1);
        def.marchand.articles.set(n - 1, new Defs.ArticleDef(a.objet(), a.nbt(), prix));
        return fait(c, def, n, a.objet() + " : " + a.prix() + " -> " + prix);
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        Defs.Pnj def = marchand(c);
        if (def == null) return 0;
        int n = IntegerArgumentType.getInteger(c, "numero");
        if (def.marchand.rachat || n > def.marchand.articles.size())
            return erreur(c, "heraliz_quetes.marchand.numero", "Pas d'article n°%s chez ce marchand.", n);
        Defs.ArticleDef a = def.marchand.articles.remove(n - 1);
        return fait(c, def, n, "- " + a.objet());
    }

    private static int ajouter(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Defs.Pnj def = marchand(c);
        if (def == null) return 0;
        if (def.marchand.rachat) return erreur(c, "heraliz_quetes.marchand.pas_vendeur", "Ce marchand rachète : il ne vend rien.");
        ItemStack main = p.getMainHandItem();
        if (main.isEmpty() || main.is(HeralizQuetes.REGISTRE.get()))
            return erreur(c, "heraliz_quetes.marchand.main_vide", "Prends en main l'objet à vendre.");
        String objet = String.valueOf(ForgeRegistries.ITEMS.getKey(main.getItem()));
        // données de l'objet gardées (soin, accessoire TaCZ...), sauf les marques d'usure, d'anti-copie et de nom
        String nbt = "";
        if (main.hasTag()) {
            var tag = main.getTag().copy();
            tag.remove("spartan_uid");
            tag.remove("Damage");
            if (!tag.isEmpty()) nbt = tag.toString();
        }
        def.marchand.articles.add(new Defs.ArticleDef(objet, nbt, LongArgumentType.getLong(c, "prix")));
        return fait(c, def, def.marchand.articles.size(), "+ " + objet + " " + LongArgumentType.getLong(c, "prix"));
    }

    private static int rachat(CommandContext<CommandSourceStack> c) {
        Defs.Pnj def = marchand(c);
        if (def == null) return 0;
        int n = IntegerArgumentType.getInteger(c, "regle");
        String rar = StringArgumentType.getString(c, "rarete");
        if (!def.marchand.rachat || n > def.marchand.regles.size())
            return erreur(c, "heraliz_quetes.marchand.regle", "Pas de règle de rachat n°%s chez ce marchand.", n);
        if (!RARETES.contains(rar)) return erreur(c, "heraliz_quetes.marchand.rarete_inconnue", "Rareté inconnue : %s", rar);
        Defs.Regle r = def.marchand.regles.get(n - 1);
        long prix = LongArgumentType.getLong(c, "prix");
        Map<String, Long> m = new LinkedHashMap<>(r.prix());
        m.put(rar, prix);
        def.marchand.regles.set(n - 1, new Defs.Regle(r.tag(), m));
        return fait(c, def, 1, r.tag() + " " + rar + " -> " + prix);
    }

    private static int reinitialiser(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "pnj");
        if (MODIFS.remove(id) == null) return erreur(c, "heraliz_quetes.marchand.rien", "Aucun changement fait en jeu pour « %s ».", id);
        sauver();
        Catalogue.charger();
        c.getSource().sendSuccess(() -> t("heraliz_quetes.marchand.reinitialise", "« %s » suit de nouveau son fichier.", id), false);
        if (c.getSource().getEntity() instanceof ServerPlayer p) montrer(p, id, 0);
        return 1;
    }
}
