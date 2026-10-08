package fr.heraliz.corps.client;

import com.mojang.blaze3d.platform.Window;
import fr.heraliz.corps.client.kit.HzButton;
import fr.heraliz.corps.client.kit.SonsUi;
import fr.heraliz.corps.client.kit.Ui;
import fr.heraliz.corps.entite.CorpsContenu;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.menu.CorpsMenu;
import fr.heraliz.corps.menu.Disposition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fouille d'un corps, au style des interfaces HeraliZ (verre, DM Sans, capitales espacees), comme la fouille d'un joueur
 * a terre de Reanimation : a gauche le corps (equipement et sac, accessoires, inventaire), a droite son propre inventaire
 * et « Tout prendre ». L'echelle reste celle du joueur, sauf si le panneau ne tient pas dans la fenetre (elle est alors
 * baissee le temps de la fouille).
 */
public class CorpsEcran extends AbstractContainerScreen<CorpsMenu> {
    private final Disposition d;
    private HzButton toutPrendre;
    private boolean echelleBaissee;
    private String sTitre, sCause, sEquipement, sSac, sAccessoires, sInventaire, sToi, sAide, sPlein;
    /** Il reste des objets sur le corps et plus aucune place chez le joueur. */
    private boolean pleinAffiche;
    private boolean ouvert;

    public CorpsEcran(CorpsMenu menu, Inventory inv, Component titre) {
        super(menu, inv, titre);
        this.d = menu.disposition();
        this.imageWidth = Disposition.LARGEUR;
        this.imageHeight = d.hauteur;
    }

    @Override
    protected void init() {
        adapterEchelle();
        super.init();
        sTitre = title.getString().toUpperCase(Locale.ROOT);
        CorpsEntity c = menu.corps();
        sCause = c == null ? "" : Ui.ellipsis(font, c.cause().getString(), Ui.FONT, imageWidth - 2 * Disposition.MARGE);
        sEquipement = Component.translatable("gui.heraliz_corps.equipement").getString();
        sSac = Component.translatable("gui.heraliz_corps.sac").getString();
        sAccessoires = Component.translatable("gui.heraliz_corps.accessoires").getString();
        sInventaire = Component.translatable("gui.heraliz_corps.inventaire").getString();
        sToi = Component.translatable("gui.heraliz_corps.ton_inventaire").getString();
        sAide = Component.translatable("gui.heraliz_corps.aide").getString();
        sPlein = Component.translatable("gui.heraliz_corps.plein").getString().toUpperCase(Locale.ROOT);
        toutPrendre = addRenderableWidget(new HzButton(leftPos + Disposition.DROITE, topPos + d.yBouton, Disposition.COLONNE, 18,
                Component.translatable("gui.heraliz_corps.tout_prendre"), Ui.accent(), this::prendreTout).look(HzButton.Look.PRIMARY));
        if (!ouvert) {
            ouvert = true;
            SonsUi.ouvrir(0.5F);
        }
    }

    /** Baisse l'echelle d'interface si le panneau ne tient pas (petite fenetre, grande echelle choisie). */
    private void adapterEchelle() {
        Window w = minecraft.getWindow();
        int voulue = w.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
        int s = voulue;
        while (s > 1 && (w.getWidth() / s < imageWidth + 8 || w.getHeight() / s < imageHeight + 8)) s--;
        if (s != (int) w.getGuiScale()) {
            w.setGuiScale(s);
            width = w.getGuiScaledWidth();
            height = w.getGuiScaledHeight();
        }
        echelleBaissee = s != voulue;
    }

