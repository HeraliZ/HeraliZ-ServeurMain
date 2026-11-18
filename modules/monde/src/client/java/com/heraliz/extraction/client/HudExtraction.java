package com.heraliz.extraction.client;

import com.heraliz.extraction.item.ExtractionTerminalItem;
import com.heraliz.extraction.reseau.Reseau;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.noyau.client.kit.Bandeau;
import fr.heraliz.noyau.client.kit.Formes;
import fr.heraliz.noyau.client.kit.Palette;
import fr.heraliz.noyau.client.kit.PoliceHud;
import fr.heraliz.noyau.client.kit.TexteHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Chrono d'extraction en haut au milieu de l'écran, version compacte (18/11, demande du propriétaire : « une UI plus propre
 * pendant qu'on s'extracte » ; maquette validée {@code maquettes/notifications/2_extraction_avant_apres.jpg}) : le même
 * verre que les bandeaux communs ({@link Bandeau}), 100 x 18 au lieu de 154 x 34. « Extraction » et le temps qui reste à
 * tenir, une barre fine (verte dans la zone, rouge au bord), puis l'aide (« Reste dans la zone », « Reviens dans la
 * zone ! ») et le temps avant la fermeture de la zone. Le serveur envoie le chrono toutes les 4 ticks
 * ({@link Reseau.Progression}) ; entre deux, il avance tout seul.
 */
public final class HudExtraction {
    private HudExtraction() {
    }

    private static final float LARGEUR_MIN = 100f, HAUTEUR = 18f, HAUT = 5f, MARGE = 4f;
    private static final int ROUGE_TEXTE = 0xFFB3AC;

    /** Textes à leur vraie taille (polices des bandeaux) ; recalculés seulement quand ils changent. */
    private static final TexteHud TITRE = new TexteHud(PoliceHud.BANDEAU_TITRE), TEMPS = new TexteHud(PoliceHud.MINI_GRAS),
            AIDE_DEDANS = new TexteHud(PoliceHud.PETIT), AIDE_DEHORS = new TexteHud(PoliceHud.PETIT), ZONE = new TexteHud(PoliceHud.PETIT);

    /** Dernier chrono reçu (null : aucun) et l'heure du jeu à sa réception. */
    @Nullable
    private static Reseau.Progression dernier;
    private static long recu;
    private static float apparition;
    private static long derniereImage;

    /** Chrono reçu du serveur ({@link Reseau#PROGRESSION_CLIENT}). */
    public static void recevoir(Reseau.Progression p) {
        Minecraft mc = Minecraft.getInstance();
        recu = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.aTenir() > 0) {
            dernier = p;
        } else {
            recu = Long.MIN_VALUE; // fin : la carte s'efface (la dernière valeur reste affichée pendant le fondu)
        }
    }

    /** Déconnexion : plus rien à afficher. */
    public static void vider() {
        dernier = null;
        apparition = 0;
    }

    public static void rendu(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Reseau.Progression p = dernier;
        long nanos = System.nanoTime();
        float dt = derniereImage == 0 ? 0 : Math.min(0.1F, (nanos - derniereImage) / 1e9F);
        derniereImage = nanos;
        if (p == null || mc.level == null || mc.player == null) {
            apparition = 0;
            return;
        }
        long maintenant = mc.level.getGameTime();
        // sans nouvelles depuis 2 s (zone déchargée, paquet perdu) : on efface aussi
        boolean actif = recu != Long.MIN_VALUE && maintenant - recu < 40;
        apparition = Mth.clamp(apparition + (actif ? dt * 4 : -dt * 4), 0, 1);
        if (apparition <= 0) {
            if (!actif) dernier = null;
            return;
        }
        if (mc.options.hideGui) return;
        float ecoule = actif ? Math.max(0, maintenant - recu + partial) : 0;
        float tenus = Math.min(p.aTenir(), p.tenus() + (p.dedans() ? ecoule : 0));
        float f = tenus / p.aTenir();
        int reste = Mth.ceil((p.aTenir() - tenus) / 20.0F);
        int zone = p.zone() < 0 ? -1 : Math.max(0, (int) ((p.zone() - ecoule) / 20));
        float ease = 1 - (1 - apparition) * (1 - apparition);

        TITRE.texte(Component.translatable("hud.heraliz_extraction.titre").getString());
        TEMPS.texte(ExtractionTerminalItem.formatTime(reste));
        AIDE_DEDANS.texte(Component.translatable(p.cielOuvert() ? "hud.heraliz_extraction.reste_ciel" : "hud.heraliz_extraction.reste").getString());
        AIDE_DEHORS.texte(Component.translatable("hud.heraliz_extraction.reviens").getString());
        ZONE.texte(zone < 0 ? "" : Component.translatable("hud.heraliz_extraction.zone", ExtractionTerminalItem.formatTime(zone)).getString());
        // largeur : la plus longue des deux aides (pas de saut quand on passe au bord), plus le temps de la zone
        float aide = Math.max(AIDE_DEDANS.largeur(), AIDE_DEHORS.largeur());
        float lw = Math.max(LARGEUR_MIN, Math.max(TITRE.largeur() + 8 + TEMPS.largeur(), aide + (ZONE.vide() ? 0 : 8 + ZONE.largeur())) + 2 * MARGE);
        float x = Formes.caler((w - lw) / 2f), y = Formes.caler(HAUT - (1 - ease) * 10);

        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = Formes.tampon(g);
        Formes.rect(vc, m, x, y, x + lw, y + HAUTEUR, Palette.argb(Palette.VERRE, Math.round(205 * ease)));
        Formes.bord(vc, m, x, y, lw, HAUTEUR, Formes.pixel(), Palette.argb(0xFFFFFF, Math.round(20 * ease)));
        // barre fine : verte dans la zone, rouge au bord
        float bx0 = x + MARGE, bx1 = x + lw - MARGE, by = y + 9.5f;
        Formes.rect(vc, m, bx0, by, bx1, by + 0.75f, Palette.argb(0xFFFFFF, Math.round(30 * ease)));
        Formes.rect(vc, m, bx0, by, bx0 + (bx1 - bx0) * Mth.clamp(f, 0, 1), by + 0.75f, Palette.argb(p.dedans() ? Bandeau.OK : Bandeau.DANGER, ease));
        g.flush();
        TITRE.ajouter(g, x + MARGE, y + 5.5f, Palette.argb(Bandeau.TEXTE, ease), TexteHud.GAUCHE, true);
        TEMPS.ajouter(g, x + lw - MARGE, y + 5.5f, Palette.argb(Bandeau.TEXTE, ease), TexteHud.DROITE, true);
        if (p.dedans()) AIDE_DEDANS.ajouter(g, x + MARGE, y + 13.75f, Palette.argb(Bandeau.ATTENUE, ease), TexteHud.GAUCHE, true);
        else AIDE_DEHORS.ajouter(g, x + MARGE, y + 13.75f, Palette.argb(ROUGE_TEXTE, ease), TexteHud.GAUCHE, true);
        if (!ZONE.vide()) ZONE.ajouter(g, x + lw - MARGE, y + 13.75f, Palette.argb(Bandeau.FAIBLE, ease), TexteHud.DROITE, true);
        g.flush();
    }
}
