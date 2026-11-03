package fr.heralisz.clans.territory.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.territory.DrapeauBlockEntity;
import fr.heraliz.noyau.client.zones.ZoneDessin;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cercles de capture des drapeaux de territoire (03/11, choix du propriétaire : même dessin que les autres zones) : dessin
 * commun des zones du noyau ({@link ZoneDessin}) à la place du mur lumineux de 1,6 bloc. Sol très léger dans le cercle,
 * au bord une bande nette, un dégradé au sol et un mur bas très léger ; rien qui gêne la vue de ceux qui se battent dedans.
 * Couleur selon l'état de la zone : capture ou contestation orange, tenue par son clan vert, par un autre rouge, libre
 * blanc. Visible jusqu'à {@link #PORTEE} blocs, à travers le décor seulement de près (réglage du noyau).
 * <p>Posé sur le vrai sol (sans arbres ni objets posés), borné à +/- hauteurCapture du drapeau. Construit une fois par
 * drapeau, refait quand son cercle change ou au plus tard toutes les {@link #REFAIRE_TICKS} (le sol a pu changer), un seul
 * par image. Les drapeaux sont suivis dès que leur bloc est chargé chez le joueur
 * ({@link DrapeauBlockEntity#CHARGEMENT_CLIENT}), même mât caché derrière un mur ; oubliés quand leur bloc part.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class ZonesDrapeaux {
    private static final float[] BLANC = rgb(0xE6E9EE), VERT = rgb(Ui.OK), ROUGE = rgb(Ui.DANGER), ORANGE = rgb(0xFF9A3C);
    /** Opacité du sol dans le cercle. */
    private static final float FOND = 0.12F;
    /** Distance (blocs) au-delà de laquelle un morceau du cercle n'est pas dessiné (avant : mur visible à 48 blocs). */
    private static final double PORTEE = 48;
    private static final int REFAIRE_TICKS = 200;

    private static final Map<DrapeauBlockEntity, Entree> DRAPEAUX = new IdentityHashMap<>();

    private static final class Entree {
        ZoneDessin dessin;
        int cle;
        long faite;
    }

    private ZonesDrapeaux() {
    }

    /** Branché par la partie client : bloc d'un drapeau chargé chez le joueur. */
    public static void suivre(DrapeauBlockEntity be) {
        DRAPEAUX.computeIfAbsent(be, k -> new Entree());
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    /** Capture ou contestation orange, tenue par son clan vert, par un autre rouge, libre blanc. */
    private static float[] couleur(DrapeauBlockEntity be) {
        if (be.etat == EtatZone.CAPTURE || be.etat == EtatZone.CONTESTE) return ORANGE;
        if (be.proprioTag.isEmpty()) return BLANC;
        ClanSnapshot moi = ClientClans.snapshot();
        return moi != null && be.proprioTag.equalsIgnoreCase(moi.tag()) ? VERT : ROUGE;
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || DRAPEAUX.isEmpty()) return;
        Level lvl = Minecraft.getInstance().level;
        long t = lvl == null ? 0 : lvl.getGameTime();
        boolean refait = false;
        Vec3 cam = e.getCamera().getPosition();
        for (Iterator<Map.Entry<DrapeauBlockEntity, Entree>> it = DRAPEAUX.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<DrapeauBlockEntity, Entree> d = it.next();
            DrapeauBlockEntity be = d.getKey();
            Entree en = d.getValue();
            if (be.isRemoved() || be.getLevel() != lvl || lvl == null) {
                if (en.dessin != null) en.dessin.close();
                it.remove();
                continue;
            }
            if (be.zoneId.isEmpty()) continue;
            BlockPos pos = be.getBlockPos();
            int r = Math.max(1, be.rayonCapture), h = Math.max(1, be.hauteurCapture);
            int cle = r * 1000 + h;
            double dx = cam.x - (pos.getX() + 0.5), dz = cam.z - (pos.getZ() + 0.5);
            if (Math.sqrt(dx * dx + dz * dz) - r > PORTEE) continue;   // loin du cercle : rien, rien de construit
            boolean aRefaire = en.dessin == null || en.cle != cle || t - en.faite >= REFAIRE_TICKS || t < en.faite;
            if (aRefaire && !refait) {   // un seul par image ; les autres gardent l'ancien jusqu'aux suivantes
                if (en.dessin != null) en.dessin.close();
                en.dessin = construire(lvl, pos, r, h);
                en.cle = cle;
                en.faite = t;
                refait = true;
            }
            if (en.dessin == null) continue;
            float[] c = couleur(be);
            en.dessin.dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, c[0], c[1], c[2], 1F, PORTEE);
        }
    }

    /** Cases du cercle (centre du drapeau, rayon r) sur leur vrai sol, et son bord ; intérieur vers le mât. */
    private static ZoneDessin construire(Level lvl, BlockPos pos, int r, int h) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        int fy = pos.getY();
        ZoneDessin.Construction d = ZoneDessin.construction(cx, fy, cz);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = pos.getX() - r; x <= pos.getX() + r; x++) {
            for (int z = pos.getZ() - r; z <= pos.getZ() + r; z++) {
                double ex = x + 0.5 - cx, ez = z + 0.5 - cz;
                if (ex * ex + ez * ez <= (double) r * r && lvl.hasChunk(x >> 4, z >> 4)) {
                    d.sol(x, z, sol(lvl, mp, x, z, fy, h), FOND);
                }
            }
        }
        int n = Mth.clamp(Math.round(Mth.TWO_PI * r), 24, 128);
        double[] gx = new double[n], gz = new double[n], gy = new double[n];
        for (int i = 0; i < n; i++) {
            double a = Mth.TWO_PI * i / n;
            gx[i] = cx + Math.cos(a) * r;
            gz[i] = cz + Math.sin(a) * r;
            int x = Mth.floor(gx[i]), z = Mth.floor(gz[i]);
            gy[i] = lvl.hasChunk(x >> 4, z >> 4) ? sol(lvl, mp, x, z, fy, h) : Double.NaN;
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (Double.isNaN(gy[i]) || Double.isNaN(gy[j])) continue;
            double mx = cx - (gx[i] + gx[j]) / 2, mz = cz - (gz[i] + gz[j]) / 2, l = Math.sqrt(mx * mx + mz * mz);
            if (l < 1.0E-3) continue;
            d.bord(gx[i], gy[i], gz[i], gx[j], gy[j], gz[j], mx / l, mz / l);
        }
        return d.finir();
    }

    /**
     * Dessus du vrai sol de la colonne (x, z), borné à +/- h du drapeau (fy) : carte MOTION_BLOCKING du joueur (la variante
     * NO_LEAVES est vide chez lui), puis on descend jusqu'au vrai terrain avec de l'air (ou un objet posé) au-dessus ; sous
     * un toit plus haut que la borne, premier sol en descendant ; rien trouvé : hauteur du drapeau.
     */
    private static int sol(Level level, BlockPos.MutableBlockPos mp, int x, int z, int fy, int h) {
        int bas = fy - h, haut = fy + h;
        int y = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), haut + 1);
        if (y <= bas) return bas;
        for (int yy = y; yy > bas; yy--) {
            if (terrain(level, mp.set(x, yy - 1, z)) && !terrain(level, mp.set(x, yy, z))) return yy;
        }
        return fy;
    }

    /**
     * Vrai terrain : bloc plein et opaque (terre, pierre, sable, béton, briques, planches), glace, eau, ou bloc bas qui
     * couvre toute la case (chemin, dalle du bas). Jamais les feuilles, les troncs ni un objet posé.
     */
    private static boolean terrain(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return true;
        if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return false;
        if (s.is(BlockTags.ICE) || s.isSolidRender(level, p)) return true;
        VoxelShape forme = s.getCollisionShape(level, p);
        if (forme.isEmpty()) return false;
        AABB b = forme.bounds();
        return b.minX <= 0.01 && b.minZ <= 0.01 && b.maxX >= 0.99 && b.maxZ >= 0.99 && b.minY <= 0.01 && b.maxY >= 0.25 && b.maxY < 0.99;
    }
}
