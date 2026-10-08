package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.moteur.MoteurSons;
import fr.heralisz.matchs.moteur.api.Kit;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ecran de choix de classe (MATCHS_SPEC §1.7) : une carte par kit (classe, nom, silhouette de l'arme ou apercu TaCZ,
 * armes, 3 jauges portee / cadence / mobilite, limite « 1/1 SNIPER · PRIS PAR VORTEX »), compte a rebours en haut,
 * choix au clic ou aux touches 1 a 6. Le serveur valide (limites) et renvoie l'etat ; l'ecran se ferme a la fin du
 * choix. Echelle forcee (canevas 640 x 360), fond en degrade, aucun element vanilla. Les textes sont prepares a chaque
 * changement d'etat ({@link MoteurClient#versionChoix()}), pas a chaque image.
 */
public class ChoixClasseScreen extends Screen {
    private static final int CARTE_H = 206, ECART = 10;
    private int version = -1;
    private int cartesX, cartesY, carteW;
    // donnees preparees
    private List<Kit> kits = List.of();
    private List<ItemStack> apercus = List.of();
    private MutableComponent[] noms = new MutableComponent[0], armes = new MutableComponent[0], armes2 = new MutableComponent[0],
            limite1 = new MutableComponent[0], limite2 = new MutableComponent[0];
    private String[] classes = new String[0];
    private boolean[] pleine = new boolean[0];
    private String monKit = "";
    private String enAttente = "";
    private int couleur = 0x6FA8FF;
    private final TexteEspace titre = new TexteEspace(Ui.BOLD, 1.6f), sousTitre = new TexteEspace(Ui.SMALL, 1.2f),
            aide = new TexteEspace(Ui.SMALL, 1.0f);
    private int secondesMontrees = -1;
    private MutableComponent secondesC = Component.empty();
    private MutableComponent jaugePortee, jaugeCadence, jaugeMobilite, choisi;

    public ChoixClasseScreen() {
        super(Component.translatable("matchs.choix.titre"));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        version = -1;
        preparer();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (MoteurClient.versionChoix() != version) preparer();
    }

    private void preparer() {
        version = MoteurClient.versionChoix();
        PaquetsMoteur.ChoixOuvrirS2C c = MoteurClient.choix();
        if (c == null) return;
        Font font = this.font;
        kits = c.kits();
        apercus = c.apercus();
        couleur = c.couleur();
        int n = kits.size();
        carteW = Math.min(118, (width - 48 - (n - 1) * ECART) / Math.max(1, n));
        int total = n * carteW + (n - 1) * ECART;
        cartesX = width / 2 - total / 2;
        cartesY = Math.max(64, height / 2 - CARTE_H / 2 + 14);
        titre.texte(c.titre().getString());
        sousTitre.texte(Component.translatable("matchs.choix.sous_titre").getString());
        aide.texte(Component.translatable("matchs.choix.aide", n).getString());
        jaugePortee = Ui.tr(Ui.SMALL, "matchs.choix.portee");
        jaugeCadence = Ui.tr(Ui.SMALL, "matchs.choix.cadence");
        jaugeMobilite = Ui.tr(Ui.SMALL, "matchs.choix.mobilite");
        choisi = Ui.tr(Ui.SMALL, "matchs.choix.choisi");
        PaquetsMoteur.ChoixEtatS2C etat = MoteurClient.choixEtat();
        monKit = etat == null ? "" : etat.monKit();
        if (!monKit.isEmpty()) enAttente = "";
        noms = new MutableComponent[n];
        armes = new MutableComponent[n];
        armes2 = new MutableComponent[n];
        limite1 = new MutableComponent[n];
        limite2 = new MutableComponent[n];
        classes = new String[n];
        pleine = new boolean[n];
        Map<String, Integer> lim = c.limites();
        for (int i = 0; i < n; i++) {
            Kit k = kits.get(i);
            classes[i] = Component.translatable("matchs.classe." + k.classe()).getString();
            noms[i] = Ui.lit(Ui.ellipsis(font, k.nom(Ui.french()), Ui.BOLD, (int) ((carteW - 16) / 1.3f)), Ui.BOLD);
            ItemStack ap = i < apercus.size() ? apercus.get(i) : ItemStack.EMPTY;
            String p = k.principale() == null ? "-" : (!ap.isEmpty() ? ap.getHoverName().getString() : joli(k.principale().gunId()));
            armes[i] = Ui.lit(Ui.ellipsis(font, p, Ui.FONT, carteW - 16), Ui.FONT);
            armes2[i] = Ui.lit(k.secondaire() == null ? "" : Ui.ellipsis(font, "+ " + joli(k.secondaire().gunId()), Ui.SMALL, carteW - 16), Ui.SMALL);
            Integer max = lim.get(k.classe());
            if (max == null) {
                limite1[i] = Ui.tr(Ui.SMALL, "matchs.choix.sans_limite");
                limite2[i] = Component.empty();
                continue;
            }
            int pris = 0;
            StringBuilder qui = new StringBuilder();
            if (etat != null) {
                for (PaquetsMoteur.Pris pr : etat.pris()) {
                    if (!pr.classe().equals(k.classe())) continue;
                    pris++;
                    if (qui.length() > 0) qui.append(", ");
                    qui.append(pr.nom());
                }
            }
            boolean mien = kits.get(i).id().equals(monKit);
            int compte = pris + (mien ? 1 : 0);
            pleine[i] = pris >= max && !mien;
            limite1[i] = Ui.lit(compte + "/" + max + " " + classes[i].toUpperCase(Locale.ROOT), Ui.SMALL);
            limite2[i] = pris > 0 ? Ui.lit(Ui.ellipsis(font, Component.translatable("matchs.choix.pris_par", qui.toString()).getString(), Ui.SMALL, carteW - 16), Ui.SMALL)
                    : Ui.tr(Ui.SMALL, "matchs.choix.libre");
        }
    }

