package fr.heralisz.utils.ambiance;

import com.mojang.blaze3d.shaders.FogShape;
import fr.heralisz.utils.BorderFog;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Brouillard d'ambiance : voile de distance le jour (les lointains se perdent, plus vite par temps couvert), brume
 * épaisse certains matins, rideau de pluie, nuit qui avale l'horizon ; couleur assortie au ciel (même teinte, voir
 * {@link Ambiance#teinte}). Atténué sous la roche et dans les bâtiments (grottes, faille, intérieurs).
 * <p>
 * Passe en dernier : si un autre réglage a déjà pris la main (tempête du désert, arènes des boss), on n'y touche pas.
 * La brume de bordure de la map ({@link BorderFog}) s'ajoute par-dessus dans tous les cas.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class Brouillard {
    /** Le dernier brouillard de terrain a été réglé par un autre (désert, boss) : sa couleur n'est pas retouchée. */
    private static boolean externe;
    private static final float[] C = new float[3];

    private Brouillard() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog e) {
        boolean terrain = e.getMode() == FogRenderer.FogMode.FOG_TERRAIN;
        if (fr.heralisz.utils.cine.Cinematique.actif()) {                // la cinématique règle sa propre brume
            fr.heralisz.utils.cine.Cinematique.brume(e);
            return;
        }
        if (brumeSpawn(e)) {
            return;
        }
        if (terrain) {
            externe = e.isCanceled();
        }
        if (!e.isCanceled() && Ambiance.actif && libre(e.getCamera(), e.getType())) {
            float far = e.getFarPlaneDistance();
            float cache = Math.max(Ambiance.brume, Ambiance.pluie * 0.7f);
            if (terrain) {
                float debut = far * Mth.lerp(Ambiance.nuages, 0.62f, 0.36f);
                float fin = far;
                debut = Mth.lerp(Ambiance.nuit, debut, far * 0.10f);
                fin = Mth.lerp(Ambiance.nuit, fin, far * 0.88f);
                debut = Mth.lerp(Ambiance.pluie, debut, 12f);
                fin = Mth.lerp(Ambiance.pluie, fin, Math.min(fin, 140f));
                debut = Mth.lerp(Ambiance.brume, debut, 0f);
                fin = Mth.lerp(Ambiance.brume, fin, Math.min(fin, 72f));
                // sous la roche / à l'intérieur : on revient au réglage d'origine
                e.setNearPlaneDistance(Mth.lerp(Ambiance.abri, debut, e.getNearPlaneDistance()));
                e.setFarPlaneDistance(Mth.lerp(Ambiance.abri, fin, e.getFarPlaneDistance()));
                e.setFogShape(FogShape.SPHERE);
                e.setCanceled(true);
            } else if (cache > 0.01f) {
                // ciel : la brume et la pluie le noient aussi (sinon il resterait net au-dessus d'un sol embrumé)
                e.setFarPlaneDistance(Mth.lerp(cache * (1f - Ambiance.abri), far, 22f));
                e.setCanceled(true);
            }
        }
        if (terrain) {
            BorderFog.appliquer(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void couleur(ViewportEvent.ComputeFogColor e) {
        if (fr.heralisz.utils.cine.Cinematique.actif()) {
            fr.heralisz.utils.cine.Cinematique.couleurBrume(e);
            return;
        }
        if (externe || !Ambiance.actif || !libre(e.getCamera(), e.getCamera().getFluidInCamera())) return;
        C[0] = e.getRed();
        C[1] = e.getGreen();
        C[2] = e.getBlue();
        Ambiance.teinte(C);
        // brume du matin : blanc grisâtre (sombre avant l'aube)
        if (Ambiance.brume > 0.01f) {
            float j = Mth.lerp(Ambiance.nuit, 0.78f, 0.16f);
            float k = Ambiance.brume * 0.75f;
            C[0] = Mth.lerp(k, C[0], 0.92f * j);
            C[1] = Mth.lerp(k, C[1], 0.94f * j);
            C[2] = Mth.lerp(k, C[2], 0.97f * j);
        }
        e.setRed(C[0]);
        e.setGreen(C[1]);
        e.setBlue(C[2]);
    }

    /** Monde du spawn (grotte dans le vide) : brume épaisse dès que le ciel est visible (on ne voit jamais le vide autour) ;
     *  sous la roche, le réglage d'origine. */
    private static boolean brumeSpawn(ViewportEvent.RenderFog e) {
        var l = net.minecraft.client.Minecraft.getInstance().level;
        if (l == null || !l.dimension().location().equals(fr.heralisz.utils.cine.CineDecor.SPAWN)
                || e.getType() != FogType.NONE) {
            return false;
        }
        var p = net.minecraft.core.BlockPos.containing(e.getCamera().getPosition());
        float ciel = l.canSeeSky(p) ? 1f : 0f;
        if (ciel > 0f) {
            e.setNearPlaneDistance(2f);
            e.setFarPlaneDistance(Math.min(e.getFarPlaneDistance(), 40f));
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
            return true;
        }
        return false;
    }

    /** Pas dans l'eau, la lave ou la neige poudreuse, pas aveuglé : le brouillard d'ambiance s'applique. */
    private static boolean libre(Camera cam, FogType type) {
        if (type != FogType.NONE) return false;
        return !(cam.getEntity() instanceof LivingEntity le && (le.hasEffect(MobEffects.BLINDNESS) || le.hasEffect(MobEffects.DARKNESS)));
    }
}
