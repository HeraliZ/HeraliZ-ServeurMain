package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.CategoriesArmes;
import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Arme dessinee a la craie sur un mur (case d'air devant le mur, {@link BlocArmeMur#FACING} = vers la salle) : contour
 * de la silhouette de l'arme ({@link CategoriesArmes}) sur 2,4 x 1,2 blocs, qui s'illumine un instant a l'achat.
 */
public class ArmeMurBE extends ZbBE {
    private String gunId = "";
    private String categorie = CategoriesArmes.FUSIL;

    public ArmeMurBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.ARME_MUR_BE.get(), pos, state);
    }

    /** Serveur : arme representee. */
    public void arme(String gunId) {
        this.gunId = gunId == null ? "" : gunId;
        this.categorie = CategoriesArmes.de(this.gunId);
        synchroniser();
    }

    /** Serveur : achat (eclat de la craie). */
    public void achetee() {
        marquer();
        synchroniser();
    }

    public String gunId() {
        return gunId;
    }

    public String categorie() {
        return categorie;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("Gun", gunId);
        tag.putString("Cat", categorie);
    }

    @Override
    protected void lire(CompoundTag tag) {
        gunId = tag.getString("Gun");
        categorie = tag.getString("Cat");
        if (categorie.isEmpty()) categorie = CategoriesArmes.FUSIL;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5, 0.5, 1.5);
    }
}
