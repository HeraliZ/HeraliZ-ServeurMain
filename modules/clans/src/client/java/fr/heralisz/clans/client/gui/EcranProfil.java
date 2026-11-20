package fr.heralisz.clans.client.gui;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.kit.BannerArt;
import fr.heralisz.clans.client.gui.kit.Echelle;
import fr.heralisz.clans.client.gui.kit.HzIcon;
import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.client.gui.tabs.SaisonTab;
import fr.heralisz.clans.profil.ProfilVue;
import fr.heralisz.clans.registry.Sons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Écran du profil d'un joueur ({@code /profil}, {@code /stats} ; 19/11, maquettes validées par le propriétaire) : le même
 * cadre que l'écran du clan (échelle forcée, canevas 640 x 360, panneau min(560, l-32) x min(320, h-24), en-tête 44,
 * marge 16), sans onglets ; pied comme les Quêtes du jour.
 * <ul>
 * <li>en-tête : tête du joueur, pseudo (DM Sans gras 14 à sa vraie taille), grade de la boutique, arrivée sur le serveur et
 * temps de jeu ; à droite, son solde (seulement sur son propre profil) ;</li>
 * <li>à gauche : COMBAT (kills, morts, K/D) et SURVIE (zombies tués, extractions, conteneurs) en tuiles, puis QUÊTES ET
 * RECORDS (quêtes de Varga et titre, meilleure série de kills, plus longue vie sur la carte, morts contre un joueur) ;</li>
 * <li>à droite, sur verre : son clan et la saison, ses classements, et ce qu'il a sur lui (son propre profil) ou ce
 * qu'on voit des autres (le profil d'un autre joueur ne dit jamais son argent, ce qu'il a sur lui ni où il est).</li>
 * </ul>
 * Rien n'est demandé au serveur : tout arrive avec {@link ProfilVue} ; l'écran ne fait que dessiner.
 */
public class EcranProfil extends Screen {
    private static final int ENTETE = 44, MARGE = Ui.MARGE;
    /** DM Sans gras 14 à sa vraie taille (pseudo de l'en-tête). */
    private static final ResourceLocation TITRE = HeraliszClans.id("ui_titre");
    private static final int ROUGE_CLAIR = 0xFF8A82;

    private final ProfilVue v;
    private int px, py, pw, ph;

    public EcranProfil(ProfilVue vue) {
        super(Component.translatable("gui.profil.titre"));
        this.v = vue;
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(560, width - 32);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        addRenderableWidget(new HzIcon(px + pw - 24, py + 10, 12, Icons.CROIX, Component.translatable("gui.fermer"), this::onClose));
    }

