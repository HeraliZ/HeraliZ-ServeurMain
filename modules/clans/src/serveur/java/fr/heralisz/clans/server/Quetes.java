package fr.heralisz.clans.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Json;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.QueteEtat;
import fr.heralisz.clans.network.Packets;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Quetes de clan : catalogue {@code config/heralisz_clans/quetes.json} (ecrit avec les valeurs par defaut s'il manque ;
 * meme contenu que {@code tools/clans/gen_quetes.py}), tirage de 3 quetes « jour » a 06:00 et 2 quetes « semaine » le
 * lundi 06:00 (heure du serveur, config [quetes]), progression commune au clan, achevement automatique : XP au clan,
 * argent en banque, message aux membres en ligne, ligne de journal.
 *
 * <p>Fichier : un tableau d'entrees {@code {id, type, objectif, xp, argent, periode: "jour"|"semaine", titre_fr, titre_en}}.
 * Une entree invalide (type inconnu, objectif < 1, id en double) est ignoree avec un avertissement ; un id > 64 ou un
 * titre > 96 caracteres est coupe (limites reseau). DEPOTS_BANQUE suit le net depose de la periode
 * ({@link #mouvementBanque}), pas la somme des depots.
 */
public final class Quetes {
    private Quetes() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random ALEA = new Random();
    private static final List<QueteEtat> JOUR = new ArrayList<>();
    private static final List<QueteEtat> SEMAINE = new ArrayList<>();
    private static boolean chargee;
    /**
     * Les boss sont sur le serveur (jar heraliz_boss) : sinon les quetes KILL_BOSS ne sont ni tirees ni gardees en cours
     * (25/10 : boss mis de cote pour l'ouverture ; elles reviennent seules avec le jar).
     */
    private static boolean bossPresents = true;

    private static boolean disponible(QueteEtat.Type type) {
        return type != QueteEtat.Type.KILL_BOSS || bossPresents;
    }

    /** Une entree du catalogue par defaut. */
    private record Entree(String id, QueteEtat.Type type, long objectif, int xp, long argent, QueteEtat.Periode periode, String fr, String en) {
        QueteEtat etat() {
            return new QueteEtat(id, type, periode, objectif, xp, argent, fr, en, 0, false, 0);
        }

        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("type", type.name());
            o.addProperty("objectif", objectif);
            o.addProperty("xp", xp);
            o.addProperty("argent", argent);
            o.addProperty("periode", periode == QueteEtat.Periode.JOUR ? "jour" : "semaine");
            o.addProperty("titre_fr", fr);
            o.addProperty("titre_en", en);
            return o;
        }
    }

    private static Entree jour(String id, QueteEtat.Type t, long obj, int xp, long argent, String fr, String en) {
        return new Entree(id, t, obj, xp, argent, QueteEtat.Periode.JOUR, fr, en);
    }

    private static Entree semaine(String id, QueteEtat.Type t, long obj, int xp, long argent, String fr, String en) {
        return new Entree(id, t, obj, xp, argent, QueteEtat.Periode.SEMAINE, fr, en);
    }

    /** Catalogue par defaut (identique a tools/clans/gen_quetes.py : 14 « jour », 11 « semaine »). */
    static List<Entree> defaut() {
        List<Entree> l = new ArrayList<>();
        l.add(jour("j_rodeurs_150", QueteEtat.Type.KILL_ZOMBIES, 150, 300, 400, "Abattre 150 rôdeurs", "Kill 150 walkers"));
        l.add(jour("j_rodeurs_300", QueteEtat.Type.KILL_ZOMBIES, 300, 500, 700, "Abattre 300 rôdeurs", "Kill 300 walkers"));
        l.add(jour("j_joueurs_5", QueteEtat.Type.KILL_JOUEURS, 5, 350, 500, "Tuer 5 joueurs", "Kill 5 players"));
        l.add(jour("j_joueurs_10", QueteEtat.Type.KILL_JOUEURS, 10, 600, 900, "Tuer 10 joueurs", "Kill 10 players"));
        l.add(jour("j_boss_1", QueteEtat.Type.KILL_BOSS, 1, 400, 600, "Vaincre un boss", "Defeat a boss"));
        l.add(jour("j_loot_60", QueteEtat.Type.LOOT_OUVERTS, 60, 250, 300, "Fouiller 60 caches", "Search 60 loot spots"));
        l.add(jour("j_loot_120", QueteEtat.Type.LOOT_OUVERTS, 120, 400, 500, "Fouiller 120 caches", "Search 120 loot spots"));
        l.add(jour("j_temps_180", QueteEtat.Type.TEMPS_JEU_MIN, 180, 200, 250, "Jouer 180 min sur la carte", "Play 180 min on the map"));
        l.add(jour("j_degats_5000", QueteEtat.Type.DEGATS, 5000, 300, 400, "Infliger 5 000 dégâts", "Deal 5,000 damage"));
        l.add(jour("j_capture_1", QueteEtat.Type.CAPTURES, 1, 350, 500, "Capturer une zone", "Capture a zone"));
        l.add(jour("j_tenir_60", QueteEtat.Type.TENIR_ZONE_MIN, 60, 300, 400, "Tenir une zone 60 minutes", "Hold a zone for 60 minutes"));
        l.add(jour("j_ravito_1", QueteEtat.Type.RAVITAILLEMENTS, 1, 300, 400, "Lancer un ravitaillement", "Call a supply drop"));
        l.add(jour("j_depot_1500", QueteEtat.Type.DEPOTS_BANQUE, 1500, 200, 300, "Déposer 1 500 hérals", "Deposit 1,500 herals"));
        l.add(jour("j_distance_10000", QueteEtat.Type.DISTANCE_M, 10000, 200, 250, "Parcourir 10 km à pied", "Walk 10 km"));
        l.add(semaine("s_rodeurs_2000", QueteEtat.Type.KILL_ZOMBIES, 2000, 2500, 3000, "Abattre 2 000 rôdeurs", "Kill 2,000 walkers"));
        l.add(semaine("s_joueurs_25", QueteEtat.Type.KILL_JOUEURS, 25, 2000, 2500, "Tuer 25 joueurs", "Kill 25 players"));
        l.add(semaine("s_boss_5", QueteEtat.Type.KILL_BOSS, 5, 2500, 3000, "Vaincre 5 boss", "Defeat 5 bosses"));
        l.add(semaine("s_loot_600", QueteEtat.Type.LOOT_OUVERTS, 600, 1800, 2200, "Fouiller 600 caches", "Search 600 loot spots"));
        l.add(semaine("s_temps_1500", QueteEtat.Type.TEMPS_JEU_MIN, 1500, 1500, 1800, "Jouer 1 500 min sur la carte", "Play 1,500 min on the map"));
        l.add(semaine("s_degats_40000", QueteEtat.Type.DEGATS, 40000, 2000, 2500, "Infliger 40 000 dégâts", "Deal 40,000 damage"));
        l.add(semaine("s_captures_3", QueteEtat.Type.CAPTURES, 3, 2200, 2800, "Capturer 3 zones", "Capture 3 zones"));
        l.add(semaine("s_tenir_120", QueteEtat.Type.TENIR_ZONE_MIN, 120, 1500, 2000, "Tenir une zone 120 minutes", "Hold a zone for 120 minutes"));
        l.add(semaine("s_ravito_4", QueteEtat.Type.RAVITAILLEMENTS, 4, 2000, 2500, "Lancer 4 ravitaillements", "Call 4 supply drops"));
        l.add(semaine("s_depot_15000", QueteEtat.Type.DEPOTS_BANQUE, 15000, 1500, 2000, "Déposer 15 000 hérals", "Deposit 15,000 herals"));
        l.add(semaine("s_distance_80000", QueteEtat.Type.DISTANCE_M, 80000, 1200, 1500, "Parcourir 80 km à pied", "Walk 80 km"));
        return l;
    }

    // ------------------------------------------------------------------ catalogue
    static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("quetes.json");
    }

    /** Charge le catalogue (l'ecrit avec les valeurs par defaut s'il manque). */
    public static void charger() {
        JOUR.clear();
        SEMAINE.clear();
        bossPresents = net.minecraftforge.fml.ModList.get().isLoaded("heraliz_boss");
        int sansBoss = 0;
        Path f = fichier();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                JsonArray a = new JsonArray();
                for (Entree e : defaut()) a.add(e.json());
                Files.writeString(f, GSON.toJson(a), StandardCharsets.UTF_8);
                HeraliszClans.LOG.info("[clans] quetes.json ecrit avec le catalogue par defaut");
            }
            String texte = Files.readString(f, StandardCharsets.UTF_8);
            // ancien nom de la monnaie dans les titres par defaut (« jetons » -> « hérals ») : fichier mis a jour une fois
            String converti = texte;
            for (String ancien : new String[]{"Déposer 1 500 jetons", "Déposer 15 000 jetons", "Deposit 1,500 tokens", "Deposit 15,000 tokens"}) {
                converti = converti.replace("\"" + ancien + "\"", "\"" + QueteEtat.nouveauNom(ancien) + "\"");
            }
            if (!converti.equals(texte)) {
                Files.writeString(f, converti, StandardCharsets.UTF_8);
                texte = converti;
                HeraliszClans.LOG.info("[clans] quetes.json : titres convertis vers le nouveau nom de la monnaie (hérals)");
            }
            JsonElement root = JsonParser.parseString(texte);
            JsonArray a = root.isJsonArray() ? root.getAsJsonArray() : Json.arr(root.getAsJsonObject(), "quetes");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (JsonElement e : a) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = Json.str(o, "id", "");
                QueteEtat.Type type;
                try {
                    type = QueteEtat.Type.valueOf(Json.str(o, "type", "").toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    HeraliszClans.LOG.warn("[clans] quetes.json : type inconnu pour « {} », ignoree", id);
                    continue;
                }
                long objectif = Json.lng(o, "objectif", 0);
                if (id.isEmpty() || objectif < 1 || !ids.add(id)) {
                    HeraliszClans.LOG.warn("[clans] quetes.json : entree « {} » invalide (id vide/double ou objectif < 1), ignoree", id);
                    continue;
                }
                String periode = Json.str(o, "periode", "jour").toLowerCase(java.util.Locale.ROOT);
                QueteEtat.Periode per = periode.startsWith("sem") || periode.startsWith("week") ? QueteEtat.Periode.SEMAINE : QueteEtat.Periode.JOUR;
                String titreFr = Json.str(o, "titre_fr", id), titreEn = Json.str(o, "titre_en", null);
                // longueurs bornees (reseau) : coupees par QueteEtat, on previent l'admin
                if (id.length() > QueteEtat.ID_MAX || titreFr.length() > QueteEtat.TITRE_MAX || (titreEn != null && titreEn.length() > QueteEtat.TITRE_MAX)) {
                    HeraliszClans.LOG.warn("[clans] quetes.json : « {} » : id > {} ou titre > {} caracteres, coupe", id, QueteEtat.ID_MAX, QueteEtat.TITRE_MAX);
                }
                if (!disponible(type)) {
                    sansBoss++;
                    continue;
                }
                QueteEtat q = new QueteEtat(id, type, per, objectif, Json.integer(o, "xp", 0), Json.lng(o, "argent", 0), titreFr, titreEn, 0, false, 0);
                (per == QueteEtat.Periode.JOUR ? JOUR : SEMAINE).add(q);
            }
        } catch (IOException | RuntimeException ex) {
            HeraliszClans.LOG.error("[clans] quetes.json illisible, catalogue par defaut garde", ex);
            JOUR.clear();
            SEMAINE.clear();
            for (Entree e : defaut()) if (disponible(e.type)) (e.periode == QueteEtat.Periode.JOUR ? JOUR : SEMAINE).add(e.etat());
        }
        chargee = true;
        if (sansBoss > 0) HeraliszClans.LOG.info("[clans] boss absents du serveur : {} quete(s) « vaincre un boss » ecartee(s)", sansBoss);
        HeraliszClans.LOG.info("[clans] catalogue de quetes : {} jour, {} semaine", JOUR.size(), SEMAINE.size());
    }

    /** Relit le catalogue (les quetes deja tirees par les clans ne changent pas). */
    public static void recharger() {
        charger();
    }

    public static int tailleJour() {
        return JOUR.size();
    }

    public static int tailleSemaine() {
        return SEMAINE.size();
    }

    // ------------------------------------------------------------------ horaires
    private static ZonedDateTime maintenant() {
        return ZonedDateTime.now(ZoneId.systemDefault());
    }

    /** Prochain tirage quotidien : aujourd'hui a l'heure configuree si elle n'est pas passee, sinon demain. */
    public static long prochainJour(long nowMs) {
        ZonedDateTime now = maintenant();
        ZonedDateTime t = now.withHour(ClansConfig.QUETES_HEURE.get()).withMinute(0).withSecond(0).withNano(0);
        if (!t.isAfter(now)) t = t.plusDays(1);
        return t.toInstant().toEpochMilli();
    }

    /** Prochain tirage hebdomadaire : le prochain jour configure (lundi par defaut) a l'heure configuree. */
    public static long prochaineSemaine(long nowMs) {
        ZonedDateTime now = maintenant();
        DayOfWeek jour = DayOfWeek.of(ClansConfig.QUETES_JOUR_SEMAINE.get());
        ZonedDateTime t = now.withHour(ClansConfig.QUETES_HEURE.get()).withMinute(0).withSecond(0).withNano(0);
        while (t.getDayOfWeek() != jour || !t.isAfter(now)) t = t.plusDays(1);
        return t.toInstant().toEpochMilli();
    }

    // ------------------------------------------------------------------ tirage
    /** Tire n quetes du catalogue, types differents autant que possible, en evitant les ids du tirage precedent. */
    private static List<QueteEtat> tirer(List<QueteEtat> catalogue, int n, List<QueteEtat> precedentes) {
        List<QueteEtat> pool = new ArrayList<>(catalogue);
        Collections.shuffle(pool, ALEA);
        List<QueteEtat> out = new ArrayList<>(n);
        EnumSet<QueteEtat.Type> types = EnumSet.noneOf(QueteEtat.Type.class);
        java.util.Set<String> anciens = new java.util.HashSet<>();
        for (QueteEtat q : precedentes) anciens.add(q.id);
        // 1er passage : types et ids nouveaux ; 2e : types nouveaux ; 3e : n'importe quoi
        for (int passe = 0; passe < 3 && out.size() < n; passe++) {
            for (QueteEtat q : pool) {
                if (out.size() >= n) break;
                if (out.contains(q)) continue;
                if (passe < 2 && types.contains(q.type)) continue;
                if (passe < 1 && anciens.contains(q.id)) continue;
                out.add(q);
                types.add(q.type);
            }
        }
        List<QueteEtat> copies = new ArrayList<>(out.size());
        for (QueteEtat q : out) copies.add(q.copie());
        return copies;
    }

    /** Premier tirage d'un clan (creation) : jour + semaine, horaires calcules. */
    public static void initialiser(Clan c, long now) {
        if (!chargee) charger();
        c.quetesJour.clear();
        c.quetesJour.addAll(tirer(JOUR, ClansConfig.QUETES_PAR_JOUR.get(), List.of()));
        c.quetesSemaine.clear();
        c.quetesSemaine.addAll(tirer(SEMAINE, ClansConfig.QUETES_PAR_SEMAINE.get(), List.of()));
        c.prochainResetJour = prochainJour(now);
        c.prochainResetSemaine = prochaineSemaine(now);
        c.depotNetJour = 0;
        c.depotNetSemaine = 0;
    }

    /**
     * Verifie les horaires d'un clan : nouveau tirage si l'heure est passee (ou si le clan n'a aucune quete alors que le
     * catalogue en a). Vrai si quelque chose a change.
     */
    public static boolean verifier(Clan c, long now) {
        if (!chargee) charger();
        boolean change = false;
        if (now >= c.prochainResetJour || (c.quetesJour.isEmpty() && !JOUR.isEmpty())) {
            List<QueteEtat> prec = new ArrayList<>(c.quetesJour);
            c.quetesJour.clear();
            c.quetesJour.addAll(tirer(JOUR, ClansConfig.QUETES_PAR_JOUR.get(), prec));
            c.prochainResetJour = prochainJour(now);
            c.depotNetJour = 0;
            change = true;
        }
        if (now >= c.prochainResetSemaine || (c.quetesSemaine.isEmpty() && !SEMAINE.isEmpty())) {
            List<QueteEtat> prec = new ArrayList<>(c.quetesSemaine);
            c.quetesSemaine.clear();
            c.quetesSemaine.addAll(tirer(SEMAINE, ClansConfig.QUETES_PAR_SEMAINE.get(), prec));
            c.prochainResetSemaine = prochaineSemaine(now);
            c.depotNetSemaine = 0;
            change = true;
        }
        return remplacerIndisponibles(c) || change;
    }

    /**
     * Quete en cours devenue impossible (boss retires du serveur) : remplacee par une autre du catalogue, d'un type et d'un
     * id que le clan n'a pas deja ; la progression des autres quetes est gardee.
     */
    private static boolean remplacerIndisponibles(Clan c) {
        if (bossPresents) return false;
        boolean change = false;
        for (int i = 0; i < 2; i++) {
            List<QueteEtat> liste = i == 0 ? c.quetesJour : c.quetesSemaine;
            for (int k = 0; k < liste.size(); k++) {
                QueteEtat q = liste.get(k);
                if (q.terminee || disponible(q.type)) continue;
                List<QueteEtat> pool = new ArrayList<>(i == 0 ? JOUR : SEMAINE);
                Collections.shuffle(pool, ALEA);
                QueteEtat choisie = null;
                for (int passe = 0; passe < 2 && choisie == null; passe++) {
                    for (QueteEtat r : pool) {
                        boolean pris = false;
                        for (QueteEtat x : liste) pris |= x.id.equals(r.id) || (passe == 0 && x.type == r.type);
                        if (!pris) {
                            choisie = r;
                            break;
                        }
                    }
                }
                if (choisie == null) continue;
                liste.set(k, choisie.copie());
                change = true;
                HeraliszClans.LOG.info("[clans] [{}] quete « {} » remplacee par « {} » (boss absents)", c.tag, q.id, choisie.id);
            }
        }
        return change;
    }

    /** Force un nouveau tirage (admin). */
    public static void retirer(Clan c, long now) {
        c.prochainResetJour = 0;
        c.prochainResetSemaine = 0;
        verifier(c, now);
    }

    // ------------------------------------------------------------------ progression
    /**
     * Fait avancer toutes les quetes en cours de ce type ; celles qui atteignent leur objectif sont terminees tout de
     * suite (XP, argent, notice, journal). Marque le clan a synchroniser. Ne fait rien si delta <= 0.
     */
    public static void progresser(Clan c, QueteEtat.Type type, long delta) {
        if (c == null || delta <= 0) return;
        boolean change = false, finie = false;
        for (int i = 0; i < 2; i++) {
            for (QueteEtat q : i == 0 ? c.quetesJour : c.quetesSemaine) {
                if (q.terminee || q.type != type) continue;
                q.progression = Math.min(q.objectif, q.progression + delta);
                change = true;
                if (q.progression >= q.objectif) {
                    terminer(c, q);
                    finie = true;
                }
            }
        }
        if (!change) return;
        ClanStore store = ClanStore.get();
        if (store != null) store.marquerDirty();
        if (finie) Sync.marquer(c);
        else Sync.marquerLent(c);
    }

    /**
     * Depot ou retrait en banque : met a jour le net depose de la periode (jamais negatif) et cale la progression des
     * quetes DEPOTS_BANQUE en cours dessus (elle peut donc reculer apres un retrait). Un cycle depot/retrait ne fait
     * rien gagner ; une quete deja terminee n'est pas remise en cause.
     */
    public static void mouvementBanque(Clan c, long delta) {
        if (c == null || delta == 0) return;
        c.depotNetJour = Math.max(0, c.depotNetJour + delta);
        c.depotNetSemaine = Math.max(0, c.depotNetSemaine + delta);
        boolean change = false, finie = false;
        for (int i = 0; i < 2; i++) {
            long net = i == 0 ? c.depotNetJour : c.depotNetSemaine;
            for (QueteEtat q : i == 0 ? c.quetesJour : c.quetesSemaine) {
                if (q.terminee || q.type != QueteEtat.Type.DEPOTS_BANQUE) continue;
                long prog = Math.min(q.objectif, net);
                if (prog == q.progression) continue;
                q.progression = prog;
                change = true;
                if (q.progression >= q.objectif) {
                    terminer(c, q);
                    finie = true;
                }
            }
        }
        ClanStore store = ClanStore.get();
        if (store != null) store.marquerDirty();
        if (!change) return;
        if (finie) Sync.marquer(c);
        else Sync.marquerLent(c);
    }

    private static void terminer(Clan c, QueteEtat q) {
        long now = System.currentTimeMillis();
        q.terminee = true;
        q.termineeLe = now;
        if (q.argent > 0) {
            c.banque = ClanService.plafonnerBanque(c.banque + q.argent);
            c.ajouterMouvement(new Mouvement(now, null, q.titreFr, q.argent, "quete"));
        }
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.QUETE, q.titreFr));
        ClanService.ajouterXp(c, q.xp, null);
        fr.heralisz.clans.saison.Saison.ajouterPve(c, null, "quete " + q.id, "quete_clan", 1);
        String argent = Economie.active().format(q.argent);
        ClanService.diffuserChat(c, Component.translatable("msg.quete_finie", q.titreFr, q.xp, argent));
        ClanService.diffuser(c, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.QUETE_FINIE, List.of(q.titreFr), "quete_finie", null));
        ClanStore store = ClanStore.get();
        if (store != null && q.argent > 0) store.sauvegarderMaintenant();
        HeraliszClans.LOG.info("[clans] [{}] quete « {} » terminee (+{} XP, +{})", c.tag, q.id, q.xp, q.argent);
    }
}
