package fr.heraliz.usure;

import net.minecraftforge.common.ForgeConfigSpec;

/** Reglages du joueur ({@code config/heraliz_usure-client.toml}), aussi dans l'ecran du mod (liste des mods, « Config »). */
public final class ClientConfig {
    private ClientConfig() {
    }

    public enum ModeHud { TOUJOURS, SI_USEE, JAMAIS }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<ModeHud> HUD;
    public static final ForgeConfigSpec.BooleanValue BARRE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        HUD = b.comment("Petit affichage de l'etat de l'arme pres des munitions : TOUJOURS, SI_USEE (pas quand elle est neuve) ou JAMAIS.",
                "L'alerte « ruinee » reste toujours affichee.").defineEnum("hud", ModeHud.TOUJOURS);
        BARRE = b.comment("Barre d'usure sur l'icone des armes (inventaire, barre d'objets).").define("barre", true);
        SPEC = b.build();
    }
}
