package fr.heralisz.clans.client.gui.perms;

import fr.heralisz.clans.client.gui.kit.Icons;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Perm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Aides de dessin des ecrans de permissions : lignes groupees (CLAN, BASE, CLASHWAR) dans l'ordre {@link Perm#ORDRE},
 * pastilles a trois etats, bascule segmentee, tetes des joueurs. Rien n'est alloue par image hors des textes.
 */
public final class PermsUi {
    private PermsUi() {
    }

    /** Taille des pastilles (interrupteurs de grade et etats des joueurs). */
    public static final int PASTILLE = 9;
    /** Hauteur d'un titre de groupe, d'une ligne de permission, et espace entre deux sous-groupes de CLAN. */
    public static final int H_TITRE = 18, H_LIGNE = 14, ECART_SOUS = 4, ECART_GROUPE = 4;

    /** Une ligne de liste : titre de groupe (perm null) ou permission ; y relatif au haut du contenu. */
    public record Ligne(@Nullable Perm perm, Perm.Groupe groupe, int y, int h) {
    }

    /** Lignes de la liste (titres de groupes compris), filtrees sur la base si demande. */
    public static List<Ligne> lignes(boolean seulementBase) {
        List<Ligne> l = new ArrayList<>();
        int y = 0, sous = -1;
        Perm.Groupe groupe = null;
        for (Perm p : Perm.ORDRE) {
            if (seulementBase && !p.estBase()) continue;
            if (p.groupe() != groupe) {
                if (groupe != null) y += ECART_GROUPE;
                groupe = p.groupe();
                sous = p.sousGroupe();
                l.add(new Ligne(null, groupe, y, H_TITRE));
                y += H_TITRE;
            } else if (p.sousGroupe() != sous) {
                sous = p.sousGroupe();
                y += ECART_SOUS;
            }
            l.add(new Ligne(p, groupe, y, H_LIGNE));
            y += H_LIGNE;
        }
        return l;
    }

    /** Point dans le rectangle. */
    public static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Hauteur totale des lignes. */
    public static int hauteur(List<Ligne> l) {
        if (l.isEmpty()) return 0;
        Ligne d = l.get(l.size() - 1);
        return d.y() + d.h();
    }

    /** Titre de groupe (« CLAN », « BASE », « CLASHWAR ») avec un filet qui s'efface. */
    public static void titre(GuiGraphics g, Font font, Perm.Groupe groupe, int x, int y, int w) {
        Ui.section(g, font, Component.translatable(groupe.libelle()).getString(), x, y + 6, w);
    }

    /** Couleur d'un grade : chef dore, haut grade accent, membre texte, recrue attenue. */
    public static int couleurGrade(Grade g, int accent) {
        return switch (g) {
            case CHEF -> Ui.OR;
            case HAUT_GRADE -> accent;
            case MEMBRE -> Ui.TEXT;
            case RECRUE -> Ui.MUTED;
        };
    }

    /**
     * Pastille d'un etat (colonne HERITE, AUTORISE ou REFUSE) centree sur cx. HERITE montre toujours la valeur du grade
     * en gris (pleine = le grade l'a, creuse = non), nette quand elle s'applique, estompee quand un reglage personnel
     * la remplace ; pas d'anneau : c'est l'etat par defaut de presque toutes les lignes, il doit rester calme.
     * AUTORISE (verte) et REFUSE (rouge) actives sont pleines et cerclees d'un anneau : un reglage personnel ressort.
     * Une pastille non permise est estompee et sans survol.
     */
    public static void etat(GuiGraphics g, int cx, int y, Perm.Etat colonne, boolean actif, boolean parLeGrade, boolean survol, boolean permis) {
        int x = cx - PASTILLE / 2, t = PASTILLE;
        float k = permis ? 1f : 0.4f;
        boolean hover = survol && permis && !actif;
        if (colonne == Perm.Etat.HERITE) {
            int fill = parLeGrade ? (actif ? 140 : 40) : (hover ? 22 : 0);
            int edge = actif ? (parLeGrade ? 175 : 140) : hover ? 120 : 45;
            if (fill > 0) Ui.soft(g, x, y, t, t, Ui.argb(0xFFFFFF, (int) (fill * k)));
            Ui.softOutline(g, x + 1, y + 1, t - 2, t - 2, Ui.argb(0xFFFFFF, (int) (edge * k)));
            return;
        }
        int rgb = colonne == Perm.Etat.AUTORISE ? Ui.OK : Ui.DANGER;
        if (actif) {
            Ui.soft(g, x, y, t, t, Ui.argb(rgb, (int) (225 * k)));
            Ui.softOutline(g, x + 1, y + 1, t - 2, t - 2, Ui.argb(Ui.mix(rgb, 0xFFFFFF, 0.35f), (int) (200 * k)));
            Ui.softOutline(g, x - 1, y - 1, t + 2, t + 2, Ui.argb(rgb, (int) (170 * k)));
        } else {
            Ui.soft(g, x, y, t, t, Ui.argb(hover ? rgb : 0xFFFFFF, (int) ((hover ? 40 : 8) * k)));
            Ui.softOutline(g, x + 1, y + 1, t - 2, t - 2, Ui.argb(hover ? rgb : 0xFFFFFF, (int) ((hover ? 170 : 40) * k)));
        }
    }

