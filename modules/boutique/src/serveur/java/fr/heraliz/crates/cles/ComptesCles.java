package fr.heraliz.crates.cles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Comptes de clés des joueurs (25/10, demande des propriétaires) : les clés de caisse ne sont plus des objets, mais des
 * nombres gardés par le serveur, hors du monde, un petit fichier par joueur : {@code heraliz_crates/cles/<uuid>.json} à la
 * racine du serveur (il survit aux changements de carte, comme le butin changé en jeu).
 * <pre>
 * {"version": 1, "nom": "Pseudo", "cles": {"quete": 2, "gemme": 5}, "migration": 1}
 * </pre>
 * {@code cles} : caisse ({@link CrateType#id}) -> nombre ; une caisse inconnue (retirée du code) est gardée telle quelle.
 * {@code migration} : numéro de la dernière conversion de clés physiques appliquée ({@link MigrationCles}), pour qu'une
 * conversion ne soit jamais comptée deux fois, même après un plantage.
 * <p>
 * Tout se passe sur le fil serveur. Chaque changement est écrit tout de suite (fichier à côté, puis mis à la place : jamais
 * un fichier à moitié écrit) ; si l'écriture échoue, le changement est annulé en mémoire et l'opération refusée. Un
 * fichier illisible n'est jamais écrasé : il est copié à côté ({@code .illisible.<date>}) et le compte est fermé (aucune
 * clé ajoutée, prise ni convertie) jusqu'à ce que le staff le répare. Chaque mouvement est noté dans
 * {@code heraliz_crates/cles_journal.log}.
 */
public final class ComptesCles {
    private ComptesCles() {
    }

    /** Plafond par caisse (aucun débordement possible). */
    public static final int MAX = 1_000_000;
    /** Identifiants de remises uniques gardés par compte. */
    static final int REMISES_MAX = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Le compte d'un joueur en mémoire. */
    static final class Compte {
        final UUID uuid;
        String nom = "";
        final Map<String, Integer> cles = new LinkedHashMap<>();
        int migration;
        /**
         * Remises faites une seule fois (26/10, {@link #crediterUneFois}) : identifiants déjà crédités, écrits avec les
         * clés dans le même fichier ; les plus anciennes sortent au-delà de {@link #REMISES_MAX}.
         */
        final java.util.LinkedHashSet<String> remises = new java.util.LinkedHashSet<>();
        /** Fichier illisible : compte fermé (jamais réécrit). */
        boolean ferme;

        Compte(UUID uuid) {
            this.uuid = uuid;
        }

        int de(CrateType t) {
            return Math.max(0, cles.getOrDefault(t.id, 0));
        }
    }

    private static final Map<UUID, Compte> CACHE = new HashMap<>();

    public static Path dossier() {
        return FMLPaths.GAMEDIR.get().resolve("heraliz_crates").resolve("cles");
    }

    private static Path fichier(UUID u) {
        return dossier().resolve(u + ".json");
    }

    /** Oublie tout (arrêt du serveur, monde solo suivant). */
    public static void vider() {
        CACHE.clear();
    }

    /** Le compte (lu sur le disque la première fois). Fil serveur. */
    static Compte compte(UUID u) {
        Compte c = CACHE.get(u);
        if (c != null) return c;
        c = new Compte(u);
        Path f = fichier(u);
        if (Files.isRegularFile(f)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("nom")) c.nom = o.get("nom").getAsString();
                c.migration = o.has("migration") ? o.get("migration").getAsInt() : 0;
                if (o.has("remises")) for (JsonElement e : o.getAsJsonArray("remises")) c.remises.add(e.getAsString());
                if (o.has("cles")) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("cles").entrySet()) {
                        int n = e.getValue().getAsInt();
                        if (n > 0) c.cles.put(e.getKey(), Math.min(MAX, n));
                    }
                }
            } catch (Exception ex) {
                c.cles.clear();
                c.ferme = true;
                HeralizCrates.LOGGER.error("[HeralizCrates] compte de clés illisible ({}) : {} — compte FERMÉ, fichier gardé tel quel", f, ex.toString());
                try {
                    Files.copy(f, f.resolveSibling(u + ".json.illisible." + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignore) {
                }
            }
        }
        CACHE.put(u, c);
        return c;
    }

    private static boolean ecrire(Compte c) {
        if (c.ferme) return false;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("version", 1);
            o.addProperty("nom", c.nom);
            JsonObject cles = new JsonObject();
            c.cles.forEach((k, v) -> {
                if (v > 0) cles.addProperty(k, v);
            });
            o.add("cles", cles);
            o.addProperty("migration", c.migration);
            if (!c.remises.isEmpty()) {
                com.google.gson.JsonArray r = new com.google.gson.JsonArray();
                c.remises.forEach(r::add);
                o.add("remises", r);
            }
            Path f = fichier(c.uuid);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(c.uuid + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception ex) {
            HeralizCrates.LOGGER.error("[HeralizCrates] compte de clés de {} impossible à écrire : {}", c.uuid, ex.toString());
            return false;
        }
    }

    private static void journal(Compte c, String op, CrateType t, int n, String source) {
        try {
            Files.createDirectories(dossier().getParent());
            Files.writeString(dossier().getParent().resolve("cles_journal.log"),
                    java.time.LocalDateTime.now().withNano(0) + " " + c.nom + " " + c.uuid + " " + op + " " + n + " " + t.id
                            + " -> " + c.de(t) + " (" + source + ")\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) {
            HeralizCrates.LOGGER.warn("[HeralizCrates] journal des clés : {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ lecture
    public static int solde(UUID u, CrateType t) {
        return compte(u).de(t);
    }

    public static boolean ouvert(UUID u) {
        return !compte(u).ferme;
    }

    /** Numéro de la dernière conversion de clés physiques appliquée. */
    static int migration(UUID u) {
        return compte(u).migration;
    }

    // ------------------------------------------------------------------ écriture (fil serveur)
    /**
     * Ajoute {@code n} clés de cette caisse, écrit le compte et prévient le joueur s'il est en ligne. Faux (rien changé) si
     * le compte est fermé ou si l'écriture échoue.
     */
    public static boolean crediter(@Nullable MinecraftServer srv, UUID u, @Nullable String nom, CrateType t, int n, String source) {
        if (n <= 0) return false;
        Compte c = compte(u);
        if (c.ferme) return false;
        String ancienNom = c.nom;
        int avant = c.de(t);
        if (nom != null && !nom.isEmpty()) c.nom = nom;
        c.cles.put(t.id, (int) Math.min(MAX, (long) avant + n));
        if (!ecrire(c)) {
            c.cles.put(t.id, avant);
            c.nom = ancienNom;
            return false;
        }
        journal(c, "+", t, n, source);
        envoyer(srv, u);
        return true;
    }

    /**
     * Comme {@link #crediter}, mais une seule fois pour un même {@code id} (26/10, récompenses des quêtes du jour) : l'id
     * est écrit avec les clés, dans la même écriture ; redemander la même remise (après un plantage, par exemple) ne
     * crédite rien et rend vrai. Faux (rien changé) si le compte est fermé ou si l'écriture échoue.
     */
    public static boolean crediterUneFois(@Nullable MinecraftServer srv, UUID u, @Nullable String nom, CrateType t, int n, String source, String id) {
        if (n <= 0 || id == null || id.isEmpty()) return false;
        Compte c = compte(u);
        if (c.ferme) return false;
        if (c.remises.contains(id)) return true;
        String ancienNom = c.nom;
        int avant = c.de(t);
        if (nom != null && !nom.isEmpty()) c.nom = nom;
        c.cles.put(t.id, (int) Math.min(MAX, (long) avant + n));
        c.remises.add(id);
        String sortie = null;
        if (c.remises.size() > REMISES_MAX) {
            sortie = c.remises.iterator().next();
            c.remises.remove(sortie);
        }
        if (!ecrire(c)) {
            c.cles.put(t.id, avant);
            c.nom = ancienNom;
            c.remises.remove(id);
            if (sortie != null) {
                // rare : on remet la plus ancienne en tête
                java.util.List<String> l = new java.util.ArrayList<>(c.remises);
                c.remises.clear();
                c.remises.add(sortie);
                c.remises.addAll(l);
            }
            return false;
        }
        journal(c, "+", t, n, source + " #" + id);
        envoyer(srv, u);
        return true;
    }

    /**
     * Prend {@code n} clés de cette caisse si le joueur les a, et écrit le compte AVANT de rendre vrai. Faux (rien pris)
     * sinon. Fil serveur : deux demandes ne peuvent pas prendre la même clé.
     */
    public static boolean consommer(@Nullable MinecraftServer srv, UUID u, CrateType t, int n, String source) {
        if (n <= 0) return false;
        Compte c = compte(u);
        if (c.ferme) return false;
        int avant = c.de(t);
        if (avant < n) return false;
        c.cles.put(t.id, avant - n);
        if (!ecrire(c)) {
            c.cles.put(t.id, avant);
            return false;
        }
        journal(c, "-", t, n, source);
        envoyer(srv, u);
        return true;
    }

    /**
     * Applique la conversion numéro {@code numero} (clés physiques retirées d'un joueur) : rien si elle l'a déjà été (numéro
     * plus petit ou égal au dernier appliqué). Vrai si elle est appliquée (maintenant ou avant).
     */
    static boolean appliquerMigration(@Nullable MinecraftServer srv, UUID u, @Nullable String nom, int numero, Map<CrateType, Integer> cles, String source) {
        Compte c = compte(u);
        if (c.ferme) return false;
        if (numero <= c.migration) return true;
        Map<String, Integer> avant = new LinkedHashMap<>(c.cles);
        int migAvant = c.migration;
        String nomAvant = c.nom;
        if (nom != null && !nom.isEmpty()) c.nom = nom;
        cles.forEach((t, n) -> c.cles.put(t.id, (int) Math.min(MAX, (long) c.de(t) + Math.max(0, n))));
        c.migration = numero;
        if (!ecrire(c)) {
            c.cles.clear();
            c.cles.putAll(avant);
            c.migration = migAvant;
            c.nom = nomAvant;
            return false;
        }
        cles.forEach((t, n) -> journal(c, "+", t, n, source + " #" + numero));
        envoyer(srv, u);
        return true;
    }

    /**
     * Remise à zéro d'un joueur ({@code /wipe}) : les clés des caisses de jeu partent (quêtes, vote, événement, boss,
     * chantier, trauma, largage), comme avant quand elles étaient dans l'inventaire ; les clés des caisses cosmétiques
     * (Gemme, Camo et emotes, vendues en boutique, et stickers) sont gardées. Le numéro de conversion reste.
     */
    public static boolean wipe(@Nullable MinecraftServer srv, UUID u) {
        Compte c = compte(u);
        if (c.ferme) return false;
        Map<String, Integer> avant = new LinkedHashMap<>(c.cles);
        boolean change = false;
        for (CrateType t : CrateType.values()) {
            if (!GARDEES_AU_WIPE.contains(t) && c.de(t) > 0) {
                c.cles.remove(t.id);
                change = true;
            }
        }
        if (!change) return true;
        if (!ecrire(c)) {
            c.cles.clear();
            c.cles.putAll(avant);
            return false;
        }
        for (CrateType t : CrateType.values()) {
            int n = avant.getOrDefault(t.id, 0);
            if (n > 0 && !GARDEES_AU_WIPE.contains(t)) journal(c, "-", t, n, "wipe");
        }
        envoyer(srv, u);
        return true;
    }

    /** Clés gardées par {@code /wipe} : celles des caisses cosmétiques (achetées en boutique ou sans effet sur le jeu). */
    public static final Set<CrateType> GARDEES_AU_WIPE = Set.of(CrateType.GEMME, CrateType.CAMO, CrateType.SPECTACLE, CrateType.STICKER);

    // ------------------------------------------------------------------ affichage
    /** Envoie au joueur (s'il est en ligne) toutes ses clés. */
    public static void envoyer(@Nullable MinecraftServer srv, UUID u) {
        if (srv == null) return;
        ServerPlayer p = srv.getPlayerList().getPlayer(u);
        if (p != null) envoyer(p);
    }

    public static void envoyer(ServerPlayer p) {
        Compte c = compte(p.getUUID());
        Map<String, Integer> m = new LinkedHashMap<>();
        for (CrateType t : CrateType.values()) {
            int n = c.de(t);
            if (n > 0) m.put(t.id, n);
        }
        HzNet.toPlayer(p, new Packets.Solde(m, !c.ferme));
    }

    /** Toutes les clés d'un joueur (commande du staff). */
    public static Map<CrateType, Integer> tout(UUID u) {
        Compte c = compte(u);
        Map<CrateType, Integer> m = new LinkedHashMap<>();
        for (CrateType t : CrateType.values()) {
            int n = c.de(t);
            if (n > 0) m.put(t, n);
        }
        return m;
    }
}
