package com.Spartan.scan;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.HashSet;
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
    /** Configs Mixin déclarées par les jars officiels → paquet de leurs classes Mixin. */
    public Map<String, String> mixins = new TreeMap<>();

    private transient Set<String> hashes;
    private transient Set<String> mixinFingerprints;
    private transient Map<String, String> packages;
    /**
     * Modpack officiel d'avant la dernière mise à jour du fichier, encore accepté (27/10) : la mise en ligne change le
     * pack des joueurs et ce fichier sans redémarrer le serveur ; un joueur lancé juste avant (ou juste après) ne doit
     * pas être pris pour un tricheur. Un seul niveau (jamais de chaîne).
     */
    public transient OfficialProfile precedent;

    public boolean isEmpty() {
        return mods == null || mods.isEmpty();
    }

    public boolean knowsFile(String sha256) {
        return index().hashes.contains(sha256) || (precedent != null && precedent.index().hashes.contains(sha256));
    }

    /** Un jar officiel porte ce nom (pour distinguer « modifié » d'« inconnu »). */
    public boolean knowsName(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return mods.containsKey(n) || (precedent != null && precedent.mods.containsKey(n));
    }

    /** Empreinte hexadécimale d'une config Mixin (ScanReport.hex(mixinFingerprint(nom))). */
    public boolean knowsMixin(String fingerprintHex) {
        return index().mixinFingerprints.contains(fingerprintHex)
                || (precedent != null && precedent.index().mixinFingerprints.contains(fingerprintHex));
    }

    /** Empreinte de dossier mods officielle (celle-ci ou la précédente). */
    public boolean knowsFolder(String sha256) {
        return (!empreinteDossier.isEmpty() && empreinteDossier.equalsIgnoreCase(sha256))
                || (precedent != null && !precedent.empreinteDossier.isEmpty() && precedent.empreinteDossier.equalsIgnoreCase(sha256));
    }

    /**
     * Paquet officiel de la config Mixin de cette empreinte ("" si inconnu). Une config qui porte le nom d'une config
     * officielle avec un AUTRE paquet est une triche déguisée.
     */
    public String mixinPackage(String fingerprintHex) {
        String p = index().packages.getOrDefault(fingerprintHex, "");
        return p.isEmpty() && precedent != null ? precedent.index().packages.getOrDefault(fingerprintHex, "") : p;
    }

    public long[] mixinFingerprints() {
        Set<String> tout = new HashSet<>(index().mixinFingerprints);
        if (precedent != null) {
            tout.addAll(precedent.index().mixinFingerprints);
        }
        return tout.stream().mapToLong(h -> Long.parseUnsignedLong(h, 16)).toArray();
    }

    private synchronized OfficialProfile index() {
        if (hashes == null) {
            Set<String> h = new HashSet<>();
            mods.values().forEach(v -> h.add(v.toLowerCase(Locale.ROOT)));
            Set<String> m = new HashSet<>();
            Map<String, String> pk = new TreeMap<>();
            mixins.forEach((n, pkg) -> {
                String fp = ScanReport.hex(ScanReport.mixinFingerprint(n));
                m.add(fp);
                pk.put(fp, pkg == null ? "" : pkg);
            });
            packages = pk;
            mixinFingerprints = m;
            hashes = h;
        }
        return this;
    }

    public String summary() {
        if (isEmpty()) {
            return "aucun modpack officiel (config/spartan/officiel.json absent)";
        }
        return mods.size() + " jars officiels, " + mixins.size() + " configs Mixin connues (fabriqué le " + genere + ")"
                + (precedent != null ? " ; le précédent (" + precedent.genere + ") reste accepté" : "");
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
            p.mixins = new TreeMap<>();
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
