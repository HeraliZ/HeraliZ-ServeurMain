package fr.heralisz.matchs.clashwar.client;

import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.Hold;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Ecran INSCRIPTION du ClashWar (MATCHS_SPEC §2.2) : a gauche les membres connectes du clan (tete, nom, grade), a droite
 * les colonnes Equipe 1..N ; on glisse-depose un membre dans une equipe (ou hors d'une equipe pour l'en retirer), un
 * clic sur un membre libre le place dans la premiere equipe qui a de la place. Chaque equipe a 1 joueur au moins et
 * au plus la taille du format ; un joueur n'est que dans une equipe ; N &lt;= membres connectes. ENREGISTRER envoie la
 * repartition (le serveur revalide tout) ; RETIRER LE CLAN (maintenir) desinscrit. Sans la permission
 * CLASHWAR_INSCRIRE : lecture seule et « ME RETIRER » si l'on est inscrit.
 * <p>Brouillon garde a l'arrivee d'une mise a jour du serveur ; textes prepares aux changements (aucune allocation par
 * image hors survols).
 */
public class EcranInscription extends Screen {
    private CwPaquets.InscriptionS2C donnees;
    private final List<List<UUID>> brouillon = new ArrayList<>();
    private List<List<UUID>> serveur = List.of();
    private final Map<UUID, Component> noms = new HashMap<>();
    private final Map<UUID, Component> grades = new HashMap<>();
    private final List<Component> titresEquipes = new ArrayList<>();
    private long finMs;
    private String message = "";
    private boolean messageErreur;
    private long messageMs;
    private long secondeAffichee = -1;
    private String compte = "";
    private String sousTitre = "";
    /** Textes prepares (changements seulement). */
    private String titreMembres = "", titreEquipes = "", piedLecture = "", piedAide = "", texteAucun = "", titreEcran = "";
    private final Component deposer = Ui.lit(PeintreTableau.tr("clashwar.inscription.deposer"), Ui.SMALL);
    private final String nouvelle = PeintreTableau.tr("clashwar.inscription.nouvelle");
    private final List<Component> compteurs = new ArrayList<>();
    /** Etiquettes « É1 », « É2 »... de la liste des membres. */
    private final List<String> puces = new ArrayList<>();
    private Component pied = Component.empty();

    // geometrie
    private int px, py, pw, ph, lx, ly, lw, lh, ex, ey, ew, eh, carteL, carteH;
    private int defilement;
    // glisser-deposer
    @Nullable
    private UUID glisse;
    private int origine;
    private double appuiX, appuiY, sourisX, sourisY;
    private boolean glisserActif;

    private HzButton enregistrer;
    private Hold retirerClan, meRetirer;

    public EcranInscription(CwPaquets.InscriptionS2C p) {
        super(Component.translatable("clashwar.inscription.titre"));
        this.donnees = p;
        appliquer(p, true);
    }

    /** Mise a jour du serveur (brouillon garde s'il a ete modifie). */
    void recevoir(CwPaquets.InscriptionS2C p) {
        boolean modifie = !normaliser(brouillon).equals(serveur);
        this.donnees = p;
        appliquer(p, !modifie);
        if (minecraft != null) rebuildWidgets();
    }

