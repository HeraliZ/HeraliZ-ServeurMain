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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Dialogue avec un PNJ (style des écrans de clan, canevas 640 x 360) : en bas de l'écran, le nom et le métier du PNJ, son
 * texte qui s'écrit, et les réponses possibles (clic ou touches 1 à 9). Le monde reste visible derrière. L'écran ne fait
 * qu'envoyer le numéro de la réponse : c'est le serveur qui décide de la suite.
 */
public final class EcranDialogue extends Screen {
    /** Titre à sa vraie taille (police 14) : jamais de texte agrandi, flou (25/10, audit des écrans). */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre");
    private static final int LIGNE = 17;
    /** Marge gardée en haut de l'écran : le panneau ne grandit pas au-delà. */
    private static final int MARGE_HAUT = 10;
    private Reseau.Dialogue d;
    private long debut;
    private String texte = "";
    private int px, py, pw, ph;
    private boolean envoye;
    /** Texte trop long pour le panneau : il défile (molette) ; pendant l'écriture, il suit la dernière ligne. */
    private int lignesTexte, defile;
    private boolean suivre = true;

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
        defile = 0;
        suivre = true;
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
        lignesTexte = l.size();
        // hauteur bornée à l'écran (avant, py pouvait passer sous 0) ; le reste du texte défile
        ph = Math.min(34 + l.size() * 10 + 12 + d.options().size() * LIGNE + 10, height - 14 - MARGE_HAUT);
        py = height - ph - 14;
    }

    /** Zone du texte : entre le filet sous le nom et les réponses. */
    private int texteHaut() {
        return py + 30;
    }

    private int texteBas() {
        return py + ph - 10 - d.options().size() * LIGNE - 4;
    }

    /** Défilement qui montre la fin de n lignes de texte. */
    private int defilePour(int n) {
        return Math.max(0, 4 + n * 10 + 2 - (texteBas() - texteHaut()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = defilePour(lignesTexte);
        if (max <= 0) return false;
        suivre = false;
        defile = Mth.clamp((int) (defile - delta * 20), 0, max);
        return true;
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
        g.drawString(f, Ui.lit(nom, TITRE), px + 16, py + 9, Ui.argb(Ui.TEXT, 255), false);
        int nw = f.width(Ui.lit(nom, TITRE));
        String role = TexteClient.str(d.role());
        if (!role.isEmpty()) Ui.spaced(g, f, role, Ui.SMALL, px + 16 + nw + 10, py + 13, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, px + 16, px + pw - 16, py + 26, 0xFFFFFF, 40, false, true);

        // texte qui s'écrit
        String vu = texte.substring(0, visibles());
        List<FormattedCharSequence> lv = f.split(Ui.lit(vu, Ui.FONT), pw - 32);
        int th = texteHaut(), tb = texteBas(), max = defilePour(lignesTexte);
        if (suivre && !ecrit()) defile = defilePour(lv.size());
        defile = Mth.clamp(defile, 0, max);
        g.enableScissor(px + 2, th, px + pw - 2, Math.max(th, tb));
        int cy = th + 4 - defile;
        for (FormattedCharSequence l : lv) {
            if (cy + 10 > th && cy < tb) g.drawString(f, l, px + 16, cy, Ui.argb(Ui.TEXT, 245), false);
            cy += 10;
        }
        g.disableScissor();
        if (max > 0 && tb > th) {
            int zone = tb - th, barre = Math.max(12, zone * zone / (zone + max)), by = th + (zone - barre) * defile / max;
            g.fill(px + pw - 7, by, px + pw - 5, by + barre, Ui.argb(0xFFFFFF, 60));
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
