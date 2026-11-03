package com.Spartan.esp;

import com.Spartan.Spartan;
import com.Spartan.alert.AlertManager;
import com.Spartan.capture.CaptureManager;
import com.Spartan.config.SpartanConfig;
import com.Spartan.util.Text;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Suivi à travers les murs (03/11, demande du propriétaire : « si un joueur regarde à travers le mur un autre joueur et que
 * son curseur le suit, ça le screenshot »). Branché dans la boucle des paires de l'anti-ESP ({@link VisibilityEngine}) :
 * rien de plus par tick que quelques comparaisons sur les paires déjà calculées, un échantillon tous les
 * {@code periodeTicks}, et une ligne de vue de plus seulement quand la visée est sur une cible cachée.
 *
 * <p>Un échantillon ne compte que si la cible est VRAIMENT invisible pour un joueur honnête (sinon l'épisode s'arrête) :
 * <ul>
 *   <li>aucune ligne de vue, même avec l'anticipation de l'anti-ESP (pas de côté, saut, ping) ; et aucune non plus en
 *   comptant portes, trappes et blocs dessinés invisibles comme transparents (vitres des portes, barrières) — vitres,
 *   fenêtres, barreaux, feuillages et tout bloc non opaque le sont déjà ;</li>
 *   <li>ni vue (par lui) depuis {@code vuRecemmentSecondes}, ni entendue (tir, voix) depuis {@code entenduSecondes}, ni
 *   en train de marcher sans être accroupie à portée des pas, ni en véhicule (moteur) ;</li>
 *   <li>pas de pseudo visible à travers le mur (cible envoyée au jeu et pas accroupie) ;</li>
 *   <li>pas vue récemment par un allié de l'observateur (annonces en vocal, ping), ni alliée elle-même ;</li>
 *   <li>ni trop proche, ni trop loin ; observateur ni spectateur, ni créatif, ni staff qui voit tout.</li>
 * </ul>
 * Un épisode suspect (tous les critères de {@link SuiviCalcul}) est noté dans {@code config/spartan/suivi.log} ; l'alerte
 * part seulement après {@code episodesMin} épisodes et {@code dureeTotaleMinSecondes} dans {@code fenetreMinutes}, avec
 * une capture d'écran. Jamais de sanction automatique. Fil du serveur uniquement.
 */
final class SuiviMurs {
    /** Lignes de vue de contrôle au plus par échantillonnage (au-delà : l'échantillon est sauté, jamais compté). */
    private static final int VERIFS_MAX = 64;
    private static final String NOM = "Suivi à travers les murs";
    /** cos² 30° : au-delà de ce cône autour de la visée, une paire n'est même pas regardée de près. */
    private static final double COS2_CONE = Math.pow(Math.cos(Math.toRadians(30)), 2);

    private static boolean actif;
    private static boolean averti;
    private static int periode = 2;
    private static double distanceMin;
    private static double distanceMax;
    private static double distancePas;
    private static int vuTicks;
    private static int entenduTicks;
    private static int allieTicks;
    private static double peek;
    private static double saut;
    private static SuiviCalcul.Reglages reglages;
    private static int verifs;

    // ligne de vue « honnête » : portes, trappes et blocs invisibles transparents
    private static final LevelVoxels VOXELS = new LevelVoxels(true, true);
    private static final Sight SIGHT = new Sight(new Raycaster(VOXELS));

    /** Un épisode suspect retenu pour l'alerte. */
    private record Trace(long quand, String cible, double secondes, SuiviCalcul.Bilan bilan) {
    }

    private static final class Dossier {
        final ArrayDeque<Trace> traces = new ArrayDeque<>();
        long derniereAlerte;
    }

    private static final Map<UUID, Dossier> DOSSIERS = new HashMap<>();

    private SuiviMurs() {
    }

