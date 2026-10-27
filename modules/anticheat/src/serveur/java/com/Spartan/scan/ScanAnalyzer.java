package com.Spartan.scan;

import com.Spartan.scan.Finding.Severity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Transforme un rapport de contrôle en indices, sans dépendance à Minecraft
 * (testé unitairement).
 *
 * Deux familles :
 * - indices universels, valables sans référence : agent Java ou natif,
 *   programme attaché au jeu, anticheat modifié en mémoire, classe de triche
 *   connue, DLL chargée depuis le dossier temporaire, mods chargés hors du
 *   dossier mods ;
 * - écarts au client de référence (modpack officiel) : jar inconnu ou modifié,
 *   module, config Mixin, chargeur de classes ou code d'origine inconnus.
 *
 * Tout ce qui APPARAÎT en cours de partie (absent du premier contrôle de la
 * session) est aggravé : c'est la signature d'une injection après le lancement
 * (module, config Mixin, code d'origine inconnue, DLL hors des dossiers sûrs).
 */
public final class ScanAnalyzer {
    /** Fournisseurs de mods d'un lancement normal. */
    private static final Set<String> NORMAL_PROVIDERS = Set.of("mods folder", "minecraft", "JarInJar");
    /** Fournisseurs propres à l'environnement de développement. */
    private static final Set<String> DEV_PROVIDERS = Set.of("userdev classpath", "exploded directory");
    /** Chargeurs de classes d'un jeu Forge normal. */
    private static final Set<String> NORMAL_LOADERS = Set.of(
            "jdk.internal.loader.ClassLoaders$AppClassLoader",
            "jdk.internal.loader.ClassLoaders$PlatformClassLoader",
            "jdk.internal.loader.ClassLoaders$BootClassLoader",
            "cpw.mods.modlauncher.TransformingClassLoader",
            "cpw.mods.cl.ModuleClassLoader",
            "jdk.internal.reflect.DelegatingClassLoader");

    /** Classes visées par Mixin qui servent aux triches, et ce qu'elles permettent. */
    private static final Map<String, String> SENSITIVE = new LinkedHashMap<>();

    static {
        SENSITIVE.put("com.Spartan.", "l'anticheat");
        SENSITIVE.put("net.minecraft.client.renderer.LevelRenderer", "le rendu du monde (ESP / X-ray)");
        SENSITIVE.put("net.minecraft.client.renderer.entity.", "le rendu des entités (ESP)");
        SENSITIVE.put("net.minecraft.client.renderer.GameRenderer", "le rendu (ESP / FOV)");
        SENSITIVE.put("net.minecraft.client.renderer.LightTexture", "la luminosité (fullbright)");
        SENSITIVE.put("net.minecraft.client.renderer.FogRenderer", "le brouillard (no-fog)");
        SENSITIVE.put("net.minecraft.client.renderer.ItemBlockRenderTypes", "la transparence des blocs (X-ray)");
        SENSITIVE.put("net.minecraft.client.renderer.block.", "le rendu des blocs (X-ray)");
        SENSITIVE.put("net.minecraft.client.renderer.culling.", "la visibilité des objets (ESP)");
        SENSITIVE.put("net.minecraft.client.renderer.chunk.", "le rendu des blocs (X-ray)");
        SENSITIVE.put("net.minecraft.client.player.LocalPlayer", "le mouvement du joueur (fly / speed / NoSlow)");
        SENSITIVE.put("net.minecraft.client.player.KeyboardInput", "les entrées clavier (NoSlow / bots)");
        SENSITIVE.put("net.minecraft.client.player.Input", "les entrées clavier (NoSlow / bots)");
        SENSITIVE.put("net.minecraft.world.entity.Entity", "le mouvement / les collisions");
        SENSITIVE.put("net.minecraft.world.entity.LivingEntity", "le mouvement / l'usage des objets");
        SENSITIVE.put("net.minecraft.world.entity.player.Player", "le joueur (portée, mouvement)");
        SENSITIVE.put("net.minecraft.client.multiplayer.MultiPlayerGameMode", "les attaques et le minage");
        SENSITIVE.put("net.minecraft.client.multiplayer.ClientPacketListener", "les paquets reçus (anti-recul)");
        SENSITIVE.put("net.minecraft.network.Connection", "les paquets réseau");
        SENSITIVE.put("net.minecraft.client.Minecraft", "la boucle du jeu (clics, timer)");
        SENSITIVE.put("net.minecraft.client.MouseHandler", "la souris (aimbot)");
        SENSITIVE.put("net.minecraft.client.Timer", "la vitesse du jeu (timer)");
        SENSITIVE.put("com.tacz.", "les armes TaCZ (recul, visée)");
    }

    /** Bibliothèques natives d'un Java 17 normal (Windows et Linux), dossier bin / lib / server. */
    private static final java.util.regex.Pattern JDK_NATIF = java.util.regex.Pattern.compile("^(lib)?(attach|awt|awt_xawt|awt_headless"
            + "|dt_shmem|dt_socket|extnet|fontmanager|freetype|instrument|j2gss|j2pcsc|j2pkcs11|jaas|jabsysinfo|java"
            + "|javaaccessbridge|javajpeg|jawt|jdwp|jimage|jli|jsig|jsound|jsvml|jvm|lcms|le|management|management_agent"
            + "|management_ext|mlib_image|net|nio|prefs|rmi|saproc|sctp|simdsort|splashscreen|sspi_bridge|sunmscapi|syslookup"
            + "|verify|w2k_lsa_auth|windowsaccessbridge-64|zip|msvcp140(_1|_2|_atomic_wait|_codecvt_ids)?|vcruntime140(_1)?"
            + "|ucrtbase|concrt140)\\.(dll|so)$|^api-ms-win-[a-z0-9-]+\\.dll$|^(java|javaw)\\.exe$");

    /** Réglages venant de la config du serveur. */
    public record Settings(List<String> allowedAgents, List<String> blacklistedMods) {
    }

    private ScanAnalyzer() {
    }

    /**
     * @param ref      client de référence (peut être vide)
     * @param baseline inventaire du premier contrôle de la session, ou null pour le premier
     */
    public static List<Finding> analyze(ScanReport r, ReferenceProfile ref, Set<String> baseline, Settings s) {
        return analyze(r, ref, null, baseline, s);
    }

    /**
     * @param off modpack officiel fabriqué avec le serveur (peut être null ou vide) : juge les jars du dossier mods et
     *            les configs Mixin même sans client de référence appris
     */
    public static List<Finding> analyze(ScanReport r, ReferenceProfile ref, OfficialProfile off, Set<String> baseline,
                                        Settings s) {
        List<Finding> out = new ArrayList<>();
        boolean hasRef = ref != null && !ref.isEmpty();
        OfficialProfile official = off != null && !off.isEmpty() ? off : null;
        jvm(r, ref, hasRef, official != null, s, out);
        if (r.attachListener) {
            out.add(new Finding(Severity.STRONG, "attach",
                    "un programme s'est attaché au jeu en cours de partie (injecteur ?)"));
        }
        for (String t : r.tamper) {
            out.add(new Finding(Severity.STRONG, "tamper:" + t, "anticheat modifié en mémoire : " + t));
        }
        for (String c : r.classHits) {
            out.add(new Finding(Severity.STRONG, "class:" + c, "classe de triche connue présente : " + c));
        }
        for (String p : r.processHits) {
            out.add(new Finding(Severity.SUSPECT, "proc:" + p.toLowerCase(Locale.ROOT),
                    "programme de triche / d'injection ouvert sur le PC : " + p));
        }
        for (ScanReport.NativeEntry n : r.natives) {
            if (!n.reason().isEmpty()) {
                out.add(new Finding(Severity.SUSPECT, "dll:" + n.name().toLowerCase(Locale.ROOT),
                        "bibliothèque native chargée dans le jeu : " + n.name() + " (" + n.place() + ", " + n.reason() + ")"));
            } else if (n.place().startsWith("%JAVA%") && !JDK_NATIF.matcher(n.name().toLowerCase(Locale.ROOT)).matches()) {
                // une DLL glissée dans le dossier du Java du jeu (souvent celui du launcher) est chargée à la place de la vraie
                out.add(new Finding(Severity.SUSPECT, "dll:jdk:" + n.name().toLowerCase(Locale.ROOT),
                        "bibliothèque inconnue dans le Java du jeu : " + n.name() + " (" + n.place() + ") : DLL ajoutée au Java ?"));
            }
        }
        injection(r, out);
        if (r.unnamedThreads > 0) {
            // des threads sans nom qui exécutent du code inconnu : ce sont ceux du client injecté (Doomsday, 11/10)
            boolean actifs = r.origins.stream().anyMatch(o -> !(hasRef && ref.origins.contains(o.key()))
                    && o.threads().contains("(sans nom)"));
            out.add(new Finding(actifs ? Severity.STRONG : Severity.SUSPECT, "threads:unnamed",
                    r.unnamedThreads + " thread(s) sans nom dans le jeu" + (actifs
                            ? " qui exécutent du code d'origine inconnue : client injecté"
                            : " (trace fréquente d'un injecteur)")));
        }
        providers(r, ref, hasRef, official != null, s, out);
        files(r, ref, hasRef, official, out);
        modules(r, ref, hasRef, official, out);
        mixins(r, ref, hasRef, official, out);
        code(r, ref, hasRef, out);
        if (baseline != null) {
            sessionChanges(r, baseline, out);
        }
        for (String e : r.errors) {
            out.add(new Finding(Severity.INFO, "error:" + e, "étape du contrôle en échec : " + e));
        }
        out.sort(Comparator.comparing(Finding::severity).reversed());
        return out;
    }

    /** Code natif injecté (relevé sous Windows par le jeu du joueur, voir InjectionCollector). */
    private static void injection(ScanReport r, List<Finding> out) {
        for (String e : r.injection) {
            String[] p = e.split("\\|", -1);
            if (p[0].equals("thread") && p.length >= 2) {
                out.add(new Finding(Severity.STRONG, "inject:thread:" + p[1],
                        "thread démarré hors de tout module (" + p[1] + ") : code natif injecté en mémoire (DLL cachée)"));
            } else if (p[0].equals("hook") && p.length >= 4) {
                boolean cache = p[3].isEmpty();
                out.add(new Finding(cache ? Severity.STRONG : Severity.SUSPECT, "inject:hook:" + p[1] + ":" + p[3],
                        "affichage détourné : " + p[1] + " saute vers " + (cache ? "du code injecté sans module (" + p[2] + ")"
                                : "le module " + p[3] + " (overlay inconnu ou ESP)")));
            }
        }
    }

    /** Plus haute gravité, ou null si aucun indice. */
    public static Severity verdict(List<Finding> findings) {
        Severity max = null;
        for (Finding f : findings) {
            if (max == null || f.severity().compareTo(max) > 0) {
                max = f.severity();
            }
        }
        return max;
    }

    /** Options du launcher qui chargent les mods hors du dossier mods : chaque jar chargé est alors jugé un par un. */
    private static final Set<String> LAUNCHER_FML_ARGS = Set.of("arg:--fml.mavenroots", "arg:--fml.modlists", "arg:--fml.mods");

    private static void jvm(ScanReport r, ReferenceProfile ref, boolean hasRef, boolean official, Settings s,
                            List<Finding> out) {
        for (String flag : r.jvmFlags) {
            String key = "jvm:" + flag.toLowerCase(Locale.ROOT);
            if (flag.startsWith("-javaagent:")) {
                String name = flag.substring("-javaagent:".length());
                agent(r, name, key, ref, hasRef, s, out);
            } else if (flag.startsWith("-agentlib:jdwp")) {
                out.add(new Finding(Severity.STRONG, key, "débogueur Java activé (permet de modifier le jeu en direct)"));
            } else if (flag.startsWith("-agentpath:") || flag.startsWith("-agentlib:")) {
                out.add(new Finding(Severity.STRONG, key, "agent natif chargé au lancement : " + flag));
            } else if (flag.startsWith("-Xbootclasspath")) {
                out.add(new Finding(Severity.STRONG, key, "code ajouté au chargeur de démarrage de Java (-Xbootclasspath)"));
            } else if (flag.startsWith("-Djava.system.class.loader")) {
                out.add(new Finding(Severity.STRONG, key, "chargeur de classes remplacé au lancement : " + flag));
            } else if (flag.equals("-Djdk.attach.allowAttachSelf")) {
                out.add(new Finding(Severity.STRONG, key, "le jeu s'autorise à s'injecter du code lui-même (allowAttachSelf)"));
            } else if (official && LAUNCHER_FML_ARGS.contains(flag.toLowerCase(Locale.ROOT))) {
                out.add(new Finding(Severity.INFO, key, "mods chargés par le launcher hors du dossier mods ("
                        + flag.substring(4) + ") : chaque jar est comparé au modpack officiel"));
            } else if (flag.startsWith("arg:--fml.")) {
                out.add(new Finding(Severity.STRONG, key, "option de lancement qui charge du code hors du dossier mods : "
                        + flag.substring(4)));
            } else if (flag.equals("-XX:+EnableDynamicAgentLoading") || flag.equals("-XX:+StartAttachListener")) {
                out.add(new Finding(Severity.SUSPECT, key, "injection à chaud explicitement autorisée (" + flag + ")"));
            } else if (flag.equals("-Xverify:none")) {
                out.add(new Finding(Severity.SUSPECT, key, "vérification du code Java désactivée (-Xverify:none)"));
            } else if (flag.startsWith("env:")) {
                out.add(new Finding(Severity.INFO, key, "variable d'environnement Java définie : " + flag.substring(4)));
            }
        }
    }

    private static void agent(ScanReport r, String name, String key, ReferenceProfile ref, boolean hasRef,
                              Settings s, List<Finding> out) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean allowedName = s.allowedAgents().stream().anyMatch(a -> !a.isBlank() && lower.contains(a));
        if (!allowedName) {
            out.add(new Finding(Severity.STRONG, key, "agent Java chargé au lancement : " + name));
            return;
        }
        if (!hasRef || ref.agents.isEmpty()) {
            return; // autorisé par son nom, rien à comparer
        }
        String sha = r.files.stream().filter(f -> f.origin().equals("agent") && f.name().equalsIgnoreCase(name))
                .map(ScanReport.FileEntry::sha256).findFirst().orElse("");
        if (!ref.agents.contains(lower + ":" + sha)) {
            out.add(new Finding(Severity.STRONG, key + ":" + sha, "agent Java au nom autorisé mais différent de celui du launcher : "
                    + name + (sha.isEmpty() ? "" : " (sha256 " + shortSha(sha) + ")")));
        }
    }

    private static void providers(ScanReport r, ReferenceProfile ref, boolean hasRef, boolean official, Settings s,
                                  List<Finding> out) {
        for (String lf : r.loadedFiles) {
            String[] parts = lf.split("\\|", -1);
            String provider = parts[0];
            String file = parts.length > 1 ? parts[1] : "?";
            String ids = parts.length > 2 ? parts[2] : "";
            for (String id : ids.split(",")) {
                if (!id.isBlank() && s.blacklistedMods().contains(id.toLowerCase(Locale.ROOT))) {
                    out.add(new Finding(Severity.STRONG, "banned:" + id, "mod interdit chargé : " + id + " (" + file + ")"));
                }
            }
            // « maven libs » : dossier de mods du launcher (Helios) ; ses jars sont jugés un par un avec le modpack officiel
            if (NORMAL_PROVIDERS.contains(provider) || (hasRef && ref.providers.contains(provider))
                    || (official && "maven libs".equals(provider))) {
                continue;
            }
            Severity sev = DEV_PROVIDERS.contains(provider) ? Severity.INFO : Severity.STRONG;
            out.add(new Finding(sev, "provider:" + provider + "|" + file,
                    "mod chargé hors du dossier mods (" + provider + ") : " + file + (ids.isEmpty() ? "" : " [" + ids + "]")));
        }
    }

    private static void files(ScanReport r, ReferenceProfile ref, boolean hasRef, OfficialProfile off, List<Finding> out) {
        if (!hasRef && off == null) {
            return;
        }
        for (ScanReport.FileEntry f : r.files) {
            if (f.origin().equals("agent") || f.sha256().isEmpty()
                    || (hasRef && ref.fileHashes.contains(f.sha256())) || (off != null && off.knowsFile(f.sha256()))) {
                continue;
            }
            boolean inMods = f.origin().equals("mods") || f.origin().equals("modlist");
            if (!inMods && !hasRef) {
                continue; // bibliothèques du lanceur : seul le client de référence appris les connaît
            }
            String where = f.origin().equals("mods") ? "dans le dossier mods"
                    : f.origin().equals("modlist") ? "chargé par le launcher (hors du dossier mods)"
                    : "du jeu (" + f.origin().substring(f.origin().indexOf(':') + 1) + ")";
            String bare = f.name().endsWith("/") ? f.name().substring(0, f.name().length() - 1) : f.name();
            boolean modified = (hasRef && ref.fileNames.contains(ReferenceProfile.fileKey(f)))
                    || (inMods && off != null && off.knowsName(bare));
            Severity sev = modified || !f.origin().equals("mods") ? Severity.STRONG : Severity.SUSPECT;  // hors dossier : fort
            String what = modified ? "jar MODIFIÉ " : "jar inconnu ";
            out.add(new Finding(sev, "file:" + f.sha256(), what + where + " : " + f.name() + " ("
                    + human(f.size()) + ", sha256 " + shortSha(f.sha256()) + ")"));
        }
    }

    private static void modules(ScanReport r, ReferenceProfile ref, boolean hasRef, OfficialProfile off,
                                List<Finding> out) {
        if (!hasRef) {
            return;
        }
        for (String m : r.modules) {
            String key = ReferenceProfile.moduleKey(m);
            if (ref.modules.contains(key)) {
                continue;
            }
            // Mod du jeu dont le jar est un jar officiel (son empreinte est contrôlée par files()) : un mod ajouté
            // ou renommé après l'apprentissage du client de référence n'est pas « inconnu ».
            int at = m.indexOf('@');
            if (off != null && key.startsWith("GAME/") && at >= 0 && off.knowsName(m.substring(at + 1))) {
                continue;
            }
            String layer = key.contains("/") ? key.substring(0, key.indexOf('/')) : "?";
            boolean launchLayer = !layer.equals("GAME");
            out.add(new Finding(launchLayer ? Severity.STRONG : Severity.SUSPECT, "module:" + key,
                    (launchLayer ? "composant de lancement inconnu (transformateur / coremod) : " : "module de code inconnu chargé : ") + m));
        }
    }

    /** Classes que la config Mixin de Spartan lui-même modifie (anti-ESP). */
    private static final Set<String> OWN_MIXIN_TARGETS = Set.of("net.minecraft.server.level.ServerPlayer",
            "net.minecraft.server.level.ServerLevel", "com.tacz.guns.sound.SoundManager");

    /**
     * La config Mixin de Spartan (même nom, même paquet, mêmes cibles) : connue d'office,
     * pour qu'une mise à jour de Spartan n'alerte pas sur tout le monde avant que la
     * référence soit réapprise. Une config qui prendrait son nom pour viser autre chose
     * reste signalée.
     */
    public static boolean isSpartanOwn(ScanReport.MixinEntry m) {
        return "spartan.mixins.json".equals(m.name()) && "com.Spartan.mixin".equals(m.pkg())
                && m.targetCount() <= OWN_MIXIN_TARGETS.size() && OWN_MIXIN_TARGETS.containsAll(m.targets());
    }

    private static void mixins(ScanReport r, ReferenceProfile ref, boolean hasRef, OfficialProfile off, List<Finding> out) {
        if (!hasRef && off == null) {
            return;
        }
        for (ScanReport.MixinEntry m : r.mixins) {
            String fp = ScanReport.hex(ScanReport.mixinFingerprint(m.name()));
            if (off != null && off.knowsMixin(fp) && !m.pkg().isEmpty() && !off.mixinPackage(fp).isEmpty()
                    && !off.mixinPackage(fp).equals(m.pkg())) {
                out.add(new Finding(Severity.STRONG, "mixin:" + fp + ":paquet",
                        "config Mixin au nom d'une config officielle mais d'un autre paquet : " + m.name() + " ("
                                + m.pkg() + " au lieu de " + off.mixinPackage(fp) + ") — triche déguisée"));
                continue;
            }
            if ((hasRef && ref.mixins.containsKey(fp)) || (off != null && off.knowsMixin(fp)) || isSpartanOwn(m)) {
                continue;
            }
            Set<String> touched = new TreeSet<>();
            for (String t : m.targets()) {
                String what = sensitive(t);
                if (what != null) {
                    touched.add(what);
                }
            }
            String detail = (m.pkg().isEmpty() ? "" : m.pkg() + ", ")
                    + (m.source().isEmpty() ? "" : "déclarée par " + m.source() + ", ")
                    + (m.targetCount() > 0 ? m.targetCount() + " classe(s) visée(s)" : "classes visées inconnues");
            String targets = m.targets().isEmpty() ? "" : " → modifie " + String.join(", ", shortNames(m.targets(), 6));
            out.add(new Finding(touched.isEmpty() ? Severity.SUSPECT : Severity.STRONG, "mixin:" + fp,
                    "config Mixin inconnue : " + m.name() + " (" + detail + ")"
                            + targets + (touched.isEmpty() ? "" : " — touche " + String.join(", ", touched))));
        }
    }

    private static void code(ScanReport r, ReferenceProfile ref, boolean hasRef, List<Finding> out) {
        for (ScanReport.OriginEntry o : r.origins) {
            if (hasRef && ref.origins.contains(o.key())) {
                continue;
            }
            // Noms de classes impossibles pour du code Java compilé (caractères de contrôle) : code brouillé chargé
            // directement en mémoire, sans fichier. Aucun mod honnête n'en a : indice fort même sans référence.
            boolean brouille = nomImpossible(o.key()) || o.classes().stream().anyMatch(ScanAnalyzer::nomImpossible);
            List<String> classes = o.classes().stream().map(ScanAnalyzer::lisible).toList();
            out.add(new Finding(hasRef || brouille ? Severity.STRONG : Severity.SUSPECT, "origin:" + o.key(),
                    (brouille ? "client injecté : code brouillé (noms de classes aux caractères de contrôle, impossibles en Java"
                            + " normal) chargé en mémoire sans fichier (" + lisible(o.key()) + ") : "
                            : "code d'origine inconnue en cours d'exécution (" + lisible(o.key()) + ") : ")
                            + String.join(", ", classes) + " — threads : " + String.join(", ", o.threads())));
        }
        for (String l : r.loaders) {
            if (NORMAL_LOADERS.contains(l) || (hasRef && ref.loaders.contains(l))) {
                continue;
            }
            out.add(new Finding(Severity.SUSPECT, "loader:" + l, "chargeur de classes inhabituel : " + l));
        }
    }

    /** Ce qui n'était pas là au premier contrôle de la session. */
    private static void sessionChanges(ScanReport r, Set<String> baseline, List<Finding> out) {
        Set<String> now = inventory(r);
        Set<String> appeared = new TreeSet<>(now);
        appeared.removeAll(baseline);
        if (appeared.isEmpty()) {
            return;
        }
        // Les indices déjà relevés pour ces éléments sont aggravés…
        for (int i = 0; i < out.size(); i++) {
            Finding f = out.get(i);
            if (appeared.contains(inventoryKeyOf(f.key()))) {
                out.set(i, f.withSeverity(Severity.STRONG, " (apparu en cours de partie)"));
                appeared.remove(inventoryKeyOf(f.key()));
            }
        }
        // … et le reste est signalé : modules et configs Mixin ne changent jamais après le
        // lancement ; une DLL ordinaire (pilote, overlay) peut se charger plus tard : soupçon.
        Map<String, String> mixinNames = new LinkedHashMap<>();
        for (ScanReport.MixinEntry m : r.mixins) {
            mixinNames.put("x:" + ScanReport.hex(ScanReport.mixinFingerprint(m.name())), m.name() + " (" + m.pkg() + ")");
        }
        Map<String, ScanReport.NativeEntry> natives = new LinkedHashMap<>();
        for (ScanReport.NativeEntry n : r.natives) {
            natives.put("d:" + n.name().toLowerCase(Locale.ROOT), n);
        }
        for (String item : appeared) {
            String what = mixinNames.getOrDefault(item, item.substring(item.indexOf(':') + 1));
            if (item.startsWith("o:")) {
                continue; // code d'origine connue de la référence : rien à dire (les autres sont aggravés plus haut)
            }
            if (item.startsWith("d:")) {
                ScanReport.NativeEntry n = natives.get(item);
                String place = n == null ? "" : n.place();
                // Programmes installés et dossier du jeu : un pilote ou un outil peut se charger tard (soupçon). Ailleurs
                // (profil, Temp, Téléchargements, dossier quelconque), hors overlay connu : c'est la trace d'une injection.
                boolean sure = place.startsWith("%PROGRAMFILES%") || place.startsWith("%JEU%") || overlay(what);
                out.add(new Finding(sure ? Severity.SUSPECT : Severity.STRONG, "late:" + item,
                        "bibliothèque native chargée en cours de partie : " + what + (place.isEmpty() ? "" : " (" + place + ")")
                                + (sure ? "" : " — injection probable")));
            } else if (item.startsWith("m:")) {
                out.add(new Finding(Severity.STRONG, "late:" + item, "module de code chargé en cours de partie : " + what));
            } else {
                out.add(new Finding(Severity.STRONG, "late:" + item, "config Mixin ajoutée en cours de partie : " + what));
            }
        }
    }

    /**
     * Inventaire comparable d'un contrôle à l'autre : modules et configs Mixin (figés
     * après le lancement dans un jeu normal) et bibliothèques natives.
     */
    public static Set<String> inventory(ScanReport r) {
        Set<String> out = new TreeSet<>();
        for (String m : r.modules) {
            out.add("m:" + ReferenceProfile.moduleKey(m));
        }
        for (ScanReport.MixinEntry m : r.mixins) {
            out.add("x:" + ScanReport.hex(ScanReport.mixinFingerprint(m.name())));
        }
        for (ScanReport.NativeEntry n : r.natives) {
            out.add("d:" + n.name().toLowerCase(Locale.ROOT));
        }
        // code d'origine inconnue vu dans les piles : un client injecté après la connexion n'y est pas au premier contrôle
        for (ScanReport.OriginEntry o : r.origins) {
            out.add("o:" + o.key());
        }
        return out;
    }

    /** Lien entre la clé d'un indice et la clé d'inventaire correspondante. */
    private static String inventoryKeyOf(String findingKey) {
        if (findingKey.startsWith("module:")) {
            return "m:" + findingKey.substring(7);
        }
        if (findingKey.startsWith("mixin:")) {
            return "x:" + findingKey.substring(6);
        }
        if (findingKey.startsWith("dll:")) {
            return "d:" + findingKey.substring(4);
        }
        if (findingKey.startsWith("origin:")) {
            return "o:" + findingKey.substring(7);
        }
        return "";
    }

    /**
     * Nom de classe qu'aucun compilateur Java ne produit : caractères de contrôle (C0 / C1). Les brouilleurs de clients
     * de triche s'en servent pour rendre leur code illisible ; la JVM les accepte quand la classe est chargée à la main.
     */
    static boolean nomImpossible(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                return true;
            }
        }
        return false;
    }

    /** Nom lisible dans une alerte : les caractères de contrôle deviennent \xNN. */
    static String lisible(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                sb.append(String.format(Locale.ROOT, "\\x%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Overlays et outils légitimes qui se chargent dans les jeux (noms de DLL, en minuscules). */
    private static final List<String> OVERLAYS = List.of("discord", "gameoverlayrenderer", "graphics-hook", "obs-",
            "nvspcap", "nvinject", "rtss", "medal", "overwolf", "owclient", "rivatuner", "action_x64", "bdcam", "fraps",
            "xsplit", "amdhook", "steam", "epicgames", "uplay", "galaxy", "radeon", "igo64", "eosovh");

    static boolean overlay(String dll) {
        String n = dll.toLowerCase(Locale.ROOT);
        return OVERLAYS.stream().anyMatch(n::contains);
    }

    static String sensitive(String target) {
        for (Map.Entry<String, String> en : SENSITIVE.entrySet()) {
            String k = en.getKey();
            if (k.endsWith(".") ? target.startsWith(k) : target.equals(k) || target.startsWith(k + "$")) {
                return en.getValue();
            }
        }
        return null;
    }

    private static List<String> shortNames(List<String> classes, int max) {
        List<String> out = new ArrayList<>();
        for (String c : classes) {
            if (out.size() >= max) {
                out.add("…");
                break;
            }
            out.add(c.substring(c.lastIndexOf('.') + 1));
        }
        return out;
    }

    static String shortSha(String sha) {
        return sha.length() > 12 ? sha.substring(0, 12) + "…" : sha;
    }

    static String human(long bytes) {
        if (bytes >= 1 << 20) {
            return String.format(Locale.ROOT, "%.1f Mo", bytes / 1048576.0);
        }
        if (bytes >= 1024) {
            return (bytes / 1024) + " Ko";
        }
        return bytes + " o";
    }
}
