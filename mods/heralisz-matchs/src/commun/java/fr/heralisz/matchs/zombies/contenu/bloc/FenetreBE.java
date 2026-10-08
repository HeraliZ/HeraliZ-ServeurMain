package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Fenetre barricadee (bloc maitre = coin minimal de l'ouverture) : 6 planches clouees de travers sur la face interieure,
 * arrachees une a une par les zombies et reclouees par les joueurs. Le jeu detient le compte ({@code ZombiesApi.planches})
 * et le transmet par {@code ZombiesDecor.fenetre} ; ce bloc-entite ne fait que l'afficher et animer chaque changement
 * (planche arrachee : tiree vers l'exterieur puis qui tombe ; planche reposee : qui vole jusqu'a sa place et claque).
 * L'ordre dans lequel les planches partent est propre a chaque fenetre ({@link #ordre}).
 */
public class FenetreBE extends ZbBE {
    public static final int MAX = 6;
    public static final int AUCUNE = 0, ARRACHEE = 1, REPAREE = 2;

    private int largeur = 2, hauteur = 2;
    private Direction exterieur = Direction.NORTH;
    private int planches = MAX;
    private int action = AUCUNE;
    private int emplacement = -1;
    /** Ordre de depart des emplacements (0..5), tire de la position : planche k presente si son rang &lt; planches. */
    private final int[] ordre = new int[MAX];
    private final int[] rang = new int[MAX];

    public FenetreBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.FENETRE_BE.get(), pos, state);
        calculerOrdre();
    }

    private void calculerOrdre() {
        long h = Mth.getSeed(worldPosition);
        for (int i = 0; i < MAX; i++) ordre[i] = i;
        for (int i = MAX - 1; i > 0; i--) {
            h = h * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((h >>> 33) % (i + 1));
            int t = ordre[i];
            ordre[i] = ordre[j];
            ordre[j] = t;
        }
        for (int i = 0; i < MAX; i++) rang[ordre[i]] = i;
    }

    // ------------------------------------------------------------------ serveur
    /** Dimensions de l'ouverture (cases le long du mur, cases en hauteur) et normale vers l'exterieur. */
    public void configurer(int largeur, int hauteur, Direction exterieur) {
        this.largeur = Mth.clamp(largeur, 1, 4);
        this.hauteur = Mth.clamp(hauteur, 1, 4);
        this.exterieur = exterieur.getAxis().isHorizontal() ? exterieur : Direction.NORTH;
        synchroniser();
    }

    /** Nouveau compte de planches avec l'animation de l'evenement. */
    public void planches(int n, ZombiesDecor.EvenementFenetre ev) {
        n = Mth.clamp(n, 0, MAX);
        int avant = planches;
        planches = n;
        action = AUCUNE;
        emplacement = -1;
        if (ev == ZombiesDecor.EvenementFenetre.ARRACHEE && n < avant) {
            action = ARRACHEE;
            emplacement = ordre[n];
        } else if (ev == ZombiesDecor.EvenementFenetre.REPAREE && n > avant) {
            action = REPAREE;
            emplacement = ordre[n - 1];
        }
        marquer();
        synchroniser();
    }

    // ------------------------------------------------------------------ lecture (rendu)
    public int planches() {
        return planches;
    }

    public int largeur() {
        return largeur;
    }

    public int hauteur() {
        return hauteur;
    }

    public Direction exterieur() {
        return exterieur;
    }

    public int action() {
        return action;
    }

    public int emplacement() {
        return emplacement;
    }

    /** Vrai si la planche de cet emplacement (0..5) est clouee. */
    public boolean presente(int emp) {
        return rang[emp] < planches;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putByte("L", (byte) largeur);
        tag.putByte("H", (byte) hauteur);
        tag.putByte("Ext", (byte) exterieur.get3DDataValue());
        tag.putByte("N", (byte) planches);
        tag.putByte("A", (byte) action);
        tag.putByte("E", (byte) emplacement);
    }

    @Override
    protected void lire(CompoundTag tag) {
        largeur = tag.contains("L") ? Mth.clamp(tag.getByte("L"), 1, 4) : 2;
        hauteur = tag.contains("H") ? Mth.clamp(tag.getByte("H"), 1, 4) : 2;
        Direction d = Direction.from3DDataValue(tag.getByte("Ext"));
        exterieur = d.getAxis().isHorizontal() ? d : Direction.NORTH;
        planches = tag.contains("N") ? Mth.clamp(tag.getByte("N"), 0, MAX) : MAX;
        action = tag.getByte("A");
        emplacement = tag.getByte("E");
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = worldPosition;
        int lx = exterieur.getAxis() == Direction.Axis.Z ? largeur : 1;
        int lz = exterieur.getAxis() == Direction.Axis.X ? largeur : 1;
        return new AABB(p.getX() - 2, p.getY() - 2, p.getZ() - 2, p.getX() + lx + 2, p.getY() + hauteur + 1, p.getZ() + lz + 2);
    }
}
