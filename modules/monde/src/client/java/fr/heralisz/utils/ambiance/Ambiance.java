package fr.heralisz.utils.ambiance;

import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ambiance de la map (côté client uniquement, rien côté serveur) : l'état du ciel est calculé à partir du temps du
 * monde, qui est synchronisé — tous les joueurs voient donc les mêmes nuages, la même nuit étoilée ou couverte, la même
 * brume au même moment. Depuis le cycle jour / nuit (25/10, cycle.Cycle : le jour et la nuit n'avancent pas à la même
 * vitesse), les fronts de nuages suivent le temps de jeu ({@code getGameTime}, 20 ticks par seconde réelle, toujours),
 * la nuit et la brume du matin suivent l'heure ({@code getDayTime}). Lu par le ciel ({@link Ciel}), l'éclairage ({@link CielHeraliz}), le brouillard
 * ({@link Brouillard}) et le filtre d'image ({@link FiltreImage}). Recalculé une fois par image (quelques cosinus).
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class Ambiance {
    /** 0 en plein jour .. 1 en pleine nuit (selon la hauteur du soleil). */
    public static float nuit;
    /** Étoiles : 0 tant que le ciel est clair .. 1 en pleine nuit (elles apparaissent après le coucher du soleil). */
    public static float nuitEtoiles;
    /** Nébulosité : 0 ciel dégagé .. 1 couvert (pluie comprise). Des « fronts » passent toutes les ~6 minutes réelles. */
    public static float nuages;
    /** Brume du matin (0..1), un matin sur deux environ. */
    public static float brume;
    /** 0 dehors .. 1 sous la roche ou à l'intérieur (peu de lumière du ciel à la caméra). */
    public static float abri;
    public static float pluie;
    /** Éclat de la lune selon sa phase : 1 pleine lune .. 0 nouvelle lune. */
    public static float lune;
    /** L'ambiance s'applique (monde principal) : sinon tout reste vanilla (dimensions des boss...). */
    public static boolean actif;

    private static float abriCible;

    private Ambiance() {}

    /** Recalcul pour l'image en cours (avant le rendu du monde). */
    public static void maj(ClientLevel l, float pt) {
        actif = l.dimension() == Level.OVERWORLD;
        float s = soleil(l, pt);
        nuit = lisse((0.12f - s) / 0.42f);
        nuitEtoiles = lisse((0.05f - s) / 0.35f);
        pluie = l.getRainLevel(pt);
        double t = l.getGameTime() + (double) pt;                // temps réel (le jour et la nuit du cycle n'ont pas la même vitesse)
        nuages = Math.max(nebulosite(t), Math.min(1f, pluie * 1.3f));
        brume = brumeMatin(l.getDayTime()) * (1f - pluie);
        lune = l.getMoonBrightness();
    }

    /** Hauteur du soleil : 1 à midi .. -1 à minuit. */
    public static float soleil(ClientLevel l, float pt) {
        return Mth.cos(l.getTimeOfDay(pt) * Mth.TWO_PI);
    }

    /**
     * Nébulosité au temps de jeu t (ticks réels) : bruit lissé dont un point de contrôle tombe tous les 7000 ticks
     * (~6 min réelles) ;
     * répartition voulue ≈ 45 % de ciel dégagé, 30 % voilé, 25 % couvert.
     */
    public static float nebulosite(double t) {
        double u = t / 7000.0;
        long i = (long) Math.floor(u);
        float f = (float) (u - i);
        float v = Mth.lerp(f * f * (3 - 2 * f), hash(i), hash(i + 1));
        return lisse((v - 0.38f) / 0.42f);
    }

    /** Brume du matin : un jour sur deux, montée de 3 h 30 à 5 h 30, épaisse au lever du soleil, dissipée vers 9 h 30. */
    static float brumeMatin(long dayTime) {
        long jour = Math.floorDiv(dayTime - 18000L, 24000L);
        if (hash(jour * 31L + 7L) > 0.5f) return 0f;
        float h = (float) Math.floorMod(dayTime - 18000L, 24000L);          // millièmes d'heure depuis minuit
        float monte = lisse((h - 3500f) / 2000f);
        float tombe = lisse((9500f - h) / 2000f);
        return Math.min(monte, tombe) * (0.65f + 0.35f * hash(jour * 13L + 1L));
    }

    /** Teinte commune du ciel et du brouillard : plus terne et plus gris le jour, très sombre la nuit. */
    public static void teinte(float[] c) {
        float l = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
        float sat = Mth.lerp(nuages, 0.86f, 0.45f);
        sat = Mth.lerp(nuit, sat, 0.55f);
        float lum = Mth.lerp(nuages, 0.97f, 0.84f);
        lum = Mth.lerp(nuit, lum, Mth.lerp(1f - nuages, 0.42f, 0.62f) * Mth.lerp(lune, 0.75f, 1f));
        for (int k = 0; k < 3; k++) {
            c[k] = (l + (c[k] - l) * sat) * lum;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null || mc.gameRenderer == null) return;
        var cam = mc.gameRenderer.getMainCamera();
        if (cam == null || !cam.isInitialized()) return;
        int ciel = mc.level.getBrightness(LightLayer.SKY, BlockPos.containing(cam.getPosition()));
        abriCible = 1f - Mth.clamp((ciel - 5) / 8f, 0f, 1f);
        abri += (abriCible - abri) * 0.06f;
    }

    @SubscribeEvent
    public static void image(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase == TickEvent.Phase.START && mc.level != null) {
            maj(mc.level, e.renderTickTime);
        }
    }

    static float lisse(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    static float hash(long i) {
        long h = i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }
}
