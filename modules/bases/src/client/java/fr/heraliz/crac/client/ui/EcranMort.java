package fr.heraliz.crac.client.ui;

import fr.heraliz.crac.client.group.EcranGroupe;
import fr.heraliz.crac.client.kit.Ui;
import fr.heraliz.crac.network.C2SReapparition;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CReapparition;
import java.lang.reflect.Field;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
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
    private static final float W = 330, H = 160;
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
    private float carteX(int i) {
        return this.x0 + 13.33F + i * ((this.pw - 26.67F - 8) / 2 + 8);
    }

    private float carteW() {
        return avecBase() ? (this.pw - 26.67F - 8) / 2 : this.pw - 26.67F;
    }

    private float carteY() {
        return this.y0 + 46;
    }

    private static final float CARTE_H = 84;

    @Override
    protected void rendu(GuiGraphics g, float mx, float my, float pt) {
        fond(g);
        panneau(g, ACCENT);
        String sous = this.cause.getString();
        // en-tête sans « Échap fermer » ni croix : on ne quitte cet écran qu'en choisissant
        float x = this.x0 + 13.33F;
        float place = this.x0 + this.pw - 13.33F - x;
        caps(g, this.font, Component.translatable("crac.mort.titre").getString(), TITRE, x, this.y0 + 14 - 3.91F * 0.93F, 0.93F, 1.12F, a(BLANC, 255));
        if (!sous.isEmpty()) {
            texte(g, this.font, lit(Ui.ellipsis(this.font, sous, MINI, Math.round(place)), MINI), x, this.y0 + 28.17F - 4.21F, a(ATTENUE, 255));
        }
        section(g, Component.translatable("crac.mort.ou").getString(), this.x0 + 13.33F, this.y0 + 38, this.pw - 26.67F);
        carteCamp(g, mx, my);
        if (avecBase()) {
            carteBase(g, mx, my);
            // pied : délai partagé avec /home
            float py = this.y0 + this.ph - 14;
            texte(g, this.font, lit(Ui.ellipsis(this.font, Component.translatable("crac.mort.partage").getString(), MINI, Math.round(this.pw - 26.67F)), MINI),
                this.x0 + 13.33F, py - 4.21F, a(PALE, 255));
        }
    }

    private void carteCamp(GuiGraphics g, float mx, float my) {
        float x = carteX(0), y = carteY(), w = carteW();
        boolean hv = actifs() && dans(mx, my, x, y, w, CARTE_H);
        boutonTeinte(g, x, y, w, CARTE_H, VERT, hv ? 1 : 0, this.px);
        caps(g, this.font, Component.translatable("crac.mort.camp").getString(), MINI_GRAS, x + 9, y + 10 - 4.21F, 0.8F, a(VERT_PALE, 255));
        texte(g, this.font, lit(Component.translatable("crac.mort.camp_aide").getString(), MOYEN), x + 9, y + 24 - 4.7F, a(TEXTE, 255));
        texte(g, this.font, lit(Ui.ellipsis(this.font, Component.translatable("crac.mort.camp_info").getString(), MINI, Math.round(w - 18)), MINI),
            x + 9, y + 36 - 4.21F, a(ATTENUE, 255));
        grandBouton(g, x + 9, y + CARTE_H - 26, w - 18, 17, Component.translatable("crac.mort.camp_bouton").getString(), -2, hv, actifs());
    }

    private void carteBase(GuiGraphics g, float mx, float my) {
        float x = carteX(1), y = carteY(), w = carteW();
        boolean ouverte = baseOuverte();
        boolean hv = ouverte && actifs() && dans(mx, my, x, y, w, CARTE_H);
        if (ouverte) {
            boutonTeinte(g, x, y, w, CARTE_H, OR, hv ? 1 : 0, this.px);
        } else {
            rect(g, x, y, x + w, y + CARTE_H, a(BLANC, 8));
            contour(g, x, y, w, CARTE_H, this.px, a(BLANC, 30));
        }
        caps(g, this.font, Component.translatable("crac.mort.base").getString(), MINI_GRAS, x + 9, y + 10 - 4.21F, 0.8F, a(ouverte ? OR : PALE, 255));
        texte(g, this.font, lit(Component.translatable("crac.mort.base_aide").getString(), MOYEN), x + 9, y + 24 - 4.7F, a(ouverte ? TEXTE : GRIS, 255));
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
        texte(g, this.font, lit(Ui.ellipsis(this.font, etat, MINI, Math.round(w - 18)), MINI), x + 9, y + 36 - 4.21F, a(couleur, 255));
        grandBouton(g, x + 9, y + CARTE_H - 26, w - 18, 17, Component.translatable("crac.mort.base_bouton").getString(), OR, hv, ouverte && actifs());
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
