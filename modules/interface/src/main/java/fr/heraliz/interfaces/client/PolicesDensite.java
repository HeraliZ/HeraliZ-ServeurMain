package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import fr.heraliz.interfaces.nettete.mixin.FontSetAccesseur;
import fr.heraliz.interfaces.nettete.mixin.TrueTypeAccesseur;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

/**
 * Lettres tracées à la taille exacte où elles s'affichent (03/11, retour du propriétaire : « les écritures font toujours
 * un peu pixelisées »).
 * <p>
 * La vraie cause du flou : nos polices TTF (DM Sans, Onest) sont tracées 8 fois plus fin que l'unité d'interface
 * (oversample 8), puis réduites à l'écran par la carte graphique : 4 fois à l'échelle 2, 2,7 fois à l'échelle 3, sans
 * mipmaps. Que la planche soit lue au point près (Minecraft) ou lissée ({@link PolicesNettes}), chaque point de l'écran ne
 * lit que 1 à 4 points de la lettre sur 9 à 16 : traits trop fins ou trop gras selon où ils tombent, bords en escalier,
 * petites capitales « grignotées ». Aucune valeur fixe d'oversample ne peut convenir, puisque l'échelle change (HUD du
 * joueur, écrans forcés, texte agrandi).
 * <p>
 * Ici, au moment de dessiner une lettre d'une de nos polices, on calcule combien de points d'écran fait une unité
 * (échelle d'interface x agrandissement de la pose) et on rend la lettre tracée à exactement cette finesse : un point de la
 * planche = un point de l'écran, lu au point près (planches à part, jamais lissées). C'est le rendu d'un texte de
 * navigateur : net à toutes les échelles, entières ou non (2, 2,5, 3…), et pour le texte agrandi des anciens écrans.
 * <ul>
 * <li>Mêmes avances et mêmes mesures qu'avant (les largeurs ne changent pas, rien ne bouge dans les écrans) : seule la
 * planche des lettres change, et chaque lettre est posée à sa vraie place (sa marge gauche n'est plus comptée deux fois,
 * voir {@link Couche#marge}).</li>
 * <li>Le tracé reprend le fournisseur TTF du jeu (même fichier en mémoire, même taille, même décalage), avec
 * l'oversample voulu ; rien n'est relu sur le disque.</li>
 * <li>Seulement sur nos polices (espaces de noms de {@link PolicesNettes}), dans l'interface (projection orthogonale de
 * l'écran), sans rotation, à une finesse multiple de 1/4 entre 1 et 12 ; sinon (texte dans le monde, animations
 * d'agrandissement, texte tourné) : le rendu d'avant, inchangé.</li>
 * <li>Au plus 3 finesses gardées par police (HUD, écrans) ; les planches d'une police fermée (rechargement des
 * ressources) sont rendues.</li>
 * <li>Aucune allocation quand la lettre est déjà tracée ; au moindre problème, tout repasse au rendu d'avant
 * (journal : « Lettres nettes coupées »). Désactivable : {@code -Dheraliz.lettresNettes=false}.</li>
 * </ul>
 * Branché par {@code nettete.mixin.TexteDensiteMixin} (configuration de mixins non obligatoire : si elle ne s'applique
 * pas, le jeu démarre avec le rendu d'avant).
 */
public final class PolicesDensite {
    private PolicesDensite() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    /** Lettre laissée au jeu (police qui n'est pas TTF, espace, lettre trop grande). */
    private static final Object JEU = new Object();
    private static final int COUCHES = 3, PLANCHES_MAX = 8, PLANCHE = 256;
    /** Finesse de la couche servie par {@link #notre} (fil de rendu). */
    private static float finesse;
    private static boolean actif = !"false".equals(System.getProperty("heraliz.lettresNettes"));
    private static final Map<FontSet, Etat> ETATS = new IdentityHashMap<>();
    private static int compteur;
    /** Finesse de la dernière lettre rendue par {@link #glyphe} (0 : lettre du jeu), lue par le gras et l'ombre. */
    private static float derniere;

