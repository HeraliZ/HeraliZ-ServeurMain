package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Anim;
import fr.heraliz.interfaces.kit.Echelle;
import fr.heraliz.interfaces.kit.Fond;
import fr.heraliz.interfaces.kit.HzButton;
import fr.heraliz.interfaces.kit.HzIcon;
import fr.heraliz.interfaces.kit.Icons;
import fr.heraliz.interfaces.kit.Sons;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran Graphismes (25/10, demande du proprietaire) au style des clans : en haut, les trois profils de {@link Qualite}
 * (Performance, Equilibre, Qualite) en cartes, un clic sur une carte pose tous ses reglages d'un coup ; dessous, les
 * reglages en cours, lus en direct dans les options de Minecraft (noms et valeurs traduits par Minecraft). Ouvert a la
 * place de l'ecran Video de Minecraft ou d'Embeddium quand on vient des Options ({@link InterfaceClient#video}), et par la
 * tuile « Qualite graphique » du menu Echap ; « Reglages detailles » ouvre l'ecran Video d'origine. Les mipmaps du profil
 * choisi sont posees en quittant l'ecran (un seul rechargement des textures). En panne : l'ecran d'origine (ou le parent)
 * a la place, et pour le reste de la session.
 */
public class EcranGraphismes extends Screen {
    private static final int ENTETE = 42, CARTE_H = 76, PIED = 18, LIGNE = 15, PAR_COLONNE = 5;
    private final Screen parent;
    /** Ecran Video d'origine (Minecraft ou Embeddium), null : ouvert depuis le menu Echap (cree a la demande). */
    @Nullable
    private final Screen detaille;
    private int px, py, pw, ph, resumeY;
    private long ouvert = -1;

    public EcranGraphismes(Screen parent, @Nullable Screen detaille) {
        super(Component.translatable("gui.heraliz_interface.graphismes.titre"));
        this.parent = parent;
        this.detaille = detaille;
    }

    @Override
    protected void init() {
        if (ouvert < 0) ouvert = Util.getMillis();
        if (InterfaceConfig.oui(InterfaceConfig.ECHELLE_AUTO, true)) Echelle.forcer(this);
        pw = Math.min(540, width - 32);
        ph = Math.min(312, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int accent = Ui.accent();
        addRenderableWidget(new HzIcon(px + pw - 24, py + 12, 12, Icons.CROIX, CommonComponents.GUI_DONE, this::onClose));
        Qualite.Profil[] profils = Qualite.Profil.values();
        int n = profils.length;
        int cw = (pw - 2 * Ui.MARGE - (n - 1) * Ui.ESPACE) / n;
        int cy = py + ENTETE + 12;
        for (int i = 0; i < n; i++) {
            addRenderableWidget(new Carte(px + Ui.MARGE + i * (cw + Ui.ESPACE), cy, cw, CARTE_H, profils[i], accent));
        }
        resumeY = cy + CARTE_H + 14;
        int by = py + ph - 12 - PIED;
        Component detail = Component.translatable("gui.heraliz_interface.graphismes.detail");
        int bw = Math.max(130, Math.round(Ui.spacedWidth(font, detail.getString(), Ui.BOLD, 1.2f)) + 28);
        addRenderableWidget(new HzButton(px + Ui.MARGE, by, bw, PIED, detail, accent, this::ouvrirDetaille));
        int fw = Math.max(100, Math.round(Ui.spacedWidth(font, CommonComponents.GUI_DONE.getString(), Ui.BOLD, 1.2f)) + 28);
        addRenderableWidget(new HzButton(px + pw - Ui.MARGE - fw, by, fw, PIED, CommonComponents.GUI_DONE, accent, this::onClose)
                .look(HzButton.Look.PRIMARY));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            if (minecraft.level == null) renderBackground(g);      // depuis l'ecran titre : fond de Minecraft sous le voile
            float t = Anim.sortie(Anim.progression(ouvert, 0, 220));
            Fond.dessiner(g, width, height, parent instanceof EcranPause ? 1f : t);
            Fond.lueur(g, width / 2f, py + ph * 0.55f, pw * 0.6f, ph * 0.62f, 0x000000, Math.round(90 * t));
            Ui.glass(g, px, py, pw, ph, Math.round(Ui.VERRE_ALPHA * t));
            Ui.hairline(g, px + 24, px + pw - 24, py, Ui.accent(), Math.round(160 * t), true, true);
            int a = Math.round(255 * t);
            Ui.spaced(g, font, title.getString().toUpperCase(Locale.ROOT), Ui.BOLD, px + Ui.MARGE, py + 11, Ui.argb(Ui.TEXT, a), 1.6f);
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.heraliz_interface.graphismes.sous_titre"), px + Ui.MARGE, py + 25,
                    Ui.argb(Ui.MUTED, a), false);
            Ui.hairline(g, px + 8, px + pw - 8, py + ENTETE, 0xFFFFFF, 40, true, true);
            resume(g, px + Ui.MARGE, resumeY, pw - 2 * Ui.MARGE, a);
            super.render(g, mouseX, mouseY, partialTick);
        } catch (Throwable ex) {
            InterfaceClient.graphismesEnPanne(ex);
            minecraft.setScreen(detaille != null ? detaille : parent);
        }
    }

    /** Reglages en cours, sur deux colonnes : nom (Minecraft) a gauche, valeur a droite. */
    private void resume(GuiGraphics g, int x, int y, int w, int a) {
        Ui.section(g, font, Component.translatable("gui.heraliz_interface.graphismes.regle").getString(), x, y, w, a);
        List<Component[]> l = lignes(minecraft.options);
        int cw = (w - 16) / 2;
        for (int i = 0; i < l.size(); i++) {
            int lx = x + (i / PAR_COLONNE) * (cw + 16), ly = y + 16 + (i % PAR_COLONNE) * LIGNE;
            if (i % 2 == 0) Ui.soft(g, lx, ly - 3, cw, LIGNE - 1, Ui.argb(0xFFFFFF, Math.round(5 * a / 255f)));
            Component valeur = Ui.lit(l.get(i)[1].getString(), Ui.BOLD);
            int vw = font.width(valeur);
            String nom = Ui.ellipsis(font, l.get(i)[0].getString(), Ui.FONT, cw - vw - 18);
            g.drawString(font, Ui.lit(nom, Ui.FONT), lx + 6, ly, Ui.argb(Ui.mix(Ui.TEXT, Ui.MUTED, 0.45f), a), false);
            Ui.right(g, font, valeur, lx + cw - 6, ly, Ui.argb(Ui.TEXT, a));
        }
        if (Qualite.rechargementEnAttente()) {
            g.drawString(font, Ui.tr(Ui.SMALL, "gui.heraliz_interface.graphismes.rechargement"), x, y + 16 + PAR_COLONNE * LIGNE + 2,
                    Ui.argb(Ui.FAINT, a), false);
        }
    }

    /** Nom et valeur de chaque reglage pose par les profils (et de la synchronisation verticale, des images par seconde). */
    private static List<Component[]> lignes(Options o) {
        List<Component[]> l = new ArrayList<>(10);
        l.add(ligne("options.graphics", o.graphicsMode().get().getCaption()));
        l.add(ligne("options.renderDistance", Component.translatable("options.chunks", o.renderDistance().get())));
        l.add(ligne("options.particles", o.particles().get().getCaption()));
        l.add(ligne("options.renderClouds", o.cloudStatus().get().getCaption()));
        l.add(ligne("options.biomeBlendRadius", Component.translatable("options.biomeBlendRadius." + (o.biomeBlendRadius().get() * 2 + 1))));
        l.add(ligne("options.entityShadows", CommonComponents.optionStatus(o.entityShadows().get())));
        l.add(ligne("options.ao", CommonComponents.optionStatus(o.ambientOcclusion().get())));
        l.add(ligne("options.entityDistanceScaling", Component.literal(Math.round(o.entityDistanceScaling().get() * 100) + " %")));
        l.add(ligne("options.vsync", CommonComponents.optionStatus(o.enableVsync().get())));
        int ips = o.framerateLimit().get();
        l.add(ligne("options.framerateLimit", ips >= 260 ? Component.translatable("options.framerateLimit.max")
                : Component.translatable("options.framerate", ips)));
        return l;
    }

    private static Component[] ligne(String cle, Component valeur) {
        return new Component[]{Component.translatable(cle), valeur};
    }

    private void ouvrirDetaille() {
        minecraft.setScreen(detaille != null ? detaille : ecranVideo(this));
    }

    /** Ecran Video d'Embeddium s'il est la (le meme que depuis les Options), sinon celui de Minecraft. */
    static Screen ecranVideo(Screen parent) {
        try {
            Class<?> c = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI");
            return (Screen) c.getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            return new VideoSettingsScreen(parent, Minecraft.getInstance().options);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        Qualite.appliquerMipmaps();
        super.removed();
    }

    // ------------------------------------------------------------------ carte d'un profil
    /** Carte d'un profil : barres de niveau et nom, description, « Actif » ou « Choisir », etiquette « Conseille ». */
    private final class Carte extends AbstractButton {
        private final Qualite.Profil profil;
        private final int accent;
        private final String nom, actif, choisir;
        @Nullable
        private final String conseille;
        private final List<Component> description = new ArrayList<>();
        private final Anim.Horloge horloge = new Anim.Horloge();
        private float survol, choisi;
        private boolean pret;

        Carte(int x, int y, int w, int h, Qualite.Profil profil, int accent) {
            super(x, y, w, h, Component.translatable("gui.heraliz_interface.pause.graphismes." + profil.cle));
            this.profil = profil;
            this.accent = accent & 0xFFFFFF;
            this.nom = getMessage().getString().toUpperCase(Locale.ROOT);
            this.actif = Ui.t("gui.heraliz_interface.graphismes.actif").toUpperCase(Locale.ROOT);
            this.choisir = Ui.t("gui.heraliz_interface.graphismes.choisir").toUpperCase(Locale.ROOT);
            this.conseille = profil == Qualite.Profil.PERFORMANCE
                    ? Ui.t("gui.heraliz_interface.graphismes.conseille").toUpperCase(Locale.ROOT) : null;
            List<String> l = Ui.lignes(font, Ui.t("gui.heraliz_interface.graphismes.desc." + profil.cle), Ui.SMALL, w - 20);
            for (int i = 0; i < Math.min(3, l.size()); i++) description.add(Ui.lit(l.get(i), Ui.SMALL));
        }

        @Override
        public void onPress() {
            if (Qualite.actuel() != profil) Qualite.appliquer(profil);
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            Sons.jouer(Sons.CLIC, 0.6f);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean estActif = Qualite.actuel() == profil;
            float dt = horloge.dt();
            survol = Anim.vers(survol, isHoveredOrFocused() ? 1f : 0f, dt, 16f);
            choisi = pret ? Anim.vers(choisi, estActif ? 1f : 0f, dt, 14f) : (estActif ? 1f : 0f);
            pret = true;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            Font f = Minecraft.getInstance().font;
            float vif = Math.max(choisi, survol);
            Ui.soft(g, x, y, w, h, Ui.argb(Ui.mix(0xFFFFFF, accent, choisi), Math.round(6 + 8 * survol + 26 * choisi)));
            Ui.softOutline(g, x + 1, y + 1, w - 2, h - 2, Ui.argb(Ui.mix(0xFFFFFF, accent, vif), Math.round(36 + 90 * survol + 150 * choisi)));
            if (choisi > 0.01f) g.fill(x + 1, y + 10, x + 3, y + 24, Ui.argb(accent, Math.round(230 * choisi)));
            // niveau du profil : 1, 2 ou 3 barres qui montent (comme la tuile du menu Echap)
            int niveau = profil.ordinal() + 1, base = y + 20;
            for (int i = 0; i < 3; i++) {
                int bh = 4 + 2 * i;
                int couleur = i < niveau ? Ui.argb(Ui.mix(Ui.TEXT, accent, choisi), 235) : Ui.argb(0x4A4F59, 150);
                g.fill(x + 10 + i * 5, base - bh, x + 13 + i * 5, base, couleur);
            }
            Ui.spaced(g, f, nom, Ui.BOLD, x + 30, y + 11, Ui.argb(0xFFFFFF, 255), 1.0f);
            int ly = y + 29;
            int texte = Ui.argb(Ui.mix(Ui.MUTED, Ui.TEXT, 0.25f * survol + 0.4f * choisi), 255);
            for (Component c : description) {
                g.drawString(f, c, x + 10, ly, texte, false);
                ly += 9;
            }
            int bas = y + h - 14;
            if (estActif) {
                Ui.point(g, x + 10, bas + 1, Ui.OK, 255);
                Ui.spaced(g, f, actif, Ui.SMALL, x + 18, bas, Ui.argb(Ui.OK, 255), 0.8f);
            } else {
                Ui.spaced(g, f, choisir, Ui.SMALL, x + 10, bas, Ui.argb(Ui.mix(Ui.FAINT, Ui.TEXT, survol), 255), 0.8f);
            }
            if (conseille != null) {
                Ui.chip(g, f, conseille, x + w - 10 - Ui.chipWidth(f, conseille), bas - 3, Ui.OK);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, getMessage());
            if (active) {
                out.add(NarratedElementType.USAGE, isFocused() ? Component.translatable("narration.button.usage.focused")
                        : Component.translatable("narration.button.usage.hovered"));
            }
        }
    }
}
