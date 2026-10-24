package fr.heraliz.crates.client.gui;

import com.mojang.math.Axis;
import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.client.gui.kit.Echelle;
import fr.heraliz.crates.client.gui.kit.SonsUi;
import fr.heraliz.crates.client.gui.kit.Trace;
import fr.heraliz.crates.client.gui.kit.Trace.P;
import fr.heraliz.crates.client.gui.kit.Ui;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.heraliz.crates.client.gui.kit.Trace.a;
import static fr.heraliz.crates.client.gui.kit.Trace.mix;

/**
 * Écran d'une caisse, au niveau de la boutique premium (Caisses 2.22.0, maquette « variante A Vitrine » validée le
 * 24/10 : maquettes/caisses_premium/, aperçu outils/apercu_caisses_premium.py). Ouvert par un clic droit sur n'importe
 * quelle caisse, avec ou sans clé. Canevas de 640 x 360 unités (échelle forcée par {@link Echelle}), voile sombre sur le
 * monde, panneaux de verre, bord d'un pixel, filet de couleur en haut, polices cp_* à leur vraie taille.
 * <p>
 * En-tête : nom de la caisse, « Caisse d'objets · N objets à gagner », case « Tes clés » en haut à droite (avec
 * « + Acheter » si la boutique vend la clé de cette caisse : ferme l'écran et ouvre /boutique sur l'onglet Clés), onglets
 * 01 Ouvrir et 02 Contenu. Onglet Ouvrir : la roue horizontale (cartes en dégradé de la rareté, la plus grande sous le
 * repère doré), dessous l'objet sous le repère et les chances par rareté (barre et légende), à droite le panneau
 * d'ouverture (la clé, « Après l'ouverture », le bouton). Onglet Contenu : filtres par rareté à gauche, grille de cartes
 * avec la chance de chaque objet (molette). À l'arrêt de la roue, la carte « Tu as obtenu » par-dessus (Encore / Fermer).
 * Rangée des touches en bas. La roue tourne dans l'écran et ralentit en douceur (temps réel) ; Espace ou un clic la passe.
 * Tout est décidé par le serveur (CaseOpening) : l'écran ne fait qu'afficher et demander.
 * <p>
 * Toutes les mesures ci-dessous sont recopiées dans {@code outils/apercu_caisses_premium.py}, qui refait les images de
 * l'écran dans les trois langues et refuse tout texte qui dépasse : changer l'un, c'est changer l'autre.
 */
public class CaseOpenScreen extends Screen {
    private enum State { IDLE, WAIT, SPIN, DONE }

    // ------------------------------------------------------------------ mesures de la maquette (unités du canevas)
    private static final int GW = 640, GH = 360;
    private static final float M = 16, HAUT = 86, BAS = 336, PIED_Y = 348, TITRE_Y = 20, SOUS_Y = 37;
    /** Case des clés en haut à droite (avec / sans « Acheter »). */
    private static final float CLES_W = 158, CLES_W_SEUL = 112, CLES_H = 30, CLES_Y = 14;
    private static final float ONGLET_Y = 54, ONGLET_H = 22;
    /** Onglet Ouvrir : bande de la roue, carte du milieu, écart entre deux centres, retrait par pas, objet, écart entre
     *  panneaux, panneau « sous le repère + chances », ligne de la clé, bouton. */
    private static final float A_ROUE_H = 118, A_CARTE_W = 72, A_CARTE_H = 96, A_RETRAIT = 0.22F, A_ICONE = 40, A_ECART = 9,
            A_INFO_W = 380, A_LIGNE_H = 30, BOUTON_H = 22;
    private static final int STEP = 80;
    /** Onglet Contenu : colonne des raretés, hauteur d'une ligne, colonnes, carte, écart, objet. */
    private static final float C_FILTRE_W = 150, C_FILTRE_LIGNE = 27, C_CARTE_H = 76, C_GAP = 6, C_ICONE = 30;
    private static final int C_COLS = 6;
    /** Carte du gain. */
    private static final float G_W = 260, G_H = 248, G_ICONE = 64;

    // ------------------------------------------------------------------ couleurs (EcranBoutique, HeraliZ Loot)
    private static final int ACCENT = 0xE3201B, WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, OPTION = 0xC3C8D0, MUTED = 0x8F95A0, FAINT = 0x5A5F69,
            PINK = 0xF08A86, NIGHT = 0x050608, GLASS = 0x0D0F14, GOLD = 0xF2C14E, DANGER = 0xFF8A80, CARTE = 0x151922, SOMBRE = 0x10131A;
    /** Raretés de HeraliZ Loot (Commun, Peu commun, Rare, Épique, Légendaire, Unique), par rang de LootRarity. */
    private static final int[] RARETES = {0x9A9A9A, 0x5CBF4A, 0x3F8CFF, 0xA64DFF, 0xF2B83A, 0xFF3333};

    // ------------------------------------------------------------------ polices (gras, em, espacement : les bq_* de la boutique)
    private static final P TITRE = Trace.p("cp_titre", 13, 1.93F), NOM = Trace.p("cp_nom", 11, 0), BOUTON = Trace.p("cp_bouton", 9, 0.85F),
            ONGLET = Trace.p("cp_bouton", 9, 1.05F), MINI = Trace.p("cp_mini", 6.5F, 0.56F), MINI_NUM = Trace.p("cp_mini", 6.5F, 0.5F),
            TEXTE = Trace.p("cp_texte", 7.5F, 0), TEXTE_GRAS = Trace.p("cp_bouton", 9, 0), PETIT = Trace.p("cp_petit", 6.5F, 0),
            PETIT_GRAS = Trace.p("cp_mini", 6.5F, 0), MICRO = Trace.p("cp_micro", 5.6F, 0.4F), CHIFFRE_GRAND = Trace.p("cp_titre", 13, 0),
            CHIFFRE = Trace.p("cp_nom", 11, 0);

    // ------------------------------------------------------------------ roue
    /** Largeur logique d'une carte (sert au point d'arrêt : n'importe où sur la carte gagnante). */
    private static final int CW = 72;
    /** Durée de la roue en ms : celle du serveur (CrateAnim.REEL_TICKS), l'objet est donné à la fin. */
    private static final long DUREE_ROUE = CrateAnim.REEL_TICKS * 50L;
    /** Après l'arrêt : temps avant la carte du gain, puis son apparition (ms). */
    private static final long GAIN_RETARD = 650, GAIN_FONDU = 300;
    /** Cartes gardées de la bande précédente : la roue repart de ce qu'on voit. */
    private static final int KEEP = 5;
    /** Dérive lente de la roue au repos (unités par seconde). */
    private static final float DERIVE = 6f;

