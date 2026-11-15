package fr.heraliz.interfaces.kit;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Flou du monde derriere nos ecrans (menus, inventaire, sac, fouille, coffres) : applique a l'image principale juste
 * avant de dessiner l'interface : le monde et le HUD deja dessines sont flous, l'interface reste nette (programme
 * assets/heraliz_interface/shaders/program/flou.json).
 * <p>15/11 (audit perf n° 1 : avant, quatre passes gaussiennes sur l'image entiere, a pleine resolution, a chaque image,
 * de plus en plus cher avec la taille de l'ecran) : l'image est d'abord reduite au quart (chaque cote divise par 4, 16
 * fois moins de pixels : moyenne de 4 x 4 pixels), floutee la (une passe horizontale, une verticale), puis agrandie a sa
 * place, lue lissee. Le flou de l'image reduite est regle pour la meme largeur de flou que les quatre passes d'origine
 * (meme variance, reduction et agrandissement compris) : meme aspect a l'oeil (rendus avant / apres : ecart moyen
 * inferieur a 1 sur 255), de l'ordre de 10 a 20 fois moins de lectures de l'image, et plus rien qui grandit avec le rayon.
 * Petits rayons (debut de l'animation d'ouverture des menus) : image reduite de moitie seulement. Plus aucune image de
 * travail a la taille de l'ecran (avant : une, gardee en memoire de la carte graphique).
 * <p>N'utilise que l'API publique ({@link PostPass}) et des cibles a nous : aucun conflit avec les effets d'image des
 * autres mods (desaturation de Reanimation, filtres d'ambiance). Au moindre souci (pilote, ressource absente), le flou
 * est coupe pour la session et l'ecran garde son voile sombre. Le joueur peut le couper : « Flou derriere les ecrans »
 * de l'ecran Graphismes (reglage {@code flou} de heraliz_interface-client.toml).
 */
public final class Flou {
    private Flou() {
    }

    /** Rayon (pixels de l'ecran) a partir duquel l'image est reduite au quart (en dessous : de moitie). */
    private static final float SEUIL_QUART = 8f;

    private static final Reduite DEMI = new Reduite(2), QUART = new Reduite(4);
    /** Projections des passes (taille de l'ecran, taille de l'image reduite) : reprises a chaque image, jamais recreees. */
    private static final Matrix4f ORTHO = new Matrix4f(), ORTHO_REDUITE = new Matrix4f();
    private static boolean enPanne;
    /** Reglage du dernier rayon, refait seulement quand il change (animation d'ouverture, taille de l'interface). */
    private static float dernierRayon = -1f, sigmaReduit;
    private static int portee;
    private static Reduite reduite = QUART;

    public static boolean disponible() {
        return !enPanne;
    }

    /** Floute l'image principale (rayon en pixels d'ecran ; 0 = rien). A appeler en debut de rendu de l'ecran. */
    public static void appliquer(float rayon) {
        if (enPanne || rayon < 0.5f) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget principal = mc.getMainRenderTarget();
        try {
            if (rayon != dernierRayon) regler(rayon);
            Reduite r = reduite;
            r.preparer(mc, principal);
            ORTHO.setOrtho(0f, principal.width, 0f, principal.height, 0.1f, 1000f);
            ORTHO_REDUITE.setOrtho(0f, r.a.width, 0f, r.a.height, 0.1f, 1000f);
            passe(r.reduire, ORTHO_REDUITE, 0f, 0f, 0f, 0f, r.f);
            passe(r.horizontal, ORTHO_REDUITE, portee, sigmaReduit, 1f, 0f, 0f);
            passe(r.vertical, ORTHO_REDUITE, portee, sigmaReduit, 0f, 1f, 0f);
            passe(r.agrandir, ORTHO, 0f, 0f, 0f, 0f, 0f);
        } catch (Throwable t) {
            enPanne = true;
            HeralizInterface.LOG.warn("[interface] flou indisponible, voile sombre seul", t);
            liberer();
        } finally {
            principal.bindWrite(true);
            // les passes ecrivent une profondeur : on la vide pour que l'interface se dessine par-dessus
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
    }

    /**
     * Facteur de reduction pour ce rayon, et flou de l'image reduite : la variance des quatre passes d'origine (rayon R
     * puis R / 2), moins celle de la reduction (moyenne de f x f pixels : (f^2 - 1) / 12) et de l'agrandissement lisse
     * (f^2 / 6), ramenee aux pixels de l'image reduite (divisee par f^2). Lectures jusqu'a 2,5 sigma de part et d'autre.
     */
    private static void regler(float rayon) {
        dernierRayon = rayon;
        reduite = rayon < SEUIL_QUART ? DEMI : QUART;
        double f2 = reduite.f * reduite.f;
        double v = (variance(rayon) + variance(rayon * 0.5f) - (f2 - 1) / 12.0 - f2 / 6.0) / f2;
        sigmaReduit = (float) Math.sqrt(Math.max(0.09, v));
        portee = Math.max(1, (int) Math.ceil(sigmaReduit * 2.5f));
    }

    /** Variance d'une passe d'origine (flou.fsh sans Sigma) : lectures de -r a r par pas de 1, sigma r / 2. */
    private static double variance(float r) {
        if (r < 0.5f) return 0;
        double s = r * 0.5, k = -0.5 / (s * s), total = 0, m1 = 0, m2 = 0;
        for (float x = -r; x <= r; x += 1f) {
            double w = Math.exp(x * x * k);
            total += w;
            m1 += x * w;
            m2 += (double) x * x * w;
        }
        double m = m1 / total;
        return m2 / total - m * m;
    }

    private static void passe(PostPass p, Matrix4f ortho, float rayon, float sigma, float dx, float dy, float reduction) {
        p.setOrthoMatrix(ortho);
        EffectInstance e = p.getEffect();
        e.safeGetUniform("Radius").set(rayon);
        e.safeGetUniform("Sigma").set(sigma);
        e.safeGetUniform("Reduction").set(reduction);
        e.safeGetUniform("BlurDir").set(dx, dy);
        p.process(0f);
    }

    /** Libere les programmes et les cibles (reglage coupe, rechargement des ressources, panne). */
    public static void liberer() {
        DEMI.liberer();
        QUART.liberer();
        dernierRayon = -1f;
    }

    /**
     * Chaine d'une image reduite d'un facteur f : principal -> a (moyenne de f x f pixels), a -> b (flou horizontal),
     * b -> a (flou vertical), a -> principal (agrandie, lue lissee). Cibles et programmes crees au premier usage.
     */
    private static final class Reduite {
        final int f;
        TextureTarget a, b;
        PostPass reduire, horizontal, vertical, agrandir;

        Reduite(int f) {
            this.f = f;
        }

        void preparer(Minecraft mc, RenderTarget principal) throws Exception {
            int w = (principal.width + f - 1) / f, h = (principal.height + f - 1) / f;
            if (agrandir == null) {
                liberer();                                          // reste d'une creation ratee
                a = cible(w, h);
                b = cible(w, h);
                String programme = HeralizInterface.MODID + ":flou";
                reduire = new PostPass(mc.getResourceManager(), programme, principal, a);
                horizontal = new PostPass(mc.getResourceManager(), programme, a, b);
                vertical = new PostPass(mc.getResourceManager(), programme, b, a);
                agrandir = new PostPass(mc.getResourceManager(), programme, a, principal);
            } else if (a.width != w || a.height != h) {
                a.resize(w, h, Minecraft.ON_OSX);
                b.resize(w, h, Minecraft.ON_OSX);
                a.setFilterMode(GL11.GL_LINEAR);                    // la nouvelle image revient « au plus proche »
                b.setFilterMode(GL11.GL_LINEAR);
            }
        }

        private static TextureTarget cible(int w, int h) {
            TextureTarget t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            t.setFilterMode(GL11.GL_LINEAR);
            return t;
        }

        void liberer() {
            for (PostPass p : new PostPass[]{reduire, horizontal, vertical, agrandir}) {
                try {
                    if (p != null) p.close();
                } catch (Throwable ignored) {
                }
            }
            for (TextureTarget t : new TextureTarget[]{a, b}) {
                try {
                    if (t != null) t.destroyBuffers();
                } catch (Throwable ignored) {
                }
            }
            reduire = horizontal = vertical = agrandir = null;
            a = b = null;
        }
    }
}
