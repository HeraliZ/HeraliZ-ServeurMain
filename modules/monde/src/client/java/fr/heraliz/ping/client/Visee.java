package fr.heraliz.ping.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Ce que le joueur vise, depuis ses yeux (comme la verification du serveur) : bloc par sa forme de collision (l'herbe
 * haute et les fleurs ne gênent pas, liquides traverses), ou entite (boite elargie de 0,3 bloc pour viser facilement
 * au loin ; les objets au sol comptent). Appele seulement quand on pingue (quelques objets crees, aucun par image).
 */
public final class Visee {
    private Visee() {
    }

    /** Cible : entite (id >= 0) ou bloc, et le point vise. */
    public record Cible(int entite, long bloc, double x, double y, double z) {
    }

    @Nullable
    public static Cible viser(Minecraft mc, double portee, float pt) {
        Player p = mc.player;
        if (p == null || mc.level == null) return null;
        Vec3 oeil = p.getEyePosition(pt);
        Vec3 regard = p.getViewVector(pt);
        Vec3 fin = oeil.add(regard.scale(portee));
        BlockHitResult bloc = mc.level.clip(new ClipContext(oeil, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dMax = bloc.getType() == HitResult.Type.MISS ? portee : oeil.distanceTo(bloc.getLocation());
        Vec3 finE = oeil.add(regard.scale(dMax));
        Entity meilleure = null;
        double meilleureD = dMax * dMax;
        AABB zone = p.getBoundingBox().expandTowards(regard.scale(dMax)).inflate(1.0);
        for (Entity e : mc.level.getEntities(p, zone, e -> !e.isSpectator() && (e.isPickable() || e instanceof ItemEntity) && !e.isInvisibleTo(p)
                && !p.isPassengerOfSameVehicle(e) && e != p.getVehicle())) {
            AABB b = e.getBoundingBox().inflate(0.3 + e.getPickRadius());
            Optional<Vec3> touche = b.clip(oeil, finE);
            double d = b.contains(oeil) ? 0 : touche.map(v -> v.distanceToSqr(oeil)).orElse(Double.MAX_VALUE);
            if (d < meilleureD) {
                meilleureD = d;
                meilleure = e;
            }
        }
        if (meilleure != null) {
            double x = meilleure.getX(), y = meilleure.getY() + meilleure.getBbHeight(), z = meilleure.getZ();
            return new Cible(meilleure.getId(), BlockPos.containing(x, y, z).asLong(), x, y, z);
        }
        if (bloc.getType() == HitResult.Type.MISS) return null;
        Vec3 h = bloc.getLocation();
        return new Cible(-1, bloc.getBlockPos().asLong(), h.x, h.y, h.z);
    }
}