    /**
     * Remplace {@code fontSet.getGlyph(cp)} dans le rendu d'une chaîne ({@code Font$StringRenderOutput.accept}) : la
     * lettre à la finesse de l'écran pour nos polices, celle du jeu sinon.
     */
    public static BakedGlyph glyphe(FontSet set, int cp, Matrix4f pose) {
        derniere = 0;
        if (actif) {
            try {
                BakedGlyph g = notre(set, cp, pose);
                if (g != null) {
                    derniere = finesse;
                    return g;
                }
            } catch (Throwable t) {
                couper(t);
            }
        }
        return set.getGlyph(cp);
    }

    /** Lettre brouillée (texte « obfusqué ») : rendue par le jeu, sans gras ni ombre fins. */
    public static BakedGlyph auHasard(FontSet set, com.mojang.blaze3d.font.GlyphInfo info) {
        derniere = 0;
        return set.getRandomGlyph(info);
    }

    /**
     * Décalage du gras (03/11, retour du propriétaire : messages du chat en gras « plusieurs barres par lettre »).
     * Minecraft fait le gras en redessinant la lettre une unité plus loin, et l'ombre une unité en bas à droite : prévu pour
     * sa police en pixels (une unité = un pixel de la lettre), sur DM Sans cela fait deux ou trois copies de la lettre à 2,
     * 3 ou 4 points d'écran l'une de l'autre (traits doublés, lettres « creuses »), floues avant le 03/11, nettes et donc
     * criantes depuis. Pour nos lettres tracées à la finesse de l'écran : un seul point d'écran. L'avance du gras (une
     * unité, mise en page de Minecraft) ne change pas : aucune largeur ne bouge.
     */
    public static float decalageGras(com.mojang.blaze3d.font.GlyphInfo info) {
        float d = derniere;
        return d > 0 ? 1f / d : info.getBoldOffset();
    }

    /** Décalage de l'ombre (voir {@link #decalageGras}) : un point d'écran pour nos lettres. */
    public static float decalageOmbre(com.mojang.blaze3d.font.GlyphInfo info) {
        float d = derniere;
        return d > 0 ? 1f / d : info.getShadowOffset();
    }

    private static BakedGlyph notre(FontSet set, int cp, Matrix4f pose) {
        int cle = cle(pose);
        if (cle == 0) return null;
        Etat e = ETATS.get(set);
        if (e == null) e = nouvel(set);
        if (!e.nous) return null;
        Couche c = e.couche(cle);
        finesse = c.finesse;
        Object o = c.glyphes.get(cp);
        if (o == null) {
            o = c.creer(e, cp);
            c.glyphes.put(cp, o);
        }
        return o == JEU ? null : (BakedGlyph) o;
    }

    /**
     * Finesse du dessin, en quarts de point d'écran par unité (8 = 2 points, 10 = 2,5, 12 = 3) ; 0 = rendu du jeu.
     * Seulement dans l'interface : projection orthogonale de l'écran à l'échelle courante, pose sans rotation ni
     * déformation.
     */
    static int cle(Matrix4f m) {
        if (!RenderSystem.isOnRenderThread()) return 0;
        float sx = m.m00(), sy = m.m11();
        if (sx <= 0 || Math.abs(m.m01()) > 1e-4f || Math.abs(m.m10()) > 1e-4f || Math.abs(sx - sy) > sx * 1e-3f) return 0;
        Matrix4f p = RenderSystem.getProjectionMatrix();
        if (p.m23() != 0f || p.m33() != 1f) return 0;
        Window w = Minecraft.getInstance().getWindow();
        double s = w.getGuiScale();
        if (s <= 0 || Math.abs(p.m00() * w.getWidth() - 2 * s) > 0.01 * s) return 0;
        double q4 = s * sx * 4;
        long q = Math.round(q4);
        if (q < 4 || q > 48 || Math.abs(q4 - q) > 0.02) return 0;
        return (int) q;
    }

    private static Etat nouvel(FontSet set) {
        nettoyer();
        ResourceLocation nom = ((FontSetAccesseur) set).heraliz$nom();
        Etat e = new Etat(set, nom != null && PolicesNettes.notre(nom.getNamespace()));
        ETATS.put(set, e);
        return e;
    }

