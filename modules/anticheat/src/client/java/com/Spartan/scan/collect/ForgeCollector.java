package com.Spartan.scan.collect;

import com.Spartan.integrity.LaunchToken;
import com.Spartan.scan.ScanReport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import java.io.InputStream;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * Ce que Forge a réellement chargé : modules de chaque couche de ModLauncher
 * (bibliothèques, services de transformation, Minecraft, Forge, mods),
 * fichiers de mods et leur provenance, configs Mixin.
 *
 * C'est la vérité du jeu en cours d'exécution, indépendamment du contenu du
 * dossier mods : un mod chargé par une autre voie (option de lancement, service
 * de transformation, coremod) apparaît ici.
 */
public final class ForgeCollector {
    /** Jars déjà hachés (chemin + taille + date) : les contrôles suivants ne relisent rien. */
    private static final Map<String, String> HASH_CACHE = new ConcurrentHashMap<>();
    private static final long MAX_HASHED_FILE = 256L << 20;
    private static final long MAX_HASHED_TOTAL = 1L << 30;

    private ForgeCollector() {
    }

    /**
     * Couches de modules du jeu, de la plus haute (GAME) à la plus basse, hors JDK.
     * Le nom vient de ModLauncher quand il est disponible (BOOT, SERVICE, PLUGIN, GAME).
     */
    public static Map<ModuleLayer, String> layers() {
        Map<ModuleLayer, String> names = new IdentityHashMap<>();
        try {
            Launcher.INSTANCE.findLayerManager().ifPresent(m -> {
                for (IModuleLayerManager.Layer l : IModuleLayerManager.Layer.values()) {
                    m.getLayer(l).ifPresent(layer -> names.putIfAbsent(layer, l.name()));
                }
            });
        } catch (Throwable t) {
            // ModLauncher absent (tests) : couches nommées par profondeur
        }
        Map<ModuleLayer, String> out = new LinkedHashMap<>();
        ModuleLayer start = ForgeCollector.class.getModule().getLayer();
        List<ModuleLayer> todo = new ArrayList<>();
        if (start != null) {
            todo.add(start);
        }
        names.keySet().forEach(todo::add);
        for (int i = 0; i < todo.size() && i < 64; i++) {
            ModuleLayer layer = todo.get(i);
            if (layer == ModuleLayer.boot() || out.containsKey(layer)) {
                continue;
            }
            out.put(layer, names.getOrDefault(layer, "L" + out.size()));
            todo.addAll(layer.parents());
        }
        return out;
    }

    /** Tous les noms de modules connus du jeu, JDK compris (pour juger l'origine du code). */
    public static Set<String> knownModuleNames(Map<ModuleLayer, String> layers) {
        Set<String> known = new TreeSet<>();
        for (Module m : ModuleLayer.boot().modules()) {
            known.add(m.getName());
        }
        for (ModuleLayer layer : layers.keySet()) {
            for (Module m : layer.modules()) {
                known.add(m.getName());
            }
        }
        return known;
    }

    /** Modules de chaque couche, avec l'empreinte des jars situés hors du dossier mods. */
    public static void modules(ScanReport r, Map<ModuleLayer, String> layers, Path modsDir) {
        Path mods = modsDir.toAbsolutePath().normalize();
        long[] budget = {MAX_HASHED_TOTAL};
        List<String> entries = new ArrayList<>();
        Set<Path> hashed = new HashSet<>();
        for (Map.Entry<ModuleLayer, String> en : layers.entrySet()) {
            for (ResolvedModule rm : en.getKey().configuration().modules()) {
                String fileName = rm.reference().location().map(ForgeCollector::lastSegment).orElse("");
                entries.add(en.getValue() + "/" + rm.name() + "@" + fileName);
                Path file = location(rm);
                if (file == null) {
                    continue;
                }
                Path abs = file.toAbsolutePath().normalize();
                // Jars du dossier mods : déjà dans l'inventaire. Un jar contenant plusieurs modules : une fois.
                if (!abs.startsWith(mods) && hashed.add(abs)) {
                    String sha = hash(file, budget);
                    if (sha != null) {
                        r.files.add(new ScanReport.FileEntry("layer:" + en.getValue(),
                                JvmCollector.fileName(file.toString()), size(file), sha));
                    }
                }
            }
        }
        entries.sort(null);
        r.modules.addAll(entries);
    }

