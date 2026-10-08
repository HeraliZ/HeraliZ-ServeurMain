package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Membres : tableau (NOM · GRADE · KILLS · MORTS · K/D · TEMPS · JETONS · VU), lignes de 16, point vert/gris, grade
 * colore, defilement. Au survol d'une ligne : fond blanc 5 % et, a droite, les actions que le joueur a le droit de
 * faire sur ce membre (promouvoir, retrograder, exclure en maintenant 0,8 s la souris appuyee sur la croix ; sortir de
 * la croix ou relacher annule).
 */
public final class MembresTab extends Tab {
    private static final int LIGNE = 16, HOLD_MS = 800;
    // colonnes (x relatif) : bord gauche ou bord droit pour les nombres
    private static final int X_POINT = 2, X_NOM = 12, X_GRADE = 118, R_KILLS = 222, R_MORTS = 262, R_KD = 302, R_TEMPS = 352,
            R_JETONS = 414, X_VU = 424, X_ACTIONS = 484;

    private Liste liste;
    private final List<ClanSnapshot.MembreVue> tries = new ArrayList<>();

    @Override
    protected void construire() {
        tries.clear();
        tries.addAll(s.membres());
        // chef d'abord, puis par grade, puis en ligne, puis nom
        tries.sort((a, b) -> {
            int c = Integer.compare(a.grade().ordinal(), b.grade().ordinal());
            if (c != 0) return c;
            c = Boolean.compare(b.enLigne(), a.enLigne());
            if (c != 0) return c;
            return a.nom().compareToIgnoreCase(b.nom());
        });
        liste = new Liste(x, y + 14, w, h - 14);
        liste.accent(accent);
        liste.setContentHeight(tries.size() * LIGNE);
        ecran.ajouter(liste);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int c = Ui.argb(Ui.MUTED, 255);
        float sp = 0.8f;
        Ui.spaced(g, font, tr("gui.colonne.nom"), Ui.SMALL, x + X_NOM, y, c, sp);
        Ui.spaced(g, font, tr("gui.colonne.grade"), Ui.SMALL, x + X_GRADE, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.colonne.kills"), Ui.SMALL, x + R_KILLS, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.colonne.morts"), Ui.SMALL, x + R_MORTS, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.colonne.kd"), Ui.SMALL, x + R_KD, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.colonne.temps"), Ui.SMALL, x + R_TEMPS, y, c, sp);
        Ui.spacedRight(g, font, tr("gui.colonne.jetons"), Ui.SMALL, x + R_JETONS, y, c, sp);
        Ui.spaced(g, font, tr("gui.colonne.vu"), Ui.SMALL, x + X_VU, y, c, sp);
        Ui.hairline(g, x, x + w, y + 10, 0xFFFFFF, 40, false, true);
    }

    // ------------------------------------------------------------------ droits
    private boolean peutPromouvoir(ClanSnapshot.MembreVue m) {
        Grade sup = m.grade().superieur();
        return s.jePeux(Perm.PROMOUVOIR) && !m.uuid().equals(s.moi()) && sup != null && s.monGrade().domine(m.grade())
                && (s.jeSuisChef() || s.monGrade().ordinal() <= sup.ordinal());
    }

    private boolean peutRetrograder(ClanSnapshot.MembreVue m) {
        return s.jePeux(Perm.RETROGRADER) && !m.uuid().equals(s.moi()) && m.grade().inferieur() != null && s.monGrade().domine(m.grade());
    }

    private boolean peutExclure(ClanSnapshot.MembreVue m) {
        return s.jePeux(Perm.EXCLURE) && !m.uuid().equals(s.moi()) && s.monGrade().domine(m.grade());
    }

    // ------------------------------------------------------------------ liste
    private final class Liste extends HzScroll {
        @Nullable
        private UUID holdCible;
        private long holdDepuis;
        private boolean holdFait;

        Liste(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        /** Rectangles des trois actions d'une ligne (x absolu, y en coordonnees de contenu). */
        private int actionX(int i) {
            return getX() + X_ACTIONS + i * 14;
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            long now = ClientClans.maintenantServeur();
            int premiere = Math.max(0, getScroll() / LIGNE), derniere = Math.min(tries.size(), (getScroll() + getHeight()) / LIGNE + 1);
            List<Component> info = null;
            // le maintien d'exclusion ne survit qu'au survol continu de SA croix (souris sortie, ligne hors champ,
            // relachement hors liste : annule), comme le widget Hold
            boolean croixTenue = false;
            for (int i = premiere; i < derniere; i++) {
                ClanSnapshot.MembreVue m = tries.get(i);
                int ly = getY() + i * LIGNE;
                boolean hover = dans(mouseX, mouseY, getX(), ly, getWidth() - 2, LIGNE);
                boolean moi = m.uuid().equals(s.moi());
                if (hover) Ui.soft(g, getX(), ly, getWidth() - 2, LIGNE, Ui.argb(0xFFFFFF, 13));
                else if (moi) Ui.soft(g, getX(), ly, getWidth() - 2, LIGNE, Ui.argb(accent, 14));
                int ty = ly + 4;
                Ui.point(g, getX() + X_POINT, ly + 6, m.enLigne() ? Ui.OK : Ui.FAINT, m.enLigne() ? 255 : 160);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, m.nom(), moi ? Ui.BOLD : Ui.FONT, 100), moi ? Ui.BOLD : Ui.FONT), getX() + X_NOM, ty,
                        Ui.argb(Ui.TEXT, 255), false);
                if (m.grade() == Grade.CHEF) Icons.draw(g, Icons.CHEF, getX() + X_GRADE, ty - 1, 10, Ui.OR, 255);
                g.drawString(font, Ui.tr(Ui.FONT, m.grade().libelle()), getX() + X_GRADE + (m.grade() == Grade.CHEF ? 12 : 0), ty,
                        Ui.argb(couleurGrade(m.grade()), 255), false);
                int num = Ui.argb(Ui.TEXT, 220);
                Ui.right(g, font, Ui.lit(Ui.nombre(m.stats().kills), Ui.FONT), getX() + R_KILLS, ty, num);
                Ui.right(g, font, Ui.lit(Ui.nombre(m.stats().morts), Ui.FONT), getX() + R_MORTS, ty, num);
                Ui.right(g, font, Ui.lit(Ui.ratio(m.stats().kd()), Ui.FONT), getX() + R_KD, ty, num);
                Ui.right(g, font, Ui.lit(Ui.tempsJeu(m.stats().tempsJeuTicks), Ui.FONT), getX() + R_TEMPS, ty, Ui.argb(Ui.MUTED, 255));
                Ui.right(g, font, Ui.jetons(m.solde(), Ui.FONT), getX() + R_JETONS, ty, num);
                String vu = m.enLigne() ? tr("gui.en_ligne_court") : Ui.ilYA(Math.max(0, now - m.vuLe()));
                g.drawString(font, Ui.lit(vu, Ui.SMALL), getX() + X_VU, ty + 1, Ui.argb(m.enLigne() ? Ui.OK : Ui.FAINT, 255), false);
                // actions au survol
                if (hover) {
                    boolean[] droits = {peutPromouvoir(m), peutRetrograder(m), peutExclure(m)};
                    int[] icones = {Icons.HAUT, Icons.BAS, Icons.CROIX};
                    String[] cles = {"gui.action.promouvoir", "gui.action.retrograder", "gui.action.exclure_maintenir"};
                    for (int a = 0; a < 3; a++) {
                        if (!droits[a]) continue;
                        int ax = actionX(a), ay = ly + 3;
                        boolean hi = dans(mouseX, mouseY, ax - 1, ly, 14, LIGNE);
                        int rgb = a == 2 ? Ui.DANGER : hi ? Ui.TEXT : Ui.MUTED;
                        if (hi) Ui.soft(g, ax - 2, ay - 2, 14, 14, Ui.argb(a == 2 ? Ui.DANGER : 0xFFFFFF, 24));
                        Icons.draw(g, icones[a], ax, ay, 10, rgb, hi || a == 2 ? 255 : 180);
                        if (hi) info = List.of(Ui.tr(Ui.FONT, cles[a]));
                        // progression du maintien pour l'exclusion
                        if (a == 2 && hi && holdCible != null && holdCible.equals(m.uuid())) {
                            croixTenue = true;
                            float p = Math.min(1, (Util.getMillis() - holdDepuis) / (float) HOLD_MS);
                            g.fill(ax - 2, ly + LIGNE - 2, ax - 2 + Math.round(14 * p), ly + LIGNE - 1, Ui.argb(Ui.DANGER, 240));
                            if (p >= 1 && !holdFait) {
                                holdFait = true;
                                Ui.son(Sons.UI_CLIC, 0.8f);
                                envoyer(new Packets.ActionMembreC2S(Packets.ActionMembreC2S.Genre.EXCLURE, m.uuid()));
                                holdCible = null;
                            }
                        }
                    }
                }
                if (i < tries.size() - 1) Ui.hairline(g, getX(), getX() + getWidth() - 2, ly + LIGNE - 1, 0xFFFFFF, 14, false, true);
            }
            if (!croixTenue) {
                holdCible = null;
                holdFait = false;
            }
            if (info != null) ecran.infobulle(info);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !dansZone(mx, my)) return false;
            int i = (int) ((my + getScroll() - getY()) / LIGNE);
            if (i < 0 || i >= tries.size()) return false;
            ClanSnapshot.MembreVue m = tries.get(i);
            for (int a = 0; a < 3; a++) {
                if (!dans(mx, my + getScroll(), actionX(a) - 1, getY() + i * LIGNE, 14, LIGNE)) continue;
                switch (a) {
                    case 0 -> {
                        if (!peutPromouvoir(m)) return false;
                        Ui.son(Sons.UI_CLIC, 0.6f);
                        envoyer(new Packets.ActionMembreC2S(Packets.ActionMembreC2S.Genre.PROMOUVOIR, m.uuid()));
                    }
                    case 1 -> {
                        if (!peutRetrograder(m)) return false;
                        Ui.son(Sons.UI_CLIC, 0.6f);
                        envoyer(new Packets.ActionMembreC2S(Packets.ActionMembreC2S.Genre.RETROGRADER, m.uuid()));
                    }
                    default -> {
                        if (!peutExclure(m)) return false;
                        holdCible = m.uuid();
                        holdDepuis = Util.getMillis();
                        holdFait = false;
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            holdCible = null;
            holdFait = false;
            return super.mouseReleased(mx, my, button);
        }
    }
}
