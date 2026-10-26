package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import fr.heraliz.quetes.client.kit.HzField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Éditeur d'un marchand (op seulement, ouvert par le Registre du marchand ; style des écrans de clan). Vendeur : une ligne
 * par article (icône, nom, rareté, prix modifiable, monter, retirer), pages, et « ajouter un objet de la barre ».
 * Racheteur : une carte par règle (tag ou objet) avec les 6 prix par rareté, retirer, ajouter une règle d'un objet de la
 * barre. Onglet Échanges (1.11.0) : une ligne par échange (objet donné et quantité → objet reçu et quantité), ajout par
 * deux clics dans la barre d'objets (gauche = donné, droit = reçu). Marchand créé en jeu : « Supprimer ce marchand »
 * (deux clics). Chaque changement part au serveur, qui revérifie les droits, l'enregistre et renvoie la liste à jour.
 */
public final class EcranEditionMarchand extends Screen {
    private static final int[] RARETES = {0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646};
    private static final int LIGNE = 26, REGLE_H = 58;
    private static final int ONGLET_PRINCIPAL = 0, ONGLET_ECHANGES = 1;

    private Reseau.Edition e;
    private int px, py, pw, ph, page, onglet;
    /** Action « Entrée » de chaque champ de prix ou de quantité. */
    private final Map<HzField, Runnable> valider = new LinkedHashMap<>();
    private HzField prixAjout, nDonneAjout, nRecuAjout;
    /** Écran à refaire au prochain tick (jamais au milieu d'un clic : la liste des boutons serait changée en cours de lecture). */
    private boolean aRefaire;
    /** Aller à la dernière page au prochain rafraîchissement (après un ajout) ; aller à la première (règle ajoutée devant). */
    private boolean allerFin, allerDebut;
    /** Case de la barre d'objets choisie pour « Ajouter » (-1 : la première qui a un objet) ; et l'objet reçu d'un échange. */
    private int caseChoisie = -1, caseRecu = -1;
    /** « Supprimer ce marchand » cliqué une fois : le prochain clic confirme. */
    private boolean confirmerSuppr;
    private static final int CASE = 18;
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre");

    private EcranEditionMarchand(Reseau.Edition e) {
        super(Component.translatable("gui.heraliz_quetes.edition"));
        this.e = e;
        this.onglet = e.troc() ? ONGLET_ECHANGES : ONGLET_PRINCIPAL;
    }

