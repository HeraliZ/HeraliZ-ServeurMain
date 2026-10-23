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
 * Ecran d'une caisse (maquette « caisses » validée, style des écrans de clan) : ouvert par un clic droit sur n'importe
 * quelle caisse, avec ou sans clé. Grand panneau de verre : en-tête (tuile de la caisse, nom, sous-titre, clés), la roue
 * (bande de cartes, repère doré au milieu, bords fondus), en bas le contenu possible (grille de cartes) et, à droite, les
 * chances par rareté puis le bouton (« Il te faut une clé » éteint, ou « Ouvrir · 1 clé » doré). La roue tourne dans
 * l'écran et ralentit en douceur (temps réel, rien par à-coups) ; à l'arrêt, une carte « Tu as obtenu » (Encore / Fermer).
 * Espace ou un clic pendant la roue la passe. Tout est décidé par le serveur (CaseOpening) : l'écran ne fait qu'afficher.
 */
public class CaseOpenScreen extends Screen {
    private enum State { IDLE, WAIT, SPIN, DONE }

    /** Couleurs des raretés de la maquette (Commun, Peu commun, Rare, Épique, Légendaire, Unique), par rang de LootRarity. */
    private static final int[] RARETES = {0xB0B6C0, 0x5FD08A, 0x4C8DFF, 0xA86BFF, 0xE8C36A, 0xFF5A5A};
    private static final int OR_HAUT = 0xF0CF7B, OR_BAS = 0xD9AC4C, OR_TEXTE = 0x1A1408;

