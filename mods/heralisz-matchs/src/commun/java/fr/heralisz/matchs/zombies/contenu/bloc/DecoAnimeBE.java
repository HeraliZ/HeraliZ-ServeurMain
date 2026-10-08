package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Partie animee d'un decor de The Giant ({@link BlocDecoAnime}) : etat donne par le jeu, transition animee par le rendu
 * depuis {@link #depuis()}.
 * <ul>
 *     <li>Levier du piege : {@link #ETEINT} (pas de courant : voyants eteints, levier leve), {@link #DISPONIBLE} (voyant
 *     vert), {@link #EN_MARCHE} (levier abaisse, voyant rouge ; puis recharge : rouge clignotant, et retour au repos tout
 *     seul apres {@code duree + recharge} ticks).</li>
 *     <li>Porte de confinement : {@link #FERMEE} (voyant rouge), {@link #OUVERTE} (battants qui coulissent en 2,5 s,
 *     voyant vert).</li>
 * </ul>
 */
public class DecoAnimeBE extends ZbBE {
    public static final int ETEINT = 0, DISPONIBLE = 1, EN_MARCHE = 2;
    public static final int FERMEE = 0, OUVERTE = 1;
    /** Ouverture de la porte (ticks). */
    public static final int OUVERTURE_TICKS = 50;
    private int etat;
    /** Levier : duree du piege et de la recharge (ticks). */
    private int duree, recharge;

    public DecoAnimeBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.DECO_ANIME_BE.get(), pos, state);
    }

    public int etat() {
        return etat;
    }

    public int duree() {
        return duree;
    }

    public int recharge() {
        return recharge;
    }

    public int genre() {
        return getBlockState().getBlock() instanceof BlocDecoAnime b ? b.genre : BlocDecoAnime.LEVIER;
    }

    /** Porte ouverte (battants rentres a plus de moitie) : plus de collision des battants. */
    public boolean ouverte() {
        return etat == OUVERTE && ecoule(0) > OUVERTURE_TICKS * 0.5F;
    }

    /** Change l'etat (et les durees du levier) ; rien si identique. */
    public void etat(int e, int dureeTicks, int rechargeTicks) {
        if (e == etat && dureeTicks == duree && rechargeTicks == recharge && e != EN_MARCHE) return;
        etat = e;
        duree = dureeTicks;
        recharge = rechargeTicks;
        marquer();
        synchroniser();
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putInt("Etat", etat);
        if (duree != 0) tag.putInt("Duree", duree);
        if (recharge != 0) tag.putInt("Recharge", recharge);
    }

    @Override
    protected void lire(CompoundTag tag) {
        etat = tag.getInt("Etat");
        duree = tag.getInt("Duree");
        recharge = tag.getInt("Recharge");
    }

    @Override
    public AABB getRenderBoundingBox() {
        // porte : les battants coulissent jusqu'a ~1,6 bloc hors du prop de chaque cote (4 cases de large)
        if (genre() == BlocDecoAnime.PORTE) return new AABB(worldPosition).inflate(6, 1, 6).expandTowards(0, 4, 0);
        return new AABB(worldPosition).inflate(0.5);
    }
}
