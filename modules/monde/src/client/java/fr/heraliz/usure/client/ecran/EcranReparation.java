package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.MenuReparation;
import fr.heraliz.usure.Reparation;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.client.kit.Echelle;
import fr.heraliz.usure.client.kit.Net;
import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Atelier de réparation (26/10, demande du propriétaire) : au style des écrans de clan (verre, DM Sans à sa vraie taille),
 * panneau 228 x 252 : en-tête (kit et utilisations restantes), case de l'objet à réparer, état avant → après en
 * pourcentage avec sa barre (part rendue en vert), boutons « Annuler » et « Réparer », puis l'inventaire. La case du kit
 * en main est grisée (bloquée). Le serveur calcule et décide de tout ({@code MenuReparationServeur}) ; l'écran lit l'état
 * synchronisé du menu.
 * <p>11/11 (demande du propriétaire : l'état maximal qui baisse n'était pas compris) : pour une arme, la part de la barre
 * qui ne se répare plus est en rouge (vif : ce que coûte cette réparation ; plus sombre : ce qui était déjà perdu), avec
 * une ligne « En rouge : ne se répare plus (max …) » et les réparations restantes de l'arme (nombre fixé par sa rareté).
 * Une arme déjà au mieux ou sans réparation garde la même barre, pour voir pourquoi. Aperçus : modules/monde/outils/
 * apercu_atelier.py.
 */
public final class EcranReparation extends AbstractContainerScreen<MenuReparation> {
    private static final int L = 228, H = 252, ENTETE = 44, M = 16, CASE = MenuReparation.CASE;
    private static final int BLANC = 0xFFFFFF;
    /** Ligne « en rouge » : rouge des refus adouci vers le texte atténué. */
    private static final int ROUGE_DOUX = Ui.mix(Ui.DANGER, Ui.MUTED, 0.25f);
    private static final ResourceLocation TITRE = Net.TITRE, PG = Net.PETIT_GRAS, MICRO = Net.MICRO;
    /** Zone de l'aperçu (droite de la case) et boutons (depuis le coin du panneau). */
    private static final int AX = MenuReparation.CIBLE_X + CASE + 10, BY = 116, BH = 14, BW = 74;

    public EcranReparation(MenuReparation menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        imageWidth = L;
        imageHeight = H;
    }

    private static String t(String cle, Object... args) {
        return I18n.get("gui.heraliz_usure.atelier." + cle, args);
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        super.init();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // tout est dessiné par renderBg (polices du mod)
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        Font f = font;
        int x = leftPos, y = topPos;
        Ui.glass(g, x, y, L, H, 214);
        entete(g, f, x, y, mx, my);
        for (int k = 0; k < menu.slots.size(); k++) {
            Slot s = menu.slots.get(k);
            float cx = x + s.x - 2, cy = y + s.y - 2;
            boolean kit = k == menu.indexKit();
            Net.rect(g, cx, cy, cx + CASE, cy + CASE, Ui.argb(0x000000, kit ? 150 : 90));
            int bord = kit ? Ui.argb(Ui.OK, 150) : hoveredSlot == s ? Ui.argb(Ui.OR, 200) : Ui.argb(BLANC, k == 0 ? 70 : 34);
            Net.cadre(g, cx, cy, CASE, CASE, bord);
        }
        apercu(g, f, x, y);
        boutons(g, f, x, y, mx, my);
        libelle(g, f, t("inventaire"), x + M, y + MenuReparation.INVENTAIRE_Y - 10, 9 * (CASE + 2) - 2);
    }

    private void entete(GuiGraphics g, Font f, int x, int y, int mx, int my) {
        icone(g, x + M, y + 10, Ui.OK);
        float tx = x + M + 34;
        Net.texte(g, f, t("titre"), TITRE, tx, y + 9 + Net.cap(TITRE) / 2, Ui.TEXT);
        ItemStack kit = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(menu.caseKit());
        String sous = (kit.isEmpty() ? "" : kit.getHoverName().getString() + " · ")
                + t("utilisations", menu.donnee(MenuReparation.D_RESTANTES), menu.donnee(MenuReparation.D_TOTAL));
        Net.texte(g, f, Net.couper(f, sous, Ui.SMALL, L - M - 34 - 28), Ui.SMALL, tx, y + 27 + Net.cap(Ui.SMALL) / 2, Ui.MUTED);
        float fx = x + L - 16, fy = y + 14;
        boolean survol = mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8;
        Net.centre(g, f, "×", Ui.BOLD, fx, fy, survol ? Ui.TEXT : Ui.MUTED);
        Net.rect(g, x + 8, y + ENTETE, x + L - 8, y + ENTETE + Net.point(), Ui.argb(BLANC, 40));
    }

