package fr.heralisz.clans.api;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Grade;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Mouvement;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.server.ClanService;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.Sync;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static fr.heralisz.clans.api.ClansApi.JOURNAL_ARGS_MAX;
import static fr.heralisz.clans.api.ClansApi.NOM_SYSTEME;

/**
 * Partie serveur de {@link ClansApi} : lectures et ecritures sur les clans du serveur (store, banque, journal,
 * permissions). Corps d'origine de l'API, inchanges ; branchee par {@code clans.serveur.PartieServeur} (jars -serveur
 * et -complet).
 */
public final class ClansApiServeur implements ClansApi.Moteur {
    private ClansApiServeur() {
    }

    /** Branche les clans du serveur sur l'API (appele une fois, pendant la construction du mod). */
    public static void installer() {
        ClansApi.moteur = new ClansApiServeur();
    }

    @Nullable
    private static ClanStore store() {
        return ClanStore.get();
    }

    @Nullable
    private static Clan clan(@Nullable UUID clanId) {
        ClanStore s = store();
        return s == null || clanId == null ? null : s.get(clanId);
    }

    /** Vrai si les donnees des clans sont chargees (serveur demarre). */
    @Override
    public boolean disponible() {
        return store() != null;
    }

    /** Clan du joueur. */
    @Override
    public Optional<UUID> clanDe(UUID joueur) {
        ClanStore s = store();
        Clan c = s == null || joueur == null ? null : s.clanDe(joueur);
        return c == null ? Optional.empty() : Optional.of(c.id);
    }

    /** Photo d'un clan (vide s'il n'existe pas, ou plus). */
    @Override
    public Optional<ClanInfo> infos(UUID clanId) {
        Clan c = clan(clanId);
        return c == null ? Optional.empty() : Optional.of(photo(c, ServerLifecycleHooks.getCurrentServer()));
    }

    /** Clan par tag (insensible a la casse). */
    @Override
    public Optional<ClanInfo> parTag(String tag) {
        ClanStore s = store();
        Clan c = s == null || tag == null ? null : s.parTag(tag);
        return c == null ? Optional.empty() : Optional.of(photo(c, ServerLifecycleHooks.getCurrentServer()));
    }

    /** Photos de tous les clans (ordre quelconque). */
    @Override
    public List<ClanInfo> tous() {
        ClanStore s = store();
        if (s == null) return List.of();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<ClanInfo> l = new ArrayList<>(s.tous().size());
        for (Clan c : s.tous()) l.add(photo(c, server));
        return Collections.unmodifiableList(l);
    }

    /** Grade du joueur dans son clan. */
    @Override
    public Optional<Grade> grade(UUID joueur) {
        ClanStore s = store();
        Clan c = s == null || joueur == null ? null : s.clanDe(joueur);
        return c == null ? Optional.empty() : Optional.ofNullable(c.gradeDe(joueur));
    }

    /**
     * Vrai si le joueur a la permission dans SON clan (le chef les a toutes ; faux sans clan). Tient compte des
     * reglages individuels : un reglage AUTORISE / REFUSE du joueur l'emporte sur la permission de son grade.
     */
    @Override
    public boolean aPerm(UUID joueur, Perm perm) {
        ClanStore s = store();
        Clan c = s == null || joueur == null ? null : s.clanDe(joueur);
        return c != null && perm != null && c.peut(joueur, perm);
    }

    /**
     * Vrai si le joueur est membre du clan donne et y a la permission (ex. : agir dans la base de ce clan), reglages
     * individuels compris (voir {@link #aPerm(UUID, Perm)}).
     */
    @Override
    public boolean aPerm(UUID joueur, UUID clanId, Perm perm) {
        Clan c = clan(clanId);
        return c != null && joueur != null && perm != null && c.peut(joueur, perm);
    }

