package fr.heralisz.utils.ambiance;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lueur minimale dans les bases (27/11, choix du propriétaire « on ne voit rien dans les bases ») : les pièces de CRac
 * coupent toute la lumière du ciel, et la nuit de HeraliZ est très sombre ({@link CielHeraliz}). Dans la zone d'une base
 * (la sienne ou celle d'un autre clan, zone de l'armoire connue du jeu par CRac : {@code ClientClaimCache.claimAt}, lu par
 * réflexion, HeraliZ Utils ne dépend pas de CRac), l'éclairage ne descend pas sous {@link #LUEUR} : on distingue les murs,
 * les coffres et les portes sans lampe, sans voir comme en plein jour. Hors des bases, rien ne change (grottes et nuits
 * restent noires). Passage en douceur (une demi-seconde). Seulement sur la carte (overworld). Jeu du joueur seulement.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class LueurBases {
    /** Plancher de l'éclairage (couleur de la carte de lumière, 0 à 1) dans une base. */
    static final float LUEUR = 0.11f;
    private static final float PAS = 0.01f;
    /** Plancher en cours, lu par {@link CielHeraliz#adjustLightmapColors} pour chaque case de la carte de lumière. */
    static volatile float niveau;
    private static Method claimAt;
    private static boolean cherche;

    private LueurBases() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        float cible = 0f;
        if (mc.level != null && mc.player != null && mc.level.dimension() == Level.OVERWORLD && dansUneBase(mc.player.blockPosition())) {
            cible = LUEUR;
        }
        float n = niveau;
        niveau = n < cible ? Math.min(cible, n + PAS) : Math.max(cible, n - PAS);
    }

    private static boolean dansUneBase(BlockPos pos) {
        if (!cherche) {
            cherche = true;
            try {
                claimAt = Class.forName("fr.heraliz.crac.client.ClientClaimCache").getMethod("claimAt", BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                claimAt = null;   // CRac absent : jamais de lueur
            }
        }
        if (claimAt == null) return false;
        try {
            return claimAt.invoke(null, pos) instanceof Enum<?> ici && !"NONE".equals(ici.name());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
