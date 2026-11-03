package com.heraliz.extraction.client;

import com.heraliz.extraction.HeralizExtraction;
import com.heraliz.extraction.ZoneShape;
import com.heraliz.extraction.entity.ExtractionTerminalEntity;
import fr.heraliz.noyau.client.zones.ZoneDessin;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zones d'extraction chez le joueur (03/11, choix du propriétaire après les rendus) : dessin commun des zones du noyau
 * ({@link ZoneDessin}) à la place du mur lumineux de 1,6 bloc. Sol teinté très léger sur les cases où l'on s'extrait,
 * au bord une bande nette, un dégradé au sol et un mur bas très léger ; rien qui gêne la vue des joueurs qui se battent
 * dedans. Couleur de l'avancement (rouge, orange, vert), fondu d'ouverture et de fermeture comme avant. Visible à travers
 * le décor seulement près du joueur.
 * <p>Chaque zone est construite une fois et refaite seulement quand sa forme change (recalculée chaque seconde par
 * l'entité : {@link ZoneShape#signature()}). Les zones sont suivies par le tick de leur entité
 * ({@link ExtractionTerminalEntity#ZONE_CLIENT}), même quand le PC n'est pas dessiné (caché derrière un mur).
 */
@Mod.EventBusSubscriber(modid = HeralizExtraction.MODID, value = Dist.CLIENT)
public final class ZonesExtraction {
    /** Opacité du sol de la zone. */
    private static final float FOND = 0.14F;
    /** Distance (blocs) au-delà de laquelle un morceau de la zone n'est pas dessiné. */
    private static final double PORTEE = 96;

    private static final Map<ExtractionTerminalEntity, Entree> ZONES = new IdentityHashMap<>();

    private static final class Entree {
        ZoneDessin dessin;
        ZoneShape forme;
        int signature;
    }

    private ZonesExtraction() {
    }

    /** Branché par la partie client : chaque tick d'une zone chez le joueur. */
    static void suivre(ExtractionTerminalEntity e) {
        ZONES.computeIfAbsent(e, k -> new Entree());
    }

    @SubscribeEvent
    public static void rendu(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ZONES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level lvl = mc.level;
        float partiel = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        for (Iterator<Map.Entry<ExtractionTerminalEntity, Entree>> it = ZONES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ExtractionTerminalEntity, Entree> z = it.next();
            ExtractionTerminalEntity t = z.getKey();
            Entree en = z.getValue();
            if (t.isRemoved() || t.level() != lvl) {
                if (en.dessin != null) {
                    en.dessin.close();
                }
                it.remove();
                continue;
            }
            float force = t.getVisualStrength(partiel);
            if (force <= 0.01F) {
                continue;
            }
            ZoneShape forme = t.getZone();
            if (en.dessin == null || forme != en.forme && forme.signature() != en.signature) {
                if (en.dessin != null) {
                    en.dessin.close();
                }
                en.dessin = construire(lvl, forme);
                en.signature = forme.signature();
            }
            en.forme = forme;
            float[] c = ExtractionTerminalEntity.progressColor(t.getProgress(partiel));
            en.dessin.dessiner(e.getPoseStack().last().pose(), e.getProjectionMatrix(), cam, c[0], c[1], c[2], force, PORTEE);
        }
    }

    /** Cases de la zone (sur leur vrai sol) et contour : arête i -> i + 1, intérieur vers le centre. */
    private static ZoneDessin construire(Level lvl, ZoneShape f) {
        double cx = f.centerX(), cz = f.centerZ();
        int gy = f.groundAt(Mth.floor(cx), Mth.floor(cz));
        ZoneDessin.Construction d = ZoneDessin.construction(cx, gy == Integer.MIN_VALUE ? 0 : gy, cz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = f.originX(); x < f.originX() + f.size(); x++) {
            for (int z = f.originZ(); z < f.originZ() + f.size(); z++) {
                int g = f.groundAt(x, z);
                if (g != Integer.MIN_VALUE) {
                    d.sol(x, z, ZoneShape.solReel(lvl, pos, x, g, z), FOND);
                }
            }
        }
        int n = ZoneShape.OUTLINE_SEGMENTS;
        for (int i = 0; i < n; i++) {
            double a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            double d0 = f.outlineDistance(i), d1 = f.outlineDistance(i + 1);
            double xa = cx + Math.cos(a0) * d0, za = cz + Math.sin(a0) * d0;
            double xb = cx + Math.cos(a1) * d1, zb = cz + Math.sin(a1) * d1;
            double mx = cx - (xa + xb) / 2, mz = cz - (za + zb) / 2, l = Math.sqrt(mx * mx + mz * mz);
            if (l < 1.0E-3) {
                continue;
            }
            d.bord(xa, f.outlineGround(i), za, xb, f.outlineGround(i + 1), zb, mx / l, mz / l);
        }
        return d.finir();
    }
}
