package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Ease;
import fr.heraliz.casino.client.render.scene.FxSink;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.client.render.scene.Sprite;
import fr.heraliz.casino.game.slot.Suspense;

/**
 * Suspense des machines a sous, cote affichage : rouleau qui tourne au ralenti quand il ne manque plus qu'un symbole de
 * bonus, colonne de lumiere sur ce rouleau, battements de coeur de plus en plus rapides.
 */
final class Tease {
    private Tease() {
    }

    /**
     * Cases parcourues par un rouleau en suspens a l'instant lt : pleine vitesse jusqu'au debut du ralenti (slowAt),
     * puis "slowCells" cases au pas jusqu'a l'arret (stop).
     * @param fastCells cases parcourues avant le ralenti
     */
    static float travel(float lt, float slowAt, float stop, int fastCells, float slowCells) {
        float span = Math.max(1f, slowAt - 1);
        if (lt < slowAt) return fastCells * Suspense.cruise((lt - 1) / span);
        return fastCells + Suspense.slow(lt - slowAt, stop - slowAt, fastCells * Suspense.cruiseSpeed() / span, slowCells);
    }

    /**
     * Colonne de lumiere sur un rouleau en suspens (x0..x1, y0..y1 dans le plan de l'ecran, a la profondeur z) :
     * un voile sur toute la colonne et deux filets vifs sur ses bords, qui battent avec la tension.
     */
    static void column(Scene s, Pose p, DrawList out, float x0, float y0, float x1, float y1, float z, int rgb, float tension) {
        if (tension <= 0.01f) return;
        float beat = 0.7f + 0.3f * (float) Math.sin(s.time * (0.5f + 0.7f * tension));
        p.push().translate(0, 0, z);
        out.quad(p, Sprite.BEAM, x0 - 0.2f, y0, x1 + 0.2f, y1, rgb, 0.22f * tension * beat);
        p.pop();
        float w = 0.45f;
        for (float x : new float[]{x0, x1}) {
            p.push().translate(0, 0, z + 0.01f);
            out.quad(p, Sprite.BEAM, x - w, y0 - 0.3f, x + w, y1 + 0.3f, rgb, 0.85f * tension * beat);
            p.pop();
            // le meme filet, tete en bas : la colonne est eclairee sur toute sa hauteur
            p.push().translate(0, 0, z + 0.01f).rotZ(180);
            out.quad(p, Sprite.BEAM, -x - w, -y1 - 0.3f, -x + w, -y0 + 0.3f, rgb, 0.85f * tension * beat);
            p.pop();
        }
    }

    /** Halo qui bat sur un symbole de bonus deja tombe (il n'en manque plus qu'un). */
    static void ember(Scene s, Pose p, DrawList out, float x, float y, float z, float size, int rgb, float tension) {
        if (tension <= 0.01f) return;
        float beat = 0.6f + 0.4f * (float) Math.sin(s.time * 0.9f);
        out.billboard(p, Sprite.HALO, x, y, z, size * (0.9f + 0.2f * beat), 0, rgb, 0.5f * tension * beat);
        out.billboard(p, Sprite.FLARE, x, y, z, size * 0.7f, s.time * 3, 0xFFFFFF, 0.25f * tension * beat);
    }

    /** Assombrissement des autres rouleaux pendant le suspense (1 = normal). */
    static float dim(float tension) {
        return 1 - 0.45f * Ease.clamp01(tension);
    }

    /**
     * Sons du suspense d'un rouleau : montee au debut du ralenti, battements de coeur de plus en plus rapides.
     * @param t ticks depuis le debut du ralenti
     */
    static void sounds(FxSink fx, float t, int total, float x, float y, float z) {
        int k = (int) Math.floor(t);
        if (t < 0 || t - k >= 1 || k >= total) return;
        if (k == 0) fx.sound(Snd.TENSION, x, y, z, 0.8f, 1f);
        if (Suspense.beat(k, total)) fx.sound(Snd.HEARTBEAT, x, y, z, 1f, 0.9f + 0.5f * k / total);
    }
}
