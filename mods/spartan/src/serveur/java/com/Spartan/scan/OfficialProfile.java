package com.Spartan.scan;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Modpack OFFICIEL des joueurs, fabriqué automatiquement à chaque mise à jour du serveur (outils/construire.py, GitHub)
 * à partir des jars réellement distribués : config/spartan/officiel.json.
 *
 * <p>Sans rien apprendre à la main, le contrôle d'intégrité sait alors :
 * <ul>
 *    <li>quels jars ont le droit d'être dans le dossier mods (par empreinte : renommer ne sert à rien) ;</li>
 *    <li>quelles configs Mixin les mods officiels déclarent (manifeste, jars imbriqués, fichiers de config) : toute autre
 *    config (mod de triche, client injecté qui passe par Mixin) est inconnue ;</li>
 *    <li>l'empreinte exacte du dossier mods officiel (comparée dès la connexion).</li>
 * </ul>
 * Le client de référence appris (/spartan reference learn) reste utile pour ce qui ne se voit qu'en jeu (modules,
 * chargeurs, origines du code) ; les deux se complètent.
 */
public final class OfficialProfile {
    private static final Gson GSON = new Gson();

    public String genere = "";
    /** Empreinte du dossier mods officiel (même calcul que LaunchToken.modsHash). */
    public String empreinteDossier = "";
    /** Nom du jar (minuscules) → sha256. */
    public Map<String, String> mods = new TreeMap<>();
    /** Configs Mixin déclarées par les jars officiels. */
    public List<String> mixins = new ArrayList<>();

    private transient Set<String> hashes;
    private transient Set<String> mixinFingerprints;

    public boolean isEmpty() {
        return mods == null || mods.isEmpty();
    }

    public boolean knowsFile(String sha256) {
        return index().hashes.contains(sha256);
    }

    /** Un jar officiel porte ce nom (pour distinguer « modifié » d'« inconnu »). */
    public boolean knowsName(String fileName) {
        return mods.containsKey(fileName.toLowerCase(Locale.ROOT));
    }

    /** Empreinte hexadécimale d'une config Mixin (ScanReport.hex(mixinFingerprint(nom))). */
    public boolean knowsMixin(String fingerprintHex) {
        return index().mixinFingerprints.contains(fingerprintHex);
    }

    public long[] mixinFingerprints() {
        return index().mixinFingerprints.stream().mapToLong(h -> Long.parseUnsignedLong(h, 16)).toArray();
    }

    private synchronized OfficialProfile index() {
        if (hashes == null) {
            Set<String> h = new HashSet<>();
            mods.values().forEach(v -> h.add(v.toLowerCase(Locale.ROOT)));
            Set<String> m = new HashSet<>();
            mixins.forEach(n -> m.add(ScanReport.hex(ScanReport.mixinFingerprint(n))));
            mixinFingerprints = m;
            hashes = h;
        }
        return this;
    }

    public String summary() {
        if (isEmpty()) {
            return "aucun modpack officiel (config/spartan/officiel.json absent)";
        }
        return mods.size() + " jars officiels, " + mixins.size() + " configs Mixin connues (fabriqué le " + genere + ")";
    }

    /** @throws JsonParseException si le fichier est illisible */
    public static OfficialProfile fromJson(String json) {
        OfficialProfile p = GSON.fromJson(json, OfficialProfile.class);
        if (p == null) {
            return new OfficialProfile();
        }
        if (p.mods == null) {
            p.mods = new TreeMap<>();
        } else {
            Map<String, String> lower = new TreeMap<>();
            p.mods.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v == null ? "" : v.toLowerCase(Locale.ROOT)));
            p.mods = lower;
        }
        if (p.mixins == null) {
            p.mixins = new ArrayList<>();
        }
        if (p.genere == null) {
            p.genere = "";
        }
        if (p.empreinteDossier == null) {
            p.empreinteDossier = "";
        }
        return p;
    }
}