    /** Rend les planches des polices fermées (rechargement des ressources, changement de langue). */
    private static void nettoyer() {
        for (Iterator<Etat> it = ETATS.values().iterator(); it.hasNext(); ) {
            Etat e = it.next();
            if (e.fermee()) {
                e.liberer();
                it.remove();
            }
        }
    }

    private static void couper(Throwable t) {
        actif = false;
        LOG.warn("Lettres nettes coupées : rendu des polices d'avant", t);
        try {
            for (Etat e : ETATS.values()) e.liberer();
        } catch (Throwable ignore) {
            // déjà en repli
        }
        ETATS.clear();
    }

    /** Une police du jeu (FontSet) : ses couches, une par finesse. */
    private static final class Etat {
        final FontSet set;
        final boolean nous;
        final Couche[] couches = new Couche[COUCHES];
        long tour;

        Etat(FontSet set, boolean nous) {
            this.set = set;
            this.nous = nous;
        }

        List<GlyphProvider> fournisseurs() {
            return ((FontSetAccesseur) set).heraliz$fournisseurs();
        }

        Couche couche(int cle) {
            for (Couche c : couches) {
                if (c != null && c.cle == cle) {
                    c.vu = ++tour;
                    return c;
                }
            }
            // une place vide, sinon la finesse la plus anciennement servie (ses planches sont rendues)
            int libre = 0;
            for (int i = 0; i < COUCHES; i++) {
                if (couches[i] == null) {
                    libre = i;
                    break;
                }
                if (couches[i].vu < couches[libre].vu) libre = i;
            }
            if (couches[libre] != null) couches[libre].liberer();
            Couche c = new Couche(cle);
            c.vu = ++tour;
            couches[libre] = c;
            return c;
        }

        /** Un fournisseur TTF de la police est fermé : la police n'est plus celle du jeu. */
        boolean fermee() {
            for (GlyphProvider p : fournisseurs()) {
                if (p instanceof TrueTypeGlyphProvider t && ((TrueTypeAccesseur) t).heraliz$police() == null) return true;
            }
            return false;
        }

        void liberer() {
            for (int i = 0; i < COUCHES; i++) {
                if (couches[i] != null) couches[i].liberer();
                couches[i] = null;
            }
        }
    }

    /** Une lettre tracée, posée sans sa marge gauche comptée deux fois (voir {@link Couche#marge}). */
    private record Decalee(SheetGlyphInfo info, float marge) implements SheetGlyphInfo {
        @Override
        public int getPixelWidth() {
            return info.getPixelWidth();
        }

        @Override
        public int getPixelHeight() {
            return info.getPixelHeight();
        }

        @Override
        public void upload(int x, int y) {
            info.upload(x, y);
        }

        @Override
        public boolean isColored() {
            return info.isColored();
        }

        @Override
        public float getOversample() {
            return info.getOversample();
        }

        @Override
        public float getBearingX() {
            return info.getBearingX() - marge;
        }

        @Override
        public float getBearingY() {
            return info.getBearingY();
        }
    }

    /** Les lettres d'une police à une finesse : copies des fournisseurs TTF et planches à elles. */
    private static final class Couche {
        final int cle;
        final float finesse;
        long vu;
        final Int2ObjectOpenHashMap<Object> glyphes = new Int2ObjectOpenHashMap<>();
        final Map<GlyphProvider, TrueTypeGlyphProvider> copies = new IdentityHashMap<>();
        final List<FontTexture> planches = new ArrayList<>();
        final List<ResourceLocation> noms = new ArrayList<>();

        Couche(int cle) {
            this.cle = cle;
            this.finesse = cle / 4f;
        }

        /** Même choix de fournisseur que FontSet.computeBakedGlyph : le premier qui a la lettre. */
        Object creer(Etat e, int cp) {
            if (e.fermee()) return JEU;
            for (GlyphProvider p : e.fournisseurs()) {
                GlyphInfo info = p.getGlyph(cp);
                if (info == null) continue;
                if (!(p instanceof TrueTypeGlyphProvider ttf) || info instanceof GlyphInfo.SpaceGlyphInfo) return JEU;
                TrueTypeGlyphProvider copie = copie(ttf);
                if (copie == null) return JEU;
                GlyphInfo fine = copie.getGlyph(cp);
                if (fine == null || fine instanceof GlyphInfo.SpaceGlyphInfo) return JEU;
                float marge = marge((TrueTypeAccesseur) ttf, cp);
                BakedGlyph g = fine.bake(lettre -> coudre(new Decalee(lettre, marge)));
                return g == null ? JEU : g;
            }
            return JEU;
        }

