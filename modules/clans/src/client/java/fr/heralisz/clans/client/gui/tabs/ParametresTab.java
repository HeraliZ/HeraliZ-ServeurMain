package fr.heralisz.clans.client.gui.tabs;

import fr.heralisz.clans.client.ClansClient;
import fr.heralisz.clans.client.ClientClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Hold;
import fr.heralisz.clans.client.gui.kit.HzArea;
import fr.heralisz.clans.client.gui.kit.HzButton;
import fr.heralisz.clans.client.gui.kit.HzField;
import fr.heralisz.clans.client.gui.kit.HzScroll;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Parametres : IDENTITE (nom, tag, description : champ + lien MODIFIER, ou « prochain changement dans 2 j 3 h »),
 * BANNIERE (apercu + MODIFIER -> atelier de banniere), DIRECTION (chef : transferer a un membre choisi dans une liste, maintenir
 * 1 s), DANGER (QUITTER LE CLAN maintenir 1 s ; DISSOUDRE chef, maintenir 1,5 s). Les champs sans permission sont grises.
 */
public final class ParametresTab extends Tab {
    private static final int GAUCHE = 248, DROITE_X = 272;
    private HzField nom, tag;
    private HzArea desc;
    private HzButton modifNom, modifTag, modifDesc;
    @Nullable
    private UUID cibleTransfert;
    private Hold transferer;
    private final List<ClanSnapshot.MembreVue> autres = new ArrayList<>();

