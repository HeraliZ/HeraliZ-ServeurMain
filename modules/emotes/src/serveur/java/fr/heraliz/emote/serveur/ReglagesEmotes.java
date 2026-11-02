package fr.heraliz.emote.serveur;

import fr.heraliz.emote.EmoteRules;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Réglages du serveur d'HeraliZ Emote (audit du 02/11, mineur 20 : avant, écrits dans le code). Déclarés par la partie
 * serveur seulement : fichier {@code config/heraliz_emote-serveur.toml} du serveur, jamais envoyé aux joueurs ni écrit
 * sur leur PC. Relu tout seul quand le fichier change (Forge).
 */
public final class ReglagesEmotes {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue SECONDES_SANS_ARME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("armes");
        SECONDES_SANS_ARME = b.comment("Secondes sans arme en main après la fin d'une emote (une emote montre le joueur de dos, ce qui",
                        "ne doit pas aider dans un combat). 10 : réglage d'origine ; 0 : aucune attente après l'emote (jamais d'arme",
                        "pendant l'emote).")
                .defineInRange("secondes_sans_arme", (int) (EmoteRules.SANS_ARME_MS / 1000), 0, 60);
        b.pop();
        SPEC = b.build();
    }

    private ReglagesEmotes() {
    }

    /** Constructeur du @Mod (PartieServeur.init). */
    static void enregistrer() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "heraliz_emote-serveur.toml");
    }

    /** Délai sans arme après une emote (ms) ; le réglage d'origine tant que le fichier n'est pas lu. */
    static long sansArmeMs() {
        try {
            return SECONDES_SANS_ARME.get() * 1000L;
        } catch (IllegalStateException pasEncoreLu) {
            return EmoteRules.SANS_ARME_MS;
        }
    }
}
