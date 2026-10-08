package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Levier du courant : plaque murale a rayures, grosse poignee rouge qui bascule de haut en bas (avec rebond) quand le
 * courant est retabli, voyant rouge qui passe au vert. L'etat {@link BlocLevier#ACTIF} du bloc eclaire un peu la piece.
 */
public class LevierBE extends ZbBE {
    private boolean actif;

    public LevierBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.LEVIER_BE.get(), pos, state);
    }

    /** Serveur : courant retabli ou coupe (anime). */
    public void actif(boolean a) {
        if (a == actif) return;
        actif = a;
        marquer();
        if (level != null && getBlockState().hasProperty(BlocLevier.ACTIF)) {
            level.setBlock(worldPosition, getBlockState().setValue(BlocLevier.ACTIF, a), 2);
        }
        synchroniser();
    }

    /** Serveur : etat initial sans animation. */
    public void reinitialiser() {
        actif = false;
        depuis = -100000L;
        if (level != null && getBlockState().hasProperty(BlocLevier.ACTIF)) {
            level.setBlock(worldPosition, getBlockState().setValue(BlocLevier.ACTIF, false), 2);
        }
        synchroniser();
    }

    public boolean actif() {
        return actif;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putBoolean("Actif", actif);
    }

    @Override
    protected void lire(CompoundTag tag) {
        actif = tag.getBoolean("Actif");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }
}
