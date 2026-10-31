package fr.heralisz.clans.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Json;
import fr.heralisz.clans.model.SourceXp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

/**
 * Regles de l'XP de clan (31/10, demande du proprietaire : « montee de niveau beaucoup plus difficile » et « empecher de
 * progresser trop vite en repetant une seule activite ») : plafond par clan, par source et par jour (heure de Paris).
 * Tout est dans {@code config/heralisz_clans/niveaux.json} (section {@code plafonds_jour}, ajoutee au fichier si elle
 * manque), relu par {@code /clan admin recharger}.
 * Le gain de chaque action reste dans {@code heralisz_clans-common.toml} ([xp] et xp_heure_valeur) ; les quetes dans
 * quetes.json.
 * <p>
 * Plafond d'une source : {@code plafond} XP par jour donnes en entier, puis {@code au_dela_pct} % du reste (0 : plus rien ;
 * plafond 0 : sans limite). Les compteurs sont gardes dans le clan ({@link Clan#xpJour}) : un redemarrage ne les remet pas
 * a zero. Le staff ({@link SourceXp#ADMIN}) n'est jamais plafonne. Serveur seulement, toujours sur le fil du serveur.
 */
public final class XpClan {
    private XpClan() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int N = SourceXp.values().length;

    /** Plafonds par defaut (XP par clan et par jour) et part donnee au-dela (%), index = ordinal de {@link SourceXp}. */
    private static final long[] PLAFOND_DEFAUT = new long[N];
    private static final int[] AU_DELA_DEFAUT = new int[N];

    static {
        PLAFOND_DEFAUT[SourceXp.QUETE.ordinal()] = 0;            // deja limitees par le tirage (3 par jour, 2 par semaine)
        PLAFOND_DEFAUT[SourceXp.CAPTURE.ordinal()] = 450;        // 3 captures a 150
        PLAFOND_DEFAUT[SourceXp.TENUE.ordinal()] = 600;          // 2 zones de valeur 5 tenues 6 h
        PLAFOND_DEFAUT[SourceXp.RAVITAILLEMENT.ordinal()] = 300; // 3 signaux a 100
        PLAFOND_DEFAUT[SourceXp.JOUEUR.ordinal()] = 150;         // 30 joueurs a 5
        PLAFOND_DEFAUT[SourceXp.ZOMBIE.ordinal()] = 400;         // 400 zombies a 1
        PLAFOND_DEFAUT[SourceXp.BOSS.ordinal()] = 150;           // 3 boss a 50
        PLAFOND_DEFAUT[SourceXp.DON.ordinal()] = 3000;           // un objet Unique et un peu plus...
        AU_DELA_DEFAUT[SourceXp.DON.ordinal()] = 25;             // ... puis le quart
    }

    private static final long[] plafond = PLAFOND_DEFAUT.clone();
    private static final int[] auDelaPct = AU_DELA_DEFAUT.clone();
    // ------------------------------------------------------------------ jour (heure de Paris)
    private static long jour = Long.MIN_VALUE, finJourMs;

    /** Numero du jour en cours a Paris (jours depuis 1970) ; recalcule seulement a minuit. */
    static long jour(long now) {
        if (now >= finJourMs || jour == Long.MIN_VALUE) {
            LocalDate d = Instant.ofEpochMilli(now).atZone(PARIS).toLocalDate();
            jour = d.toEpochDay();
            finJourMs = d.plusDays(1).atStartOfDay(PARIS).toInstant().toEpochMilli();
        }
        return jour;
    }

    /** Remet les compteurs du clan a zero si le jour a change. */
    private static void caler(Clan c, long now) {
        long j = jour(now);
        if (c.xpJourNumero != j) {
            Arrays.fill(c.xpJour, 0);
            c.xpJourNumero = j;
        }
    }

    // ------------------------------------------------------------------ plafonds
    /** Plafond du jour d'une source (0 = sans limite ; toujours 0 pour le staff). */
    public static long plafond(SourceXp s) {
        return s == SourceXp.ADMIN ? 0 : plafond[s.ordinal()];
    }

    public static int auDelaPct(SourceXp s) {
        return auDelaPct[s.ordinal()];
    }

    /** XP deja proposee aujourd'hui pour cette source (avant plafond). */
    public static long aujourdhui(Clan c, SourceXp s, long now) {
        caler(c, now);
        return c.xpJour[s.ordinal()];
    }

    /** XP que le clan recevrait pour {@code xp} proposee maintenant (rien n'est compte). */
    public static long apercu(Clan c, SourceXp s, long xp, long now) {
        if (xp <= 0) return 0;
        long p = plafond(s);
        if (p <= 0) return xp;
        long deja = aujourdhui(c, s, now);
        long plein = Math.min(xp, Math.max(0, p - deja));
        return plein + (xp - plein) * auDelaPct(s) / 100;
    }

