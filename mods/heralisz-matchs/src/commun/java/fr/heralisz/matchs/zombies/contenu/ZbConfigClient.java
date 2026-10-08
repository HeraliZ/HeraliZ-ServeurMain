package fr.heralisz.matchs.zombies.contenu;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages client du mode zombies ({@code config/heralisz_matchs-zombies-client.toml}, enregistre par
 * {@link ZombiesContenu#construire}) : volumes de la musique et de l'ambiance, mode de lecture de la playlist,
 * musique « 115 » et jingles classiques integres au mod. Lus a chaque image par la musique
 * ({@code zombies/client/musique}) : une modification du fichier (ou {@code /zbmusique volume}) s'applique aussitot.
 */
public final class ZbConfigClient {
    private ZbConfigClient() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue VOLUME_MUSIQUE;
    public static final ForgeConfigSpec.DoubleValue VOLUME_AMBIANCE;
    public static final ForgeConfigSpec.ConfigValue<String> MODE;
    public static final ForgeConfigSpec.BooleanValue MUSIQUE_115;
    public static final ForgeConfigSpec.BooleanValue JINGLES_CLASSIQUES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Musique du mode zombies de HeraliZ. Vos morceaux : config/heralisz_matchs/musiques/ (voir LISEZMOI.txt).")
                .push("musique");
        VOLUME_MUSIQUE = b.comment("Volume de la playlist (0 a 1), multiplie par le curseur « Musique » des options.")
                .defineInRange("volume_musique", 0.6, 0.0, 1.0);
        VOLUME_AMBIANCE = b.comment("Volume du fond d'ambiance (0 a 1), multiplie par le curseur « Ambiance ».")
                .defineInRange("volume_ambiance", 0.55, 0.0, 1.0);
        MODE = b.comment("continue = la playlist enchaine les morceaux de la 1re manche a la fin ;",
                        "manches = un morceau par manche, coupe en fondu a la fin de la manche ; aucune = pas de playlist.")
                .define("mode", "continue");
        MUSIQUE_115 = b.comment("Jouer la musique « 115 » (integree au mod) en tete de la playlist et a l'easter egg",
                        "(votre fichier easter_egg.ogg passe avant).")
                .define("musique_115", true);
        JINGLES_CLASSIQUES = b.comment("Utiliser les jingles classiques integres (debut/fin de manche, boite mystere)",
                        "a la place des jingles HeraliZ (vos fichiers manche_debut.ogg... passent avant).")
                .define("jingles_classiques", true);
        b.pop();
        SPEC = b.build();
    }

    /** Volume de la musique (0..1), tolerant a une configuration pas encore chargee. */
    public static float volumeMusique() {
        try {
            return (float) (double) VOLUME_MUSIQUE.get();
        } catch (IllegalStateException e) {
            return 0.6F;
        }
    }

    public static float volumeAmbiance() {
        try {
            return (float) (double) VOLUME_AMBIANCE.get();
        } catch (IllegalStateException e) {
            return 0.55F;
        }
    }

    public static String mode() {
        try {
            String m = MODE.get();
            return m == null ? "continue" : m.trim().toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalStateException e) {
            return "continue";
        }
    }

    public static boolean musique115() {
        try {
            return MUSIQUE_115.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean jinglesClassiques() {
        try {
            return JINGLES_CLASSIQUES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