    @Override
    protected void construire() {
        int fw = 170;
        // --- identite
        boolean pNom = s.jePeux(Perm.MODIFIER_NOM) && s.cdNomMs() <= 0;
        nom = new HzField(x, y + 22, fw, 16, Clan.NOM_MAX, HzField.TOUT).accent(accent).remplacerAuFocus(false);
        nom.setValue(ecran.brouillon("param.nom", s.nom()));
        nom.active = pNom;
        nom.setResponder(v -> {
            ecran.garderBrouillon("param.nom", v);
            nom.setErreur(v.equals(s.nom()) ? "" : cle(Clan.validerNom(v)));
            majBoutons();
        });
        ecran.ajouter(nom);
        modifNom = new HzButton(x + fw + 8, y + 23, 60, 14, Component.translatable("gui.action.modifier"), accent,
                () -> envoyerChamp(Packets.ParametreC2S.Champ.NOM, Clan.nettoyer(nom.getValue()))).look(HzButton.Look.LINK);
        modifNom.visible = pNom;                                     // sinon le lien chevauche le texte de delai
        ecran.ajouter(modifNom);

        boolean pTag = s.jePeux(Perm.MODIFIER_TAG) && s.cdTagMs() <= 0;
        tag = new HzField(x, y + 58, 60, 16, Clan.TAG_MAX, HzField.TAG).accent(accent).remplacerAuFocus(false);
        tag.transform(v -> v.toUpperCase(Locale.ROOT));
        tag.setValue(ecran.brouillon("param.tag", s.tag()));
        tag.active = pTag;
        tag.setResponder(v -> {
            ecran.garderBrouillon("param.tag", v);
            tag.setErreur(v.equals(s.tag()) ? "" : cle(Clan.validerTag(v)));
            majBoutons();
        });
        ecran.ajouter(tag);
        modifTag = new HzButton(x + fw + 8, y + 59, 60, 14, Component.translatable("gui.action.modifier"), accent,
                () -> envoyerChamp(Packets.ParametreC2S.Champ.TAG, tag.getValue().toUpperCase(Locale.ROOT))).look(HzButton.Look.LINK);
        modifTag.visible = pTag;
        ecran.ajouter(modifTag);

        boolean pDesc = s.jePeux(Perm.MODIFIER_DESCRIPTION) && s.cdDescMs() <= 0;
        desc = new HzArea(x, y + 94, fw, 36, Clan.DESC_MAX, Clan.DESC_LIGNES).accent(accent)
                .placeholder(tr("gui.creation.desc_invite"));
        desc.setValue(ecran.brouillon("param.desc", s.description()));
        desc.active = pDesc;
        desc.setResponder(v -> {
            ecran.garderBrouillon("param.desc", v);
            desc.setErreur(cle(Clan.validerDescription(v)));
            majBoutons();
        });
        ecran.ajouter(desc);
        modifDesc = new HzButton(x + fw + 8, y + 95, 60, 14, Component.translatable("gui.action.modifier"), accent,
                () -> envoyerChamp(Packets.ParametreC2S.Champ.DESCRIPTION, Clan.nettoyerDescription(desc.getValue()))).look(HzButton.Look.LINK);
        modifDesc.visible = pDesc;
        ecran.ajouter(modifDesc);
        majBoutons();

        // --- danger
        int dy = y + h - 20;
        if (!s.jeSuisChef()) {
            Hold quitter = new Hold(x, dy, 120, 18, Component.translatable("gui.action.quitter"), 1000,
                    () -> envoyer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.QUITTER)));
            ecran.ajouter(quitter);
        } else {
            Hold dissoudre = new Hold(x, dy, 120, 18, Component.translatable("gui.action.dissoudre"), 1500,
                    () -> envoyer(new Packets.SimpleC2S(Packets.SimpleC2S.Genre.DISSOUDRE)));
            ecran.ajouter(dissoudre);
        }

        // --- banniere : MODIFIER ouvre l'atelier (meme pendant le delai : il l'affiche et grise ENREGISTRER)
        boolean pBan = s.jePeux(Perm.MODIFIER_BANNIERE);
        HzButton modifBan = new HzButton(x + DROITE_X + 56, y + 16, 60, 14, Component.translatable("gui.action.modifier"), accent,
                () -> ClansClient.ouvrir("banniere")).look(HzButton.Look.LINK);
        modifBan.visible = pBan;
        ecran.ajouter(modifBan);

        // --- direction (chef) ; la cible choisie survit a une reconstruction (brouillon) tant qu'elle est encore membre
        if (s.jeSuisChef()) {
            autres.clear();
            for (ClanSnapshot.MembreVue m : s.membres()) if (!m.uuid().equals(s.moi())) autres.add(m);
            String cible = ecran.brouillon("param.cible", "");
            cibleTransfert = null;
            for (ClanSnapshot.MembreVue m : autres) if (m.uuid().toString().equals(cible)) cibleTransfert = m.uuid();
            autres.sort((a, b) -> {
                int c = Integer.compare(a.grade().ordinal(), b.grade().ordinal());
                return c != 0 ? c : a.nom().compareToIgnoreCase(b.nom());
            });
            ListeMembres liste = new ListeMembres(x + DROITE_X, y + 108, w - DROITE_X, 60);
            liste.accent(accent);
            liste.setContentHeight(autres.size() * 12);
            ecran.ajouter(liste);
            // assez large pour « Transferer a <pseudo> » ; un libelle trop long est resserre puis coupe par Hold
            transferer = new Hold(x + DROITE_X, y + 174, Math.min(190, w - DROITE_X), 16, Component.translatable("gui.action.transferer"), 1000, () -> {
                if (cibleTransfert != null) envoyer(new Packets.ActionMembreC2S(Packets.ActionMembreC2S.Genre.TRANSFERER, cibleTransfert));
                ecran.garderBrouillon("param.cible", null);
            }).couleur(accent);
            transferer.active = cibleTransfert != null;
            ecran.ajouter(transferer);
        }
    }

    private static String cle(@Nullable String k) {
        return k == null ? "" : Component.translatable(k).getString();
    }

    private void majBoutons() {
        modifNom.active = nom.active && !nom.getValue().equals(s.nom()) && Clan.validerNom(nom.getValue()) == null;
        modifTag.active = tag.active && !tag.getValue().equalsIgnoreCase(s.tag()) && Clan.validerTag(tag.getValue()) == null;
        modifDesc.active = desc.active && !desc.getValue().equals(s.description()) && Clan.validerDescription(desc.getValue()) == null;
    }

    private void envoyerChamp(Packets.ParametreC2S.Champ champ, String valeur) {
        envoyer(new Packets.ParametreC2S(champ, valeur));
        ecran.garderBrouillon("param.nom", null);
        ecran.garderBrouillon("param.tag", null);
        ecran.garderBrouillon("param.desc", null);
    }

    @Override
    public void tick() {
        nom.tick();
        tag.tick();
        desc.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int fw = 170;
        Ui.section(g, font, tr("gui.parametres.identite"), x, y, GAUCHE - 16);
        libelle(g, tr("gui.creation.nom"), x, y + 13, s.cdNomMs(), s.jePeux(Perm.MODIFIER_NOM), fw);
        libelle(g, tr("gui.creation.tag"), x, y + 49, s.cdTagMs(), s.jePeux(Perm.MODIFIER_TAG), fw);
        libelle(g, tr("gui.creation.description"), x, y + 85, s.cdDescMs(), s.jePeux(Perm.MODIFIER_DESCRIPTION), fw);
        petit(g, tr("gui.parametres.cooldown_info"), x, y + 140, GAUCHE - 8, Ui.FAINT);
        // danger : l'aide a droite du bouton tient dans la colonne (deux lignes au plus, centrees sur le bouton)
        Ui.section(g, font, tr("gui.parametres.danger"), x, y + h - 36, GAUCHE - 16);
        String aide = tr(s.jeSuisChef() ? "gui.parametres.chef_quitter" : "gui.parametres.maintenir");
        List<FormattedCharSequence> lignes = font.split(Ui.lit(aide, Ui.SMALL), GAUCHE - 4 - 128);
        int n = Math.min(2, lignes.size());
        for (int i = 0; i < n; i++) {
            g.drawString(font, lignes.get(i), x + 128, y + h - 15 - (n - 1) * 5 + i * 10, Ui.argb(Ui.FAINT, 255), false);
        }
        // separateur
        g.fill(x + GAUCHE + 4, y, x + GAUCHE + 5, y + h, Ui.argb(0xFFFFFF, 14));
        // banniere
        int bx = x + DROITE_X;
        Ui.section(g, font, tr("gui.parametres.banniere"), bx, y, w - DROITE_X);
        BannerArt.draw(g, s.banniere(), bx, y + 14, 45, 60);
        long resteBanniere = ClientClans.reste(s.cdBanniereMs());
        int bw = w - DROITE_X - 56;
        if (resteBanniere > 0) {
            petit(g, tr("gui.parametres.prochain", Ui.duree(resteBanniere)), bx + 56, y + 34, bw, Ui.AMBRE);
        } else if (!s.jePeux(Perm.MODIFIER_BANNIERE)) {
            petit(g, tr("gui.parametres.sans_permission"), bx + 56, y + 34, bw, Ui.FAINT);
        } else {
            petit(g, tr("gui.atelier.parametres_info"), bx + 56, y + 34, bw, Ui.FAINT);
        }
        // direction
        if (s.jeSuisChef()) {
            Ui.section(g, font, tr("gui.parametres.direction"), bx, y + 86, w - DROITE_X);
            petit(g, tr("gui.parametres.transfert_info"), bx, y + 97, w - DROITE_X, Ui.FAINT);
            if (autres.isEmpty()) petit(g, tr("gui.parametres.seul"), bx, y + 112, w - DROITE_X, Ui.FAINT);
            ClanSnapshot.MembreVue c = cibleTransfert == null ? null : s.membre(cibleTransfert);
            if (transferer != null) transferer.setMessage(c == null ? Component.translatable("gui.action.transferer")
                    : Component.translatable("gui.action.transferer_a", c.nom()));
        }
    }

    /** Libelle d'un champ ; le delai (ambre) reste a droite du champ, « reserve aux grades autorises » (plus long) suit le libelle. */
    private void libelle(GuiGraphics g, String texte, int lx, int ly, long cd, boolean perm, int fw) {
        Ui.spaced(g, font, texte, Ui.SMALL, lx, ly, Ui.argb(Ui.MUTED, 255), 1.0f);
        if (cd > 0) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.parametres.prochain_court", Ui.duree(cd)), lx + fw + 8, ly + 11 + 2, Ui.argb(Ui.AMBRE, 255), false);
        } else if (!perm) {
            int tw = (int) Ui.spacedWidth(font, texte.toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f);
            petit(g, tr("gui.parametres.sans_permission"), lx + tw + 10, ly, GAUCHE - 4 - (tw + 10), Ui.FAINT);
        }
    }

    /** Petit texte coupe avec « … » a la largeur donnee (il ne traverse ni le separateur ni le bord du panneau). */
    private void petit(GuiGraphics g, String texte, int tx, int ty, int largeur, int rgb) {
        g.drawString(font, Ui.lit(Ui.ellipsis(font, texte, Ui.SMALL, largeur), Ui.SMALL), tx, ty, Ui.argb(rgb, 255), false);
    }

    /** Liste des membres pour le transfert : une ligne de 12 px, la cible choisie est surlignee. */
    private final class ListeMembres extends HzScroll {
        ListeMembres(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int wl = getWidth() - 2;
            for (int i = 0; i < autres.size(); i++) {
                ClanSnapshot.MembreVue m = autres.get(i);
                int ly = getY() + i * 12;
                boolean sel = m.uuid().equals(cibleTransfert), hover = dans(mouseX, mouseY, getX(), ly, wl, 12);
                if (sel) Ui.soft(g, getX(), ly, wl, 12, Ui.argb(accent, 40));
                else if (hover) Ui.soft(g, getX(), ly, wl, 12, Ui.argb(0xFFFFFF, 10));
                Ui.point(g, getX() + 2, ly + 4, m.enLigne() ? Ui.OK : Ui.FAINT, 255);
                g.drawString(font, Ui.lit(Ui.ellipsis(font, m.nom(), Ui.FONT, 110), Ui.FONT), getX() + 10, ly + 2, Ui.argb(Ui.TEXT, 255), false);
                g.drawString(font, Ui.tr(Ui.SMALL, m.grade().libelle()), getX() + 126, ly + 3, Ui.argb(couleurGrade(m.grade()), 255), false);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !dansZone(mx, my)) return false;
            int i = (int) ((my + getScroll() - getY()) / 12);
            if (i < 0 || i >= autres.size()) return false;
            Ui.son(Sons.UI_CLIC, 0.5f);
            UUID u = autres.get(i).uuid();
            cibleTransfert = u.equals(cibleTransfert) ? null : u;
            ecran.garderBrouillon("param.cible", cibleTransfert == null ? null : cibleTransfert.toString());
            if (transferer != null) transferer.active = cibleTransfert != null;
            return true;
        }
    }
}
