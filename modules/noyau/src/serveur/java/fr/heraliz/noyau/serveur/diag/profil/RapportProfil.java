package fr.heraliz.noyau.serveur.diag.profil;

import fr.heraliz.noyau.serveur.diag.profil.Origines.Genre;
import fr.heraliz.noyau.serveur.diag.profil.Origines.Origine;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Rapport d'un profil du serveur (01/11, « un truc qui fait comme spark : quel mod, quel fichier, quelle ligne fait
 * lag »). Tout est calculé ici, sur le fil des rapports, à partir des relevés de la pile ({@link Releves}) et des mesures
 * du serveur déjà mises en forme ({@link Entrees}) :
 * <ol>
 *     <li>résumé : ms par tick (moyenne, médiane, 95 %, 99 %, pire), TPS, part du fil serveur dans les ticks, entre les
 *     ticks et en attente ;</li>
 *     <li>temps par mod ou plugin, pendant les ticks puis entre les ticks : chaque relevé va au cadre le plus haut de la
 *     pile qui appartient à un mod ou un plugin (sinon Mohist ou Forge, sinon Minecraft), voir {@link Genre#rang()} ;</li>
 *     <li>points chauds : la ligne de ce code-là (fichier:ligne) ; puis les 10 premiers de chaque mod ;</li>
 *     <li>méthodes « feuilles » : là où le fil serveur était vraiment (temps propre), avec qui les appelait ;</li>
 *     <li>écouteurs d'événements de Forge (temps compris, avec l'événement) ;</li>
 *     <li>catégories (chemins des créatures, IA, tronçons, entités de bloc…) ;</li>
 *     <li>temps par système (mesures exactes du serveur), pires ticks, arbres des appels façon spark, état du serveur.</li>
 * </ol>
 * Aucune classe de Minecraft : vérifiable hors du jeu sur de fausses piles.
 */
public final class RapportProfil {
    /** Ce que le serveur fournit au rapport. */
    public static final class Entrees {
        public Releves releves;
        /** durée de chaque tick (ms) et son début (heure murale, ms), dans l'ordre */
        public double[] ticksMs = new double[0];
        public long[] ticksDebutMs = new long[0];
        public int nbTicks;
        public long debutMs, dureeNanos;
        public int intervalleMs;
        /** ramasse-miettes pendant le profil (ms), -1 si inconnu ; temps processeur du fil serveur (% d'un cœur), -1 */
        public long gcMs = -1;
        public double cpuFil = -1;
        public String lanceur = "";
        /** sections déjà mises en forme par le serveur (mesures exactes, commandes, état du serveur) */
        public String systemes = "", commandes = "", etat = "";
        /** état de la traduction des noms de Minecraft (ligne d'en-tête) */
        public String noms = "";
        public UnaryOperator<String> nomMethode = UnaryOperator.identity();
        public BiFunction<String, String, String> proprietaireMixin;
    }

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String BUS = "net.minecraftforge.eventbus.";
    /**
     * Catégories, de la plus précise à la plus large : un relevé prend la première qui correspond à un cadre de sa pile
     * (« classe.méthode » en minuscules, noms de Minecraft traduits quand la table est là).
     */
    private static final String[][] CATEGORIES = {
            {"explosion", "explosions"},
            {".pathfinder.", "chemins des créatures"}, {"pathnavigation", "chemins des créatures"}, {"pathfinder", "chemins des créatures"},
            {"goalselector", "IA des créatures (objectifs)"}, {".ai.goal.", "IA des créatures (objectifs)"},
            {".ai.behavior.", "IA des créatures (cerveau)"}, {".ai.brain", "IA des créatures (cerveau)"}, {".ai.sensing.", "IA des créatures (sens)"},
            {".lighting.", "lumière"},
            {"regionfile", "tronçons : disque"}, {"ioworker", "tronçons : disque"}, {"chunkserializer", "tronçons : lecture et écriture"},
            {"chunkmap$trackedentity", "suivi des entités par les joueurs"}, {"serverentity", "suivi des entités par les joueurs"},
            {"chunkmap", "tronçons : chargement, envoi aux joueurs"}, {"serverchunkcache", "tronçons : chargement, envoi aux joueurs"},
            {"chunkholder", "tronçons : chargement, envoi aux joueurs"}, {"distancemanager", "tronçons : chargement, envoi aux joueurs"},
            {"boundtickingblockentity", "entités de bloc"}, {".block.entity.", "entités de bloc"}, {"blockentity", "entités de bloc"},
            {"levelticks", "blocs : ticks programmés"}, {"neighborupdater", "blocs : mises à jour voisines"},
            {"dimensiondatastorage", "sauvegarde"}, {"playerdatastorage", "sauvegarde"}, {"levelstoragesource", "sauvegarde"},
            {"nbtio", "sauvegarde"}, {"saveall", "sauvegarde"},
            {"servergamepacketlistenerimpl", "paquets des joueurs"}, {"packetutils", "paquets des joueurs"},
            {"net.minecraft.network.", "réseau"},
            {"net.minecraft.commands.", "commandes"}, {"com.mojang.brigadier", "commandes"}, {"bukkit.command", "commandes"},
            {"serverplayer", "joueurs"},
            {"net.minecraft.world.entity.", "entités"}, {"tickentity", "entités"}, {"ticknonpassenger", "entités"},
            {"net.minecraft.world.level.", "monde (blocs, tronçons, météo)"},
            {"simplepluginmanager", "événements de Bukkit (plugins)"}, {"net.minecraftforge.eventbus", "événements de Forge (écouteurs)"},
    };

