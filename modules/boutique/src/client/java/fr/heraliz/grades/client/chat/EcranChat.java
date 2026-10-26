package fr.heraliz.grades.client.chat;

import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.reseau.ReseauChat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Chat ouvert HeraliZ (maquette {@code maquettes/staff_chat/5_} validée) : reprend le chat de Minecraft (saisie,
 * historique, suggestions des commandes, Entrée) et ne change que l'affichage : panneau de verre à gauche, onglets
 * Proximité / Global / Clan au-dessus de la saisie (Tab change de canal hors d'une commande, messages non lus), lignes
 * marquées de leur canal, molette pour remonter, liens cliquables.
 */
public final class EcranChat extends ChatScreen {
    private static Field champSuggestions;
    private CommandSuggestions suggestions;
    private int defil;

    public EcranChat(String initial) {
        super(initial);
    }

    @Override
    protected void init() {
        super.init();
        input.setX(8);
        input.setY(height - 12);
        input.setWidth(largeurBas() - 8);
        try {
            if (champSuggestions == null) champSuggestions = ObfuscationReflectionHelper.findField(ChatScreen.class, "f_95577_");
            suggestions = (CommandSuggestions) champSuggestions.get(this);
        } catch (RuntimeException | IllegalAccessException ex) {
            suggestions = null;
        }
        ChatHeraliz.NON_LUS[ChatHeraliz.canal] = 0;
    }

    /**
     * Largeur de la saisie et des onglets : jamais sous la barre d'objets DayZ, centrée en bas (25/10, audit en jeu : en
     * 1080p, échelle 3, la saisie et « TAB pour changer » passaient sous les cases 1 et 2). 140 = demi-largeur de la barre
     * avec ses écarts et sa jauge (dayzhotbar), plus 8 de marge.
     */
    private int largeurBas() {
        return Math.max(120, Math.min(ChatHeraliz.largeur(width), width / 2 - 140 - 8));
    }

    /**
     * Onglets de h-28 à h-17 : 2 de vide au-dessus du panneau de saisie, qui commence à h-15 (26/10 : à h-24, les onglets
     * finissaient à h-13 et le panneau de saisie, dessiné après, couvrait leur bas et leur bordure).
     */
    private float yOnglets() {
        return height - 28;
    }

    private float basLignes() {
        return height - 56;   // au-dessus des cœurs et de l'armure (25/10, audit en jeu)
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int w = ChatHeraliz.largeur(width);
        int[] total = new int[1];
        List<ChatHeraliz.Place> places = ChatHeraliz.places(font, w, 10, basLignes(), defil, total);
        defil = Math.max(0, Math.min(defil, Math.max(0, total[0] - ChatHeraliz.LIGNES_OUVERT)));
        // panneau des lignes
        if (!places.isEmpty()) {
            float haut = basLignes() - (places.size() - 0.5F) * 10 - 3;
            Trace.rect(g, 4, haut, 4 + w, basLignes() + 8, a(0x0D0F14, 190));
            Trace.bord(g, 4, haut, w, basLignes() + 8 - haut, 1 / 3F, a(0xFFFFFF, 26));
            for (ChatHeraliz.Place p : places) {
                if (p.premier()) ChatHeraliz.pastille(g, font, p.ligne().canal, 10, p.cy(), 1);
                ChatHeraliz.rang(g, font, p.rang(), p.x(), p.cy(), 1, false);
            }
            if (total[0] > ChatHeraliz.LIGNES_OUVERT) {
                float hh = basLignes() + 8 - haut, part = (float) ChatHeraliz.LIGNES_OUVERT / total[0];
                float yb = haut + (1 - part) * hh * (1 - (float) defil / Math.max(1, total[0] - ChatHeraliz.LIGNES_OUVERT));
                Trace.rect(g, 4 + w - 1.5F, yb, 4 + w - 0.5F, yb + hh * part, a(0xFFFFFF, 70));
            }
        }
        onglets(g, mx, my);
        // saisie
        int lb = largeurBas();
        Trace.rect(g, 4, height - 15, 4 + lb, height - 2, a(0x0D0F14, 215));
        Trace.bord(g, 4, height - 15, lb, 13, 1 / 3F, a(0xFFFFFF, 40));
        if (!aideACote && input.getValue().isEmpty()) {
            // pas la place à côté des onglets : l'aide se met dans la case de saisie vide, à droite
            String aide = Component.translatable("gui.heraliz_grades.chat.aide_tab").getString();
            float tw = Trace.largeur(font, aide, ChatHeraliz.MICRO);
            Trace.m(g, font, aide, 4 + lb - 6 - tw, height - 8.5F, ChatHeraliz.MICRO, a(0x8F95A0, 170), 0);
        }
        input.render(g, mx, my, pt);
        if (suggestions != null) suggestions.render(g, mx, my);
        Style s = ChatHeraliz.styleA(font, places, mx, my);
        if (s != null && s.getHoverEvent() != null) g.renderComponentHoverEffect(font, s, mx, my);
    }

    private static final String[] ONGLETS = {"proximite", "global", "clan"};
    /** L'aide « TAB pour changer » tient à côté des onglets (calculé à chaque image). */
    private boolean aideACote = true;

    /** Onglets ; rend la zone de chacun pour le clic : x0, x1 par canal. */
    private float[] zonesOnglets() {
        float[] z = new float[ReseauChat.NOMBRE * 2];
        float x = 4;
        for (int c = 0; c < ReseauChat.NOMBRE; c++) {
            String nom = Component.translatable("gui.heraliz_grades.chat.onglet." + ONGLETS[c]).getString();
            float tw = Trace.largeur(font, nom, ChatHeraliz.ONGLET) + 12;
            if (ChatHeraliz.NON_LUS[c] > 0 && c != ChatHeraliz.canal) tw += 10;
            z[c * 2] = x;
            z[c * 2 + 1] = x + tw;
            x += tw + 3;
        }
        return z;
    }

    private void onglets(GuiGraphics g, int mx, int my) {
        float[] z = zonesOnglets();
        float y = yOnglets(), h = 11;
        boolean clan = ChatHeraliz.aUnClan();
        int[] couleur = {0xA0A6B0, 0xE6E8EC, 0x6FD08C};
        for (int c = 0; c < ReseauChat.NOMBRE; c++) {
            float x0 = z[c * 2], x1 = z[c * 2 + 1];
            boolean actif = c == ChatHeraliz.canal, hv = mx >= x0 && mx < x1 && my >= y && my < y + h, eteint = c == ReseauChat.CLAN && !clan;
            int col = couleur[c];
            Trace.rect(g, x0, y, x1, y + h, a(actif ? col : 0x0D0F14, actif ? 60 : hv ? 200 : 170));
            Trace.bord(g, x0, y, x1 - x0, h, 1 / 3F, a(actif ? col : 0xFFFFFF, actif ? 220 : 50));
            String nom = Component.translatable("gui.heraliz_grades.chat.onglet." + ONGLETS[c]).getString();
            float tx = x0 + 6;
            Trace.m(g, font, nom, tx, y + h / 2, ChatHeraliz.ONGLET, a(eteint ? 0x5A5F69 : actif ? 0xFFFFFF : 0xC3C8D0, 255), 0);
            if (ChatHeraliz.NON_LUS[c] > 0 && !actif) {
                float bx = x1 - 9;
                Trace.disque(g, bx + 2.5F, y + h / 2, 3.5F, a(0xE3201B, 255));
                Trace.c(g, font, String.valueOf(Math.min(9, ChatHeraliz.NON_LUS[c])), bx + 2.5F, y + h / 2, ChatHeraliz.MICRO, a(0xFFFFFF, 255), 0);
            }
        }
        String aide = Component.translatable("gui.heraliz_grades.chat.aide_tab").getString();
        float xi = z[z.length - 1] + 8, place = largeurBas() + 4 - xi;
        // l'aide entière à côté des onglets, sinon dans la case de saisie (jamais coupée, jamais sous la barre d'objets)
        aideACote = place >= Trace.largeur(font, aide, ChatHeraliz.MICRO);
        if (aideACote) Trace.m(g, font, aide, xi, y + h / 2, ChatHeraliz.MICRO, a(0x8F95A0, 200), 0);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB && !input.getValue().startsWith("/")) {
            ChatHeraliz.suivant();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            defil += ChatHeraliz.LIGNES_OUVERT - 1;
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            defil = Math.max(0, defil - (ChatHeraliz.LIGNES_OUVERT - 1));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (suggestions != null && suggestions.mouseScrolled(Math.max(-1, Math.min(1, delta)))) return true;
        defil = Math.max(0, defil + (delta > 0 ? 1 : -1) * (hasShiftDown() ? 1 : 3));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (suggestions != null && suggestions.mouseClicked((int) mx, (int) my, b)) return true;
        if (b == 0) {
            float[] z = zonesOnglets();
            float y = yOnglets();
            if (my >= y && my < y + 11) {
                for (int c = 0; c < ReseauChat.NOMBRE; c++) {
                    if (mx >= z[c * 2] && mx < z[c * 2 + 1]) {
                        if (c != ReseauChat.CLAN || ChatHeraliz.aUnClan()) ChatHeraliz.choisir(c);
                        return true;
                    }
                }
            }
            List<ChatHeraliz.Place> places = ChatHeraliz.places(font, ChatHeraliz.largeur(width), 10, basLignes(), defil, null);
            Style s = ChatHeraliz.styleA(font, places, mx, my);
            if (s != null && handleComponentClicked(s)) return true;
        }
        return input.mouseClicked(mx, my, b);
    }
}
