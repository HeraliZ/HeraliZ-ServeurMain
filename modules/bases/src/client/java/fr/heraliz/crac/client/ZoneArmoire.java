package fr.heraliz.crac.client;

import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.network.S2CNearbyClaims;
import fr.heraliz.noyau.client.zones.ZoneDessin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zone des armoires à outils, plan de construction en main (CRac 1.11.0, demande du propriétaire du 18/10) : le bord de
 * chaque zone proche (celle que l'armoire protège, un carré autour d'elle), verte pour la sienne, rouge pour celle des
 * autres. Les zones viennent du serveur ({@link ClientClaimCache}).
 * <p>03/11 (choix du propriétaire après les rendus) : dessin commun des zones du noyau ({@link ZoneDessin}) : au bord, une
 * bande nette et un dégradé au sol vers l'intérieur, un mur bas très léger ; rien à l'intérieur de la base (ni ses sols ni
 * ses toits ne sont teintés). Visible à travers le décor seulement près du joueur. Posé sur le vrai sol, sans les arbres
 * ni les objets posés ({@link SolZones}), borné à la hauteur de la zone ; rien là où le terrain n'est pas chargé.
 * Construit une fois par zone et gardé dans la carte graphique : refait quand la zone change ou toutes les
 * {@link #REFAIRE_TICKS} (le sol a pu changer), une seule par image ; tout est rendu {@link #OUBLIER_TICKS} après qu'on
 * a rangé le plan.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class ZoneArmoire {
    private static final int VERT = 0x5FD08A, ROUGE = 0xE86A5A;
    /** Zone refaite au plus tard après (ticks) ; zones rendues à la carte graphique après (ticks) sans plan en main. */
    private static final int REFAIRE_TICKS = 200, OUBLIER_TICKS = 600;
    /** Distance (blocs) au-delà de laquelle un morceau de bord n'est pas dessiné. */
    private static final double PORTEE = 64;
    /** Position de travail de {@link SolZones} (fil du rendu seulement). */
    private static final BlockPos.MutableBlockPos CURSEUR = new BlockPos.MutableBlockPos();

    private static final Map<S2CNearbyClaims.ClaimEntry, Entree> ZONES = new HashMap<>();
    private static final Set<S2CNearbyClaims.ClaimEntry> VUES = new HashSet<>();
    private static long dernierUsage;
    private static Level monde;

    private record Entree(ZoneDessin dessin, long faite) {
    }

    private ZoneArmoire() {
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        Level lvl = mc.level;
        if (lvl != monde) {
            oublier();
            monde = lvl;
        }
        if (p == null || lvl == null) {
            return;
        }
        long t = lvl.getGameTime();
        if (!(p.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            if (!ZONES.isEmpty() && (t - dernierUsage > OUBLIER_TICKS || t < dernierUsage)) {
                oublier();
            }
            return;
        }
        dernierUsage = t;
        List<S2CNearbyClaims.ClaimEntry> claims = ClientClaimCache.get();
        VUES.clear();
        VUES.addAll(claims);
        for (Iterator<Map.Entry<S2CNearbyClaims.ClaimEntry, Entree>> it = ZONES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<S2CNearbyClaims.ClaimEntry, Entree> z = it.next();
            if (!VUES.contains(z.getKey())) {
                z.getValue().dessin().close();
                it.remove();
            }
        }
        boolean refaite = false;
        Vec3 cam = e.getCamera().getPosition();
        for (S2CNearbyClaims.ClaimEntry c : claims) {
            Entree z = ZONES.get(c);
            if (!refaite && (z == null || t - z.faite() >= REFAIRE_TICKS || t < z.faite())) {
                if (z != null) {
                    z.dessin().close();
                }
                z = new Entree(construire(lvl, c), t);
                ZONES.put(c, z);
                refaite = true;   // une seule par image
            }
            if (z == null) {
                continue;
            }
            int rgb = c.ownedByMe() ? VERT : ROUGE;
            z.dessin().dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam,
                (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, 1F, PORTEE);
        }
    }

    private static void oublier() {
        for (Entree z : ZONES.values()) {
            z.dessin().close();
        }
        ZONES.clear();
    }

    /** Les quatre côtés du carré, bloc par bloc, chaque point sur le vrai sol ; pas de teinte à l'intérieur. */
    private static ZoneDessin construire(Level lvl, S2CNearbyClaims.ClaimEntry c) {
        BlockPos a = c.center();
        int ymin = a.getY() - c.yRadius(), ymax = a.getY() + c.yRadius() + 1;
        int x0 = a.getX() - c.radius(), x1 = a.getX() + c.radius() + 1;
        int z0 = a.getZ() - c.radius(), z1 = a.getZ() + c.radius() + 1;
        ZoneDessin.Construction d = ZoneDessin.construction(a.getX(), a.getY(), a.getZ());
        // côtés parcourus dans le même sens ; (dx, dz) : pas le long du côté ; l'intérieur est à (-dz, dx)
        cote(d, lvl, x0, z0, 1, 0, x1 - x0, ymin, ymax);
        cote(d, lvl, x1, z0, 0, 1, z1 - z0, ymin, ymax);
        cote(d, lvl, x1, z1, -1, 0, x1 - x0, ymin, ymax);
        cote(d, lvl, x0, z1, 0, -1, z1 - z0, ymin, ymax);
        return d.finir();
    }

    private static void cote(ZoneDessin.Construction d, Level lvl, int sx, int sz, int dx, int dz, int n, int ymin, int ymax) {
        double yPrec = sol(lvl, sx, sz, ymin, ymax);
        for (int i = 1; i <= n; i++) {
            int x = sx + dx * i, z = sz + dz * i;
            double y = sol(lvl, x, z, ymin, ymax);
            if (!Double.isNaN(y) && !Double.isNaN(yPrec)) {
                d.bord(x - dx, yPrec, z - dz, x, y, z, -dz, dx);
            }
            yPrec = y;
        }
    }

    /**
     * Sol le plus haut des 4 colonnes [x-1..x] x [z-1..z] qui touchent le point (x, z) de la grille (vrai sol, sans arbres ni
     * objets posés : {@link SolZones}), borné à la zone ; NaN si le terrain n'est pas chargé.
     */
    private static double sol(Level lvl, int xb, int zb, int ymin, int ymax) {
        int best = SolZones.INCONNU;
        for (int x = xb - 1; x <= xb; x++) {
            for (int z = zb - 1; z <= zb; z++) {
                best = Math.max(best, SolZones.dessus(lvl, x, z, CURSEUR));
            }
        }
        if (best == SolZones.INCONNU) {
            return Double.NaN;
        }
        return Math.max(ymin, Math.min(best, ymax - 1));
    }
}