    /**
     * Début de la boucle des paires (chaque tick) : vrai si ce tick échantillonne. Les réglages sont relus à chaque
     * échantillonnage (fichier rechargé à chaud par Forge).
     */
    static boolean debut(long tick) {
        if (tick % periode != 0) {
            return false;
        }
        boolean voulu = SpartanConfig.SUIVI_ENABLED.get();
        boolean bruits = SpartanConfig.ESP_REVEAL_SHOTS.get() && SpartanConfig.ESP_REVEAL_VOICE.get();
        if (voulu && !bruits && !averti) {
            averti = true;
            Spartan.LOGGER.warn("[Spartan] Suivi à travers les murs coupé : antiEsp.revealOnShot et revealOnVoice doivent être"
                    + " actifs (sinon un joueur qui tire ou parle ne serait pas compté comme entendu).");
        }
        actif = voulu && bruits;
        periode = SpartanConfig.SUIVI_PERIODE.get();
        if (!actif) {
            return false;
        }
        distanceMin = Math.max(SpartanConfig.SUIVI_DISTANCE_MIN.get(), SpartanConfig.ESP_MIN_DISTANCE.get());
        distanceMax = SpartanConfig.SUIVI_DISTANCE_MAX.get();
        distancePas = SpartanConfig.SUIVI_DISTANCE_PAS.get();
        vuTicks = (int) Math.round(SpartanConfig.SUIVI_VU_SECONDES.get() * 20);
        entenduTicks = (int) Math.round(SpartanConfig.SUIVI_ENTENDU_SECONDES.get() * 20);
        allieTicks = (int) Math.round(SpartanConfig.SUIVI_ALLIE_SECONDES.get() * 20);
        peek = Math.max(0.6, SpartanConfig.ESP_PEEK.get());
        saut = Math.max(0.6, SpartanConfig.ESP_JUMP.get());
        int parSeconde = Math.max(1, 20 / periode);
        reglages = new SuiviCalcul.Reglages(SpartanConfig.SUIVI_MARGE_DEGRES.get(), 0.9,
                (int) Math.ceil(SpartanConfig.SUIVI_DUREE_MIN.get() * parSeconde), SpartanConfig.SUIVI_PART_SUR_CIBLE.get(),
                SpartanConfig.SUIVI_BALAYAGE_MIN.get(), SpartanConfig.SUIVI_CORRELATION_MIN.get(),
                SpartanConfig.SUIVI_VIRAGES_MIN.get(), parSeconde, 4.0 / parSeconde);
        verifs = 0;
        VOXELS.reset(); // des chunks ont pu être déchargés depuis le dernier échantillonnage
        if (tick % 6000 == 0) {
            purger();
        }
        return true;
    }

    /**
     * Test rapide (aucun accès au monde) : la paire peut-elle être un suivi à travers les murs ? Ligne de vue calculée il y a
     * peu et absente, cible ni vue ni entendue récemment, distance dans les bornes.
     */
    static boolean candidate(VisibilityEngine.Pair pr, long tick) {
        return !pr.lastResult && tick - pr.lastComputed <= 6 && pr.dist >= distanceMin && pr.dist <= distanceMax
                && tick - pr.lastSeen > vuTicks && tick - pr.vuSuivi > vuTicks && tick - pr.revealUntil > entenduTicks;
    }

    /** La paire n'est plus un suivi possible : l'épisode en cours (s'il y en a un) est clos. */
    static void clore(VisibilityEngine.Pair pr) {
        pr.suivi = null;
    }

