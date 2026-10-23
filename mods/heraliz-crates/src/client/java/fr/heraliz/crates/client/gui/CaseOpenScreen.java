package fr.heraliz.crates.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.gui.kit.Echelle;
import fr.heraliz.crates.client.gui.kit.SonsUi;
import fr.heraliz.crates.client.gui.kit.Ui;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran d'une caisse (proposition B « carte centrale » validée le 23/10, maquettes/caisses_proposition_B.png, style des
 * écrans de clan) : ouvert par un clic droit sur n'importe quelle caisse, avec ou sans clé. Panneau de verre centré
 * (584 x 328 depuis Caisses 2.20.0 : il occupe presque tout le canevas, comme le créateur de personnage) : nom de la
 * caisse en capitales espacées, « N clés · N objets » ; la roue, dont la carte du milieu est la
 * plus grande et les autres rapetissent et s'effacent vers les bords (repère doré au-dessus) ; le nom et la rareté de
 * l'objet sous le repère ; les chances en une barre colorée et sa légende ; le contenu possible en mini-grille d'icônes ;
 * le bouton doré arrondi (« Il te faut une clé » éteint, ou « Ouvrir · 1 clé »). La roue tourne dans
 * l'écran et ralentit en douceur (temps réel, rien par à-coups) ; à l'arrêt, une carte « Tu as obtenu » (Encore / Fermer).
 * Espace ou un clic pendant la roue la passe. Tout est décidé par le serveur (CaseOpening) : l'écran ne fait qu'afficher.
 */
public class CaseOpenScreen extends Screen {
    private enum State { IDLE, WAIT, SPIN, DONE }

    /**
     * Polices (DM Sans, tracées à leur vraie taille, jamais agrandies : nettes comme dans /perso ; Caisses 2.20.0, l'écran
     * était trop petit et illisible) : titre gras 18, nom de l'objet gras 14, sous-titre 10, rareté gras 10, texte courant
     * 9 (légende, aide, erreurs), petites capitales grasses 9 (section, boutons de la carte du gain), quantités grasses 8.
     * Tailles et mise en page reprises par l'aperçu outils/apercu_ecran_caisse.py (il lit les constantes de ce fichier).
     */
    private static final net.minecraft.resources.ResourceLocation TITRE = fr.heraliz.crates.HeralizCrates.id("ui_titre"),
            NOM = fr.heraliz.crates.HeralizCrates.id("ui_nom"), SOUS = fr.heraliz.crates.HeralizCrates.id("ui_sous"),
            SOUS_GRAS = fr.heraliz.crates.HeralizCrates.id("ui_sous_bold"), TEXTE = Ui.SMALL,
            TEXTE_GRAS = fr.heraliz.crates.HeralizCrates.id("ui_small_bold"), COMPTE = fr.heraliz.crates.HeralizCrates.id("ui_compte_bold");

    /** Couleurs des raretés de la maquette (Commun, Peu commun, Rare, Épique, Légendaire, Unique), par rang de LootRarity. */
    private static final int[] RARETES = {0xB0B6C0, 0x5FD08A, 0x4C8DFF, 0xA86BFF, 0xE8C36A, 0xFF5A5A};
    private static final int OR_HAUT = 0xF0CF7B, OR_BAS = 0xD9AC4C, OR_TEXTE = 0x1A1408;

    /** Panneau (proposition B) : 584 x 328 au canevas d'au moins 600 x 340 (Echelle), marges intérieures PAD. */
    private static final int PANNEAU_W = 584, PANNEAU_H = 334, PAD = 30;
    /** Hauts des lignes (depuis le haut du panneau) : titre, sous-titre, roue, nom, rareté, barre des chances, légende,
     *  section « Contenu possible », grille, bouton, rangée des touches en bas (comme la boutique : ESPACE Ouvrir…). */
    private static final int TITRE_Y = 12, SOUS_Y = 34, ROUE_Y = 57, NOM_Y = 155, RARETE_Y = 175, CHANCES_Y = 194,
            LEGENDE_Y = 202, SECTION_Y = 230, GRILLE_Y = 244, BOUTON_Y = 285, TOUCHES_Y = 315;
    /** Rangée des touches : hauteur d'une touche, marge du texte dans la touche, écart touche-action, écart entre deux. */
    private static final int TOUCHE_H = 15, TOUCHE_PAD = 5, TOUCHE_ECART = 5, TOUCHES_ESPACE = 14, TOUCHE_TEXTE_Y = 5;
    /** Espacement des capitales : titre, rareté, section, bouton, boutons du gain. */
    private static final float ESP_TITRE = 2.2f, ESP_TOUCHE = 0.3f, ESP_RARETE = 1.4f, ESP_SECTION = 1.4f, ESP_BOUTON = 1.4f,
            ESP_GAIN = 1.0f;
    /** Halo de rareté derrière la roue : demi-largeur (part du panneau), demi-hauteur (part de la roue). */
    private static final float HALO_L = 0.42f, HALO_H = 0.95f;
    /** Roue : écart entre les centres des cartes (STEP), largeur logique d'une carte (CW, sert au point d'arrêt), carte du
     *  milieu (CARTE_MAX px), les autres rapetissent avec la distance ; marge de la roue dans le panneau. */
    private static final int STEP = 76, CW = 68, CARTE_MAX = 84, ROUE_H = 92, ROUE_MARGE = 20;
    /** Légende des chances : pastille (côté, haut sous la ligne), écart pastille-texte, écart entre deux entrées,
     *  hauteur d'une ligne. */
    private static final int LEGENDE_POINT = 5, LEGENDE_POINT_Y = 1, LEGENDE_ECART = 4, LEGENDE_ESPACE = 14, LEGENDE_LIGNE = 12;
    private static final int GRILLE_COLS = 12, CELLULE_W = 39, GRILLE_H = 36, GRILLE_GAP = 5, GRILLE_ITEM = 22;
    private static final int BOUTON_W = 236, BOUTON_H = 24;
    /** Carte du gain : panneau, carte de l'objet, objet, boutons (hauteur), hauts du titre, de la carte et du nom. */
    private static final int GAIN_W = 268, GAIN_H = 236, GAIN_CARTE = 120, GAIN_ITEM = 56, GAIN_BOUTON_H = 24,
            GAIN_TITRE_Y = 16, GAIN_CARTE_Y = 36, GAIN_NOM_Y = 166;
    /** Couleur de la légende, du sous-titre et de l'aide : plus claire que MUTED, lisible sur le verre. */
    private static final int CLAIR = 0xB4BAC4;
    /** Opacité du halo de rareté derrière la roue. */
    private static final int HALO_ALPHA = 34;
    /** Durée de la roue en ms : celle du serveur (CrateAnim.REEL_TICKS), l'objet est donné à la fin. */
    private static final long DUREE_ROUE = CrateAnim.REEL_TICKS * 50L;
    /** Après l'arrêt : temps avant la carte du gain, puis son apparition (ms). */
    private static final long GAIN_RETARD = 650, GAIN_FONDU = 300;
    /** Cartes gardées de la bande précédente : la roue repart de ce qu'on voit. */
    private static final int KEEP = 5;
    /** Dérive lente de la roue au repos (px par seconde). */
    private static final float DERIVE = 6f;

