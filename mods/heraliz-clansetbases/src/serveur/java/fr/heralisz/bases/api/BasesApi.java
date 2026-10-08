package fr.heralisz.bases.api;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.combat.CombatLocal;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.EntreeJour;
import fr.heralisz.bases.model.Plan;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.monde.Modele;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.server.BaseService;
import fr.heralisz.bases.server.BasesStore;
import fr.heralisz.bases.server.Fenetre;
import fr.heralisz.bases.server.Sync;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Facade du coeur pour les modules du meme mod (pillage, interfaces) et pour les autres mods. Fil serveur uniquement
 * (sauf {@link #pillage(PillageHooks)}, appele pendant la construction du mod). Rien ici n'envoie de paquet sans le dire.
 */
public final class BasesApi {
    private BasesApi() {
    }

    private static PillageHooks pillage = PillageHooks.AUCUN;

    // ------------------------------------------------------------------ extension pillage
    /** Installe les regles du module pillage (une fois, depuis PillageModule.init). */
    public static void pillage(PillageHooks hooks) {
        pillage = hooks == null ? PillageHooks.AUCUN : hooks;
        HeraliszBases.LOG.info("[bases] module pillage branche : {}", pillage.getClass().getName());
    }

    public static PillageHooks pillage() {
        return pillage;
    }

    // ------------------------------------------------------------------ lecture
    /** Base d'un clan (null s'il n'en a pas). */
    @Nullable
    public static Base base(UUID clan) {
        BasesStore s = BasesStore.get();
        return s == null ? null : s.base(clan);
    }

    /** Toutes les bases (vue non modifiable ; vide hors serveur). */
    public static Collection<Base> bases() {
        BasesStore s = BasesStore.get();
        return s == null ? List.of() : s.bases();
    }

    /** Base qui contient ce point (dimension des bases seulement). */
    @Nullable
    public static Base baseA(Level level, BlockPos pos) {
        return BaseService.baseA(level, pos);
    }

    /** Vrai si le niveau est la dimension des bases. */
    public static boolean dansBunkers(@Nullable Level level) {
        return Grille.estBunkers(level);
    }

    /** Plan du bunker (null si le modele n'est pas charge). */
    @Nullable
    public static Plan plan() {
        Modele m = Modele.get();
        return m == null ? null : m.plan;
    }

    /** Coin de la structure de la base (coordonnees relatives de bunker.json = pos - origine). */
    public static BlockPos origine(Base b) {
        return BaseService.origine(b);
    }

    /** Vrai si le point est dans le volume constructible (salles ouvertes, hall, couloirs) : hors de la coque. */
    public static boolean constructible(Base b, BlockPos pos) {
        return BaseService.constructible(b, pos);
    }

    /** Zone du point (id de salle, hall, couloir, sas_nord...). */
    public static String zone(Base b, BlockPos pos) {
        return BaseService.zone(b, pos);
    }

    /** Op en creatif / spectateur (ignore les regles). */
    public static boolean estAdmin(ServerPlayer p) {
        return BaseService.estAdmin(p);
    }

    /** Membre du clan proprietaire. */
    public static boolean estMembre(ServerPlayer p, Base b) {
        return BaseService.estMembre(p, b);
    }

    // ------------------------------------------------------------------ fenetre et entrees du jour
    public static boolean fenetreOuverte() {
        return Fenetre.ouverte();
    }

    /** Fin de la fenetre ouverte (epoch ms), 0 si fermee ou forcee. */
    public static long finFenetreMs() {
        return Fenetre.finMs();
    }

    /** Cle du jour de pillage (change a reset_heure). */
    public static String jour() {
        return Fenetre.jour();
    }

    @Nullable
    public static EntreeJour entreeJour(UUID joueur) {
        BasesStore s = BasesStore.get();
        return s == null ? null : s.entreeJour(joueur);
    }

    /** Enregistre l'entree du jour d'un joueur (bases.json marque modifie). */
    public static void entreeJour(EntreeJour e) {
        BasesStore s = BasesStore.get();
        if (s != null) s.entreeJour(e);
    }

    /** Signale une modification d'une base (journal de pillage, extra) : sauvegardee au prochain autosave. */
    public static void modifiee(Base b) {
        BasesStore s = BasesStore.get();
        if (s != null) s.marquerDirty();
    }

    // ------------------------------------------------------------------ combat
    /** Tague en combat (tag local, ou celui de HeraliZ Matchs s'il est present). */
    public static boolean tagueCombat(ServerPlayer p) {
        return CombatLocal.tague(p);
    }

    public static long combatRestantMs(ServerPlayer p) {
        return CombatLocal.restantMs(p);
    }

    // ------------------------------------------------------------------ deplacements
    /**
     * Entree differee (10 s immobile, refus si combat / a terre / base pas prete), genre PILLAGE pour le module pillage.
     * {@code encore} est reverifie a l'echeance ; {@code apres} est appele juste apres l'arrivee (enregistrer le pillard
     * AVANT le controle de presence suivant). {@code entree} null = nord.
     */
    public static boolean programmerEntree(ServerPlayer p, Base b, @Nullable String entree, Packets.TeleportS2C.Genre genre,
                                           Predicate<ServerPlayer> encore, @Nullable Consumer<ServerPlayer> apres) {
        return BaseService.programmerEntree(p, b, entree, genre, encore, apres);
    }

    /** Ramene le joueur a sa position d'entree (ou au spawn), sans delai. */
    public static void ramener(ServerPlayer p) {
        BaseService.ramener(p);
    }

    /** Ramene le joueur avec un message (cle de traduction). */
    public static void ejecter(ServerPlayer p, String cle) {
        BaseService.ejecter(p, cle);
    }

    // ------------------------------------------------------------------ messages et ecrans
    public static void erreur(ServerPlayer p, String cle, Object... args) {
        BaseService.erreur(p, cle, args);
    }

    public static void info(ServerPlayer p, String cle, Object... args) {
        BaseService.info(p, cle, args);
    }

    /** Refus discret (1 par seconde au plus). */
    public static void refus(ServerPlayer p, String cle) {
        BaseService.refus(p, cle);
    }

    /** Notice + chat aux membres en ligne d'un clan. */
    public static void aClan(UUID clan, Packets.NoticeS2C notice, @Nullable net.minecraft.network.chat.Component chat) {
        BaseService.aClan(clan, notice, chat);
    }

    /** Renvoie l'etat de la base a tous les membres en ligne d'un clan (apres un changement de pillage). */
    public static void synchroniser(UUID clan) {
        Sync.clan(clan);
    }
}
