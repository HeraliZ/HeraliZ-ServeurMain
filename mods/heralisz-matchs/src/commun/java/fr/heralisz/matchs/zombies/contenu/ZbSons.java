package fr.heralisz.matchs.zombies.contenu;

import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mode zombies ({@code heralisz_matchs:zb_*}), tous generes par {@code tools/matchs/gen_sons_zombies.py}
 * (synthese, melodies originales ; aucun son extrait d'un jeu). Les identifiants de
 * {@code ZombiesApi.Sons} (joues par le jeu) sont enregistres ici ; s'y ajoutent les voix des creatures, les jingles des
 * machines, l'ambiance en boucle et la sortie de terre (joues par le contenu).
 */
public final class ZbSons {
    private ZbSons() {
    }

    public static final DeferredRegister<SoundEvent> SONS = DeferredRegister.create(Registries.SOUND_EVENT, HeraliszMatchs.MODID);

    private static RegistryObject<SoundEvent> son(String id) {
        return SONS.register(id, () -> SoundEvent.createVariableRangeEvent(HeraliszMatchs.id(id)));
    }

    // ------------------------------------------------------------------ joues par le jeu (ZombiesApi.Sons)
    public static final RegistryObject<SoundEvent> MANCHE_DEBUT = son("zb_manche_debut");
    public static final RegistryObject<SoundEvent> MANCHE_FIN = son("zb_manche_fin");
    public static final RegistryObject<SoundEvent> SPECIALE_DEBUT = son("zb_speciale_debut");
    public static final RegistryObject<SoundEvent> PARTIE_FIN = son("zb_partie_fin");
    public static final RegistryObject<SoundEvent> ACHAT = son("zb_achat");
    public static final RegistryObject<SoundEvent> REFUS = son("zb_refus");
    public static final RegistryObject<SoundEvent> PORTE = son("zb_porte");
    public static final RegistryObject<SoundEvent> EBOULIS = son("zb_eboulis");
    public static final RegistryObject<SoundEvent> PLANCHE_POSE = son("zb_planche_pose");
    public static final RegistryObject<SoundEvent> PLANCHE_ARRACHE = son("zb_planche_arrache");
    public static final RegistryObject<SoundEvent> BOITE_ROULETTE = son("zb_boite_roulette");
    public static final RegistryObject<SoundEvent> BOITE_PELUCHE = son("zb_boite_peluche");
    public static final RegistryObject<SoundEvent> BOITE_ARRIVEE = son("zb_boite_arrivee");
    public static final RegistryObject<SoundEvent> ARME_PRISE = son("zb_arme_prise");
    public static final RegistryObject<SoundEvent> BONUS_APPARITION = son("zb_bonus_apparition");
    public static final RegistryObject<SoundEvent> BONUS_PRIS = son("zb_bonus_pris");
    public static final RegistryObject<SoundEvent> BONUS_FIN = son("zb_bonus_fin");
    public static final RegistryObject<SoundEvent> ATOMIQUE = son("zb_atomique");
    public static final RegistryObject<SoundEvent> ATOUT_BU = son("zb_atout_bu");
    public static final RegistryObject<SoundEvent> COURANT = son("zb_courant");
    public static final RegistryObject<SoundEvent> A_TERRE = son("zb_a_terre");
    public static final RegistryObject<SoundEvent> REANIME = son("zb_reanime");
    public static final RegistryObject<SoundEvent> MORT = son("zb_mort");
    // ajouts v2 du jeu (ZombiesApi.Sons)
    public static final RegistryObject<SoundEvent> PAP_AMELIORATION = son("zb_pap_amelioration");
    public static final RegistryObject<SoundEvent> PAP_PRETE = son("zb_pap_prete");
    public static final RegistryObject<SoundEvent> ATELIER = son("zb_atelier");
    public static final RegistryObject<SoundEvent> MATERIAU = son("zb_materiau");
    public static final RegistryObject<SoundEvent> EASTER_EGG_ETAPE = son("zb_easter_egg_etape");
    public static final RegistryObject<SoundEvent> EASTER_EGG_FIN = son("zb_easter_egg_fin");
    public static final RegistryObject<SoundEvent> OBJECTIF = son("zb_objectif");
    public static final RegistryObject<SoundEvent> GRIFFE_FENETRE = son("zb_griffe_fenetre");

    // ------------------------------------------------------------------ joues par le contenu
    /** Ambiance du laboratoire (boucle de 32 s en flux, jouee par le client pendant une partie). */
    public static final RegistryObject<SoundEvent> AMBIANCE = son("zb_ambiance");
    public static final RegistryObject<SoundEvent> ZOMBIE_GROGNE = son("zb_zombie_grogne");
    public static final RegistryObject<SoundEvent> ZOMBIE_CRI = son("zb_zombie_cri");
    public static final RegistryObject<SoundEvent> ZOMBIE_ATTAQUE = son("zb_zombie_attaque");
    public static final RegistryObject<SoundEvent> ZOMBIE_TOUCHE = son("zb_zombie_touche");
    public static final RegistryObject<SoundEvent> ZOMBIE_MORT = son("zb_zombie_mort");
    public static final RegistryObject<SoundEvent> ZOMBIE_PAS = son("zb_zombie_pas");
    public static final RegistryObject<SoundEvent> RODEUR_GROGNE = son("zb_rodeur_grogne");
    public static final RegistryObject<SoundEvent> RODEUR_ATTAQUE = son("zb_rodeur_attaque");
    public static final RegistryObject<SoundEvent> RODEUR_MORT = son("zb_rodeur_mort");
    public static final RegistryObject<SoundEvent> RODEUR_APPARITION = son("zb_rodeur_apparition");
    public static final RegistryObject<SoundEvent> SORTIE_TERRE = son("zb_sortie_terre");
    public static final RegistryObject<SoundEvent> MACHINE_BLINDAGE = son("zb_machine_blindage");
    public static final RegistryObject<SoundEvent> MACHINE_REANIMATION = son("zb_machine_reanimation");
    public static final RegistryObject<SoundEvent> MACHINE_VIGUEUR = son("zb_machine_vigueur");
    public static final RegistryObject<SoundEvent> MACHINE_DOUBLE_IMPACT = son("zb_machine_double_impact");
    public static final RegistryObject<SoundEvent> MACHINE_RECHARGEMENT = son("zb_machine_rechargement");
    public static final RegistryObject<SoundEvent> MACHINE_ALLUMAGE = son("zb_machine_allumage");

    // ------------------------------------------------------------------ zombies v2 (contenu)
    /** Machine d'atout qui sert : declic, bouteille qui tombe dans le distributeur. */
    public static final RegistryObject<SoundEvent> MACHINE_SERVIR = son("zb_machine_servir");
    /** Animation de boisson (1re personne, joues par le client) : capsule, gorgees, rot, bouteille jetee. */
    public static final RegistryObject<SoundEvent> BOISSON_DECAPSULE = son("zb_boisson_decapsule");
    public static final RegistryObject<SoundEvent> BOISSON_GORGEE = son("zb_boisson_gorgee");
    public static final RegistryObject<SoundEvent> BOISSON_ROT = son("zb_boisson_rot");
    public static final RegistryObject<SoundEvent> BOISSON_VERRE = son("zb_boisson_verre");
    /** Pack-a-Punch : jingle de la machine sous tension (joue par le contenu, de temps en temps). */
    public static final RegistryObject<SoundEvent> PAP_JINGLE = son("zb_pap_jingle");
    /** Grille : chaine qui casse, cadenas qui tombe (en plus du son de porte du jeu). */
    public static final RegistryObject<SoundEvent> GRILLE = son("zb_grille");
    /** Zombie qui enjambe une fenetre (appui, raclement, reception). */
    public static final RegistryObject<SoundEvent> ZOMBIE_ENJAMBE = son("zb_zombie_enjambe");

    // v3 : machines et quete de The Giant (sons de synthese, tools/matchs/zombies/gen_sons_v3.py)
    public static final RegistryObject<SoundEvent> MACHINE_MULE_KICK = son("zb_machine_mule_kick");
    public static final RegistryObject<SoundEvent> MACHINE_DEADSHOT = son("zb_machine_deadshot");
    public static final RegistryObject<SoundEvent> TELEPORTEUR = son("zb_teleporteur");
    public static final RegistryObject<SoundEvent> TELEPORTEUR_ARRIVEE = son("zb_teleporteur_arrivee");
    public static final RegistryObject<SoundEvent> FUSIBLE_RAMASSE = son("zb_fusible_ramasse");
    public static final RegistryObject<SoundEvent> FUSIBLE = son("zb_fusible");
    public static final RegistryObject<SoundEvent> LEVIER_COURANT = son("zb_levier_courant");
    public static final RegistryObject<SoundEvent> AME = son("zb_ame");
    public static final RegistryObject<SoundEvent> CUVE_PLEINE = son("zb_cuve_pleine");
    public static final RegistryObject<SoundEvent> LIAISON_DEBUT = son("zb_liaison_debut");
    public static final RegistryObject<SoundEvent> LIAISON_TIC = son("zb_liaison_tic");
    public static final RegistryObject<SoundEvent> LIAISON_OK = son("zb_liaison_ok");
    public static final RegistryObject<SoundEvent> RIDEAU = son("zb_rideau");
    public static final RegistryObject<SoundEvent> TOUR_ECLAIRS = son("zb_tour_eclairs");
    public static final RegistryObject<SoundEvent> PIEGE = son("zb_piege");
    public static final RegistryObject<SoundEvent> PIEGE_MORT = son("zb_piege_mort");
    public static final RegistryObject<SoundEvent> ECLAIR = son("zb_eclair");
    public static final RegistryObject<SoundEvent> BOWIE = son("zb_bowie");
    public static final RegistryObject<SoundEvent> QUETE_OBJET = son("zb_quete_objet");
    public static final RegistryObject<SoundEvent> PHARE = son("zb_phare");
    public static final RegistryObject<SoundEvent> BOCAL = son("zb_bocal");
}
