package fr.heraliz.clashwar.moteur.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.clashwar.client.kit.TexteNet;
import fr.heraliz.clashwar.moteur.MoteurSons;
import fr.heraliz.clashwar.moteur.api.Kit;
import fr.heraliz.clashwar.moteur.net.PaquetsMoteur;
import fr.heraliz.clashwar.reseau.MatchsNet;
import fr.heraliz.noyau.client.kit.Bandeau;
import fr.heraliz.noyau.client.kit.Echelle;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.SonsUi;
import fr.heraliz.noyau.client.kit.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ecran de choix de classe (maquette validee le 19/11 : maquettes/clashwar/1a). Panneau de verre au centre (largeur des
 * ecrans de clan) :
 * <ul>
 *     <li>en-tete : trait de la couleur de l'equipe, « Choisis ta classe », ligne du serveur (tour et manche), score du
 *     match (mon equipe d'abord, {@link MoteurClient#score}), anneau du temps et secondes (rouge a 3 s) ;</li>
 *     <li>une carte par classe : nom, touche, role ({@code matchs.classe.<classe>.role}), image de l'arme principale,
 *     son nom (deux lignes au plus), arme secondaire, objets propres a la classe (grenades...), jauges portee / cadence
 *     / mobilite, disponibilite dans l'equipe (« Ton choix », « Choisie par », « Prise par » : carte voilee, « 1 par
 *     equipe », « Libre ») ;</li>
 *     <li>ce que toutes les classes ont en commun (tenue, arme blanche, soins), une seule fois ;</li>
 *     <li>pied : touches, coequipiers qui n'ont pas encore choisi, classe donnee sans choix a la fin du temps.</li>
 * </ul>
 * Choix au clic ou aux touches 1 a 9 ; le serveur valide (limites) et renvoie l'etat ; l'ecran se ferme a la fin du
 * choix. Echelle forcee (canevas 640 x 360). Textes nets a leur vraie taille ({@link TexteNet}), prepares a chaque
 * changement d'etat ({@link MoteurClient#versionChoix()}) ; a chaque image, seul le nombre de secondes peut changer.
 */
public class ChoixClasseScreen extends Screen {
    // ------------------------------------------------------------------ mesures (unites, maquette 1a)
    private static final int PW = 560, PH = 286, M = 16, ENTETE = 50;
    private static final int CARTE_W = 100, CARTE_H = 168, ECART = 7, PAD = 8;
    private static final int IMG_Y = 30, IMG_H = 52, ICONE = 56, ICONE_2 = 13, ICONE_PROPRE = 10, ICONE_COMMUN = 12;
    private static final float ANNEAU_R = 12f, ANNEAU_EP = 1.5f;
    private static final int VERRE_ALPHA = 214, BORD_ALPHA = 26, LISERE_ALPHA = 41, VOILE_ALPHA = 150;
    /** Couleurs (palette du noyau). */
    private static final int TEXTE = Palette.TEXTE, ATTENUE = Bandeau.ATTENUE, FAIBLE = Bandeau.FAIBLE, MUTED = Palette.ATTENUE,
            TRES_FAIBLE = Palette.TRES_ATTENUE, ROUGE = Ui.DANGER, JAUGE = 0xD9DDE4;
    /** Polices (vraie taille) : titre, nom de classe et secondes. */
    private static final ResourceLocation F_TITRE = HudMoteur.gras(17), F_NOM = HudMoteur.gras(14);
    /** Pieces de la tenue de l'Armurerie dont l'image represente la tenue (premiere trouvee). */
    private static final String[] PIECES_ICONE = {"chest", "vest", "head", "legs"};

    /** Une carte de classe, preparee a chaque changement d'etat. */
    private static final class Carte {
        Kit kit;
        ItemStack arme = ItemStack.EMPTY, arme2 = ItemStack.EMPTY;
        final List<ItemStack> propres = new ArrayList<>();
        final TexteNet nom = TexteNet.ui(F_NOM, 14), role = TexteNet.ui(Ui.SMALL, 9), touche = TexteNet.hud(PoliceHud.MINI_GRAS);
        final TexteNet arme1 = TexteNet.ui(Ui.BOLD, 11), arme1b = TexteNet.ui(Ui.BOLD, 11), secondaire = TexteNet.ui(Ui.SMALL, 9);
        final TexteNet dispo = TexteNet.ui(Ui.SMALL, 9), dispoGras = TexteNet.hud(PoliceHud.MINI_GRAS);
        boolean deuxLignes, mien, pleine;
        /** Disponibilite : 0 libre / limite, 1 choisie par un coequipier, 2 prise (limite atteinte), 3 mon choix. */
        int etatDispo;
    }