    static void recevoir(Reseau.Edition m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranEditionMarchand ecran && ecran.e.pnj().equals(m.pnj())) {
            ecran.e = m;
            if (m.troc()) ecran.onglet = ONGLET_ECHANGES;
            ecran.aRefaire = true;
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

    private static long lire(HzField b, long defaut) {
        try {
            return Long.parseLong(b.getValue().trim());
        } catch (NumberFormatException ex) {
            return defaut;
        }
    }

    private HzField champ(int x, int y, int w, long valeur) {
        return champ(x, y, w, valeur, 9);
    }

    private HzField champ(int x, int y, int w, long valeur, int longueur) {
        // champ du kit (25/10, audit des écrans) : chiffres en DM Sans comme le reste, plus la police pixel de Minecraft
        HzField b = new HzField(x, y, w, 16, longueur, HzField.NOMBRE).accent(Ui.OR);
        b.setValue(String.valueOf(valeur));
        return addRenderableWidget(b);
    }

    private boolean echanges() {
        return onglet == ONGLET_ECHANGES;
    }

    private int lignesParPage() {
        return Math.max(1, (ph - 52 - 64) / LIGNE);
    }

    private int reglesParPage() {
        return Math.max(1, (ph - 30 - 34 - 54) / (REGLE_H + 6));
    }

    private int parPage() {
        return !echanges() && e.rachat() ? reglesParPage() : lignesParPage();
    }

    private int nombre() {
        return echanges() ? e.echanges().size() : e.rachat() ? e.regles().size() : e.articles().size();
    }

    private int pages() {
        return Math.max(1, (nombre() + parPage() - 1) / parPage());
    }

    private void changerOnglet(int o) {
        if (onglet == o) return;
        onglet = o;
        page = 0;
        aRefaire = true;
        SonsUi.clic(0.5F);
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
        // onglets : ce qu'il vend (ou rachète) / ses échanges ; un marchand de troc n'a que les échanges
        if (!e.troc()) {
            String principal = TexteClient.tr(e.rachat() ? "gui.heraliz_quetes.edition.onglet.rachat" : "gui.heraliz_quetes.edition.onglet.vente");
            HzButton b1 = addRenderableWidget(new HzButton(px + 18, py + 29, 120, 13, Component.literal(principal), Ui.OR,
                    () -> changerOnglet(ONGLET_PRINCIPAL)));
            HzButton b2 = addRenderableWidget(new HzButton(px + 142, py + 29, 120, 13,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.onglet.echanges", e.echanges().size())), Ui.OR,
                    () -> changerOnglet(ONGLET_ECHANGES)));
            (echanges() ? b2 : b1).look(HzButton.Look.PRIMARY);
        }
        if (allerFin) {
            allerFin = false;
            page = pages() - 1;
        }
        if (allerDebut) {
            allerDebut = false;
            page = 0;
        }
        page = Math.max(0, Math.min(page, pages() - 1));
        if (echanges()) initEchanges(bas);
        else if (e.rachat()) initRachat(bas);
        else initVente(bas);
        // pages
        addRenderableWidget(new HzButton(px + 18, bas, 20, 18, Component.literal("←"), Ui.ACCENT_NEUTRE, () -> {
            page--;
            aRefaire = true;
        })).active = page > 0;
        addRenderableWidget(new HzButton(px + 78, bas, 20, 18, Component.literal("→"), Ui.ACCENT_NEUTRE, () -> {
            page++;
            aRefaire = true;
        })).active = page < pages() - 1;
        if (e.cree()) {
            // marchand créé en jeu : pas de fichier où revenir, on peut le supprimer (deux clics)
            addRenderableWidget(new HzButton(px + pw - 18 - 120, bas, 120, 18,
                    Component.literal(TexteClient.tr(confirmerSuppr ? "gui.heraliz_quetes.edition.supprimer_ok" : "gui.heraliz_quetes.edition.supprimer")),
                    Ui.DANGER, () -> {
                if (!confirmerSuppr) {
                    confirmerSuppr = true;
                    aRefaire = true;
                    SonsUi.clic(0.5F);
                    return;
                }
                envoyer(Reseau.Modif.SUPPRIMER, 0, 0, 0);
                onClose();
            })).look(confirmerSuppr ? HzButton.Look.PRIMARY : HzButton.Look.OUTLINE);
        } else if (e.modifie()) {
            addRenderableWidget(new HzButton(px + pw - 18 - 120, bas, 120, 18,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.reinitialiser")), Ui.MUTED,
                    () -> envoyer(Reseau.Modif.REINITIALISER, 0, 0, 0)));
        }
    }

    private void initVente(int bas) {
        int n0 = page * lignesParPage();
        for (int i = n0; i < Math.min(e.articles().size(), n0 + lignesParPage()); i++) {
            Reseau.EArticle a = e.articles().get(i);
            int y = py + 52 + (i - n0) * LIGNE, xd = px + pw - 18;
            final int index = i;
            addRenderableWidget(new HzButton(xd - 22, y + 4, 22, 16, Component.literal("×"), Ui.DANGER,
                    () -> envoyer(Reseau.Modif.RETIRER, index, 0, 0)));
            HzButton monter = addRenderableWidget(new HzButton(xd - 48, y + 4, 22, 16, Component.literal("↑"), Ui.ACCENT_NEUTRE,
                    () -> envoyer(Reseau.Modif.MONTER, index, 0, 0)));
            monter.active = i > 0;
            HzField b = champ(xd - 160, y + 4, 70, a.prix());
            Runnable enregistrer = () -> {
                long v = lire(b, a.prix());
                if (v >= 1 && v != a.prix()) envoyer(Reseau.Modif.PRIX, index, 0, v);
            };
            valider.put(b, enregistrer);
            addRenderableWidget(new HzButton(xd - 86, y + 4, 34, 16,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ok")), Ui.OR, enregistrer).look(HzButton.Look.PRIMARY));
        }
        ajoutPrix(bas, Reseau.Modif.AJOUTER_MAIN, 50);
    }

    /** Bas de l'écran : prix + « Ajouter » pour l'objet choisi dans la barre (article vendu, ou règle de rachat). */
    private void ajoutPrix(int bas, int type, long defaut) {
        int ax = px + 118;
        prixAjout = champ(ax + 9 * CASE + 6, bas + 1, 60, defaut);
        Runnable ajouter = () -> {
            long v = lire(prixAjout, 0);
            int c = caseAjout();
            if (v >= 1 && c >= 0) {
                // aller voir le nouvel article (à la fin) ou la nouvelle règle (devant), appliqué à la reconstruction
                if (type == Reseau.Modif.AJOUTER_REGLE) allerDebut = true;
                else allerFin = true;
                envoyer(type, 0, c, v);
            }
        };
        valider.put(prixAjout, ajouter);
        addRenderableWidget(new HzButton(ax + 9 * CASE + 70, bas, 70, 18,
                Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ajouter")), Ui.OK, ajouter).look(HzButton.Look.PRIMARY));
    }

    private void initRachat(int bas) {
        int n0 = page * reglesParPage();
        for (int r = n0; r < Math.min(e.regles().size(), n0 + reglesParPage()); r++) {
            Reseau.ERegle regle = e.regles().get(r);
            int y = py + 54 + (r - n0) * (REGLE_H + 6), cw = (pw - 36 - 18 - 90) / 6;
            HzField[] boites = new HzField[6];
            for (int k = 0; k < 6; k++) boites[k] = champ(px + 27 + k * (cw + 3), y + 34, cw, regle.prix()[k]);
            final int index = r;
            Runnable enregistrer = () -> {
                for (int k = 0; k < 6; k++) {
                    long v = lire(boites[k], regle.prix()[k]);
                    if (v != regle.prix()[k]) envoyer(Reseau.Modif.PRIX_RACHAT, index, k, v);
                }
            };
            for (HzField b : boites) valider.put(b, enregistrer);
            addRenderableWidget(new HzButton(px + pw - 27 - 84, y + 32, 84, 16,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.enregistrer")), Ui.OR, enregistrer).look(HzButton.Look.PRIMARY));
            addRenderableWidget(new HzButton(px + pw - 27 - 22, y + 5, 22, 14, Component.literal("×"), Ui.DANGER,
                    () -> envoyer(Reseau.Modif.RETIRER_REGLE, index, 0, 0)));
        }
        ajoutPrix(bas, Reseau.Modif.AJOUTER_REGLE, 10);
    }

    private void initEchanges(int bas) {
        int n0 = page * lignesParPage(), xd = px + pw - 18;
        for (int i = n0; i < Math.min(e.echanges().size(), n0 + lignesParPage()); i++) {
            Reseau.EEchange x = e.echanges().get(i);
            int y = py + 52 + (i - n0) * LIGNE;
            final int index = i;
            addRenderableWidget(new HzButton(xd - 22, y + 4, 22, 16, Component.literal("×"), Ui.DANGER,
                    () -> envoyer(Reseau.Modif.ECH_RETIRER, index, 0, 0)));
            HzButton monter = addRenderableWidget(new HzButton(xd - 48, y + 4, 22, 16, Component.literal("↑"), Ui.ACCENT_NEUTRE,
                    () -> envoyer(Reseau.Modif.ECH_MONTER, index, 0, 0)));
            monter.active = i > 0;
            HzField bd = champ(xd - 186, y + 4, 40, x.nDonne(), 3), br = champ(xd - 132, y + 4, 40, x.nRecu(), 3);
            Runnable enregistrer = () -> {
                long d = lire(bd, x.nDonne()), r = lire(br, x.nRecu());
                if (d >= 1 && d != x.nDonne()) envoyer(Reseau.Modif.ECH_N_DONNE, index, 0, d);
                if (r >= 1 && r != x.nRecu()) envoyer(Reseau.Modif.ECH_N_RECU, index, 0, r);
            };
            valider.put(bd, enregistrer);
            valider.put(br, enregistrer);
            addRenderableWidget(new HzButton(xd - 86, y + 4, 34, 16,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ok")), Ui.OR, enregistrer).look(HzButton.Look.PRIMARY));
        }
        // ajouter : objet donné (clic gauche dans la barre) × quantité → objet reçu (clic droit) × quantité
        int x0 = px + 118 + 9 * CASE + 4;
        nDonneAjout = champ(x0 + 18, bas + 1, 36, 1, 3);
        nRecuAjout = champ(x0 + 86, bas + 1, 36, 1, 3);
        Runnable ajouter = () -> {
            int d = caseAjout(), r = caseRecuAjout();
            long nd = lire(nDonneAjout, 0), nr = lire(nRecuAjout, 0);
            if (d >= 0 && r >= 0 && nd >= 1 && nr >= 1) {
                allerFin = true;
                Reseau.CANAL.sendToServer(new Reseau.AjoutEchange(e.pnj(), d, (int) nd, r, (int) nr));
                SonsUi.clic(0.5F);
            }
        };
        valider.put(nDonneAjout, ajouter);
        valider.put(nRecuAjout, ajouter);
        addRenderableWidget(new HzButton(x0 + 126, bas, 46, 18,
                Component.literal(TexteClient.tr("gui.heraliz_quetes.edition.ajouter")), Ui.OK, ajouter).look(HzButton.Look.PRIMARY));
    }

    @Override
    public void tick() {
        super.tick();
        for (HzField b : valider.keySet()) b.tick();
        if (aRefaire) {
            aRefaire = false;
            rebuildWidgets();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            Runnable action = null;
            for (Map.Entry<HzField, Runnable> en : valider.entrySet()) if (en.getKey().isFocused()) action = en.getValue();
            if (action != null) {
                action.run();
                return true;
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
        // nom à la vraie taille de la police du titre (14), jamais agrandi
        g.drawString(f, Ui.lit(TexteClient.str(e.nom()), TITRE), px + 18, py + 11, Ui.argb(Ui.TEXT, 255), false);
        if (e.troc()) {
            Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.echanges").toUpperCase(), Ui.SMALL, px + 19, py + 32, Ui.argb(Ui.OR, 255), 0.8f);
        }
        String etat = e.cree() ? "gui.heraliz_quetes.edition.cree" : e.modifie() ? "gui.heraliz_quetes.edition.modifie" : "gui.heraliz_quetes.edition.fichier";
        Ui.spacedRight(g, f, TexteClient.tr(etat), Ui.SMALL, px + pw - 18, py + 18, Ui.argb(e.cree() || e.modifie() ? Ui.AMBRE : Ui.MUTED, 255), 0.6f);
        Ui.hairline(g, px + 18, px + pw - 18, py + 44, 0xFFFFFF, 30, false, true);
        List<Component> bulle = echanges() ? echanges(g, f, mx, my) : e.rachat() ? rachat(g, f, mx, my) : vente(g, f, mx, my);
        int bas = py + ph - 30;
        Ui.hairline(g, px + 18, px + pw - 18, bas - 8, 0xFFFFFF, 30, false, true);
        Ui.centered(g, f, Ui.lit((page + 1) + "/" + pages(), Ui.BOLD), px + 58, bas + 5, Ui.argb(Ui.TEXT, 255));
        List<Component> b2 = barre(g, f, mx, my, bas);
        if (b2 != null) bulle = b2;
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
            String sous = a.rarete() >= 0 ? TexteClient.tr("gui.heraliz_quetes.rarete." + a.rarete()) : "";
            int faim = faim(a.objet());
            if (faim > 0) {
                // cuisses de la barre de faim (1 cuisse = 2 points) et prix par cuisse, pour comparer les nourritures
                String cuisses = faim % 2 == 0 ? String.valueOf(faim / 2) : String.format(java.util.Locale.ROOT, "%.1f", faim / 2.0);
                String parCuisse = String.format(java.util.Locale.ROOT, "%.1f", a.prix() * 2.0 / faim);
                sous = (sous.isEmpty() ? "" : sous + " · ") + TexteClient.tr("gui.heraliz_quetes.edition.faim", cuisses, parCuisse);
            }
            if (!sous.isEmpty()) g.drawString(f, Ui.lit(sous, Ui.SMALL), px + 64, y + 14, Ui.argb(faim > 0 ? Ui.OK : col, 255), false);
            Ui.right(g, f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.SMALL), px + pw - 18 - 170, y + 8, Ui.argb(Ui.MUTED, 255));
            if (mx >= px + 38 && mx < px + 58 && my >= y + 2 && my < y + 22) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), a.objet()));
        }
        return bulle;
    }

    private List<Component> echanges(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int n0 = page * lignesParPage(), xd = px + pw - 18;
        if (e.echanges().isEmpty()) {
            Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.echanges_vide"), Ui.SMALL, px + 18, py + 56, Ui.argb(Ui.MUTED, 255), 0.4f);
        }
        // place des deux noms : entre les icônes et les champs de quantité
        int nomW = Math.max(30, (xd - 192 - (px + 38) - 2 * 26 - 14) / 2);
        for (int i = n0; i < Math.min(e.echanges().size(), n0 + lignesParPage()); i++) {
            Reseau.EEchange x = e.echanges().get(i);
            int y = py + 52 + (i - n0) * LIGNE;
            if ((i - n0) % 2 == 0) g.fill(px + 14, y, px + pw - 14, y + LIGNE - 2, Ui.argb(0xFFFFFF, 6));
            Ui.right(g, f, Ui.lit(String.valueOf(i + 1), Ui.SMALL), px + 32, y + 9, Ui.argb(Ui.FAINT, 255));
            int xa = px + 38;
            bulle = cote(g, f, x.donne(), xa, y, nomW, mx, my, bulle, TexteClient.tr("gui.heraliz_quetes.edition.donne"));
            int xr = xa + 26 + nomW + 14;
            Ui.centered(g, f, Ui.lit("→", Ui.BOLD), xr - 8, y + 9, Ui.argb(Ui.OR, 255));
            bulle = cote(g, f, x.recu(), xr, y, nomW, mx, my, bulle, TexteClient.tr("gui.heraliz_quetes.edition.recoit"));
            Ui.centered(g, f, Ui.lit("→", Ui.BOLD), xd - 139, y + 8, Ui.argb(Ui.MUTED, 255));
        }
        return bulle;
    }

    /** Un côté d'un échange dans l'éditeur : icône, nom, et ce qu'il est (donné / reçu). */
    private List<Component> cote(GuiGraphics g, Font f, ItemStack s, int x, int y, int nomW, int mx, int my, List<Component> bulle, String quoi) {
        g.fill(x, y + 2, x + 20, y + 22, Ui.argb(0xFFFFFF, 14));
        g.renderItem(s, x + 2, y + 4);
        g.drawString(f, Ui.lit(Ui.ellipsis(f, s.getHoverName().getString(), Ui.BOLD, nomW), Ui.BOLD), x + 26, y + 4, Ui.argb(Ui.TEXT, 255), false);
        g.drawString(f, Ui.lit(quoi, Ui.SMALL), x + 26, y + 14, Ui.argb(Ui.MUTED, 255), false);
        if (mx >= x && mx < x + 20 && my >= y + 2 && my < y + 22) return new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), s));
        return bulle;
    }

    /** Bas de l'écran : la barre d'objets du joueur (cliquer la case de l'objet à ajouter) et l'aide. */
    private List<Component> barre(GuiGraphics g, Font f, int mx, int my, int bas) {
        List<Component> bulle = null;
        int ax = px + 118, choisie = caseAjout(), recu = echanges() ? caseRecuAjout() : -1;
        String aide = echanges() ? "gui.heraliz_quetes.edition.ajout_echange"
                : e.rachat() ? "gui.heraliz_quetes.edition.ajout_regle" : "gui.heraliz_quetes.edition.main_vide";
        g.drawString(f, Ui.lit(Ui.ellipsis(f, TexteClient.tr(aide), Ui.SMALL, px + pw - 18 - ax), Ui.SMALL), ax, bas - 19, Ui.argb(Ui.FAINT, 255), false);
        for (int k = 0; k < 9; k++) {
            int x = ax + k * CASE;
            ItemStack s = caseBarre(k);
            int teinte = k == recu ? Ui.OK : Ui.OR;
            boolean marquee = k == choisie || k == recu;
            g.fill(x, bas, x + CASE - 1, bas + CASE - 1, Ui.argb(marquee ? teinte : 0xFFFFFF, marquee ? 70 : 14));
            if (marquee) Ui.softOutline(g, x, bas, CASE - 1, CASE - 1, Ui.argb(teinte, 220));
            if (!s.isEmpty()) {
                g.renderItem(s, x + 1, bas + 1);
                g.renderItemDecorations(f, s, x + 1, bas + 1);
                if (mx >= x && mx < x + CASE && my >= bas && my < bas + CASE) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), s));
            }
        }
        if (echanges()) {
            // aperçu de l'échange à ajouter : [donné] × n → [reçu] × n
            int x0 = ax + 9 * CASE + 4;
            if (choisie >= 0) g.renderItem(caseBarre(choisie), x0, bas + 1);
            Ui.centered(g, f, Ui.lit("→", Ui.BOLD), x0 + 61, bas + 5, Ui.argb(Ui.OR, 255));
            if (recu >= 0) g.renderItem(caseBarre(recu), x0 + 68, bas + 1);
        } else if (e.rachat()) {
            Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.aide_rachat"), Ui.SMALL, px + 18, bas - 30, Ui.argb(Ui.FAINT, 255), 0.4f);
        }
        return bulle;
    }

