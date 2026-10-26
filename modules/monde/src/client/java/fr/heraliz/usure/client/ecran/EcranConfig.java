package fr.heraliz.usure.client.ecran;

import fr.heraliz.usure.Contenu;
import fr.heraliz.usure.Reseau;
import fr.heraliz.usure.Usure;
import fr.heraliz.usure.client.kit.Echelle;
import fr.heraliz.usure.client.kit.HzButton;
import fr.heraliz.usure.client.kit.HzField;
import fr.heraliz.usure.client.kit.SonsUi;
import fr.heraliz.usure.client.kit.Ui;
import fr.heraliz.usure.regles.Categorie;
import fr.heraliz.usure.regles.Etat;
import fr.heraliz.usure.regles.Regles;
import fr.heraliz.usure.regles.Surcharge;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ecran d'administration ({@code /usure config}, op 2), style des ecrans de clan : cinq onglets (categories, etats,
 * reparation, armes et accessoires, general). On modifie une copie des regles ; « Enregistrer » l'envoie au
 * serveur qui verifie les droits, corrige les valeurs, reecrit {@code serverconfig/heraliz_usure-server.toml} et previent
 * tous les joueurs. Chiffres a virgule ou a point ; un champ vide d'une ligne d'arme = valeur de la categorie.
 */
public class EcranConfig extends Screen {
    private static final String[] ONGLETS = {"categories", "etats", "reparation", "armes", "general"};
    private static final Predicate<String> NOMBRE = s -> s.matches("[0-9]{0,4}([.,][0-9]{0,3})?");
    private static final Predicate<String> ENTIER = s -> s.matches("[0-9]{0,4}");
    private static final Predicate<String> IDENT = s -> s.matches("[a-z0-9_.:*/-]*");
    private static final int LIGNE = 21;

    private Regles original;
    private Regles travail;
    private int onglet;
    private final int[] defilement = new int[ONGLETS.length];
    private int px, py, pw, ph;
    private Zone zone;
    private HzButton boutonDefauts, boutonEnregistrer;
    private boolean modifie;
    private long confirmerDefautsJusqua, enregistreMs;
    /** Fermeture demandee avec des modifications pas enregistrees : un second appui avant cette heure ferme. */
    private long confirmerFermerJusqua;

    public EcranConfig(Regles regles) {
        super(Component.translatable("heraliz_usure.ecran.titre"));
        this.original = regles;
        this.travail = regles.copie();
    }

    /** Onglet ouvert (banc d'essai). */
    public EcranConfig onglet(int i) {
        onglet = Math.max(0, Math.min(ONGLETS.length - 1, i));
        return this;
    }