    public final String texte;
    /** une ligne pour le chat */
    public final String resume;

    private final Entrees e;
    private final Origines origines;
    private final Map<String, Long> categoriesCache = new HashMap<>();
    private final double periodeMs;
    private final double moyTick;
    private final Analyse ticks, entre;

    private RapportProfil(Entrees e) {
        this.e = e;
        this.origines = new Origines(e.proprietaireMixin);
        Releves r = e.releves;
        this.periodeMs = r.tous() == 0 ? e.intervalleMs : e.dureeNanos / 1e6 / r.tous();
        double somme = 0;
        for (int i = 0; i < e.nbTicks; i++) somme += e.ticksMs[i];
        this.moyTick = e.nbTicks == 0 ? 0 : somme / e.nbTicks;
        this.ticks = new Analyse(r.ticks, true);
        this.entre = new Analyse(r.entre, false);
        StringBuilder b = new StringBuilder(64 * 1024);
        String resumeLigne = ecrire(b);
        this.texte = b.toString();
        this.resume = resumeLigne;
    }

    public static RapportProfil ecrire(Entrees e) {
        return new RapportProfil(e);
    }

    /** Résumé en direct (toutes les 10 s) : les mods les plus lourds pendant les ticks, même règle que le rapport. */
    public static String resumeDirect(Arbre arbre, BiFunction<String, String, String> proprietaireMixin, int combien) {
        Origines or = new Origines(proprietaireMixin);
        Map<String, int[]> parMod = new HashMap<>();
        List<Arbre.Noeud> pile = new ArrayList<>();
        List<Origine> resp = new ArrayList<>();
        for (int i = 0; i < arbre.racine.nbEnfants(); i++) {
            pile.add(arbre.racine.enfant(i));
            resp.add(null);
        }
        while (!pile.isEmpty()) {
            Arbre.Noeud n = pile.remove(pile.size() - 1);
            Origine r = resp.remove(resp.size() - 1);
            Origine o = or.de(n.classe, n.module, n.methode);
            if (r == null || o.genre().rang() >= r.genre().rang()) r = o;
            if (n.propre > 0) parMod.computeIfAbsent(r.nom(), k -> new int[1])[0] += n.propre;
            for (int i = 0; i < n.nbEnfants(); i++) {
                pile.add(n.enfant(i));
                resp.add(r);
            }
        }
        List<Map.Entry<String, int[]>> l = trierInt(parMod);
        int total = Math.max(1, arbre.racine.total);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(combien, l.size()); i++) {
            b.append(i > 0 ? ", " : "").append(l.get(i).getKey()).append(' ').append(Math.round(100.0 * l.get(i).getValue()[0] / total)).append(" %");
        }
        return b.length() == 0 ? "rien relevé" : b.toString();
    }

    // ------------------------------------------------------------------ analyse d'un arbre
    /** Une ligne de tableau : libellé, relevés, et ce qui l'accompagne (appelants, mod responsable…). */
    private static final class Ligne {
        final String libelle;
        final Genre genre;
        int n, propre;
        Map<String, Integer> avec;

        Ligne(String libelle, Genre genre) {
            this.libelle = libelle;
            this.genre = genre;
        }

        void avec(String quoi, int k) {
            if (avec == null) avec = new HashMap<>();
            avec.merge(quoi, k, Integer::sum);
        }

        String principal() {
            if (avec == null) return null;
            String m = null;
            int v = -1;
            for (Map.Entry<String, Integer> x : avec.entrySet()) {
                if (x.getValue() > v) {
                    v = x.getValue();
                    m = x.getKey();
                }
            }
            return m;
        }
    }

    private final class Analyse {
        final Arbre arbre;
        final boolean dansTick;
        final int total;
        final Map<String, Ligne> parMod = new HashMap<>();
        final Map<String, Genre> genreMod = new HashMap<>();
        final EnumMap<Genre, int[]> parGenre = new EnumMap<>(Genre.class);
        final Map<String, Ligne> chauds = new HashMap<>();
        final Map<String, Map<String, Ligne>> chaudsParMod = new HashMap<>();
        final Map<String, Ligne> feuilles = new HashMap<>();
        final Map<String, Ligne> ecouteurs = new HashMap<>();
        final Map<String, int[]> categories = new LinkedHashMap<>();
        final IdentityHashMap<Arbre.Noeud, Arbre.Noeud> respDe = new IdentityHashMap<>();

        Analyse(Arbre arbre, boolean dansTick) {
            this.arbre = arbre;
            this.dansTick = dansTick;
            this.total = arbre.racine.total;
            for (int i = 0; i < arbre.racine.nbEnfants(); i++) {
                visiter(arbre.racine.enfant(i), null, -1, 0L, null, false, null, null, null);
            }
        }

        /**
         * {@code resp} : cadre responsable jusqu'ici (rang {@code rang}) ; {@code nonJava} : dernier cadre qui n'est pas de
         * Java ({@code nonJavaBus} s'il est du bus d'événements) ; {@code posteur} et {@code evt} : qui a posté l'événement
         * en cours et lequel ; {@code ecouteur} : écouteur en cours (classe#méthode).
         */
        private void visiter(Arbre.Noeud n, Arbre.Noeud resp, int rang, long masque, Arbre.Noeud nonJava, boolean nonJavaBus,
                             Arbre.Noeud posteur, String evt, String ecouteur) {
            Origine o = origines.de(n.classe, n.module, n.methode);
            boolean java = o.genre() == Genre.JAVA;
            boolean bus = n.classe.startsWith(BUS);
            int r = o.genre().rang();
            if (r >= rang) {
                resp = n;
                rang = r;
            }
            masque |= categories(n);
            if (bus) {
                if (nonJava != null && !nonJavaBus) {
                    posteur = nonJava;
                    evt = null;
                }
                String ev = evenement(n.classe);
                if (ev != null) evt = ev;
            } else if (!java && nonJava != null && nonJavaBus) {
                String cle = n.classe + '#' + n.methode;
                if (!cle.equals(ecouteur)) {
                    Ligne l = ecouteurs.computeIfAbsent(cle, k -> new Ligne(libelle(n, o, false) + etiquette(o), o.genre()));
                    l.n += n.total;
                    l.avec((evt != null ? evt : "événement sans nom (écouteur ajouté par addListener)") + " · posté par "
                            + (posteur == null ? "?" : court(posteur)), n.total);
                }
                ecouteur = cle;
            }
            if (!java) {
                nonJava = n;
                nonJavaBus = bus;
            }
            if (n.propre > 0) attribuer(n, o, resp, masque, nonJava);
            if (n.profondeur > 3000) return;
            for (int i = 0; i < n.nbEnfants(); i++) visiter(n.enfant(i), resp, rang, masque, nonJava, nonJavaBus, posteur, evt, ecouteur);
        }

        private void attribuer(Arbre.Noeud n, Origine o, Arbre.Noeud resp, long masque, Arbre.Noeud nonJava) {
            int k = n.propre;
            Origine ro = resp == n ? o : origines.de(resp.classe, resp.module, resp.methode);
            respDe.put(n, resp);
            parMod.computeIfAbsent(ro.nom(), x -> new Ligne(x, ro.genre())).n += k;
            genreMod.put(ro.nom(), ro.genre());
            parGenre.computeIfAbsent(ro.genre(), g -> new int[1])[0] += k;
            String cleResp = resp.classe + '#' + resp.methode + ':' + resp.ligne;
            Ligne c = chauds.computeIfAbsent(cleResp, x -> new Ligne(libelle(resp, ro, true) + etiquette(ro), ro.genre()));
            c.n += k;
            if (resp == n) c.propre += k;
            Ligne cm = chaudsParMod.computeIfAbsent(ro.nom(), x -> new HashMap<>()).computeIfAbsent(cleResp, x -> new Ligne(libelle(resp, ro, true), ro.genre()));
            cm.n += k;
            if (resp == n) cm.propre += k;
            Ligne f = feuilles.computeIfAbsent(n.classe + '#' + n.methode + ':' + n.ligne, x -> new Ligne(libelle(n, o, true) + etiquette(o), o.genre()));
            f.n += k;
            if (resp != n) f.avec(libelle(resp, ro, true) + etiquette(ro), k);
            else if (o.genre() == Genre.JAVA && nonJava != null && nonJava != n) f.avec(libelle(nonJava, origines.de(nonJava.classe, nonJava.module, nonJava.methode), true), k);
            int cat = Long.numberOfTrailingZeros(masque);
            String nom = masque == 0 ? "autre" : CATEGORIES[cat][1];
            if (Origines.attente(n.classe, n.methode)) nom = "attente du fil serveur (tronçon du disque, verrou, autre fil)";
            categories.computeIfAbsent(nom, x -> new int[1])[0] += k;
        }

        /** ms par tick d'un nombre de relevés de cet arbre */
        double ms(int n) {
            if (dansTick) return total == 0 ? 0 : n * moyTick / total;
            return e.nbTicks == 0 ? 0 : n * periodeMs / e.nbTicks;
        }

        double pct(int n) {
            return total == 0 ? 0 : 100.0 * n / total;
        }
    }

    private long categories(Arbre.Noeud n) {
        String cle = n.classe + '#' + n.methode;
        Long m = categoriesCache.get(cle);
        if (m != null) return m;
        String s = (n.classe + "." + e.nomMethode.apply(n.methode)).toLowerCase(Locale.ROOT);
        long r = 0;
        for (int i = 0; i < CATEGORIES.length && i < 64; i++) if (s.contains(CATEGORIES[i][0])) r |= 1L << i;
        categoriesCache.put(cle, r);
        return r;
    }

    /** « LivingTickEvent » d'après une classe de Forge « ASMEventHandler_12_Classe_methode_LivingTickEvent ». */
    private static String evenement(String classe) {
        int i = classe.indexOf("ASMEventHandler_");
        if (i < 0) return null;
        int j = classe.lastIndexOf('_');
        return j > i + 16 && j < classe.length() - 1 ? classe.substring(j + 1) : null;
    }

    // ------------------------------------------------------------------ libellés
    private String methode(Arbre.Noeud n) {
        return e.nomMethode.apply(n.methode);
    }

    private String court(Arbre.Noeud n) {
        return Origines.court(n.classe, methode(n));
    }

    /** « IaZombies.cibler (fr/heralisz/utils/zombies/IaZombies.java:212) » ; mixin : sa classe à lui. */
    private String libelle(Arbre.Noeud n, Origine o, boolean ligne) {
        String l = court(n);
        int num = ligne ? n.ligne : -1;
        if (o.mixin() != null) {
            String m = o.mixin().equals("?") ? "mixin" : "mixin " + o.mixin();
            return l + " (" + m + (num >= 0 ? ", ligne " + num : "") + ")";
        }
        if (o.genre() == Genre.JAVA && num < 0) return l;
        return l + " (" + Origines.fichier(n.classe, n.fichier, num, o.genre().rang() == 3) + ")";
    }

    private static String etiquette(Origine o) {
        return "  [" + o.nom() + "]";
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ texte
    private String ecrire(StringBuilder b) {
        Releves r = e.releves;
        double[] tri = Arrays.copyOf(e.ticksMs, e.nbTicks);
        Arrays.sort(tri);
        double p50 = centile(tri, 50), p95 = centile(tri, 95), p99 = centile(tri, 99), pire = tri.length == 0 ? 0 : tri[tri.length - 1];
        int lents = 0;
        for (double d : tri) if (d > 50) lents++;
        double secondes = e.dureeNanos / 1e9;
        double tps = secondes <= 0 ? 0 : Math.min(20.0, e.nbTicks / secondes);
        int tous = Math.max(1, r.tous());

        b.append("HeraliZ — profil du serveur (/diag profil)\n");
        b.append("Date : ").append(LocalDateTime.ofInstant(Instant.ofEpochMilli(e.debutMs), ZoneId.systemDefault()).withNano(0))
                .append(String.format(Locale.ROOT, " ; durée %.0f s ; lancé par %s%n", secondes, e.lanceur));
        b.append(String.format(Locale.ROOT, "Relevés de la pile du fil serveur : %d, toutes les %d ms demandées (%.2f ms en vrai), pendant ET entre les ticks%n",
                r.tous(), e.intervalleMs, periodeMs));
        b.append("Noms des méthodes de Minecraft : ").append(e.noms).append('\n');
        double coutMoyUs = r.coutNb == 0 ? 0 : r.coutTotalNanos / 1000.0 / r.coutNb;
        b.append(String.format(Locale.ROOT, "Coût des relevés : %.0f µs par relevé en moyenne (pire %.0f µs) ; le fil serveur est arrêté au plus ce temps-là, soit au plus %.2f %% de son temps%n",
                coutMoyUs, r.coutMaxNanos / 1000.0, e.dureeNanos <= 0 ? 0 : 100.0 * r.coutTotalNanos / e.dureeNanos));
        if (r.ticks.tronques + r.entre.tronques > 0) {
            b.append("Piles coupées faute de place (arbre plein) : ").append(r.ticks.tronques + r.entre.tronques).append('\n');
        }
        b.append('\n');

        // ---- résumé
        b.append("== Résumé ==\n");
        b.append(String.format(Locale.ROOT, "Ticks : %d ; %.1f ms en moyenne, médiane %.1f, 95 %% %.1f, 99 %% %.1f, pire %.0f ms ; %s TPS ; ticks de plus de 50 ms : %d (%.1f %%)%n",
                e.nbTicks, moyTick, p50, p95, p99, pire, f1(tps), lents, e.nbTicks == 0 ? 0 : 100.0 * lents / e.nbTicks));
        b.append("  objectifs : moyenne <= 30 ms, 95 % <= 40 ms, 99 % < 50 ms (50 ms = limite des 20 TPS)\n");
        double sommeTicks = 0;
        for (int i = 0; i < e.nbTicks; i++) sommeTicks += e.ticksMs[i];
        b.append(String.format(Locale.ROOT, "Fil serveur : %.1f %% dans les ticks (mesuré : %.1f %%), %.1f %% entre les ticks (commandes, paquets des joueurs, tâches), %.1f %% à attendre le tick suivant, %.1f %% de relevés écartés (bord d'un tick)%n",
                100.0 * r.ticks.racine.total / tous, secondes <= 0 ? 0 : sommeTicks / 10.0 / secondes,
                100.0 * r.entre.racine.total / tous, 100.0 * r.attente / tous, 100.0 * r.ecartes / tous));
        if (e.gcMs >= 0) {
            b.append(String.format(Locale.ROOT, "Ramasse-miettes (GC) pendant le profil : %d ms (%.2f ms par tick)%n", e.gcMs,
                    e.nbTicks == 0 ? 0 : (double) e.gcMs / e.nbTicks));
        }
        if (e.cpuFil >= 0) b.append(String.format(Locale.ROOT, "Temps processeur du fil serveur : %.0f %% d'un cœur%n", e.cpuFil));
        List<Map.Entry<String, Ligne>> mods = trier(ticks.parMod);
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(5, mods.size()); i++) {
            Ligne l = mods.get(i).getValue();
            top.append(i > 0 ? ", " : "").append(mods.get(i).getKey()).append(' ').append(f2(ticks.ms(l.n))).append(" ms (")
                    .append(Math.round(ticks.pct(l.n))).append(" %)");
        }
        b.append("Les plus lourds pendant les ticks (ms par tick) : ").append(top.length() == 0 ? "rien relevé" : top).append('\n');
        List<Map.Entry<String, Ligne>> chauds = trierSans(ticks.chauds);
        if (!chauds.isEmpty()) {
            Ligne l = chauds.get(0).getValue();
            b.append("Point le plus chaud (hors Minecraft et Java) : ").append(l.libelle).append(String.format(Locale.ROOT, " — %s ms par tick (%.1f %%)%n",
                    f2(ticks.ms(l.n)), ticks.pct(l.n)));
        }
        List<Map.Entry<String, Ligne>> modsEntre = trier(entre.parMod);
        if (!modsEntre.isEmpty()) {
            Ligne l = modsEntre.get(0).getValue();
            b.append("Le plus lourd entre les ticks : ").append(modsEntre.get(0).getKey()).append(String.format(Locale.ROOT, " %s ms par tick (%.0f %% du travail entre les ticks)%n",
                    f2(entre.ms(l.n)), entre.pct(l.n)));
        }
        b.append('\n');
        StringBuilder top3 = new StringBuilder();
        for (int i = 0; i < Math.min(3, mods.size()); i++) {
            top3.append(i > 0 ? ", " : "").append(mods.get(i).getKey()).append(' ').append(Math.round(ticks.pct(mods.get(i).getValue().n))).append(" %");
        }
        String resume = String.format(Locale.ROOT, "%.1f ms/tick (95 %% %.0f, pire %.0f), %s TPS ; %s", moyTick, p95, pire, f1(tps),
                top3.length() == 0 ? "rien relevé" : top3.toString());
        if (!chauds.isEmpty()) {
            Ligne l = chauds.get(0).getValue();
            resume += " ; point chaud : " + l.libelle.replaceAll("\\s+\\[.*$", "") + " " + f2(ticks.ms(l.n)) + " ms/tick";
        }

        // ---- 1. par mod
        b.append("== 1. Temps par mod ou plugin, pendant les ticks ==\n");
        b.append("Chaque relevé compte pour le code du mod ou du plugin le plus haut dans la pile (ce qu'il appelait dans Minecraft compris) ;\n");
        b.append("sans mod ni plugin : Mohist ou Forge, sinon Minecraft.\n");
        tableauMods(b, ticks);
        b.append("-- par genre --\n");
        tableauGenres(b, ticks);
        b.append('\n');
        b.append("== 1 bis. Temps par mod ou plugin, entre les ticks (commandes, paquets des joueurs, tâches) ==\n");
        b.append("ms par tick = temps de travail entre deux ticks, ramené au nombre de ticks.\n");
        tableauMods(b, entre);
        b.append('\n');

        // ---- 2. points chauds
        b.append("== 2. Points chauds pendant les ticks (ligne du code responsable, hors Minecraft et Java) ==\n");
        b.append("  ms/tick      %   dont propre   ligne\n");
        int k = 0;
        for (Map.Entry<String, Ligne> x : chauds) {
            if (k++ >= 30) break;
            Ligne l = x.getValue();
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %9.1f %%   %s%n", f2(ticks.ms(l.n)), ticks.pct(l.n), ticks.pct(l.propre), l.libelle));
        }
        if (chauds.isEmpty()) b.append("  aucun\n");
        b.append('\n');
        b.append("== 2 bis. Points chauds par mod (10 premiers de chacun) ==\n");
        for (Map.Entry<String, Ligne> m : mods) {
            Ligne lm = m.getValue();
            if (ticks.pct(lm.n) < 0.2 && lm.n < 5) continue;
            b.append(String.format(Locale.ROOT, "-- %s : %s ms par tick (%.1f %%) --%n", m.getKey(), f2(ticks.ms(lm.n)), ticks.pct(lm.n)));
            List<Map.Entry<String, Ligne>> l = trier(ticks.chaudsParMod.getOrDefault(m.getKey(), Map.of()));
            for (int i = 0; i < Math.min(10, l.size()); i++) {
                Ligne x = l.get(i).getValue();
                b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  (propre %4.1f %%)  %s%n", f2(ticks.ms(x.n)), ticks.pct(x.n), ticks.pct(x.propre), x.libelle));
            }
        }
        b.append('\n');
        b.append("== 2 ter. Points chauds entre les ticks ==\n");
        List<Map.Entry<String, Ligne>> chaudsEntre = trierSans(entre.chauds);
        for (int i = 0; i < Math.min(20, chaudsEntre.size()); i++) {
            Ligne l = chaudsEntre.get(i).getValue();
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(entre.ms(l.n)), entre.pct(l.n), l.libelle));
        }
        if (chaudsEntre.isEmpty()) b.append("  aucun\n");
        b.append('\n');

        // ---- 3. feuilles
        b.append("== 3. Méthodes les plus chères en temps propre (là où le fil serveur était), pendant les ticks ==\n");
        List<Map.Entry<String, Ligne>> feuilles = trier(ticks.feuilles);
        for (int i = 0; i < Math.min(30, feuilles.size()); i++) {
            Ligne l = feuilles.get(i).getValue();
            String par = l.principal();
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(ticks.ms(l.n)), ticks.pct(l.n), l.libelle));
            if (par != null) b.append("                    <- appelée par ").append(par).append('\n');
        }
        if (feuilles.isEmpty()) b.append("  aucune\n");
        b.append('\n');

        // ---- 4. écouteurs
        b.append("== 4. Écouteurs d'événements (Forge), temps compris, pendant les ticks ==\n");
        b.append("D'après les relevés (précision : environ ").append(f2(ticks.ms(1) * 3)).append(" ms par tick) ; « posté par » = le code qui a lancé l'événement.\n");
        List<Map.Entry<String, Ligne>> ec = trier(ticks.ecouteurs);
        for (int i = 0; i < Math.min(30, ec.size()); i++) {
            Ligne l = ec.get(i).getValue();
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(ticks.ms(l.n)), ticks.pct(l.n), l.libelle));
            String evt = l.principal();
            if (evt != null) b.append("                    événement : ").append(evt).append('\n');
        }
        if (ec.isEmpty()) b.append("  aucun relevé dans un écouteur\n");
        List<Map.Entry<String, Ligne>> ecE = trier(entre.ecouteurs);
        if (!ecE.isEmpty()) {
            b.append("-- entre les ticks --\n");
            for (int i = 0; i < Math.min(10, ecE.size()); i++) {
                Ligne l = ecE.get(i).getValue();
                String evt = l.principal();
                b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%s%n", f2(entre.ms(l.n)), entre.pct(l.n), l.libelle, evt == null ? "" : " — " + evt));
            }
        }
        b.append('\n');

        // ---- 5. catégories
        b.append("== 5. Par catégorie, pendant les ticks ==\n");
        for (Map.Entry<String, int[]> c : trierInt(ticks.categories)) {
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(ticks.ms(c.getValue()[0])), ticks.pct(c.getValue()[0]), c.getKey()));
        }
        b.append("-- entre les ticks --\n");
        for (Map.Entry<String, int[]> c : trierInt(entre.categories)) {
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(entre.ms(c.getValue()[0])), entre.pct(c.getValue()[0]), c.getKey()));
        }
        b.append('\n');

        // ---- 6. systèmes (mesures exactes)
        b.append("== 6. Temps par système (mesuré exactement par le serveur pendant le profil) ==\n");
        b.append(e.systemes.isEmpty() ? "  non mesuré\n" : e.systemes);
        b.append('\n');
        if (!e.commandes.isEmpty()) {
            b.append(e.commandes).append('\n');
        }

        // ---- 7. pires ticks
        pires(b);

        // ---- 8. arbres
        b.append("== 8. Arbre des appels pendant les ticks (comme spark ; % des relevés pendant les ticks, ms par tick) ==\n");
        b.append("Méthodes regroupées (toutes lignes confondues) ; « › » enchaîne les appels sans embranchement ; seuil 0,5 %.\n");
        arbre(b, ticks);
        b.append('\n');
        b.append("== 9. Arbre des appels entre les ticks (% du travail entre les ticks, ms par tick) ==\n");
        arbre(b, entre);
        b.append('\n');

        b.append("== État du serveur à la fin ==\n").append(e.etat).append('\n');
        return resume;
    }

    private void tableauMods(StringBuilder b, Analyse a) {
        b.append("  ms/tick      %   mod ou plugin\n");
        List<Map.Entry<String, Ligne>> l = trier(a.parMod);
        for (int i = 0; i < Math.min(40, l.size()); i++) {
            Ligne x = l.get(i).getValue();
            Genre g = a.genreMod.get(l.get(i).getKey());
            String genre = g == null || g.rang() < 3 ? "" : " (" + g.libelle + ")";
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%s%n", f2(a.ms(x.n)), a.pct(x.n), l.get(i).getKey(), genre));
        }
        if (l.isEmpty()) b.append("  rien relevé\n");
    }

    private void tableauGenres(StringBuilder b, Analyse a) {
        for (Genre g : Genre.values()) {
            int[] v = a.parGenre.get(g);
            if (v == null) continue;
            b.append(String.format(Locale.ROOT, "%9s  %5.1f %%  %s%n", f2(a.ms(v[0])), a.pct(v[0]), g.libelle));
        }
    }

    /** Ticks de plus de 50 ms (les 8 pires) et pires moments entre deux ticks, avec ce qui tournait. */
    private void pires(StringBuilder b) {
        Releves r = e.releves;
        b.append("== 7. Pires ticks (plus de 50 ms) et ce qui tournait ==\n");
        Integer[] idx = new Integer[e.nbTicks];
        for (int i = 0; i < e.nbTicks; i++) idx[i] = i;
        Arrays.sort(idx, (x, y) -> Double.compare(e.ticksMs[y], e.ticksMs[x]));
        Set<Integer> choisis = new HashSet<>();
        for (int i = 0; i < Math.min(8, idx.length); i++) if (e.ticksMs[idx[i]] > 50) choisis.add(idx[i]);
        Map<Integer, Map<String, Integer>> parTick = new HashMap<>();
        Map<Integer, Map<String, Integer>> parEcart = new HashMap<>();
        Map<Integer, int[]> travailEcart = new HashMap<>();
        for (int j = 0; j < r.journal(); j++) {
            int t = r.tick(j);
            boolean dans = r.dans(j);
            if (dans && !choisis.contains(t)) continue;
            Arbre.Noeud f = r.feuille(j);
            Analyse a = dans ? ticks : entre;
            Arbre.Noeud rn = a.respDe.getOrDefault(f, f);
            Origine o = origines.de(rn.classe, rn.module, rn.methode);
            String cle = o.nom() + " — " + libelle(rn, o, true);
            if (dans) {
                parTick.computeIfAbsent(t, x -> new HashMap<>()).merge(cle, 1, Integer::sum);
            } else {
                parEcart.computeIfAbsent(t, x -> new HashMap<>()).merge(cle, 1, Integer::sum);
                travailEcart.computeIfAbsent(t, x -> new int[1])[0]++;
            }
        }
        if (choisis.isEmpty()) b.append("  aucun tick de plus de 50 ms pendant le profil\n");
        for (int i = 0; i < Math.min(8, idx.length); i++) {
            int t = idx[i];
            if (!choisis.contains(t)) continue;
            Map<String, Integer> m = parTick.getOrDefault(t, Map.of());
            int n = 0;
            for (int v : m.values()) n += v;
            b.append(String.format(Locale.ROOT, "-- tick %d à %s : %.0f ms (%d relevés) --%n", t + 1, heure(e.ticksDebutMs[t]), e.ticksMs[t], n));
            detail(b, m, n);
        }
        b.append("-- pires moments entre deux ticks (travail estimé d'après les relevés) --\n");
        List<Map.Entry<Integer, int[]>> ecarts = new ArrayList<>(travailEcart.entrySet());
        ecarts.sort((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0]));
        int montres = 0;
        for (Map.Entry<Integer, int[]> x : ecarts) {
            double ms = x.getValue()[0] * periodeMs;
            if (montres >= 5 || ms < 20) break;
            montres++;
            int t = x.getKey();
            b.append(String.format(Locale.ROOT, "-- après le tick %d (%s) : environ %.0f ms de travail (%d relevés) --%n", t + 1,
                    t >= 0 && t < e.nbTicks ? heure(e.ticksDebutMs[t]) : "?", ms, x.getValue()[0]));
            detail(b, parEcart.getOrDefault(t, Map.of()), x.getValue()[0]);
        }
        if (montres == 0) b.append("  aucun moment de plus de 20 ms entre deux ticks\n");
        b.append('\n');
    }

    private static void detail(StringBuilder b, Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        for (int i = 0; i < Math.min(4, l.size()); i++) {
            b.append(String.format(Locale.ROOT, "  %5.1f %%  %s%n", 100.0 * l.get(i).getValue() / Math.max(1, n), l.get(i).getKey()));
        }
    }

    private static String heure(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(HEURE);
    }

    // ------------------------------------------------------------------ arbre façon spark
    private static final class NoeudM {
        final String classe, methode, fichier, module;
        int total, propre;
        final List<NoeudM> enfants = new ArrayList<>(2);
        final Map<Integer, Integer> lignes = new HashMap<>(4);

        NoeudM(String classe, String methode, String fichier, String module) {
            this.classe = classe;
            this.methode = methode;
            this.fichier = fichier;
            this.module = module;
        }

        NoeudM enfant(Arbre.Noeud n) {
            for (NoeudM m : enfants) if (m.methode.equals(n.methode) && m.classe.equals(n.classe)) return m;
            NoeudM m = new NoeudM(n.classe, n.methode, n.fichier, n.module);
            enfants.add(m);
            return m;
        }
    }

    private static void fusionner(Arbre.Noeud n, NoeudM m) {
        for (int i = 0; i < n.nbEnfants(); i++) {
            Arbre.Noeud c = n.enfant(i);
            NoeudM mc = m.enfant(c);
            mc.total += c.total;
            mc.propre += c.propre;
            mc.lignes.merge(c.ligne, c.total, Integer::sum);
            if (c.profondeur < 3000) fusionner(c, mc);
        }
    }

    private int lignesArbre;

    private void arbre(StringBuilder b, Analyse a) {
        if (a.total == 0) {
            b.append("  rien relevé\n");
            return;
        }
        NoeudM racine = new NoeudM("", "", null, null);
        fusionner(a.arbre.racine, racine);
        int seuil = Math.max(3, (int) Math.ceil(a.total * 0.005));
        lignesArbre = 0;
        b.append("        %    ms/tick\n");
        racine.enfants.sort((x, y) -> Integer.compare(y.total, x.total));
        for (NoeudM m : racine.enfants) if (m.total >= seuil) imprimer(b, a, m, 0, seuil, null);
        if (lignesArbre >= 700) b.append("  … (arbre coupé à 700 lignes)\n");
    }

    private void imprimer(StringBuilder b, Analyse a, NoeudM n, int prof, int seuil, String modParent) {
        if (lignesArbre >= 700) return;
        List<NoeudM> chaine = new ArrayList<>();
        chaine.add(n);
        NoeudM d = n;
        List<NoeudM> grands;
        while (true) {
            grands = new ArrayList<>();
            for (NoeudM c : d.enfants) if (c.total >= seuil) grands.add(c);
            if (grands.size() == 1 && d.propre < seuil && chaine.size() < 400) {
                d = grands.get(0);
                chaine.add(d);
            } else {
                break;
            }
        }
        // longue chaîne : on garde le début, chaque entrée dans le code d'un mod (et ce qu'il appelle juste après) et les
        // trois derniers appels ; le reste devient « … (n) »
        int t = chaine.size();
        Origine[] or = new Origine[t];
        boolean[] garder = new boolean[t];
        for (int i = 0; i < t; i++) {
            NoeudM x = chaine.get(i);
            or[i] = origines.de(x.classe, x.module, x.methode);
            if (t <= 7 || i == 0 || i >= t - 3) garder[i] = true;
            if (or[i].genre().rang() == 3 && (i == 0 || !or[i].nom().equals(or[i - 1].nom()))) {
                garder[i] = true;
                if (i + 1 < t) garder[i + 1] = true;
            }
        }
        StringBuilder l = new StringBuilder();
        String mod = modParent;
        int sautes = 0;
        for (int i = 0; i < t; i++) {
            if (!garder[i]) {
                sautes++;
                continue;
            }
            if (sautes > 0) {
                l.append(" › … (").append(sautes).append(')');
                sautes = 0;
            }
            NoeudM x = chaine.get(i);
            Origine o = or[i];
            if (l.length() > 0) l.append(" › ");
            l.append(Origines.court(x.classe, e.nomMethode.apply(x.methode)));
            if (o.genre() != Genre.MINECRAFT && o.genre() != Genre.JAVA && !o.nom().equals(mod)) {
                l.append(" [").append(o.nom()).append(']');
                mod = o.nom();
            }
            if ((i == t - 1 && o.genre() != Genre.JAVA) || o.genre().rang() == 3) l.append(" (").append(lignes(x, o)).append(')');
        }
        if (d.propre >= seuil && !grands.isEmpty()) l.append("  · propre ").append(f1(a.pct(d.propre))).append(" %");
        String retrait = prof <= 30 ? "  ".repeat(prof) : "  ".repeat(30) + "[" + prof + "] ";
        b.append(String.format(Locale.ROOT, "%8.1f %% %9s  ", a.pct(n.total), f2(a.ms(n.total)))).append(retrait).append(l).append('\n');
        lignesArbre++;
        grands.sort((x, y) -> Integer.compare(y.total, x.total));
        for (NoeudM g : grands) imprimer(b, a, g, prof + 1, seuil, mod);
    }

    /** « fr/…/IaZombies.java:212, 230 » : les deux lignes les plus chargées de la méthode. */
    private static String lignes(NoeudM x, Origine o) {
        List<Map.Entry<Integer, Integer>> l = new ArrayList<>(x.lignes.entrySet());
        l.sort((p, q) -> Integer.compare(q.getValue(), p.getValue()));
        if (o.mixin() != null) {
            String m = o.mixin().equals("?") ? "mixin" : "mixin " + o.mixin().substring(o.mixin().lastIndexOf('.') + 1);
            return l.isEmpty() ? m : m + ", ligne " + l.get(0).getKey();
        }
        StringBuilder s = new StringBuilder(Origines.fichier(x.classe, x.fichier, l.isEmpty() ? -1 : l.get(0).getKey(), o.genre().rang() == 3));
        if (l.size() > 1 && l.get(1).getValue() * 5 >= l.get(0).getValue()) s.append(", ").append(l.get(1).getKey());
        return s.toString();
    }

    // ------------------------------------------------------------------ tris
    private static List<Map.Entry<String, Ligne>> trier(Map<String, Ligne> m) {
        List<Map.Entry<String, Ligne>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Integer.compare(y.getValue().n, x.getValue().n));
        return l;
    }

    /** Points chauds sans les lignes de Minecraft ni de Java (déjà vues par mod) */
    private static List<Map.Entry<String, Ligne>> trierSans(Map<String, Ligne> m) {
        List<Map.Entry<String, Ligne>> l = new ArrayList<>();
        for (Map.Entry<String, Ligne> x : m.entrySet()) {
            Genre g = x.getValue().genre;
            if (g == Genre.MINECRAFT || g == Genre.JAVA) continue;
            l.add(x);
        }
        l.sort((x, y) -> Integer.compare(y.getValue().n, x.getValue().n));
        return l;
    }

    private static List<Map.Entry<String, int[]>> trierInt(Map<String, int[]> m) {
        List<Map.Entry<String, int[]>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0]));
        return l;
    }

    private static double centile(double[] tri, double p) {
        if (tri.length == 0) return 0;
        return tri[Math.max(0, Math.min(tri.length - 1, (int) Math.ceil(p / 100.0 * tri.length) - 1))];
    }
}