    /** Objet de la case k (0 à 8) de la barre d'objets, sans le registre lui-même. */
    private static ItemStack caseBarre(int k) {
        var p = Minecraft.getInstance().player;
        if (p == null || k < 0 || k > 8) return ItemStack.EMPTY;
        ItemStack s = p.getInventory().getItem(k);
        return s.is(fr.heraliz.quetes.HeralizQuetes.REGISTRE.get()) ? ItemStack.EMPTY : s;
    }

    /** Case qui sera ajoutée (objet vendu, règle, ou objet DONNÉ d'un échange) : celle cliquée, sinon la première qui a un objet (-1 : aucune). */
    private int caseAjout() {
        if (caseChoisie >= 0 && !caseBarre(caseChoisie).isEmpty()) return caseChoisie;
        for (int k = 0; k < 9; k++) if (!caseBarre(k).isEmpty()) return k;
        return -1;
    }

    /** Case de l'objet REÇU d'un échange (clic droit dans la barre ; -1 : pas encore choisie). */
    private int caseRecuAjout() {
        return caseRecu >= 0 && !caseBarre(caseRecu).isEmpty() ? caseRecu : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int ax = px + 118, bas = py + ph - 30;
        if (my >= bas && my < bas + CASE && mx >= ax && mx < ax + 9 * CASE) {
            int k = (int) ((mx - ax) / CASE);
            if (!caseBarre(k).isEmpty()) {
                if (echanges() && bouton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) caseRecu = k;
                else caseChoisie = k;
                SonsUi.clic(0.5F);
            }
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    /** Points de faim que rend l'objet (0 : pas une nourriture). */
    private static int faim(ItemStack s) {
        try {
            var p = s.getItem().getFoodProperties(s, Minecraft.getInstance().player);
            return p == null ? 0 : p.getNutrition();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Nom montré pour une règle : le tag tel quel, ou le nom de l'objet pour une règle d'un objet (« =minecraft:diamond »). */
    private static String nomRegle(String tag) {
        if (!tag.startsWith("=")) return tag;
        ResourceLocation rl = ResourceLocation.tryParse(tag.substring(1));
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        if (it == null || it == Items.AIR) return tag.substring(1);
        // objet sans nom traduit (arme TaCZ sans GunId : « item.tacz.modern_kinetic_gun ») : nom lisible
        String nom = new ItemStack(it).getHoverName().getString();
        if (!nom.equals(it.getDescriptionId())) return nom;
        if ("tacz".equals(rl.getNamespace())) {
            switch (rl.getPath()) {
                case "modern_kinetic_gun" -> {
                    return TexteClient.tr("gui.heraliz_quetes.edition.toutes_armes");
                }
                case "ammo" -> {
                    return TexteClient.tr("gui.heraliz_quetes.edition.toutes_munitions");
                }
                case "attachment" -> {
                    return TexteClient.tr("gui.heraliz_quetes.edition.tous_accessoires");
                }
                default -> {
                }
            }
        }
        return TexteClient.tr("gui.heraliz_quetes.edition.objet_sans_nom", rl.getPath().replace('_', ' '), rl.getNamespace());
    }

    private List<Component> rachat(GuiGraphics g, Font f, int mx, int my) {
        int cw = (pw - 36 - 18 - 90) / 6, n0 = page * reglesParPage();
        if (e.regles().isEmpty()) {
            Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.edition.regles_vide"), Ui.SMALL, px + 18, py + 56, Ui.argb(Ui.MUTED, 255), 0.4f);
        }
        for (int r = n0; r < Math.min(e.regles().size(), n0 + reglesParPage()); r++) {
            Reseau.ERegle regle = e.regles().get(r);
            int y = py + 54 + (r - n0) * (REGLE_H + 6);
            Ui.glass(g, px + 18, y, pw - 36, REGLE_H, 110);
            String titre = Ui.ellipsis(f, TexteClient.tr("gui.heraliz_quetes.edition.regle", r + 1, nomRegle(regle.tag())), Ui.BOLD, pw - 36 - 18 - 40);
            g.drawString(f, Ui.lit(titre, Ui.BOLD), px + 27, y + 7, Ui.argb(Ui.TEXT, 255), false);
            for (int k = 0; k < 6; k++) {
                Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.rarete." + k).toUpperCase(), Ui.SMALL, px + 27 + k * (cw + 3), y + 23,
                        Ui.argb(couleur(k), 255), 0.4f);
            }
        }
        return null;
    }
}
