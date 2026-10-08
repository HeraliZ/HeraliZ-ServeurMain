package fr.heraliz.corps.client.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sons d'interface partages avec le mod clans (generes par tools/clans/gen_sounds.py, aucun son vanilla) :
 * {@code heralisz_clans:ui_clic}, {@code ui_ouvrir}, {@code ui_fermer}. Resolus une fois par identifiant (pas de
 * dependance de compilation au registre des clans). Sans le mod clans : clic vanilla discret.
 */
public final class SonsUi {
    private SonsUi() {
    }

    public static final ResourceLocation CLIC = new ResourceLocation("heralisz_clans", "ui_clic");
    public static final ResourceLocation OUVRIR = new ResourceLocation("heralisz_clans", "ui_ouvrir");
    public static final ResourceLocation FERMER = new ResourceLocation("heralisz_clans", "ui_fermer");

    private static SoundEvent clic, ouvrir, fermer;
    private static boolean resolus;

    private static void resoudre() {
        if (resolus) return;
        resolus = true;
        clic = ForgeRegistries.SOUND_EVENTS.getValue(CLIC);
        ouvrir = ForgeRegistries.SOUND_EVENTS.getValue(OUVRIR);
        fermer = ForgeRegistries.SOUND_EVENTS.getValue(FERMER);
    }

    public static void clic(float volume) {
        resoudre();
        if (clic == null) jouer(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), volume * 0.35F);
        else jouer(clic, volume);
    }

    public static void ouvrir(float volume) {
        resoudre();
        jouer(ouvrir, volume);
    }

    public static void fermer(float volume) {
        resoudre();
        jouer(fermer, volume);
    }

    /** Joue un son (source UI) ; rien si null. */
    public static void jouer(SoundEvent son, float volume) {
        if (son != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(son, volume));
    }
}