    private final CrateType type;
    private final int session;
    private final boolean free;
    /** La boutique (/boutique de HeraliZ Grades) vend la clé de cette caisse : boutons « Acheter ». */
    private final boolean boutique;
    private final List<Packets.Card> contents;
    private final RandomSource random = RandomSource.create();
    /** Chance totale et nombre d'objets par rareté (rang de LootRarity). */
    private final double[] chances = new double[RARETES.length];
    private final int[] nombres = new int[RARETES.length];
    private final ItemStack cle;

    private State state = State.IDLE;
    private State before = State.IDLE;
    private int ticks;
    private int waitSince, lastRequest = -100;
    private boolean spaceHeld;

    private List<Packets.Card> strip = new ArrayList<>();
    private boolean idleWrap = true;
    /** pos : coordonnée de la bande sous le repère au repos ; view : celle de l'image en cours. */
    private float pos, view, spinFrom, spinTo;
    private long spinStartMs, doneAtMs, idleMs;
    private int winIndex = -1;
    private int carteSon = Integer.MIN_VALUE;
    private long dernierSon;

    @Nullable
    private Component error;
    private int errorAt;
    private int keys;

    /** 0 : Ouvrir, 1 : Contenu. */
    private int onglet;
    /** Filtre de l'onglet Contenu : -1 = tout, sinon rang de la rareté. */
    private int filtre = -1;
    private float scroll;

    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CaseOpenScreen(CrateType type, int session, boolean free, boolean boutique, List<Packets.Card> contents) {
        super(type.crateName());
        this.type = type;
        this.session = session;
        this.free = free;
        this.boutique = boutique;
        this.contents = contents;
        this.cle = new ItemStack(ModRegistry.key(type));
        for (Packets.Card c : contents) {
            chances[rang(c.rarity())] += Math.max(0, c.chance());
            nombres[rang(c.rarity())]++;
        }
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

    // =================================================================== textes
    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heralizcrates.case." + cle, args).getString();
    }

