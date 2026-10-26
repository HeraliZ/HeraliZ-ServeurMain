package fr.heraliz.tactique.client.soins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.api.item.IConsumable;
import fr.heralisz.tactical.api.item.ICustomItem;
import fr.heralisz.tactical.api.item.IThrowable;
import fr.heralisz.tactical.item.index.ConsumableIndex;
import fr.heralisz.tactical.item.index.ThrowableIndex;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heraliz.noyau.client.kit.Anim;
import fr.heraliz.noyau.client.kit.BarreProgression;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import fr.heraliz.noyau.client.kit.ToucheHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.IEventBus;
import org.joml.Matrix4f;

/**
 * Petite barre des soins et des grenades sous le réticule (refonte du 25/10 ; retours du propriétaire : « une petite
 * barre bien propre, un peu en italique », docs/HUD_REFONTE.md, maquettes {@code maquettes/hud_refonte/soins_*}). Code
 * neuf de HeraliZ : il remplace, à l'affichage seulement, l'ancien anneau du moteur repris de LR Tactical
 * ({@code fr.heralisz.tactical.client.overlay.UsingProgressOverlay}, licence GPL gardée, plus enregistré) ; il lit
 * seulement l'objet utilisé par le joueur et ses durées ({@code use_duration} des index).
 * <ul>
 *     <li>soin : barre fine penchée comme les cases de la barre DayZ ({@link BarreProgression}), verte, remplie en
 *     continu (fraction de tick), temps restant en petit à sa droite, rien d'autre (25/10, demande du propriétaire :
 *     ni nom de l'objet ni aide pour arrêter dessous) ; arrêté avant la fin : barre rouge qui se vide en
 *     {@value BarreProgression#INTERRUPTION_MS} ms ;</li>
 *     <li>grenade : barre blanche pendant qu'on la dégoupille, puis mèche rouge qui se vide jusqu'à l'explosion
 *     (« explose dans 2,1 s ») ; « Lancer doux (accroupi) » accroupi ;</li>
 *     <li>le réticule reste visible ; fondu d'entrée et de sortie ; rien ne reste affiché après la fin d'une action.</li>
 * </ul>
 * Rien ne change côté serveur (durées, effets, recharges, arrêt en rappuyant).
 */
public final class HudSoins {
    private HudSoins() {
    }

    /** Haut de la barre sous le centre du réticule ; écart entre la barre et le temps restant. */
    static final float BARRE_DY = 11f, TEMPS_ECART = 4f;
    /** Lignes sous la barre (milieu des capitales, sous le centre du réticule), largeur maximale d'une ligne. */
    static final float NOM_CY = 19.5f, AIDE_CY = 28.5f, DOUX_CY = 38f, LIGNE_MAX = 140f, ECART_AIDE = 3f;
    /** Pastille « lancer doux » : hauteur, marge, espace entre les capitales. */
    static final float PUCE_H = 9f, PUCE_PAD = 4f, PUCE_ESPACE = 0.6f;
    /** Opacité de l'aide pour arrêter (discrète). */
    static final float AIDE_ALPHA = 0.8f;
    /** Un soin arrêté plus de ce nombre de ticks avant la fin est « interrompu » (sinon : fini). */
    private static final int MARGE_FIN = 3;

    private enum Genre { SOIN, GRENADE, AUTRE }

    /** Lignes d'aide : aucune, touche pour arrêter, relâcher pour arrêter, interrompu, mèche de la grenade. */
    private static final int AUCUNE = 0, ARRETER = 1, RELACHER = 2, INTERROMPU = 3, MECHE = 4;

    private static final BarreProgression BARRE = new BarreProgression();
    private static final Anim.Fondu FONDU = new Anim.Fondu();
    private static final TexteHud NOM = new TexteHud(PoliceHud.MINI_GRAS);
    private static final TexteHud TEMPS = new TexteHud(PoliceHud.MINI);
    private static final TexteHud AIDE = new TexteHud(PoliceHud.MINI);
    private static final TexteHud DOUX = new TexteHud(PoliceHud.MINI_GRAS, PUCE_ESPACE);
    private static ToucheHud utiliser;

    /** Objet utilisé (référence) et ce qu'on en a lu une fois. */
    private static ItemStack objet = ItemStack.EMPTY;
    private static Genre genre = Genre.AUTRE;
    private static int duree = 1, preparation, vie;
    private static boolean bascule, cuisable, actifAvant, temps, doux;
    private static int derniersTicks;
    /** Clés des textes en cache. */
    private static String langue;
    private static int ligne = -1, ligneTexte = -1, dixiemes = -1, dixiemesMeche = -1;

