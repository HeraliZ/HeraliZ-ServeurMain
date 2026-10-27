package fr.heraliz.casino.client.screen;

import fr.heraliz.casino.block.entity.AtmBlockEntity;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.item.ChipItem;
import fr.heraliz.casino.net.ActionPacket;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ComptePacket;
import fr.heraliz.casino.registry.ModRegistry;
import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.SonsUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static fr.heraliz.grades.client.boutique.Trace.a;
import static fr.heraliz.grades.client.boutique.Trace.mix;

/**
 * Ecran du distributeur de capsules (« ATM », 27/10), maquette validee par le proprietaire (outils/maquette_atm.py,
 * maquettes/atm_capsules) : canevas de 640 x 360 unites (echelle forcee par {@link Echelle}), style de la boutique (voile
 * sombre sur le monde, verre, polices a leur vraie taille). A gauche le solde du compte et les capsules portees, puis les
 * derniers mouvements ; a droite deposer et retirer (boutons et nombre tape), puis ce que fait le compte a la caisse.
 * <p>Il n'affiche que ce que le serveur envoie (ComptePacket) : chaque operation part au serveur, qui decide de tout et
 * renvoie le resultat, ecrit ici (jamais dans le chat). Mesures recopiees de la maquette : changer l'un, c'est changer
 * l'autre.
 */
public final class AtmScreen extends Screen {
    // ------------------------------------------------------------------ mesures de la maquette
    private static final int GW = 640, GH = 360, M = 16, GAUCHE = 290;
    private static final int WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, MUTED = 0x8F95A0, FAINT = 0x5A5F69, NIGHT = 0x050608, GLASS = 0x0D0F14,
            GOLD = 0xF2C14E, OK = 0x6FD08C, DANGER = 0xFF8A80, CAPSULE = 0xE0503C;
    private static final P TITRE = Trace.p("bq_titre", 13, 1.93F), BOUTON = Trace.p("bq_bouton", 9, 0.85F), MINI = Trace.p("bq_mini", 6.5F, 0.56F),
            MINI_NUM = Trace.p("bq_mini", 6.5F, 0.5F), TEXTE = Trace.p("bq_texte", 7.5F, 0), TEXTE_GRAS = Trace.p("bq_bouton", 9, 0),
            PETIT = Trace.p("bq_petit", 6.5F, 0), PETIT_GRAS = Trace.p("bq_mini", 6.5F, 0), CHIFFRE_GRAND = Trace.p("bq_titre", 13, 0);
    private static final long[] DEPOTS = {100, 500, 1000}, RETRAITS = {100, 1000, 5000};
    /** Chiffres tapes, au plus. */
    private static final int MAX_CHIFFRES = 10;
    /** Duree d'affichage du resultat d'une operation (ms). */
    private static final long MESSAGE_MS = 7000;

    private final AtmBlockEntity be;
    private final long ouvertA = System.currentTimeMillis();
    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();
    /** Champ actif : 0 aucun, 1 deposer, 2 retirer. */
    private int champ;
    private final StringBuilder saisieDepot = new StringBuilder(), saisieRetrait = new StringBuilder();
    /** Operation envoyee, en attente de la reponse du serveur (ms ; 0 : rien). */
    private long envoiA;
    /** Champ dont le nombre a ete envoye (0 : un bouton) : efface si l'operation reussit. */
    private int envoiChamp;
    private final Map<String, String> noms = new HashMap<>();

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public AtmScreen(AtmBlockEntity be) {
        super(Component.translatable("screen.heralizcasino.atm.titre"));
        this.be = be;
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("screen.heralizcasino.atm." + cle, args).getString();
    }

    private static String nombre(long v) {
        return Chips.format(v);
    }