    /** « tacz:ai_awp » -> « AI AWP ». */
    private static String joli(String id) {
        int d = id.indexOf(':');
        return (d >= 0 ? id.substring(d + 1) : id).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        PaquetsMoteur.ChoixOuvrirS2C c = MoteurClient.choix();
        if (c == null) return;
        Font font = this.font;
        int cx = width / 2;
        // en-tete : titre, sous-titre, compte a rebours
        titre.centre(g, cx, 20, Ui.argb(Ui.TEXT, 255));
        sousTitre.centre(g, cx, 33, Ui.argb(Ui.MUTED, 255));
        float reste = MoteurClient.choixRestantS();
        int s = (int) Math.ceil(reste);
        if (s != secondesMontrees) {
            secondesMontrees = s;
            secondesC = Ui.lit(Integer.toString(s), Ui.BOLD);
            if (s <= 3 && s > 0 && minecraft != null) HudMoteur.jouer(MoteurSons.COMPTE.get(), 0.4f);
        }
        int bw = 260;
        Ui.bar(g, cx - bw / 2, 46, bw, 2, reste / Math.max(1f, c.secondes()), s <= 3 ? Ui.DANGER : couleur);
        g.pose().pushPose();
        g.pose().translate(cx + bw / 2f + 10, 40, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, secondesC, 0, 0, Ui.argb(s <= 3 ? Ui.DANGER : Ui.TEXT, 255), false);
        g.pose().popPose();
        // cartes
        for (int i = 0; i < kits.size(); i++) carte(g, font, i, mx, my);
        aide.centre(g, cx, cartesY + CARTE_H + 14, Ui.argb(Ui.FAINT, 255));
    }

    private int carteA(double mx, double my) {
        if (my < cartesY || my > cartesY + CARTE_H) return -1;
        for (int i = 0; i < kits.size(); i++) {
            int x = cartesX + i * (carteW + ECART);
            if (mx >= x && mx < x + carteW) return i;
        }
        return -1;
    }

