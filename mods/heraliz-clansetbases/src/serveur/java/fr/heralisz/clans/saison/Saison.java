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
 * <li>PVE : bareme par source, petites actions plafonnees par joueur et par jour ;</li>
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
        HeraliszClans.LOG.info("[saison] saison {} {}", d.numero, d.active ? "en cours jusqu'au " + HEURE.format(Instant.ofEpochMilli(d.fin)) : "arretee");
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        sauvegarder();
    }

    public static Bareme bareme() {
        return bareme;
    }

    public static void recharger() {
        bareme = Bareme.charger(FMLPaths.CONFIGDIR.get().resolve("heralisz_clans"));
        cacheSale = true;
    }

    static void sauvegarder() {
        if (dossier == null) return;
        d.sauvegarder(dossier.resolve("saison_etat.json"));
        sale = false;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || dossier == null) return;
        if (++ticks % 20 != 0) return;
        if (d.active && System.currentTimeMillis() >= d.fin) terminer("fin de la date prevue");
        if (ticks % 1200 == 0 && sale) sauvegarder();
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
        return d.active && System.currentTimeMillis() >= d.fin - bareme.verrouJours * 86_400_000L;
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
            String jour = LocalDate.now(PARIS).toString();
            if (!jour.equals(d.jour)) {
                d.jour = jour;
                d.plafonds.clear();
            }
            double deja = d.plafonds.getOrDefault(joueur, 0.0);
            pts = Math.min(pts, bareme.plafondJour - deja);
            if (pts <= 1e-9) return;
            d.plafonds.put(joueur, deja + pts);
        }
        d.scores(c.id)[SaisonDonnees.PVE] += pts;
        changer();
        noter(c, qui, "pve", source + (quantite != 1 ? " x" + arrondi(quantite) : ""), pts);
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
    public static void commencer(int jours) {
        if (d.active) terminer("nouvelle saison");
        int numero = d.numero + 1;
        d = new SaisonDonnees();
        d.numero = numero;
        d.debut = System.currentTimeMillis();
        d.fin = d.debut + jours * 86_400_000L;
        d.active = true;
        changer();
        sauvegarder();
        journal("--- debut de la saison " + numero + " (" + jours + " jours, fin " + HEURE.format(Instant.ofEpochMilli(d.fin)) + ")");
        annoncer(Component.translatableWithFallback("msg.saison.debut", "La saison %s commence ! Fin le %s.", numero,
                HEURE.format(Instant.ofEpochMilli(d.fin))).withStyle(ChatFormatting.GOLD));
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
     * Remise à zéro du serveur ({@code /wipe serveur}) : la saison en cours est archivée (comme une fin normale), puis
     * points, journal des paires et plafonds sont effacés ; aucune saison active ensuite ({@code /saison commencer}).
     */
    public static void remiseAZero() {
        if (d.active) terminer("remise a zero du serveur");
        int numero = d.numero;
        d = new SaisonDonnees();
        d.numero = numero;
        changer();
        sauvegarder();
        journal("--- remise a zero du serveur : points effaces");
    }

    // ------------------------------------------------------------------ journal
    private static void noter(Clan c, String qui, String categorie, String quoi, double pts) {
        fr.heralisz.clans.server.Sync.marquerLent(c);
        double[] s = d.scores(c.id);
        journal(String.format(Locale.ROOT, "[%s] | %s | %s | %s | %+.2f | pve %.2f pvp %.2f events %.2f", c.tag, qui, categorie, quoi,
                pts, s[0], s[1], s[2]));
    }

    private static void journal(String ligne) {
        if (dossier == null) return;
        try {
            Files.createDirectories(dossier);
            Files.writeString(dossier.resolve("saison_journal.log"), HEURE.format(Instant.now()) + " | " + ligne + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            HeraliszClans.LOG.warn("[saison] journal impossible a ecrire : {}", e.toString());
        }
    }

    /** Les {@code n} dernieres lignes du journal qui parlent du clan (tag), les plus recentes a la fin. */
    public static List<String> journalDe(String tag, int n) {
        List<String> l = new ArrayList<>();
        if (dossier == null) return l;
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
        if (server != null) server.getPlayerList().broadcastSystemMessage(c, false);
    }

    static String arrondi(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("\\.?0+$", "");
    }

    /** Points entiers pour l'affichage (12 450). */
    public static long entiers(double v) {
        return (long) Math.floor(v + 1e-9);
    }
}
