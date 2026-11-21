package fr.heralisz.clans.profil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.EcrituresFond;
import fr.heralisz.economie.api.Compte;
import fr.heralisz.economie.server.ComptesStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Profil des joueurs (19/11, demande du propriétaire : « voir un panel avec son profil, kills, morts, etc. » ; maquettes
 * validées, écran {@code /profil}) : ce qui est compté pour CHAQUE joueur, avec ou sans clan, depuis toujours (seul
 * {@code /wipe} le remet à 0). Les kills de joueurs ne sont pas ici : ce sont ceux de {@code KillsJoueurs} (mêmes règles).
 * <ul>
 * <li>morts (toute vraie mort ; une mise à terre de Réanimation n'en est pas une), dont celles qu'un joueur a faites ;</li>
 * <li>zombies tués et conteneurs fouillés : mêmes règles que les statistiques des clans ({@code ClanEvents}, qui appelle
 * {@link #mort}, {@link #zombie}, {@link #kill} et {@link #conteneur} au même endroit que pour les clans) ;</li>
 * <li>extractions : arrivée au spawn depuis la carte, sans être mort (même règle que les quêtes du jour) ;</li>
 * <li>série de kills sans mourir, plus longue présence d'affilée sur la carte sans mourir (une déconnexion la met en
 * pause, une mort ou un départ de la carte la termine), temps de jeu, date d'arrivée.</li>
 * </ul>
 * Jamais compté : en match (ClashWar), au tutoriel des bases, les bots de charge ; les zombies tués en créatif ou en
 * spectateur. Rien ne tourne à chaque tick : tout vient d'événements (connexion, mort, changement de monde…) ; les
 * horloges de la session et de la vie en cours ne sont que des heures de début, gardées en mémoire.
 * <p>Fichier {@code heralisz_clans/profils.json} (hors du monde), écrit sur le fil d'écriture des clans au plus une fois
 * par minute s'il a changé, à l'arrêt et après un /wipe. Au premier démarrage (pas de fichier), il part des statistiques
 * des membres des clans (morts, zombies, conteneurs, temps de jeu, comptés depuis leur arrivée dans leur clan) une seule
 * fois (clé « reprise » du fichier) ; les noms des joueurs connus (comptes de hérals, kills) y sont repris pour que
 * {@code /profil pseudo} les trouve. Un fichier illisible est mis de côté, jamais écrasé. Fil serveur.
 */
public final class Profils {
    private Profils() {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Mondes où rien n'est compté : tutoriel des bases ; les arènes du ClashWar par leur espace de noms. */
    private static final Set<String> MONDES_EXCLUS = Set.of("crac:tuto");
    private static final String ESPACE_CLASHWAR = "heraliz_clashwar";
    /** Monde du spawn (arrivée d'une extraction). */
    private static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    private static final String BOT = "heraliz_bot";

    /** Ce qui est gardé d'un joueur (le fichier ne porte que les valeurs non nulles). */
    static final class Fiche {
        String nom = "";
        /** Première connexion connue (ms epoch), 0 : inconnue. */
        long depuis;
        /** Temps de jeu des sessions finies (ms). */
        long tempsMs;
        int morts, mortsJoueur, zombies, extractions, conteneurs, serie, serieMax;
        /** Vie en cours sur la carte (morceaux finis, ms) et plus longue. */
        long vieMs, vieMaxMs;

        void effacer() {
            tempsMs = 0;
            morts = mortsJoueur = zombies = extractions = conteneurs = serie = serieMax = 0;
            vieMs = vieMaxMs = 0;
        }
    }

    /** Session d'un joueur connecté : début (ms) et début du morceau de vie en cours sur la carte (0 : pas sur la carte). */
    private static final class Session {
        long debut, carte;
    }

    private static final Map<UUID, Fiche> FICHES = new HashMap<>();
    /** Pseudo en minuscules -> joueur ({@code /profil pseudo}). */
    private static final Map<String, UUID> PAR_NOM = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Tick de la dernière réapparition de chaque joueur connecté (une téléportation dans ce tick n'est pas une extraction). */
    private static final Map<UUID, Integer> REAPPARUS = new HashMap<>();
    @Nullable
    private static Path fichier;
    private static boolean sale;
    private static final AtomicBoolean ECHEC = new AtomicBoolean();
    /** Jamais sur le tick de la sauvegarde automatique de Minecraft (Etalement du noyau). */
    private static final int DECALAGE_SAUVEGARDE = fr.heraliz.noyau.api.Etalement.decalage("clans.profils", 1200);

    // ------------------------------------------------------------------ cycle de vie
    /** Après les clans, les comptes (ServerAboutToStart) et les kills ({@code KillsJoueurs}, même événement, avant). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onStart(ServerStartedEvent e) {
        FICHES.clear();
        PAR_NOM.clear();
        SESSIONS.clear();
        REAPPARUS.clear();
        ECHEC.set(false);
        sale = false;
        fichier = e.getServer().getServerDirectory().toPath().resolve("heralisz_clans").resolve("profils.json");
        if (!Files.exists(fichier)) {
            reprise();
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject j = o.has("joueurs") ? o.getAsJsonObject("joueurs") : new JsonObject();
            for (Map.Entry<String, JsonElement> x : j.entrySet()) {
                try {
                    lire(UUID.fromString(x.getKey()), x.getValue().getAsJsonObject());
                } catch (RuntimeException ignore) {
                    HeraliszClans.LOG.warn("[profils] ligne « {} » ignoree dans {}", x.getKey(), fichier.getFileName());
                }
            }
            HeraliszClans.LOG.info("[profils] {} profil(s) charge(s)", FICHES.size());
        } catch (IOException | RuntimeException ex) {
            // illisible : jamais ecrase (mis de cote), on repart comme au premier demarrage
            HeraliszClans.LOG.error("[profils] {} illisible : mis de cote, profils repris des clans", fichier, ex);
            try {
                Files.move(fichier, fichier.resolveSibling("profils.illisible-" + System.currentTimeMillis() + ".json"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
            }
            FICHES.clear();
            PAR_NOM.clear();
            reprise();
        }
    }

    /**
     * Premier démarrage (une seule fois : ensuite le fichier existe) : statistiques des membres des clans (comptées depuis
     * leur arrivée dans leur clan), noms des joueurs connus des comptes de hérals et des kills.
     */
    private static void reprise() {
        int membres = 0;
        ClanStore s = ClanStore.get();
        if (s != null) {
            for (Clan c : s.tous()) {
                for (Membre m : c.membres.values()) {
                    if (m.stats == null) continue;
                    Fiche f = fiche(m.uuid, m.dernierNom);
                    f.morts += Math.max(0, m.stats.morts);
                    f.zombies += Math.max(0, m.stats.zombies);
                    f.conteneurs += Math.max(0, m.stats.lootOuverts);
                    f.tempsMs += Math.max(0L, m.stats.tempsJeuTicks) * 50L;
                    if (m.rejointLe > 0 && (f.depuis == 0 || m.rejointLe < f.depuis)) f.depuis = m.rejointLe;
                    membres++;
                }
            }
        }
        fr.heralisz.clans.classement.KillsJoueurs.pourChaque((u, nom) -> fiche(u, nom));
        ComptesStore cs = ComptesStore.get();
        if (cs != null) {
            for (Compte c : cs.tous()) if (c.dernierNom() != null && !c.dernierNom().isEmpty()) fiche(c.uuid(), c.dernierNom());
        }
        HeraliszClans.LOG.info("[profils] premier demarrage : {} profil(s), statistiques de {} membre(s) de clan reprises",
                FICHES.size(), membres);
        sale = true;
        sauvegarder();
    }

    /** Arrêt : sessions en cours comptées (heure de fin), puis fichier écrit (attendu par l'écrivain des clans). */
    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> x : SESSIONS.entrySet()) finSession(x.getKey(), x.getValue(), now);
        SESSIONS.clear();
        REAPPARUS.clear();
        if (sale || ECHEC.getAndSet(false)) sauvegarder();
        fichier = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || fichier == null) return;
        if (!fr.heraliz.noyau.api.Etalement.tour(e.getServer().getTickCount(), 1200, DECALAGE_SAUVEGARDE)) return;
        if (ECHEC.getAndSet(false)) sale = true;
        if (sale) sauvegarder();
    }

    // ------------------------------------------------------------------ fichier
    private static void lire(UUID u, JsonObject o) {
        Fiche f = fiche(u, o.has("nom") ? o.get("nom").getAsString() : "");
        f.depuis = lng(o, "depuis");
        f.tempsMs = lng(o, "temps");
        f.morts = (int) lng(o, "morts");
        f.mortsJoueur = (int) lng(o, "morts_joueur");
        f.zombies = (int) lng(o, "zombies");
        f.extractions = (int) lng(o, "extractions");
        f.conteneurs = (int) lng(o, "conteneurs");
        f.serie = (int) lng(o, "serie");
        f.serieMax = (int) lng(o, "serie_max");
        f.vieMs = lng(o, "vie");
        f.vieMaxMs = lng(o, "vie_max");
    }

    private static long lng(JsonObject o, String cle) {
        return o.has(cle) ? Math.max(0L, o.get(cle).getAsLong()) : 0L;
    }

    private static void ajouter(JsonObject o, String cle, long v) {
        if (v != 0) o.addProperty(cle, v);
    }

    /** Clés des chiffres d'une fiche dans le fichier, dans l'ordre du relevé ({@link #sauvegarder}). */
    private static final String[] CLES = {"depuis", "temps", "morts", "morts_joueur", "zombies", "extractions", "conteneurs", "serie",
            "serie_max", "vie", "vie_max"};

    /**
     * Relevé sur le fil serveur : une simple copie des chiffres de chaque fiche (21/11, audit : avant, l'arbre JSON de toutes
     * les fiches, une par joueur connu, était refait ici à chaque minute où un chiffre avait changé, c'est-à-dire toujours en
     * jeu) ; mise en JSON, texte et écriture sur le fil d'écriture des clans, dans l'ordre (seul le relevé le plus récent
     * d'un même fichier est écrit). Même fichier qu'avant. À l'arrêt, l'écrivain attend cette écriture : rien n'est perdu.
     */
    private static void sauvegarder() {
        Path f = fichier;
        if (f == null) return;
        int n = FICHES.size(), champs = CLES.length;
        UUID[] ids = new UUID[n];
        String[] noms = new String[n];
        long[] chiffres = new long[n * champs];
        int i = 0;
        for (Map.Entry<UUID, Fiche> x : FICHES.entrySet()) {
            Fiche fi = x.getValue();
            ids[i] = x.getKey();
            noms[i] = fi.nom;
            int k = i * champs;
            chiffres[k] = fi.depuis;
            chiffres[k + 1] = fi.tempsMs;
            chiffres[k + 2] = fi.morts;
            chiffres[k + 3] = fi.mortsJoueur;
            chiffres[k + 4] = fi.zombies;
            chiffres[k + 5] = fi.extractions;
            chiffres[k + 6] = fi.conteneurs;
            chiffres[k + 7] = fi.serie;
            chiffres[k + 8] = fi.serieMax;
            chiffres[k + 9] = fi.vieMs;
            chiffres[k + 10] = fi.vieMaxMs;
            i++;
        }
        sale = false;
        EcrituresFond.ecrire(f, () -> {
            JsonObject joueurs = new JsonObject();
            for (int j = 0; j < n; j++) {
                JsonObject o = new JsonObject();
                if (!noms[j].isEmpty()) o.addProperty("nom", noms[j]);
                for (int c = 0; c < champs; c++) ajouter(o, CLES[c], chiffres[j * champs + c]);
                joueurs.add(ids[j].toString(), o);
            }
            JsonObject racine = new JsonObject();
            racine.addProperty("version", 1);
            // les statistiques des clans ont été reprises une fois (premier démarrage) : jamais une deuxième
            racine.addProperty("reprise", true);
            racine.add("joueurs", joueurs);
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }, () -> ECHEC.set(true));
    }

    // ------------------------------------------------------------------ fiches
    /** Fiche du joueur, créée au besoin ; {@code nom} (s'il n'est pas vide) devient son dernier pseudo connu. */
    private static Fiche fiche(UUID u, @Nullable String nom) {
        Fiche f = FICHES.computeIfAbsent(u, k -> new Fiche());
        if (nom != null && !nom.isEmpty() && !nom.equals(f.nom)) {
            if (!f.nom.isEmpty()) PAR_NOM.remove(f.nom.toLowerCase(Locale.ROOT), u);
            f.nom = nom;
            PAR_NOM.put(nom.toLowerCase(Locale.ROOT), u);
        }
        return f;
    }

    /** Fiche existante (null : joueur jamais vu depuis la mise à jour, ni repris). */
    @Nullable
    static Fiche get(UUID u) {
        return FICHES.get(u);
    }

    /** Joueurs connus (profils). */
    static int taille() {
        return FICHES.size();
    }

    /** Place du joueur aux zombies tués (1 + ceux qui en ont plus) ; 0 s'il n'en a aucun. */
    static int rangZombies(UUID u) {
        Fiche f = FICHES.get(u);
        if (f == null || f.zombies <= 0) return 0;
        int devant = 0;
        for (Fiche x : FICHES.values()) if (x.zombies > f.zombies) devant++;
        return devant + 1;
    }

    /** Joueur par son pseudo (dernier connu ici, insensible à la casse) ; null si inconnu. */
    @Nullable
    static UUID parNom(String nom) {
        return nom == null ? null : PAR_NOM.get(nom.toLowerCase(Locale.ROOT));
    }

    /** Temps de jeu, session en cours comprise (ms). */
    static long tempsJeu(UUID u, long now) {
        Fiche f = FICHES.get(u);
        long t = f == null ? 0 : f.tempsMs;
        Session s = SESSIONS.get(u);
        return s == null ? t : t + Math.max(0L, now - s.debut);
    }

    /** Temps de jeu sans la partie en cours (ms) : profil d'un staff caché vu par un joueur (21/11). */
    static long tempsJeuSansSession(UUID u) {
        Fiche f = FICHES.get(u);
        return f == null ? 0 : f.tempsMs;
    }

    /** Plus longue présence d'affilée sur la carte, sans la partie en cours (ms) : staff caché (21/11). */
    static long vieMaxSansSession(UUID u) {
        Fiche f = FICHES.get(u);
        return f == null ? 0 : Math.max(f.vieMaxMs, f.vieMs);
    }

    /** Plus longue présence d'affilée sur la carte, vie en cours comprise (ms). */
    static long vieMax(UUID u, long now) {
        Fiche f = FICHES.get(u);
        if (f == null) return 0;
        long vie = f.vieMs;
        Session s = SESSIONS.get(u);
        if (s != null && s.carte > 0) vie += Math.max(0L, now - s.carte);
        return Math.max(f.vieMaxMs, vie);
    }

    // ------------------------------------------------------------------ règles communes
    private static boolean bot(@Nullable Entity e) {
        return e != null && e.getTags().contains(BOT);
    }

    /** Monde où rien ne compte (tutoriel des bases, arènes du ClashWar). */
    private static boolean mondeExclu(Entity e) {
        ResourceLocation m = e.level().dimension().location();
        return ESPACE_CLASHWAR.equals(m.getNamespace()) || MONDES_EXCLUS.contains(m.toString());
    }

    private static boolean surCarte(ServerPlayer p) {
        return p.isAlive() && p.level().dimension() == Level.OVERWORLD;
    }

    /** Morceau de vie sur la carte en cours : ajouté à la vie (le morceau suivant repart de {@code now}, ou de rien). */
    private static void finMorceau(Fiche f, Session s, long now, boolean continuer) {
        if (s.carte > 0) f.vieMs += Math.max(0L, now - s.carte);
        s.carte = continuer ? now : 0;
    }

    /** La vie en cours sur la carte est finie (mort, départ de la carte) : record gardé, vie remise à 0. */
    private static void finVie(Fiche f) {
        f.vieMaxMs = Math.max(f.vieMaxMs, f.vieMs);
        f.vieMs = 0;
    }

    private static void finSession(UUID u, Session s, long now) {
        Fiche f = FICHES.get(u);
        if (f == null) return;
        f.tempsMs += Math.max(0L, now - s.debut);
        finMorceau(f, s, now, false);   // une déconnexion met la vie en pause, elle ne la termine pas
        sale = true;
    }

    // ------------------------------------------------------------------ connexion, mondes
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (fichier == null || !(e.getEntity() instanceof ServerPlayer p) || bot(p)) return;
        long now = System.currentTimeMillis();
        Fiche f = fiche(p.getUUID(), p.getGameProfile().getName());
        // arrivée sur le serveur : la plus ancienne connue (Bukkit la garde avec le joueur ; sinon : maintenant)
        long premiere = premiereConnexion(p);
        if (premiere > 0 && (f.depuis == 0 || premiere < f.depuis)) f.depuis = premiere;
        if (f.depuis == 0) f.depuis = now;
        Session s = new Session();
        s.debut = now;
        s.carte = surCarte(p) ? now : 0;
        SESSIONS.put(p.getUUID(), s);
        sale = true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        REAPPARUS.remove(u);
        Session s = SESSIONS.remove(u);
        if (s != null) finSession(u, s, System.currentTimeMillis());
    }

    /** Tick de la réapparition, en premier (avant toute téléportation qui la suit dans le même tick). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRespawnTick(PlayerEvent.PlayerRespawnEvent e) {
        if (!e.isEndConquered() && e.getEntity() instanceof ServerPlayer p) REAPPARUS.put(p.getUUID(), p.server.getTickCount());
    }

    /** Réapparition : sur la carte (base), une nouvelle vie commence. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        Fiche f = FICHES.get(p.getUUID());
        if (s == null || f == null) return;
        finMorceau(f, s, System.currentTimeMillis(), surCarte(p));
    }

    /**
     * Changement de monde : quitter la carte termine la vie en cours sur la carte ; arriver au spawn depuis la carte,
     * vivant et pas dans le tick de sa réapparition, est une extraction (comme pour les quêtes du jour : sur la carte, seul
     * /tpa marche, et seulement vers la carte).
     */
    @SubscribeEvent
    public static void onMonde(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || bot(p)) return;
        Session s = SESSIONS.get(p.getUUID());
        Fiche f = FICHES.get(p.getUUID());
        if (s == null || f == null) return;
        long now = System.currentTimeMillis();
        if (e.getFrom() == Level.OVERWORLD) {
            finMorceau(f, s, now, false);
            finVie(f);
            Integer reapparu = REAPPARUS.get(p.getUUID());
            boolean apresMort = reapparu != null && reapparu == p.server.getTickCount();
            if (!apresMort && p.isAlive() && SPAWN.equals(e.getTo().location()) && !ClansApi.enMatch(p)
                    && !p.isCreative() && !p.isSpectator()) {
                f.extractions++;
            }
            sale = true;
        }
        if (e.getTo() == Level.OVERWORLD && p.isAlive()) s.carte = now;
    }

    // ------------------------------------------------------------------ compteurs (appelés par ClanEvents)
    /**
     * Vraie mort d'un joueur (pas en match : déjà filtré par l'appelant). {@code tueur} : l'auteur crédité
     * ({@code ClanEvents.tueur}), un joueur ou non.
     */
    public static void mort(ServerPlayer p, @Nullable Entity tueur) {
        if (fichier == null || bot(p) || mondeExclu(p)) return;
        Fiche f = fiche(p.getUUID(), p.getGameProfile().getName());
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) finMorceau(f, s, System.currentTimeMillis(), false);
        finVie(f);
        f.serie = 0;
        f.morts++;
        if (tueur instanceof ServerPlayer t && t != p && !bot(t)) f.mortsJoueur++;
        sale = true;
    }

    /** Kill d'un joueur accepté par les règles anti-boost ({@code KillsJoueurs} vient de le compter) : série. */
    public static void kill(ServerPlayer tueur) {
        if (fichier == null || bot(tueur)) return;
        Fiche f = fiche(tueur.getUUID(), tueur.getGameProfile().getName());
        f.serie++;
        f.serieMax = Math.max(f.serieMax, f.serie);
        sale = true;
    }

    /** Créature hostile (hors boss) tuée par le joueur, comme les zombies des statistiques des clans. */
    public static void zombie(ServerPlayer tueur) {
        if (fichier == null || bot(tueur) || tueur.isCreative() || tueur.isSpectator() || mondeExclu(tueur)) return;
        fiche(tueur.getUUID(), tueur.getGameProfile().getName()).zombies++;
        sale = true;
    }

    /** Première fouille d'un conteneur rempli (HeraliZ Loot), comme les « loot ouverts » des clans. */
    public static void conteneur(ServerPlayer p) {
        if (fichier == null || bot(p) || mondeExclu(p)) return;
        fiche(p.getUUID(), p.getGameProfile().getName()).conteneurs++;
        sale = true;
    }

    // ------------------------------------------------------------------ /wipe
    /**
     * {@code /wipe <joueur>} : ses statistiques repartent de 0 (son pseudo et sa date d'arrivée restent) ; sa session en
     * cours repart de maintenant.
     */
    public static void effacer(UUID joueur) {
        Fiche f = FICHES.get(joueur);
        if (f == null) return;
        f.effacer();
        repartir(SESSIONS.get(joueur), System.currentTimeMillis());
        sale = true;
        sauvegarder();
    }

    /** {@code /wipe tout} : les statistiques de tous les joueurs repartent de 0. */
    public static void toutEffacer() {
        long now = System.currentTimeMillis();
        for (Fiche f : FICHES.values()) f.effacer();
        for (Session s : SESSIONS.values()) repartir(s, now);
        sale = true;
        sauvegarder();
    }

    private static void repartir(@Nullable Session s, long now) {
        if (s == null) return;
        s.debut = now;
        if (s.carte > 0) s.carte = now;
    }

    // ------------------------------------------------------------------ arrivée sur le serveur (Bukkit, par réflexion)
    private static java.lang.reflect.Method bukkitJoueur, premiereFois;
    private static boolean bukkitCherche;

    /**
     * Date de la première connexion gardée par Bukkit avec le joueur (serveur Mohist), 0 si inconnue (serveur Forge seul,
     * ou joueur sans cette donnée). Le joueur est connecté : rien n'est lu sur le disque.
     */
    private static long premiereConnexion(ServerPlayer p) {
        if (!bukkitCherche) {
            bukkitCherche = true;
            for (ClassLoader cl : new ClassLoader[]{MinecraftServer.class.getClassLoader(), Profils.class.getClassLoader(),
                    Thread.currentThread().getContextClassLoader()}) {
                if (cl == null) continue;
                try {
                    bukkitJoueur = Class.forName("org.bukkit.Bukkit", false, cl).getMethod("getPlayer", UUID.class);
                    break;
                } catch (ReflectiveOperationException | LinkageError ex) {
                    bukkitJoueur = null;
                }
            }
        }
        if (bukkitJoueur == null) return 0;
        try {
            Object b = bukkitJoueur.invoke(null, p.getUUID());
            if (b == null) return 0;
            if (premiereFois == null || !premiereFois.getDeclaringClass().isInstance(b)) premiereFois = b.getClass().getMethod("getFirstPlayed");
            Object t = premiereFois.invoke(b);
            return t instanceof Number n ? Math.max(0L, n.longValue()) : 0;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            return 0;
        }
    }
}
