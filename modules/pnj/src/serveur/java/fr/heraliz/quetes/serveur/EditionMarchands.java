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
import fr.heraliz.quetes.entite.Pnj;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ventes et prix des marchands modifiés EN JEU par un op (Registre du marchand : écran d'édition, op permission 4 ; tout
 * est revérifié ici). Gardés à la racine du serveur ({@code heraliz_quetes/marchands_jeu.json}), hors de {@code config/} :
 * la mise en ligne ne les écrase jamais. Pour un PNJ présent dans ce fichier, sa clé « marchand » ENTIÈRE (articles,
 * règles de rachat, échanges) vient d'ici et non plus de son fichier {@code pnj/<id>.json} ;
 * « Revenir au fichier » revient au fichier.
 * <p>
 * Depuis la 1.11.0 : échanges (troc, {@link Echanges}), règles de rachat d'un seul objet, et marchands créés de zéro en
 * jeu ({@code /marchand nouveau}, {@link MarchandsCrees}), renommés, changés de rôle et supprimés.
 */
final class EditionMarchands {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final List<String> RARETES = List.of("commun", "peu_commun", "rare", "epique", "legendaire", "mythique");
    /** Règles de rachat d'un marchand, au plus (limite de l'écran d'édition). */
    private static final int REGLES_MAX = 16;
    private static Path fichier;
    /** Marchands modifiés en jeu, par identifiant de PNJ (même format que la clé « marchand » des fichiers). */
    private static final Map<String, JsonObject> MODIFS = new LinkedHashMap<>();

    private EditionMarchands() {
    }

