package fr.heraliz.crac.client.ui;

import fr.heraliz.crac.client.group.EcranGroupe;
import fr.heraliz.crac.client.kit.Dessin;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.C2SReapparition;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CReapparition;
import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import static fr.heraliz.crac.client.kit.Dessin.*;

/**
 * Écran de mort HeraliZ (25/10, demande du propriétaire), à la place de celui de Minecraft : deux choix clairs,
 * « Au camp » (toujours : la réapparition habituelle, au spawn) et « À la base » (devant l'armoire de la base de son clan,
 * si le serveur l'a permis : base posée, droit du clan, délai partagé avec /home fini). Le serveur a envoyé l'état à la
 * mort ({@link S2CReapparition}) et revérifie le choix à la réapparition ; si le délai finit pendant que l'écran est
 * ouvert, « À la base » s'allume. Boutons actifs après 1 s (comme Minecraft, contre un clic de trop). Au style des écrans
 * de clan. Retours du propriétaire (25/10, premier essai) : ni croix (on la prenait pour un bouton de fermeture) ni bouton
 * « Quitter le serveur » ; sans base (ou tant que le serveur n'a pas répondu), « Au camp » prend toute la largeur et la
 * carte « À la base » n'apparaît pas.
 */
public final class EcranMort extends EcranGroupe {
    private static int raison = -1;
    private static long resteMs, recuA;
    private static Field champCause, champHardcore;

    private final Component cause;
    private int ticks;
    private boolean envoye;

    private EcranMort(Component cause) {
        super(Component.translatable("crac.mort.titre"));
        this.cause = cause;
    }