    /** Objet de l'equipement (tenue, arme blanche, soin, grenade) : cle de comparaison entre classes, image, nom. */
    private record Objet(String cle, ItemStack pile, String nom) {
    }

    private int version = -1;
    private Carte[] cartes = new Carte[0];
    private final List<ItemStack> communsPiles = new ArrayList<>();
    private final List<TexteNet> communsNoms = new ArrayList<>();
    private int couleur = Palette.INFO;
    private String monKit = "";
    private String enAttente = "";
    private float colJauge = 36;
    private boolean score;
    private int couleurMoi, couleurAutre;
    private boolean attente, defautVisible;
    private int nombreTouches;
    private int secondesMontrees = -1;
    // textes de l'en-tete et du pied
    private final TexteNet titre = TexteNet.ui(F_TITRE, 17), sousTitre = TexteNet.ui(Ui.FONT, 11);
    private final TexteNet secondes = TexteNet.ui(F_NOM, 14);
    private final TexteNet tagMoi = TexteNet.hud(PoliceHud.MINI_GRAS), tagAutre = TexteNet.hud(PoliceHud.MINI_GRAS),
            scoreTexte = TexteNet.ui(Ui.BOLD, 11);
    private final TexteNet portee = TexteNet.hud(PoliceHud.MINI), cadence = TexteNet.hud(PoliceHud.MINI),
            mobilite = TexteNet.hud(PoliceHud.MINI);
    private final TexteNet communs = TexteNet.ui(Ui.SMALL, 9);
    private final TexteNet touches = TexteNet.hud(PoliceHud.MINI_GRAS), aide = TexteNet.ui(Ui.SMALL, 9),
            attenteTexte = TexteNet.ui(Ui.SMALL, 9), defaut = TexteNet.ui(Ui.SMALL, 9);

    public ChoixClasseScreen() {
        super(Component.translatable("matchs.choix.titre"));
    }

    @Override
    protected void init() {
        Echelle.forcer(this);
        version = -1;
        preparer();
    }

    @Override
    public void removed() {
        Echelle.restaurer();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Choix impose : Echap ne ferme pas tant qu'un choix est en cours (le serveur ferme l'ecran a la fin du choix). */
    @Override
    public boolean shouldCloseOnEsc() {
        return MoteurClient.choix() == null;
    }

    @Override
    public void tick() {
        if (MoteurClient.versionChoix() != version) preparer();
    }

