package com.Spartan.scan;

import com.Spartan.Spartan;
import com.Spartan.alert.DiscordWebhook;
import com.Spartan.check.CheckManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.evidence.EvidenceRecorder;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Networks.ScanRequestPacket;
import com.Spartan.scan.Finding.Severity;
import com.Spartan.util.AsyncIO;
import com.Spartan.util.JsonFiles;
import com.Spartan.util.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Contrôle d'intégrité du client, côté serveur : planifie les contrôles (à la
 * connexion, puis à intervalle aléatoire, ou à la demande du staff), recompose
 * les rapports, les analyse et prévient le staff — EN SILENCE : jamais
 * d'expulsion, rien n'est montré au joueur. Le staff reçoit sur Discord le
 * pseudo, les indices et le rapport complet en pièce jointe, puis surveille le
 * joueur en spectateur et décide.
 */
public final class ScanManager {
    private static final SecureRandom RNG = new SecureRandom();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final int COLOR_STRONG = 0xE74C3C;
    private static final int COLOR_SUSPECT = 0xE67E22;
    private static final int COLOR_CLEAN = 0x2ECC71;
    private static final int MISSES_TO_ALERT = 2;

    private static ReferenceProfile reference = new ReferenceProfile();
    private static OfficialProfile official = new OfficialProfile();
    private static long[] knownMixins = new long[0];
    private static boolean warnedNoReference;

    private ScanManager() {
    }

    /** Ce que le staff a demandé pour le prochain rapport. */
    private enum Learn { NONE, REPLACE, MERGE }

    private static final class State {
        final String name;
        long nextAt;
        int scans;
        // contrôle en cours
        int pendingId;
        long pendingSince;
        int pendingTick;
        String pendingTrigger;
        ScanTransfer.Assembly assembly;
        // demandes du staff en attente de ce rapport
        final List<Consumer<Component>> requesters = new ArrayList<>();
        Learn learn = Learn.NONE;
        // session
        Set<String> baseline;
        final Set<String> alerted = new HashSet<>();
        int misses;
        /** Sondes du contrôle en cours : marque renvoyée par le jeu (« #… ») → classes de triche de ce paquet. */
        Map<String, String> sondesNoms = Map.of();
        // dernier résultat
        List<Finding> lastFindings = List.of();
        Severity lastVerdict;
        long lastAt;
        String lastEvidence;
        /** Dernier contrôle immédiat (touche des menus de triche, veille du jeu), en ms. */
        long dernierImmediat;

        State(String name) {
            this.name = name;
        }
    }

    // ------------------------------------------------------------------ référence