    private final CrateType type;
    private final int session;
    private final boolean free;
    private final List<Packets.Card> contents;
    private final RandomSource random = RandomSource.create();
    /** Chance totale par rareté (rang de LootRarity), pour la colonne « Chances ». */
    private final double[] chances = new double[RARETES.length];

    private State state = State.IDLE;
    private State before = State.IDLE;
    private int ticks;
    private int waitSince, lastRequest = -100;
    private boolean spaceHeld;

    private List<Packets.Card> strip = new ArrayList<>();
    private boolean idleWrap = true;
    /** pos : coordonnée de la bande sous le repère (px) au repos ; view : celle de l'image en cours. */
    private float pos, view, spinFrom, spinTo;
    private long spinStartMs, doneAtMs, idleMs;
    private int winIndex = -1;
    private int carteSon = Integer.MIN_VALUE;
    private long dernierSon;

    @Nullable
    private Component error;
    private int errorAt;
    private int keys;
    private int grilleScroll;

    // mise en page (unités d'interface, canevas 640 x 360)
    private int px, py, pw, ph, pad;
    private int roueX, roueY, roueW;
    private int basY, colX, colW, grilleX, grilleY, grilleW, grilleH, celluleW;
    private int boutonX, boutonY, boutonW;
    private int gainX, gainY, gainW, gainH, encoreX, fermerX, gainBoutonY, gainBoutonW;

    public CaseOpenScreen(CrateType type, int session, boolean free, List<Packets.Card> contents) {
        super(type.crateName());
        this.type = type;
        this.session = session;
        this.free = free;
        this.contents = contents;
        for (Packets.Card c : contents) chances[rang(c.rarity())] += Math.max(0, c.chance());
        buildIdleStrip();
    }

    /** Bande de repos : des objets du contenu tirés selon leurs chances (surtout des communs, comme la vraie roue). */
    private void buildIdleStrip() {
        strip = new ArrayList<>();
        double total = 0;
        for (Packets.Card c : contents) total += Math.max(0, c.chance());
        for (int i = 0; i < 40 && !contents.isEmpty(); i++) {
            double r = random.nextDouble() * total;
            Packets.Card pick = contents.get(contents.size() - 1);
            for (Packets.Card c : contents) {
                r -= Math.max(0, c.chance());
                if (r < 0) {
                    pick = c;
                    break;
                }
            }
            strip.add(pick);
        }
        idleWrap = true;
        pos = view = 3 * STEP + CW / 2f;
        idleMs = Util.getMillis();
    }

    private static int rang(int rarity) {
        return Math.max(0, Math.min(RARETES.length - 1, rarity));
    }

    private static int couleur(int rarity) {
        return RARETES[rang(rarity)];
    }