    // ------------------------------------------------------------------ textes
    private static String tr(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    /** Taille (fichier .json de la police) d'une police du kit, pour poser un texte sur le milieu de ses capitales. */
    private static float taille(ResourceLocation police) {
        if (police == Ui.SMALL) return 9f;
        if (police == Ui.GROS) return 17f;
        if (police == TITRE) return 14f;
        return 11f;
    }

    /** Texte à sa vraie taille, milieu des capitales sur {@code cy} ; alignement -1 gauche, 0 centre, 1 droite. Rend sa largeur. */
    private int texte(GuiGraphics g, Component c, ResourceLocation police, float x, float cy, int argb, int align) {
        int w = font.width(c);
        float gauche = align > 0 ? x - w : align == 0 ? x - w / 2f : x;
        g.pose().pushPose();
        Ui.placer(g, gauche, cy - Ui.milieu(taille(police)));
        g.drawString(font, c, 0, 0, argb, false);
        g.pose().popPose();
        return w;
    }

    private int texte(GuiGraphics g, String s, ResourceLocation police, float x, float cy, int rgb) {
        return texte(g, Ui.lit(s, police), police, x, cy, Ui.argb(rgb, 255), -1);
    }

    /** Capitales espacées (sections, légendes, sous-titre), milieu des capitales sur {@code cy}. */
    private void capitales(GuiGraphics g, String s, float x, float cy, int rgb, float espace, boolean droite) {
        if (droite) Ui.spacedRight(g, font, s, Ui.SMALL, x, cy - Ui.milieu(9f), Ui.argb(rgb, 255), espace);
        else Ui.spaced(g, font, s, Ui.SMALL, x, cy - Ui.milieu(9f), Ui.argb(rgb, 255), espace);
    }

    /** Langue du jeu (« fr_fr », « ru_ru »...) pour choisir les noms envoyés dans les trois langues. */
    private static String langue() {
        String l = Minecraft.getInstance().getLanguageManager().getSelected();
        return l == null ? "en_us" : l;
    }

    /** « 24/10 » cette année, sinon « 24/10/2026 ». */
    private static String date(long ms) {
        LocalDate d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate auj = LocalDate.now();
        return d.getYear() == auj.getYear() ? String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue())
                : String.format(Locale.ROOT, "%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    /** Temps de jeu : « 86 h », ou « 42 min » sous une heure. */
    private static String heures(long ms) {
        long min = Math.max(0, ms / 60_000L);
        if (min < 60) return min + tr("gui.duree.min");
        return Ui.nombre(min / 60) + tr("gui.duree.h");
    }

    /** Ancienneté dans le clan : « 18 j », ou la durée exacte sous un jour. */
    private static String anciennete(long ms) {
        long jours = ms / 86_400_000L;
        return jours >= 1 ? Ui.nombre(jours) + tr("gui.duree.j") : Ui.duree(ms);
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.fond(g, width, height);
        Ui.glass(g, px, py, pw, ph);
        int accent = v.clan() != null ? v.clan().banniere().accent() : Ui.ACCENT_NEUTRE;
        Ui.hairline(g, px + 14, px + pw - 14, py, accent, 170, true, true);
        entete(g);
        int contenu = pw - 2 * MARGE;
        int gl = Math.round(contenu * 300f / 528f);
        int gx = px + MARGE, dx = gx + gl + 16, dl = contenu - gl - 16;
        statistiques(g, gx, gl);
        droite(g, dx, dl);
        pied(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void entete(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        // tête du joueur : celle de la liste des joueurs s'il est en ligne, sinon la tête par défaut de son identifiant
        ResourceLocation peau = DefaultPlayerSkin.getDefaultSkin(v.joueur());
        ClientPacketListener co = mc.getConnection();
        PlayerInfo info = co == null ? null : co.getPlayerInfo(v.joueur());
        if (info != null) peau = info.getSkinLocation();
        g.fill(px + MARGE - 1, py + 7, px + MARGE + 29, py + 37, Ui.argb(0x000000, 110));
        PlayerFaceRenderer.draw(g, peau, px + MARGE, py + 8, 28);

        int tx = px + MARGE + 38;
        int bord = px + pw - 40;
        int largeurSolde = 0;
        if (v.moi() && v.solde() >= 0) {
            MutableComponent solde = Ui.lit(Ui.jetonsTexte(v.solde()), Ui.BOLD);
            largeurSolde = texte(g, solde, Ui.BOLD, bord, py + 15, Ui.argb(Ui.OR, 255), 1);
            capitales(g, tr("gui.profil.solde"), bord, py + 31, Ui.MUTED, 1.0f, true);
            largeurSolde = Math.max(largeurSolde, (int) Ui.spacedWidth(font, tr("gui.profil.solde").toUpperCase(Locale.ROOT), Ui.SMALL, 1.0f));
        }
        int place = bord - (largeurSolde > 0 ? largeurSolde + 16 : 0) - tx;
        String grade = v.grade().choisir(langue());
        int largeurGrade = grade.isEmpty() ? 0 : (int) Ui.spacedWidth(font, grade.toUpperCase(Locale.ROOT), Ui.SMALL, 0.6f) + 18;
        String pseudo = Ui.ellipsis(font, v.pseudo(), TITRE, Math.max(40, place - largeurGrade));
        int nw = texte(g, Ui.lit(pseudo, TITRE), TITRE, tx, py + 15, Ui.argb(Ui.TEXT, 255), -1);
        if (!grade.isEmpty()) Ui.chip(g, font, grade, tx + nw + 8, py + 10, v.gradeCouleur());
        String sous = v.depuis() > 0 ? tr("gui.profil.depuis_temps", date(v.depuis()), heures(v.tempsJeuMs()))
                : tr("gui.profil.temps", heures(v.tempsJeuMs()));
        capitales(g, Ui.spacedEllipsis(font, sous, Ui.SMALL, 1.0f, Math.max(40, place)), tx, py + 31, Ui.MUTED, 1.0f, false);
        Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
    }

    /** Colonne de gauche : tuiles de combat et de survie, lignes des quêtes et des records. */
    private void statistiques(GuiGraphics g, int x, int w) {
        ProfilVue.Compteurs c = v.compteurs();
        int tw = (w - 18) / 3, th = 44;
        Ui.section(g, font, tr("gui.profil.section.combat"), x, py + 54, w);
        Ui.tile(g, font, x, py + 64, tw, th, Ui.nombre(c.kills()), tr("gui.profil.tuile.kills"));
        Ui.tile(g, font, x + tw + 9, py + 64, tw, th, Ui.nombre(c.morts()), tr("gui.profil.tuile.morts"));
        Ui.tile(g, font, x + 2 * (tw + 9), py + 64, tw, th, Ui.ratio(c.kd()), tr("gui.profil.tuile.kd"));
        Ui.section(g, font, tr("gui.profil.section.survie"), x, py + 120, w);
        Ui.tile(g, font, x, py + 130, tw, th, Ui.nombre(c.zombies()), tr("gui.profil.tuile.zombies"));
        Ui.tile(g, font, x + tw + 9, py + 130, tw, th, Ui.nombre(c.extractions()), tr("gui.profil.tuile.extractions"));
        Ui.tile(g, font, x + 2 * (tw + 9), py + 130, tw, th, Ui.nombre(c.conteneurs()), tr("gui.profil.tuile.conteneurs"));
        Ui.section(g, font, tr("gui.profil.section.records"), x, py + 186, w);
        int y = py + 196, h = 24;
        // quêtes de Varga et dernier titre gagné
        if (v.quetes() >= 0) {
            String titre = v.titre().choisir(langue());
            ligne(g, x, y, w, h, tr("gui.profil.ligne.quetes"), false,
                    titre.isEmpty() ? new Morceau[]{new Morceau(tr("gui.profil.quetes", Ui.nombre(v.quetes())), Ui.TEXT, false)}
                            : new Morceau[]{new Morceau(tr("gui.profil.quetes", Ui.nombre(v.quetes())) + " · ", Ui.TEXT, false),
                            new Morceau(titre, Ui.OR, true)});
            y += h;
        }
        ligne(g, x, y, w, h, tr("gui.profil.ligne.serie"), false, new Morceau(Ui.nombre(c.serieMax()), Ui.TEXT, true),
                new Morceau(" " + tr("gui.profil.serie"), Ui.MUTED, false));
        y += h;
        ligne(g, x, y, w, h, tr("gui.profil.ligne.vie"), false, new Morceau(c.vieMaxMs() > 0 ? Ui.duree(c.vieMaxMs()) : "—", Ui.TEXT, true));
        y += h;
        ligne(g, x, y, w, h, tr("gui.profil.ligne.morts_joueur"), true, new Morceau(Ui.nombre(c.mortsJoueur()), Ui.TEXT, true),
                new Morceau(" " + tr("gui.profil.sur", Ui.nombre(c.morts())), Ui.MUTED, false));
    }

    /** Un morceau de la valeur d'une ligne : texte, couleur, gras. */
    private record Morceau(String texte, int couleur, boolean gras) {
    }

    /** Ligne : libellé atténué à gauche, valeur à droite (morceaux), filet dessous sauf la dernière. */
    private void ligne(GuiGraphics g, int x, int y, int w, int h, String libelle, boolean derniere, Morceau... valeur) {
        float cy = y + h / 2f;
        int largeurValeur = 0;
        for (Morceau m : valeur) largeurValeur += font.width(Ui.lit(m.texte(), m.gras() ? Ui.BOLD : Ui.FONT));
        texte(g, Ui.lit(Ui.ellipsis(font, libelle, Ui.FONT, Math.max(30, w - largeurValeur - 8)), Ui.FONT), Ui.FONT, x, cy, Ui.argb(Ui.MUTED, 255), -1);
        float xd = x + w;
        for (int i = valeur.length - 1; i >= 0; i--) {
            Morceau m = valeur[i];
            ResourceLocation p = m.gras() ? Ui.BOLD : Ui.FONT;
            xd -= texte(g, Ui.lit(m.texte(), p), p, xd, cy, Ui.argb(m.couleur(), 255), 1);
        }
        if (!derniere) Ui.hairline(g, x, x + w, y + h - 1, 0xFFFFFF, 14, false, true);
    }

    /** Colonne de droite, sur verre : clan et saison, classements, puis ce qu'il a sur lui (le sien) ou la note (un autre). */
    private void droite(GuiGraphics g, int x, int w) {
        int top = py + 50, haut = 242;
        Ui.soft(g, x, top, w, haut, Ui.argb(Ui.VERRE, 120));
        Ui.softOutline(g, x + 1, top + 1, w - 2, haut - 2, Ui.argb(0xFFFFFF, 26));
        int ix = x + 10, iw = w - 20;
        Ui.section(g, font, tr(v.moi() ? "gui.profil.section.mon_clan" : "gui.profil.section.son_clan"), ix, py + 59, iw);
        ProfilVue.ClanVue c = v.clan();
        if (c != null) {
            BannerArt.draw(g, c.banniere(), ix, py + 70, 18, 24);
            int cx = ix + 26;
            String tag = "[" + c.tag() + "]";
            int tagW = font.width(Ui.lit(tag, Ui.FONT));
            String nom = Ui.ellipsis(font, c.nom(), Ui.BOLD, Math.max(30, iw - 26 - tagW - 5));
            int nw = texte(g, Ui.lit(nom, Ui.BOLD), Ui.BOLD, cx, py + 75.5f, Ui.argb(Ui.TEXT, 255), -1);
            texte(g, Ui.lit(tag, Ui.FONT), Ui.FONT, cx + nw + 5, py + 75.5f, Ui.argb(Ui.MUTED, 255), -1);
            int chipW = Ui.chip(g, font, tr(c.grade().libelle()), cx, py + 83, c.banniere().accent());
            if (c.depuis() > 0) {
                long anc = Math.max(0, System.currentTimeMillis() - c.depuis());
                texte(g, Ui.lit(tr("gui.profil.clan.depuis", anciennete(anc)), Ui.SMALL), Ui.SMALL, cx + chipW + 6, py + 88.6f,
                        Ui.argb(Ui.MUTED, 255), -1);
            }
            Morceau[] saison;
            if (!c.saisonActive()) saison = new Morceau[]{new Morceau(tr("gui.profil.saison.finie"), Ui.MUTED, false)};
            else if (c.saisonRang() <= 0) {
                saison = new Morceau[]{new Morceau(tr("gui.profil.saison.non_classe", Ui.nombre(c.saisonPoints())), Ui.MUTED, false)};
            } else {
                saison = new Morceau[]{new Morceau(SaisonTab.rang(c.saisonRang()), Ui.TEXT, true),
                        new Morceau(" " + tr("gui.profil.saison.rang", Ui.nombre(c.saisonPoints())), Ui.MUTED, false)};
            }
            ligne(g, ix, py + 100, iw, 18, tr("gui.profil.saison", c.saison()), true, saison);
        } else {
            texte(g, Ui.lit(tr("gui.profil.sans_clan"), Ui.BOLD), Ui.BOLD, ix, py + 80, Ui.argb(Ui.TEXT, 255), -1);
            if (v.moi()) {
                texte(g, Ui.lit(Ui.ellipsis(font, tr("gui.profil.sans_clan.aide"), Ui.SMALL, iw), Ui.SMALL), Ui.SMALL, ix, py + 92,
                        Ui.argb(Ui.MUTED, 255), -1);
            }
        }

        Ui.section(g, font, tr("gui.profil.section.classements"), ix, py + 127, iw);
        ProfilVue.Rangs r = v.rangs();
        int y = py + 137;
        boolean herals = v.moi() && r.comptes() > 0;
        ligne(g, ix, y, iw, 17, tr("gui.profil.classement.kills"), false, rang(r.kills(), r.joueurs()));
        y += 17;
        if (herals) {
            ligne(g, ix, y, iw, 17, tr("gui.profil.classement.herals"), false, rang(r.herals(), r.comptes()));
            y += 17;
        }
        ligne(g, ix, y, iw, 17, tr("gui.profil.classement.zombies"), true, rang(r.zombies(), r.joueurs()));

        ProfilVue.SurLui s = v.surLui();
        if (s != null) surLui(g, s, ix, iw);
        else if (!v.moi()) {
            Ui.section(g, font, tr("gui.profil.section.visible"), ix, py + 201, iw);
            List<FormattedCharSequence> l = font.split(Ui.lit(tr("gui.profil.visible"), Ui.SMALL), iw);
            int ly = py + 218;
            for (int i = 0; i < Math.min(4, l.size()); i++, ly += 11) {
                g.pose().pushPose();
                Ui.placer(g, ix, ly - Ui.milieu(9f));
                g.drawString(font, l.get(i), 0, 0, Ui.argb(Ui.MUTED, 255), false);
                g.pose().popPose();
            }
        }
    }

    /** « 12e » en gras puis « sur 1 240 » ; « pas classé » sans rien de compté. */
    private static Morceau[] rang(int rang, int total) {
        if (rang <= 0) return new Morceau[]{new Morceau(tr("gui.profil.non_classe"), Ui.MUTED, false)};
        return new Morceau[]{new Morceau(SaisonTab.rang(rang), Ui.TEXT, true),
                new Morceau(" " + tr("gui.profil.sur", Ui.nombre(Math.max(total, rang))), Ui.MUTED, false)};
    }

    /** Son propre profil : bourses de hérals et capsules du casino qu'il a sur lui ; l'avertissement des bourses. */
    private void surLui(GuiGraphics g, ProfilVue.SurLui s, int ix, int iw) {
        boolean bourses = s.bourses() >= 0, capsules = s.capsulesCompte() >= 0 || s.capsulesSurLui() >= 0;
        if (!bourses && !capsules) return;
        Ui.section(g, font, tr("gui.profil.section.sur_toi"), ix, py + 201, iw);
        int y = py + 211;
        if (bourses) {
            Morceau[] m = s.bourses() > 0
                    ? new Morceau[]{new Morceau(Ui.nombre(s.bourses()), Ui.OR, true), new Morceau(" " + (s.nombreBourses() == 1
                    ? tr("gui.profil.bourses.une") : tr("gui.profil.bourses.nombre", Ui.nombre(s.nombreBourses()))), Ui.MUTED, false)}
                    : new Morceau[]{new Morceau(tr("gui.profil.bourses.aucune"), Ui.MUTED, false)};
            ligne(g, ix, y, iw, 17, tr("gui.profil.bourses"), !capsules, m);
            y += 17;
        }
        if (capsules) {
            long compte = Math.max(0, s.capsulesCompte()), surLui = Math.max(0, s.capsulesSurLui());
            ligne(g, ix, y, iw, 17, tr("gui.profil.capsules"), true, new Morceau(Ui.nombre(compte), Ui.TEXT, true),
                    new Morceau(" " + tr("gui.profil.capsules.detail", Ui.nombre(surLui)), Ui.MUTED, false));
        }
        if (s.bourses() > 0) {
            List<FormattedCharSequence> l = font.split(Ui.lit(tr("gui.profil.bourses.alerte"), Ui.SMALL), iw);
            int ly = py + 260;
            for (int i = 0; i < Math.min(3, l.size()); i++, ly += 10) {
                g.pose().pushPose();
                Ui.placer(g, ix, ly - Ui.milieu(9f));
                g.drawString(font, l.get(i), 0, 0, Ui.argb(ROUGE_CLAIR, 255), false);
                g.pose().popPose();
            }
        }
    }

    /** Pied : touche Échap, aide (son profil), note à droite. */
    private void pied(GuiGraphics g) {
        int yp = py + ph - 12;
        Ui.hairline(g, px + 8, px + pw - 8, yp - 8, 0xFFFFFF, 22, false, false);
        int x = px + MARGE;
        String touche = tr("gui.profil.pied.echap").toUpperCase(Locale.ROOT);
        int tw = (int) Ui.spacedWidth(font, touche, Ui.SMALL, 0.8f) + 8;
        Ui.soft(g, x, yp - 5, tw, 10, Ui.argb(0xFFFFFF, 18));
        Ui.softOutline(g, x + 1, yp - 4, tw - 2, 8, Ui.argb(0xFFFFFF, 60));
        capitales(g, touche, x + 4, yp, Ui.TEXT, 0.8f, false);
        x += tw + 4;
        x += texte(g, tr("gui.fermer"), Ui.SMALL, x, yp, Ui.MUTED) + 12;
        String note = tr("gui.profil.pied.note");
        int noteW = font.width(Ui.lit(note, Ui.SMALL));
        int fin = px + pw - MARGE;
        if (v.moi() && x + font.width(Ui.lit(tr("gui.profil.pied.autre"), Ui.SMALL)) + 12 <= fin - noteW) {
            texte(g, tr("gui.profil.pied.autre"), Ui.SMALL, x, yp, Ui.MUTED);
        }
        if (x + 12 <= fin - noteW) texte(g, Ui.lit(note, Ui.SMALL), Ui.SMALL, fin, yp, Ui.argb(Ui.FAINT, 255), 1);
    }

    // ------------------------------------------------------------------ fermeture
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        Ui.son(Sons.UI_FERMER, 0.6f);
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
}
