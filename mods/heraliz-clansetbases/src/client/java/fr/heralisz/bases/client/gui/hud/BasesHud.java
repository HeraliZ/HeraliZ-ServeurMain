package fr.heralisz.bases.client.gui.hud;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.client.ClientBases;
import fr.heralisz.bases.client.gui.BaseScreen;
import fr.heralisz.bases.client.gui.PillerScreen;
import fr.heralisz.bases.client.gui.PlanBunker;
import fr.heralisz.bases.client.gui.kit.Icons;
import fr.heralisz.bases.client.gui.kit.Sons;
import fr.heralisz.bases.client.gui.kit.Ui;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.network.Packets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * HUD des bases (BASES_SPEC §6), a l'echelle du joueur :
 * <ul>
 * <li>rappel en haut a gauche dans un bunker : « BASE [TAG] · Salle 3 » (couleur du clan) ; en pillage, liseré rouge et
 * seconde ligne « 1 VIE · FIN A 23 H · 52 MIN » ;</li>
 * <li>compte a rebours de teleportation AU CENTRE de l'ecran (le reticule vanilla est masque) : anneau fin qui se remplit
 * dans le sens des aiguilles d'une montre autour d'une icone (entrer, sortir, pillage), « ENTREE DANS LA BASE · 7 s »
 * et « Ne bougez pas » dessous, un tic par seconde ; annulation montree 2,5 s : anneau rouge, croix, petite secousse,
 * « TELEPORTATION ANNULEE » et la raison (« Vous avez bouge », « Vous avez ete touche », « Vous etes en combat ») ;</li>
 * <li>toasts en haut a droite (verre, liseré colore, icone, titre en capitales espacees, texte sur deux lignes au plus) :
 * base prete, salle debloquee, base attaquee (7 s, pulsation), pillage termine, fin proche, info, erreur ;</li>
 * <li>refus discrets (casser, ouvrir... dans la base) : une ligne au-dessus de la barre d'action, 1,6 s.</li>
 * </ul>
 * Tous les textes sont prepares a la reception (ou une fois par seconde pour les comptes a rebours) ; rien n'est dessine
 * avec F1 ni derriere nos ecrans (qui dessinent eux-memes les toasts par {@link #toasts}).
 */
@Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT)
public final class BasesHud {
    private BasesHud() {
    }

    private static final int TOAST_W = 160, PILE_MAX = 3;
    private static final long FONDU_IN = 250, FONDU_OUT = 500;

    // =================================================================== toasts
    private record Toast(long depuis, long duree, int accent, int icone, boolean pulse, String titre, Component ligne1,
                         @Nullable Component ligne2) {
        float alpha(long now) {
            long age = now - depuis;
            if (age < FONDU_IN) return age / (float) FONDU_IN;
            long reste = depuis + duree - now;
            if (reste < FONDU_OUT) return Math.max(0, reste / (float) FONDU_OUT);
            return 1;
        }

        boolean fini(long now) {
            return now >= depuis + duree;
        }

        int hauteur() {
            return ligne2 != null ? 40 : 30;
        }

        Toast relance(long now) {
            return new Toast(now, duree, accent, icone, pulse, titre, ligne1, ligne2);
        }
    }

    private static final Deque<Toast> TOASTS = new ArrayDeque<>();
    private static final List<Toast> EN_ATTENTE = new ArrayList<>();

    // ---- refus discret
    @Nullable
    private static Component refus;
    private static long refusLe;

    // ---- teleportation
    private static Packets.TeleportS2C.Genre tpGenre = Packets.TeleportS2C.Genre.ENTREE;
    private static long tpAnnuleeLe;
    private static String tpAnnuleeRaisonCle = "";
    private static Component tpAnnuleeRaison = Component.empty();
    private static int tpSeconde = -1;
    private static String tpTitre = "", tpAnnuleeTitre = "";
    private static Component tpSous = Component.empty(), tpPoint = Component.empty(), tpSecondes = Component.empty();
    /** Duree de l'annulation a l'ecran (ms). */
    private static final long ANNULEE_MS = 2500;

    // ---- rappel de base
    private static int vHud = -1;
    private static long hudSeconde;
    private static String hudMode = "", hudTag = "", hudLigne2 = "";
    private static Component hudZone = Component.empty();
    private static int hudTagLargeur, hudModeLargeur;

    /** Empile un toast (3 visibles au plus ; les suivants attendent une place). REFUS = ligne discrete. */
    public static void notice(Packets.NoticeS2C n) {
        long now = Util.getMillis();
        Component texte = Component.translatable(n.cle(), n.args().toArray());
        if (n.genre() == Packets.NoticeS2C.Genre.REFUS) {
            refus = Ui.in(texte, Ui.SMALL);
            refusLe = now;
            return;
        }
        // l'erreur qui double une annulation de teleportation deja affichee sous le reticule est ignoree
        if (n.genre() == Packets.NoticeS2C.Genre.ERREUR && now - tpAnnuleeLe < 1500 && n.cle().equals(tpAnnuleeRaisonCle)) return;
        int accent, icone;
        long duree = 4500;
        boolean pulse = false;
        switch (n.genre()) {
            case BASE_CREEE -> {
                accent = Ui.OK;
                icone = Icons.BUNKER;
            }
            case SALLE_DEBLOQUEE -> {
                accent = Ui.accent();
                icone = Icons.CADENAS_OUVERT;
            }
            case ATTAQUEE -> {
                accent = Ui.DANGER;
                icone = Icons.ALERTE;
                duree = 7000;
                pulse = true;
            }
            case PILLAGE_TERMINE -> {
                accent = Ui.OR;
                icone = Icons.BOUCLIER;
            }
            case PILLAGE_FIN_PROCHE -> {
                accent = Ui.AMBRE;
                icone = Icons.HORLOGE;
                duree = 6000;
            }
            case ERREUR -> {
                accent = Ui.DANGER;
                icone = Icons.CROIX;
                duree = 3500;
            }
            default -> {
                accent = Ui.accent();
                icone = Icons.INFO;
            }
        }
        String titre = Ui.t("gui.heralisz_bases.toast." + n.genre().name().toLowerCase(Locale.ROOT));
        String brut = texte.getString();
        // salle debloquee : le titre le dit deja, le texte est le nom de la salle (args = nom fr, nom en)
        if (n.genre() == Packets.NoticeS2C.Genre.SALLE_DEBLOQUEE && n.args().size() >= 2) brut = Ui.french() ? n.args().get(0) : n.args().get(1);
        String[] l = couper(Minecraft.getInstance().font, brut, TOAST_W - 8 - 27);
        Toast t = new Toast(now, duree, accent, icone, pulse, titre, Ui.lit(l[0], Ui.FONT), l[1] == null ? null : Ui.lit(l[1], Ui.FONT));
        if (TOASTS.size() < PILE_MAX) TOASTS.addLast(t);
        else EN_ATTENTE.add(t);
    }

    /** Coupe le texte au dernier espace qui tient : deux lignes au plus, la seconde abregee si besoin. */
    private static String[] couper(Font font, String texte, int largeur) {
        List<String> l = Ui.lignes(font, texte, Ui.FONT, largeur);
        if (l.isEmpty()) return new String[]{"", null};
        if (l.size() == 1) return new String[]{l.get(0), null};
        String reste = String.join(" ", l.subList(1, l.size()));
        return new String[]{l.get(0), Ui.ellipsis(font, reste, Ui.FONT, largeur)};
    }

    /** Teleportation : DEBUT (le compte a rebours se dessine depuis ClientBases), ANNULEE (2,5 s en rouge), FAITE. */
    public static void teleport(Packets.TeleportS2C t) {
        tpGenre = t.genre();
        String g = t.genre().name().toLowerCase(Locale.ROOT);
        tpTitre = Ui.t("gui.heralisz_bases.tp2." + g);
        tpSous = Ui.tr(Ui.FONT, "gui.heralisz_bases.tp2.immobile");
        tpPoint = Ui.lit("  \u00b7  ", Ui.FONT);
        tpSeconde = -1;
        if (t.etat() == Packets.TeleportS2C.Etat.ANNULEE) {
            tpAnnuleeLe = Util.getMillis();
            tpAnnuleeRaisonCle = t.raison();
            tpAnnuleeTitre = Ui.t("gui.heralisz_bases.tp2.annulee");
            tpAnnuleeRaison = t.raison().isEmpty() ? Component.empty() : Ui.tr(Ui.FONT, raisonCourte(t.raison()));
        } else {
            tpAnnuleeLe = 0;
        }
    }

    /** Raison courte d'une annulation (« Vous avez bouge ») : gui.heralisz_bases.tp2.raison.&lt;fin de la cle&gt;, sinon la cle. */
    private static String raisonCourte(String cle) {
        String court = "gui.heralisz_bases.tp2.raison." + cle.substring(cle.lastIndexOf('.') + 1);
        return net.minecraft.client.resources.language.I18n.exists(court) ? court : cle;
    }

    /** Vrai pendant un compte a rebours ou l'affichage de son annulation (le reticule vanilla est alors masque). */
    private static boolean compteVisible() {
        return ClientBases.teleport() != null || (tpAnnuleeLe > 0 && Util.getMillis() - tpAnnuleeLe < ANNULEE_MS);
    }

    /** Deconnexion : tout est oublie. */
    public static void vider() {
        TOASTS.clear();
        EN_ATTENTE.clear();
        refus = null;
        tpAnnuleeLe = 0;
        vHud = -1;
    }

    private static boolean ecranANous(@Nullable net.minecraft.client.gui.screens.Screen s) {
        return s instanceof BaseScreen || s instanceof PillerScreen;
    }

    /**
     * Rien du HUD vanilla (reticule, barre d'objets, coeurs, chat...) ni la main a travers le verre translucide de nos
     * ecrans (comme les clans) : le banc montrait le chat et la barre d'objets sous le panneau.
     */
    @SubscribeEvent
    public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre e) {
        if (ecranANous(Minecraft.getInstance().screen)) {
            e.setCanceled(true);
            return;
        }
        // compte a rebours au centre : l'anneau remplace le reticule
        if (e.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id()) && compteVisible()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHand(net.minecraftforge.client.event.RenderHandEvent e) {
        if (ecranANous(Minecraft.getInstance().screen)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ecranANous(mc.screen)) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        Font font = mc.font;
        rappel(g, font);
        compteARebours(g, font, w, h);
        refus(g, font, w, h);
        toasts(g, font, w);
    }

    // =================================================================== rappel « BASE [TAG] · Salle 3 »
    private static void preparerRappel(Packets.HudS2C hud) {
        Font font = Minecraft.getInstance().font;
        boolean pillage = hud.mode() == Packets.HudS2C.Mode.PILLAGE;
        hudMode = Ui.t(pillage ? "gui.heralisz_bases.hud.pillage" : "gui.heralisz_bases.hud.base");
        hudTag = "[" + hud.tag() + "]";
        String zone = PlanBunker.nomZone(ClientBases.plan(), hud.zone());
        hudZone = zone.isEmpty() ? Component.empty() : Ui.lit("·  " + zone, Ui.FONT);
        hudModeLargeur = (int) Ui.spacedWidth(font, hudMode, Ui.BOLD, 1.2f);
        hudTagLargeur = (int) Ui.spacedWidth(font, hudTag, Ui.BOLD, 0.6f);
        hudLigne2 = "";
        if (pillage) {
            FenetreVue f = ClientBases.fenetre();
            int finH;
            if (f != null) finH = f.finH();
            else finH = hud.finMs() > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(hud.finMs()), ZoneId.systemDefault()).getHour() : 23;
            long reste = hud.finMs() > 0 ? hud.finMs() - System.currentTimeMillis() : -1;
            // fenetre forcee par un admin : pas d'heure de fin (finMs = 0), on n'affiche pas « fin a 23 h »
            hudLigne2 = hud.finMs() <= 0 ? Ui.t("gui.heralisz_bases.hud.pillage_vie")
                    : reste > 0 ? Ui.t("gui.heralisz_bases.hud.pillage_ligne_reste", finH, Ui.duree(reste))
                    : Ui.t("gui.heralisz_bases.hud.pillage_ligne", finH);
        }
    }

    private static void rappel(GuiGraphics g, Font font) {
        Packets.HudS2C hud = ClientBases.hud();
        if (hud.mode() == Packets.HudS2C.Mode.AUCUN) return;
        long s = System.currentTimeMillis() / 1000;
        if (ClientBases.versionHud() != vHud || (hud.mode() == Packets.HudS2C.Mode.PILLAGE && s != hudSeconde)) {
            vHud = ClientBases.versionHud();
            hudSeconde = s;
            preparerRappel(hud);
        }
        boolean pillage = hud.mode() == Packets.HudS2C.Mode.PILLAGE;
        int couleur = (hud.couleur() & 0xFFFFFF) == 0 ? Ui.ACCENT_NEUTRE : hud.couleur() & 0xFFFFFF;
        int x = 6, y = 6;
        int largeur = 10 + hudModeLargeur + 6 + hudTagLargeur + 6 + font.width(hudZone) + 10;
        if (pillage) largeur = Math.max(largeur, 22 + (int) Ui.spacedWidth(font, hudLigne2, Ui.SMALL, 0.6f) + 10);
        int haut = pillage ? 30 : 18;
        Ui.glass(g, x, y, largeur, haut, 170);
        int bord = pillage ? Ui.DANGER : couleur;
        g.fill(x + 1, y + 2, x + 3, y + haut - 2, Ui.argb(bord, 230));
        int tx = x + 10;
        Ui.spaced(g, font, hudMode, Ui.BOLD, tx, y + 5, Ui.argb(pillage ? Ui.mix(Ui.DANGER, 0xFFFFFF, 0.2f) : Ui.MUTED, 255), 1.2f);
        tx += hudModeLargeur + 6;
        Ui.spaced(g, font, hudTag, Ui.BOLD, tx, y + 5, Ui.argb(Ui.mix(couleur, 0xFFFFFF, 0.25f), 255), 0.6f);
        tx += hudTagLargeur + 6;
        g.drawString(font, hudZone, tx, y + 5, Ui.argb(Ui.TEXT, 235), false);
        if (pillage) {
            Icons.draw(g, Icons.VIE, x + 10, y + 18, 8, Ui.DANGER, 230);
            Ui.spaced(g, font, hudLigne2, Ui.SMALL, x + 22, y + 19, Ui.argb(Ui.MUTED, 255), 0.6f);
        }
    }

    // =================================================================== compte a rebours de teleportation
    /**
     * Compte a rebours au centre de l'ecran : disque sombre, piste blanche, anneau d'accent (rouge en pillage) qui se
     * remplit depuis midi avec une tete claire, icone au centre ; dessous « ENTREE DANS LA BASE · 7 s » et « Ne bougez
     * pas » sur une bande sombre. Annulation : anneau rouge plein, croix, secousse de 0,2 s, titre et raison, fondu final.
     */
    private static void compteARebours(GuiGraphics g, Font font, int w, int h) {
        long now = Util.getMillis();
        float cx = w / 2f, cy = h / 2f;
        int ty = Math.round(cy + 32);
        Packets.TeleportS2C t = ClientBases.teleport();
        if (t != null) {
            int s = ClientBases.teleportRestantS();
            if (s != tpSeconde) {
                if (tpSeconde > 0 && s < tpSeconde) Sons.jouer(Sons.TIC, 0.5f, s <= 3 ? 1.25f : 1.0f);
                if (tpTitre.isEmpty()) teleport(t);
                tpSeconde = s;
                tpSecondes = Ui.tr(Ui.BOLD, "gui.heralisz_bases.tp2.secondes", Math.max(0, s));
            }
            float p = ClientBases.teleportProgression();
            int accent = tpGenre == Packets.TeleportS2C.Genre.PILLAGE ? Ui.DANGER : Ui.accent();
            Ui.disque(g, cx, cy, 27f, Ui.argb(0x000000, 84));
            Ui.anneau(g, cx, cy, 19.6f, 21.4f, 0f, 1f, Ui.argb(0xFFFFFF, 40));
            Ui.anneau(g, cx, cy, 19f, 22f, 0f, p, Ui.argb(accent, 240));
            double ang = p * 2 * Math.PI;
            Ui.disque(g, cx + 20.5f * (float) Math.sin(ang), cy - 20.5f * (float) Math.cos(ang), 2.3f,
                    Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.6f), 255));
            int icone = tpGenre == Packets.TeleportS2C.Genre.SORTIE ? Icons.SORTIR
                    : tpGenre == Packets.TeleportS2C.Genre.PILLAGE ? Icons.CIBLE : Icons.ENTRER;
            Icons.draw(g, icone, Math.round(cx - 6), Math.round(cy - 6), 12, Ui.mix(accent, 0xFFFFFF, 0.55f), 235);
            float tw = Ui.spacedWidth(font, tpTitre, Ui.SMALL, 1.0f), pw = font.width(tpPoint), sw = font.width(tpSecondes);
            float total = tw + pw + sw, x0 = cx - total / 2f;
            Ui.band(g, Math.round(x0 - 36), ty - 6, Math.round(x0 + total + 36), ty + 24, 0x000000, 100, 30);
            Ui.spaced(g, font, tpTitre, Ui.SMALL, x0, ty + 1, Ui.argb(Ui.mix(accent, 0xFFFFFF, 0.35f), 255), 1.0f);
            Ui.at(g, font, tpPoint, x0 + tw, ty, Ui.argb(Ui.MUTED, 255));
            Ui.at(g, font, tpSecondes, x0 + tw + pw, ty, Ui.argb(Ui.TEXT, 255));
            Ui.centered(g, font, tpSous, cx, ty + 12, Ui.argb(Ui.MUTED, 255));
            return;
        }
        long age = now - tpAnnuleeLe;
        if (tpAnnuleeLe <= 0 || age >= ANNULEE_MS) return;
        float a = age > ANNULEE_MS - 500 ? 1 - (age - (ANNULEE_MS - 500)) / 500f : 1;
        int al = Math.round(255 * a);
        if (al < 6) return;
        float choc = age < 220 ? (float) Math.sin(age / 220.0 * Math.PI * 3) * 2.5f * (1 - age / 220f) : 0f;
        float x = cx + choc;
        Ui.disque(g, x, cy, 27f, Ui.argb(0x000000, Math.round(92 * a)));
        Ui.anneau(g, x, cy, 19f, 22f, 0f, 1f, Ui.argb(Ui.DANGER, Math.round(225 * a)));
        Icons.draw(g, Icons.CROIX, Math.round(x - 6), Math.round(cy - 6), 12, Ui.DANGER, al);
        float tw = Ui.spacedWidth(font, tpAnnuleeTitre, Ui.SMALL, 1.0f);
        float bw = Math.max(tw, font.width(tpAnnuleeRaison));
        Ui.band(g, Math.round(cx - bw / 2f - 36), ty - 6, Math.round(cx + bw / 2f + 36), ty + 24, 0x000000, Math.round(100 * a), 30);
        Ui.spacedCentered(g, font, tpAnnuleeTitre, Ui.SMALL, cx, ty + 1, Ui.argb(Ui.DANGER, al), 1.0f);
        Ui.centered(g, font, tpAnnuleeRaison, cx, ty + 12, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.55f), al));
    }

    // =================================================================== refus discret
    private static void refus(GuiGraphics g, Font font, int w, int h) {
        if (refus == null) return;
        long age = Util.getMillis() - refusLe;
        if (age > 1600) {
            refus = null;
            return;
        }
        float a = age > 1100 ? 1 - (age - 1100) / 500f : 1;
        int al = Math.round(255 * a);
        if (al < 6) return;
        int tw = font.width(refus);
        int y = h - 82;
        Ui.band(g, w / 2 - tw / 2 - 30, y - 3, w / 2 + tw / 2 + 30, y + 10, 0x000000, Math.round(90 * a), 26);
        Ui.centered(g, font, refus, w / 2f, y, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.35f), al));
    }

    // =================================================================== pile de toasts
    /** Pile de toasts du HUD : en haut a droite. */
    public static void toasts(GuiGraphics g, Font font, int w) {
        toasts(g, font, w - 8 - TOAST_W, 8);
    }

    /** Pile de toasts dans nos ecrans : centree en haut (le coin haut droit porte la croix et la banque / le chrono). */
    public static void toastsEcran(GuiGraphics g, Font font, int w) {
        toasts(g, font, (w - TOAST_W) / 2, 2);
    }

    private static void toasts(GuiGraphics g, Font font, int x, int y0) {
        if (TOASTS.isEmpty() && EN_ATTENTE.isEmpty()) return;
        long now = Util.getMillis();
        while (!TOASTS.isEmpty() && TOASTS.peekFirst().fini(now)) TOASTS.pollFirst();
        while (TOASTS.size() < PILE_MAX && !EN_ATTENTE.isEmpty()) TOASTS.addLast(EN_ATTENTE.remove(0).relance(now));
        int y = y0;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        for (Toast t : TOASTS) {
            float a = t.alpha(now);
            int dx = Math.round((1 - Math.min(1, (now - t.depuis) / (float) FONDU_IN)) * 12);
            dessiner(g, font, t, x + dx, y, t.hauteur(), a, now);
            y += t.hauteur() + 6;
        }
        g.pose().popPose();
    }

    private static void dessiner(GuiGraphics g, Font font, Toast t, int x, int y, int h, float a, long now) {
        int al = Math.round(255 * a);
        if (al < 6) return;
        Ui.soft(g, x, y, TOAST_W, h, Ui.argb(Ui.VERRE, Math.round(215 * a)));
        Ui.softOutline(g, x + 1, y + 1, TOAST_W - 2, h - 2, Ui.argb(0xFFFFFF, Math.round(26 * a)));
        if (t.pulse) {
            float p = (float) (0.5 + 0.5 * Math.sin(now / 160.0));
            Ui.soft(g, x, y, TOAST_W, h, Ui.argb(t.accent, Math.round(30 * p * a)));
        }
        g.fill(x + 1, y + 2, x + 3, y + h - 2, Ui.argb(t.accent, Math.round(230 * a)));
        Icons.draw(g, t.icone, x + 9, y + (h - 12) / 2, 12, t.accent, al);
        int tx = x + 27;
        Ui.spaced(g, font, t.titre, Ui.SMALL, tx, y + 6, Ui.argb(t.accent, al), 0.8f);
        g.drawString(font, t.ligne1, tx, y + 15, Ui.argb(Ui.TEXT, al), false);
        if (t.ligne2 != null) g.drawString(font, t.ligne2, tx, y + 25, Ui.argb(Ui.TEXT, al), false);
    }
}
