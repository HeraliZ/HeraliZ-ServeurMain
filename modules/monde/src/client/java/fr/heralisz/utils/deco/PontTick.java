package fr.heralisz.utils.deco;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Tick client d'un pont de corde (repris tel quel de PontBlockEntity.tickClient) : relit le pont de temps en temps, mesure
 * le balancement donné par les marcheurs et la poussée du vent de la faille (Vent, côté client). Branché par
 * client.PartieClient sur {@link PontBlockEntity#TICK_CLIENT}.
 */
public final class PontTick {
    private PontTick() {}

    public static void tick(PontBlockEntity be) {
        if (be.getLevel() != null) {
            tickClient(be, be.getLevel());
        }
    }

    static void tickClient(PontBlockEntity be, Level l) {
        if (--be.attente <= 0) {                                            // relu de temps en temps (planchers changés)
            be.attente = be.lu ? 400 : 20;
            be.lire(l);
        }
        if (!be.lu) {
            return;
        }
        // vent de la faille au milieu du pont : brise + rafale, projetée sur la droite du pont
        Direction d = be.sens(), r = d.getClockWise();
        double mx = be.getBlockPos().getX() + 0.5 + d.getStepX() * be.n * 0.5, mz = be.getBlockPos().getZ() + 0.5 + d.getStepZ() * be.n * 0.5;
        double t = l.getGameTime();
        float g = Vent.rafale(mx, be.getBlockPos().getY(), mz, t);
        be.ventPrec = be.ventLat;
        be.ventLat += (g * Vent.SENS * r.getStepZ() - be.ventLat) * 0.25f;
        be.brise = Vent.dansZone(mx, be.getBlockPos().getY(), mz) ? Vent.brise(t) : 0f;
        be.energiePrec = be.energie;
        be.energie *= 0.965f;
        be.nMarche = 0;
        for (Player pl : l.players()) {
            float s = be.surLePont(pl.getX(), pl.getY(), pl.getZ());
            if (Float.isNaN(s)) {
                continue;
            }
            double v = Math.hypot(pl.getX() - pl.xo, pl.getZ() - pl.zo);
            double chute = pl.yo - pl.getY();
            be.energie = Math.min(1.2f, be.energie + (float) (v * 0.22 + (chute > 0.1 ? chute * 0.6 : 0)));
            if (be.nMarche < be.marche.length) {
                be.marche[be.nMarche++] = s;
            }
        }
    }
}
