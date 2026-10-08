package fr.heralisz.matchs.zombies.jeu.api;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Etat du HUD zombies cote <b>client</b>, rempli par les paquets du jeu ({@code zombies/jeu/net}) et lu par le HUD
 * ({@code zombies/client}) a chaque image, sans allocation. Rien ici n'importe de classe client : ce ne sont que des
 * donnees. Fil client.
 * <ul>
 *     <li>{@link #actif()} : vrai pendant une partie (de l'arrivee dans l'instance a l'ecran de fin) ;</li>
 *     <li>{@link #manche()}, {@link #speciale()}, {@link #restants()} (zombies restants de la manche, a faire
 *     apparaitre + vivants), {@link #courant()} ;</li>
 *     <li>{@link #joueurs()} : jusqu'a 4 lignes (points, kills, atouts en masque {@link TypeAtout#bit()}, etat, secondes
 *     restantes a terre, materiaux, kits d'auto-reanimation, plaques d'armure), ordre d'arrivee, couleur fixe ;</li>
 *     <li>{@link #bonusSecondes(TypeBonus)} : secondes restantes des bonus a minuterie (decompte local) ;</li>
 *     <li>invite d'achat : {@link #invite()} (« Ouvrir la porte », « Acheter MK14 », « Reparer la fenetre »,
 *     « Reanimer Vortex »...), {@link #inviteCout()} (0 = gratuit), {@link #inviteAbordable()}, {@link #progression()}
 *     (0..100 pendant un maintien ou une attente : reparation, reanimation, Pack-a-Punch ; -1 sinon). La touche
 *     affichee est celle de l'echange de main (F par defaut, {@code options.keySwapOffhand}) ;</li>
 *     <li>v2 : {@link #objectif()} (texte de l'objectif courant, en haut a gauche, vide = rien), {@link #intro()} (lore
 *     du debut de partie, {@link #introDepuisMs()}), {@link #ambiance()} (dernier {@link MomentMusique} recu, pour
 *     reprendre la bonne musique) ;</li>
 *     <li>v3 : {@link #lieu()} (nom de la zone de la carte ou se trouve le joueur, sous la boussole).</li>
 * </ul>
 * {@link #version()} augmente a chaque changement (pour un cache de rendu).
 */
public final class HudZombies {
    private HudZombies() {
    }

    public static final int DEBOUT = 0, A_TERRE = 1, MORT = 2;

    /**
     * Ligne de joueur.
     *
     * @param couleur          couleur RGB du joueur (blanc, bleu, jaune, vert comme dans CoD)
     * @param atouts           masque des atouts ({@link TypeAtout#bit()})
     * @param etat             {@link #DEBOUT}, {@link #A_TERRE}, {@link #MORT}
     * @param secondes         secondes restantes avant de mourir a terre (0 sinon)
     * @param materiaux        materiaux de recuperation possedes (etablis)
     * @param autoReanimations kits d'auto-reanimation possedes (relevent seul une fois a terre)
     * @param plaques          plaques d'armure restantes (0..3, chacune absorbe une part des degats)
     */
    public record Joueur(UUID id, String nom, int couleur, int points, int kills, int tetes, int atouts, int etat, int secondes,
                         int materiaux, int autoReanimations, int plaques) {
        /** Ligne sans les champs v2 (materiaux, kits, plaques a 0). */
        public Joueur(UUID id, String nom, int couleur, int points, int kills, int tetes, int atouts, int etat, int secondes) {
            this(id, nom, couleur, points, kills, tetes, atouts, etat, secondes, 0, 0, 0);
        }
    }

    private static boolean actif;
    private static int manche;
    private static boolean speciale;
    private static int restants;
    private static boolean courant;
    private static List<Joueur> joueurs = List.of();
    private static final int[] BONUS = new int[TypeBonus.values().length];
    private static long bonusRecuMs;
    @Nullable
    private static Component invite;
    private static int inviteCout;
    private static boolean inviteAbordable;
    private static int progression = -1;
    private static long version;
    private static String objectifId = "";
    private static Component objectif = Component.empty();
    private static long objectifDepuisMs;
    /** Repere de la boussole vers la cible de l'objectif (0.2.3), null = aucun. */
    @Nullable
    private static net.minecraft.world.phys.Vec3 objectifCible;
    private static List<Component> intro = List.of();
    private static long introDepuisMs;
    private static String lieu = "";
    private static MomentMusique ambiance = MomentMusique.SILENCE;
    private static EvenementsHud ecouteur = new EvenementsHud() {
    };

    // ------------------------------------------------------------------ lecture (HUD)
    public static boolean actif() {
        return actif;
    }

    public static int manche() {
        return manche;
    }

    public static boolean speciale() {
        return speciale;
    }

    public static int restants() {
        return restants;
    }

    public static boolean courant() {
        return courant;
    }

    /** Lignes des joueurs (liste non modifiable, remplacee a chaque paquet). */
    public static List<Joueur> joueurs() {
        return joueurs;
    }

    /** Ligne du joueur, ou null. */
    @Nullable
    public static Joueur joueur(UUID id) {
        for (int i = 0; i < joueurs.size(); i++) if (joueurs.get(i).id().equals(id)) return joueurs.get(i);
        return null;
    }

    /** Secondes restantes du bonus (0 = inactif), decomptees localement depuis le dernier paquet. */
    public static int bonusSecondes(TypeBonus type) {
        int s = BONUS[type.ordinal()];
        if (s <= 0) return 0;
        int ecoule = (int) ((System.currentTimeMillis() - bonusRecuMs) / 1000L);
        return Math.max(0, s - ecoule);
    }

    @Nullable
    public static Component invite() {
        return invite;
    }

    public static int inviteCout() {
        return inviteCout;
    }

    public static boolean inviteAbordable() {
        return inviteAbordable;
    }

    /** 0..100 pendant un maintien ou une attente (reparation, reanimation, Pack-a-Punch), -1 sinon. */
    public static int progression() {
        return progression;
    }

    /** Identifiant de l'objectif courant ({@code porte}, {@code courant}, {@code pack_a_punch}, {@code mystere}...), vide = aucun. */
    public static String objectifId() {
        return objectifId;
    }

    /** Texte de l'objectif courant (jamais null, vide = aucun objectif a afficher). */
    public static Component objectif() {
        return objectif;
    }

    /** Instant (ms) ou l'objectif courant est apparu (animation d'entree). */
    public static long objectifDepuisMs() {
        return objectifDepuisMs;
    }

    /** Lignes du lore d'intro (vide = aucune), recues au debut de la partie. */
    public static List<Component> intro() {
        return intro;
    }

    /** Instant (ms) de reception du lore d'intro (effet machine a ecrire puis effacement). */
    public static long introDepuisMs() {
        return introDepuisMs;
    }

    /** Nom du lieu ou se trouve le joueur (zone de la carte, sous la boussole), vide = inconnu. */
    public static String lieu() {
        return lieu;
    }

    /** Dernier moment musical recu ({@link MomentMusique#SILENCE} hors partie). */
    public static MomentMusique ambiance() {
        return ambiance;
    }

    public static long version() {
        return version;
    }

    /** Ecouteur courant des evenements (jamais null). */
    public static EvenementsHud ecouteur() {
        return ecouteur;
    }

    /** Enregistre l'ecouteur du HUD (un seul ; null = aucun). */
    public static void ecouteur(@Nullable EvenementsHud e) {
        ecouteur = e != null ? e : new EvenementsHud() {
        };
    }

    // ------------------------------------------------------------------ ecriture (reserve aux paquets du jeu)
    /** Reserve au jeu : etat complet. */
    public static void etat(boolean actifN, int mancheN, boolean specialeN, int restantsN, boolean courantN, List<Joueur> joueursN, int[] bonus) {
        actif = actifN;
        manche = mancheN;
        speciale = specialeN;
        restants = restantsN;
        courant = courantN;
        joueurs = List.copyOf(joueursN);
        for (int i = 0; i < BONUS.length; i++) BONUS[i] = i < bonus.length ? bonus[i] : 0;
        bonusRecuMs = System.currentTimeMillis();
        if (!actif) {
            invite = null;
            progression = -1;
            objectifId = "";
            objectif = Component.empty();
            objectifCible = null;
            intro = List.of();
            lieu = "";
        }
        version++;
    }

    /** Reserve au jeu : invite d'achat (texte null = aucune). */
    public static void invite(@Nullable Component texte, int cout, boolean abordable, int progressionN) {
        invite = texte;
        inviteCout = cout;
        inviteAbordable = abordable;
        progression = progressionN;
        version++;
    }

    /** Reserve au jeu : objectif courant (texte vide = aucun). */
    public static void objectif(String id, Component texte) {
        // l'animation d'entree ne rejoue qu'au changement d'objectif (pas a chaque progression : (1/3) -> (2/3))
        if (!id.equals(objectifId)) objectifDepuisMs = System.currentTimeMillis();
        objectifId = id == null ? "" : id;
        objectif = texte == null ? Component.empty() : texte;
        version++;
    }

    /** Reserve au jeu : repere de la boussole vers la cible de l'objectif courant (null = aucun). */
    public static void objectifCible(@Nullable net.minecraft.world.phys.Vec3 c) {
        objectifCible = c;
    }

    /** Cible de l'objectif courant montree sur la boussole (0.2.3), ou null. */
    @Nullable
    public static net.minecraft.world.phys.Vec3 objectifCible() {
        return objectifCible;
    }

    /** Reserve au jeu : lore d'intro. */
    public static void intro(List<Component> lignes) {
        intro = List.copyOf(lignes);
        introDepuisMs = System.currentTimeMillis();
        version++;
    }

    /** Reserve au jeu : nom du lieu (zone de la carte) du joueur local. */
    public static void lieu(String nom) {
        lieu = nom == null ? "" : nom;
        version++;
    }

    /** Reserve au jeu : moment musical courant. */
    public static void ambiance(MomentMusique m) {
        ambiance = m == null ? MomentMusique.SILENCE : m;
        version++;
    }

    /** Reserve au jeu : remise a zero (deconnexion, fin). */
    public static void vider() {
        etat(false, 0, false, 0, false, List.of(), new int[0]);
        ambiance = MomentMusique.SILENCE;
    }
}
