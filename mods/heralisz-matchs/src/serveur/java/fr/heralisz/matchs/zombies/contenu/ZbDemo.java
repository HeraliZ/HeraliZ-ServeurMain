package fr.heralisz.matchs.zombies.contenu;

import fr.heralisz.matchs.zombies.contenu.bloc.FenetreBE;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Demonstration de banc ({@code /zbdev fenetre demo}) : un zombie colle a une fenetre arrache les 6 planches une a une
 * (rythme du jeu, {@link ZombiesApi#ARRACHAGE_TICKS}), puis enjambe la fenetre vide avec le meme deplacement interpole
 * que le jeu ({@link ZombiesApi#ENJAMBEMENT_TICKS}). Aucune regle de partie : seulement les evenements du decor, pour
 * regarder et capturer les animations sans lancer de partie. Fil serveur.
 */
public final class ZbDemo {
    private ZbDemo() {
    }

    private static final List<Demo> DEMOS = new ArrayList<>();

    private static final class Demo {
        final ServerLevel l;
        final ZombieLabo z;
        final FenetreBE f;
        final Vec3 dehors, dedans, centre;
        final Direction ext;
        int t, planches = FenetreBE.MAX, phase;

        Demo(ServerLevel l, ZombieLabo z, FenetreBE f, Vec3 dehors, Vec3 dedans, Vec3 centre, Direction ext) {
            this.l = l;
            this.z = z;
            this.f = f;
            this.dehors = dehors;
            this.dedans = dedans;
            this.centre = centre;
            this.ext = ext;
        }
    }

    /** Lance la demonstration (le zombie doit etre deja ajoute au monde, sans IA). */
    public static void fenetre(ServerLevel l, ZombieLabo z, FenetreBE f, Vec3 dehors, Vec3 dedans, Vec3 centre, Direction ext) {
        f.planches(FenetreBE.MAX, ZombiesDecor.EvenementFenetre.INSTALLEE);
        DEMOS.add(new Demo(l, z, f, dehors, dedans, centre, ext));
    }

    public static void vider() {
        DEMOS.clear();
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || DEMOS.isEmpty()) return;
        Iterator<Demo> it = DEMOS.iterator();
        while (it.hasNext()) {
            Demo d = it.next();
            if (d.z.isRemoved() || d.f.isRemoved()) {
                it.remove();
                continue;
            }
            d.t++;
            float lacet = d.ext.getOpposite().toYRot();
            if (d.phase == 0) {
                d.z.setPos(d.dehors.x, d.dehors.y, d.dehors.z);
                d.z.setYRot(lacet);
                d.z.setYHeadRot(lacet);
                d.z.setYBodyRot(lacet);
                if (d.t % ZombiesApi.ARRACHAGE_TICKS == 0) {
                    d.planches--;
                    d.z.swing(InteractionHand.MAIN_HAND);
                    d.z.animerArrachage();
                    d.f.planches(d.planches, ZombiesDecor.EvenementFenetre.ARRACHEE);
                    d.l.playSound(null, d.centre.x, d.centre.y, d.centre.z, ZbSons.PLANCHE_ARRACHE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                    d.l.sendParticles(ZbRegistres.COPEAU.get(), d.centre.x, d.centre.y, d.centre.z, 14, 0.5, 0.4, 0.5, 0.12);
                    if (d.planches <= 0) {
                        d.phase = 1;
                        d.t = 0;
                        d.z.noPhysics = true;
                        d.z.setNoGravity(true);
                        d.z.animerEnjambement();
                        d.l.playSound(null, d.centre.x, d.centre.y, d.centre.z, ZbSons.ZOMBIE_ENJAMBE.get(), SoundSource.HOSTILE, 0.9F, 1.0F);
                    }
                }
            } else if (d.phase == 1) {
                double t = Math.min(1.0, d.t / (double) ZombiesApi.ENJAMBEMENT_TICKS);
                double h = lisse(t);
                double v = lisse(Math.max(0, Math.min(1, (t - 0.35) / 0.65)));
                double y = d.dehors.y + (d.dedans.y - d.dehors.y) * v + 0.3 * Math.sin(Math.PI * Math.min(1.0, t / 0.75));
                d.z.setPos(d.dehors.x + (d.dedans.x - d.dehors.x) * h, y, d.dehors.z + (d.dedans.z - d.dehors.z) * h);
                d.z.setYRot(lacet);
                d.z.setYBodyRot(lacet);
                d.z.setYHeadRot(lacet);
                if (t >= 1.0) {
                    d.z.noPhysics = false;
                    d.z.setNoGravity(false);
                    d.z.setPos(d.dedans.x, d.dedans.y, d.dedans.z);
                    it.remove();
                }
            }
        }
    }

    private static double lisse(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
