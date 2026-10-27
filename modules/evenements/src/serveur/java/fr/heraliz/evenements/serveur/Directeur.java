package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.evenements.HeralizEvenements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Le Directeur d'événements (docs/EVENEMENTS.md §1) : lance tout seul les événements de la carte d'après le planning
 * ({@code config/heraliz_evenements/planning.json}, fichier du dépôt qui fait foi, relu par {@code /event recharger}),
 * vérifie les conditions communes (assez de joueurs sur la carte, pas de ClashWar, un seul exemplaire actif par type),
 * tient le journal. Hors événement, il ne fait qu'un contrôle par minute (le changement de minute est vu en une
 * comparaison par seconde) ; pendant un événement, il l'appelle une fois par seconde.
 */
public final class Directeur {
    private Directeur() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final Map<String, Evenement> EVENEMENTS = new LinkedHashMap<>();
    private static final Random ALEA = new Random();

    /**
     * Un créneau du planning : quel événement, quels jours, à quelles heures (heure de Paris), avec quelle chance ; ou,
     * avec {@code toutes_les} (25/10, demande du propriétaire : un largage toutes les 3 h 30), à intervalle régulier
     * ({@code intervalleMs} &gt; 0 : jours et heures ignorés). Le prochain départ est gardé dans {@link Etat} : un
     * redémarrage ne remet pas l'attente à zéro ; serveur éteint à l'heure prévue : départ {@code premierMs} après le
     * démarrage. Refusé (pas assez de joueurs, ClashWar, aucun lieu…) : 27/10, règle du propriétaire, jamais de nouvel
     * essai rapproché : l'attente repart pour un intervalle entier, comme si l'événement avait eu lieu (l'ancienne clé
     * {@code reessai_apres} est ignorée).
     */
    record Creneau(String evenement, Set<DayOfWeek> jours, List<LocalTime> heures, double probabilite, int joueursMin,
                   long intervalleMs, long premierMs) {
        boolean intervalle() {
            return intervalleMs > 0;
        }
    }

    private static boolean actif = true;
    private static final List<Creneau> PLANNING = new ArrayList<>();
    /** Créneaux déjà traités aujourd'hui (« largage|20:30 »), oubliés au changement de jour. */
    private static final Set<String> FAITS = new HashSet<>();
    private static String jourFaits = "";
    private static int ticks;
    private static long derniereMinute;

