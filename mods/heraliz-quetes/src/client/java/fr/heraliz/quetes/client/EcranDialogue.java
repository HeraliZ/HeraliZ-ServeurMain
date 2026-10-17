package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Dialogue avec un PNJ (style des écrans de clan, canevas 640 x 360) : en bas de l'écran, le nom et le métier du PNJ, son
 * texte qui s'écrit, et les réponses possibles (clic ou touches 1 à 9). Le monde reste visible derrière. L'écran ne fait
 * qu'envoyer le numéro de la réponse : c'est le serveur qui décide de la suite.
 */
public final class EcranDialogue extends Screen {
    private static final int LIGNE = 17;
    private Reseau.Dialogue d;
    private long debut;
    private String texte = "";
    private int px, py, pw, ph;
    private boolean envoye;

    private EcranDialogue(Reseau.Dialogue d) {
        super(Component.translatable("gui.heraliz_quetes.dialogue"));
        changer(d);
    }

    /** Page reçue du serveur : ouvre le dialogue, ou change la page ; entite -1 = fermer. */
    static void recevoir(Reseau.Dialogue d) {
        Minecraft mc = Minecraft.getInstance();
        if (d.entite() < 0) {
            if (mc.screen instanceof EcranDialogue e) {
                e.envoye = true;
                e.onClose();
            }
            return;
        }
        if (mc.screen instanceof EcranDialogue e) {
            e.changer(d);
            e.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } else {
            SonsUi.ouvrir(0.5F);
            mc.setScreen(new EcranDialogue(d));
        }
    }

    private void changer(Reseau.Dialogue nouveau) {
        d = nouveau;
        texte = TexteClient.str(d.texte());
        debut = System.currentTimeMillis();
        envoye = false;
    }

    private int visibles() {
        return (int) Math.min(texte.length(), (System.currentTimeMillis() - debut) / 14);
    }

    private boolean ecrit() {
        return visibles() >= texte.length();
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(500, width - 40);
        px = (width - pw) / 2;
        List<FormattedCharSequence> l = font.split(Ui.lit(texte, Ui.FONT), pw - 32);
        ph = 34 + l.size() * 10 + 12 + d.options().size() * LIGNE + 10;
        py = height - ph - 14;
    }

    private void choisir(int i) {
        if (i < 0 || i >= d.options().size() || envoye) return;
        if (!ecrit()) {
            debut = 0;
            return;
        }
        envoye = true;
        SonsUi.clic(0.6F);
        Reseau.CANAL.sendToServer(new Reseau.Choix(d.entite(), d.options().get(i).id()));
    }

    private int optionSous(double mx, double my) {
        int oy = py + ph - 10 - d.options().size() * LIGNE;
        for (int i = 0; i < d.options().size(); i++) {
            int y = oy + i * LIGNE;
            if (mx >= px + 12 && mx < px + pw - 12 && my >= y && my < y + LIGNE - 2) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        int i = optionSous(mx, my);
        if (i >= 0) {
            choisir(i);
            return true;
        }
        if (!ecrit()) debut = 0;
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choisir(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) {
            choisir(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) && !ecrit()) {
            debut = 0;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (!envoye) Reseau.CANAL.sendToServer(new Reseau.Choix(d.entite(), -1));
        envoye = true;
        super.onClose();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fondLeger(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        int accent = Ui.OR;
        g.fill(px + 1, py + 4, px + 3, py + ph - 4, Ui.argb(accent, 210));

        // nom et métier
        String nom = TexteClient.str(d.nom());
        Ui.gros(g, f, Ui.lit(nom, Ui.BOLD), px + 16, py + 10, 1.25f, Ui.argb(Ui.TEXT, 255));
        int nw = (int) (f.width(Ui.lit(nom, Ui.BOLD)) * 1.25f);
        String role = TexteClient.str(d.role());
        if (!role.isEmpty()) Ui.spaced(g, f, role, Ui.SMALL, px + 16 + nw + 10, py + 13, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, px + 16, px + pw - 16, py + 26, 0xFFFFFF, 40, false, true);

        // texte qui s'écrit
        String vu = texte.substring(0, visibles());
        int cy = py + 34;
        for (FormattedCharSequence l : f.split(Ui.lit(vu, Ui.FONT), pw - 32)) {
            g.drawString(f, l, px + 16, cy, Ui.argb(Ui.TEXT, 245), false);
            cy += 10;
        }

        // réponses
        int sous = optionSous(mx, my);
        int oy = py + ph - 10 - d.options().size() * LIGNE;
        for (int i = 0; i < d.options().size(); i++) {
            Reseau.Option o = d.options().get(i);
            int y = oy + i * LIGNE, c = switch (o.style()) {
                case 1 -> Ui.OR;
                case 2 -> Ui.OK;
                case 3 -> Ui.MUTED;
                case 4 -> Ui.ACCENT_NEUTRE;
                default -> Ui.TEXT;
            };
            boolean survol = i == sous && ecrit();
            if (survol) {
                Ui.soft(g, px + 12, y, pw - 24, LIGNE - 2, Ui.argb(0xFFFFFF, 16));
                Ui.softOutline(g, px + 13, y + 1, pw - 26, LIGNE - 4, Ui.argb(c, 90));
            }
            Ui.chip(g, f, Integer.toString(i + 1), px + 16, y + 2, c);
            String lib = Ui.ellipsis(f, TexteClient.str(o.libelle()), Ui.FONT, pw - 64);
            g.drawString(f, Ui.lit(lib, Ui.FONT), px + 36, y + 4, Ui.argb(ecrit() ? c : Ui.FAINT, 255), false);
        }
        super.render(g, mx, my, pt);
    }
}
