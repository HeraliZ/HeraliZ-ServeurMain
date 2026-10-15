package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Choix de la classe (5 s) au debut d'une manche de recherche et destruction ou a la reapparition en match a mort :
 * 5 cartes (numero, nom, arme principale en grand, secondaire, melee, soins, grenades, tenue), barre du temps restant,
 * touches 1 a 5. La carte choisie prend la couleur de l'equipe. Sans choix, la classe deja retenue est donnee. En duel,
 * les 5 classes sont celles de l'hote, les memes pour tous (ligne « Classes de X » sous le titre) ; en entrainement, les
 * siennes. Le serveur revalide et construit le kit ; en reapparition l'ecran se ferme au choix.
 */
public class ChoixClasseScreen extends Screen implements EcranDuels {
    private static final int CW = 112, CH = 156, GAP = 8;
    private final PaquetsDuels.ChoixS2C donnees;
    private final long debutMs = Util.getMillis();
    private int choisie;
    private int x0, y0;
    private final List<Carte> cartes = new ArrayList<>();
    private String titre = "", hote = "", contexte = "", choisieTxt = "";

    /** Textes et icones d'une carte, prepares a l'ouverture. */
    private record Carte(String nom, String principale, ItemStack icone, String secondaire, String melee, String soins, String grenades, String tenue) {
    }

    public ChoixClasseScreen(PaquetsDuels.ChoixS2C d) {
        super(Component.translatable("duel.choix.titre"));
        this.donnees = d;
        this.choisie = Math.max(0, Math.min(ClassePerso.EMPLACEMENTS - 1, d.choisie()));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        int total = ClassePerso.EMPLACEMENTS * CW + (ClassePerso.EMPLACEMENTS - 1) * GAP;
        x0 = (width - total) / 2;
        y0 = (height - CH) / 2 + 14;
        titre = Component.translatable("duel.choix.titre").getString();
        hote = donnees.hote().isEmpty() ? "" : Component.translatable("duel.choix.classes_de", donnees.hote()).getString();
        contexte = donnees.contexte().getString();
        choisieTxt = Component.translatable("duel.choix.choisie").getString();
        Catalogue c = DuelsClient.catalogue().vide() ? Catalogue.factice() : DuelsClient.catalogue();
        cartes.clear();
        String aucune = Component.translatable("duel.classes.aucune_arme").getString();
        for (int i = 0; i < ClassePerso.EMPLACEMENTS; i++) {
            ClassePerso k = i < donnees.classes().size() ? donnees.classes().get(i) : ClassePerso.vide(i + 1);
            Catalogue.Arme p = c.arme(k.principale()), s = c.arme(k.secondaire());
            Catalogue.Objet m = Catalogue.chercher(c.melees(), k.melee()), l = Catalogue.chercher(c.letales(), k.letale()),
                    t = Catalogue.chercher(c.tactiques(), k.tactique()), te = Catalogue.chercher(c.tenues(), k.tenue());
            StringBuilder soins = new StringBuilder();
            for (String id : k.soins()) {
                Catalogue.Objet o = Catalogue.chercher(c.soins(), id);
                if (o == null) continue;
                if (soins.length() > 0) soins.append(", ");
                soins.append(Rendu.nom(o.nom(), o.id()));
            }
            String gren = (l == null ? "" : Rendu.nom(l.nom(), l.id())) + (l != null && t != null ? ", " : "") + (t == null ? "" : Rendu.nom(t.nom(), t.id()));
            int w = CW - 16;
            cartes.add(new Carte(Ui.ellipsis(font, Rendu.nomClasse(k.nom()), Ui.BOLD, w - 14), Ui.ellipsis(font, p == null ? aucune : Rendu.nom(p.nom(), p.id()), Ui.FONT, w),
                    p == null ? ItemStack.EMPTY : p.icone(), Ui.ellipsis(font, s == null ? "—" : Rendu.nom(s.nom(), s.id()), Ui.SMALL, w),
                    Ui.ellipsis(font, m == null ? "—" : Rendu.nom(m.nom(), m.id()), Ui.SMALL, w), Ui.ellipsis(font, soins.length() == 0 ? "—" : soins.toString(), Ui.SMALL, w),
                    Ui.ellipsis(font, gren.isEmpty() ? "—" : gren, Ui.SMALL, w), Ui.ellipsis(font, te == null ? "—" : Rendu.nom(te.nom(), te.id()), Ui.SMALL, w)));
        }
    }

    private float reste() {
        long ms = donnees.secondes() * 1000L - (Util.getMillis() - debutMs);
        return Math.max(0, ms) / (donnees.secondes() * 1000f);
    }

