package fr.heraliz.grades.client.chat;

import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Chat HeraliZ (25/10, maquettes {@code maquettes/staff_chat/4_} et {@code 5_} validées) : remplace l'affichage du chat
 * de Minecraft, rien d'autre (les messages arrivent comme avant, {@code ChatComponentMixin}).
 * <ul>
 *     <li><b>fermé</b> : 4 lignes au plus, chacune s'efface au bout de 8 s, jamais par-dessus une interface (inventaire,
 *     fouille, menus…) ;</li>
 *     <li><b>ouvert</b> ({@link EcranChat}) : panneau de verre, onglets Proximité / Global / Clan (Tab pour changer, messages
 *     non lus), défilement à la molette, liens cliquables ;</li>
 *     <li>chaque ligne porte son canal : PROX, GLOBAL, CLAN, MP (messages privés) ou INFO (serveur, commandes) ;</li>
 *     <li>les annonces d'extraction ne vont plus dans le chat : petite notification en haut à gauche ;</li>
 *     <li>les comptes rendus de commandes des autres op (« [X : …] ») ne s'affichent plus.</li>
 * </ul>
 */
public final class ChatHeraliz {
    private ChatHeraliz() {
    }

    /** Canaux d'affichage : ceux d'écriture (ReseauChat) puis messages privés et informations. */
    public static final int MP = 3, INFO = 4;
    private static final int MAX_LIGNES = 100, VIE_MS = 8000, FONDU_MS = 1000, LIGNES_FERME = 4, H = 10;
    static final int LIGNES_OUVERT = 12;
    private static final int[] COULEUR_CANAL = {0xA0A6B0, 0xE6E8EC, 0x6FD08C, 0xB48CFF, 0xF2C14E};
    private static final String[] TAG = {"prox", "global", "clan", "mp", "info"};
    static final P TEXTE = Trace.p("bq_texte", 7.5F, 0), GRAS = Trace.p("bq_texte_gras", 7.5F, 0), MICRO = Trace.p("bq_micro", 5.6F, 0.4F),
            MINI = Trace.p("bq_mini", 6.5F, 0.56F), PETIT = Trace.p("bq_petit", 6.5F, 0), ONGLET = Trace.p("bq_mini", 6.5F, 0);

    /** Une ligne : canal, texte complet (polices posées), heure ; lignes coupées gardées pour une largeur. */
    static final class Ligne {
        final int canal;
        final Component texte;
        final long t;
        private List<FormattedCharSequence> rangs;
        private int largeur = -1;

        Ligne(int canal, Component texte) {
            this.canal = canal;
            this.texte = texte;
            this.t = System.currentTimeMillis();
        }

        List<FormattedCharSequence> rangs(Font f, int w) {
            if (w != largeur || rangs == null) {
                rangs = f.split(texte, Math.max(20, w));
                largeur = w;
            }
            return rangs;
        }
    }

    private static final ArrayDeque<Ligne> LIGNES = new ArrayDeque<>();
    static int canal = ReseauChat.GLOBAL;
    static final int[] NON_LUS = new int[ReseauChat.NOMBRE];

