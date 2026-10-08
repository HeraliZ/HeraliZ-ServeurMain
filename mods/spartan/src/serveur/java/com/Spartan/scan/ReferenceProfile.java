package com.Spartan.scan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * « Client de référence » : ce qu'un jeu PROPRE, lancé avec le modpack
 * officiel, contient réellement. Appris depuis un ou plusieurs PC de
 * confiance (/spartan reference learn | add). Tout ce qu'un joueur a en plus
 * (jar inconnu, module, config Mixin, code d'origine inconnue…) devient un
 * indice.
 *
 * Fichier : config/spartan/reference.json (sérialisé tel quel par Gson).
 */
public final class ReferenceProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String learnedFrom = "";
    public long learnedAt;
    public int samples;
    /** Empreintes de tous les fichiers de code connus (quel que soit leur nom). */
    public Set<String> fileHashes = new TreeSet<>();
    /** « origine/nom » des fichiers connus (pour distinguer « inconnu » de « modifié »). */
    public Set<String> fileNames = new TreeSet<>();
    /** « COUCHE/module ». */
    public Set<String> modules = new TreeSet<>();
    /** Empreinte hexadécimale (nom + paquet) → libellé lisible. */
    public TreeMap<String, String> mixins = new TreeMap<>();
    public Set<String> origins = new TreeSet<>();
    public Set<String> loaders = new TreeSet<>();
    public Set<String> providers = new TreeSet<>();
    /** « nom:empreinte » des agents Java autorisés (authlib-injector du launcher…). */
    public Set<String> agents = new TreeSet<>();

    public boolean isEmpty() {
        return samples == 0;
    }

    /** Apprend un rapport : remplace (replace=true) ou complète la référence. */
    public void learn(ScanReport r, String from, boolean replace, long now) {
        if (replace) {
            fileHashes.clear();
            fileNames.clear();
            modules.clear();
            mixins.clear();
            origins.clear();
            loaders.clear();
            providers.clear();
            agents.clear();
            samples = 0;
            learnedFrom = "";
        }
        for (ScanReport.FileEntry f : r.files) {
            if (f.sha256().isEmpty()) {
                continue;
            }
            if (f.origin().equals("agent")) {
                agents.add(f.name().toLowerCase(Locale.ROOT) + ":" + f.sha256());
            }
            fileHashes.add(f.sha256());
            fileNames.add(fileKey(f));
        }
        for (String m : r.modules) {
            modules.add(moduleKey(m));
        }
        for (ScanReport.MixinEntry m : r.mixins) {
            mixins.put(ScanReport.hex(ScanReport.mixinFingerprint(m.name())), m.name() + " (" + m.pkg() + ")");
        }
        for (ScanReport.OriginEntry o : r.origins) {
            origins.add(o.key());
        }
        loaders.addAll(r.loaders);
        for (String lf : r.loadedFiles) {
            providers.add(provider(lf));
        }
        samples++;
        learnedAt = now;
        learnedFrom = learnedFrom.isEmpty() ? from : learnedFrom.contains(from) ? learnedFrom : learnedFrom + ", " + from;
    }

    /** Empreintes des configs Mixin connues, envoyées au client avec chaque demande de contrôle. */
    public long[] knownMixins() {
        long[] out = new long[mixins.size()];
        int i = 0;
        for (String hex : mixins.keySet()) {
            out[i++] = Long.parseUnsignedLong(hex, 16);
        }
        return out;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** @throws JsonParseException si le fichier est illisible */
    public static ReferenceProfile fromJson(String json) {
        ReferenceProfile p = GSON.fromJson(json, ReferenceProfile.class);
        if (p == null) {
            return new ReferenceProfile();
        }
        // Champs absents d'un ancien fichier : jamais null.
        p.fileHashes = orEmpty(p.fileHashes);
        p.fileNames = orEmpty(p.fileNames);
        p.modules = orEmpty(p.modules);
        p.origins = orEmpty(p.origins);
        p.loaders = orEmpty(p.loaders);
        p.providers = orEmpty(p.providers);
        p.agents = orEmpty(p.agents);
        if (p.mixins == null) {
            p.mixins = new TreeMap<>();
        }
        if (p.learnedFrom == null) {
            p.learnedFrom = "";
        }
        return p;
    }

    private static Set<String> orEmpty(Set<String> s) {
        return s == null ? new TreeSet<>() : new TreeSet<>(s);
    }

    public String summary() {
        if (isEmpty()) {
            return "aucune référence apprise";
        }
        return fileHashes.size() + " fichiers, " + modules.size() + " modules, " + mixins.size()
                + " configs Mixin, " + loaders.size() + " chargeurs, " + origins.size() + " origines de code ("
                + samples + " client(s) : " + learnedFrom + ")";
    }

    static String fileKey(ScanReport.FileEntry f) {
        return f.origin() + "/" + f.name();
    }

    /** « GAME/jei@jei-1.20.1.jar » → « GAME/jei ». */
    static String moduleKey(String entry) {
        int at = entry.indexOf('@');
        return at >= 0 ? entry.substring(0, at) : entry;
    }

    /** « mods folder|x.jar|x » → « mods folder ». */
    static String provider(String loadedFile) {
        int bar = loadedFile.indexOf('|');
        return bar >= 0 ? loadedFile.substring(0, bar) : loadedFile;
    }
}
