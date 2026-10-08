package fr.heraliz.wonder.sliquifier.client;

import fr.heraliz.wonder.sliquifier.SliqCalculs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Glisse du joueur local sur les flaques (comme dans CoD : on file sur la gelee, aucun degat, jamais). Le deplacement
 * du joueur est calcule par son client (comme sur la glace de Minecraft) ; le serveur ne fait que decider ou sont les
 * flaques. A chaque tick, au sol dans une flaque : la vitesse horizontale est multipliee par {@code boostJoueurs}
 * (bornee a {@code vitesseMaxJoueurs}) ; le frottement du sol est presque annule, on garde son elan ; gouttes sous les
 * pieds et pas mouilles. Aucune allocation (les flaques sont lues dans les tableaux de {@link FxSliq}).
 */
public final class GlisseJoueur {
    private GlisseJoueur() {
    }

    private static int dernierPas;
    /** Banc ({@code /sliqfx glisser <ticks>}) : la touche « avancer » reste enfoncee ce nombre de ticks. */
    static int avancer;
    /** Vrai pendant que le joueur local glisse (lu par le banc). */
    static boolean glisse;

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        glisse = false;
        if (avancer > 0) {
            mc.options.keyUp.setDown(--avancer > 0);
        }
        if (p == null || !FxSliq.glisseJoueurs || FxSliq.nF == 0 || p.isSpectator() || p.getAbilities().flying || !p.onGround() || p.isInWater()
                || p.isPassenger()) return;
        float now = FxSliq.tickCourant();
        int dans = -1;
        for (int i = 0; i < FxSliq.nF; i++) {
            float r = FxSliq.rayonAffiche(i, now);
            if (r < 0.2f) continue;
            if (SliqCalculs.dansFlaque(p.getX(), p.getY(), p.getZ(), p.getBbWidth() * 0.5, FxSliq.fX[i], FxSliq.fY[i], FxSliq.fZ[i], r)) {
                dans = i;
                break;
            }
        }
        if (dans < 0) return;
        glisse = true;
        Vec3 v = p.getDeltaMovement();
        double f = SliqCalculs.facteurGlisse(v.x, v.z, FxSliq.boost, FxSliq.vitesseMax);
        if (f != 1.0) p.setDeltaMovement(v.x * f, v.y, v.z * f);
        double vh = Math.sqrt(v.x * v.x + v.z * v.z) * f;
        if (vh > 0.12) {
            FxSliq.gouttesPieds(p.getX(), p.getY(), p.getZ(), (float) (v.x * f), (float) (v.z * f), FxSliq.papFlaque(dans));
            int periode = vh > 0.45 ? 5 : 8;
            if (++dernierPas >= periode) {
                dernierPas = 0;
                FxSliq.sonPas(p.getX(), p.getY(), p.getZ(), (float) Math.min(1.0, 0.35 + vh));
            }
        }
    }
}
