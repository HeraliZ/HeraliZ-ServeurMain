package fr.heraliz.quetes.client;

import fr.heraliz.quetes.client.kit.Echelle;
import fr.heraliz.quetes.client.kit.HzButton;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Écran d'un marchand (style des écrans de clan). Vendeur : une carte par article (icône, nom, rareté, prix, ACHETER ;
 * Maj + clic = 5). Racheteur : l'inventaire du joueur, on clique ce qu'on vend (prix au survol), total à droite, « Vendre
 * la sélection » ou « Tout vendre ». Échanges (1.11.0, onglet à part, ou seul pour un marchand de troc) : une carte par
 * échange (objet donné × n → objet reçu × n, combien on en a, ÉCHANGER ; Maj + clic = 5 fois). Prix, solde et échanges
 * viennent du serveur, qui revérifie tout.
 */
public final class EcranMarchand extends Screen {
    /** Titre à sa vraie taille (police 14) : jamais de texte agrandi, flou (25/10, audit des écrans). */
    private static final ResourceLocation TITRE = new ResourceLocation("heraliz_quetes", "ui_titre");
    /** Couleurs des raretés (Commun ... Unique / Mythique). */
    private static final int[] RARETES = {0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646};
    private static final int COLS = 4, CARTE_H = 44, CASE = 20, COLS_E = 2;

    private Reseau.Boutique b;
    private final Set<Integer> choisies = new LinkedHashSet<>();
    private int px, py, pw, ph, defil;
    private HzButton vendreSel, vendreTout;
    /** Onglet des échanges ouvert (toujours pour un marchand de troc). */
    private boolean ongletEchanges;
    /** Boutons à refaire au prochain tick (onglet changé, échanges apparus ou disparus). */
    private boolean aRefaire;
    /** Aide du rachat, coupée en lignes à la largeur de la colonne de gauche (calculée dans init). */
    private List<String> aideLignes = List.of();

    private EcranMarchand(Reseau.Boutique b) {
        super(Component.translatable("gui.heraliz_quetes.marchand"));
        this.b = b;
        this.ongletEchanges = b.troc();
    }

