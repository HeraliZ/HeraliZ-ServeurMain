package fr.heralisz.tags.client.gui;

import fr.heralisz.tags.catalogue.TagCatalog;
import fr.heralisz.tags.catalogue.TagDef;
import fr.heralisz.tags.client.ClientTags;
import fr.heralisz.tags.client.gui.kit.Echelle;
import fr.heralisz.tags.client.gui.kit.HzScroll;
import fr.heralisz.tags.client.gui.kit.Ui;
import fr.heralisz.tags.network.Packets;
import fr.heralisz.tags.network.TagsNet;
import fr.heralisz.tags.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * « COLLECTION » (echelle forcee, canevas 640×360) : grille de tous les tags (cases 40, 8 par ligne, defilante) ;
 * possede = image nette + lisere de rarete, manquant = silhouette sombre 30 % ; survol = nom + rarete ; a droite la
 * roue (4 emplacements en losange : haut, droite, bas, gauche). Placement : clic sur un emplacement puis sur un tag
 * (ou l'inverse), ou glisser-deposer d'un tag vers un emplacement ; clic droit sur un emplacement = vider.
 * Chaque changement part au serveur (RoueC2S) ; l'EtatS2C de retour fait foi. Compteur « 14 / 36 ».
 */
public class IndexScreen extends Screen implements EcranTags {
    public static final int CASE = 40, PAS = 48, COLONNES = 8, MARGE = 16;

    private int px, py, pw, ph;
    private HzScroll scroll;
    private int survol = -1;
    /** Emplacement de la roue selectionne (-1 aucun), tag selectionne (null aucun). */
    private int empSel = -1;
    private String tagSel;
    /** Glisser-deposer : tag saisi, point de depart, actif des que la souris a bouge de 4 px. */
    private String glisseId;
    private double glisseX0, glisseY0;
    private boolean glisse;
    private int empSurvol = -1;
    /** Centres des 4 emplacements de la roue et leur taille. */
    private final int[] ex = new int[4], ey = new int[4];
    private int empTaille;
    private boolean fermeture;

    public IndexScreen() {
        super(Component.translatable("gui.heralisz_tags.collection"));
    }

    @Override
    protected void init() {
        if (!fermeture) Echelle.appliquer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int grilleW = COLONNES * PAS - (PAS - CASE);
        int gx = px + MARGE, gy = py + 44;
        int gh = ph - 44 - MARGE;
        int sw = Math.min(grilleW + 8, pw - MARGE * 2);
        scroll = new HzScroll(gx, gy, sw, gh);
        // roue : losange dans la colonne de droite
        int rx = gx + sw + 12, rw = px + pw - MARGE - rx;
        empTaille = Math.max(28, Math.min(CASE, (rw - 12) / 3));
        int ccx = rx + rw / 2, ccy = gy + 18 + empTaille + 6 + empTaille / 2;
        int pasE = empTaille + 6;
        ex[0] = ccx;
        ey[0] = ccy - pasE;
        ex[1] = ccx + pasE;
        ey[1] = ccy;
        ex[2] = ccx;
        ey[2] = ccy + pasE;
        ex[3] = ccx - pasE;
        ey[3] = ccy;
        rafraichir();
    }

    @Override
    public void removed() {
        fermeture = true;
        Echelle.retablir();
        super.removed();
    }

    /** Recalcule la hauteur du contenu (apres un EtatS2C ou un redimensionnement). */
    public void rafraichir() {
        if (scroll == null) return;
        int n = TagCatalog.taille();
        int lignes = (n + COLONNES - 1) / COLONNES;
        scroll.setContentHeight(lignes * PAS - (PAS - CASE));
        if (tagSel != null && !ClientTags.possede(tagSel)) tagSel = null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // fond : degrade diagonal, le monde reste lisible
        Ui.fondDegrade(g, 0, 0, width, height, 0x8C000000, 0xB4000000);
        Ui.glass(g, px, py, pw, ph);

        // en-tete : titre + compteur
        Ui.spaced(g, font, title.getString(), Ui.BOLD, px + MARGE, py + 15, Ui.argb(Ui.TEXT, 255), 2.2f);
        String compteur = ClientTags.nombreDebloques() + " / " + TagCatalog.taille();
        Ui.right(g, font, Ui.lit(compteur, Ui.BOLD), px + pw - MARGE, py + 14, Ui.argb(Ui.TEXT, 255));
        Ui.right(g, font, Ui.tr(Ui.SMALL, "gui.heralisz_tags.debloques"), px + pw - MARGE, py + 25, Ui.argb(Ui.MUTED, 255));
        Ui.hairline(g, px + MARGE, px + pw - MARGE, py + 34, 0xFFFFFF, 40, false, true);

        // grille
        scroll.tick(partial);
        survol = -1;
        List<TagDef> tags = TagCatalog.tous();
        boolean atlas = Ui.atlasPresent();
        scroll.begin(g);
        int x0 = scroll.x(), y0 = scroll.y() - scroll.offset();
        for (int i = 0; i < tags.size(); i++) {
            TagDef d = tags.get(i);
            int cx = x0 + (i % COLONNES) * PAS, cy = y0 + (i / COLONNES) * PAS;
            if (cy + CASE < scroll.y() || cy > scroll.y() + scroll.height()) continue;
            boolean possede = ClientTags.possede(d.id());
            boolean hover = !glisse && scroll.contient(mouseX, mouseY) && mouseX >= cx && mouseX < cx + CASE && mouseY >= cy && mouseY < cy + CASE;
            boolean sel = d.id().equals(tagSel);
            if (hover) survol = i;
            Ui.soft(g, cx, cy, CASE, CASE, Ui.argb(0xFFFFFF, hover ? 18 : 8));
            if (atlas) {
                Ui.caseAtlas(g, d.cas(), cx + 2, cy + 2, CASE - 4, possede ? 1f : 0.3f);
                if (!possede) Ui.soft(g, cx + 2, cy + 2, CASE - 4, CASE - 4, Ui.argb(0x000000, 90));
            } else {
                Ui.soft(g, cx + 6, cy + 6, CASE - 12, CASE - 12, Ui.argb(possede ? d.rarete().couleur : 0xFFFFFF, possede ? 40 : 10));
            }
            if (sel) Ui.softOutline(g, cx + 1, cy + 1, CASE - 2, CASE - 2, Ui.argb(Ui.accent, 255));
            else if (possede) Ui.softOutline(g, cx + 1, cy + 1, CASE - 2, CASE - 2, Ui.argb(d.rarete().couleur, hover ? 220 : 130));
            else if (hover) Ui.softOutline(g, cx + 1, cy + 1, CASE - 2, CASE - 2, Ui.argb(0xFFFFFF, 50));
            // pastille : ce tag est deja dans la roue
            int q = quartierDe(d.id());
            if (q >= 0) {
                g.fill(cx + CASE - 9, cy + 3, cx + CASE - 3, cy + 9, Ui.argb(Ui.accent, 230));
                Ui.centered(g, font, Ui.lit(String.valueOf(q + 1), Ui.SMALL), cx + CASE - 6, cy + 2, 0xFF0B0C0F);
            }
        }
        scroll.end(g);

        // droite : la roue
        empSurvol = -1;
        int rx = scroll.x() + scroll.width() + 12;
        Ui.spaced(g, font, Component.translatable("gui.heralisz_tags.roue").getString(), Ui.SMALL, rx, scroll.y(), Ui.argb(Ui.MUTED, 255), 1.4f);
        for (int i = 0; i < 4; i++) {
            int qx = ex[i] - empTaille / 2, qy = ey[i] - empTaille / 2;
            String id = ClientTags.roue(i);
            TagDef d = TagCatalog.get(id);
            boolean hover = mouseX >= qx && mouseX < qx + empTaille && mouseY >= qy && mouseY < qy + empTaille;
            if (hover) empSurvol = i;
            boolean sel = i == empSel;
            boolean cible = hover && (glisse || tagSel != null);
            Ui.soft(g, qx, qy, empTaille, empTaille, Ui.argb(cible ? Ui.accent : 0xFFFFFF, cible ? 40 : hover ? 16 : 8));
            int bord = sel || cible ? Ui.argb(Ui.accent, 255) : Ui.argb(d != null ? d.rarete().couleur : 0xFFFFFF, d != null ? 130 : 36);
            Ui.softOutline(g, qx + 1, qy + 1, empTaille - 2, empTaille - 2, bord);
            if (d != null && atlas) Ui.caseAtlas(g, d.cas(), qx + 2, qy + 2, empTaille - 4, 1f);
            else if (d != null) Ui.soft(g, qx + 6, qy + 6, empTaille - 12, empTaille - 12, Ui.argb(d.rarete().couleur, 40));
            else Ui.centered(g, font, Ui.lit(String.valueOf(i + 1), Ui.SMALL), ex[i], ey[i] - 3, Ui.argb(Ui.FAINT, 255));
        }
        // aide
        int ay = ey[2] + empTaille / 2 + 12;
        int aw = px + pw - MARGE - rx;
        String aideCle = empSel >= 0 ? "gui.heralisz_tags.aide_emplacement" : tagSel != null ? "gui.heralisz_tags.aide_tag" : "gui.heralisz_tags.aide_roue";
        for (FormattedCharSequence l : font.split(Ui.tr(Ui.SMALL, aideCle), aw)) {
            g.drawString(font, l, rx, ay, Ui.argb(Ui.MUTED, 255), false);
            ay += 10;
        }
        // appui bref de J = quartier 1 (haut)
        ay += 4;
        for (FormattedCharSequence l : font.split(Ui.tr(Ui.SMALL, "gui.heralisz_tags.aide_touche"), aw)) {
            g.drawString(font, l, rx, ay, Ui.argb(Ui.FAINT, 255), false);
            ay += 10;
        }

        super.render(g, mouseX, mouseY, partial);

        // tag en cours de glissement : image suivant la souris
        if (glisse && glisseId != null) {
            TagDef d = TagCatalog.get(glisseId);
            if (d != null) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                if (atlas) Ui.caseAtlas(g, d.cas(), mouseX - 14, mouseY - 14, 28, 0.85f);
                else Ui.soft(g, mouseX - 10, mouseY - 10, 20, 20, Ui.argb(d.rarete().couleur, 120));
                g.pose().popPose();
            }
        } else if (survol >= 0) {
            TagDef d = tags.get(survol);
            boolean possede = ClientTags.possede(d.id());
            Ui.tooltip(g, font, List.of(
                    Ui.lit(d.nom(Ui.french()), Ui.BOLD),
                    Ui.tr(Ui.SMALL, d.rarete().cle()).withStyle(s -> s.withColor(d.rarete().couleur)),
                    Ui.tr(Ui.SMALL, possede ? "gui.heralisz_tags.possede" : "gui.heralisz_tags.manquant").withStyle(s -> s.withColor(Ui.MUTED))
            ), mouseX, mouseY, d.rarete().couleur);
        } else if (empSurvol >= 0 && !glisse && tagSel == null) {
            TagDef d = TagCatalog.get(ClientTags.roue(empSurvol));
            if (d != null) {
                Ui.tooltip(g, font, List.of(
                        Ui.lit(d.nom(Ui.french()), Ui.BOLD),
                        Ui.tr(Ui.SMALL, "gui.heralisz_tags.quartier_" + empSurvol).withStyle(s -> s.withColor(Ui.MUTED)),
                        Ui.tr(Ui.SMALL, "gui.heralisz_tags.clic_droit_vider").withStyle(s -> s.withColor(Ui.FAINT))
                ), mouseX, mouseY, d.rarete().couleur);
            }
        }
    }

    /** Quartier ou se trouve ce tag, -1 sinon. */
    private static int quartierDe(String id) {
        for (int i = 0; i < 4; i++) if (id.equals(ClientTags.roue(i))) return i;
        return -1;
    }

    /** Index du tag de la grille sous la souris (-1). */
    private int tagSous(double mx, double my) {
        if (!scroll.contient(mx, my)) return -1;
        int x0 = scroll.x(), y0 = scroll.y() - scroll.offset();
        List<TagDef> tags = TagCatalog.tous();
        for (int i = 0; i < tags.size(); i++) {
            int cx = x0 + (i % COLONNES) * PAS, cy = y0 + (i / COLONNES) * PAS;
            if (mx >= cx && mx < cx + CASE && my >= cy && my < cy + CASE) return i;
        }
        return -1;
    }

    private int emplacementSous(double mx, double my) {
        for (int i = 0; i < 4; i++) {
            int qx = ex[i] - empTaille / 2, qy = ey[i] - empTaille / 2;
            if (mx >= qx && mx < qx + empTaille && my >= qy && my < qy + empTaille) return i;
        }
        return -1;
    }

    /** Place le tag dans le quartier (en retirant son ancien quartier) et envoie la roue au serveur. */
    private void placer(int quartier, String id) {
        if (quartier < 0 || id == null) return;
        if (!ClientTags.possede(id)) return;
        int ancien = quartierDe(id);
        if (ancien >= 0 && ancien != quartier) ClientTags.roueLocale(ancien, ClientTags.roue(quartier));
        ClientTags.roueLocale(quartier, id);
        envoyerRoue();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.ROUE_CHOISIR.get(), 0.6f, 1f));
        empSel = -1;
        tagSel = null;
    }

    private void vider(int quartier) {
        if (quartier < 0 || ClientTags.roue(quartier).isEmpty()) return;
        ClientTags.roueLocale(quartier, "");
        envoyerRoue();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(Sons.ROUE_CHOISIR.get(), 0.4f, 0.8f));
    }

    private void envoyerRoue() {
        TagsNet.toServer(new Packets.RoueC2S(ClientTags.roue()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return scroll.mouseScrolled(mx, my, delta) || super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (scroll.mouseClicked(mx, my, bouton)) return true;
        int emp = emplacementSous(mx, my);
        if (emp >= 0) {
            if (bouton == 1) {
                vider(emp);
                empSel = -1;
                return true;
            }
            if (tagSel != null) {
                placer(emp, tagSel);
                return true;
            }
            empSel = empSel == emp ? -1 : emp;
            return true;
        }
        int i = tagSous(mx, my);
        if (i >= 0 && bouton == 0) {
            TagDef d = TagCatalog.tous().get(i);
            if (!ClientTags.possede(d.id())) return true;
            if (empSel >= 0) {
                placer(empSel, d.id());
                return true;
            }
            // selection (clic) ou debut de glisser-deposer
            glisseId = d.id();
            glisseX0 = mx;
            glisseY0 = my;
            glisse = false;
            return true;
        }
        if (bouton == 0) {
            empSel = -1;
            tagSel = null;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int bouton, double dx, double dy) {
        if (glisseId != null && !glisse && (Math.abs(mx - glisseX0) > 4 || Math.abs(my - glisseY0) > 4)) glisse = true;
        return scroll.mouseDragged(mx, my) || glisseId != null || super.mouseDragged(mx, my, bouton, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int bouton) {
        scroll.mouseReleased();
        if (glisseId != null) {
            if (glisse) {
                int emp = emplacementSous(mx, my);
                if (emp >= 0) placer(emp, glisseId);
            } else {
                // simple clic : selection du tag (un second clic sur le meme le deselectionne)
                tagSel = glisseId.equals(tagSel) ? null : glisseId;
                empSel = -1;
            }
            glisseId = null;
            glisse = false;
            return true;
        }
        return super.mouseReleased(mx, my, bouton);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_4 && tagSel != null) {
            placer(key - org.lwjgl.glfw.GLFW.GLFW_KEY_1, tagSel);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // pas d'assombrissement vanilla : notre degrade s'en charge
    }
}
