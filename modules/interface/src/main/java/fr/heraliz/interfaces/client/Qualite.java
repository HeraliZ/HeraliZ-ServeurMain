package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import java.util.Locale;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.inventory.InventoryMenu;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Qualite graphique (ecran {@link EcranGraphismes} et tuile du menu Echap) : trois profils qui posent d'un coup des
 * reglages de Minecraft (enregistres dans options.txt comme si le joueur les avait changes dans Options &gt; Graphismes)
 * et le filtrage anisotrope de la grande texture des blocs (net en vue rasante). Le profil choisi est garde dans
 * config/heraliz_interface-client.toml ({@link InterfaceConfig#PROFIL_GRAPHIQUE}) ; depuis le 25/10 (choix du
 * proprietaire), Performance est pose une fois chez tout le monde ({@link ReglagesDefaut} version 4). Tous les profils
 * coupent la synchronisation verticale. Le brouillard n'est jamais touche (ambiance et equite entre joueurs) ; la
 * distance de vue reste plafonnee a 8 par {@link InterfaceClient}.
 * <p>Les mipmaps (petites versions des textures pour le loin) se changent en dernier et une seule fois : Minecraft doit
 * recharger toutes les textures. Comme l'ecran Video de Minecraft, on attend que l'ecran se ferme
 * ({@link #appliquerMipmaps()}) : plusieurs clics de suite ne rechargent qu'une fois.
 */
public final class Qualite {
    private Qualite() {
    }

    public enum Profil {
        PERFORMANCE("performance", GraphicsStatus.FAST, 6, ParticleStatus.MINIMAL, 2, CloudStatus.OFF, 0, 0, false, false, 0.75),
        EQUILIBRE("equilibre", GraphicsStatus.FANCY, 8, ParticleStatus.DECREASED, 4, CloudStatus.FAST, 1, 4, false, true, 1.0),
        QUALITE("qualite", GraphicsStatus.FANCY, 8, ParticleStatus.ALL, 4, CloudStatus.FANCY, 2, 8, true, true, 1.0);

        /** Nom garde dans la config (et fin de la cle de traduction). */
        public final String cle;
        final GraphicsStatus graphismes;
        final int distance;
        final ParticleStatus particules;
        final int mipmaps;
        /** Nos nuages doux ne regardent que « coupes ou non » ; FAST / FANCY ne sert qu'au secours (nuages de Minecraft). */
        final CloudStatus nuages;
        final int melange;
        /** Filtrage anisotrope de la grande texture des blocs (0 : aucun). */
        final int anisotrope;
        /** Ombres sous les entites. */
        final boolean ombres;
        /** Lumiere douce (occlusion ambiante) : la changer reconstruit les troncons. */
        final boolean lumiereDouce;
        /** Distance d'affichage des entites (1 = normale). */
        final double entites;

        Profil(String cle, GraphicsStatus graphismes, int distance, ParticleStatus particules, int mipmaps, CloudStatus nuages,
               int melange, int anisotrope, boolean ombres, boolean lumiereDouce, double entites) {
            this.cle = cle;
            this.graphismes = graphismes;
            this.distance = distance;
            this.particules = particules;
            this.mipmaps = mipmaps;
            this.nuages = nuages;
            this.melange = melange;
            this.anisotrope = anisotrope;
            this.ombres = ombres;
            this.lumiereDouce = lumiereDouce;
            this.entites = entites;
        }

        static Profil de(String cle) {
            for (Profil p : values()) {
                if (p.cle.equals(cle)) return p;
            }
            return PERFORMANCE;
        }
    }

    /** Mipmaps a poser a la fermeture du menu (-1 : rien). */
    private static int mipmapsEnAttente = -1;
    private static boolean anisotropeSignale;

    /** Profil choisi (Performance si la config n'est pas lisible). */
    public static Profil actuel() {
        return Profil.de(InterfaceConfig.texte(InterfaceConfig.PROFIL_GRAPHIQUE, Profil.PERFORMANCE.cle).toLowerCase(Locale.ROOT));
    }

    /** Vrai si un rechargement des textures attend la fermeture de l'ecran (mipmaps du profil choisi). */
    public static boolean rechargementEnAttente() {
        return mipmapsEnAttente >= 0;
    }

    /** Cran du profil choisi : 0 Performance, 1 Equilibre, 2 Qualite (interrupteur du menu Echap). */
    public static int cran() {
        return actuel().ordinal();
    }

    /** Profil suivant (apres Qualite : Performance), pose tout de suite. */
    public static void suivant() {
        Profil[] t = Profil.values();
        appliquer(t[(cran() + 1) % t.length]);
    }

    /** Pose un profil : tout de suite sauf les mipmaps (a la fermeture de l'ecran). */
    public static void appliquer(Profil p) {
        poser(p, true);
    }

    /**
     * Reglages par defaut ({@link ReglagesDefaut}, au premier tick du jeu) : meme chose, mais les mipmaps sont seulement
     * ecrites (prises au prochain chargement des textures, sans en provoquer un) et le filtrage anisotrope attend le
     * chargement des ressources.
     */
    static void parDefaut(Profil p) {
        poser(p, false);
    }

    private static void poser(Profil p, boolean enJeu) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        boolean graphismesChanges = o.graphicsMode().get() != p.graphismes;
        if (graphismesChanges) o.graphicsMode().set(p.graphismes);
        o.renderDistance().set(Math.min(p.distance, InterfaceClient.VUE_MAX));   // son rappel redemande les tronçons
        o.particles().set(p.particules);
        o.cloudStatus().set(p.nuages);
        o.entityShadows().set(p.ombres);
        o.entityDistanceScaling().set(p.entites);
        o.enableVsync().set(false);                                            // jamais de synchronisation verticale
        boolean lumiereAvant = o.ambientOcclusion().get();
        o.ambientOcclusion().set(p.lumiereDouce);                              // son rappel reconstruit les tronçons
        int melangeAvant = o.biomeBlendRadius().get();
        o.biomeBlendRadius().set(p.melange);                                   // son rappel reconstruit les tronçons
        // changer Rapides / Detailles demande de reconstruire les tronçons (feuilles opaques ou non), comme l'ecran Video ;
        // une seule fois si le melange des biomes ou la lumiere douce l'ont deja fait
        if (graphismesChanges && melangeAvant == p.melange && lumiereAvant == p.lumiereDouce) mc.levelRenderer.allChanged();
        if (enJeu) {
            mipmapsEnAttente = o.mipmapLevels().get() != p.mipmaps ? p.mipmaps : -1;
        } else {
            o.mipmapLevels().set(p.mipmaps);
        }
        o.save();
        try {
            InterfaceConfig.PROFIL_GRAPHIQUE.set(p.cle);
            InterfaceConfig.PROFIL_GRAPHIQUE.save();
        } catch (Exception e) {
            HeralizInterface.LOG.warn("[interface] profil graphique non enregistre : {}", e.toString());
        }
        if (enJeu) anisotrope();
        HeralizInterface.LOG.info("[interface] qualite graphique : {}{}", p.cle, enJeu ? "" : " (reglage par defaut)");
    }

    /**
     * Fermeture du menu Echap ou de l'ecran Graphismes : mipmaps du dernier profil choisi, puis rechargement des textures (une seule fois, comme
     * l'ecran Video de Minecraft). Le filtrage anisotrope est repose apres le rechargement ({@link InterfaceClient}).
     */
    public static void appliquerMipmaps() {
        int m = mipmapsEnAttente;
        mipmapsEnAttente = -1;
        if (m < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.mipmapLevels().get() == m) return;
        mc.options.mipmapLevels().set(m);
        mc.options.save();
        mc.updateMaxMipLevel(m);
        mc.delayTextureReload();
    }

    /**
     * Filtrage anisotrope de la grande texture des blocs selon le profil (aucun en Performance, x4 en Equilibre, x8 en
     * Qualite), si la carte graphique le gere : appele apres chaque rechargement des ressources et a chaque changement
     * de profil (fil de rendu).
     */
    public static void anisotrope() {
        try {
            GLCapabilities caps = GL.getCapabilities();
            if (!caps.GL_EXT_texture_filter_anisotropic && !caps.GL_ARB_texture_filter_anisotropic && !caps.OpenGL46) {
                if (!anisotropeSignale) {
                    anisotropeSignale = true;
                    HeralizInterface.LOG.info("[interface] filtrage anisotrope non gere par la carte graphique");
                }
                return;
            }
            int voulu = actuel().anisotrope;
            float max = GL11.glGetFloat(0x84FF);                                  // GL_MAX_TEXTURE_MAX_ANISOTROPY
            float valeur = voulu <= 1 ? 1f : Math.max(1f, Math.min(voulu, max));
            TextureAtlas blocs = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            blocs.bind();
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE, valeur);              // GL_TEXTURE_MAX_ANISOTROPY
        } catch (Throwable t) {
            if (!anisotropeSignale) {
                anisotropeSignale = true;
                HeralizInterface.LOG.warn("[interface] filtrage anisotrope non pose : {}", t.toString());
            }
        }
    }
}
