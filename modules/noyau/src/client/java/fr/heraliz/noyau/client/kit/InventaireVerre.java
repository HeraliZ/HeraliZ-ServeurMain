package fr.heraliz.noyau.client.kit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix4f;

/**
 * « Ton inventaire » dans nos écrans en verre, le même partout (inventaire D1, sac à dos, fouille, coffres ; validés le
 * 14/11) : « Sur toi » (les 27 cases) puis « Accès rapide » (la barre de 9, numérotée). Cases de {@link #CADRE} au pas de
 * {@link #PAS}. Ici : la place des cases du joueur dans n'importe quel menu ({@link #placer}), et les étiquettes et numéros
 * ({@link #etiquettes}) ; les cases se dessinent avec les autres ({@link CaseVerre}).
 */
public final class InventaireVerre {
    /** Côté d'une case, pas entre deux cases, marge entre le bord de la case et l'objet. */
    public static final int CADRE = 20, PAS = 22, PAD = 2;
    /** Largeur des 9 colonnes et hauteur des 3 rangées. */
    public static final int LARGEUR = 9 * PAS - (PAS - CADRE), HAUTEUR = 3 * PAS - (PAS - CADRE);
    /** Milieu des étiquettes au-dessus du haut des cases. */
    public static final int ETIQUETTE = 7;

    private final TexteHud surToi = new TexteHud(PoliceHud.MINI_GRAS, 0.9f), accesRapide = new TexteHud(PoliceHud.MINI_GRAS, 0.9f);
    private int x, yGrille, yBarre;

    /** Coin haut gauche de la grille (27 cases) et haut de la barre, depuis le coin du panneau. */
    public void disposer(int x, int yGrille, int yBarre) {
        this.x = x;
        this.yGrille = yGrille;
        this.yBarre = yBarre;
    }

    public int x() {
        return x;
    }

    public int yGrille() {
        return yGrille;
    }

    public int yBarre() {
        return yBarre;
    }

    /** Textes dans la langue du joueur (à l'ouverture de l'écran). */
    public void textes() {
        surToi.texte(Component.translatableWithFallback("noyau.verre.sur_toi", "Sur toi").getString());
        accesRapide.texte(Component.translatableWithFallback("noyau.verre.acces_rapide", "Accès rapide").getString());
    }

    /** Pose une case de l'inventaire du joueur (barre ou 27 cases) ; faux pour toute autre case. */
    public boolean placer(Slot s, Inventory inv) {
        if (s.container != inv) return false;
        int cs = s.getContainerSlot();
        if (cs >= 0 && cs < 9) {
            CasesEcran.placer(s, x + PAD + cs * PAS, yBarre + PAD);
            return true;
        }
        if (cs >= 9 && cs < 36) {
            int k = cs - 9;
            CasesEcran.placer(s, x + PAD + (k % 9) * PAS, yGrille + PAD + (k / 9) * PAS);
            return true;
        }
        return false;
    }

    /** Étiquettes et numéros de la barre (textes envoyés au tampon), panneau en (px, py). */
    public void etiquettes(GuiGraphics g, int px, int py) {
        int c = Palette.argb(Palette.ATTENUE, 255);
        surToi.ajouter(g, px + x, py + yGrille - ETIQUETTE, c, TexteHud.GAUCHE, false);
        accesRapide.ajouter(g, px + x, py + yBarre - ETIQUETTE, c, TexteHud.GAUCHE, false);
        for (int i = 0; i < 9; i++) CaseVerre.numero(g, i + 1, px + x + i * PAS, py + yBarre);
    }

    /** Filet discret au-dessus de « Sur toi » (séparation d'avec le contenu), de x0 à x1 (absolus). */
    public void filet(VertexConsumer vc, Matrix4f m, float x0, float x1, int py) {
        float y = py + yGrille - ETIQUETTE - 9;
        Formes.rect(vc, m, x0, y, x1, y + Formes.pixel(), Palette.argb(0xFFFFFF, 15));
    }
}
