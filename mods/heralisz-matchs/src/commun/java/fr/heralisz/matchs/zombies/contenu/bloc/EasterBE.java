package fr.heralisz.matchs.zombies.contenu.bloc;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatEtape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Etape d'easter egg ({@link BlocEasterEgg}) : suit l'etat donne par le jeu ({@code ZombiesDecor.easterEgg}).
 * CACHEE : radio dont le cadran luit faiblement et gresille, interrupteur leve (voyant rouge), fragment dont les veines
 * pulsent doucement ; TROUVEE : radio qui « parle » (haut-parleur et cadran vifs), interrupteur abaisse (voyant vert),
 * fragment qui s'eleve et disparait ; REFUSEE : gresillement bref ; TERMINE : tout pulse fort. {@link #trouvee()} garde
 * memoire d'une etape trouvee (le fragment reste invisible ensuite).
 */
public class EasterBE extends ZbBE {
    private EtatEtape etat = EtatEtape.CACHEE;
    private boolean trouvee;

    public EasterBE(BlockPos pos, BlockState state) {
        super(ZbRegistres.EASTER_BE.get(), pos, state);
    }

    public void etat(EtatEtape e) {
        if (e == null) e = EtatEtape.CACHEE;
        if (e == EtatEtape.CACHEE) trouvee = false;
        if (e == EtatEtape.TROUVEE) trouvee = true;
        etat = e;
        marquer();
        synchroniser();
    }

    public EtatEtape etat() {
        return etat;
    }

    public boolean trouvee() {
        return trouvee;
    }

    @Override
    protected void ecrire(CompoundTag tag) {
        tag.putString("Etat", etat.name());
        tag.putBoolean("Trouvee", trouvee);
    }

    @Override
    protected void lire(CompoundTag tag) {
        try {
            etat = EtatEtape.valueOf(tag.getString("Etat"));
        } catch (IllegalArgumentException e) {
            etat = EtatEtape.CACHEE;
        }
        trouvee = tag.getBoolean("Trouvee");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5);
    }
}
