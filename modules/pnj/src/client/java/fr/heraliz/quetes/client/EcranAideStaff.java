package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * « Appeler le staff » pendant le tutoriel (03/11, réponse du propriétaire : « aucune commande pendant le tutoriel, SAUF
 * pouvoir appeler le staff ») : le chat reste caché ({@link ChatTutoriel}) ; T ou / ouvrent cette petite fenêtre à la
 * place. On écrit son problème, Entrée ou « Envoyer » joue {@code /aide <message>} (HeraliZ Grades : staff connecté et
 * salon Discord du staff, une fois par minute au plus). Aucune autre commande ne peut partir d'ici.
 */
final class EcranAideStaff extends Screen {
    private static final int MAX = 160, PW = 280, PH = 150;
    private String texte = "";
    private int px, py;

    private EcranAideStaff() {
        super(Component.translatable("gui.heraliz_quetes.aide_staff.titre"));
    }

    /** La fenêtre qui s'ouvre à la place du chat (T, /) tant qu'il est caché. */
    static EcranAideStaff nouveau() {
        SonsUi.ouvrir(0.5F);
        return new EcranAideStaff();
    }

    @Override
    protected void init() {
        Echelle.forcer(this, PW, PH);
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        int bw = 96, y = py + PH - 32;
        addRenderableWidget(new HzButton(px + PW / 2 - bw - 4, y, bw, 20, Component.translatable("gui.heraliz_quetes.aide_staff.annuler"), Ui.MUTED,
                this::onClose));
        addRenderableWidget(new HzButton(px + PW / 2 + 4, y, bw, 20, Component.translatable("gui.heraliz_quetes.aide_staff.envoyer"), Ui.OK,
                this::envoyer).look(HzButton.Look.PRIMARY));
    }

    private void envoyer() {
        String m = texte.strip();
        var co = Minecraft.getInstance().getConnection();
        if (m.isEmpty() || co == null) return;
        co.sendCommand("aide " + m);
        SonsUi.clic(0.8F);
        onClose();
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (c >= 32 && c != '§' && texte.length() < MAX) {
            texte += c;
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!texte.isEmpty()) texte = texte.substring(0, texte.offsetByCodePoints(texte.length(), -1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            envoyer();
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null) for (char c : clip.replace('\n', ' ').replace('\r', ' ').toCharArray()) charTyped(c, 0);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, PW, PH, 225);
        String titre = TexteClient.tr("gui.heraliz_quetes.aide_staff.titre");
        Ui.spacedCentered(g, f, Ui.ellipsisEspace(f, titre, Ui.SMALL, 0.8f, PW - 20), Ui.SMALL, px + PW / 2f, py + 12, Ui.argb(Ui.MUTED, 255), 0.8f);
        // explication, coupée en lignes à la largeur du panneau
        int ly = py + 28;
        for (String l : lignes(f, TexteClient.tr("gui.heraliz_quetes.aide_staff.aide"), PW - 28)) {
            g.drawString(f, Ui.lit(l, Ui.SMALL), px + 14, ly, Ui.argb(Ui.TEXT, 230), false);
            ly += 10;
            if (ly > py + 58) break;
        }
        // champ du message : la fin du texte tapé si elle dépasse
        int cx = px + 14, cy = py + 72, cw = PW - 28;
        Ui.soft(g, cx, cy, cw, 22, Ui.argb(0x000000, 120));
        Ui.softOutline(g, cx + 1, cy + 1, cw - 2, 20, Ui.argb(0xFFFFFF, 40));
        String vu = texte;
        while (!vu.isEmpty() && f.width(Ui.lit(vu + "_", Ui.FONT)) > cw - 12) vu = vu.substring(1);
        if (texte.isEmpty()) {
            g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.aide_staff.invite"), Ui.FONT), cx + 6, cy + 7, Ui.argb(Ui.FAINT, 255), false);
        } else {
            g.drawString(f, Ui.lit(vu + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), Ui.FONT), cx + 6, cy + 7, Ui.argb(Ui.TEXT, 255), false);
        }
        super.render(g, mx, my, pt);
    }

    /** Coupe un texte en lignes de {@code largeur} au plus (par mots), dans la petite police. */
    private static List<String> lignes(Font f, String s, int largeur) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String mot : s.split(" ")) {
            String essai = cur.isEmpty() ? mot : cur + " " + mot;
            if (!cur.isEmpty() && f.width(Ui.lit(essai, Ui.SMALL)) > largeur) {
                out.add(cur);
                cur = mot;
            } else {
                cur = essai;
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
