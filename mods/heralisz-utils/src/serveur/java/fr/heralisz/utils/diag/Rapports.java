package fr.heralisz.utils.diag;

import fr.heralisz.utils.HeraliszUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Fil d'écriture du diagnostic (démon, priorité basse) : seul fil qui touche au disque. Reçoit les épisodes du chien de
 * garde et les profils par une file, met en forme les rapports et les écrit dans {@code heraliz_diag/} à la racine du
 * serveur (jamais dans le monde), un fichier texte par rapport, {@code rapports_max} gardés (les plus anciens effacés,
 * les blocages en dernier). Les ticks longs sous le seuil de blocage sont réunis dans un même rapport pendant
 * {@code ecart_rapports_secondes} (un serveur qui rame à chaque tick n'écrit pas un fichier par tick). Écrit aussi la
 * ligne de résumé « [diag] » du journal toutes les {@code resume_minutes}. Aucune adresse IP dans les rapports (masquées).
 */
final class Rapports implements Runnable {
    private record Tache(String genre, Supplier<String> texte, String nom) {
    }

    private static final LinkedBlockingQueue<Object> FILE = new LinkedBlockingQueue<>(10_000);
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static volatile Rapports courant;
    private static final List<String> DERNIERS = new ArrayList<>();
    static volatile int ecrits;

    private final Path dossier;
    private final Thread fil;
    private volatile boolean arreter;

    // regroupement des ticks longs (fil d'écriture seulement)
    private Agregat lot;
    private final List<String> lotLignes = new ArrayList<>();
    private int lotEpisodes, lotSansEchantillon;
    private double lotSommeMs, lotEchantillonneMs;
    private Episode lotPire;
    private long lotDebutMs, lotFinMs, dernierLotMs;
    // résumé
    private long dernierResumeMs = System.currentTimeMillis();
    private int resumeEpisodes, resumeBlocages, resumeRapports;
    private long gcAvant = PausesGc.tempsTotalMs();

    private Rapports(Path dossier) {
        this.dossier = dossier;
        fil = new Thread(this, "HeraliZ-diag-ecriture");
        fil.setDaemon(true);
        fil.setPriority(Thread.MIN_PRIORITY);
    }

    static synchronized void demarrer(Path dossier) {
        if (courant != null && courant.fil.isAlive()) return;
        courant = new Rapports(dossier);
        courant.fil.start();
    }

    /** Arrêt du serveur : écrit ce qui reste (au plus {@code attenteMs}). */
    static void arreter(long attenteMs) {
        Rapports r;
        synchronized (Rapports.class) {
            r = courant;
            courant = null;
        }
        if (r == null) return;
        r.arreter = true;
        try {
            r.fil.join(attenteMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ entrées (n'importe quel fil)

    static void episode(Episode ep) {
        if (!FILE.offer(ep)) HeraliszUtils.LOG.warn("[diag] file des rapports pleine : épisode oublié");
    }

    /** Rapport déjà mis en forme (provisoire d'un blocage). */
    static void texte(String genre, String texte) {
        FILE.offer(new Tache(genre, () -> texte, null));
    }

    /** Rapport mis en forme plus tard par le fil d'écriture ; rend le nom prévu du fichier. */
    static String plusTard(String genre, Supplier<String> texte) {
        String nom = nomPrevu(genre);
        FILE.offer(new Tache(genre, texte, nom));
        return nom;
    }

    static synchronized List<String> derniers() {
        return List.copyOf(DERNIERS);
    }

    static String heure(long ms) {
        return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(ms));
    }

    static String date(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(ms));
    }

    private static String nomPrevu(String genre) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + "_" + genre + ".txt";
    }

    // ------------------------------------------------------------------ fil d'écriture

    @Override
    public void run() {
        while (true) {
            try {
                if (arreter && FILE.isEmpty()) break;
                Object o = FILE.poll(1, TimeUnit.SECONDS);
                if (o instanceof Episode ep) traiter(ep);
                else if (o instanceof Tache t) ecrire(t.genre, t.texte.get(), t.nom);
                long now = System.currentTimeMillis();
                if (lot != null && now - dernierLotMs >= DiagConfig.ecartRapportsS * 1000L) viderLot();
                int resume = DiagConfig.resumeMinutes;
                if (resume > 0 && DiagConfig.actif && now - dernierResumeMs >= resume * 60_000L) resume(now);
            } catch (InterruptedException e) {
                if (arreter) break;
            } catch (Throwable t) {
                HeraliszUtils.LOG.warn("[diag] erreur du fil d'écriture (il continue) : {}", t.toString());
            }
        }
        try {
            if (lot != null) viderLot();
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[diag] derniers ticks longs non écrits : {}", t.toString());
        }
    }

    private void traiter(Episode ep) throws InterruptedException {
        // laisse arriver l'avis du ramasse-miettes de la fin de l'épisode
        long attente = Math.min(1000, ep.finMs + 300 - System.currentTimeMillis());
        if (attente > 0 && !arreter) Thread.sleep(attente);
        resumeEpisodes++;
        if (ep.blocage()) {
            resumeBlocages++;
            ecrire("blocage", ep.texte(false), null);
            return;
        }
        if (lot == null) {
            lot = new Agregat();
            lotDebutMs = ep.debutMs;
        }
        lot.fusionner(ep.agregat);
        lotEpisodes++;
        if (ep.agregat.echantillons == 0) lotSansEchantillon++;
        lotSommeMs += ep.dureeMs;
        lotEchantillonneMs += ep.msParEchantillon() * ep.agregat.echantillons;
        lotFinMs = Math.max(lotFinMs, ep.finMs);
        if (lotPire == null || ep.dureeMs > lotPire.dureeMs) lotPire = ep;
        if (lotLignes.size() < 400) lotLignes.add(ep.ligne());
        if (System.currentTimeMillis() - dernierLotMs >= DiagConfig.ecartRapportsS * 1000L) viderLot();
    }

    private void viderLot() {
        Agregat a = lot;
        lot = null;
        dernierLotMs = System.currentTimeMillis();
        if (a == null) return;
        StringBuilder b = new StringBuilder(64 * 1024);
        b.append("HeraliZ Utils · diagnostic du serveur\n");
        b.append(String.format(Locale.ROOT, "Rapport : TICKS LONGS (%d épisode(s) réunis, du %s à %s)%n", lotEpisodes, date(lotDebutMs), heure(lotFinMs)));
        b.append(String.format(Locale.ROOT, "Seuil : %d ms (blocage à part au-delà de %d ms). Temps en tout : %.0f ms. Pire : %.0f ms %s, à %s%n",
                DiagConfig.seuilMs, DiagConfig.blocageMs, lotSommeMs, lotPire.dureeMs, lotPire.phase(), heure(lotPire.debutMs)));
        if (lotSansEchantillon > 0) b.append(lotSansEchantillon).append(" épisode(s) fini(s) avant le premier échantillon (juste au-dessus du seuil).\n");
        b.append("Note : chaque épisode n'est échantillonné qu'à partir du seuil ; le début du tick n'est pas vu.\n");
        b.append("Ramasse-miettes pendant la période : ").append(PausesGc.resume(PausesGc.entre(lotDebutMs, lotFinMs))).append('\n');
        if (!lotPire.commandes.isEmpty()) b.append("Dernières commandes avant le pire (nom seul) : ").append(String.join(", ", lotPire.commandes)).append('\n');
        b.append('\n');
        b.append("== Liste des épisodes (heure, durée, tick, échantillons -> propriétaires en tête) ==\n");
        for (String l : lotLignes) b.append("  ").append(l).append('\n');
        if (lotEpisodes > lotLignes.size()) b.append("  … ").append(lotEpisodes - lotLignes.size()).append(" de plus\n");
        b.append('\n');
        b.append("== Tous les épisodes réunis ==\n");
        a.ecrire(b, a.echantillons == 0 ? DiagConfig.intervalleMs : lotEchantillonneMs / a.echantillons, 12);
        b.append("== Le pire épisode seul ==\n");
        b.append(lotPire.ligne()).append('\n');
        if (lotPire.agregat != null && lotPire.agregat.echantillons > 0) lotPire.agregat.ecrire(b, lotPire.msParEchantillon(), 3);
        Instantane i = lotPire.contexte != null ? lotPire.contexte : Instantane.dernier;
        if (i != null) i.decrire(b, "Relevé du serveur avant le pire épisode");
        lotLignes.clear();
        lotEpisodes = 0;
        lotSansEchantillon = 0;
        lotSommeMs = 0;
        lotEchantillonneMs = 0;
        lotPire = null;
        lotFinMs = 0;
        ecrire("ticks-longs", b.toString(), null);
    }

    private void resume(long now) {
        long fenetre = now - dernierResumeMs;
        dernierResumeMs = now;
        Compteurs.Stats s = Compteurs.fenetre(fenetre);
        List<PausesGc.Pause> gc = PausesGc.entre(now - fenetre, now);
        long gcTotal = PausesGc.tempsTotalMs();
        long gcFenetre = Math.max(0, gcTotal - gcAvant);
        gcAvant = gcTotal;
        Runtime rt = Runtime.getRuntime();
        Instantane i = Instantane.dernier;
        String etat = i == null ? "pas de relevé" : String.format(Locale.ROOT, "%d joueur(s), %d entités, %d tronçons ; types : %s",
                i.joueurs(), i.entites(), i.troncons(), i.plusNombreux(6));
        HeraliszUtils.LOG.info(String.format(Locale.ROOT,
                "[diag] %d min : %s ; ticks longs %d (blocages %d), rapports %d ; %s ; ramasse-miettes %d ms (pire pause %d ms) ; mémoire %d / %d Mo",
                Math.round(fenetre / 60_000.0), s.court(), resumeEpisodes, resumeBlocages, resumeRapports, etat, gcFenetre,
                PausesGc.pire(gc), (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
        resumeEpisodes = 0;
        resumeBlocages = 0;
        resumeRapports = 0;
    }

    private void ecrire(String genre, String texte, String nomPrevu) {
        try {
            Files.createDirectories(dossier);
            String nom = nomPrevu != null ? nomPrevu : nomPrevu(genre);
            Path f = dossier.resolve(nom);
            for (int k = 2; Files.exists(f) && k < 100; k++) f = dossier.resolve(nom.replace(".txt", "_" + k + ".txt"));
            Files.writeString(f, IP.matcher(texte).replaceAll("x.x.x.x"), StandardCharsets.UTF_8);
            ecrits++;
            resumeRapports++;
            synchronized (Rapports.class) {
                DERNIERS.add(f.getFileName().toString());
                while (DERNIERS.size() > 20) DERNIERS.remove(0);
            }
            HeraliszUtils.LOG.info("[diag] rapport écrit : heraliz_diag/{}", f.getFileName());
            menage();
        } catch (IOException | RuntimeException e) {
            HeraliszUtils.LOG.warn("[diag] rapport {} non écrit : {}", genre, e.toString());
        }
    }

    /** Garde au plus {@code rapports_max} rapports : les plus anciens partent, les blocages en dernier. */
    private void menage() throws IOException {
        List<Path> tous = new ArrayList<>();
        try (DirectoryStream<Path> d = Files.newDirectoryStream(dossier, "*.txt")) {
            for (Path p : d) tous.add(p);
        }
        int max = DiagConfig.rapportsMax;
        if (tous.size() <= max) return;
        tous.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        List<Path> ordre = new ArrayList<>();
        for (Path p : tous) if (!p.getFileName().toString().contains("_blocage")) ordre.add(p);
        for (Path p : tous) if (p.getFileName().toString().contains("_blocage")) ordre.add(p);
        int trop = tous.size() - max;
        for (int k = 0; k < trop && k < ordre.size(); k++) Files.deleteIfExists(ordre.get(k));
    }
}