    // =================================================================== mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        pw = PANNEAU_W;
        ph = PANNEAU_H;
        px = Math.max(0, (width - pw) / 2);
        py = Math.max(0, (height - ph) / 2);
        pad = PAD;
        roueX = px + ROUE_MARGE;
        roueW = pw - 2 * ROUE_MARGE;
        roueY = py + ROUE_Y;
        // contenu possible : 12 colonnes centrées, une rangée visible (molette)
        celluleW = CELLULE_W;
        grilleW = GRILLE_COLS * celluleW + (GRILLE_COLS - 1) * GRILLE_GAP;
        grilleX = px + (pw - grilleW) / 2;
        basY = py + SECTION_Y;
        grilleY = py + GRILLE_Y;
        grilleH = GRILLE_H;
        colX = px + pad;
        colW = pw - 2 * pad;
        boutonW = BOUTON_W;
        boutonX = px + (pw - boutonW) / 2;
        boutonY = py + BOUTON_Y;
        // carte du gain, au centre de l'écran
        gainW = GAIN_W;
        gainH = GAIN_H;
        gainX = (width - gainW) / 2;
        gainY = Math.max(0, (height - gainH) / 2);
        gainBoutonW = (gainW - 32 - 8) / 2;
        gainBoutonY = gainY + gainH - 16 - GAIN_BOUTON_H;
        encoreX = gainX + 16;
        fermerX = encoreX + gainBoutonW + 8;
        grilleScroll = Mth.clamp(grilleScroll, 0, maxScroll());
        countKeys();
    }

    private boolean busy() {
        return state == State.WAIT || state == State.SPIN;
    }

    private boolean peutOuvrir() {
        return !busy() && (free || keys >= 1) && !contents.isEmpty();
    }

    /** Carte du gain affichée (roue arrêtée depuis un instant). */
    private boolean gainVisible(long now) {
        return state == State.DONE && winIndex >= 0 && winIndex < strip.size() && !idleWrap && now - doneAtMs >= GAIN_RETARD;
    }

    private void countKeys() {
        if (minecraft == null || minecraft.player == null) return;
        Inventory inv = minecraft.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof CrateKeyItem k && k.type == type) n += st.getCount();
        }
        keys = n;
    }

    // =================================================================== actions
    private void open() {
        if (ticks - lastRequest < 6 || !peutOuvrir()) return;
        lastRequest = ticks;
        before = state;
        state = State.WAIT;
        waitSince = ticks;
        error = null;
        play(ModRegistry.SND_KEY_TURN.get(), 1.1f, 0.45f);
        HzNet.toServer(new Packets.CaseOpen(session));
    }

    private void skip() {
        if (state != State.SPIN) return;
        finishSpin();
        HzNet.toServer(new Packets.CaseSkip());
    }

    /** Réponse du serveur. */
    public void onResult(Packets.CaseResult r) {
        if (!r.error().isEmpty() || r.winIndex() < 0 || r.winIndex() >= r.strip().size()) {
            state = before;
            if (!r.error().isEmpty() && !r.error().equals("gui.heralizcrates.case.error.busy")) {
                error = Component.translatable(r.error());
                errorAt = ticks;
                play(ModRegistry.SND_LOCKED.get(), 1f, 0.5f);
            }
            return;
        }
        startSpin(r.strip(), r.winIndex());
        play(ModRegistry.SND_ROLL_START.get(), 1f, 0.5f);
    }

    private void startSpin(List<Packets.Card> serverStrip, int win) {
        float actuelle = view;
        List<Packets.Card> next = new ArrayList<>(serverStrip);
        int k = Mth.floor(actuelle / STEP);
        float offset = actuelle - k * STEP;
        if (next.size() > 2 * KEEP + 2 && win > 2 * KEEP + 1) {
            for (int j = -KEEP; j <= KEEP; j++) {
                Packets.Card c = at(k + j);
                if (c != null) next.set(KEEP + j, c);
            }
            spinFrom = KEEP * STEP + offset;
        } else {
            spinFrom = 3 * STEP + CW / 2f;
        }
        strip = next;
        idleWrap = false;
        winIndex = win;
        // arrêt n'importe où sur la carte gagnante, parfois tout au bord (suspense)
        spinTo = win * STEP + CW * (0.08f + 0.84f * random.nextFloat());
        spinStartMs = Util.getMillis();
        pos = view = spinFrom;
        carteSon = Mth.floor(pos / STEP);
        state = State.SPIN;
    }

    private void finishSpin() {
        pos = view = spinTo;
        state = State.DONE;
        doneAtMs = Util.getMillis();
        play(ModRegistry.SND_ROLL_STOP.get(), 1f, 0.7f);
        if (winIndex >= 0 && winIndex < strip.size()) {
            int tier = CardArt.rarity(strip.get(winIndex).rarity()).soundTier;
            SoundEvent s = tier >= 2 ? ModRegistry.SND_REWARD_LEGENDARY.get() : tier == 1 ? ModRegistry.SND_REWARD_RARE.get() : ModRegistry.SND_REWARD_COMMON.get();
            play(s, 1f, 0.8f);
        }
    }

    private void play(SoundEvent s, float pitch, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    @Nullable
    private Packets.Card at(int i) {
        if (strip.isEmpty()) return null;
        if (idleWrap) return strip.get(Math.floorMod(i, strip.size()));
        return i >= 0 && i < strip.size() ? strip.get(i) : null;
    }

    /** Position de la roue à l'instant donné : ralentissement « ease-out » quartique, en temps réel. */
    private float spinPos(long now) {
        float u = Mth.clamp((now - spinStartMs) / (float) DUREE_ROUE, 0f, 1f);
        float r = 1f - u;
        float e = 1f - r * r * r * r;
        return spinFrom + (spinTo - spinFrom) * e;
    }

    // =================================================================== boucle
    @Override
    public void tick() {
        ticks++;
        countKeys();
        if (state == State.WAIT && ticks - waitSince > 60) state = before;
        if (state == State.SPIN && Util.getMillis() - spinStartMs >= DUREE_ROUE) finishSpin();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        // aussi appelé pendant une déconnexion : plus rien à envoyer si la connexion n'existe plus
        if (minecraft != null && minecraft.getConnection() != null) HzNet.toServer(new Packets.CaseClose(session));
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // un appui = une action : garder la touche enfoncée n'enchaîne pas les ouvertures
            if (!spaceHeld) {
                spaceHeld = true;
                if (state == State.SPIN) skip();
                else if (state != State.DONE || gainVisible(Util.getMillis())) open();
            }
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) spaceHeld = false;
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        // pendant la roue, un clic n'importe où la passe
        if (state == State.SPIN) {
            skip();
            return true;
        }
        long now = Util.getMillis();
        if (gainVisible(now)) {
            if (encoreVisible() && dans(mx, my, encoreX, gainBoutonY, gainBoutonW, GAIN_BOUTON_H)) {
                SonsUi.clic(0.6f);
                open();
            } else if (dans(mx, my, encoreVisible() ? fermerX : encoreX, gainBoutonY, encoreVisible() ? gainBoutonW : gainW - 32, GAIN_BOUTON_H)) {
                SonsUi.clic(0.6f);
                onClose();
            }
            return true;
        }
        if (dans(mx, my, boutonX, boutonY, boutonW, BOUTON_H) && peutOuvrir()) {
            SonsUi.clic(0.6f);
            open();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (dans(mx, my, grilleX, grilleY, grilleW + 6, grilleH)) {
            grilleScroll = Mth.clamp(grilleScroll - (int) Math.signum(delta) * (GRILLE_H + GRILLE_GAP), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean encoreVisible() {
        return free || keys >= 1;
    }

    private int maxScroll() {
        int rangs = (contents.size() + GRILLE_COLS - 1) / GRILLE_COLS;
        return Math.max(0, rangs * (GRILLE_H + GRILLE_GAP) - GRILLE_GAP - grilleH);
    }

    // =================================================================== rendu
    @Override
    public void renderBackground(GuiGraphics g) {
        Ui.fond(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        if (state == State.SPIN) {
            view = spinPos(now);
            sonRoue(now);
        } else if (state != State.DONE && idleWrap) {
            view = pos + DERIVE * (now - idleMs) / 1000f;
        } else {
            view = pos;
        }
        renderBackground(g);

        Lot lot = new Lot(g);
        lot.verre(px, py, pw, ph, 200);
        // halo très doux de la couleur de l'objet sous le repère derrière la roue (comme les cartes de la boutique)
        Packets.Card sous = at(indexSousRepere());
        if (sous != null) lot.lueur(px + pw / 2f, roueCy(), pw * HALO_L, ROUE_H * HALO_H, Ui.argb(couleur(sous.rarity()), HALO_ALPHA));
        lot.fin();
        renderEntete(g);
        renderRoue(g, now);
        renderObjet(g, now);
        renderChances(g);
        Packets.Card survolee = renderGrille(g, mouseX, mouseY);
        renderBouton(g, mouseX, mouseY);

        if (gainVisible(now)) {
            renderGain(g, mouseX, mouseY, now);
        } else if (!busy() && state != State.DONE) {
            Packets.Card c = survolee != null ? survolee : carteRoueSous(mouseX, mouseY);
            if (c != null) Ui.tooltip(g, font, CardArt.tooltip(c), mouseX, mouseY, couleur(c.rarity()));
        }
    }

    /** Petit « tac » à chaque carte qui passe sous le repère (son de la roue du mod), jamais en rafale. */
    private void sonRoue(long now) {
        int carte = Mth.floor(view / STEP);
        if (carte == carteSon) return;
        carteSon = carte;
        if (now - dernierSon < 35) return;
        dernierSon = now;
        float vitesse = Math.abs(spinPos(now) - spinPos(now - 50));
        play(ModRegistry.SND_ROLL_TICK.get(), 0.9f + Math.min(0.35f, vitesse / 160f) + random.nextFloat() * 0.06f, 0.4f);
    }

    /** En-tête centré : nom de la caisse en capitales espacées, dessous « N clés · N objets ». */
    private void renderEntete(GuiGraphics g) {
        float cx = px + pw / 2f;
        String titre = type.crateName().getString().toUpperCase(Locale.ROOT);
        // tracé à sa vraie taille (plus d'agrandissement : des lettres agrandies sont floues)
        while (titre.length() > 1 && Ui.spacedWidth(font, titre, TITRE, ESP_TITRE) > pw - 2 * pad) titre = titre.substring(0, titre.length() - 1);
        Ui.spacedCentered(g, font, titre, TITRE, cx, py + TITRE_Y, Ui.argb(Ui.TEXT, 255), ESP_TITRE);
        String n = Integer.toString(contents.size());
        String sous = free ? Component.translatable("gui.heralizcrates.case.subtitle_free", n).getString()
                : keys == 1 ? Component.translatable("gui.heralizcrates.case.subtitle_one", n).getString()
                : Component.translatable("gui.heralizcrates.case.subtitle", Integer.toString(keys), n).getString();
        Ui.centered(g, font, Ui.lit(sous, SOUS), cx, py + SOUS_Y, Ui.argb(CLAIR, 255));
    }

    /** Centre vertical des cartes de la roue. */
    private float roueCy() {
        return roueY + ROUE_H / 2f;
    }

    /** Taille d'une carte de la roue selon sa distance au repère (px) : la plus grande au milieu. */
    private static float tailleCarte(float d) {
        return CARTE_MAX / (1f + 0.24f * Math.abs(d) / STEP);
    }

    /** Index de la carte sous le repère (celle dont le centre est le plus proche). */
    private int indexSousRepere() {
        return Math.round((view - CW / 2f) / STEP);
    }

    /** Carte de la roue sous la souris (roue à l'arrêt). */
    @Nullable
    private Packets.Card carteRoueSous(int mx, int my) {
        if (mx < roueX || mx >= roueX + roueW || my < roueY || my >= roueY + ROUE_H) return null;
        float centre = roueX + roueW / 2f;
        int i = Math.round((mx - centre + view - CW / 2f) / STEP);
        float d = i * STEP + CW / 2f - view;
        float t = tailleCarte(d) / 2f;
        float cx = centre + d, cy = roueCy();
        return Math.abs(mx - cx) <= t && Math.abs(my - cy) <= t ? at(i) : null;
    }

    private void renderRoue(GuiGraphics g, long now) {
        int x0 = roueX, x1 = roueX + roueW, y0 = roueY, y1 = roueY + ROUE_H;
        float centre = roueX + roueW / 2f;

        boolean fini = state == State.DONE && winIndex >= 0 && winIndex < strip.size() && !idleWrap;
        float pose = fini ? Mth.clamp((now - doneAtMs) / 350f, 0f, 1f) : 0f;
        pose = 1 - (1 - pose) * (1 - pose);

        g.enableScissor(x0, y0 - 6, x1, y1 + 6);
        int first = Mth.floor((view - roueW / 2f) / STEP) - 1, last = Mth.floor((view + roueW / 2f) / STEP) + 1;
        // des bords vers le milieu : la carte centrale passe par-dessus ses voisines
        List<Integer> ordre = new ArrayList<>();
        for (int i = first; i <= last; i++) ordre.add(i);
        ordre.sort((a, b) -> Float.compare(Math.abs(b * STEP + CW / 2f - view), Math.abs(a * STEP + CW / 2f - view)));
        int z = 0;
        for (int i : ordre) {
            Packets.Card c = at(i);
            if (c == null) continue;
            float d = i * STEP + CW / 2f - view;
            float taille = tailleCarte(d);
            float fondu = Mth.clamp(1f - (Math.abs(d) - STEP * 1.2f) / (roueW / 2f - STEP * 1.2f), 0.25f, 1f);
            float milieu = Mth.clamp(1f - Math.abs(d) / STEP, 0f, 1f);
            boolean gagnante = fini && i == winIndex;
            g.pose().pushPose();
            g.pose().translate(0, 0, z += 2);
            carteRoue(g, c, centre + d, roueCy(), taille, fondu, fini ? (gagnante ? 1f : 0f) : milieu, fini && !gagnante ? pose : 0f);
            g.pose().popPose();
        }
        g.disableScissor();

        // repère doré au-dessus de la roue
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Lot lot = new Lot(g);
        float cx = Math.round(centre);
        lot.tri(cx - 6, y0 - 10, cx, y0 - 3, cx + 6, y0 - 10, Ui.argb(Ui.OR, 255));
        lot.fin();
        g.pose().popPose();
    }

    /**
     * Carte de la roue (proposition B) centrée en (cx, cy), de taille px : verre clair, lueur de la rareté derrière l'objet,
     * trait de couleur en bas. fondu (0..1) : effacement vers les bords ; avant (0..1) : mise en avant (carte du milieu,
     * contour de la couleur de la rareté) ; eteinte (0..1) : les autres après l'arrêt.
     */
    private void carteRoue(GuiGraphics g, Packets.Card c, float cx, float cy, float px, float fondu, float avant, float eteinte) {
        int col = couleur(c.rarity());
        int w = Math.round(px), x = Math.round(cx - px / 2f), y = Math.round(cy - px / 2f);
        Lot lot = new Lot(g);
        if (avant > 0) {
            lot.lueur(x + w / 2f, y + w / 2f, w * 0.75f, w * 0.75f, Ui.argb(col, (int) (70 * avant)));
            lot.rondContour(x - 1, y - 1, w + 2, w + 2, Ui.argb(col, (int) (200 * avant)));
        }
        lot.rondVert(x, y, w, w, Ui.argb(0x1C2028, 255), Ui.argb(0x15181F, 255));
        lot.rondContour(x, y, w, w, Ui.argb(0xFFFFFF, 18));
        lot.lueur(x + w / 2f, y + w * 0.47f, w * 0.34f, w * 0.3f, Ui.argb(col, (int) (60 + 70 * avant)));
        float trait = w * 0.34f;
        lot.rect(x + w / 2f - trait, y + w - 4, x + w / 2f + trait, y + w - 3, Ui.argb(col, 255));
        lot.fin();
        CardArt.item(g, c.stack(), x + w / 2f, y + w * 0.47f, w * 0.42f);
        float voile = Math.max(1f - fondu, 0.6f * eteinte);
        if (voile > 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            Lot v = new Lot(g);
            v.rond(x, y, w, w, Ui.argb(Ui.VERRE, (int) (215 * voile)));
            v.fin();
            g.pose().popPose();
        }
        if (c.count() > 1) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            Ui.right(g, font, Ui.lit("×" + c.count(), COMPTE), x + w - 5, y + 3, Ui.argb(0xFFFFFF, (int) (225 * fondu)));
            g.pose().popPose();
        }
    }

    /** Nom de l'objet sous le repère, puis sa rareté en capitales colorées. */
    private void renderObjet(GuiGraphics g, long now) {
        Packets.Card c = at(indexSousRepere());
        if (c == null) return;
        float cx = px + pw / 2f;
        String nom = c.stack().getHoverName().getString() + (c.count() > 1 ? "  ×" + c.count() : "");
        nom = Ui.ellipsis(font, nom, NOM, pw - 2 * pad);
        Ui.centered(g, font, Ui.lit(nom, NOM), cx, py + NOM_Y, Ui.argb(Ui.TEXT, 255));
        String rn = CardArt.rarity(c.rarity()).displayName().getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, rn, SOUS_GRAS, cx, py + RARETE_Y, Ui.argb(couleur(c.rarity()), 255), ESP_RARETE);
    }

    /**
     * Carte d'objet (maquette) : fond de verre clair, lueur ronde de la couleur de la rareté derrière l'objet, nom, rareté
     * en petites capitales, trait de couleur en bas. gagne (0..1) : carte gagnée mise en avant ; eteinte (0..1) : les autres.
     */
    private void carte(GuiGraphics g, Packets.Card c, int x, int y, int w, int h, int itemPx, float gagne, float eteinte) {
        int col = couleur(c.rarity());
        float itemCy = y + h * 0.42f;
        Lot lot = new Lot(g);
        if (gagne > 0) {
            lot.lueur(x + w / 2f, y + h / 2f, w * 0.9f, h * 0.9f, Ui.argb(col, (int) (60 * gagne)));
            lot.rondContour(x - 1, y - 1, w + 2, h + 2, Ui.argb(col, (int) (220 * gagne)));
        }
        lot.rondVert(x, y, w, h, Ui.argb(0xFFFFFF, 12), Ui.argb(0xFFFFFF, 4));
        lot.rondContour(x, y, w, h, Ui.argb(0xFFFFFF, 18));
        lot.lueur(x + w / 2f, itemCy + itemPx * 0.12f, w * 0.36f, itemPx * 0.8f, Ui.argb(col, 120));
        lot.rect(x + 6, y + h - 3, x + w - 6, y + h - 1, Ui.argb(col, 255));
        lot.fin();
        CardArt.item(g, c.stack(), x + w / 2f, itemCy, itemPx);
        // textes au-dessus des objets (rendus plus près de l'écran)
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        String rn = CardArt.rarity(c.rarity()).displayName().getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, rn, TEXTE_GRAS, x + w / 2f, y + h - 20, Ui.argb(col, 255), ESP_GAIN);
        if (c.count() > 1) Ui.right(g, font, Ui.lit("×" + c.count(), COMPTE), x + w - 6, y + 4, Ui.argb(0xFFFFFF, 225));
        if (eteinte > 0) {
            Lot voile = new Lot(g);
            voile.rond(x, y, w, h, Ui.argb(0x07080A, (int) (150 * eteinte)));
            voile.fin();
        }
        g.pose().popPose();
    }

    /** Mini-grille du contenu possible (12 colonnes, une rangée visible, molette) ; rend la carte survolée. */
    @Nullable
    private Packets.Card renderGrille(GuiGraphics g, int mx, int my) {
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.case.possible").getString(), TEXTE_GRAS, grilleX, basY, Ui.argb(Ui.MUTED, 255), ESP_SECTION);
        if (contents.isEmpty()) {
            Ui.centered(g, font, Ui.lit(Component.translatable("gui.heralizcrates.case.empty").getString(), TEXTE), px + pw / 2f, grilleY + 13, Ui.argb(Ui.FAINT, 255));
            return null;
        }
        Packets.Card survolee = null;
        g.enableScissor(grilleX - 1, grilleY - 1, grilleX + grilleW + 1, grilleY + grilleH + 1);
        for (int i = 0; i < contents.size(); i++) {
            int col = i % GRILLE_COLS, rangee = i / GRILLE_COLS;
            int x = grilleX + col * (celluleW + GRILLE_GAP), y = grilleY + rangee * (GRILLE_H + GRILLE_GAP) - grilleScroll;
            if (y + GRILLE_H < grilleY || y > grilleY + grilleH) continue;
            Packets.Card c = contents.get(i);
            boolean survol = my >= grilleY && my < grilleY + grilleH && dans(mx, my, x, y, celluleW, GRILLE_H);
            int couleur = couleur(c.rarity());
            Lot lot = new Lot(g);
            lot.rond(x, y, celluleW, GRILLE_H, Ui.argb(0xFFFFFF, survol ? 22 : 10));
            lot.rect(x + 6, y + GRILLE_H - 3, x + celluleW - 6, y + GRILLE_H - 1, Ui.argb(couleur, 255));
            lot.fin();
            CardArt.item(g, c.stack(), x + celluleW / 2f, y + GRILLE_H / 2f - 1, GRILLE_ITEM);
            if (c.count() > 1) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                Ui.right(g, font, Ui.lit("×" + c.count(), COMPTE), x + celluleW - 3, y + 2, Ui.argb(0xFFFFFF, 215));
                g.pose().popPose();
            }
            if (survol) survolee = c;
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            // flèches discrètes : d'autres rangées à la molette
            Lot lot = new Lot(g);
            int fx = grilleX + grilleW + 6, fy = grilleY + grilleH / 2;
            if (grilleScroll > 0) lot.tri(fx, fy - 3, fx + 4, fy - 8, fx + 8, fy - 3, Ui.argb(0xFFFFFF, 110));
            if (grilleScroll < max) lot.tri(fx + 8, fy + 3, fx + 4, fy + 8, fx, fy + 3, Ui.argb(0xFFFFFF, 110));
            lot.fin();
        }
        return survolee;
    }

    /** Largeur d'une entrée de la légende des chances : pastille, écart, texte. */
    private float largeurLegende(String texte) {
        return LEGENDE_POINT + LEGENDE_ECART + font.width(Ui.lit(texte, TEXTE));
    }

    /** Chances : une barre, une part de couleur par rareté, et sa légende centrée dessous (deux lignes au plus). */
    private void renderChances(GuiGraphics g) {
        double total = 0;
        for (double v : chances) total += v;
        if (total <= 0) return;
        int x0 = colX, x1 = colX + colW, y = py + CHANCES_Y;
        Lot lot = new Lot(g);
        float x = x0;
        int dernier = -1;
        for (int r = 0; r < RARETES.length; r++) if (chances[r] > 0) dernier = r;
        for (int r = 0; r < RARETES.length; r++) {
            if (chances[r] <= 0) continue;
            float w = (float) (chances[r] / total * (x1 - x0));
            float fin = r == dernier ? x1 : x + w;
            lot.rect(x, y, Math.max(x + 1, fin - (r == dernier ? 0 : 1f)), y + 3, Ui.argb(RARETES[r], 255));
            x = fin;
        }
        lot.fin();
        // légende : « ● Commun 52,0 % » ; plusieurs lignes si elle ne tient pas sur une
        List<String> textes = new ArrayList<>();
        List<Integer> rangs = new ArrayList<>();
        for (int r = 0; r < RARETES.length; r++) {
            if (chances[r] <= 0) continue;
            textes.add(CardArt.rarity(r).displayName().getString() + " " + Ui.pct(chances[r]));
            rangs.add(r);
        }
        List<List<Integer>> lignes = new ArrayList<>();
        List<Integer> ligne = new ArrayList<>();
        float lw = 0;
        for (int k = 0; k < textes.size(); k++) {
            float w = largeurLegende(textes.get(k));
            if (!ligne.isEmpty() && lw + LEGENDE_ESPACE + w > colW) {
                lignes.add(ligne);
                ligne = new ArrayList<>();
                lw = 0;
            }
            lw += (ligne.isEmpty() ? 0 : LEGENDE_ESPACE) + w;
            ligne.add(k);
        }
        if (!ligne.isEmpty()) lignes.add(ligne);
        float ly = py + LEGENDE_Y;
        for (List<Integer> l : lignes) {
            float largeur = -LEGENDE_ESPACE;
            for (int k : l) largeur += largeurLegende(textes.get(k)) + LEGENDE_ESPACE;
            float lx = Math.round(px + pw / 2f - largeur / 2f);
            Lot points = new Lot(g);
            float tx = lx;
            for (int k : l) {
                points.rond(Math.round(tx), Math.round(ly + LEGENDE_POINT_Y), LEGENDE_POINT, LEGENDE_POINT, Ui.argb(RARETES[rangs.get(k)], 255));
                tx += largeurLegende(textes.get(k)) + LEGENDE_ESPACE;
            }
            points.fin();
            tx = lx;
            for (int k : l) {
                g.pose().pushPose();
                Ui.placer(g, tx + LEGENDE_POINT + LEGENDE_ECART, ly);
                g.drawString(font, Ui.lit(textes.get(k), TEXTE), 0, 0, Ui.argb(CLAIR, 255), false);
                g.pose().popPose();
                tx += largeurLegende(textes.get(k)) + LEGENDE_ESPACE;
            }
            ly += LEGENDE_LIGNE;
        }
    }

    /** Bouton principal et ligne d'aide dessous. */
    private void renderBouton(GuiGraphics g, int mx, int my) {
        boolean actif = peutOuvrir() || state == State.SPIN;
        String cle;
        if (state == State.SPIN) cle = "gui.heralizcrates.case.skip_space";
        else if (state == State.WAIT) cle = "gui.heralizcrates.case.wait";
        else if (!free && keys < 1) cle = "gui.heralizcrates.case.need_key";
        else if (free) cle = "gui.heralizcrates.case.open";
        else cle = "gui.heralizcrates.case.open_key";
        boolean dore = actif || state == State.WAIT;
        boolean survol = actif && state != State.SPIN && dans(mx, my, boutonX, boutonY, boutonW, BOUTON_H);
        Lot lot = new Lot(g);
        if (dore) {
            float m = survol ? 0.15f : state == State.WAIT ? -0.3f : 0f;
            int haut = m >= 0 ? Ui.mix(OR_HAUT, 0xFFFFFF, m) : Ui.mix(OR_HAUT, 0x0D0F14, -m);
            int bas = m >= 0 ? Ui.mix(OR_BAS, 0xFFFFFF, m) : Ui.mix(OR_BAS, 0x0D0F14, -m);
            lot.pilule(boutonX, boutonY, boutonW, BOUTON_H, Ui.argb(haut, 255), Ui.argb(bas, 255));
        } else {
            lot.pilule(boutonX, boutonY, boutonW, BOUTON_H, Ui.argb(0xFFFFFF, 18), Ui.argb(0xFFFFFF, 12));
        }
        lot.fin();
        String texte = Component.translatable(cle).getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, texte, Ui.BOLD, boutonX + boutonW / 2f, boutonY + 7, Ui.argb(dore ? OR_TEXTE : Ui.MUTED, 255), ESP_BOUTON);

        renderTouches(g);
    }

    /**
     * Rangée du bas, comme la boutique : touches en pastilles (ESPACE Ouvrir, MOLETTE Contenu, ÉCHAP Fermer) à gauche ;
     * à droite, l'erreur du serveur ou d'où viennent les clés.
     */
    private void renderTouches(GuiGraphics g) {
        List<String[]> touches = new ArrayList<>();
        if (state == State.SPIN) touches.add(new String[]{"gui.heralizcrates.case.touche.espace", "gui.heralizcrates.case.skip"});
        else if (peutOuvrir()) touches.add(new String[]{"gui.heralizcrates.case.touche.espace", "gui.heralizcrates.case.open"});
        if (maxScroll() > 0) touches.add(new String[]{"gui.heralizcrates.case.touche.molette", "gui.heralizcrates.case.action.contenu"});
        touches.add(new String[]{"gui.heralizcrates.case.touche.echap", "gui.heralizcrates.case.close"});
        int y = py + TOUCHES_Y;
        float x = px + pad;
        for (String[] t : touches) {
            String touche = Component.translatable(t[0]).getString().toUpperCase(Locale.ROOT);
            String action = Component.translatable(t[1]).getString();
            int tw = Math.round(Ui.spacedWidth(font, touche, TEXTE_GRAS, ESP_TOUCHE)) + 2 * TOUCHE_PAD;
            Lot lot = new Lot(g);
            lot.rond(Math.round(x), y, tw, TOUCHE_H, Ui.argb(0xFFFFFF, 14));
            lot.rondContour(Math.round(x), y, tw, TOUCHE_H, Ui.argb(0xFFFFFF, 70));
            lot.fin();
            Ui.spaced(g, font, touche, TEXTE_GRAS, Math.round(x) + TOUCHE_PAD, y + TOUCHE_TEXTE_Y, Ui.argb(Ui.TEXT, 255), ESP_TOUCHE);
            float ax = Math.round(x) + tw + TOUCHE_ECART;
            g.pose().pushPose();
            Ui.placer(g, ax, y + TOUCHE_TEXTE_Y);
            g.drawString(font, Ui.lit(action, TEXTE), 0, 0, Ui.argb(CLAIR, 255), false);
            g.pose().popPose();
            x = ax + font.width(Ui.lit(action, TEXTE)) + TOUCHES_ESPACE;
        }
        // à droite : erreur du serveur, ou d'où viennent les clés
        String note = null;
        int couleur = Ui.MUTED;
        if (error != null && ticks - errorAt < 80) {
            note = error.getString();
            couleur = Ui.DANGER;
        } else if (contents.isEmpty()) {
            note = Component.translatable("gui.heralizcrates.case.empty").getString();
        } else if (!free && keys < 1 && state != State.SPIN) {
            note = Component.translatable("gui.heralizcrates.case.help.keys").getString();
        }
        if (note != null) {
            int place = Math.round(px + pw - pad - 2 - x);
            if (place > 20) {
                note = Ui.ellipsis(font, note, TEXTE, place);
                Ui.right(g, font, Ui.lit(note, TEXTE), px + pw - pad - 2, y + TOUCHE_TEXTE_Y, Ui.argb(couleur, 255));
            }
        }
    }

    /** Fin de la roue : panneau principal assombri, carte « Tu as obtenu » au centre. */
    private void renderGain(GuiGraphics g, int mx, int my, long now) {
        Packets.Card c = strip.get(winIndex);
        int col = couleur(c.rarity());
        float a = Mth.clamp((now - doneAtMs - GAIN_RETARD) / (float) GAIN_FONDU, 0f, 1f);
        a = 1 - (1 - a) * (1 - a);
        g.pose().pushPose();
        g.pose().translate(0, (1 - a) * 6, 400);
        Lot lot = new Lot(g);
        lot.rect(0, -10, width, height + 10, Ui.argb(0x05060A, (int) (165 * a)));
        lot.lueur(width / 2f, gainY + GAIN_CARTE_Y + GAIN_CARTE / 2f, 160, 140, Ui.argb(col, (int) (45 * a)));
        lot.verre(gainX, gainY, gainW, gainH, (int) (235 * a));
        lot.fin();
        if (a < 0.15f) {
            g.pose().popPose();
            return;
        }
        int cx = width / 2;
        Ui.spacedCentered(g, font, Component.translatable("gui.heralizcrates.case.won").getString(), TEXTE_GRAS, cx, gainY + GAIN_TITRE_Y, Ui.argb(Ui.MUTED, 255), ESP_SECTION);
        carte(g, c, cx - GAIN_CARTE / 2, gainY + GAIN_CARTE_Y, GAIN_CARTE, GAIN_CARTE, GAIN_ITEM, 1f, 0f);
        String nom = c.stack().getHoverName().getString() + (c.count() > 1 ? "  ×" + c.count() : "");
        nom = Ui.ellipsis(font, nom, NOM, gainW - 32);
        Ui.centered(g, font, Ui.lit(nom, NOM), cx, gainY + GAIN_NOM_Y, Ui.argb(Ui.TEXT, 255));

        // boutons : « Encore (1 clé) » doré si une clé reste, « Fermer » éteint
        boolean encore = encoreVisible();
        int fx = encore ? fermerX : encoreX, fw = encore ? gainBoutonW : gainW - 32;
        lot = new Lot(g);
        if (encore) {
            boolean s = dans(mx, my, encoreX, gainBoutonY, gainBoutonW, GAIN_BOUTON_H);
            lot.rondVert(encoreX, gainBoutonY, gainBoutonW, GAIN_BOUTON_H, Ui.argb(s ? Ui.mix(OR_HAUT, 0xFFFFFF, 0.15f) : OR_HAUT, 255),
                    Ui.argb(s ? Ui.mix(OR_BAS, 0xFFFFFF, 0.15f) : OR_BAS, 255));
        }
        boolean sf = dans(mx, my, fx, gainBoutonY, fw, GAIN_BOUTON_H);
        lot.rond(fx, gainBoutonY, fw, GAIN_BOUTON_H, Ui.argb(0xFFFFFF, sf ? 26 : 15));
        lot.rondContour(fx, gainBoutonY, fw, GAIN_BOUTON_H, Ui.argb(0xFFFFFF, sf ? 40 : 20));
        lot.fin();
        if (encore) {
            String t = Component.translatable(free ? "gui.heralizcrates.case.again" : "gui.heralizcrates.case.again_key").getString().toUpperCase(Locale.ROOT);
            Ui.spacedCentered(g, font, t, TEXTE_GRAS, encoreX + gainBoutonW / 2f, gainBoutonY + 7, Ui.argb(OR_TEXTE, 255), ESP_GAIN);
        }
        String f = Component.translatable("gui.heralizcrates.case.close").getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, f, TEXTE_GRAS, fx + fw / 2f, gainBoutonY + 7, Ui.argb(sf ? Ui.TEXT : Ui.MUTED, 255), ESP_GAIN);
        g.pose().popPose();
    }

    // =================================================================== formes
    /**
     * Lot de formes envoyées en une fois à la carte graphique (quads du rendu « gui », même sens de rotation que
     * GuiGraphics.fill) : aucun texte ni objet entre la création et {@link #fin()}, la pose est lue à la création.
     */
    private static final class Lot {
        private final GuiGraphics g;
        private final VertexConsumer vc;
        private final Matrix4f m;

        Lot(GuiGraphics g) {
            this.g = g;
            this.vc = g.bufferSource().getBuffer(RenderType.gui());
            this.m = g.pose().last().pose();
        }

        void fin() {
            g.flush();
        }

        private void v(float x, float y, int c) {
            vc.vertex(m, x, y, 0).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).endVertex();
        }

        /** Quad quelconque : haut-gauche, bas-gauche, bas-droit, haut-droit (couleur par sommet). */
        void quad(float x0, float y0, float x1, float y1, int hg, int bg, int bd, int hd) {
            if (x1 <= x0 || y1 <= y0) return;
            v(x0, y0, hg);
            v(x0, y1, bg);
            v(x1, y1, bd);
            v(x1, y0, hd);
        }

        void rect(float x0, float y0, float x1, float y1, int c) {
            quad(x0, y0, x1, y1, c, c, c, c);
        }

        /** Dégradé vertical (haut -> bas). */
        void vert(float x0, float y0, float x1, float y1, int haut, int bas) {
            quad(x0, y0, x1, y1, haut, bas, bas, haut);
        }

        /** Dégradé horizontal (gauche -> droite). */
        void horiz(float x0, float y0, float x1, float y1, int gauche, int droite) {
            quad(x0, y0, x1, y1, gauche, gauche, droite, droite);
        }

        /** Triangle plein (trois sommets dans le sens de fill). */
        void tri(float ax, float ay, float bx, float by, float cx, float cy, int c) {
            v(ax, ay, c);
            v(bx, by, c);
            v(cx, cy, c);
            v(cx, cy, c);
        }

        /** Rectangle aux coins arrondis (rayon 2 px). */
        void rond(int x, int y, int w, int h, int c) {
            rondVert(x, y, w, h, c, c);
        }

        /** Rectangle arrondi en dégradé vertical. */
        void rondVert(int x, int y, int w, int h, int haut, int bas) {
            if (w < 4 || h < 4) return;
            rect(x + 2, y, x + w - 2, y + 1, lerp(haut, bas, 0f));
            rect(x + 1, y + 1, x + w - 1, y + 2, lerp(haut, bas, 1f / h));
            vert(x, y + 2, x + w, y + h - 2, lerp(haut, bas, 2f / h), lerp(haut, bas, (h - 2f) / h));
            rect(x + 1, y + h - 2, x + w - 1, y + h - 1, lerp(haut, bas, (h - 2f) / h));
            rect(x + 2, y + h - 1, x + w - 2, y + h, bas);
        }

        /** Bouton en pilule (bouts en demi-cercles), dégradé vertical. */
        void pilule(int x, int y, int w, int h, int haut, int bas) {
            float r = h / 2f;
            vert(x + r, y, x + w - r, y + h, haut, bas);
            int n = 12;
            for (int cote = 0; cote < 2; cote++) {
                float cx = cote == 0 ? x + r : x + w - r, cy = y + r;
                float sens = cote == 0 ? -1 : 1;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * i / n, a1 = Math.PI * (i + 1) / n;
                    float ax = cx + sens * r * (float) Math.sin(a0), ay = cy - r * (float) Math.cos(a0);
                    float bx = cx + sens * r * (float) Math.sin(a1), by = cy - r * (float) Math.cos(a1);
                    int ca = lerp(haut, bas, (ay - y) / h), cb = lerp(haut, bas, (by - y) / h), cc = lerp(haut, bas, 0.5f);
                    if (cote == 0) {
                        v(cx, cy, cc);
                        v(bx, by, cb);
                        v(ax, ay, ca);
                        v(ax, ay, ca);
                    } else {
                        v(cx, cy, cc);
                        v(ax, ay, ca);
                        v(bx, by, cb);
                        v(bx, by, cb);
                    }
                }
            }
        }

        /** Rectangle arrondi en dégradé à quatre coins (tuile de la caisse). */
        void rondDegrade(int x, int y, int w, int h, int hg, int hd, int bd, int bg) {
            rect(x + 2, y, x + w - 2, y + 1, lerp(hg, hd, 0.5f));
            rect(x + 1, y + 1, x + w - 1, y + 2, lerp(hg, hd, 0.5f));
            quad(x, y + 2, x + w, y + h - 2, hg, bg, bd, hd);
            rect(x + 1, y + h - 2, x + w - 1, y + h - 1, lerp(bg, bd, 0.5f));
            rect(x + 2, y + h - 1, x + w - 2, y + h, lerp(bg, bd, 0.5f));
        }

        /** Contour d'un pixel du rectangle arrondi (à l'intérieur de ses bords). */
        void rondContour(int x, int y, int w, int h, int c) {
            if (w < 4 || h < 4) return;
            rect(x + 2, y, x + w - 2, y + 1, c);
            rect(x + 2, y + h - 1, x + w - 2, y + h, c);
            rect(x, y + 2, x + 1, y + h - 2, c);
            rect(x + w - 1, y + 2, x + w, y + h - 2, c);
            rect(x + 1, y + 1, x + 2, y + 2, c);
            rect(x + w - 2, y + 1, x + w - 1, y + 2, c);
            rect(x + 1, y + h - 2, x + 2, y + h - 1, c);
            rect(x + w - 2, y + h - 2, x + w - 1, y + h - 1, c);
        }

        /** Panneau « verre » des écrans de clan : fond 0x0D0F14, contour clair, liseré haut. */
        void verre(int x, int y, int w, int h, int alpha) {
            rond(x, y, w, h, Ui.argb(Ui.VERRE, alpha));
            rondContour(x, y, w, h, Ui.argb(0xFFFFFF, 20 * alpha / 200));
            rect(x + 2, y + 1, x + w - 2, y + 2, Ui.argb(0xFFFFFF, 30 * alpha / 200));
        }

        /** Lueur ronde (ellipse) : couleur pleine au centre, transparente au bord. */
        void lueur(float cx, float cy, float rx, float ry, int c) {
            if ((c >>> 24) == 0) return;
            int bord = c & 0xFFFFFF;
            int n = 28;
            float s0 = 0f, c0 = 1f;
            for (int i = 1; i <= n; i++) {
                double t = i * 2 * Math.PI / n;
                float s1 = (float) Math.sin(t), c1 = (float) Math.cos(t);
                v(cx + rx * s0, cy - ry * c0, bord);
                v(cx, cy, c);
                v(cx, cy, c);
                v(cx + rx * s1, cy - ry * c1, bord);
                s0 = s1;
                c0 = c1;
            }
        }

        private static int lerp(int a, int b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int aa = a >>> 24, ba = b >>> 24;
            int al = (int) (aa + (ba - aa) * t);
            return (al << 24) | Ui.mix(a & 0xFFFFFF, b & 0xFFFFFF, t);
        }
    }
}
