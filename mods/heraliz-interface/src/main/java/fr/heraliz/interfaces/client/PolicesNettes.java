package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Lettres nettes dans toutes nos interfaces (écrans et HUD, 22/10).
 * <p>
 * Minecraft lit les planches de lettres des polices au point près (GL_NEAREST). Nos polices TTF (DM Sans, Onest) sont
 * tracées à {@code oversample} points par unité d'interface : affichées à l'échelle 3 ou 4, le jeu saute des colonnes de
 * points et les bords des lettres « mangés » donnent un texte pixelisé. Seule la fiche {@code /base info} de CRac était
 * nette, parce qu'elle lisse ses lettres (GL_LINEAR, {@code TexteLisseMixin} de CRac). Ici, on lisse partout, mais
 * seulement les planches des polices TTF de NOS mods :
 * <ul>
 * <li>{@link #noter} (appelé à l'enregistrement d'une texture) retient l'identifiant OpenGL de chaque planche de lettres
 * ({@link FontTexture}, nommée « espace:police/n » par Minecraft) dont l'espace de noms est un des nôtres ;</li>
 * <li>{@link #apresRendu} (fin de la préparation d'un rendu de texte) passe la planche liée en GL_LINEAR si c'est une de
 * celles-là et si le rendu est un rendu « intensité » (lettres TTF ; les lettres en images, comme celles de la police de
 * Minecraft reprises en secours par nos polices, sont rangées dans d'autres planches « en couleur » et restent nettes au
 * point près).</li>
 * </ul>
 * Minecraft remet GL_NEAREST lui-même à chaque préparation du rendu (TextureStateShard), d'où le réglage à chaque fois.
 * Aucune allocation par appel : un tableau d'entiers lu sans verrou, recopié seulement quand une planche s'ajoute.
 */
public final class PolicesNettes {
    private PolicesNettes() {
    }

    /**
     * Espaces de noms qui ont des polices dans nos mods ({@code assets/<espace>/font/*.json}). Liste tenue à jour par
     * {@code python outils/polices_nettes.py}, qui signale tout espace de noms de police absent d'ici.
     */
    private static final Set<String> NOS_ESPACES = Set.of(
            "airstrike", "caracrea", "crac", "heralisz_camo", "heralisz_clans", "heralisz_economie",
            "heralisz_utils", "heraliz_armory", "heraliz_boss", "heraliz_clansetbases", "heraliz_corps", "heraliz_emote",
            "heraliz_extraction", "heraliz_gabarit", "heraliz_grades", "heraliz_interface", "heraliz_ping",
            "heraliz_quetes", "heraliz_tactique", "heraliz_usure", "heralizcasino", "heralizcrates",
            "reanimation");

    /** Planche de lettres retenue, par nom (« espace:police/n ») : un rechargement des ressources les réenregistre. */
    private static final Map<ResourceLocation, Integer> PAR_NOM = new HashMap<>();
    /** Identifiants OpenGL des planches de lettres de nos polices (trié, recopié à chaque changement). */
    private static volatile int[] planches = new int[0];

    /**
     * À l'enregistrement d'une texture : retient les planches de lettres de nos polices. Une planche réenregistrée sous le
     * même nom (rechargement des ressources, changement de langue) remplace l'ancienne, dont l'identifiant est oublié.
     */
    public static void noter(ResourceLocation nom, AbstractTexture texture) {
        if (nom == null || !NOS_ESPACES.contains(nom.getNamespace())) {
            return;
        }
        synchronized (PAR_NOM) {
            Integer avant;
            if (texture instanceof FontTexture) {
                int id = texture.getId();
                if (id <= 0) {
                    return;
                }
                avant = PAR_NOM.put(nom, id);
                if (avant != null && avant == id) {
                    return;
                }
            } else {
                avant = PAR_NOM.remove(nom);
                if (avant == null) {
                    return;
                }
            }
            int[] nouvelles = new int[PAR_NOM.size()];
            int i = 0;
            for (int id : PAR_NOM.values()) {
                nouvelles[i++] = id;
            }
            Arrays.sort(nouvelles);
            planches = nouvelles;
        }
    }

    /** À la fin de la préparation d'un rendu ({@code RenderStateShard.setupRenderState}) de nom {@code nom}. */
    public static void apresRendu(String nom) {
        // Rendus de lettres TTF : « text_intensity », « text_intensity_see_through », « text_intensity_polygon_offset ».
        if (nom == null || !nom.startsWith("text_intensity")) {
            return;
        }
        int[] ids = planches;
        if (ids.length == 0) {
            return;
        }
        int id = RenderSystem.getShaderTexture(0);
        if (id <= 0 || Arrays.binarySearch(ids, id) < 0) {
            return;
        }
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(3553, 10241, 9729); // GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR
        GlStateManager._texParameter(3553, 10240, 9729); // GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR
    }
}
