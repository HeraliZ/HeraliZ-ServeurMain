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
        if (!BasesReglages.b(BasesReglages.TUTO_ACTIF, true) || p.server.getLevel(MondeBases.TUTO) == null) {
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
     * Étape de la cabane en cours : {étape (1 à 9, 10 = tout est fait), fait, total} ; {0, 0, 0} hors du monde du tutoriel
     * ou avant l'arrivée. 1 : prendre le plan ; 2 : la 1re fondation ; 3 : la 2e ; 4 : les murs et la porte ; 5 : le toit ;
     * 6 : tout en bois ; 7 : le Claim Block ; 8 : l'entretien ; 9 : la caisse en métal (plus de home depuis 1.16.0).
     */
    public static int[] progression(ServerPlayer p) {
        return Tutoriel.progression(p);
    }

    /** Nombre d'étapes de la cabane (9 depuis 1.16.0). */
    public static int etapes() {
        return Tutoriel.ETAPES;
    }

    /** Jours de protection de la première base contre les raids ({@code [protection] jours}, 4 aujourd'hui), arrondis. */
    public static int joursProtection() {
        return (int) Math.round(BasesReglages.d(BasesReglages.PROTECTION_JOURS, 4.0));
    }

    /** Varga a posé le sas (2e porte) devant la cabane à la fin du tutoriel de ce joueur (depuis le démarrage du serveur). */
    public static boolean sas(ServerPlayer p) {
        return Tutoriel.SAS.contains(p.getUUID());
    }

    /** Lance le tutoriel (titres, kit, envoi dans le monde du tutoriel) d'un joueur qui ne l'a pas fait ; false sinon. */
    public static boolean lancer(ServerPlayer p) {
        if (etat(p) != 0) {
            return false;
        }
        Tutoriel.lancer(p);
        return true;
    }

    /**
     * Un clan vient d'être créé (CRac 1.16.0, appelé par les bases des clans) : son chef fait le tutoriel des bases dans le
     * monde à part s'il ne l'a jamais fait ; sa cabane d'essai disparaît à la fin.
     */
    public static void clanCree(ServerPlayer chef) {
        BasesDonnees d = BasesDonnees.get(chef.server);
        BasesDonnees.Joueur j = d.siConnu(chef.getUUID());
        if (j != null && j.etape != BasesDonnees.Etape.AUCUNE || etat(chef) != 0 || Tutoriel.enCours(chef)) {
            return;
        }
        // 1.21.0 : seulement au spawn, hors combat, debout et hors match (créer un clan téléportait n'importe où, même en
        // plein combat ou en duel) ; sinon il part à son prochain passage au spawn (Tutoriel.seconde)
        if (!peutPartir(chef)) {
            d.joueur(chef.getUUID()).tutoClan = true;
            d.marquer();
            T.chat(chef, T.t("crac.tuto.clan_plus_tard", "[Tuto] Ton clan est créé ! Retourne au spawn : le Commandant Varga t'y montrera comment bâtir une base."),
                net.minecraft.ChatFormatting.GOLD);
            return;
        }
        T.chat(chef, T.t("crac.tuto.clan", "[Tuto] Ton clan est créé ! Le Commandant Varga va te montrer comment bâtir une base."),
            net.minecraft.ChatFormatting.GOLD);
        Tutoriel.lancer(chef);
    }

    /** Ni en combat, ni à terre, en vie : le tutoriel peut l'emmener. */
    static boolean libre(ServerPlayer p) {
        return p.isAlive() && fr.heraliz.crac.heraliz.Heraliz.combatSecondsLeft(p) <= 0
            && !fr.heraliz.crac.heraliz.SaisonPont.aTerre(p);
    }

    /** Au spawn et {@link #libre} : le tutoriel dû à la création du clan peut partir. */
    static boolean peutPartir(ServerPlayer p) {
        return Deplacements.auSpawn(p) && libre(p);
    }

    /** Ouvre la fiche technique des bases ({@code /base info}) chez ce joueur (Quêtes : après la présentation du Bâtisseur). */
    public static void ficheBase(ServerPlayer p) {
        FicheBase.envoyer(p);
    }
}
