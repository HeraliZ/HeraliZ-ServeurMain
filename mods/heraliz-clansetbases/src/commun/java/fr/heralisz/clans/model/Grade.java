package fr.heralisz.clans.model;

import java.util.EnumSet;

/**
 * Grades d'un clan, du plus haut au plus bas. Le chef a tous les droits (non modifiables) ; les trois autres grades
 * ont un jeu de permissions par defaut que le chef peut changer.
 */
public enum Grade {
    CHEF, HAUT_GRADE, MEMBRE, RECRUE;

    /** Cle de traduction du libelle (grade.chef = « Chef »...). */
    public String libelle() {
        return "grade." + name().toLowerCase();
    }

    /** Grade juste au-dessus (null pour le chef : la promotion vers CHEF est un transfert). */
    public Grade superieur() {
        return this == CHEF || this == HAUT_GRADE ? null : values()[ordinal() - 1];
    }

    /** Grade juste en dessous (null pour la recrue). */
    public Grade inferieur() {
        return this == RECRUE ? null : values()[ordinal() + 1];
    }

    /** Vrai si ce grade est strictement au-dessus de l'autre. */
    public boolean domine(Grade autre) {
        return ordinal() < autre.ordinal();
    }

    /** Permissions par defaut du grade (le chef : toutes). */
    public EnumSet<Perm> permsParDefaut() {
        return switch (this) {
            case CHEF -> EnumSet.allOf(Perm.class);
            case HAUT_GRADE -> {
                EnumSet<Perm> s = EnumSet.allOf(Perm.class);
                s.remove(Perm.MODIFIER_NOM);
                s.remove(Perm.MODIFIER_TAG);
                yield s;
            }
            // base (BASES_SPEC §1) : membre = entrer, construire, casser, coffres, poser coffres, portes ; recrue = entrer, portes
            case MEMBRE -> EnumSet.of(Perm.BANQUE_DEPOSER, Perm.BANQUE_JOURNAL, Perm.CAPTURER, Perm.CHAT_CLAN,
                    Perm.BASE_ENTRER, Perm.BASE_CONSTRUIRE, Perm.BASE_CASSER, Perm.BASE_COFFRES, Perm.BASE_POSER_COFFRES, Perm.BASE_PORTES);
            case RECRUE -> EnumSet.of(Perm.BANQUE_DEPOSER, Perm.CAPTURER, Perm.CHAT_CLAN, Perm.BASE_ENTRER, Perm.BASE_PORTES);
        };
    }

    /** Les grades dont les permissions sont modifiables (tous sauf le chef). */
    public static Grade[] modifiables() {
        return new Grade[]{HAUT_GRADE, MEMBRE, RECRUE};
    }

    public static Grade parNom(String s, Grade def) {
        if (s == null) return def;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
