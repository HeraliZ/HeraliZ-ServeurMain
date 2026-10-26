package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.reseau.ReseauBoutique;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boutique en jeu (/boutique) : articles payés en Points Boutique (le solde du compte du site, {@link Pb}).
 * <p>
 * Un achat : le serveur fait débiter le compte par le site (référence unique, gardée sur le disque AVANT l'appel), puis
 * joue les commandes de l'article avec la console ; si une commande échoue, le site recrédite (référence « -r »). Après
 * un plantage ou une coupure du site, chaque achat resté en cours est repris : débité sans livraison = remboursé,
 * jamais débité = oublié. Un seul achat à la fois par joueur. Articles dans {@code config/heraliz_grades/boutique.json}
 * (relu tout seul), achats en cours dans {@code heraliz_grades/achats_en_cours.json}, journal dans {@code journal.log}.
 */
final class BoutiqueServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * groupe = grade (rescape, traqueur, roi_de_la_zone, beta) ou caisse de la clé (gemme, camo, spectacle), lu par l'écran ;
     * quantite = mois du grade ou nombre de clés (0 = à vie) ; apresAchat = suffixe du message envoyé après l'achat (ou null).
     */
    record Article(String id, String categorie, String groupe, int quantite, Map<String, String> nom, Map<String, String> description, long prix,
                   int couleur, String icone, boolean avant, List<String> commandes, String apresAchat) {
    }

    /** Achat en cours : etat « debit » (débit demandé) ou « rembourser » (débité, livraison ratée). */
    private static final class EnCours {
        String ref, joueur, uuid, article, etat;
        long prix;
    }

    private static final List<Article> ARTICLES = new ArrayList<>();
    private static final Map<String, EnCours> EN_COURS = new LinkedHashMap<>();
    private static final Set<UUID> OCCUPES = new HashSet<>();
    private static Path fichierArticles, fichierEnCours, dossier;
    private static long dateArticles = -1;
    private static MinecraftServer serveur;
    private static int ticks;

    private BoutiqueServeur() {
    }

    static void brancher() {
        ReseauBoutique.RECEPTION_ACHAT.brancher(r -> acheter(r.joueur(), r.id()));
    }

    static void demarrer(MinecraftServer srv, Path dossierDonnees) {
        serveur = srv;
        dossier = dossierDonnees;
        fichierArticles = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("boutique.json");
        fichierEnCours = dossier.resolve("achats_en_cours.json");
        dateArticles = -1;
        relireSiChange();
        EN_COURS.clear();
        OCCUPES.clear();
        try {
            if (Files.exists(fichierEnCours)) {
                for (JsonElement e : JsonParser.parseString(Files.readString(fichierEnCours, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    EnCours c = GSON.fromJson(e, EnCours.class);
                    if (c != null && c.ref != null) EN_COURS.put(c.ref, c);
                }
            }
        } catch (Exception ex) {
            // jamais écrasé : à regarder à la main (achats peut-être à rembourser)
            LOG.error("[grades] {} illisible ({}) : achats en cours NON repris", fichierEnCours, ex.toString());
            try {
                Files.copy(fichierEnCours, fichierEnCours.resolveSibling("achats_en_cours.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignore) {
            }
        }
        if (!EN_COURS.isEmpty()) LOG.warn("[grades] boutique : {} achat(s) en cours à reprendre", EN_COURS.size());
    }

    static void arreter() {
        serveur = null;
        OCCUPES.clear();
    }

    static void tick() {
        if (serveur == null) return;
        if (++ticks % 200 == 0) relireSiChange();
        if (ticks % 600 == 0) reprendre();
    }

    // ------------------------------------------------------------------ articles
    static void relireSiChange() {
        try {
            if (fichierArticles == null || !Files.exists(fichierArticles)) return;
            long d = Files.getLastModifiedTime(fichierArticles).toMillis();
            if (d == dateArticles) return;
            dateArticles = d;
            List<Article> l = new ArrayList<>();
            JsonObject o = JsonParser.parseString(Files.readString(fichierArticles, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("articles")) {
                JsonObject a = e.getAsJsonObject();
                List<String> cmds = new ArrayList<>();
                for (JsonElement c : a.getAsJsonArray("commandes")) cmds.add(c.getAsString());
                String couleur = a.has("couleur") ? a.get("couleur").getAsString().replace("#", "") : "6FA8FF";
                long prix = a.get("prix").getAsLong();
                String id = a.get("id").getAsString();
                if (prix <= 0 || cmds.isEmpty() || !id.matches("[a-z0-9_]{1,32}")) continue;
                String apres = a.has("apres_achat") ? a.get("apres_achat").getAsString() : null;
                if (apres != null && !apres.matches("[a-z0-9_]{1,32}")) apres = null;
                l.add(new Article(id, a.has("categorie") ? a.get("categorie").getAsString() : "divers", a.has("groupe") ? a.get("groupe").getAsString() : "",
                        a.has("quantite") ? a.get("quantite").getAsInt() : 1, textes(a.get("nom")), textes(a.get("description")),
                        prix, Integer.parseInt(couleur, 16), a.has("icone") ? a.get("icone").getAsString() : "minecraft:paper",
                        a.has("avant") && a.get("avant").getAsBoolean(), cmds, apres));
            }
            ARTICLES.clear();
            ARTICLES.addAll(l);
            LOG.info("[grades] boutique : {} article(s)", l.size());
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : articles inchangés", fichierArticles, ex.toString());
        }
    }

    private static Map<String, String> textes(JsonElement e) {
        Map<String, String> m = new LinkedHashMap<>();
        if (e == null || e.isJsonNull()) return m;
        if (e.isJsonPrimitive()) m.put("fr", e.getAsString());
        else for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) m.put(x.getKey(), x.getValue().getAsString());
        return m;
    }

    private static String texte(Map<String, String> m, ServerPlayer p) {
        String langue = p.getLanguage() == null ? "fr" : p.getLanguage().toLowerCase(java.util.Locale.ROOT);
        String court = langue.length() >= 2 ? langue.substring(0, 2) : "fr";
        return m.getOrDefault(court, m.getOrDefault("fr", m.values().stream().findFirst().orElse("")));
    }

    private static Article article(String id) {
        for (Article a : ARTICLES) if (a.id().equals(id)) return a;
        return null;
    }

    private static ItemStack icone(String id) {
        Item i = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return new ItemStack(i == null || i == Items.AIR ? Items.PAPER : i);
    }

    // ------------------------------------------------------------------ écran
    /** /boutique : demande le solde au site, puis ouvre l'écran (solde inconnu = -1 si le site ne répond pas). */
    static int ouvrir(ServerPlayer p) {
        if (!PontBukkit.authentifie(p)) {
            p.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.kit.connexion", "Connecte-toi d'abord avec /login."));
            return 0;
        }
        Component non = Restrictions.empechement(p);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
        UUID u = p.getUUID();
        // onglets « Clés » et « Mes clés » : ses clés (sur son compte depuis le 25/10) arrivent avant l'écran
        fr.heraliz.crates.cles.MigrationCles.convertir(p);
        fr.heraliz.crates.api.ClesApi.envoyer(p);
        Pb.solde(p.getGameProfile().getName(), r -> {
            ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
            if (j != null) envoyer(j, true, r != null && r.code() == 200 ? r.nombre("solde") : -1, "", false);
        });
        return 1;
    }

    static void envoyer(ServerPlayer p, boolean ouvrir, long solde, String message, boolean ok) {
        List<ReseauBoutique.Offre> l = new ArrayList<>();
        for (Article a : ARTICLES) {
            l.add(new ReseauBoutique.Offre(a.id(), a.categorie(), a.groupe(), a.quantite(), texte(a.nom(), p), texte(a.description(), p), a.prix(), a.couleur(),
                    icone(a.icone()), a.avant()));
        }
        ReseauBoutique.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauBoutique.EtatBoutique(ouvrir, solde, l, message, ok));
    }

    // ------------------------------------------------------------------ achat
    private static void acheter(ServerPlayer p, String id) {
        Article a = article(id);
        if (a == null || serveur == null) return;
        if (!PontBukkit.authentifie(p)) return;
        Component non = Restrictions.empechement(p);
        if (non != null) {
            p.sendSystemMessage(non.copy().withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        if (!Pb.actif()) {
            envoyer(p, false, -1, "indisponible", false);
            return;
        }
        UUID u = p.getUUID();
        if (!OCCUPES.add(u)) return;
        EnCours c = new EnCours();
        c.ref = Pb.ref("achat");
        c.joueur = p.getGameProfile().getName();
        c.uuid = u.toString();
        c.article = a.id();
        c.prix = a.prix();
        c.etat = "debit";
        EN_COURS.put(c.ref, c);
        if (!sauver()) {
            EN_COURS.remove(c.ref);
            OCCUPES.remove(u);
            envoyer(p, false, -1, "erreur", false);
            return;
        }
        Pb.debiter(c.ref, c.joueur, c.prix, "boutique:" + a.id(), r -> {
            ServerPlayer j = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
            if (r == null) {
                // site injoignable : on ne sait pas si le débit a eu lieu ; repris plus tard (remboursé s'il a eu lieu)
                OCCUPES.remove(u);
                if (j != null) envoyer(j, false, -1, "injoignable", false);
                return;
            }
            if (!r.ok()) {
                EN_COURS.remove(c.ref);
                sauver();
                OCCUPES.remove(u);
                if (j != null) envoyer(j, false, r.corps().has("solde") ? r.nombre("solde") : -1, "solde_insuffisant".equals(r.raison()) ? "solde" : "refuse", false);
                return;
            }
            long solde = r.nombre("solde");
            int faites = j == null ? 0 : livrer(a, c.joueur);
            boolean livre = faites == a.commandes().size();
            if (!livre && faites > 0) {
                // 25/10 (audit) : livré en partie (ex. grade donné, clés refusées) : pas de remboursement automatique, le
                // joueur garderait ce qui est déjà donné ; noté pour le staff, qui règle à la main
                EN_COURS.remove(c.ref);
                sauver();
                OCCUPES.remove(u);
                journal("PARTIEL " + c.joueur + " " + a.id() + " " + a.prix() + " PB (" + c.ref + ") : " + faites + "/" + a.commandes().size()
                        + " commandes jouées, à régler à la main");
                LienDiscord.evenement("⚠️ Boutique : achat de " + a.id() + " par " + c.joueur + " livré en partie (" + faites + "/"
                        + a.commandes().size() + ") : à régler à la main (réf. " + c.ref + ")");
                if (j != null) {
                    j.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.boutique.partiel",
                            "Ton achat n'a été livré qu'en partie : le staff est prévenu et va le terminer.").withStyle(net.minecraft.ChatFormatting.GOLD));
                }
                return;
            }
            if (livre) {
                EN_COURS.remove(c.ref);
                sauver();
                OCCUPES.remove(u);
                journal("achat " + c.joueur + " " + a.id() + " " + a.prix() + " PB (" + c.ref + ")");
                if (j != null) {
                    envoyer(j, false, solde, a.apresAchat() == null ? "achete" : "achete_" + a.apresAchat(), true);
                    if (a.apresAchat() != null) {
                        j.sendSystemMessage(Component.translatableWithFallback("message.heraliz_grades.boutique." + a.apresAchat(),
                                "Achat réussi : regarde les consignes sur Discord (discord.gg/heraliz).").withStyle(net.minecraft.ChatFormatting.GOLD));
                    }
                }
                LienDiscord.evenement("Boutique : " + c.joueur + " a acheté " + a.id() + " (" + a.prix() + " PB)");
            } else {
                c.etat = "rembourser";
                sauver();
                rembourser(c, () -> {
                    OCCUPES.remove(u);
                    ServerPlayer k = serveur == null ? null : serveur.getPlayerList().getPlayer(u);
                    if (k != null) envoyer(k, false, -1, "rembourse", false);
                });
            }
        });
    }

    /**
     * Joue les commandes de l'article avec la console, dans l'ordre, et s'arrête à la première qui échoue ; nombre de
     * commandes réussies (toutes : livré ; aucune : remboursé ; entre les deux : à régler à la main).
     */
    private static int livrer(Article a, String joueur) {
        CommandSourceStack console = serveur.createCommandSourceStack();
        int faites = 0;
        for (String cmd : a.commandes()) {
            String ligne = cmd.replace("{joueur}", joueur).replace("{player}", joueur);
            try {
                int res = serveur.getCommands().performPrefixedCommand(console, ligne);
                if (res <= 0) {
                    LOG.error("[grades] boutique : « {} » a échoué ({})", ligne, res);
                    return faites;
                }
            } catch (Exception ex) {
                LOG.error("[grades] boutique : « {} » a planté ({})", ligne, ex.toString());
                return faites;
            }
            faites++;
        }
        return faites;
    }

    private static void rembourser(EnCours c, Runnable fin) {
        Pb.crediter(c.ref + "-r", c.joueur, c.prix, "remboursement:" + c.article, r -> {
            if (r != null && r.ok()) {
                EN_COURS.remove(c.ref);
                sauver();
                journal("remboursé " + c.joueur + " " + c.article + " " + c.prix + " PB (" + c.ref + ")");
            }
            // sinon : gardé « rembourser », réessayé toutes les 30 s
            if (fin != null) fin.run();
        });
    }

    /** Achats restés en cours (site injoignable, plantage) : remboursés s'ils ont été débités sans livraison. */
    private static void reprendre() {
        for (EnCours c : new ArrayList<>(EN_COURS.values())) {
            UUID u = UUID.fromString(c.uuid);
            if (OCCUPES.contains(u)) continue;
            OCCUPES.add(u);
            if ("rembourser".equals(c.etat)) {
                rembourser(c, () -> OCCUPES.remove(u));
                continue;
            }
            Pb.statut(c.ref, r -> {
                if (r == null || r.code() != 200) {
                    OCCUPES.remove(u);
                    return;
                }
                if (r.corps().has("fait") && r.corps().get("fait").getAsBoolean()) {
                    c.etat = "rembourser";
                    sauver();
                    rembourser(c, () -> OCCUPES.remove(u));
                } else if (System.currentTimeMillis() - heureRef(c.ref) < 120_000L) {
                    // « pas fait » trop tôt : la demande de débit (site lent, réponse perdue) peut encore arriver au site ;
                    // l'oublier maintenant ferait payer sans livraison ni remboursement. Revu dans 30 s (comme /trade).
                    OCCUPES.remove(u);
                } else {
                    EN_COURS.remove(c.ref);
                    sauver();
                    OCCUPES.remove(u);
                }
            });
        }
    }

    /** Heure de création d'une référence « type-horodatage(base 36)-hasard » ({@link Pb#ref}) ; 0 si illisible. */
    private static long heureRef(String ref) {
        try {
            String[] p = ref.split("-");
            return p.length >= 3 ? Long.parseLong(p[p.length - 2], 36) : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static boolean sauver() {
        try {
            Files.createDirectories(dossier);
            Path tmp = fichierEnCours.resolveSibling("achats_en_cours.json.tmp");
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(EN_COURS.values())), StandardCharsets.UTF_8);
            Files.move(tmp, fichierEnCours, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ex) {
            LOG.error("[grades] {} impossible à écrire ({})", fichierEnCours, ex.toString());
            return false;
        }
    }

    static void journal(String ligne) {
        try {
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("journal.log"), java.time.LocalDateTime.now().withNano(0) + " [pb] " + ligne + "\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            LOG.warn("[grades] journal : {}", ex.toString());
        }
    }
}
