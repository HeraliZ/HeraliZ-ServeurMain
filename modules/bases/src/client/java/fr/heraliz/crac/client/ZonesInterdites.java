package fr.heraliz.crac.client;

import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.network.S2CZonesInterdites;
import fr.heraliz.noyau.client.zones.ZoneDessin;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zones où l'on ne construit pas, plan de construction en main (HeraliZ, 27/10, demande du propriétaire) : les cases
 * interdites du masque du serveur ({@code ZonesConstruction}, envoyé par morceaux autour du joueur,
 * {@link S2CZonesInterdites}). Rien plan rangé, en créatif, ou hors de la carte.
 * <p>03/11 (choix du propriétaire après les rendus) : dessin commun des zones du noyau ({@link ZoneDessin}) : sol rouge
 * translucide sur les cases interdites, au bord (là où l'on peut de nouveau construire) une bande nette, un dégradé au sol
 * et un mur bas très léger ; la case visée, si elle est interdite, est plus marquée. Visible à travers le décor seulement
 * près du joueur. Posé sur le vrai sol, sans les arbres ni les objets posés ({@link SolZones}).
 * <p>Construit une fois pour un carré de {@link #FENETRE} blocs autour du joueur (calé sur une grille de
 * {@link #GRILLE} blocs) et gardé dans la carte graphique ; refait quand le joueur change de case de la grille, quand le
 * serveur envoie un nouveau masque, ou au plus tard toutes les {@link #REFAIRE_TICKS} (le sol a pu changer). Rien n'est
 * recalculé à chaque image ; tout est rendu {@link #OUBLIER_TICKS} après qu'on a rangé le plan.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT)
public final class ZonesInterdites {
    private static final float R = 0xE8 / 255F, G = 0x4A / 255F, B = 0x3C / 255F;
    /** Teinte du sol des cases interdites ; en plus sur la case visée. */
    private static final float TEINTE = 0.18F, TEINTE_VISEE = 0.22F;
    /** Côté du carré construit autour du joueur, et sa grille (blocs). */
    private static final int FENETRE = 80, GRILLE = 16;
    private static final int REFAIRE_TICKS = 200, OUBLIER_TICKS = 600;
    /** Distance (blocs) au-delà de laquelle un morceau n'est pas dessiné. */
    private static final double PORTEE = 40;

    private static volatile S2CZonesInterdites donnees;
    private static WeakReference<Level> monde = new WeakReference<>(null);

    /** Position de travail de {@link SolZones} (fil du rendu seulement). */
    private static final BlockPos.MutableBlockPos CURSEUR = new BlockPos.MutableBlockPos();
    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap COLONNES = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    /** Le carré construit : pour quelles données, quel monde, quel coin, quand. */
    private static ZoneDessin dessin;
    private static S2CZonesInterdites dessinDonnees;
    private static Level dessinMonde;
    private static int dessinX = Integer.MIN_VALUE, dessinZ = Integer.MIN_VALUE;
    private static long dessinTick;
    /** La case visée marquée : son dessin et sa case. */
    private static ZoneDessin vise;
    private static long viseCle = Long.MIN_VALUE;
    private static long dernierUsage;

    private ZonesInterdites() {
    }

    public static void recevoir(S2CZonesInterdites p) {
        donnees = p.largeur() == 0 ? null : p;
        monde = new WeakReference<>(Minecraft.getInstance().level);
    }

    /** Les zones connues, seulement dans le monde où elles ont été reçues. */
    private static S2CZonesInterdites zones() {
        S2CZonesInterdites d = donnees;
        Minecraft mc = Minecraft.getInstance();
        return d != null && mc.level != null && monde.get() == mc.level ? d : null;
    }

    /** Le bloc (x, z) est-il dans une case interdite connue ? */
    public static boolean interdit(int x, int z) {
        S2CZonesInterdites d = zones();
        return d != null && d.interdit(Math.floorDiv(x - d.x0(), d.pas()), Math.floorDiv(z - d.z0(), d.pas()));
    }

    /** Même règle que le serveur (StructureService) : centre de la pièce, puis chacun de ses blocs. */
    public static boolean touche(PieceKey key, List<BlockPos> blocs) {
        if (zones() == null) {
            return false;
        }
        Vec3 c = key.centreWorld();
        if (interdit((int) Math.floor(c.x), (int) Math.floor(c.z))) {
            return true;
        }
        for (BlockPos q : blocs) {
            if (interdit(q.getX(), q.getZ())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        Level lvl = mc.level;
        S2CZonesInterdites d = zones();
        if (lvl != dessinMonde) {
            oublier();
        }
        if (p == null || lvl == null) {
            return;
        }
        long t = lvl.getGameTime();
        if (d == null || p.getAbilities().instabuild || !(p.getMainHandItem().getItem() instanceof BuildingPlanItem)) {
            if (dessin != null && (d == null || t - dernierUsage > OUBLIER_TICKS || t < dernierUsage)) {
                oublier();
            }
            return;
        }
        dernierUsage = t;
        int cx = Math.floorDiv(p.getBlockX(), GRILLE) * GRILLE - FENETRE / 2 + GRILLE / 2;
        int cz = Math.floorDiv(p.getBlockZ(), GRILLE) * GRILLE - FENETRE / 2 + GRILLE / 2;
        if (dessin == null || dessinDonnees != d || cx != dessinX || cz != dessinZ || t - dessinTick >= REFAIRE_TICKS || t < dessinTick) {
            if (dessin != null) {
                dessin.close();
            }
            COLONNES.clear();
            dessin = construire(lvl, d, cx, cz);
            dessinDonnees = d;
            dessinMonde = lvl;
            dessinX = cx;
            dessinZ = cz;
            dessinTick = t;
            if (vise != null) {
                vise.close();
                vise = null;
                viseCle = Long.MIN_VALUE;
            }
        }
        Vec3 cam = e.getCamera().getPosition();
        dessin.dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, R, G, B, 1F, PORTEE);
        // case visée (plan en main : la visée de Minecraft s'arrête au premier bloc), si elle est interdite
        HitResult h = mc.hitResult;
        long cle = Long.MIN_VALUE;
        int vi = 0, vj = 0;
        if (h instanceof BlockHitResult bh && h.getType() == HitResult.Type.BLOCK) {
            vi = Math.floorDiv(bh.getBlockPos().getX() - d.x0(), d.pas());
            vj = Math.floorDiv(bh.getBlockPos().getZ() - d.z0(), d.pas());
            if (d.interdit(vi, vj)) {
                cle = (long) vi << 32 | (vj & 0xFFFFFFFFL);
            }
        }
        if (cle != viseCle) {
            if (vise != null) {
                vise.close();
                vise = null;
            }
            viseCle = cle;
            if (cle != Long.MIN_VALUE) {
                int x0 = d.x0() + vi * d.pas(), z0 = d.z0() + vj * d.pas();
                ZoneDessin.Construction c = ZoneDessin.construction(x0, p.getY(), z0);
                for (int x = x0; x < x0 + d.pas(); x++) {
                    for (int z = z0; z < z0 + d.pas(); z++) {
                        int y = colonne(lvl, x, z);
                        if (y != SolZones.INCONNU) {
                            c.sol(x, z, y, TEINTE_VISEE);
                        }
                    }
                }
                vise = c.finir();
            }
        }
        if (vise != null) {
            vise.dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, R, G, B, 1F, PORTEE);
        }
    }

    private static void oublier() {
        if (dessin != null) {
            dessin.close();
        }
        if (vise != null) {
            vise.close();
        }
        dessin = vise = null;
        dessinDonnees = null;
        dessinMonde = null;
        viseCle = Long.MIN_VALUE;
        COLONNES.clear();
    }

    /** Le carré [x0, x0 + FENETRE) x [z0, z0 + FENETRE) : teinte des colonnes interdites, bords vers les cases permises. */
    private static ZoneDessin construire(Level lvl, S2CZonesInterdites d, int x0, int z0) {
        ZoneDessin.Construction c = ZoneDessin.construction(x0, Minecraft.getInstance().player.getY(), z0);
        for (int x = x0; x < x0 + FENETRE; x++) {
            for (int z = z0; z < z0 + FENETRE; z++) {
                if (!interdit(d, x, z)) {
                    continue;
                }
                int y = colonne(lvl, x, z);
                if (y != SolZones.INCONNU) {
                    c.sol(x, z, y, TEINTE);
                }
                // bords vers une colonne permise (une case hors de la fenêtre du serveur compte comme interdite : pas de bord)
                if (!interdit(d, x - 1, z) && connue(d, x - 1, z)) bord(c, lvl, x, z, x, z + 1, 1, 0);
                if (!interdit(d, x + 1, z) && connue(d, x + 1, z)) bord(c, lvl, x + 1, z + 1, x + 1, z, -1, 0);
                if (!interdit(d, x, z - 1) && connue(d, x, z - 1)) bord(c, lvl, x + 1, z, x, z, 0, 1);
                if (!interdit(d, x, z + 1) && connue(d, x, z + 1)) bord(c, lvl, x, z + 1, x + 1, z + 1, 0, -1);
            }
        }
        return c.finir();
    }

    /** Arête de A à B (points de la grille), intérieur de la zone vers (nx, nz). */
    private static void bord(ZoneDessin.Construction c, Level lvl, int xa, int za, int xb, int zb, int nx, int nz) {
        int ya = point(lvl, xa, za), yb = point(lvl, xb, zb);
        if (ya != SolZones.INCONNU && yb != SolZones.INCONNU) {
            c.bord(xa, ya, za, xb, yb, zb, nx, nz);
        }
    }

    private static boolean interdit(S2CZonesInterdites d, int x, int z) {
        return d.interdit(Math.floorDiv(x - d.x0(), d.pas()), Math.floorDiv(z - d.z0(), d.pas()));
    }

    /** Colonne dans la fenêtre envoyée par le serveur (hors de la fenêtre : on ne sait pas). */
    private static boolean connue(S2CZonesInterdites d, int x, int z) {
        int i = Math.floorDiv(x - d.x0(), d.pas()), j = Math.floorDiv(z - d.z0(), d.pas());
        return i >= 0 && j >= 0 && i < d.largeur() && j < d.hauteur();
    }

    /** Haut du vrai sol de la colonne (x, z) ; {@link SolZones#INCONNU} hors du terrain chargé. */
    private static int colonne(Level lvl, int x, int z) {
        long cle = (long) x << 32 | (z & 0xFFFFFFFFL);
        if (COLONNES.containsKey(cle)) {
            return COLONNES.get(cle);
        }
        int y = SolZones.dessus(lvl, x, z, CURSEUR);
        COLONNES.put(cle, y);
        return y;
    }

    /** Point (x, z) de la grille : vrai sol le plus haut des 4 colonnes qui le touchent. */
    private static int point(Level lvl, int xb, int zb) {
        int best = SolZones.INCONNU;
        for (int x = xb - 1; x <= xb; x++) {
            for (int z = zb - 1; z <= zb; z++) {
                best = Math.max(best, colonne(lvl, x, z));
            }
        }
        return best;
    }
}
