package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.model.ZoneVue;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Panneau du drapeau (clic droit sur le socle) : petit verre 260 x 150 a l'echelle du joueur (pas d'echelle forcee).
 * Nom de la zone, banniere + nom du proprietaire + « depuis 2 h 14 » (ou « Zone libre »), bonus de loot, bloc
 * RAVITAILLEMENT : bouton PRIMARY « LANCER LE SIGNAL » (membre avec la permission, disponible) ou « disponible dans
 * 1 h 03 » ; les non-membres voient l'information seule.
 */
public class FlagPanelScreen extends Screen {
    private static final int W = 260, H = 150;
    private final ZoneVue z;
    private int px, py;
    private boolean envoye;

    public FlagPanelScreen(ZoneVue zone) {
        super(Component.literal(zone.nom()));
        this.z = zone;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        int accent = z.banniere() != null ? z.banniere().accent() : Ui.ACCENT_NEUTRE;
        addRenderableWidget(new HzIcon(px + W - 22, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        if (!z.libre() && z.membre() && z.peutLancer() && z.ravitaillementDansMs() <= 0 && !envoye) {
            addRenderableWidget(new HzButton(px + 16, py + H - 16 - 20, 132, 20, Component.translatable("zone.lancer_signal"), accent, () -> {
                envoye = true;
                Ui.son(Sons.UI_CLIC, 0.7f);
                if (minecraft != null && minecraft.getConnection() != null) ClansNet.toServer(new Packets.RavitaillementC2S(z.zoneId()));
                else fr.heralisz.clans.HeraliszClans.LOG.info("[clans] (hors ligne) signal demande pour {}", z.zoneId());
                onClose();
            }).look(HzButton.Look.PRIMARY));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.fondLeger(g, width, height);
        Ui.glass(g, px, py, W, H);
        int accent = z.banniere() != null ? z.banniere().accent() : Ui.ACCENT_NEUTRE;
        // titre
        Ui.spaced(g, font, z.nom(), Ui.BOLD, px + 16, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        if (z.etat() != EtatZone.NEUTRE && z.etat() != EtatZone.TENUE) {
            int tw = (int) Ui.spacedWidth(font, z.nom().toUpperCase(java.util.Locale.ROOT), Ui.BOLD, 1.6f);
            Ui.chip(g, font, etatCourt(), px + 16 + tw + 8, py + 9, z.etat() == EtatZone.CONTESTE ? Ui.DANGER : Ui.AMBRE);
        }
        Ui.hairline(g, px + 8, px + W - 8, py + 27, 0xFFFFFF, 40, true, true);
        // proprietaire
        int y = py + 36;
        if (z.libre() || z.banniere() == null) {
            g.drawString(font, Ui.tr(Ui.BOLD, "zone.libre"), px + 16, y, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.libre_info"), px + 16, y + 12, Ui.argb(Ui.MUTED, 255), false);
        } else {
            BannerArt.draw(g, z.banniere(), px + 16, y - 2, 30, 40);
            int tx = px + 54;
            g.drawString(font, Ui.lit(z.proprioNom(), Ui.BOLD), tx, y, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.lit("[" + z.proprioTag() + "]", Ui.FONT), tx + font.width(Ui.lit(z.proprioNom(), Ui.BOLD)) + 6, y, Ui.argb(accent, 255), false);
            g.drawString(font, Ui.tr(Ui.SMALL, "zone.depuis", Ui.duree(z.tenueDepuisMs())), tx, y + 12, Ui.argb(Ui.MUTED, 255), false);
            g.drawString(font, Ui.tr(Ui.SMALL, "zone.bonus_loot", z.lootBonus()), tx, y + 24, Ui.argb(Ui.OK, 230), false);
            if (z.membre()) Ui.chip(g, font, Component.translatable("gui.drapeau.votre_clan").getString(), tx, y + 36 - 2, accent);
        }
        // ravitaillement
        int ry = py + 90;
        Ui.section(g, font, Component.translatable("zone.ravitaillement").getString(), px + 16, ry, W - 32);
        int ly = py + H - 16 - 20 + 6;
        if (z.libre()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.libre_ravito"), px + 16, ly, Ui.argb(Ui.FAINT, 255), false);
        } else if (!z.membre()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.reserve"), px + 16, ly, Ui.argb(Ui.FAINT, 255), false);
        } else if (z.ravitaillementDansMs() > 0) {
            Icons.draw(g, Icons.HORLOGE, px + 16, ly - 2, 10, Ui.MUTED, 255);
            g.drawString(font, Ui.tr(Ui.FONT, "zone.disponible_dans", Ui.duree(z.ravitaillementDansMs())), px + 30, ly - 1, Ui.argb(Ui.MUTED, 255), false);
        } else if (!z.peutLancer()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.sans_permission"), px + 16, ly, Ui.argb(Ui.FAINT, 255), false);
        } else if (envoye) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.signal_envoye"), px + 16, ly, Ui.argb(Ui.OK, 255), false);
        } else {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.signal_info"), px + 158, ly - 6, Ui.argb(Ui.FAINT, 255), false);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.drapeau.signal_info2"), px + 158, ly + 3, Ui.argb(Ui.FAINT, 255), false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String etatCourt() {
        return switch (z.etat()) {
            case CAPTURE -> Component.translatable("gui.hud.capture_court").getString();
            case CONTESTE -> Component.translatable("gui.hud.conteste").getString();
            case DEFENSE -> Component.translatable("gui.hud.defense_court").getString();
            case PROTEGEE -> Component.translatable("gui.hud.protegee_court").getString();
            case LIMITE -> Component.translatable("gui.hud.limite").getString();
            default -> "";
        };
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
        Ui.son(Sons.UI_FERMER, 0.6f);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
