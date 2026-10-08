package fr.heralisz.utils.ambiance;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Filtre d'image (le « shader » de la map) : une seule passe plein écran après le rendu du monde et de l'arme en main,
 * avant l'interface — étalonnage des couleurs façon film (verts ramenés vers l'olive, saturation et exposition un peu
 * baissées, ombres froides), vision nocturne bleu-gris (effet Purkinje), vignette et grain de pellicule. Voir
 * shaders/program/ambiance.fsh. Coût : une passe à la taille de l'écran + une copie.
 * <p>
 * Passe par l'emplacement « effet de post-traitement » du jeu, partagé avec d'autres mods (saignement de PlayerRevive,
 * écran gris à faibles PV et irradiation de geckolib222…) : le filtre ne s'installe que si l'emplacement est libre, ne
 * remplace jamais l'effet d'un autre, et revient tout seul quand celui-ci est retiré.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, value = Dist.CLIENT)
public final class FiltreImage {
    static final ResourceLocation CHAINE = new ResourceLocation(HeraliszUtils.MODID, "shaders/post/ambiance.json");
    private static PostChain chaine;
    private static EffectInstance effet;
    private static Field champPasses;
    private static boolean enPanne;
    private static final RandomSource HASARD = RandomSource.create();
    private static Method irisInstance;
    private static Method irisPack;
    private static boolean irisCherche;

    private FiltreImage() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gr = mc.gameRenderer;
        if (gr == null) return;
        PostChain cur = gr.currentEffect();
        boolean veut = !enPanne && AmbianceConfig.filtre() && mc.level != null && Ambiance.actif
                && mc.player != null && mc.getCameraEntity() == mc.player && !irisActif()
                && !"off".equals(System.getProperty("heralisz.filtre"));        // banc d'essai : mesure sans le filtre
        if (cur != null && cur == chaine) {
            if (!veut) {
                gr.shutdownEffect();
                oublier();
            }
            return;
        }
        oublier();                                                   // le nôtre n'est plus en place
        if (!veut || cur != null) return;                            // emplacement pris par un autre effet : on attend
        gr.loadEffect(CHAINE);
        PostChain c = gr.currentEffect();
        if (c == null || !CHAINE.toString().equals(c.getName())) {
            enPanne = true;                                          // shader introuvable ou invalide : pas de boucle
            HeraliszUtils.LOG.error("Filtre d'image HeraliZ indisponible ({}), désactivé pour cette session", CHAINE);
            return;
        }
        chaine = c;
        effet = notrePasse(c);
    }

    /** Uniformes de l'image en cours (avant le rendu, après le calcul de l'ambiance). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void image(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || effet == null || chaine == null) return;
        if (Minecraft.getInstance().gameRenderer.currentEffect() != chaine) return;
        effet.safeGetUniform("Nuit").set(Ambiance.nuit);
        effet.safeGetUniform("Nuages").set(Ambiance.nuages);
        effet.safeGetUniform("Pluie").set(Ambiance.pluie);
        effet.safeGetUniform("Brume").set(Ambiance.brume);
        effet.safeGetUniform("Graine").set(HASARD.nextFloat());
        effet.safeGetUniform("Force").set(1f);
    }

    private static void oublier() {
        chaine = null;
        effet = null;
    }

    @SuppressWarnings("unchecked")
    private static EffectInstance notrePasse(PostChain c) {
        try {
            if (champPasses == null) {
                champPasses = ObfuscationReflectionHelper.findField(PostChain.class, "f_110009_");   // PostChain.passes
            }
            for (PostPass p : (List<PostPass>) champPasses.get(c)) {
                if (p.getName().endsWith("ambiance")) {
                    return p.getEffect();
                }
            }
        } catch (Exception ex) {
            HeraliszUtils.LOG.warn("Filtre d'image : passes illisibles, réglages par défaut", ex);
        }
        return null;
    }

    /** Pack de shaders Oculus en cours : on laisse alors le ciel et l'image à ce pack. */
    static boolean irisActif() {
        if (!irisCherche) {
            irisCherche = true;
            if (ModList.get().isLoaded("oculus")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisInstance = api.getMethod("getInstance");
                    irisPack = api.getMethod("isShaderPackInUse");
                } catch (Exception ex) {
                    irisInstance = null;
                }
            }
        }
        if (irisInstance == null) return false;
        try {
            return (Boolean) irisPack.invoke(irisInstance.invoke(null));
        } catch (Exception ex) {
            return false;
        }
    }
}
