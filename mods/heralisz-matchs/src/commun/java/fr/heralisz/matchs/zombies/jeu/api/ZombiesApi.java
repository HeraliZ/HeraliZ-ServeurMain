package fr.heralisz.matchs.zombies.jeu.api;

import fr.heralisz.matchs.pont.Services;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Point d'entree du jeu zombies pour les autres sous-paquets ({@code zombies/contenu}, {@code zombies/client}) et les
 * autres modules. Fil serveur sauf mention.
 * <ul>
 *     <li>Enregistrements (a faire dans {@code ZombiesContenu.construire}) : {@link #fabrique(ZombieFabrique)},
 *     {@link #decor(ZombiesDecor)} ; cote client, {@link HudZombies#ecouteur(EvenementsHud)}.</li>
 *     <li>Lectures : {@link #estZombie(Entity)}, {@link #partieDe(Entity)}, {@link #planches(Level, BlockPos)}
 *     (pour la collision des fenetres), {@link #partieA(Level, BlockPos)}.</li>
 *     <li>Interaction : {@link #interagir(ServerPlayer)} (clic droit sur un bloc-entite du contenu = touche F).</li>
 *     <li>Sons : ids fixes {@link Sons} que le contenu enregistre (sounds.json, SoundEvent du mod) ; c'est le
 *     <b>jeu</b> qui les joue (le contenu ne les rejoue pas) ; un son absent est simplement ignore.</li>
 * </ul>
 */
public final class ZombiesApi {
    private ZombiesApi() {
    }

    /** Cle de {@code persistentData} (chaine = id de la partie) posee sur chaque creature du jeu. */
    public static final String TAG_PARTIE = "heralisz:zombies";
    /** Cle de {@code persistentData} (booleen) posee sur les entites de decor creees par le jeu (roulette, bonus de secours). */
    public static final String TAG_DECOR = "heralisz:zombies_decor";
    /**
     * Marqueur NBT (octet 1, racine du tag de la pile) d'une arme passee au Pack-a-Punch, a n'importe quel niveau (voir
     * {@link #NBT_PAP_NIVEAU}).
     */
    public static final String NBT_PAP = "HeraliszZbPap";
    /**
     * Niveau de Pack-a-Punch de l'arme (entier 1..{@link #PAP_NIVEAU_MAX}, racine du tag de la pile) : degats x2 / x3 / x4
     * cote serveur, nom « ★ » / « ★★ » / « ★★★ », camo {@code pap_1..3}. Absent avec {@link #NBT_PAP} = niveau 1.
     */
    public static final String NBT_PAP_NIVEAU = "HeraliszZbPapNiveau";
    /** Niveau de Pack-a-Punch le plus haut (par defaut ; {@code regles.json:pack_a_punch.niveaux} peut en donner moins). */
    public static final int PAP_NIVEAU_MAX = 3;
    /** Rarete d'une arme tiree a la boite (chaine {@link Rarete#id()}, racine du tag de la pile). */
    public static final String NBT_RARETE = "HeraliszZbRarete";
    /** Duree de l'enjambement d'une fenetre (ticks) : deplacement interpole du jeu, animation du contenu. */
    public static final int ENJAMBEMENT_TICKS = 16;
    /** Intervalle de base entre deux planches arrachees (ticks, ~1,2 s ; un peu plus rapide aux manches hautes). */
    public static final int ARRACHAGE_TICKS = 24;
    /** Duree de l'amelioration au Pack-a-Punch (ticks). */
    public static final int PAP_TICKS = 60;

    private static ZombieFabrique fabrique;
    @Nullable
    private static BossFinal bossFinal;
    private static ZombiesDecor decor = new ZombiesDecor() {
    };

    // ------------------------------------------------------------------ enregistrements
    /** Enregistre la fabrique de creatures (module contenu). */
    public static void fabrique(@Nullable ZombieFabrique f) {
        fabrique = f;
        HeraliszMatchs.LOG.info("[zombies] fabrique de creatures : {}", f == null ? "secours vanilla" : f.getClass().getName());
    }

    /** Fabrique enregistree, ou null (le jeu utilise alors son secours). */
    @Nullable
    public static ZombieFabrique fabrique() {
        return fabrique;
    }

    /** Enregistre le rendu du decor (module contenu). */
    public static void decor(@Nullable ZombiesDecor d) {
        decor = d != null ? d : new ZombiesDecor() {
        };
        HeraliszMatchs.LOG.info("[zombies] decor : {}", d == null ? "aucun" : d.getClass().getName());
    }

    /** Decor enregistre (jamais null : implementation vide par defaut). */
    public static ZombiesDecor decor() {
        return decor;
    }

    // ------------------------------------------------------------------ acte IV de The Giant : le boss N-07
    /** Cle de {@code persistentData} (booleen) posee sur le boss final et tout ce que son combat cree (projectiles, statue). */
    public static final String TAG_BOSS = "heralisz:zombies_boss";

    /**
     * Combat final de la quete de The Giant (acte IV, D-057) : l'adaptateur du mod du boss ({@code zombies/compat/N07Final},
     * charge seulement si {@code heralisz_n07} est installe) s'enregistre ici. Le hall du boss et le puits du monte-charge sont
     * poses avec l'instance (structures annexes de la carte) ; au debut de chaque partie le jeu demande la salle
     * ({@link #salle}), puis la porte de confinement lance {@link SalleBoss#lancer}. Fil serveur.
     */
    public interface BossFinal {
        /**
         * Lie la salle deja posee a cette origine (coordonnees absolues) et la remet a zero (par tranches, sans gel). Null si
         * la salle n'est pas la (mod du boss ajoute apres la pose de l'instance, gabarit absent).
         */
        @Nullable
        SalleBoss salle(ServerLevel niveau, PlanSalle plan);
    }

    /** Ou est posee la salle : variante du boss, origine absolue et rotation (comme {@code StructureTemplate}). */
    public record PlanSalle(String variante, BlockPos origine, Rotation rotation) {
    }

    /** Salle du boss liee a une instance (une par partie). Toutes les positions sont absolues. */
    public interface SalleBoss {
        /**
         * Lance l'acte IV : intro (porte, monte-charge, hall, reveil ; passable a la majorite), combat, mort, outro. Rend
         * faux si le combat ne peut pas commencer (le jeu le dit aux joueurs).
         */
        boolean lancer(ContexteBoss ctx);

        /** Vrai tant que le combat (intro comprise) n'est ni gagne ni interrompu. */
        boolean enCombat();

        /** Points d'apparition des renforts (chiens, zombies) dans le hall. */
        List<Vec3> renforts();

        /** Point de largage des bonus (Max Ammo des transitions). */
        Vec3 largage();

        /** Interieur du hall (arene). */
        AABB interieur();

        /** Tout le volume (hall + puits du monte-charge). */
        AABB volume();

        /** Combat interrompu et chaque joueur de la salle renvoye au jeu ({@link EvenementsBoss#retour}, evacuation). */
        void evacuer();

        /** Fin de partie : combat interrompu, entites du boss retirees, salle remise comme le gabarit (par tranches). */
        void remettre();

        /** L'instance est rendue : la salle est oubliee (teleporteur, evenements). */
        void oublier();

        /** Banc d'essai : le boss est vaincu tout de suite (cinematique de mort, outro). */
        void devVictoire();

        /** Resume pour {@code /zombies dev etat}. */
        String resume();
    }

    /** Contexte du combat final. */
    public record ContexteBoss(String partie, ServerLevel niveau, Supplier<List<ServerPlayer>> joueurs, int nbJoueurs, int manche,
                               Predicate<ServerPlayer> cible, Consumer<Entity> marqueur, EvenementsBoss evenements) {
    }

    /** Ce que l'adaptateur du boss signale au jeu (fil serveur, corps vides par defaut). */
    public interface EvenementsBoss {
        /** Crochet du scenario (ids de N07Api.HOOK_* : porte_cle, porte_ouverture, combat, p2_fin, p3_coupure, p3_fin...). */
        default void crochet(String id) {
        }

        /** Une cinematique du boss (apparition, phase, mort) ou une scene commence / finit. */
        default void cinematique(boolean debut) {
        }

        /** Les joueurs reprennent la main apres l'intro (passee ou non) : le combat commence. */
        default void main() {
        }

        /** Degats remappes que le boss va infliger a ce joueur (montant exact, avant le reglage de difficulte du serveur). */
        default void degats(ServerPlayer joueur, float montant) {
        }

        /** Le boss est vaincu : sa cinematique de mort commence. */
        default void mortDebut() {
        }

        /** Fin de la cinematique de mort : victoire (l'outro commence, le teleporteur s'allumera). */
        default void victoire() {
        }

        /** Fin de l'outro : le teleporteur 935 est allume. */
        default void outroFinie() {
        }

        /** Combat interrompu (boss perdu, salle oubliee...) ; raison courte pour le journal. */
        default void abandon(String raison) {
        }

        /** Un joueur doit remonter (teleporteur 935 apres la victoire, ou evacuation) : le jeu le teleporte. */
        default void retour(ServerPlayer joueur, boolean evacuation) {
        }
    }

    /** Enregistre le combat final (adaptateur du mod du boss), null pour le retirer. */
    public static void bossFinal(@Nullable BossFinal b) {
        bossFinal = b;
        HeraliszMatchs.LOG.info("[zombies] boss final de The Giant : {}", b == null ? "aucun" : b.getClass().getName());
    }

    /** Combat final enregistre, ou null (mod du boss absent : la porte de confinement acheve la quete sans combat). */
    @Nullable
    public static BossFinal bossFinal() {
        return bossFinal;
    }

    // ------------------------------------------------------------------ lectures
    /** Vrai si l'entite est une creature d'une partie de zombies. */
    public static boolean estZombie(Entity e) {
        return e != null && e.getPersistentData().contains(TAG_PARTIE);
    }

    /** Partie de la creature (ou du joueur), ou null. Côté serveur, une PartieZombies (typée : PartiesZombies.partieDe). */
    @Nullable
    public static Partie partieDe(Entity e) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && e != null ? m.zombiesPartieDe(e) : null;
    }

    /** Partie dont l'instance contient ce bloc, ou null (côté serveur : PartiesZombies.partieA). */
    @Nullable
    public static Partie partieA(Level niveau, BlockPos pos) {
        Services.Moteur m = Services.MOTEUR;
        return m != null ? m.zombiesPartieA(niveau, pos) : null;
    }

    /**
     * Planches restantes (0..6) de la fenetre qui contient ce bloc dans une partie en cours, ou -1 si le bloc n'est
     * dans aucune fenetre d'une partie (fenetre de decor : la considerer pleine).
     */
    public static int planches(Level niveau, BlockPos pos) {
        Services.Moteur m = Services.MOTEUR;
        return m != null ? m.zombiesPlanches(niveau, pos) : -1;
    }

    /**
     * Le joueur interagit (comme s'il appuyait sur F) : achat, reparation d'une planche, prise de l'arme de la boite...
     * Rend vrai si quelque chose a ete fait ou refuse (message envoye). A appeler depuis un {@code use} de bloc du contenu.
     */
    public static boolean interagir(ServerPlayer joueur) {
        Services.Moteur m = Services.MOTEUR;
        return m != null && m.zombiesInteragir(joueur);
    }

    /** Vrai si l'arme est passee au Pack-a-Punch (marqueur {@link #NBT_PAP}). Utilisable des deux cotes. */
    public static boolean estAmelioree(net.minecraft.world.item.ItemStack arme) {
        return arme != null && !arme.isEmpty() && arme.getTag() != null && arme.getTag().getBoolean(NBT_PAP);
    }

    /**
     * Niveau de Pack-a-Punch de l'arme : 0 = jamais amelioree, 1..{@link #PAP_NIVEAU_MAX} sinon (une ancienne arme qui
     * n'a que {@link #NBT_PAP} est au niveau 1). Utilisable des deux cotes.
     */
    public static int niveauPap(net.minecraft.world.item.ItemStack arme) {
        if (!estAmelioree(arme)) return 0;
        int n = arme.getTag().getInt(NBT_PAP_NIVEAU);
        return Math.max(1, Math.min(PAP_NIVEAU_MAX, n));
    }

    /** Rarete d'une arme tiree a la boite, ou null (arme du mur, de depart...). Utilisable des deux cotes. */
    @Nullable
    public static Rarete rarete(net.minecraft.world.item.ItemStack arme) {
        if (arme == null || arme.isEmpty() || arme.getTag() == null || !arme.getTag().contains(NBT_RARETE)) return null;
        return Rarete.parId(arme.getTag().getString(NBT_RARETE), null);
    }

    // ------------------------------------------------------------------ sons
    /**
     * Identifiants des sons joues par le jeu ({@code heralisz_matchs:zb_*}), a enregistrer par le contenu (sons generes
     * par script, jamais extraits d'un jeu). Les jingles de manche et la musique de fin sont joues a tous les joueurs
     * de la partie ; les autres a la position de l'action.
     */
    public static final class Sons {
        private Sons() {
        }

        public static final ResourceLocation MANCHE_DEBUT = HeraliszMatchs.id("zb_manche_debut");
        public static final ResourceLocation MANCHE_FIN = HeraliszMatchs.id("zb_manche_fin");
        public static final ResourceLocation SPECIALE_DEBUT = HeraliszMatchs.id("zb_speciale_debut");
        public static final ResourceLocation PARTIE_FIN = HeraliszMatchs.id("zb_partie_fin");
        public static final ResourceLocation ACHAT = HeraliszMatchs.id("zb_achat");
        public static final ResourceLocation REFUS = HeraliszMatchs.id("zb_refus");
        public static final ResourceLocation PORTE = HeraliszMatchs.id("zb_porte");
        public static final ResourceLocation EBOULIS = HeraliszMatchs.id("zb_eboulis");
        public static final ResourceLocation PLANCHE_POSE = HeraliszMatchs.id("zb_planche_pose");
        public static final ResourceLocation PLANCHE_ARRACHE = HeraliszMatchs.id("zb_planche_arrache");
        public static final ResourceLocation BOITE_ROULETTE = HeraliszMatchs.id("zb_boite_roulette");
        public static final ResourceLocation BOITE_PELUCHE = HeraliszMatchs.id("zb_boite_peluche");
        public static final ResourceLocation BOITE_ARRIVEE = HeraliszMatchs.id("zb_boite_arrivee");
        public static final ResourceLocation ARME_PRISE = HeraliszMatchs.id("zb_arme_prise");
        public static final ResourceLocation BONUS_APPARITION = HeraliszMatchs.id("zb_bonus_apparition");
        public static final ResourceLocation BONUS_PRIS = HeraliszMatchs.id("zb_bonus_pris");
        public static final ResourceLocation BONUS_FIN = HeraliszMatchs.id("zb_bonus_fin");
        public static final ResourceLocation ATOMIQUE = HeraliszMatchs.id("zb_atomique");
        public static final ResourceLocation ATOUT_BU = HeraliszMatchs.id("zb_atout_bu");
        public static final ResourceLocation COURANT = HeraliszMatchs.id("zb_courant");
        public static final ResourceLocation A_TERRE = HeraliszMatchs.id("zb_a_terre");
        public static final ResourceLocation REANIME = HeraliszMatchs.id("zb_reanime");
        public static final ResourceLocation MORT = HeraliszMatchs.id("zb_mort");
        // ajouts v2 (ZOMBIES_V2_SPEC) : a enregistrer par le contenu ; absents = ignores
        /** Une arme entre dans le Pack-a-Punch (3 s de machine). */
        public static final ResourceLocation PAP_AMELIORATION = HeraliszMatchs.id("zb_pap_amelioration");
        /** L'arme amelioree sort du Pack-a-Punch. */
        public static final ResourceLocation PAP_PRETE = HeraliszMatchs.id("zb_pap_prete");
        /** Fabrication a l'etabli. */
        public static final ResourceLocation ATELIER = HeraliszMatchs.id("zb_atelier");
        /** Materiaux de recuperation ramasses. */
        public static final ResourceLocation MATERIAU = HeraliszMatchs.id("zb_materiau");
        /** Etape d'easter egg trouvee. */
        public static final ResourceLocation EASTER_EGG_ETAPE = HeraliszMatchs.id("zb_easter_egg_etape");
        /** Easter egg termine (recompense). */
        public static final ResourceLocation EASTER_EGG_FIN = HeraliszMatchs.id("zb_easter_egg_fin");
        /** Nouvel objectif affiche. */
        public static final ResourceLocation OBJECTIF = HeraliszMatchs.id("zb_objectif");
        /** Coup de griffe d'un zombie a travers une fenetre. */
        public static final ResourceLocation GRIFFE_FENETRE = HeraliszMatchs.id("zb_griffe_fenetre");
        // v3 : The Giant (machines, quete)
        public static final ResourceLocation LEVIER_COURANT = HeraliszMatchs.id("zb_levier_courant");
        public static final ResourceLocation FUSIBLE = HeraliszMatchs.id("zb_fusible");
        public static final ResourceLocation FUSIBLE_RAMASSE = HeraliszMatchs.id("zb_fusible_ramasse");
        public static final ResourceLocation AME = HeraliszMatchs.id("zb_ame");
        public static final ResourceLocation CUVE_PLEINE = HeraliszMatchs.id("zb_cuve_pleine");
        public static final ResourceLocation LIAISON_DEBUT = HeraliszMatchs.id("zb_liaison_debut");
        public static final ResourceLocation LIAISON_TIC = HeraliszMatchs.id("zb_liaison_tic");
        public static final ResourceLocation LIAISON_OK = HeraliszMatchs.id("zb_liaison_ok");
        public static final ResourceLocation TELEPORTEUR = HeraliszMatchs.id("zb_teleporteur");
        public static final ResourceLocation TELEPORTEUR_ARRIVEE = HeraliszMatchs.id("zb_teleporteur_arrivee");
        public static final ResourceLocation RIDEAU = HeraliszMatchs.id("zb_rideau");
        public static final ResourceLocation TOUR_ECLAIRS = HeraliszMatchs.id("zb_tour_eclairs");
        public static final ResourceLocation PIEGE = HeraliszMatchs.id("zb_piege");
        public static final ResourceLocation PIEGE_MORT = HeraliszMatchs.id("zb_piege_mort");
        public static final ResourceLocation ECLAIR = HeraliszMatchs.id("zb_eclair");
        public static final ResourceLocation BOWIE = HeraliszMatchs.id("zb_bowie");
        public static final ResourceLocation QUETE_OBJET = HeraliszMatchs.id("zb_quete_objet");
        public static final ResourceLocation PHARE = HeraliszMatchs.id("zb_phare");
        public static final ResourceLocation BOCAL = HeraliszMatchs.id("zb_bocal");
    }
}
