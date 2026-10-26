package fr.heralisz.clans.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Permissions attribuables aux grades (et reglables membre par membre, voir {@link Etat}). Le masque reseau et JSON
 * utilise bit = ordinal : une nouvelle permission s'ajoute TOUJOURS en fin d'enum. L'ordre d'affichage de l'onglet
 * Permissions est {@link #ORDRE} (groupes {@link Groupe}).
 */
public enum Perm {
    INVITER, EXCLURE, PROMOUVOIR, RETROGRADER,
    BANQUE_DEPOSER, BANQUE_RETIRER, BANQUE_JOURNAL,
    MODIFIER_NOM, MODIFIER_TAG, MODIFIER_DESCRIPTION, MODIFIER_BANNIERE,
    CAPTURER, RAVITAILLEMENT, CHAT_CLAN,
    // --- ajoutees en dernier (ordinaux et masques existants conserves) : HeraliZ Bases (BASES_SPEC §1) et Matchs
    /** Entrer dans la base du clan. */
    BASE_ENTRER,
    /** Poser des blocs dans la base. */
    BASE_CONSTRUIRE,
    /** Casser des blocs dans la base. */
    BASE_CASSER,
    /** Ouvrir les conteneurs de la base. */
    BASE_COFFRES,
    /** Poser des conteneurs dans la base. */
    BASE_POSER_COFFRES,
    /** Portes, trappes, portillons, leviers, boutons. */
    BASE_PORTES,
    /** Creer la base, debloquer des salles. */
    BASE_GERER,
    /** Inscrire le clan au ClashWar (HeraliZ Matchs). */
    CLASHWAR_INSCRIRE,
    /**
     * Regler les permissions individuelles des membres de grade strictement inferieur (onglet Permissions › PAR
     * JOUEUR), sans pouvoir accorder une permission qu'on n'a pas. Defaut : chef et haut grade.
     */
    GERER_PERMISSIONS,
    /**
     * Se téléporter à la base du clan (25/10) : /home de CRac et réapparition à la base après une mort (même délai
     * partagé). Défaut : chef, haut grade et membre ; pas la recrue.
     */
    BASE_TP;

    /** Reglage individuel d'une permission pour un membre : comme son grade, autorisee ou refusee. */
    public enum Etat {
        /** Comme son grade (aucun reglage individuel). */
        HERITE,
        /** Autorisee pour ce membre, quel que soit son grade. */
        AUTORISE,
        /** Refusee pour ce membre, quel que soit son grade. */
        REFUSE;

        /** Cle de traduction courte (gui.permissions.etat.herite...). */
        public String libelle() {
            return "gui.permissions.etat." + name().toLowerCase(Locale.ROOT);
        }

        /** Lecture tolerante (nom insensible a la casse) ; null si inconnu. */
        public static Etat parNom(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        public static Etat parOrdinal(int i) {
            Etat[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Groupes de l'onglet Permissions (titres « CLAN », « BASE », « CLASHWAR »). */
    public enum Groupe {
        CLAN, BASE, CLASHWAR;

        /** Cle de traduction du titre (gui.permissions.groupe.clan...). */
        public String libelle() {
            return "gui.permissions.groupe." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Permissions de la base (groupe « BASE » de l'onglet Permissions). */
    public static final java.util.Set<Perm> BASE = java.util.Collections.unmodifiableSet(EnumSet.of(BASE_ENTRER, BASE_CONSTRUIRE,
            BASE_CASSER, BASE_COFFRES, BASE_POSER_COFFRES, BASE_PORTES, BASE_GERER, BASE_TP));

    /**
     * Permissions connues avant l'ajout des permissions de base et de ClashWar : un clan sauvegarde sans la liste
     * « permsConnues » n'avait que celles-ci, il recoit les defauts de son grade pour toutes les autres (migration).
     */
    public static final java.util.Set<Perm> HISTORIQUE_V1 = java.util.Collections.unmodifiableSet(EnumSet.range(INVITER, CHAT_CLAN));

    /**
     * Ordre d'affichage, groupe par groupe : CLAN (gestion des membres, banque, identite, jeu), BASE, CLASHWAR.
     * Contient chaque permission une fois.
     */
    public static final List<Perm> ORDRE = List.of(
            INVITER, EXCLURE, PROMOUVOIR, RETROGRADER, GERER_PERMISSIONS,
            BANQUE_DEPOSER, BANQUE_RETIRER, BANQUE_JOURNAL,
            MODIFIER_NOM, MODIFIER_TAG, MODIFIER_DESCRIPTION, MODIFIER_BANNIERE,
            CAPTURER, RAVITAILLEMENT, CHAT_CLAN,
            BASE_ENTRER, BASE_CONSTRUIRE, BASE_CASSER, BASE_COFFRES, BASE_POSER_COFFRES, BASE_PORTES, BASE_TP, BASE_GERER,
            CLASHWAR_INSCRIRE);

    /** Masque de toutes les permissions connues (bits hors de ce masque ignores a la lecture). */
    public static final int TOUTES = (1 << values().length) - 1;

    /** Vrai pour une permission de la base (groupe « BASE »). */
    public boolean estBase() {
        return BASE.contains(this);
    }

    /** Groupe d'affichage. */
    public Groupe groupe() {
        if (estBase()) return Groupe.BASE;
        return this == CLASHWAR_INSCRIRE ? Groupe.CLASHWAR : Groupe.CLAN;
    }

    /**
     * Sous-groupe dans le groupe CLAN (0 gestion des membres, 1 banque, 2 identite, 3 jeu ; 0 pour les autres
     * groupes) : un petit espace les separe a l'ecran.
     */
    public int sousGroupe() {
        return switch (this) {
            case INVITER, EXCLURE, PROMOUVOIR, RETROGRADER, GERER_PERMISSIONS -> 0;
            case BANQUE_DEPOSER, BANQUE_RETIRER, BANQUE_JOURNAL -> 1;
            case MODIFIER_NOM, MODIFIER_TAG, MODIFIER_DESCRIPTION, MODIFIER_BANNIERE -> 2;
            case CAPTURER, RAVITAILLEMENT, CHAT_CLAN -> 3;
            default -> 0;
        };
    }

    /** Cle de traduction du libelle (perm.inviter = « Inviter des joueurs »...). */
    public String libelle() {
        return "perm." + name().toLowerCase(Locale.ROOT);
    }

    /** Bit de la permission dans un masque. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Masque de bits d'un jeu de permissions (reseau, JSON). */
    public static int masque(EnumSet<Perm> perms) {
        int m = 0;
        for (Perm p : perms) m |= 1 << p.ordinal();
        return m;
    }

    public static EnumSet<Perm> depuisMasque(int masque) {
        EnumSet<Perm> s = EnumSet.noneOf(Perm.class);
        for (Perm p : values()) if ((masque & (1 << p.ordinal())) != 0) s.add(p);
        return s;
    }

    public static Perm parNom(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Permission par ordinal (reseau) ; null hors bornes. */
    public static Perm parOrdinal(int i) {
        Perm[] v = values();
        return i < 0 || i >= v.length ? null : v[i];
    }
}