    /** Un échantillon d'une paire candidate (observateur o, cible t). */
    static void echantillon(VisibilityEngine.Pair pr, VisibilityEngine.Snap o, VisibilityEngine.Snap t, long tick) {
        if (pr.suivi == null) {
            // tri rapide, sans trigonométrie : la visée n'est même pas dans un cône de 30° autour de la cible
            double dx = t.body.cx() - o.eye.x();
            double dy = t.body.cy() - o.eye.y();
            double dz = t.body.cz() - o.eye.z();
            double d2 = dx * dx + dy * dy + dz * dz;
            double dot = o.lx * dx + o.ly * dy + o.lz * dz;
            if (dot <= 0 || dot * dot < COS2_CONE * d2) {
                return;
            }
        }
        ServerPlayer op = o.p;
        ServerPlayer tp = t.p;
        if (o.seesAll || t.glowing || o.level != t.level || t.root != tp || op.isDeadOrDying() || tp.isDeadOrDying()
                || op.isSleeping() || op.getTags().contains("heraliz_bot")) {
            clore(pr); // véhicule (moteur entendu), surbrillance, spectateur, mort…
            return;
        }
        if (pr.visible && !tp.isDiscrete()) {
            clore(pr); // envoyée au jeu et pas accroupie : son pseudo se voit à travers le mur
            return;
        }
        double vx = t.body.vx();
        double vz = t.body.vz();
        if (pr.dist <= distancePas && vx * vx + vz * vz > 0.0016 && !tp.isShiftKeyDown()) {
            clore(pr); // elle marche à portée des pas : on l'entend
            return;
        }
        double ex = o.eye.x();
        double ey = o.eye.y();
        double ez = o.eye.z();
        double lacet = op.getYRot();
        double tangage = op.getXRot();
        double tx = t.body.cx();
        double ty = t.body.cy();
        double tz = t.body.cz();
        SuiviCalcul.Episode e = pr.suivi;
        if (e != null && tick - e.dernier > periode * 3L + 2) {
            clore(pr); // trou dans les échantillons (paire pas recalculée) : on repart de zéro
            e = null;
        }
        if (e == null) {
            if (!SuiviCalcul.surCible(reglages, ex, ey, ez, lacet, tangage, tx, ty, tz)) {
                return; // pas d'épisode : la visée n'est pas sur elle
            }
            if (!cachee(o, t)) {
                pr.vuSuivi = tick;
                return;
            }
            if (Allies.allied(op, tp) || VisibilityEngine.allieVoit(o, t, allieTicks)) {
                return;
            }
            e = new SuiviCalcul.Episode();
            e.debut = tick;
            pr.suivi = e;
        } else {
            if (++e.depuisAllie >= 20) { // toutes les 2 s : un allié l'a-t-il vue entre-temps ?
                e.depuisAllie = 0;
                if (VisibilityEngine.allieVoit(o, t, allieTicks)) {
                    clore(pr);
                    return;
                }
            }
            if (!cachee(o, t)) {
                pr.vuSuivi = tick;
                clore(pr); // visible en comptant portes, trappes et blocs invisibles comme transparents
                return;
            }
        }
        if (!SuiviCalcul.ajouter(e, reglages, ex, ey, ez, lacet, tangage, tx, ty, tz)) {
            clore(pr); // visée partie ailleurs
            return;
        }
        e.dernier = tick;
        if (!e.compte && SuiviCalcul.suspect(e, reglages)) {
            e.compte = true;
            noter(op, tp, e);
        }
    }

