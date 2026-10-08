package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzField;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Banque : a gauche le solde en gros, un champ montant et les liens DEPOSER / RETIRER (retirer grise sans la
 * permission), le solde personnel ; a droite les 30 derniers mouvements (date courte · joueur · montant colore · motif).
 */
public final class BanqueTab extends Tab {
    private static final int LIGNE = 13, GAUCHE = 200;
    private HzField montant;
    private HzButton deposer, retirer;

    @Override
    protected void construire() {
        montant = new HzField(x, y + 48, 120, 18, 12, HzField.NOMBRE).accent(accent).remplacerAuFocus(true)
                .placeholder(tr("gui.banque.montant"));
        montant.setValue(ecran.brouillon("banque.montant", ""));
        montant.setResponder(v -> {
            ecran.garderBrouillon("banque.montant", v);
            majBoutons();
        });
        ecran.ajouter(montant);
        deposer = new HzButton(x, y + 72, 60, 14, Component.translatable("banque.deposer"), accent, () -> envoyerMontant(true)).look(HzButton.Look.LINK);
        retirer = new HzButton(x + 64, y + 72, 60, 14, Component.translatable("banque.retirer"), accent, () -> envoyerMontant(false)).look(HzButton.Look.LINK);
        ecran.ajouter(deposer);
        ecran.ajouter(retirer);
        majBoutons();
        if (s.jePeux(Perm.BANQUE_JOURNAL)) {
            Liste liste = new Liste(x + GAUCHE + 24, y + 14, w - GAUCHE - 24, h - 14);
            liste.accent(accent);
            liste.setContentHeight(s.journalBanque().size() * LIGNE);
            ecran.ajouter(liste);
        }
    }

    private long valeur() {
        try {
            return montant.getValue().isEmpty() ? 0 : Long.parseLong(montant.getValue());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void majBoutons() {
        long v = valeur();
        deposer.active = s.jePeux(Perm.BANQUE_DEPOSER) && v > 0 && v <= s.monSolde();
        retirer.active = s.jePeux(Perm.BANQUE_RETIRER) && v > 0 && v <= s.banque();
    }

    private void envoyerMontant(boolean depot) {
        long v = valeur();
        if (v <= 0) return;
        Ui.son(Sons.BANQUE, 0.7f);
        envoyer(new Packets.BanqueC2S(depot, v));
        montant.setValue("");
        ecran.garderBrouillon("banque.montant", "");
        majBoutons();
    }

    @Override
    public void tick() {
        montant.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // solde du clan
        Ui.spaced(g, font, tr("banque.solde"), Ui.SMALL, x, y, Ui.argb(Ui.MUTED, 255), 1.0f);
        Ui.gros(g, font, Ui.jetons(s.banque(), Ui.BOLD), x, y + 12, 2f, Ui.argb(Ui.TEXT, 255));
        Ui.spaced(g, font, tr("gui.banque.montant"), Ui.SMALL, x, y + 40, Ui.argb(Ui.FAINT, 255), 0.8f);
        // solde perso
        g.drawString(font, Ui.in(Component.translatable("banque.votre_solde", Ui.jetons(s.monSolde(), Ui.SMALL)), Ui.SMALL), x, y + 94,
                Ui.argb(Ui.MUTED, 255), false);
        if (!s.jePeux(Perm.BANQUE_RETIRER)) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.banque.retrait_reserve"), x, y + 106, Ui.argb(Ui.FAINT, 255), false);
        }
        // separateur vertical doux
        g.fill(x + GAUCHE + 8, y, x + GAUCHE + 9, y + h, Ui.argb(0xFFFFFF, 14));
        // journal
        int jx = x + GAUCHE + 24;
        Ui.section(g, font, tr("banque.mouvements"), jx, y, w - GAUCHE - 24);
        if (!s.jePeux(Perm.BANQUE_JOURNAL)) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.banque.journal_reserve"), jx, y + 18, Ui.argb(Ui.FAINT, 255), false);
        } else if (s.journalBanque().isEmpty()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.banque.journal_vide"), jx, y + 18, Ui.argb(Ui.FAINT, 255), false);
        }
    }

    private final class Liste extends HzScroll {
        Liste(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            List<Mouvement> l = s.journalBanque();
            long now = ClientClans.maintenantServeur();
            int premiere = Math.max(0, getScroll() / LIGNE), derniere = Math.min(l.size(), (getScroll() + getHeight()) / LIGNE + 1);
            int wl = getWidth() - 2;
            for (int i = premiere; i < derniere; i++) {
                Mouvement m = l.get(i);
                int ly = getY() + i * LIGNE, ty = ly + 2;
                if (dans(mouseX, mouseY, getX(), ly, wl, LIGNE)) Ui.soft(g, getX(), ly, wl, LIGNE, Ui.argb(0xFFFFFF, 10));
                g.drawString(font, Ui.lit(Ui.dateCourte(m.quand(), now), Ui.SMALL), getX(), ty + 1, Ui.argb(Ui.FAINT, 255), false);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, m.nom(), Ui.FONT, 86), Ui.FONT), getX() + 36, ty, Ui.argb(Ui.TEXT, 230), false);
                int cm = m.montant() >= 0 ? Ui.OK : Ui.DANGER;
                Ui.right(g, font, Ui.jetonsSigne(m.montant(), Ui.FONT), getX() + 212, ty, Ui.argb(cm, 255));
                String motif = Component.translatable("banque.motif." + m.motif()).getString();
                if (motif.startsWith("banque.motif.")) motif = m.motif();
                g.drawString(font, Ui.lit(Ui.ellipsis(font, motif, Ui.SMALL, wl - 222), Ui.SMALL), getX() + 220, ty + 1, Ui.argb(Ui.MUTED, 255), false);
            }
        }
    }
}