    // ------------------------------------------------------------------ bascule segmentee (PAR GRADE / PAR JOUEUR)
    /** Hauteur de la bascule. */
    public static final int H_BASCULE = 13;

    /** Largeurs des segments (libelle en capitales espacees + 16). */
    public static int[] largeurs(Font font, String... libelles) {
        int[] w = new int[libelles.length];
        for (int i = 0; i < libelles.length; i++) {
            w[i] = (int) Ui.spacedWidth(font, libelles[i].toUpperCase(Locale.ROOT), Ui.SMALL, 0.8f) + 16;
        }
        return w;
    }

    /** Dessine la bascule en (x, y) : segment actif teinte d'accent, survol eclairci. Rend la largeur totale. */
    public static int bascule(GuiGraphics g, Font font, int x, int y, String[] libelles, int[] largeurs, int actif, int survol, int accent) {
        int total = 0;
        for (int w : largeurs) total += w;
        Ui.soft(g, x, y, total, H_BASCULE, Ui.argb(0xFFFFFF, 7));
        Ui.softOutline(g, x + 1, y + 1, total - 2, H_BASCULE - 2, Ui.argb(0xFFFFFF, 22));
        int sx = x;
        for (int i = 0; i < libelles.length; i++) {
            int w = largeurs[i];
            if (i == actif) {
                Ui.soft(g, sx + 1, y + 1, w - 2, H_BASCULE - 2, Ui.argb(accent, 150));
            } else if (i == survol) {
                Ui.soft(g, sx + 1, y + 1, w - 2, H_BASCULE - 2, Ui.argb(0xFFFFFF, 16));
            }
            int c = i == actif ? Ui.argb(0xFFFFFF, 255) : i == survol ? Ui.argb(Ui.TEXT, 255) : Ui.argb(Ui.MUTED, 255);
            Ui.spacedCentered(g, font, libelles[i], Ui.SMALL, sx + w / 2f, y + 3.5f, c, 0.8f);
            sx += w;
        }
        return total;
    }

    /** Segment sous la souris (-1 sinon). */
    public static int segmentSous(double mx, double my, int x, int y, int[] largeurs) {
        if (my < y || my >= y + H_BASCULE) return -1;
        int sx = x;
        for (int i = 0; i < largeurs.length; i++) {
            if (mx >= sx && mx < sx + largeurs[i]) return i;
            sx += largeurs[i];
        }
        return -1;
    }

    // ------------------------------------------------------------------ tetes
    /** Peau d'un joueur : la vraie s'il est dans la liste des joueurs du serveur, sinon celle par defaut de son UUID. */
    public static ResourceLocation peau(UUID u) {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        PlayerInfo info = c == null ? null : c.getPlayerInfo(u);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(u);
    }

    /** Tete du joueur (visage + calque du chapeau), carree de la taille donnee. */
    public static void tete(GuiGraphics g, UUID u, int x, int y, int taille) {
        PlayerFaceRenderer.draw(g, peau(u), x, y, taille);
    }

    /** Petite icone de cadenas attenuee (lecture seule). */
    public static void cadenas(GuiGraphics g, int x, int y, int rgb, int alpha) {
        Icons.draw(g, Icons.CADENAS, x, y, 8, rgb, alpha);
    }
}
