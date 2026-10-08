package fr.heraliz.wonder.serveur;

import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.Calculs;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.WonderConfig;
import fr.heraliz.wonder.net.Impact;
import fr.heraliz.wonder.net.Reseau;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Eclaboussure verte du Pistolet a rayons (BO1/BO2) : petit rayon, degats de zone degressifs du centre au bord, aucun
 * bloc casse, rien a travers les murs. La cible touchee de plein fouet a deja pris la balle TaCZ : elle est exclue.
 * Le tireur peut se blesser a bout portant (reglage {@code autoDegats}, en partie de zombies seulement par defaut).
 * Pack-a-Punch (Porter's X2) : rayon plus grand. Un seul appel par impact, une seule recherche d'entites bornee.
 */
public final class Eclaboussure {
    private Eclaboussure() {
    }

    public static double rayon(int pap) {
        return Calculs.rayonEclaboussure(pap, WonderConfig.d(WonderConfig.RAYON_RAYON), WonderConfig.d(WonderConfig.RAYON_RAYON_PAP));
    }

    /**
     * Eclaboussure au point (x, y, z). {@code exclue} = creature touchee de plein fouet ; {@code face} = face du bloc
     * touche (null sur une creature).
     */
    public static void eclater(ServerLevel niveau, @Nullable LivingEntity tireur, double x, double y, double z, int pap,
                               @Nullable Entity exclue, @Nullable Direction face) {
        long t0 = System.nanoTime();
        double r = rayon(pap);
        boolean zombies = tireur != null && Regles.enZombies(tireur);
        Vec3 centre = new Vec3(x, y, z);
        DamageSource src = Degats.source(niveau, Degats.ECLABOUSSURE, tireur);
        float max = (float) WonderConfig.d(WonderConfig.RAYON_DEGATS), bord = (float) WonderConfig.d(WonderConfig.RAYON_DEGATS_BORD);
        AABB zone = new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
        List<LivingEntity> l = niveau.getEntitiesOfClass(LivingEntity.class, zone, e -> e != exclue && Regles.estCible(e, tireur, zombies));
        int touchees = 0;
        for (int i = 0; i < l.size(); i++) {
            LivingEntity e = l.get(i);
            double d = distanceBoite(e.getBoundingBox(), x, y, z);
            if (d > r || !aDecouvert(niveau, centre, e, tireur)) continue;
            if (Degats.blesser(e, src, Calculs.degatsEclaboussure(d, r, max, bord), true)) touchees++;
        }
        // a bout portant, le tireur prend aussi (CoD ; PhD Flopper n'existe pas ici)
        if (tireur instanceof ServerPlayer p && autoDegats(zombies) && !p.isCreative()) {
            double d = distanceBoite(p.getBoundingBox(), x, y, z);
            if (d < r) Degats.blesser(p, src, Calculs.autoDegats(d, r, WonderConfig.d(WonderConfig.RAYON_AUTO_MAX)), true);
        }
        Reseau.autour(niveau, x, y, z, new Impact(Armes.RAYON, (byte) pap, (float) x, (float) y, (float) z,
                (byte) (face == null ? -1 : face.get3DDataValue()), (byte) Math.round(r * 10)));
        Perf.eclaboussure(System.nanoTime() - t0, touchees);
    }

    private static boolean autoDegats(boolean zombies) {
        return switch (WonderConfig.lire(WonderConfig.RAYON_AUTO)) {
            case TOUJOURS -> true;
            case JAMAIS -> false;
            case ZOMBIES -> zombies;
        };
    }

    /** Distance du point a la boite (0 dedans). */
    static double distanceBoite(AABB b, double x, double y, double z) {
        double dx = Math.max(Math.max(b.minX - x, 0), x - b.maxX);
        double dy = Math.max(Math.max(b.minY - y, 0), y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - z, 0), z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Pas de bloc plein entre le centre de l'eclaboussure et le milieu de la creature. */
    static boolean aDecouvert(ServerLevel niveau, Vec3 depuis, Entity e, @Nullable Entity contexte) {
        Vec3 vers = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        if (depuis.distanceToSqr(vers) < 0.36) return true;
        Entity ctx = contexte != null ? contexte : e;
        return niveau.clip(new ClipContext(depuis, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx)).getType() == HitResult.Type.MISS;
    }
}