    /**
     * Compte {@code xp} proposee pour cette source et renvoie l'XP a donner au clan (plafond du jour applique). Le
     * compteur garde l'XP proposee (avant reduction) : c'est elle qui dit ou en est le clan par rapport au plafond.
     */
    static long accorder(Clan c, SourceXp s, long xp, long now) {
        if (xp <= 0) return 0;
        long donne = apercu(c, s, xp, now);
        if (s != SourceXp.ADMIN) c.xpJour[s.ordinal()] = Math.min(Long.MAX_VALUE / 2, c.xpJour[s.ordinal()] + xp);
        return donne;
    }

    /** Vrai si l'XP proposee {@code xp} vient de faire passer le compteur du jour au-dessus du plafond de la source. */
    static boolean vientDePasser(Clan c, SourceXp s, long xp) {
        long p = plafond(s);
        if (p <= 0 || s == SourceXp.ADMIN) return false;
        long apres = c.xpJour[s.ordinal()];
        return apres >= p && apres - xp < p;
    }

    // ------------------------------------------------------------------ gains (affichage)
    /** Gain d'une action par source, tel que le serveur le donne (0 = variable : quetes, dons). */
    public static long gain(SourceXp s) {
        return switch (s) {
            case CAPTURE -> ClansConfig.XP_CAPTURE.get();
            case TENUE -> ClansConfig.XP_HEURE_VALEUR.get();
            case RAVITAILLEMENT -> ClansConfig.XP_RAVITAILLEMENT.get();
            case JOUEUR -> ClansConfig.XP_KILL_JOUEUR.get();
            case ZOMBIE -> ClansConfig.XP_KILL_ZOMBIE.get();
            case BOSS -> ClansConfig.XP_BOSS.get();
            case QUETE, DON, ADMIN -> 0;
        };
    }

    // ------------------------------------------------------------------ fichier
    /**
     * Lit la section {@code plafonds_jour} de niveaux.json (apres {@code Niveaux.charger}, qui ecrit le fichier s'il
     * manque) ; absente, elle prend les valeurs par defaut et est ajoutee au fichier. Fichier illisible : valeurs par
     * defaut, fichier laisse tel quel.
     */
    public static void charger(Path dossierConfig) {
        Path f = dossierConfig.resolve("niveaux.json");
        System.arraycopy(PLAFOND_DEFAUT, 0, plafond, 0, N);
        System.arraycopy(AU_DELA_DEFAUT, 0, auDelaPct, 0, N);
        JsonObject o;
        try {
            o = Files.exists(f) ? JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
        } catch (IOException | RuntimeException ex) {
            HeraliszClans.LOG.error("[clans] niveaux.json illisible : plafonds d'XP par defaut", ex);
            return;
        }
        boolean ajoute = false;
        if (o.has("plafonds_jour") && o.get("plafonds_jour").isJsonObject()) {
            JsonObject pj = o.getAsJsonObject("plafonds_jour");
            for (SourceXp s : SourceXp.values()) {
                if (s == SourceXp.ADMIN || !pj.has(s.id()) || !pj.get(s.id()).isJsonObject()) continue;
                JsonObject r = pj.getAsJsonObject(s.id());
                plafond[s.ordinal()] = Math.max(0, Json.lng(r, "plafond", PLAFOND_DEFAUT[s.ordinal()]));
                auDelaPct[s.ordinal()] = Math.max(0, Math.min(100, Json.integer(r, "au_dela_pct", AU_DELA_DEFAUT[s.ordinal()])));
            }
        } else {
            o.add("plafonds_jour", plafondsJson());
            ajoute = true;
        }
        if (ajoute) {
            try {
                Files.createDirectories(dossierConfig);
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
                HeraliszClans.LOG.info("[clans] niveaux.json : plafonds d'XP du jour ajoutes (valeurs par defaut)");
            } catch (IOException ex) {
                HeraliszClans.LOG.warn("[clans] niveaux.json : ajout des plafonds impossible ({})", ex.toString());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (SourceXp s : SourceXp.values()) {
            if (s == SourceXp.ADMIN) continue;
            sb.append(' ').append(s.id()).append('=').append(plafond[s.ordinal()] <= 0 ? "libre" : String.valueOf(plafond[s.ordinal()]));
            if (auDelaPct[s.ordinal()] > 0) sb.append("(+").append(auDelaPct[s.ordinal()]).append("%)");
        }
        HeraliszClans.LOG.info("[clans] XP de clan, plafonds du jour :{}", sb);
    }

    static JsonObject plafondsJson() {
        JsonObject pj = new JsonObject();
        pj.addProperty("_aide", "XP de clan par source, par clan et par jour (heure de Paris) : plafond = XP donnee en entier,"
                + " au_dela_pct = part donnee au-dela (0 : plus rien) ; plafond 0 = sans limite. Gains par action :"
                + " heralisz_clans-common.toml ([xp], xp_heure_valeur) et quetes.json.");
        for (SourceXp s : SourceXp.values()) {
            if (s == SourceXp.ADMIN) continue;
            JsonObject r = new JsonObject();
            r.addProperty("plafond", PLAFOND_DEFAUT[s.ordinal()]);
            r.addProperty("au_dela_pct", AU_DELA_DEFAUT[s.ordinal()]);
            pj.add(s.id(), r);
        }
        return pj;
    }
}