    static void recevoir(Reseau.Boutique m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.entite() < 0) {
            if (mc.screen instanceof EcranMarchand) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof EcranMarchand e && e.b.entite() == m.entite()) {
            boolean onglets = e.onglets();
            e.b = m;
            e.choisies.removeIf(c -> m.prixSlots()[c] <= 0);
            if (m.troc()) e.ongletEchanges = true;
            else if (m.echanges().isEmpty()) e.ongletEchanges = false;
            if (onglets != e.onglets()) e.aRefaire = true;
            return;
        }
        if (!m.ouvrir()) return;
        SonsUi.ouvrir(0.5F);
        mc.setScreen(new EcranMarchand(m));
    }

    private static int couleur(int rarete) {
        return rarete < 0 || rarete >= RARETES.length ? Ui.TEXT : RARETES[rarete];
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(600, width - 24);
        ph = Math.min(330, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        vendreSel = null;
        vendreTout = null;
        // colonne de gauche du rachat : de px + 18 jusqu'à 10 px avant le panneau de droite (px + pw - 200)
        aideLignes = Ui.lignesEspacees(font, TexteClient.tr("gui.heraliz_quetes.marchand.aide"), Ui.SMALL, 0.4f, pw - 228);
        if (onglets()) {
            // deux onglets en haut, au milieu : ce qu'il vend (ou rachète) / ses échanges
            int cx = px + pw / 2;
            HzButton b1 = addRenderableWidget(new HzButton(cx - 104, py + 15, 100, 16,
                    Component.literal(TexteClient.tr(b.rachat() ? "gui.heraliz_quetes.marchand.onglet.vendre" : "gui.heraliz_quetes.marchand.onglet.acheter")),
                    Ui.OR, () -> changerOnglet(false)));
            HzButton b2 = addRenderableWidget(new HzButton(cx + 4, py + 15, 100, 16,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.onglet.echanges")), Ui.OR, () -> changerOnglet(true)));
            (ongletEchanges ? b2 : b1).look(HzButton.Look.PRIMARY);
        }
        if (b.rachat() && !ongletEchanges) {
            int bx = px + pw - 190, bw = 172;
            vendreTout = addRenderableWidget(new HzButton(bx, py + ph - 56, bw, 18,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.tout")), Ui.OR, this::toutVendre));
            vendreSel = addRenderableWidget(new HzButton(bx, py + ph - 32, bw, 20,
                    Component.literal(TexteClient.tr("gui.heraliz_quetes.marchand.vendre")), Ui.OR, this::vendre).look(HzButton.Look.PRIMARY));
        }
    }

    /** Deux onglets : un vendeur ou un racheteur qui a aussi des échanges. */
    private boolean onglets() {
        return !b.troc() && !b.echanges().isEmpty();
    }

    private void changerOnglet(boolean echanges) {
        if (ongletEchanges == echanges) return;
        ongletEchanges = echanges;
        defil = 0;
        choisies.clear();
        aRefaire = true;
        SonsUi.clic(0.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (aRefaire) {
            aRefaire = false;
            rebuildWidgets();
        }
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

    private ItemStack caseInv(int i) {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().items.get(i);
    }

    private long prixCase(int i) {
        return (long) b.prixSlots()[i] * caseInv(i).getCount();
    }

    private void vendre() {
        if (choisies.isEmpty()) return;
        Reseau.CANAL.sendToServer(new Reseau.Vente(b.entite(), new ArrayList<>(choisies)));
        choisies.clear();
    }

    /** Tout ce qu'il prend, sauf les objets d'événement quand ils ne rapporteraient aucun point (gardés pour plus tard). */
    private void toutVendre() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Reseau.CASES; i++) {
            if (b.prixSlots()[i] > 0 && !caseInv(i).isEmpty() && !(b.pointsSlots()[i] > 0 && b.raisonPoints() != 0)) l.add(i);
        }
        if (l.isEmpty()) return;
        Reseau.CANAL.sendToServer(new Reseau.Vente(b.entite(), l));
        choisies.clear();
    }

    // ------------------------------------------------------------------ disposition
    /** Case de l'inventaire n° i (0-8 barre d'objets, en bas) : coin haut-gauche. */
    private int[] posCase(int i) {
        int col = i % 9, rang = i < 9 ? 3 : i / 9 - 1;
        int x = px + 18 + col * (CASE + 3), y = py + 66 + rang * (CASE + 3) + (i < 9 ? 6 : 0);
        return new int[]{x, y};
    }

    private int carteL() {
        return (pw - 36 - (COLS - 1) * 8) / COLS;
    }

    private int[] posCarte(int n) {
        int x = px + 18 + (n % COLS) * (carteL() + 8), y = py + 56 + (n / COLS) * (CARTE_H + 8) - defil;
        return new int[]{x, y};
    }

    private boolean dansListe(int y) {
        return y >= py + 52 && y + CARTE_H <= py + ph - 20;
    }

    private int carteLE() {
        return (pw - 36 - (COLS_E - 1) * 8) / COLS_E;
    }

    private int[] posEchange(int n) {
        int x = px + 18 + (n % COLS_E) * (carteLE() + 8), y = py + 56 + (n / COLS_E) * (CARTE_H + 8) - defil;
        return new int[]{x, y};
    }

    /** L'échange n° n peut-il se faire (actif, et le joueur a de quoi donner) ? */
    private static boolean faisable(Reseau.Echange x) {
        return x.actif() && x.possede() >= x.nDonne();
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (super.mouseClicked(mx, my, bouton)) return true;
        if (ongletEchanges) {
            for (int n = 0; n < b.echanges().size(); n++) {
                int[] c = posEchange(n);
                if (!dansListe(c[1])) continue;
                int bx = c[0] + carteLE() - 58, by = c[1] + CARTE_H - 17;
                if (mx >= bx && mx < bx + 52 && my >= by && my < by + 13) {
                    Reseau.Echange x = b.echanges().get(n);
                    if (faisable(x)) {
                        Reseau.CANAL.sendToServer(new Reseau.Troc(b.entite(), x.index(), hasShiftDown() ? 5 : 1));
                        SonsUi.clic(0.5F);
                    }
                    return true;
                }
            }
        } else if (b.rachat()) {
            for (int i = 0; i < Reseau.CASES; i++) {
                int[] c = posCase(i);
                if (mx >= c[0] && mx < c[0] + CASE && my >= c[1] && my < c[1] + CASE) {
                    if (b.prixSlots()[i] > 0 && !caseInv(i).isEmpty()) {
                        if (!choisies.remove(i)) choisies.add(i);
                        SonsUi.clic(0.5F);
                    }
                    return true;
                }
            }
        } else {
            for (int n = 0; n < b.articles().size(); n++) {
                int[] c = posCarte(n);
                if (!dansListe(c[1])) continue;
                int bx = c[0] + carteL() - 46, by = c[1] + CARTE_H - 17;
                if (mx >= bx && mx < bx + 40 && my >= by && my < by + 13) {
                    Reseau.CANAL.sendToServer(new Reseau.Achat(b.entite(), b.articles().get(n).index(), hasShiftDown() ? 5 : 1));
                    SonsUi.clic(0.5F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        if (ongletEchanges) {
            int rangs = (b.echanges().size() + COLS_E - 1) / COLS_E;
            int max = Math.max(0, rangs * (CARTE_H + 8) - (ph - 76));
            defil = (int) Math.max(0, Math.min(max, defil - d * 26));
            return true;
        }
        if (b.rachat()) return false;
        int rangs = (b.articles().size() + COLS - 1) / COLS;
        int max = Math.max(0, rangs * (CARTE_H + 8) - (ph - 76));
        defil = (int) Math.max(0, Math.min(max, defil - d * 26));
        return true;
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph, 215);
        g.drawString(f, Ui.lit(TexteClient.str(b.nom()), TITRE), px + 18, py + 11, Ui.argb(Ui.TEXT, 255), false);
        Ui.spaced(g, f, TexteClient.str(b.role()).toUpperCase(), Ui.SMALL, px + 19, py + 32, Ui.argb(Ui.OR, 255), 0.8f);
        Ui.right(g, f, Ui.lit(Ui.nombre(b.solde()) + " " + TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.BOLD), px + pw - 18, py + 15, Ui.argb(Ui.OR, 255));
        Ui.spacedRight(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.solde"), Ui.SMALL, px + pw - 18, py + 28, Ui.argb(Ui.MUTED, 255), 0.6f);
        Ui.hairline(g, px + 18, px + pw - 18, py + 44, 0xFFFFFF, 30, false, true);
        List<Component> bulle = ongletEchanges ? echanges(g, f, mx, my) : b.rachat() ? rachat(g, f, mx, my) : achat(g, f, mx, my);
        super.render(g, mx, my, pt);
        if (bulle != null) Ui.tooltip(g, f, bulle, mx, my, Ui.OR);
    }

    /** Une icône d'objet dans sa case (32 × 32), teintée de sa rareté. */
    private void icone(GuiGraphics g, ItemStack s, int x, int y, int col) {
        g.fill(x, y, x + 32, y + 32, Ui.argb(col, 40));
        Ui.softOutline(g, x, y, 32, 32, Ui.argb(col, 170));
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    private List<Component> echanges(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int cl = carteLE();
        if (b.echanges().isEmpty()) vide(g, f, "gui.heraliz_quetes.marchand.echanges_vide");
        g.enableScissor(px, py + 50, px + pw, py + ph - 16);
        for (int n = 0; n < b.echanges().size(); n++) {
            Reseau.Echange x = b.echanges().get(n);
            int[] c = posEchange(n);
            if (c[1] + CARTE_H < py + 50 || c[1] > py + ph - 16) continue;
            boolean visible = dansListe(c[1]), possible = faisable(x);
            int col = couleur(x.rarete());
            Ui.glass(g, c[0], c[1], cl, CARTE_H, 110);
            // [donné] ×n → [reçu] ×n
            icone(g, x.donne(), c[0] + 6, c[1] + 6, Ui.MUTED);
            g.drawString(f, Ui.lit("×" + x.nDonne(), Ui.BOLD), c[0] + 40, c[1] + 18, Ui.argb(Ui.TEXT, 255), false);
            Ui.centered(g, f, Ui.lit("→", Ui.BOLD), c[0] + 70, c[1] + 18, Ui.argb(Ui.OR, 255));
            icone(g, x.recu(), c[0] + 78, c[1] + 6, col);
            g.drawString(f, Ui.lit("×" + x.nRecu(), Ui.BOLD), c[0] + 112, c[1] + 18, Ui.argb(Ui.TEXT, 255), false);
            // texte : ce qu'on reçoit, contre quoi, combien on en a
            int tx = c[0] + 138, tw = cl - 138 - 6;
            g.drawString(f, Ui.lit(Ui.ellipsis(f, x.recu().getHoverName().getString(), Ui.BOLD, tw), Ui.BOLD), tx, c[1] + 6, Ui.argb(col, 255), false);
            String contre = TexteClient.tr("gui.heraliz_quetes.marchand.contre", x.nDonne(), x.donne().getHoverName().getString());
            g.drawString(f, Ui.lit(Ui.ellipsis(f, contre, Ui.SMALL, tw), Ui.SMALL), tx, c[1] + 17, Ui.argb(Ui.MUTED, 255), false);
            String etat = x.actif() ? TexteClient.tr("gui.heraliz_quetes.marchand.possede", x.possede(), x.nDonne())
                    : TexteClient.tr("gui.heraliz_quetes.marchand.echange_coupe");
            g.drawString(f, Ui.lit(Ui.ellipsis(f, etat, Ui.SMALL, tw - 60), Ui.SMALL), tx, c[1] + CARTE_H - 13,
                    Ui.argb(possible ? Ui.OK : Ui.DANGER, 255), false);
            // bouton ÉCHANGER (éteint si impossible)
            int bx = c[0] + cl - 58, by = c[1] + CARTE_H - 17;
            boolean survol = mx >= bx && mx < bx + 52 && my >= by && my < by + 13 && visible;
            g.fill(bx, by, bx + 52, by + 13, possible ? Ui.argb(Ui.OR, survol ? 255 : 210) : Ui.argb(0xFFFFFF, 20));
            Ui.spacedCentered(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.echanger"), Ui.SMALL, bx + 26, by + 3,
                    possible ? 0xFF0D0F14 : Ui.argb(Ui.MUTED, 255), 0.4f);
            if (visible && my >= c[1] + 6 && my < c[1] + 38) {
                if (mx >= c[0] + 6 && mx < c[0] + 38) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), x.donne()));
                else if (mx >= c[0] + 78 && mx < c[0] + 110) bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), x.recu()));
            }
        }
        g.disableScissor();
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.lot_echange"), Ui.SMALL, px + 18, py + ph - 12, Ui.argb(Ui.FAINT, 255), 0.4f);
        return bulle;
    }

    /** Message d'un onglet vide, en haut de la liste, coupé en lignes s'il est trop long. */
    private void vide(GuiGraphics g, Font f, String cle) {
        int y = py + 56;
        for (String l : Ui.lignesEspacees(f, TexteClient.tr(cle), Ui.SMALL, 0.4f, pw - 36)) {
            Ui.spaced(g, f, l, Ui.SMALL, px + 18, y, Ui.argb(Ui.MUTED, 255), 0.4f);
            y += 10;
        }
    }

    private List<Component> achat(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        int cl = carteL();
        if (b.articles().isEmpty()) vide(g, f, "gui.heraliz_quetes.marchand.articles_vide");
        g.enableScissor(px, py + 50, px + pw, py + ph - 16);
        for (int n = 0; n < b.articles().size(); n++) {
            Reseau.Article a = b.articles().get(n);
            int[] c = posCarte(n);
            if (c[1] + CARTE_H < py + 50 || c[1] > py + ph - 16) continue;
            int col = couleur(a.rarete());
            Ui.glass(g, c[0], c[1], cl, CARTE_H, 110);
            g.fill(c[0] + 6, c[1] + 6, c[0] + 38, c[1] + 38, Ui.argb(col, 40));
            Ui.softOutline(g, c[0] + 6, c[1] + 6, 32, 32, Ui.argb(col, 170));
            g.pose().pushPose();
            g.pose().translate(c[0] + 10, c[1] + 10, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(a.objet(), 0, 0);
            g.pose().popPose();
            String nom = Ui.ellipsis(f, a.objet().getHoverName().getString(), Ui.BOLD, cl - 50);
            g.drawString(f, Ui.lit(nom, Ui.BOLD), c[0] + 44, c[1] + 6, Ui.argb(Ui.TEXT, 255), false);
            if (a.rarete() >= 0) g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.rarete." + a.rarete()), Ui.SMALL), c[0] + 44, c[1] + 17, Ui.argb(col, 255), false);
            g.drawString(f, Ui.lit(Ui.nombre(a.prix()), Ui.BOLD), c[0] + 44, c[1] + CARTE_H - 14, Ui.argb(Ui.OR, 255), false);
            int bx = c[0] + cl - 46, by = c[1] + CARTE_H - 17;
            boolean survol = mx >= bx && mx < bx + 40 && my >= by && my < by + 13 && dansListe(c[1]);
            g.fill(bx, by, bx + 40, by + 13, Ui.argb(Ui.OR, survol ? 255 : 210));
            Ui.spacedCentered(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.acheter"), Ui.SMALL, bx + 20, by + 3, 0xFF0D0F14, 0.4f);
            if (mx >= c[0] + 6 && mx < c[0] + 38 && my >= c[1] + 6 && my < c[1] + 38 && dansListe(c[1])) {
                bulle = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), a.objet()));
            }
        }
        g.disableScissor();
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.lot"), Ui.SMALL, px + 18, py + ph - 12, Ui.argb(Ui.FAINT, 255), 0.4f);
        return bulle;
    }

    private List<Component> rachat(GuiGraphics g, Font f, int mx, int my) {
        List<Component> bulle = null;
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.inventaire"), Ui.SMALL, px + 18, py + 50, Ui.argb(Ui.MUTED, 255), 0.8f);
        for (int i = 0; i < Reseau.CASES; i++) {
            int[] c = posCase(i);
            ItemStack s = caseInv(i);
            boolean prend = b.prixSlots()[i] > 0 && !s.isEmpty();
            boolean choisie = choisies.contains(i);
            g.fill(c[0], c[1], c[0] + CASE, c[1] + CASE, Ui.argb(0xFFFFFF, choisie ? 34 : 14));
            if (prend && b.rarSlots()[i] >= 0) g.fill(c[0] + 2, c[1] + CASE - 2, c[0] + CASE - 2, c[1] + CASE - 1, Ui.argb(couleur(b.rarSlots()[i]), 230));
            if (choisie) Ui.softOutline(g, c[0], c[1], CASE, CASE, Ui.argb(Ui.OR, 255));
            if (!s.isEmpty()) {
                g.renderItem(s, c[0] + 2, c[1] + 2);
                g.renderItemDecorations(font, s, c[0] + 2, c[1] + 2);
                if (!prend) g.fill(c[0], c[1], c[0] + CASE, c[1] + CASE, 0xA00D0F14);
            }
            if (!s.isEmpty() && mx >= c[0] && mx < c[0] + CASE && my >= c[1] && my < c[1] + CASE) {
                bulle = new ArrayList<>();
                int teinte = prend ? couleur(b.rarSlots()[i]) : Ui.MUTED;
                bulle.add(Ui.lit(s.getHoverName().getString(), Ui.BOLD).withStyle(st -> st.withColor(teinte)));
                if (prend) {
                    bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.prix", Ui.nombre(b.prixSlots()[i])), Ui.FONT));
                    if (s.getCount() > 1) bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.prix_pile", s.getCount(), Ui.nombre(prixCase(i))), Ui.FONT));
                    // objet d'événement (25/10) : points de saison pour le clan, ou pourquoi il n'en aurait pas
                    if (b.pointsSlots()[i] > 0) {
                        if (b.raisonPoints() == 0) {
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.points", b.pointsSlots()[i]), Ui.FONT).withStyle(st -> st.withColor(Ui.OK)));
                        } else {
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.sans_points." + b.raisonPoints()), Ui.FONT).withStyle(st -> st.withColor(Ui.AMBRE)));
                            bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.garder"), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
                        }
                    }
                } else bulle.add(Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.refuse"), Ui.FONT).withStyle(st -> st.withColor(Ui.MUTED)));
            }
        }
        // aide sous l'inventaire, sur plusieurs lignes : elle ne passe plus sous le panneau de droite
        int ay = py + 168;
        for (String l : aideLignes) {
            if (ay > py + ph - 14) break;
            Ui.spaced(g, f, l, Ui.SMALL, px + 18, ay, Ui.argb(Ui.FAINT, 255), 0.4f);
            ay += 10;
        }
        // panneau de droite : sélection et total
        int rx = px + pw - 200, ry = py + 52, rw = 190;
        Ui.glass(g, rx, ry, rw, ph - 60, 120);
        Ui.spaced(g, f, TexteClient.tr("gui.heraliz_quetes.marchand.a_vendre"), Ui.SMALL, rx + 9, ry + 8, Ui.argb(Ui.MUTED, 255), 0.8f);
        int y = ry + 22;
        long total = 0, points = 0;
        int n = 0;
        for (int i : choisies) {
            ItemStack s = caseInv(i);
            if (s.isEmpty()) continue;
            total += prixCase(i);
            points += Math.max(0, b.pointsSlots()[i]);
            if (n++ >= 9) continue;
            String nom = Ui.ellipsis(f, s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : ""), Ui.FONT, rw - 70);
            g.drawString(f, Ui.lit(nom, Ui.FONT), rx + 9, y, Ui.argb(couleur(b.rarSlots()[i]), 255), false);
            Ui.right(g, f, Ui.lit(Ui.nombre(prixCase(i)), Ui.BOLD), rx + rw - 9, y, Ui.argb(Ui.TEXT, 255));
            y += 11;
        }
        if (n > 9) g.drawString(f, Ui.lit("+" + (n - 9), Ui.FONT), rx + 9, y, Ui.argb(Ui.MUTED, 255), false);
        // objets d'événement choisis : une ligne de plus sous le total (points de saison, ou pourquoi il n'y en a pas)
        int ty = py + ph - (points > 0 ? 93 : 82);
        Ui.hairline(g, rx + 9, rx + rw - 9, ty, 0xFFFFFF, 50, false, false);
        g.drawString(f, Ui.lit(TexteClient.tr("gui.heraliz_quetes.marchand.total"), Ui.BOLD), rx + 9, ty + 6, Ui.argb(Ui.TEXT, 255), false);
        Ui.right(g, f, Ui.lit(Ui.nombre(total) + " " + TexteClient.tr("gui.heraliz_quetes.marchand.herals"), Ui.BOLD), rx + rw - 9, ty + 6, Ui.argb(Ui.OR, 255));
        if (points > 0) {
            String ligne = b.raisonPoints() == 0 ? TexteClient.tr("gui.heraliz_quetes.marchand.points", points)
                    : TexteClient.tr("gui.heraliz_quetes.marchand.sans_points." + b.raisonPoints());
            g.drawString(f, Ui.lit(Ui.ellipsis(f, ligne, Ui.FONT, rw - 18), Ui.FONT), rx + 9, ty + 17,
                    Ui.argb(b.raisonPoints() == 0 ? Ui.OK : Ui.AMBRE, 255), false);
        }
        if (vendreSel != null) vendreSel.active = !choisies.isEmpty();
        return bulle;
    }
}