    /** Cartes de la roue (largeur, hauteur, écart) et de la grille du contenu. */
    private static final int CW = 72, CH = 72, GAP = 6, STEP = CW + GAP;
    private static final int GRILLE_COLS = 6, GRILLE_H = 52, GRILLE_GAP = 5;
    private static final int ROUE_H = 92, BOUTON_H = 22, LIGNE_CHANCE = 13;
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
    private final ItemStack icone;
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
        this.icone = new ItemStack(ModRegistry.key(type));
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
        px = Math.round(width * 0.05f);
        pw = width - 2 * px;
        py = Math.round(height * 0.07f);
        ph = height - 2 * py;
        pad = 14;
        roueX = px + pad;
        roueW = pw - 2 * pad;
        roueY = py + 12 + 26 + 12;
        basY = roueY + ROUE_H + 10;
        colW = 142;
        colX = px + pw - pad - colW;
        grilleX = px + pad;
        grilleW = colX - 14 - grilleX;
        grilleY = basY + 12;
        grilleH = py + ph - pad - grilleY;
        celluleW = (grilleW - (GRILLE_COLS - 1) * GRILLE_GAP) / GRILLE_COLS;
        boutonX = colX;
        boutonW = colW;
        boutonY = py + ph - pad - 11 - BOUTON_H;
        // carte du gain, au centre de l'écran
        gainW = 214;
        gainH = 200;
        gainX = (width - gainW) / 2;
        gainY = (height - gainH) / 2;
        gainBoutonW = (gainW - 32 - 8) / 2;
        gainBoutonY = gainY + gainH - 16 - 20;
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
            if (encoreVisible() && dans(mx, my, encoreX, gainBoutonY, gainBoutonW, 20)) {
                SonsUi.clic(0.6f);
                open();
            } else if (dans(mx, my, encoreVisible() ? fermerX : encoreX, gainBoutonY, encoreVisible() ? gainBoutonW : gainW - 32, 20)) {
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
            grilleScroll = Mth.clamp(grilleScroll - (int) Math.signum(delta) * (GRILLE_H + GRILLE_GAP) / 2, 0, maxScroll());
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
        lot.fin();
        renderEntete(g);
        renderRoue(g, now);
        Packets.Card survolee = renderGrille(g, mouseX, mouseY);
        renderChances(g);
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

    private void renderEntete(GuiGraphics g) {
        int x = px + pad, y = py + 12;
        int glow = type.glow & 0xFFFFFF, acc = type.accent & 0xFFFFFF;
        // tuile de la caisse : dégradé de ses couleurs, halo doux, la clé dessus
        Lot lot = new Lot(g);
        lot.lueur(x + 13, y + 13, 24, 24, Ui.argb(glow, 70));
        int a = Ui.mix(glow, 0x0D0F14, 0.25f), b = Ui.mix(acc, 0x0D0F14, 0.35f);
        lot.rondDegrade(x, y, 26, 26, Ui.argb(a, 255), Ui.argb(Ui.mix(a, b, 0.5f), 255), Ui.argb(b, 255), Ui.argb(Ui.mix(a, b, 0.5f), 255));
        lot.rondContour(x, y, 26, 26, Ui.argb(0xFFFFFF, 40));
        lot.fin();
        CardArt.item(g, icone, x + 13, y + 13, 18);

        int tx = x + 26 + 9;
        String titre = type.crateName().getString().toUpperCase(Locale.ROOT);
        g.pose().pushPose();
        g.pose().translate(tx, y + 2, 0);
        g.pose().scale(1.15f, 1.15f, 1);
        Ui.spaced(g, font, titre, Ui.BOLD, 0, 0, Ui.argb(Ui.TEXT, 255), 0.8f);
        g.pose().popPose();
        String etat = state == State.SPIN || state == State.WAIT ? "gui.heralizcrates.case.state.spin"
                : state == State.DONE ? "gui.heralizcrates.case.state.done" : "gui.heralizcrates.case.state.preview";
        String sous = Component.translatable(categoryKey()).getString() + " · " + Component.translatable(etat).getString();
        Ui.spaced(g, font, sous, CardArt.MINI, tx, y + 17, Ui.argb(Ui.MUTED, 255), 0.9f);

        // pastille des clés, à droite
        Component lib = free ? Ui.tr(Ui.SMALL, "gui.heralizcrates.case.keys_free")
                : Ui.tr(Ui.SMALL, "gui.heralizcrates.case.keys_label").append(Ui.lit(" " + keys, Ui.BOLD).withStyle(st -> st.withColor(Ui.OR)));
        int lw = font.width(lib), pillW = lw + 18, pillH = 15;
        int pillX = px + pw - pad - pillW, pillY = y + 5;
        lot = new Lot(g);
        lot.rond(pillX, pillY, pillW, pillH, Ui.argb(0xFFFFFF, 15));
        lot.rondContour(pillX, pillY, pillW, pillH, Ui.argb(0xFFFFFF, 20));
        lot.fin();
        g.drawString(font, lib, pillX + 9, pillY + 3, Ui.argb(Ui.TEXT, 255), false);
    }

    /** Sorte de la caisse (« Caisse de boss », « Caisse de vote »…). */
    private String categoryKey() {
        return switch (type) {
            case SUJET_N07, SYLVORN, MERE_DES_RUINES, PORTEUR_DE_PESTE -> "gui.heralizcrates.case.kind.boss";
            case VOTE -> "gui.heralizcrates.case.kind.vote";
            case QUETE -> "gui.heralizcrates.case.kind.quest";
            case EVENT -> "gui.heralizcrates.case.kind.event";
            default -> "gui.heralizcrates.case.kind.items";
        };
    }

    private int cartesY() {
        return roueY + (ROUE_H - CH) / 2;
    }

    /** Carte de la roue sous la souris (roue à l'arrêt). */
    @Nullable
    private Packets.Card carteRoueSous(int mx, int my) {
        int y0 = cartesY();
        if (my < y0 || my >= y0 + CH || mx < roueX || mx >= roueX + roueW) return null;
        float s = mx - (roueX + roueW / 2f) + view;
        int i = Mth.floor(s / STEP);
        return s - i * STEP < CW ? at(i) : null;
    }

    private void renderRoue(GuiGraphics g, long now) {
        int x0 = roueX, x1 = roueX + roueW, y0 = roueY, y1 = roueY + ROUE_H;
        float centre = roueX + roueW / 2f;
        Lot lot = new Lot(g);
        lot.rond(x0, y0, roueW, ROUE_H, Ui.argb(0x000000, 90));
        lot.rondContour(x0, y0, roueW, ROUE_H, Ui.argb(0xFFFFFF, 15));
        lot.fin();

        boolean fini = state == State.DONE && winIndex >= 0 && winIndex < strip.size() && !idleWrap;
        float pose = fini ? Mth.clamp((now - doneAtMs) / 350f, 0f, 1f) : 0f;
        pose = 1 - (1 - pose) * (1 - pose);

        g.enableScissor(x0 + 1, y0 + 1, x1 - 1, y1 - 1);
        // les cartes glissent au sous-pixel : base entière + décalage fractionnaire dans la pose (aucun à-coup à la fin)
        float base = centre - view;
        int baseI = Mth.floor(base);
        g.pose().pushPose();
        g.pose().translate(base - baseI, 0, 0);
        int y = cartesY();
        int first = Mth.floor((view - roueW / 2f) / STEP) - 1, last = Mth.floor((view + roueW / 2f) / STEP) + 1;
        for (int i = first; i <= last; i++) {
            Packets.Card c = at(i);
            if (c == null) continue;
            int x = baseI + i * STEP;
            boolean gagnante = fini && i == winIndex;
            carte(g, c, x, y, CW, CH, 26, gagnante ? pose : 0f, fini && !gagnante ? pose : 0f);
        }
        g.pose().popPose();

        // bords fondus et repère doré, au-dessus des objets
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        lot = new Lot(g);
        int fondu = Math.round(roueW * 0.18f);
        lot.horiz(x0, y0, x0 + fondu, y1, Ui.argb(Ui.VERRE, 235), Ui.argb(Ui.VERRE, 0));
        lot.horiz(x1 - fondu, y0, x1, y1, Ui.argb(Ui.VERRE, 0), Ui.argb(Ui.VERRE, 235));
        float cx = Math.round(centre) - 1;
        lot.horiz(cx - 7, y0, cx, y1, Ui.argb(Ui.OR, 0), Ui.argb(Ui.OR, 70));
        lot.horiz(cx + 2, y0, cx + 9, y1, Ui.argb(Ui.OR, 70), Ui.argb(Ui.OR, 0));
        lot.rect(cx, y0, cx + 2, y1, Ui.argb(Ui.OR, 255));
        lot.tri(cx + 1 - 6, y0, cx + 1, y0 + 6, cx + 1 + 6, y0, Ui.argb(Ui.OR, 255));
        lot.tri(cx + 1, y1 - 6, cx + 1 - 6, y1, cx + 1 + 6, y1, Ui.argb(Ui.OR, 255));
        lot.fin();
        g.pose().popPose();
        g.disableScissor();
    }

    /**
     * Carte d'objet (maquette) : fond de verre clair, lueur ronde de la couleur de la rareté derrière l'objet, nom, rareté
     * en petites capitales, trait de couleur en bas. gagne (0..1) : carte gagnée mise en avant ; eteinte (0..1) : les autres.
     */
    private void carte(GuiGraphics g, Packets.Card c, int x, int y, int w, int h, int itemPx, float gagne, float eteinte) {
        int col = couleur(c.rarity());
        boolean petite = h < 60;
        float itemCy = y + (petite ? 17 : h < 80 ? 24 : 38);
        Lot lot = new Lot(g);
        if (gagne > 0) {
            lot.lueur(x + w / 2f, y + h / 2f, w * 0.9f, h * 0.9f, Ui.argb(col, (int) (60 * gagne)));
            lot.rondContour(x - 1, y - 1, w + 2, h + 2, Ui.argb(col, (int) (220 * gagne)));
        }
        lot.rondVert(x, y, w, h, Ui.argb(0xFFFFFF, 12), Ui.argb(0xFFFFFF, 4));
        lot.rondContour(x, y, w, h, Ui.argb(0xFFFFFF, 18));
        lot.lueur(x + w / 2f, itemCy + itemPx * 0.12f, w * 0.36f, itemPx * 0.8f, Ui.argb(col, 120));
        lot.rect(x + 5, y + h - 2, x + w - 5, y + h, Ui.argb(col, 255));
        lot.fin();
        CardArt.item(g, c.stack(), x + w / 2f, itemCy, itemPx);
        // textes au-dessus des objets (rendus plus près de l'écran)
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        if (h < 80) {
            String nom = Ui.ellipsis(font, c.stack().getHoverName().getString(), CardArt.MINI, w - 8);
            Ui.centered(g, font, Ui.lit(nom, CardArt.MINI), x + w / 2f, y + h - (petite ? 21 : 26), Ui.argb(Ui.TEXT, 255));
        }
        String rn = CardArt.rarity(c.rarity()).displayName().getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, rn, CardArt.MINI_GRAS, x + w / 2f, y + h - (petite ? 12 : 15), Ui.argb(col, 255), 0.8f);
        if (c.count() > 1) Ui.right(g, font, Ui.lit("×" + c.count(), CardArt.MINI_GRAS), x + w - 4, y + 3, Ui.argb(0xFFFFFF, 210));
        if (eteinte > 0) {
            Lot voile = new Lot(g);
            voile.rond(x, y, w, h, Ui.argb(0x07080A, (int) (150 * eteinte)));
            voile.fin();
        }
        g.pose().popPose();
    }

