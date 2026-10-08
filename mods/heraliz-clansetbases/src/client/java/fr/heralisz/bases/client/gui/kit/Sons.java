package fr.heralisz.bases.client.gui.kit;

import fr.heralisz.bases.HeraliszBases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * Sons d'interface du mod (generes par tools/bases/gen_sons.py : ui_clic, ui_ouvrir, ui_fermer, tp_tic ; aucun son
 * vanilla). Ils ne sont joues que par le client : pas besoin de les inscrire au registre (le gestionnaire de sons les
 * trouve par leur entree de sounds.json, sous-titres compris). Instances creees une fois.
 */
public final class Sons {
    private Sons() {
    }

    public static final SoundEvent CLIC = SoundEvent.createVariableRangeEvent(HeraliszBases.id("ui_clic"));
    public static final SoundEvent OUVRIR = SoundEvent.createVariableRangeEvent(HeraliszBases.id("ui_ouvrir"));
    public static final SoundEvent FERMER = SoundEvent.createVariableRangeEvent(HeraliszBases.id("ui_fermer"));
    public static final SoundEvent TIC = SoundEvent.createVariableRangeEvent(HeraliszBases.id("tp_tic"));

    /** Joue un son d'interface (source UI). */
    public static void jouer(SoundEvent s, float volume) {
        jouer(s, volume, 1.0f);
    }

    public static void jouer(SoundEvent s, float volume, float hauteur) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, hauteur, volume));
    }
}
