package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/**
 * Element d'ouverture payant (bloc maitre = coin minimal du plan de la porte) : {@link #type} {@code porte} (porte
 * metallique barree de planches et d'une barre rayee), {@code grille} (grille fermee par une chaine et un cadenas) ou
 * {@code debris} (tas de gravats qui bouche le passage), et ses pancartes de prix a la craie. Modeles generes par
 * {@code tools/matchs/zombies/gen_obstacles.py} ({@code obstacle_<type>_<L>x<H>}), dessines par le rendu client.
 * <p>Geometrie : {@link #largeur} le long du mur, {@link #hauteur}, {@link #profondeur} (epaisseur du plan pose, 1 en
 * general), {@link #normale} = cote « avant » (les deux faces sont decorees). Pancartes (0 a 2) : position donnee par la
 * carte (relative a l'origine du modele = centre bas du plan pose, axes du monde) et orientation ; sans pancarte, une
 * ardoise est accrochee sur chaque face.
 */
public class ObstacleBE extends ZbBE {
    public static final int MAX_PANCARTES = 2;
    private String type = "porte";
    private int largeur = 2, hauteur = 3, profondeur = 1, cout;
    private Direction normale = Direction.SOUTH;
    private int pancartes;
    /** Dessiner le cadre du modele (faux sur la carte : elle a deja son cadre raye autour de la porte). */
    private boolean cadre = true;
    private final float[] px = new float[MAX_PANCARTES], py = new float[MAX_PANCARTES], pz = new float[MAX_PANCARTES],
            pyaw = new float[MAX_PANCARTES];

    public ObstacleBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.OBSTACLE_BE.get(), pos, state);
    }

    /** Serveur : avec ou sans le cadre du modele (a appeler apres {@link #configurer}). */
    public void cadre(boolean oui) {
        cadre = oui;
        synchroniser();
    }

    public boolean cadre() {
        return cadre;
    }

    /** Serveur : configuration (sans les pancartes, voir {@link #pancarte}). */
    public void configurer(String type, int largeur, int hauteur, int profondeur, Direction normale, int cout) {
        String t = type == null ? "porte" : type.toLowerCase(Locale.ROOT);
        this.type = t.equals("grille") || t.equals("debris") ? t : "porte";
        this.largeur = Mth.clamp(largeur, 1, 8);
        this.hauteur = Mth.clamp(hauteur, 1, 6);
        this.profondeur = Mth.clamp(profondeur, 1, 4);
        this.normale = normale != null && normale.getAxis().isHorizontal() ? normale : Direction.SOUTH;
        this.cout = Math.max(0, cout);
        this.pancartes = 0;
        marquer();
        synchroniser();
    }

    /** Serveur : ajoute une pancarte (origine de l'ardoise relative a l'origine du modele, lacet = face de l'ardoise). */
    public void pancarte(float x, float y, float z, float yaw) {
        if (pancartes >= MAX_PANCARTES) return;
        px[pancartes] = x;
        py[pancartes] = y;
        pz[pancartes] = z;
        pyaw[pancartes] = yaw;
        pancartes++;
        synchroniser();
    }

    public String type() {
        return type;
    }

    public int largeur() {
        return largeur;
    }

    public int hauteur() {
        return hauteur;
    }

    public int profondeur() {
        return profondeur;
    }

    public Direction normale() {
        return normale;
    }

    public int cout() {
        return cout;
    }

    public int pancartes() {
        return pancartes;
    }

    public float px(int i) {
        return px[i];
    }

    public float py(int i) {
        return py[i];
    }

    public float pz(int i) {
        return pz[i];
    }

    public float pyaw(int i) {
        return pyaw[i];
    }

    /** Etendue du plan pose le long de x et de z (blocs). */
    public int etendueX() {
        return normale.getAxis() == Direction.Axis.X ? profondeur : largeur;
    }

    public int etendueZ() {
        return normale.getAxis() == Direction.Axis.Z ? profondeur : largeur;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("T", type);
        tag.putByte("L", (byte) largeur);
        tag.putByte("H", (byte) hauteur);
        tag.putByte("P", (byte) profondeur);
        tag.putByte("N", (byte) normale.get3DDataValue());
        tag.putInt("C", cout);
        tag.putBoolean("Cadre", cadre);
        tag.putByte("NP", (byte) pancartes);
        for (int i = 0; i < pancartes; i++) {
            tag.putFloat("PX" + i, px[i]);
            tag.putFloat("PY" + i, py[i]);
            tag.putFloat("PZ" + i, pz[i]);
            tag.putFloat("PW" + i, pyaw[i]);
        }
    }

    @Override
    protected void lire(CompoundTag tag) {
        String t = tag.getString("T");
        type = t.equals("grille") || t.equals("debris") ? t : "porte";
        largeur = tag.contains("L") ? Mth.clamp(tag.getByte("L"), 1, 8) : 2;
        hauteur = tag.contains("H") ? Mth.clamp(tag.getByte("H"), 1, 6) : 3;
        profondeur = tag.contains("P") ? Mth.clamp(tag.getByte("P"), 1, 4) : 1;
        Direction d = Direction.from3DDataValue(tag.getByte("N"));
        normale = d.getAxis().isHorizontal() ? d : Direction.SOUTH;
        cout = tag.getInt("C");
        cadre = !tag.contains("Cadre") || tag.getBoolean("Cadre");
        pancartes = Mth.clamp(tag.getByte("NP"), 0, MAX_PANCARTES);
        for (int i = 0; i < pancartes; i++) {
            px[i] = tag.getFloat("PX" + i);
            py[i] = tag.getFloat("PY" + i);
            pz[i] = tag.getFloat("PZ" + i);
            pyaw[i] = tag.getFloat("PW" + i);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = worldPosition;
        AABB b = new AABB(p.getX() - 1.5, p.getY() - 0.5, p.getZ() - 1.5, p.getX() + etendueX() + 1.5, p.getY() + hauteur + 1,
                p.getZ() + etendueZ() + 1.5);
        for (int i = 0; i < pancartes; i++) {
            double ox = p.getX() + etendueX() / 2.0 + px[i], oy = p.getY() + py[i], oz = p.getZ() + etendueZ() / 2.0 + pz[i];
            b = b.minmax(new AABB(ox - 1, oy - 1, oz - 1, ox + 1, oy + 1.5, oz + 1));
        }
        return b;
    }
}
