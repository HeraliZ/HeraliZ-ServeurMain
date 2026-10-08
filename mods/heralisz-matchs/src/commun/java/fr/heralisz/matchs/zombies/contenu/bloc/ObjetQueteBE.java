package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Objet de quete de The Giant ({@link BlocObjetQuete}) : {@link #CACHE} (rien, aucune forme), {@link #VISIBLE} (au repos,
 * petit eclat qui attire l'oeil), {@link #ACTIF} (il faut tirer dessus ou le prendre : il tourne en l'air et brille),
 * {@link #PRIS} (effet de disparition 1 s, puis rien). Les etats sont donnes par le jeu ; le rendu ({@code ObjetsRendu})
 * et les particules (client) suivent.
 */
public class ObjetQueteBE extends ZbBE {
    public static final int CACHE = 0, VISIBLE = 1, ACTIF = 2, PRIS = 3;
    private int etat = CACHE;
    /** Identifiant de l'arme montree (Annihilator du fourneau), vide sinon. */
    private String donnee = "";
    /**
     * Decalage du point de pose (blocs, repere du monde) depuis le centre bas de la case : objet pose sur l'ancre de son
     * decor voisin (singe sur la sole de l'incinerateur, nounours sur l'eau, arme sur la tablette du fourneau, carte sur le
     * sous-main), calcule par le decor du jeu a la pose. 0 = centre de la case.
     */
    private float ox, oy, oz;
    /** Client : pile de l'arme a montrer (construite une fois). */
    @javax.annotation.Nullable
    public net.minecraft.world.item.ItemStack pileClient;

    public ObjetQueteBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.OBJET_QUETE_BE.get(), pos, state);
    }

    public int etat() {
        return etat;
    }

    public int genre() {
        return getBlockState().hasProperty(BlocObjetQuete.GENRE) ? getBlockState().getValue(BlocObjetQuete.GENRE) : 0;
    }

    /** Vrai si l'objet a une forme (on peut le viser, lui tirer dessus). */
    public boolean visible() {
        return etat == VISIBLE || etat == ACTIF;
    }

    public void etat(int e, String d) {
        String dd = d == null ? "" : d;
        if (e == etat && dd.equals(donnee)) return;
        etat = e;
        donnee = dd;
        marquer();
        synchroniser();
    }

    public String donnee() {
        return donnee;
    }

    public float ox() {
        return ox;
    }

    public float oy() {
        return oy;
    }

    public float oz() {
        return oz;
    }

    /** Decalage du point de pose (voir {@link #ox}) ; envoye aux clients. */
    public void decalage(float x, float y, float z) {
        if (x == ox && y == oy && z == oz) return;
        ox = x;
        oy = y;
        oz = z;
        synchroniser();
    }

    /** Client : petit eclat dore de temps en temps (fusible, arme du fourneau), lueur verte (objets actifs). */
    public static void tickClient(Level level, BlockPos pos, BlockState state, ObjetQueteBE be) {
        if (!be.visible() || (level.getGameTime() + pos.asLong()) % 10 != 0) return;
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, false) == null) return;
        RandomSource r = level.random;
        boolean actif = be.etat == ACTIF;
        if (actif || r.nextInt(3) == 0) {
            double rr = actif ? 0.45 : 0.18;
            level.addParticle(ZbRegistres.LUEUR.get(), pos.getX() + 0.5 + (r.nextDouble() - 0.5) * rr, pos.getY() + 0.3 + r.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * rr, actif ? 0.45 : 1.0, actif ? 1.0 : 0.85, actif ? 0.6 : 0.45);
        }
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putInt("Etat", etat);
        if (!donnee.isEmpty()) tag.putString("Donnee", donnee);
        if (ox != 0 || oy != 0 || oz != 0) {
            tag.putFloat("Ox", ox);
            tag.putFloat("Oy", oy);
            tag.putFloat("Oz", oz);
        }
    }

    @Override
    protected void lire(CompoundTag tag) {
        etat = tag.getInt("Etat");
        ox = tag.getFloat("Ox");
        oy = tag.getFloat("Oy");
        oz = tag.getFloat("Oz");
        String d = tag.getString("Donnee");
        if (!d.equals(donnee)) pileClient = null;
        donnee = d;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).move(ox, oy, oz).inflate(0.6, 0.2, 0.6).expandTowards(0, 1.2, 0);
    }
}
