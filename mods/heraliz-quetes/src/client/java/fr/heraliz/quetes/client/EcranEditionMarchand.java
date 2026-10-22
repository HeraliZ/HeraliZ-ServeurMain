package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Éditeur d'un marchand (op seulement, ouvert par le Registre du marchand ; style des écrans de clan). Vendeur : une ligne
 * par article (icône, nom, rareté, prix modifiable, monter, retirer), pages, et « ajouter l'objet en main ». Racheteur :
 * une carte par règle (tag) avec les 6 prix par rareté. Chaque changement part au serveur, qui revérifie les droits,
 * l'enregistre et renvoie la liste à jour.
 */
public final class EcranEditionMarchand extends Screen {
    private static final int[] RARETES = {0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646};
    private static final int LIGNE = 26, REGLE_H = 58;

    private Reseau.Edition e;
    private int px, py, pw, ph, page;
    /** Action « Entrée » de chaque champ de prix. */
    private final Map<EditBox, Runnable> valider = new LinkedHashMap<>();
    private EditBox prixAjout;

    private EcranEditionMarchand(Reseau.Edition e) {
        super(Component.translatable("gui.heraliz_quetes.edition"));
        this.e = e;
    }

    static void recevoir(Reseau.Edition m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranEditionMarchand ecran && ecran.e.pnj().equals(m.pnj())) {
            ecran.e = m;
            ecran.rebuildWidgets();
            return;
        }
        SonsUi.ouvrir(0.5F);
        mc.setScreen(new EcranEditionMarchand(m));
    }

    private static int couleur(int rarete) {
        return rarete < 0 || rarete >= RARETES.length ? Ui.TEXT : RARETES[rarete];
    }

    private void envoyer(int type, int index, int rarete, long prix) {
        Reseau.CANAL.sendToServer(new Reseau.Modif(e.pnj(), type, index, rarete, prix));
        SonsUi.clic(0.5F);
    }

    private static long lire(EditBox b, long defaut) {
        try {
            return Long.parseLong(b.getValue().trim());
        } catch (NumberFormatException ex) {
            return defaut;
        }
    }

    private EditBox champ(int x, int y, int w, long valeur) {
        EditBox b = new EditBox(font, x + 4, y + 3, w - 8, 10, Component.empty());
        b.setBordered(false);
        b.setMaxLength(9);
        b.setFilter(s -> s.chars().allMatch(Character::isDigit));
        b.setValue(String.valueOf(valeur));
        b.setTextColor(Ui.argb(Ui.OR, 255));
        return addRenderableWidget(b);
    }

    private int lignesParPage() {
        return Math.max(1, (ph - 52 - 64) / LIGNE);
    }

    private int pages() {
        return Math.max(1, (e.articles().size() + lignesParPage() - 1) / lignesParPage());
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        valider.clear();
        pw = Math.min(600, width - 24);
        ph = Math.min(330, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bas = py + ph - 30;
        if (e.rachat()) {
            for (int r = 0; r < e.regles().size(); r++) {
                Reseau.ERegle regle = e.regles().get(r);
                int y = py + 54 + r * (REGLE_H + 6), cw = (pw - 36 - 18 - 90) / 6;
                if (y + REGLE_H > bas - 4) break;
                EditBox[] boites = new EditBox[6];
                for (int k = 0; k < 6; k++) boites[k] = champ(px + 27 + k * (cw + 3), y + 34, cw, regle.prix()[k]);
                final int index = r;
                Runnable enregistrer = () -> {
                    for (int k = 0; k < 6; k++) {
                        long v = lire(boites[k], regle.prix()[k]);
                        if (v != regle.prix()[k]) envoyer(Reseau.Modif.PRIX_RACHAT, index, k, v);
                    }
                };
                for (EditBox b : boites) valider.put(b, enregistrer);
                addRenderableWidget(new HzButton(px + pw - 27 - 84, y + 32, 84, 16,
                        Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.enregistrer")), Ui.OR, enregistrer).look(HzButton.Look.PRIMARY));
            }
        } else {
            page = Math.max(0, Math.min(page, pages() - 1));
            int n0 = page * lignesParPage();
            for (int i = n0; i < Math.min(e.articles().size(), n0 + lignesParPage()); i++) {
                Reseau.EArticle a = e.articles().get(i);
                int y = py + 52 + (i - n0) * LIGNE, xd = px + pw - 18;
                final int index = i;
                addRenderableWidget(new HzButton(xd - 22, y + 4, 22, 16, Component.literal("✕"), Ui.DANGER,
                        () -> envoyer(Reseau.Modif.RETIRER, index, 0, 0)));
                HzButton monter = addRenderableWidget(new HzButton(xd - 48, y + 4, 22, 16, Component.literal("▲"), Ui.ACCENT_NEUTRE,
                        () -> envoyer(Reseau.Modif.MONTER, index, 0, 0)));
                monter.active = i > 0;
                EditBox b = champ(xd - 160, y + 4, 70, a.prix());
                Runnable enregistrer = () -> {
                    long v = lire(b, a.prix());
                    if (v >= 1 && v != a.prix()) envoyer(Reseau.Modif.PRIX, index, 0, v);
                };
                valider.put(b, enregistrer);
                addRenderableWidget(new HzButton(xd - 86, y + 4, 34, 16,
                        Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ok")), Ui.OR, enregistrer).look(HzButton.Look.PRIMARY));
            }
            // pages
            addRenderableWidget(new HzButton(px + 18, bas, 20, 18, Component.literal("◀"), Ui.ACCENT_NEUTRE, () -> {
                page--;
                rebuildWidgets();
            })).active = page > 0;
            addRenderableWidget(new HzButton(px + 78, bas, 20, 18, Component.literal("▶"), Ui.ACCENT_NEUTRE, () -> {
                page++;
                rebuildWidgets();
            })).active = page < pages() - 1;
            // ajouter l'objet en main
            int ax = px + 118;
            prixAjout = champ(ax + 150, bas + 1, 60, 50);
            Runnable ajouter = () -> {
                long v = lire(prixAjout, 0);
                if (v >= 1) {
                    page = Integer.MAX_VALUE; // aller voir le nouvel article, à la fin
                    envoyer(Reseau.Modif.AJOUTER_MAIN, 0, 0, v);
                }
            };
            valider.put(prixAjout, ajouter);
            addRenderableWidget(new HzButton(ax + 214, bas, 70, 18,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ajouter")), Ui.OK, ajouter).look(HzButton.Look.PRIMARY));
        }
        if (e.modifie()) {
            addRenderableWidget(new HzButton(px + pw - 18 - 120, bas, 120, 18,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.reinitialiser")), Ui.MUTED,
                    () -> envoyer(Reseau.Modif.REINITIALISER, 0, 0, 0)));
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            for (Map.Entry<EditBox, Runnable> en : valider.entrySet()) {
                if (en.getKey().isFocused()) {
                    en.getValue().run();
                    return true;
                }
            }
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

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 215);
        Ui.gros(g, f, Ui.lit(TexteClient.str(e.nom()), Ui.BOLD), px + 18, py + 14, 1.5f, Ui.argb(Ui.TEXT, 255));
        Ui.spaced(g, f, TexteClient.tr(e.rachat() ? "gui.heraliz_quetes.edition.rachat" : "gui.heraliz_quetes.edition.vente").toUpperCase(),
                Ui.SMALL, px + 19, py + 32, Ui.argb(Ui.OR, 255), 0.8f);
        Ui.spacedRight(g, f, TexteClient.tr(e.modifie() ? "gui.heraliz_quetes.edition.modifie" : "gui.heraliz_quetes.edition.fichier"),
                Ui.SMALL, px + pw - 18, py + 18, Ui.argb(e.modifie() ? Ui.AMBRE : Ui.MUTED, 255), 0.6f);
        Ui.hairline(g, px + 18, px + pw - 18, py + 44, 0xFFFFFF, 30, false, true);
        // fonds des champs de prix
        for (EditBox b : valider.keySet()) {
            g.fill(b.getX() - 4, b.getY() - 3, b.getX() + b.getWidth() + 4, b.getY() + 13, Ui.argb(0xFFFFFF, b.isFocused() ? 30 : 14));
            if (b.isFocused()) Ui.softOutline(g, b.getX() - 4, b.getY() - 3, b.getWidth() + 8, 16, Ui.argb(Ui.OR, 200));
        }
        List<Component> bulle = e.rachat() ? rachat(g, f) : vente(g, f, mx, my);
        super.render(g, mx, my, pt);
        if (bulle != null) Ui.tooltip(g, f, bulle, mx, my, Ui.OR);
    }

    private List<Component> vente(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int n0 = page * lignesParPage();
        if (e.articles().isEmpty()) {
            Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.vide"), Ui.SMALL, px + 18, py + 56, Ui.argb(Ui.MUTED, 255), 0.4f);
        }
        for (int i = n0; i < Math.min(e.articles().size(), n0 + lignesParPage()); i++) {
            Reseau.EArticle a = e.articles().get(i);
            int y = py + 52 + (i - n0) * LIGNE, col = couleur(a.rarete());
            if ((i - n0) % 2 == 0) g.fill(px + 14, y, px + pw - 14, y + LIGNE - 2, Ui.argb(0xFFFFFF, 6));
            Ui.right(g, f, Ui.lit(String.valueOf(i + 1), Ui.SMALL), px + 32, y + 9, Ui.argb(Ui.FAINT, 255));
            g.fill(px + 38, y + 2, px + 58, y + 22, Ui.argb(col, 40));
            g.renderItem(a.objet(), px + 40, y + 4);
            String nom = Ui.ellipsis(f, a.objet().getHoverName().getString(), Ui.BOLD, pw - 300);
            g.drawString(f, Ui.lit(nom, Ui.BOLD), px + 64, y + 4, Ui.argb(Ui.TEXT, 255), false);
            if (a.rarete() >= 0) g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.rarete." + a.rarete()), Ui.SMALL), px + 64, y + 14, Ui.argb(col, 255), false);
            Ui.right(g, f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.SMALL), px + pw - 18 - 170, y + 8, Ui.argb(Ui.MUTED, 255));
            if (mx >= px + 38 && mx < px + 58 && my >= y + 2 && my < y + 22) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), a.objet()));
        }
        int bas = py + ph - 30;
        Ui.hairline(g, px + 18, px + pw - 18, bas - 8, 0xFFFFFF, 30, false, true);
        Ui.centered(g, f, Ui.lit((page + 1) + "/" + pages(), Ui.BOLD), px + 58, bas + 5, Ui.argb(Ui.TEXT, 255));
        // objet en main, prêt à être ajouté
        ItemStack main = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
        int ax = px + 118;
        g.fill(ax, bas - 1, ax + 20, bas + 19, Ui.argb(0xFFFFFF, 14));
        if (!main.isEmpty()) g.renderItem(main, ax + 2, bas + 1);
        String txt = Ui.ellipsis(f, main.isEmpty() ? TexteClient.tr("gui.heraliz_quetes.edition.main_vide") : main.getHoverName().getString(), Ui.FONT, 118);
        g.drawString(f, Ui.lit(txt, Ui.FONT), ax + 25, bas + 5, Ui.argb(main.isEmpty() ? Ui.MUTED : Ui.TEXT, 255), false);
        return bulle;
    }

    private List<Component> rachat(GuiGraphics g, Font f) {
        int cw = (pw - 36 - 18 - 90) / 6;
        for (int r = 0; r < e.regles().size(); r++) {
            Reseau.ERegle regle = e.regles().get(r);
            int y = py + 54 + r * (REGLE_H + 6);
            if (y + REGLE_H > py + ph - 34) break;
            Ui.glass(g, px + 18, y, pw - 36, REGLE_H, 110);
            g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.edition.regle", r + 1, regle.tag()), Ui.BOLD), px + 27, y + 7, Ui.argb(Ui.TEXT, 255), false);
            for (int k = 0; k < 6; k++) {
                Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.rarete." + k).toUpperCase(), Ui.SMALL, px + 27 + k * (cw + 3), y + 23,
                        Ui.argb(couleur(k), 255), 0.4f);
            }
        }
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.aide_rachat"), Ui.SMALL, px + 18, py + ph - 24, Ui.argb(Ui.FAINT, 255), 0.4f);
        return null;
    }
}
