package fr.heralisz.matchs.duels.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.HzScroll;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.duels.DuelsConfig;
import fr.heralisz.matchs.duels.net.PaquetsDuels;
import fr.heralisz.matchs.duels.net.Vues;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ecran DUEL ({@code /duel}) : a gauche les joueurs en ligne (tete, nom, groupe, « en match ») et mes statistiques de
 * duel ; a droite FORMAT (1V1, 2V2 seulement si les deux camps sont des groupes de 2), MODE (recherche et destruction,
 * match a mort), CARTE (Nuketown), resume et DEFIER. Un defi recu s'affiche en bandeau (ACCEPTER / REFUSER) ; un defi
 * envoye remplace DEFIER par « en attente · 24 s » et ANNULER. Le serveur revalide tout.
 * <p>Panneau min(560, l-32) x min(320, h-24) centre, echelle forcee (canevas 640 x 360).
 */
public class DuelScreen extends Screen implements EcranDuels {
    private static final int W = 560, H = 320;
    private Vues.EcranDuel donnees;
    @Nullable
    private UUID selection;
    private String format = Vues.FORMAT_1V1, mode = Vues.MODE_RD, carte = DuelsConfig.CARTE_DEFAUT;
    private int px, py, pw, ph, dy;
    private Liste liste;
    private int scrollGarde = -1;
    private Component resume = Component.empty(), statut = Component.empty(), banniere = Component.empty();
    private int derniereSeconde = -1;
    private long recuMs;

    public DuelScreen(Vues.EcranDuel e) {
        super(Component.translatable("duel.ecran.titre"));
        this.donnees = e;
        this.recuMs = Util.getMillis();
        if (e.preselection() != null) selection = e.preselection();
        if (e.sortant() != null) {
            format = e.sortant().format();
            mode = e.sortant().mode();
        }
        if (!e.cartes().isEmpty()) carte = e.cartes().get(0).id();
    }

    /** Nouvelles donnees du serveur (liste, defis) : l'ecran se reconstruit en gardant les choix. */
    void mettreAJour(Vues.EcranDuel e) {
        this.donnees = e;
        this.recuMs = Util.getMillis();
        if (e.preselection() != null) selection = e.preselection();
        scrollGarde = liste == null ? -1 : liste.getScroll();
        rebuildWidgets();
    }

    @Nullable
    private Vues.Candidat candidat() {
        if (selection == null) return null;
        for (Vues.Candidat c : donnees.candidats()) if (c.id().equals(selection)) return c;
        return null;
    }

    private boolean deuxContreDeuxPossible() {
        Vues.Candidat c = candidat();
        return donnees.tailleGroupe() == 2 && c != null && c.groupeDe2() && !c.memeGroupe();
    }

