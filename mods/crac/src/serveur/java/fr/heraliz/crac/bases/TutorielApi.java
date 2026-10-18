package fr.heraliz.crac.bases;

import net.minecraft.server.level.ServerPlayer;

/**
 * API du tutoriel des bases pour nos autres mods, lue PAR RÉFLEXION (HeraliZ Quêtes : le Commandant Varga lance le
 * tutoriel et le guide à la radio). Ne jamais renommer cette classe ni changer ses signatures. Côté serveur seulement.
 */
public final class TutorielApi {
    private TutorielApi() {
    }

    /** -1 : pas de tutoriel ici (réglage coupé, monde des bases absent) ; 0 : à faire ; 1 : kit reçu ; 2 : en cours ; 3 : fini. */
    public static int etat(ServerPlayer p) {
        if (!BasesReglages.b(BasesReglages.TUTO_ACTIF, true) || p.server.getLevel(MondeBases.BASES) == null) {
            return -1;
        }
        BasesDonnees.Joueur j = BasesDonnees.get(p.server).siConnu(p.getUUID());
        if (j == null) {
            return Tutoriel.aUneBase(p) ? 3 : 0;
        }
        return switch (j.etape) {
            case AUCUNE -> Tutoriel.aUneBase(p) ? 3 : 0;
            case KIT -> 1;
            case PHASE -> 2;
            case FINI -> 3;
        };
    }

    /**
     * Étape de la cabane en cours : {étape (1 à 6, 7 = tout est fait), fait, total} ; {0, 0, 0} hors du monde des bases
     * ou avant l'arrivée. 1 : un coin plat ; 2 : les fondations ; 3 : murs, porte, planchers ; 4 : le Claim Block ;
     * 5 : tout en bois ; 6 : l'entretien.
     */
    public static int[] progression(ServerPlayer p) {
        return Tutoriel.progression(p);
    }

    /** Lance le tutoriel (titres, kit, envoi dans le monde des bases) d'un joueur qui ne l'a pas fait ; false sinon. */
    public static boolean lancer(ServerPlayer p) {
        if (etat(p) != 0) {
            return false;
        }
        Tutoriel.lancer(p);
        return true;
    }
}