    @Override
    public void tick() {
        if (reste() <= 0 && minecraft != null) minecraft.setScreen(null);
    }

    private void choisir(int i) {
        if (i < 0 || i >= ClassePerso.EMPLACEMENTS) return;
        choisie = i;
        SonsUi.clic(0.7f);
        if (minecraft != null && minecraft.getConnection() != null) MatchsNet.versServeur(new PaquetsDuels.ChoixClasseC2S(i));
        if (donnees.fermerAuChoix() && minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fondLeger(g, width, height);
        Font f = font;
        int couleur = donnees.couleur() == 0 ? Ui.accent() : donnees.couleur();
        float cx = width / 2f;
        // classes de l'hote : une ligne de plus sous le titre, le reste remonte d'autant
        int dy = hote.isEmpty() ? 0 : 12;
        Ui.spacedCentered(g, f, titre, Ui.BOLD, cx, y0 - 44 - dy, Ui.argb(Ui.TEXT, 255), 1.8f);
        if (!hote.isEmpty()) Ui.spacedCentered(g, f, hote, Ui.FONT, cx, y0 - 42, Ui.argb(Ui.TEXT, 255), 0.6f);
        Ui.spacedCentered(g, f, contexte, Ui.SMALL, cx, y0 - 30, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.3f), 255), 0.8f);
        float r = reste();
        Ui.bar(g, (int) (cx - 120), y0 - 18, 240, 2, r, r < 0.3f ? Ui.DANGER : couleur);
        for (int i = 0; i < cartes.size(); i++) carte(g, f, i, x0 + i * (CW + GAP), y0, mx, my, couleur);
        super.render(g, mx, my, pt);
    }

    private void carte(GuiGraphics g, Font f, int i, int x, int y, int mx, int my, int couleur) {
        Carte c = cartes.get(i);
        boolean sel = i == choisie, hover = mx >= x && mx < x + CW && my >= y && my < y + CH;
        Ui.glass(g, x, y, CW, CH, sel ? 220 : 185);
        if (sel) {
            Ui.soft(g, x, y, CW, CH, Ui.argb(couleur, 34));
            Ui.softOutline(g, x + 1, y + 1, CW - 2, CH - 2, Ui.argb(couleur, 230));
        } else if (hover) {
            Ui.softOutline(g, x + 1, y + 1, CW - 2, CH - 2, Ui.argb(couleur, 140));
        }
        Ui.gros(g, f, Ui.lit(String.valueOf(i + 1), Ui.BOLD), x + 8, y + 8, 1.4f, Ui.argb(sel ? couleur : Ui.FAINT, 255));
        g.drawString(f, Ui.lit(c.nom(), Ui.BOLD), x + 22, y + 10, Ui.argb(Ui.TEXT, 255), false);
        Rendu.icone(g, c.icone(), x + (CW - 48) / 2, y + 26, 48, c.principale(), 255);
        Ui.centered(g, f, Ui.lit(c.principale(), Ui.FONT), x + CW / 2f, y + 78, Ui.argb(Ui.TEXT, 255));
        Ui.hairline(g, x + 8, x + CW - 8, y + 91, 0xFFFFFF, 30, true, true);
        int ly = y + 96;
        g.drawString(f, Ui.lit(c.secondaire(), Ui.SMALL), x + 8, ly, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(c.melee(), Ui.SMALL), x + 8, ly + 10, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(c.soins(), Ui.SMALL), x + 8, ly + 20, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(c.grenades(), Ui.SMALL), x + 8, ly + 30, Ui.argb(Ui.MUTED, 255), false);
        g.drawString(f, Ui.lit(c.tenue(), Ui.SMALL), x + 8, ly + 40, Ui.argb(Ui.MUTED, 255), false);
        if (sel) Ui.chip(g, f, choisieTxt, x + CW - 8 - (int) Ui.spacedWidth(f, choisieTxt.toUpperCase(java.util.Locale.ROOT), Ui.SMALL, 0.6f) - 10, y + 8, couleur);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b != 0) return super.mouseClicked(mx, my, b);
        for (int i = 0; i < cartes.size(); i++) {
            int x = x0 + i * (CW + GAP);
            if (mx >= x && mx < x + CW && my >= y0 && my < y0 + CH) {
                choisir(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) {
            choisir(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            if (minecraft != null) minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(key, scan, mods);
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
        return "cartes x" + x0 + "+" + (CW + GAP) + "k y" + y0 + " (" + CW + "x" + CH + ") choisie " + choisie + " reste " + reste();
    }
}
