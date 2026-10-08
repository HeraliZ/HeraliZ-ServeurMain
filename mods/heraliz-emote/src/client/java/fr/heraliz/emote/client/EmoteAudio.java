package fr.heraliz.emote.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import javax.annotation.Nullable;

/**
 * The volume of the music of emotes, for every player's emotes. Emotecraft plays a song note by
 * note as note block sounds in the "players" category, at the position of the player who emotes
 * (real note blocks use the "records" category): those notes play at a quarter of their volume here, and not at
 * all for other players' emotes when the player turned those off. That one choice is also on
 * Minecraft's Music & Sounds screen.
 */
public final class EmoteAudio {
    /** The music of emotes plays at a quarter of its volume. */
    public static final float MUSIC_VOLUME = 0.25F;

    private EmoteAudio() {
    }

    /** How loud a sound should be: 1 for anything that is not the note of an emote's song. */
    public static float factor(SoundInstance sound) {
        if (sound.getSource() != SoundSource.PLAYERS) return 1.0F;
        ResourceLocation id = sound.getLocation();
        if (!"minecraft".equals(id.getNamespace()) || !id.getPath().startsWith("block.note_block.")) return 1.0F;
        if (!isOwn(sound) && !HeralizConfig.OTHER_PLAYERS_SOUNDS.get()) return 0.0F;
        return MUSIC_VOLUME;
    }

    /** The song of the local player's emote (or of the menu preview) plays where they stand. */
    private static boolean isOwn(SoundInstance sound) {
        Player player = Minecraft.getInstance().player;
        if (player == null || sound.isRelative()) return true;
        return player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 0.25;
    }

    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) return;
        float factor = factor(sound);
        if (factor >= 0.999F) return;
        event.setSound(factor <= 0.001F ? null : new Scaled(sound, factor));
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof SoundOptionsScreen screen)) return;
        int w = 150;
        // beside Done at the bottom when there is room, else in the top right corner
        int x = screen.width / 2 + 104;
        int y = screen.height - 27;
        if (x + w > screen.width - 4) {
            x = screen.width - w - 4;
            y = 4;
        }
        event.addListener(othersButton(x, y, w, 20, "heraliz_emote.sound.others"));
    }

    /** On/off for hearing other players' emotes, saved at once. */
    public static CycleButton<Boolean> othersButton(int x, int y, int w, int h, String label) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(HeralizConfig.OTHER_PLAYERS_SOUNDS.get())
                .create(x, y, w, h, Component.translatable(label), (b, on) -> {
                    HeralizConfig.OTHER_PLAYERS_SOUNDS.set(on);
                    HeralizConfig.SPEC.save();
                });
        button.setTooltip(Tooltip.create(Component.translatable("heraliz_emote.config.other_players.tooltip")));
        return button;
    }

    /** A sound played at a fraction of its volume. */
    private record Scaled(SoundInstance inner, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }
    }
}