    @Nullable
    private Vues.CarteVue carteVue() {
        for (Vues.CarteVue c : donnees.cartes()) if (c.id().equals(carte)) return c;
        return donnees.cartes().isEmpty() ? null : donnees.cartes().get(0);
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        libelles();
        pw = Math.min(W, width - 32);
        ph = Math.min(H, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int accent = Ui.accent();
        Vues.DefiVue in = donnees.entrant() != null ? donnees.entrant() : DuelsClient.defi();
        dy = in != null ? 30 : 0;
        if (!deuxContreDeuxPossible() && Vues.FORMAT_2V2.equals(format)) format = Vues.FORMAT_1V1;
        Vues.CarteVue cv = carteVue();
        if (cv != null && !cv.modes().contains(mode) && !cv.modes().isEmpty()) mode = cv.modes().get(0);
        addRenderableWidget(new HzIcon(px + pw - 24, py + 9, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
        // defi recu
        if (in != null) {
            int bx = px + pw - 16 - 92 - 70;
            addRenderableWidget(new HzButton(bx, py + 37, 86, 16, Component.translatable("duel.ecran.accepter"), Ui.OK,
                    () -> repondre(in.id(), true)).look(HzButton.Look.PRIMARY));
            addRenderableWidget(new HzButton(bx + 96, py + 41, 60, 10, Component.translatable("duel.ecran.refuser"), accent,
                    () -> repondre(in.id(), false)).look(HzButton.Look.LINK));
        }
        // liste des joueurs
        int ly = py + 54 + dy, lh = ph - 54 - dy - 64;
        liste = addRenderableWidget(new Liste(px + 16, ly, 200, Math.max(40, lh)));
        liste.setContentHeight(donnees.candidats().size() * 18);
        if (scrollGarde >= 0) liste.setScroll(scrollGarde);
        // colonne de droite
        int rx = px + 232, rw = pw - 248;
        int y = py + 52 + dy;
        Option o1 = addRenderableWidget(new Option(rx, y, 70, 20, Component.literal("1V1"), List.of(), accent, () -> choisir("format", Vues.FORMAT_1V1)));
        Option o2 = addRenderableWidget(new Option(rx + 76, y, 70, 20, Component.literal("2V2"), List.of(), accent, () -> choisir("format", Vues.FORMAT_2V2)));
        o1.choisie(Vues.FORMAT_1V1.equals(format));
        o2.choisie(Vues.FORMAT_2V2.equals(format));
        o2.active = deuxContreDeuxPossible();
        if (cv != null) {
            o1.active = cv.formats().isEmpty() || cv.formats().contains(Vues.FORMAT_1V1);
            o2.active &= cv.formats().isEmpty() || cv.formats().contains(Vues.FORMAT_2V2);
        }
        y += 44;
        int mw = (rw - 8) / 2;
        String[] modes = {Vues.MODE_RD, Vues.MODE_MAM};
        for (int i = 0; i < 2; i++) {
            String m = modes[i];
            List<Component> desc = List.of(Ui.tr(Ui.SMALL, "duel.mode." + m + ".l1"), Ui.tr(Ui.SMALL, "duel.mode." + m + ".l2"));
            Option o = addRenderableWidget(new Option(rx + i * (mw + 8), y, mw, 44, Component.translatable("duel.mode." + m), desc, accent,
                    () -> choisir("mode", m)));
            o.choisie(m.equals(mode));
            o.active = cv == null || cv.modes().isEmpty() || cv.modes().contains(m);
        }
        y += 68;
        int cx = rx;
        for (Vues.CarteVue c : donnees.cartes()) {
            Option o = addRenderableWidget(new Option(cx, y, 110, 34, Component.literal(c.nom(Ui.french())),
                    List.of(Ui.tr(Ui.SMALL, "duel.ecran.carte_info")), accent, () -> choisir("carte", c.id())));
            o.choisie(c.id().equals(carte));
            cx += 118;
        }
        // pied
        int fy = py + ph - 34;
        addRenderableWidget(new HzButton(rx, fy + 5, 90, 10, Component.translatable("duel.ecran.mes_classes"), accent,
                () -> MatchsNet.versServeur(new PaquetsDuels.OuvrirC2S(PaquetsDuels.OuvrirC2S.CLASSES, null))).look(HzButton.Look.LINK));
        Vues.DefiVue out = donnees.sortant();
        if (out != null) {
            addRenderableWidget(new HzButton(px + pw - 16 - 60, fy + 5, 60, 10, Component.translatable("duel.ecran.annuler"), accent,
                    () -> repondre(out.id(), false)).look(HzButton.Look.LINK).danger());
        } else {
            HzButton defier = addRenderableWidget(new HzButton(px + pw - 16 - 130, fy, 130, 20, Component.translatable("duel.ecran.defier"), accent,
                    this::defier).look(HzButton.Look.PRIMARY));
            Vues.Candidat c = candidat();
            defier.active = c != null && !c.occupe() && !c.memeGroupe() && cv != null;
        }
        majTextes();
    }

    private void choisir(String quoi, String valeur) {
        switch (quoi) {
            case "format" -> format = valeur;
            case "mode" -> mode = valeur;
            default -> carte = valeur;
        }
        scrollGarde = liste == null ? -1 : liste.getScroll();
        rebuildWidgets();
    }

    private void defier() {
        Vues.Candidat c = candidat();
        if (c == null) return;
        if (minecraft != null && minecraft.getConnection() != null) MatchsNet.versServeur(new PaquetsDuels.DefierC2S(c.id(), format, mode, carte));
    }

    private void repondre(int id, boolean accepte) {
        if (minecraft != null && minecraft.getConnection() != null) MatchsNet.versServeur(new PaquetsDuels.ReponseDefiC2S(id, accepte));
        if (accepte) onClose();
    }

    /** Textes qui changent (resume, compte a rebours) : recalcules a chaque changement ou chaque seconde. */
    private void majTextes() {
        Vues.Candidat c = candidat();
        Vues.CarteVue cv = carteVue();
        String nomCarte = cv == null ? "—" : cv.nom(Ui.french());
        resume = c == null ? Ui.tr(Ui.FONT, "duel.ecran.choisir_joueur")
                : Ui.tr(Ui.FONT, "duel.ecran.resume", c.nom(), format.toUpperCase(Locale.ROOT), Component.translatable("duel.mode." + mode), nomCarte);
        long ecoule = Util.getMillis() - recuMs;
        Vues.DefiVue out = donnees.sortant();
        statut = out == null ? Component.empty()
                : Ui.tr(Ui.FONT, "duel.ecran.en_attente", out.defie(), Math.max(0, (out.resteMs() - ecoule) / 1000));
        Vues.DefiVue in = donnees.entrant() != null ? donnees.entrant() : DuelsClient.defi();
        banniere = in == null ? Component.empty()
                : Ui.tr(Ui.FONT, "duel.ecran.defi_recu", in.defiant(), in.format().toUpperCase(Locale.ROOT), Component.translatable("duel.mode." + in.mode()),
                in.carte(), Math.max(0, (in.resteMs() - ecoule) / 1000));
    }

    @Override
    public void tick() {
        int s = (int) ((Util.getMillis() - recuMs) / 1000);
        if (s != derniereSeconde) {
            derniereSeconde = s;
            majTextes();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        Font f = font;
        Ui.spaced(g, f, sTitre, Ui.BOLD, px + 16, py + 11, Ui.argb(Ui.TEXT, 255), 1.6f);
        g.drawString(f, sousTitre, px + 70, py + 13, Ui.argb(Ui.MUTED, 255), false);
        Ui.hairline(g, px + 8, px + pw - 8, py + 28, 0xFFFFFF, 40, true, true);
        if (dy > 0) {
            Ui.soft(g, px + 16, py + 34, pw - 32, 22, Ui.argb(Ui.OR, 26));
            Ui.softOutline(g, px + 17, py + 35, pw - 34, 20, Ui.argb(Ui.OR, 110));
            g.drawString(f, banniere, px + 26, py + 41, Ui.argb(Ui.TEXT, 255), false);
        }
        int y = py + 40 + dy;
        Ui.section(g, f, sJoueurs, px + 16, y, 200);
        int rx = px + 232, rw = pw - 248;
        Ui.section(g, f, sFormat, rx, y, rw);
        if (!deuxContreDeuxPossible()) g.drawString(f, info2v2, rx + 156, y + 19, Ui.argb(Ui.FAINT, 255), false);
        Ui.section(g, f, sMode, rx, y + 44, rw);
        Ui.section(g, f, sCarte, rx, y + 112, rw);
        // statistiques
        int ty = py + ph - 52, tw = 62;
        Ui.tile(g, f, px + 16, ty, tw, 38, vVict, sVict);
        Ui.tile(g, f, px + 16 + tw + 7, ty, tw, 38, vDef, sDef);
        Ui.tile(g, f, px + 16 + 2 * (tw + 7), ty, tw, 38, vRatio, sRatio);
        // pied
        int fy = py + ph - 34;
        Ui.hairline(g, rx, px + pw - 16, fy - 8, 0xFFFFFF, 30, false, true);
        g.drawString(f, resume, rx, fy - 22, Ui.argb(Ui.MUTED, 255), false);
        if (donnees.sortant() != null) Ui.right(g, f, statut, px + pw - 86, fy + 6, Ui.argb(Ui.AMBRE, 255));
        if (donnees.candidats().isEmpty()) g.drawString(f, personne, px + 22, py + 60 + dy, Ui.argb(Ui.FAINT, 255), false);
        super.render(g, mx, my, pt);
    }

    /** Libelles fixes, calcules a la construction (aucun calcul de texte par image). */
    private String sTitre = "", sJoueurs = "", sFormat = "", sMode = "", sCarte = "", sVict = "", sDef = "", sRatio = "", vVict = "", vDef = "",
            vRatio = "", sEnMatch = "", sVotreGroupe = "", sGroupe = "";
    private Component sousTitre = Component.empty(), info2v2 = Component.empty(), personne = Component.empty();

    private void libelles() {
        sTitre = Component.translatable("duel.ecran.titre").getString();
        sJoueurs = Component.translatable("duel.ecran.joueurs").getString();
        sFormat = Component.translatable("duel.ecran.format").getString();
        sMode = Component.translatable("duel.ecran.mode").getString();
        sCarte = Component.translatable("duel.ecran.carte").getString();
        sVict = Component.translatable("duel.ecran.victoires").getString();
        sDef = Component.translatable("duel.ecran.defaites").getString();
        sRatio = Component.translatable("duel.ecran.ratio").getString();
        Vues.StatsVue st = donnees.stats();
        vVict = Ui.nombre(st.victoires());
        vDef = Ui.nombre(st.defaites());
        vRatio = Ui.ratio(st.morts() == 0 ? st.eliminations() : st.eliminations() / (double) st.morts());
        sEnMatch = Component.translatable("duel.ecran.en_match").getString();
        sVotreGroupe = Component.translatable("duel.ecran.votre_groupe").getString();
        sGroupe = Component.translatable("duel.ecran.groupe").getString();
        sousTitre = Ui.tr(Ui.SMALL, "duel.ecran.sous_titre");
        info2v2 = Ui.tr(Ui.SMALL, "duel.ecran.2v2_info");
        personne = Ui.tr(Ui.SMALL, "duel.ecran.personne");
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
    public boolean mouseReleased(double mx, double my, int b) {
        for (var c : children()) c.mouseReleased(mx, my, b);
        return true;
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.6f);
        super.onClose();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public String journal() {
        StringBuilder sb = new StringBuilder("panneau ").append(px).append(',').append(py).append(' ').append(pw).append('x').append(ph)
                .append(" liste ").append(liste.getX()).append(',').append(liste.getY()).append(" lignes 18 selection ").append(selection)
                .append(" format ").append(format).append(" mode ").append(mode).append(" carte ").append(carte);
        return sb.toString();
    }

    // ------------------------------------------------------------------ liste des joueurs
    private final class Liste extends HzScroll {
        Liste(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mx, int my, float pt) {
            Font f = font;
            int y = getY();
            List<Vues.Candidat> l = donnees.candidats();
            for (int i = 0; i < l.size(); i++, y += 18) {
                if (y + 18 < getY() + getScroll() || y > getY() + getScroll() + getHeight()) continue;
                Vues.Candidat c = l.get(i);
                boolean sel = c.id().equals(selection);
                boolean hover = mx >= getX() && mx < getX() + getWidth() - 4 && my >= y && my < y + 18;
                boolean dispo = !c.occupe() && !c.memeGroupe();
                if (sel) Ui.soft(g, getX(), y, getWidth() - 4, 17, Ui.argb(Ui.accent(), 40));
                else if (hover && dispo) Ui.soft(g, getX(), y, getWidth() - 4, 17, Ui.argb(0xFFFFFF, 12));
                tete(g, c, getX() + 3, y + 3);
                int a = dispo ? 255 : 110;
                g.drawString(f, Ui.lit(c.nom(), sel ? Ui.BOLD : Ui.FONT), getX() + 20, y + 5, Ui.argb(Ui.TEXT, a), false);
                int xr = getX() + getWidth() - 8;
                if (c.occupe()) Ui.spacedRight(g, f, sEnMatch, Ui.SMALL, xr, y + 6, Ui.argb(Ui.FAINT, 255), 0.6f);
                else if (c.memeGroupe()) Ui.spacedRight(g, f, sVotreGroupe, Ui.SMALL, xr, y + 6, Ui.argb(Ui.FAINT, 255), 0.6f);
                else if (c.groupeDe2()) Ui.spacedRight(g, f, sGroupe, Ui.SMALL, xr, y + 6, Ui.argb(Ui.OK, 220), 0.6f);
            }
        }

        private void tete(GuiGraphics g, Vues.Candidat c, int x, int y) {
            PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(c.id()) : null;
            if (info != null) {
                ResourceLocation skin = info.getSkinLocation();
                PlayerFaceRenderer.draw(g, skin, x, y, 12);
            } else {
                Ui.soft(g, x, y, 12, 12, Ui.argb(0xFFFFFF, 20));
                Ui.centered(g, font, Ui.lit(c.nom().isEmpty() ? "?" : c.nom().substring(0, 1).toUpperCase(Locale.ROOT), Ui.SMALL), x + 6, y + 3,
                        Ui.argb(Ui.MUTED, 255));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!dansZone(mx, my) || b != 0) return false;
            int i = (int) ((my + getScroll() - getY()) / 18);
            List<Vues.Candidat> l = donnees.candidats();
            if (i < 0 || i >= l.size()) return true;
            Vues.Candidat c = l.get(i);
            if (c.occupe() || c.memeGroupe()) return true;
            selection = c.id();
            SonsUi.clic(0.5f);
            scrollGarde = getScroll();
            Minecraft.getInstance().execute(DuelScreen.this::rebuildWidgets);
            return true;
        }
    }
}