    private void appliquer(CwPaquets.InscriptionS2C p, boolean prendreServeur) {
        finMs = Util.getMillis() + p.finDansMs();
        serveur = normaliser(p.equipes());
        noms.clear();
        grades.clear();
        for (CwPaquets.Membre m : p.membres()) {
            noms.put(m.uuid(), Ui.lit(m.nom(), Ui.FONT));
            grades.put(m.uuid(), Ui.tr(Ui.SMALL, m.grade()));
        }
        if (prendreServeur) {
            brouillon.clear();
            for (List<UUID> e : p.equipes()) brouillon.add(new ArrayList<>(e));
        } else {
            // membres partis : retires du brouillon
            for (List<UUID> e : brouillon) e.removeIf(u -> !noms.containsKey(u));
        }
        titresEquipes.clear();
        puces.clear();
        for (int i = 0; i < 32; i++) puces.add(PeintreTableau.tr("clashwar.inscription.puce", i + 1));
        for (int i = 0; i < 32; i++) titresEquipes.add(Ui.lit(PeintreTableau.tr("clashwar.inscription.equipe", i + 1).toUpperCase(Locale.ROOT), Ui.BOLD));
        sousTitre = PeintreTableau.tr("clashwar.inscription.sous_titre", "[" + p.tag() + "] " + p.nomClan(), p.format().toUpperCase(Locale.ROOT),
                p.clansInscrits(), p.equipesTotal());
        titreMembres = PeintreTableau.tr("clashwar.inscription.membres", p.membres().size());
        piedLecture = PeintreTableau.tr("clashwar.inscription.lecture_seule");
        piedAide = PeintreTableau.tr("clashwar.inscription.aide", p.taille());
        texteAucun = PeintreTableau.tr("clashwar.inscription.aucun");
        titreEcran = PeintreTableau.tr("clashwar.inscription.titre", p.edition());
        compteurs.clear();
        for (int i = 0; i <= p.taille(); i++) compteurs.add(Ui.lit(i + "/" + p.taille(), Ui.SMALL));
        if (!p.message().isEmpty()) {
            message = PeintreTableau.tr(p.message());
            messageErreur = p.erreur();
            messageMs = Util.getMillis();
        }
    }

    private static List<List<UUID>> normaliser(List<List<UUID>> l) {
        List<List<UUID>> r = new ArrayList<>();
        for (List<UUID> e : l) if (!e.isEmpty()) r.add(List.copyOf(e));
        return r;
    }