    // ------------------------------------------------------------------ preparation (changement d'etat seulement)
    private void preparer() {
        version = MoteurClient.versionChoix();
        PaquetsMoteur.ChoixOuvrirS2C c = MoteurClient.choix();
        if (c == null) return;
        List<Kit> kits = c.kits();
        int n = kits.size();
        couleur = c.couleur() & 0xFFFFFF;
        PaquetsMoteur.ChoixEtatS2C etat = MoteurClient.choixEtat();
        monKit = etat == null ? "" : etat.monKit();
        if (!monKit.isEmpty()) enAttente = "";
        List<PaquetsMoteur.Pris> pris = etat == null ? List.of() : etat.pris();
        List<String> enCours = etat == null ? List.of() : etat.attente();
        // aucun coequipier (ni choix, ni attente) : on joue seul, les limites par equipe ne comptent pas
        boolean seul = etat != null && pris.isEmpty() && enCours.isEmpty();
        Map<String, Integer> lim = c.limites();
        int cw = largeurCarte(n), inner = cw - 2 * PAD;

        // en-tete
        titre.texte(I18n.get("matchs.choix.entete"));
        sousTitre.texte(c.titre().getString(), PW - 2 * M - 11 - 150);
        MoteurClient.Score sc = MoteurClient.score.get();
        score = sc != null;
        if (sc != null) {
            tagMoi.texte(sc.tagMoi());
            tagAutre.texte(sc.tagAutre());
            scoreTexte.texte(I18n.get("matchs.choix.score", sc.scoreMoi(), sc.scoreAutre()));
            couleurMoi = sc.couleurMoi() & 0xFFFFFF;
            couleurAutre = sc.couleurAutre() & 0xFFFFFF;
        }
        // jauges : colonne des libelles a la largeur du plus long (langues)
        portee.texte(I18n.get("matchs.choix.portee"));
        cadence.texte(I18n.get("matchs.choix.cadence"));
        mobilite.texte(I18n.get("matchs.choix.mobilite"));
        colJauge = Math.min(inner - 20, Math.max(36, (float) Math.ceil(Math.max(portee.largeur(), Math.max(cadence.largeur(), mobilite.largeur()))) + 6));

        // equipement : ce que toutes les classes ont est montre une fois sous les cartes
        List<List<Objet>> objets = new ArrayList<>(n);
        for (Kit k : kits) objets.add(objets(k));
        Set<String> tous = null;
        for (List<Objet> l : objets) {
            Set<String> cles = new HashSet<>();
            for (Objet o : l) cles.add(o.cle());
            if (tous == null) tous = cles;
            else tous.retainAll(cles);
        }
        if (tous == null) tous = Set.of();
        communsPiles.clear();
        communsNoms.clear();
        if (n > 0) {
            for (Objet o : objets.get(0)) {
                if (!tous.contains(o.cle())) continue;
                communsPiles.add(o.pile());
                communsNoms.add(TexteNet.ui(Ui.SMALL, 9).texte(o.nom()));
            }
        }
        communs.texte(I18n.get("matchs.choix.communs"));

        // cartes
        if (cartes.length != n) {
            cartes = new Carte[n];
            for (int i = 0; i < n; i++) cartes[i] = new Carte();
        }
        boolean francais = Ui.french();
        String defautNom = null;
        for (int i = 0; i < n; i++) {
            Kit k = kits.get(i);
            Carte ca = cartes[i];
            ca.kit = k;
            ca.arme = i < c.apercus().size() ? c.apercus().get(i) : ItemStack.EMPTY;
            ca.arme2 = i < c.apercus2().size() ? c.apercus2().get(i) : ItemStack.EMPTY;
            ca.propres.clear();
            for (Objet o : objets.get(i)) if (!tous.contains(o.cle()) && !o.pile().isEmpty()) ca.propres.add(o.pile());
            ca.nom.texte(k.nom(francais), inner - 14);
            String cleRole = "matchs.classe." + k.classe() + ".role";
            ca.role.texte(I18n.exists(cleRole) ? I18n.get(cleRole) : "", inner);
            ca.touche.texte(Integer.toString(i + 1));
            // arme principale : nom du jeu (pack d'armes), deux lignes au plus
            String p = k.principale() == null ? "" : !ca.arme.isEmpty() ? nomCourt(ca.arme) : nomArme(k.principale().gunId());
            List<String> lignes = ca.arme1.lignes(p, inner, 2);
            ca.arme1.texte(lignes.isEmpty() ? "" : lignes.get(0));
            ca.arme1b.texte(lignes.size() > 1 ? lignes.get(1) : "");
            ca.deuxLignes = lignes.size() > 1;
            String s = k.secondaire() == null ? "" : !ca.arme2.isEmpty() ? nomCourt(ca.arme2) : nomArme(k.secondaire().gunId());
            ca.secondaire.texte(s, inner - 17 - 11 * Math.min(3, ca.propres.size()));
            // disponibilite dans mon equipe
            ca.mien = k.id().equals(monKit) || (monKit.isEmpty() && k.id().equals(enAttente));
            Integer max = lim.get(k.classe());
            int prisClasse = 0;
            StringBuilder parClasse = new StringBuilder(), parKit = new StringBuilder();
            for (PaquetsMoteur.Pris pr : pris) {
                if (pr.classe().equals(k.classe())) {
                    prisClasse++;
                    if (parClasse.length() > 0) parClasse.append(", ");
                    parClasse.append(pr.nom());
                }
                if (pr.kit().equals(k.id())) {
                    if (parKit.length() > 0) parKit.append(", ");
                    parKit.append(pr.nom());
                }
            }
            boolean pleineEquipe = max != null && prisClasse >= max;
            ca.pleine = pleineEquipe && !ca.mien;
            if (ca.mien) {
                ca.etatDispo = 3;
                ca.dispoGras.texte(I18n.get("matchs.choix.ton_choix"), inner - 9);
            } else if (ca.pleine) {
                ca.etatDispo = 2;
                ca.dispo.texte(I18n.get("matchs.choix.pris_par", parClasse.toString()), inner);
            } else if (parKit.length() > 0) {
                ca.etatDispo = 1;
                ca.dispo.texte(I18n.get("matchs.choix.choisie_par", parKit.toString()), inner - 6);
            } else {
                ca.etatDispo = 0;
                ca.dispo.texte(max != null && !seul ? I18n.get("matchs.choix.limite", max) : I18n.get("matchs.choix.libre"), inner);
            }
            // classe donnee a la fin du temps : la premiere dont la classe n'est pas pleine dans l'equipe (comme le serveur)
            if (defautNom == null && !pleineEquipe) defautNom = k.nom(francais);
        }
        if (defautNom == null && n > 0) defautNom = kits.get(0).nom(francais);

        // pied
        nombreTouches = Math.min(9, n);
        touches.texte(I18n.get("matchs.choix.touches", nombreTouches));
        aide.texte(I18n.get("matchs.choix.aide"));
        attente = !seul && !enCours.isEmpty();
        defautVisible = monKit.isEmpty() && defautNom != null;
        defaut.texte(defautVisible ? I18n.get("matchs.choix.defaut", defautNom) : "", 170);
        float placeAttente = PW - 2 * M - (touches.largeur() + 8 + 6 + aide.largeur() + 18) - 7 - (defaut.largeur() + 14);
        attenteTexte.texte(attente ? I18n.get("matchs.choix.attente", String.join(", ", enCours)) : "", Math.max(20, placeAttente));
    }

