package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.net.Vues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tableau des scores de fin de duel : VICTOIRE / DEFAITE / EGALITE a la couleur de l'equipe, mode · format · carte ·
 * duree, score en grand entre les deux equipes, pastilles des manches (recherche et destruction), puis une table par
 * equipe : joueur, eliminations, morts, ratio, tirs a la tete (moi surligne). Le Coffre est rendu par le serveur quelques
 * secondes apres ; l'ecran reste ouvert jusqu'a sa fermeture.
 */
public class ScoresScreen extends Screen implements EcranDuels {
    private static final int W = 520, H = 300;
    private final Vues.Tableau t;
    private int px, py, pw, ph;
    private String titre = "", sous = "", colJoueur = "", colElim = "", colMorts = "", colRatio = "", colTetes = "", entete = "", score = "";
    private int couleurTitre = Ui.TEXT;
    private final List<String[]> lignes = new ArrayList<>();
    private final List<Integer> couleursLignes = new ArrayList<>();
    private final List<Boolean> moiLignes = new ArrayList<>();

    public ScoresScreen(Vues.Tableau t) {
        super(Component.translatable("duel.scores.titre"));
        this.t = t;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(W, width - 32);
        ph = Math.min(H, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        UUID moi = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        String monEquipe = "";
        for (Vues.JoueurScore j : t.joueurs()) if (j.id().equals(moi)) monEquipe = j.equipe();
        boolean gagne = !t.gagnante().isEmpty() && t.gagnante().equals(monEquipe);
        titre = Component.translatable(t.gagnante().isEmpty() ? "duel.annonce.egalite" : gagne ? "duel.annonce.victoire" : "duel.annonce.defaite").getString();
        couleurTitre = t.gagnante().isEmpty() ? Ui.TEXT : gagne ? Ui.OR : Ui.DANGER;
        int duree = t.dureeS();
        sous = Component.translatable("duel.mode." + t.mode()).getString() + "  ·  " + t.format().toUpperCase(Locale.ROOT) + "  ·  " + t.carte() + "  ·  "
                + String.format(Locale.ROOT, "%d:%02d", duree / 60, duree % 60) + (t.entrainement() ? "  ·  " + Component.translatable("duel.scores.entrainement").getString() : "");
        colJoueur = Component.translatable("duel.scores.joueur").getString();
        colElim = Component.translatable("duel.scores.elim").getString();
        colMorts = Component.translatable("duel.scores.morts").getString();
        colRatio = Component.translatable("duel.scores.ratio").getString();
        colTetes = Component.translatable("duel.scores.tetes").getString();
        entete = Component.translatable(Vues.MODE_RD.equals(t.mode()) ? "duel.scores.manches" : "duel.scores.eliminations", t.objectif()).getString();
        Vues.EquipeScore a = t.equipes().size() > 0 ? t.equipes().get(0) : null, b = t.equipes().size() > 1 ? t.equipes().get(1) : null;
        score = (a == null ? 0 : a.score()) + "  —  " + (b == null ? 0 : b.score());
        lignes.clear();
        couleursLignes.clear();
        moiLignes.clear();
        for (Vues.EquipeScore e : t.equipes()) {
            for (Vues.JoueurScore j : t.joueurs()) {
                if (!j.equipe().equals(e.id())) continue;
                lignes.add(new String[]{j.bot() ? Component.translatable("duel.cible").getString() : j.nom(), String.valueOf(j.eliminations()),
                        String.valueOf(j.morts()), Ui.ratio(j.ratio()), String.valueOf(j.tetes())});
                couleursLignes.add(e.couleur());
                moiLignes.add(j.id().equals(moi));
            }
        }
        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        addRenderableWidget(new HzButton(px + pw - 16 - 110, py + ph - 30, 110, 20, Component.translatable("duel.scores.fermer"), Ui.accent(), this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Font f = font;
        float cx = px + pw / 2f;
        Ui.spacedCentered(g, f, titre, Ui.BOLD, cx, py + 16, Ui.argb(couleurTitre, 255), 3.0f);
        Ui.spacedCentered(g, f, sous, Ui.SMALL, cx, py + 32, Ui.argb(Ui.MUTED, 255), 0.6f);
        // equipes et score
        Vues.EquipeScore a = t.equipes().size() > 0 ? t.equipes().get(0) : null, b = t.equipes().size() > 1 ? t.equipes().get(1) : null;
        int sy = py + 52;
        if (a != null) Ui.right(g, f, Ui.lit(a.nom(), Ui.BOLD), cx - 48, sy + 6, Ui.argb(a.couleur(), 255));
        if (b != null) g.drawString(f, Ui.lit(b.nom(), Ui.BOLD), (int) cx + 48, sy + 6, Ui.argb(b.couleur(), 255), false);
        g.pose().pushPose();
        float sc = 2f;
        float sw = f.width(Ui.lit(score, Ui.BOLD)) * sc;
        g.pose().translate(cx - sw / 2f, sy, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(f, Ui.lit(score, Ui.BOLD), 0, 0, Ui.argb(Ui.TEXT, 255), false);
        g.pose().popPose();
        Ui.spacedCentered(g, f, entete, Ui.SMALL, cx, sy + 22, Ui.argb(Ui.FAINT, 255), 0.6f);
        // manches
        if (!t.manches().isEmpty()) {
            int n = t.manches().size(), dw = 12;
            int dx = (int) (cx - (n * dw) / 2f);
            for (int i = 0; i < n; i++) {
                String id = t.manches().get(i);
                int c = a != null && id.equals(a.id()) ? a.couleur() : b != null ? b.couleur() : Ui.MUTED;
                Ui.soft(g, dx + i * dw + 2, sy + 33, 8, 8, Ui.argb(c, 230));
            }
        }
        // table
        int ty = py + 104, x0 = px + 40, w = pw - 80;
        int cE = x0 + w - 200, cM = x0 + w - 150, cR = x0 + w - 95, cT = x0 + w - 40;
        Ui.spaced(g, f, colJoueur, Ui.SMALL, x0 + 10, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.spacedRight(g, f, colElim, Ui.SMALL, cE, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.spacedRight(g, f, colMorts, Ui.SMALL, cM, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.spacedRight(g, f, colRatio, Ui.SMALL, cR, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.spacedRight(g, f, colTetes, Ui.SMALL, cT, ty, Ui.argb(Ui.MUTED, 255), 0.8f);
        Ui.hairline(g, x0, x0 + w, ty + 10, 0xFFFFFF, 40, false, false);
        int ly = ty + 16;
        int avant = -1;
        for (int i = 0; i < lignes.size(); i++) {
            int c = couleursLignes.get(i);
            if (avant != -1 && c != avant) ly += 6;
            avant = c;
            String[] l = lignes.get(i);
            boolean moi = moiLignes.get(i);
            if (moi) Ui.soft(g, x0, ly - 3, w, 15, Ui.argb(c, 30));
            g.fill(x0, ly - 3, x0 + 2, ly + 12, Ui.argb(c, 230));
            g.drawString(f, Ui.lit(l[0], moi ? Ui.BOLD : Ui.FONT), x0 + 10, ly, Ui.argb(Ui.TEXT, 255), false);
            Ui.right(g, f, Ui.lit(l[1], Ui.BOLD), cE, ly, Ui.argb(Ui.TEXT, 255));
            Ui.right(g, f, Ui.lit(l[2], Ui.FONT), cM, ly, Ui.argb(Ui.TEXT, 230));
            Ui.right(g, f, Ui.lit(l[3], Ui.FONT), cR, ly, Ui.argb(Ui.TEXT, 230));
            Ui.right(g, f, Ui.lit(l[4], Ui.FONT), cT, ly, Ui.argb(Ui.MUTED, 255));
            ly += 16;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.6f);
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
    public String journal() {
        return "panneau " + px + "," + py + " " + pw + "x" + ph + " gagnante '" + t.gagnante() + "' lignes " + lignes.size() + " manches " + t.manches();
    }
}
