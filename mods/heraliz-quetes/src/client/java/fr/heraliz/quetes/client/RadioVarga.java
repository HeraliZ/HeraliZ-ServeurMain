package fr.heraliz.quetes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heraliz.quetes.HeralizQuetes;
import fr.heraliz.quetes.client.kit.SonsUi;
import fr.heraliz.quetes.client.kit.Ui;
import fr.heraliz.quetes.reseau.Reseau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * La radio du Commandant Varga, en bas à gauche de l'écran (maquette A « radio carrée », validée le 18/10) : son
 * portrait (sa vraie apparence de PNJ : skin CaraCrea, sinon son skin), son nom, la consigne qui s'écrit lettre par
 * lettre, et l'avancée du tutoriel des bases (6 petites barres, « ÉTAPE n/6 »). Le serveur envoie chaque consigne
 * ({@link Reseau.Radio}) ; la consigne reste à l'écran tant que l'étape n'est pas faite (la suivante la remplace) ; seul
 * le mot de la fin s'efface tout seul, quelques secondes après.
 * Dessiné dans un repère de 640 × 360 (même taille à l'écran quelle que soit l'échelle de l'interface).
 */
public final class RadioVarga {
    private RadioVarga() {
    }

    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");
    /** Taille du texte dans le repère 640 × 360. */
    private static final float TXT = 0.7F;
    private static final int LARGEUR = 210, HAUTEUR = 54, PORTRAIT = 44;
    /** Une lettre toutes les 28 ms ; le mot de la fin reste 14 s après la fin de la phrase. */
    private static final long LETTRE_MS = 28, RESTE_FIN_MS = 14_000, FONDU_MS = 600;

    private static Reseau.Radio radio;
    private static long debut;
    private static int lettresJouees;
    private static ResourceLocation skin;

    static void recevoir(Reseau.Radio r) {
        if (r.texte().vide()) {
            radio = null;
            return;
        }
        radio = r;
        debut = System.currentTimeMillis();
        lettresJouees = 0;
        skin = null;
        SonsUi.ouvrir(0.35F);
    }

    static void vider() {
        radio = null;
        skin = null;
    }

    static void rendu(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Reseau.Radio r = radio;
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.options.hideGui || mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        String texte = TexteClient.str(r.texte());
        long age = System.currentTimeMillis() - debut;
        int lettres = (int) Math.min(texte.length(), age / LETTRE_MS);
        long finPhrase = texte.length() * LETTRE_MS;
        // une consigne reste jusqu'à la suivante ; le mot de la fin s'efface
        long reste = r.etape() == 0 ? RESTE_FIN_MS - (age - finPhrase) : Long.MAX_VALUE;
        if (reste <= 0) {
            radio = null;
            return;
        }
        float alpha = Math.min(1F, Math.min(age / 250F, reste / (float) FONDU_MS));
        // un petit bruit de radio toutes les 3 lettres
        if (lettres > lettresJouees) {
            if (lettres / 3 > lettresJouees / 3 && lettres < texte.length()) SonsUi.clic(0.12F);
            lettresJouees = lettres;
        }
        float s = Math.min(w / 640F, h / 360F);
        g.pose().pushPose();
        g.pose().scale(s, s, 1);
        dessiner(g, mc.font, r, texte, lettres, (int) (h / s), alpha, s);
        g.pose().popPose();
    }

    private static void dessiner(GuiGraphics g, Font f, Reseau.Radio r, String texte, int lettres, int hv, float alpha, float s) {
        int a = (int) (255 * alpha);
        String montre = texte.substring(0, lettres);
        boolean tape = lettres < texte.length();
        // hauteur d'après la phrase entière (l'encadré ne grandit pas pendant qu'elle s'écrit)
        int tx0 = 8 + 5 + PORTRAIT + 7;
        int largeurTexte = (int) ((8 + LARGEUR - 8 - tx0) / TXT);
        int nLignes = f.split(Ui.lit(texte, Ui.FONT), largeurTexte).size();
        int hauteur = Math.max(HAUTEUR, 16 + (int) Math.ceil(nLignes * 8.5F) + 11);
        int x = 8, y = hv - 6 - 22 - 8 - hauteur;
        Ui.glass(g, x, y, LARGEUR, hauteur, (int) (205 * alpha));

        // portrait : fond kaki, buste du PNJ, lignes de radio, cadre
        int px = x + 5, py = y + 5;
        g.fillGradient(px, py, px + PORTRAIT, py + PORTRAIT, Ui.argb(0x222C1E, a), Ui.argb(0x161C13, a));
        // le découpage se donne dans le repère de l'interface, sans notre échelle
        g.enableScissor(Math.round(px * s), Math.round(py * s), Math.round((px + PORTRAIT) * s), Math.round((py + PORTRAIT) * s));
        buste(g, r, px, py, alpha);
        g.disableScissor();
        for (int ly = py; ly < py + PORTRAIT; ly += 2) g.fill(px, ly, px + PORTRAIT, ly + 1, Ui.argb(0x000000, (int) (40 * alpha)));
        int cadre = Ui.argb(0x96AA6E, (int) (160 * alpha));
        g.fill(px, py, px + PORTRAIT, py + 1, cadre);
        g.fill(px, py + PORTRAIT - 1, px + PORTRAIT, py + PORTRAIT, cadre);
        g.fill(px, py, px + 1, py + PORTRAIT, cadre);
        g.fill(px + PORTRAIT - 1, py, px + PORTRAIT, py + PORTRAIT, cadre);
        // point rouge « en direct », qui clignote pendant qu'il parle
        if (!tape || (System.currentTimeMillis() / 400) % 2 == 0) Ui.point(g, px + 3, py + 3, 0xFF5046, a);

        // en-tête : nom, « RADIO · 7e RÉG. », barres de signal
        int tx = px + PORTRAIT + 7;
        String nom = TexteClient.str(r.nom()).toUpperCase(java.util.Locale.ROOT);
        float nw = texte(g, f, Ui.lit(nom, Ui.BOLD), tx, y + 5, Ui.argb(Ui.OR, a));
        texte(g, f, Ui.lit(TexteClient.tr("heraliz_quetes.tuto.radio_entete"), Ui.SMALL), tx + nw + 5, y + 6, Ui.argb(Ui.FAINT, a));
        for (int i = 0; i < 4; i++) {
            int bh = 2 + i * 2;
            g.fill(x + LARGEUR - 18 + i * 3, y + 11 - bh, x + LARGEUR - 16 + i * 3, y + 11, Ui.argb(i < 3 ? 0x96AA6E : Ui.FAINT, a));
        }

        // la consigne, lettre par lettre
        List<FormattedCharSequence> lignes = f.split(Ui.lit(montre, Ui.FONT), largeurTexte);
        float ly = y + 16;
        float finX = tx;
        for (FormattedCharSequence l : lignes) {
            g.pose().pushPose();
            g.pose().translate(tx, ly, 0);
            g.pose().scale(TXT, TXT, 1);
            g.drawString(f, l, 0, 0, Ui.argb(Ui.TEXT, a), false);
            g.pose().popPose();
            finX = tx + f.width(l) * TXT;
            ly += 8.5F;
        }
        if (tape) {
            int cy = (int) (ly - 8.5F);
            g.fill((int) finX + 1, cy, (int) finX + 2, cy + 6, Ui.argb(Ui.TEXT, a));
        }

        // avancée : 6 barres et « ÉTAPE n/6 » (« TERMINÉ » pour le mot de la fin)
        int total = Math.max(1, r.total());
        int bx = tx, by = y + hauteur - 7;
        for (int i = 0; i < total; i++) {
            int c = r.etape() == 0 || i < r.etape() - 1 ? Ui.OK : i == r.etape() - 1 ? Ui.AMBRE : 0x3E424A;
            g.fill(bx + i * 9, by, bx + i * 9 + 7, by + 2, Ui.argb(c, a));
        }
        String etape = r.etape() == 0 ? TexteClient.tr("heraliz_quetes.tuto.radio_fini")
                : TexteClient.tr("heraliz_quetes.tuto.radio_etape", r.etape(), total);
        texte(g, f, Ui.lit(etape, Ui.SMALL), bx + total * 9 + 2, by - 2, Ui.argb(Ui.MUTED, a));
    }

    /** Texte réduit à la taille de la maquette ; rend sa largeur dans le repère 640 × 360. */
    private static float texte(GuiGraphics g, Font f, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(TXT, TXT, 1);
        g.drawString(f, c, 0, 0, argb, false);
        g.pose().popPose();
        return f.width(c) * TXT;
    }

    /** Buste du PNJ d'après son skin 64 × 64 : bras et torse (avec leurs calques), puis la tête et ses cheveux. */
    private static void buste(GuiGraphics g, Reseau.Radio r, int px, int py, float alpha) {
        ResourceLocation tex = skin(r);
        RenderSystem.enableBlend();
        g.setColor(1F, 1F, 1F, alpha);
        int t = 22, bras = 11, haut = py + 26;
        int cx = px + PORTRAIT / 2;
        // torse (8 × 12 du skin, on n'en voit que le haut) et bras
        g.blit(tex, cx - t / 2, haut, t, 33, 20, 20, 8, 12, 64, 64);
        g.blit(tex, cx - t / 2, haut, t, 33, 20, 36, 8, 12, 64, 64);
        g.blit(tex, cx - t / 2 - bras, haut, bras, 33, 44, 20, 4, 12, 64, 64);
        g.blit(tex, cx - t / 2 - bras, haut, bras, 33, 44, 36, 4, 12, 64, 64);
        g.blit(tex, cx + t / 2, haut, bras, 33, 36, 52, 4, 12, 64, 64);
        g.blit(tex, cx + t / 2, haut, bras, 33, 52, 52, 4, 12, 64, 64);
        // tête 8 × 8 et son calque (cheveux, casquette)
        g.blit(tex, cx - t / 2, py + 4, t, t, 8, 8, 8, 8, 64, 64);
        g.blit(tex, cx - t / 2 - 1, py + 3, t + 2, t + 2, 40, 8, 8, 8, 64, 64);
        g.setColor(1F, 1F, 1F, 1F);
    }

    /** Skin peint par CaraCrea pour l'apparence du PNJ, sinon son skin du jar, sinon Steve / Alex. */
    private static ResourceLocation skin(Reseau.Radio r) {
        if (!r.apparence().isEmpty() && ModList.get().isLoaded("caracrea")) {
            ResourceLocation peinte = CaraCreaPnj.texture(r.apparence());
            if (peinte != null) return peinte;   // CaraCrea peint en arrière-plan : redemandé à chaque image
        }
        if (skin == null) {
            ResourceLocation rl = r.skin().isEmpty() ? null : ResourceLocation.tryParse(HeralizQuetes.MODID + ":textures/pnj/" + r.skin() + ".png");
            skin = rl != null && Minecraft.getInstance().getResourceManager().getResource(rl).isPresent() ? rl : r.brasFins() ? ALEX : STEVE;
        }
        return skin;
    }
}
