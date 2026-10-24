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
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        // dernier résultat
        List<Finding> lastFindings = List.of();
        Severity lastVerdict;
        long lastAt;
        String lastEvidence;

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

    /** Démarrage du serveur : charge le modpack officiel et le client de référence. */
    public static void load() {
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
        official = loadOfficial();
        setReference(reference);
        return official.summary();
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
        ModMessages.sendToPlayer(new ScanRequestPacket(id, SpartanConfig.SCAN_RENDER_SAMPLES.get(),
                !reference.isEmpty() || !official.isEmpty(),
                knownMixins, SpartanConfig.getScanMixinWatch(), SpartanConfig.getScanClassProbes(),
                SpartanConfig.getScanProcessPatterns(), SpartanConfig.getScanNativePatterns()), p);
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
        ScanReport report;
        try {
            if (s.assembly == null) {
                s.assembly = new ScanTransfer.Assembly(scanId, total);
            }
            byte[] packed = s.assembly.accept(scanId, index, total, data);
            if (packed == null) {
                return; // il en manque encore
            }
            report = ScanTransfer.unpack(packed);
            if (report.scanId != scanId) {
                throw new IllegalStateException("identifiant de contrôle différent");
            }
        } catch (RuntimeException e) {
            String trigger = s.pendingTrigger;
            reschedule(s);
            Finding f = new Finding(Severity.SUSPECT, "badreport",
                    "rapport de contrôle invalide (" + Text.oneLine(e.getMessage()) + ") : client modifié ?");
            alert(p, s, List.of(f), null, trigger, null);
            return;
        }
        complete(p, s, report);
    }

    private static void complete(ServerPlayer p, State s, ScanReport r) {
        String trigger = s.pendingTrigger;
        Learn learn = s.learn;
        s.learn = Learn.NONE;
        s.misses = 0;
        s.scans++;
        reschedule(s);

        if (learn != Learn.NONE) {
            reference.learn(r, s.name, learn == Learn.REPLACE, System.currentTimeMillis());
            setReference(reference);
            JsonFiles.writeAsync(referenceFile(), reference.toJson());
            Spartan.LOGGER.info("[Spartan] Client de référence appris depuis {} : {}", s.name, reference.summary());
            notifyRequesters(s, Component.literal("[Spartan] Référence " + (learn == Learn.REPLACE ? "apprise" : "complétée")
                    + " depuis " + s.name + " : " + reference.summary()).withStyle(ChatFormatting.GREEN));
            s.baseline = ScanAnalyzer.inventory(r);
            s.lastFindings = List.of();
            s.lastVerdict = null;
            s.lastAt = System.currentTimeMillis();
            return;
        }
        if (reference.isEmpty() && !warnedNoReference) {
            warnedNoReference = true;
            Spartan.LOGGER.warn(official.isEmpty()
                    ? "[Spartan] Ni modpack officiel (config/spartan/officiel.json) ni client de référence : seuls les indices universels sont relevés."
                    : "[Spartan] Client de référence non appris : le code d'origine inconnue reste « suspect » (orange) au premier contrôle. "
                    + "Depuis un jeu propre : /spartan reference learn <pseudo>.");
        }

        List<Finding> findings = ScanAnalyzer.analyze(r, reference, official, s.baseline, settings());
        if (s.baseline == null) {
            s.baseline = ScanAnalyzer.inventory(r);
        }
        Severity verdict = ScanAnalyzer.verdict(findings);
        s.lastFindings = findings;
        s.lastVerdict = verdict;
        s.lastAt = System.currentTimeMillis();

        boolean asked = !s.requesters.isEmpty();
        // nouvel indice, ou indice déjà signalé en orange qui passe au rouge (code injecté apparu en cours de partie…)
        boolean fresh = findings.stream().anyMatch(f -> f.severity() != Severity.INFO && !s.alerted.contains(alertKey(f)));
        String evidence = null;
        if (fresh || asked) {
            evidence = alert(p, s, findings, r, trigger, verdict);
        }
        if (asked) {
            notifyRequesters(s, summaryLine(s.name, verdict, findings, evidence));
        }
    }

    // ------------------------------------------------------------------ alertes

    private static ScanAnalyzer.Settings settings() {
        return new ScanAnalyzer.Settings(SpartanConfig.getAllowedJavaAgents(), SpartanConfig.getBlacklistedMods());
    }

    /**
     * Rapport au staff : Discord (indices + rapport complet joint), staff en jeu, alerts.log.
     * N'alerte que si un indice au moins est suspect ou fort. @return le fichier de preuve, ou null
     */
    private static String alert(ServerPlayer p, State s, List<Finding> findings, ScanReport r, String trigger, Severity verdict) {
        Severity v = verdict != null ? verdict : ScanAnalyzer.verdict(findings);
        if (v == null || v == Severity.INFO) {
            return null;
        }
        for (Finding f : findings) {
            s.alerted.add(f.key());
            s.alerted.add(alertKey(f));
        }
        CheckManager.recordSilent("ClientScan", p.getUUID());

        String json = GSON.toJson(evidence(p, s, findings, r, trigger, v));
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
                "Aucune action automatique : surveillez-le en spectateur, puis `/sban` si c'est confirmé." + referenceHint(),
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
    private static String referenceHint() {
        return reference.isEmpty()
                ? "\n⚠️ Client de référence non appris : le code inconnu reste en orange au premier contrôle. Depuis un jeu PROPRE : `/spartan reference learn <pseudo>`."
                : "";
    }

    private static String firstText(List<Finding> findings) {
        return findings.isEmpty() ? "" : findings.get(0).text();
    }

    private static JsonObject evidence(ServerPlayer p, State s, List<Finding> findings, ScanReport r, String trigger, Severity v) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "controle-integrite");
        root.addProperty("player", s.name);
        root.addProperty("uuid", p.getUUID().toString());
        root.addProperty("trigger", trigger);
        root.addProperty("capturedAt", System.currentTimeMillis());
        root.addProperty("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        root.addProperty("verdict", v.name());
        root.addProperty("reference", reference.summary());
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
        for (Consumer<Component> r : s.requesters) {
            try {
                r.accept(msg);
            } catch (RuntimeException ignored) {
                // demandeur déconnecté
            }
        }
        s.requesters.clear();
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
        List<String> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.get(p.getUUID());
            if (s != null && s.lastVerdict != null && s.lastVerdict != Severity.INFO) {
                out.add(s.name + " (" + s.lastVerdict.label + ")");
            }
        }
        return out;
    }

    /**
     * Contrôle du JVM du serveur lui-même (diagnostic : voir ce que le scanner relève
     * dans un environnement connu). Le résultat est écrit dans un fichier de preuve.
     */
    public static void selfScan(MinecraftServer server, Consumer<Component> reply) {
        CollecteLocale.Collecteur collecteur = CollecteLocale.COLLECTEUR;
        if (collecteur == null) {
            reply.accept(Component.literal("[Spartan] Autocontrôle indisponible ici : le code de contrôle n'est que dans le "
                    + "jar des joueurs (serveur dédié = jar -serveur). Utiliser /spartan scan <joueur>.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Thread serverThread = Thread.currentThread();
        Thread worker = new Thread(() -> {
            ScanReport r = collecteur.collecter(FMLPaths.MODSDIR.get(), FMLPaths.GAMEDIR.get(), serverThread,
                    SpartanConfig.getScanMixinWatch(), SpartanConfig.getScanClassProbes(),
                    SpartanConfig.getScanProcessPatterns(), SpartanConfig.getScanNativePatterns());
            server.execute(() -> {
                List<Finding> findings = ScanAnalyzer.analyze(r, reference, official, null, settings());
                JsonObject root = new JsonObject();
                root.addProperty("type", "autocontrole-serveur");
                JsonArray arr = new JsonArray();
                findings.forEach(f -> arr.add(f.severity() + " " + f.text()));
                root.add("findings", arr);
                root.add("report", GSON.toJsonTree(r));
                String file = EvidenceRecorder.writeJson("serveur", "autocontrole", GSON.toJson(root));
                reply.accept(Component.literal("[Spartan] Autocontrôle : " + r.files.size() + " fichiers, " + r.modules.size()
                        + " modules, " + r.mixins.size() + " configs Mixin, " + r.threadCount + " threads, "
                        + r.origins.size() + " origine(s) de code hors module, " + findings.size() + " indice(s) en "
                        + r.durationMs + " ms → evidence/" + file).withStyle(ChatFormatting.AQUA));
            });
        }, "Spartan-SelfScan");
        worker.setDaemon(true);
        worker.start();
    }

    private static long randomMs(int minSeconds, int maxSeconds) {
        int lo = Math.min(minSeconds, maxSeconds);
        int hi = Math.max(minSeconds, maxSeconds);
        return (lo + RNG.nextInt(hi - lo + 1)) * 1000L;
    }
}
