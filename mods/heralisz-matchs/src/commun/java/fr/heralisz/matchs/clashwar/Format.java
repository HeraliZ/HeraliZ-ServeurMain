package fr.heralisz.matchs.clashwar;

import fr.heralisz.matchs.moteur.api.Carte;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Format d'une edition du ClashWar (MATCHS_SPEC §2.1) : nombre de joueurs par equipe et type de carte associe.
 */
public enum Format {
    UN_UN(1, "1v1", Carte.TYPE_CLASHWAR_1V1),
    DEUX_DEUX(2, "2v2", Carte.TYPE_CLASHWAR_2V2),
    TROIS_TROIS(3, "3v3", Carte.TYPE_CLASHWAR_3V3),
    QUATRE_QUATRE(4, "4v4", Carte.TYPE_CLASHWAR_4V4);

    /** Joueurs au plus par equipe (une equipe peut etre incomplete). */
    public final int taille;
    /** Nom court affiche et ecrit dans la configuration ("3v3"). */
    public final String nom;
    /** Type de carte {@code Carte.TYPE_CLASHWAR_*}. */
    public final String typeCarte;

    Format(int taille, String nom, String typeCarte) {
        this.taille = taille;
        this.nom = nom;
        this.typeCarte = typeCarte;
    }

    /** "3v3", "3", "TROIS_TROIS" (insensible a la casse) ; null si inconnu. */
    @Nullable
    public static Format parNom(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (Format f : values()) {
            if (f.nom.equals(t) || f.name().toLowerCase(Locale.ROOT).equals(t) || String.valueOf(f.taille).equals(t)) return f;
        }
        return null;
    }

    /** Format de cette taille, ou 2v2 par defaut. */
    public static Format deTaille(int taille) {
        for (Format f : values()) if (f.taille == taille) return f;
        return DEUX_DEUX;
    }
}
