package fr.heraliz.crac.client.group;

import java.util.List;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.C2SInviteResponse;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COpenInvites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static fr.heraliz.crac.client.kit.Dessin.*;

/** The invitations to join a group: who invites, to which group (in its colour), Accept or Decline. */
public class InvitationsScreen extends EcranGroupe {
    private static final float W = 300;
    private static final float CARD = 40;
    private static final float GAP = 4;
    private static final float TOP = 40;
    private final List<S2COpenInvites.InviteEntry> invites;
    /** Cards that fit on the screen. */
    private int shown;
    /** Défilement de la liste à la molette (cartes au-delà de celles qui tiennent). */
    private float scroll;

    public InvitationsScreen(List<S2COpenInvites.InviteEntry> invites) {
        super(Component.translatable("crac.gui.invitations"));
        this.invites = invites;
    }

    public static void open(List<S2COpenInvites.InviteEntry> invites) {
        if (invites.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof InvitationsScreen) {
                mc.setScreen(null);
            }
        } else {
            Minecraft.getInstance().setScreen(new InvitationsScreen(invites));
        }
    }

    @Override
    protected void initCanevas() {
        this.shown = Math.min(this.invites.size(), Math.max(1, (int) ((this.ch - 40 - TOP - 14) / (CARD + GAP))));
        this.placer(W, TOP + this.shown * (CARD + GAP) - GAP + 14);
        this.scroll = Mth.clamp(this.scroll, 0, this.scrollMax());
    }

    private float listTop() {
        return this.y0 + TOP;
    }

    private float listBottom() {
        return this.listTop() + this.shown * (CARD + GAP) - GAP;
    }

    private float scrollMax() {
        return Math.max(0, (this.invites.size() - this.shown) * (CARD + GAP));
    }

    private float cardY(int i) {
        return this.listTop() + i * (CARD + GAP) - this.scroll;
    }

    /** Carte visible (au moins en partie) dans la liste découpée. */
    private boolean visible(int i) {
        float y = this.cardY(i);
        return y + CARD > this.listTop() && y < this.listBottom();
    }

    private float[] acceptRect(int i) {
        String s = Component.translatable("crac.gui.accept").getString();
        float w = this.largeurPetitBouton(s, false);
        float[] d = this.declineRect(i);
        return new float[]{d[0] - 4 - w, d[1], w, 14};
    }

    private float[] declineRect(int i) {
        String s = Component.translatable("crac.gui.decline").getString();
        float w = this.largeurPetitBouton(s, false);
        return new float[]{this.x0 + W - 13.33F - 6 - w, this.cardY(i) + (CARD - 14) / 2, w, 14};
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, ACCENT);
        this.entete(g, -1, Component.translatable("crac.gui.invitations").getString(), BLANC,
            Component.translatable("crac.gui.invitations_count", this.invites.size()).getString());
        float x = this.x0 + 13.33F, w = W - 26.67F;
        // toutes les invitations, découpées à la hauteur du panneau ; molette pour voir les suivantes
        this.decoupe(g, x - 2, this.listTop(), x + w + 2, this.listBottom());
        boolean dansListe = my >= this.listTop() && my < this.listBottom();
        for (int i = 0; i < this.invites.size(); i++) {
            if (!this.visible(i)) {
                continue;
            }
            float y = this.cardY(i);
            S2COpenInvites.InviteEntry e = this.invites.get(i);
            rect(g, x, y, x + w, y + CARD, a(BLANC, 8));
            contour(g, x, y, w, CARD, this.px, a(BLANC, 26));
            rect(g, x, y + 5, x + 1.33F, y + CARD - 5, a(e.groupColor() & 0xFFFFFF, 255));
            this.tete(g, e.inviterId(), x + 8, y + (CARD - 16) / 2, 16);
            float tx = x + 30;
            float[] acc = this.acceptRect(i);
            float room = acc[0] - 6 - tx;
            String inviter = Ui.ellipsis(this.font, e.inviterName(), GRAS_9, Math.round(room));
            float iw = largeur(this.font, inviter, GRAS_9);
            texte(g, this.font, lit(inviter, GRAS_9), tx, y + 13 - 5.44F, a(BLANC, 255));
            String invited = Ui.ellipsis(this.font, " " + Component.translatable("crac.gui.invited_you").getString(), MINI, Math.round(room - iw));
            texte(g, this.font, lit(invited, MINI), tx + iw, y + 13 - 4.21F, a(ATTENUE, 255));
            String group = Ui.ellipsis(this.font, e.groupName() + "  [" + e.groupTag() + "]", MOYEN_GRAS, Math.round(room));
            texte(g, this.font, lit(group, MOYEN_GRAS), tx, y + 26 - 4.7F, a(mix(e.groupColor() & 0xFFFFFF, BLANC, 0.45F), 255));
            float[] d = this.declineRect(i);
            this.petitBouton(g, acc[0], acc[1], acc[2], acc[3], Component.translatable("crac.gui.accept").getString(), VERT, dansListe && dans(mx, my, acc[0], acc[1], acc[2], acc[3]), false);
            this.petitBouton(g, d[0], d[1], d[2], d[3], Component.translatable("crac.gui.decline").getString(), 0xFF6B6B, dansListe && dans(mx, my, d[0], d[1], d[2], d[3]), false);
        }
        g.disableScissor();
        float max = this.scrollMax();
        if (max > 0) {
            // barre de défilement fine à droite des cartes
            float piste = this.listBottom() - this.listTop();
            float pouce = Math.max(12, piste * piste / (piste + max));
            float ty = this.listTop() + (piste - pouce) * this.scroll / max;
            rect(g, this.x0 + W - 7, ty, this.x0 + W - 6, ty + pouce, a(BLANC, 56));
        }
    }

    @Override
    protected boolean molette(double mx, double my, double delta) {
        this.scroll = Mth.clamp(this.scroll - (float) delta * (CARD + GAP) / 2, 0, this.scrollMax());
        return true;
    }

    @Override
    protected boolean clic(double mx, double my, int button) {
        if (my < this.listTop() || my >= this.listBottom()) {
            return false;
        }
        for (int i = 0; i < this.invites.size(); i++) {
            if (!this.visible(i)) {
                continue;
            }
            float[] acc = this.acceptRect(i);
            float[] d = this.declineRect(i);
            if (dans(mx, my, acc[0], acc[1], acc[2], acc[3])) {
                NetworkHandler.CHANNEL.sendToServer(new C2SInviteResponse(this.invites.get(i).groupId(), true));
                return true;
            }
            if (dans(mx, my, d[0], d[1], d[2], d[3])) {
                NetworkHandler.CHANNEL.sendToServer(new C2SInviteResponse(this.invites.get(i).groupId(), false));
                return true;
            }
        }
        return false;
    }
}