    private static int largeurCarte(int n) {
        if (n <= 0) return CARTE_W;
        return Math.min(CARTE_W, (PW - 2 * M - (n - 1) * ECART) / n);
    }

    /** Equipement du kit hors armes a feu : tenue, arme blanche, soins, grenades (piles du registre, noms du jeu). */
    private static List<Objet> objets(Kit k) {
        List<Objet> l = new ArrayList<>();
        ItemStack tenue = tenue(k);
        if (!tenue.isEmpty()) l.add(new Objet("armure:" + (k.armureKit() == null ? String.join(",", k.armurePieces()) : k.armureKit()),
                tenue, nomTenue(k, tenue)));
        if (k.melee() != null) {
            ItemStack st = pile(k.melee(), 1);
            if (!st.isEmpty()) l.add(new Objet("melee:" + k.melee(), st, nomCourt(st)));
        }
        for (Kit.Objet o : k.soins()) {
            ItemStack st = pile(o.id(), o.nombre());
            if (!st.isEmpty()) l.add(new Objet("soin:" + o.id() + ":" + o.nombre(), st, nombre(nomCourt(st), o.nombre())));
        }
        for (Kit.Objet o : k.grenades()) {
            ItemStack st = pile(o.id(), o.nombre());
            if (!st.isEmpty()) l.add(new Objet("grenade:" + o.id() + ":" + o.nombre(), st, nombre(nomCourt(st), o.nombre())));
        }
        return l;
    }

    private static String nombre(String nom, int n) {
        return n <= 1 ? nom : I18n.get("matchs.choix.nombre", nom, n);
    }

