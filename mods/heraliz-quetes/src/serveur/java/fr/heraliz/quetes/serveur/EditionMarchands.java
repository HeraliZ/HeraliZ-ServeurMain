package fr.heraliz.quetes.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.Texte;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ventes et prix des marchands modifiés EN JEU par un op (Registre du marchand : écran d'édition, op permission 4 et
 * connecté sur AuthMe ; tout est revérifié ici). Gardés à
 * la racine du serveur ({@code heraliz_quetes/marchands_jeu.json}), hors de {@code config/} : la mise en ligne ne les
 * écrase jamais. Pour un PNJ présent dans ce fichier, sa clé « marchand » ENTIÈRE (articles ou règles de rachat) vient
 * d'ici et non plus de son fichier {@code pnj/<id>.json} ; {@code /marchand reinitialiser <pnj>} revient au fichier.
 */
final class EditionMarchands {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final List<String> RARETES = List.of("commun", "peu_commun", "rare", "epique", "legendaire", "mythique");
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

    // ------------------------------------------------------------------ droits
    /** Op (permission 4). AuthMe bloque déjà toute commande et tout clic avant le /login. */
    static boolean peut(ServerPlayer p) {
        return p.hasPermissions(4);
    }

    private static void refuser(ServerPlayer p) {
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.marchand.refuse",
                "Réservé aux op.").withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ écran d'édition
    /** Ouvre (ou met à jour) l'éditeur du marchand chez cet op. */
    static void ouvrir(ServerPlayer p, String id) {
        if (!peut(p)) {
            refuser(p);
            return;
        }
        Defs.Pnj def = Catalogue.pnj(id);
        if (def == null || def.marchand == null) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.marchand.pas_marchand", "« %s » n'est pas un marchand.", id)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Defs.Marchand m = def.marchand;
        List<Reseau.EArticle> articles = new ArrayList<>();
        for (Defs.ArticleDef a : m.articles) {
            ItemStack s = Marchands.objet(a, 1);
            if (s.isEmpty()) s = new ItemStack(Items.BARRIER).setHoverName(Component.literal(a.objet()));
            articles.add(new Reseau.EArticle(s, a.prix(), Ponts.rarete(s)));
        }
        List<Reseau.ERegle> regles = new ArrayList<>();
        for (Defs.Regle r : m.regles) {
            long[] prix = new long[6];
            for (int k = 0; k < 6; k++) {
                Long v = r.prix().get(RARETES.get(k));
                if (v == null && k == 5) v = r.prix().get("unique");
                prix[k] = v == null ? 0 : v;
            }
            regles.add(new Reseau.ERegle(r.tag(), prix));
        }
        Reseau.envoyer(p, new Reseau.Edition(id, def.nom.vide() ? Texte.fr(id) : def.nom, m.rachat, MODIFS.containsKey(id), articles, regles));
    }

    /** Un changement demandé depuis l'écran : droits revérifiés, appliqué, enregistré, puis l'écran est mis à jour. */
    static void modif(ServerPlayer p, Reseau.Modif r) {
        if (!peut(p)) {
            refuser(p);
            return;
        }
        Defs.Pnj def = Catalogue.pnj(r.pnj());
        if (def == null || def.marchand == null) return;
        Defs.Marchand m = def.marchand;
        List<Defs.ArticleDef> l = m.articles;
        String quoi = null;
        switch (r.type()) {
            case Reseau.Modif.PRIX -> {
                if (m.rachat || r.index() < 0 || r.index() >= l.size() || r.prix() < 1) break;
                Defs.ArticleDef a = l.get(r.index());
                l.set(r.index(), new Defs.ArticleDef(a.objet(), a.nbt(), r.prix()));
                quoi = a.objet() + " : " + a.prix() + " -> " + r.prix();
            }
            case Reseau.Modif.RETIRER -> {
                if (m.rachat || r.index() < 0 || r.index() >= l.size()) break;
                quoi = "- " + l.remove(r.index()).objet();
            }
            case Reseau.Modif.MONTER -> {
                if (m.rachat || r.index() < 1 || r.index() >= l.size()) break;
                l.add(r.index() - 1, l.remove(r.index()));
                quoi = "^ " + l.get(r.index() - 1).objet();
            }
            case Reseau.Modif.AJOUTER_MAIN -> {
                ItemStack main = p.getMainHandItem();
                if (m.rachat || r.prix() < 1 || main.isEmpty() || main.is(HeralizQuetes.REGISTRE.get())) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.main_vide"), 0xFF6B6B);
                    break;
                }
                String objet = String.valueOf(ForgeRegistries.ITEMS.getKey(main.getItem()));
                // données de l'objet gardées (soin, accessoire TaCZ...), sauf l'usure et la marque anti-copie
                String nbt = "";
                if (main.hasTag()) {
                    CompoundTag tag = main.getTag().copy();
                    tag.remove("spartan_uid");
                    tag.remove("Damage");
                    if (!tag.isEmpty()) nbt = tag.toString();
                }
                l.add(new Defs.ArticleDef(objet, nbt, r.prix()));
                quoi = "+ " + objet + " " + r.prix();
            }
            case Reseau.Modif.PRIX_RACHAT -> {
                if (!m.rachat || r.index() < 0 || r.index() >= m.regles.size() || r.rarete() < 0 || r.rarete() >= 6 || r.prix() < 0) break;
                Defs.Regle g = m.regles.get(r.index());
                Map<String, Long> prix = new LinkedHashMap<>(g.prix());
                prix.remove("unique");
                prix.put(RARETES.get(r.rarete()), r.prix());
                m.regles.set(r.index(), new Defs.Regle(g.tag(), prix));
                quoi = g.tag() + " " + RARETES.get(r.rarete()) + " -> " + r.prix();
            }
            case Reseau.Modif.REINITIALISER -> {
                if (MODIFS.remove(def.id) == null) break;
                sauver();
                Catalogue.charger();
                LOG.info("[heraliz_quetes] marchand {} remis à son fichier par {}", def.id, p.getGameProfile().getName());
                ouvrir(p, def.id);
                return;
            }
            default -> {
            }
        }
        if (quoi != null) {
            enregistrer(def);
            LOG.info("[heraliz_quetes] marchand {} modifié par {} : {}", def.id, p.getGameProfile().getName(), quoi);
        }
        ouvrir(p, def.id);
    }

    // ------------------------------------------------------------------ commandes
    private static final SuggestionProvider<CommandSourceStack> MARCHANDS = (c, b) ->
            SharedSuggestionProvider.suggest(Catalogue.tous().stream().filter(p -> p.marchand != null).map(p -> p.id), b);

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("marchand").requires(s -> s.hasPermission(4))
                .then(Commands.literal("registre").executes(EditionMarchands::registre))
                .then(Commands.literal("editer").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .executes(c -> {
                            ouvrir(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "pnj"));
                            return 1;
                        }))));
    }

    private static int registre(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!peut(p)) {
            refuser(p);
            return 0;
        }
        ItemStack s = new ItemStack(HeralizQuetes.REGISTRE.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.registre_donne",
                "Registre du marchand : clic droit sur un marchand pour ouvrir son éditeur."), false);
        return 1;
    }
}
