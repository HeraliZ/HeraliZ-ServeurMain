package fr.heraliz.noyau.client.kit;

import fr.heraliz.noyau.HeralizNoyau;
import net.minecraft.resources.ResourceLocation;

/**
 * Polices des HUD en jeu (refonte du 25/10, docs/HUD_REFONTE.md), toujours dessinées à leur vraie taille (jamais de
 * {@code pose().scale} sur un texte) : Oswald Bold condensée pour les titres et les chiffres « militaires », DM Sans pour
 * les textes, Onest en secours pour le cyrillique (fichiers {@code assets/heraliz_noyau/font/hud_*.json}, licences OFL à
 * côté). Tailles fixes de la charte : 6,5 / 8 / 11 / 16 unités d'interface (taille de l'em).
 * <p>Dans le fichier .json, « size » = em x (ascendante - descendante) / unités par em (Oswald 1,482, DM Sans 1,302) ;
 * Onest y est réglé pour avoir la même ligne de base (DM Sans : même taille ; Oswald : x 1,0581). Placement vertical
 * (Minecraft 1.20.1, police TTF sans décalage) : ligne de base = point de dessin + em x ascendante / 1000 - 3. Les HUD
 * posent leurs textes sur le milieu des capitales ({@link #milieu} sous le point de dessin) ou sur la ligne de base
 * ({@link #base}). Mêmes calculs dans {@code modules/noyau/outils/apercu_hud.py}.
 */
public final class PoliceHud {
    /** Métriques des fichiers (pour 1000 unités par em) : ascendante (table hhea) et hauteur des capitales. */
    private static final int OSWALD_ASC = 1193, OSWALD_CAP = 810, DMSANS_ASC = 992, DMSANS_CAP = 700;

    /** Oswald Bold 11 : titres (« À TERRE »). */
    public static final PoliceHud TITRE = new PoliceHud("hud_titre", 11f, OSWALD_ASC, OSWALD_CAP);
    /** Oswald Bold 16 : grands chiffres (temps avant la mort). */
    public static final PoliceHud CHIFFRE = new PoliceHud("hud_chiffre", 16f, OSWALD_ASC, OSWALD_CAP);
    /** Oswald Bold 8 : petits chiffres (temps au centre d'un anneau, pourcentage d'usure). */
    public static final PoliceHud CHIFFRE_PETIT = new PoliceHud("hud_chiffre_petit", 8f, OSWALD_ASC, OSWALD_CAP);
    /** DM Sans 8, normal et gras : lignes d'état, noms. */
    public static final PoliceHud TEXTE = new PoliceHud("hud_texte", 8f, DMSANS_ASC, DMSANS_CAP);
    public static final PoliceHud TEXTE_GRAS = new PoliceHud("hud_texte_gras", 8f, DMSANS_ASC, DMSANS_CAP);
    /** DM Sans 6,5, normal et gras : aides, légendes, touches, pastilles. */
    public static final PoliceHud MINI = new PoliceHud("hud_mini", 6.5f, DMSANS_ASC, DMSANS_CAP);
    public static final PoliceHud MINI_GRAS = new PoliceHud("hud_mini_gras", 6.5f, DMSANS_ASC, DMSANS_CAP);

    /** Police du jeu ({@code heraliz_noyau:hud_*}). */
    public final ResourceLocation id;
    /** Taille de l'em, en unités d'interface. */
    public final float em;
    /** Ligne de base sous le point de dessin, hauteur des capitales, milieu des capitales sous le point de dessin. */
    public final float base, cap, milieu;

    private PoliceHud(String nom, float em, int ascendante, int capitales) {
        this.id = HeralizNoyau.id(nom);
        this.em = em;
        this.base = em * ascendante / 1000f - 3f;
        this.cap = em * capitales / 1000f;
        this.milieu = base - cap / 2f;
    }
}