    private void carte(GuiGraphics g, Font font, int i, int mx, int my) {
        Kit k = kits.get(i);
        int x = cartesX + i * (carteW + ECART), y = cartesY, w = carteW, h = CARTE_H;
        boolean mien = k.id().equals(monKit) || (monKit.isEmpty() && k.id().equals(enAttente));
        boolean survol = carteA(mx, my) == i && !pleine[i];
        if (survol && !mien) y -= 2;
        Ui.glass(g, x, y, w, h, mien ? 225 : 195);
        if (mien) {
            Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(couleur, 255));
            g.fill(x + 2, y + 1, x + w - 2, y + 3, Ui.argb(couleur, 255));
        } else if (survol) {
            Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(couleur, 150));
        }
        int tx = x + 8;
        Ui.chip(g, font, classes[i], tx, y + 8, mien ? couleur : 0x9AA3B5);
        g.drawString(font, Ui.lit(Integer.toString(i + 1), Ui.SMALL), x + w - 12, y + 10, Ui.argb(Ui.FAINT, 255), false);
        g.pose().pushPose();
        g.pose().translate(tx, y + 24, 0);
        g.pose().scale(1.3f, 1.3f, 1);
        g.drawString(font, noms[i], 0, 0, Ui.argb(Ui.TEXT, 255), false);
        g.pose().popPose();
        // silhouette de l'arme (apercu TaCZ si disponible)
        int zy = y + 42, zh = 40;
        Ui.soft(g, x + 6, zy, w - 12, zh, Ui.argb(0xFFFFFF, 6));
        ItemStack ap = i < apercus.size() ? apercus.get(i) : ItemStack.EMPTY;
        if (!ap.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f - 20, zy + zh / 2f - 20, 0);
            g.pose().scale(2.5f, 2.5f, 1);
            g.renderItem(ap, 0, 0);
            g.pose().popPose();
        } else {
            int px = w - 12 >= Silhouettes.LARGEUR * 2 ? 2 : 1;
            int sw = Silhouettes.LARGEUR * px, sh = Silhouettes.HAUTEUR * px;
            int sx = x + (w - sw) / 2 + px * 2, sy = zy + (zh - sh) / 2 + px * 2;
            g.enableScissor(x + 7, zy + 1, x + w - 7, zy + zh - 1);
            Silhouettes.dessiner(g, k.classe(), sx + px, sy + px, px, Ui.argb(0x000000, 120));
            Silhouettes.dessiner(g, k.classe(), sx, sy, px, Ui.argb(Ui.mix(mien ? couleur : 0xD8DCE4, 0xFFFFFF, 0.2f), 235));
            g.disableScissor();
        }
        g.drawString(font, armes[i], tx, y + 88, Ui.argb(Ui.TEXT, 235), false);
        g.drawString(font, armes2[i], tx, y + 100, Ui.argb(Ui.MUTED, 255), false);
        // jauges
        jauge(g, font, tx, y + 116, w - 16, jaugePortee, k.portee(), mien);
        jauge(g, font, tx, y + 134, w - 16, jaugeCadence, k.cadence(), mien);
        jauge(g, font, tx, y + 152, w - 16, jaugeMobilite, k.mobilite(), mien);
        // limite
        Ui.hairline(g, x + 6, x + w - 6, y + h - 34, 0xFFFFFF, 30, true, true);
        int cl = pleine[i] ? Ui.DANGER : Ui.MUTED;
        g.drawString(font, limite1[i], tx, y + h - 28, Ui.argb(cl, 255), false);
        g.drawString(font, limite2[i], tx, y + h - 17, Ui.argb(pleine[i] ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.3f) : Ui.FAINT, 255), false);
        if (mien) {
            int cw = font.width(choisi) + 10;
            Ui.soft(g, x + w - cw - 6, y + 24, cw, 11, Ui.argb(couleur, 60));
            g.drawString(font, choisi, x + w - cw - 1, y + 26, Ui.argb(0xFFFFFF, 255), false);
        }
        if (pleine[i]) Ui.soft(g, x, y, w, h, Ui.argb(0x05060A, 120));
    }

    private static void jauge(GuiGraphics g, Font font, int x, int y, int w, MutableComponent label, float v, boolean mien) {
        g.drawString(font, label, x, y, Ui.argb(Ui.MUTED, 255), false);
        int segs = 10, sw = (w - (segs - 1)) / segs;
        int pleins = Math.round(v * segs);
        for (int s = 0; s < segs; s++) {
            int c = s < pleins ? Ui.argb(mien ? Ui.accent() : 0xE6E9EF, s < pleins ? 230 : 0) : Ui.argb(0xFFFFFF, 24);
            g.fill(x + s * (sw + 1), y + 9, x + s * (sw + 1) + sw, y + 12, c);
        }
    }

    // ------------------------------------------------------------------ entrees
    private void choisir(int i) {
        if (i < 0 || i >= kits.size() || pleine[i]) return;
        enAttente = kits.get(i).id();
        SonsUi.jouer(MoteurSons.CHOIX.get(), 0.7f);
        MatchsNet.versServeur(new PaquetsMoteur.ChoixC2S(enAttente));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int i = carteA(mx, my);
        if (bouton == 0 && i >= 0) {
            choisir(i);
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choisir(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) {
            choisir(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // fond dessine dans render (degrade, le monde reste visible)
    }
}