    /** Branché par la partie client : remplace l'écran de mort de Minecraft. */
    public static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EcranMort::ouverture);
        // état d'une mort d'une autre session oublié : à la connexion suivante, le serveur renvoie celui du moment
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> raison = -1);
        // mort : plus de mains ni d'arme à l'écran (25/10 : « je me vois debout alors que je suis mort »)
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RenderHandEvent e) -> {
            net.minecraft.client.player.LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null && p.isDeadOrDying()) e.setCanceled(true);
        });
    }

    /** État envoyé par le serveur à la mort. */
    public static void recevoir(S2CReapparition m) {
        raison = m.raison();
        resteMs = m.resteMs();
        recuA = System.currentTimeMillis();
    }

    private static void ouverture(ScreenEvent.Opening e) {
        if (!(e.getNewScreen() instanceof DeathScreen ds)) {
            return;
        }
        Component c = Component.empty();
        try {
            if (champHardcore == null) {
                champHardcore = ObfuscationReflectionHelper.findField(DeathScreen.class, "f_95908_");
                champCause = ObfuscationReflectionHelper.findField(DeathScreen.class, "f_95907_");
            }
            if (champHardcore.getBoolean(ds)) {
                return;
            }
            Object o = champCause.get(ds);
            if (o instanceof Component cc) {
                c = cc;
            }
        } catch (RuntimeException | IllegalAccessException ex) {
            return;
        }
        e.setNewScreen(new EcranMort(c));
    }

    private static long reste() {
        return Math.max(0, resteMs - (System.currentTimeMillis() - recuA));
    }

    /** « À la base » est possible maintenant. */
    private static boolean baseOuverte() {
        return (raison == 0 || raison == 4) && reste() <= 0;
    }

    @Override
    protected void initCanevas() {
        placer(W, H);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void tick() {
        this.ticks++;
        // réapparu : l'écran se ferme, et l'état de cette mort est oublié (la prochaine attend le sien)
        if (this.minecraft.player != null && this.minecraft.player.isAlive()) {
            raison = -1;
            this.minecraft.setScreen(null);
        }
    }

    /** La carte « À la base » est montrée : le serveur a répondu, et le clan du joueur a une base (raison 1 : aucune). */
    private static boolean avecBase() {
        return raison >= 0 && raison != 1;
    }

    private boolean actifs() {
        return this.ticks >= 20 && !this.envoye;
    }

    private void reapparaitre(boolean base) {
        if (!actifs() || this.minecraft.player == null) {
            return;
        }
        this.envoye = true;
        NetworkHandler.CHANNEL.sendToServer(new C2SReapparition(base));
        this.minecraft.player.respawn();
    }

    // ------------------------------------------------------------------ dessin
    /*
     * 28/10 (demande du propriétaire) : écran agrandi de 20 % (S), même structure. Textes à leur vraie taille : polices
     * ui_mort_* (assets/crac/font, tailles d'avant x 1,2), sans pose().scale ; lettres posées sur un point entier de
     * l'écran (n) ; tout texte coupé par « … » s'il ne tient pas dans sa case (titre, sous-titre, cartes, boutons, pied).
     */
    private static final float S = 1.2F;
    private static final float W = 330 * S, H = 160 * S, MARGE = 13.33F * S, ECART = 8 * S, CARTE_H = 84 * S;
    private static final ResourceLocation M_TITRE = new ResourceLocation("crac", "ui_mort_titre"),
            M_MINI = new ResourceLocation("crac", "ui_mort_mini"),
            M_MINI_GRAS = new ResourceLocation("crac", "ui_mort_mini_bold"),
            M_MOYEN = new ResourceLocation("crac", "ui_mort_moyen");
    /** Décalage du haut des lettres (points du canevas) : ceux d'avant x S. */
    private static final float D_MINI = 4.21F * S, D_MOYEN = 4.7F * S, D_TITRE = 3.91F * 0.93F * S;

    /** Position ramenée sur un point entier de l'écran (lettres nettes). */
    private float n(float v) {
        return Math.round(v / this.px) * this.px;
    }

    private float carteX(int i) {
        return this.x0 + MARGE + i * ((this.pw - 2 * MARGE - ECART) / 2 + ECART);
    }

    private float carteW() {
        return avecBase() ? (this.pw - 2 * MARGE - ECART) / 2 : this.pw - 2 * MARGE;
    }

    private float carteY() {
        return this.y0 + 46 * S;
    }

    /** Texte simple, coupé à {@code place}. */
    private void ligne(GuiGraphics g, String s, ResourceLocation f, float x, float y, float place, int argb) {
        texte(g, this.font, lit(Ui.ellipsis(this.font, s, f, (int) Math.floor(place)), f), n(x), n(y), argb);
    }

    /** Capitales espacées raccourcies par « … » tant qu'elles dépassent {@code place}. */
    private String coupe(String s, ResourceLocation f, float espace, float place) {
        String t = s;
        while (t.length() > 1 && largeurCaps(this.font, t, f, espace) > place) {
            t = t.substring(0, t.length() - (t.endsWith("…") ? 2 : 1)).trim() + "…";
        }
        return t;
    }

    /** Capitales espacées coupées à {@code place} ; rend leur largeur. */
    private float capsCoupees(GuiGraphics g, String s, ResourceLocation f, float x, float y, float espace, float place, int argb) {
        return caps(g, this.font, coupe(s, f, espace, place), f, n(x), n(y), espace, argb);
    }

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        fond(g);
        panneau(g, ACCENT);
        String sous = this.cause.getString();
        // en-tête sans « Échap fermer » ni croix : on ne quitte cet écran qu'en choisissant
        float x = this.x0 + MARGE;
        float place = this.pw - 2 * MARGE;
        capsCoupees(g, Component.translatable("crac.mort.titre").getString(), M_TITRE, x, this.y0 + 14 * S - D_TITRE, 1.12F * S, place,
            a(BLANC, 255));
        if (!sous.isEmpty()) {
            ligne(g, sous, M_MINI, x, this.y0 + 28.17F * S - D_MINI, place, a(ATTENUE, 255));
        }
        // titre de section : capitales espacées et filet qui s'efface vers la droite
        float capsTop = this.y0 + 38 * S;
        float sw = capsCoupees(g, Component.translatable("crac.mort.ou").getString(), M_MINI, x, capsTop - 2.46F * S, 0.8F * S, place,
            a(ATTENUE, 255));
        if (sw + 4.67F * S < place) {
            degradeH(g, x + sw + 4.67F * S, capsTop + 1.75F * S, x + place, capsTop + 1.75F * S + this.px, a(BLANC, 41), a(BLANC, 0));
        }
        carteCamp(g, mx, my);
        if (avecBase()) {
            carteBase(g, mx, my);
            // pied : délai partagé avec /home
            float py = this.y0 + this.ph - 14 * S;
            ligne(g, Component.translatable("crac.mort.partage").getString(), M_MINI, x, py - D_MINI, place, a(PALE, 255));
        }
    }

    /** Grand bouton en capitales (celui des écrans de groupe, x S, texte coupé s'il ne tient pas). */
    private void boutonCarte(GuiGraphics g, float x, float y, float w, float h, String texte, int rgb, boolean survol, boolean actif) {
        if (rgb == -2) {
            Dessin.bouton(g, x, y, w, h, true, actif && survol ? 1 : 0, this.px);
        } else {
            boutonTeinte(g, x, y, w, h, rgb, actif && survol ? 1 : 0, this.px);
        }
        int couleur = !actif ? a(PALE, 255) : rgb >= 0 ? a(mix(rgb, BLANC, survol ? 0.35F : 0.15F), 255) : a(BLANC, 255);
        float espace = 0.8F * S;
        String t = coupe(texte, M_MINI_GRAS, espace, w - 8 * S);
        float tw = largeurCaps(this.font, t, M_MINI_GRAS, espace);
        caps(g, this.font, t, M_MINI_GRAS, n(x + (w - tw) / 2), n(y + h / 2 - D_MINI), espace, couleur);
    }

    private void carteCamp(GuiGraphics g, float mx, float my) {
        float x = carteX(0), y = carteY(), w = carteW(), in = 9 * S;
        boolean hv = actifs() && dans(mx, my, x, y, w, CARTE_H);
        boutonTeinte(g, x, y, w, CARTE_H, VERT, hv ? 1 : 0, this.px);
        capsCoupees(g, Component.translatable("crac.mort.camp").getString(), M_MINI_GRAS, x + in, y + 10 * S - D_MINI, 0.8F * S, w - 2 * in,
            a(VERT_PALE, 255));
        ligne(g, Component.translatable("crac.mort.camp_aide").getString(), M_MOYEN, x + in, y + 24 * S - D_MOYEN, w - 2 * in, a(TEXTE, 255));
        ligne(g, Component.translatable("crac.mort.camp_info").getString(), M_MINI, x + in, y + 36 * S - D_MINI, w - 2 * in, a(ATTENUE, 255));
        boutonCarte(g, x + in, y + CARTE_H - 26 * S, w - 2 * in, 17 * S, Component.translatable("crac.mort.camp_bouton").getString(), -2, hv,
            actifs());
    }

    private void carteBase(GuiGraphics g, float mx, float my) {
        float x = carteX(1), y = carteY(), w = carteW(), in = 9 * S;
        boolean ouverte = baseOuverte();
        boolean hv = ouverte && actifs() && dans(mx, my, x, y, w, CARTE_H);
        if (ouverte) {
            boutonTeinte(g, x, y, w, CARTE_H, OR, hv ? 1 : 0, this.px);
        } else {
            rect(g, x, y, x + w, y + CARTE_H, a(BLANC, 8));
            contour(g, x, y, w, CARTE_H, this.px, a(BLANC, 30));
        }
        capsCoupees(g, Component.translatable("crac.mort.base").getString(), M_MINI_GRAS, x + in, y + 10 * S - D_MINI, 0.8F * S, w - 2 * in,
            a(ouverte ? OR : PALE, 255));
        ligne(g, Component.translatable("crac.mort.base_aide").getString(), M_MOYEN, x + in, y + 24 * S - D_MOYEN, w - 2 * in,
            a(ouverte ? TEXTE : GRIS, 255));
        String etat;
        int couleur;
        if (raison < 0) {
            etat = Component.translatable("crac.mort.verif").getString();
            couleur = ATTENUE;
        } else if (ouverte) {
            etat = Component.translatable("crac.mort.base_ok").getString();
            couleur = VERT;
        } else if (raison == 0 || raison == 4) {
            long s = (reste() + 999) / 1000;
            etat = Component.translatable("crac.mort.base_attente", String.format("%d:%02d", s / 60, s % 60)).getString();
            couleur = OR;
        } else {
            etat = Component.translatable("crac.mort.raison." + raison).getString();
            couleur = ROUGE_PALE;
        }
        ligne(g, etat, M_MINI, x + in, y + 36 * S - D_MINI, w - 2 * in, a(couleur, 255));
        boutonCarte(g, x + in, y + CARTE_H - 26 * S, w - 2 * in, 17 * S, Component.translatable("crac.mort.base_bouton").getString(), OR, hv,
            ouverte && actifs());
    }

    @Override
    protected boolean clic(double mx, double my, int bouton) {
        if (bouton != 0) {
            return false;
        }
        if (dans(mx, my, carteX(0), carteY(), carteW(), CARTE_H)) {
            reapparaitre(false);
            return true;
        }
        if (avecBase() && baseOuverte() && dans(mx, my, carteX(1), carteY(), carteW(), CARTE_H)) {
            reapparaitre(true);
            return true;
        }
        return false;
    }
}