        /**
         * Le fournisseur du jeu à une autre finesse : même police en mémoire (jamais fermée par nous : elle reste celle du
         * jeu), même taille ({@code pointScale x (ascendante - descendante) / oversample}), même décalage, mêmes lettres
         * sautées.
         */
        TrueTypeGlyphProvider copie(TrueTypeGlyphProvider ttf) {
            TrueTypeGlyphProvider c = copies.get(ttf);
            if (c != null) return c;
            TrueTypeAccesseur a = (TrueTypeAccesseur) ttf;
            STBTTFontinfo police = a.heraliz$police();
            ByteBuffer memoire = a.heraliz$memoire();
            if (police == null || memoire == null) return null;
            float os = a.heraliz$oversample();
            if (os <= 0) return null;
            int hauteur;
            try (MemoryStack pile = MemoryStack.stackPush()) {
                IntBuffer asc = pile.mallocInt(1), desc = pile.mallocInt(1), ecart = pile.mallocInt(1);
                STBTruetype.stbtt_GetFontVMetrics(police, asc, desc, ecart);
                hauteur = asc.get(0) - desc.get(0);
            }
            if (hauteur <= 0) return null;
            float taille = a.heraliz$echelle() * hauteur / os;
            StringBuilder sautees = new StringBuilder();
            for (IntIterator it = a.heraliz$sautees().iterator(); it.hasNext(); ) sautees.appendCodePoint(it.nextInt());
            c = new TrueTypeGlyphProvider(memoire, police, taille, finesse, a.heraliz$decalageX() / os, a.heraliz$decalageY() / os,
                    sautees.toString());
            copies.put(ttf, c);
            return c;
        }

        /**
         * Marge gauche de la lettre (unités d'interface). Minecraft 1.20.1 la compte deux fois (TrueTypeGlyphProvider.Glyph :
         * bearingX = (lsb + x0 + décalage) / oversample, alors que x0, le bord de la lettre tracée, la contient déjà) : chaque
         * lettre est poussée à droite de sa propre marge, d'où des espacements inégaux (« dujour » : le j, à marge
         * négative, recule sur l'espace). Retirée ici : lettres posées comme la police les dessine, avances inchangées.
         */
        static float marge(TrueTypeAccesseur a, int cp) {
            STBTTFontinfo police = a.heraliz$police();
            float os = a.heraliz$oversample();
            if (police == null || os <= 0) return 0;
            int index = STBTruetype.stbtt_FindGlyphIndex(police, cp);
            if (index == 0) return 0;
            try (MemoryStack pile = MemoryStack.stackPush()) {
                IntBuffer avance = pile.mallocInt(1), lsb = pile.mallocInt(1);
                STBTruetype.stbtt_GetGlyphHMetrics(police, index, avance, lsb);
                return lsb.get(0) * a.heraliz$echelle() / os;
            }
        }

        /** Comme FontSet.stitch, dans nos planches (espace de noms à part : jamais lissées par PolicesNettes). */
        BakedGlyph coudre(SheetGlyphInfo info) {
            if (info.isColored() || info.getPixelWidth() > PLANCHE || info.getPixelHeight() > PLANCHE) return null;
            for (FontTexture t : planches) {
                BakedGlyph g = t.add(info);
                if (g != null) return g;
            }
            if (planches.size() >= PLANCHES_MAX) return null;
            ResourceLocation nom = new ResourceLocation("heraliz_nettete", "planche_" + (++compteur));
            FontTexture t = new FontTexture(GlyphRenderTypes.createForIntensityTexture(nom), false);
            Minecraft.getInstance().getTextureManager().register(nom, t);
            planches.add(t);
            noms.add(nom);
            return t.add(info);
        }

        void liberer() {
            for (ResourceLocation n : noms) Minecraft.getInstance().getTextureManager().release(n);
            noms.clear();
            planches.clear();
            glyphes.clear();
            copies.clear();
        }
    }
}
