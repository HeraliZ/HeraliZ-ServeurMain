package fr.heraliz.noyau.serveur.diag.profil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Noms lisibles des méthodes de Minecraft dans les rapports (01/11). Dans le serveur, elles portent leur nom SRG
 * (« MinecraftServer.m_5705_ ») ; les classes ont déjà leur nom officiel. La table « nom SRG -> nom officiel de Mojang »
 * est faite à la demande, sur le fil des rapports, à partir de deux fichiers que l'installation de Forge (ou de Mohist)
 * laisse d'habitude dans {@code libraries/} :
 * <ul>
 *     <li>les correspondances de Mojang (nom officiel -> nom obfusqué) :
 *     {@code libraries/net/minecraft/server/<version>/server-<version>-mappings.txt} ;</li>
 *     <li>la table de MCPConfig (nom obfusqué -> nom SRG) :
 *     {@code libraries/de/oceanlabs/mcp/mcp_config/<version>/mcp_config-<version>-mappings.txt}, ou le zip
 *     {@code mcp_config-<version>.zip} (fichier {@code config/joined.tsrg}).</li>
 * </ul>
 * On peut aussi les déposer dans {@code diag/noms/} (n'importe quel nom : le contenu est reconnu). Sans eux, les rapports
 * gardent les noms SRG et {@code python outils/diag_noms.py <rapport>} les traduit (le workflow « Lenteurs du serveur » le
 * fait seul). Rien n'est téléchargé par le serveur. Table gardée tant que la mémoire le permet (référence douce).
 */
public final class NomsSrg {
    private NomsSrg() {
    }

    private static final Pattern SRG = Pattern.compile("\\bm_\\d+_\\b");
    private static final Map<String, String> PRIMITIFS = Map.of("void", "V", "boolean", "Z", "byte", "B", "char", "C",
            "short", "S", "int", "I", "long", "J", "float", "F", "double", "D");

    private static SoftReference<Map<String, String>> table = new SoftReference<>(null);
    private static boolean introuvable;
    private static String origine = "";

    /** Prépare la table si possible (fil des rapports) ; vrai si les noms seront traduits. */
    public static synchronized boolean preparer(Path racineServeur) {
        if (table.get() != null) return true;
        if (introuvable) return false;
        try {
            Path mojang = null, tsrg = null;
            List<Path> candidats = new ArrayList<>();
            ajouterFichiers(candidats, racineServeur.resolve("diag/noms"), 1);
            ajouterFichiers(candidats, racineServeur.resolve("libraries/net/minecraft/server"), 3);
            ajouterFichiers(candidats, racineServeur.resolve("libraries/de/oceanlabs/mcp/mcp_config"), 3);
            for (Path p : candidats) {
                String n = p.getFileName().toString();
                if (n.endsWith("-merged.txt") || n.endsWith(".sha1")) continue;
                String genre = genre(p);
                if (mojang == null && genre.equals("mojang")) mojang = p;
                if (tsrg == null && genre.equals("tsrg")) tsrg = p;
            }
            if (mojang == null || tsrg == null) {
                introuvable = true;
                origine = "correspondances introuvables dans libraries/ (Mojang " + (mojang != null ? "oui" : "non") + ", MCPConfig "
                        + (tsrg != null ? "oui" : "non") + ")";
                return false;
            }
            Map<String, String> t;
            try (BufferedReader m = lire(mojang); BufferedReader s = lire(tsrg)) {
                t = construire(m, s);
            }
            if (t.isEmpty()) {
                introuvable = true;
                origine = "correspondances illisibles";
                return false;
            }
            table = new SoftReference<>(t);
            origine = t.size() + " noms, d'après " + racineServeur.relativize(mojang) + " et " + racineServeur.relativize(tsrg);
            return true;
        } catch (IOException | RuntimeException e) {
            introuvable = true;
            origine = "correspondances illisibles (" + e + ")";
            return false;
        }
    }

    /** D'où vient la table (ou pourquoi il n'y en a pas), pour l'en-tête des rapports. */
    public static synchronized String origine() {
        return origine;
    }

    private static void ajouterFichiers(List<Path> l, Path d, int profondeur) throws IOException {
        if (!Files.isDirectory(d)) return;
        try (Stream<Path> s = Files.walk(d, profondeur)) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".txt") || n.endsWith(".tsrg") || n.endsWith(".zip");
            }).sorted().forEach(l::add);
        }
    }

    /** « mojang », « tsrg » ou « » d'après les premières lignes. */
    private static String genre(Path p) {
        try (BufferedReader r = lire(p)) {
            if (r == null) return "";
            String l;
            for (int i = 0; i < 50 && (l = r.readLine()) != null; i++) {
                if (l.isEmpty() || l.startsWith("#")) continue;
                if (l.startsWith("tsrg2 ")) return "tsrg";
                if (!l.startsWith(" ") && !l.startsWith("\t") && l.contains(" -> ") && l.endsWith(":")) return "mojang";
                if (!l.startsWith("\t") && l.split(" ").length >= 2 && !l.contains("->")) return "tsrg";
                return "";
            }
        } catch (IOException | RuntimeException e) {
            return "";
        }
        return "";
    }

    /** Lecteur du fichier, ou de {@code config/joined.tsrg} si c'est un zip de MCPConfig (null si absent). */
    private static BufferedReader lire(Path p) throws IOException {
        if (p.getFileName().toString().endsWith(".zip")) {
            ZipFile z = new ZipFile(p.toFile());
            ZipEntry e = z.getEntry("config/joined.tsrg");
            if (e == null) {
                z.close();
                return null;
            }
            return new BufferedReader(new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    super.close();
                    z.close();
                }
            };
        }
        return Files.newBufferedReader(p, StandardCharsets.UTF_8);
    }

    /**
     * Table « m_12345_ -> nomOfficiel » d'après les correspondances de Mojang (officiel -> obfusqué, avec les types) et la
     * table de MCPConfig (obfusqué -> SRG, tsrg ou tsrg2). Jointure sur (classe obfusquée, méthode obfusquée, descripteur).
     */
    public static Map<String, String> construire(BufferedReader mojang, BufferedReader tsrg) throws IOException {
        List<String> lignes = new ArrayList<>();
        Map<String, String> classes = new HashMap<>();
        String l;
        while ((l = mojang.readLine()) != null) {
            if (l.isEmpty() || l.startsWith("#")) continue;
            lignes.add(l);
            if (!l.startsWith(" ") && l.endsWith(":")) {
                int f = l.indexOf(" -> ");
                if (f > 0) classes.put(l.substring(0, f), l.substring(f + 4, l.length() - 1));
            }
        }
        // (classe obfusquée + ' ' + méthode obfusquée + ' ' + descripteur obfusqué) -> nom officiel
        Map<String, String> officiels = new HashMap<>();
        String courante = null;
        for (String x : lignes) {
            if (!x.startsWith(" ")) {
                int f = x.indexOf(" -> ");
                courante = f > 0 && x.endsWith(":") ? x.substring(f + 4, x.length() - 1).replace('.', '/') : null;
                continue;
            }
            if (courante == null) continue;
            String s = x.trim();
            int par = s.indexOf('('), fleche = s.lastIndexOf(" -> ");
            if (par < 0 || fleche < par) continue;   // champ
            String debut = s.substring(0, par);
            // « 12:34:void tick(int) -> a » ou « void tick(int):12:34 -> a » (nouvelles versions)
            int dp = debut.lastIndexOf(':');
            if (dp >= 0) debut = debut.substring(dp + 1);
            int sp = debut.indexOf(' ');
            if (sp < 0) continue;
            String retour = debut.substring(0, sp), nom = debut.substring(sp + 1);
            int fp = s.indexOf(')', par);
            if (fp < 0) continue;
            String params = s.substring(par + 1, fp);
            StringBuilder d = new StringBuilder("(");
            if (!params.isEmpty()) for (String t : params.split(",")) d.append(descripteur(t.trim(), classes));
            d.append(')').append(descripteur(retour, classes));
            String obf = s.substring(fleche + 4).trim();
            officiels.put(courante + ' ' + obf + ' ' + d, nom);
        }
        Map<String, String> r = new HashMap<>();
        String classe = null;
        while ((l = tsrg.readLine()) != null) {
            if (l.isEmpty() || l.startsWith("tsrg2") || l.startsWith("#")) continue;
            if (!l.startsWith("\t")) {
                int sp = l.indexOf(' ');
                classe = sp > 0 ? l.substring(0, sp) : l;
                continue;
            }
            if (l.startsWith("\t\t") || classe == null) continue;
            String[] p = l.substring(1).split(" ");
            if (p.length >= 3 && p[1].startsWith("(") && p[2].startsWith("m_")) {
                String off = officiels.get(classe + ' ' + p[0] + ' ' + p[1]);
                if (off != null) r.putIfAbsent(p[2], off);
            }
        }
        return r;
    }

    private static String descripteur(String t, Map<String, String> classes) {
        int dims = 0;
        while (t.endsWith("[]")) {
            dims++;
            t = t.substring(0, t.length() - 2);
        }
        String p = PRIMITIFS.get(t);
        String d = p != null ? p : "L" + classes.getOrDefault(t, t).replace('.', '/') + ";";
        return "[".repeat(dims) + d;
    }

    /** Nom officiel d'une méthode (« m_5705_ » -> « tickServer », aussi dans « lambda$m_5705_$0 ») ; sinon tel quel. */
    public static String nom(String methode) {
        Map<String, String> t = table.get();
        if (t == null || methode.indexOf("m_") < 0) return methode;
        return remplacer(methode, t);
    }

    /** Remplace tous les noms SRG connus dans un texte (rapports). */
    public static String traduire(String texte) {
        Map<String, String> t = table.get();
        if (t == null || texte.indexOf("m_") < 0) return texte;
        return remplacer(texte, t);
    }

    static String remplacer(String texte, Map<String, String> t) {
        Matcher m = SRG.matcher(texte);
        StringBuilder b = null;
        int fin = 0;
        while (m.find()) {
            String o = t.get(m.group());
            if (o == null) continue;
            if (b == null) b = new StringBuilder(texte.length() + 64);
            b.append(texte, fin, m.start()).append(o);
            fin = m.end();
        }
        if (b == null) return texte;
        return b.append(texte, fin, texte.length()).toString();
    }

    /** Pour les essais hors du jeu. */
    static synchronized void installer(Map<String, String> t) {
        table = new SoftReference<>(t);
        introuvable = false;
        origine = t.size() + " noms (essai)";
    }
}
