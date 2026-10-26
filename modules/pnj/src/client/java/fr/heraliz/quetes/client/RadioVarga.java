package fr.heraliz.quetes.client;

import com.mojang.blaze3d.platform.Window;
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
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;

import java.util.List;

/**
 * La radio du Commandant Varga, en bas à gauche de l'écran (maquette A « radio carrée », validée le 18/10) : son
 * portrait (sa vraie apparence de PNJ : skin CaraCrea, sinon son skin), son nom, la consigne qui s'écrit lettre par
 * lettre, et l'avancée du tutoriel des bases (une petite barre par étape, « ÉTAPE n/9 »). Le serveur envoie chaque consigne
 * ({@link Reseau.Radio}) ; la consigne reste à l'écran tant que l'étape n'est pas faite (la suivante la remplace) ; seul
 * le mot de la fin s'efface tout seul, quelques secondes après.
 * Dessiné dans un repère de 640 × 360 (même taille à l'écran quelle que soit l'échelle de l'interface) dont une unité
 * vaut un nombre entier de points d'écran (3 en 1080p). Texte à sa vraie taille (polices de 7,5 et 6,5 : le texte de la
 * maquette réduit à 0,7), jamais agrandi ni réduit, chaque texte posé sur un point de l'écran.
 */
public final class RadioVarga {
    private RadioVarga() {
    }

    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");
    /**
     * Polices à leur vraie taille dans le repère 640 × 360 (avant : polices de 11 et 9 réduites à 0,7) : nom en gras 7,5,
     * consigne en 7,5, petits textes en 6,5.
     */
    private static final ResourceLocation NOM = new ResourceLocation("heraliz_quetes", "ui_micro"),
            MOYEN = new ResourceLocation("heraliz_quetes", "ui_moyen"), MINI = new ResourceLocation("heraliz_quetes", "ui_mini");
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

    /**
     * Pendant le tutoriel (une consigne à l'écran), le chat est caché : ses messages couvraient la radio. Il revient
     * dès qu'on l'ouvre (touche T) et à la fin du tutoriel.
     */
    static void cacherChat(RenderGuiOverlayEvent.Pre e) {
        Reseau.Radio r = radio;
        if (r != null && r.etape() > 0 && e.getOverlay() == VanillaGuiOverlay.CHAT_PANEL.type()
                && !(Minecraft.getInstance().screen instanceof ChatScreen)) {
            e.setCanceled(true);
        }
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
        // une unité du repère = un nombre entier de points d'écran (avant : min(w / 640, h / 360), n'importe quelle valeur,
        // 0,98 dans une fenêtre 1080p) : en 1080p à l'échelle 3 de l'interface, s = 1, rien n'est agrandi ni réduit ; à une
        // autre échelle, une unité vaut toujours 3 points en 1080p (4 en 1440p) et les lettres, à leur vraie taille dans le
        // repère, sont dessinées point pour point comme à l'échelle 3
        Window fenetre = mc.getWindow();
        int points = Math.max(1, Math.round(Math.min(fenetre.getWidth() / 640F, fenetre.getHeight() / 360F)));
        float s = (float) (points / fenetre.getGuiScale());
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
        int largeurTexte = 8 + LARGEUR - 8 - tx0;
        int nLignes = lignes(f, texte, largeurTexte, true).size();
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

        // en-tête : nom, « RADIO · 7e RÉG. », barres de signal (milieux des capitales à leur place d'avant)
        int tx = px + PORTRAIT + 7;
        String nom = TexteClient.str(r.nom()).toUpperCase(java.util.Locale.ROOT);
        float nw = texte(g, f, nom, NOM, tx, y + 8.1F, Ui.argb(Ui.OR, a));
        texte(g, f, TexteClient.tr("heraliz_quetes.tuto.radio_entete"), MINI, tx + nw + 5, y + 8.4F, Ui.argb(Ui.FAINT, a));
        for (int i = 0; i < 4; i++) {
            int bh = 2 + i * 2;
            g.fill(x + LARGEUR - 18 + i * 3, y + 11 - bh, x + LARGEUR - 16 + i * 3, y + 11, Ui.argb(i < 3 ? 0x96AA6E : Ui.FAINT, a));
        }

        // la consigne, lettre par lettre
        List<FormattedCharSequence> lignes = lignes(f, montre, largeurTexte, false);
        float ly = y + 16;
        float finX = tx;
        for (FormattedCharSequence l : lignes) {
            ligne(g, f, l, tx, ly + 3.1F, Ui.argb(Ui.TEXT, a));
            finX = tx + f.getSplitter().stringWidth(l);
            ly += 8.5F;
        }
        if (tape) {
            int cy = (int) (ly - 8.5F);
            g.fill((int) finX + 1, cy, (int) finX + 2, cy + 6, Ui.argb(Ui.TEXT, a));
        }

        // avancée : une barre par étape et « ÉTAPE n/9 » (« TERMINÉ » pour le mot de la fin)
        int total = Math.max(1, r.total());
        int bx = tx, by = y + hauteur - 7;
        for (int i = 0; i < total; i++) {
            int c = r.etape() == 0 || i < r.etape() - 1 ? Ui.OK : i == r.etape() - 1 ? Ui.AMBRE : 0x3E424A;
            g.fill(bx + i * 9, by, bx + i * 9 + 7, by + 2, Ui.argb(c, a));
        }
        String etape = r.etape() == 0 ? TexteClient.tr("heraliz_quetes.tuto.radio_fini")
                : TexteClient.tr("heraliz_quetes.tuto.radio_etape", r.etape(), total);
        texte(g, f, etape, MINI, bx + total * 9 + 2, by + 0.4F, Ui.argb(Ui.MUTED, a));
    }

