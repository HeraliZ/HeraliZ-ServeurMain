package fr.heraliz.crac.client.ui;

import java.util.List;
import fr.heraliz.crac.client.group.EcranGroupe;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.C2SAccesBase;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CAccesBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Armoire de la base, onglets Accès et Base (CRac 1.10.0, maquette validée le 18/10) ; l'onglet Entretien est l'écran du
 * stock ({@link ClaimUpkeepScreen}). Accès : le propriétaire cherche un joueur (connectés, ou déconnecté par son pseudo
 * exact) et lui donne l'accès, ou le retire ; les autres voient la liste. Tout est décidé par le serveur, qui renvoie la
 * liste à chaque changement.
 */
public class EcranAccesBase extends EcranGroupe {
    private static final float W = 300, H = 292, ROW = 18;
    private static final int TROUVES_VUS = 4;
    /** Attente après la dernière lettre tapée avant de chercher (ticks). */
    private static final int ATTENTE = 5;

    private S2CAccesBase etat;
    private boolean ongletBase;
    private String recherche = "";
    private int attente = -1;
    private int defile = 0;

    private EcranAccesBase(S2CAccesBase etat) {
        super(Component.translatable("crac.acces.titre"));
        this.etat = etat;
        this.recherche = etat.recherche();
    }

