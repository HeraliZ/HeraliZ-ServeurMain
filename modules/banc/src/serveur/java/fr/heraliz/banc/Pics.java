package fr.heraliz.banc;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pics de téléportations de groupe (01/11, scénario « téléportations » du banc) : {@code /bots pic <cas> <nombre>}, mis
 * en file, un à la fois. Pour chaque pic : 20 ticks de référence, le déclenchement (dans un tick, comme de vrais
 * joueurs), puis une fenêtre de ticks mesurés un par un ; à la fin, UNE ligne {@code [banc] PIC {json}} (tick du
 * déclenchement, pire tick de la fenêtre, moyenne, référence d'avant).
 * <ul>
 *     <li>{@code extraction} : {@code nombre} bots d'une même zone sortent ensemble par le vrai chemin d'une extraction
 *     ({@link Camp#extraire}) ;</li>
 *     <li>{@code spawn} : {@code nombre} bots de zones différentes vont au spawn en même temps (fin de {@code /spawn} :
 *     {@link Camp#versSpawn}) ;</li>
 *     <li>{@code borne} : {@code nombre} bots déjà au camp cliquent sur la borne de départ dans le même tick
 *     ({@link Camp#borne}) ; fenêtre de 15 s (compte à rebours, points cherchés, arrivées) ;</li>
 *     <li>{@code connexion} : un bot se reconnecte (déconnecté 3 s avant) : coût d'une connexion.</li>
 * </ul>
 * Coût hors pic : un test par tick. Fil serveur.
 */
final class Pics {
    private Pics() {
    }

    private record Demande(String cas, int n) {
    }

    private static final ArrayDeque<Demande> FILE = new ArrayDeque<>();
    private static final int PREPARATION = 1, AVANT = 2, APRES = 3, REFERENCE = 20;
    @Nullable
    private static Demande pic;
    private static int phase, compte, faits, fenetre;
    private static long actionNs, debutTick;
    private static double sommeAvant;
    private static int nbAvant, nbApres;
    private static double[] apres = new double[400];
    private static final List<Pilote> CHOISIS = new ArrayList<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START && pic != null) debutTick = System.nanoTime();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && pic != null && debutTick != 0) finTick();
        });
    }

    static boolean demander(String cas, int n) {
        if (!List.of("extraction", "spawn", "borne", "connexion").contains(cas)) return false;
        FILE.add(new Demande(cas, Math.max(1, n)));
        return true;
    }

    static void vider() {
        FILE.clear();
        for (Pilote p : CHOISIS) {
            p.retenu = false;
            if (p.etat == Pilote.Etat.HORS_LIGNE && p.retour == Integer.MAX_VALUE) p.retour = 0;
        }
        CHOISIS.clear();
        pic = null;
        phase = 0;
    }

    private static void finTick() {
        double d = (System.nanoTime() - debutTick) / 1_000_000.0;
        if (phase == AVANT) {
            sommeAvant += d;
            nbAvant++;
        } else if (phase == APRES) {
            if (nbApres < apres.length) apres[nbApres++] = d;
            if (nbApres >= fenetre) resultat();
        }
    }

    /** Par Bots.tick (fin du tick, avant la mesure) : changements de phase et déclenchement. */
    static void tick(MinecraftServer srv) {
        if (pic == null) {
            if (FILE.isEmpty()) return;
            pic = FILE.poll();
            debutTick = 0;
            preparer(srv);
            return;
        }
        if (phase == PREPARATION) {
            if (--compte <= 0) avant();
        } else if (phase == AVANT && nbAvant >= REFERENCE) {
            declencher(srv);
        }
    }

    private static void avant() {
        phase = AVANT;
        sommeAvant = 0;
        nbAvant = 0;
    }

    private static void preparer(MinecraftServer srv) {
        CHOISIS.clear();
        faits = 0;
        String cas = pic.cas();
        List<Pilote> l = new ArrayList<>();
        for (Pilote p : Bots.pilotes()) if (p.etat == Pilote.Etat.EN_JEU && p.vivant()) l.add(p);
        switch (cas) {
            case "extraction" -> {
                // la zone la plus peuplée : un groupe qui sort ensemble
                Map<Integer, List<Pilote>> parZone = new HashMap<>();
                for (Pilote p : l) parZone.computeIfAbsent(p.zone.zone(), k -> new ArrayList<>()).add(p);
                List<Pilote> mieux = List.of();
                for (List<Pilote> g : parZone.values()) if (g.size() > mieux.size()) mieux = g;
                CHOISIS.addAll(mieux.subList(0, Math.min(pic.n(), mieux.size())));
                fenetre = 100;
                avant();
            }
            case "spawn" -> {
                // un par zone, à tour de rôle : des joueurs de partout
                Map<Integer, List<Pilote>> parZone = new HashMap<>();
                for (Pilote p : l) parZone.computeIfAbsent(p.zone.zone(), k -> new ArrayList<>()).add(p);
                for (int i = 0; CHOISIS.size() < pic.n() && !parZone.isEmpty(); i++) {
                    boolean un = false;
                    for (List<Pilote> g : parZone.values()) {
                        if (i < g.size() && CHOISIS.size() < pic.n()) {
                            CHOISIS.add(g.get(i));
                            un = true;
                        }
                    }
                    if (!un) break;
                }
                fenetre = 100;
                avant();
            }
            case "borne" -> {
                // d'abord au camp (comme des joueurs qui viennent de réapparaître), puis la borne tous ensemble
                for (Pilote p : l) {
                    if (CHOISIS.size() >= pic.n()) break;
                    if (Bots.allerAuCamp(p, true)) CHOISIS.add(p);
                }
                fenetre = 300;
                phase = PREPARATION;
                compte = 160;
            }
            default -> {
                // connexion : un bot au calme se déconnecte maintenant, revient au déclenchement
                for (Pilote p : l) {
                    if (p.calme()) {
                        CHOISIS.add(p);
                        break;
                    }
                }
                if (CHOISIS.isEmpty() && !l.isEmpty()) CHOISIS.add(l.get(0));
                for (Pilote p : CHOISIS) Bots.deconnecter(p, Integer.MAX_VALUE);
                fenetre = 100;
                phase = PREPARATION;
                compte = 60;
            }
        }
        if (CHOISIS.isEmpty()) {
            HeralizBanc.LOG.info("[banc] pic {} : aucun bot disponible", cas);
            pic = null;
            phase = 0;
        }
    }

    private static void declencher(MinecraftServer srv) {
        long t0 = System.nanoTime();
        Bots.dansUnPic = true;
        try {
            declencherTous(srv);
        } finally {
            Bots.dansUnPic = false;
        }
        actionNs = System.nanoTime() - t0;
        phase = APRES;
        nbApres = 0;
    }

    private static void declencherTous(MinecraftServer srv) {
        for (Pilote p : CHOISIS) {
            ServerPlayer j = p.joueur;
            switch (pic.cas()) {
                case "extraction" -> {
                    if (j != null && p.vivant() && Camp.extraire(j)) {
                        faits++;
                        Rythmes.compter(Rythmes.PICS);
                        if (Camp.auCamp(j)) Bots.allerAuCamp(p, false);
                    }
                }
                case "spawn" -> {
                    if (j != null && p.vivant() && Bots.allerAuCamp(p, false)) {
                        faits++;
                        Rythmes.compter(Rythmes.PICS);
                    }
                }
                case "borne" -> {
                    p.retenu = false;
                    if (j != null && p.vivant() && Bots.borne(p)) faits++;
                }
                default -> {
                    if (Bots.reconnecter(srv, p)) faits++;
                }
            }
        }
    }

    private static void resultat() {
        double max = 0, somme = 0;
        int pire = 0;
        for (int i = 0; i < nbApres; i++) {
            somme += apres[i];
            if (apres[i] > max) {
                max = apres[i];
                pire = i;
            }
        }
        int arrives = 0;
        if ("borne".equals(pic.cas())) {
            for (Pilote p : CHOISIS) if (p.joueur != null && !Camp.auCamp(p.joueur)) arrives++;
        }
        JsonObject j = new JsonObject();
        j.addProperty("cas", pic.cas());
        j.addProperty("n", pic.n());
        j.addProperty("choisis", CHOISIS.size());
        j.addProperty("faits", faits);
        if ("borne".equals(pic.cas())) j.addProperty("arrives", arrives);
        j.addProperty("action_ms", Math.round(actionNs / 1e5) / 10.0);
        j.addProperty("tick_ms", Math.round(apres[0] * 10) / 10.0);
        j.addProperty("pire_ms", Math.round(max * 10) / 10.0);
        j.addProperty("pire_apres_ticks", pire);
        j.addProperty("moyenne_ms", Math.round(somme / Math.max(1, nbApres) * 10) / 10.0);
        j.addProperty("avant_ms", Math.round(sommeAvant / Math.max(1, nbAvant) * 10) / 10.0);
        j.addProperty("fenetre_s", fenetre / 20);
        HeralizBanc.LOG.info("[banc] PIC {}", j);
        HeralizBanc.LOG.info("[banc] pic {} : {} sur {}, tick du déclenchement {} ms, pire {} ms ({} ticks après), avant {} ms",
                pic.cas(), faits, CHOISIS.size(), String.format(Locale.ROOT, "%.1f", apres[0]), String.format(Locale.ROOT, "%.1f", max), pire,
                String.format(Locale.ROOT, "%.1f", sommeAvant / Math.max(1, nbAvant)));
        CHOISIS.clear();
        pic = null;
        phase = 0;
    }
}
