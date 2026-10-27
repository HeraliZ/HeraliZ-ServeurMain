package fr.heralisz.clans.saison;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saison des clans (docs/SAISON_CLANS.md) : points de saison des clans en trois classements (PVE, PVP, events) et un
 * classement general (somme des places). Regles :
 * <ul>
 * <li>un clan sans coeur pose (CRac) ne gagne rien et n'est pas classe ;</li>
 * <li>PVE : bareme par source, petites actions plafonnees par joueur et par jour, boss a rendement decroissant par clan et
 * par jour ;</li>
 * <li>PVP : +1 / -1, jamais sous 0 pour un clan ; rien entre membres d'un meme clan, joueurs de la meme adresse IP,
 * victime sans arme ni protection (rarete minimale, objets du /kit exclus) ; meme victime : x1, x0,5 puis 0 sur 6 h ;</li>
 * <li>events : grille de places, participation, multiplicateur par event ;</li>
 * <li>prise du coeur pendant la Purge (CRac) : -15 % pour le clan pris, +10 % pour l'attaquant, une fois par Purge ;</li>
 * <li>coeur deplace et pas repose a temps : le clan perd tout ;</li>
 * <li>les {@link Bareme#verrouJours} derniers jours, personne ne rejoint un clan.</li>
 * </ul>
 * Chaque point est note dans heralisz_clans/saison_journal.log (qui, quand, pourquoi, combien). Fil serveur seulement.
 */
public final class Saison {
    private Saison() {
    }

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(PARIS);
    public static final String[] CATEGORIES = {"pve", "pvp", "events"};

    static Bareme bareme = Bareme.defaut();
    private static SaisonDonnees d = new SaisonDonnees();
    private static Path dossier;
    private static boolean sale;
    private static int ticks;
    /** Classement en cache (refait au plus une fois par seconde quand un score change). */
    private static List<Ligne> cache = List.of();
    private static boolean cacheSale = true;

    /** Une ligne du classement : points par categorie, places (1 = premier) et place au general. */
    public record Ligne(UUID clan, String tag, String nom, double pve, double pvp, double events, int rPve, int rPvp, int rEvents,
                        int rGeneral) {
        public double total() {
            return pve + pvp + events;
        }
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onStart(ServerAboutToStartEvent e) {
        dossier = e.getServer().getServerDirectory().toPath().resolve("heralisz_clans");
        recharger();
        d = SaisonDonnees.charger(dossier.resolve("saison_etat.json"));
        cacheSale = true;
        // jamais aucune saison : la premiere commence toute seule (les points comptent des l'ouverture)
        if (d.numero == 0 && !d.active) nouvelle("premier demarrage");
        HeraliszClans.LOG.info("[saison] saison {} {}", d.numero, !d.active ? "terminee (/saison reset pour en lancer une neuve)"
                : d.fin > 0 ? "en cours jusqu'au " + HEURE.format(Instant.ofEpochMilli(d.fin)) : "en cours, sans date de fin");
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        ecrireJournal();
        sauvegarder();
    }

    public static Bareme bareme() {
        return bareme;
    }

    public static void recharger() {
        bareme = Bareme.charger(FMLPaths.CONFIGDIR.get().resolve("heralisz_clans"));
        cacheSale = true;
    }

    /** Écriture en fond de l'état ratée : il redevient « à sauver » (fil d'écriture des clans). */
    private static final java.util.concurrent.atomic.AtomicBoolean ECHEC = new java.util.concurrent.atomic.AtomicBoolean();

    static void sauvegarder() {
        if (dossier == null) return;
        d.sauvegarder(dossier.resolve("saison_etat.json"), () -> ECHEC.set(true));
        sale = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || dossier == null) return;
        if (++ticks % 20 != 0) return;
        if (ECHEC.getAndSet(false)) sale = true;
        ecrireJournal();
        if (d.active && d.fin > 0 && System.currentTimeMillis() >= d.fin) terminer("fin de la date prevue");
        if (ticks % 1200 == 0 && sale) sauvegarder();
        // classement refait toutes les 30 s meme sans point gagne : un clan dissous, renomme ou qui pose / perd son
        // coeur n'y restait (ou n'y apparaissait) qu'au prochain point marque par n'importe quel clan
        if (ticks % 600 == 0) cacheSale = true;
    }

    private static void changer() {
        sale = true;
        cacheSale = true;
    }

    // ------------------------------------------------------------------ etat
    public static boolean active() {
        return d.active;
    }

    public static int numero() {
        return d.numero;
    }

    public static long fin() {
        return d.fin;
    }

    /** Les derniers jours : personne ne rejoint un clan. */
    public static boolean verrou() {
        return d.active && d.fin > 0 && System.currentTimeMillis() >= d.fin - bareme.verrouJours * 86_400_000L;
    }

    /** Message d'erreur si on ne peut pas rejoindre un clan maintenant, sinon null. */
    @Nullable
    public static Component refusRejoindre() {
        if (!verrou()) return null;
        return Component.translatableWithFallback("msg.saison.verrou",
                "Fin de saison : plus personne ne rejoint un clan pendant les %s derniers jours.", bareme.verrouJours);
    }

    public static double[] scores(UUID clan) {
        double[] s = d.scores.get(clan);
        return s == null ? new double[3] : s.clone();
    }

    // ------------------------------------------------------------------ PVE
    /** Points PVE pour le clan du joueur ({@code quantite} unites de {@code source}). */
    public static void pve(ServerPlayer p, String source, double quantite) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c != null) ajouterPve(c, p.getUUID(), p.getGameProfile().getName(), source, quantite);
    }

    public static void ajouterPve(Clan c, @Nullable UUID joueur, String qui, String source, double quantite) {
        if (!d.active || c == null || quantite <= 0) return;
        double pts = bareme.pointsPve(source) * quantite;
        if (pts <= 0 || !Ponts.aUnCoeur(c.id)) return;
        if (joueur != null && bareme.plafonnees.contains(source)) {
            jourCourant();
            double deja = d.plafonds.getOrDefault(joueur, 0.0);
            pts = Math.min(pts, bareme.plafondJour - deja);
            if (pts <= 1e-9) return;
            d.plafonds.put(joueur, deja + pts);
        }
        d.scores(c.id)[SaisonDonnees.PVE] += pts;
        changer();
        noter(c, qui, "pve", source + (quantite != 1 ? " x" + arrondi(quantite) : ""), pts);
    }

    /**
     * Boss tue : {@code part} = part des degats faite par le clan (0 a 1). Le 1er kill de ce boss par ce clan dans la
     * journee (heure de Paris) vaut le plein tarif, les suivants {@link Bareme#bossCoefs} (le dernier coefficient vaut pour
     * tous les suivants). Chaque participation compte, quelle que soit la part. 25/10 : avant, chacun des 5 boss rapportait
     * 150 points a chaque reapparition (toutes les heures), ce qui ecrasait toutes les autres sources PVE.
     */
    public static void boss(@Nullable Clan c, String type, double part) {
        if (!d.active || c == null || part <= 0 || !Ponts.aUnCoeur(c.id)) return;
        jourCourant();
        String cle = c.id + "|" + type;
        int deja = d.bossJour.getOrDefault(cle, 0);
        d.bossJour.put(cle, deja + 1);
        sale = true;
        double coef = bareme.bossCoefs[Math.min(deja, bareme.bossCoefs.length - 1)];
        String qui = "boss " + type + " (kill " + (deja + 1) + " du jour, x" + arrondi(coef) + ")";
        if (coef <= 0) {
            noter(c, qui, "pve", "boss x" + arrondi(part), 0);
            return;
        }
        ajouterPve(c, null, qui, "boss", part * coef);
    }

    /** Compteurs du jour (heure de Paris) : plafonds des petites actions, boss deja tues, captures ; remis a zero a minuit. */
    private static void jourCourant() {
        String jour = LocalDate.now(PARIS).toString();
        if (jour.equals(d.jour)) return;
        d.jour = jour;
        d.plafonds.clear();
        d.bossJour.clear();
        d.capturesJour.clear();
        d.evenementsJour.clear();
        sale = true;
    }

    /**
     * Événement de la carte réussi par un joueur (25/10, module heraliz_evenements : largage ouvert…) : points de la
     * catégorie events pour son clan ({@code events.points.<source>} du barème), au plus {@code events.par_jour.<source>}
     * fois par clan et par jour. Rend les points donnés (0 : rien).
     */
    public static double evenement(ServerPlayer p, String source, double quantite) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (!d.active || c == null || quantite <= 0 || !Ponts.aUnCoeur(c.id)) return 0;
        if (p.getTags().contains("heraliz_bot")) return 0;
        Double v = bareme.evenementsPoints.get(source);
        if (v == null || v <= 0) return 0;
        jourCourant();
        String cle = c.id + "|" + source;
        int deja = d.evenementsJour.getOrDefault(cle, 0);
        Integer max = bareme.evenementsParJour.get(source);
        String qui = p.getGameProfile().getName();
        if (max != null && deja >= max) {
            noter(c, qui, "events", source + " : deja " + deja + " aujourd'hui (plafond)", 0);
            return 0;
        }
        d.evenementsJour.put(cle, deja + 1);
        double pts = v * quantite;
        d.scores(c.id)[SaisonDonnees.EVENTS] += pts;
        changer();
        noter(c, qui, "events", source + " (" + (deja + 1) + "e du jour)", pts);
        return pts;
    }

    /**
     * Capture d'un drapeau par le clan {@code c} (25/10, demande du propriétaire : plus la zone est forte, plus elle
     * rapporte, avec une limite). Points = valeur de la ville (1 a 7) x {@link Bareme#captureParValeur} x coefficient de
     * la difficulte des vagues (1 a 3). Zone prise a un autre clan qui la tenait depuis {@link Bareme#captureTenueMin}
     * minutes au moins : x {@link Bareme#capturePriseCoef}, en points PVP (un conflit) ; sinon en points PVE. Rien si ce
     * clan a deja ete recompense pour cette zone dans les {@link Bareme#captureMemeZoneH} heures, ni si les deux memes
     * clans se sont deja echange cette zone dans les {@link Bareme#captureMemePaireH} heures (clans complices) ; au plus
     * {@link Bareme#capturePlafondJour} points de capture par clan et par jour.
     */
    public static void capture(@Nullable Clan c, @Nullable Clan ancien, String zone, String nomZone, int valeur, int difficulte, long tenueMs) {
        if (!d.active || c == null || !Ponts.aUnCoeur(c.id)) return;
        jourCourant();
        long now = System.currentTimeMillis();
        String cleZone = "zone|" + zone + "|" + c.id;
        Long deja = d.captures.get(cleZone);
        if (deja != null && now - deja < bareme.captureMemeZoneH * 3_600_000L) {
            noter(c, "capture " + nomZone, "pve", "capture deja recompensee recemment", 0);
            return;
        }
        String clePaire = ancien == null ? null : "paire|" + zone + "|" + c.id + "|" + ancien.id;
        String clePaireInverse = ancien == null ? null : "paire|" + zone + "|" + ancien.id + "|" + c.id;
        if (clePaire != null) {
            Long a = d.captures.get(clePaire), b = d.captures.get(clePaireInverse);
            long fen = (long) (bareme.captureMemePaireH * 3_600_000L);
            if ((a != null && now - a < fen) || (b != null && now - b < fen)) {
                noter(c, "capture " + nomZone, "pvp", "zone deja echangee avec [" + ancien.tag + "] recemment", 0);
                return;
            }
        }
        boolean prise = ancien != null && tenueMs >= bareme.captureTenueMin * 60_000L;
        double pts = Math.max(1, valeur) * bareme.captureParValeur * bareme.captureDifficulte[Math.max(1, Math.min(3, difficulte)) - 1]
                * (prise ? bareme.capturePriseCoef : 1);
        double dejaJour = d.capturesJour.getOrDefault(c.id, 0.0);
        pts = Math.min(pts, bareme.capturePlafondJour - dejaJour);
        if (pts <= 1e-9) {
            noter(c, "capture " + nomZone, prise ? "pvp" : "pve", "plafond du jour atteint", 0);
            return;
        }
        d.capturesJour.put(c.id, dejaJour + pts);
        d.captures.put(cleZone, now);
        if (clePaire != null) d.captures.put(clePaire, now);
        if (d.captures.size() > 2048) {
            long fen = (long) (Math.max(bareme.captureMemeZoneH, bareme.captureMemePaireH) * 3_600_000L);
            d.captures.values().removeIf(t -> now - t >= fen);
        }
        int cat = prise ? SaisonDonnees.PVP : SaisonDonnees.PVE;
        d.scores(c.id)[cat] += pts;
        changer();
        noter(c, "capture " + nomZone, CATEGORIES[cat], (prise ? "prise a [" + ancien.tag + "]" : "zone libre") + " valeur " + valeur, pts);
    }

    // ------------------------------------------------------------------ PVP
    /**
     * Un joueur en tue un autre (hors match, hors spawn : verifie par l'appelant). +kill au clan du tueur, -mort au clan de
     * la victime (jamais sous 0), avec les regles anti-abus de la classe.
     */
    public static void kill(ServerPlayer tueur, ServerPlayer victime) {
        if (!d.active || tueur == victime) return;
        // bots de charge (/bots de HeraliZ Utils) : jamais de points
        if (tueur.getTags().contains("heraliz_bot") || victime.getTags().contains("heraliz_bot")) return;
        ClanStore s = ClanStore.get();
        if (s == null) return;
        Clan ct = s.clanDe(tueur.getUUID()), cv = s.clanDe(victime.getUUID());
        if (ct == null && cv == null) return;
        if (ct != null && ct == cv) return;
        String ipT = tueur.getIpAddress(), ipV = victime.getIpAddress();
        if (ipT != null && ipT.equals(ipV)) return;
        if (!Ponts.menace(victime, bareme.pvpRareteMin, bareme.pvpExclus)) return;
        long now = System.currentTimeMillis(), fenetre = (long) (bareme.pvpFenetreH * 3_600_000L);
        String cle = tueur.getUUID() + "|" + victime.getUUID();
        List<Long> l = d.paires.computeIfAbsent(cle, k -> new ArrayList<>());
        l.removeIf(t -> now - t >= fenetre);
        double coef = bareme.pvpCoefs[Math.min(l.size(), bareme.pvpCoefs.length - 1)];
        l.add(now);
        if (d.paires.size() > 4096) d.paires.values().removeIf(x -> x.isEmpty() || now - x.get(x.size() - 1) >= fenetre);
        sale = true;
        if (coef <= 0) return;
        if (ct != null && Ponts.aUnCoeur(ct.id)) {
            double pts = bareme.pvpKill * coef;
            d.scores(ct.id)[SaisonDonnees.PVP] += pts;
            noter(ct, tueur.getGameProfile().getName(), "pvp", "kill " + victime.getGameProfile().getName(), pts);
        }
        if (cv != null && Ponts.aUnCoeur(cv.id)) {
            double[] sc = d.scores(cv.id);
            double avant = sc[SaisonDonnees.PVP];
            sc[SaisonDonnees.PVP] = Math.max(0, avant - bareme.pvpMort * coef);
            noter(cv, victime.getGameProfile().getName(), "pvp", "mort par " + tueur.getGameProfile().getName(), sc[SaisonDonnees.PVP] - avant);
        }
        changer();
    }

    // ------------------------------------------------------------------ events
    /**
     * Resultat d'un event : {@code classement} = clans dans l'ordre (1er, 2e...), {@code participants} = clans qui ont joue
     * (ceux hors du podium recoivent la participation). Points x multiplicateur de l'event.
     */
    public static void event(String event, List<UUID> classement, Collection<UUID> participants) {
        if (!d.active) return;
        ClanStore s = ClanStore.get();
        if (s == null) return;
        double mult = bareme.multiplicateur(event);
        Set<UUID> faits = new LinkedHashSet<>();
        for (int i = 0; i < classement.size(); i++) {
            UUID id = classement.get(i);
            if (id == null || !faits.add(id)) continue;
            double base = i < bareme.places.length ? bareme.places[i] : bareme.participation;
            donnerEvent(s.get(id), event, "place " + (i + 1), base * mult);
        }
        for (UUID id : participants) {
            if (id != null && faits.add(id)) donnerEvent(s.get(id), event, "participation", bareme.participation * mult);
        }
    }

    private static void donnerEvent(@Nullable Clan c, String event, String quoi, double pts) {
        if (c == null || pts <= 0 || !Ponts.aUnCoeur(c.id)) return;
        d.scores(c.id)[SaisonDonnees.EVENTS] += pts;
        changer();
        noter(c, "-", "events", event + " " + quoi, pts);
    }

    // ------------------------------------------------------------------ raids et coeur
    /**
     * Prise du coeur du clan {@code victime} par un membre du clan {@code attaquant} pendant la Purge {@code purge}.
     * Une seule prise par clan pris et par Purge. Renvoie faux si deja pris (rien ne change).
     */
    public static boolean raid(UUID attaquant, UUID victime, String purge, String qui) {
        if (!d.active || victime == null || victime.equals(attaquant)) return false;
        String cle = purge + "|" + victime;
        if (d.raids.contains(cle)) return false;
        d.raids.add(cle);
        ClanStore s = ClanStore.get();
        Clan cv = s == null ? null : s.get(victime), ca = s == null || attaquant == null ? null : s.get(attaquant);
        double[] sv = d.scores(victime);
        double[] pris = new double[3];
        for (int i = 0; i < 3; i++) {
            pris[i] = sv[i];
            sv[i] = sv[i] * (100 - bareme.raidPerduPct) / 100.0;
        }
        double perte = (pris[0] + pris[1] + pris[2]) * bareme.raidPerduPct / 100.0;
        if (cv != null) noter(cv, qui, "raid", "coeur pris par " + (ca == null ? "?" : "[" + ca.tag + "]"), -perte);
        if (ca != null && Ponts.aUnCoeur(ca.id)) {
            double[] sa = d.scores(ca.id);
            double gain = 0;
            for (int i = 0; i < 3; i++) {
                double g = pris[i] * bareme.raidGagnePct / 100.0;
                sa[i] += g;
                gain += g;
            }
            noter(ca, qui, "raid", "coeur de " + (cv == null ? "?" : "[" + cv.tag + "]"), gain);
        }
        changer();
        sauvegarder();
        annoncer(Component.translatableWithFallback("msg.saison.raid", "⚠ [%s] a pris le coeur de [%s] !",
                ca == null ? "?" : ca.tag, cv == null ? "?" : cv.tag).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Le clan perd tous ses points (coeur deplace et pas repose a temps). */
    public static void perdreTout(UUID clan, String motif) {
        double[] s = d.scores.get(clan);
        if (s == null) return;
        double avant = s[0] + s[1] + s[2];
        s[0] = s[1] = s[2] = 0;
        changer();
        sauvegarder();
        ClanStore st = ClanStore.get();
        Clan c = st == null ? null : st.get(clan);
        if (c != null) {
            noter(c, "-", "perte", motif, -avant);
            ClanService.diffuserChat(c, "msg.saison.perdu", motif);
        }
    }

    /** Correction du staff (journalisee). */
    public static void corriger(Clan c, int categorie, double delta, String motif, String staff) {
        double[] s = d.scores(c.id);
        double avant = s[categorie];
        s[categorie] = Math.max(0, avant + delta);
        changer();
        sauvegarder();
        noter(c, staff, CATEGORIES[categorie], "correction : " + motif, s[categorie] - avant);
    }

    // ------------------------------------------------------------------ classements
    /** Classement general (cache), clans avec coeur seulement. */
    public static List<Ligne> classement() {
        if (!cacheSale) return cache;
        cacheSale = false;
        ClanStore s = ClanStore.get();
        if (s == null) return cache = List.of();
        List<Clan> clans = new ArrayList<>();
        for (Clan c : s.tous()) if (Ponts.aUnCoeur(c.id)) clans.add(c);
        int n = clans.size();
        Map<UUID, int[]> places = new HashMap<>();
        for (Clan c : clans) places.put(c.id, new int[3]);
        for (int cat = 0; cat < 3; cat++) {
            final int k = cat;
            List<Clan> tri = new ArrayList<>(clans);
            tri.sort(Comparator.comparingDouble((Clan c) -> -scores(c.id)[k]));
            for (int i = 0; i < tri.size(); i++) {
                double v = scores(tri.get(i).id)[k];
                // egalite = meme place ; rien du tout = derniere place
                int place = v <= 0 ? n : i + 1;
                if (i > 0 && v > 0 && v == scores(tri.get(i - 1).id)[k]) place = places.get(tri.get(i - 1).id)[k];
                places.get(tri.get(i).id)[k] = place;
            }
        }
        List<Clan> gen = new ArrayList<>(clans);
        gen.sort(Comparator.comparingInt((Clan c) -> places.get(c.id)[0] + places.get(c.id)[1] + places.get(c.id)[2])
                .thenComparingDouble(c -> -(scores(c.id)[0] + scores(c.id)[1] + scores(c.id)[2])));
        List<Ligne> l = new ArrayList<>(n);
        for (int i = 0; i < gen.size(); i++) {
            Clan c = gen.get(i);
            double[] sc = scores(c.id);
            int[] p = places.get(c.id);
            l.add(new Ligne(c.id, c.tag, c.nom, sc[0], sc[1], sc[2], p[0], p[1], p[2], i + 1));
        }
        return cache = List.copyOf(l);
    }

    /** {pve, pvp, events, general} ; 0 = pas classe. */
    public static int[] rangs(UUID clan) {
        for (Ligne l : classement()) if (l.clan().equals(clan)) return new int[]{l.rPve(), l.rPvp(), l.rEvents(), l.rGeneral()};
        return new int[4];
    }

    // ------------------------------------------------------------------ debut et fin
    /**
     * 25/10 (choix du proprietaire : « une commande pour tout remettre a 0, une pour arreter la saison a une heure
     * precise ; des l'ouverture et des la remise a 0, les points comptent ») : la saison tourne toute seule, sans date de
     * fin ({@code fin} = 0) tant que le staff n'en programme pas une ({@link #programmerFin}). Arrivee a cette date, elle
     * est figee et archivee ; {@link #reset} en relance une neuve.
     */
    private static void nouvelle(String motif) {
        int numero = d.numero + 1;
        d = new SaisonDonnees();
        d.numero = numero;
        d.debut = System.currentTimeMillis();
        d.fin = 0;
        d.active = true;
        changer();
        sauvegarder();
        journal("--- debut de la saison " + numero + " (" + motif + ", sans date de fin)");
        annoncer(Component.translatableWithFallback("msg.saison.nouvelle", "La saison %s commence : tous les clans repartent de 0 point !",
                numero).withStyle(ChatFormatting.GOLD));
        HeraliszClans.LOG.info("[saison] saison {} commencee ({})", numero, motif);
    }

    /** Remise a 0 de tous les clans : la saison en cours est archivee, une nouvelle commence tout de suite. */
    public static void reset(String qui) {
        if (d.active) terminer("remise a zero par " + qui);
        nouvelle("remise a zero par " + qui);
    }

    /** Fin de la saison a l'heure {@code ms} (0 : plus de date de fin) ; annoncee a tous. */
    public static void programmerFin(long ms) {
        d.fin = Math.max(0, ms);
        changer();
        sauvegarder();
        if (ms > 0) {
            journal("--- fin de la saison " + d.numero + " programmee le " + HEURE.format(Instant.ofEpochMilli(ms)));
            annoncer(Component.translatableWithFallback("msg.saison.fin_programmee", "La saison %s se terminera le %s (heure de Paris).",
                    d.numero, HEURE.format(Instant.ofEpochMilli(ms))).withStyle(ChatFormatting.GOLD));
        } else {
            journal("--- fin de la saison " + d.numero + " annulee : plus de date de fin");
        }
    }

    /** Fige la saison : classement archive avec les membres de chaque clan (preuve pour les prix). */
    public static void terminer(String motif) {
        if (!d.active) return;
        List<Ligne> l = classement();
        d.active = false;
        JsonObject a = new JsonObject();
        a.addProperty("numero", d.numero);
        a.addProperty("debut", HEURE.format(Instant.ofEpochMilli(d.debut)));
        a.addProperty("fin", HEURE.format(Instant.now()));
        a.addProperty("motif", motif);
        JsonArray lignes = new JsonArray();
        ClanStore s = ClanStore.get();
        for (Ligne x : l) {
            JsonObject o = new JsonObject();
            o.addProperty("general", x.rGeneral());
            o.addProperty("tag", x.tag());
            o.addProperty("nom", x.nom());
            o.addProperty("pve", arrondi(x.pve()));
            o.addProperty("pvp", arrondi(x.pvp()));
            o.addProperty("events", arrondi(x.events()));
            o.addProperty("place_pve", x.rPve());
            o.addProperty("place_pvp", x.rPvp());
            o.addProperty("place_events", x.rEvents());
            JsonArray membres = new JsonArray();
            Clan c = s == null ? null : s.get(x.clan());
            if (c != null) for (Membre m : c.membres.values()) membres.add(m.dernierNom + " (" + m.uuid + ")");
            o.add("membres", membres);
            lignes.add(o);
        }
        a.add("classement", lignes);
        if (dossier != null) SaisonDonnees.ecrire(dossier.resolve("saisons").resolve("saison_" + d.numero + ".json"), a);
        changer();
        sauvegarder();
        journal("--- fin de la saison " + d.numero + " (" + motif + ")");
        annoncer(Component.translatableWithFallback("msg.saison.fin", "La saison %s est terminee ! Classement : /saison classement", d.numero)
                .withStyle(ChatFormatting.GOLD));
        HeraliszClans.LOG.info("[saison] saison {} terminee ({}), archive dans saisons/saison_{}.json", d.numero, motif, d.numero);
    }

    /**
     * Remise à zéro du serveur ({@code /wipe tout}) : la saison en cours est archivée (comme une fin normale), puis une
     * nouvelle commence tout de suite (25/10 : avant, plus aucune saison jusqu'à {@code /saison commencer}).
     */
    public static void remiseAZero() {
        reset("remise a zero du serveur");
    }

    // ------------------------------------------------------------------ journal
    private static void noter(Clan c, String qui, String categorie, String quoi, double pts) {
        fr.heralisz.clans.server.Sync.marquerLent(c);
        double[] s = d.scores(c.id);
        journal(String.format(Locale.ROOT, "[%s] | %s | %s | %s | %+.2f | pve %.2f pvp %.2f events %.2f", c.tag, qui, categorie, quoi,
                pts, s[0], s[1], s[2]));
    }

    /** Lignes du journal pas encore ecrites : ecrites en une fois chaque seconde (avant : un fichier ouvert par point gagne). */
    private static final StringBuilder JOURNAL_ATTENTE = new StringBuilder();

    private static void journal(String ligne) {
        if (dossier == null) return;
        JOURNAL_ATTENTE.append(HEURE.format(Instant.now())).append(" | ").append(ligne).append('\n');
        if (JOURNAL_ATTENTE.length() > 65536) ecrireJournal();
    }

    /** Lignes en attente ajoutées au journal sur le fil d'écriture des clans (27/10, perf), dans l'ordre. */
    private static void ecrireJournal() {
        if (JOURNAL_ATTENTE.length() == 0 || dossier == null) return;
        String bloc = JOURNAL_ATTENTE.toString();
        JOURNAL_ATTENTE.setLength(0);
        Path dir = dossier, f = dir.resolve("saison_journal.log");
        fr.heralisz.clans.server.EcrituresFond.ajouter(f, () -> {
            try {
                Files.createDirectories(dir);
                Files.writeString(f, bloc, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                HeraliszClans.LOG.warn("[saison] journal impossible a ecrire : {}", e.toString());
            }
        });
    }

    /** Les {@code n} dernieres lignes du journal qui parlent du clan (tag), les plus recentes a la fin. */
    public static List<String> journalDe(String tag, int n) {
        List<String> l = new ArrayList<>();
        if (dossier == null) return l;
        ecrireJournal();
        // le journal s'écrit en fond : on attend que les lignes en file soient sur le disque avant de le lire
        fr.heralisz.clans.server.EcrituresFond.attendre();
        Path f = dossier.resolve("saison_journal.log");
        if (!Files.exists(f)) return l;
        try {
            List<String> tout = Files.readAllLines(f, StandardCharsets.UTF_8);
            String cle = "| [" + tag + "] |";
            for (int i = tout.size() - 1; i >= 0 && l.size() < n; i--) if (tout.get(i).contains(cle)) l.add(0, tout.get(i));
        } catch (IOException e) {
            l.add("journal illisible : " + e);
        }
        return l;
    }

    private static void annoncer(Component c) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // au demarrage (premiere saison lancee toute seule), la liste des joueurs peut ne pas exister encore
        if (server != null && server.getPlayerList() != null) server.getPlayerList().broadcastSystemMessage(c, false);
    }

    static String arrondi(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", "");
    }

    /** Points entiers pour l'affichage (12 450). */
    public static long entiers(double v) {
        return (long) Math.floor(v + 1e-9);
    }
}
