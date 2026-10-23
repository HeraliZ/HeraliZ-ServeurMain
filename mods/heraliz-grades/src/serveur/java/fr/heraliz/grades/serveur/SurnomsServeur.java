package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.PermsGrades;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surnom et chat en couleur (22/10, demande du propriétaire, grades de la boutique) :
 * <ul>
 *    <li>{@code /nick <surnom>} (permission de grade « nick » : Traqueur et Roi de la Zone ; op niveau 4 toujours) : 3 à 16
 *    lettres, chiffres ou _, jamais le pseudo d'un autre joueur connu (connectés, gradés, usercache.json du serveur) ni le
 *    surnom d'un autre ; affiché « ~Surnom » (on sait que ce n'est pas un vrai pseudo) dans le chat, les messages du serveur
 *    et le tab ; survol du nom = vrai pseudo (pas dans le chat Bukkit). Le nom au-dessus de la tête reste le vrai pseudo
 *    (calculé chez chaque joueur). {@code /nick reset} le retire, {@code /nick qui <surnom>} (pour tous) donne
 *    le vrai pseudo. Un joueur qui n'a plus la permission garde son surnom, mais il n'est plus affiché ;</li>
 *    <li>{@code /chatcouleur <couleur|aucune>} (permission « chat_couleur » : Roi de la Zone ; op niveau 4 toujours) :
 *    couleur de ses messages dans le chat (format du chat Bukkit, {@link PontBukkit}). Même règle si la permission est
 *    perdue.</li>
 * </ul>
 * Gardés hors du monde : {@code heraliz_grades/nicks.json} et {@code heraliz_grades/chat_couleurs.json} à la racine du
 * serveur. Comme les autres fichiers des grades : un fichier illisible n'est jamais écrasé et plus rien ne change tant
 * qu'il l'est. Fil serveur seulement (sauf {@link PontBukkit#CODE_CHAT}).
 */
final class SurnomsServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Couleurs du chat proposées : identifiant (commande, fichier) -> code couleur Bukkit. */
    static final Map<String, String> COULEURS = new LinkedHashMap<>();

    static {
        COULEURS.put("blanc", "§f");
        COULEURS.put("or", "§6");
        COULEURS.put("orange", Grade.couleurBukkit(0xFF9A3C));
        COULEURS.put("rouge", "§c");
        COULEURS.put("rose", "§d");
        COULEURS.put("violet", Grade.couleurBukkit(0xB48CFF));
        COULEURS.put("bleu", "§9");
        COULEURS.put("cyan", "§b");
        COULEURS.put("vert", "§a");
        COULEURS.put("gris", "§7");
    }

    /** Surnom par joueur (sans le « ~ »). */
    private static final Map<UUID, String> SURNOMS = new ConcurrentHashMap<>();
    /** Dernier vrai pseudo connu du joueur qui a un surnom (pour /nick qui, même déconnecté). */
    private static final Map<UUID, String> NOMS = new ConcurrentHashMap<>();
    /** Couleur du chat choisie par joueur (identifiant de {@link #COULEURS}). */
    private static final Map<UUID, String> CHOIX_COULEUR = new ConcurrentHashMap<>();
    private static Path fichierSurnoms, fichierCouleurs;
    private static boolean surnomsIllisible, couleursIllisible;

    private SurnomsServeur() {
    }

    // ------------------------------------------------------------------ fichiers
    static void demarrer(Path dossier) {
        SURNOMS.clear();
        NOMS.clear();
        CHOIX_COULEUR.clear();
        PontBukkit.CODE_CHAT.clear();
        fichierSurnoms = dossier.resolve("nicks.json");
        fichierCouleurs = dossier.resolve("chat_couleurs.json");
        surnomsIllisible = couleursIllisible = false;
        if (Files.exists(fichierSurnoms)) {
            try {
                JsonObject joueurs = JsonParser.parseString(Files.readString(fichierSurnoms, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("joueurs");
                if (joueurs == null) throw new IllegalStateException("clé « joueurs » absente");
                for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                    UUID u = UUID.fromString(e.getKey());
                    JsonObject o = e.getValue().getAsJsonObject();
                    String s = o.get("surnom").getAsString();
                    if (!valide(s)) continue;
                    SURNOMS.put(u, s);
                    if (o.has("nom")) NOMS.put(u, o.get("nom").getAsString());
                }
            } catch (Exception ex) {
                SURNOMS.clear();
                NOMS.clear();
                surnomsIllisible = true;
                LOG.error("[grades] {} ILLISIBLE ({}) : surnoms coupés tant qu'il n'est pas réparé. Le fichier n'est pas touché.", fichierSurnoms, ex.toString());
            }
        }
        if (Files.exists(fichierCouleurs)) {
            try {
                JsonObject joueurs = JsonParser.parseString(Files.readString(fichierCouleurs, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("joueurs");
                if (joueurs == null) throw new IllegalStateException("clé « joueurs » absente");
                for (Map.Entry<String, JsonElement> e : joueurs.entrySet()) {
                    String c = e.getValue().getAsString();
                    if (COULEURS.containsKey(c)) CHOIX_COULEUR.put(UUID.fromString(e.getKey()), c);
                }
            } catch (Exception ex) {
                CHOIX_COULEUR.clear();
                couleursIllisible = true;
                LOG.error("[grades] {} ILLISIBLE ({}) : chat en couleur coupé tant qu'il n'est pas réparé. Le fichier n'est pas touché.", fichierCouleurs, ex.toString());
            }
        }
    }

    static void arreter() {
        SURNOMS.clear();
        NOMS.clear();
        CHOIX_COULEUR.clear();
        PontBukkit.CODE_CHAT.clear();
        fichierSurnoms = fichierCouleurs = null;
    }

    static void oublier(UUID joueur) {
        PontBukkit.CODE_CHAT.remove(joueur);
    }

    private static void sauverSurnoms() throws IOException {
        if (surnomsIllisible || fichierSurnoms == null) throw new IOException("fichier des surnoms illisible");
        JsonObject joueurs = new JsonObject();
        SURNOMS.forEach((u, s) -> {
            JsonObject o = new JsonObject();
            o.addProperty("nom", NOMS.getOrDefault(u, "?"));
            o.addProperty("surnom", s);
            joueurs.add(u.toString(), o);
        });
        JsonObject racine = new JsonObject();
        racine.addProperty("_note", "Surnoms (/nick) : écrit par le serveur. nom = dernier vrai pseudo connu.");
        racine.add("joueurs", joueurs);
        ecrire(fichierSurnoms, racine);
    }

    private static void sauverCouleurs() throws IOException {
        if (couleursIllisible || fichierCouleurs == null) throw new IOException("fichier des couleurs du chat illisible");
        JsonObject joueurs = new JsonObject();
        CHOIX_COULEUR.forEach((u, c) -> joueurs.addProperty(u.toString(), c));
        JsonObject racine = new JsonObject();
        racine.addProperty("_note", "Couleur du chat (/chatcouleur) : écrit par le serveur. Couleurs : " + String.join(", ", COULEURS.keySet()) + ".");
        racine.add("joueurs", joueurs);
        ecrire(fichierCouleurs, racine);
    }

    private static void ecrire(Path fichier, JsonObject racine) throws IOException {
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomiqueImpossible) {
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------ règles
    private static boolean valide(String s) {
        return s != null && s.matches("[A-Za-z0-9_]{3,16}");
    }

    /** Op niveau 4 connecté sur AuthMe : surnom et couleur toujours permis. */
    private static boolean staff(ServerPlayer p) {
        return p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    private static boolean droitSurnom(ServerPlayer p) {
        return PermsServeur.valeur(p.getUUID(), PermsGrades.NICK) > 0 || staff(p);
    }

    private static boolean droitCouleur(ServerPlayer p) {
        return PermsServeur.valeur(p.getUUID(), PermsGrades.CHAT_COULEUR) > 0 || staff(p);
    }

    /** Surnom affiché de ce joueur (sans le « ~ »), null s'il n'en a pas ou n'y a plus droit. */
    static String surnomAffiche(ServerPlayer p) {
        String s = SURNOMS.get(p.getUUID());
        return s != null && (PermsServeur.valeur(p.getUUID(), PermsGrades.NICK) > 0 || p.hasPermissions(4)) ? s : null;
    }

    /** Remet à jour la couleur du chat du joueur (lue par l'écouteur du chat Bukkit). Appelé régulièrement (fil serveur). */
    static void majChat(ServerPlayer p) {
        String c = CHOIX_COULEUR.get(p.getUUID());
        String code = c == null || !(PermsServeur.valeur(p.getUUID(), PermsGrades.CHAT_COULEUR) > 0 || p.hasPermissions(4)) ? null : COULEURS.get(c);
        if (code == null) PontBukkit.CODE_CHAT.remove(p.getUUID());
        else PontBukkit.CODE_CHAT.put(p.getUUID(), code);
    }

    /**
     * Vrai si ce texte est le vrai pseudo d'un AUTRE joueur connu : connectés, gradés, et le cache des profils du serveur
     * (usercache.json, lu tel quel : en mode hors ligne, le cache de Minecraft inventerait un profil pour n'importe quel nom).
     */
    private static boolean pseudoDUnAutre(MinecraftServer srv, String s, UUID moi) {
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            if (!o.getUUID().equals(moi) && o.getGameProfile().getName().equalsIgnoreCase(s)) return true;
        }
        RegistreGrades r = ServeurGrades.registre;
        if (r != null) {
            for (Map.Entry<UUID, RegistreGrades.Fiche> e : r.tous()) {
                if (!e.getKey().equals(moi) && e.getValue().nom().equalsIgnoreCase(s)) return true;
            }
        }
        for (Map.Entry<UUID, String> e : NOMS.entrySet()) {
            if (!e.getKey().equals(moi) && e.getValue().equalsIgnoreCase(s)) return true;
        }
        Path cache = srv.getServerDirectory().toPath().resolve("usercache.json");
        if (Files.exists(cache)) {
            try {
                JsonArray l = JsonParser.parseString(Files.readString(cache, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement x : l) {
                    JsonObject o = x.getAsJsonObject();
                    if (!o.has("name") || !o.get("name").getAsString().equalsIgnoreCase(s)) continue;
                    UUID u;
                    try {
                        u = o.has("uuid") ? UUID.fromString(o.get("uuid").getAsString()) : null;
                    } catch (IllegalArgumentException ex) {
                        u = null;
                    }
                    if (!moi.equals(u)) return true;
                }
            } catch (Exception ex) {
                LOG.warn("[grades] usercache.json illisible pour /nick : {}", ex.toString());
            }
        }
        return false;
    }

    private static boolean surnomDUnAutre(String s, UUID moi) {
        for (Map.Entry<UUID, String> e : SURNOMS.entrySet()) {
            if (!e.getKey().equals(moi) && e.getValue().equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ commandes
    private static MutableComponent msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades." + cle, fr, args);
    }

    private static MutableComponent nomCouleur(String id) {
        return Component.translatableWithFallback("heraliz_grades.couleur." + id, id);
    }

    static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("nick")
                .then(Commands.literal("reset").executes(SurnomsServeur::retirer))
                .then(Commands.literal("qui")
                        .then(Commands.argument("surnom", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new ArrayList<>(SURNOMS.values()), b))
                                .executes(SurnomsServeur::qui)))
                .then(Commands.argument("surnom", StringArgumentType.word()).executes(SurnomsServeur::poser)));
        d.register(Commands.literal("chatcouleur")
                .then(Commands.argument("couleur", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> l = new ArrayList<>(COULEURS.keySet());
                            l.add("aucune");
                            return SharedSuggestionProvider.suggest(l, b);
                        })
                        .executes(SurnomsServeur::couleur)));
    }

    private static int poser(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        if (!droitSurnom(p)) {
            p.sendSystemMessage(msg("nick.droit", "Le surnom est réservé aux grades Traqueur et Roi de la Zone."));
            return 0;
        }
        String s = StringArgumentType.getString(c, "surnom");
        if (s.startsWith("~")) s = s.substring(1);
        if (!valide(s)) {
            p.sendSystemMessage(msg("nick.invalide", "Surnom invalide : 3 à 16 lettres (sans accent), chiffres ou _."));
            return 0;
        }
        if (surnomDUnAutre(s, p.getUUID()) || pseudoDUnAutre(p.server, s, p.getUUID())) {
            p.sendSystemMessage(msg("nick.pris", "Ce surnom est déjà pris (pseudo ou surnom d'un autre joueur)."));
            return 0;
        }
        String avant = SURNOMS.get(p.getUUID()), nomAvant = NOMS.get(p.getUUID());
        SURNOMS.put(p.getUUID(), s);
        NOMS.put(p.getUUID(), p.getGameProfile().getName());
        try {
            sauverSurnoms();
        } catch (IOException ex) {
            remettre(SURNOMS, p.getUUID(), avant);
            remettre(NOMS, p.getUUID(), nomAvant);
            LOG.error("[grades] écriture de {} impossible : {}", fichierSurnoms, ex.toString());
            p.sendSystemMessage(msg("erreur", "Impossible d'enregistrer le changement (voir la console du serveur)."));
            return 0;
        }
        ServeurGrades.rafraichir(p);
        TabServeur.maj(p.server, true);
        p.sendSystemMessage(msg("nick.ok", "Ton surnom est maintenant ~%s.", s));
        return 1;
    }

    private static void remettre(Map<UUID, String> m, UUID u, String v) {
        if (v == null) m.remove(u);
        else m.put(u, v);
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        String avant = SURNOMS.remove(p.getUUID());
        if (avant == null) {
            p.sendSystemMessage(msg("nick.aucun", "Tu n'as pas de surnom."));
            return 0;
        }
        String nomAvant = NOMS.remove(p.getUUID());
        try {
            sauverSurnoms();
        } catch (IOException ex) {
            SURNOMS.put(p.getUUID(), avant);
            if (nomAvant != null) NOMS.put(p.getUUID(), nomAvant);
            LOG.error("[grades] écriture de {} impossible : {}", fichierSurnoms, ex.toString());
            p.sendSystemMessage(msg("erreur", "Impossible d'enregistrer le changement (voir la console du serveur)."));
            return 0;
        }
        ServeurGrades.rafraichir(p);
        TabServeur.maj(p.server, true);
        p.sendSystemMessage(msg("nick.reset", "Surnom retiré : ton vrai pseudo est de nouveau affiché."));
        return 1;
    }

    private static int qui(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String s = StringArgumentType.getString(c, "surnom");
        if (s.startsWith("~")) s = s.substring(1);
        for (Map.Entry<UUID, String> e : SURNOMS.entrySet()) {
            if (!e.getValue().equalsIgnoreCase(s)) continue;
            ServerPlayer o = src.getServer().getPlayerList().getPlayer(e.getKey());
            String vrai = o != null ? o.getGameProfile().getName() : NOMS.getOrDefault(e.getKey(), "?");
            String surnom = e.getValue();
            src.sendSuccess(() -> msg("nick.qui", "~%1$s est %2$s.", surnom, vrai), false);
            return 1;
        }
        String cherche = s;
        src.sendFailure(msg("nick.qui_inconnu", "Personne n'a le surnom %s.", cherche));
        return 0;
    }

    private static int couleur(CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
        if (!droitCouleur(p)) {
            p.sendSystemMessage(msg("chatcouleur.droit", "Le chat en couleur est réservé au grade Roi de la Zone."));
            return 0;
        }
        String id = StringArgumentType.getString(c, "couleur").toLowerCase(Locale.ROOT);
        boolean aucune = id.equals("aucune");
        if (!aucune && !COULEURS.containsKey(id)) {
            p.sendSystemMessage(msg("chatcouleur.inconnue", "Couleur inconnue : %1$s. Couleurs : %2$s.", id, String.join(", ", COULEURS.keySet()) + ", aucune"));
            return 0;
        }
        String avant = aucune ? CHOIX_COULEUR.remove(p.getUUID()) : CHOIX_COULEUR.put(p.getUUID(), id);
        try {
            sauverCouleurs();
        } catch (IOException ex) {
            remettre(CHOIX_COULEUR, p.getUUID(), avant);
            LOG.error("[grades] écriture de {} impossible : {}", fichierCouleurs, ex.toString());
            p.sendSystemMessage(msg("erreur", "Impossible d'enregistrer le changement (voir la console du serveur)."));
            return 0;
        }
        majChat(p);
        if (aucune) p.sendSystemMessage(msg("chatcouleur.aucune", "Tes messages s'écrivent de nouveau en blanc."));
        else p.sendSystemMessage(msg("chatcouleur.ok", "Tes messages s'écrivent maintenant en %s.", nomCouleur(id)));
        return 1;
    }
}
