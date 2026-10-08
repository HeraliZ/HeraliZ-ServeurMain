package fr.heralisz.clans.client.gui.kit;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.banniere.AtlasSdf;
import fr.heralisz.clans.client.banniere.Composeur;
import fr.heralisz.clans.client.banniere.Rendu;
import fr.heralisz.clans.client.banniere.TextureBanniere;
import fr.heralisz.clans.client.banniere.Vivante;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.clans.model.Calque;
import fr.heralisz.clans.territory.client.TissuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rendu des bannieres v2 dans les interfaces (spec docs/specs/BANNIERE_V2_SPEC.md §3) : fanion a pointe, fond en
 * degrade, calques de formes (atlas SDF) nets a toutes les tailles, compose par {@link Composeur}.
 * <ul>
 *   <li>{@link #draw} : fanion 2:3 inscrit et centre dans la boite, texture a sa <b>taille physique</b> (echelle GUI
 *   reelle x echelle de la pile de matrices), en cache LRU de {@value #CACHE_MAX} entrees (cle de banniere + taille ;
 *   a l'eviction : release() + close()). Une banniere n'est composee qu'une fois par taille.</li>
 *   <li>{@link #drawVivante} : banniere qui change a chaque geste (atelier, apercu de creation) : voir {@link Vivante}
 *   (fil de fond, demi-resolution pendant le geste, jamais plus que l'envoi GL sur le fil de rendu).</li>
 *   <li>{@link #formeSous} : test « ce point est-il dans la forme reelle du calque » (selection au clic).</li>
 *   <li>{@link #vider()} : tout oublier (deconnexion, rechargement des ressources) ; vide aussi les vignettes
 *   ({@link FormeArt}) et les tissus 3D ({@link TissuTexture}), pas l'atlas ({@link AtlasSdf}, relu au rechargement des
 *   ressources seulement, sur un fil de fond).</li>
 * </ul>
 * L'ancienne planche {@code textures/gui/emblemes.png} n'est plus lue.
 */
public final class BannerArt {
    private BannerArt() {
    }

    /** Nombre de textures gardees (en-tete, classement, invitations, toasts, apercus : largement assez). */
    public static final int CACHE_MAX = 48;

    /** Cle du cache (la sonde, mutable, sert aux recherches sans allocation ; les cles stockees ne changent plus). */
    private static final class Cle {
        String banniere;
        int w, h;

        Cle(String banniere, int w, int h) {
            this.banniere = banniere;
            this.w = w;
            this.h = h;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cle c && c.w == w && c.h == h && c.banniere.equals(banniere);
        }

        @Override
        public int hashCode() {
            return (banniere.hashCode() * 31 + w) * 31 + h;
        }
    }

    private record Entree(ResourceLocation rl, TextureBanniere texture) {
    }

    private static final Cle SONDE = new Cle("", 0, 0);
    private static final Map<Cle, Entree> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static int compteur;
    private static final int[] UN_CALQUE = new int[Composeur.CHAMPS];

    /**
     * Oublie toutes les textures de bannieres, vignettes et tissus (deconnexion, rechargement). L'atlas, ressource du
     * client independante du serveur, est garde : il n'est relu (hors du fil de rendu) qu'au rechargement des
     * ressources, par {@link AtlasSdf#RECHARGEMENT}.
     */
    public static void vider() {
        Minecraft mc = Minecraft.getInstance();
        for (Entree e : CACHE.values()) liberer(mc, e);
        CACHE.clear();
        Vivante.vider();
        FormeArt.vider();
        TissuTexture.vider();
    }

    private static void liberer(Minecraft mc, Entree e) {
        mc.getTextureManager().release(e.rl);
        e.texture.close();
    }

    /** Texture 64 x 96 (ancien usage du drapeau 3D, gardee pour compatibilite). */
    public static ResourceLocation texture(Banniere b) {
        return texture(b, 64, 96);
    }

    /** Texture de la banniere a {@code w x h} pixels (fanion sur toute la largeur, pointe en bas, hors fanion transparent). */
    public static ResourceLocation texture(Banniere b, int w, int h) {
        SONDE.banniere = b.cle();
        SONDE.w = w;
        SONDE.h = h;
        Entree e = CACHE.get(SONDE);
        if (e != null) return e.rl;
        int np = w * h;
        int[] px = Rendu.pixels(np);
        int n = Rendu.emballer(b);
        Rendu.COMPOSEUR.banniere(px, w, h, b.couleurA, b.couleurB, b.degrade.ordinal(), Rendu.calques(), n, AtlasSdf.octets());
        TextureBanniere tex = new TextureBanniere(false);
        ResourceLocation rl = HeraliszClans.id("banniere/" + (compteur++));
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().register(rl, tex);
        tex.envoyer(Rendu.versTampon(px, np), w, h);
        CACHE.put(new Cle(SONDE.banniere, w, h), new Entree(rl, tex));
        if (CACHE.size() > CACHE_MAX) {
            Iterator<Entree> it = CACHE.values().iterator();
            liberer(mc, it.next());
            it.remove();
        }
        return rl;
    }

    // ------------------------------------------------------------------ fanion 2:3 inscrit (sorties de inscrire())
    /** Decalage GUI du fanion dans la boite, taille dessinee (px physiques) et taille de la texture (bornee). */
    private static float insX, insY;
    private static int insW, insH, texW, texH;

    /**
     * Le canevas fait 64 x 96 (2:3) : le fanion est inscrit dans la boite (w, h) et centre, sans jamais etre etire (une
     * boite 3:4 l'elargissait de 12,5 % : carres en rectangles, cercles en ellipses). Tailles en pixels physiques ; la
     * texture est bornee a {@link Rendu#MAX_PX} en gardant le ratio.
     */
    private static void inscrire(int w, int h, float e) {
        float wi = Math.min(w, h * 2f / 3f), hi = wi * 1.5f;
        insW = Math.max(1, Math.round(wi * e));
        insH = Math.max(1, Math.round(hi * e));
        float k = Math.min(1f, Rendu.MAX_PX / (float) Math.max(insW, insH));
        texW = Math.max(1, Math.round(insW * k));
        texH = Math.max(1, Math.round(insH * k));
        // decalage arrondi au pixel physique : pas de demi-pixel (echantillonnage au plus proche net)
        insX = Math.round((w * e - insW) / 2f) / e;
        insY = Math.round((h * e - insH) / 2f) / e;
    }

    /** Dessin GUI de la banniere dans (x, y, w, h) : fanion 2:3 inscrit et centre, net a la taille physique. */
    public static void draw(GuiGraphics g, Banniere b, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        float e = Rendu.echelle(g);
        inscrire(w, h, e);
        int tw = texW, th = texH, dw = insW, dh = insH;
        ResourceLocation rl = texture(b, tw, th);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // repere en pixels physiques : la texture tombe pile sur la grille de l'ecran
        g.pose().pushPose();
        g.pose().translate(x + insX, y + insY, 0);
        g.pose().scale(1 / e, 1 / e, 1);
        g.blit(rl, 0, 0, dw, dh, 0, 0, tw, th, tw, th);
        g.pose().popPose();
    }

    /** Dessin GUI avec une opacite (bannieres eteintes). */
    public static void draw(GuiGraphics g, Banniere b, int x, int y, int w, int h, float alpha) {
        g.setColor(1, 1, 1, alpha);
        draw(g, b, x, y, w, h);
        g.setColor(1, 1, 1, 1);
    }

    /**
     * Dessin GUI d'une banniere qui change souvent (atelier, apercu de creation) : une seule texture de travail,
     * recomposee hors du fil de rendu quand {@link Banniere#cle()} change. Rien n'entre dans le cache.
     */
    public static void drawVivante(GuiGraphics g, Banniere b, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        float e = Rendu.echelle(g);
        inscrire(w, h, e);
        int tw = texW, th = texH, dw = insW, dh = insH;
        g.pose().pushPose();
        g.pose().translate(x + insX, y + insY, 0);
        g.pose().scale(1 / e, 1 / e, 1);
        Vivante.dessiner(g, b, 0, 0, dw, dh, tw, th);
        g.pose().popPose();
    }

    /**
     * Le point (u, v) du canevas 64 x 96 est-il dans la forme reelle du calque {@code calque} (contour compris) ? Test
     * sur le champ de distance de l'atlas, pas sur la boite. Sans atlas : test sur la boite tournee.
     */
    public static boolean formeSous(Banniere b, int calque, float u, float v) {
        return formeSous(b, calque, u, v, 0f);
    }

    /** Idem avec une tolerance en unites du canevas (positive = plus large, pratique pour les formes fines). */
    public static boolean formeSous(Banniere b, int calque, float u, float v, float marge) {
        Calque c = b.calque(calque);
        if (c == null) return false;
        byte[] atlas = AtlasSdf.octets();
        if (atlas == null) {
            double th = Math.toRadians(c.rot()), dx = u - c.cx(), dy = v - c.cy();
            double lx = Math.cos(th) * dx + Math.sin(th) * dy, ly = -Math.sin(th) * dx + Math.cos(th) * dy;
            return Math.abs(lx) <= c.w() / 2.0 + marge && Math.abs(ly) <= c.h() / 2.0 + marge;
        }
        Rendu.emballer(c, UN_CALQUE, 0);
        return Rendu.COMPOSEUR.distanceCanevas(UN_CALQUE, 0, atlas, u, v) + marge >= 0;
    }

    /** Mesures de la banniere vivante (derniere fenetre de 2 s pendant un geste). */
    public static String mesuresVivante() {
        return Objects.requireNonNullElse(Vivante.mesures(), "");
    }
}