    static void enregistrer(Evenement e) {
        EVENEMENTS.put(e.id(), e);
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void demarrage(ServerAboutToStartEvent e) {
        Path dossier = e.getServer().getServerDirectory().toPath().resolve("heraliz_evenements");
        Outils.dossier = dossier;
        Etat.charger(dossier);
        Lieux.charger(dossier);
        ObjetsServeur.charger(dossier);
        charger();
        FAITS.clear();
        jourFaits = "";
        ticks = 0;
        derniereMinute = 0;
    }

    @SubscribeEvent
    public static void arret(ServerStoppingEvent e) {
        for (Evenement ev : EVENEMENTS.values()) {
            if (ev.actif()) ev.annuler(e.getServer(), "arret du serveur");
        }
        Etat.sauver();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null || ++ticks % 20 != 0) return;
        MinecraftServer server = e.getServer();
        for (Evenement ev : EVENEMENTS.values()) {
            if (!ev.actif()) continue;
            try {
                ev.seconde(server);
            } catch (RuntimeException ex) {
                HeralizEvenements.LOG.error("[evenements] {} en erreur : annule", ev.id(), ex);
                ev.annuler(server, "erreur");
            }
        }
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != derniereMinute) {
            derniereMinute = minute;
            // événements sans heure fixe (hélicoptère) : chacun son tirage, une comparaison d'heure par minute
            for (Evenement ev : EVENEMENTS.values()) {
                try {
                    ev.minute(server);
                } catch (RuntimeException ex) {
                    HeralizEvenements.LOG.error("[evenements] {} : planification en erreur", ev.id(), ex);
                }
            }
            planning(server);
        }
    }

    // ------------------------------------------------------------------ planning
    static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("heraliz_evenements").resolve("planning.json");
    }

    /** Relit le planning (l'écrit avec les valeurs par défaut s'il manque). Rend le nombre de créneaux. */
    static int charger() {
        PLANNING.clear();
        actif = true;
        Path f = fichier();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(defaut()), StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            lire(o);
        } catch (IOException | RuntimeException ex) {
            HeralizEvenements.LOG.error("[evenements] planning.json illisible : planning par defaut", ex);
            PLANNING.clear();
            lire(defaut());
        }
        long maintenant = System.currentTimeMillis();
        for (Creneau c : PLANNING) {
            if (!c.intervalle()) continue;
            // jamais prévu, ou prévu pendant que le serveur était éteint : premier départ un peu après le démarrage
            if (Etat.prochain(c.evenement()) <= maintenant) Etat.prochain(c.evenement(), maintenant + c.premierMs());
            HeralizEvenements.LOG.info("[evenements] {} toutes les {} min, prochain a {}", c.evenement(), c.intervalleMs() / 60_000L,
                    heure(Etat.prochain(c.evenement())));
        }
        for (Evenement ev : EVENEMENTS.values()) ev.recharger();
        ObjetsServeur.recharger();
        HeralizEvenements.LOG.info("[evenements] planning : {} creneau(x), {}", PLANNING.size(), actif ? "actif" : "coupe");
        return PLANNING.size();
    }

    private static void lire(JsonObject o) {
        actif = !o.has("actif") || o.get("actif").getAsBoolean();
        for (JsonElement el : o.getAsJsonArray("creneaux")) {
            try {
                JsonObject c = el.getAsJsonObject();
                Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
                if (c.has("jours")) for (JsonElement j : c.getAsJsonArray("jours")) jours.addAll(jours(j.getAsString()));
                List<LocalTime> heures = new ArrayList<>();
                if (c.has("heures")) for (JsonElement h : c.getAsJsonArray("heures")) heures.add(LocalTime.parse(h.getAsString()));
                double proba = c.has("probabilite") ? c.get("probabilite").getAsDouble() : 1;
                int min = c.has("joueurs_min") ? c.get("joueurs_min").getAsInt() : 0;
                long intervalle = c.has("toutes_les") ? duree(c.get("toutes_les").getAsString()) : 0;
                long premier = c.has("premier_apres") ? duree(c.get("premier_apres").getAsString()) : 30 * 60_000L;
                // 27/10 : plus de nouvel essai rapproché (règle du propriétaire) ; la clé d'un ancien fichier est seulement signalée
                if (c.has("reessai_apres")) {
                    HeralizEvenements.LOG.warn("[evenements] planning.json : reessai_apres ignore pour {} (un depart refuse attend"
                            + " l'intervalle normal)", c.has("evenement") ? c.get("evenement").getAsString() : "?");
                }
                if (c.has("toutes_les") && intervalle < 10 * 60_000L) throw new IllegalArgumentException("toutes_les : 0:10 au moins");
                if (intervalle == 0 && (jours.isEmpty() || heures.isEmpty())) throw new IllegalArgumentException("ni jours/heures ni toutes_les");
                PLANNING.add(new Creneau(c.get("evenement").getAsString(), jours, heures, Math.max(0, Math.min(1, proba)), Math.max(0, min),
                        intervalle, Math.max(60_000L, premier)));
            } catch (RuntimeException ex) {
                HeralizEvenements.LOG.warn("[evenements] planning.json : creneau illisible ignore ({}) : {}", ex.getMessage(), el);
            }
        }
    }

    /** « 3:30 » (heures:minutes) ou « 210 » (minutes) en millisecondes. */
    private static long duree(String s) {
        String t = s.trim();
        int i = t.indexOf(':');
        long minutes = i < 0 ? Long.parseLong(t) : Long.parseLong(t.substring(0, i)) * 60 + Long.parseLong(t.substring(i + 1));
        if (minutes < 0) throw new IllegalArgumentException("duree negative : " + s);
        return minutes * 60_000L;
    }

    private static String heure(long ms) {
        ZonedDateTime t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), Outils.PARIS);
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    private static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    private static Set<DayOfWeek> jours(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("tous")) return EnumSet.allOf(DayOfWeek.class);
        for (int i = 0; i < JOURS.length; i++) if (JOURS[i].equals(t)) return EnumSet.of(DayOfWeek.of(i + 1));
        return EnumSet.of(DayOfWeek.valueOf(t.toUpperCase(Locale.ROOT)));
    }

    private static JsonObject defaut() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Planning des evenements de la carte (heure de Paris). Un creneau a heures fixes : jours (tous, lundi..."
                + " dimanche) et heures (HH:MM). Ou a intervalle : toutes_les (H:MM), premier_apres (H:MM apres le demarrage si rien n'est"
                + " prevu, 0:30 par defaut). probabilite : chance qu'il parte (1 = toujours) ; joueurs_min : joueurs en survie sur la"
                + " carte au moins. Jamais pendant un ClashWar. Depart refuse (pas assez de joueurs, ClashWar, aucun lieu...) : pas de"
                + " nouvel essai rapproche, l'attente repart pour un intervalle entier (toutes_les), comme s'il avait eu lieu ; a heures"
                + " fixes, il attend le creneau suivant. Relu par /event recharger.");
        o.addProperty("actif", true);
        JsonArray a = new JsonArray();
        JsonObject c = new JsonObject();
        c.addProperty("evenement", "largage");
        c.addProperty("toutes_les", "3:30");
        c.addProperty("premier_apres", "0:30");
        c.addProperty("probabilite", 1.0);
        c.addProperty("joueurs_min", 4);
        a.add(c);
        o.add("creneaux", a);
        return o;
    }

    private static void planning(MinecraftServer server) {
        if (!actif || PLANNING.isEmpty()) return;
        ZonedDateTime now = ZonedDateTime.now(Outils.PARIS);
        String jour = now.toLocalDate().toString();
        if (!jour.equals(jourFaits)) {
            jourFaits = jour;
            FAITS.clear();
        }
        LocalTime minute = now.toLocalTime().withSecond(0).withNano(0);
        long maintenant = System.currentTimeMillis();
        for (Creneau c : PLANNING) {
            if (c.intervalle()) {
                if (maintenant < Etat.prochain(c.evenement())) continue;
                if (ALEA.nextDouble() > c.probabilite()) {
                    Etat.prochain(c.evenement(), maintenant + c.intervalleMs());
                    Outils.journal("planning : " + c.evenement() + " pas tire au sort, prochain a " + heure(Etat.prochain(c.evenement())));
                    continue;
                }
                String refus = lancer(server, c.evenement(), true, "planning", c.joueursMin());
                // 27/10 (règle du propriétaire : « pas de nouvel essai rapproché ») : parti ou refusé, l'attente repart pour un
                // intervalle entier, comme s'il avait eu lieu ; au prochain passage, il part si les conditions sont remplies
                Etat.prochain(c.evenement(), maintenant + c.intervalleMs());
                if (refus != null) {
                    Outils.journal("planning : " + c.evenement() + " pas lance (" + refus + "), prochain essai a "
                            + heure(Etat.prochain(c.evenement())) + " (intervalle normal)");
                }
                continue;
            }
            if (!c.jours().contains(now.getDayOfWeek()) || !c.heures().contains(minute)) continue;
            if (!FAITS.add(c.evenement() + "|" + minute)) continue;
            if (ALEA.nextDouble() > c.probabilite()) {
                Outils.journal("planning : " + c.evenement() + " de " + minute + " pas tire au sort");
                continue;
            }
            String refus = lancer(server, c.evenement(), true, "planning", c.joueursMin());
            if (refus != null) {
                long suivant = creneauSuivant(c, now);
                Outils.journal("planning : " + c.evenement() + " de " + minute + " pas lance (" + refus + "), prochain essai "
                        + (suivant > 0 ? "a " + heure(suivant) + " (creneau suivant)" : ": aucun creneau dans les 7 jours"));
            }
        }
    }

    /** Prochain créneau à heure fixe après {@code now} (heure en ms, 0 : aucun dans les 7 jours) : pour le journal. */
    private static long creneauSuivant(Creneau c, ZonedDateTime now) {
        long apres = now.toInstant().toEpochMilli() + 60_000L, mieux = 0;
        for (int jour = 0; jour <= 7; jour++) {
            ZonedDateTime d = now.plusDays(jour);
            if (!c.jours().contains(d.getDayOfWeek())) continue;
            for (LocalTime h : c.heures()) {
                long t = d.with(h).toInstant().toEpochMilli();
                if (t >= apres && (mieux == 0 || t < mieux)) mieux = t;
            }
        }
        return mieux;
    }

    // ------------------------------------------------------------------ lancement
    /**
     * Lance un événement. {@code points} : il rapporte des points de saison (toujours pour le planning ; le staff le
     * demande avec « avec_points »). Rend la raison du refus, ou null s'il est parti.
     */
    @Nullable
    static String lancer(MinecraftServer server, String id, boolean points, String par, int joueursMin) {
        Evenement e = EVENEMENTS.get(id);
        if (e == null) return "evenement inconnu : " + id;
        if (e.actif()) return "deja en cours";
        if (Outils.clashwarEnCours()) return "ClashWar en cours";
        int n = Outils.joueursCarte(server);
        if (n < joueursMin) return n + " joueur(s) sur la carte, il en faut " + joueursMin;
        String refus = e.refus(server);
        if (refus != null) return refus;
        int numero = Etat.suivant();
        if (!e.demarrer(server, numero, points, par)) return "aucun lieu valide";
        return null;
    }

    /** Coffre d'événement ouvert pour la première fois (caisse de largage…) : à l'événement de ce type ({@code EvenementsApi}). */
    public static void coffreOuvert(ServerPlayer p, String id) {
        int i = id.indexOf('#');
        Evenement e = EVENEMENTS.get(i < 0 ? id : id.substring(0, i));
        if (e != null) e.coffreOuvert(p, id);
    }

    // ------------------------------------------------------------------ agenda (joueurs)
    /** Les prochains créneaux (dans les 24 h), « 20:30 largage », pour {@code /event}. */
    static List<String[]> prochains(int max) {
        List<String[]> l = new ArrayList<>();
        if (!actif) return l;
        ZonedDateTime now = ZonedDateTime.now(Outils.PARIS);
        long maintenant = System.currentTimeMillis(), limite = maintenant + 24 * 3_600_000L;
        record Depart(long ms, String evenement) {
        }
        List<Depart> tous = new ArrayList<>();
        for (Creneau c : PLANNING) {
            if (c.intervalle()) {
                for (long t = Math.max(Etat.prochain(c.evenement()), maintenant); t < limite; t += c.intervalleMs()) {
                    tous.add(new Depart(t, c.evenement()));
                }
                continue;
            }
            for (int jour = 0; jour <= 1; jour++) {
                ZonedDateTime d = now.plusDays(jour);
                if (!c.jours().contains(d.getDayOfWeek())) continue;
                for (LocalTime h : c.heures()) {
                    long t = d.with(h).toInstant().toEpochMilli();
                    if (t > maintenant && t < limite) tous.add(new Depart(t, c.evenement()));
                }
            }
        }
        tous.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        for (Depart d : tous) {
            if (l.size() >= max) break;
            l.add(new String[]{heure(d.ms()), d.evenement()});
        }
        return l;
    }
}
