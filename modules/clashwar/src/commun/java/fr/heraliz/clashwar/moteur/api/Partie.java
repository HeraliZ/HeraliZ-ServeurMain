package fr.heraliz.clashwar.moteur.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Une partie en cours (un match ClashWar, un duel, une partie de zombies). Chaque mode en derive.
 * <p>Cycle de vie :
 * <ol>
 *     <li>le mode cree la partie, ajoute ses {@link Equipe}s, puis {@link MatchApi#enregistrer(Partie)} ;</li>
 *     <li>chaque joueur passe par {@link Coffre#deposer} puis {@link MatchApi#rejoindre(ServerPlayer, Partie)} ;</li>
 *     <li>le moteur appelle {@link #tick()} a chaque tick serveur (fin de tick) tant que la phase n'est pas
 *     {@link Phase#TERMINEE} ; il appelle {@link #surElimination} a la place de toute mort d'un joueur de la partie,
 *     {@link #surDeconnexion} a la deconnexion, {@link #surReconnexion} a la reconnexion ;</li>
 *     <li>le mode appelle {@link #terminer(Equipe)} (annonces, recompenses, ecran de fin), puis {@link #fermer()} quand
 *     tout le monde est rendu (Coffre, {@link MatchApi#quitter}) : la partie est retiree et son instance liberee.</li>
 * </ol>
 * Fil serveur uniquement. Pas d'allocation par tick dans les implementations (listes reutilisees).
 */
public abstract class Partie {
    /** Phases communes ; un mode peut avoir son propre sous-etat (manche, vague...) en plus. */
    public enum Phase {
        /** Creee, joueurs en cours de transfert (coffre, teleportation, pose de l'instance). */
        ATTENTE,
        /** Choix de classe (10 s ClashWar, 5 s duels). */
        CHOIX,
        /** Preparation : joueurs immobilises derriere la barriere, compte a rebours. */
        PREPARATION,
        /** Combat. */
        COMBAT,
        /** Prolongation / mort subite (zone qui se resserre...). */
        PROLONGATION,
        /** Fin de jeu : annonces, ecran des scores, killcam finale ; plus aucun degat. */
        FIN,
        /** Fermee : plus de tick, retiree du moteur. */
        TERMINEE
    }

    private final String id;
    private final String mode;
    private final MinecraftServer serveur;
    protected final List<Equipe> equipes = new ArrayList<>();
    protected final Set<UUID> spectateurs = new LinkedHashSet<>();
    private Phase phase = Phase.ATTENTE;
    private int ticksPhase;
    private long ticks;
    private final long debutMs = System.currentTimeMillis();
    @Nullable
    private Instance instance;
    @Nullable
    private Equipe gagnante;

    /**
     * @param id      identifiant unique ("clashwar-12-m3", "duel-4f2a"...) ; sert aussi de marque des objets pretes
     *                ({@link Equipement#donner(ServerPlayer, Kit, String)})
     * @param mode    "clashwar", "duel" ou "zombies"
     * @param serveur serveur courant
     */
    protected Partie(String id, String mode, MinecraftServer serveur) {
        this.id = id;
        this.mode = mode;
        this.serveur = serveur;
    }

    // ------------------------------------------------------------------ a implementer par le mode
    /** Appele a chaque tick serveur (phase de fin) tant que la partie n'est pas TERMINEE. Ne pas allouer. */
    public abstract void tick();

    /**
     * Le joueur aurait du mourir (degats >= sante + absorption, vide, mort subite) : le moteur a annule la mort ;
     * le mode decide (spectateur, reapparition, a terre...). Le moteur ne touche ni a la sante ni a la position.
     *
     * @param victime joueur de la partie
     * @param tueur   auteur reel (proprietaire du projectile ou de l'explosif), null si aucun (chute, vide, zone)
     * @param source  source des degats d'origine
     */
    public abstract void surElimination(ServerPlayer victime, @Nullable LivingEntity tueur, DamageSource source);

    /** Le joueur se deconnecte pendant la partie (son Coffre reste sur disque : rendu au retour si la partie est finie). */
    public abstract void surDeconnexion(ServerPlayer joueur);

    // ------------------------------------------------------------------ crochets facultatifs
    /**
     * Le joueur se reconnecte alors que la partie existe encore. Rendre vrai pour le reprendre dans la partie ; faux
     * (defaut) = le moteur lui rend son Coffre et le retire ({@link MatchApi#quitter}).
     */
    public boolean surReconnexion(ServerPlayer joueur) {
        return false;
    }

    /**
     * Degats subis par un joueur de la partie (avant le test d'elimination). Rend le montant a appliquer (0 = annule :
     * tir ami, preparation...). Defaut : 0 hors COMBAT/PROLONGATION, sinon inchange.
     */
    public float surDegats(ServerPlayer victime, @Nullable LivingEntity attaquant, DamageSource source, float montant) {
        return phase == Phase.COMBAT || phase == Phase.PROLONGATION ? montant : 0f;
    }

    /** Le joueur est sorti des bornes de l'instance. Defaut : elimination (source = vide). */
    public void surSortieBornes(ServerPlayer joueur) {
        surElimination(joueur, null, joueur.damageSources().fellOutOfWorld());
    }

    /** Casse de bloc autorisee dans l'instance ? Defaut : non. */
    public boolean peutCasser(ServerPlayer joueur, BlockPos pos, BlockState etat) {
        return false;
    }

    /** Pose de bloc autorisee dans l'instance ? Defaut : non. */
    public boolean peutPoser(ServerPlayer joueur, BlockPos pos, BlockState etat) {
        return false;
    }

    /** Appele par {@link #terminer(Equipe)} : annonces, recompenses, stats, ecran de fin. Defaut : rien. */
    protected void surFin(@Nullable Equipe gagnante) {
    }

    // ------------------------------------------------------------------ cycle de vie
    /**
     * Fin du jeu : retient la gagnante (null = aucune, partie annulee), passe en {@link Phase#FIN} et appelle
     * {@link #surFin}. Sans effet si la partie est deja en FIN ou TERMINEE.
     */
    public void terminer(@Nullable Equipe gagnante) {
        if (phase == Phase.FIN || phase == Phase.TERMINEE) return;
        this.gagnante = gagnante;
        phase(Phase.FIN);
        surFin(gagnante);
    }

    /**
     * Ferme la partie : phase TERMINEE, retrait du moteur ({@link MatchApi#retirer}) et liberation de l'instance. Les
     * joueurs doivent avoir ete rendus avant ({@link Coffre#rendre}, {@link MatchApi#quitter}).
     */
    public void fermer() {
        if (phase == Phase.TERMINEE) return;
        phase(Phase.TERMINEE);
        MatchApi.retirer(this);
        if (instance != null) {
            Arenes.liberer(instance);
            instance = null;
        }
    }

    // ------------------------------------------------------------------ etat
    public String id() {
        return id;
    }

    /** "clashwar", "duel" ou "zombies". */
    public String mode() {
        return mode;
    }

    public MinecraftServer serveur() {
        return serveur;
    }

    public Phase phase() {
        return phase;
    }

    /** Change de phase et remet {@link #ticksPhase()} a zero. */
    public void phase(Phase nouvelle) {
        this.phase = nouvelle;
        this.ticksPhase = 0;
    }

    /** Ticks ecoules dans la phase courante (incremente par le moteur avant {@link #tick()}). */
    public int ticksPhase() {
        return ticksPhase;
    }

    /** Ticks ecoules depuis la creation (incremente par le moteur). */
    public long ticks() {
        return ticks;
    }

    /** Reserve au moteur : avance les compteurs d'un tick. */
    public final void avancer() {
        ticks++;
        ticksPhase++;
    }

    public long debutMs() {
        return debutMs;
    }

    /** Equipes (ordre d'ajout), non modifiable. */
    public List<Equipe> equipes() {
        return Collections.unmodifiableList(equipes);
    }

    public void ajouterEquipe(Equipe e) {
        equipes.add(e);
    }

    /** Equipe du joueur, ou null. */
    @Nullable
    public Equipe equipeDe(UUID joueur) {
        for (int i = 0; i < equipes.size(); i++) {
            Equipe e = equipes.get(i);
            if (e.contient(joueur)) return e;
        }
        return null;
    }

    /** Vrai si le joueur est inscrit dans une equipe de la partie. */
    public boolean contient(UUID joueur) {
        return equipeDe(joueur) != null;
    }

    /** Tous les joueurs inscrits (nouvelle liste). */
    public List<UUID> joueurs() {
        List<UUID> l = new ArrayList<>();
        for (Equipe e : equipes) l.addAll(e.joueurs());
        return l;
    }

    /** Joueurs inscrits et connectes (nouvelle liste). */
    public List<ServerPlayer> joueursEnLigne() {
        List<ServerPlayer> l = new ArrayList<>();
        for (Equipe e : equipes) {
            for (UUID u : e.joueurs()) {
                ServerPlayer p = serveur.getPlayerList().getPlayer(u);
                if (p != null) l.add(p);
            }
        }
        return l;
    }

    /** Joueur connecte par UUID, ou null. */
    @Nullable
    public ServerPlayer joueur(UUID u) {
        return serveur.getPlayerList().getPlayer(u);
    }

    /** Equipes ayant encore au moins un vivant (nouvelle liste). */
    public List<Equipe> equipesEnVie() {
        List<Equipe> l = new ArrayList<>();
        for (Equipe e : equipes) if (e.enVie()) l.add(e);
        return l;
    }

    /** Spectateurs (eliminés qui regardent, visiteurs), modifiable par le mode. */
    public Set<UUID> spectateurs() {
        return spectateurs;
    }

    @Nullable
    public Instance instance() {
        return instance;
    }

    /** Associe l'instance d'arene (marquee occupee par cette partie). */
    public void instance(@Nullable Instance instance) {
        this.instance = instance;
        if (instance != null) instance.occuper(id);
    }

    /** Gagnante retenue par {@link #terminer(Equipe)}, ou null. */
    @Nullable
    public Equipe gagnante() {
        return gagnante;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + " " + phase + " " + equipes + "]";
    }
}