    /** Pictogramme : clé plate dans un anneau. */
    private static void icone(GuiGraphics g, float x, float y, int c) {
        float cx = x + 12, cy = y + 12;
        Net.disque(g, cx, cy, 11, Ui.argb(c, 40));
        Net.anneau(g, cx, cy, 11, 1.1f, Ui.argb(c, 200));
        Net.trait(g, cx - 5, cy + 5, cx + 3, cy - 3, 2.4f, Ui.argb(c, 230));
        Net.anneau(g, cx + 4.2f, cy - 4.2f, 3.2f, 1.6f, Ui.argb(c, 230));
        Net.disque(g, cx - 5, cy + 5, 1.6f, Ui.argb(c, 230));
    }

    private void libelle(GuiGraphics g, Font f, String txt, float x, float y, float largeur) {
        float w = Net.caps(g, f, txt, PG, 1.0f, x, y + Net.cap(PG) / 2, Ui.MUTED);
        Net.rect(g, x + w + 6, y + 2.6f, x + largeur, y + 2.6f + Net.point(), Ui.argb(BLANC, 30));
    }

    private void apercu(GuiGraphics g, Font f, int x, int y) {
        libelle(g, f, t("objet"), x + M, y + ENTETE + 10, L - 2 * M);
        int code = menu.donnee(MenuReparation.D_CODE);
        float ax = x + AX, aw = x + L - M - ax;
        ItemStack objet = menu.slots.get(0).getItem();
        if (code == MenuReparation.VIDE || objet.isEmpty()) {
            lignes(g, f, t("vide"), ax, y + 72, aw, Ui.FAINT, 3);
            return;
        }
        Net.texte(g, f, Net.couper(f, objet.getHoverName().getString(), Ui.BOLD, aw), Ui.BOLD, ax, y + 70, Ui.TEXT);
        boolean arme = menu.donnee(MenuReparation.D_GENRE) == MenuReparation.ARME;
        // une arme déjà au mieux, ou sans réparation, garde la barre : on voit ce qui ne se répare plus (11/11)
        boolean barre = code == MenuReparation.OK || arme && (code == MenuReparation.PLAFOND || code == MenuReparation.PLUS_DE_REPARATION);
        if (!barre) {
            lignes(g, f, t("code." + code), ax, y + 84, aw, code == MenuReparation.PLAFOND ? Ui.MUTED : Ui.AMBRE, 3);
            return;
        }
        int avant = menu.donnee(MenuReparation.D_AVANT), apres = menu.donnee(MenuReparation.D_APRES);
        int maxAvant = arme ? menu.donnee(MenuReparation.D_MAX_AVANT) : Usure.PLEIN, maxApres = arme ? menu.donnee(MenuReparation.D_MAX_APRES) : Usure.PLEIN;
        float cy = y + 82;
        if (code == MenuReparation.OK) {
            // « 42 % → 49 % » et la pastille du gain
            float w1 = Net.texte(g, f, Reparation.pct(avant), PG, ax, cy, Ui.MUTED);
            float w2 = Net.texte(g, f, "→", Ui.SMALL, ax + w1 + 5, cy, Ui.FAINT);
            float w3 = Net.texte(g, f, Reparation.pct(apres), PG, ax + w1 + 5 + w2 + 5, cy, Ui.OK);
            String gain = "+" + Math.max(0, Math.round((apres - avant) / 10f)) + " %";
            float gx = ax + w1 + w2 + w3 + 18, gw = Net.capsLargeur(f, gain, MICRO, 0.6f) + 8;
            if (gx + gw <= ax + aw) {
                Net.rect(g, gx, cy - 5, gx + gw, cy + 5, Ui.argb(Ui.OK, 40));
                Net.cadre(g, gx, cy - 5, gw, 10, Ui.argb(Ui.OK, 170));
                Net.capsCentre(g, f, gain, MICRO, 0.6f, gx + gw / 2, cy, Ui.OK);
            }
        } else {
            Net.texte(g, f, Net.couper(f, t("code." + code), Ui.SMALL, aw), Ui.SMALL, ax, cy, code == MenuReparation.PLAFOND ? Ui.MUTED : Ui.DANGER);
        }
        // barre : état (gris clair), part rendue (vert) ; en rouge ce qui ne se répare plus : vif pour ce que coûte cette
        // réparation, plus sombre pour ce qui était déjà perdu
        float by = y + 89, bx1 = ax + aw;
        Net.rect(g, ax, by, bx1, by + 3, Ui.argb(BLANC, 22));
        Net.rect(g, ax, by, ax + aw * avant / 1000f, by + 3, Ui.argb(BLANC, 110));
        if (apres > avant) Net.rect(g, ax + aw * avant / 1000f, by, ax + aw * apres / 1000f, by + 3, Ui.argb(Ui.OK, 235));
        if (maxApres < maxAvant) Net.rect(g, ax + aw * maxApres / 1000f, by, ax + aw * maxAvant / 1000f, by + 3, Ui.argb(Ui.DANGER, 235));
        if (maxAvant < Usure.PLEIN) Net.rect(g, ax + aw * maxAvant / 1000f, by, bx1, by + 3, Ui.argb(Ui.DANGER, 120));
        if (!arme) {
            Net.texte(g, f, Net.couper(f, t("armure"), Ui.SMALL, aw), Ui.SMALL, ax, y + 99, Ui.FAINT);
            return;
        }
        if (maxApres < Usure.PLEIN) {
            String l = maxApres < maxAvant ? t("max", Reparation.pct(maxAvant), Reparation.pct(maxApres)) : t("max_stable", Reparation.pct(maxApres));
            Net.texte(g, f, Net.couper(f, l, Ui.SMALL, aw), Ui.SMALL, ax, y + 99, ROUGE_DOUX);
        }
        // réparations restantes de l'arme (nombre fixé par sa rareté) : celle-ci en use une
        int restantes = menu.donnee(MenuReparation.D_REP_RESTANTES), total = menu.donnee(MenuReparation.D_REP_MAX);
        boolean ok = code == MenuReparation.OK;
        String r = ok ? t("reparations", restantes, restantes - 1, total) : t("reparations_stable", restantes, total);
        int c = restantes <= 0 ? Ui.DANGER : ok && restantes <= 1 ? Ui.AMBRE : Ui.MUTED;
        Net.texte(g, f, Net.couper(f, r, Ui.SMALL, aw), Ui.SMALL, ax, y + 108, c);
    }

