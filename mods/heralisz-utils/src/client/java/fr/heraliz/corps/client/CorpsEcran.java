package fr.heraliz.corps.client;

import com.mojang.blaze3d.platform.Window;
import fr.heraliz.corps.entite.CorpsContenu;
import fr.heraliz.corps.entite.CorpsEntity;
import fr.heraliz.corps.menu.CorpsMenu;
import fr.heraliz.corps.menu.Disposition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fouille d'un corps, comme un coffre de Minecraft et dans son style de base (texture du coffre du pack du serveur :
 * panneaux gris facon DayZ ; pas le style des clans, choix du proprietaire) : EN HAUT le corps, EN DESSOUS son propre
 * inventaire, a la place qu'il a dans un coffre.
 * <ul>
 *     <li>en-tete : « Corps de X » (petit losange vert si le mort etait de son clan), la cause de la mort, et a droite le
 *     temps avant que le corps disparaisse (detail au survol) ;</li>
 *     <li>Equipement (tete, torse, jambes, pieds, main gauche) et Sac (avec le nombre d'objets dedans), Accessoires
 *     (Curios) s'il en portait, Inventaire du mort (3 rangees, puis sa barre rapide), puis le bouton « Tout prendre » ;</li>
 *     <li>dessous : « Ton inventaire » (le bas d'un coffre, tel quel), en rouge « Inventaire plein » s'il reste des
 *     objets sur le corps et plus aucune place.</li>
 * </ul>
 * Seule amelioration par rapport au coffre de base : des textes clairs (lisibles sur les panneaux sombres du pack), des
 * etiquettes de section et un filet sous l'en-tete. L'echelle reste celle du joueur, sauf si le panneau ne tient pas
 * dans la fenetre (elle est alors baissee le temps de la fouille).
 */
public class CorpsEcran extends AbstractContainerScreen<CorpsMenu> {
    /** Texture du coffre (celle du pack du serveur, sinon celle de Minecraft). */
    private static final ResourceLocation COFFRE = new ResourceLocation("textures/gui/container/generic_54.png");
    /** En-tete du coffre (bord haut compris) ; bande d'interieur repetee pour la hauteur du corps (bords gauche et droit). */
    private static final int ENTETE_COFFRE = 17, BANDE_V = 5, BANDE = 11;
    /** Bas du coffre dans sa texture (etiquette, inventaire et barre rapide du joueur) ; cadre d'une case. */
    private static final int BAS_V = 126, CASE_U = 7, CASE_V = 17;
    private static final int TITRE = 0xFFE8E8E8, TEXTE = 0xFFB8B8B8, DISCRET = 0xFF8F8F8F, DANGER = 0xFFE5604A, CLAN = 0xFF5FD08A;

    private final Disposition d;
    private Button toutPrendre;
    private boolean echelleBaissee;
    private String sEquipement, sSac, sAccessoires, sInventaire, sToi, sPlein, sCause = "";
    /** Il reste des objets sur le corps et plus aucune place chez le joueur. */
    private boolean pleinAffiche;
    /** Minuteur de l'en-tete (texte, position) : son infobulle au survol. */
    private String sTemps = "";
    private int xTemps, wTemps;
    private boolean tempsUrgent;

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
        CorpsEntity c = menu.corps();
        sCause = c == null ? "" : couper(c.cause().getString(), Disposition.COLONNE);
        sEquipement = Component.translatable("gui.heraliz_corps.equipement").getString();
        sSac = Component.translatable("gui.heraliz_corps.sac").getString();
        sAccessoires = Component.translatable("gui.heraliz_corps.accessoires").getString();
        sInventaire = Component.translatable("gui.heraliz_corps.inventaire").getString();
        sToi = Component.translatable("gui.heraliz_corps.ton_inventaire").getString();
        sPlein = Component.translatable("gui.heraliz_corps.plein").getString();
        toutPrendre = addRenderableWidget(Button.builder(Component.translatable("gui.heraliz_corps.tout_prendre"), b -> prendreTout())
                .bounds(leftPos + Disposition.GAUCHE, topPos + d.yBouton, Disposition.COLONNE, Disposition.BOUTON)
                .tooltip(Tooltip.create(Component.translatable("gui.heraliz_corps.tout_prendre_aide")))
                .build());
        majEtat();
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
        majEtat();
    }

    /** « Tout prendre » actif tant qu'il reste un objet ; « Inventaire plein » s'il reste des objets et plus de place. */
    private void majEtat() {
        if (toutPrendre == null) return;
        boolean reste = false;
        for (int i = 0; i < menu.nbCorps() && !reste; i++) reste = menu.slots.get(i).hasItem();
        toutPrendre.active = reste;
        boolean place = false;
        for (int i = menu.nbCorps(); i < menu.slots.size() && !place; i++) place = !menu.slots.get(i).hasItem();
        pleinAffiche = reste && !place;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
        // detail du minuteur au survol
        CorpsEntity c = menu.corps();
        if (c != null && wTemps > 0 && hoveredSlot == null && menu.getCarried().isEmpty()
                && mouseX >= leftPos + xTemps - 1 && mouseX < leftPos + xTemps + wTemps + 1
                && mouseY >= topPos + Disposition.Y_TITRE - 1 && mouseY < topPos + Disposition.Y_TITRE + 9) {
            g.renderTooltip(font, Component.translatable(c.vide() ? "hud.heraliz_corps.vide" : "hud.heraliz_corps.disparait", sTemps),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        // panneau du corps : en-tete du coffre, puis son interieur repete jusqu'au bas du corps
        g.blit(COFFRE, x, y, 0, 0, imageWidth, ENTETE_COFFRE);
        for (int yy = ENTETE_COFFRE; yy < d.hauteurCorps; yy += BANDE) {
            g.blit(COFFRE, x, y + yy, 0, BANDE_V, imageWidth, Math.min(BANDE, d.hauteurCorps - yy));
        }
        // dessous : le bas du coffre tel quel (etiquette, inventaire et barre rapide du joueur)
        g.blit(COFFRE, x, y + d.hauteurCorps, 0, BAS_V, imageWidth, Disposition.BAS_COFFRE);
        // cadres des cases du corps, les memes que ceux du coffre
        for (int i = 0; i < menu.nbCorps(); i++) {
            Slot s = menu.slots.get(i);
            g.blit(COFFRE, x + s.x - 1, y + s.y - 1, CASE_U, CASE_V, Disposition.CASE, Disposition.CASE);
        }
        // filet grave sous l'en-tete
        int gauche = x + Disposition.GAUCHE, droite = x + Disposition.GAUCHE + Disposition.COLONNE;
        g.fill(gauche, y + Disposition.ENTETE - 3, droite, y + Disposition.ENTETE - 2, 0x40000000);
        g.fill(gauche, y + Disposition.ENTETE - 2, droite, y + Disposition.ENTETE - 1, 0x16FFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int gauche = Disposition.GAUCHE + 1;
        int droite = Disposition.GAUCHE + Disposition.COLONNE - 1;
        CorpsEntity c = menu.corps();
        // minuteur a droite du titre
        wTemps = 0;
        if (c != null && minecraft.level != null) {
            long reste = Math.max(0, c.expire() - minecraft.level.getGameTime()) / 20;
            sTemps = String.format(Locale.ROOT, "%d:%02d", reste / 60, reste % 60);
            tempsUrgent = reste < 60;
            wTemps = font.width(sTemps);
            xTemps = droite - wTemps;
            g.drawString(font, sTemps, xTemps, Disposition.Y_TITRE, tempsUrgent ? DANGER : DISCRET, false);
        }
        // titre « Corps de X » (losange vert : un membre de son clan)
        int tx = gauche;
        if (c != null && c.clanAmi) {
            g.fill(tx + 2, 7, tx + 4, 13, CLAN);
            g.fill(tx + 1, 8, tx + 5, 12, CLAN);
            g.fill(tx, 9, tx + 6, 11, CLAN);
            tx += 9;
        }
        int placeTitre = (wTemps > 0 ? xTemps - 8 : droite) - tx;
        g.drawString(font, couper(title.getString(), placeTitre), tx, Disposition.Y_TITRE, TITRE, false);
        if (!sCause.isEmpty()) g.drawString(font, sCause, gauche, Disposition.Y_CAUSE, DISCRET, false);
        // sections du corps
        g.drawString(font, sEquipement, gauche, d.yEquipement - 9, TEXTE, false);
        ItemStack sac = menu.slots.get(CorpsContenu.SAC).getItem();
        int n = sac.isEmpty() ? 0 : objetsDansSac(sac);
        String etiquetteSac = n > 0 ? Component.translatable("gui.heraliz_corps.sac_n", n).getString() : sSac;
        g.drawString(font, couper(etiquetteSac, droite - d.xSac - 1), d.xSac + 1, d.yEquipement - 9, TEXTE, false);
        if (d.rangsCurios > 0) g.drawString(font, sAccessoires, gauche, d.yCurios - 9, TEXTE, false);
        g.drawString(font, sInventaire, gauche, d.yInventaire - 9, TEXTE, false);
        // inventaire du joueur
        if (pleinAffiche) g.drawString(font, couper(sPlein, Disposition.COLONNE), gauche, d.yEtiquetteJoueur, DANGER, false);
        else g.drawString(font, sToi, gauche, d.yEtiquetteJoueur, TEXTE, false);
    }

    /** Texte coupe avec « … » s'il depasse la largeur. */
    private String couper(String s, int largeur) {
        if (font.width(s) <= largeur) return s;
        return font.plainSubstrByWidth(s, Math.max(0, largeur - font.width("…"))).trim() + "…";
    }

    /** Objets ranges dans un sac (liste « Items » de son NBT, celle de HeraliZ Backpack). */
    private static int objetsDansSac(ItemStack sac) {
        CompoundTag t = sac.getTag();
        return t == null ? 0 : t.getList("Items", 10).size();
    }

    /** Case du corps (et non de l'inventaire du joueur). */
    private boolean estCorps(Slot s) {
        return minecraft != null && minecraft.player != null && s.container != minecraft.player.getInventory();
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