    /** Grille du contenu possible (6 colonnes, molette) ; rend la carte survolée. */
    @Nullable
    private Packets.Card renderGrille(GuiGraphics g, int mx, int my) {
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.case.possible").getString(), CardArt.MINI_GRAS, grilleX, basY, Ui.argb(Ui.MUTED, 255), 1.4f);
        if (contents.isEmpty()) {
            Ui.spaced(g, font, Component.translatable("gui.heralizcrates.case.empty").getString(), CardArt.MINI, grilleX, grilleY + 4, Ui.argb(Ui.FAINT, 255), 0.6f);
            return null;
        }
        Packets.Card survolee = null;
        g.enableScissor(grilleX - 2, grilleY - 1, grilleX + grilleW + 2, grilleY + grilleH);
        for (int i = 0; i < contents.size(); i++) {
            int col = i % GRILLE_COLS, rangee = i / GRILLE_COLS;
            int x = grilleX + col * (celluleW + GRILLE_GAP), y = grilleY + rangee * (GRILLE_H + GRILLE_GAP) - grilleScroll;
            if (y + GRILLE_H < grilleY || y > grilleY + grilleH) continue;
            Packets.Card c = contents.get(i);
            carte(g, c, x, y, celluleW, GRILLE_H, 18, 0f, 0f);
            if (my >= grilleY && my < grilleY + grilleH && dans(mx, my, x, y, celluleW, GRILLE_H)) survolee = c;
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            // barre de défilement fine à droite de la grille
            int total = grilleH + max, barH = Math.max(14, grilleH * grilleH / total);
            int barY = grilleY + (grilleH - barH) * grilleScroll / max;
            Lot lot = new Lot(g);
            lot.rect(grilleX + grilleW + 4, grilleY, grilleX + grilleW + 5, grilleY + grilleH, Ui.argb(0xFFFFFF, 14));
            lot.rect(grilleX + grilleW + 3, barY, grilleX + grilleW + 6, barY + barH, Ui.argb(0xFFFFFF, 70));
            lot.fin();
        }
        return survolee;
    }

