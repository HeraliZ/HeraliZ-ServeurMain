package fr.heraliz.airstrike.strike;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Règle ferme du propriétaire (26/10) : une frappe aérienne ne fait AUCUN dégât aux bases, c'est un objet de PvP. Elle ne
 * casse aucun bloc (même hors des bases, carte comprise), ne pose aucun feu, et un joueur à l'abri dans une base (dans le
 * rayon d'une armoire de CRac, avec un toit au-dessus de la tête) n'est touché ni par les bombes, ni par le mitraillage, ni
 * par les nuages (gaz, napalm, fumée). Hors des bases, les murs protègent comme d'habitude (les explosions de Minecraft
 * tiennent compte de ce qui les sépare de la cible ; mitraillage et nuages : ligne de vue). Les pièces de CRac ignorent en
 * plus toute explosion de frappe (CRac, {@code GenericExplosionHandler}).
 */
public final class AbriFrappes {
    private AbriFrappes() {
    }

    /** Une explosion de frappe est en cours ({@link StrikeEffects}) : l'événement d'explosion qui suit vient d'elle. */
    static boolean enCours;
    /** Hauteur (blocs) au-dessus de la tête où l'on cherche un toit. */
    private static final int TOIT_MAX = 24;

    /** Explosion d'une frappe : aucun bloc touché (ni cassé, ni feu), joueurs à l'abri dans une base épargnés. */
    public static void explosion(ExplosionEvent.Detonate e) {
        if (!enCours || !(e.getLevel() instanceof ServerLevel l)) return;
        e.getAffectedBlocks().clear();
        e.getAffectedEntities().removeIf(x -> abrite(l, x));
    }

    /** Joueur à l'abri : dans une base (claim de CRac) et sous un toit. */
    public static boolean abrite(ServerLevel l, Entity e) {
        if (!(e instanceof Player)) return false;
        return sousUnToit(l, e) && dansUneBase(l, e.blockPosition());
    }

    /** Un bloc qui arrête un joueur au-dessus de sa tête (pièces de CRac comprises), à TOIT_MAX blocs au plus. */
    static boolean sousUnToit(ServerLevel l, Entity e) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int x = e.getBlockX(), z = e.getBlockZ(), y0 = (int) Math.floor(e.getEyeY()) + 1;
        for (int y = y0; y < y0 + TOIT_MAX && y < l.getMaxBuildHeight(); y++) {
            m.set(x, y, z);
            if (!l.getBlockState(m).getCollisionShape(l, m).isEmpty()) return true;
        }
        return false;
    }

    /** Vue dégagée entre deux points (blocs pleins et pièces arrêtent la vue) ; sert au mitraillage et aux nuages. */
    public static boolean vue(ServerLevel l, Vec3 de, Vec3 vers, @Nullable Entity cible) {
        return l.clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cible)).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ bases (CRac, par réflexion)
    @Nullable
    private static Method claimAt;
    private static long prochainEssai;
    private static boolean erreurDite;

    /**
     * Dans le rayon d'une armoire de CRac ({@code fr.heraliz.crac.claim.ClaimManager.getAt}). Bases illisibles (CRac en
     * erreur) : vrai, par prudence (une frappe ne doit jamais toucher l'intérieur d'une base) ; sans CRac : faux.
     */
    static boolean dansUneBase(ServerLevel l, BlockPos p) {
        if (claimAt == null) {
            long now = System.currentTimeMillis();
            if (now < prochainEssai) return true;
            prochainEssai = now + 60_000L;
            try {
                claimAt = Class.forName("fr.heraliz.crac.claim.ClaimManager").getMethod("getAt", ServerLevel.class, BlockPos.class);
            } catch (ClassNotFoundException e) {
                return false;
            } catch (ReflectiveOperationException | LinkageError e) {
                erreur(e);
                return true;
            }
        }
        try {
            return claimAt.invoke(null, l, p.immutable()) != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            erreur(e);
            claimAt = null;
            prochainEssai = System.currentTimeMillis() + 60_000L;
            return true;
        }
    }

    private static void erreur(Throwable e) {
        if (erreurDite) return;
        erreurDite = true;
        AirstrikeMod.LOGGER.error("[frappes] bases des clans (CRac) illisibles : joueurs dans une base considérés à l'abri ({})", e.toString());
    }
}