    private static String t(String cle, Object... args) {
        return Component.translatable(cle, args).getString();
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = Math.min(600, width - 24);
        ph = Math.min(340, height - 12);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        zone = new Zone(px + 12, py + 66, pw - 24, ph - 66 - 40);
        zone.accent(Ui.accent());
        addRenderableWidget(zone);
        int accent = Ui.accent();
        int by = py + ph - 28;
        boutonDefauts = addRenderableWidget(new HzButton(px + Ui.MARGE, by + 5, 150, 10, Component.translatable("heraliz_usure.ecran.defauts"), accent,
                this::defauts).look(HzButton.Look.LINK).danger());
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 210, by, 96, 18, Component.translatable("heraliz_usure.ecran.annuler"), accent, this::onClose));
        boutonEnregistrer = addRenderableWidget(new HzButton(px + pw - Ui.MARGE - 108, by, 108, 18, Component.translatable("heraliz_usure.ecran.enregistrer"),
                accent, this::enregistrer).look(HzButton.Look.PRIMARY));
        construire();
    }

    // ---------------------------------------------------------------- actions
    private void defauts() {
        long now = Util.getMillis();
        if (now > confirmerDefautsJusqua) {
            confirmerDefautsJusqua = now + 3000;
            boutonDefauts.setMessage(Component.translatable("heraliz_usure.ecran.defauts_confirmer"));
            return;
        }
        confirmerDefautsJusqua = 0;
        boutonDefauts.setMessage(Component.translatable("heraliz_usure.ecran.defauts"));
        travail = Regles.defauts();
        construire();
        maj();
    }

    private void enregistrer() {
        Reseau.envoyerAuServeur(new Reseau.EnregistrerRegles(travail.copie()));
        original = travail.copie();
        enregistreMs = Util.getMillis();
        maj();
    }

    private void maj() {
        modifie = !travail.memesQue(original);
    }

    // ---------------------------------------------------------------- construction des onglets
    private void construire() {
        if (zone == null) return;
        zone.clear();
        switch (onglet) {
            case 0 -> categories();
            case 1 -> etats();
            case 2 -> reparation();
            case 3 -> armes();
            default -> general();
        }
        zone.setScroll(defilement[onglet]);
    }

    private int largeurContenu() {
        return zone.getWidth() - 6;
    }

    /** Champ numerique (valeur obligatoire). */
    private HzField champ(int x, int y, int w, float valeur, String suffixe, Consumer<Float> change) {
        HzField f = new HzField(x, y, w, 14, 8, NOMBRE).suffix(suffixe).accent(Ui.accent()).erreurVisible(false);
        f.setValue(nombre(valeur));
        f.setResponder(s -> {
            Float v = lire(s);
            if (v == null) {
                f.setErreur("!");
            } else {
                f.setErreur("");
                change.accept(v);
                travail.viderCache();
                maj();
            }
        });
        return zone.add(f);
    }

    /** Champ numerique d'une ligne d'arme : vide = valeur de la categorie (affichee en gris). */
    private HzField champOptionnel(int x, int y, int w, float valeur, String heritee, Consumer<Float> change) {
        HzField f = new HzField(x, y, w, 14, 8, NOMBRE).accent(Ui.accent()).placeholder(heritee).erreurVisible(false).remplacerAuFocus(false);
        f.setValue(Float.isNaN(valeur) ? "" : nombre(valeur));
        f.setResponder(s -> {
            if (s.isEmpty()) {
                f.setErreur("");
                change.accept(Float.NaN);
            } else {
                Float v = lire(s);
                if (v == null) {
                    f.setErreur("!");
                    return;
                }
                f.setErreur("");
                change.accept(v);
            }
            travail.viderCache();
            maj();
        });
        return zone.add(f);
    }

    private static String nombre(float v) {
        String s = Regles.texte(v);
        return Ui.french() ? s.replace('.', ',') : s;
    }

    private static Float lire(String s) {
        if (s == null || s.isEmpty() || s.equals(".") || s.equals(",")) return null;
        try {
            float f = Float.parseFloat(s.replace(',', '.'));
            return Float.isFinite(f) && f >= 0 ? f : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Titres de colonnes en petites capitales. */
    private void entetes(int y, int[] xs, String[] titres) {
        zone.dessin((g, mx, my) -> {
            for (int i = 0; i < titres.length; i++) {
                Ui.spaced(g, font, titres[i], Ui.SMALL, xs[i], y, Ui.argb(Ui.MUTED, 255), 0.6f);
            }
        });
    }

    /** Fond de ligne (survol) sur toute la largeur. */
    private void fondLigne(int x, int y, int w, int h) {
        zone.dessin((g, mx, my) -> Cadre.ligne(g, x, y, w, h, Cadre.dedans(mx, my, x, y, w, h)));
    }

    private void texte(int x, int y, java.util.function.Supplier<String> texte, int couleur) {
        zone.dessin((g, mx, my) -> g.drawString(font, Ui.lit(texte.get(), Ui.FONT), x, y, Ui.argb(couleur, 255), false));
    }

    /** Paragraphe d'aide (petit, attenue), coupe a la largeur ; rend la hauteur. */
    private int aide(int x, int y, int w, String texte) {
        List<FormattedCharSequence> lignes = font.split(Ui.lit(texte, Ui.SMALL), w);
        zone.dessin((g, mx, my) -> {
            int ly = y;
            for (FormattedCharSequence l : lignes) {
                g.drawString(font, l, x, ly, Ui.argb(Ui.MUTED, 230), false);
                ly += 9;
            }
        });
        return lignes.size() * 9;
    }

    private void section(int x, int y, int w, String titre) {
        zone.dessin((g, mx, my) -> Ui.section(g, font, titre, x, y, w));
    }

    // ---------------------------------------------------------------- 1. categories
    private void categories() {
        int x0 = zone.getX(), y0 = zone.getY() + 2, cw = largeurContenu();
        int colNom = 150, nc = 5, colW = (cw - colNom) / nc;
        int[] xs = new int[nc + 1];
        xs[0] = x0 + 8;
        for (int j = 0; j < nc; j++) xs[j + 1] = x0 + colNom + j * colW + 2;
        entetes(y0, xs, new String[]{t("heraliz_usure.ecran.col.categorie"), t("heraliz_usure.ecran.col.usure"), t("heraliz_usure.ecran.col.tirs"),
                t("heraliz_usure.ecran.col.pluie"), t("heraliz_usure.ecran.col.eau"), t("heraliz_usure.ecran.col.silencieux")});
        int fw = Math.min(70, colW - 10);
        for (Categorie c : Categorie.TOUTES) {
            int i = c.ordinal();
            int y = y0 + 12 + i * LIGNE;
            fondLigne(x0, y, cw, LIGNE - 2);
            String nom = t(c.cleTraduction());
            texte(x0 + 8, y + 5, () -> nom, Ui.TEXT);
            champ(xs[1], y + 2, fw, travail.usure[i], "%", v -> travail.usure[i] = v);
            texte(xs[2] + 2, y + 5, () -> {
                float u = travail.usure[i];
                return u <= 0 ? "—" : Ui.nombre(Math.round(100f / u));
            }, Ui.MUTED);
            champ(xs[3], y + 2, fw, travail.pluie[i], "+%", v -> travail.pluie[i] = v);
            champ(xs[4], y + 2, fw, travail.eau[i], "+%", v -> travail.eau[i] = v);
            champ(xs[5], y + 2, fw, travail.silencieux[i], "+%", v -> travail.silencieux[i] = v);
        }
        int y = y0 + 12 + Categorie.TOUTES.length * LIGNE + 4;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.categories"));
        zone.setContentHeight(y - zone.getY() + 4);
    }

    // ---------------------------------------------------------------- 2. etats
    private void etats() {
        int x0 = zone.getX(), y0 = zone.getY() + 2, cw = largeurContenu();
        int[] xs = {x0 + 8, x0 + 170, x0 + 330};
        entetes(y0, xs, new String[]{t("heraliz_usure.ecran.col.etat"), t("heraliz_usure.ecran.col.a_partir"), t("heraliz_usure.ecran.col.exemple")});
        for (Etat e : Etat.TOUS) {
            int i = e.ordinal();
            int y = y0 + 12 + i * LIGNE;
            fondLigne(x0, y, cw, LIGNE - 2);
            String nom = t(e.cleTraduction());
            zone.dessin((g, mx, my) -> {
                Ui.soft(g, x0 + 8, y + 7, 5, 5, Ui.argb(e.couleur, 255));
                g.drawString(font, Ui.lit(nom, Ui.FONT), x0 + 18, y + 5, Ui.argb(Ui.TEXT, 255), false);
            });
            switch (e) {
                case NEUF -> champ(xs[1], y + 2, 70, travail.seuilNeuf, "%", v -> travail.seuilNeuf = v);
                case BON -> champ(xs[1], y + 2, 70, travail.seuilBon, "%", v -> travail.seuilBon = v);
                case USE -> champ(xs[1], y + 2, 70, travail.seuilUse, "%", v -> travail.seuilUse = v);
                case ABIME -> texte(xs[1] + 2, y + 5, () -> t("heraliz_usure.ecran.sous_use"), Ui.MUTED);
                case RUINE -> texte(xs[1] + 2, y + 5, () -> t("heraliz_usure.ecran.ruine_zero"), Ui.MUTED);
            }
            texte(xs[2] + 2, y + 5, () -> e == Etat.RUINE ? t("heraliz_usure.ecran.ne_tire_plus") : exemple(e), Ui.MUTED);
        }
        // echelle des etats de 0 a 100 %
        int y = y0 + 12 + Etat.TOUS.length * LIGNE + 8;
        int bx = x0 + 8, bw = cw - 16, ye = y;
        zone.dessin((g, mx, my) -> {
            int y1 = ye;
            float[] bornes = {0, travail.seuilUse, travail.seuilBon, travail.seuilNeuf, 100};
            Etat[] ordre = {Etat.ABIME, Etat.USE, Etat.BON, Etat.NEUF};
            for (int k = 0; k < 4; k++) {
                int a = bx + Math.round(bw * bornes[k] / 100f), b = bx + Math.round(bw * bornes[k + 1] / 100f);
                g.fill(a, y1, Math.max(a, b - 1), y1 + 4, Ui.argb(ordre[k].couleur, 200));
            }
            g.fill(bx - 1, y1 - 2, bx + 1, y1 + 6, Ui.argb(Etat.RUINE.couleur, 255));
            for (int k = 0; k <= 4; k++) {
                int a = bx + Math.round(bw * bornes[k] / 100f);
                String s = Regles.texte(bornes[k]) + " %";
                int lw = font.width(Ui.lit(s, Ui.SMALL));
                g.drawString(font, Ui.lit(s, Ui.SMALL), Math.max(bx, Math.min(bx + bw - lw, a - lw / 2)), y1 + 8, Ui.argb(Ui.MUTED, 255), false);
            }
        });
        y += 22;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.etats"));
        zone.setContentHeight(y - zone.getY() + 4);
    }

    /** « ≈ 312 tirs » : nombre de tirs qu'un fusil d'assaut passe dans cet etat (largeur de la tranche / usure par tir). */
    private String exemple(Etat e) {
        float haut = switch (e) {
            case NEUF -> 100;
            case BON -> travail.seuilNeuf;
            case USE -> travail.seuilBon;
            default -> travail.seuilUse;
        };
        float bas = switch (e) {
            case NEUF -> travail.seuilNeuf;
            case BON -> travail.seuilBon;
            case USE -> travail.seuilUse;
            default -> 0;
        };
        float u = travail.usure[Categorie.FUSIL.ordinal()];
        if (u <= 0) return t("heraliz_usure.ecran.jamais");
        return t("heraliz_usure.ecran.tirs_etat", Ui.nombre(Math.max(0, Math.round((haut - bas) / u))));
    }

    // ---------------------------------------------------------------- 3. reparation
    private void reparation() {
        int x0 = zone.getX(), y0 = zone.getY() + 2, cw = largeurContenu();
        // dernieres colonnes rapprochees : leurs titres russes restent avant la barre de defilement
        int[] xs = {x0 + 8, x0 + 190, x0 + 290, x0 + 380, x0 + 470};
        entetes(y0, xs, new String[]{t("heraliz_usure.ecran.col.kit"), t("heraliz_usure.ecran.col.gain"), t("heraliz_usure.ecran.col.plafond"),
                t("heraliz_usure.ecran.col.utilisations"), t("heraliz_usure.ecran.col.ruinee")});
        for (int k = 0; k < Regles.NB_KITS; k++) {
            int i = k;
            int y = y0 + 12 + k * (LIGNE + 3);
            fondLigne(x0, y, cw, LIGNE + 1);
            ItemStack icone = new ItemStack(Contenu.KITS.get(k).get());
            zone.dessin((g, mx, my) -> {
                g.renderItem(icone, x0 + 6, y + 3);
                g.drawString(font, Ui.in(icone.getHoverName(), Ui.FONT), x0 + 26, y + 7, Ui.argb(Ui.TEXT, 255), false);
            });
            champ(xs[1], y + 4, 70, travail.kitGain[i], "%", v -> travail.kitGain[i] = v);
            champ(xs[2], y + 4, 70, travail.kitPlafond[i], "%", v -> travail.kitPlafond[i] = v);
            HzField u = new HzField(xs[3], y + 4, 70, 14, 4, ENTIER).accent(Ui.accent()).erreurVisible(false);
            u.setValue(String.valueOf(travail.kitUtilisations[i]));
            u.setResponder(s -> {
                try {
                    int v = Integer.parseInt(s);
                    if (v < 1) throw new NumberFormatException();
                    u.setErreur("");
                    travail.kitUtilisations[i] = v;
                    maj();
                } catch (NumberFormatException ex) {
                    u.setErreur("!");
                }
            });
            zone.add(u);
            zone.add(new Interrupteur(xs[4], y + 4, 70, 14, () -> travail.kitRuinee[i], v -> {
                travail.kitRuinee[i] = v;
                maj();
            }));
        }
        int y = y0 + 12 + Regles.NB_KITS * (LIGNE + 3) + 10;
        section(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.section.max"));
        y += 12;
        fondLigne(x0, y, cw, LIGNE - 2);
        texte(x0 + 8, y + 5, () -> t("heraliz_usure.ecran.perte_max"), Ui.TEXT);
        champ(xs[3], y + 2, 70, travail.perteMaxParReparation, "%", v -> travail.perteMaxParReparation = v);
        y += LIGNE;
        fondLigne(x0, y, cw, LIGNE - 2);
        texte(x0 + 8, y + 5, () -> t("heraliz_usure.ecran.max_minimum"), Ui.TEXT);
        champ(xs[3], y + 2, 70, travail.maxMinimum, "%", v -> travail.maxMinimum = v);
        y += LIGNE + 6;
        int ye = y;
        zone.dessin((g, mx, my) -> g.drawString(font, Ui.lit(exempleReparation(), Ui.SMALL), x0 + 8, ye, Ui.argb(Ui.OR, 230), false));
        y += 12;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.reparation"));
        zone.setContentHeight(y - zone.getY() + 4);
    }

    /** Exemple calcule avec les valeurs en cours : une arme a 30 % reparee avec le kit standard. */
    private String exempleReparation() {
        fr.heraliz.usure.Reparation.Calcul c = fr.heraliz.usure.Reparation.calculer(300, 1000, 1, travail);
        return t("heraliz_usure.ecran.exemple_reparation", t("item.heraliz_usure.kit_standard"), Math.round(c.etat() / 10f), Math.round(c.max() / 10f));
    }

    // ---------------------------------------------------------------- 4. armes et accessoires
    private void armes() {
        int x0 = zone.getX(), y0 = zone.getY() + 2, cw = largeurContenu();
        int accent = Ui.accent();
        section(x0 + 8, y0 + 2, cw - 220, t("heraliz_usure.ecran.section.armes"));
        zone.add(new HzButton(x0 + cw - 206, y0, 104, 12, Component.translatable("heraliz_usure.ecran.arme_en_main"), accent, this::ajouterArmeEnMain)
                .look(HzButton.Look.PILL));
        zone.add(new HzButton(x0 + cw - 96, y0, 90, 12, Component.translatable("heraliz_usure.ecran.ajouter"), accent, () -> {
            travail.surcharges.add(new Surcharge(""));
            reconstruire();
        }).look(HzButton.Look.PILL));
        int y = y0 + 18;
        int idW = 150, catW = 84, nW = 44;
        int xCat = x0 + 6 + idW + 6, xN = xCat + catW + 6, xEx = xN + 4 * (nW + 4) + 4, xDel = x0 + cw - 16;
        entetes(y, new int[]{x0 + 8, xCat, xN, xN + (nW + 4), xN + 2 * (nW + 4), xN + 3 * (nW + 4), xEx},
                new String[]{t("heraliz_usure.ecran.col.arme"), t("heraliz_usure.ecran.col.categorie"), t("heraliz_usure.ecran.col.usure_court"),
                        t("heraliz_usure.ecran.col.pluie"), t("heraliz_usure.ecran.col.eau"), t("heraliz_usure.ecran.col.silencieux_court"),
                        t("heraliz_usure.ecran.col.exclue")});
        y += 12;
        String[] options = new String[Categorie.TOUTES.length + 1];
        options[0] = t("heraliz_usure.ecran.auto");
        for (Categorie c : Categorie.TOUTES) options[c.ordinal() + 1] = t(c.cleTraduction() + ".court");
        if (travail.surcharges.isEmpty()) {
            texte(x0 + 8, y + 5, () -> t("heraliz_usure.ecran.aucune_surcharge"), Ui.FAINT);
            y += LIGNE;
        }
        for (Surcharge s : new ArrayList<>(travail.surcharges)) {
            int ly = y;
            fondLigne(x0, ly, cw, LIGNE - 2);
            HzField id = new HzField(x0 + 6, ly + 2, idW, 14, 80, IDENT).accent(accent).placeholder("mod:arme").erreurVisible(false).remplacerAuFocus(false);
            id.transform(v -> v.toLowerCase(Locale.ROOT));
            id.setValue(s.motif);
            id.setResponder(v -> {
                s.motif = v.trim();
                travail.viderCache();
                maj();
            });
            zone.add(id);
            zone.add(new Selecteur(xCat, ly + 2, catW, 14, options, () -> s.categorie == null ? 0 : s.categorie.ordinal() + 1, i -> {
                s.categorie = i == 0 ? null : Categorie.TOUTES[i - 1];
                travail.viderCache();
                maj();
                reconstruire();
            }));
            Categorie base = s.categorie != null ? s.categorie : categorieAuto(s);
            int c = base == null ? -1 : base.ordinal();
            champOptionnel(xN, ly + 2, nW, s.usure, c < 0 ? "-" : nombre(travail.usure[c]), v -> s.usure = v);
            champOptionnel(xN + (nW + 4), ly + 2, nW, s.pluie, c < 0 ? "-" : nombre(travail.pluie[c]), v -> s.pluie = v);
            champOptionnel(xN + 2 * (nW + 4), ly + 2, nW, s.eau, c < 0 ? "-" : nombre(travail.eau[c]), v -> s.eau = v);
            champOptionnel(xN + 3 * (nW + 4), ly + 2, nW, s.silencieux, c < 0 ? "-" : nombre(travail.silencieux[c]), v -> s.silencieux = v);
            zone.add(new Interrupteur(xEx, ly + 2, 26, 14, () -> s.exclue, v -> {
                s.exclue = v;
                travail.viderCache();
                maj();
            }).sansEtiquette());
            zone.add(new Croix(xDel, ly + 3, 12, true, () -> {
                travail.surcharges.remove(s);
                travail.viderCache();
                maj();
                reconstruire();
            }));
            y += LIGNE;
        }
        y += 4;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.armes"));
        y += 10;
        // accessoires
        section(x0 + 8, y + 2, cw - 120, t("heraliz_usure.ecran.section.accessoires"));
        zone.add(new HzButton(x0 + cw - 96, y, 90, 12, Component.translatable("heraliz_usure.ecran.ajouter"), accent, () -> {
            String cle = "";
            int n = 1;
            while (travail.accessoires.containsKey(cle)) cle = "accessoire_" + n++;
            travail.accessoires.put(cle, 10f);
            reconstruire();
        }).look(HzButton.Look.PILL));
        y += 18;
        if (travail.accessoires.isEmpty()) {
            texte(x0 + 8, y + 5, () -> t("heraliz_usure.ecran.aucun_accessoire"), Ui.FAINT);
            y += LIGNE;
        }
        for (Map.Entry<String, Float> e : new ArrayList<>(travail.accessoires.entrySet())) {
            int ly = y;
            String[] cle = {e.getKey()};
            fondLigne(x0, ly, cw, LIGNE - 2);
            HzField id = new HzField(x0 + 6, ly + 2, 220, 14, 80, IDENT).accent(accent).placeholder("tacz:muzzle_...").erreurVisible(false)
                    .remplacerAuFocus(false);
            id.transform(v -> v.toLowerCase(Locale.ROOT));
            id.setValue(cle[0]);
            id.setResponder(v -> {
                String n = v.trim();
                if (n.equals(cle[0])) return;
                if (travail.accessoires.containsKey(n)) {
                    id.setErreur("!");
                    return;
                }
                id.setErreur("");
                Float val = travail.accessoires.remove(cle[0]);
                travail.accessoires.put(n, val == null ? 0f : val);
                cle[0] = n;
                maj();
            });
            zone.add(id);
            champ(x0 + 236, ly + 2, 70, e.getValue(), "+%", v -> travail.accessoires.put(cle[0], v));
            zone.add(new Croix(xDel, ly + 3, 12, true, () -> {
                travail.accessoires.remove(cle[0]);
                maj();
                reconstruire();
            }));
            y += LIGNE;
        }
        y += 4;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.accessoires"));
        zone.setContentHeight(y - zone.getY() + 6);
    }

    /** Categorie que TaCZ donnerait a l'arme de la ligne (null pour un groupe « * » ou une arme inconnue). */
    private Categorie categorieAuto(Surcharge s) {
        if (s.groupe() || s.motif.isEmpty()) return null;
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(s.motif);
            if (rl == null) return null;
            var index = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(rl).orElse(null);
            if (index == null) return null;
            return travail.categorieAuto(index.getType() == null ? "" : index.getType().toLowerCase(Locale.ROOT), index.getGunData());
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    private void ajouterArmeEnMain() {
        var p = minecraft == null ? null : minecraft.player;
        String id = p == null ? null : Usure.gunId(p.getMainHandItem());
        if (id == null) {
            SonsUi.clic(0.3F);
            return;
        }
        for (Surcharge s : travail.surcharges) if (s.motif.equals(id)) return;
        travail.surcharges.add(new Surcharge(id));
        travail.viderCache();
        maj();
        reconstruire();
    }

    // ---------------------------------------------------------------- 5. general
    private void general() {
        int x0 = zone.getX(), y0 = zone.getY() + 2, cw = largeurContenu();
        int y = y0;
        y = interrupteur(x0, y, cw, "actif", () -> travail.actif, v -> travail.actif = v);
        y = interrupteur(x0, y, cw, "creatif", () -> travail.usureCreatif, v -> travail.usureCreatif = v);
        y = interrupteur(x0, y, cw, "match", () -> travail.sansUsureEnMatch, v -> travail.sansUsureEnMatch = v);
        y = interrupteur(x0, y, cw, "hud", () -> travail.hudAutorise, v -> travail.hudAutorise = v);
        y = liste(x0, y, cw, "dimensions", travail.dimensionsSansUsure, l -> travail.dimensionsSansUsure = l);
        y = liste(x0, y, cw, "munitions", travail.munitionsMelee, l -> travail.munitionsMelee = l);
        y += 4;
        y += aide(x0 + 8, y, cw - 16, t("heraliz_usure.ecran.aide.general"));
        zone.setContentHeight(y - zone.getY() + 4);
    }

    private int interrupteur(int x0, int y, int cw, String cle, java.util.function.BooleanSupplier valeur, Consumer<Boolean> change) {
        int h = 26;
        fondLigne(x0, y, cw, h);
        String nom = t("heraliz_usure.ecran.general." + cle), aide = t("heraliz_usure.ecran.general." + cle + ".aide");
        zone.dessin((g, mx, my) -> {
            g.drawString(font, Ui.lit(nom, Ui.FONT), x0 + 8, y + 4, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.lit(Ui.ellipsis(font, aide, Ui.SMALL, cw - 120), Ui.SMALL), x0 + 8, y + 15, Ui.argb(Ui.MUTED, 255), false);
        });
        zone.add(new Interrupteur(x0 + cw - 90, y + 6, 82, 14, valeur, v -> {
            change.accept(v);
            travail.viderCache();
            maj();
        }));
        return y + h + 3;
    }

    private int liste(int x0, int y, int cw, String cle, List<String> valeurs, Consumer<List<String>> change) {
        int h = 26;
        fondLigne(x0, y, cw, h);
        String nom = t("heraliz_usure.ecran.general." + cle), aide = t("heraliz_usure.ecran.general." + cle + ".aide");
        zone.dessin((g, mx, my) -> {
            g.drawString(font, Ui.lit(nom, Ui.FONT), x0 + 8, y + 4, Ui.argb(Ui.TEXT, 255), false);
            g.drawString(font, Ui.lit(Ui.ellipsis(font, aide, Ui.SMALL, cw / 2 - 20), Ui.SMALL), x0 + 8, y + 15, Ui.argb(Ui.MUTED, 255), false);
        });
        HzField f = new HzField(x0 + cw / 2, y + 6, cw / 2 - 8, 14, 512, s -> s.matches("[a-z0-9_.:/, -]*")).accent(Ui.accent())
                .erreurVisible(false).remplacerAuFocus(false);
        f.transform(v -> v.toLowerCase(Locale.ROOT));
        f.setValue(String.join(", ", valeurs));
        f.setResponder(s -> {
            List<String> l = new ArrayList<>();
            for (String p : s.split("[, ]+")) if (!p.isBlank()) l.add(p.trim());
            change.accept(l);
            travail.corriger();
            maj();
        });
        zone.add(f);
        return y + h + 3;
    }

    private void reconstruire() {
        defilement[onglet] = zone.getScroll();
        construire();
    }

    // ---------------------------------------------------------------- rendu
    @Override
    public void tick() {
        if (zone != null) zone.tickChamps();
        if (confirmerDefautsJusqua > 0 && Util.getMillis() > confirmerDefautsJusqua) {
            confirmerDefautsJusqua = 0;
            boutonDefauts.setMessage(Component.translatable("heraliz_usure.ecran.defauts"));
        }
        if (boutonEnregistrer != null) boutonEnregistrer.active = modifie;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Cadre.panneau(g, font, width, height, px, py, pw, ph, title.getString(), t("heraliz_usure.ecran.sous_titre"));
        Cadre.fermer(g, px + pw - Ui.MARGE - 7, py + 14, mouseX, mouseY);
        int tw = (int) Ui.spacedWidth(font, title.getString().toUpperCase(Locale.ROOT), Ui.BOLD, 1.6f);
        if (modifie && Util.getMillis() < confirmerFermerJusqua) {
            Ui.chip(g, font, t("heraliz_usure.ecran.quitter_confirmer"), px + Ui.MARGE + tw + 10, py + 13, Ui.DANGER);
        } else if (modifie) Ui.chip(g, font, t("heraliz_usure.ecran.modifie"), px + Ui.MARGE + tw + 10, py + 13, Ui.AMBRE);
        else if (Util.getMillis() - enregistreMs < 2500) Ui.chip(g, font, t("heraliz_usure.ecran.enregistre_court"), px + Ui.MARGE + tw + 10, py + 13, Ui.OK);
        // onglets
        int tx = px + Ui.MARGE, accent = Ui.accent();
        for (int i = 0; i < ONGLETS.length; i++) {
            String nom = t("heraliz_usure.ecran.onglet." + ONGLETS[i]);
            int w = (int) Ui.spacedWidth(font, nom.toUpperCase(Locale.ROOT), Ui.BOLD, 1.0f);
            boolean over = Cadre.dedans(mouseX, mouseY, tx - 4, py + 44, w + 8, 16);
            Ui.spaced(g, font, nom, Ui.BOLD, tx, py + 48, Ui.argb(i == onglet ? 0xFFFFFF : over ? Ui.TEXT : Ui.MUTED, 255), 1.0f);
            if (i == onglet) g.fill(tx, py + 58, tx + w, py + 59, Ui.argb(accent, 230));
            tx += w + 18;
        }
        Ui.hairline(g, px + Ui.MARGE, px + pw - Ui.MARGE, py + ph - 36, 0xFFFFFF, 30, true, true);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        if (bouton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (Cadre.dedans(mx, my, px + pw - Ui.MARGE - 10, py + 11, 13, 13)) {
                SonsUi.clic(0.6F);
                onClose();
                return true;
            }
            int tx = px + Ui.MARGE;
            for (int i = 0; i < ONGLETS.length; i++) {
                int w = (int) Ui.spacedWidth(font, t("heraliz_usure.ecran.onglet." + ONGLETS[i]).toUpperCase(Locale.ROOT), Ui.BOLD, 1.0f);
                if (Cadre.dedans(mx, my, tx - 4, py + 44, w + 8, 16)) {
                    changerOnglet(i);
                    return true;
                }
                tx += w + 18;
            }
        }
        return super.mouseClicked(mx, my, bouton);
    }

    private void changerOnglet(int i) {
        if (i == onglet) return;
        defilement[onglet] = zone.getScroll();
        onglet = i;
        setFocused(null);
        SonsUi.clic(0.6F);
        construire();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && zone != null && zone.getFocused() instanceof HzField) {
            zone.setFocused((GuiEventListener) null); // Echap dans un champ : quitte d'abord le champ
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && (zone == null || zone.getFocused() == null)) {
            changerOnglet((onglet + 1) % ONGLETS.length);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Echap, croix ou « Fermer » : avec des modifications pas enregistrees, il faut confirmer par un second appui. */
    @Override
    public void onClose() {
        long now = Util.getMillis();
        if (modifie && now > confirmerFermerJusqua) {
            confirmerFermerJusqua = now + 3000;
            return;
        }
        confirmerFermerJusqua = 0;
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
