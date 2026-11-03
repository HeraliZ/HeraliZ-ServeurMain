package fr.heraliz.armory.client.cible;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.cible.Tenues;
import fr.heraliz.armory.client.gui.Ui;
import fr.heraliz.armory.network.ArmoryNetwork;
import fr.heraliz.armory.network.PaquetsCible;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Locale;

/**
 * Écran de la cible d'essai (03/11) : choix de l'armure qu'elle porte, au style des écrans du clan (verre, DM Sans à sa
 * vraie taille, capitales espacées, aucun bouton de Minecraft). Une ligne par tenue (sans armure, ensembles du Commun à
 * l'Unique, combinaisons), avec la protection au corps et à la tête calculée par le serveur (vrai plafond) ; « Casque » :
 * les pièces de tête de la tenue, qu'on peut retirer ; « Ramasser » pour celui qui l'a posée et le staff. Un clic sur une
 * ligne habille la cible tout de suite (le serveur vérifie la distance) ; on la voit derrière l'écran.
 */
public class EcranCible extends Screen {
    private static final ResourceLocation MINI_GRAS = HeralizArmory.id("ui_mini_bold"), CHIFFRE = HeralizArmory.id("ui_chiffre");
    private static final int L = 300, H = 236, LIGNE = 15, HAUT_LISTE = 46, BAS = 34;

    private final PaquetsCible.Ouvrir ouvert;
    private final List<Tenues.Tenue> tenues = Tenues.TOUTES;
    private String choisie;
    private boolean casque;
    private float defilement;
    private int x0, y0;

    public EcranCible(PaquetsCible.Ouvrir ouvert) {
        super(Component.translatable("gui.heraliz_armory.cible.titre"));
        this.ouvert = ouvert;
        this.choisie = ouvert.tenue();
        this.casque = ouvert.casque();
    }

    @Override
    protected void init() {
        x0 = (width - L) / 2;
        y0 = (height - H) / 2;
        int i = Tenues.index(choisie);
        if (i >= 0) defilement = Mth.clamp((i - visibles() / 2) * LIGNE, 0, maxDefilement());
    }

    private int hauteurListe() {
        return H - HAUT_LISTE - BAS;
    }

    private int visibles() {
        return hauteurListe() / LIGNE;
    }