    @Override
    public void removed() {
        super.removed();
        SonsUi.fermer(0.5F);
        if (echelleBaissee) {
            echelleBaissee = false;
            Window w = minecraft.getWindow();
            w.setGuiScale(w.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
        }
    }

    private void prendreTout() {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CorpsMenu.BOUTON_TOUT_PRENDRE);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (toutPrendre != null) {
            boolean reste = false;
            for (int i = 0; i < menu.nbCorps() && !reste; i++) reste = menu.slots.get(i).hasItem();
            toutPrendre.active = reste;
            boolean place = false;
            for (int i = menu.nbCorps(); i < menu.slots.size() && !place; i++) place = !menu.slots.get(i).hasItem();
            pleinAffiche = reste && !place;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Ui.fond(g, width, height);
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        int accent = Ui.accent();
        Ui.glass(g, x - 4, y - 4, imageWidth + 8, imageHeight + 8, 225);
        Ui.band(g, x - 2, y - 2, x + imageWidth + 2, y + 30, accent, 26, 80);
        Ui.hairline(g, x + 6, x + imageWidth - 6, y + Disposition.ENTETE - 2, 0xFFFFFF, 40, true, true);
        // separation des deux colonnes
        int sep = x + Disposition.DROITE - Disposition.ECART / 2;
        for (int yy = y + Disposition.ENTETE + 8; yy < y + imageHeight - 8; yy += 2) g.fill(sep, yy, sep + 1, yy + 1, Ui.argb(0xFFFFFF, 18));
        for (Slot s : menu.slots) {
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean corps = estCorps(s);
            boolean survol = hoveredSlot == s;
            Ui.soft(g, sx, sy, 18, 18, Ui.argb(0x000000, corps ? 125 : 95));
            Ui.softOutline(g, sx + 1, sy + 1, 16, 16, survol ? Ui.argb(accent, 200) : Ui.argb(0xFFFFFF, corps && s.hasItem() ? 34 : 16));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        CorpsEntity c = menu.corps();
        int tx = Disposition.GAUCHE;
        if (c != null && c.clanAmi) {
            int a = Ui.accent();
            g.fill(tx + 2, 7, tx + 4, 13, Ui.argb(a, 255));
            g.fill(tx + 1, 8, tx + 5, 12, Ui.argb(a, 255));
            g.fill(tx, 9, tx + 6, 11, Ui.argb(a, 255));
            tx += 10;
        }
        Ui.spaced(g, font, sTitre, Ui.BOLD, tx, 6, Ui.argb(Ui.TEXT, 255), 1.0F);
        if (!sCause.isEmpty()) g.drawString(font, Ui.lit(sCause, Ui.FONT), Disposition.GAUCHE, 19, Ui.argb(Ui.MUTED, 255), false);
        if (c != null && minecraft.level != null) {
            long maintenant = minecraft.level.getGameTime();
            long reste = Math.max(0, c.expire() - maintenant) / 20;
            String temps = String.format(Locale.ROOT, "%d:%02d", reste / 60, reste % 60);
            String chip = Component.translatable(c.vide() ? "hud.heraliz_corps.vide" : "hud.heraliz_corps.disparait", temps).getString();
            int w = (int) Ui.spacedWidth(font, chip.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6F) + 10;
            Ui.chip(g, font, chip, imageWidth - Disposition.MARGE - w, 9, reste < 60 ? Ui.DANGER : Ui.MUTED);
        }
        // sections du corps
        Ui.section(g, font, sEquipement, Disposition.GAUCHE, d.yEquipement - Disposition.ETIQUETTE + 1, 5 * Disposition.CASE + 4);
        Ui.spaced(g, font, sSac, Ui.SMALL, d.xSac, d.yEquipement - Disposition.ETIQUETTE + 1, Ui.argb(Ui.MUTED, 255), 1.0F);
        ItemStack sac = menu.slots.get(CorpsContenu.SAC).getItem();
        if (!sac.isEmpty()) {
            int n = objetsDansSac(sac);
            g.drawString(font, Ui.tr(Ui.SMALL, n == 1 ? "gui.heraliz_corps.sac_objet" : "gui.heraliz_corps.sac_objets", n),
                    d.xSac + Disposition.CASE + 4, d.yEquipement + 6, Ui.argb(Ui.MUTED, 255), false);
        }
        if (d.rangsCurios > 0) Ui.section(g, font, sAccessoires, Disposition.GAUCHE, d.yCurios - Disposition.ETIQUETTE + 1, Disposition.COLONNE);
        Ui.section(g, font, sInventaire, Disposition.GAUCHE, d.yInventaire - Disposition.ETIQUETTE + 1, Disposition.COLONNE);
        // colonne du joueur
        Ui.section(g, font, sToi, Disposition.DROITE, d.yJoueur - Disposition.ETIQUETTE + 1, Disposition.COLONNE);
        Ui.spacedCentered(g, font, sAide, Ui.SMALL, Disposition.DROITE + Disposition.COLONNE / 2F, d.yAide, Ui.argb(Ui.FAINT, 255), 0.6F);
        if (pleinAffiche) {
            int w = (int) Ui.spacedWidth(font, sPlein, Ui.SMALL, 0.6F) + 10;
            Ui.chip(g, font, sPlein, Disposition.DROITE + (Disposition.COLONNE - w) / 2, d.yAide + 12, Ui.DANGER);
        }
    }

    /** Case du corps (et non de l'inventaire du joueur). */
    private boolean estCorps(Slot s) {
        return minecraft != null && minecraft.player != null && s.container != minecraft.player.getInventory();
    }

    /** Objets ranges dans un sac (liste « Items » de son NBT, celle de HeraliZ Backpack). */
    private static int objetsDansSac(ItemStack sac) {
        CompoundTag t = sac.getTag();
        return t == null ? 0 : t.getList("Items", 10).size();
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> l = super.getTooltipFromContainerItem(stack);
        Slot s = hoveredSlot;
        if (s != null && estCorps(s)) {
            String id = menu.idCurios(s.getContainerSlot());
            if (!id.isEmpty()) {
                l = new ArrayList<>(l);
                l.add(Component.translatable("gui.heraliz_corps.porte_en", Component.translatable("curios.identifier." + id))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (s.getContainerSlot() == CorpsContenu.SAC) {
                l = new ArrayList<>(l);
                int n = objetsDansSac(stack);
                l.add(Component.translatable(n == 1 ? "gui.heraliz_corps.sac_objet" : "gui.heraliz_corps.sac_objets", n).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return l;
    }
}
