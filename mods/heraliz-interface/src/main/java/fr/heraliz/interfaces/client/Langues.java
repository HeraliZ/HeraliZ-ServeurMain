package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.kit.Anim;
import fr.heraliz.interfaces.kit.Sons;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Les trois langues du serveur (francais par defaut, anglais, russe), choisies d'un clic sur un drapeau : ecran titre et
 * menu Echap. Le choix est celui de Minecraft (options.txt) : menus, descriptions et messages traduits du serveur
 * suivent. Meme enchainement que l'ecran des langues de Minecraft (rechargement des ressources).
 */
public final class Langues {
    private Langues() {
    }

    /** Ordre des drapeaux de textures/gui/drapeaux.png (48 x 32 chacun). */
    public static final List<String> CODES = List.of("fr_fr", "en_us", "ru_ru");
    private static final ResourceLocation DRAPEAUX = HeralizInterface.id("textures/gui/drapeaux.png");

    /** Indice de la langue du jeu parmi les trois (-1 : une autre, choisie dans les options de Minecraft). */
    public static int actuelle() {
        return CODES.indexOf(Minecraft.getInstance().getLanguageManager().getSelected());
    }

    /** Passe le jeu dans cette langue (rien si c'est deja elle). */
    public static void choisir(String code) {
        Minecraft mc = Minecraft.getInstance();
        if (code.equals(mc.getLanguageManager().getSelected())) return;
        mc.options.languageCode = code;
        mc.getLanguageManager().setSelected(code);
        mc.reloadResourcePacks();
        mc.options.save();
    }

    /**
     * Trois drapeaux cliquables : celui de la langue du jeu encadre de l'accent, les autres attenues. Avec un libelle,
     * dessine comme une petite tuile du menu Echap (« LANGUE » a gauche, drapeaux a droite).
     */
    public static final class Selecteur extends AbstractWidget {
        private static final int ECART = 4;
        private final int accent;
        @Nullable
        private final String libelle;
        private final int dw, dh;
        private float survol;
        private final Anim.Horloge horloge = new Anim.Horloge();
        public float opacite = 1f, decalageY = 0f;

        /** Drapeaux de dw x dh ; largeur totale = libelle eventuel + 3 drapeaux. */
        public Selecteur(int x, int y, int w, int h, int dw, @Nullable String libelle, int accent) {
            super(x, y, w, h, Component.translatable("options.language"));
            this.dw = dw;
            this.dh = Math.round(dw * 2 / 3f);
            this.libelle = libelle;
            this.accent = accent & 0xFFFFFF;
        }

        /** Largeur occupee par les trois drapeaux. */
        public static int largeurDrapeaux(int dw) {
            return 3 * dw + 2 * ECART;
        }

        private int premierX() {
            return getX() + getWidth() - (libelle != null ? 8 : 0) - largeurDrapeaux(dw);
        }

        private int drapeauY() {
            return Math.round(getY() + decalageY) + (getHeight() - dh) / 2;
        }

        /** Drapeau sous la souris (-1 : aucun). */
        private int sous(double mx, double my) {
            int y = drapeauY();
            if (my < y - 2 || my > y + dh + 2) return -1;
            for (int i = 0; i < 3; i++) {
                int x = premierX() + i * (dw + ECART);
                if (mx >= x - 2 && mx <= x + dw + 2) return i;
            }
            return -1;
        }

        @Override
        public void onClick(double mx, double my) {
            int i = sous(mx, my);
            if (i >= 0) choisir(CODES.get(i));
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            Sons.jouer(Sons.CLIC, 0.6f);
        }

        private int a(int alpha) {
            return Math.round(alpha * opacite);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (opacite < 0.06f) return;
            int choisi = actuelle(), vise = active && isHoveredOrFocused() ? sous(mouseX, mouseY) : -1;
            survol = Anim.vers(survol, vise >= 0 ? 1f : 0f, horloge.dt(), 16f);
            int x = getX(), w = getWidth(), h = getHeight();
            int y = Math.round(getY() + decalageY);
            if (libelle != null) {
                // comme une Tuile du menu Echap : fond leger, contour, libelle en petites capitales
                Font font = Minecraft.getInstance().font;
                Ui.soft(g, x, y, w, h, Ui.argb(0xFFFFFF, a((int) (5 + 6 * survol))));
                Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.mix(0xFFFFFF, accent, survol), a((int) (24 + 50 * survol))));
                Ui.spaced(g, font, libelle, Ui.SMALL, x + 8, y + (h - 6) / 2f, Ui.argb(Ui.mix(Ui.TEXT, Ui.MUTED, 0.25f), a(255)), 0.8f);
            }
            int dy = drapeauY();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (int i = 0; i < 3; i++) {
                int dx = premierX() + i * (dw + ECART);
                boolean actif = i == choisi, survole = i == vise;
                if (actif) {
                    Ui.soft(g, dx - 2, dy - 2, dw + 4, dh + 4, Ui.argb(accent, a(60)));
                    Ui.softOutline(g, dx - 1, dy - 1, dw + 2, dh + 2, Ui.argb(accent, a(255)));
                } else if (survole) {
                    Ui.softOutline(g, dx - 1, dy - 1, dw + 2, dh + 2, Ui.argb(0xFFFFFF, a(160)));
                }
                float alpha = (actif ? 1f : survole ? 0.9f : 0.5f) * opacite;
                g.setColor(1f, 1f, 1f, alpha);
                g.blit(DRAPEAUX, dx, dy, dw, dh, i * 48f, 0f, 48, 32, 144, 32);
                g.setColor(1f, 1f, 1f, 1f);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
