package com.dayzhotbar.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

/**
 * Bruit de changement d'objet en main des AUTRES joueurs.
 * <p>
 * MAtmos (mod d'ambiance de The_TCT, jar d'un autre auteur, non modifié) joue un bruit « je prends un objet » (sons
 * {@code matmos_tct:inventory.*}) chaque fois qu'un joueur change d'objet en main. Il le fait dans le jeu de chaque
 * joueur pour TOUS les joueurs que ce jeu voit, pas seulement pour lui-même, au volume 0,3 ; et ces fichiers son sont en
 * stéréo : le moteur audio du jeu n'atténue jamais un son stéréo avec la distance. Le changement d'objet de n'importe
 * quel joueur visible (soin, grenade, arme...) s'entendait donc aussi fort à 50 blocs qu'à côté de soi.
 * <p>
 * Ici, sans toucher à MAtmos : son propre bruit reste tel quel ; celui d'un autre joueur n'est joué qu'à moins de
 * {@link #PORTEE} blocs, plus faible ({@link #VOLUME}) et de moins en moins fort avec la distance, comme un son normal.
 * Même défaut pour les autres bruits « du joueur » de MAtmos ({@code matmos_tct:player.*} : chute dans l'eau, vent de
 * course) : ceux d'un autre joueur sont coupés au-delà de {@link #PORTEE} blocs et gardés tels quels en dessous (certains
 * tournent en boucle : ils ne sont pas remplacés).
 * Réglages fixes, hors de la config des joueurs : un joueur ne doit pas pouvoir réentendre les autres de loin.
 */
public final class SonChangementObjet {
    /** Portée, en blocs, du bruit de changement d'objet d'un autre joueur. */
    static final double PORTEE = 8.0;
    /** Part du volume d'origine de MAtmos gardée tout près d'un autre joueur ; elle baisse ensuite jusqu'à 0 à PORTEE. */
    static final float VOLUME = 0.5F;
    /** MAtmos joue le bruit sur les pieds du joueur concerné : à moins de cette distance de soi, c'est son propre bruit. */
    private static final double SOI = 0.05;

    private SonChangementObjet() {
    }

    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance son = event.getSound();
        if (son == null || son.isRelative()) return;
        ResourceLocation id = son.getLocation();
        if (!"matmos_tct".equals(id.getNamespace())) return;
        boolean inventaire = id.getPath().startsWith("inventory.");
        if (!inventaire && !id.getPath().startsWith("player.")) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer moi = mc.player;
        if (moi == null) return;
        double dx = son.getX() - moi.getX();
        double dy = son.getY() - moi.getY();
        double dz = son.getZ() - moi.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < SOI) return;
        if (!inventaire) {
            if (distance >= PORTEE) event.setSound(null);
            return;
        }
        // getVolume / getPitch lisent le son choisi : il faut d'abord le résoudre (null : son inconnu, rien à jouer)
        if (distance >= PORTEE || son.resolve(mc.getSoundManager()) == null) {
            event.setSound(null);
            return;
        }
        float volume = son.getVolume() * VOLUME * (float) (1.0 - distance / PORTEE);
        // atténuation calculée ici (Attenuation.NONE) : le moteur audio n'en applique aucune à un fichier stéréo
        event.setSound(new SimpleSoundInstance(id, son.getSource(), volume, son.getPitch(), RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, son.getX(), son.getY(), son.getZ(), false));
    }
}
