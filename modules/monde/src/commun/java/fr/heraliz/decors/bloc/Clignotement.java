package fr.heraliz.decors.bloc;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Lampes rouges qui clignotent (1.30.3, repris du jar Décors de la nouvelle carte) : toutes sur le même rythme, allumées
 * 5 s puis éteintes 1 s (cycle de {@link #CYCLE} ticks, dont {@link #NOIR} dans le noir).
 * <p>
 * 19/11 (audit des images par seconde, point 2) : le clignotement n'est plus qu'une image. Avant, le serveur basculait
 * l'état {@link #ALLUME} de chaque lampe, et sa lumière (15 / 0), toutes au même tick : chez chaque joueur proche,
 * l'éclairage refait et les morceaux de carte autour reconstruits (jusqu'à 27 par lampe), un à-coup toutes les 5 s puis
 * 1 s après ; au serveur, la lumière refaite et un paquet par lampe. Maintenant la lumière reste à {@link #LUMIERE},
 * l'état ne change plus, le verre clignote par sa texture animée (écrite par modules/monde/outils/lampes_clignotantes.py,
 * même rythme) et la seconde noire de la pièce est refaite près du joueur par la teinte de la lumière
 * ({@code client/AlarmeRouge}). {@link #ALLUME} reste (états déjà posés sur la carte) : ses deux valeurs ont le même
 * modèle. Les lampes dont la lumière a été gardée éteinte par une sauvegarde sont rallumées une fois au chargement
 * ({@code serveur/ClignotementServeur}).
 */
public final class Clignotement {
    public static final BooleanProperty ALLUME = BooleanProperty.create("allume");
    public static final int CYCLE = 120;
    public static final int NOIR = 20;
    /** Lumière des lampes, la même allumées ou dans la seconde noire. */
    public static final int LUMIERE = 15;

    private Clignotement() {}

    /** Vrai pendant les {@code CYCLE - NOIR} premiers ticks du cycle (même rythme que les textures animées). */
    public static boolean allumeA(long t) {
        return Math.floorMod(t, CYCLE) < CYCLE - NOIR;
    }

    public static boolean clignote(Block b) {
        return b instanceof LampeBunker || b instanceof LampeFixe || b instanceof Gyrophare;
    }

    public static int lumiere(BlockState s) {
        return LUMIERE;
    }
}
