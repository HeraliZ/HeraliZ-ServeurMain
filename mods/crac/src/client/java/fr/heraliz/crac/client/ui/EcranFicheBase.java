package fr.heraliz.crac.client.ui;

import java.util.List;
import fr.heraliz.crac.client.group.EcranGroupe;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.S2CFicheBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Fiche technique des bases ({@code /base info}, CRac 1.13.0, maquette validée le 19/10) : onglets Poser, Améliorer,
 * Réparer, Entretien et Raids. Rien n'est calculé ici : les chiffres viennent du serveur ({@link S2CFicheBase}).
 */
public class EcranFicheBase extends EcranGroupe {
    private static final float W = 540, H = 300, ROW = 30;
    private static final String[] ONGLETS = {"poser", "ameliorer", "reparer", "entretien", "raids"};

    private final S2CFicheBase fiche;
    private int onglet = 1;

    public EcranFicheBase(S2CFicheBase fiche) {
        super(Component.translatable("crac.fiche.titre"));
        this.fiche = fiche;
    }

    @Override
    protected void initCanevas() {
        this.placer(W, H);
    }

    private float gx() {
        return this.x0 + 13.33F;
    }

    private float gw() {
        return W - 26.67F;
    }

    private static String t(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    /** « 1 250 » : milliers séparés par une espace. */
    private static String nombre(int n) {
        String s = Integer.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    private static int couleur(String palier) {
        return switch (palier) {
            case "wood" -> 0xC8965A;
            case "stone" -> 0xB4B4AE;
            case "metal" -> 0x8CAAC8;
            case "armored" -> 0xDCB450;
            default -> 0x9AA0AA;
        };
    }

    private static Item objet(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item i = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return i == null ? Items.AIR : i;
    }

    /** Somme en hérals des objets d'un coût que vend un marchand ; -1 si aucun n'a de prix. */
    private int herals(List<S2CFicheBase.Cout> couts) {
        int total = 0;
        boolean un = false;
        for (S2CFicheBase.Cout c : couts) {
            Integer p = this.fiche.prix().get(c.objet());
            if (p != null) {
                total += p * c.nombre();
                un = true;
            }
        }
        return un ? total : -1;
    }

    private float[][] onglets() {
        float[][] r = new float[ONGLETS.length][];
        float x = this.gx();
        for (int i = 0; i < ONGLETS.length; i++) {
            float w = this.largeurPetitBouton(t("crac.fiche.onglet." + ONGLETS[i]), false) + 10;
            r[i] = new float[]{x, this.y0 + 38, w, 14};
            x += w + 4;
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ dessin

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        this.fond(g);
        this.panneau(g, VERT);
        this.entete(g, -1, t("crac.fiche.titre"), BLANC, t("crac.fiche.sous"));
        float[][] o = this.onglets();
        for (int i = 0; i < ONGLETS.length; i++) {
            this.petitBouton(g, o[i][0], o[i][1], o[i][2], o[i][3], t("crac.fiche.onglet." + ONGLETS[i]), i == this.onglet ? VERT : -1,
                dans(mx, my, o[i][0], o[i][1], o[i][2], o[i][3]), false);
        }
        rect(g, this.gx(), this.y0 + 58, this.gx() + this.gw(), this.y0 + 58 + this.px, a(BLANC, 20));
        switch (this.onglet) {
            case 0 -> this.poser(g);
            case 1 -> this.ameliorer(g);
            case 2 -> this.reparer(g);
            case 3 -> this.entretien(g);
            default -> this.raids(g);
        }
    }

    private void aide(GuiGraphics g, String s, float y) {
        texte(g, this.font, lit(Ui.ellipsis(this.font, s, MOYEN, Math.round(this.gw())), MOYEN), this.gx(), y, a(GRIS, 255));
    }

    private void pied(GuiGraphics g, String l1, String l2) {
        float x = this.gx(), w = this.gw();
        rect(g, x, this.y0 + H - 30, x + w, this.y0 + H - 30 + this.px, a(BLANC, 20));
        texte(g, this.font, lit(Ui.ellipsis(this.font, l1, MINI, Math.round(w)), MINI), x, this.y0 + H - 24 - 4.21F, a(ATTENUE, 255));
        if (!l2.isEmpty()) {
            texte(g, this.font, lit(Ui.ellipsis(this.font, l2, MINI, Math.round(w)), MINI), x, this.y0 + H - 14 - 4.21F, a(ATTENUE, 255));
        }
    }

    private void entetes(GuiGraphics g, float y, float[] cols, String... noms) {
        for (int i = 0; i < noms.length; i++) {
            caps(g, this.font, noms[i], MINI_GRAS, cols[i], y - 4.21F, 0.6F, a(ATTENUE, 255));
        }
    }

    /** Fond d'une ligne de palier : bande de sa couleur à gauche et son nom. */
    private void lignePalier(GuiGraphics g, float y, float h, String palier) {
        float x = this.gx() - 4, w = this.gw() + 8;
        rect(g, x, y, x + w, y + h, a(BLANC, 12));
        int c = couleur(palier);
        rect(g, x, y + 4, x + 2, y + h - 4, a(c, 255));
        texte(g, this.font, lit(t("tier.crac." + palier), GRAS_9), this.gx() + 4, y + h / 2 - 5.44F, a(c, 255));
    }

    /** Icône et « 5 planches » ; rend la largeur prise. */
    private float cout(GuiGraphics g, S2CFicheBase.Cout c, float x, float cy, int maxW) {
        Item i = objet(c.objet());
        if (i == Items.AIR) {
            return 0;
        }
        ItemStack st = new ItemStack(i);
        g.pose().pushPose();
        g.pose().translate(x, cy - 7, 0);
        g.pose().scale(14 / 16F, 14 / 16F, 1);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
        String s = Ui.ellipsis(this.font, nombre(c.nombre()) + " " + st.getHoverName().getString(), MOYEN, maxW - 18);
        texte(g, this.font, lit(s, MOYEN), x + 18, cy - 4.9F, a(TEXTE, 255));
        return 18 + largeur(this.font, s, MOYEN);
    }

    private void tiret(GuiGraphics g, float x, float cy) {
        texte(g, this.font, lit(t("crac.fiche.rien"), MOYEN), x, cy - 4.9F, a(PALE, 255));
    }

    private void poser(GuiGraphics g) {
        this.aide(g, t("crac.fiche.poser.aide"), this.y0 + 66);
        List<S2CFicheBase.Pose> l = this.fiche.poses();
        int parColonne = (l.size() + 1) / 2;
        float colW = (this.gw() - 12) / 2, h = 19;
        for (int i = 0; i < l.size(); i++) {
            S2CFicheBase.Pose p = l.get(i);
            float x = this.gx() + (i / parColonne) * (colW + 12);
            float y = this.y0 + 82 + (i % parColonne) * h;
            rect(g, x - 2, y, x + colW, y + h - 2, a(BLANC, 10));
            float cy = y + (h - 2) / 2;
            texte(g, this.font, lit(t("piece.crac." + p.piece()), MOYEN_GRAS), x + 4, cy - 4.9F, a(BLANC, 255));
            float cx = x + colW * 0.5F;
            if (p.couts().isEmpty()) {
                texte(g, this.font, lit(t("crac.fiche.gratuit"), MOYEN), cx, cy - 4.9F, a(VERT_PALE, 255));
            }
            for (S2CFicheBase.Cout c : p.couts()) {
                cx += this.cout(g, c, cx, cy, Math.round(x + colW - cx)) + 8;
            }
        }
        this.pied(g, t("crac.fiche.poser.pied1"), t("crac.fiche.poser.pied2"));
    }

    private void ameliorer(GuiGraphics g) {
        boolean meme = true;
        for (S2CFicheBase.Palier p : this.fiche.paliers()) {
            meme &= p.memePartout();
        }
        this.aide(g, t(meme ? "crac.fiche.ameliorer.aide" : "crac.fiche.ameliorer.aide_mur"), this.y0 + 66);
        float w = this.gw();
        float[] cols = {this.gx(), this.gx() + w * 0.17F, this.gx() + w * 0.45F, this.gx() + w * 0.72F, this.gx() + w * 0.85F};
        this.entetes(g, this.y0 + 86, cols, t("crac.fiche.col.palier"), t("crac.fiche.col.materiau"), t("crac.fiche.col.carte"),
            t("crac.fiche.col.solidite"), t("crac.fiche.col.cout"));
        float y = this.y0 + 94;
        for (S2CFicheBase.Palier p : this.fiche.paliers()) {
            if (p.ameliorer().isEmpty()) {
                continue;
            }
            this.lignePalier(g, y, ROW - 4, p.id());
            float cy = y + (ROW - 4) / 2;
            for (int i = 0; i < Math.min(2, p.ameliorer().size()); i++) {
                this.cout(g, p.ameliorer().get(i), cols[1 + i], cy, Math.round(cols[2 + i] - cols[1 + i] - 4));
            }
            if (p.ameliorer().size() < 2) {
                this.tiret(g, cols[2], cy);
            }
            texte(g, this.font, lit(t("crac.fiche.pv", nombre(p.pvMur())), MOYEN), cols[3], cy - 4.9F, a(TEXTE, 255));
            int h = this.herals(p.ameliorer());
            if (h < 0) {
                this.tiret(g, cols[4], cy);
            } else {
                texte(g, this.font, lit(t("crac.fiche.herals", nombre(h)), MOYEN_GRAS), cols[4], cy - 4.9F, a(OR, 255));
            }
            y += ROW;
        }
        this.pied(g, t("crac.fiche.ameliorer.pied1"), t("crac.fiche.ameliorer.pied2"));
    }

    private void reparer(GuiGraphics g) {
        this.aide(g, t("crac.fiche.reparer.aide", String.valueOf(this.fiche.reparePourcent()), dixiemes(this.fiche.repareSecondes())), this.y0 + 66);
        float w = this.gw();
        float[] cols = {this.gx(), this.gx() + w * 0.2F, this.gx() + w * 0.6F};
        this.entetes(g, this.y0 + 86, cols, t("crac.fiche.col.palier"), t("crac.fiche.col.une_reparation"), t("crac.fiche.col.solidite"));
        float y = this.y0 + 94;
        for (S2CFicheBase.Palier p : this.fiche.paliers()) {
            this.lignePalier(g, y, ROW - 6, p.id());
            float cy = y + (ROW - 6) / 2, cx = cols[1];
            if (p.reparer().isEmpty()) {
                this.tiret(g, cx, cy);
            }
            for (S2CFicheBase.Cout c : p.reparer()) {
                cx += this.cout(g, c, cx, cy, Math.round(cols[2] - cx - 4)) + 8;
            }
            texte(g, this.font, lit(t("crac.fiche.pv", nombre(p.pvMur())), MOYEN), cols[2], cy - 4.9F, a(TEXTE, 255));
            y += ROW - 2;
        }
        this.pied(g, t("crac.fiche.reparer.pied"), "");
    }

    private void entretien(GuiGraphics g) {
        this.aide(g, t("crac.fiche.entretien.aide", dixiemes(this.fiche.entretienHeures())), this.y0 + 66);
        float w = this.gw();
        float[] cols = {this.gx(), this.gx() + w * 0.2F};
        this.entetes(g, this.y0 + 86, cols, t("crac.fiche.col.palier"), t("crac.fiche.col.par_piece"));
        float y = this.y0 + 94;
        for (S2CFicheBase.Palier p : this.fiche.paliers()) {
            this.lignePalier(g, y, ROW - 6, p.id());
            float cy = y + (ROW - 6) / 2;
            if (p.entretien().objet().isEmpty() || p.entretien().nombre() <= 0) {
                this.tiret(g, cols[1], cy);
            } else {
                this.cout(g, p.entretien(), cols[1], cy, Math.round(w * 0.75F));
            }
            y += ROW - 2;
        }
        this.pied(g, t("crac.fiche.entretien.pied"), "");
    }

    private void raids(GuiGraphics g) {
        this.aide(g, t("crac.fiche.raids.aide", nombre(this.fiche.degatsC4()), nombre(this.fiche.degatsCharge())), this.y0 + 66);
        float w = this.gw();
        float[] cols = {this.gx(), this.gx() + w * 0.25F, this.gx() + w * 0.5F, this.gx() + w * 0.75F};
        this.entetes(g, this.y0 + 86, cols, t("crac.fiche.col.palier"), t("crac.fiche.col.solidite"), t("crac.fiche.col.c4"),
            t("crac.fiche.col.charges"));
        float y = this.y0 + 94;
        for (S2CFicheBase.Palier p : this.fiche.paliers()) {
            this.lignePalier(g, y, ROW - 6, p.id());
            float cy = y + (ROW - 6) / 2;
            texte(g, this.font, lit(t("crac.fiche.pv", nombre(p.pvMur())), MOYEN), cols[1], cy - 4.9F, a(TEXTE, 255));
            texte(g, this.font, lit(nombre(p.c4()), MOYEN_GRAS), cols[2], cy - 4.9F, a(ROUGE_PALE, 255));
            texte(g, this.font, lit(nombre(p.charges()), MOYEN_GRAS), cols[3], cy - 4.9F, a(ROUGE_PALE, 255));
            y += ROW - 2;
        }
        this.pied(g, t("crac.fiche.raids.pied"), "");
    }

    /** « 3 », « 2,5 ». */
    private static String dixiemes(float v) {
        int d = Math.round(v * 10);
        return d % 10 == 0 ? String.valueOf(d / 10) : (d / 10) + "," + (d % 10);
    }

    // ------------------------------------------------------------------------------------------------ souris

    @Override
    protected boolean clic(double mx, double my, int bouton) {
        float[][] o = this.onglets();
        for (int i = 0; i < o.length; i++) {
            if (dans(mx, my, o[i][0], o[i][1], o[i][2], o[i][3])) {
                this.onglet = i;
                return true;
            }
        }
        return false;
    }
}