    /**
     * Jars de mods chargés par Forge HORS du dossier mods (launcher Helios : fournisseur « maven libs » et options
     * --fml.mavenRoots / --fml.modLists ; ou tout autre moyen) : hachés et jugés un par un comme ceux du dossier mods
     * (origine « modlist »). Un jar de triche glissé dans le dossier du launcher est donc vu comme les autres.
     */
    public static void modsHorsDossier(ScanReport r, Path modsDir) {
        Path mods = modsDir.toAbsolutePath().normalize();
        long[] budget = {MAX_HASHED_TOTAL};
        Set<Path> vus = new HashSet<>();
        ModList.get().getModFiles().forEach(info -> {
            IModFile f = info.getFile();
            String provider = f.getProvider() == null ? "?" : f.getProvider().name();
            if ("minecraft".equals(provider) || provider.toLowerCase(Locale.ROOT).contains("jarinjar")) {
                return; // Minecraft / Forge eux-mêmes, jars imbriqués (jugés avec leur jar)
            }
            Path p = f.getFilePath();
            if (p == null || !Files.isRegularFile(p)) {
                return;
            }
            Path abs = p.toAbsolutePath().normalize();
            if (abs.startsWith(mods) || !vus.add(abs)) {
                return;
            }
            String sha = hash(p, budget);
            if (sha != null) {
                r.files.add(new ScanReport.FileEntry("modlist", JvmCollector.fileName(p.toString()), size(p), sha));
            }
        });
    }

    /** Fichiers de mods vus par FML : provenance (dossier mods, JarInJar…), nom, identifiants. */
    public static void loadedFiles(ScanReport r) {
        List<String> out = new ArrayList<>();
        ModList.get().getModFiles().forEach(info -> {
            IModFile f = info.getFile();
            String provider = f.getProvider() == null ? "?" : f.getProvider().name();
            String ids = f.getModInfos().stream().map(IModInfo::getModId).collect(Collectors.joining(","));
            out.add(ScanReport.cut(provider, 40) + "|" + ScanReport.cut(f.getFileName(), 120) + "|" + ScanReport.cut(ids, 80));
        });
        out.sort(null);
        r.loadedFiles.addAll(out);
    }

    /** Ce qu'on sait d'une config Mixin, rassemblé depuis plusieurs sources. */
    private static final class MixinAcc {
        final String name;
        String pkg = "";
        String source = "";
        final Set<String> watched = new TreeSet<>();
        final Set<String> targets = new TreeSet<>();

        MixinAcc(String name) {
            this.name = name;
        }
    }