    /** Colonne « Chances » : une ligne par rareté présente dans la caisse. */
    private void renderChances(GuiGraphics g) {
        Ui.spaced(g, font, Component.translatable("gui.heralizcrates.case.chances").getString(), CardArt.MINI_GRAS, colX, basY, Ui.argb(Ui.MUTED, 255), 1.4f);
        int y = grilleY;
        Lot lot = new Lot(g);
        List<Integer> lignes = new ArrayList<>();
        for (int r = 0; r < RARETES.length; r++) {
            if (chances[r] <= 0) continue;
            int ly = y + lignes.size() * LIGNE_CHANCE;
            if (ly + LIGNE_CHANCE > boutonY - 6) break;
            lignes.add(r);
            lot.rond(colX, ly + 3, 5, 5, Ui.argb(RARETES[r], 255));
            lot.rect(colX, ly + LIGNE_CHANCE - 1, colX + colW, ly + LIGNE_CHANCE, Ui.argb(0xFFFFFF, 13));
        }
        lot.fin();
        for (int k = 0; k < lignes.size(); k++) {
            int r = lignes.get(k), ly = y + k * LIGNE_CHANCE;
            g.drawString(font, Ui.lit(CardArt.rarity(r).displayName().getString(), Ui.SMALL), colX + 10, ly + 1, Ui.argb(Ui.TEXT, 255), false);
            Ui.right(g, font, Ui.lit(Ui.pct(chances[r]), Ui.SMALL), colX + colW, ly + 1, Ui.argb(Ui.TEXT, 255));
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
            lot.rondVert(boutonX, boutonY, boutonW, BOUTON_H, Ui.argb(haut, 255), Ui.argb(bas, 255));
        } else {
            lot.rond(boutonX, boutonY, boutonW, BOUTON_H, Ui.argb(0xFFFFFF, 15));
            lot.rondContour(boutonX, boutonY, boutonW, BOUTON_H, Ui.argb(0xFFFFFF, 20));
        }
        lot.fin();
        String texte = Component.translatable(cle).getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, texte, Ui.BOLD, boutonX + boutonW / 2f, boutonY + 6, Ui.argb(dore ? OR_TEXTE : Ui.FAINT, 255), 1.2f);