    static void demarrer(Path racineServeur) {
        fichier = racineServeur.resolve("heraliz_quetes").resolve("marchands_jeu.json");
        MODIFS.clear();
        MarchandsCrees.demarrer(racineServeur);
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

    /** Ajoute les marchands créés en jeu, puis remplace le marchand des PNJ modifiés en jeu (à chaque lecture des fichiers). */
    static void appliquer(Map<String, Defs.Pnj> pnj, List<String> erreurs) {
        MarchandsCrees.ajouter(pnj, erreurs);
        for (Map.Entry<String, JsonObject> e : MODIFS.entrySet()) {
            Defs.Pnj def = pnj.get(e.getKey());
            if (def == null) continue;
            Defs.Marchand m = Defs.marchand(e.getValue());
            if (def.marchand != null) m.bouton = def.marchand.bouton;
            // marchand créé en jeu : son rôle est celui de pnj_jeu.json
            String role = MarchandsCrees.role(def.id);
            if (role != null) MarchandsCrees.appliquerRole(m, role);
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

    private static JsonObject cote(String objet, String nbt, int nombre) {
        JsonObject j = new JsonObject();
        j.addProperty("objet", objet);
        if (!nbt.isEmpty()) j.addProperty("nbt", nbt);
        j.addProperty("nombre", nombre);
        return j;
    }

    private static JsonObject versJson(Defs.Marchand m) {
        JsonObject o = new JsonObject();
        o.addProperty("type", m.hdv ? "hdv" : m.recyclage ? "recyclage" : m.troc ? "troc" : m.rachat ? "rachat" : "achat");
        // les deux listes sont gardées quand elles ne sont pas vides : changer le rôle d'un marchand créé ne perd rien
        if (m.rachat || !m.regles.isEmpty()) {
            JsonArray l = new JsonArray();
            for (Defs.Regle r : m.regles) {
                JsonObject j = new JsonObject();
                if (r.tag().startsWith("=")) j.addProperty("objet", r.tag().substring(1));
                else j.addProperty("tag", r.tag());
                JsonObject prix = new JsonObject();
                r.prix().forEach(prix::addProperty);
                j.add("prix", prix);
                l.add(j);
            }
            o.add("rachat", l);
        }
        if ((!m.rachat && !m.troc) || !m.articles.isEmpty()) {
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
        if (!m.echanges.isEmpty()) {
            JsonArray l = new JsonArray();
            for (Defs.EchangeDef e : m.echanges) {
                JsonObject j = new JsonObject();
                j.add("donne", cote(e.donne(), e.nbtDonne(), e.nDonne()));
                j.add("recoit", cote(e.recu(), e.nbtRecu(), e.nRecu()));
                l.add(j);
            }
            o.add("echanges", l);
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
            if (s.isEmpty()) s = inconnu(a.objet());
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
        List<Reseau.EEchange> echanges = new ArrayList<>();
        for (Defs.EchangeDef e : m.echanges) {
            ItemStack d = Echanges.donne(e), r = Echanges.recu(e);
            echanges.add(new Reseau.EEchange(d.isEmpty() ? inconnu(e.donne()) : d, e.nDonne(), r.isEmpty() ? inconnu(e.recu()) : r, e.nRecu()));
        }
        Reseau.envoyer(p, new Reseau.Edition(id, def.nom.vide() ? Texte.fr(id) : def.nom, m.rachat && !m.troc, MODIFS.containsKey(id),
                articles, regles, MarchandsCrees.cree(id), m.troc, echanges));
    }

    private static ItemStack inconnu(String objet) {
        return new ItemStack(Items.BARRIER).setHoverName(Component.literal(objet));
    }

    /** Objet de la case 0 à 8 de la barre d'objets (vide hors de la barre ou si c'est le registre). */
    private static ItemStack caseBarre(ServerPlayer p, int c) {
        ItemStack s = c >= 0 && c < 9 ? p.getInventory().getItem(c) : ItemStack.EMPTY;
        return s.is(HeralizQuetes.REGISTRE.get()) ? ItemStack.EMPTY : s;
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
        List<Defs.EchangeDef> ech = m.echanges;
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
                // objet de la case de la barre d'objets choisie dans l'écran (0 à 8)
                ItemStack main = caseBarre(p, r.rarete());
                if (m.rachat || m.troc || r.prix() < 1 || main.isEmpty()) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.main_vide"), 0xFF6B6B);
                    break;
                }
                if (Echanges.interdit(main)) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.echange.interdit"), 0xFF6B6B);
                    break;
                }
                String objet = Echanges.id(main);
                // données de l'objet gardées (soin, accessoire TaCZ...), sauf l'usure et la marque anti-copie
                l.add(new Defs.ArticleDef(objet, Echanges.nbtPropre(main), r.prix()));
                quoi = "+ " + objet + " " + r.prix();
                // 25/10 : un objet vendu n'est plus racheté que 50 % au plus de son prix (Marchands.prixRachat) : on le dit
                // à l'op si un racheteur le payait plus
                for (Defs.Pnj autre : Catalogue.tous()) {
                    if (autre.marchand != null && autre.marchand.rachat && Marchands.prixRachat(autre.marchand, main) > r.prix() / 2) {
                        Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.rachat_moitie", Texte.cle(main.getDescriptionId()),
                                Texte.brut(Ponts.nombre(r.prix())), Texte.brut(Ponts.nombre(r.prix() / 2))), 0xE8C36A);
                        break;
                    }
                }
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
            case Reseau.Modif.AJOUTER_REGLE -> {
                // règle d'UN objet (case de la barre d'objets), même prix pour toutes les raretés ; elle passe devant les tags
                ItemStack main = caseBarre(p, r.rarete());
                if (!m.rachat || m.troc || r.prix() < 1 || main.isEmpty()) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.main_vide"), 0xFF6B6B);
                    break;
                }
                if (Echanges.interdit(main)) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.echange.interdit"), 0xFF6B6B);
                    break;
                }
                if (m.regles.size() >= REGLES_MAX) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.trop", Texte.brut(REGLES_MAX)), 0xFF6B6B);
                    break;
                }
                String objet = Echanges.id(main);
                Map<String, Long> prix = new LinkedHashMap<>();
                for (String k : RARETES) prix.put(k, r.prix());
                m.regles.add(0, new Defs.Regle("=" + objet, prix));
                quoi = "+ rachat " + objet + " " + r.prix();
                long achat = SaisonQuetes.prixVente(SaisonQuetes.ventes(), main);
                if (achat == 0) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.jamais_rachete", Texte.cle(main.getDescriptionId())), 0xE8C36A);
                } else if (achat > 0 && r.prix() > achat / 2) {
                    Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.rachat_moitie", Texte.cle(main.getDescriptionId()),
                            Texte.brut(Ponts.nombre(achat)), Texte.brut(Ponts.nombre(achat / 2))), 0xE8C36A);
                }
            }
            case Reseau.Modif.RETIRER_REGLE -> {
                if (!m.rachat || r.index() < 0 || r.index() >= m.regles.size()) break;
                quoi = "- rachat " + m.regles.remove(r.index()).tag();
            }
            case Reseau.Modif.ECH_RETIRER -> {
                if (r.index() < 0 || r.index() >= ech.size()) break;
                Defs.EchangeDef e = ech.remove(r.index());
                quoi = "- échange " + e.donne() + " -> " + e.recu();
            }
            case Reseau.Modif.ECH_MONTER -> {
                if (r.index() < 1 || r.index() >= ech.size()) break;
                ech.add(r.index() - 1, ech.remove(r.index()));
                quoi = "^ échange " + ech.get(r.index() - 1).recu();
            }
            case Reseau.Modif.ECH_N_DONNE, Reseau.Modif.ECH_N_RECU -> {
                if (r.index() < 0 || r.index() >= ech.size() || r.prix() < 1 || r.prix() > Defs.ECHANGE_MAX) break;
                Defs.EchangeDef e = ech.get(r.index());
                int n = (int) r.prix();
                ech.set(r.index(), r.type() == Reseau.Modif.ECH_N_DONNE
                        ? new Defs.EchangeDef(e.donne(), e.nbtDonne(), n, e.recu(), e.nbtRecu(), e.nRecu())
                        : new Defs.EchangeDef(e.donne(), e.nbtDonne(), e.nDonne(), e.recu(), e.nbtRecu(), n));
                quoi = "échange " + e.donne() + " x" + e.nDonne() + " -> " + e.recu() + " x" + e.nRecu() + " : "
                        + (r.type() == Reseau.Modif.ECH_N_DONNE ? "donné" : "reçu") + " " + n;
            }
            case Reseau.Modif.REINITIALISER -> {
                // un marchand créé en jeu n'a pas de fichier où revenir : on le supprime à la place (bouton à part)
                if (MarchandsCrees.cree(def.id) || MODIFS.remove(def.id) == null) break;
                sauver();
                Catalogue.charger();
                LOG.info("[heraliz_quetes] marchand {} remis à son fichier par {}", def.id, p.getGameProfile().getName());
                ouvrir(p, def.id);
                return;
            }
            case Reseau.Modif.SUPPRIMER -> {
                supprimer(p, def.id);
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

    /** Ajouter un échange depuis l'éditeur : deux cases de la barre d'objets et deux quantités, tout revérifié. */
    static void ajoutEchange(ServerPlayer p, Reseau.AjoutEchange a) {
        if (!peut(p)) {
            refuser(p);
            return;
        }
        Defs.Pnj def = Catalogue.pnj(a.pnj());
        if (def == null || def.marchand == null) return;
        ItemStack donne = caseBarre(p, a.caseDonne()), recu = caseBarre(p, a.caseRecu());
        int nDonne = a.nDonne(), nRecu = a.nRecu();
        if (donne.isEmpty() || recu.isEmpty() || nDonne < 1 || nRecu < 1 || nDonne > Defs.ECHANGE_MAX || nRecu > Defs.ECHANGE_MAX) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.echange.ajout_vide", Texte.brut(Defs.ECHANGE_MAX)), 0xFF6B6B);
            ouvrir(p, def.id);
            return;
        }
        if (Echanges.interdit(donne) || Echanges.interdit(recu)) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.echange.interdit"), 0xFF6B6B);
            ouvrir(p, def.id);
            return;
        }
        if (def.marchand.echanges.size() >= Reseau.MAX_ECHANGES) {
            Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.trop", Texte.brut(Reseau.MAX_ECHANGES)), 0xFF6B6B);
            ouvrir(p, def.id);
            return;
        }
        String idDonne = Echanges.id(donne), idRecu = Echanges.id(recu);
        String refus = Echanges.refusAjout(idDonne, idRecu);
        if (refus != null) {
            Moteur.notif(p, Texte.cle(refus), 0xFF6B6B);
            ouvrir(p, def.id);
            return;
        }
        def.marchand.echanges.add(new Defs.EchangeDef(idDonne, Echanges.nbtPropre(donne), nDonne, idRecu, Echanges.nbtPropre(recu), nRecu));
        enregistrer(def);
        LOG.info("[heraliz_quetes] marchand {} modifié par {} : + échange {} x{} -> {} x{}", def.id, p.getGameProfile().getName(),
                idDonne, nDonne, idRecu, nRecu);
        // l'objet reçu ne sera plus jamais racheté contre des hérals : on le dit à l'op s'il l'était
        for (Defs.Pnj autre : Catalogue.tous()) {
            if (autre.marchand != null && autre.marchand.rachat && Marchands.prixRachat(autre.marchand, recu) > 0) {
                Moteur.notif(p, Texte.cle("heraliz_quetes.marchand.jamais_rachete", Texte.cle(recu.getDescriptionId())), 0xE8C36A);
                break;
            }
        }
        ouvrir(p, def.id);
    }

    // ------------------------------------------------------------------ marchands créés en jeu
    /** Supprime un marchand créé en jeu : sa fiche, ce qu'il vend, et ses PNJ posés (ceux non chargés : à leur chargement). */
    private static boolean supprimer(ServerPlayer p, String id) {
        if (!peut(p)) {
            refuser(p);
            return false;
        }
        if (!MarchandsCrees.cree(id)) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.marchand.pas_cree",
                    "« %s » vient d'un fichier du serveur : on ne le supprime pas en jeu (/pnj retirer l'enlève du monde).", id)
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (!MarchandsCrees.supprimer(id)) {
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.marchand.ecriture",
                    "Fichier du serveur impossible à écrire : rien n'a changé.").withStyle(ChatFormatting.RED));
            return false;
        }
        MODIFS.remove(id);
        sauver();
        int n = 0;
        for (Pnj e : Commandes.trouver(id)) {
            e.discard();
            n++;
        }
        Catalogue.charger();
        Marchands.oublierCache();
        LOG.info("[heraliz_quetes] marchand créé en jeu {} supprimé par {} ({} PNJ retirés)", id, p.getGameProfile().getName(), n);
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_quetes.marchand.supprime",
                "Marchand « %s » supprimé (%s PNJ retiré(s)).", id, n).withStyle(ChatFormatting.GREEN));
        return true;
    }

    // ------------------------------------------------------------------ commandes
    private static final SuggestionProvider<CommandSourceStack> MARCHANDS = (c, b) ->
            SharedSuggestionProvider.suggest(Catalogue.tous().stream().filter(p -> p.marchand != null).map(p -> p.id), b);
    private static final SuggestionProvider<CommandSourceStack> CREES = (c, b) ->
            SharedSuggestionProvider.suggest(MarchandsCrees.ids(), b);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (c, b) ->
            SharedSuggestionProvider.suggest(MarchandsCrees.ROLES, b);

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("marchand").requires(s -> s.hasPermission(4))
                .then(Commands.literal("registre").executes(EditionMarchands::registre))
                .then(Commands.literal("editer").then(Commands.argument("pnj", StringArgumentType.word()).suggests(MARCHANDS)
                        .executes(c -> {
                            ouvrir(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "pnj"));
                            return 1;
                        })))
                .then(Commands.literal("nouveau").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .then(Commands.argument("nom", StringArgumentType.greedyString())
                                        .executes(c -> nouveau(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "role"),
                                                StringArgumentType.getString(c, "nom")))))))
                .then(Commands.literal("supprimer").then(Commands.argument("id", StringArgumentType.word()).suggests(CREES)
                        .executes(c -> demanderSuppression(c, StringArgumentType.getString(c, "id")))
                        .then(Commands.literal("confirmer").executes(c ->
                                supprimer(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT)) ? 1 : 0))))
                .then(Commands.literal("nom").then(Commands.argument("id", StringArgumentType.word()).suggests(CREES)
                        .then(Commands.argument("nom", StringArgumentType.greedyString())
                                .executes(c -> renommer(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "nom"))))))
                .then(Commands.literal("role").then(Commands.argument("id", StringArgumentType.word()).suggests(CREES)
                        .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .executes(c -> changerRole(c, StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "role")))))));
    }

    /** L'op qui tape la commande, s'il a le droit (permission 4, revérifiée ici) ; sinon null (refus envoyé). */
    private static ServerPlayer op(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!peut(p)) {
            refuser(p);
            return null;
        }
        return p;
    }

    private static int erreur(CommandContext<CommandSourceStack> c, String cle, String fr, Object... args) {
        c.getSource().sendFailure(Component.translatableWithFallback(cle, fr, args));
        return 0;
    }

    private static String role(String saisi) {
        String r = saisi.toLowerCase(Locale.ROOT);
        return MarchandsCrees.ROLES.contains(r) ? r : null;
    }

    /** {@code /marchand nouveau <id> <vendeur|racheteur|troc> <nom>} : crée le marchand, le pose ici, ouvre son éditeur. */
    private static int nouveau(CommandContext<CommandSourceStack> c, String idSaisi, String roleSaisi, String nomSaisi) throws CommandSyntaxException {
        ServerPlayer p = op(c);
        if (p == null) return 0;
        String id = idSaisi.toLowerCase(Locale.ROOT);
        if (!MarchandsCrees.idValide(id)) {
            return erreur(c, "heraliz_quetes.marchand.id_invalide", "Identifiant « %s » refusé : 2 à 32 caractères, lettres minuscules, chiffres ou _.", idSaisi);
        }
        String role = role(roleSaisi);
        if (role == null) return erreur(c, "heraliz_quetes.marchand.role_invalide", "Rôle « %s » inconnu : vendeur, racheteur ou troc.", roleSaisi);
        String nom = MarchandsCrees.nettoyerNom(nomSaisi);
        if (nom.isEmpty()) return erreur(c, "heraliz_quetes.marchand.nom_vide", "Il faut un nom à afficher.");
        if (Catalogue.pnj(id) != null) {
            return erreur(c, "heraliz_quetes.marchand.existe", "Le PNJ « %s » existe déjà (fichier du serveur ou marchand créé en jeu).", id);
        }
        if (!MarchandsCrees.creer(id, nom, role)) {
            return erreur(c, "heraliz_quetes.marchand.ecriture", "Fichier du serveur impossible à écrire : rien n'a changé.");
        }
        // ce qu'il vend, rachète ou échange : vide pour commencer, gardé dans marchands_jeu.json
        Defs.Marchand m = new Defs.Marchand();
        MarchandsCrees.appliquerRole(m, role);
        MODIFS.put(id, versJson(m));
        sauver();
        Catalogue.charger();
        Pnj pnj = Commandes.poser(p, id);
        LOG.info("[heraliz_quetes] marchand {} ({}, {}) créé en jeu par {}", id, nom, role, p.getGameProfile().getName());
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.cree",
                "Marchand « %s » (%s) créé et posé ici. Son apparence : /pnj apparence %s moi", nom, id, id).withStyle(ChatFormatting.GREEN), false);
        if (pnj == null) erreur(c, "heraliz_quetes.marchand.pose_ratee", "PNJ non posé : /pnj creer %s pour le poser.", id);
        ouvrir(p, id);
        return 1;
    }

    private static int demanderSuppression(CommandContext<CommandSourceStack> c, String idSaisi) throws CommandSyntaxException {
        ServerPlayer p = op(c);
        if (p == null) return 0;
        String id = idSaisi.toLowerCase(Locale.ROOT);
        if (!MarchandsCrees.cree(id)) {
            return erreur(c, "heraliz_quetes.marchand.pas_cree",
                    "« %s » vient d'un fichier du serveur : on ne le supprime pas en jeu (/pnj retirer l'enlève du monde).", id);
        }
        String cmd = "/marchand supprimer " + id + " confirmer";
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.confirmer",
                        "Supprimer le marchand « %s » pour de bon ? Clique ou tape : %s", id, cmd)
                .withStyle(st -> st.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))), false);
        return 1;
    }

    private static int renommer(CommandContext<CommandSourceStack> c, String idSaisi, String nomSaisi) throws CommandSyntaxException {
        ServerPlayer p = op(c);
        if (p == null) return 0;
        String id = idSaisi.toLowerCase(Locale.ROOT);
        if (!MarchandsCrees.cree(id)) {
            return erreur(c, "heraliz_quetes.marchand.pas_cree_nom", "« %s » vient d'un fichier du serveur : son nom se change dans son fichier.", id);
        }
        String nom = MarchandsCrees.nettoyerNom(nomSaisi);
        if (nom.isEmpty()) return erreur(c, "heraliz_quetes.marchand.nom_vide", "Il faut un nom à afficher.");
        if (!MarchandsCrees.renommer(id, nom)) return erreur(c, "heraliz_quetes.marchand.ecriture", "Fichier du serveur impossible à écrire : rien n'a changé.");
        Catalogue.charger();
        for (Pnj e : Commandes.trouver(id)) e.definirNom(Texte.fr(nom));
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.renomme", "Marchand « %s » : nouveau nom « %s ».", id, nom), false);
        return 1;
    }

    private static int changerRole(CommandContext<CommandSourceStack> c, String idSaisi, String roleSaisi) throws CommandSyntaxException {
        ServerPlayer p = op(c);
        if (p == null) return 0;
        String id = idSaisi.toLowerCase(Locale.ROOT);
        if (!MarchandsCrees.cree(id)) {
            return erreur(c, "heraliz_quetes.marchand.pas_cree_role", "« %s » vient d'un fichier du serveur : son rôle se change dans son fichier.", id);
        }
        String role = role(roleSaisi);
        if (role == null) return erreur(c, "heraliz_quetes.marchand.role_invalide", "Rôle « %s » inconnu : vendeur, racheteur ou troc.", roleSaisi);
        if (!MarchandsCrees.changerRole(id, role)) return erreur(c, "heraliz_quetes.marchand.ecriture", "Fichier du serveur impossible à écrire : rien n'a changé.");
        Defs.Pnj def = Catalogue.pnj(id);
        if (def != null && def.marchand != null) {
            // ses listes sont gardées (articles, règles, échanges) : seul ce qu'il propose d'abord change
            MarchandsCrees.appliquerRole(def.marchand, role);
            enregistrer(def);
        }
        Catalogue.charger();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.role_change", "Marchand « %s » : rôle %s.", id, role), false);
        return 1;
    }

    private static int registre(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = op(c);
        if (p == null) return 0;
        ItemStack s = new ItemStack(HeralizQuetes.REGISTRE.get());
        if (!p.getInventory().add(s)) p.drop(s, false);
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.registre_donne",
                "Registre du marchand : clic droit sur un marchand pour ouvrir son éditeur."), false);
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_quetes.marchand.registre_nouveau",
                "Nouveau marchand : /marchand nouveau <id> <vendeur|racheteur|troc> <nom affiché>, là où tu te tiens."), false);
        return 1;
    }
}
