package fr.heralisz.clans.client.gui.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.banniere.AtlasSdf;
import fr.heralisz.clans.client.banniere.Rendu;
import fr.heralisz.clans.client.banniere.TextureBanniere;
import fr.heralisz.clans.model.Formes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vignette nette d'une forme seule (sélecteur de l'atelier, liste des calques, grille des emblèmes) : la forme,
 * blanche sur transparent, est composée depuis l'atlas SDF à la taille physique du dessin (la plus grande dimension
 * de ses proportions par défaut remplit le carré), puis teintée par la couleur du shader. Cache LRU par
 * (forme, taille px) de {@value #CACHE_MAX} entrées (release() + close() à l'éviction).
 */
public final class FormeArt {
    private FormeArt() {
    }

    public static final int CACHE_MAX = 256;

    private record Entree(ResourceLocation rl, TextureBanniere texture) {
    }

    /** Clé = forme x 4096 + taille px ; la sonde mutable évite toute allocation à la recherche. */
    private static final class Cle {
        int v;

        Cle(int v) {
            this.v = v;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cle c && c.v == v;
        }

        @Override
        public int hashCode() {
            return v * 0x9E3779B1;
        }
    }

    private static final Cle SONDE = new Cle(0);
    private static final Map<Cle, Entree> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int compteur;

    /**
     * Dessine la forme {@code forme} dans le carré (x, y, taille) GUI, couleur {@code rgb}, opacité {@code alpha}
     * (0..255). Forme inconnue : rien.
     */
    public static void draw(GuiGraphics g, int forme, int x, int y, int taille, int rgb, int alpha) {
        if (taille <= 0 || alpha <= 0 || !Formes.existe(forme)) return;
        int S = Rendu.px(taille, Rendu.echelle(g));
        ResourceLocation rl = texture(forme, S);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, Math.min(255, alpha) / 255f);
        g.blit(rl, x, y, taille, taille, 0, 0, S, S, S, S);
        g.setColor(1, 1, 1, 1);
    }

    /** Texture S x S (blanc, alpha = couverture) de la forme. */
    public static ResourceLocation texture(int forme, int S) {
        SONDE.v = forme * 4096 + S;
        Entree e = CACHE.get(SONDE);
        if (e != null) return e.rl;
        int np = S * S;
        int[] px = Rendu.pixels(np);
        Rendu.COMPOSEUR.forme(px, S, forme, Formes.largeurDefaut(forme), Formes.hauteurDefaut(forme), AtlasSdf.octets());
        TextureBanniere tex = new TextureBanniere(false);
        ResourceLocation rl = HeraliszClans.id("forme/" + (compteur++));
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().register(rl, tex);
        tex.envoyer(Rendu.versTampon(px, np), S, S);
        CACHE.put(new Cle(SONDE.v), new Entree(rl, tex));
        if (CACHE.size() > CACHE_MAX) {
            Iterator<Entree> it = CACHE.values().iterator();
            Entree vieille = it.next();
            mc.getTextureManager().release(vieille.rl);
            vieille.texture.close();
            it.remove();
        }
        return rl;
    }

    /** Oublie toutes les vignettes (déconnexion, rechargement des ressources). */
    public static void vider() {
        Minecraft mc = Minecraft.getInstance();
        for (Entree e : CACHE.values()) {
            mc.getTextureManager().release(e.rl);
            e.texture.close();
        }
        CACHE.clear();
    }
}