    /**
     * Vraiment cachée pour un joueur honnête : aucune ligne de vue même à travers portes, trappes et blocs invisibles, avec
     * l'anticipation de l'anti-ESP. Budget dépassé ou erreur : « visible » (l'échantillon ne compte jamais contre lui).
     */
    private static boolean cachee(VisibilityEngine.Snap o, VisibilityEngine.Snap t) {
        if (verifs >= VERIFS_MAX) {
            return false;
        }
        verifs++;
        try {
            VOXELS.use(o.level, o.level.getMinSection(), o.level.getSectionsCount());
            return !SIGHT.canSee(o.eye, t.body, o.lookahead, peek, saut);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Épisode suspect : journal, dossier du joueur, alerte quand le dossier est assez lourd. */
    private static void noter(ServerPlayer op, ServerPlayer tp, SuiviCalcul.Episode e) {
        SuiviCalcul.Bilan b = SuiviCalcul.bilan(e);
        double secondes = b.echantillons() * periode / 20.0;
        String nom = Text.oneLine(op.getName().getString());
        String cible = Text.oneLine(tp.getName().getString());
        AlertManager.journal("suivi.log", String.format(Locale.ROOT,
                "[Spartan] %s suit %s à travers les murs : %.1f s, angle moyen %.1f°, %.0f %% sur la cible, cible déplacée de"
                        + " %.0f°, corrélation %.2f, %d virage(s) suivi(s), %.0f m", nom, cible, secondes, b.angleMoyen(),
                b.partSurCible() * 100, b.balayage(), b.correlation(), b.virages(), b.distanceMoyenne()));
        long maintenant = System.currentTimeMillis();
        Dossier d = DOSSIERS.computeIfAbsent(op.getUUID(), u -> new Dossier());
        d.traces.addLast(new Trace(maintenant, cible, secondes, b));
        long fenetre = SpartanConfig.SUIVI_FENETRE_MINUTES.get() * 60_000L;
        while (!d.traces.isEmpty() && maintenant - d.traces.peekFirst().quand() > fenetre) {
            d.traces.removeFirst();
        }
        while (d.traces.size() > 64) {
            d.traces.removeFirst();
        }
        int n = d.traces.size();
        double total = 0;
        double angle = 0;
        double correlation = 0;
        int virages = 0;
        double distance = 0;
        java.util.LinkedHashSet<String> cibles = new java.util.LinkedHashSet<>();
        for (Trace tr : d.traces) {
            total += tr.secondes();
            angle += tr.bilan().angleMoyen() * tr.secondes();
            correlation += tr.bilan().correlation();
            virages += tr.bilan().virages();
            distance += tr.bilan().distanceMoyenne();
            cibles.add(tr.cible());
        }
        if (n < SpartanConfig.SUIVI_EPISODES_MIN.get() || total < SpartanConfig.SUIVI_DUREE_TOTALE_MIN.get()
                || maintenant - d.derniereAlerte < SpartanConfig.SUIVI_DELAI_ALERTE_MINUTES.get() * 60_000L) {
            return;
        }
        d.derniereAlerte = maintenant;
        String details = String.format(Locale.ROOT,
                "sa visée suit des joueurs qu'il ne peut pas voir (aucune ligne de vue, ni vus, ni entendus) : %d épisode(s)"
                        + " en %d min, durée totale %.0f s, angle moyen %.1f° de la cible, corrélation moyenne %.2f, %d virage(s)"
                        + " suivi(s), distance moyenne %.0f m ; cible(s) : %s. Wallhack / ESP possible : à vérifier en spectateur"
                        + " (aucune sanction automatique)",
                n, SpartanConfig.SUIVI_FENETRE_MINUTES.get(), total, angle / Math.max(0.001, total), correlation / n, virages,
                distance / n, String.join(", ", cibles));
        AlertManager.dispatch(op, NOM, n, details, null);
        if (SpartanConfig.SUIVI_CAPTURE.get()) {
            String refus = CaptureManager.demander(op, "suivi à travers les murs (" + cible + ")");
            if (refus != null) {
                AlertManager.journal("suivi.log", "[Spartan] capture de " + nom + " impossible : " + refus);
            }
        }
    }

    /** Dossiers sans épisode récent ni alerte récente : oubliés (toutes les 5 min). */
    private static void purger() {
        long maintenant = System.currentTimeMillis();
        long fenetre = SpartanConfig.SUIVI_FENETRE_MINUTES.get() * 60_000L;
        long delai = SpartanConfig.SUIVI_DELAI_ALERTE_MINUTES.get() * 60_000L;
        Iterator<Dossier> it = DOSSIERS.values().iterator();
        while (it.hasNext()) {
            Dossier d = it.next();
            Trace derniere = d.traces.peekLast();
            if ((derniere == null || maintenant - derniere.quand() > fenetre) && maintenant - d.derniereAlerte > delai) {
                it.remove();
            }
        }
    }

    /** Arrêt du serveur. */
    static void reset() {
        DOSSIERS.clear();
        averti = false;
    }

    /** /spartan esp : épisodes retenus pour ce joueur. */
    static String decrire(UUID id) {
        Dossier d = DOSSIERS.get(id);
        if (d == null || d.traces.isEmpty()) {
            return "suivi à travers les murs : aucun épisode suspect récent";
        }
        return "suivi à travers les murs : " + d.traces.size() + " épisode(s) suspect(s) récent(s) (voir config/spartan/suivi.log)";
    }
}