    private static boolean anglais() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.startsWith("en");
    }

    /** 12 450 (espace en français), 12,450 sinon (comme l'aperçu). */
    private static String nombre(long v) {
        String s = String.format(Locale.ROOT, "%,d", v);
        return Ui.french() ? s.replace(',', ' ') : s;
    }

    /** Pourcentage : entier dès 10 %, une décimale dessous (deux sous 0,1 %), virgule et espace sauf en anglais. */
    static String pct(double v) {
        String s;
        if (v >= 10) s = String.format(Locale.ROOT, "%.0f", v);
        else if (v >= 0.095 || v <= 0) s = String.format(Locale.ROOT, "%.1f", v);
        else s = String.format(Locale.ROOT, "%.2f", Math.max(0.01, v));
        if (s.endsWith(".0") && v >= 1) s = s.substring(0, s.length() - 2);
        if (anglais()) return s + "%";
        return s.replace('.', ',') + " %";
    }

    private static String nomRarete(int rarity) {
        return Component.translatable("rarity.heralizcrates." + CardArt.rarity(rarity).id).getString();
    }

    private static String nomObjet(Packets.Card c) {
        String n = ChatFormatting.stripFormatting(c.stack().getHoverName().getString());
        return n == null ? "" : n;
    }

    /** Genre de la caisse pour le sous-titre (« Caisse de boss », « Caisse d'objets »…). */
    private String genre() {
        String k = switch (type) {
            case SUJET_N07, SYLVORN, MERE_DES_RUINES, PORTEUR_DE_PESTE -> "boss";
            case VOTE -> "vote";
            case QUETE -> "quest";
            case EVENT -> "event";
            default -> "items";
        };
        return t("kind." + k);
    }

    private static String nCles(int n) {
        return t(n == 1 ? "n_cles_1" : "n_cles", nombre(n));
    }

    private static String nObjets(int n) {
        return t(n == 1 ? "n_objets_1" : "n_objets", nombre(n));
    }

    // =================================================================== mise en page
    @Override
    protected void init() {
        Echelle.forcer(this);
        // canevas 640 x 360 centré (la fenêtre « 1080p » sans sa barre de titre fait 640 x 352 : 4 unités hors champ)
        ox = (width - GW) / 2;
        oy = (height - GH) / 2;
        scroll = Mth.clamp(scroll, 0, maxScroll());
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
        onglet = 0;
        play(ModRegistry.SND_KEY_TURN.get(), 1.1f, 0.45f);
        HzNet.toServer(new Packets.CaseOpen(session));
    }

    private void skip() {
        if (state != State.SPIN) return;
        finishSpin();
        HzNet.toServer(new Packets.CaseSkip());
    }

    /**
     * « + Acheter » : ferme l'écran de la caisse et demande /boutique cles (HeraliZ Grades) ; le serveur décide (mêmes
     * refus que /boutique : combat, à terre, match, tutoriel, carte…) et ouvre la boutique sur l'onglet Clés.
     */
    private void acheter() {
        if (!boutique || busy() || minecraft == null) return;
        SonsUi.clic(0.6f);
        Minecraft mc = minecraft;
        onClose();
        if (mc.player != null && mc.getConnection() != null) mc.player.connection.sendCommand("boutique cles");
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
        onglet = 0;
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

    private void changerOnglet(int o) {
        if (o == onglet || busy() || gainVisible(Util.getMillis())) return;
        onglet = o;
        SonsUi.clic(0.6f);
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
        if (key == GLFW.GLFW_KEY_TAB) {
            changerOnglet(1 - onglet);
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
        double x = mx - ox, y = my - oy;
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zones.get(i).dedans(x, y)) {
                zones.get(i).action().run();
                return true;
            }
        }
        // carte du gain : rien d'autre ne se clique dessous
        if (gainVisible(Util.getMillis())) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (onglet == 1 && !gainVisible(Util.getMillis())) {
            double x = mx - ox, y = my - oy;
            if (x >= grilleX() && x < GW - M && y >= HAUT && y < BAS) {
                scroll = Mth.clamp(scroll - (float) Math.signum(delta) * (C_CARTE_H + C_GAP) / 2, 0, maxScroll());
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return state != State.SPIN && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // =================================================================== rendu
    @Override
    public void renderBackground(GuiGraphics g) {
        voile(g);
    }

    /** Le monde reste visible derrière un voile sombre (plus sombre sur les côtés) : celui de la boutique. */
    private void voile(GuiGraphics g) {
        float[] at = {0, 0.24F, 0.46F, 0.62F, 1};
        int[] al = {235, 215, 185, 200, 232};
        for (int i = 0; i < at.length - 1; i++) {
            Trace.gradH(g, width * at[i], 0, width * at[i + 1], height, a(NIGHT, al[i]), a(NIGHT, al[i + 1]));
        }
        Trace.gradV(g, 0, 0, width, height * 0.3F, a(0, 90), a(0, 0));
        Trace.gradV(g, 0, height * 0.6F, width, height, a(0, 0), a(0, 120));
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
        sx = mouseX;
        sy = mouseY;
        zones.clear();
        renderBackground(g);
        boolean gain = gainVisible(now);
        if (gain || busy()) onglet = 0;

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        Packets.Card survolee = null;
        entete(g, f);
        if (onglet == 1) {
            survolee = contenu(g, f);
        } else {
            survolee = roue(g, f, now);
            sousRepere(g, f);
            ouverture(g, f);
        }
        if (gain) {
            zones.clear();
            carteGain(g, f, now);
        } else {
            pied(g, f);
        }
        g.pose().popPose();

        if (!gain && !busy() && state != State.DONE && survolee != null) {
            Ui.tooltip(g, font, CardArt.tooltip(survolee), mouseX, mouseY, couleur(survolee.rarity()));
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

    // ------------------------------------------------------------------ formes communes (comme la boutique)
    private static void verre(GuiGraphics g, float x, float y, float w, float h, int alpha) {
        Trace.rect(g, x, y, x + w, y + h, a(GLASS, alpha));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 26));
        Trace.rect(g, x + 1 / 3F, y + 1 / 3F, x + w - 1 / 3F, y + 2 / 3F, a(WHITE, 41));
    }

    private static void filetHaut(GuiGraphics g, float x, float y, float w, int c, float alpha) {
        float mid = x + w / 2;
        Trace.gradH(g, x + 10, y, mid, y + 2 / 3F, a(c, 0), a(c, alpha));
        Trace.gradH(g, mid, y, x + w - 10, y + 2 / 3F, a(c, alpha), a(c, 0));
    }

    private void bouton(GuiGraphics g, float x, float y, float w, float h, boolean actif, int c, float alpha) {
        if (actif) {
            float hv = survol(x, y, w, h) ? 1 : 0;
            Trace.gradH(g, x, y, x + w, y + h, a(c, (77 + 34 * hv) * alpha), a(c, (31 + 26 * hv) * alpha));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(c, (140 + 60 * hv) * alpha));
        } else {
            Trace.rect(g, x, y, x + w, y + h, a(WHITE, 13 * alpha));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 31 * alpha));
        }
    }

    private static void separateur(GuiGraphics g, float x0, float x1, float y, float alpha) {
        Trace.rect(g, x0, y, x1, y + 1 / 3F, a(WHITE, 23 * alpha));
    }

    /** Pastille pleine de la couleur (texte sombre en petites capitales), centrée (cx) ou depuis x (gauche). */
    private static float pucePleine(GuiGraphics g, Font f, String txt, float x, float cy, int c, boolean centre, float alpha) {
        float w = Trace.largeur(f, txt, MINI) + 10;
        float x0 = centre ? x - w / 2 : x;
        Trace.rect(g, x0, cy - 5, x0 + w, cy + 5, a(c, 255 * alpha));
        Trace.c(g, f, txt, x0 + w / 2, cy, MINI, a(SOMBRE, 255 * alpha), 0);
        return w;
    }

    /** La clé de la caisse (le vrai objet, dessiné par le jeu, inclinée comme dans la boutique), centrée dans la boîte. */
    private void dessinerCle(GuiGraphics g, float x, float y, float w, float h) {
        float s = Math.min(w, h) / 16f;
        g.pose().pushPose();
        g.pose().translate(x + w / 2, y + h / 2, 100);
        g.pose().mulPose(Axis.ZP.rotationDegrees(28));
        g.pose().scale(s, s, s);
        g.renderItem(cle, -8, -8);
        g.pose().popPose();
    }

    /** Quantité « ×12 » dans une pastille sombre, coin haut droit en (xDroite, haut). */
    private static void compte(GuiGraphics g, Font f, int n, float xDroite, float haut, float alpha) {
        String t = "×" + n;
        float w = Trace.largeur(f, t, MINI_NUM) + 6;
        Trace.rect(g, xDroite - w, haut, xDroite, haut + 9, a(0x07080A, 190 * alpha));
        Trace.c(g, f, t, xDroite - w / 2, haut + 4.5F, MINI_NUM, a(WHITE, 235 * alpha), 0);
    }

    // ------------------------------------------------------------------ en-tête, onglets, pied
    /** Case « Tes clés » (à la place du solde de PB) ; rend son bord gauche. */
    private float caseCles(GuiGraphics g, Font f) {
        int col = type.glow;
        float cw = boutique ? CLES_W : CLES_W_SEUL, cx = GW - M - cw, cy = CLES_Y, ch = CLES_H;
        Trace.gradH(g, cx, cy, cx + cw, cy + ch, a(col, 40), a(col, 12));
        Trace.bord(g, cx, cy, cw, ch, 1 / 3F, a(col, 110));
        Trace.lueur(g, cx + 16, cy + ch / 2, 18, 15, a(col, 60));
        dessinerCle(g, cx + 4, cy + 3, 24, 24);
        float droite = cx + cw - (boutique ? 44 : 6);
        Trace.t(g, f, t("tes_cles"), cx + 33, cy + 6, MINI, a(mix(col, WHITE, 0.45F), 230), droite - cx - 35);
        Trace.t(g, f, nombre(keys), cx + 33, cy + 15.5F, CHIFFRE_GRAND, a(keys > 0 || free ? WHITE : DANGER, 255), droite - cx - 35);
        if (boutique) {
            float bx = cx + cw - 44;
            if (!busy() && survol(bx, cy, 44, ch)) Trace.rect(g, bx, cy + 1 / 3F, cx + cw - 1 / 3F, cy + ch - 1 / 3F, a(col, 30));
            Trace.rect(g, bx, cy + 5, bx + 1 / 3F, cy + ch - 5, a(col, 70));
            Trace.rect(g, bx + 22 - 4.5F, cy + 10.5F - 0.67F, bx + 22 + 4.5F, cy + 10.5F + 0.67F, a(col, 255));
            Trace.rect(g, bx + 22 - 0.67F, cy + 10.5F - 4.5F, bx + 22 + 0.67F, cy + 10.5F + 4.5F, a(col, 255));
            Trace.c(g, f, t("acheter"), bx + 22, cy + 22, MICRO, a(mix(col, WHITE, 0.4F), 230), 40);
            zone(bx, cy, 44, ch, this::acheter);
        }
        return cx;
    }

    private void entete(GuiGraphics g, Font f) {
        float gauche = caseCles(g, f);
        Trace.t(g, f, type.crateName().getString(), M, TITRE_Y, TITRE, a(LIGHT, 255), gauche - M - 12);
        String sous = t(contents.size() == 1 ? "sous_1" : "sous", genre(), nombre(contents.size()));
        Trace.t(g, f, sous, M, SOUS_Y, TEXTE, a(MUTED, 255), gauche - M - 12);
        float x = M, ty = ONGLET_Y, th = ONGLET_H;
        boolean verrou = busy() || gainVisible(Util.getMillis());
        for (int i = 0; i < 2; i++) {
            String lab = t(i == 0 ? "onglet.ouvrir" : "onglet.contenu");
            String n = i == 1 ? nombre(contents.size()) : null;
            float badge = n == null ? 0 : Trace.largeur(f, n, MINI_NUM) + 8;
            float w = Trace.largeur(f, lab, ONGLET) + 21.33F + 12 + (badge > 0 ? badge + 5 : 0);
            boolean sel = i == onglet, hv = !sel && !verrou && survol(x, ty, w, th);
            if (sel) {
                Trace.gradH(g, x, ty, x + w * 0.75F, ty + th, a(ACCENT, 51), a(ACCENT, 10));
                Trace.gradH(g, x + w * 0.75F, ty, x + w, ty + th, a(ACCENT, 10), a(ACCENT, 0));
                Trace.bord(g, x, ty, w, th, 1 / 3F, a(ACCENT, 92));
                Trace.rect(g, x + 4, ty + th - 1.33F, x + w - 4, ty + th, a(ACCENT, 255));
            } else {
                Trace.rect(g, x, ty, x + w, ty + th, a(WHITE, hv ? 16 : 8));
                Trace.bord(g, x, ty, w, th, 1 / 3F, a(WHITE, hv ? 40 : 22));
            }
            Trace.m(g, f, String.format(Locale.ROOT, "%02d", i + 1), x + 6.67F, ty + th / 2, MINI_NUM, a(sel ? PINK : FAINT, 255), 0);
            float lw = Trace.m(g, f, lab, x + 21.33F, ty + th / 2, ONGLET, a(sel || hv ? WHITE : OPTION, 255), 0);
            if (badge > 0) {
                float bx = x + 21.33F + lw + 5;
                Trace.rect(g, bx, ty + th / 2 - 4.5F, bx + badge, ty + th / 2 + 4.5F, a(WHITE, 22));
                Trace.c(g, f, n, bx + badge / 2, ty + th / 2, MINI_NUM, a(OPTION, 255), 0);
            }
            final int o = i;
            if (!verrou) zone(x, ty, w, th, () -> changerOnglet(o));
            x += w + 4;
        }
        separateur(g, M, GW - M, ONGLET_Y + ONGLET_H + 4, 1);
    }

    /** Touches en pastilles à gauche (ESPACE Ouvrir, TAB Contenu, ÉCHAP Fermer…), note à droite. */
    private void pied(GuiGraphics g, Font f) {
        List<String[]> touches = new ArrayList<>();
        String note;
        int noteC = FAINT;
        if (onglet == 1) {
            if (maxScroll() > 0) touches.add(new String[]{"touche.molette", "action.defiler"});
            touches.add(new String[]{"touche.tab", "onglet.ouvrir"});
            note = t("note.contenu");
        } else {
            if (state == State.SPIN) touches.add(new String[]{"touche.espace", "skip"});
            else if (peutOuvrir()) touches.add(new String[]{"touche.espace", "open"});
            if (!busy()) touches.add(new String[]{"touche.tab", "onglet.contenu"});
            note = t("help.keys");
        }
        touches.add(new String[]{"touche.echap", "close"});
        if (error != null && ticks - errorAt < 80) {
            note = error.getString();
            noteC = DANGER;
        } else if (contents.isEmpty()) {
            note = t("empty");
        }
        touchesEtNote(g, f, touches, note, noteC);
    }

    private void touchesEtNote(GuiGraphics g, Font f, List<String[]> touches, @Nullable String note, int noteC) {
        float cy = PIED_Y, x = M;
        for (String[] k : touches) {
            String kl = t(k[0]).toUpperCase(Locale.ROOT);
            float tw = Trace.largeur(f, kl, MINI_NUM), w = Math.max(9, tw + 4.67F);
            Trace.rect(g, x, cy - 4.5F, x + w, cy + 4.5F, a(WHITE, 30));
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(WHITE, 61));
            Trace.c(g, f, kl, x + w / 2, cy, MINI_NUM, a(WHITE, 255), 0);
            x += w + 3.5F;
            x += Trace.m(g, f, t(k[1]), x, cy, PETIT, a(MUTED, 255), 0) + 10;
        }
        if (note != null && GW - M - x - 8 > 20) Trace.d(g, f, note, GW - M, cy, PETIT, a(noteC, 255), GW - M - x - 8);
    }

    // ------------------------------------------------------------------ onglet Ouvrir : la roue
    /** Centre vertical des cartes de la roue (canevas). */
    private static float roueCy() {
        return HAUT + A_ROUE_H / 2 + 2;
    }

    /** Index de la carte sous le repère (celle dont le centre est le plus proche). */
    private int indexSousRepere() {
        return Math.round((view - CW / 2f) / STEP);
    }

    /** La roue ; rend la carte survolée (roue à l'arrêt), pour l'infobulle. */
    @Nullable
    private Packets.Card roue(GuiGraphics g, Font f, long now) {
        float rx = M, ry = HAUT, rw = GW - 2 * M, rh = A_ROUE_H;
        verre(g, rx, ry, rw, rh, 175);
        float centre = rx + rw / 2, cy = roueCy();
        boolean fini = state == State.DONE && winIndex >= 0 && winIndex < strip.size() && !idleWrap;
        float pose = fini ? Mth.clamp((now - doneAtMs) / 350f, 0f, 1f) : 0f;
        pose = 1 - (1 - pose) * (1 - pose);

        Packets.Card survolee = null;
        g.enableScissor(Math.max(0, ox + Math.round(rx + 1)), Math.max(0, oy + Math.round(ry + 1)), ox + Math.round(rx + rw - 1), oy + Math.round(ry + rh - 1));
        int first = Mth.floor((view - rw / 2f) / STEP) - 1, last = Mth.floor((view + rw / 2f) / STEP) + 1;
        // des bords vers le milieu : la carte centrale passe par-dessus ses voisines
        List<Integer> ordre = new ArrayList<>();
        for (int i = first; i <= last; i++) ordre.add(i);
        ordre.sort((p, q) -> Float.compare(Math.abs(q * STEP + CW / 2f - view), Math.abs(p * STEP + CW / 2f - view)));
        int z = 0;
        for (int i : ordre) {
            Packets.Card c = at(i);
            if (c == null) continue;
            float d = i * STEP + CW / 2f - view;
            float s = 1 / (1 + A_RETRAIT * Math.abs(d) / STEP);
            float w = A_CARTE_W * s, h = A_CARTE_H * s;
            float fondu = Mth.clamp((Math.abs(d) - STEP * 1.2F) / (rw / 2 - STEP * 1.2F), 0, 1);
            boolean gagnante = fini && i == winIndex;
            float avant = fini ? (gagnante ? 1 : 0) : Mth.clamp(1 - Math.abs(d) / (STEP * 0.75F), 0, 1);
            float voile = Math.max(fondu * 0.75F, fini && !gagnante ? 0.6F * pose : 0);
            g.pose().pushPose();
            g.pose().translate(0, 0, z += 2);
            carteRoue(g, f, c, centre + d, cy, w, h, A_ICONE * s, avant, voile);
            g.pose().popPose();
            if (!busy() && survol(centre + d - w / 2, cy - h / 2, w, h) && survol(rx, ry, rw, rh)) survolee = c;
        }
        g.flush();
        g.disableScissor();
        // bords : la roue s'efface sous le verre ; repères dorés en haut et en bas
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Trace.gradH(g, rx + 1 / 3F, ry + 1, rx + 70, ry + rh - 1, a(GLASS, 230), a(GLASS, 0));
        Trace.gradH(g, rx + rw - 70, ry + 1, rx + rw - 1 / 3F, ry + rh - 1, a(GLASS, 0), a(GLASS, 230));
        Trace.repere(g, centre, ry + 1.5F, 5, true, a(GOLD, 255));
        Trace.repere(g, centre, ry + rh - 1.5F, 5, false, a(GOLD, 255));
        g.pose().popPose();
        return survolee;
    }

    /**
     * Carte d'objet de la roue (w x h, centrée en cx, cy) : verre, dégradé de la rareté depuis le haut, lueur derrière
     * l'objet, trait de couleur en bas ; avant (0..1) = carte sous le repère (bord de couleur, filet, nom de la rareté) ;
     * voile (0..1) = effacement vers les bords, ou les autres cartes après l'arrêt.
     */
    private void carteRoue(GuiGraphics g, Font f, Packets.Card c, float cx, float cy, float w, float h, float icone, float avant, float voile) {
        int col = couleur(c.rarity());
        float x = cx - w / 2, y = cy - h / 2;
        if (avant > 0) Trace.lueur(g, cx, cy, w * 0.85F, h * 0.75F, a(col, 60 * avant));
        Trace.rect(g, x, y, x + w, y + h, a(CARTE, 240));
        Trace.gradV(g, x, y, x + w, y + h * 0.62F, a(col, 30 + 40 * avant), a(col, 0));
        float iy = cy - h * 0.08F;
        Trace.lueur(g, cx, iy, w * 0.42F, w * 0.42F, a(col, 55 + 50 * avant));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 26 * (1 - avant)));
        if (avant > 0) {
            Trace.bord(g, x, y, w, h, 2 / 3F, a(col, 200 * avant));
            filetHaut(g, x, y, w, mix(col, WHITE, 0.3F), 255 * avant);
        }
        Trace.rect(g, cx - w * 0.3F, y + h - 4, cx + w * 0.3F, y + h - 4 + 4 / 3F, a(col, 255));
        if (avant > 0.02F) Trace.c(g, f, nomRarete(c.rarity()), cx, y + h - 13, MICRO, a(col, 255 * avant), w - 8);
        CardArt.item(g, c.stack(), cx, iy, icone);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        if (c.count() > 1) compte(g, f, c.count(), x + w - 3, y + 3, 1 - voile);
        if (voile > 0) Trace.rect(g, x - 1, y - 1, x + w + 1, y + h + 1, a(GLASS, 225 * voile));
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ onglet Ouvrir : sous le repère et chances
    private void sousRepere(GuiGraphics g, Font f) {
        float by = HAUT + A_ROUE_H + A_ECART, ix = M, iw = A_INFO_W;
        verre(g, ix, by, iw, BAS - by, 199);
        float x = ix + 12, y = by + 11, w = iw - 24;
        Packets.Card o = at(indexSousRepere());
        Trace.t(g, f, t("sous_repere"), x, y, MINI, a(MUTED, 255), w);
        if (o != null) {
            String nom = nomObjet(o) + (o.count() > 1 ? "  ×" + o.count() : "");
            Trace.t(g, f, nom, x, y + 11, NOM, a(WHITE, 255), w);
            float pw = pucePleine(g, f, nomRarete(o.rarity()), x, y + 31, couleur(o.rarity()), false, 1);
            Trace.m(g, f, t("chance", pct(o.chance())), x + pw + 6, y + 31, TEXTE, a(OPTION, 255), w - pw - 6);
        }
        separateur(g, ix + 12, ix + iw - 12, by + 51, 1);
        Trace.t(g, f, t("chances_rarete"), x, by + 59, MINI, a(MUTED, 255), w);
        legende(g, f, x, by + 72, w);
    }

    /** Barre des chances (une part par rareté) et légende en trois colonnes : pastille, nom, pourcentage. */
    private void legende(GuiGraphics g, Font f, float x, float y, float w) {
        List<Integer> rs = new ArrayList<>();
        for (int r = 0; r < RARETES.length; r++) if (nombres[r] > 0) rs.add(r);
        if (rs.isEmpty()) return;
        double total = 0;
        for (int r : rs) total += chances[r];
        float xx = x;
        for (int k = 0; k < rs.size(); k++) {
            int r = rs.get(k);
            boolean der = k == rs.size() - 1;
            float fin = der ? x + w : xx + (float) (total > 0 ? chances[r] / total : 0) * w;
            Trace.rect(g, xx, y, Math.max(xx + 1, fin - (der ? 0 : 2 / 3F)), y + 4, a(RARETES[r], 255));
            xx = fin;
        }
        float colw = (w - 2 * 14) / 3;
        for (int k = 0; k < rs.size(); k++) {
            int r = rs.get(k);
            float cx = x + (k % 3) * (colw + 14), cy = y + 16 + (k / 3) * 13;
            Trace.disque(g, cx + 2.4F, cy, 2.4F, a(RARETES[r], 255));
            float pw = Trace.d(g, f, pct(total > 0 ? chances[r] / total * 100 : 0), cx + colw, cy, PETIT_GRAS, a(mix(RARETES[r], WHITE, 0.25F), 255), 0);
            Trace.m(g, f, nomRarete(r), cx + 8, cy, TEXTE, a(LIGHT, 255), colw - 8 - pw - 4);
        }
    }

    // ------------------------------------------------------------------ onglet Ouvrir : panneau d'ouverture
    private void ouverture(GuiGraphics g, Font f) {
        float by = HAUT + A_ROUE_H + A_ECART;
        float ox0 = M + A_INFO_W + A_ECART, ow = GW - M - ox0;
        verre(g, ox0, by, ow, BAS - by, 199);
        filetHaut(g, ox0, by, ow, ACCENT, 255);
        float cx = ox0 + 10, cw = ow - 20;
        Trace.t(g, f, t("ouverture"), cx, by + 11, MINI, a(MUTED, 255), cw);
        boolean sansCle = !free && keys < 1 && !busy();
        float ly = by + 21;
        Trace.rect(g, cx, ly, cx + cw, ly + A_LIGNE_H, a(WHITE, 10));
        Trace.bord(g, cx, ly, cw, A_LIGNE_H, 1 / 3F, a(WHITE, 30));
        dessinerCle(g, cx + 4, ly + 3, 22, A_LIGNE_H - 6);
        float nw = Trace.d(g, f, "×" + nombre(keys), cx + cw - 7, ly + A_LIGNE_H / 2, CHIFFRE, a(sansCle ? DANGER : WHITE, 255), 0);
        float place = cw - 30 - nw - 12;
        String nomCle = type.keyName().getString();
        Trace.m(g, f, nomCle, cx + 30, ly + A_LIGNE_H / 2 - 4.2F, TEXTE_GRAS, a(WHITE, 255), place);
        if (sansCle) Trace.m(g, f, t("aucune", nomCle), cx + 30, ly + A_LIGNE_H / 2 + 5.2F, PETIT_GRAS, a(DANGER, 255), place);
        else Trace.m(g, f, free ? t("keys_free") : t("une_cle"), cx + 30, ly + A_LIGNE_H / 2 + 5.2F, PETIT, a(MUTED, 255), place);

        float yy = ly + A_LIGNE_H + 10, bh = BOUTON_H, bty = BAS - 10 - bh;
        if (peutOuvrir() && !free) {
            Trace.m(g, f, t("apres"), cx, yy, PETIT, a(MUTED, 255), cw - 50);
            Trace.d(g, f, nCles(Math.max(0, keys - 1)), cx + cw, yy, PETIT_GRAS, a(mix(type.glow, WHITE, 0.35F), 255), 0);
        }
        if (sansCle && boutique) {
            // vers la boutique (/boutique, onglet Clés) : seulement pour les caisses dont la boutique vend la clé
            float sy = bty - 6 - 20;
            boolean hv = survol(cx, sy, cw, 20);
            Trace.gradH(g, cx, sy, cx + cw, sy + 20, a(GOLD, hv ? 64 : 40), a(GOLD, hv ? 24 : 12));
            Trace.bord(g, cx, sy, cw, 20, 1 / 3F, a(GOLD, hv ? 180 : 120));
            Trace.c(g, f, "+ " + t("acheter_cles"), cx + cw / 2, sy + 10, BOUTON, a(mix(GOLD, WHITE, 0.3F), 255), cw - 8);
            zone(cx, sy, cw, 20, this::acheter);
        }
        String lab;
        boolean actif = peutOuvrir() || busy();
        if (state == State.SPIN) lab = t("skip_space");
        else if (state == State.WAIT) lab = t("wait");
        else if (contents.isEmpty()) lab = t("empty");
        else if (sansCle) lab = t("need_key");
        else lab = t(free ? "open" : "open_key");
        bouton(g, cx, bty, cw, bh, actif, ACCENT, state == State.WAIT ? 0.6F : 1);
        Trace.c(g, f, lab, cx + cw / 2, bty + bh / 2, BOUTON, a(actif ? WHITE : MUTED, 255), cw - 8);
        if (peutOuvrir()) zone(cx, bty, cw, bh, () -> {
            SonsUi.clic(0.6f);
            open();
        });
    }

    // ------------------------------------------------------------------ onglet Contenu
    private static float grilleX() {
        return M + C_FILTRE_W + A_ECART;
    }

    private List<Packets.Card> filtres() {
        if (filtre < 0) return contents;
        List<Packets.Card> l = new ArrayList<>();
        for (Packets.Card c : contents) if (rang(c.rarity()) == filtre) l.add(c);
        return l;
    }

    private float maxScroll() {
        int n = filtres().size();
        int rangs = (n + C_COLS - 1) / C_COLS;
        return Math.max(0, rangs * (C_CARTE_H + C_GAP) - C_GAP - (BAS - HAUT));
    }

    /** Filtres par rareté à gauche, grille de cartes à droite ; rend la carte survolée. */
    @Nullable
    private Packets.Card contenu(GuiGraphics g, Font f) {
        float fx = M, fw = C_FILTRE_W;
        verre(g, fx, HAUT, fw, BAS - HAUT, 199);
        Trace.t(g, f, t("raretes"), fx + 10, HAUT + 11, MINI, a(MUTED, 255), fw - 20);
        double total = 0;
        for (double v : chances) total += v;
        float y = HAUT + 22;
        for (int r = -1; r < RARETES.length; r++) {
            if (r >= 0 && nombres[r] == 0) continue;
            boolean s = r == filtre;
            float h = C_FILTRE_LIGNE - 3;
            boolean hv = !s && survol(fx + 6, y, fw - 12, h);
            if (s) {
                Trace.gradH(g, fx + 6, y, fx + fw - 6, y + h, a(ACCENT, 60), a(ACCENT, 14));
                Trace.bord(g, fx + 6, y, fw - 12, h, 1 / 3F, a(ACCENT, 140));
            } else {
                Trace.rect(g, fx + 6, y, fx + fw - 6, y + h, a(WHITE, hv ? 16 : 8));
            }
            int col = r < 0 ? WHITE : RARETES[r];
            if (r >= 0) Trace.disque(g, fx + 14, y + h / 2, 2.6F, a(col, 255));
            float lx = fx + 22;
            double p = r < 0 ? 100 : total > 0 ? chances[r] / total * 100 : 0;
            float pw = Trace.d(g, f, pct(p), fx + fw - 12, y + h / 2, PETIT_GRAS, a(r >= 0 ? mix(col, WHITE, 0.25F) : OPTION, 255), 0);
            float place = fx + fw - 12 - pw - 4 - lx;
            Trace.m(g, f, r < 0 ? t("tout") : nomRarete(r), lx, y + h / 2 - 3.8F, s ? TEXTE_GRAS : TEXTE, a(s || hv ? WHITE : LIGHT, 255), place);
            Trace.m(g, f, nObjets(r < 0 ? contents.size() : nombres[r]), lx, y + h / 2 + 4.6F, PETIT, a(MUTED, 255), place);
            final int rr = r;
            zone(fx + 6, y, fw - 12, h, () -> {
                if (filtre != rr) {
                    filtre = rr;
                    scroll = 0;
                    SonsUi.clic(0.6f);
                }
            });
            y += C_FILTRE_LIGNE;
        }

        float gx = grilleX(), gw = GW - M - gx - 8;
        float cw = (gw - (C_COLS - 1) * C_GAP) / C_COLS;
        List<Packets.Card> objets = filtres();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        Packets.Card survolee = null;
        if (objets.isEmpty()) {
            Trace.c(g, f, t("empty"), gx + gw / 2, HAUT + 40, TEXTE, a(FAINT, 255), gw);
        }
        g.enableScissor(Math.max(0, ox + Math.round(gx - 1)), Math.max(0, oy + Math.round(HAUT)), ox + Math.round(gx + gw + 1), oy + Math.round(BAS));
        boolean dansGrille = survol(gx, HAUT, gw, BAS - HAUT);
        int z = 0;
        for (int i = 0; i < objets.size(); i++) {
            float x = gx + (i % C_COLS) * (cw + C_GAP);
            float cy = HAUT + (i / C_COLS) * (C_CARTE_H + C_GAP) - scroll;
            if (cy + C_CARTE_H < HAUT || cy > BAS) continue;
            Packets.Card c = objets.get(i);
            boolean hv = dansGrille && survol(x, cy, cw, C_CARTE_H);
            if (hv) survolee = c;
            g.pose().pushPose();
            g.pose().translate(0, 0, z += 2);
            carteContenu(g, f, c, x, cy, cw, hv);
            g.pose().popPose();
        }
        g.flush();
        g.disableScissor();
        // barre de défilement
        float max = maxScroll();
        if (max > 0) {
            float sxb = GW - M - 3, total2 = max + (BAS - HAUT);
            float part = (BAS - HAUT) / total2;
            float h = (BAS - HAUT) * part, y0 = HAUT + (BAS - HAUT - h) * (scroll / max);
            Trace.rect(g, sxb, HAUT, sxb + 2, BAS, a(WHITE, 18));
            Trace.rect(g, sxb, y0, sxb + 2, y0 + h, a(WHITE, 110));
        }
        return survolee;
    }

    private void carteContenu(GuiGraphics g, Font f, Packets.Card c, float x, float y, float cw, boolean hv) {
        int col = couleur(c.rarity());
        Trace.rect(g, x, y, x + cw, y + C_CARTE_H, a(CARTE, 235));
        Trace.gradV(g, x, y, x + cw, y + C_CARTE_H * 0.6F, a(col, hv ? 60 : 36), a(col, 0));
        Trace.lueur(g, x + cw / 2, y + 24, cw * 0.4F, 18, a(col, 70));
        Trace.bord(g, x, y, cw, C_CARTE_H, hv ? 2 / 3F : 1 / 3F, hv ? a(col, 200) : a(WHITE, 26));
        if (hv) filetHaut(g, x, y, cw, mix(col, WHITE, 0.3F), 255);
        List<String> ls = Trace.lignes(f, nomObjet(c), PETIT, cw - 8);
        if (ls.size() > 2) ls = List.of(ls.get(0), Trace.tenir(f, String.join(" ", ls.subList(1, ls.size())), PETIT, cw - 8));
        for (int k = 0; k < ls.size(); k++) {
            Trace.c(g, f, ls.get(k), x + cw / 2, y + 46 + k * 8.5F + (ls.size() == 1 ? 4.25F : 0), PETIT, a(LIGHT, 255), cw - 8);
        }
        Trace.c(g, f, pct(c.chance()), x + cw / 2, y + 66, PETIT_GRAS, a(mix(col, WHITE, 0.25F), 255), cw - 8);
        Trace.rect(g, x + cw * 0.2F, y + C_CARTE_H - 4, x + cw * 0.8F, y + C_CARTE_H - 4 + 4 / 3F, a(col, 255));
        CardArt.item(g, c.stack(), x + cw / 2, y + 24, C_ICONE);
        if (c.count() > 1) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            compte(g, f, c.count(), x + cw - 3, y + 3, 1);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ carte du gain
    /** Fin de la roue : écran assombri, carte « Tu as obtenu » au centre (Encore / Fermer, clés restantes). */
    private void carteGain(GuiGraphics g, Font f, long now) {
        Packets.Card o = strip.get(winIndex);
        int col = couleur(o.rarity());
        float an = Mth.clamp((now - doneAtMs - GAIN_RETARD) / (float) GAIN_FONDU, 0f, 1f);
        an = 1 - (1 - an) * (1 - an);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Trace.rect(g, -ox, -oy, width - ox, height - oy, a(NIGHT, 175 * an));
        g.pose().translate(0, (1 - an) * 6, 0);
        float x = (GW - G_W) / 2, y = (GH - G_H) / 2 - 6, mid = GW / 2F;
        Trace.lueur(g, mid, y + 90, 200, 140, a(col, 50 * an));
        Trace.rect(g, x, y, x + G_W, y + G_H, a(GLASS, 238 * an));
        Trace.gradV(g, x, y, x + G_W, y + 170, a(col, 80 * an), a(col, 0));
        float icy = y + 86, tour = (now % 60000L) / 1000f * 0.08F;
        for (int k = 0; k < 40; k++) {
            double ang = k / 40.0 * 2 * Math.PI + tour;
            float r0 = 44, r1 = k % 2 == 0 ? 82 : 66;
            float sn = (float) Math.sin(ang), cs = (float) Math.cos(ang);
            Trace.trait(g, mid + r0 * sn, icy - r0 * cs, mid + r1 * sn, icy - r1 * cs, 0.6F, a(col, 60 * an));
        }
        Trace.lueur(g, mid, icy, 58, 58, a(col, 110 * an));
        Trace.anneau(g, mid, icy, 45.5F, 46, a(col, 70 * an));
        Trace.bord(g, x, y, G_W, G_H, 2 / 3F, a(col, 200 * an));
        filetHaut(g, x, y, G_W, mix(col, WHITE, 0.3F), 255 * an);
        if (an < 0.15F) {
            g.pose().popPose();
            return;
        }
        Trace.c(g, f, t("won"), mid, y + 14, MINI, a(mix(col, WHITE, 0.45F), 255 * an), G_W - 20);
        Trace.lueur(g, mid, icy + 38, 34, 4, a(0, 150 * an));
        pucePleine(g, f, nomRarete(o.rarity()), mid, y + 140, col, true, an);
        String nom = nomObjet(o) + (o.count() > 1 ? "  ×" + o.count() : "");
        List<String> ls = Trace.lignes(f, nom, NOM, G_W - 24);
        if (ls.size() > 2) ls = List.of(ls.get(0), Trace.tenir(f, String.join(" ", ls.subList(1, ls.size())), NOM, G_W - 24));
        for (int k = 0; k < ls.size(); k++) Trace.c(g, f, ls.get(k), mid, y + 157 + k * 13, NOM, a(WHITE, 255 * an), G_W - 24);
        float ny = y + 157 + ls.size() * 13 + 2;
        Trace.c(g, f, t("ajoute") + " · " + t("chance", pct(o.chance())), mid, ny, PETIT, a(OPTION, 255 * an), G_W - 24);
        separateur(g, x + 14, x + G_W - 14, y + 186, an);
        String reste = free ? t("keys_free") : keys <= 0 ? t("reste_0") : t(keys == 1 ? "reste_1" : "reste", nombre(keys));
        Trace.c(g, f, reste, mid, y + 196, PETIT, a(MUTED, 255 * an), G_W - 24);

        // boutons : « Ouvrir · 1 clé » si une clé reste (sinon « + Acheter des clés » si la boutique la vend), « Fermer »
        float bh = BOUTON_H, bw = (G_W - 24 - 8) / 2, by = y + G_H - 12 - bh;
        boolean encore = free || keys >= 1;
        if (encore || boutique) {
            float bx = x + 12;
            if (encore) {
                bouton(g, bx, by, bw, bh, true, ACCENT, an);
                Trace.c(g, f, t(free ? "open" : "open_key"), bx + bw / 2, by + bh / 2, BOUTON, a(WHITE, 255 * an), bw - 8);
                zone(bx, by, bw, bh, () -> {
                    SonsUi.clic(0.6f);
                    open();
                });
            } else {
                bouton(g, bx, by, bw, bh, true, GOLD, an);
                Trace.c(g, f, t("acheter_cles"), bx + bw / 2, by + bh / 2, BOUTON, a(mix(GOLD, WHITE, 0.3F), 255 * an), bw - 8);
                zone(bx, by, bw, bh, this::acheter);
            }
            float fx = bx + bw + 8;
            boolean hv = survol(fx, by, bw, bh);
            bouton(g, fx, by, bw, bh, false, WHITE, an);
            if (hv) Trace.rect(g, fx, by, fx + bw, by + bh, a(WHITE, 12 * an));
            Trace.c(g, f, t("close"), fx + bw / 2, by + bh / 2, BOUTON, a(hv ? WHITE : OPTION, 255 * an), bw - 8);
            zone(fx, by, bw, bh, this::fermer);
        } else {
            float bx = x + 12, bw2 = G_W - 24;
            boolean hv = survol(bx, by, bw2, bh);
            bouton(g, bx, by, bw2, bh, false, WHITE, an);
            if (hv) Trace.rect(g, bx, by, bx + bw2, by + bh, a(WHITE, 12 * an));
            Trace.c(g, f, t("close"), bx + bw2 / 2, by + bh / 2, BOUTON, a(hv ? WHITE : OPTION, 255 * an), bw2 - 8);
            zone(bx, by, bw2, bh, this::fermer);
        }
        // l'objet gagné par-dessus son halo
        CardArt.item(g, o.stack(), mid, icy, G_ICONE);
        g.pose().popPose();

        List<String[]> touches = new ArrayList<>();
        if (encore) touches.add(new String[]{"touche.espace", "action.encore"});
        touches.add(new String[]{"touche.echap", "close"});
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        touchesEtNote(g, f, touches, null, FAINT);
        g.pose().popPose();
    }

    private void fermer() {
        SonsUi.clic(0.6f);
        onClose();
    }
}
