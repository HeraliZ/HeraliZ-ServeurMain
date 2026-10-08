package fr.heralisz.matchs.zombies.jeu.api;

import fr.heralisz.matchs.moteur.api.Instance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Rendu du decor d'une partie de zombies, fourni par le module contenu ({@code zombies/contenu}) via
 * {@link ZombiesApi#decor(ZombiesDecor)}. Le jeu ({@code zombies/jeu}) detient <b>toute la verite</b> (planches,
 * portes, boite, courant, atouts, Pack-a-Punch, etablis, easter egg, achats, points) et appelle ces crochets pour que
 * les blocs-entites et entites visibles suivent. Toutes les methodes ont un corps vide par defaut ; fil serveur ; ne
 * jamais lever d'exception (elles sont de toute facon attrapees et journalisees).
 * <p>Les interactions des joueurs (touche F, voir {@link ZombiesApi#interagir}) sont traitees par le jeu : un
 * bloc-entite du contenu n'a rien a verifier ni a debiter.
 */
public interface ZombiesDecor {
    /** Evenement d'une fenetre. */
    enum EvenementFenetre {
        /** Etat pose sans animation (debut de partie, bonus Charpentier, fin). */
        INSTALLEE,
        /** Un zombie vient d'arracher une planche (animation + son d'arrachage). */
        ARRACHEE,
        /** Un joueur vient de reposer une planche (animation de clouage + son). */
        REPAREE
    }

    /** Etat d'un emplacement de boite mystere. */
    enum EtatBoite {
        /** Pas de boite ici (emplacement vide, eventuellement un socle eteint). */
        ABSENTE,
        /** Boite presente, fermee, achetable (lueur). */
        FERMEE,
        /** Couvercle ouvert, roulette en cours (l'ItemDisplay est gere par le jeu). */
        ROULETTE,
        /** Couvercle ouvert, arme proposee a l'acheteur (rarete : {@link #boiteTirage}). */
        OUVERTE,
        /** Tirage du nounours : la boite tremble, s'envole et quitte cet emplacement (5 s). */
        DEPART,
        /** La boite arrive a cet emplacement (faisceau, chute du ciel, poussiere : 1,5 s), puis FERMEE. */
        ARRIVEE
    }

    /** Ambiance lumineuse et sonore cote serveur (la musique, elle, est cote client : {@link MomentMusique}). */
    enum Ambiance {
        MANCHE_DEBUT, MANCHE_FIN, SPECIALE_DEBUT, SPECIALE_FIN, COURANT, FIN_PARTIE
    }

    /** Etat de la machine Pack-a-Punch ({@link #packAPunch}). */
    enum EtatPap {
        /** Courant coupe : machine eteinte (debut de partie). */
        ETEINTE,
        /** Courant retabli : machine allumee, en attente d'une arme. */
        PRETE,
        /** Une arme vient d'entrer : amelioration de {@link ZombiesApi#PAP_TICKS} ticks ({@code arme} = arme d'origine). */
        AMELIORATION,
        /** L'arme amelioree sort et attend son proprietaire ({@code arme} = arme amelioree, « ★ nom »). */
        OFFERTE,
        /** L'arme a ete reprise (ou rendue d'office a la fin du delai) : la machine redevient {@link #PRETE}. */
        REPRISE
    }

    /** Etat d'une etape de l'easter egg ({@link #easterEgg}). */
    enum EtatEtape {
        /** Debut de partie : objet pose, discret (radio qui gresille, interrupteur, objet a ramasser). */
        CACHEE,
        /** Etape trouvee dans le bon ordre (effet : radio qui parle, interrupteur qui bascule, objet qui disparait). */
        TROUVEE,
        /** Mauvais ordre : petit effet de refus (etincelle, gresillement), rien n'avance. */
        REFUSEE,
        /** Toutes les etapes sont trouvees : effet final (appele sur chaque etape). */
        TERMINE
    }

    /**
     * Etat d'un teleporteur de The Giant (pad, son pupitre et son noyau de 115) : {@link #ETEINT} sans courant,
     * {@link #ALIMENTE} sous courant (noyau vide), {@link #CHARGE} cuve pleine (noyau vert, pret a lier), {@link #LIAISON}
     * 30 s pour courir au mainframe, {@link #LIE}, {@link #TRANSPORT} teleportation (1,5 s), {@link #RECHARGE} bobines
     * orange qui refroidissent.
     */
    enum EtatPad {
        ETEINT, ALIMENTE, CHARGE, LIAISON, LIE, TRANSPORT, RECHARGE
    }

    /** Genres d'objets de quete ({@link #objetQuete}). */
    int OBJET_FUSIBLE = 0, OBJET_BOITIER = 1, OBJET_SINGE = 2, OBJET_NOUNOURS = 3, OBJET_ARME = 4, OBJET_CARTE = 5;
    /** Etats d'objets de quete : rien, au repos (eclat), actif (a tirer / a prendre, il brille), pris (effet puis rien). */
    int OBJET_CACHE = 0, OBJET_VISIBLE = 1, OBJET_ACTIF = 2, OBJET_PRIS = 3;

    /**
     * Debut de partie, instance prete, avant la teleportation des joueurs : poser ou remettre a neuf tous les
     * blocs-entites du plan (fenetres a 6 planches, armes au mur avec leur {@code gunId}, emplacements de boite
     * {@link EtatBoite#ABSENTE}, machines d'atout eteintes, levier abaisse, Pack-a-Punch eteint
     * ({@link PlanZombies#packAPunch()}), etablis ({@link PlanZombies#ateliers()}), etapes d'easter egg cachees
     * ({@link PlanZombies#easterEgg()}), pancartes de prix des portes ({@link PlanZombies.Porte#pancarte()})). Les portes
     * ont deja ete remises en place par le jeu (structure d'origine).
     */
    default void installer(Instance instance, PlanZombies plan) {
    }

    /** Fin de partie, avant liberation de l'instance : retirer les entites de decor creees par le contenu. */
    default void nettoyer(Instance instance, PlanZombies plan) {
    }

    /**
     * Etat d'une fenetre. Tant que {@code planches > 0} l'ouverture doit bloquer les zombies (collision) ; a 0 elle
     * peut etre franchie (le jeu fait passer les zombies lui-meme). Les joueurs ne la franchissent jamais.
     */
    default void fenetre(Instance instance, PlanZombies.Fenetre fenetre, int planches, EvenementFenetre evenement) {
    }

    /** Porte achetee ({@code ouverte} vrai : les blocs viennent d'etre retires par le jeu, jouer l'effet) ou refermee. */
    default void porte(Instance instance, PlanZombies.Porte porte, boolean ouverte) {
    }

    /** Etat d'un emplacement de boite mystere ({@code emplacement} = index dans {@link PlanZombies#boites()}). */
    default void boite(Instance instance, int emplacement, EtatBoite etat) {
    }

    /**
     * La roulette s'arrete sur une arme de cette rarete (juste avant {@link EtatBoite#OUVERTE}) : teinter le faisceau, la
     * lueur et le son selon {@link Rarete#couleur}. L'objet tire lui-meme (ItemDisplay du jeu) brille deja de cette
     * couleur.
     */
    default void boiteTirage(Instance instance, int emplacement, Rarete rarete) {
    }

    /** Courant retabli (levier leve, lumieres) ou coupe (debut de partie). */
    default void courant(Instance instance, boolean actif) {
    }

    /** Machine d'atout alimentee ou eteinte. */
    default void atout(Instance instance, PlanZombies.Atout atout, boolean alimente) {
    }

    /** Arme au mur achetee par un joueur (effet de craie qui s'illumine), pour info. */
    default void armeAchetee(Instance instance, PlanZombies.ArmeMur arme) {
    }

    /**
     * Etat du Pack-a-Punch ({@code arme} = pile concernee, {@link ItemStack#EMPTY} pour ETEINTE, PRETE, REPRISE).
     * Sons de la machine joues par le jeu ({@link ZombiesApi.Sons#PAP_AMELIORATION}, {@link ZombiesApi.Sons#PAP_PRETE}).
     */
    default void packAPunch(Instance instance, PlanZombies.PackAPunch machine, EtatPap etat, ItemStack arme) {
    }

    /**
     * Vrai si le contenu dessine lui-meme l'arme dans la machine pendant AMELIORATION et OFFERTE ; faux (defaut) = le jeu
     * pose un {@code item_display} devant la machine.
     */
    default boolean packAPunchAfficheArme() {
        return false;
    }

    /** Etabli : {@code recette} null = installation / remise a neuf ; sinon un joueur vient de fabriquer cette recette. */
    default void atelier(Instance instance, PlanZombies.Atelier atelier, @Nullable String recette) {
    }

    /** Etape de l'easter egg (voir {@link EtatEtape}). */
    default void easterEgg(Instance instance, PlanZombies.EtapeEasterEgg etape, EtatEtape etat) {
    }

    /**
     * Un zombie vient d'apparaitre (deja ajoute au monde). {@code fenetre} null = il sort de terre dans une salle (animation
     * de sortie, particules maison) ; sinon il arrive derriere cette fenetre. Appele aussi quand le garde-fou replace un
     * zombie bloque ou trop loin (il ressort de terre pres des joueurs).
     */
    default void apparition(Instance instance, Mob zombie, @Nullable PlanZombies.Fenetre fenetre) {
    }

    /**
     * Un zombie sort d'un point de salle d'un certain genre ({@code sol}, {@code coin}, {@code conduit}, {@code gravats},
     * {@code tunnel} : champ {@code apparitions_salle_details} de la carte). Par defaut : {@link #apparition} sans fenetre
     * (sortie de terre). Le contenu peut adapter l'animation (sortie d'une bouche d'aeration, d'un tas de gravats...).
     */
    default void apparitionSalle(Instance instance, Mob zombie, String genre) {
        apparition(instance, zombie, null);
    }

    /**
     * Le zombie, colle a la fenetre ({@link PlanZombies.Fenetre#dehors()}), arrache une planche (le jeu appelle aussi
     * {@code swing} et joue {@link ZombiesApi.Sons#PLANCHE_ARRACHE}) : une fois toutes les ~{@link ZombiesApi#ARRACHAGE_TICKS}
     * ticks.
     */
    default void arrachage(Instance instance, Mob zombie, PlanZombies.Fenetre fenetre) {
    }

    /**
     * Le zombie <b>commence</b> a enjamber la fenetre vide : le jeu le deplace lui-meme (sans physique, interpole) de
     * {@link PlanZombies.Fenetre#dehors()} a {@link PlanZombies.Fenetre#dedans()} en {@link ZombiesApi#ENJAMBEMENT_TICKS}
     * ticks (pas de teleportation seche) ; l'animation d'enjambement doit durer autant.
     */
    default void franchissement(Instance instance, Mob zombie, PlanZombies.Fenetre fenetre) {
    }

    /** Escalade (0.2.3) : {@link #ESCALADE_FIN}, {@link #ESCALADE_MUR} (grimpe a un mur), {@link #ESCALADE_SAUT} (saute un obstacle bas). */
    int ESCALADE_FIN = 0, ESCALADE_MUR = 1, ESCALADE_SAUT = 2;

    /**
     * Le zombie commence ou finit une escalade (0.2.3) : le jeu le deplace lui-meme (sans physique, interpole, ~1,5 bloc/s
     * le long du mur puis un pas sur le rebord) ; le contenu montre la pose d'escalade ({@link #ESCALADE_MUR}) ou l'arc d'un
     * saut d'obstacle ({@link #ESCALADE_SAUT}, meme duree que l'enjambement), et la retire a {@link #ESCALADE_FIN}.
     */
    default void escalade(Instance instance, Mob zombie, int etat) {
    }

    /**
     * Bocal a moelle de The Giant (0.2.3, tires au hasard a chaque partie) : montre ({@code visible}) ou retire le decor du
     * bocal a cette case. Le contenu ne pose un bocal que dans une case d'air au-dessus d'un appui, et ne retire que son
     * propre decor de bocal.
     */
    default void bocal(Instance instance, BlockPos pos, Direction face, boolean visible) {
    }

    /**
     * Comme {@link #objetQuete}, mais l'objet peut etre montre a une {@code ancre} (0.2.3, point absolu en blocs) quand
     * {@code pos} est une case d'un decor de la carte (sole d'un fourneau, surface d'une cuve) : plus aucune case n'est
     * decoupee dans les decors pour la quete. Ancre null = {@link #objetQuete}.
     */
    default void objetQueteAncre(Instance instance, BlockPos pos, Direction face, int genre, int etat, String donnee, @Nullable Vec3 ancre) {
        objetQuete(instance, pos, face, genre, etat, donnee);
    }

    /**
     * Cree (et ajoute au monde) l'entite visible d'un bonus au sol a {@code pos}, ou rend null pour laisser le jeu
     * utiliser son rendu de secours. Le jeu la retire lui-meme ({@code discard}) quand le bonus est ramasse ou expire ;
     * clignotement des 5 dernieres secondes : {@link #bonusExpire}. L'entite ne doit pas etre ramassable.
     */
    @Nullable
    default Entity bonus(ServerLevel niveau, Vec3 pos, TypeBonus type) {
        return null;
    }

    /** Le bonus va disparaitre ({@code secondes} restantes, appele a 5 s puis chaque seconde). */
    default void bonusExpire(Entity bonus, int secondes) {
    }

    /**
     * Cree (et ajoute au monde) l'entite visible d'un tas de materiaux de recuperation lache par un zombie, ou rend null
     * (rendu de secours du jeu). Meme contrat que {@link #bonus} : non ramassable, retiree par le jeu (ramassage en
     * marchant dessus, expiration) ; {@link #bonusExpire} est aussi appele pour son clignotement.
     */
    @Nullable
    default Entity materiau(ServerLevel niveau, Vec3 pos, int quantite) {
        return null;
    }

    /** Changement d'ambiance (lumieres de manche, brouillard des Rodeurs...). */
    default void ambiance(Instance instance, Ambiance ambiance) {
    }

    // ------------------------------------------------------------------ v3 : machines et quete de The Giant (D-051)
    /**
     * Armoire du courant de The Giant ({@link PlanGiant#courant}) : {@code fusibles} = douilles garnies (bit i), {@code courant}
     * vrai = le levier vient d'etre abaisse (sectionneur, arcs, aiguille a 380 V). Appele a l'installation puis a chaque
     * changement.
     */
    default void armoire(Instance instance, int fusibles, boolean courant) {
    }

    /**
     * Objet de quete ({@code genre} = OBJET_*, {@code etat} = OBJET_CACHE..OBJET_PRIS) pose ou mis a jour a cette case ;
     * {@code donnee} = identifiant de l'arme pour OBJET_ARME (Annihilator du fourneau), vide sinon.
     */
    default void objetQuete(Instance instance, BlockPos pos, Direction face, int genre, int etat, String donnee) {
    }

    /** Etat d'un teleporteur (pad + pupitre) ; {@code dureeTicks} = duree de la liaison ou de la recharge. */
    default void pad(Instance instance, PlanGiant.Teleporteur t, EtatPad etat, int dureeTicks) {
    }

    /** Niveau de la cuve a ames d'un teleporteur (0..1). */
    default void cuveAmes(Instance instance, PlanGiant.Teleporteur t, float niveau) {
    }

    /** Une ame s'envole d'un zombie tue ({@code depuis}) jusqu'au cornet de la cuve du teleporteur. */
    default void ame(Instance instance, Vec3 depuis, PlanGiant.Teleporteur t) {
    }

    /** Mainframe : courant, pads lies (bits 0..2 = A, B, C), pads en liaison (memes bits). */
    default void mainframe(Instance instance, boolean courant, int lies, int enLiaison) {
    }

    /** Des joueurs arrivent sur le mainframe (eclair, colonne de lumiere). */
    default void arrivee(Instance instance, Vec3 pos) {
    }

    /** Rideau du Pack-a-Punch : 0 ferme, 1 deverrouille (il monte en 3 s), 2 ouvert (installation : sans animation). */
    default void rideauPap(Instance instance, int etat) {
    }

    /** Piege electrique allume ou eteint. */
    default void piege(Instance instance, PlanGiant.Piege piege, boolean actif) {
    }

    /** Piege allume : appele toutes les 4 ticks (arcs, etincelles). {@code tue} = une creature vient d'y mourir. */
    default void piegeTick(Instance instance, PlanGiant.Piege piege, @Nullable Vec3 tue) {
    }

    /** Passerelle de la cour abaissee (les blocs ont deja ete changes par le jeu) : poussiere, son. */
    default void passerelle(Instance instance, boolean abaissee) {
    }

    /** Rayon de la tete du robot geant jusqu'au tas de neige (machine secrete). */
    default void rayonRobot(Instance instance, Vec3 depuis, Vec3 vers) {
    }

    /** Effet ponctuel nomme : {@code bocal}, {@code objet_touche}, {@code phare}, {@code bowie}, {@code fusible}... */
    default void effet(Instance instance, Vec3 pos, String type) {
    }

    /**
     * Decors cuits de The Giant suivant le courant (D-053) : generateurs et lampe d'etabli allumes, voyants des leviers des
     * pieges. Appele a l'installation (etat de depart) puis au retour du courant.
     */
    default void courantGiant(Instance instance, boolean actif) {
    }

    /** Porte de confinement de N-07 (acte IV) : ouverte = les battants coulissent, plus de collision. */
    default void porteConfinement(Instance instance, boolean ouverte) {
    }
}