    /**
     * Lignes coupées à la largeur, gardées tant que le texte ne change pas (la phrase entière : jusqu'à la consigne
     * suivante ; la partie écrite : jusqu'à la lettre suivante). Avant, les deux étaient recoupées à chaque image.
     */
    private static String coupeEntier, coupeMontre;
    private static List<FormattedCharSequence> lignesEntier, lignesMontre;
    private static int largeurEntier = -1, largeurMontre = -1;

    private static List<FormattedCharSequence> lignes(Font f, String t, int largeur, boolean entier) {
        if (entier) {
            if (largeur != largeurEntier || !t.equals(coupeEntier)) {
                lignesEntier = f.split(Ui.lit(t, MOYEN), largeur);
                coupeEntier = t;
                largeurEntier = largeur;
            }
            return lignesEntier;
        }
        if (largeur != largeurMontre || !t.equals(coupeMontre)) {
            lignesMontre = f.split(Ui.lit(t, MOYEN), largeur);
            coupeMontre = t;
            largeurMontre = largeur;
        }
        return lignesMontre;
    }

    // ------------------------------------------------------------------ texte à sa vraie taille, posé net
    /** Milieu des capitales sous le point de dessin (DM Sans du mod, décalage 1 : taille x 0,49309 - 1). */
    private static float milieu(ResourceLocation police) {
        float taille = switch (police.getPath()) {
            case "ui_mini" -> 6.5F;
            case "ui_micro", "ui_moyen" -> 7.5F;
            default -> 11F;
        };
        return taille * 0.49309F - 1F;
    }

    /** Pose le dessin qui suit sur le point d'écran le plus proche (lettres nettes ; comme le journal des quêtes). */
    private static void placer(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        float ex = m.m00() * x + m.m30(), ey = m.m11() * y + m.m31();
        float rx = (float) (Math.round(ex * s) / s), ry = (float) (Math.round(ey * s) / s);
        float kx = m.m00() == 0 ? 1 : m.m00(), ky = m.m11() == 0 ? 1 : m.m11();
        g.pose().translate(x + (rx - ex) / kx, y + (ry - ey) / ky, 0);
    }

    /** Texte : bord gauche en x, milieu des capitales sur cy ; rend sa largeur dans le repère 640 × 360. */
    private static float texte(GuiGraphics g, Font f, String s, ResourceLocation police, float x, float cy, int argb) {
        Component c = Ui.lit(s, police);
        g.pose().pushPose();
        placer(g, x, cy - milieu(police));
        g.drawString(f, c, 0, 0, argb, false);
        g.pose().popPose();
        return f.getSplitter().stringWidth(c);
    }

    /** Ligne de la consigne, déjà coupée (police {@link #MOYEN}) : bord gauche en x, milieu des capitales sur cy. */
    private static void ligne(GuiGraphics g, Font f, FormattedCharSequence l, float x, float cy, int argb) {
        g.pose().pushPose();
        placer(g, x, cy - milieu(MOYEN));
        g.drawString(f, l, 0, 0, argb, false);
        g.pose().popPose();
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
