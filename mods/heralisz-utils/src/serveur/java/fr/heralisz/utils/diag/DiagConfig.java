package fr.heralisz.utils.diag;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages du diagnostic (heraliz_diag-server.toml, section [diag]). Les fils du diagnostic lisent les copies
 * {@code volatile} ci-dessous, rafraîchies toutes les 5 s par le chien de garde (jamais de lecture de la config sur le
 * fil du serveur pendant un tick) ; tant que la config n'est pas chargée (avant le monde), valeurs par défaut.
 */
final class DiagConfig {
    private DiagConfig() {
    }

    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ACTIF;
    private static final ForgeConfigSpec.IntValue SEUIL, BLOCAGE, INTERVALLE, PROFIL_INTERVALLE, RAPPORTS_MAX, RESUME,
            ECART_RAPPORTS;

    static volatile boolean actif = true;
    static volatile int seuilMs = 150;
    static volatile int blocageMs = 2000;
    static volatile int intervalleMs = 10;
    static volatile int profilIntervalleMs = 5;
    static volatile int rapportsMax = 50;
    static volatile int resumeMinutes = 5;
    static volatile int ecartRapportsS = 60;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("diag");
        ACTIF = b.comment("Chien de garde des ticks et résumé dans le journal. false : seul /diag profil reste possible.")
                .define("actif", true);
        SEUIL = b.comment("Un tick plus long que ce nombre de millisecondes est échantillonné (piles du fil du serveur) jusqu'à sa fin.")
                .defineInRange("seuil_ms", 150, 60, 10000);
        BLOCAGE = b.comment("Au-delà (ms) : rapport « blocage » à part, avec la pile entière de chaque échantillon et les piles de tous les fils.")
                .defineInRange("blocage_ms", 2000, 200, 60000);
        INTERVALLE = b.comment("Écart entre deux échantillons pendant un tick long (ms).")
                .defineInRange("intervalle_ms", 10, 2, 100);
        PROFIL_INTERVALLE = b.comment("Écart entre deux échantillons pendant /diag profil (ms).")
                .defineInRange("profil_intervalle_ms", 5, 2, 100);
        RAPPORTS_MAX = b.comment("Rapports gardés dans le dossier heraliz_diag (les plus anciens sont effacés, les blocages en dernier).")
                .defineInRange("rapports_max", 50, 5, 500);
        RESUME = b.comment("Une ligne [diag] de résumé dans le journal toutes les N minutes (0 : jamais).")
                .defineInRange("resume_minutes", 5, 0, 120);
        ECART_RAPPORTS = b.comment("Ticks longs (sous le seuil de blocage) regroupés dans un même rapport pendant N secondes.")
                .defineInRange("ecart_rapports_secondes", 60, 5, 3600);
        b.pop();
        SPEC = b.build();
    }

    /** Relit la config (fil du chien de garde) ; rien si elle n'est pas encore chargée. */
    static void rafraichir() {
        try {
            actif = ACTIF.get();
            seuilMs = SEUIL.get();
            blocageMs = Math.max(BLOCAGE.get(), SEUIL.get() + 50);
            intervalleMs = INTERVALLE.get();
            profilIntervalleMs = PROFIL_INTERVALLE.get();
            rapportsMax = RAPPORTS_MAX.get();
            resumeMinutes = RESUME.get();
            ecartRapportsS = ECART_RAPPORTS.get();
        } catch (RuntimeException ignored) {
            // config pas encore chargée (avant le monde) : valeurs par défaut
        }
    }
}
