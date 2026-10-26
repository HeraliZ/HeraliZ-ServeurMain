package fr.heraliz.tactique.client.son;

import com.mojang.blaze3d.audio.SoundBuffer;
import fr.heraliz.tactique.mixin.client.SoundBufferAccessor;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sons des armes placés dans l'espace (25/10, retour des joueurs : « avec une arme, quand quelqu'un tire, on l'entend
 * sur tout l'écran, on n'entend pas la direction »). Minecraft ne place un son dans l'espace que s'il est mono : un son
 * stéréo est joué tel quel dans les deux oreilles, d'où qu'il vienne. 149 des 219 sons de tir des gunpacks sont en
 * stéréo. Chez le joueur, au chargement (une fois par son, gardé en mémoire), les sons des gunpacks (dossier
 * {@code tacz_sounds}) sont mélangés en mono ; les fichiers des auteurs ne sont pas touchés. Un tir silencieux lointain
 * reste difficile à situer : le serveur le fait partir d'un point flou autour du tireur ({@code SonLointain}).
 */
public final class SonsMono {
    private SonsMono() {
    }

    /** Son d'un gunpack TaCZ (chemin du fichier : {@code <ns>:tacz_sounds/...}). */
    public static boolean viser(Object cle) {
        return cle instanceof ResourceLocation rl && rl.getPath().startsWith("tacz_sounds/");
    }

    /** Copie mono d'un son stéréo 16 bits (moyenne des deux voies) ; le son tel quel sinon. */
    public static SoundBuffer mono(SoundBuffer b) {
        if (b == null) return null;
        SoundBufferAccessor a = (SoundBufferAccessor) b;
        AudioFormat f = a.heraliz$format();
        ByteBuffer d = a.heraliz$donnees();
        if (d == null || f.getChannels() != 2 || f.getSampleSizeInBits() != 16) return b;
        ByteOrder ordre = f.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer src = d.duplicate().order(ordre);
        src.position(0);
        int images = src.remaining() / 4;
        ByteBuffer out = BufferUtils.createByteBuffer(images * 2).order(ordre);
        for (int i = 0; i < images; i++) {
            int g = src.getShort(i * 4), dr = src.getShort(i * 4 + 2);
            out.putShort((short) ((g + dr) / 2));
        }
        out.flip();
        return new SoundBuffer(out, new AudioFormat(f.getSampleRate(), 16, 1, true, f.isBigEndian()));
    }
}