    /** Pile d'un objet du registre, vide s'il est inconnu. */
    private static ItemStack pile(String id, int nombre) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, Math.min(64, nombre)));
    }

    /** Image de la tenue : une piece de l'ensemble de l'Armurerie ({@code kit_<ensemble>}), sinon la premiere piece listee. */
    private static ItemStack tenue(Kit k) {
        String ens = ensemble(k);
        if (ens != null) {
            for (String piece : PIECES_ICONE) {
                ItemStack st = pile("heraliz_armory:" + ens + "_" + piece, 1);
                if (!st.isEmpty()) return st;
            }
        }
        for (String p : k.armurePieces()) {
            ItemStack st = pile(p, 1);
            if (!st.isEmpty()) return st;
        }
        return ItemStack.EMPTY;
    }

    /** « siberie » pour « kit_siberie », « siberie » ou « heraliz_armory:kit_siberie » ; null sans tenue. */
    private static String ensemble(Kit k) {
        String kit = k.armureKit();
        if (kit == null || kit.isBlank()) return null;
        String id = kit.trim().toLowerCase(Locale.ROOT);
        int ns = id.indexOf(':');
        if (ns >= 0) id = id.substring(ns + 1);
        return id.startsWith("kit_") ? id.substring(4) : id;
    }

    /** Nom de la tenue : celui de l'objet {@code kit_<ensemble>} de l'Armurerie (« Tenue · Siberie »), sinon de la piece. */
    private static String nomTenue(Kit k, ItemStack piece) {
        String ens = ensemble(k);
        String cle = ens == null ? null : "item.heraliz_armory.kit_" + ens;
        return cle != null && I18n.exists(cle) ? I18n.get(cle) : nomCourt(piece);
    }

    /** Nom affiche d'un objet, sans codes de couleur ni nom de skin (« Dague TAC | Ember Shard » -> « Dague TAC »). */
    private static String nomCourt(ItemStack st) {
        String s = ChatFormatting.stripFormatting(st.getHoverName().getString());
        if (s == null) return "";
        int barre = s.indexOf(" |");
        return (barre > 0 ? s.substring(0, barre) : s).trim();
    }

    /** Nom d'une arme du pack d'armes par son id (« tacz:glock_17 » -> clé « tacz.gun.glock_17.name »), sinon l'id. */
    private static String nomArme(String gunId) {
        int d = gunId.indexOf(':');
        String cle = (d >= 0 ? gunId.substring(0, d) : "tacz") + ".gun." + (d >= 0 ? gunId.substring(d + 1) : gunId) + ".name";
        if (I18n.exists(cle)) return I18n.get(cle);
        return (d >= 0 ? gunId.substring(d + 1) : gunId).replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ rendu
    private int px0() {
        return (width - PW) / 2;
    }

    private int py0() {
        return (height - PH) / 2;
    }

    private int cartesX0() {
        int n = cartes.length, cw = largeurCarte(n);
        return px0() + PW / 2 - (n * cw + (n - 1) * ECART) / 2;
    }

    private int cartesY() {
        return py0() + ENTETE + 12;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Ui.fond(g, width, height);
        PaquetsMoteur.ChoixOuvrirS2C c = MoteurClient.choix();
        if (c == null || cartes.length == 0) return;
        float reste = MoteurClient.choixRestantS();
        int s = (int) Math.ceil(reste);
        if (s != secondesMontrees) {
            secondesMontrees = s;
            secondes.texte(Integer.toString(Math.max(0, s)));
            if (s <= 3 && s > 0 && minecraft != null) HudMoteur.jouer(MoteurSons.COMPTE.get(), 0.4f);
        }
        boolean urgent = s <= 3;
        int n = cartes.length, cw = largeurCarte(n), x0 = cartesX0(), cy0 = cartesY();
        int survol = carteA(mx, my);
        int px = px0(), py = py0();
        float pix = Formes.pixel();
        float ax = px + PW - M - ANNEAU_R, ay = py + ENTETE / 2f;

        // ---- formes
        VertexConsumer vc = Formes.tampon(g);
        Matrix4f m = g.pose().last().pose();
        Formes.rect(vc, m, px, py, px + PW, py + PH, Palette.argb(Palette.VERRE, VERRE_ALPHA));
        Formes.bord(vc, m, px, py, PW, PH, pix, Palette.argb(0xFFFFFF, BORD_ALPHA));
        Formes.rect(vc, m, px + pix, py + pix, px + PW - pix, py + 2 * pix, Palette.argb(0xFFFFFF, LISERE_ALPHA));
        Formes.rect(vc, m, px + M, py + 14, px + M + 3, py + 38, Palette.argb(couleur, 255));
        // anneau du temps : piste, puis la part restante depuis midi
        float frac = Math.max(0f, Math.min(1f, reste / Math.max(1f, c.secondes())));
        Formes.arc(vc, m, ax, ay, ANNEAU_R - ANNEAU_EP / 2f, ANNEAU_R + ANNEAU_EP / 2f, 0f, 1f, Palette.argb(0xFFFFFF, 34), pix);
        if (frac > 0f) {
            Formes.arc(vc, m, ax, ay, ANNEAU_R - ANNEAU_EP / 2f, ANNEAU_R + ANNEAU_EP / 2f, 0f, frac,
                    Palette.argb(urgent ? ROUGE : couleur, 255), pix);
        }
        // score du match : carres de couleur des equipes
        float scoreDroite = ax - ANNEAU_R - 14, sy = ay;
        float xAutre = scoreDroite - tagAutre.largeur() - 7, xScore = xAutre - 6, xMoi = xScore - scoreTexte.largeur() - 6;
        if (score) {
            Formes.rect(vc, m, scoreDroite - 3, sy - 3, scoreDroite, sy + 3, Palette.argb(couleurAutre, 255));
            float xc = xMoi - tagMoi.largeur() - 4;
            Formes.rect(vc, m, xc - 3, sy - 3, xc, sy + 3, Palette.argb(couleurMoi, 255));
        }
        Formes.rect(vc, m, px + M, py + ENTETE, px + PW - M, py + ENTETE + pix, Palette.argb(0xFFFFFF, BORD_ALPHA));
        // cartes
        for (int i = 0; i < n; i++) formesCarte(vc, m, cartes[i], x0 + i * (cw + ECART), cy0, cw, i == survol, pix);
        // pied : filet et cadre de la touche
        float fy = py + PH - 14;
        Formes.rect(vc, m, px + M, py + PH - 28, px + PW - M, py + PH - 28 + pix, Palette.argb(0xFFFFFF, 18));
        float lw = touches.largeur() + 8;
        Formes.bord(vc, m, px + M, fy - 5, lw, 10, pix, Palette.argb(0xFFFFFF, 60));
        float xa = px + M + lw + 6 + aide.largeur() + 18;
        if (attente) Formes.rect(vc, m, xa, fy - 1.5f, xa + 3, fy + 1.5f, Palette.argb(TRES_FAIBLE, 255));
        g.flush();

        // ---- objets (image des armes, equipement)
        for (int i = 0; i < n; i++) objetsCarte(g, cartes[i], x0 + i * (cw + ECART), cy0, cw);
        float cyCommun = cy0 + CARTE_H + 14;
        float xc = px + M + communs.largeur() + 12;
        for (int i = 0; i < communsPiles.size(); i++) {
            objet(g, communsPiles.get(i), xc + 1.5f, cyCommun - ICONE_COMMUN / 2f, ICONE_COMMUN);
            xc += 18 + communsNoms.get(i).largeur() + 12;
        }

        // ---- textes
        titre.ajouter(g, px + M + 11, py + 21, Palette.argb(TEXTE, 255), TexteNet.GAUCHE, false);
        sousTitre.ajouter(g, px + M + 11, py + 34, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        secondes.ajouter(g, ax, ay, Palette.argb(urgent ? ROUGE : TEXTE, 255), TexteNet.CENTRE, false);
        if (score) {
            tagAutre.ajouter(g, scoreDroite - 7, sy, Palette.argb(ATTENUE, 255), TexteNet.DROITE, false);
            scoreTexte.ajouter(g, xScore, sy, Palette.argb(TEXTE, 255), TexteNet.DROITE, false);
            tagMoi.ajouter(g, xMoi, sy, Palette.argb(ATTENUE, 255), TexteNet.DROITE, false);
        }
        for (int i = 0; i < n; i++) textesCarte(g, cartes[i], x0 + i * (cw + ECART), cy0, cw);
        communs.ajouter(g, px + M, cyCommun, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        xc = px + M + communs.largeur() + 12;
        for (TexteNet t : communsNoms) {
            t.ajouter(g, xc + 18, cyCommun, Palette.argb(ATTENUE, 255), TexteNet.GAUCHE, false);
            xc += 18 + t.largeur() + 12;
        }
        touches.ajouter(g, px + M + lw / 2f, fy, Palette.argb(ATTENUE, 255), TexteNet.CENTRE, false);
        aide.ajouter(g, px + M + lw + 6, fy, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        if (attente) attenteTexte.ajouter(g, xa + 7, fy, Palette.argb(ATTENUE, 255), TexteNet.GAUCHE, false);
        if (defautVisible) defaut.ajouter(g, px + PW - M, fy, Palette.argb(FAIBLE, 255), TexteNet.DROITE, false);
        g.flush();

        // ---- cartes prises : voile au-dessus des images, puis « Prise par ... » en clair
        boolean voile = false;
        for (Carte ca : cartes) voile |= ca.pleine;
        if (voile) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            vc = Formes.tampon(g);
            m = g.pose().last().pose();
            for (int i = 0; i < n; i++) {
                if (!cartes[i].pleine) continue;
                float x = x0 + i * (cw + ECART);
                Formes.rect(vc, m, x + pix, cy0 + pix, x + cw - pix, cy0 + CARTE_H - pix, Palette.argb(Palette.VERRE, VOILE_ALPHA));
            }
            g.flush();
            for (int i = 0; i < n; i++) {
                if (cartes[i].pleine) cartes[i].dispo.ajouter(g, x0 + i * (cw + ECART) + PAD, cy0 + CARTE_H - 10, Palette.argb(ROUGE, 255), TexteNet.GAUCHE, false);
            }
            g.flush();
            g.pose().popPose();
        }
    }

    /** Fond, contour, bande de la carte choisie, touche, jauges, filet et marque de disponibilite. */
    private void formesCarte(VertexConsumer vc, Matrix4f m, Carte ca, float x, float y, int w, boolean survol, float pix) {
        boolean choisi = ca.mien, sur = survol && !ca.pleine && !choisi;
        if (choisi) {
            Formes.rect(vc, m, x, y, x + w, y + CARTE_H, Palette.argb(couleur, 22));
            Formes.bord(vc, m, x, y, w, CARTE_H, pix, Palette.argb(couleur, 255));
            Formes.rect(vc, m, x, y, x + w, y + 2, Palette.argb(couleur, 255));
        } else {
            Formes.rect(vc, m, x, y, x + w, y + CARTE_H, Palette.argb(0xFFFFFF, sur ? 12 : 7));
            Formes.bord(vc, m, x, y, w, CARTE_H, pix, Palette.argb(0xFFFFFF, sur ? 70 : 24));
        }
        // touche
        float kx = x + w - PAD - 10, ky = y + 8;
        if (choisi) Formes.rect(vc, m, kx, ky, kx + 10, ky + 10, Palette.argb(couleur, 230));
        else Formes.bord(vc, m, kx, ky, 10, 10, pix, Palette.argb(0xFFFFFF, 46));
        // jauges : piste, puis la valeur
        float tx = x + PAD, bx0 = tx + colJauge, bx1 = x + w - PAD, jy = y + CARTE_H - 45;
        int plein = Palette.argb(choisi ? Palette.clair(couleur, 0.15f) : JAUGE, 235);
        jauge(vc, m, bx0, bx1, jy, ca.kit.portee(), plein);
        jauge(vc, m, bx0, bx1, jy + 8.5f, ca.kit.cadence(), plein);
        jauge(vc, m, bx0, bx1, jy + 17f, ca.kit.mobilite(), plein);
        // filet et disponibilite
        Formes.rect(vc, m, x + pix, y + CARTE_H - 19, x + w - pix, y + CARTE_H - 19 + pix, Palette.argb(0xFFFFFF, 18));
        float dy = y + CARTE_H - 10;
        if (ca.etatDispo == 3) {
            Formes.coche(vc, m, tx + 3, dy, 6f, 1.2f, 1f, Palette.argb(Palette.clair(couleur, 0.25f), 255));
        } else if (ca.etatDispo == 1) {
            Formes.rect(vc, m, tx, dy - 1.5f, tx + 3, dy + 1.5f, Palette.argb(couleur, 255));
        }
    }

    /** Jauge fine (1,5 de haut) : piste blanche, puis la valeur 0..1. */
    private static void jauge(VertexConsumer vc, Matrix4f m, float x0, float x1, float cy, float v, int plein) {
        Formes.rect(vc, m, x0, cy - 0.75f, x1, cy + 0.75f, Palette.argb(0xFFFFFF, 26));
        Formes.rect(vc, m, x0, cy - 0.75f, x0 + (x1 - x0) * Math.max(0f, Math.min(1f, v)), cy + 0.75f, plein);
    }

    /** Images de l'arme principale, de l'arme secondaire et des objets propres a la classe. */
    private void objetsCarte(GuiGraphics g, Carte ca, float x, float y, int w) {
        if (!ca.arme.isEmpty()) objet(g, ca.arme, x + w / 2f - ICONE / 2f, y + IMG_Y + IMG_H / 2f - ICONE / 2f, ICONE);
        float yy = ligneSecondaire(ca, y);
        if (!ca.arme2.isEmpty()) objet(g, ca.arme2, x + PAD - 0.5f, yy - ICONE_2 / 2f, ICONE_2);
        float xi = x + w - PAD;
        for (int i = ca.propres.size() - 1, k = 0; i >= 0 && k < 3; i--, k++) {
            xi -= ICONE_PROPRE;
            objet(g, ca.propres.get(i), xi, yy - ICONE_PROPRE / 2f, ICONE_PROPRE);
            xi -= 1;
        }
    }

    private static float ligneSecondaire(Carte ca, float y) {
        return y + IMG_Y + IMG_H + 8 + (ca.deuxLignes ? 21 : 11);
    }

    private void textesCarte(GuiGraphics g, Carte ca, float x, float y, int w) {
        float tx = x + PAD;
        ca.nom.ajouter(g, tx, y + 13, Palette.argb(TEXTE, 255), TexteNet.GAUCHE, false);
        ca.touche.ajouter(g, x + w - PAD - 5, y + 13, ca.mien ? Palette.argb(0x0B0D12, 255) : Palette.argb(ATTENUE, 255), TexteNet.CENTRE, false);
        ca.role.ajouter(g, tx, y + 24, Palette.argb(ATTENUE, 255), TexteNet.GAUCHE, false);
        float yy = y + IMG_Y + IMG_H + 8;
        ca.arme1.ajouter(g, tx, yy, Palette.argb(TEXTE, 255), TexteNet.GAUCHE, false);
        if (ca.deuxLignes) ca.arme1b.ajouter(g, tx, yy + 10, Palette.argb(TEXTE, 255), TexteNet.GAUCHE, false);
        ca.secondaire.ajouter(g, tx + 17, ligneSecondaire(ca, y), Palette.argb(ATTENUE, 255), TexteNet.GAUCHE, false);
        float jy = y + CARTE_H - 45;
        portee.ajouter(g, tx, jy, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        cadence.ajouter(g, tx, jy + 8.5f, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        mobilite.ajouter(g, tx, jy + 17, Palette.argb(MUTED, 255), TexteNet.GAUCHE, false);
        float dy = y + CARTE_H - 10;
        switch (ca.etatDispo) {
            case 3 -> ca.dispoGras.ajouter(g, tx + 9, dy, Palette.argb(Palette.clair(couleur, 0.25f), 255), TexteNet.GAUCHE, false);
            case 1 -> ca.dispo.ajouter(g, tx + 6, dy, Palette.argb(ATTENUE, 255), TexteNet.GAUCHE, false);
            case 0 -> ca.dispo.ajouter(g, tx, dy, Palette.argb(FAIBLE, 255), TexteNet.GAUCHE, false);
            default -> {
                // prise : dessinee au-dessus du voile (render)
            }
        }
    }

    /** Objet du jeu dans un carre de {@code taille} unites (image de l'objet : l'agrandir n'est pas un texte). */
    private static void objet(GuiGraphics g, ItemStack st, float x, float y, float taille) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(taille / 16f, taille / 16f, 1f);
        g.renderItem(st, 0, 0);
        g.pose().popPose();
    }

    private int carteA(double mx, double my) {
        int cy0 = cartesY();
        if (my < cy0 || my > cy0 + CARTE_H) return -1;
        int n = cartes.length, cw = largeurCarte(n), x0 = cartesX0();
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (cw + ECART);
            if (mx >= x && mx < x + cw) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ entrees
    private void choisir(int i) {
        if (i < 0 || i >= cartes.length || cartes[i].pleine) return;
        enAttente = cartes[i].kit.id();
        SonsUi.jouer(MoteurSons.CHOIX.get(), 0.7f);
        MatchsNet.versServeur(new PaquetsMoteur.ChoixC2S(enAttente));
        // le choix se voit tout de suite (le serveur confirme ou refuse par l'etat)
        version = -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int bouton) {
        int i = carteA(mx, my);
        if (bouton == 0 && i >= 0) {
            choisir(i);
            return true;
        }
        return super.mouseClicked(mx, my, bouton);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            choisir(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) {
            choisir(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // fond dessine dans render (degrade, le monde reste visible)
    }
}