    /**
     * Reglage individuel d'une permission pour un membre : {@link Perm.Etat#HERITE} (comme son grade, aussi pour un
     * clan ou un joueur inconnu, un non-membre et le chef), {@link Perm.Etat#AUTORISE} ou {@link Perm.Etat#REFUSE}.
     * Pour savoir si le joueur PEUT, utiliser {@link #aPerm(UUID, UUID, Perm)}.
     */
    @Override
    public Perm.Etat etatPermJoueur(UUID clanId, UUID membre, Perm perm) {
        Clan c = clan(clanId);
        return c == null ? Perm.Etat.HERITE : c.etatPerm(membre, perm);
    }

    /**
     * Regle une permission pour un membre (HERITE = comme son grade). {@code acteur} = le joueur qui fait le reglage,
     * soumis aux memes regles que l'editeur du clan : il a GERER_PERMISSIONS (defaut chef et haut grade), son grade
     * est strictement superieur a celui du membre, et il n'accorde pas une permission qu'il n'a pas lui-meme ;
     * {@code acteur} null = systeme (commande admin, mod appelant) : seules restent les regles « membre du clan » et
     * « le chef n'est pas reglable ». Journal du clan, snapshot aux membres, clans.json sauvegarde a l'autosave. Vrai
     * si le reglage est permis (applique, ou deja dans cet etat) ; faux sinon (rien n'est fait).
     */
    @Override
    public boolean definirPermJoueur(@Nullable UUID acteur, UUID clanId, UUID membre, Perm perm, Perm.Etat etat) {
        Clan c = clan(clanId);
        if (c == null || membre == null || perm == null || etat == null) return false;
        return ClanService.definirPermJoueur(acteur, c, membre, perm, etat);
    }

    /** Nom affiche d'un joueur : en ligne, sinon dernier pseudo connu par les clans, sinon debut de l'UUID. */
    @Override
    public String nomJoueur(UUID joueur) {
        return ClanService.nomDe(joueur);
    }

    /** Solde de la banque du clan (0 s'il n'existe pas). */
    @Override
    public long soldeBanque(UUID clanId) {
        Clan c = clan(clanId);
        return c == null ? 0 : c.banque;
    }

