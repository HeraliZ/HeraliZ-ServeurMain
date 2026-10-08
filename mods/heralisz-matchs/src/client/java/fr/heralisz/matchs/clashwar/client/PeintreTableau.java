package fr.heralisz.matchs.clashwar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.clashwar.net.VueTableau;
import fr.heralisz.matchs.client.kit.Icons;
import fr.heralisz.matchs.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dessin du tableau du tournoi sur un canevas de {@value #L} x {@value #H} unites (MATCHS_SPEC §2.3) : en-tete
 * (edition, format, etat), puis les deux moities du tableau qui convergent vers la finale au centre, bannieres v2 des
 * clans, libelles d'equipes, compteurs en direct, chemin des vainqueurs ; entre deux editions, l'accueil (prochaine
 * date, inscriptions, dernier champion). Utilise par {@link TextureTableau} (mur-ecran et ecran {@code /clashwar
 * tableau}) : il n'est appele qu'au changement de la vue, jamais a chaque image.
 */
public final class PeintreTableau {
    private PeintreTableau() {
    }

    /** Canevas de conception. */
    public static final int L = 1024, H = 512;
    private static final int MARGE = 28, HAUT_CORPS = 92, BAS_CORPS = H - 26;

    /** Ligne d'equipe dessinee (pour les survols de l'ecran) : rectangle en unites du canevas et index d'equipe. */
    public record Zone(int x, int y, int w, int h, int equipe) {
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    /** Lignes d'equipes du dernier dessin. */
    public static List<Zone> zones() {
        return ZONES;
    }

    /**
     * Peint la vue dans (0, 0, L, H) (pose deja mise a l'echelle par l'appelant).
     *
     * @param pxParUnite pixels physiques par unite du canevas (resolution des bannieres)
     * @param moi        joueur local (son equipe est soulignee), ou null
     */
    public static void peindre(GuiGraphics g, VueTableau v, float pxParUnite, @Nullable UUID moi) {
        ZONES.clear();
        Font font = Minecraft.getInstance().font;
        fond(g);
        entete(g, font, v);
        boolean tableau = !v.tours().isEmpty() && !v.equipes().isEmpty()
                && (v.etat() == VueTableau.Etat.EN_COURS || v.etat() == VueTableau.Etat.TERMINE);
        if (tableau) tableau(g, font, v, pxParUnite, moi);
        else accueil(g, font, v, pxParUnite);
        // pied : signature discrete
        Ui.hairline(g, MARGE, L - MARGE, H - 20, 0xFFFFFF, 30, true, true);
        Ui.spacedCentered(g, font, "HeraliZ · /clashwar tableau", Ui.SMALL, L / 2f, H - 14, Ui.argb(Ui.FAINT, 255), 1.2f);
    }

    // ================================================================== fond et en-tete
    private static void fond(GuiGraphics g) {
        // degrade vertical bleu nuit -> presque noir, voile diagonal, halo central : jamais un aplat
        g.fillGradient(0, 0, L, H, 0xFF141A26, 0xFF07090E);
        int bandes = 32;
        for (int i = 0; i < bandes; i++) {
            int x0 = L * i / bandes, x1 = L * (i + 1) / bandes;
            float t = (i + 0.5f) / bandes;
            g.fillGradient(x0, 0, x1, H, Ui.argb(0x2A3550, (int) (34 * (1 - t))), Ui.argb(0x000000, (int) (40 * t)));
        }
        // lueur centrale douce : bandes horizontales qui s'effacent vers les bords (pas de marches visibles)
        for (int r = 0; r < 24; r++) {
            float t = (r + 0.5f) / 24f;
            int a = (int) (16 * Math.sin(Math.PI * t));
            Ui.band(g, 140, 120 + r * 12, L - 140, 132 + r * 12, Ui.ACCENT_NEUTRE, a, 260);
        }
        g.fill(0, 0, L, 2, Ui.argb(Ui.ACCENT_NEUTRE, 150));
        g.fill(0, H - 1, L, H, Ui.argb(0xFFFFFF, 20));
    }

    private static void entete(GuiGraphics g, Font font, VueTableau v) {
        Ui.gros(g, font, Ui.lit("CLASHWAR", Ui.BOLD), MARGE, 18, 2.6f, 0xFFFFFFFF);
        StringBuilder sb = new StringBuilder("HeraliZ");
        if (v.edition() > 0 && v.etat() != VueTableau.Etat.AUCUN) sb.append("  ·  ").append(tr("clashwar.mur.edition", v.edition()));
        if (!v.format().isEmpty() && v.etat() != VueTableau.Etat.AUCUN) sb.append("  ·  ").append(tr("clashwar.mur.format", v.format().toUpperCase(Locale.ROOT)));
        Ui.spaced(g, font, sb.toString(), Ui.SMALL, MARGE + 2, 50, Ui.argb(Ui.MUTED, 255), 1.4f);
        // etat a droite
        String etat;
        int couleur;
        switch (v.etat()) {
            case INSCRIPTION -> {
                etat = tr("clashwar.mur.etat.inscription", heure(v.finInscriptionMs()));
                couleur = Ui.OK;
            }
            case PREPARATION -> {
                etat = tr("clashwar.mur.etat.preparation");
                couleur = Ui.AMBRE;
            }
            case EN_COURS -> {
                int t = Math.min(v.tourCourant(), v.tours().size() - 1);
                etat = tr("clashwar.mur.etat.direct", tr("clashwar.tour." + v.cleTour(t)));
                couleur = Ui.DANGER;
            }
            case TERMINE -> {
                etat = tr("clashwar.mur.etat.termine");
                couleur = Ui.OR;
            }
            case ANNULE -> {
                etat = tr("clashwar.mur.etat.annule");
                couleur = Ui.MUTED;
            }
            default -> {
                etat = v.prochaineMs() > 0 ? tr("clashwar.mur.etat.prochain", date(v.prochaineMs())) : "";
                couleur = Ui.ACCENT_NEUTRE;
            }
        }
        if (!etat.isEmpty()) {
            String haut = etat.toUpperCase(Locale.ROOT);
            float w = Ui.spacedWidth(font, haut, Ui.BOLD, 1.4f);
            int cx = (int) (L - MARGE - w - 26), cy = 26;
            Ui.soft(g, cx, cy, (int) w + 26, 22, Ui.argb(couleur, 30));
            Ui.softOutline(g, cx + 1, cy + 1, (int) w + 24, 20, Ui.argb(couleur, 120));
            Ui.point(g, cx + 9, cy + 8, couleur, 255);
            Ui.spaced(g, font, haut, Ui.BOLD, cx + 19, cy + 7, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.45f), 255), 1.4f);
        }
        Ui.hairline(g, MARGE, L - MARGE, 72, 0xFFFFFF, 46, false, true);
    }

    // ================================================================== accueil (pas de tableau)
    private static void accueil(GuiGraphics g, Font font, VueTableau v, float px) {
        int cx = L / 2;
        String titre, ligne1, ligne2 = "";
        switch (v.etat()) {
            case INSCRIPTION -> {
                titre = tr("clashwar.mur.inscriptions");
                ligne1 = tr("clashwar.mur.cloture", heure(v.finInscriptionMs()));
                ligne2 = tr("clashwar.mur.inscrits", v.clansInscrits(), v.equipesInscrites());
            }
            case PREPARATION -> {
                titre = tr("clashwar.mur.preparation");
                ligne1 = tr("clashwar.mur.inscrits", v.clansInscrits(), v.equipesInscrites());
            }
            case ANNULE -> {
                titre = tr("clashwar.mur.annulee");
                ligne1 = v.prochaineMs() > 0 ? tr("clashwar.mur.prochain", date(v.prochaineMs()), v.prochainFormat().toUpperCase(Locale.ROOT)) : "";
            }
            default -> {
                titre = tr("clashwar.mur.prochain_titre");
                ligne1 = v.prochaineMs() > 0 ? date(v.prochaineMs()) : "";
                ligne2 = v.prochainFormat().isEmpty() ? "" : tr("clashwar.mur.format_prochain", v.prochainFormat().toUpperCase(Locale.ROOT));
            }
        }
        boolean champion = v.champion() != null && v.etat() != VueTableau.Etat.INSCRIPTION;
        int colonne = champion ? cx - 170 : cx;
        float tw = Ui.spacedWidth(font, titre.toUpperCase(Locale.ROOT), Ui.BOLD, 2f) * 2f;
        g.pose().pushPose();
        g.pose().translate(colonne - tw / 2f, 170, 0);
        g.pose().scale(2f, 2f, 1f);
        Ui.spaced(g, font, titre, Ui.BOLD, 0, 0, 0xFFFFFFFF, 2f);
        g.pose().popPose();
        Ui.hairline(g, colonne - 150, colonne + 150, 202, Ui.ACCENT_NEUTRE, 160, true, true);
        if (!ligne1.isEmpty()) Ui.gros(g, font, Ui.lit(ligne1, Ui.BOLD), colonne - font.width(Ui.lit(ligne1, Ui.BOLD)) * 1.6f / 2f, 220, 1.6f,
                Ui.argb(Ui.TEXT, 255));
        if (!ligne2.isEmpty()) Ui.spacedCentered(g, font, ligne2, Ui.SMALL, colonne, 252, Ui.argb(Ui.MUTED, 255), 1.4f);
        if (v.etat() == VueTableau.Etat.INSCRIPTION) {
            Ui.spacedCentered(g, font, tr("clashwar.mur.inviter"), Ui.SMALL, colonne, 300, Ui.argb(Ui.OK, 255), 1.4f);
        }
        if (champion) {
            VueTableau.Equipe c = v.champion();
            int x = cx + 90, y = 130;
            Ui.soft(g, x, y, 250, 230, Ui.argb(c.couleur(), 16));
            Ui.softOutline(g, x + 1, y + 1, 248, 228, Ui.argb(Ui.OR, 90));
            Ui.spacedCentered(g, font, tr("clashwar.mur.dernier_champion", v.championEdition()), Ui.SMALL, x + 125, y + 12, Ui.argb(Ui.OR, 255), 1.4f);
            banniere(g, c.banniere(), x + 125 - 40, y + 30, 80, 120, px);
            Ui.centered(g, font, Ui.lit(c.libelle(), Ui.BOLD), x + 125, y + 160, Ui.argb(c.couleur(), 255));
            Ui.centered(g, font, Ui.lit(c.nomClan(), Ui.FONT), x + 125, y + 176, Ui.argb(Ui.MUTED, 255));
            if (!c.joueurs().isEmpty()) {
                Ui.centered(g, font, Ui.lit(String.join(" · ", c.joueurs()), Ui.SMALL), x + 125, y + 200, Ui.argb(Ui.FAINT, 255));
            }
        }
    }

    // ================================================================== tableau
    private static void tableau(GuiGraphics g, Font font, VueTableau v, float px, @Nullable UUID moi) {
        List<List<VueTableau.Rencontre>> tours = v.tours();
        int nbTours = tours.size();
        int monEquipe = -1;
        if (moi != null) {
            for (int i = 0; i < v.equipes().size(); i++) if (v.equipes().get(i).uuids().contains(moi)) monEquipe = i;
        }
        int corpsH = BAS_CORPS - HAUT_CORPS;
        if (nbTours == 1) {
            boiteFinale(g, font, v, tours.get(0).get(0), L / 2 - 110, HAUT_CORPS + corpsH / 2 - 30, 220, px, monEquipe);
            return;
        }
        int colonnes = 2 * (nbTours - 1) + 1, ecart = colonnes > 7 ? 14 : 22;
        int colL = (L - 2 * MARGE - (colonnes - 1) * ecart) / colonnes;
        int finaleL = Math.min(220, colL + 60);
        // largeur des colonnes laterales recalculee pour laisser plus de place a la finale
        colL = (L - 2 * MARGE - finaleL - (colonnes - 1) * ecart) / (colonnes - 1);
        int premiers = tours.get(0).size() / 2;
        int ligneH = Math.max(11, Math.min(30, corpsH / Math.max(1, premiers) / 2 - 8));
        // titres des colonnes
        for (int t = 0; t < nbTours - 1; t++) {
            String nom = tr("clashwar.tour." + v.cleTour(t)).toUpperCase(Locale.ROOT);
            int xg = MARGE + t * (colL + ecart), xd = L - MARGE - (t + 1) * colL - t * ecart;
            int c = t == Math.min(v.tourCourant(), nbTours - 1) && v.etat() == VueTableau.Etat.EN_COURS ? Ui.TEXT : Ui.MUTED;
            Ui.spacedCentered(g, font, nom, Ui.SMALL, xg + colL / 2f, HAUT_CORPS - 12, Ui.argb(c, 255), 1.2f);
            Ui.spacedCentered(g, font, nom, Ui.SMALL, xd + colL / 2f, HAUT_CORPS - 12, Ui.argb(c, 255), 1.2f);
        }
        int finaleX = MARGE + (nbTours - 1) * (colL + ecart);
        for (int t = 0; t < nbTours - 1; t++) {
            List<VueTableau.Rencontre> l = tours.get(t);
            int parCote = l.size() / 2;
            float creneau = corpsH / (float) parCote;
            for (int cote = 0; cote < 2; cote++) {
                int x = cote == 0 ? MARGE + t * (colL + ecart) : L - MARGE - (t + 1) * colL - t * ecart;
                for (int k = 0; k < parCote; k++) {
                    int index = cote * parCote + k;
                    VueTableau.Rencontre r = l.get(index);
                    int cy = Math.round(HAUT_CORPS + (k + 0.5f) * creneau);
                    int y = cy - ligneH;
                    // liaison vers la rencontre suivante
                    int suivX, suivY;
                    if (t + 1 < nbTours - 1) {
                        int parCoteSuiv = parCote / 2;
                        float creneauSuiv = corpsH / (float) parCoteSuiv;
                        suivY = Math.round(HAUT_CORPS + (k / 2 + 0.5f) * creneauSuiv);
                        suivX = cote == 0 ? x + colL + ecart : x - ecart;
                    } else {
                        suivY = HAUT_CORPS + corpsH / 2;
                        suivX = cote == 0 ? finaleX : finaleX + finaleL;
                    }
                    int gagnantCouleur = r.gagnant >= 0 && r.gagnant < v.equipes().size() ? v.equipes().get(r.gagnant).couleur() : -1;
                    liaison(g, cote == 0 ? x + colL : x, cy, suivX, suivY, gagnantCouleur);
                    boite(g, font, v, r, x, y, colL, ligneH, px, monEquipe);
                }
            }
        }
        boiteFinale(g, font, v, tours.get(nbTours - 1).get(0), finaleX, HAUT_CORPS + corpsH / 2 - 26, finaleL, px, monEquipe);
    }

    /** Liaison en equerre entre deux rencontres ; chemin du vainqueur a sa couleur. */
    private static void liaison(GuiGraphics g, int x0, int y0, int x1, int y1, int couleurVainqueur) {
        int c = couleurVainqueur >= 0 ? Ui.argb(couleurVainqueur, 170) : Ui.argb(0xFFFFFF, 38);
        int xm = (x0 + x1) / 2;
        g.fill(Math.min(x0, xm), y0, Math.max(x0, xm) + 1, y0 + 1, c);
        g.fill(xm, Math.min(y0, y1), xm + 1, Math.max(y0, y1) + 1, c);
        g.fill(Math.min(xm, x1), y1, Math.max(xm, x1), y1 + 1, c);
    }

    /** Boite d'une rencontre : deux lignes (equipe A en haut, B en bas). */
    private static void boite(GuiGraphics g, Font font, VueTableau v, VueTableau.Rencontre r, int x, int y, int w, int ligneH, float px, int moi) {
        boolean direct = r.enCours();
        Ui.soft(g, x, y, w, ligneH * 2, Ui.argb(0x0D0F14, 215));
        Ui.softOutline(g, x + 1, y + 1, w - 2, ligneH * 2 - 2, direct ? Ui.argb(Ui.DANGER, 200) : Ui.argb(0xFFFFFF, 30));
        g.fill(x + 4, y + ligneH, x + w - 4, y + ligneH + 1, Ui.argb(0xFFFFFF, 18));
        ligne(g, font, v, r, r.a, r.vivantsA, r.totalA, x, y, w, ligneH, px, moi, r.statut == 3);
        ligne(g, font, v, r, r.b, r.vivantsB, r.totalB, x, y + ligneH, w, ligneH, px, moi, r.statut == 3);
        if (direct) {
            String t = tr("clashwar.mur.direct").toUpperCase(Locale.ROOT);
            float tw = Ui.spacedWidth(font, t, Ui.SMALL, 0.8f);
            int cx = (int) (x + w - tw - 14);
            Ui.soft(g, cx, y - 9, (int) tw + 12, 9, Ui.argb(Ui.DANGER, 210));
            Ui.spaced(g, font, t, Ui.SMALL, cx + 6, y - 7.5f, 0xFFFFFFFF, 0.8f);
        }
    }

    private static void ligne(GuiGraphics g, Font font, VueTableau v, VueTableau.Rencontre r, int equipe, int vivants, int total, int x, int y,
                              int w, int h, float px, int moi, boolean exemption) {
        boolean connue = equipe >= 0 && equipe < v.equipes().size();
        if (!connue) {
            String t = exemption ? tr("clashwar.mur.exempt") : "—";
            Ui.spaced(g, font, t, Ui.SMALL, x + 8, y + (h - 6) / 2f, Ui.argb(Ui.FAINT, 255), 1f);
            return;
        }
        VueTableau.Equipe e = v.equipes().get(equipe);
        boolean gagne = r.gagnant == equipe, perdu = r.finie() && r.gagnant >= 0 && !gagne;
        ZONES.add(new Zone(x, y, w, h, equipe));
        if (gagne) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Ui.argb(e.couleur(), 34));
            g.fill(x + 1, y + 2, x + 3, y + h - 2, Ui.argb(e.couleur(), 230));
        }
        int b = Math.max(6, h - 6);
        int bw = Math.round(b * 2 / 3f);
        banniere(g, e.banniere(), x + 6, y + (h - b) / 2, bw, b, px, perdu ? 0.45f : 1f);
        int tx = x + 10 + bw;
        int couleur = perdu ? Ui.FAINT : equipe == moi ? Ui.mix(e.couleur(), 0xFFFFFF, 0.25f) : Ui.TEXT;
        // grandes lignes (peu d'equipes) : libelle agrandi
        float k = h >= 26 ? 1.3f : 1f;
        int reserve = r.enCours() ? 30 : 18;
        String libelle = Ui.ellipsis(font, e.libelle(), Ui.BOLD, (int) ((w - (tx - x) - reserve) / k));
        float ty = y + (h - 8 * k) / 2f;
        Ui.gros(g, font, Ui.lit(libelle, Ui.BOLD), tx, Math.round(ty), k, Ui.argb(couleur, 255));
        if (equipe == moi) {
            int lw = Math.round(font.width(Ui.lit(libelle, Ui.BOLD)) * k), base = Math.round(ty + 9 * k);
            g.fill(tx, base, tx + lw, base + 1, Ui.argb(e.couleur(), 220));
        }
        // a droite : vivants en direct, F (forfait), coche du vainqueur
        String droite = "";
        int dc = Ui.MUTED;
        if (r.enCours() && total > 0) {
            droite = vivants + "/" + total;
            dc = vivants > 0 ? Ui.TEXT : Ui.DANGER;
        } else if (gagne && r.statut == 2) {
            if (r.forfait) {
                droite = "F";
                dc = Ui.AMBRE;
            } else {
                int t = Math.min(10, h - 4);
                Icons.draw(g, Icons.COCHE, x + w - 6 - t, y + (h - t) / 2, t, Ui.mix(e.couleur(), 0xFFFFFF, 0.3f), 255);
            }
        }
        if (!droite.isEmpty()) Ui.right(g, font, Ui.lit(droite, Ui.BOLD), x + w - 7, ty, Ui.argb(dc, 255));
    }

    /** Finale au centre : boite plus grande, couronne du champion au-dessus quand elle est jouee. */
    private static void boiteFinale(GuiGraphics g, Font font, VueTableau v, VueTableau.Rencontre r, int x, int y, int w, float px, int moi) {
        int ligneH = 26;
        Ui.spacedCentered(g, font, tr("clashwar.tour.finale").toUpperCase(Locale.ROOT), Ui.BOLD, x + w / 2f, y - 16, Ui.argb(Ui.OR, 255), 2f);
        Ui.soft(g, x - 3, y - 3, w + 6, ligneH * 2 + 6, Ui.argb(Ui.OR, 26));
        boite(g, font, v, r, x, y, w, ligneH, px, moi);
        Ui.softOutline(g, x - 2, y - 2, w + 4, ligneH * 2 + 4, Ui.argb(Ui.OR, r.enCours() ? 60 : 140));
        if (r.finie() && r.gagnant >= 0 && r.gagnant < v.equipes().size()) {
            VueTableau.Equipe c = v.equipes().get(r.gagnant);
            int bw = 54, bh = 81;
            int by = y - 26 - bh - 18;
            Ui.spacedCentered(g, font, tr("clashwar.mur.champion").toUpperCase(Locale.ROOT), Ui.SMALL, x + w / 2f, by - 10, Ui.argb(Ui.OR, 255), 1.6f);
            banniere(g, c.banniere(), x + w / 2 - bw / 2, by, bw, bh, px, 1f);
            Ui.centered(g, font, Ui.lit(c.libelle(), Ui.BOLD), x + w / 2f, y + ligneH * 2 + 14, Ui.argb(c.couleur(), 255));
            String noms = String.join(" · ", c.joueurs());
            Ui.centered(g, font, Ui.lit(Ui.ellipsis(font, noms, Ui.SMALL, w + 80), Ui.SMALL), x + w / 2f, y + ligneH * 2 + 28, Ui.argb(Ui.MUTED, 255));
        } else if (r.a >= 0 || r.b >= 0) {
            Ui.spacedCentered(g, font, tr("clashwar.mur.vers_finale"), Ui.SMALL, x + w / 2f, y + ligneH * 2 + 14, Ui.argb(Ui.FAINT, 255), 1.2f);
        }
    }

    // ================================================================== aides
    /** Banniere nette : texture du mod clans a la resolution physique (pixels par unite). */
    static void banniere(GuiGraphics g, Banniere b, int x, int y, int w, int h, float px) {
        banniere(g, b, x, y, w, h, px, 1f);
    }

    static void banniere(GuiGraphics g, Banniere b, int x, int y, int w, int h, float px, float alpha) {
        if (w <= 0 || h <= 0) return;
        int pw = Math.max(4, Math.round(w * px)), ph = Math.max(6, Math.round(h * px));
        ResourceLocation rl;
        try {
            rl = fr.heralisz.clans.client.gui.kit.BannerArt.texture(b, pw, ph);
        } catch (RuntimeException | LinkageError ex) {
            Ui.soft(g, x, y, w, h, Ui.argb(b.accent(), 200));
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(rl, x, y, w, h, 0, 0, pw, ph, pw, ph);
        g.setColor(1f, 1f, 1f, 1f);
    }

    static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    /** « 20:30 » aujourd'hui, sinon « mer. 20:30 » (fuseau du client). */
    static String heure(long ms) {
        if (ms <= 0) return "--:--";
        ZonedDateTime d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        String hm = String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        if (d.toLocalDate().equals(LocalDate.now())) return hm;
        return tr("clashwar.jour.court." + d.getDayOfWeek().getValue()) + " " + hm;
    }

    /** « mercredi 20:30 » (ou « aujourd'hui 20:30 »). */
    static String date(long ms) {
        ZonedDateTime d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault());
        String hm = String.format(Locale.ROOT, "%02d:%02d", d.getHour(), d.getMinute());
        if (d.toLocalDate().equals(LocalDate.now())) return tr("clashwar.jour.aujourdhui", hm);
        return tr("clashwar.jour.long." + d.getDayOfWeek().getValue()) + " " + hm;
    }

    /** Composant dans la police grasse. */
    static MutableComponent gras(String s) {
        return Ui.lit(s, Ui.BOLD);
    }
}
