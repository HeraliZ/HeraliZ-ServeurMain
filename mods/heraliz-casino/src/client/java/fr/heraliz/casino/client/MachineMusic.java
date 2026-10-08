package fr.heraliz.casino.client;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.client.camera.CasinoCamera;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.screen.MachineScreen;
import fr.heraliz.casino.config.ClientConfig;
import fr.heraliz.casino.machine.MachineType;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Musique d'ambiance des machines a sous : tant que le joueur joue sur une machine (ecran ouvert ou camera sur la
 * machine), sa musique tourne en boucle (fondu a l'entree et a la sortie) et la musique de Minecraft se tait.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class MachineMusic {
    private MachineMusic() {
    }

    private static Loop playing;
    private static Snd current;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Snd want = mc.level == null || mc.player == null ? null : music(active(mc));
        if (want != current) {
            if (playing != null) playing.fadeOut();
            playing = null;
            if (want != null) {
                playing = new Loop(want);
                mc.getSoundManager().play(playing);
            }
            current = want;
        }
        // la musique de Minecraft ne joue pas par-dessus
        if (current != null) mc.getMusicManager().stopPlaying();
    }

    /** Etat courant, pour les tests : musique voulue, lue ou non, volume. */
    public static String debugState() {
        Minecraft mc = Minecraft.getInstance();
        return current == null ? "aucune"
                : current.id + " lue=" + (playing != null && mc.getSoundManager().isActive(playing)) + " volume=" + (playing == null ? 0 : playing.getVolume());
    }

    private static MachineBlockEntity active(Minecraft mc) {
        if (mc.screen instanceof MachineScreen<?> ms) return ms.machine();
        return CasinoCamera.target();
    }

    private static Snd music(MachineBlockEntity be) {
        if (be == null || be.isRemoved()) return null;
        return switch (be.type) {
            case ROTTEN -> Snd.MUSIC_ROTTEN;
            case OLYMPE -> Snd.MUSIC_OLYMPE;
            case LOUP -> Snd.MUSIC_LOUP;
            case NICHE -> Snd.MUSIC_NICHE;
            default -> null;
        };
    }

    /** Boucle non spatialisee, reglee par le volume "Juke-box / blocs musicaux" et le reglage du mod. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private static final int FADE_IN = 40, FADE_OUT = 30;
        private int age;
        private int fadeFrom = -1;
        private float fadeStartVolume;

        Loop(Snd s) {
            super(ModRegistry.sound(s), SoundSource.RECORDS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0001f;
        }

        void fadeOut() {
            if (fadeFrom < 0) {
                fadeFrom = age;
                fadeStartVolume = volume;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            float target = 0.55f * (float) (double) ClientConfig.MUSIC_VOLUME.get();
            if (fadeFrom >= 0) {
                float k = 1f - (age - fadeFrom) / (float) FADE_OUT;
                volume = Math.max(0.0001f, fadeStartVolume * k);
                if (k <= 0) stop();
            } else {
                volume = Math.max(0.0001f, target * Math.min(1f, age / (float) FADE_IN));
            }
        }
    }
}
