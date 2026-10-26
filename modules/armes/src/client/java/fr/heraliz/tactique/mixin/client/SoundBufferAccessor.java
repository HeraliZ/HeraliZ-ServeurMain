package fr.heraliz.tactique.mixin.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** Données et format d'un son chargé (sons des armes passés en mono, voir client/son/SonsMono). */
@Mixin(SoundBuffer.class)
public interface SoundBufferAccessor {
    @Accessor("data")
    ByteBuffer heraliz$donnees();

    @Accessor("format")
    AudioFormat heraliz$format();
}
