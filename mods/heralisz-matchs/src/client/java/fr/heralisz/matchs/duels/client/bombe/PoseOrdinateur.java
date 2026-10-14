package fr.heralisz.matchs.duels.client.bombe;

import fr.heralisz.matchs.duels.bombe.OrdinateurDuel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Où dessiner l'ordinateur à cette image. Posé : à sa position, tourné de son lacet. Piraté : dans les mains du
 * défenseur, devant lui à hauteur des mains (accroupi), clavier vers lui et légèrement relevé ; pour le défenseur
 * lui-même en vue à la 1re personne, devant ses yeux, plus bas (l'écran de piratage occupe le bas de la vue). Sert au
 * rendu (modèle, écran en direct, câble) et aux poses des bras (Player Animator).
 */
final class PoseOrdinateur {
    private PoseOrdinateur() {
    }

    /** Position (monde) de l'origine du modèle (centre du dessous du boîtier), lacet et inclinaison (degrés). */
    double x, y, z;
    float lacet, inclinaison;
    /** Vrai si l'ordinateur est dans les mains d'un joueur (piratage). */
    boolean tenu;
    /** Hauteur du sol sous l'ordinateur ou sous le pirate (câble). */
    double sol;

    static PoseOrdinateur nouvelle() {
        return new PoseOrdinateur();
    }

    /** Remplit cette pose pour l'ordinateur à cette image. */
    PoseOrdinateur calculer(OrdinateurDuel o, float partial) {
        double ex = Mth.lerp(partial, o.xo, o.getX()), ey = Mth.lerp(partial, o.yo, o.getY()), ez = Mth.lerp(partial, o.zo, o.getZ());
        Entity u = o.etat() == OrdinateurDuel.PIRATAGE ? o.level().getEntity(o.utilisateurId()) : null;
        if (u == null) {
            this.tenu = false;
            this.x = ex;
            this.y = ey;
            this.z = ez;
            this.lacet = o.getYRot();
            this.inclinaison = 0f;
            this.sol = ey;
            return this;
        }
        this.tenu = true;
        Minecraft mc = Minecraft.getInstance();
        Vec3 pieds = u.getPosition(partial);
        this.sol = pieds.y;
        boolean premiere = u == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (premiere) {
            Camera cam = mc.gameRenderer.getMainCamera();
            float yaw = u.getViewYRot(partial);
            double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
            Vec3 oeil = cam.getPosition();
            this.x = oeil.x + fx * 0.5;
            this.y = oeil.y - 0.52;
            this.z = oeil.z + fz * 0.5;
            this.lacet = yaw + 180f;
            this.inclinaison = -32f;
        } else {
            float corps = u instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : u.getViewYRot(partial);
            double fx = -Mth.sin(corps * Mth.DEG_TO_RAD), fz = Mth.cos(corps * Mth.DEG_TO_RAD);
            this.x = pieds.x + fx * 0.40;
            this.y = pieds.y + (u.isCrouching() ? 0.62 : 0.78);
            this.z = pieds.z + fz * 0.40;
            this.lacet = corps + 180f;
            this.inclinaison = -20f;
        }
        return this;
    }

    /**
     * Point du modèle (pixels de modèle, espace de Blockbench) dans le monde, pour cette pose : mêmes transformations que
     * le rendu GeckoLib (rotation de 180 - lacet autour de Y, inclinaison autour de X, 0,01 bloc plus haut).
     */
    Vec3 point(double px, double py, double pz) {
        double vx = px / 16.0, vy = py / 16.0 + 0.01, vz = pz / 16.0;
        double phi = Math.toRadians(this.inclinaison);
        double y1 = vy * Math.cos(phi) - vz * Math.sin(phi), z1 = vy * Math.sin(phi) + vz * Math.cos(phi);
        double t = Math.toRadians(180.0 - this.lacet);
        double x2 = vx * Math.cos(t) + z1 * Math.sin(t), z2 = -vx * Math.sin(t) + z1 * Math.cos(t);
        return new Vec3(this.x + x2, this.y + y1, this.z + z2);
    }
}
