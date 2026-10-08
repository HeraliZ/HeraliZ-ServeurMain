package fr.heralisz.matchs.duels.killcam.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.duels.killcam.Killcam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Son d'un tir rejoue : le vrai son de l'arme TaCZ (celui que le jeu joue pour un tir, via
 * {@code TimelessAPI.getGunDisplay} et {@code SoundPlayManager.playClientSound}, par reflexion tolerante), sinon le
 * coup de feu maison {@code heralisz_matchs:kc_tir} (genere par script).
 */
final class SonsTir {
    private SonsTir() {
    }

    private static boolean pret, tacz;
    private static Method getGunDisplay, playClientSound;

    private static void preparer() {
        if (pret) return;
        pret = true;
        if (!ModList.get().isLoaded("tacz")) return;
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            getGunDisplay = api.getMethod("getGunDisplay", ItemStack.class);
            Class<?> spm = Class.forName("com.tacz.guns.client.sound.SoundPlayManager");
            playClientSound = spm.getMethod("playClientSound", Entity.class, ResourceLocation.class, float.class, float.class, int.class);
            tacz = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeraliszMatchs.LOG.warn("[killcam] sons TaCZ introuvables, son de tir maison", ex);
        }
    }

    /** Vrai si les sons d'armes TaCZ sont utilisables. */
    static boolean tacz() {
        preparer();
        return tacz;
    }

    static void jouer(FauxJoueur tireur, ItemStack arme, double x, double y, double z) {
        preparer();
        float hauteur = 0.92f + tireur.getRandom().nextFloat() * 0.12f;
        if (tacz && !arme.isEmpty()) {
            try {
                Optional<?> d = (Optional<?>) getGunDisplay.invoke(null, arme);
                if (d.isPresent()) {
                    Object son = d.get().getClass().getMethod("getSounds", String.class).invoke(d.get(), "shoot");
                    if (son instanceof ResourceLocation rl && playClientSound.invoke(null, tireur, rl, 0.8f, hauteur, 96) != null) return;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                HeraliszMatchs.LOG.debug("[killcam] son TaCZ indisponible", ex);
            }
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Killcam.SON_TIR.get(), SoundSource.PLAYERS, 0.9f, hauteur,
                SoundInstance.createUnseededRandom(), x, y, z));
    }
}