    /**
     * Configs Mixin du jeu. Mixin retire ses configs de sa liste publique dès qu'il les
     * applique : on les retrouve donc autrement.
     * 1. déclarées par les jars chargés (attribut MixinConfigs du manifeste) ;
     * 2. pas encore appliquées (liste publique de Mixin) ;
     * 3. réellement appliquées aux classes sensibles (watchClasses : rendu, mouvement, réseau…),
     *    ce qui attrape aussi une config enregistrée par du code, hors manifeste.
     * Les classes visées sont jointes pour les configs inconnues du serveur et pour toute
     * config qui vise l'anticheat.
     */
    public static void mixins(ScanReport r, Set<Long> knownMixins, boolean referenceKnown, List<String> watchClasses) {
        Map<String, MixinAcc> byName = new TreeMap<>();
        ModList.get().getModFiles().forEach(info -> {
            IModFile f = info.getFile();
            String declared = manifestAttribute(f, "MixinConfigs");
            if (declared == null) {
                return;
            }
            for (String cfg : declared.split(",")) {
                String name = cfg.trim();
                if (!name.isEmpty() && byName.size() < ScanReport.MAX_MIXINS) {
                    MixinAcc a = byName.computeIfAbsent(name, MixinAcc::new);
                    a.source = f.getFileName();
                    readConfig(f, name, a);
                }
            }
        });
        for (Config c : Mixins.getConfigs()) {
            IMixinConfig ic = c.getConfig();
            MixinAcc a = byName.computeIfAbsent(ic != null ? ic.getName() : c.getName(), MixinAcc::new);
            addConfigDetails(a, ic);
        }
        for (String cls : watchClasses) {
            Set<IMixinInfo> applied;
            try {
                applied = Mixins.getMixinsForClass(cls);
            } catch (RuntimeException e) {
                continue;
            }
            for (IMixinInfo mi : applied) {
                IMixinConfig ic = mi.getConfig();
                if (ic == null) {
                    continue;
                }
                MixinAcc a = byName.computeIfAbsent(ic.getName(), MixinAcc::new);
                a.watched.add(cls);
                addConfigDetails(a, ic);
            }
        }
        for (MixinAcc a : byName.values()) {
            for (String t : a.targets) {
                if (watchClasses.contains(t)) {
                    a.watched.add(t);
                }
            }
            boolean touchesSpartan = a.targets.stream().anyMatch(t -> t.startsWith("com.Spartan."));
            if (touchesSpartan) {
                r.tamper.add("config Mixin " + ScanReport.cut(a.name, 80) + " vise l'anticheat");
            }
            boolean unknown = referenceKnown && !knownMixins.contains(ScanReport.mixinFingerprint(a.name));
            List<String> sample = new ArrayList<>();
            if (unknown || touchesSpartan) {
                // Classes sensibles d'abord : ce sont elles qui disent ce que fait la config.
                for (String t : a.watched) {
                    if (sample.size() < ScanReport.MAX_TARGETS) {
                        sample.add(t);
                    }
                }
                for (String t : a.targets) {
                    if (sample.size() < ScanReport.MAX_TARGETS && !sample.contains(t)) {
                        sample.add(t);
                    }
                }
            }
            r.mixins.add(new ScanReport.MixinEntry(ScanReport.cut(a.name, 200), ScanReport.cut(a.pkg, 200),
                    ScanReport.cut(a.source, 120), Math.max(a.targets.size(), a.watched.size()), sample));
        }
    }

    private static void addConfigDetails(MixinAcc a, IMixinConfig ic) {
        if (ic == null) {
            return;
        }
        if (a.pkg.isEmpty() && ic.getMixinPackage() != null) {
            a.pkg = ic.getMixinPackage();
        }
        try {
            if (ic.getTargets() != null) {
                ic.getTargets().forEach(t -> a.targets.add(t.replace('/', '.')));
            }
        } catch (RuntimeException e) {
            // config pas encore préparée : nom et paquet suffisent
        }
    }