    /** Branché par la partie client du mod porteur (ClientTactique). */
    public static void brancher(IEventBus busMod) {
        busMod.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("soins", HudSoins::rendre));
    }

    private static void rendre(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            FONDU.couper();
            BARRE.arreter();
            actifAvant = false;
            return;
        }
        ItemStack st = p.getUseItem();
        boolean utilise = p.isUsingItem() && st.getItem() instanceof ICustomItem;
        if (utilise && st != objet) prendre(st);
        if (utilise && genre == Genre.AUTRE && duree <= 1) utilise = false;
        float restant = 0f, meche = 0f;
        if (utilise) {
            int ticks = p.getTicksUsingItem();
            derniersTicks = ticks;
            switch (genre) {
                case SOIN -> {
                    BARRE.normal(Math.min(1f, (ticks + pt) / duree), Palette.OK);
                    restant = Math.max(0f, (duree - ticks - pt) / 20f);
                    ligne = AUCUNE;   // seulement la barre et le temps (25/10)
                }
                case GRENADE -> {
                    if (cuisable && vie > 0 && ticks >= preparation) {
                        float reste = Math.max(0f, vie - (ticks - preparation) - pt);
                        BARRE.normal(reste / vie, Palette.DANGER);
                        meche = reste / 20f;
                        ligne = MECHE;
                    } else {
                        BARRE.normal(Math.min(1f, (ticks + pt) / duree), 0xFFFFFF);
                        ligne = AUCUNE;
                    }
                }
                default -> {
                    BARRE.normal(Math.min(1f, (ticks + pt) / duree), 0xFFFFFF);
                    ligne = AUCUNE;
                }
            }
        } else if (actifAvant && genre == Genre.SOIN && derniersTicks < duree - MARGE_FIN) {
            BARRE.interrompre();
            ligne = AUCUNE;   // la barre rouge qui se vide suffit (25/10)
        }
        actifAvant = utilise;
        float a = FONDU.maj(utilise || BARRE.enFin());
        if (!FONDU.visible()) {
            BARRE.arreter();
            return;
        }
        if (a <= 0f || mc.options.hideGui || p.isSpectator()) return;
        textes(mc, utilise, restant, meche);
        doux = utilise && genre == Genre.GRENADE && p.isCrouching();

        float cx = w / 2f, cy = h / 2f;
        float largeurAide = ligneTexte == ARRETER ? utiliser.largeur() + ECART_AIDE + AIDE.largeur() : AIDE.largeur();
        float xAide = cx - largeurAide / 2f;
        float pw = DOUX.largeur() + 2f * PUCE_PAD, cyDoux = cy + DOUX_CY;
        float aAide = a * AIDE_ALPHA;

        // formes, un seul vidage
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        BARRE.ajouter(vc, m, cx, cy + BARRE_DY, a);
        if (ligneTexte == ARRETER) utiliser.fond(vc, m, xAide, cy + AIDE_CY, 0f, 0, aAide);
        if (doux) {
            Formes.rect(vc, m, cx - pw / 2f, cyDoux - PUCE_H / 2f, cx + pw / 2f, cyDoux + PUCE_H / 2f, Palette.argb(Palette.ATTENUE, 40f / 255f * a));
            Formes.bord(vc, m, cx - pw / 2f, cyDoux - PUCE_H / 2f, pw, PUCE_H, Formes.pixel(), Palette.argb(Palette.ATTENUE, 110f / 255f * a));
        }
        g.flush();

        // textes, un seul vidage
        if (temps) {
            TEMPS.ajouter(g, cx + BarreProgression.LARGEUR / 2f + TEMPS_ECART, cy + BARRE_DY + BarreProgression.HAUTEUR / 2f,
                    Palette.argb(Palette.ATTENUE, a), TexteHud.GAUCHE, true);
        }
        if (genre == Genre.GRENADE) NOM.ajouter(g, cx, cy + NOM_CY, Palette.argb(Palette.TEXTE, a), TexteHud.CENTRE, true);
        if (ligneTexte == ARRETER) {
            utiliser.texte(g, xAide, cy + AIDE_CY, aAide);
            AIDE.ajouter(g, xAide + utiliser.largeur() + ECART_AIDE, cy + AIDE_CY, Palette.argb(Palette.ATTENUE, aAide), TexteHud.GAUCHE, true);
        } else if (ligneTexte != AUCUNE) {
            int c = ligneTexte == INTERROMPU ? Palette.clair(Palette.DANGER, 0.3f)
                    : ligneTexte == MECHE ? Palette.clair(Palette.DANGER, 0.45f) : Palette.ATTENUE;
            AIDE.ajouter(g, cx, cy + AIDE_CY, Palette.argb(c, ligneTexte == RELACHER ? aAide : a), TexteHud.CENTRE, true);
        }
        if (doux) DOUX.ajouter(g, cx - pw / 2f + PUCE_PAD, cyDoux, Palette.argb(0xD8DCE2, a), TexteHud.GAUCHE, false);
        g.flush();
    }

    /** Nouvel objet en main : genre, durées et nom lus une fois. */
    private static void prendre(ItemStack st) {
        objet = st;
        ICustomItem item = (ICustomItem) st.getItem();
        duree = Math.max(1, item.getMaxUsingTick(st));
        bascule = false;
        cuisable = false;
        vie = 0;
        preparation = 0;
        if (st.getItem() instanceof IConsumable) {
            genre = Genre.SOIN;
            ConsumableIndex index = HeraliszTacticalAPI.getConsumableIndex(st).orElse(null);
            bascule = index != null && index.getData().isToggleUse();
        } else if (st.getItem() instanceof IThrowable lancer) {
            genre = Genre.GRENADE;
            ThrowableIndex<?, ?> index = lancer.getThrowableIndex(st).orElse(null);
            ThrowableData data = index == null ? null : index.getData();
            if (data != null) {
                cuisable = data.isCookable();
                vie = data.getEntityData().getLifeTime();
                preparation = data.getPrepareTime();
            }
        } else {
            genre = Genre.AUTRE;
            if (item.getMaxUsingTick(st) <= 0) duree = 1;
        }
        NOM.texte(genre == Genre.AUTRE ? "" : st.getHoverName().getString());
        dixiemes = -1;
        dixiemesMeche = -1;
    }

    /** Textes recalculés seulement si la langue, la touche, la ligne d'aide ou le dixième de seconde affiché changent. */
    private static void textes(Minecraft mc, boolean utilise, float restant, float meche) {
        if (utiliser == null) utiliser = new ToucheHud(mc.options.keyUse);
        utiliser.maj();
        String l = mc.getLanguageManager().getSelected();
        boolean nouvelleLangue = !l.equals(langue);
        langue = l;
        Language lang = Language.getInstance();
        if (nouvelleLangue) {
            DOUX.texte(lang.getOrDefault("overlay.heraliz_tactique.lancer_doux"));
            dixiemes = -1;
        }
        if (ligne != ligneTexte || nouvelleLangue) {
            ligneTexte = ligne;
            dixiemesMeche = -1;
            switch (ligne) {
                case ARRETER -> AIDE.texte(lang.getOrDefault("overlay.heraliz_tactique.soin_arreter"));
                case RELACHER -> AIDE.texte(lang.getOrDefault("overlay.heraliz_tactique.soin_relacher"));
                case INTERROMPU -> AIDE.texte(lang.getOrDefault("overlay.heraliz_tactique.soin_interrompu"));
                default -> AIDE.texte("");
            }
        }
        if (utilise && ligne == MECHE) {
            int d = (int) Math.ceil(meche * 10f);
            if (d != dixiemesMeche) {
                dixiemesMeche = d;
                AIDE.texte(I18n.get("overlay.heraliz_tactique.explose", dixieme(d, l)));
            }
        }
        // temps restant d'un soin, en petit à droite de la barre, au dixième de seconde
        temps = utilise && genre == Genre.SOIN && restant > 0.05f;
        if (temps) {
            int d = (int) Math.ceil(restant * 10f);
            if (d != dixiemes) {
                dixiemes = d;
                TEMPS.texte(I18n.get("overlay.heraliz_tactique.secondes", dixieme(d, l)));
            }
        }
    }

    /** « 4,2 » (virgule en français et en russe). */
    private static String dixieme(int d, String langue) {
        boolean virgule = langue.startsWith("fr") || langue.startsWith("ru");
        return d / 10 + (virgule ? "," : ".") + d % 10;
    }
}
