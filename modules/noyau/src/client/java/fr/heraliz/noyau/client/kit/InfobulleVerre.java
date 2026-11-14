package fr.heraliz.noyau.client.kit;

import fr.heraliz.noyau.client.BadgeRarete;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Infobulles de nos écrans en verre (inventaire D1 validé le 14/11) :
 * <ul>
 *     <li>objets : l'infobulle du jeu (mêmes lignes que partout, badge de rareté du noyau sous le nom), aux couleurs du
 *     verre : fond 0xF20B0C0F, cadre de la couleur de la rareté qui s'efface vers le bas (blanc atténué sans rareté) ;
 *     seulement quand un {@link EcranVerre} est ouvert ({@link #enregistrer}) ;</li>
 *     <li>nos propres textes (effets, bonus de loot…) : {@link Bulle}, l'infobulle des écrans du clan ({@code Ui.tooltip})
 *     gardée en mémoire : ses lignes ne sont refaites que si leur texte change.</li>
 * </ul>
 */
public final class InfobulleVerre {
    private InfobulleVerre() {
    }

    /** Fond (ARGB), côtés et bas (blanc à 7 %). */
    public static final int FOND = 0xF20B0C0F, COTES = 0x12FFFFFF;
    /** Largeur maximale d'une ligne. */
    private static final int MAX = 240;

    /** Branche les couleurs des infobulles du jeu (partie client du noyau). */
    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, RenderTooltipEvent.Color.class, InfobulleVerre::couleurs);
    }

    private static void couleurs(RenderTooltipEvent.Color e) {
        if (!(Minecraft.getInstance().screen instanceof EcranVerre)) return;
        int rang = BadgeRarete.rang(e.getComponents());
        e.setBackgroundStart(FOND);
        e.setBackgroundEnd(FOND);
        e.setBorderStart(rang >= 0 ? Palette.argb(BadgeRarete.couleur(rang), 200) : Palette.argb(0xFFFFFF, 70));
        e.setBorderEnd(COTES);
    }

    /** Infobulle de nos textes : fond sombre, liseré haut de la couleur d'accent, première ligne en titre. */
    public static final class Bulle {
        private final List<FormattedCharSequence> lignes = new ArrayList<>();
        private int largeur, titre = 1;
        private Object cle;

        /** Vrai si la bulle montre déjà ce contenu (clé comparée par {@code equals}). */
        public boolean montre(Object cle) {
            return cle != null && cle.equals(this.cle);
        }

        /** Nouveau contenu (à n'appeler que s'il change) : lignes coupées à la largeur maximale. */
        public void remplir(Object cle, Font font, List<Component> textes) {
            this.cle = cle;
            lignes.clear();
            largeur = 0;
            titre = 1;
            for (int i = 0; i < textes.size(); i++) {
                List<FormattedCharSequence> morceaux = font.split(textes.get(i), MAX);
                if (morceaux.isEmpty()) lignes.add(FormattedCharSequence.EMPTY);
                else lignes.addAll(morceaux);
                if (i == 0) titre = Math.max(1, morceaux.size());
            }
            for (FormattedCharSequence l : lignes) largeur = Math.max(largeur, font.width(l));
        }

        public void oublier() {
            cle = null;
            lignes.clear();
        }

        /** Largeur du texte (le cadre déborde de 6 de chaque côté). */
        public int largeur() {
            return largeur;
        }

        /** Hauteur du texte (le cadre déborde de 5 en haut et de 3 en bas). */
        public int hauteur() {
            int n = lignes.size();
            return n == 0 ? 0 : 9 + (n - 1) * 10 + (n > titre ? 2 : 0);
        }

        /** Dessine la bulle, texte à partir de (x, y), par-dessus l'écran. */
        public void dessiner(GuiGraphics g, Font font, int x, int y, int accent) {
            int n = lignes.size();
            if (n == 0) return;
            int w = largeur, h = hauteur();
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 400f);
            g.fill(x - 6, y - 5, x + w + 6, y + h + 3, FOND);
            g.fill(x - 6, y - 5, x + w + 6, y - 4, Palette.argb(accent, 200));
            g.fill(x - 6, y - 4, x - 5, y + h + 3, COTES);
            g.fill(x + w + 5, y - 4, x + w + 6, y + h + 3, COTES);
            g.fill(x - 6, y + h + 2, x + w + 6, y + h + 3, COTES);
            int ly = y;
            for (int i = 0; i < n; i++) {
                g.drawString(font, lignes.get(i), x, ly, 0xFFFFFFFF, false);
                ly += i == titre - 1 ? 12 : 10;
            }
            g.pose().popPose();
        }
    }
}