    private static String manifestAttribute(IModFile f, String key) {
        try {
            Path mf = f.findResource("META-INF", "MANIFEST.MF");
            if (mf == null || !Files.isRegularFile(mf)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(mf)) {
                return new Manifest(in).getMainAttributes().getValue(key);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Configs déjà lues (jar + config → paquet et cibles) : le contenu d'un jar chargé ne change pas. */
    private static final Map<String, String[]> CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * Lit la config Mixin dans le jar qui la déclare : son paquet, et les classes du jeu que
     * ses Mixins modifient (annotation @Mixin de chaque classe Mixin).
     */
    private static void readConfig(IModFile f, String config, MixinAcc a) {
        String key = f.getFilePath() + "|" + config;
        String[] cached = CONFIG_CACHE.computeIfAbsent(key, k -> {
            List<String> out = new ArrayList<>();
            try {
                Path p = f.findResource(config.split("/"));
                if (p == null || !Files.isRegularFile(p) || Files.size(p) > 256 * 1024) {
                    return new String[] {""};
                }
                JsonElement e = JsonParser.parseString(Files.readString(p));
                if (!e.isJsonObject()) {
                    return new String[] {""};
                }
                JsonObject json = e.getAsJsonObject();
                out.add(json.has("package") && json.get("package").isJsonPrimitive() ? json.get("package").getAsString() : "");
                for (String mixin : MixinTargets.mixinClasses(json)) {
                    Path cls = f.findResource((mixin.replace('.', '/') + ".class").split("/"));
                    if (cls == null || !Files.isRegularFile(cls) || Files.size(cls) > 1 << 20) {
                        continue;
                    }
                    try {
                        out.addAll(MixinTargets.targetsOf(Files.readAllBytes(cls)));
                    } catch (RuntimeException bad) {
                        // classe illisible : on garde les autres
                    }
                }
            } catch (Exception ex) {
                return out.isEmpty() ? new String[] {""} : out.toArray(new String[0]);
            }
            return out.isEmpty() ? new String[] {""} : out.toArray(new String[0]);
        });
        if (a.pkg.isEmpty()) {
            a.pkg = cached[0];
        }
        for (int i = 1; i < cached.length; i++) {
            a.targets.add(cached[i]);
        }
    }

    /** Mixins appliqués aux classes de l'anticheat (le client de triche le neutralise). */
    public static void mixinsOnSpartan(ScanReport r, List<String> spartanClasses) {
        for (String cls : spartanClasses) {
            try {
                if (!Mixins.getMixinsForClass(cls).isEmpty()) {
                    r.tamper.add("mixin appliqué à " + ScanReport.cut(cls, 120));
                }
            } catch (RuntimeException e) {
                // classe inconnue de Mixin : rien d'appliqué
            }
        }
    }

    /**
     * Fichier réel derrière un module. ModLauncher place chaque jar dans un système de
     * fichiers « union » : union:/C:/…/mod.jar%2312!/ → C:/…/mod.jar (pour un jar imbriqué,
     * c'est le jar qui le contient).
     */
    static Path location(ResolvedModule rm) {
        return fileOf(rm.reference().location().orElse(null));
    }

    static Path fileOf(URI uri) {
        try {
            if (uri == null) {
                return null;
            }
            Path p;
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                p = Paths.get(uri);
            } else if ("union".equalsIgnoreCase(uri.getScheme())) {
                String raw = uri.getRawSchemeSpecificPart();
                int cut = raw.indexOf("%23");
                if (cut > 0) {
                    raw = raw.substring(0, cut);
                }
                int bang = raw.indexOf("!/");
                if (bang > 0) {
                    raw = raw.substring(0, bang);
                }
                String path = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
                p = Paths.get(new URI("file", null, path.startsWith("/") ? path : "/" + path, null));
            } else {
                return null;
            }
            return Files.isRegularFile(p) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Nom du jar le plus intérieur : union:/…/outer.jar%2312!/META-INF/jarjar/inner.jar%2313!/ → inner.jar. */
    static String lastSegment(URI uri) {
        String s = uri.toString();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("!")) {
            s = s.substring(0, s.length() - 1);
        }
        int pct = s.toLowerCase(Locale.ROOT).lastIndexOf("%23");
        int slash = s.lastIndexOf('/');
        if (pct > slash) {
            s = s.substring(0, pct);
        }
        int hash = s.lastIndexOf('#');
        if (hash > s.lastIndexOf('/')) {
            s = s.substring(0, hash);
        }
        try {
            s = URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // échappement invalide : nom brut
        }
        return JvmCollector.fileName(s);
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Empreinte d'un jar, en cache ; null si trop gros, illisible ou budget épuisé. */
    static String hash(Path file, long[] budget) {
        try {
            long size = Files.size(file);
            String key = file.toAbsolutePath() + "|" + size + "|" + Files.getLastModifiedTime(file).toMillis();
            String cached = HASH_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            if (size > MAX_HASHED_FILE || size > budget[0]) {
                return null;
            }
            budget[0] -= size;
            String sha = LaunchToken.sha256File(file);
            HASH_CACHE.put(key, sha);
            return sha;
        } catch (Exception e) {
            return null;
        }
    }
}
