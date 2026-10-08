package com.Spartan.scan;

import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Rapport d'un contrôle d'intégrité du client : ce qui est réellement chargé
 * dans le jeu du joueur (fichiers, modules, configs Mixin, agents, threads,
 * DLL…). Construit côté client, analysé côté serveur.
 *
 * Le décodage est borné partout (nombre d'éléments, longueur des chaînes) :
 * c'est le client qui l'envoie, il peut contenir n'importe quoi.
 */
public final class ScanReport {
    public static final int FORMAT = 3;

    public static final int NAME = 128;
    public static final int TEXT = 256;
    public static final int MAX_FILES = 2048;
    public static final int MAX_MODULES = 4096;
    public static final int MAX_LOADED = 2048;
    public static final int MAX_MIXINS = 2048;
    public static final int MAX_TARGETS = 24;
    public static final int MAX_FLAGS = 128;
    public static final int MAX_THREADS = 1024;
    public static final int MAX_ORIGINS = 256;
    public static final int MAX_SAMPLES = 6;
    public static final int MAX_LOADERS = 128;
    public static final int MAX_TAMPER = 128;
    public static final int MAX_NATIVES = 1024;
    public static final int MAX_HITS = 256;
    public static final int MAX_ERRORS = 32;
    public static final int MAX_INJECTION = 32;

    private static final Pattern SHA256 = Pattern.compile("^([0-9a-f]{64})?$");

    /**
     * Fichier de code et son empreinte.
     * origin : "mods" (dossier mods), "layer:BOOT"… (jar d'un module hors du dossier mods),
     * "agent" (agent Java du lancement).
     */
    public record FileEntry(String origin, String name, long size, String sha256) {
    }

    /**
     * Config Mixin : nom, paquet, jar qui la déclare, nombre de classes visées connues et un
     * extrait de ces classes (classes sensibles en priorité).
     */
    public record MixinEntry(String name, String pkg, String source, int targetCount, List<String> targets) {
    }

    /** Code vu dans les piles d'exécution qui n'appartient à aucun module chargé normalement. */
    public record OriginEntry(String key, List<String> classes, List<String> threads) {
    }

    /** Bibliothèque native chargée dans le jeu (DLL / .so), emplacement anonymisé. */
    public record NativeEntry(String name, String place, String reason) {
    }

    public int scanId;
    public long durationMs;
    public String java = "";
    public String os = "";
    public final List<FileEntry> files = new ArrayList<>();
    /** "COUCHE/module@fichier". */
    public final List<String> modules = new ArrayList<>();
    /** "fournisseur|fichier|mod1,mod2". */
    public final List<String> loadedFiles = new ArrayList<>();
    public final List<MixinEntry> mixins = new ArrayList<>();
    public final List<String> jvmFlags = new ArrayList<>();
    public boolean attachDisabled;
    public boolean attachListener;
    public int threadCount;
    public final List<String> threadNames = new ArrayList<>();
    public int unnamedThreads;
    public final List<OriginEntry> origins = new ArrayList<>();
    public final List<String> loaders = new ArrayList<>();
    public final List<String> tamper = new ArrayList<>();
    public final List<NativeEntry> natives = new ArrayList<>();
    public final List<String> processHits = new ArrayList<>();
    public final List<String> classHits = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    /** Traces de code natif injecté : « thread|0x… » (thread démarré hors de tout module), « hook|fonction|0x…|module ». */
    public final List<String> injection = new ArrayList<>();

    /**
     * Empreinte stable d'une config Mixin, identique client et serveur. Seul le nom compte : le
     * paquet n'est pas toujours connu (selon la façon dont la config a été trouvée).
     */
    public static long mixinFingerprint(String name) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (d[i] & 0xFF);
            }
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hex(long fingerprint) {
        return String.format("%016x", fingerprint);
    }

    // ------------------------------------------------------------------ encodage

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(FORMAT);
        b.writeInt(scanId);
        b.writeVarLong(Math.max(0, durationMs));
        str(b, java, TEXT);
        str(b, os, TEXT);
        list(b, files, MAX_FILES, (o, f) -> {
            str(o, f.origin, NAME);
            str(o, f.name, TEXT);
            o.writeVarLong(Math.max(0, f.size));
            str(o, f.sha256 == null ? "" : f.sha256, 64);
        });
        strings(b, modules, MAX_MODULES, TEXT);
        strings(b, loadedFiles, MAX_LOADED, TEXT);
        list(b, mixins, MAX_MIXINS, (o, m) -> {
            str(o, m.name, TEXT);
            str(o, m.pkg, TEXT);
            str(o, m.source, TEXT);
            o.writeVarInt(Math.max(0, m.targetCount));
            strings(o, m.targets, MAX_TARGETS, TEXT);
        });
        strings(b, jvmFlags, MAX_FLAGS, TEXT);
        b.writeBoolean(attachDisabled);
        b.writeBoolean(attachListener);
        b.writeVarInt(Math.max(0, threadCount));
        strings(b, threadNames, MAX_THREADS, NAME);
        b.writeVarInt(Math.max(0, unnamedThreads));
        list(b, origins, MAX_ORIGINS, (o, e) -> {
            str(o, e.key, TEXT);
            strings(o, e.classes, MAX_SAMPLES, TEXT);
            strings(o, e.threads, MAX_SAMPLES, NAME);
        });
        strings(b, loaders, MAX_LOADERS, TEXT);
        strings(b, tamper, MAX_TAMPER, TEXT);
        list(b, natives, MAX_NATIVES, (o, n) -> {
            str(o, n.name, NAME);
            str(o, n.place, TEXT);
            str(o, n.reason, NAME);
        });
        strings(b, processHits, MAX_HITS, NAME);
        strings(b, classHits, MAX_HITS, TEXT);
        strings(b, errors, MAX_ERRORS, TEXT);
        strings(b, injection, MAX_INJECTION, TEXT);
    }

    public static ScanReport read(FriendlyByteBuf b) {
        int format = b.readVarInt();
        if (format != FORMAT) {
            throw new DecoderException("Spartan: format de rapport inconnu (" + format + ")");
        }
        ScanReport r = new ScanReport();
        r.scanId = b.readInt();
        r.durationMs = b.readVarLong();
        r.java = b.readUtf(TEXT);
        r.os = b.readUtf(TEXT);
        readList(b, MAX_FILES, r.files, i -> {
            String origin = i.readUtf(NAME);
            String name = i.readUtf(TEXT);
            long size = i.readVarLong();
            String sha = i.readUtf(64);
            if (!SHA256.matcher(sha).matches()) {
                throw new DecoderException("Spartan: empreinte invalide");
            }
            return new FileEntry(origin, name, size, sha);
        });
        readStrings(b, MAX_MODULES, TEXT, r.modules);
        readStrings(b, MAX_LOADED, TEXT, r.loadedFiles);
        readList(b, MAX_MIXINS, r.mixins, i -> {
            String name = i.readUtf(TEXT);
            String pkg = i.readUtf(TEXT);
            String source = i.readUtf(TEXT);
            int count = i.readVarInt();
            List<String> targets = new ArrayList<>();
            readStrings(i, MAX_TARGETS, TEXT, targets);
            return new MixinEntry(name, pkg, source, count, targets);
        });
        readStrings(b, MAX_FLAGS, TEXT, r.jvmFlags);
        r.attachDisabled = b.readBoolean();
        r.attachListener = b.readBoolean();
        r.threadCount = b.readVarInt();
        readStrings(b, MAX_THREADS, NAME, r.threadNames);
        r.unnamedThreads = b.readVarInt();
        readList(b, MAX_ORIGINS, r.origins, i -> {
            String key = i.readUtf(TEXT);
            List<String> classes = new ArrayList<>();
            readStrings(i, MAX_SAMPLES, TEXT, classes);
            List<String> threads = new ArrayList<>();
            readStrings(i, MAX_SAMPLES, NAME, threads);
            return new OriginEntry(key, classes, threads);
        });
        readStrings(b, MAX_LOADERS, TEXT, r.loaders);
        readStrings(b, MAX_TAMPER, TEXT, r.tamper);
        readList(b, MAX_NATIVES, r.natives, i -> new NativeEntry(i.readUtf(NAME), i.readUtf(TEXT), i.readUtf(NAME)));
        readStrings(b, MAX_HITS, NAME, r.processHits);
        readStrings(b, MAX_HITS, TEXT, r.classHits);
        readStrings(b, MAX_ERRORS, TEXT, r.errors);
        readStrings(b, MAX_INJECTION, TEXT, r.injection);
        if (r.threadCount < 0 || r.unnamedThreads < 0 || r.durationMs < 0) {
            throw new DecoderException("Spartan: compteur négatif");
        }
        return r;
    }

    // ------------------------------------------------------------------ outils

    /** Coupe une chaîne pour qu'elle tienne dans le format (le client honnête ne produit jamais d'invalide). */
    public static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void str(FriendlyByteBuf b, String s, int max) {
        b.writeUtf(cut(s, max), max);
    }

    private static void strings(FriendlyByteBuf b, List<String> list, int maxItems, int maxLen) {
        list(b, list, maxItems, (o, s) -> str(o, s, maxLen));
    }

    private static <T> void list(FriendlyByteBuf b, List<T> list, int maxItems, BiConsumer<FriendlyByteBuf, T> writer) {
        int n = Math.min(list == null ? 0 : list.size(), maxItems);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            writer.accept(b, list.get(i));
        }
    }

    private static int count(FriendlyByteBuf b, int max) {
        int n = b.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("Spartan: liste hors limites (" + n + ")");
        }
        return n;
    }

    private static void readStrings(FriendlyByteBuf b, int maxItems, int maxLen, List<String> out) {
        int n = count(b, maxItems);
        for (int i = 0; i < n; i++) {
            out.add(b.readUtf(maxLen));
        }
    }

    private static <T> void readList(FriendlyByteBuf b, int maxItems, List<T> out, Function<FriendlyByteBuf, T> reader) {
        int n = count(b, maxItems);
        for (int i = 0; i < n; i++) {
            out.add(reader.apply(b));
        }
    }
}
