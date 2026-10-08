package fr.heralisz.bases.server;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.model.FenetreVue;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fenetre de pillage (BASES_SPEC §4) : de {@code fenetre_debut} a {@code fenetre_fin} heure du serveur (18 h - 23 h par
 * defaut), forcable par {@code /base admin fenetre ouvrir|fermer|auto} (mode garde dans bases.json). Le « jour de
 * pillage » change a {@code reset_heure} (6 h) : cle {@link #jour()}.
 */
public final class Fenetre {
    private Fenetre() {
    }

    private static FenetreVue.Mode mode() {
        BasesStore s = BasesStore.get();
        return s == null ? FenetreVue.Mode.AUTO : s.fenetre();
    }

    /** Vrai si les pillages sont permis maintenant. */
    public static boolean ouverte() {
        return switch (mode()) {
            case OUVERTE -> true;
            case FERMEE -> false;
            case AUTO -> dansLesHeures(LocalDateTime.now());
        };
    }

    private static boolean dansLesHeures(LocalDateTime t) {
        int d = BasesConfig.FENETRE_DEBUT.get(), f = BasesConfig.FENETRE_FIN.get();
        int minutes = t.getHour() * 60 + t.getMinute();
        if (f > d) return minutes >= d * 60 && minutes < f * 60;
        // fenetre qui passe minuit (ex. 22 h - 2 h)
        return minutes >= d * 60 || minutes < f * 60;
    }

    /** Millisecondes avant le prochain changement (ouverture ou fermeture) ; -1 si le mode est force. */
    public static long msAvantChangement() {
        if (mode() != FenetreVue.Mode.AUTO) return -1;
        LocalDateTime now = LocalDateTime.now();
        boolean ouverte = dansLesHeures(now);
        int h = ouverte ? BasesConfig.FENETRE_FIN.get() : BasesConfig.FENETRE_DEBUT.get();
        LocalDateTime cible = now.toLocalDate().atStartOfDay().plusHours(h);
        if (!cible.isAfter(now)) cible = cible.plusDays(1);
        ZoneId z = ZoneId.systemDefault();
        return cible.atZone(z).toInstant().toEpochMilli() - now.atZone(z).toInstant().toEpochMilli();
    }

    /** Fin de la fenetre ouverte (epoch ms), 0 si fermee ou forcee. */
    public static long finMs() {
        if (mode() != FenetreVue.Mode.AUTO || !ouverte()) return 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cible = now.toLocalDate().atStartOfDay().plusHours(BasesConfig.FENETRE_FIN.get());
        if (!cible.isAfter(now)) cible = cible.plusDays(1);
        // valeur exacte (pas « maintenant + reste ») : identique d'un appel a l'autre, le HUD n'est pas renvoye
        return cible.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Cle du jour de pillage : date de (maintenant - reset_heure), ex. « 2026-10-01 ». */
    public static String jour() {
        return LocalDateTime.now().minusHours(BasesConfig.RESET_HEURE.get()).toLocalDate().toString();
    }

    public static FenetreVue vue() {
        return new FenetreVue(ouverte(), BasesConfig.FENETRE_DEBUT.get(), BasesConfig.FENETRE_FIN.get(), msAvantChangement(), mode());
    }

    /** Force la fenetre (admin) ; AUTO = suit les heures. */
    public static void forcer(FenetreVue.Mode m) {
        BasesStore s = BasesStore.get();
        if (s != null) s.fenetre(m);
    }
}
