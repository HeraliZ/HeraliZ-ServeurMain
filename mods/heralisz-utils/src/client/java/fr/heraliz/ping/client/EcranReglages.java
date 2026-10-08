package fr.heraliz.ping.client;

import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.client.gui.kit.Echelle;
import fr.heraliz.ping.client.gui.kit.HzButton;
import fr.heraliz.ping.client.gui.kit.SonsUi;
import fr.heraliz.ping.client.gui.kit.Ui;
import fr.heraliz.ping.client.hud.Atlas;
import fr.heraliz.ping.client.hud.Lot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Reglages des pings, au style des ecrans de clan (verre, DM Sans, capitales espacees, canevas 640 x 360) : volume du
 * son, taille des marqueurs, pseudo des auteurs, fleches au bord de l'ecran, nombre de pings affiches ; legende des 6
 * types et rappel de la touche. Ouvert depuis la liste des mods (Config) ou par la touche « Reglages des pings ».
 * Chaque changement s'applique tout de suite ; le fichier est ecrit a la fermeture.
 */
public final class EcranReglages extends Screen {
    private static final int PL = 360, PH = 270, LIGNE = 24;
    @Nullable
    private final Screen parent;
    private int px, py;

    public EcranReglages(@Nullable Screen parent) {
        super(Component.translatable("heraliz_ping.reglages.titre"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        px = (width - PL) / 2;
        py = (height - PH) / 2;
        int accent = Ui.accent();
        int y = py + 52;
        pas(y, PingConfig.VOLUME, 10, accent);
        y += LIGNE;
        pas(y, PingConfig.TAILLE, 10, accent);
        y += LIGNE;
        bascule(y, PingConfig.NOMS_TOUJOURS, "heraliz_ping.reglages.noms.toujours", "heraliz_ping.reglages.noms.besoin", accent);
        y += LIGNE;
        bascule(y, PingConfig.FLECHES, "heraliz_ping.oui", "heraliz_ping.non", accent);
        y += LIGNE;
        pas(y, PingConfig.MAX_VISIBLES, 2, accent);
        addRenderableWidget(new HzButton(px + PL - 16 - 96, py + PH - 30, 96, 18, Component.translatable("heraliz_ping.reglages.termine"), accent,
                this::onClose).look(HzButton.Look.PRIMARY));
    }

    /** Valeur entiere avec [-] et [+] a droite de la ligne. */
    private void pas(int y, ForgeConfigSpec.IntValue v, int pas, int accent) {
        int xd = px + PL - 16;
        addRenderableWidget(new HzButton(xd - 18, y, 18, 16, Component.literal("+"), accent, () -> regler(v, pas)));
        addRenderableWidget(new HzButton(xd - 18 - 52 - 18, y, 18, 16, Component.literal("-"), accent, () -> regler(v, -pas)));
    }

    private void regler(ForgeConfigSpec.IntValue v, int delta) {
        int min = v == PingConfig.VOLUME ? 0 : v == PingConfig.TAILLE ? 50 : 4;
        int max = v == PingConfig.VOLUME ? 100 : v == PingConfig.TAILLE ? 160 : 32;
        v.set(Math.max(min, Math.min(max, v.get() + delta)));
    }

    /** Interrupteur : un bouton dont le libelle est l'etat courant. */
    private void bascule(int y, ForgeConfigSpec.BooleanValue v, String cleOui, String cleNon, int accent) {
        int l = 88;
        HzButton[] b = new HzButton[1];
        b[0] = new HzButton(px + PL - 16 - l, y, l, 16, Component.translatable(v.get() ? cleOui : cleNon), accent, () -> {
            v.set(!v.get());
            b[0].setMessage(Component.translatable(v.get() ? cleOui : cleNon));
        });
        addRenderableWidget(b[0]);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, PL, PH);
        int accent = Ui.accent();
        Ui.spaced(g, font, I18n.get("heraliz_ping.reglages.titre"), Ui.BOLD, px + 16, py + 14, Ui.argb(Ui.TEXT, 255), 1.6f);
        g.drawString(font, Ui.tr(Ui.FONT, "heraliz_ping.reglages.sous_titre"), px + 16, py + 28, Ui.argb(Ui.MUTED, 255), false);
        g.fill(px + 16, py + 42, px + 16 + 26, py + 43, Ui.argb(accent, 230));
        Ui.hairline(g, px + 44, px + PL - 16, py + 42, 0xFFFFFF, 36, false, true);
        String[] cles = {"volume", "taille", "noms", "fleches", "max"};
        int y = py + 52;
        for (int i = 0; i < cles.length; i++) {
            g.drawString(font, Ui.tr(Ui.FONT, "heraliz_ping.reglages." + cles[i]), px + 16, y + 4, Ui.argb(Ui.TEXT, 235), false);
            String valeur = switch (i) {
                case 0 -> PingConfig.VOLUME.get() + " %";
                case 1 -> PingConfig.TAILLE.get() + " %";
                case 4 -> String.valueOf(PingConfig.MAX_VISIBLES.get());
                default -> null;
            };
            if (valeur != null) Ui.centered(g, font, Ui.lit(valeur, Ui.BOLD), px + PL - 16 - 18 - 26, y + 4, Ui.argb(Ui.TEXT, 255));
            if (i < cles.length - 1) Ui.hairline(g, px + 16, px + PL - 16, y + LIGNE - 4, 0xFFFFFF, 14, false, false);
            y += LIGNE;
        }
        // legende des 6 types (icones de l'atlas du HUD)
        int ly = py + 198;
        Ui.section(g, font, I18n.get("heraliz_ping.reglages.legende"), px + 16, ly - 12, PL - 32);
        int cases = PingType.nombre(), lc = (PL - 32) / cases;
        if (Atlas.pret()) {
            Lot.debut(g.pose().last().pose());
            for (PingType t : PingType.values()) {
                float cx = px + 16 + lc * t.ordinal() + lc / 2f;
                Lot.carre(Atlas.DISQUE, cx, ly + 9, 9, Lot.argb(Ui.VERRE, 0.9f));
                Lot.carre(Atlas.ANNEAU, cx, ly + 9, 9, Lot.argb(t.couleur, 1f));
                Lot.carre(Atlas.ICONES[t.ordinal()], cx, ly + 9, 5.4f, Lot.argb(t.couleur, 1f));
            }
            Lot.fin();
        }
        for (PingType t : PingType.values()) {
            float cx = px + 16 + lc * t.ordinal() + lc / 2f;
            String nom = I18n.get(t.cle()).toUpperCase(Locale.ROOT);
            Ui.spacedCentered(g, font, nom, Ui.SMALL, cx, ly + 22, Ui.argb(Ui.mix(t.couleur, 0xFFFFFF, 0.35f), 255), 0.5f);
        }
        // rappel de la touche
        Component touche = PingTouches.PING.getTranslatedKeyMessage();
        g.drawString(font, Ui.tr(Ui.SMALL, "heraliz_ping.reglages.touche", touche.getString()), px + 16, py + PH - 24, Ui.argb(Ui.MUTED, 255), false);
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // le fond est dessine par render (degrade, jamais de fond uni)
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.5f);
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        PingConfig.CLIENT_SPEC.save();
        Echelle.restaurer();
        super.removed();
    }
}
