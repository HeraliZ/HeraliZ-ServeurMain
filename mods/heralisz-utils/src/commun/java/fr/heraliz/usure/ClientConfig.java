package fr.heraliz.usure;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages du joueur ({@code config/heraliz_usure-client.toml}), aussi dans l'ecran du mod (liste des mods, « Config »).
 * L'ancien reglage {@code barre} (barre d'usure sur l'icone des armes) n'existe plus : l'icone n'affiche plus l'usure,
 * Forge retire la ligne des anciens fichiers.
 */
public final class ClientConfig {
    private ClientConfig() {
    }

    public enum ModeHud { TOUJOURS, SI_USEE, JAMAIS }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<ModeHud> HUD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HUD = b.comment("Petit indicateur de l'etat de l'arme en main, en bas a gauche de l'ecran : TOUJOURS, SI_USEE (pas quand elle est neuve) ou JAMAIS.",
                "L'alerte « ruinee » reste toujours affichee.").defineEnum("hud", ModeHud.TOUJOURS);
        SPEC = b.build();
    }
}