    /**
     * Retire {@code montant} (> 0) de la banque du clan si elle le contient : ligne de journal de banque (motif = cle
     * courte, affichee via « banque.motif.&lt;motif&gt; » si la traduction existe, sinon telle quelle ; auteur = joueur qui a
     * declenche l'achat, ou null), snapshot aux membres, clans.json ecrit tout de suite. Faux (rien n'est fait) si le
     * clan n'existe pas, si le montant est invalide ou si la banque ne suffit pas. Les jetons debites disparaissent
     * (achat) : a l'appelant de les crediter ailleurs s'il le faut, APRES ce debit.
     */
    @Override
    public boolean debiterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur) {
        ClanStore s = store();
        Clan c = clan(clanId);
        if (s == null || c == null || montant <= 0 || c.banque < montant) return false;
        c.banque -= montant;
        c.ajouterMouvement(new Mouvement(System.currentTimeMillis(), auteur, auteur == null ? NOM_SYSTEME : ClanService.nomDe(auteur),
                -montant, motif == null ? "" : motif));
        s.marquerDirty();
        Sync.marquer(c);
        s.sauvegarderMaintenant();
        HeraliszClans.LOG.info("[clans] api : banque de [{}] debitee de {} ({})", c.tag, montant, motif);
        return true;
    }

    /**
     * Ajoute {@code montant} (> 0) a la banque du clan, tronque au plafond {@link ClanService#BANQUE_MAX} (la perte est
     * journalisee dans le log serveur) ; journal de banque, snapshot, clans.json ecrit tout de suite. Renvoie les jetons
     * reellement credites (0 si le clan n'existe pas ou si le montant est invalide). Si les jetons viennent d'un
     * compte joueur, le debiter (et le sauvegarder) AVANT d'appeler cette methode.
     */
    @Override
    public long crediterBanque(UUID clanId, long montant, String motif, @Nullable UUID auteur) {
        ClanStore s = store();
        Clan c = clan(clanId);
        if (s == null || c == null || montant <= 0) return 0;
        long avant = c.banque;
        c.banque = ClanService.plafonnerBanque(avant + Math.min(montant, ClanService.BANQUE_MAX));
        long credite = c.banque - avant;
        if (credite < montant) {
            HeraliszClans.LOG.warn("[clans] api : banque de [{}] pleine, {} jetons perdus ({})", c.tag, montant - credite, motif);
        }
        if (credite > 0) {
            c.ajouterMouvement(new Mouvement(System.currentTimeMillis(), auteur, auteur == null ? NOM_SYSTEME : ClanService.nomDe(auteur),
                    credite, motif == null ? "" : motif));
        }
        s.marquerDirty();
        Sync.marquer(c);
        s.sauvegarderMaintenant();
        HeraliszClans.LOG.info("[clans] api : banque de [{}] creditee de {} ({})", c.tag, credite, motif);
        return credite;
    }

    /**
     * Compte un ClashWar dispute par le clan : clashwarJoues +1, et clashwarGagnes +1 si {@code gagne}. A appeler une
     * seule fois par clan participant et par tournoi (le vainqueur avec {@code gagne} vrai). Faux si le clan n'existe pas.
     */
    @Override
    public boolean ajouterClashWar(UUID clanId, boolean gagne) {
        ClanStore s = store();
        Clan c = clan(clanId);
        if (s == null || c == null) return false;
        c.clashwarJoues++;
        if (gagne) c.clashwarGagnes++;
        s.marquerDirty();
        Sync.marquer(c);
        return true;
    }

    /**
     * Ajoute une ligne au journal d'activite du clan (onglet Apercu, 20 dernieres). {@code cle} = cle de traduction
     * fournie par le mod appelant dans ses propres fichiers lang (ex. « heralisz_bases.journal.salle_debloquee ») ;
     * {@code args} = chaines inserees (%s), 6 au plus, 96 caracteres chacune. Faux si le clan n'existe pas.
     */
    @Override
    public List<net.minecraft.world.item.ItemStack> deposerCoffre(UUID clanId, List<net.minecraft.world.item.ItemStack> objets) {
        return fr.heralisz.clans.server.CoffreClan.deposer(clanId, objets);
    }

    @Override
    public boolean journal(UUID clanId, String cle, String... args) {
        ClanStore s = store();
        Clan c = clan(clanId);
        if (s == null || c == null || cle == null || cle.isEmpty()) return false;
        int n = args == null ? 0 : Math.min(args.length, JOURNAL_ARGS_MAX - 1);
        String[] tout = new String[n + 1];
        tout[0] = cle;
        for (int i = 0; i < n; i++) tout[i + 1] = args[i] == null ? "" : args[i];
        c.ajouterEvenement(new Evenement(System.currentTimeMillis(), Evenement.Genre.PERSO, tout));
        s.marquerDirty();
        Sync.marquer(c);
        return true;
    }

    private static ClanInfo photo(Clan c, @Nullable MinecraftServer server) {
        Map<UUID, Grade> membres = new LinkedHashMap<>();
        Map<UUID, String> noms = new LinkedHashMap<>();
        Set<UUID> enLigne = new HashSet<>();
        for (Membre m : c.membres.values()) {
            membres.put(m.uuid, c.estChef(m.uuid) ? Grade.CHEF : m.grade);
            noms.put(m.uuid, m.dernierNom);
            if (server != null && server.getPlayerList().getPlayer(m.uuid) != null) enLigne.add(m.uuid);
        }
        return new ClanInfo(c.id, c.tag, c.nom, c.banniere, c.chef, Collections.unmodifiableMap(membres),
                Collections.unmodifiableMap(noms), Collections.unmodifiableSet(enLigne), c.niveau, c.clashwarJoues, c.clashwarGagnes);
    }
}