    private float maxDefilement() {
        return Math.max(0, tenues.size() * LIGNE - hauteurListe());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        Ui.fond(g, width, height);
        Font f = this.font;
        Ui.glass(g, x0, y0, L, H, 225);
        Ui.band(g, x0 + 2, y0 + 2, x0 + L - 2, y0 + 18, Ui.ACCENT, 26, 60);
        Ui.spaced(g, f, title.getString(), Ui.BOLD, x0 + 10, y0 + 6, Ui.argb(Ui.TEXT, 255), 1.2F);
        Ui.hairline(g, x0 + 4, x0 + L - 4, y0 + 18, 0xFFFFFF, 40, false, true);
        texte(g, f, Component.translatable("gui.heraliz_armory.cible.aide").getString(), Ui.SMALL, 9.0F, x0 + 10, y0 + 27, Ui.MUTED);
        // en-têtes des colonnes
        Ui.spacedRight(g, f, Component.translatable("gui.heraliz_armory.cible.corps").getString(), MINI_GRAS, x0 + L - 62, y0 + 37,
                Ui.argb(Ui.FAINT, 255), 0.8F);
        Ui.spacedRight(g, f, Component.translatable("gui.heraliz_armory.cible.tete").getString(), MINI_GRAS, x0 + L - 18, y0 + 37,
                Ui.argb(Ui.FAINT, 255), 0.8F);
        // liste
        int lx = x0 + 6, ly = y0 + HAUT_LISTE, lw = L - 16, lh = hauteurListe();
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < tenues.size(); i++) {
            int y = ly + i * LIGNE - Math.round(defilement);
            if (y + LIGNE < ly || y > ly + lh) continue;
            ligne(g, f, i, lx, y, lw, mx, my, my >= ly && my < ly + lh);
        }
        g.disableScissor();
        barre(g, lx + lw + 3, ly, lh);
        // bas : casque, ramasser, fermer
        int by = y0 + H - BAS + 8;
        Ui.hairline(g, x0 + 6, x0 + L - 6, by - 6, 0xFFFFFF, 30, true, true);
        Tenues.Tenue t = Tenues.de(choisie);
        boolean actif = t.aUnCasque();
        boolean survol = actif && dans(mx, my, x0 + 10, by, 150, 16);
        pastille(g, x0 + 10, by + 3, casque && actif, survol, actif);
        texte(g, f, Component.translatable("gui.heraliz_armory.cible.casque").getString(), Ui.SMALL, 9.0F, x0 + 26, by + 8,
                actif ? (survol ? Ui.TEXT : Ui.MUTED) : Ui.FAINT);
        int bx = x0 + L - 10;
        bx = bouton(g, f, Component.translatable("gui.heraliz_armory.cible.fermer").getString(), bx, by, mx, my, Ui.MUTED);
        if (ouvert.ramasser()) bouton(g, f, Component.translatable("gui.heraliz_armory.cible.ramasser").getString(), bx - 6, by, mx, my, Ui.OR);
        super.render(g, mx, my, partial);
    }

    private void ligne(GuiGraphics g, Font f, int i, int x, int y, int w, int mx, int my, boolean dansListe) {
        Tenues.Tenue t = tenues.get(i);
        boolean choisi = t.id().equals(choisie), survol = dansListe && dans(mx, my, x, y, w, LIGNE);
        if (choisi) Ui.soft(g, x, y, w, LIGNE - 1, Ui.argb(Ui.ACCENT, 46));
        else if (survol) Ui.soft(g, x, y, w, LIGNE - 1, Ui.argb(0xFFFFFF, 14));
        int couleur = TextesCible.couleur(t.rarete());
        g.fill(x + 6, y + 5, x + 10, y + 9, Ui.argb(couleur, 230));
        float cy = y + 7.5F;
        float nx = x + 16 + texte(g, f, t.nom().getString(), Ui.SMALL, 9.0F, x + 16, cy, choisi ? 0xFFFFFF : Ui.TEXT);
        if (t.rang() > 0) {
            String r = Component.translatable("rarity.heraliz_armory." + t.rarete()).getString().toUpperCase(Locale.ROOT);
            Ui.spaced(g, f, r, MINI_GRAS, nx + 6, cy - milieu(6.5F), Ui.argb(Ui.mix(couleur, Ui.VERRE, 0.25F), 220), 0.6F);
        }
        float corps = i < ouvert.corps().length ? ouvert.corps()[i] : 0.0F;
        float tete = i < ouvert.tete().length ? ouvert.tete()[i] : 0.0F;
        if (!casque && t.aUnCasque()) tete = 0.0F;
        droite(g, f, Math.round(corps) + " %", x + w - 56, cy, corps > 0 ? Ui.TEXT : Ui.FAINT);
        droite(g, f, Math.round(tete) + " %", x + w - 12, cy, tete > 0 ? Ui.TEXT : Ui.FAINT);
    }

    private void barre(GuiGraphics g, int x, int y, int h) {
        float max = maxDefilement();
        if (max <= 0) return;
        g.fill(x, y, x + 2, y + h, Ui.argb(0xFFFFFF, 18));
        int ph = Math.max(14, Math.round(h * h / (float) (h + max)));
        int py = y + Math.round((h - ph) * (defilement / max));
        g.fill(x, py, x + 2, py + ph, Ui.argb(0xFFFFFF, 90));
    }

    private void pastille(GuiGraphics g, int x, int y, boolean coche, boolean survol, boolean actif) {
        int a = actif ? 255 : 90;
        if (coche) {
            Ui.soft(g, x, y, 10, 10, Ui.argb(Ui.ACCENT, survol ? 255 : 215));
        } else {
            Ui.soft(g, x, y, 10, 10, Ui.argb(0xFFFFFF, survol ? 22 : 10));
            Ui.softOutline(g, x + 1, y + 1, 8, 8, Ui.argb(0xFFFFFF, actif ? (survol ? 90 : 40) : 20));
        }
        if (coche) Ui.softOutline(g, x + 1, y + 1, 8, 8, Ui.argb(Ui.mix(Ui.ACCENT, 0xFFFFFF, 0.35F), a * 200 / 255));
    }

    /** Bouton texte aligné à droite sur {@code xDroit} ; rend son bord gauche. */
    private int bouton(GuiGraphics g, Font f, String s, int xDroit, int y, int mx, int my, int rgb) {
        int w = (int) Ui.spacedWidth(f, s.toUpperCase(Locale.ROOT), MINI_GRAS, 0.8F) + 16, x = xDroit - w;
        boolean survol = dans(mx, my, x, y, w, 16);
        Ui.soft(g, x, y, w, 16, Ui.argb(rgb, survol ? 60 : 30));
        Ui.softOutline(g, x + 1, y + 1, w - 2, 14, Ui.argb(rgb, survol ? 160 : 90));
        Ui.spaced(g, f, s, MINI_GRAS, x + 8, y + 8 - milieu(6.5F), Ui.argb(survol ? 0xFFFFFF : Ui.mix(rgb, 0xFFFFFF, 0.4F), 255), 0.8F);
        return x;
    }

    // ------------------------------------------------------------------ texte net (vraie taille, posé sur un point de l'écran)
    /** Milieu des capitales sous le point de dessin, pour une police DM Sans du mod de cette taille (décalage 1). */
    private static float milieu(float taille) {
        return taille * 0.49309F - 1.0F;
    }

    /** Texte : bord gauche en x, milieu des capitales sur cy ; rend sa largeur. */
    private static float texte(GuiGraphics g, Font f, String s, ResourceLocation police, float taille, float x, float cy, int rgb) {
        MutableComponent c = Ui.lit(s, police);
        g.pose().pushPose();
        placer(g, x, cy - milieu(taille));
        g.drawString(f, c, 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
        return f.getSplitter().stringWidth(c);
    }

    private static void droite(GuiGraphics g, Font f, String s, float xDroit, float cy, int rgb) {
        MutableComponent c = Ui.lit(s, CHIFFRE);
        g.pose().pushPose();
        placer(g, xDroit - f.getSplitter().stringWidth(c), cy - milieu(9.5F));
        g.drawString(f, c, 0, 0, Ui.argb(rgb, 255), false);
        g.pose().popPose();
    }

    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        defilement = Mth.clamp(defilement - (float) delta * LIGNE * 2, 0, maxDefilement());
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton != 0) return super.mouseClicked(mx, my, bouton);
        int lx = x0 + 6, ly = y0 + HAUT_LISTE, lw = L - 16, lh = hauteurListe();
        if (dans(mx, my, lx, ly, lw, lh)) {
            int i = (int) ((my - ly + defilement) / LIGNE);
            if (i >= 0 && i < tenues.size()) {
                choisie = tenues.get(i).id();
                envoyer(PaquetsCible.CHOISIR);
                return true;
            }
        }
        int by = y0 + H - BAS + 8;
        if (Tenues.de(choisie).aUnCasque() && dans(mx, my, x0 + 10, by, 150, 16)) {
            casque = !casque;
            envoyer(PaquetsCible.CHOISIR);
            return true;
        }
        Font f = this.font;
        int wFermer = (int) Ui.spacedWidth(f, Component.translatable("gui.heraliz_armory.cible.fermer").getString().toUpperCase(Locale.ROOT),
                MINI_GRAS, 0.8F) + 16;
        int xFermer = x0 + L - 10 - wFermer;
        if (dans(mx, my, xFermer, by, wFermer, 16)) {
            onClose();
            return true;
        }
        if (ouvert.ramasser()) {
            int w = (int) Ui.spacedWidth(f, Component.translatable("gui.heraliz_armory.cible.ramasser").getString().toUpperCase(Locale.ROOT),
                    MINI_GRAS, 0.8F) + 16;
            if (dans(mx, my, xFermer - 6 - w, by, w, 16)) {
                envoyer(PaquetsCible.RAMASSER);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, bouton);
    }

    private void envoyer(byte action) {
        ArmoryNetwork.CHANNEL.sendToServer(new PaquetsCible.Action(ouvert.entite(), action, choisie, casque));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