    public static void init() {
        ReseauChat.RECEPTION_LIGNE.brancher(ChatHeraliz::recevoir);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ChatHeraliz::overlay);
        MinecraftForge.EVENT_BUS.addListener(ChatHeraliz::ouverture);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ReseauChat.CANAL.sendToServer(new ReseauChat.Choisir(canal)));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> vider());
    }

    // ------------------------------------------------------------------ messages
    private static Component police(Component c, P p) {
        return Ui.in(c, p.police());
    }

    private static void ajouter(int c, Component texte) {
        LIGNES.addLast(new Ligne(c, texte));
        while (LIGNES.size() > MAX_LIGNES) LIGNES.removeFirst();
        if (c < ReseauChat.NOMBRE && (c != canal || !(Minecraft.getInstance().screen instanceof EcranChat))) NON_LUS[c]++;
    }

    /** Ligne de chat d'un joueur, envoyée par le serveur avec son canal. */
    private static void recevoir(ReseauChat.Ligne l) {
        MutableComponent t = Component.empty();
        t.append(police(l.nom(), GRAS));
        t.append(police(Component.literal("  "), TEXTE));
        int c = l.couleur() != 0 ? l.couleur() : 0xE6E8EC;
        t.append(Component.literal(l.message()).withStyle(s -> s.withFont(TEXTE.police()).withColor(c)));
        ajouter(Math.max(0, Math.min(ReseauChat.NOMBRE - 1, l.canal())), t);
    }

    /** Toute ligne ajoutée au chat de Minecraft (serveur, commandes, mods côté joueur). */
    public static void ajouterMinecraft(Component m) {
        if (m == null) return;
        String cle = m.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
        if (cle.equals("heraliz_extraction.chat.prefixe") || cle.equals("heraliz_evenements.chat.prefixe")) {
            NotifExtraction.recevoir(m, cle.startsWith("heraliz_evenements"));
            return;
        }
        if (cle.equals("chat.type.admin")) return;
        int c = cle.equals("chat.clan") ? ReseauChat.CLAN
                : cle.startsWith("commands.message.display") ? MP
                : cle.equals("chat.type.text") || cle.equals("chat.type.announcement") ? ReseauChat.GLOBAL : INFO;
        ajouter(c, police(m, TEXTE));
    }

    /** Chat vidé (F3 + D, ou le staff pour tout le monde). */
    public static void vider() {
        LIGNES.clear();
        java.util.Arrays.fill(NON_LUS, 0);
    }

    /** Le joueur a un clan (tag envoyé par le serveur dans les infos du tab). */
    static boolean aUnClan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        fr.heraliz.grades.reseau.ReseauGrades.LigneTab l = fr.heraliz.grades.client.TabHeraliz.info(mc.player.getUUID());
        return l != null && !l.clan().isEmpty();
    }

    static void choisir(int c) {
        if (c < 0 || c >= ReseauChat.NOMBRE || c == canal) return;
        canal = c;
        NON_LUS[c] = 0;
        ReseauChat.CANAL.sendToServer(new ReseauChat.Choisir(c));
    }

    /** Canal suivant (Tab) ; Clan sauté sans clan. */
    static void suivant() {
        int c = canal;
        for (int i = 0; i < ReseauChat.NOMBRE; i++) {
            c = (c + 1) % ReseauChat.NOMBRE;
            if (c != ReseauChat.CLAN || aUnClan()) break;
        }
        choisir(c);
    }

    // ------------------------------------------------------------------ écran de chat
    private static Field initial;

    /** Le chat de Minecraft s'ouvre (T, /) : c'est le nôtre qui s'ouvre, avec le même texte de départ. */
    private static void ouverture(ScreenEvent.Opening e) {
        if (e.getNewScreen() == null || e.getNewScreen().getClass() != ChatScreen.class) return;
        String texte = "";
        try {
            if (initial == null) initial = ObfuscationReflectionHelper.findField(ChatScreen.class, "f_95576_");
            texte = (String) initial.get(e.getNewScreen());
        } catch (RuntimeException | IllegalAccessException ex) {
            texte = "";
        }
        e.setNewScreen(new EcranChat(texte == null ? "" : texte));
    }

    // ------------------------------------------------------------------ dessin
    /** Largeur du chat (unités d'interface) : un tiers de l'écran, entre 200 et 320. */
    static int largeur(int ecran) {
        return Math.max(200, Math.min(320, Math.round(ecran * 0.34F)));
    }

    private static void overlay(RenderGuiOverlayEvent.Pre e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        if (mc.screen == null || mc.screen instanceof EcranChat) NotifExtraction.dessiner(g, mc.font);
        if (mc.screen == null) dessinerFerme(g, mc.font, w, h);
    }

    private static boolean visible(Ligne l) {
        ChatVisiblity v = Minecraft.getInstance().options.chatVisibility().get();
        if (v == ChatVisiblity.HIDDEN) return false;
        return v != ChatVisiblity.SYSTEM || l.canal == INFO || l.canal == MP;
    }

    /** Pastille du canal ; rend sa largeur (avec l'écart qui suit). */
    static float pastille(GuiGraphics g, Font f, int c, float x, float cy, float alpha) {
        String s = Component.translatable("gui.heraliz_grades.chat.tag." + TAG[c]).getString().toUpperCase(Locale.ROOT);
        float tw = Trace.largeur(f, s, MICRO);
        Trace.rect(g, x, cy - 4, x + tw + 6, cy + 4, a(0, 120 * alpha));
        Trace.c(g, f, s, x + (tw + 6) / 2, cy, MICRO, a(COULEUR_CANAL[c], 255 * alpha), 0);
        return tw + 6 + 4;
    }

    /** Un rang de texte, milieu des capitales sur cy. */
    static void rang(GuiGraphics g, Font f, FormattedCharSequence s, float x, float cy, float alpha, boolean ombre) {
        g.pose().pushPose();
        Ui.placer(g, x, cy - TEXTE.cap() / 2 - TEXTE.hautCap());
        g.drawString(f, s, 0, 0, a(0xFFFFFF, 255 * alpha), ombre);
        g.pose().popPose();
    }

    private static void dessinerFerme(GuiGraphics g, Font f, int w, int h) {
        long now = System.currentTimeMillis();
        float opacite = (float) (double) Minecraft.getInstance().options.chatOpacity().get();
        int largeur = largeur(w);
        // 25/10 (audit en jeu) : au-dessus des cœurs et de l'armure (h - 49) : à h - 44, la dernière ligne passait dessus
        float x0 = 8, cy = h - 56;
        int rangs = 0;
        Iterator<Ligne> it = LIGNES.descendingIterator();
        while (it.hasNext() && rangs < LIGNES_FERME) {
            Ligne l = it.next();
            long age = now - l.t;
            if (age > VIE_MS) break;
            if (!visible(l)) continue;
            float alpha = opacite * Math.min(1, (VIE_MS - age) / (float) FONDU_MS);
            float decal = Trace.largeur(f, Component.translatable("gui.heraliz_grades.chat.tag." + TAG[l.canal]).getString().toUpperCase(Locale.ROOT), MICRO) + 10;
            List<FormattedCharSequence> rs = l.rangs(f, (int) (largeur - decal));
            for (int i = rs.size() - 1; i >= 0 && rangs < LIGNES_FERME; i--) {
                float y = cy - rangs * H;
                if (i == 0) pastille(g, f, l.canal, x0, y, alpha);
                rang(g, f, rs.get(i), x0 + decal, y, alpha, true);
                rangs++;
            }
        }
    }

    /** Lignes visibles du chat ouvert, du bas vers le haut (après le défilement) : ligne, rang, position. */
    record Place(Ligne ligne, FormattedCharSequence rang, boolean premier, float x, float cy) {
    }

    /** Places du chat ouvert ; défilement en rangs ; rend aussi le nombre total de rangs dans total[0]. */
    static List<Place> places(Font f, int largeur, float x0, float bas, int defil, int[] total) {
        List<Place> out = new ArrayList<>();
        int n = 0;
        Iterator<Ligne> it = LIGNES.descendingIterator();
        while (it.hasNext()) {
            Ligne l = it.next();
            if (!visible(l)) continue;
            float decal = Trace.largeur(f, Component.translatable("gui.heraliz_grades.chat.tag." + TAG[l.canal]).getString().toUpperCase(Locale.ROOT), MICRO) + 10;
            List<FormattedCharSequence> rs = l.rangs(f, (int) (largeur - decal - 8));
            for (int i = rs.size() - 1; i >= 0; i--) {
                int k = n - defil;
                if (k >= 0 && k < LIGNES_OUVERT) out.add(new Place(l, rs.get(i), i == 0, x0 + decal, bas - k * H));
                n++;
            }
        }
        if (total != null) total[0] = n;
        return out;
    }

    /** Style du texte sous la souris (liens, survol) dans le chat ouvert. */
    static Style styleA(Font f, List<Place> places, double mx, double my) {
        for (Place p : places) {
            if (my < p.cy() - H / 2F || my >= p.cy() + H / 2F) continue;
            if (mx < p.x()) return null;
            return f.getSplitter().componentStyleAtWidth(p.rang(), (int) Math.floor(mx - p.x()));
        }
        return null;
    }

    // ------------------------------------------------------------------ notification des extractions
    /**
     * Annonces d'extraction (HeraliZ Extraction, préfixe « [Extraction] ») : en haut à gauche, 10 s. Les ouvertures d'une
     * même vague (arrivées à moins de 2 s) sont groupées : « Extraction 3 · 7 · 12 · 18 — pendant 1 h ». Même place pour
     * les annonces des événements de la carte (25/10, HeraliZ Événements, préfixe « [Événement] ») : titre et filet orange.
     */
    static final class NotifExtraction {
        private static final List<String> ZONES = new ArrayList<>();
        private static String duree = "", texte = "";
        private static long debut, derniere;
        private static boolean ouverture, evenement;
        private static Method hudZone;
        private static boolean hudCherche;

        static void recevoir(Component m, boolean annonceEvenement) {
            long now = System.currentTimeMillis();
            evenement = annonceEvenement;
            for (Component s : annonceEvenement ? List.<Component>of() : m.getSiblings()) {
                if (s.getContents() instanceof TranslatableContents tc && tc.getKey().equals("heraliz_extraction.zone.ouverte")
                        && tc.getArgs().length >= 2) {
                    if (!ouverture || now - derniere > 2000) ZONES.clear();
                    Object nom = tc.getArgs()[0], d = tc.getArgs()[1];
                    String n = nom instanceof Component c ? c.getString() : String.valueOf(nom);
                    ZONES.add(n.replaceAll("(?i)^extraction\\s+", ""));
                    duree = d instanceof Component c ? c.getString() : String.valueOf(d);
                    ouverture = true;
                    derniere = now;
                    debut = now;
                    return;
                }
            }
            StringBuilder b = new StringBuilder();
            for (Component s : m.getSiblings()) b.append(s.getString());
            texte = b.toString().strip();
            ouverture = false;
            debut = now;
        }

        /** Le panneau de la zone des clans est déjà en haut à gauche : on se pose dessous. */
        private static boolean zoneClan() {
            if (!hudCherche) {
                hudCherche = true;
                try {
                    hudZone = Class.forName("fr.heralisz.clans.client.ClientClans").getMethod("hudZone");
                } catch (ReflectiveOperationException | LinkageError ex) {
                    hudZone = null;
                }
            }
            try {
                return hudZone != null && hudZone.invoke(null) != null;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return false;
            }
        }

        private static java.lang.reflect.Method purge;
        private static boolean purgeCherche;

        /** L'icône de la Purge de CRac est aussi en haut à gauche (25/10) : on se pose dessous ({@code PurgeHud.hauteur()}). */
        private static int hauteurPurge() {
            if (!purgeCherche) {
                purgeCherche = true;
                try {
                    purge = Class.forName("fr.heraliz.crac.client.PurgeHud").getMethod("hauteur");
                } catch (ReflectiveOperationException | LinkageError ex) {
                    purge = null;
                }
            }
            try {
                return purge == null ? 0 : (int) purge.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return 0;
            }
        }

        static void dessiner(GuiGraphics g, Font f) {
            long age = System.currentTimeMillis() - debut;
            if (debut == 0 || age > 10_000) return;
            float alpha = Math.min(1, Math.min(age / 250F, (10_000 - age) / 800F));
            String titre = Component.translatable(evenement ? "gui.heraliz_grades.chat.evenement"
                    : ouverture ? "gui.heraliz_grades.chat.extractions_ouvertes" : "gui.heraliz_grades.chat.extraction").getString();
            int couleur = evenement ? 0xFF7A45 : 0xF2C14E;
            String corps = ouverture ? Component.translatable("gui.heraliz_grades.chat.extractions_liste", String.join(" · ", ZONES), duree).getString()
                    : texte;
            float x = 10, y = (zoneClan() ? 52 : 10) + hauteurPurge();
            float w = Math.min(300, Math.max(Trace.largeur(f, titre, MINI), Trace.largeur(f, corps, GRAS)) + 22), h = 28;
            Trace.rect(g, x, y, x + w, y + h, a(0x0D0F14, 205 * alpha));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(0xFFFFFF, 26 * alpha));
            Trace.rect(g, x, y, x + 1.5F, y + h, a(couleur, 255 * alpha));
            Trace.texte(g, f, titre, x + 9, y + 7, MINI, a(couleur, 255 * alpha));
            Trace.m(g, f, corps, x + 9, y + 19.5F, GRAS, a(0xFFFFFF, 255 * alpha), w - 16);
        }
    }
}