    private void lignes(GuiGraphics g, Font f, String txt, float x, float cy, float w, int c, int max) {
        List<FormattedCharSequence> l = f.split(Ui.lit(txt, Ui.SMALL), (int) w);
        for (int i = 0; i < Math.min(max, l.size()); i++) Net.ligne(g, f, l.get(i), Ui.SMALL, x, cy + i * 10, c);
    }

    private boolean peutReparer() {
        return menu.donnee(MenuReparation.D_CODE) == MenuReparation.OK;
    }

    private void boutons(GuiGraphics g, Font f, int x, int y, int mx, int my) {
        float rx = x + L - M - BW, ax = rx - 6 - BW, by = y + BY;
        bouton(g, f, t("annuler"), ax, by, Ui.MUTED, true, dans(mx, my, ax, by));
        bouton(g, f, t("reparer"), rx, by, Ui.OK, peutReparer(), peutReparer() && dans(mx, my, rx, by));
    }

    private static boolean dans(double mx, double my, float bx, float by) {
        return mx >= bx && mx < bx + BW && my >= by && my < by + BH;
    }

    private static void bouton(GuiGraphics g, Font f, String txt, float bx, float by, int c, boolean actif, boolean survol) {
        Net.rect(g, bx, by, bx + BW, by + BH, Ui.argb(c, !actif ? 14 : survol ? 90 : 50));
        Net.cadre(g, bx, by, BW, BH, Ui.argb(c, actif ? 210 : 70));
        Net.capsCentre(g, f, txt, PG, 1.0f, bx + BW / 2f, by + BH / 2f, actif ? Ui.TEXT : Ui.FAINT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            float rx = leftPos + L - M - BW, ax = rx - 6 - BW, by = topPos + BY;
            if (dans(mx, my, rx, by)) {
                if (peutReparer() && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MenuReparation.BOUTON_REPARER);
                    SonsUi.clic(0.6F);
                }
                return true;
            }
            float fx = leftPos + L - 16, fy = topPos + 14;
            if (dans(mx, my, ax, by) || mx >= fx - 8 && mx < fx + 8 && my >= fy - 8 && my < fy + 8) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, b);
    }
}
