package fr.heraliz.interfaces.kit;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostPass;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Flou du monde derriere nos ecrans : deux passes gaussiennes separables (programme
 * assets/heraliz_interface/shaders/program/flou.json, horizontal puis vertical), deux fois (rayon puis demi-rayon) pour
 * un flou doux sans effet de boite. Applique a l'image principale juste avant de dessiner l'interface : le monde et le
 * HUD deja dessines sont flous, l'interface reste nette.
 * <p>N'utilise que l'API publique ({@link PostPass}) et une cible a nous : aucun conflit avec les effets d'image des
 * autres mods (desaturation de Reanimation, filtres d'ambiance). Au moindre souci (pilote, ressource absente), le flou
 * est coupe pour la session et l'ecran garde son voile sombre.
 */
public final class Flou {
    private Flou() {
    }

    private static PostPass horizontal, vertical;
    private static TextureTarget tampon;
    private static boolean enPanne;

    public static boolean disponible() {
        return !enPanne;
    }

    /** Floute l'image principale (rayon en pixels d'ecran ; 0 = rien). A appeler en debut de rendu de l'ecran. */
    public static void appliquer(float rayon) {
        if (enPanne || rayon < 0.5f) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget principal = mc.getMainRenderTarget();
        try {
            if (horizontal == null) creer(mc, principal);
            if (tampon.width != principal.width || tampon.height != principal.height) {
                tampon.resize(principal.width, principal.height, Minecraft.ON_OSX);
            }
            Matrix4f ortho = new Matrix4f().setOrtho(0f, principal.width, 0f, principal.height, 0.1f, 1000f);
            passe(horizontal, ortho, rayon, 1f, 0f);
            passe(vertical, ortho, rayon, 0f, 1f);
            passe(horizontal, ortho, rayon * 0.5f, 1f, 0f);
            passe(vertical, ortho, rayon * 0.5f, 0f, 1f);
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

    private static void creer(Minecraft mc, RenderTarget principal) throws Exception {
        tampon = new TextureTarget(principal.width, principal.height, false, Minecraft.ON_OSX);
        tampon.setFilterMode(GL11.GL_LINEAR);
        String programme = HeralizInterface.MODID + ":flou";
        horizontal = new PostPass(mc.getResourceManager(), programme, principal, tampon);
        vertical = new PostPass(mc.getResourceManager(), programme, tampon, principal);
    }

    private static void passe(PostPass p, Matrix4f ortho, float rayon, float dx, float dy) {
        p.setOrthoMatrix(ortho);
        p.getEffect().safeGetUniform("Radius").set(rayon);
        p.getEffect().safeGetUniform("BlurDir").set(dx, dy);
        p.process(0f);
    }

    /** Libere les programmes et la cible (fermeture de l'ecran, rechargement des ressources). */
    public static void liberer() {
        try {
            if (horizontal != null) horizontal.close();
            if (vertical != null) vertical.close();
            if (tampon != null) tampon.destroyBuffers();
        } catch (Throwable ignored) {
        }
        horizontal = vertical = null;
        tampon = null;
    }
}
