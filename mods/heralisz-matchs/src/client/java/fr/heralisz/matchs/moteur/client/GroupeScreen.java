package fr.heralisz.matchs.moteur.client;

import fr.heralisz.matchs.client.kit.Echelle;
import fr.heralisz.matchs.client.kit.HzButton;
import fr.heralisz.matchs.client.kit.HzField;
import fr.heralisz.matchs.client.kit.HzIcon;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.SonsUi;
import fr.heralisz.matchs.client.kit.Ui;
import fr.heralisz.matchs.moteur.api.Groupes;
import fr.heralisz.matchs.moteur.net.PaquetsMoteur;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Ecran leger du groupe ({@code /groupe}, MATCHS_SPEC §1.6) : membres (chef, en ligne), actions du chef (passer chef,
 * exclure), invitation par nom, invitations recues (accepter / refuser), quitter. Panneau de verre sur un voile
 * leger, echelle forcee, aucune fenetre de confirmation. Reconstruit a chaque nouvel etat ({@code GroupeS2C}).
 */
public class GroupeScreen extends Screen {
    private static final int PW = 300;
    private int px, py, ph;
    private int version = -1;
    private HzField champ;
    private String brouillon = "";

    public GroupeScreen() {
        super(Component.translatable("matchs.groupe.titre"));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        construire();
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
        if (champ != null) {
            champ.tick();
            brouillon = champ.getValue();
        }
        if (MoteurClient.versionGroupe() != version) {
            clearWidgets();
            construire();
        }
    }

    private static void envoyer(int action, String cible) {
        MatchsNet.versServeur(new PaquetsMoteur.GroupeC2S(action, cible));
    }