        // aide (ou erreur du serveur)
        float ay = boutonY + BOUTON_H + 4;
        if (error != null && ticks - errorAt < 80) {
            String e = Ui.ellipsis(font, error.getString(), CardArt.MINI, boutonW + 20);
            Ui.centered(g, font, Ui.lit(e, CardArt.MINI), boutonX + boutonW / 2f, ay, Ui.argb(Ui.DANGER, 240));
            return;
        }
        String aide;
        if (contents.isEmpty()) aide = "gui.heralizcrates.case.empty";
        else if (state == State.SPIN) aide = "gui.heralizcrates.case.help.skip";
        else if (!free && keys < 1) aide = "gui.heralizcrates.case.help.keys";
        else aide = "gui.heralizcrates.case.help.open";
        Ui.centered(g, font, Ui.lit(Component.translatable(aide).getString(), CardArt.MINI), boutonX + boutonW / 2f, ay, Ui.argb(Ui.MUTED, 255));
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
        lot.lueur(width / 2f, gainY + 92, 130, 110, Ui.argb(col, (int) (45 * a)));
        lot.verre(gainX, gainY, gainW, gainH, (int) (235 * a));
        lot.fin();
        if (a < 0.15f) {
            g.pose().popPose();
            return;
        }
        int cx = width / 2;
        Ui.spacedCentered(g, font, Component.translatable("gui.heralizcrates.case.won").getString(), CardArt.MINI, cx, gainY + 16, Ui.argb(Ui.MUTED, 255), 1.4f);
        int cw = 92;
        carte(g, c, cx - cw / 2, gainY + 30, cw, cw, 40, 1f, 0f);
        String nom = c.stack().getHoverName().getString() + (c.count() > 1 ? "  ×" + c.count() : "");
        nom = Ui.ellipsis(font, nom, Ui.BOLD, (int) ((gainW - 24) / 1.1f));
        float nw = font.width(Ui.lit(nom, Ui.BOLD)) * 1.1f;
        Ui.gros(g, font, Ui.lit(nom, Ui.BOLD), cx - nw / 2f, gainY + 134, 1.1f, Ui.argb(Ui.TEXT, 255));

        // boutons : « Encore (1 clé) » doré si une clé reste, « Fermer » éteint
        boolean encore = encoreVisible();
        int fx = encore ? fermerX : encoreX, fw = encore ? gainBoutonW : gainW - 32;
        lot = new Lot(g);
        if (encore) {
            boolean s = dans(mx, my, encoreX, gainBoutonY, gainBoutonW, 20);
            lot.rondVert(encoreX, gainBoutonY, gainBoutonW, 20, Ui.argb(s ? Ui.mix(OR_HAUT, 0xFFFFFF, 0.15f) : OR_HAUT, 255),
                    Ui.argb(s ? Ui.mix(OR_BAS, 0xFFFFFF, 0.15f) : OR_BAS, 255));
        }
        boolean sf = dans(mx, my, fx, gainBoutonY, fw, 20);
        lot.rond(fx, gainBoutonY, fw, 20, Ui.argb(0xFFFFFF, sf ? 26 : 15));
        lot.rondContour(fx, gainBoutonY, fw, 20, Ui.argb(0xFFFFFF, sf ? 40 : 20));
        lot.fin();
        if (encore) {
            String t = Component.translatable(free ? "gui.heralizcrates.case.again" : "gui.heralizcrates.case.again_key").getString().toUpperCase(Locale.ROOT);
            Ui.spacedCentered(g, font, t, CardArt.MINI_GRAS, encoreX + gainBoutonW / 2f, gainBoutonY + 7, Ui.argb(OR_TEXTE, 255), 1.0f);
        }
        String f = Component.translatable("gui.heralizcrates.case.close").getString().toUpperCase(Locale.ROOT);
        Ui.spacedCentered(g, font, f, CardArt.MINI_GRAS, fx + fw / 2f, gainBoutonY + 7, Ui.argb(sf ? Ui.TEXT : Ui.MUTED, 255), 1.0f);
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