    /** Réponse du serveur : met l'écran ouvert à jour, sinon l'ouvre. */
    public static void recevoir(S2CAccesBase m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EcranAccesBase e && e.etat.pos().equals(m.pos())) {
            e.etat = m;
            e.defile = Mth.clamp(e.defile, 0, Math.max(0, m.ont().size() - e.lignesVues()));
        } else {
            mc.setScreen(new EcranAccesBase(m));
        }
    }

    /** Depuis l'onglet Entretien : demande l'onglet Accès au serveur. */
    public static void demander(BlockPos pos) {
        NetworkHandler.CHANNEL.sendToServer(new C2SAccesBase(pos, C2SAccesBase.Action.OUVRIR, "", null));
    }

    private void envoyer(C2SAccesBase.Action a, java.util.UUID cible) {
        NetworkHandler.CHANNEL.sendToServer(new C2SAccesBase(this.etat.pos(), a, this.recherche, cible));
    }

    @Override
    protected void initCanevas() {
        this.placer(W, H);
    }

    @Override
    public void tick() {
        if (this.attente > 0 && --this.attente == 0) {
            this.attente = -1;
            this.envoyer(C2SAccesBase.Action.CHERCHER, null);
        }
    }

    // ------------------------------------------------------------------------------------------------ mise en page

    private float gx() {
        return this.x0 + 13.33F;
    }

    private float gw() {
        return W - 26.67F;
    }

    /** Onglets : Entretien, Accès, Base (x, y, w, h de chacun). */
    private float[][] onglets() {
        String[] noms = {t("crac.acces.onglet.entretien"), t("crac.acces.onglet.acces"), t("crac.acces.onglet.base")};
        float[][] r = new float[3][];
        float x = this.gx();
        for (int i = 0; i < 3; i++) {
            float w = this.largeurPetitBouton(noms[i], false) + 8;
            r[i] = new float[]{x, this.y0 + 38, w, 14};
            x += w + 4;
        }
        return r;
    }

    private float champY() {
        return this.y0 + 72;
    }

    private float trouvesY() {
        return this.champY() + 19;
    }

    /** Haut de la liste « Ont l'accès ». */
    private float listeY() {
        return this.etat.gerer() ? this.trouvesY() + TROUVES_VUS * ROW + 14 : this.y0 + 72;
    }

    private float listeBas() {
        return this.y0 + H - 34;
    }

    private int lignesVues() {
        return Math.max(1, (int) ((this.listeBas() - this.listeY()) / ROW));
    }

    private static String t(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    // ------------------------------------------------------------------------------------------------ dessin

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, VERT);
        String sous = this.etat.protection() > 0
            ? t("crac.acces.sous_protegee", this.etat.base(), duree(this.etat.protection()))
            : t("crac.acces.sous", this.etat.base());
        this.entete(g, -1, t("crac.acces.titre"), BLANC, sous);
        float[][] o = this.onglets();
        String[] noms = {t("crac.acces.onglet.entretien"), t("crac.acces.onglet.acces"), t("crac.acces.onglet.base")};
        for (int i = 0; i < 3; i++) {
            boolean actif = i == (this.ongletBase ? 2 : 1);
            this.petitBouton(g, o[i][0], o[i][1], o[i][2], o[i][3], noms[i], actif ? VERT : -1, dans(mx, my, o[i][0], o[i][1], o[i][2], o[i][3]), false);
        }
        rect(g, this.gx(), this.y0 + 58, this.gx() + this.gw(), this.y0 + 58 + this.px, a(BLANC, 20));
        if (this.ongletBase) {
            this.renduBase(g);
        } else {
            this.renduAcces(g, mx, my);
        }
    }

    private void renduAcces(GuiGraphics g, float mx, float my) {
        float x = this.gx(), w = this.gw();
        if (this.etat.gerer()) {
            this.section(g, t("crac.acces.ajouter"), x, this.y0 + 64, w);
            this.champ(g, x, this.champY(), w, 15.33F, this.recherche, t("crac.acces.chercher"), true);
            List<S2CAccesBase.Ligne> tr = this.etat.trouves();
            if (tr.isEmpty() && !this.recherche.isEmpty() && this.attente < 0) {
                texte(g, this.font, lit(t("crac.acces.aucun"), MINI), x + 2, this.trouvesY() + 5 - 4.21F, a(ATTENUE, 255));
            } else if (this.recherche.isEmpty()) {
                texte(g, this.font, lit(t("crac.acces.aide"), MINI), x + 2, this.trouvesY() + 5 - 4.21F, a(PALE, 255));
            }
            for (int i = 0; i < Math.min(TROUVES_VUS, tr.size()); i++) {
                S2CAccesBase.Ligne l = tr.get(i);
                float y = this.trouvesY() + i * ROW;
                String b = t("crac.acces.donner");
                this.ligne(g, mx, my, l, y, b, VERT);
            }
        }
        String titre = t("crac.acces.ont", String.valueOf(this.etat.ont().size()));
        this.section(g, titre, x, this.listeY() - 8, w);
        List<S2CAccesBase.Ligne> ont = this.etat.ont();
        int vues = this.lignesVues();
        int fin = Math.min(ont.size(), this.defile + vues);
        for (int i = this.defile; i < fin; i++) {
            S2CAccesBase.Ligne l = ont.get(i);
            float y = this.listeY() + (i - this.defile) * ROW;
            this.ligne(g, mx, my, l, y, this.etat.gerer() && l.role() == S2CAccesBase.ACCES ? t("crac.acces.retirer") : null, ROUGE);
        }
        if (ont.size() > vues) {
            float piste = vues * ROW;
            float pouce = Math.max(12, piste * vues / ont.size());
            float ty = this.listeY() + (piste - pouce) * this.defile / Math.max(1, ont.size() - vues);
            rect(g, this.x0 + W - 6, ty, this.x0 + W - 5, ty + pouce, a(BLANC, 56));
        }
        rect(g, x, this.y0 + H - 30, x + w, this.y0 + H - 30 + this.px, a(BLANC, 20));
        texte(g, this.font, lit(Ui.ellipsis(this.font, t("crac.acces.pied1"), MINI, Math.round(w)), MINI), x, this.y0 + H - 24 - 4.21F, a(ATTENUE, 255));
        texte(g, this.font, lit(Ui.ellipsis(this.font, t("crac.acces.pied2"), MINI, Math.round(w)), MINI), x, this.y0 + H - 14 - 4.21F, a(ATTENUE, 255));
    }

    /** Une ligne de joueur : tête, pseudo, point vert s'il est connecté, rôle ou bouton à droite. */
    private void ligne(GuiGraphics g, float mx, float my, S2CAccesBase.Ligne l, float y, String bouton, int couleur) {
        float x = this.gx(), w = this.gw();
        rect(g, x, y + 1, x + w, y + ROW - 1, a(BLANC, 10));
        this.tete(g, l.id(), x + 4, y + 4, 10);
        float droite = x + w - 4;
        if (bouton != null) {
            float bw = this.largeurPetitBouton(bouton, false);
            float bx = droite - bw;
            this.petitBouton(g, bx, y + 3, bw, ROW - 6, bouton, couleur, dans(mx, my, bx, y + 3, bw, ROW - 6), false);
            droite = bx - 6;
        } else if (l.role() != S2CAccesBase.ACCES) {
            String role = t(l.role() == S2CAccesBase.PROPRIETAIRE ? "crac.acces.role.proprietaire" : "crac.acces.role.groupe");
            float rw = largeurCaps(this.font, role, MINI_GRAS, 0.52F);
            caps(g, this.font, role, MINI_GRAS, droite - rw, y + ROW / 2 - 4.21F, 0.52F, a(l.role() == S2CAccesBase.PROPRIETAIRE ? OR : ATTENUE, 255));
            droite -= rw + 6;
        }
        String etat = l.enLigne() ? "" : t("crac.acces.hors_ligne");
        float ew = etat.isEmpty() ? 0 : largeur(this.font, etat, MINI) + 6;
        String nom = Ui.ellipsis(this.font, l.nom(), GRAS_9, Math.round(droite - (x + 20) - ew));
        texte(g, this.font, lit(nom, GRAS_9), x + 20, y + ROW / 2 - 5.44F, a(BLANC, 255));
        float nx = x + 20 + largeur(this.font, nom, GRAS_9) + 4;
        if (l.enLigne()) {
            disque(g, nx + 1.5F, y + ROW / 2, 1.6F, a(VERT, 255));
        } else {
            texte(g, this.font, lit(etat, MINI), nx, y + ROW / 2 - 4.21F, a(PALE, 255));
        }
    }

    private void renduBase(GuiGraphics g) {
        float x = this.gx(), w = this.gw();
        float y = this.y0 + 70;
        this.section(g, t("crac.acces.onglet.base"), x, y - 4, w);
        y += 8;
        String[][] infos = {
            {t("crac.acces.base.groupe"), this.etat.base()},
            {t("crac.acces.base.niveau"), t("crac.acces.base.niveau_val", String.valueOf(this.etat.tier()), String.valueOf(this.etat.rayon()))},
            {t("crac.acces.base.protection"), this.etat.protection() > 0 ? t("crac.acces.base.protegee", duree(this.etat.protection())) : t("crac.acces.base.pillable")},
            {t("crac.acces.base.acces"), String.valueOf(this.etat.ont().size())},
        };
        for (String[] l : infos) {
            texte(g, this.font, lit(l[0], MOYEN), x, y, a(ATTENUE, 255));
            String v = Ui.ellipsis(this.font, l[1], MOYEN_GRAS, Math.round(w * 0.55F));
            texte(g, this.font, lit(v, MOYEN_GRAS), x + w - largeur(this.font, v, MOYEN_GRAS), y, a(BLANC, 255));
            y += 16;
            rect(g, x, y - 4, x + w, y - 4 + this.px, a(BLANC, 14));
        }
        y += 6;
        texte(g, this.font, lit(Ui.ellipsis(this.font, t("crac.acces.base.aide1"), MINI, Math.round(w)), MINI), x, y, a(PALE, 255));
        texte(g, this.font, lit(Ui.ellipsis(this.font, t("crac.acces.base.aide2"), MINI, Math.round(w)), MINI), x, y + 10, a(PALE, 255));
    }

    /** « 3 j 21 h », « 5 h 20 min », « 42 min ». */
    private static String duree(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long j = s / 86_400L, h = s % 86_400L / 3600L, m = s % 3600L / 60L;
        if (j > 0) {
            return t("crac.duree.jh", String.valueOf(j), String.valueOf(h));
        }
        return h > 0 ? t("crac.duree.hm", String.valueOf(h), String.valueOf(m)) : t("crac.duree.m", String.valueOf(Math.max(1, m)));
    }

    // ------------------------------------------------------------------------------------------------ souris, clavier

    @Override
    protected boolean clic(double mx, double my, int bouton) {
        float[][] o = this.onglets();
        if (dans(mx, my, o[0][0], o[0][1], o[0][2], o[0][3])) {
            this.envoyer(C2SAccesBase.Action.ENTRETIEN, null);
            return true;
        }
        if (dans(mx, my, o[1][0], o[1][1], o[1][2], o[1][3]) || dans(mx, my, o[2][0], o[2][1], o[2][2], o[2][3])) {
            this.ongletBase = dans(mx, my, o[2][0], o[2][1], o[2][2], o[2][3]);
            return true;
        }
        if (this.ongletBase || !this.etat.gerer()) {
            return false;
        }
        List<S2CAccesBase.Ligne> tr = this.etat.trouves();
        for (int i = 0; i < Math.min(TROUVES_VUS, tr.size()); i++) {
            if (this.surBouton(mx, my, this.trouvesY() + i * ROW, t("crac.acces.donner"))) {
                this.envoyer(C2SAccesBase.Action.DONNER, tr.get(i).id());
                return true;
            }
        }
        List<S2CAccesBase.Ligne> ont = this.etat.ont();
        int fin = Math.min(ont.size(), this.defile + this.lignesVues());
        for (int i = this.defile; i < fin; i++) {
            if (ont.get(i).role() == S2CAccesBase.ACCES && this.surBouton(mx, my, this.listeY() + (i - this.defile) * ROW, t("crac.acces.retirer"))) {
                this.envoyer(C2SAccesBase.Action.RETIRER, ont.get(i).id());
                return true;
            }
        }
        return false;
    }

    private boolean surBouton(double mx, double my, float y, String texte) {
        float bw = this.largeurPetitBouton(texte, false);
        return dans(mx, my, this.gx() + this.gw() - 4 - bw, y + 3, bw, ROW - 6);
    }

    @Override
    protected boolean molette(double mx, double my, double delta) {
        int max = Math.max(0, this.etat.ont().size() - this.lignesVues());
        this.defile = Mth.clamp(this.defile - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (this.ongletBase || !this.etat.gerer()) {
            return false;
        }
        if ((c == '_' || Character.isLetterOrDigit(c) && c < 128) && this.recherche.length() < 16) {
            this.recherche += c;
            this.attente = ATTENTE;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!this.ongletBase && this.etat.gerer()) {
            if (key == 259 && !this.recherche.isEmpty()) {   // retour arrière
                this.recherche = this.recherche.substring(0, this.recherche.length() - 1);
                this.attente = ATTENTE;
                return true;
            }
            if (key == 257 || key == 335) {   // Entrée : chercher tout de suite
                this.attente = -1;
                this.envoyer(C2SAccesBase.Action.CHERCHER, null);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
