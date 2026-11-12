package fr.heraliz.noyau.diag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Relevés de la pile d'un fil (serveur, ou rendu du jeu chez un joueur), regroupés : par mod responsable, par ligne de code, par catégorie (entités, tronçons,
 * chemins...) et par pile complète. Un relevé est attribué au PREMIER cadre de la pile (en partant du haut) qui
 * n'appartient ni à Java, ni à Minecraft, ni à Forge, ni au serveur Bukkit : c'est le code d'un mod ou d'un plugin qui
 * travaillait à cet instant. Sans tel cadre : « minecraft ». Utilisé par un seul fil à la fois (verrou de l'appelant).
 */
public final class Profil {
    private static final Set<String> MODULES_MOTEUR = Set.of("java.base", "java.desktop", "java.management", "java.logging",
            "minecraft", "forge", "fmlcore", "fmlloader", "javafmllanguage", "lowcodelanguage", "mclanguage", "eventbus",
            "modlauncher", "securejarhandler", "coremods", "bootstraplauncher", "jdk.internal.vm.ci");
    private static final String[] PAQUETS_MOTEUR = {"java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.",
            "net.minecraftforge.", "com.mojang.", "org.bukkit.", "org.spigotmc.", "com.mohistmc.", "io.netty.", "it.unimi.",
            "com.google.", "org.apache.", "cpw.mods.", "org.spongepowered.", "org.slf4j.", "org.objectweb.", "com.mojang"};
    /** Plugins Bukkit connus : début du paquet -> nom affiché. */
    private static final String[][] PLUGINS = {{"fr.xephi.authme", "plugin AuthMe"}, {"me.lucko.luckperms", "plugin LuckPerms"},
            {"litebans", "plugin LiteBans"}, {"com.sk89q.worldguard", "plugin WorldGuard"}, {"com.sk89q.worldedit", "plugin WorldEdit"},
            {"me.lucko.spark", "plugin spark"}, {"net.kauri", "plugin KauriVPN"}};
    /** Catégories, dans l'ordre où on les cherche dans la pile (la plus précise d'abord). */
    private static final String[][] CATEGORIES = {
            {"pathfinder", "chemins des créatures"}, {"pathnavigation", "chemins des créatures"},
            {"blockentity", "entités de bloc"}, {"tickblockentities", "entités de bloc"},
            {"goalselector", "IA des créatures"}, {"tickNonPassenger", "entités"}, {"entity.tick", "entités"},
            {"chunkmap", "tronçons (chargement, génération)"}, {"serverchunkcache", "tronçons (chargement, génération)"},
            {"saveall", "sauvegarde"}, {"regionfile", "sauvegarde"},
            {"servergamepacketlistenerimpl", "paquets des joueurs"}, {"packetutils", "paquets des joueurs"},
            {"commands", "commandes"},
            {"blockentityrenderdispatcher", "rendu des entités de bloc (props, conteneurs)"},
            {"entityrenderdispatcher", "rendu des entités"}, {"levelrenderer.compilechunks", "construction des tronçons"},
            {"sectionrenderdispatcher", "construction des tronçons"}, {"gamerenderer.rendergui", "interface (HUD, écrans)"},
            {"gui.render", "interface (HUD, écrans)"}, {"particleengine", "particules"}};

    public int releves;
    public final Map<String, Integer> mods = new HashMap<>();
    public final Map<String, Integer> lignes = new HashMap<>();
    public final Map<String, Integer> categories = new HashMap<>();
    public final Map<String, Integer> piles = new HashMap<>();
    /**
     * Qui a fait attendre un tronçon au fil du serveur (11/11) : les cadres qui ont demandé un tronçon pas encore chargé
     * ({@code ServerChunkCache.getChunk} bloquant), que les 14 cadres du haut ne montraient jamais (ils sont tous dans
     * la lecture du tronçon). Vide sans attente de ce genre.
     */
    public final Map<String, Integer> demandeurs = new HashMap<>();
    private final Map<String, String> modParCadre = new HashMap<>();

    public void ajouter(StackTraceElement[] pile) {
        if (pile.length == 0) return;
        releves++;
        int i = responsable(pile);
        String mod = i < 0 ? "minecraft" : mod(pile[i]);
        StackTraceElement ligne = pile[Math.max(0, i)];
        mods.merge(mod, 1, Integer::sum);
        lignes.merge(mod + " · " + cadre(ligne), 1, Integer::sum);
        categories.merge(categorie(pile), 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < Math.min(14, pile.length); k++) b.append("    ").append(cadre(pile[k])).append('\n');
        piles.merge(b.toString(), 1, Integer::sum);
        int d = demandeur(pile);
        if (d >= 0) {
            b.setLength(0);
            for (int k = d; k < Math.min(d + DEMANDEUR_CADRES, pile.length); k++) b.append("    ").append(cadre(pile[k])).append('\n');
            demandeurs.merge(b.toString(), 1, Integer::sum);
        }
    }

    private static final int DEMANDEUR_CADRES = 10;
    private static final String CACHE_TRONCONS = "net.minecraft.server.level.ServerChunkCache";
    private static final String BOUCLE = "net.minecraft.util.thread.BlockableEventLoop";

    /**
     * Indice du premier cadre qui a demandé un tronçon en bloquant le fil, ou -1. L'attente se reconnaît à la boucle
     * d'attente de Minecraft ({@code BlockableEventLoop.managedBlock}) appelée directement par
     * {@code ServerChunkCache} (son getChunk ; l'attente normale entre deux ticks est appelée par MinecraftServer, et les
     * tâches de fond par une classe interne). Les noms de méthodes ne sont pas lus (ils changent entre l'atelier et le
     * serveur), seulement les classes. On prend l'attente la plus extérieure (la demande d'origine), puis on saute les
     * cadres du cache de tronçons.
     */
    private static int demandeur(StackTraceElement[] pile) {
        int attente = -1;
        for (int k = 0; k + 1 < pile.length; k++) {
            if (BOUCLE.equals(pile[k].getClassName()) && CACHE_TRONCONS.equals(pile[k + 1].getClassName())) attente = k + 1;
        }
        if (attente < 0) return -1;
        int k = attente;
        while (k < pile.length && CACHE_TRONCONS.equals(pile[k].getClassName())) k++;
        return k < pile.length ? k : -1;
    }

    private int responsable(StackTraceElement[] pile) {
        for (int i = 0; i < pile.length; i++) if (!moteur(pile[i])) return i;
        return -1;
    }

    private static boolean moteur(StackTraceElement e) {
        String m = e.getModuleName();
        if (m != null && (MODULES_MOTEUR.contains(m) || m.startsWith("java.") || m.startsWith("jdk."))) return true;
        String c = e.getClassName();
        for (String p : PAQUETS_MOTEUR) if (c.startsWith(p)) return true;
        return false;
    }

    private String mod(StackTraceElement e) {
        String m = e.getModuleName();
        if (m != null && !m.isEmpty()) return m;
        String c = e.getClassName();
        String connu = modParCadre.get(c);
        if (connu != null) return connu;
        String r = null;
        for (String[] p : PLUGINS) if (c.startsWith(p[0])) r = p[1];
        if (r == null) {
            String[] parts = c.split("\\.");
            r = parts.length >= 3 ? parts[0] + "." + parts[1] + "." + parts[2] : c;
        }
        modParCadre.put(c, r);
        return r;
    }

    public static String cadre(StackTraceElement e) {
        String c = e.getClassName();
        int p = c.lastIndexOf('.');
        return (p >= 0 ? c.substring(p + 1) : c) + "." + e.getMethodName() + ":" + e.getLineNumber() + "  (" + c + ")";
    }

    private static String categorie(StackTraceElement[] pile) {
        for (String[] cat : CATEGORIES) {
            String motif = cat[0].toLowerCase(Locale.ROOT);
            for (StackTraceElement e : pile) {
                String s = (e.getClassName() + "." + e.getMethodName()).toLowerCase(Locale.ROOT);
                if (s.contains(motif)) return cat[1];
            }
        }
        return "autre";
    }

    /** Les {@code n} premières entrées, triées, en « 42 % nom ». */
    public static List<String> top(Map<String, Integer> m, int total, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> r = new ArrayList<>();
        for (int i = 0; i < Math.min(n, l.size()); i++) {
            r.add(String.format(Locale.ROOT, "%5.1f %%  %s", 100.0 * l.get(i).getValue() / Math.max(1, total), l.get(i).getKey()));
        }
        return r;
    }

    /** Résumé d'une ligne : les 3 mods qui pèsent le plus et la ligne la plus chargée. */
    public String resume() {
        if (releves == 0) return "rien relevé";
        List<Map.Entry<String, Integer>> l = new ArrayList<>(mods.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            if (i > 0) b.append(", ");
            b.append(l.get(i).getKey()).append(' ').append(Math.round(100.0 * l.get(i).getValue() / releves)).append(" %");
        }
        String ligne = top(lignes, releves, 1).stream().findFirst().orElse("").trim();
        return b + (ligne.isEmpty() ? "" : " — " + ligne);
    }

    public void ecrire(StringBuilder b) {
        b.append("Relevés de la pile du serveur : ").append(releves).append("\n\n");
        b.append("== Par mod ou plugin responsable ==\n");
        for (String s : top(mods, releves, 15)) b.append(s).append('\n');
        b.append("\n== Par catégorie ==\n");
        for (String s : top(categories, releves, 10)) b.append(s).append('\n');
        b.append("\n== Lignes de code les plus chargées ==\n");
        for (String s : top(lignes, releves, 25)) b.append(s).append('\n');
        b.append("\n== Piles les plus fréquentes (14 cadres du haut) ==\n");
        List<Map.Entry<String, Integer>> l = new ArrayList<>(piles.entrySet());
        l.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(8, l.size()); i++) {
            b.append(String.format(Locale.ROOT, "-- %.1f %% des relevés --%n", 100.0 * l.get(i).getValue() / Math.max(1, releves)));
            b.append(l.get(i).getKey()).append('\n');
        }
        if (!demandeurs.isEmpty()) {
            b.append("\n== Tronçons attendus par le fil du serveur : qui les a demandés (10 cadres sous le chargement) ==\n");
            List<Map.Entry<String, Integer>> d = new ArrayList<>(demandeurs.entrySet());
            d.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
            for (int i = 0; i < Math.min(6, d.size()); i++) {
                b.append(String.format(Locale.ROOT, "-- %.1f %% des relevés --%n", 100.0 * d.get(i).getValue() / Math.max(1, releves)));
                b.append(d.get(i).getKey()).append('\n');
            }
        }
    }
}