    private void construire() {
        version = MoteurClient.versionGroupe();
        PaquetsMoteur.GroupeS2C g = MoteurClient.groupe();
        List<PaquetsMoteur.MembreVue> membres = g == null ? List.of() : g.membres();
        List<PaquetsMoteur.InvitVue> invs = g == null ? List.of() : g.invitations();
        boolean chef = g == null || g.chef();
        int lignes = Math.max(1, membres.size());
        ph = 58 + lignes * 20 + (chef && membres.size() < 4 ? 46 : 0) + (invs.isEmpty() ? 0 : 22 + invs.size() * 22) + (membres.isEmpty() ? 0 : 26);
        px = width / 2 - PW / 2;
        py = Math.max(8, height / 2 - ph / 2);
        int acc = Ui.accent();
        addRenderableWidget(new HzIcon(px + PW - 22, py + 9, 12, Icons.CROIX, Component.translatable("matchs.groupe.fermer"), this::onClose));
        // membres : actions du chef sur les autres
        int y = py + 40;
        for (PaquetsMoteur.MembreVue m : membres) {
            if (g != null && g.chef() && !m.chef()) {
                String nom = m.nom();
                addRenderableWidget(new HzButton(px + PW - 112, y + 3, 50, 12, Component.translatable("matchs.groupe.action.chef"), acc,
                        () -> envoyer(Groupes.ACTION_CHEF, nom)).look(HzButton.Look.LINK));
                addRenderableWidget(new HzButton(px + PW - 58, y + 3, 44, 12, Component.translatable("matchs.groupe.action.exclure"), acc,
                        () -> envoyer(Groupes.ACTION_EXCLURE, nom)).look(HzButton.Look.LINK).danger());
            }
            y += 20;
        }
        if (membres.isEmpty()) y += 20;
        // inviter
        if (chef && membres.size() < 4) {
            y += 18;
            champ = new HzField(px + 16, y, PW - 120, 16, 16, s -> s.matches("[A-Za-z0-9_]*"));
            champ.placeholder(Component.translatable("matchs.groupe.pseudo").getString()).accent(acc);
            champ.setValue(brouillon);
            addRenderableWidget(champ);
            addRenderableWidget(new HzButton(px + PW - 96, y, 80, 16, Component.translatable("matchs.groupe.inviter"), acc, () -> {
                String n = champ.getValue().trim();
                if (!n.isEmpty()) {
                    envoyer(Groupes.ACTION_INVITER, n);
                    champ.setValue("");
                    brouillon = "";
                }
            }).look(HzButton.Look.PRIMARY));
            y += 28;
        } else {
            champ = null;
        }
        // invitations recues
        if (!invs.isEmpty()) {
            y += 22;
            for (PaquetsMoteur.InvitVue i : invs) {
                String de = i.de();
                addRenderableWidget(new HzButton(px + PW - 152, y, 76, 16, Component.translatable("matchs.groupe.accepter"), acc,
                        () -> envoyer(Groupes.ACTION_ACCEPTER, de)).look(HzButton.Look.PRIMARY));
                addRenderableWidget(new HzButton(px + PW - 66, y + 2, 50, 12, Component.translatable("matchs.groupe.refuser"), acc,
                        () -> envoyer(Groupes.ACTION_REFUSER, de)).look(HzButton.Look.LINK));
                y += 22;
            }
        }
        if (!membres.isEmpty()) {
            addRenderableWidget(new HzButton(px + 16, py + ph - 22, 140, 12, Component.translatable("matchs.groupe.quitter"), acc,
                    () -> envoyer(Groupes.ACTION_QUITTER, "")).look(HzButton.Look.LINK).danger());
        }
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        Ui.fondLeger(gg, width, height);
        Ui.glass(gg, px, py, PW, ph);
        Font font = this.font;
        PaquetsMoteur.GroupeS2C g = MoteurClient.groupe();
        List<PaquetsMoteur.MembreVue> membres = g == null ? List.of() : g.membres();
        List<PaquetsMoteur.InvitVue> invs = g == null ? List.of() : g.invitations();
        Ui.spaced(gg, font, Component.translatable("matchs.groupe.titre").getString(), Ui.BOLD, px + 16, py + 12, Ui.argb(Ui.TEXT, 255), 1.4f);
        Ui.chip(gg, font, membres.size() + "/4", px + 90, py + 11, Ui.accent());
        Ui.hairline(gg, px + 12, px + PW - 12, py + 30, 0xFFFFFF, 40, true, true);
        int y = py + 40;
        if (membres.isEmpty()) {
            gg.drawString(font, Ui.tr(Ui.FONT, "matchs.groupe.aucun"), px + 16, y + 5, Ui.argb(Ui.MUTED, 255), false);
            y += 20;
        }
        for (PaquetsMoteur.MembreVue m : membres) {
            Ui.soft(gg, px + 10, y, PW - 20, 18, Ui.argb(0xFFFFFF, 6));
            Ui.point(gg, px + 18, y + 7, m.enLigne() ? Ui.OK : Ui.FAINT, 255);
            gg.drawString(font, Ui.lit(m.nom(), Ui.BOLD), px + 28, y + 5, Ui.argb(m.enLigne() ? Ui.TEXT : Ui.MUTED, 255), false);
            if (m.chef()) {
                int nw = font.width(Ui.lit(m.nom(), Ui.BOLD));
                Ui.chip(gg, font, Component.translatable("matchs.groupe.chef").getString(), px + 34 + nw, y + 4, Ui.OR);
            }
            y += 20;
        }
        if (champ != null) {
            y += 4;
            Ui.section(gg, font, Component.translatable("matchs.groupe.section.inviter").getString(), px + 16, y, PW - 32);
            y += 42;
        }
        if (!invs.isEmpty()) {
            Ui.section(gg, font, Component.translatable("matchs.groupe.section.invitations").getString(), px + 16, y + 6, PW - 32);
            y += 22;
            for (PaquetsMoteur.InvitVue i : invs) {
                gg.drawString(font, Ui.lit(i.de(), Ui.BOLD), px + 16, y + 2, Ui.argb(Ui.TEXT, 255), false);
                gg.drawString(font, Ui.tr(Ui.SMALL, "matchs.groupe.expire", i.secondes()), px + 16, y + 12, Ui.argb(Ui.FAINT, 255), false);
                y += 22;
            }
        }
        super.render(gg, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (champ != null && champ.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            String n = champ.getValue().trim();
            if (!n.isEmpty()) {
                envoyer(Groupes.ACTION_INVITER, n);
                champ.setValue("");
                brouillon = "";
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        SonsUi.fermer(0.6f);
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // voile leger dessine dans render
    }
}
