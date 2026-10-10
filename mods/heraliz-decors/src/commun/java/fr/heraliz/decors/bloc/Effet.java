package fr.heraliz.decors.bloc;

import fr.heraliz.decors.Particules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Effets d'ambiance des décors, joués côté client par animateTick : le jeu ne l'appelle que pour quelques blocs pris au
 * hasard autour du joueur à chaque image (rien au loin, rien sur le serveur). Chaque effet garde un débit bas et des
 * particules longues plutôt que nombreuses.
 */
public enum Effet {
    AUCUN, LAMPE, CRISTAL, POUSSIERE;

    public void jouer(BlockState s, Level l, BlockPos p, RandomSource r) {
        double x = p.getX() + 0.5, y = p.getY(), z = p.getZ() + 0.5;
        switch (this) {
            case LAMPE -> {
                if (r.nextInt(3) == 0) {
                    double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 1.6;
                    l.addParticle(Particules.POUSSIERE.get(), x + Math.cos(a) * d, y - 0.4 - r.nextDouble() * 2.5, z + Math.sin(a) * d,
                            (r.nextDouble() - 0.5) * 0.003, -0.001 - r.nextDouble() * 0.002, (r.nextDouble() - 0.5) * 0.003);
                }
            }
            case CRISTAL -> {
                if (r.nextInt(4) == 0) {
                    l.addParticle(Particules.ECLAT.get(), x + (r.nextDouble() - 0.5) * 0.8, y + r.nextDouble() * 0.9,
                            z + (r.nextDouble() - 0.5) * 0.8, 0, 0.004, 0);
                }
            }
            case POUSSIERE -> {
                if (r.nextInt(2) == 0) {
                    l.addParticle(Particules.POUSSIERE.get(), x + (r.nextDouble() - 0.5) * 1.2, y + r.nextDouble(),
                            z + (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 0.004, -0.002 - r.nextDouble() * 0.004,
                            (r.nextDouble() - 0.5) * 0.004);
                }
            }
            default -> {
            }
        }
    }
}