    private static Path referenceFile() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("reference.json");
    }

    /** Modpack officiel, fabriqué avec le serveur (outils/construire.py) et déposé par la mise en ligne. */
    private static Path officialFile() {
        return FMLPaths.CONFIGDIR.get().resolve("spartan").resolve("officiel.json");
    }

    /** Date du fichier officiel lu (pour le relire tout seul quand la mise en ligne le change). */
    private static long dateOfficiel = Long.MIN_VALUE;
    private static long prochaineSurveillance;

    /** Démarrage du serveur : charge le modpack officiel et le client de référence. */
    public static void load() {
        // nouveau démarrage (jeu solo relancé) : rien ne reste d'une session précédente
        FILE.clear();
        enCours = null;
        dateOfficiel = dateFichierOfficiel();
        official = loadOfficial();
        Path f = referenceFile();
        try {
            String json = JsonFiles.readOrNull(f);
            setReference(json == null ? new ReferenceProfile() : ReferenceProfile.fromJson(json));
        } catch (IOException | JsonParseException e) {
            JsonFiles.quarantine(f, e);
            setReference(new ReferenceProfile());
        }
        Spartan.LOGGER.info("[Spartan] Modpack officiel : {}", official.summary());
        Spartan.LOGGER.info("[Spartan] Client de référence : {}", reference.summary());
    }

    /** Relit config/spartan/officiel.json (après une mise à jour du modpack, sans redémarrer). */
    public static String reloadOfficial() {
        dateOfficiel = dateFichierOfficiel();
        remplacerOfficiel(loadOfficial());
        return official.summary();
    }

    /**
     * Nouveau modpack officiel : l'ancien reste accepté (un joueur lancé juste avant la mise en ligne garde ses jars) ;
     * un fichier vide ou illisible (envoi en cours) ne remplace jamais un modpack connu.
     */
    private static void remplacerOfficiel(OfficialProfile nouveau) {
        if (nouveau.isEmpty() && !official.isEmpty()) {
            Spartan.LOGGER.warn("[Spartan] officiel.json vide ou illisible : le modpack officiel connu est gardé.");
            dateOfficiel = Long.MIN_VALUE; // relu au prochain passage
            return;
        }
        OfficialProfile ancien = official;
        if (!ancien.isEmpty() && !ancien.genere.equals(nouveau.genere)) {
            // 2.9.6 : copie de l'ancien sans son propre précédent (avant : son précédent était effacé sur place) ; un profil
            // publié n'est plus jamais modifié, une analyse en cours sur le fil « Spartan-controle » peut le lire
            nouveau.precedent = ancien.seul();
        }
        official = nouveau;
        setReference(reference);
    }

    private static long dateFichierOfficiel() {
        try {
            return Files.getLastModifiedTime(officialFile()).toMillis();
        } catch (IOException | RuntimeException e) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * Date de officiel.json relevée sur un fil à part toutes les 2 s (2.9.5, perf) ; {@code Long.MIN_VALUE} : absent ou
     * illisible. Le fil du serveur demandait lui-même la date au disque toutes les 10 s : un disque occupé (sauvegarde du
     * monde) peut geler le tick (même cas que la boutique le 28/10 : 117 ms). Le fichier n'est lu par le fil du serveur que
     * quand sa date a vraiment changé, comme avant.
     */
    private static volatile long dateVue = Long.MIN_VALUE;
    private static boolean releveLance;

    private static void lancerReleveDate() {
        if (releveLance) {
            return;
        }
        releveLance = true;
        ScheduledExecutorService fil = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Spartan-officiel-date");
            t.setDaemon(true);
            return t;
        });
        fil.scheduleWithFixedDelay(() -> dateVue = dateFichierOfficiel(), 0L, 2L, TimeUnit.SECONDS);
    }

    /** Toutes les 10 s au plus : relit officiel.json s'il a changé (mise en ligne sans redémarrage). Fil serveur. */
    private static void surveillerOfficiel() {
        long now = Util.getMillis();
        if (now < prochaineSurveillance) {
            return;
        }
        prochaineSurveillance = now + 10_000L;
        lancerReleveDate();
        long d = dateVue;
        if (d == Long.MIN_VALUE || d == dateOfficiel) {
            return;
        }
        dateOfficiel = d;
        String avant = official.genere;
        remplacerOfficiel(loadOfficial());
        if (!official.genere.equals(avant)) {
            Spartan.LOGGER.info("[Spartan] Modpack officiel mis à jour sans redémarrage : {}", official.summary());
        }
    }

    private static OfficialProfile loadOfficial() {
        try {
            String json = JsonFiles.readOrNull(officialFile());
            return json == null ? new OfficialProfile() : OfficialProfile.fromJson(json);
        } catch (IOException | JsonParseException e) {
            Spartan.LOGGER.error("[Spartan] config/spartan/officiel.json illisible ({}) : modpack officiel ignoré.", e.toString());
            return new OfficialProfile();
        }
    }

    /** Modpack officiel chargé (vide si absent). */
    public static OfficialProfile official() {
        return official;
    }

    private static void setReference(ReferenceProfile p) {
        reference = p;
        java.util.Set<Long> all = new java.util.TreeSet<>();
        for (long v : p.knownMixins()) {
            all.add(v);
        }
        for (long v : official.mixinFingerprints()) {
            all.add(v);
        }
        knownMixins = all.stream().mapToLong(Long::longValue).toArray();
    }

    public static String referenceSummary() {
        return "modpack officiel : " + official.summary() + " | client de référence : " + reference.summary();
    }

    public static void clearReference() {
        setReference(new ReferenceProfile());
        JsonFiles.writeAsync(referenceFile(), reference.toJson());
    }

    // ------------------------------------------------------------------ cycle de vie

    public static void onLogin(ServerPlayer p) {
        State s = new State(p.getName().getString());
        s.nextAt = Util.getMillis() + randomMs(SpartanConfig.SCAN_FIRST_MIN.get(), SpartanConfig.SCAN_FIRST_MAX.get());
        STATES.put(p.getUUID(), s);
    }

    public static void clear(UUID uuid) {
        STATES.remove(uuid);
    }

    /** À chaque tick du joueur. */
    public static void tick(ServerPlayer p) {
        surveillerOfficiel();
        if (!SpartanConfig.SCAN_ENABLED.get()) {
            return;
        }
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        long now = Util.getMillis();
        if (s.pendingId != 0) {
            int timeout = SpartanConfig.SCAN_TIMEOUT.get();
            // Délai compté en temps réel ET en ticks : un serveur figé ne fait expirer personne.
            if (now - s.pendingSince > timeout * 1000L && p.server.getTickCount() - s.pendingTick > timeout * 20) {
                missed(p, s);
            }
            return;
        }
        if (now >= s.nextAt && com.Spartan.integrity.PorteJoueurs.estVerifie(p.getUUID())) {
            // jamais avant la vérification : la réponse pourrait venir d'un jeu lancé sous le pseudo d'un autre
            send(p, s, s.scans == 0 ? "connexion" : "contrôle aléatoire");
        }
    }

    /** Contrôle immédiat demandé par le staff (le résultat lui est envoyé). */
    public static void request(ServerPlayer p, Consumer<Component> requester, boolean learn, boolean merge) {
        appliquerAvant(p.getUUID()); // 2.9.6 : un rapport de lui encore en analyse est appliqué d'abord, comme avant
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State(p.getName().getString()));
        if (requester != null) {
            s.requesters.add(requester);
        }
        if (learn) {
            s.learn = merge ? Learn.MERGE : Learn.REPLACE;
        }
        if (s.pendingId == 0) {
            send(p, s, learn ? "apprentissage de la référence" : "demandé par le staff");
        }
    }

    /**
     * Contrôle complet TOUT DE SUITE (08/11 : un client injecté en cours de partie n'était vu qu'au contrôle au hasard
     * suivant, une minute à huit minutes plus tard) : demandé par une touche des menus de triche ou par la veille du jeu.
     * Au plus un toutes les {@code immediateScanGapSeconds} par joueur (0 = coupé) ; jamais pendant un contrôle en cours,
     * un rapport encore en analyse, ni avant la vérification du joueur. Le contrôle juge et expulse comme les autres.
     */
    public static void controleImmediat(ServerPlayer p, String declencheur) {
        int ecart = SpartanConfig.SCAN_IMMEDIAT_ECART.get();
        if (!SpartanConfig.SCAN_ENABLED.get() || ecart <= 0) {
            return;
        }
        State s = STATES.get(p.getUUID());
        if (s == null || s.pendingId != 0 || analyseEnAttente(p.getUUID())
                || !com.Spartan.integrity.PorteJoueurs.estVerifie(p.getUUID())) {
            return;
        }
        long now = Util.getMillis();
        if (s.dernierImmediat != 0 && now - s.dernierImmediat < ecart * 1000L) {
            return;
        }
        s.dernierImmediat = now;
        send(p, s, declencheur);
    }

    private static void send(ServerPlayer p, State s, String trigger) {
        int id;
        do {
            id = RNG.nextInt();
        } while (id == 0);
        s.pendingId = id;
        s.pendingSince = Util.getMillis();
        s.pendingTick = p.server.getTickCount();
        s.pendingTrigger = trigger;
        s.assembly = null;
        // audit du 02/11 (point 13) : programmes et classes cherchés en empreintes salées (sel neuf à chaque contrôle)
        long sel = RNG.nextLong();
        List<String> classes = SpartanConfig.getScanClassProbes();
        Sondes sondes = Sondes.preparer(sel, SpartanConfig.getScanProcessPatterns(), classes);
        s.sondesNoms = Sondes.noms(sel, classes);
        ModMessages.sendToPlayer(new ScanRequestPacket(id, SpartanConfig.SCAN_RENDER_SAMPLES.get(),
                !reference.isEmpty() || !official.isEmpty(),
                knownMixins, SpartanConfig.getScanMixinWatch(), sondes), p);
    }



    private static void reschedule(State s) {
        s.pendingId = 0;
        s.assembly = null;
        s.nextAt = Util.getMillis() + randomMs(SpartanConfig.SCAN_INTERVAL_MIN.get(), SpartanConfig.SCAN_INTERVAL_MAX.get());
    }

    private static void missed(ServerPlayer p, State s) {
        s.misses++;
        notifyRequesters(s, Component.literal("[Spartan] " + s.name + " n'a pas répondu au contrôle d'intégrité.")
                .withStyle(ChatFormatting.GOLD));
        if (s.misses == MISSES_TO_ALERT) {
            Finding f = new Finding(Severity.SUSPECT, "noreply",
                    "le jeu ne répond plus aux contrôles d'intégrité (" + s.misses + " fois de suite) : scanner bloqué ou désactivé ?");
            alert(p, s, List.of(f), null, s.pendingTrigger, null);
            if (s.scans == 0) {
                // jamais un seul contrôle réussi depuis la connexion : on ne peut pas savoir ce que ce jeu a chargé
                com.Spartan.integrity.ConformiteStricte.refuser(p, "aucune réponse aux contrôles d'intégrité depuis la connexion ("
                        + s.misses + " demandes) : scanner bloqué ou retiré");
            }
        }
        // audit du 02/11 (point 13) : un jeu qui cesse de répondre EN COURS DE PARTIE n'était signalé qu'une fois, jamais
        // refusé (il suffisait de couper le scanner après le premier contrôle)
        int seuil = SpartanConfig.STRICT_SANS_REPONSE.get();
        if (s.scans > 0 && seuil > 0 && s.misses == seuil) {
            com.Spartan.integrity.ConformiteStricte.refuser(p, "plus aucune réponse aux contrôles d'intégrité en cours de partie ("
                    + s.misses + " demandes de suite) : scanner bloqué ou retiré après le premier contrôle");
        }
        s.learn = Learn.NONE;
        reschedule(s);
    }

    // ------------------------------------------------------------------ réception

    /** Un morceau de rapport reçu du client (thread serveur). */
    public static void onChunk(ServerPlayer p, int scanId, int index, int total, byte[] data) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.pendingId == 0 || scanId != s.pendingId) {
            return; // non sollicité ou périmé
        }
        byte[] packed;
        try {
            if (s.assembly == null) {
                s.assembly = new ScanTransfer.Assembly(scanId, total);
            }
            packed = s.assembly.accept(scanId, index, total, data);
            if (packed == null) {
                return; // il en manque encore
            }
        } catch (RuntimeException e) {
            appliquerAvant(p.getUUID()); // un rapport de ce joueur encore en analyse passe d'abord, comme avant
            String trigger = s.pendingTrigger;
            reschedule(s);
            alert(p, s, List.of(rapportInvalide(e)), null, trigger, null);
            return;
        }
        // 2.9.6 : la suite (décompression, décodage, analyse) se fait sur le fil « Spartan-controle » ; le résultat est
        // appliqué sur le fil du serveur. Ce qui se faisait ici à la réception est fait tout de suite, comme avant :
        // prochain contrôle planifié, demande d'apprentissage et demandeurs du staff pris pour ce rapport.
        Analyse a = new Analyse(p, s, packed, scanId, s.pendingTrigger, s.learn, new ArrayList<>(s.requesters));
        reschedule(s);
        s.learn = Learn.NONE;
        s.requesters.clear();
        FILE.add(a);
        lancerSuivante();
    }

    private static Finding rapportInvalide(RuntimeException e) {
        return new Finding(Severity.SUSPECT, "badreport",
                "rapport de contrôle invalide (" + Text.oneLine(e.getMessage()) + ") : client modifié ?");
    }

    // ------------------------------------------------------------------ analyse sur un fil à part (2.9.6)
    //
    // Perf (150 joueurs) : décompression et décodage du rapport, analyse, écarts au jeu officiel et fichier de preuve
    // (seulement s'il y a un indice) sur le fil « Spartan-controle ». Avant : 2 à 3 ms sur le fil du serveur par rapport
    // (environ un par seconde à 150 joueurs), bien plus pour un joueur suspect (rapport complet écrit en JSON).
    // Ce qui touche les joueurs et l'état du contrôle (alertes, staff, refus de connexion, validation du jeu) reste sur le
    // fil du serveur. Mêmes résultats :
    //  - l'analyse lit ce qu'elle lisait avant (client de référence, modpack officiel, inventaire du premier contrôle,
    //    réglages), pris sur le fil du serveur au lancement ; ces profils ne sont plus jamais modifiés une fois publiés
    //    (apprentissage et nouveau modpack : copies) ;
    //  - même ordre : un seul rapport analysé à la fois, dans l'ordre d'arrivée ; le suivant part quand le précédent est
    //    appliqué (un apprentissage ou un premier contrôle compte pour les rapports qui suivent, comme avant) ;
    //  - une déconnexion applique d'abord les rapports reçus avant (avant : appliqués dès la réception) ; la validation
    //    du jeu attend un rapport en cours d'analyse (une fraction de seconde).

    /** Un rapport recomposé et ce qui a été pris pour lui à sa réception (fil du serveur). */
    private static final class Analyse {
        final MinecraftServer serveur;
        final ServerPlayer recu;
        final UUID joueur;
        final State s;
        final byte[] paquet;
        final int scanId;
        final String trigger;
        final Learn learn;
        final List<Consumer<Component>> demandeurs;
        /** Sondes de ce contrôle (empreinte → classes), prises à la réception. */
        final Map<String, String> sondesNoms;
        // au lancement (fil du serveur)
        Entrees entrees;
        Future<Resultat> tache;
        long lanceA;
        boolean applique;

        Analyse(ServerPlayer p, State s, byte[] paquet, int scanId, String trigger, Learn learn,
                List<Consumer<Component>> demandeurs) {
            this.serveur = p.server;
            this.recu = p;
            this.joueur = p.getUUID();
            this.s = s;
            this.paquet = paquet;
            this.scanId = scanId;
            this.trigger = trigger;
            this.learn = learn;
            this.demandeurs = demandeurs;
            this.sondesNoms = s.sondesNoms;
        }
    }

    /** Ce que l'analyse lit, pris sur le fil du serveur au lancement ; rien n'y est modifié ensuite. */
    private record Entrees(ReferenceProfile ref, OfficialProfile off, Set<String> baseline, ScanAnalyzer.Settings reglages,
                           List<String> agents, List<String> interdits) {
    }

    /** Ce que rend le fil d'analyse (rendu au fil du serveur par la file de tâches du serveur ou par l'attente). */
    private static final class Resultat {
        ScanReport rapport;
        /** Rapport illisible : même alerte qu'avant (« rapport de contrôle invalide »). */
        RuntimeException invalide;
        /** Erreur grave à la lecture (avant : remontait de la réception). */
        Throwable erreurLecture;
        Set<String> inventaire;
        List<Finding> findings;
        Severity verdict;
        /** Erreur dans l'analyse (avant : remontait au même endroit, rien d'autre n'était fait). */
        Throwable erreurAnalyse;
        List<String> ecarts;
        Throwable erreurEcarts;
        /** Fichier de preuve déjà écrit en JSON (joueur suspect) ; null : refait sur le fil du serveur s'il le faut. */
        String preuve;
    }

    /** Rapports reçus pas encore lancés, dans l'ordre d'arrivée (fil du serveur). */
    private static final ArrayDeque<Analyse> FILE = new ArrayDeque<>();
    /** Rapport en cours d'analyse sur le fil à part (fil du serveur). */
    private static Analyse enCours;
    private static ExecutorService filAnalyse;
    /** Au-delà, une analyse est reprise par le fil du serveur (fil d'analyse bloqué : jamais vu, par prudence). */
    private static final long ANALYSE_MAX_MS = 10_000L;

    private static ExecutorService filAnalyse() {
        if (filAnalyse == null) {
            filAnalyse = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Spartan-controle");
                t.setDaemon(true);
                return t;
            });
        }
        return filAnalyse;
    }

    /** Lance le rapport suivant de la file s'il n'y en a pas déjà un en cours (fil du serveur). */
    private static void lancerSuivante() {
        if (enCours != null) {
            return;
        }
        Analyse a = FILE.poll();
        if (a == null) {
            return;
        }
        enCours = a;
        a.entrees = new Entrees(reference, official, a.s.baseline, settings(), SpartanConfig.getAllowedJavaAgents(),
                SpartanConfig.getBlacklistedMods());
        a.lanceA = Util.getMillis();
        try {
            a.tache = filAnalyse().submit(() -> {
                Resultat res = analyser(a, a.entrees);
                a.serveur.execute(() -> appliquer(a, res));
                return res;
            });
        } catch (RuntimeException refus) {
            appliquer(a, analyser(a, a.entrees)); // fil indisponible : sur le fil du serveur, comme avant
        }
    }

    /** Le travail du fil d'analyse : ne lit que le rapport et les entrées prises au lancement. */
    private static Resultat analyser(Analyse a, Entrees e) {
        Resultat res = new Resultat();
        ScanReport r;
        try {
            r = ScanTransfer.unpack(a.paquet);
            if (r.scanId != a.scanId) {
                throw new IllegalStateException("identifiant de contrôle différent");
            }
        } catch (RuntimeException ex) {
            res.invalide = ex;
            return res;
        } catch (Throwable t) {
            res.erreurLecture = t;
            return res;
        }
        Sondes.traduire(r.classHits, a.sondesNoms); // empreintes du jeu → classes cherchées (02/11)
        res.rapport = r;
        try {
            if (a.learn != Learn.NONE) {
                res.inventaire = ScanAnalyzer.inventory(r);
                return res;
            }
            res.findings = ScanAnalyzer.analyze(r, e.ref(), e.off(), e.baseline(), e.reglages());
            if (e.baseline() == null) {
                res.inventaire = ScanAnalyzer.inventory(r);
            }
            res.verdict = ScanAnalyzer.verdict(res.findings);
        } catch (Throwable t) {
            res.erreurAnalyse = t;
            return res;
        }
        if (res.verdict == Severity.SUSPECT || res.verdict == Severity.STRONG) {
            try {
                res.preuve = GSON.toJson(evidence(a.joueur, a.s.name, res.findings, r, a.trigger, res.verdict, e.ref()));
            } catch (Throwable t) {
                res.preuve = null; // refaite sur le fil du serveur si l'alerte part, comme avant
            }
        }
        try {
            res.ecarts = com.Spartan.integrity.ConformiteStricte.ecarts(r, e.off(), e.agents(), e.interdits());
        } catch (Throwable t) {
            res.erreurEcarts = t;
        }
        return res;
    }

    /** Applique un résultat une seule fois, puis lance le rapport suivant (fil du serveur). */
    private static void appliquer(Analyse a, Resultat res) {
        // jamais ailleurs que sur le fil du serveur (serveur arrêté : execute lancerait la tâche sur le fil d'analyse)
        if (a.applique || !a.serveur.isSameThread()) {
            return;
        }
        a.applique = true;
        if (enCours == a) {
            enCours = null;
        }
        try {
            terminer(a, res);
        } catch (RuntimeException e) {
            Spartan.LOGGER.error("[Spartan] Contrôle d'intégrité de {} : résultat non appliqué", a.s.name, e);
        } finally {
            lancerSuivante();
        }
    }

    /** Résultat d'un rapport en cours, attendu (fil du serveur) ; refait ici si le fil d'analyse ne répond pas. */
    private static Resultat attendre(Analyse a) {
        try {
            return a.tache.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            Spartan.LOGGER.warn("[Spartan] Analyse du contrôle de {} sans réponse ({}) : refaite sur le fil du serveur",
                    a.s.name, e.toString());
        }
        return analyser(a, a.entrees);
    }

    /** Un rapport de ce joueur est-il reçu mais pas encore appliqué ? (fil du serveur) */
    public static boolean analyseEnAttente(UUID joueur) {
        if (enCours != null && enCours.joueur.equals(joueur)) {
            return true;
        }
        for (Analyse a : FILE) {
            if (a.joueur.equals(joueur)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applique tout de suite, dans l'ordre d'arrivée, les rapports reçus jusqu'au dernier de ce joueur (fil du serveur) :
     * à sa déconnexion (avant que les autres parties l'oublient), ou avant d'en traiter un autre de lui. Avant, tout était
     * fait à la réception : rien ne peut arriver entre les deux.
     */
    public static void appliquerAvant(UUID joueur) {
        int garde = 0;
        while (analyseEnAttente(joueur) && garde++ < 256) {
            Analyse a = enCours;
            if (a == null) {
                lancerSuivante();
                continue;
            }
            appliquer(a, attendre(a));
        }
    }

    /** Applique tout de suite tous les rapports reçus (fil du serveur, commandes du staff). */
    private static void appliquerTout() {
        int garde = 0;
        while ((enCours != null || !FILE.isEmpty()) && garde++ < 256) {
            Analyse a = enCours;
            if (a == null) {
                lancerSuivante();
                continue;
            }
            appliquer(a, attendre(a));
        }
    }

    /** Chaque tick du serveur : presque rien ; reprend une analyse restée sans réponse (fil d'analyse bloqué). */
    public static void tickAnalyses() {
        Analyse a = enCours;
        if (a != null && !a.applique && Util.getMillis() - a.lanceA > ANALYSE_MAX_MS) {
            appliquer(a, attendre(a));
        }
    }

    /** Le résultat d'un rapport, sur le fil du serveur. */
    private static void terminer(Analyse a, Resultat res) {
        State s = a.s;
        ServerPlayer p = a.serveur.getPlayerList().getPlayer(a.joueur);
        if (p == null) {
            p = a.recu;
        }
        if (res.erreurLecture != null) {
            Spartan.LOGGER.error("[Spartan] Contrôle d'intégrité de {} : rapport illisible", s.name, res.erreurLecture);
            return;
        }
        if (res.invalide != null) {
            // comme avant : un rapport invalide ne consomme ni la demande d'apprentissage ni les demandeurs du staff
            if (s.learn == Learn.NONE) {
                s.learn = a.learn;
            }
            s.requesters.addAll(0, a.demandeurs);
            alert(p, s, List.of(rapportInvalide(res.invalide)), null, a.trigger, null);
            return;
        }
        complete(p, s, a, res);
    }

    private static void complete(ServerPlayer p, State s, Analyse a, Resultat res) {
        String trigger = a.trigger;
        Learn learn = a.learn;
        ScanReport r = res.rapport;
        s.misses = 0;
        s.scans++;

        if (learn != Learn.NONE) {
            // copie : le profil publié n'est jamais modifié (une analyse en cours peut le lire)
            ReferenceProfile apprise = reference.copie();
            apprise.learn(r, s.name, learn == Learn.REPLACE, System.currentTimeMillis());
            setReference(apprise);
            JsonFiles.writeAsync(referenceFile(), reference.toJson());
            Spartan.LOGGER.info("[Spartan] Client de référence appris depuis {} : {}", s.name, reference.summary());
            notifier(a.demandeurs, Component.literal("[Spartan] Référence " + (learn == Learn.REPLACE ? "apprise" : "complétée")
                    + " depuis " + s.name + " : " + reference.summary()).withStyle(ChatFormatting.GREEN));
            s.baseline = res.inventaire != null ? res.inventaire : ScanAnalyzer.inventory(r);
            s.lastFindings = List.of();
            s.lastVerdict = null;
            s.lastAt = System.currentTimeMillis();
            return;
        }
        ReferenceProfile ref = a.entrees.ref();
        if (ref.isEmpty() && !warnedNoReference) {
            warnedNoReference = true;
            Spartan.LOGGER.warn(a.entrees.off().isEmpty()
                    ? "[Spartan] Ni modpack officiel (config/spartan/officiel.json) ni client de référence : seuls les indices universels sont relevés."
                    : "[Spartan] Client de référence non appris : le code d'origine inconnue reste « suspect » (orange) au premier contrôle. "
                    + "Depuis un jeu propre : /spartan reference learn <pseudo>.");
        }
        if (res.erreurAnalyse != null) {
            Spartan.LOGGER.error("[Spartan] Contrôle d'intégrité de {} : erreur dans l'analyse", s.name, res.erreurAnalyse);
            // 03/11 : la validation du jeu le dit (avant : « aucun rapport reçu du jeu », alors que le rapport était arrivé)
            com.Spartan.integrity.ValidationJeu.erreurServeur(p, "erreur du serveur dans l'analyse du contrôle ("
                    + res.erreurAnalyse + ") : voir la console");
            return;
        }

        List<Finding> findings = res.findings;
        if (s.baseline == null) {
            s.baseline = res.inventaire != null ? res.inventaire : ScanAnalyzer.inventory(r);
        }
        Severity verdict = res.verdict;
        s.lastFindings = findings;
        s.lastVerdict = verdict;
        s.lastAt = System.currentTimeMillis();

        boolean asked = !a.demandeurs.isEmpty();
        // nouvel indice, ou indice déjà signalé en orange qui passe au rouge (code injecté apparu en cours de partie…)
        boolean fresh = findings.stream().anyMatch(f -> f.severity() != Severity.INFO && !s.alerted.contains(alertKey(f)));
        String evidence = null;
        if (fresh || asked) {
            evidence = alert(p, s, findings, r, trigger, verdict, ref, res.preuve);
        }
        if (asked) {
            notifier(a.demandeurs, summaryLine(s.name, verdict, findings, evidence));
        }
        if (res.erreurEcarts != null) {
            Spartan.LOGGER.error("[Spartan] Contrôle d'intégrité de {} : erreur dans les écarts au jeu officiel", s.name,
                    res.erreurEcarts);
            com.Spartan.integrity.ValidationJeu.erreurServeur(p, "erreur du serveur dans la comparaison au jeu officiel ("
                    + res.erreurEcarts + ") : voir la console");
            return;
        }
        // Injection (03/11) : traces certaines d'un code étranger dans le jeu = alerte + expulsion, quel que soit le mode
        com.Spartan.integrity.Injection.Verdict injection = com.Spartan.integrity.Injection.juger(p.getUUID(), findings);
        if (injection.expulser() != null) {
            com.Spartan.integrity.Injection.expulser(p, injection.expulser(), "contrôle d'intégrité, " + trigger);
        } else if (injection.reverifier() && s.pendingId == 0) {
            s.nextAt = Math.min(s.nextAt, Util.getMillis() + 5_000L); // code natif caché vu une fois : revu tout de suite
        }
        // Jeu identique au jeu officiel, sinon refusé (règle d'accès) : seulement les écarts certains, après l'alerte
        // (le staff a le rapport complet avec la preuve). Calculés aussi sans le mode strict : la validation du jeu dit
        // « identique au jeu officiel » seulement sans aucun écart.
        List<String> ecarts = res.ecarts;
        if (com.Spartan.integrity.ConformiteStricte.actif() && !ecarts.isEmpty()) {
            com.Spartan.integrity.ConformiteStricte.refuser(p, String.join(" ; ", ecarts.subList(0, Math.min(4, ecarts.size())))
                    + (ecarts.size() > 4 ? " (+" + (ecarts.size() - 4) + " autre(s))" : "") + " [" + trigger + "]");
        }
        // « ✅ Jeu validé » à la connexion (premier contrôle de la connexion seulement)
        com.Spartan.integrity.ValidationJeu.controle(p, verdict, findings, ecarts, r);
    }

    // ------------------------------------------------------------------ alertes

    private static ScanAnalyzer.Settings settings() {
        return new ScanAnalyzer.Settings(SpartanConfig.getAllowedJavaAgents(), SpartanConfig.getBlacklistedMods(),
                SpartanConfig.getScanOverlays(), SpartanConfig.getScanNativePatterns());
    }

    /**
     * Rapport au staff : Discord (indices + rapport complet joint), staff en jeu, alerts.log.
     * N'alerte que si un indice au moins est suspect ou fort. @return le fichier de preuve, ou null
     */
    private static String alert(ServerPlayer p, State s, List<Finding> findings, ScanReport r, String trigger, Severity verdict) {
        return alert(p, s, findings, r, trigger, verdict, reference, null);
    }

    /**
     * @param ref    client de référence de l'analyse de ce rapport (textes de la preuve et du rappel)
     * @param preuve preuve déjà écrite en JSON par le fil d'analyse, ou null
     */
    private static String alert(ServerPlayer p, State s, List<Finding> findings, ScanReport r, String trigger, Severity verdict,
                                ReferenceProfile ref, String preuve) {
        Severity v = verdict != null ? verdict : ScanAnalyzer.verdict(findings);
        if (v == null || v == Severity.INFO) {
            return null;
        }
        for (Finding f : findings) {
            s.alerted.add(f.key());
            s.alerted.add(alertKey(f));
        }
        CheckManager.recordSilent("ClientScan", p.getUUID());

        String json = preuve != null ? preuve : GSON.toJson(evidence(p.getUUID(), s.name, findings, r, trigger, v, ref));
        String file = EvidenceRecorder.writeJson(s.name, "controle", json);
        s.lastEvidence = file;

        long strong = findings.stream().filter(f -> f.severity() == Severity.STRONG).count();
        long suspect = findings.stream().filter(f -> f.severity() == Severity.SUSPECT).count();
        String count = (strong > 0 ? strong + " indice(s) fort(s)" : "")
                + (strong > 0 && suspect > 0 ? ", " : "") + (suspect > 0 ? suspect + " suspect(s)" : "");
        String title = (v == Severity.STRONG ? "🚨 Triche probable : " : "🔎 Joueur suspect : ") + s.name;

        // Discord : chaque ligne vient en partie du client → neutralisée.
        StringBuilder lines = new StringBuilder();
        for (Finding f : findings) {
            if (f.severity() == Severity.INFO) {
                continue;
            }
            String line = (f.severity() == Severity.STRONG ? "🔴 " : "🟠 ") + Text.discord(f.text()) + "\n";
            if (lines.length() + line.length() > 1000) {
                lines.append("… (voir le fichier joint)");
                break;
            }
            lines.append(line);
        }
        List<String[]> fields = new ArrayList<>();
        fields.add(new String[] {"Joueur", Text.discord(s.name) + " (" + p.getUUID() + ")"});
        fields.add(new String[] {"Verdict", count + " — contrôle : " + Text.discord(trigger)});
        fields.add(new String[] {"Indices", lines.toString()});
        if (r != null) {
            fields.add(new String[] {"Jeu", Text.discord(r.java + " · " + r.os) + " · contrôle en " + r.durationMs + " ms"});
        }
        fields.add(new String[] {"Preuve", "Rapport complet joint · sur le serveur : config/spartan/evidence/" + Text.discord(file)});
        DiscordWebhook.sendReport(p.getUUID().toString(), Text.oneLine(s.name), title, v == Severity.STRONG ? COLOR_STRONG : COLOR_SUSPECT,
                "Aucune action automatique : surveillez-le en spectateur, puis `/banpc` si c'est confirmé." + referenceHint(ref),
                fields, file, json.getBytes(StandardCharsets.UTF_8));

        Component msg = Component.literal("[Spartan] ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal((v == Severity.STRONG ? "Triche probable : " : "Suspect : ") + s.name)
                        .withStyle(v == Severity.STRONG ? ChatFormatting.RED : ChatFormatting.GOLD))
                .append(Component.literal(" — " + count + " (" + Text.oneLine(firstText(findings)) + ") — /spartan report "
                        + s.name).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer staff : p.server.getPlayerList().getPlayers()) {
            if (staff.hasPermissions(2) && staff != p) {
                staff.sendSystemMessage(msg);
            }
        }
        String logLine = "[Spartan] Contrôle d'intégrité — " + s.name + " : " + v.label + " (" + count + ") ["
                + Text.oneLine(trigger) + "] " + Text.oneLine(firstText(findings)) + " [preuve: " + file + "]";
        Spartan.LOGGER.warn(logLine);
        if (SpartanConfig.ALERT_LOGFILE.get()) {
            appendLog(logLine);
        }
        return file;
    }

    /** Clé d'un indice déjà signalé : un indice fort a la sienne, pour qu'un indice orange devenu rouge soit re-signalé. */
    private static String alertKey(Finding f) {
        return f.severity() == Severity.STRONG ? f.key() + "!fort" : f.key();
    }

    /** Rappel quand le client de référence n'est pas appris : le code d'origine inconnue reste orange sans lui. */
    private static String referenceHint(ReferenceProfile ref) {
        return ref.isEmpty()
                ? "\n⚠️ Client de référence non appris : le code inconnu reste en orange au premier contrôle. Depuis un jeu PROPRE : `/spartan reference learn <pseudo>`."
                : "";
    }

    private static String firstText(List<Finding> findings) {
        return findings.isEmpty() ? "" : findings.get(0).text();
    }

    /** Preuve d'un contrôle (fil du serveur ou fil d'analyse : ne lit que ses paramètres). */
    private static JsonObject evidence(UUID uuid, String name, List<Finding> findings, ScanReport r, String trigger, Severity v,
                                       ReferenceProfile ref) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "controle-integrite");
        root.addProperty("player", name);
        root.addProperty("uuid", uuid.toString());
        root.addProperty("trigger", trigger);
        root.addProperty("capturedAt", System.currentTimeMillis());
        root.addProperty("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        root.addProperty("verdict", v.name());
        root.addProperty("reference", ref.summary());
        JsonArray arr = new JsonArray();
        for (Finding f : findings) {
            JsonObject o = new JsonObject();
            o.addProperty("severity", f.severity().name());
            o.addProperty("text", f.text());
            o.addProperty("key", f.key());
            arr.add(o);
        }
        root.add("findings", arr);
        if (r != null) {
            root.add("report", GSON.toJsonTree(r));
        }
        return root;
    }

    private static Component summaryLine(String name, Severity verdict, List<Finding> findings, String evidence) {
        if (verdict == null || verdict == Severity.INFO) {
            return Component.literal("[Spartan] Contrôle de " + name + " : rien de suspect.").withStyle(ChatFormatting.GREEN);
        }
        long strong = findings.stream().filter(f -> f.severity() == Severity.STRONG).count();
        long suspect = findings.stream().filter(f -> f.severity() == Severity.SUSPECT).count();
        return Component.literal("[Spartan] Contrôle de " + name + " : " + strong + " indice(s) fort(s), " + suspect
                + " suspect(s). Détails : /spartan report " + name + (evidence == null ? "" : " — preuve " + evidence))
                .withStyle(verdict == Severity.STRONG ? ChatFormatting.RED : ChatFormatting.GOLD);
    }

    private static void notifyRequesters(State s, Component msg) {
        notifier(s.requesters, msg);
    }

    /** Prévient ces demandeurs du staff, puis les oublie. */
    private static void notifier(List<Consumer<Component>> demandeurs, Component msg) {
        for (Consumer<Component> r : demandeurs) {
            try {
                r.accept(msg);
            } catch (RuntimeException ignored) {
                // demandeur déconnecté
            }
        }
        demandeurs.clear();
    }

    private static void appendLog(String line) {
        String stamped = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + line + System.lineSeparator();
        AsyncIO.submit(() -> {
            File dir = new File(FMLPaths.CONFIGDIR.get().toFile(), "spartan");
            dir.mkdirs();
            try (FileWriter w = new FileWriter(new File(dir, "alerts.log"), StandardCharsets.UTF_8, true)) {
                w.write(stamped);
            } catch (IOException e) {
                Spartan.LOGGER.warn("[Spartan] Impossible d'écrire alerts.log : {}", e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ commandes

    /** Lignes de /spartan report. */
    public static List<Component> describe(UUID uuid, String name) {
        appliquerAvant(uuid); // 2.9.6 : le dernier rapport reçu compte, même s'il était encore en analyse
        List<Component> out = new ArrayList<>();
        State s = STATES.get(uuid);
        if (s == null || s.lastAt == 0) {
            out.add(Component.literal(name + " : aucun contrôle terminé pour l'instant (essayez /spartan scan " + name + ").")
                    .withStyle(ChatFormatting.GRAY));
            return out;
        }
        long ago = (System.currentTimeMillis() - s.lastAt) / 1000;
        Severity v = s.lastVerdict;
        out.add(Component.literal(name + " — dernier contrôle il y a " + ago + " s : "
                + (v == null || v == Severity.INFO ? "rien de suspect" : v.label)
                + (s.lastEvidence == null ? "" : " — preuve " + s.lastEvidence))
                .withStyle(v == Severity.STRONG ? ChatFormatting.RED : v == Severity.SUSPECT ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        int shown = 0;
        for (Finding f : s.lastFindings) {
            if (shown++ >= 12) {
                out.add(Component.literal("… (voir le fichier de preuve)").withStyle(ChatFormatting.GRAY));
                break;
            }
            out.add(Component.literal(" • [" + f.severity().label + "] " + Text.oneLine(f.text()))
                    .withStyle(f.severity() == Severity.STRONG ? ChatFormatting.RED
                            : f.severity() == Severity.SUSPECT ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return out;
    }

    /** Joueurs en ligne dont le dernier contrôle est suspect. */
    public static List<String> suspects(MinecraftServer server) {
        appliquerTout(); // 2.9.6 : les derniers rapports reçus comptent, même ceux encore en analyse
        List<String> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s != null && s.lastVerdict != null && s.lastVerdict != Severity.INFO) {
                out.add(s.name + " (" + s.lastVerdict.label + ")");
            }
        }
        return out;
    }

    private static long randomMs(int minSeconds, int maxSeconds) {
        int lo = Math.min(minSeconds, maxSeconds);
        int hi = Math.max(minSeconds, maxSeconds);
        return (lo + RNG.nextInt(hi - lo + 1)) * 1000L;
    }
}
