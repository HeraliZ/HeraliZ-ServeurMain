package fr.heraliz.grades.serveur;

import com.mojang.logging.LogUtils;
import fr.heraliz.grades.reseau.ReseauStaff;
import fr.heraliz.grades.staff.ActionStaff;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Journal des actions du panel du staff : {@code heraliz_grades/journal_staff.log} à la racine du serveur (une ligne par
 * action : date | staff | grade | action | joueur | détail | raison), les 200 dernières en mémoire pour l'onglet
 * Historique, et le nombre de sanctions par joueur pour sa fiche. Fil serveur seulement.
 */
final class JournalStaff {
    private static final Logger LOG = LogUtils.getLogger();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int GARDEES = 200;
    private static final ArrayDeque<ReseauStaff.Ligne> RECENTES = new ArrayDeque<>();
    private static final Map<String, Integer> SANCTIONS = new HashMap<>();
    private static Path fichier;

    private JournalStaff() {
    }

    static void demarrer(Path dossier) {
        fichier = dossier.resolve("journal_staff.log");
        RECENTES.clear();
        SANCTIONS.clear();
        if (!Files.exists(fichier)) return;
        try {
            for (String s : Files.readAllLines(fichier, StandardCharsets.UTF_8)) {
                ReseauStaff.Ligne l = lire(s);
                if (l != null) garder(l);
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("[grades] journal du staff illisible ({}) : historique vide", ex.toString());
        }
    }

    static void arreter() {
        fichier = null;
        RECENTES.clear();
        SANCTIONS.clear();
    }

    private static String champ(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').strip();
    }

    private static ReseauStaff.Ligne lire(String s) {
        String[] p = s.split(" \\| ", -1);
        if (p.length < 7) return null;
        try {
            long ms = LocalDateTime.parse(p[0], DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            return new ReseauStaff.Ligne(ms, p[1], p[2], p[3], p[4], p[5], p[6]);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void garder(ReseauStaff.Ligne l) {
        RECENTES.addLast(l);
        while (RECENTES.size() > GARDEES) RECENTES.removeFirst();
        ActionStaff a = ActionStaff.parId(l.action());
        if (a != null && a.sanction() && !l.cible().isEmpty()) SANCTIONS.merge(l.cible().toLowerCase(Locale.ROOT), 1, Integer::sum);
    }

    /** Note une action réussie (fichier, mémoire, console du serveur). */
    static ReseauStaff.Ligne noter(String staff, String grade, ActionStaff a, String cible, String detail, String raison) {
        ReseauStaff.Ligne l = new ReseauStaff.Ligne(System.currentTimeMillis(), champ(staff), champ(grade), a.id, champ(cible), champ(detail), champ(raison));
        garder(l);
        String texte = LocalDateTime.now().format(DATE) + " | " + l.staff() + " | " + l.grade() + " | " + l.action() + " | " + l.cible() + " | "
                + l.detail() + " | " + l.raison();
        LOG.info("[staff] {}", texte);
        if (fichier != null) {
            try {
                Files.createDirectories(fichier.getParent());
                Files.writeString(fichier, texte + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                LOG.warn("[grades] journal du staff impossible à écrire : {}", ex.toString());
            }
        }
        return l;
    }

    /** Les {@code n} dernières actions, la plus récente d'abord. */
    static List<ReseauStaff.Ligne> recentes(int n) {
        List<ReseauStaff.Ligne> l = new ArrayList<>(Math.min(n, RECENTES.size()));
        Iterator<ReseauStaff.Ligne> it = RECENTES.descendingIterator();
        while (it.hasNext() && l.size() < n) l.add(it.next());
        return l;
    }

    /** Les {@code n} dernières sanctions de ce joueur, la plus récente d'abord. */
    static List<ReseauStaff.Ligne> sanctionsDe(String nom, int n) {
        List<ReseauStaff.Ligne> l = new ArrayList<>();
        Iterator<ReseauStaff.Ligne> it = RECENTES.descendingIterator();
        while (it.hasNext() && l.size() < n) {
            ReseauStaff.Ligne x = it.next();
            ActionStaff a = ActionStaff.parId(x.action());
            if (a != null && a.sanction() && x.cible().equalsIgnoreCase(nom)) l.add(x);
        }
        return l;
    }

    static int nombreSanctions(String nom) {
        return SANCTIONS.getOrDefault(nom.toLowerCase(Locale.ROOT), 0);
    }
}
