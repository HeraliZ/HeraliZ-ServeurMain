package fr.heralisz.utils.zombies.client;

import fr.heralisz.utils.zombies.AttaquesZombies;
import fr.heralisz.utils.zombies.AttaquesZombies.Genre;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gestes des zombies de Mutants & Zombies chez le joueur (voir {@link AttaquesZombies}) : le serveur annonce le debut
 * d'une frappe ou d'un bond (evenement d'entite, {@code mixin/ZombieEvenementMixin}) ; les mixins de leurs modeles
 * appellent ces methodes a la fin de leur setupAnim, qui ne fait que balancer les bras et les jambes en marchant.
 * Leurs pieces de premier niveau ont toutes une pose de depart a 0 : les rotations posees ici sont absolues (xRot des
 * bras et des jambes : ajoutees a la marche, que le modele recalcule a chaque image).
 */
public final class GestesZombies {
    private GestesZombies() {
    }

    /** {debut de la frappe, debut du bond} en tickCount de l'entite ; -1000 = aucun. */
    private static final Map<Entity, float[]> DEBUTS = new WeakHashMap<>();

    public static void evenement(Entity e, byte id) {
        float[] d = DEBUTS.computeIfAbsent(e, k -> new float[]{-1000, -1000});
        if (id == AttaquesZombies.EVT_FRAPPE) d[0] = e.tickCount;
        else d[1] = e.tickCount;
    }

    private static float depuis(Entity e, int i, float age) {
        float[] d = DEBUTS.get(e);
        return d == null ? 1e6f : age - d[i];
    }

    /** 0 -> 1 doux. */
    private static float doux(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Courbe d'un geste : 0 au repos, monte jusqu'a {@code haut} pendant l'armement, puis l'impact (de haut a
     * {@code bas} en 3 ticks), puis retour au repos. Rend {montee, impact} entre 0 et 1, ou null hors du geste.
     */
    private static float[] geste(float t, int armement, float retour) {
        if (t < 0 || t > armement + 3 + retour) return null;
        if (t <= armement) return new float[]{doux(t / armement), 0};
        if (t <= armement + 3) return new float[]{1, doux((t - armement) / 3f)};
        return new float[]{1 - doux((t - armement - 3) / retour), 1 - doux((t - armement - 3) / retour)};
    }


    /** Le modele ne pose jamais yRot / zRot des bras (pose de depart 0) : remis a 0 a chaque image, geste ou non. */
    private static void repos(ModelPart brasD, ModelPart brasG) {
        brasD.yRot = brasD.zRot = 0;
        brasG.yRot = brasG.zRot = 0;
    }

    // ------------------------------------------------------------------ Brute : deux bras leves puis abattus
    public static void brute(Entity e, float age, ModelPart tete, ModelPart brasD, ModelPart brasG) {
        repos(brasD, brasG);
        float[] g = geste(depuis(e, 0, age), Genre.BRUTE.armement, 8);
        if (g == null) return;
        float m = g[0], i = g[1];
        float x = -2.8f + 2.5f * i;                   // au-dessus de la tete -> devant, vers le sol
        brasD.xRot = Mth.lerp(m, brasD.xRot, x);
        brasG.xRot = Mth.lerp(m, brasG.xRot, x);
        brasD.zRot = m * (0.25f - 0.35f * i);
        brasG.zRot = -m * (0.25f - 0.35f * i);
        tete.xRot += m * (-0.25f + 0.5f * i);
    }

    // ------------------------------------------------------------------ Blister : revers du bras droit
    public static void blister(Entity e, float age, ModelPart tete, ModelPart brasD, ModelPart brasG) {
        repos(brasD, brasG);
        float[] g = geste(depuis(e, 0, age), Genre.BLISTER.armement, 6);
        if (g == null) return;
        float m = g[0], i = g[1];
        brasD.xRot = Mth.lerp(m, brasD.xRot, -2.3f + 1.8f * i);
        brasD.yRot = m * (-0.35f + 1.0f * i);
        brasD.zRot = m * (0.55f - 0.75f * i);
        brasG.xRot = Mth.lerp(m, brasG.xRot, -0.5f);
        tete.yRot += m * (-0.3f + 0.6f * i);
    }

    // ------------------------------------------------------------------ Crawler : morsure + griffes, et bond
    public static void crawler(Entity e, float age, ModelPart tete, ModelPart brasD, ModelPart brasG, ModelPart jambeD, ModelPart jambeG) {
        repos(brasD, brasG);
        float tb = depuis(e, 1, age);
        int prep = AttaquesZombies.BOND_PREPARATION;
        if (tb >= 0 && tb < 50) {
            float accroupi = tb < prep ? doux(tb / prep) : 0;
            float vol = !e.onGround() && tb >= prep ? doux((tb - prep) / 3f) : 0;
            if (accroupi > 0 || vol > 0) {
                brasD.xRot = Mth.lerp(accroupi, brasD.xRot, 0.45f);
                brasG.xRot = Mth.lerp(accroupi, brasG.xRot, 0.45f);
                jambeD.xRot = Mth.lerp(accroupi, jambeD.xRot, -0.35f);
                jambeG.xRot = Mth.lerp(accroupi, jambeG.xRot, -0.35f);
                tete.xRot += accroupi * 0.25f;
                // en vol : bras lances vers la cible, griffes ouvertes, jambes en arriere, gueule levee
                brasD.xRot = Mth.lerp(vol, brasD.xRot, -1.45f);
                brasG.xRot = Mth.lerp(vol, brasG.xRot, -1.45f);
                brasD.zRot = vol * 0.35f;
                brasG.zRot = -vol * 0.35f;
                jambeD.xRot = Mth.lerp(vol, jambeD.xRot, 0.8f);
                jambeG.xRot = Mth.lerp(vol, jambeG.xRot, 0.8f);
                tete.xRot -= vol * 0.4f;
                return;
            }
        }
        float[] g = geste(depuis(e, 0, age), Genre.CRAWLER.armement, 5);
        if (g == null) return;
        float m = g[0], i = g[1];
        tete.xRot += m * (-0.5f + 0.8f * i);           // releve la tete puis mord
        brasD.xRot = Mth.lerp(m, brasD.xRot, -1.1f + 1.4f * i);
        brasG.xRot = Mth.lerp(m, brasG.xRot, -0.6f + 0.9f * i);
        brasD.zRot = m * 0.2f;
        brasG.zRot = -m * 0.2f;
    }
}
