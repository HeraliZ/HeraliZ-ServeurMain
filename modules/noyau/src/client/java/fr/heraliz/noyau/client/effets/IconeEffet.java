package fr.heraliz.noyau.client.effets;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffect;
import org.joml.Matrix4f;

/**
 * Un effet comme nos HUD le montrent (V2 choisi le 14/11) : son icône à la taille d'un cœur (9 x 9), sans cadre, dans un
 * anneau fin : il se vide avec le temps (vert pour un bonus, rouge pour un malus, doré pour le bonus de loot), plein et
 * ambre pour un effet de zone, plein et rouge avec un petit point rouge qui respire pour ce qu'il faut soigner.
 * <p>Ordre de dessin (les formes partent au tampon, les icônes sont dessinées tout de suite) : {@link #anneau} pour
 * tous, vidage ({@code g.flush()}), {@link #icone} pour tous, puis {@link #point} et vidage. Centres sur un demi-point
 * d'interface (x,5) : l'icône tombe sur des points entiers.
 */
public final class IconeEffet {
    private IconeEffet() {
    }

    /** Rayons de l'anneau, côté de l'icône, écart entre deux effets. */
    public static final float R_INT = 5.7f, R_EXT = 6.25f, TAILLE = 9f, PAS = 15f;
    /** Or du bonus de loot, rouge du point « à soigner ». */
    public static final int OR = 0xE8C36A, ROUGE_POINT = 0xFF3B30;

    /** Couleur (RVB) d'une catégorie ({@link EffetsJoueur#SOIN} …). */
    public static int couleur(int categorie) {
        return switch (categorie) {
            case EffetsJoueur.SOIN, EffetsJoueur.MALUS -> Palette.DANGER;
            case EffetsJoueur.ZONE -> Palette.ALERTE;
            case EffetsJoueur.LOOT -> OR;
            default -> Palette.OK;
        };
    }

    /** Anneau de l'effet centré en (cx, cy) : piste discrète, puis la part qui reste. {@code ombre} : sur le monde. */
    public static void anneau(VertexConsumer vc, Matrix4f m, float cx, float cy, int categorie, float reste, boolean ombre) {
        float px = Formes.pixel();
        if (ombre) Formes.arc(vc, m, cx, cy, R_INT - 0.1f, R_EXT + 0.1f, 0f, 1f, Palette.argb(0x000000, 71), px);
        Formes.arc(vc, m, cx, cy, R_INT, R_EXT, 0f, 1f, Palette.argb(0xFFFFFF, ombre ? 36 : 31), px);
        int c = couleur(categorie);
        if (categorie == EffetsJoueur.SOIN) Formes.arc(vc, m, cx, cy, R_INT, R_EXT, 0f, 1f, Palette.argb(c, 242), px);
        else if (categorie == EffetsJoueur.ZONE) Formes.arc(vc, m, cx, cy, R_INT, R_EXT, 0f, 1f, Palette.argb(c, 230), px);
        else Formes.arc(vc, m, cx, cy, R_INT, R_EXT, 1f - reste, 1f, Palette.argb(c, 242), px);
    }

    /** Contour plus clair autour de l'anneau (effet survolé dans l'inventaire). */
    public static void survol(VertexConsumer vc, Matrix4f m, float cx, float cy) {
        Formes.arc(vc, m, cx, cy, 6.7f, 7.2f, 0f, 1f, Palette.argb(0xFFFFFF, 217), Formes.pixel());
    }

    /** Icône de l'effet (texture du jeu, 9 x 9) centrée en (cx, cy) ; mélange déjà mis par l'appelant. */
    public static void icone(GuiGraphics g, MobEffect effet, float cx, float cy) {
        TextureAtlasSprite s = Minecraft.getInstance().getMobEffectTextures().get(effet);
        g.pose().pushPose();
        g.pose().translate(cx - TAILLE / 2f, cy - TAILLE / 2f, 0f);
        g.blit(0, 0, 0, 9, 9, s);
        g.pose().popPose();
    }

    /** Point rouge « à soigner », en haut à droite de l'icône ; {@code pulsation} de 0,6 à 1 ({@link EffetsJoueur#pulsation}). */
    public static void point(VertexConsumer vc, Matrix4f m, float cx, float cy, float pulsation) {
        float x = cx + 4.6f, y = cy - 4.6f, r = 1.2f, px = Formes.pixel();
        Formes.halo(vc, m, x, y, r * 3.2f, Palette.argb(ROUGE_POINT, Math.round(77 * pulsation)));
        Formes.disque(vc, m, x, y, r + 0.45f, Palette.argb(0x1A0606, 217), px);
        Formes.disque(vc, m, x, y, r, Palette.argb(ROUGE_POINT, Math.round(255 * pulsation)), px);
    }
}
