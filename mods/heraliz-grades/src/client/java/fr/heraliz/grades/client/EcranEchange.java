package fr.heraliz.grades.client;

import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.HzButton;
import fr.heraliz.grades.client.kit.HzField;
import fr.heraliz.grades.client.kit.SonsUi;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauBoutique;
import fr.heraliz.grades.reseau.ReseauBoutique.ActionEchange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Échange entre deux joueurs (/trade, maquette validée, style des écrans de clan ; canevas 640 x 360). À gauche ce que
 * je donne (cases choisies dans mon inventaire, dessous, et PB), à droite ce que l'autre donne. L'écran ne fait que
 * DEMANDER (proposer une case, retirer, PB, prêt, annuler) : le serveur garde l'échange et le revérifie à chaque tick.
 * Fermer l'écran annule l'échange (sauf pendant l'exécution).
 */
public final class EcranEchange extends Screen {
    private static final int PB = 0xFFC23A, CASE = 22, ECART = 3, INV = 20;

    private ReseauBoutique.EtatEchange etat;
    private boolean fermeParServeur;
    private int px, py, pw, ph, cw;
    private HzButton pret, annuler, validerPb;
    private HzField champPb;
    private String message = "";
    private long messageA;

    private EcranEchange(ReseauBoutique.EtatEchange e) {
        super(Component.translatable("gui.heraliz_grades.trade.titre"));
        etat = e;
    }

    public static void recevoir(ReseauBoutique.EtatEchange e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase() >= 3) {
            if (mc.screen instanceof EcranEchange x) {
                x.fermeParServeur = true;
                mc.setScreen(null);
            }
            if (mc.player != null && !e.message().isEmpty() && e.phase() == 4) mc.player.displayClientMessage(texte(e.message()).copy().withStyle(net.minecraft.ChatFormatting.RED), false);
            if (e.phase() == 3) SonsUi.ouvrir(0.9F);
            return;
        }
        if (mc.screen instanceof EcranEchange x) {
            x.recevoirEtat(e);
        } else {
            SonsUi.ouvrir(0.6F);
            mc.setScreen(new EcranEchange(e));
        }
    }

    private void recevoirEtat(ReseauBoutique.EtatEchange e) {
        etat = e;
        if (!e.message().isEmpty()) {
            message = e.message();
            messageA = System.currentTimeMillis();
        }
        if (champPb != null && !champPb.isFocused()) champPb.setValue(e.mesPb() > 0 ? String.valueOf(e.mesPb()) : "");
        majBoutons();
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.trade." + cle, args).getString();
    }

    /** Message du serveur : code (« plein ») ou code et nom (« solde:Pseudo »). */
    private static Component texte(String m) {
        int i = m.indexOf(':');
        return i < 0 ? Component.translatable("gui.heraliz_grades.trade.msg." + m)
                : Component.translatable("gui.heraliz_grades.trade.msg." + m.substring(0, i), m.substring(i + 1));
    }

    private static void action(ActionEchange.Genre g, long v) {
        ReseauBoutique.CANAL.sendToServer(new ActionEchange(g, v));
    }

    // ------------------------------------------------------------------ mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(600, width - 32);
        ph = Math.min(334, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cw = (pw - 28 - 10) / 2;
        int bas = py + ph - 30;
        pret = addRenderableWidget(new HzButton(px + pw / 2 - 124, bas, 120, 20, Component.empty(), Ui.OK, () -> action(ActionEchange.Genre.PRET, 0))
                .look(HzButton.Look.PRIMARY));
        annuler = addRenderableWidget(new HzButton(px + pw / 2 + 4, bas, 120, 20, Component.literal(t("annuler")), Ui.DANGER, () -> {
            action(ActionEchange.Genre.ANNULER, 0);
        }).danger());
        int fy = py + 104;
        champPb = addRenderableWidget(new HzField(px + 24, fy, 110, 16, 9, HzField.NOMBRE).suffix("PB").placeholder("0").accent(PB));
        champPb.setValue(etat.mesPb() > 0 ? String.valueOf(etat.mesPb()) : "");
        validerPb = addRenderableWidget(new HzButton(px + 140, fy, 70, 16, Component.literal(t("valider")), PB, this::envoyerPb).look(HzButton.Look.OUTLINE));
        majBoutons();
    }

    private void envoyerPb() {
        String v = champPb.getValue().trim();
        long n = v.isEmpty() ? 0 : Long.parseLong(v);
        action(ActionEchange.Genre.PB, n);
        champPb.setFocused(false);
    }

    private void majBoutons() {
        if (pret == null) return;
        boolean libre = etat.phase() < 2;
        pret.setMessage(Component.literal(t(etat.monPret() ? "plus_pret" : "pret")));
        pret.active = libre;
        annuler.active = libre;
        champPb.active = libre && etat.sesPb() == 0;
        validerPb.active = champPb.active;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && champPb.isFocused()) {
            envoyerPb();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Case n de l'inventaire dessiné : rangées 9 à 35 puis la barre (0 à 8), comme Minecraft. */
    private int caseInventaire(int n) {
        return n < 27 ? n + 9 : n - 27;
    }

    private int[] posInventaire(int n) {
        int x0 = px + 14 + (cw - 9 * INV) / 2, y0 = py + 168;
        return new int[]{x0 + (n % 9) * INV, y0 + (n / 9) * INV + (n >= 27 ? 4 : 0)};
    }

    private int[] posOffre(int cote, int i) {
        int x0 = px + 14 + cote * (cw + 10) + (cw - 9 * (CASE + ECART) + ECART) / 2;
        return new int[]{x0 + i * (CASE + ECART), py + 68};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (etat.phase() < 2 && !champPb.isMouseOver(mx, my)) {
            for (int n = 0; n < 36; n++) {
                int[] p = posInventaire(n);
                if (mx >= p[0] && mx < p[0] + INV - 1 && my >= p[1] && my < p[1] + INV - 1) {
                    int c = caseInventaire(n);
                    int deja = etat.mesCases().indexOf(c);
                    if (deja >= 0) action(ActionEchange.Genre.RETIRER, deja);
                    else if (!Minecraft.getInstance().player.getInventory().getItem(c).isEmpty()) action(ActionEchange.Genre.PROPOSER, c);
                    SonsUi.clic(0.6F);
                    return true;
                }
            }
            for (int i = 0; i < etat.mesObjets().size(); i++) {
                int[] p = posOffre(0, i);
                if (mx >= p[0] && mx < p[0] + CASE && my >= p[1] && my < p[1] + CASE) {
                    action(ActionEchange.Genre.RETIRER, i);
                    SonsUi.clic(0.6F);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public void tick() {
        super.tick();
        majBoutons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // fermé par le joueur (Échap) : l'échange est annulé ; le serveur refuse pendant l'exécution
        if (!fermeParServeur) action(ActionEchange.Genre.ANNULER, 0);
        Echelle.restaurer();
        super.removed();
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font f = font;
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);

        Component titre = Ui.in(Component.literal(t("titre").toUpperCase(Locale.ROOT)), Ui.BOLD);
        Ui.gros(g, f, titre, px + 14, py + 12, 1.6f, Ui.argb(Ui.TEXT, 255));
        Ui.spaced(g, f, t("sous_titre"), Ui.SMALL, px + 14 + f.width(titre) * 1.6f + 12, py + 18, Ui.argb(Ui.MUTED, 255), 0.8f);
        Component cs = Ui.in(Component.literal(etat.monSolde() < 0 ? "— PB" : Ui.nombre(etat.monSolde()) + " PB"), Ui.BOLD);
        int sw = f.width(cs) + 22, sx = px + pw - 14 - sw;
        Ui.soft(g, sx, py + 10, sw, 18, Ui.argb(PB, 30));
        Ui.softOutline(g, sx + 1, py + 11, sw - 2, 16, Ui.argb(PB, 110));
        g.drawString(f, cs, sx + 11, py + 15, Ui.argb(PB, 255), false);
        Ui.hairline(g, px + 14, px + pw - 14, py + 32, 0xFFFFFF, 50, false, true);

        ItemStack survole = ItemStack.EMPTY;
        // deux côtés
        for (int cote = 0; cote < 2; cote++) {
            boolean moi = cote == 0;
            int x = px + 14 + cote * (cw + 10), y = py + 40;
            Ui.soft(g, x, y, cw, 116, Ui.argb(0x000000, 60));
            Ui.softOutline(g, x + 1, y + 1, cw - 2, 114, Ui.argb(0xFFFFFF, 20));
            String nom = moi ? t("toi") : etat.autre();
            g.drawString(f, Ui.in(Component.literal(nom), Ui.BOLD), x + 10, y + 9, Ui.argb(Ui.TEXT, 255), false);
            boolean p = moi ? etat.monPret() : etat.sonPret();
            String chip = t(p ? "etat_pret" : "etat_attend");
            int chw = (int) Ui.spacedWidth(f, chip.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 10;
            Ui.chip(g, f, chip, x + cw - 10 - chw, y + 7, p ? Ui.OK : Ui.MUTED);
            java.util.List<ItemStack> objets = moi ? etat.mesObjets() : etat.sesObjets();
            for (int i = 0; i < ReseauBoutique.CASES_ECHANGE; i++) {
                int[] q = posOffre(cote, i);
                boolean sur = mx >= q[0] && mx < q[0] + CASE && my >= q[1] && my < q[1] + CASE;
                Ui.soft(g, q[0], q[1], CASE, CASE, Ui.argb(0xFFFFFF, sur && i < objets.size() ? 18 : 8));
                Ui.softOutline(g, q[0] + 1, q[1] + 1, CASE - 2, CASE - 2, Ui.argb(p ? Ui.OK : 0xFFFFFF, p ? 70 : 22));
                if (i < objets.size()) {
                    g.renderItem(objets.get(i), q[0] + 3, q[1] + 3);
                    g.renderItemDecorations(f, objets.get(i), q[0] + 3, q[1] + 3);
                    if (sur) survole = objets.get(i);
                }
            }
            long donnes = moi ? etat.mesPb() : etat.sesPb();
            if (moi) {
                Ui.spaced(g, f, t("pb_donnes"), Ui.SMALL, x + 10, y + 56, Ui.argb(Ui.MUTED, 255), 0.6f);
                String note = etat.sesPb() > 0 ? t("un_seul") : donnes > 0
                        ? t("taxe_toi", etat.autre(), Ui.nombre(donnes - donnes / 10), Ui.nombre(donnes / 10)) : t("taxe_info");
                Ui.spaced(g, f, Ui.ellipsis(f, note, Ui.SMALL, cw - 20), Ui.SMALL, x + 10, y + 86, Ui.argb(donnes > 0 ? PB : Ui.FAINT, 255), 0.4f);
            } else {
                Ui.spaced(g, f, t("pb_donnes"), Ui.SMALL, x + 10, y + 56, Ui.argb(Ui.MUTED, 255), 0.6f);
                Ui.gros(g, f, Ui.in(Component.literal(Ui.nombre(donnes) + " PB"), Ui.BOLD), x + 10, y + 66, 1.2f, Ui.argb(donnes > 0 ? PB : Ui.MUTED, 255));
                Ui.spaced(g, f, t("tu_recevras", Ui.nombre(donnes - donnes / 10)), Ui.SMALL, x + 10, y + 86, Ui.argb(donnes > 0 ? PB : Ui.FAINT, 255), 0.4f);
            }
        }

        // mon inventaire
        int ix = px + 14;
        Ui.section(g, f, t("inventaire"), ix, py + 160 - 2, cw);
        java.util.List<Integer> cases = etat.mesCases();
        net.minecraft.world.entity.player.Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int n = 0; n < 36; n++) {
            int[] q = posInventaire(n);
            int c = caseInventaire(n);
            boolean propose = cases.contains(c), sur = mx >= q[0] && mx < q[0] + INV - 1 && my >= q[1] && my < q[1] + INV - 1;
            Ui.soft(g, q[0], q[1], INV - 1, INV - 1, Ui.argb(propose ? PB : 0xFFFFFF, propose ? 40 : sur ? 16 : 7));
            if (propose) Ui.softOutline(g, q[0] + 1, q[1] + 1, INV - 3, INV - 3, Ui.argb(PB, 140));
            ItemStack st = inv.getItem(c);
            if (!st.isEmpty()) {
                g.renderItem(st, q[0] + 1, q[1] + 1);
                g.renderItemDecorations(f, st, q[0] + 1, q[1] + 1);
                if (sur) survole = st;
            }
        }
        // aide à droite
        int ax = px + 14 + cw + 10, ay = py + 166;
        for (int i = 1; i <= 4; i++) {
            Ui.soft(g, ax, ay + (i - 1) * 22, 4, 4, Ui.argb(PB, 200));
            Ui.spaced(g, f, Ui.ellipsis(f, t("aide." + i), Ui.SMALL, cw - 16), Ui.SMALL, ax + 10, ay - 1 + (i - 1) * 22, Ui.argb(Ui.MUTED, 255), 0.4f);
        }

        // message, compte à rebours
        long age = System.currentTimeMillis() - messageA;
        if (!message.isEmpty() && age < 5000) {
            Ui.spacedCentered(g, f, texte(message).getString(), Ui.SMALL, px + pw / 2f, py + ph - 44, Ui.argb(Ui.DANGER, 255), 0.6f);
        }
        super.render(g, mx, my, pt);
        if (etat.phase() == 1 || etat.phase() == 2) {
            g.fill(px, py + 40, px + pw, py + ph - 36, Ui.argb(0x000000, 120));
            String gros = etat.phase() == 1 ? String.valueOf(Math.max(1, (etat.reste() + 19) / 20)) : "…";
            Ui.gros(g, f, Ui.in(Component.literal(gros), Ui.BOLD), px + pw / 2f - f.width(Ui.in(Component.literal(gros), Ui.BOLD)) * 1.5f, py + 110, 3f, Ui.argb(PB, 255));
            Ui.spacedCentered(g, f, t(etat.phase() == 1 ? "compte" : "execution"), Ui.SMALL, px + pw / 2f, py + 150, Ui.argb(Ui.TEXT, 255), 0.8f);
        }
        if (!survole.isEmpty()) g.renderTooltip(f, survole, mx, my);
    }
}
