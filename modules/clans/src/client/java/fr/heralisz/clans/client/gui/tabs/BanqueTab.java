package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.HeraliszClans;
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
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Banque : a gauche le solde en gros, un champ montant et les liens DEPOSER / RETIRER (retirer grise sans la
 * permission), le solde personnel ; a droite les 30 derniers mouvements (date courte · joueur · montant colore · motif).
 * Le solde est dans une police a sa vraie taille, pose sur un point de l'ecran (regle du 23/10 : jamais de texte agrandi).
 */
public final class BanqueTab extends Tab {
    private static final int LIGNE = 13, GAUCHE = 200;
    /** Solde du clan : gras 22 a sa vraie taille, avec le glyphe « jeton » de l'economie a la meme echelle (ui_montant). */
    private static final ResourceLocation SOLDE = HeraliszClans.id("ui_montant");
    private HzField montant;
    private HzButton deposer, retirer, coffre;

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
        // coffre du clan (3.5.0) : une ligne de cases, ouverte par le serveur (écran de coffre de Minecraft)
        coffre = new HzButton(x, y + 124, 124, 16, Component.translatable("gui.banque.coffre"), accent, () -> {
            Ui.son(Sons.BANQUE, 0.5f);
            envoyer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.COFFRE));
        });
        coffre.active = s.jePeux(Perm.BANQUE_DEPOSER) || s.jePeux(Perm.BANQUE_RETIRER);
        ecran.ajouter(coffre);
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
        // meme milieu des capitales que l'ancien texte 11 agrandi 2 fois depuis y + 12 ; glyphe « jeton » garde (sinon le mot)
        net(g, Ui.jetonsGlyphe(s.banque(), SOLDE), x, y + 12 + 2f * Ui.milieu(11) - Ui.milieu(22), Ui.argb(Ui.TEXT, 255));
        Ui.spaced(g, font, tr("gui.banque.montant"), Ui.SMALL, x, y + 40, Ui.argb(Ui.FAINT, 255), 0.8f);
        // solde perso
        g.drawString(font, Ui.in(Component.translatable("banque.votre_solde", Ui.jetons(s.monSolde(), Ui.SMALL)), Ui.SMALL), x, y + 94,
                Ui.argb(Ui.MUTED, 255), false);
        // textes de gauche coupes avant le separateur (x + GAUCHE + 8)
        if (!s.jePeux(Perm.BANQUE_RETIRER)) {
            g.drawString(font, Ui.lit(Ui.ellipsis(font, tr("gui.banque.retrait_reserve"), Ui.SMALL, GAUCHE), Ui.SMALL), x, y + 106,
                    Ui.argb(Ui.FAINT, 255), false);
        }
        g.drawString(font, Ui.lit(Ui.ellipsis(font, tr("gui.banque.coffre_aide"), Ui.SMALL, GAUCHE), Ui.SMALL), x, y + 144,
                Ui.argb(Ui.FAINT, 255), false);
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

    /** Texte dessine a sa vraie taille, pose sur un point entier de l'ecran ({@link Ui#placer}) ; (x, y) = point de dessin. */
    private void net(GuiGraphics g, Component c, float x, float y, int argb) {
        g.pose().pushPose();
        Ui.placer(g, x, y);
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
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
