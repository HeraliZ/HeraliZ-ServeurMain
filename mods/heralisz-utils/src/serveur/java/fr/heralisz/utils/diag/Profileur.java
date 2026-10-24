package fr.heralisz.utils.diag;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Profil demandé ({@code /diag profil <secondes>}) : un fil à part lit la pile du fil du serveur en continu (toutes les
 * {@code profil_intervalle_ms}), pendant que le crochet des entités chronomètre chaque tick d'entité par type
 * ({@link SuiviEntites}) et que les chargements de tronçons sont comptés. À la fin, le fil du serveur relève les
 * entités et remet le tout au fil d'écriture, qui met en forme le rapport « profil ».
 * <p>Coût pendant le profil seulement : une lecture de pile toutes les 5 ms (un court arrêt de la JVM à chaque fois, de
 * l'ordre de 1 à 3 % du temps) et deux lectures d'horloge par tick d'entité.
 */
final class Profileur implements Runnable {
    static volatile Profileur courant;
    static final AtomicLong CHARGES = new AtomicLong(), LUS = new AtomicLong(), DECHARGES = new AtomicLong();

    private final MinecraftServer srv;
    private final CommandSourceStack source;
    final int secondes;
    private final int intervalleMs;
    private final long debutMs, debutNs;
    private final int premierTick;
    private final Instantane avant;
    private final Agregat travail = new Agregat();
    private final Thread fil;
    private volatile boolean arreter;
    private volatile boolean fini;

    // rempli par le fil du profil
    private int echTick, echEntre, echRepos, echTotal;
    private long trouMs;
    private long finMs;
    // rempli par le fil du serveur à la fin
    private Instantane apres;
    private Map<EntityType<?>, long[]> temps;
    private Compteurs.Stats stats;
    private long charges, lus, decharges;
    private boolean crochet;

    private Profileur(MinecraftServer srv, CommandSourceStack source, int secondes) {
        this.srv = srv;
        this.source = source;
        this.secondes = secondes;
        this.intervalleMs = DiagConfig.profilIntervalleMs;
        this.debutMs = System.currentTimeMillis();
        this.debutNs = System.nanoTime();
        this.premierTick = ChienDeGarde.numero + 1;
        this.avant = Instantane.prendre(srv);
        fil = new Thread(this, "HeraliZ-diag-profil");
        fil.setDaemon(true);
        fil.setPriority(Thread.NORM_PRIORITY - 1);
    }

    /** Fil du serveur (commande). Rend null si lancé, sinon le profil déjà en cours. */
    static Profileur lancer(MinecraftServer srv, CommandSourceStack source, int secondes) {
        Profileur p = courant;
        if (p != null) return p;
        CHARGES.set(0);
        LUS.set(0);
        DECHARGES.set(0);
        p = new Profileur(srv, source, secondes);
        SuiviEntites.commencerMesure();
        courant = p;
        p.fil.start();
        HeraliszUtils.LOG.info("[diag] profil lancé pour {} s (un échantillon toutes les {} ms)", secondes, p.intervalleMs);
        return null;
    }

    /** Arrêt avant la fin (le rapport est écrit avec ce qui a été vu). */
    static boolean arreterCourant() {
        Profileur p = courant;
        if (p == null) return false;
        p.arreter = true;
        p.fil.interrupt();
        return true;
    }

    long resteS() {
        return Math.max(0, secondes - (System.nanoTime() - debutNs) / 1_000_000_000L);
    }

    @Override
    public void run() {
        long finPrevue = debutNs + secondes * 1_000_000_000L;
        long intervalle = Math.max(1, intervalleMs) * 1_000_000L;
        long precedent = 0;
        try {
            while (!arreter && Diag.enService && System.nanoTime() < finPrevue) {
                long t = System.nanoTime();
                boolean enTick = ChienDeGarde.dansTick;
                ThreadInfo ti = Echantillon.lire(ChienDeGarde.serveur);
                if (ti != null) {
                    Thread.State etat = ti.getThreadState();
                    echTotal++;
                    if (!enTick && etat != Thread.State.RUNNABLE) {
                        echRepos++;
                    } else {
                        if (enTick) echTick++;
                        else echEntre++;
                        travail.ajouter(ti.getStackTrace(), etat, enTick ? Echantillon.entite(true) : null);
                    }
                }
                if (precedent != 0 && t - precedent > 3 * intervalle) trouMs += (t - precedent - intervalle) / 1_000_000L;
                precedent = t;
                long reste = intervalle - (System.nanoTime() - t);
                if (reste > 0) Thread.sleep(reste / 1_000_000L, (int) (reste % 1_000_000L));
            }
        } catch (InterruptedException ignored) {
            // arrêt demandé
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[diag] erreur pendant le profil : {}", t.toString());
        }
        finMs = System.currentTimeMillis();
        if (!Diag.enService) {
            SuiviEntites.mesure = false;
            courant = null;
            return;
        }
        try {
            srv.execute(this::finir);
        } catch (Throwable t) {
            SuiviEntites.mesure = false;
            courant = null;
        }
    }

    /** Fil du serveur : relevés de fin, rapport, message. */
    private void finir() {
        if (fini) return;
        fini = true;
        String nom = null;
        try {
            temps = SuiviEntites.finirMesure();
            crochet = SuiviEntites.vu;
            apres = Instantane.prendre(srv);
            charges = CHARGES.get();
            lus = LUS.get();
            decharges = DECHARGES.get();
            double duree = Math.max(0.001, (finMs - debutMs) / 1000.0);
            stats = Compteurs.numeros(premierTick, ChienDeGarde.numero, duree);
            nom = Rapports.plusTard("profil", this::texte);
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[diag] fin du profil impossible : {}", t.toString());
        } finally {
            SuiviEntites.mesure = false;
            courant = null;
        }
        String tete = travail.tete(5);
        HeraliszUtils.LOG.info("[diag] profil fini : heraliz_diag/{} ; en tête : {}", nom, tete);
        try {
            if (nom != null) {
                Component a = Component.translatableWithFallback("heralisz_utils.diag.profil.fini", "Profil fini : heraliz_diag/%s", nom);
                source.sendSuccess(() -> a.copy().withStyle(ChatFormatting.GRAY), false);
            }
            Component b = Component.translatableWithFallback("heralisz_utils.diag.profil.tete", "En tête : %s", tete);
            source.sendSuccess(() -> b.copy().withStyle(ChatFormatting.GRAY), false);
        } catch (Throwable ignored) {
            // joueur parti entre-temps
        }
    }

    // ------------------------------------------------------------------ rapport (fil d'écriture)

    private String texte() {
        StringBuilder b = new StringBuilder(128 * 1024);
        double dureeS = Math.max(0.001, (finMs - debutMs) / 1000.0);
        b.append("HeraliZ Utils · diagnostic du serveur\n");
        b.append(String.format(Locale.ROOT, "Rapport : PROFIL demandé (/diag profil %d)%s%n", secondes, arreter ? ", arrêté avant la fin" : ""));
        b.append(String.format(Locale.ROOT, "Du %s à %s : %.1f s, un échantillon toutes les %d ms (%d échantillons)%n%n",
                Rapports.date(debutMs), Rapports.heure(finMs), dureeS, intervalleMs, echTotal));

        b.append("== Ticks pendant le profil ==\n");
        b.append(stats == null ? "?" : stats.court()).append('\n');
        if (stats != null) {
            b.append(String.format(Locale.ROOT, "Temps passé dans les ticks : %.0f ms sur %.0f ms (%.0f %%). Plus grand écart entre deux débuts de tick : %.0f ms%n",
                    stats.sommeMs(), dureeS * 1000, 100 * stats.sommeMs() / (dureeS * 1000), stats.ecartPire()));
        }
        b.append('\n');

        b.append("== Où passe le temps du fil du serveur (échantillons) ==\n");
        int tot = Math.max(1, echTotal);
        b.append(String.format(Locale.ROOT, "  %5.1f %%  dans les ticks%n", 100.0 * echTick / tot));
        b.append(String.format(Locale.ROOT, "  %5.1f %%  entre deux ticks, au travail (paquets des joueurs, tronçons, tâches)%n", 100.0 * echEntre / tot));
        b.append(String.format(Locale.ROOT, "  %5.1f %%  au repos (attente du tick suivant : de la marge)%n", 100.0 * echRepos / tot));
        if (trouMs > 0) b.append(String.format(Locale.ROOT, "  trous entre les échantillons : %d ms (JVM entière arrêtée : ramasse-miettes ou machine saturée)%n", trouMs));
        b.append("Ramasse-miettes pendant le profil : ").append(PausesGc.resume(PausesGc.entre(debutMs, finMs))).append("\n\n");

        entites(b);
        troncons(b);

        b.append("== Échantillons du fil du serveur au travail (dans les ticks et entre deux ticks, repos exclu) ==\n");
        double msParEch = (dureeS * 1000 - trouMs) / tot;
        travail.ecrire(b, msParEch, 15);
        avant.decrire(b, "Relevé au début du profil");
        if (apres != null) apres.decrire(b, "Relevé à la fin du profil");
        return b.toString();
    }

    private void entites(StringBuilder b) {
        b.append("== Temps par type d'entité (chaque tick d'entité chronométré, passagers compris) ==\n");
        if (temps == null || temps.isEmpty()) {
            b.append(crochet ? "Aucune entité n'a tourné pendant le profil.\n\n"
                    : "Mesure par type indisponible : le crochet de Level.guardEntityTick n'est pas posé (autre version de Minecraft ou de Mohist).\n"
                    + "Voir plus bas « Entité dont le tick était en cours » (échantillons) et les relevés.\n\n");
            return;
        }
        int ticks = stats == null ? 0 : stats.ticks();
        double sommeTicks = stats == null ? 0 : stats.sommeMs();
        List<Map.Entry<EntityType<?>, long[]>> l = new ArrayList<>(temps.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1]));
        long totalNs = 0, totalAppels = 0;
        for (Map.Entry<EntityType<?>, long[]> e : l) {
            totalNs += e.getValue()[1];
            totalAppels += e.getValue()[0];
        }
        b.append(String.format(Locale.ROOT, "Toutes les entités : %.1f ms en tout, %.2f ms par tick du serveur (%.1f %% du temps des ticks), %d ticks d'entité%n",
                totalNs / 1e6, ticks == 0 ? 0 : totalNs / 1e6 / ticks, sommeTicks <= 0 ? 0 : 100 * totalNs / 1e6 / sommeTicks, totalAppels));
        b.append(String.format(Locale.ROOT, "%10s %10s %8s %9s %9s %10s %9s  %s%n", "ms total", "ms/tick", "% ticks", "nb début", "nb fin",
                "µs/entité", "pire ms", "type"));
        int k = 0;
        for (Map.Entry<EntityType<?>, long[]> e : l) {
            if (k++ >= 60) break;
            long[] v = e.getValue();
            String nom = Instantane.nom(e.getKey());
            double ms = v[1] / 1e6;
            b.append(String.format(Locale.ROOT, "%10.1f %10.3f %7.1f%% %9d %9d %10.1f %9.2f  %s%n", ms, ticks == 0 ? 0 : ms / ticks,
                    sommeTicks <= 0 ? 0 : 100 * ms / sommeTicks, avant.types().getOrDefault(nom, 0),
                    apres == null ? 0 : apres.types().getOrDefault(nom, 0), v[0] == 0 ? 0 : v[1] / 1e3 / v[0], v[2] / 1e6, nom));
        }
        if (l.size() > 60) b.append("… ").append(l.size() - 60).append(" type(s) de plus\n");
        b.append('\n');
    }

    private void troncons(StringBuilder b) {
        b.append("== Tronçons pendant le profil ==\n");
        b.append(String.format(Locale.ROOT, "Chargements (ChunkEvent.Load) : %d ; lus sur le disque (ChunkDataEvent.Load) : %d ; "
                + "écart (en gros : tronçons générés) : %d ; déchargements : %d%n", charges, lus, Math.max(0, charges - lus), decharges));
        if (apres != null) {
            for (Instantane.Monde m : apres.mondes()) {
                int a = -1;
                for (Instantane.Monde x : avant.mondes()) if (x.nom().equals(m.nom())) a = x.troncons();
                b.append(String.format(Locale.ROOT, "  %-28s tronçons chargés : %d -> %d%n", m.nom(), a, m.troncons()));
            }
        }
        b.append('\n');
    }
}