    // ------------------------------------------------------------------ cycle
    @Override
    protected void init() {
        Echelle.forcer(this);
        ox = (width - GW) / 2;
        oy = (height - GH) / 2;
        if (envoiA == 0 && minecraft != null && minecraft.player != null) {
            // le serveur renvoie le compte a jour (derniers mouvements compris)
            CasinoNet.CHANNEL.sendToServer(new ActionPacket(be.getBlockPos(), AtmBlockEntity.ACTION_OUVRIR, new long[0]));
        }
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

    @Override
    public void tick() {
        Minecraft mc = minecraft;
        if (mc == null || mc.player == null || mc.level == null || be.isRemoved() || mc.level.getBlockEntity(be.getBlockPos()) != be
                || !be.isNear(mc.player)) {
            onClose();
            return;
        }
        // reponse recue : l'operation est finie ; reussie, le nombre tape est efface
        if (envoiA != 0 && ComptePacket.clientMessageA >= envoiA) {
            if (ComptePacket.clientMessageOk && envoiChamp != 0) saisie(envoiChamp).setLength(0);
            envoiA = 0;
        } else if (envoiA != 0 && System.currentTimeMillis() - envoiA > 4000) {
            envoiA = 0;
        }
    }

    private boolean enAttente() {
        return envoiA != 0;
    }

    private StringBuilder saisie(int c) {
        return c == 2 ? saisieRetrait : saisieDepot;
    }

    private static long valeur(StringBuilder s) {
        if (s.length() == 0) return 0;
        try {
            return Long.parseLong(s.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ operations
    private void envoyer(int action, long n, int depuisChamp) {
        if (enAttente() || !ComptePacket.clientActif) return;
        if (action == AtmBlockEntity.ACTION_RETIRER && n <= 0) return;
        envoiA = System.currentTimeMillis();
        envoiChamp = depuisChamp;
        SonsUi.clic(0.8F);
        CasinoNet.CHANNEL.sendToServer(new ActionPacket(be.getBlockPos(), action, new long[]{Math.max(0, n)}));
    }

    private void valider(int c) {
        long n = valeur(saisie(c));
        if (n <= 0) return;
        envoyer(c == 2 ? AtmBlockEntity.ACTION_RETIRER : AtmBlockEntity.ACTION_DEPOSER, n, c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            double x = mx - ox, y = my - oy;
            for (int i = zones.size() - 1; i >= 0; i--) {
                if (zones.get(i).dedans(x, y)) {
                    zones.get(i).action().run();
                    return true;
                }
            }
            champ = 0;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && champ != 0) {
            valider(champ);
            return true;
        }
        if (key == 259 && champ != 0) {
            StringBuilder s = saisie(champ);
            if (s.length() > 0) s.setLength(s.length() - 1);
            return true;
        }
        if (key == 258) {
            champ = champ == 1 ? 2 : 1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (champ != 0 && c >= '0' && c <= '9') {
            StringBuilder s = saisie(champ);
            if (s.length() == 0 && c == '0') return true;
            if (s.length() < MAX_CHIFFRES) s.append(c);
            return true;
        }
        return super.charTyped(c, mods);
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sx = mx;
        sy = my;
        zones.clear();
        voile(g);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        long[] surToi = surToi();
        entete(g, f);
        carteSolde(g, f, M, 60, GAUCHE, 118, surToi);
        carteMouvements(g, f, M, 186, GAUCHE, 150);
        float xd = M + GAUCHE + 10;
        carteOperations(g, f, xd, 60, GW - M - xd, 170, surToi[3]);
        carteCaisse(g, f, xd, 238, GW - M - xd, 98);
        pied(g, f);
        g.pose().popPose();
    }

    /** Capsules portees : nombre de capsules de 1, de 5, de 10, puis leur valeur totale. */
    private long[] surToi() {
        long[] n = new long[4];
        if (minecraft == null || minecraft.player == null) return n;
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ChipItem c) {
                int k = Chips.indexOf(c.value);
                if (k < 0) continue;
                n[k] += s.getCount();
                n[3] += (long) c.value * s.getCount();
            }
        }
        return n;
    }

    /** Le monde reste visible derriere un voile sombre (plus sombre sur les cotes) : celui de la boutique. */
    private void voile(GuiGraphics g) {
        float[] at = {0, 0.24F, 0.46F, 0.62F, 1};
        int[] al = {235, 215, 185, 200, 232};
        for (int i = 0; i < at.length - 1; i++) {
            Trace.gradH(g, width * at[i], 0, width * at[i + 1], height, a(NIGHT, al[i]), a(NIGHT, al[i + 1]));
        }
        Trace.gradV(g, 0, 0, width, height * 0.3F, a(0, 90), a(0, 0));
        Trace.gradV(g, 0, height * 0.6F, width, height, a(0, 0), a(0, 120));
    }

    private static void verre(GuiGraphics g, float x, float y, float w, float h) {
        Trace.rect(g, x, y, x + w, y + h, a(GLASS, 199));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 26));
        Trace.rect(g, x + 1 / 3F, y + 1 / 3F, x + w - 1 / 3F, y + 2 / 3F, a(WHITE, 41));
    }

    private static void filetHaut(GuiGraphics g, float x, float y, float w, int c) {
        float mid = x + w / 2;
        Trace.gradH(g, x + 10, y, mid, y + 2 / 3F, a(c, 0), a(c, 255));
        Trace.gradH(g, mid, y, x + w - 10, y + 2 / 3F, a(c, 255), a(c, 0));
    }

    private static void filet(GuiGraphics g, float x0, float x1, float y) {
        Trace.rect(g, x0, y, x1, y + 1 / 3F, a(WHITE, 23));
    }

    /** Bouton (fond en degrade de sa couleur, plus vif au survol ; eteint : gris) ; un clic lance l'action s'il est actif. */
    private void bouton(GuiGraphics g, Font f, float x, float y, float w, float h, String texte, boolean actif, int c, Runnable action) {
        if (actif) {
            float hv = survol(x, y, w, h) ? 1 : 0;
            Trace.gradH(g, x, y, x + w, y + h, a(c, 77 + 34 * hv), a(c, 31 + 26 * hv));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(c, 140 + 60 * hv));
            zone(x, y, w, h, action);
        } else {
            Trace.rect(g, x, y, x + w, y + h, a(WHITE, 13));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 31));
        }
        Trace.c(g, f, texte, x + w / 2, y + h / 2, BOUTON, a(actif ? WHITE : FAINT, 255), w - 6);
    }

    /** Champ du nombre tape (clic : il prend le clavier). */
    private void champ(GuiGraphics g, Font f, float x, float y, float w, float h, int quel, boolean actif) {
        StringBuilder s = saisie(quel);
        boolean focus = champ == quel && actif;
        Trace.rect(g, x, y, x + w, y + h, a(WHITE, 12));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, focus ? 60 : survol(x, y, w, h) ? 50 : 40));
        if (s.length() > 0) {
            float tw = Trace.m(g, f, nombre(valeur(s)), x + 6, y + h / 2, TEXTE, a(LIGHT, 255), w - 10);
            if (focus && (System.currentTimeMillis() / 500) % 2 == 0) Trace.rect(g, x + 6 + tw + 1, y + 3.5F, x + 6 + tw + 1.5F, y + h - 3.5F, a(WHITE, 200));
        } else {
            Trace.m(g, f, t("autre_nombre"), x + 6, y + h / 2, PETIT, a(FAINT, 255), w - 10);
            if (focus && (System.currentTimeMillis() / 500) % 2 == 0) Trace.rect(g, x + 6, y + 3.5F, x + 6.5F, y + h - 3.5F, a(WHITE, 200));
        }
        if (actif) zone(x, y, w, h, () -> champ = quel);
    }

    /** Objet (capsule) dessine a la taille voulue. */
    private static void objet(GuiGraphics g, ItemStack s, float x, float y, float d) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(d / 16F, d / 16F, 1);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack capsule(int index) {
        return new ItemStack(ModRegistry.chip(index));
    }

    private void entete(GuiGraphics g, Font f) {
        Trace.texte(g, f, Trace.tenir(f, t("titre"), TITRE, GW - 2 * M), M, 18, TITRE, a(LIGHT, 255));
        Trace.texte(g, f, Trace.tenir(f, t("sous_titre"), TEXTE, GW - 2 * M), M, 35, TEXTE, a(MUTED, 255));
        filet(g, M, GW - M, 50);
    }

    private void carteSolde(GuiGraphics g, Font f, float x, float y, float w, float h, long[] surToi) {
        verre(g, x, y, w, h);
        filetHaut(g, x, y, w, GOLD);
        Trace.lueur(g, x + 34, y + 44, 34, 30, a(CAPSULE, 70));
        objet(g, capsule(2), x + 14, y + 24, 21);
        Trace.texte(g, f, Trace.tenir(f, t("sur_ton_compte"), MINI, w - 60), x + 50, y + 22, MINI, a(mix(GOLD, WHITE, 0.35F), 255));
        boolean actif = ComptePacket.clientActif;
        String solde = actif ? nombre(ComptePacket.clientSolde) : "—";
        String unite = t("capsules");
        float wu = Trace.largeur(f, unite, TEXTE_GRAS) + 4;
        float w1 = Trace.texte(g, f, Trace.tenir(f, solde, CHIFFRE_GRAND, w - 60 - wu), x + 50, y + 33, CHIFFRE_GRAND, a(WHITE, 255));
        if (actif) Trace.texte(g, f, unite, x + 50 + w1 + 4, y + 33 + CHIFFRE_GRAND.cap() - TEXTE_GRAS.cap(), TEXTE_GRAS, a(GOLD, 255));
        filet(g, x + 10, x + w - 10, y + 62);
        Trace.texte(g, f, t("sur_toi"), x + 10, y + 70, MINI, a(MUTED, 255));
        Trace.d(g, f, t("n_capsules", nombre(surToi[3])), x + w - 10, y + 70 + MINI.cap() / 2, PETIT_GRAS, a(LIGHT, 255), w - 80);
        float cx = x + 10;
        for (int k = 2; k >= 0; k--) {
            objet(g, capsule(k), cx, y + 82, 14);
            cx += 16;
            cx += Trace.m(g, f, "× " + nombre(surToi[k]), cx, y + 89, TEXTE_GRAS, a(LIGHT, 255), 0) + 14;
        }
        Trace.texte(g, f, Trace.tenir(f, t("note_capsules"), PETIT, w - 20), x + 10, y + h - 14, PETIT, a(FAINT, 255));
    }

    private void carteMouvements(GuiGraphics g, Font f, float x, float y, float w, float h) {
        verre(g, x, y, w, h);
        Trace.texte(g, f, t("mouvements"), x + 10, y + 10, MINI, a(MUTED, 255));
        List<ComptePacket.Mouvement> l = ComptePacket.clientMouvements;
        if (!ComptePacket.clientActif || l.isEmpty()) {
            Trace.m(g, f, t(ComptePacket.clientActif ? "aucun_mouvement" : "compte_ferme"), x + 14, y + 30, PETIT, a(FAINT, 255), w - 28);
            return;
        }
        long passe = Math.max(0, (System.currentTimeMillis() - ComptePacket.clientRecu) / 1000);
        float ly = y + 24;
        for (int i = 0; i < l.size() && i < 7; i++) {
            ComptePacket.Mouvement m = l.get(i);
            int c = m.n() > 0 ? OK : m.genre().equals("retrait") ? GOLD : DANGER;
            Trace.rect(g, x + 8, ly - 1, x + w - 8, ly + 13, a(WHITE, 6));
            Trace.m(g, f, (m.n() > 0 ? "+" : "−") + nombre(Math.abs(m.n())), x + 14, ly + 6, TEXTE_GRAS, a(c, 255), 54);
            Trace.m(g, f, libelle(m), x + 70, ly + 6, TEXTE, a(LIGHT, 255), w - 70 - 70);
            Trace.d(g, f, quand(m.age() + passe), x + w - 14, ly + 6, PETIT, a(FAINT, 255), 60);
            ly += 16;
        }
    }

    /** Ce qu'est un mouvement, dans la langue du joueur ; l'objet de la caisse par son vrai nom. */
    private String libelle(ComptePacket.Mouvement m) {
        String d = m.detail();
        return switch (m.genre()) {
            case "depot", "retrait", "entree", "staff", "wipe" -> t("genre." + m.genre());
            case "caisse" -> d.isEmpty() ? t("genre.caisse_seul") : t("genre.caisse", nomObjet(d));
            case "rendu" -> d.isEmpty() ? t("genre.rendu_seul") : t("genre.rendu", nomObjet(d));
            default -> t("genre.autre");
        };
    }

    /** « identifiant [NBT] » d'un objet de la caisse -> son nom ; sinon (nom d'un encaissement) le texte tel quel. */
    private String nomObjet(String d) {
        return noms.computeIfAbsent(d, k -> {
            int sp = k.indexOf(' ');
            String id = sp < 0 ? k : k.substring(0, sp);
            ResourceLocation rl = id.indexOf(':') > 0 ? ResourceLocation.tryParse(id) : null;
            Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (it == null || it == Items.AIR) return k;
            ItemStack s = new ItemStack(it);
            if (sp > 0) {
                try {
                    s.setTag(TagParser.parseTag(k.substring(sp + 1)));
                } catch (Exception ignore) {
                }
            }
            return s.getHoverName().getString();
        });
    }

    private static String quand(long s) {
        if (s < 60) return t("quand.instant");
        if (s < 3600) return t("quand.minutes", s / 60);
        if (s < 86400) return t("quand.heures", s / 3600);
        if (s < 2 * 86400) return t("quand.hier");
        return t("quand.jours", s / 86400);
    }

    private void carteOperations(GuiGraphics g, Font f, float x, float y, float w, float h, long porte) {
        verre(g, x, y, w, h);
        boolean ouvert = ComptePacket.clientActif && !enAttente();
        long solde = ComptePacket.clientSolde;
        // deposer
        Trace.texte(g, f, t("deposer"), x + 10, y + 10, MINI, a(OK, 255));
        bouton(g, f, x + 10, y + 20, w - 20, 18, t("tout_deposer", nombre(porte)).toUpperCase(Locale.ROOT), ouvert && porte > 0, OK,
                () -> envoyer(AtmBlockEntity.ACTION_DEPOSER, 0, 0));
        float bw = (w - 20 - 2 * 4) / 3;
        for (int i = 0; i < DEPOTS.length; i++) {
            long v = DEPOTS[i];
            bouton(g, f, x + 10 + i * (bw + 4), y + 42, bw, 15, nombre(v), ouvert && porte >= v, OK,
                    () -> envoyer(AtmBlockEntity.ACTION_DEPOSER, v, 0));
        }
        champ(g, f, x + 10, y + 61, w - 20 - 40, 15, 1, ouvert);
        bouton(g, f, x + w - 10 - 36, y + 61, 36, 15, t("ok"), ouvert && valeur(saisieDepot) > 0, OK, () -> valider(1));
        filet(g, x + 10, x + w - 10, y + 86);
        // retirer
        Trace.texte(g, f, t("retirer"), x + 10, y + 94, MINI, a(GOLD, 255));
        for (int i = 0; i < RETRAITS.length; i++) {
            long v = RETRAITS[i];
            bouton(g, f, x + 10 + i * (bw + 4), y + 104, bw, 15, nombre(v), ouvert && solde >= v, GOLD,
                    () -> envoyer(AtmBlockEntity.ACTION_RETIRER, v, 0));
        }
        champ(g, f, x + 10, y + 123, w - 20 - 40, 15, 2, ouvert);
        bouton(g, f, x + w - 10 - 36, y + 123, 36, 15, t("ok"), ouvert && valeur(saisieRetrait) > 0, GOLD, () -> valider(2));
        // resultat de la derniere operation (envoye par le serveur), sinon la regle du retrait
        long age = System.currentTimeMillis() - ComptePacket.clientMessageA;
        Component msg = ComptePacket.clientMessage;
        if (msg != null && ComptePacket.clientMessageA >= ouvertA && age < MESSAGE_MS) {
            int c = ComptePacket.clientMessageOk ? OK : DANGER;
            float al = Math.min(1, (MESSAGE_MS - age) / 600F);
            // une ligne, deux au plus (grands nombres) : la case grandit vers le bas de la carte
            List<String> l = Trace.lignes(f, msg.getString(), PETIT_GRAS, w - 32);
            int n = Math.min(2, l.size());
            float bh = n > 1 ? 22.5F : 14;
            Trace.rect(g, x + 10, y + 144, x + w - 10, y + 144 + bh, a(c, 30 * al));
            Trace.bord(g, x + 10, y + 144, w - 20, bh, 1 / 3F, a(c, 120 * al));
            for (int k = 0; k < n; k++) {
                Trace.m(g, f, l.get(k), x + 16, y + 151 + k * 8.5F, PETIT_GRAS, a(mix(c, WHITE, 0.3F), Math.max(20, 255 * al)), w - 32);
            }
        } else if (!ComptePacket.clientActif) {
            Trace.m(g, f, t("compte_ferme"), x + 10, y + 151, PETIT_GRAS, a(DANGER, 255), w - 20);
        } else {
            List<String> l = Trace.lignes(f, t("note_retrait"), PETIT, w - 20);
            for (int k = 0; k < l.size() && k < 2; k++) Trace.texte(g, f, Trace.tenir(f, l.get(k), PETIT, w - 20), x + 10, y + 145 + k * 8.5F, PETIT, a(FAINT, 255));
        }
    }

    private void carteCaisse(GuiGraphics g, Font f, float x, float y, float w, float h) {
        verre(g, x, y, w, h);
        Trace.texte(g, f, Trace.tenir(f, t("caisse_titre"), MINI, w - 20), x + 10, y + 10, MINI, a(MUTED, 255));
        boolean paie = ComptePacket.clientActif && ComptePacket.clientPaiement;
        if (paie) Trace.coche(g, x + 10, y + 22, 6, 1.2F, a(OK, 255));
        else Trace.rect(g, x + 10, y + 24.5F, x + 16, y + 25.7F, a(DANGER, 255));
        Trace.texte(g, f, Trace.tenir(f, t(paie ? "caisse_oui" : "caisse_non"), TEXTE_GRAS, w - 31), x + 21, y + 23, TEXTE_GRAS, a(LIGHT, 255));
        float ly = y + 38;
        List<String> l = Trace.lignes(f, t("caisse_explication"), PETIT, w - 20);
        for (int k = 0; k < l.size() && k < 4; k++) {
            Trace.texte(g, f, Trace.tenir(f, l.get(k), PETIT, w - 20), x + 10, ly, PETIT, a(MUTED, 255));
            ly += 9;
        }
        Trace.texte(g, f, Trace.tenir(f, t("machines"), PETIT_GRAS, w - 20), x + 10, ly + 6, PETIT_GRAS, a(FAINT, 255));
    }

    private void pied(GuiGraphics g, Font f) {
        float cy = 348, x = M;
        String[][] touches = {{"echap", "fermer"}, {"entree", "valider"}};
        for (String[] k : touches) {
            String kl = t("touche." + k[0]).toUpperCase(Locale.ROOT);
            float tw = Trace.largeur(f, kl, MINI_NUM), w = Math.max(9, tw + 4.67F);
            Trace.rect(g, x, cy - 4.5F, x + w, cy + 4.5F, a(WHITE, 30));
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(WHITE, 61));
            Trace.c(g, f, kl, x + w / 2, cy, MINI_NUM, a(WHITE, 255), 0);
            x += w + 3.5F;
            x += Trace.m(g, f, t("pied." + k[1]), x, cy, PETIT, a(MUTED, 255), 0) + 10;
        }
        if (enAttente()) Trace.d(g, f, t("attente"), GW - M, cy, PETIT, a(FAINT, 255), GW - M - x - 8);
    }
}