    // ================================================================== mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(600, width - 16);
        ph = Math.min(332, height - 10);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lx = px + 16;
        ly = py + 56;
        lw = 168;
        lh = ph - 56 - 40;
        ex = px + 200;
        ey = py + 70;
        ew = pw - 216;
        eh = ph - 70 - 40;
        carteL = (ew - 16) / 3;
        carteH = 24 + donnees.taille() * 17 + 4;
        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 14, Icons.CROIX, Component.translatable("clashwar.ecran.fermer"), this::onClose)
                .couleurs(Ui.MUTED, Ui.TEXT));
        int fy = py + ph - 30;
        int accent = donnees.couleur();
        if (donnees.peutModifier()) {
            enregistrer = addRenderableWidget(new HzButton(px + pw - 16 - 140, fy, 140, 18,
                    Component.translatable(serveur.isEmpty() ? "clashwar.inscription.inscrire" : "clashwar.inscription.enregistrer"), accent,
                    this::envoyer).look(HzButton.Look.PRIMARY));
            if (!serveur.isEmpty()) {
                retirerClan = addRenderableWidget(new Hold(px + pw - 16 - 140 - 8 - 130, fy, 130, 18,
                        Component.translatable("clashwar.inscription.retirer_clan"), 1200, () -> MatchsNet.versServeur(new CwPaquets.InscriptionC2S(List.of()))));
            } else {
                retirerClan = null;
            }
            meRetirer = null;
        } else {
            enregistrer = null;
            retirerClan = null;
            meRetirer = moiInscrit() ? addRenderableWidget(new Hold(px + pw - 16 - 130, fy, 130, 18,
                    Component.translatable("clashwar.inscription.me_retirer"), 1000, () -> MatchsNet.versServeur(new CwPaquets.RetraitC2S()))) : null;
        }
        majBoutons();
    }

    private boolean moiInscrit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (List<UUID> e : serveur) if (e.contains(mc.player.getUUID())) return true;
        return false;
    }

    private void majBoutons() {
        if (enregistrer != null) {
            List<List<UUID>> n = normaliser(brouillon);
            enregistrer.active = !n.isEmpty() && !n.equals(serveur) && Util.getMillis() < finMs;
        }
    }

    @Override
    public void tick() {
        majBoutons();
        long reste = Math.max(0, finMs - Util.getMillis()) / 1000;
        if (reste != secondeAffichee) {
            secondeAffichee = reste;
            compte = PeintreTableau.tr("clashwar.inscription.cloture", String.format(Locale.ROOT, "%d:%02d", reste / 60, reste % 60)).toUpperCase(Locale.ROOT);
        }
        titreEquipes = PeintreTableau.tr("clashwar.inscription.equipes", normaliser(brouillon).size(), donnees.taille());
        String texte;
        int couleur;
        if (!message.isEmpty() && Util.getMillis() - messageMs < 6000) {
            texte = message;
            couleur = messageErreur ? Ui.DANGER : Ui.OK;
        } else {
            texte = donnees.peutModifier() ? piedAide : piedLecture;
            couleur = Ui.FAINT;
        }
        pied = Ui.lit(texte, Ui.SMALL).withStyle(st -> st.withColor(couleur));
    }

    private void envoyer() {
        MatchsNet.versServeur(new CwPaquets.InscriptionC2S(normaliser(brouillon)));
    }

    // ================================================================== dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        int accent = donnees.couleur();
        // en-tete
        PeintreTableau.banniere(g, donnees.banniere(), px + 16, py + 10, 18, 27, (float) Minecraft.getInstance().getWindow().getGuiScale());
        Ui.spaced(g, font, titreEcran, Ui.BOLD, px + 42, py + 13, 0xFFFFFFFF, 1.4f);
        Ui.spaced(g, font, sousTitre, Ui.SMALL, px + 42, py + 28, Ui.argb(Ui.MUTED, 255), 0.8f);
        long reste = finMs - Util.getMillis();
        int cc = reste < 60_000 ? Ui.AMBRE : Ui.OK;
        float cw = Ui.spacedWidth(font, compte, Ui.SMALL, 0.8f);
        int chipX = (int) (px + pw - 34 - cw - 12);
        Ui.soft(g, chipX, py + 11, (int) cw + 12, 12, Ui.argb(cc, 30));
        Ui.softOutline(g, chipX + 1, py + 12, (int) cw + 10, 10, Ui.argb(cc, 110));
        Ui.spaced(g, font, compte, Ui.SMALL, chipX + 6, py + 14, Ui.argb(Ui.mix(cc, 0xFFFFFF, 0.4f), 255), 0.8f);
        Ui.hairline(g, px + 16, px + pw - 16, py + 46, 0xFFFFFF, 40, false, true);
        // membres
        Ui.section(g, font, titreMembres, lx, ly, lw);
        int y = ly + 14;
        for (CwPaquets.Membre m : donnees.membres()) {
            if (y + 16 > ly + lh) break;
            int eq = equipeDe(m.uuid());
            boolean survol = donnees.peutModifier() && mx >= lx && mx < lx + lw && my >= y && my < y + 16 && glisse == null;
            boolean enCours = m.uuid().equals(glisse) && glisserActif;
            if (survol) Ui.soft(g, lx, y, lw, 16, Ui.argb(0xFFFFFF, 12));
            int alpha = eq >= 0 || enCours ? 110 : 255;
            tete(g, m.uuid(), lx + 3, y + 2, 12, alpha);
            g.drawString(font, noms.get(m.uuid()), lx + 20, y + 4, Ui.argb(Ui.TEXT, alpha), false);
            if (eq >= 0) {
                Ui.chip(g, font, puces.get(Math.min(eq, puces.size() - 1)), lx + lw - 26, y + 2, accent);
            } else {
                Ui.right(g, font, grades.get(m.uuid()), lx + lw - 4, y + 5, Ui.argb(Ui.FAINT, 255));
            }
            y += 17;
        }
        if (donnees.membres().isEmpty()) Ui.spaced(g, font, texteAucun, Ui.SMALL, lx, ly + 18, Ui.argb(Ui.FAINT, 255), 0.8f);
        // equipes
        Ui.section(g, font, titreEquipes, ex, ly, ew);
        g.enableScissor(ex - 2, ey - 2, ex + ew + 2, ey + eh);
        int n = brouillon.size() + (peutAjouter() ? 1 : 0);
        for (int i = 0; i < n; i++) {
            int cx = ex + (i % 3) * (carteL + 8), cy = ey + (i / 3) * (carteH + 8) - defilement;
            if (cy > ey + eh || cy + carteH < ey) continue;
            if (i == brouillon.size()) carteAjout(g, cx, cy, mx, my);
            else carteEquipe(g, i, cx, cy, mx, my, accent);
        }
        g.disableScissor();
        // pied
        int fy = py + ph - 30;
        Ui.hairline(g, px + 16, px + pw - 16, fy - 8, 0xFFFFFF, 30, false, true);
        g.drawString(font, pied, px + 16, fy + 6, 0xFFFFFFFF, false);
        super.render(g, mx, my, pt);
        // fantome du joueur glisse
        if (glisse != null && glisserActif) {
            Component nom = noms.get(glisse);
            int w = 22 + (nom == null ? 30 : font.width(nom));
            int gx = (int) sourisX - 8, gy = (int) sourisY - 8;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            Ui.soft(g, gx, gy, w, 16, Ui.argb(Ui.VERRE, 235));
            Ui.softOutline(g, gx + 1, gy + 1, w - 2, 14, Ui.argb(accent, 220));
            tete(g, glisse, gx + 3, gy + 2, 12, 255);
            if (nom != null) g.drawString(font, nom, gx + 19, gy + 4, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }

    private void carteEquipe(GuiGraphics g, int i, int cx, int cy, int mx, int my, int accent) {
        List<UUID> e = brouillon.get(i);
        boolean cible = glisse != null && glisserActif && dans(mx, my, cx, cy, carteL, carteH) && (e.size() < donnees.taille() || origine == i);
        Ui.soft(g, cx, cy, carteL, carteH, Ui.argb(cible ? accent : 0xFFFFFF, cible ? 30 : 8));
        Ui.softOutline(g, cx + 1, cy + 1, carteL - 2, carteH - 2, cible ? Ui.argb(accent, 220) : Ui.argb(0xFFFFFF, e.isEmpty() ? 22 : 34));
        g.drawString(font, titresEquipes.get(Math.min(i, 31)), cx + 8, cy + 7, Ui.argb(e.isEmpty() ? Ui.FAINT : Ui.TEXT, 255), false);
        Ui.right(g, font, compteurs.get(Math.min(e.size(), compteurs.size() - 1)), cx + carteL - 8, cy + 8, Ui.argb(e.size() == donnees.taille() ? accent : Ui.MUTED, 255));
        for (int s = 0; s < donnees.taille(); s++) {
            int sy = cy + 22 + s * 17;
            if (s < e.size()) {
                UUID u = e.get(s);
                boolean enCours = u.equals(glisse) && glisserActif;
                boolean survol = donnees.peutModifier() && glisse == null && dans(mx, my, cx + 4, sy, carteL - 8, 16);
                if (survol) Ui.soft(g, cx + 4, sy, carteL - 8, 16, Ui.argb(0xFFFFFF, 12));
                tete(g, u, cx + 8, sy + 2, 12, enCours ? 90 : 255);
                Component nom = noms.get(u);
                if (nom != null) g.drawString(font, nom, cx + 25, sy + 4, Ui.argb(Ui.TEXT, enCours ? 90 : 255), false);
                if (survol) Icons.draw(g, Icons.CROIX, cx + carteL - 18, sy + 3, 10, Ui.DANGER, 220);
            } else {
                // emplacement vide
                for (int k = cx + 8; k < cx + carteL - 8; k += 6) g.fill(k, sy + 14, k + 3, sy + 15, Ui.argb(0xFFFFFF, 26));
                if (s == e.size() && donnees.peutModifier()) {
                    g.drawString(font, deposer, cx + 10, sy + 5, Ui.argb(Ui.FAINT, 255), false);
                }
            }
        }
    }

    private void carteAjout(GuiGraphics g, int cx, int cy, int mx, int my) {
        boolean survol = glisse == null && dans(mx, my, cx, cy, carteL, carteH);
        boolean cible = glisse != null && glisserActif && dans(mx, my, cx, cy, carteL, carteH);
        int c = survol || cible ? Ui.TEXT : Ui.FAINT;
        for (int k = cx + 2; k < cx + carteL - 2; k += 6) {
            g.fill(k, cy, k + 3, cy + 1, Ui.argb(0xFFFFFF, 40));
            g.fill(k, cy + carteH - 1, k + 3, cy + carteH, Ui.argb(0xFFFFFF, 40));
        }
        for (int k = cy + 2; k < cy + carteH - 2; k += 6) {
            g.fill(cx, k, cx + 1, k + 3, Ui.argb(0xFFFFFF, 40));
            g.fill(cx + carteL - 1, k, cx + carteL, k + 3, Ui.argb(0xFFFFFF, 40));
        }
        Icons.draw(g, Icons.PLUS, cx + carteL / 2 - 6, cy + carteH / 2 - 14, 12, c, 255);
        Ui.spacedCentered(g, font, nouvelle, Ui.SMALL, cx + carteL / 2f, cy + carteH / 2f + 2, Ui.argb(c, 255), 0.8f);
    }

    /** Tete du joueur (peau connue du client, sinon peau par defaut). */
    private void tete(GuiGraphics g, UUID u, int x, int y, int taille, int alpha) {
        ResourceLocation peau;
        PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(u) : null;
        peau = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(u);
        g.setColor(1f, 1f, 1f, alpha / 255f);
        PlayerFaceRenderer.draw(g, peau, x, y, taille);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ================================================================== souris
    private boolean peutAjouter() {
        return donnees.peutModifier() && brouillon.size() < Math.max(1, donnees.membres().size()) && brouillon.size() < 32;
    }

    private int equipeDe(UUID u) {
        for (int i = 0; i < brouillon.size(); i++) if (brouillon.get(i).contains(u)) return i;
        return -1;
    }

    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Index de la carte sous la souris (brouillon.size() = carte d'ajout), ou -1. */
    private int carteSous(double mx, double my) {
        if (!dans(mx, my, ex, ey, ew, eh)) return -1;
        int n = brouillon.size() + (peutAjouter() ? 1 : 0);
        for (int i = 0; i < n; i++) {
            int cx = ex + (i % 3) * (carteL + 8), cy = ey + (i / 3) * (carteH + 8) - defilement;
            if (dans(mx, my, cx, cy, carteL, carteH)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (super.mouseClicked(mx, my, bouton)) return true;
        if (bouton != 0 || !donnees.peutModifier()) return false;
        // membre de la liste
        int y = ly + 14;
        for (CwPaquets.Membre m : donnees.membres()) {
            if (y + 16 > ly + lh) break;
            if (dans(mx, my, lx, y, lw, 16)) {
                commencer(m.uuid(), equipeDe(m.uuid()), mx, my);
                return true;
            }
            y += 17;
        }
        // carte d'equipe
        int c = carteSous(mx, my);
        if (c >= 0 && c == brouillon.size()) {
            brouillon.add(new ArrayList<>());
            SonsUi.clic(0.6f);
            return true;
        }
        if (c >= 0) {
            int cx = ex + (c % 3) * (carteL + 8), cy = ey + (c / 3) * (carteH + 8) - defilement;
            List<UUID> e = brouillon.get(c);
            for (int s = 0; s < e.size(); s++) {
                int sy = cy + 22 + s * 17;
                if (!dans(mx, my, cx + 4, sy, carteL - 8, 16)) continue;
                if (mx >= cx + carteL - 20) {
                    e.remove(s);
                    SonsUi.clic(0.5f);
                } else {
                    commencer(e.get(s), c, mx, my);
                }
                return true;
            }
        }
        return false;
    }

    private void commencer(UUID u, int depuis, double mx, double my) {
        glisse = u;
        origine = depuis;
        appuiX = sourisX = mx;
        appuiY = sourisY = my;
        glisserActif = false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int bouton, double dx, double dy) {
        if (glisse != null) {
            sourisX = mx;
            sourisY = my;
            if (!glisserActif && (Math.abs(mx - appuiX) > 3 || Math.abs(my - appuiY) > 3)) glisserActif = true;
            return true;
        }
        return super.mouseDragged(mx, my, bouton, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int bouton) {
        for (var c : children()) c.mouseReleased(mx, my, bouton);
        if (glisse == null) return false;
        UUID u = glisse;
        glisse = null;
        if (glisserActif) {
            glisserActif = false;
            int c = carteSous(mx, my);
            if (c == brouillon.size() && peutAjouter()) {
                retirer(u);
                List<UUID> l = new ArrayList<>();
                l.add(u);
                brouillon.add(l);
                SonsUi.clic(0.6f);
            } else if (c >= 0 && c < brouillon.size()) {
                if (c != origine && brouillon.get(c).size() < donnees.taille()) {
                    retirer(u);
                    brouillon.get(c).add(u);
                    SonsUi.clic(0.6f);
                }
            } else if (dans(mx, my, lx, ly, lw, lh)) {
                retirer(u);
                SonsUi.clic(0.5f);
            }
        } else if (origine < 0) {
            // simple clic sur un membre libre : premiere equipe qui a de la place (ou nouvelle equipe)
            for (List<UUID> e : brouillon) {
                if (e.size() < donnees.taille()) {
                    e.add(u);
                    SonsUi.clic(0.6f);
                    return true;
                }
            }
            if (peutAjouter()) {
                List<UUID> l = new ArrayList<>();
                l.add(u);
                brouillon.add(l);
                SonsUi.clic(0.6f);
            }
        }
        majBoutons();
        return true;
    }

    private void retirer(UUID u) {
        for (List<UUID> e : brouillon) e.remove(u);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (dans(mx, my, ex, ey, ew, eh)) {
            int n = brouillon.size() + (peutAjouter() ? 1 : 0);
            int rangs = (n + 2) / 3, total = rangs * (carteH + 8);
            defilement = (int) Math.max(0, Math.min(Math.max(0, total - eh), defilement - delta * 18));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        SonsUi.fermer(0.5f);
        super.removed();
    }

    // ================================================================== banc d'essai
    /** Banc : simule un glisser du membre {@code membre} vers l'equipe {@code equipe} (-1 = liste). */
    void glisserBanc(int membre, int equipe) {
        if (membre < 0 || membre >= donnees.membres().size()) return;
        UUID u = donnees.membres().get(membre).uuid();
        retirer(u);
        if (equipe >= 0) {
            while (brouillon.size() <= equipe) brouillon.add(new ArrayList<>());
            if (brouillon.get(equipe).size() < donnees.taille()) brouillon.get(equipe).add(u);
        }
        majBoutons();
    }

    /** Banc : centre (interface) du bouton « Inscrire / Enregistrer », ou null s'il n'existe pas. */
    @javax.annotation.Nullable
    int[] boutonEnregistrerBanc() {
        return enregistrer == null ? null : new int[]{enregistrer.getX() + enregistrer.getWidth() / 2, enregistrer.getY() + enregistrer.getHeight() / 2};
    }

    /** Banc : position (interface) du centre de la ligne du membre {@code i} et de la carte d'equipe {@code e}. */
    int[] positions(int i, int e) {
        return new int[]{lx + lw / 2, ly + 14 + i * 17 + 8, ex + (e % 3) * (carteL + 8) + carteL / 2, ey + (e / 3) * (carteH + 8) + carteH / 2};
    }
}
