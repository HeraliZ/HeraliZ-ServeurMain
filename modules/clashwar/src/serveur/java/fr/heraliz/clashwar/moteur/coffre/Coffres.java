package fr.heraliz.clashwar.moteur.coffre;

import fr.heraliz.clashwar.HeralizClashWar;
import fr.heraliz.clashwar.moteur.Arrivees;
import fr.heraliz.clashwar.moteur.api.Coffre;
import fr.heraliz.noyau.api.CombatTag;
import fr.heraliz.clashwar.moteur.api.Equipement;
import fr.heraliz.clashwar.moteur.api.Hud;
import fr.heraliz.clashwar.moteur.api.MatchApi;
import fr.heraliz.clashwar.moteur.api.Partie;
import fr.heraliz.clashwar.moteur.compat.Compat;
import fr.heraliz.noyau.api.ATerre;
import fr.heraliz.clashwar.moteur.compat.Tactical;
import fr.heraliz.clashwar.moteur.spectateur.Spectateurs;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Implementation du {@link Coffre} (MATCHS_SPEC §1.1) : instantane complet du joueur sur disque, vidage, restauration,
 * reprise apres crash. Format du fichier {@code <serveur>/heraliz_clashwar/coffres/<uuid>.dat} (NbtIo compresse) :
 * <pre>
 * version, raison, date (ms), uuid, nom,
 * inventaire (Inventory.save), selection, curios (saveInventory(false), absent sans Curios),
 * sante, absorption, faim (FoodData), xpNiveau, xpProgres, xpTotal, effets (MobEffectInstance.save), mode (GameType),
 * dim, x, y, z, yaw, pitch, soif (IThirst.serializeNBT, absent sans le mod), brut (saveWithoutId, pour enquete),
 * a_cote (02/11 : objets tenus au curseur ou dans la grille 2 x 2 de l'inventaire au depot, absent s'il n'y en a pas),
 * numero (version 2, 15/11 : numero du depot, le meme que la marque {@link Coffre#CLE_MARQUE} posee sur le joueur)
 * </pre>
 * Objets a vie (02/11, audit point 8) : rien ne tombe au sol au depot (curseur et grille gardes dans le depot), et un
 * objet a vie qui n'est pas un pret du match retrouve dans l'inventaire du match au retour (rendu par HeraliZ Tactique
 * apres une question « Jeter cet objet ? » restee ouverte, par exemple) est rendu avec le reste, jamais efface.
 * Ecriture : octets prepares en memoire, ecrits dans {@code <uuid>.dat.tmp}, synchronises sur le disque (fsync), relus
 * et compares, puis deplaces par ATOMIC_MOVE. Un depot illisible ou une restauration incomplete deplace le fichier dans
 * {@code coffres/enquete/} (jamais supprime).
 * <p>Jamais de double, jamais de perte (15/11, audit clans_bases M4) : apres le vidage, le joueur recoit la marque du depot
 * ({@link Coffre#CLE_MARQUE} = son numero) et il est enregistre tout de suite (avant : pas avant la sauvegarde
 * automatique suivante ; un plantage entre les deux rendait le depot a un joueur qui avait encore tout, objets a vie
 * doubles). Le depot n'est rendu qu'a un joueur qui porte SA marque : marque absente = le joueur n'a jamais ete enregistre
 * vide (plantage juste apres le depot : il a encore son inventaire) ou le depot lui a deja ete rendu (plantage entre son
 * enregistrement et l'effacement du fichier) ; le fichier part alors en enquete, jamais rendu une 2e fois. Au retour :
 * marque retiree, joueur enregistre, PUIS fichier efface. Ce qu'un ecran ouvert ferait tomber au depot (inventaire plein)
 * est garde dans le depot ({@code a_cote}), jamais au sol. Fil serveur.
 * <p><b>Depots et retours etales (19/11, audit des lenteurs C3)</b> : avant, chaque joueur d'un tour passait dans le MEME
 * tick par deux releves NBT complets, une compression, une ecriture attendue jusqu'au disque (fsync), une relecture et une
 * teleportation vers un endroit souvent pas charge (114 ms puis 1,5 s le 18/11 avec trois joueurs). Maintenant :
 * <ul>
 *   <li>{@link #deposerPuis} : le depot est mis en file ; a son tour (au plus {@code coffres_par_tick} operations et
 *   {@code coffres_budget_ms} par tick), l'instantane est releve sur le fil serveur, puis compresse, ecrit, synchronise et
 *   verifie sur le fil des coffres. Le joueur est FIGE pendant ce temps (rien ne sort ni n'entre dans son inventaire : objet
 *   lache retenu, ramassage, clics, utilisation d'objet, coups et commandes refuses) ; il n'est vide que quand le fil a
 *   confirme l'ecriture (« disque avant toute modification » tient toujours). Ecriture ratee : rien n'est change. Parti
 *   avant la confirmation : jamais vide, il est enregistre avec tout ; le fichier est efface.</li>
 *   <li>{@link #rendrePuis} : le fichier est lu d'avance sur le fil des coffres, l'arrivee est prechargee
 *   ({@link Arrivees}), puis {@link #rendre} se fait a son tour, dans la meme limite par tick.</li>
 * </ul>
 * Reglage {@code coffres_en_fond} de clashwar.json (faux : depot ecrit sur le fil serveur, comme avant, mais toujours
 * etale). Fil serveur, sauf les taches du fil des coffres (qui ne touchent a aucun objet du jeu).
 */
public final class Coffres {
    private Coffres() {
    }

    /** 2 (15/11) : numero du depot et marque du joueur ; un depot de version 1 (sans numero) est rendu comme avant. */
    public static final int VERSION = 2;
    /** Etiquette de joueur « reste allonge » (lue par Reanimation, posee par les Zombies a terre). */
    private static final String RESTE_ALLONGE = "heraliz_reste_allonge";
    private static Method sauverJoueur;
    /** Joueur dont l'ecran se ferme pendant son depot : ce qui tomberait au sol est garde dans {@link #captures}. */
    @Nullable
    private static ServerPlayer capture;
    @Nullable
    private static List<ItemStack> captures;

    // ------------------------------------------------------------------ depots et retours etales (19/11)
    /** Fil des coffres : ecritures des depots et lectures d'avance des retours, un seul, dans l'ordre. */
    private static final ExecutorService FIL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-ClashWar-coffres");
        t.setDaemon(true);
        return t;
    });
    /** Un depot en ecriture qui n'a pas repondu au bout de ce delai (ticks) est abandonne (le joueur n'est pas vide). */
    private static final int ATTENTE_ECRITURE_MAX = 20 * 30;
    /** Un retour attend son arrivee au plus ce delai (ticks) ; passe ce delai, il se fait quand meme. */
    private static final int ATTENTE_RETOUR_MAX = 60;
    /**
     * Depots en ecriture au plus en meme temps : chaque joueur n'est fige que le temps de quelques ecritures (les suivants
     * attendent leur tour sans etre figes, rien n'est encore releve pour eux).
     */
    private static final int MAX_EN_ECRITURE = 8;

    /** Reglages (clashwar.json, poses par le tournoi : {@link #regler}). */
    private static boolean enFond = true;
    private static int parTick = 4;
    private static long budgetNs = 6_000_000L;

    /** Un depot demande, pas encore commence (releve a son tour). */
    private record Demande(ServerPlayer joueur, String raison, Consumer<Boolean> fini) {
    }

    /** Un depot releve, en ecriture sur le fil des coffres ; son joueur est fige jusqu'a la confirmation. */
    private static final class Depot {
        final ServerPlayer joueur;
        final String raison;
        final long numero;
        final CompoundTag instantane;
        final List<ItemStack> aCote;
        /** Objets laches pendant l'ecriture (deja dans l'instantane) : rendus seulement si le depot rate. */
        final List<ItemStack> retenus = new ArrayList<>(0);
        final Path fichier;
        final Future<Long> ecrit;
        final Consumer<Boolean> fini;
        final int depuis;

        Depot(ServerPlayer joueur, String raison, long numero, CompoundTag instantane, List<ItemStack> aCote, Path fichier, Future<Long> ecrit,
              Consumer<Boolean> fini, int depuis) {
            this.joueur = joueur;
            this.raison = raison;
            this.numero = numero;
            this.instantane = instantane;
            this.aCote = aCote;
            this.fichier = fichier;
            this.ecrit = ecrit;
            this.fini = fini;
            this.depuis = depuis;
        }
    }

    /** Un retour demande : fichier lu d'avance, arrivee prechargee, puis rendu a son tour. */
    private static final class Retour {
        final ServerPlayer joueur;
        @Nullable
        final Future<CompoundTag> lu;
        final Consumer<Boolean> fini;
        final int depuis;
        @Nullable
        ServerLevel niveau;
        double x, z;
        boolean prechargee;

        Retour(ServerPlayer joueur, @Nullable Future<CompoundTag> lu, Consumer<Boolean> fini, int depuis) {
            this.joueur = joueur;
            this.lu = lu;
            this.fini = fini;
            this.depuis = depuis;
        }
    }

    /** Position d'arrivee d'un retour (monde, x, z), notee au depot : prechargee sans attendre la lecture du fichier. */
    private record Position(String dim, double x, double z) {
    }

    private static final ArrayDeque<Demande> DEMANDES = new ArrayDeque<>();
    private static final Map<UUID, Depot> EN_ECRITURE = new LinkedHashMap<>();
    /** Depots abandonnes (ecriture trop longue) : leur fichier est efface des que l'ecriture finit. */
    private static final List<Depot> ABANDONNES = new ArrayList<>();
    private static final ArrayDeque<Retour> RETOURS = new ArrayDeque<>();
    private static final Map<UUID, Position> POSITIONS = new HashMap<>();
    /**
     * Joueurs avec un depot demande ou en ecriture ({@link #DEPOSANTS}), ou un retour demande ({@link #RENDUS}) : ni
     * commandes ni ramassage pendant ce temps, et la garde hors match les attend.
     */
    private static final Set<UUID> DEPOSANTS = new HashSet<>(), RENDUS = new HashSet<>();
    /** Tampons reutilises du tick. */
    private static final List<Depot> FINIS = new ArrayList<>();
    private static int ticks;
    /** Depuis le dernier compte rendu : depots faits, rates, retours faits ; temps d'ecriture (ns) cumule et pire. */
    private static int depotsFaits, depotsRates, retoursFaits;
    private static long ecritureNs, ecritureMaxNs;

    /**
     * Reglages de clashwar.json (relus au demarrage et par {@code /recharger clashwar}) : depot ecrit en fond, operations
     * au plus par tick, temps au plus par tick (ms ; au moins une operation par tick).
     */
    public static void regler(boolean fond, int max, int budgetMs) {
        enFond = fond;
        parTick = Math.max(1, max);
        budgetNs = Math.max(1, budgetMs) * 1_000_000L;
    }

    /** Ecouteurs du depot (branches par le moteur dans le constructeur du mod). */
    public static void enregistrer() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::lache);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::ramasse);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::clicBloc);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::clicObjet);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::clicEntite);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::clicEntiteVise);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::frappeBloc);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::utilise);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::frappe);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::touche);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Coffres::commande);
        // files visibles dans /diag fuites (moniteur des listes du noyau)
        fr.heraliz.noyau.api.Fuites.surveiller("clashwar.coffres_en_cours", () -> DEMANDES.size() + EN_ECRITURE.size() + RETOURS.size());
    }

    /**
     * Objet que Minecraft fait tomber pendant la fermeture de l'ecran du depot (inventaire plein : cases d'un atelier,
     * d'une enclume...) : garde dans le depot avec les objets « a cote », jamais au sol. 19/11 : objet lache par un joueur
     * dont le depot s'ecrit (fige) : retenu, il est deja dans l'instantane (rendu seulement si le depot rate).
     */
    private static void lache(ItemTossEvent e) {
        Player joueur = e.getPlayer();
        ServerPlayer p = capture;
        List<ItemStack> l = captures;
        if (p != null && l != null && joueur == p) {
            ItemStack s = e.getEntity().getItem();
            if (s.isEmpty()) return;
            l.add(s.copy());
            e.setCanceled(true);
            HeralizClashWar.LOG.info("[matchs] {} : {} x{} garde dans le depot (il serait tombe a la fermeture de l'ecran)",
                    p.getGameProfile().getName(), s.getHoverName().getString(), s.getCount());
            return;
        }
        if (EN_ECRITURE.isEmpty() || joueur == null) return;
        Depot d = EN_ECRITURE.get(joueur.getUUID());
        if (d == null) return;
        ItemStack s = e.getEntity().getItem();
        e.setCanceled(true);
        if (!s.isEmpty()) d.retenus.add(s.copy());
    }

    /** Le joueur est-il fige (depot en ecriture) ? */
    private static boolean fige(@Nullable Player p) {
        return p != null && !EN_ECRITURE.isEmpty() && EN_ECRITURE.containsKey(p.getUUID());
    }

    /** Depot demande ou en ecriture, ou retour demande, pour ce joueur. */
    private static boolean occupe(UUID u) {
        return (!DEPOSANTS.isEmpty() && DEPOSANTS.contains(u)) || (!RENDUS.isEmpty() && RENDUS.contains(u));
    }

    private static void ramasse(EntityItemPickupEvent e) {
        if (occupe(e.getEntity().getUUID())) e.setCanceled(true);
    }

    private static void clicBloc(PlayerInteractEvent.RightClickBlock e) {
        if (!fige(e.getEntity())) return;
        e.setUseBlock(Event.Result.DENY);
        e.setUseItem(Event.Result.DENY);
        e.setCanceled(true);
    }

    private static void clicObjet(PlayerInteractEvent.RightClickItem e) {
        if (fige(e.getEntity())) e.setCanceled(true);
    }

    private static void clicEntite(PlayerInteractEvent.EntityInteract e) {
        if (fige(e.getEntity())) e.setCanceled(true);
    }

    private static void clicEntiteVise(PlayerInteractEvent.EntityInteractSpecific e) {
        if (fige(e.getEntity())) e.setCanceled(true);
    }

    private static void frappeBloc(PlayerInteractEvent.LeftClickBlock e) {
        if (fige(e.getEntity())) e.setCanceled(true);
    }

    private static void utilise(LivingEntityUseItemEvent.Start e) {
        if (e.getEntity() instanceof Player p && fige(p)) e.setCanceled(true);
    }

    private static void frappe(AttackEntityEvent e) {
        if (fige(e.getEntity())) e.setCanceled(true);
    }

    /** Fige : il ne prend aucun coup (une mort ferait tomber ce qui est deja dans l'instantane). */
    private static void touche(LivingAttackEvent e) {
        if (e.getEntity() instanceof Player p && fige(p)) e.setCanceled(true);
    }

    /** Depot ou retour en cours : aucune commande (quelques ticks au plus). */
    private static void commande(CommandEvent e) {
        if (DEPOSANTS.isEmpty() && RENDUS.isEmpty()) return;
        if (e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p && occupe(p.getUUID())
                && !p.hasPermissions(2)) {
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ marque du depot (15/11, audit M4)
    /** Numero du depot que porte le joueur (0 : aucun). */
    public static long marque(Player p) {
        CompoundTag t = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return t.contains(Coffre.CLE_MARQUE, Tag.TAG_LONG) ? t.getLong(Coffre.CLE_MARQUE) : 0L;
    }

    /** Pose (numero different de 0) ou retire (0) la marque du depot ; elle est enregistree avec le joueur. */
    private static void marquer(Player p, long numero) {
        CompoundTag racine = p.getPersistentData();
        if (numero == 0L) {
            if (racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
                racine.getCompound(Player.PERSISTED_NBT_TAG).remove(Coffre.CLE_MARQUE);
            }
            return;
        }
        if (!racine.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) racine.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        racine.getCompound(Player.PERSISTED_NBT_TAG).putLong(Coffre.CLE_MARQUE, numero);
    }

    private static long nouveauNumero() {
        long n;
        do {
            n = ThreadLocalRandom.current().nextLong();
        } while (n == 0L);
        return n;
    }

    // ------------------------------------------------------------------ refus
    /**
     * Voir {@link Coffre#refus}. Un passager de vehicule n'est plus refuse (RETOURS du 01/10 §0) : {@link #deposer} le
     * fait descendre ({@link Descente#forcer}) ; {@link Coffre.Refus#VEHICULE} ne sert plus que si cette descente echoue.
     * 19/11 : un depot ou un retour deja en cours compte comme un depot existant.
     */
    @Nullable
    public static Coffre.Refus refus(ServerPlayer p) {
        if (p.isDeadOrDying() || p.isRemoved()) return Coffre.Refus.MORT;
        if (CombatTag.estTague(p)) return Coffre.Refus.COMBAT;
        if (ATerre.aTerre(p)) return Coffre.Refus.A_TERRE;
        if (Coffre.aUnDepot(p.getUUID()) || occupe(p.getUUID())) return Coffre.Refus.DEPOT_EXISTANT;
        if (Coffre.dossier() == null) return Coffre.Refus.ERREUR;
        return null;
    }

    /** Depot demande, en ecriture, ou retour en attente pour ce joueur (19/11) : la garde hors match l'attend. */
    public static boolean enCours(UUID joueur) {
        return occupe(joueur);
    }

    // ------------------------------------------------------------------ depot
    /**
     * Voir {@link Coffre#deposer} : depot complet sur le fil serveur, tout de suite (outils du staff, API). Le tournoi
     * passe par {@link #deposerPuis}.
     */
    public static boolean deposer(ServerPlayer p, String raison) {
        Coffre.Refus r = refus(p);
        if (r != null) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, r);
            return false;
        }
        boolean[] ok = {false};
        commencer(new Demande(p, raison, b -> ok[0] = b), false);
        return ok[0];
    }

    /**
     * Depot etale (19/11) : refus tout de suite (rendu, rien n'est fait), sinon le depot est mis en file et {@code fini} est
     * appele plus tard sur le fil serveur : vrai = fichier ecrit et verifie PUIS joueur vide, marque et enregistre ; faux =
     * rien n'a change pour lui (refus au moment du releve, ecriture ratee, deconnexion). Rend null si le depot est en file.
     */
    @Nullable
    public static Coffre.Refus deposerPuis(ServerPlayer p, String raison, Consumer<Boolean> fini) {
        Coffre.Refus r = refus(p);
        if (r != null) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, r);
            return r;
        }
        DEPOSANTS.add(p.getUUID());
        DEMANDES.addLast(new Demande(p, raison, fini));
        return null;
    }

    /**
     * Releve de l'instantane (fil serveur) puis ecriture : sur le fil des coffres si {@code fond} (le joueur reste fige
     * jusqu'a la confirmation, {@link #confirmer}), sinon tout de suite, comme avant le 19/11.
     */
    private static void commencer(Demande d, boolean fond) {
        ServerPlayer p = d.joueur();
        String raison = d.raison();
        UUID u = p.getUUID();
        if (!present(p)) {
            finDepot(d.fini(), u, false);
            return;
        }
        // refus revu au moment du releve (le joueur a pu etre tague ou tomber depuis sa demande)
        DEPOSANTS.remove(u);
        Coffre.Refus r = refus(p);
        DEPOSANTS.add(u);
        if (r != null) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, r);
            finDepot(d.fini(), u, false);
            return;
        }
        // 0. passager d'un vehicule (train compris) : il descend, et l'instantane prend SA position, pas celle du vehicule
        if (!Descente.forcer(p)) {
            HeralizClashWar.LOG.info("[matchs] coffre refuse pour {} ({}) : {}", p.getGameProfile().getName(), raison, Coffre.Refus.VEHICULE);
            finDepot(d.fini(), u, false);
            return;
        }
        // 1. objets tenus au curseur et dans la grille 2 x 2 de l'inventaire : pris a part et gardes DANS le depot (02/11,
        //    audit point 8 : la fermeture de l'ecran les rendait a l'inventaire, ou les jetait au sol s'il etait plein ;
        //    un objet a vie etait alors mis de cote par Tactique, rendu dans l'inventaire du match et efface a la fin)
        List<ItemStack> aCote = horsInventaire(p);
        // puis fermer le conteneur ouvert : son contenu revient dans l'inventaire ; ce qui n'y tient pas (et tomberait au
        // sol) est garde avec les objets a cote (15/11)
        capture = p;
        captures = aCote;
        try {
            p.closeContainer();
        } finally {
            capture = null;
            captures = null;
        }
        // 2. forcer l'ecriture du sac (mixin addAdditionalSaveData de backpacked) : la copie brute sert aussi d'enquete
        CompoundTag brut = p.saveWithoutId(new CompoundTag());
        // 3. instantane
        CompoundTag t = instantane(p, raison, brut);
        long numero = nouveauNumero();
        t.putLong("numero", numero);
        if (!aCote.isEmpty()) {
            ListTag l = new ListTag();
            for (ItemStack s : aCote) l.add(s.save(new CompoundTag()));
            t.put("a_cote", l);
        }
        // 4. disque (atomique, verifie) AVANT toute modification du joueur
        Path f = Coffre.fichier(u);
        if (f == null) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} NON ecrit : joueur laisse intact", p.getGameProfile().getName());
            rendreACote(p, aCote);
            finDepot(d.fini(), u, false);
            return;
        }
        if (!fond) {
            long ns = ecrireMesure(f, t);
            confirmer(new Depot(p, raison, numero, t, aCote, f, null, d.fini(), ticks), ns);
            return;
        }
        // 19/11 : le releve part sur le fil des coffres ; copie complete (aucune donnee qu'un autre mod pourrait encore modifier)
        CompoundTag copie = t.copy();
        Future<Long> ecrit;
        try {
            ecrit = FIL.submit(() -> ecrireMesure(f, copie));
        } catch (RejectedExecutionException ex) {
            long ns = ecrireMesure(f, t);
            confirmer(new Depot(p, raison, numero, t, aCote, f, null, d.fini(), ticks), ns);
            return;
        }
        // marque posee des maintenant (l'instantane est deja pris) : aucun objet en attente ne lui est livre pendant l'ecriture
        // (HeraliZ Boutique et Quetes lisent la marque) ; enregistre avec elle et avec tout son inventaire (deconnexion,
        // sauvegarde automatique), il retrouve le meme contenu par le depot (ou sans lui, la marque est oubliee) : ni double
        // ni perte. Retiree si le depot rate.
        marquer(p, numero);
        EN_ECRITURE.put(u, new Depot(p, raison, numero, t, aCote, f, ecrit, d.fini(), ticks));
    }

    /** Depot rate ou abandonne alors que le joueur est la : objets pris a part rendus, marque retiree. */
    private static void defaire(Depot d) {
        ServerPlayer p = d.joueur;
        List<ItemStack> l = new ArrayList<>(d.aCote);
        l.addAll(d.retenus);
        rendreACote(p, l);
        if (marque(p) == d.numero) marquer(p, 0L);
    }

    /**
     * Ecriture confirmee (ou ratee) : vidage, marque et enregistrement du joueur (fil serveur), ou rien s'il est parti
     * (jamais vide : son fichier est efface) ou si l'ecriture a rate (objets a cote rendus). {@code ns} : duree de
     * l'ecriture, -1 si elle a rate.
     */
    private static void confirmer(Depot d, long ns) {
        ServerPlayer p = d.joueur;
        UUID u = p.getUUID();
        boolean ok = ns >= 0;
        if (ok) {
            ecritureNs += ns;
            ecritureMaxNs = Math.max(ecritureMaxNs, ns);
        }
        if (!present(p)) {
            // parti (deconnexion, mort) avant la confirmation : jamais vide, il a ete enregistre avec tout (et peut-etre la
            // marque, oubliee a sa reconnexion faute de fichier) ; ce fichier ne doit jamais lui etre rendu en plus
            if (ok) effacer(d.fichier, "depot d'un joueur parti avant la fin de son ecriture");
            HeralizClashWar.LOG.warn("[matchs] coffre de {} pas fait : joueur parti pendant l'ecriture (rien n'a change pour lui)",
                    p.getGameProfile().getName());
            depotsRates++;
            finDepot(d.fini, u, false);
            return;
        }
        if (!ok) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} NON ecrit : joueur laisse intact", p.getGameProfile().getName());
            defaire(d);
            depotsRates++;
            finDepot(d.fini, u, false);
            return;
        }
        // 5. seulement maintenant : vidage (malus d'ArkaniaZ compris : tous les effets partent), marque du depot, joueur
        //    enregistre tout de suite (15/11, audit M4 : un plantage avant la sauvegarde automatique rendait le depot a un
        //    joueur qui avait encore tout)
        vider(p);
        CombatTag.effacer(u);
        marquer(p, d.numero);
        CompoundTag t = d.instantane;
        POSITIONS.put(u, new Position(t.getString("dim"), t.getDouble("x"), t.getDouble("z")));
        if (!sauverJoueur(p)) {
            HeralizClashWar.LOG.error("[matchs] {} vide mais pas enregistre : sa marque le sera avec sa prochaine sauvegarde",
                    p.getGameProfile().getName());
        }
        HeralizClashWar.LOG.info("[matchs] coffre de {} depose ({}) : {} piles, curios {}, soif {}", p.getGameProfile().getName(), d.raison,
                t.getList("inventaire", Tag.TAG_COMPOUND).size(), t.contains("curios") ? t.getList("curios", Tag.TAG_COMPOUND).size() : "-",
                t.contains("soif") ? "oui" : "-");
        depotsFaits++;
        finDepot(d.fini, u, true);
    }

    /** Joueur toujours le meme et connecte (pas deconnecte, pas reapparu sous un autre objet). */
    private static boolean present(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        return !p.isRemoved() && s != null && s.getPlayerList().getPlayer(p.getUUID()) == p;
    }

    /** Fin d'un depot : le joueur n'est plus occupe, l'appelant est prevenu (erreur journalisee). */
    private static void finDepot(@Nullable Consumer<Boolean> fini, UUID u, boolean ok) {
        DEPOSANTS.remove(u);
        prevenir(fini, u, ok);
    }

    /** Fin d'un retour : idem (encore occupe si un autre retour l'attend : reconnecte entre-temps). */
    private static void finRetour(@Nullable Consumer<Boolean> fini, UUID u, boolean ok) {
        boolean autre = false;
        for (Retour r : RETOURS) {
            if (r.joueur.getUUID().equals(u)) {
                autre = true;
                break;
            }
        }
        if (!autre) RENDUS.remove(u);
        prevenir(fini, u, ok);
    }

    private static void prevenir(@Nullable Consumer<Boolean> fini, UUID u, boolean ok) {
        if (fini == null) return;
        try {
            fini.accept(ok);
        } catch (RuntimeException ex) {
            HeralizClashWar.LOG.error("[matchs] suite du coffre de {} en erreur", u, ex);
        }
    }

    /** Instantane NBT complet du joueur (sans rien modifier). */
    public static CompoundTag instantane(ServerPlayer p, String raison, @Nullable CompoundTag brut) {
        CompoundTag t = new CompoundTag();
        t.putInt("version", VERSION);
        t.putString("raison", raison == null ? "" : raison);
        t.putLong("date", System.currentTimeMillis());
        t.putUUID("uuid", p.getUUID());
        t.putString("nom", p.getGameProfile().getName());
        Inventory inv = p.getInventory();
        t.put("inventaire", inv.save(new ListTag()));
        t.putInt("selection", inv.selected);
        ListTag curios = Compat.sauverCurios(p);
        if (curios != null) t.put("curios", curios);
        t.putFloat("sante", p.getHealth());
        t.putFloat("absorption", p.getAbsorptionAmount());
        CompoundTag faim = new CompoundTag();
        p.getFoodData().addAdditionalSaveData(faim);
        t.put("faim", faim);
        t.putInt("xpNiveau", p.experienceLevel);
        t.putFloat("xpProgres", p.experienceProgress);
        t.putInt("xpTotal", p.totalExperience);
        ListTag effets = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) effets.add(e.save(new CompoundTag()));
        t.put("effets", effets);
        t.putInt("mode", p.gameMode.getGameModeForPlayer().getId());
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        if (brut != null) t.put("brut", brut);
        return t;
    }

    /** Vidage apres depot : inventaire, Curios, effets, recharges tactiques, sante et faim pleines. */
    private static void vider(ServerPlayer p) {
        p.getInventory().clearContent();
        Compat.viderCurios(p);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.setAbsorptionAmount(0);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(5f);
        p.getFoodData().setExhaustion(0f);
        Tactical.zeroRecharges(p);
        p.clearFire();
        p.fallDistance = 0;
        p.setGameMode(GameType.SURVIVAL);
        p.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ restauration
    /**
     * Retour etale (19/11) : le fichier du depot est lu d'avance sur le fil des coffres, l'arrivee (position d'avant le
     * match) est prechargee, puis {@link #rendre} se fait a son tour (au plus 3 s d'attente de l'arrivee). {@code fini} :
     * vrai si le depot a ete rendu, faux sinon (rien a rendre, illisible, joueur parti : son depot reste alors sur le
     * disque et lui sera rendu a sa reconnexion). Un retour deja demande pour ce joueur : rien de plus.
     */
    public static void rendrePuis(ServerPlayer p, @Nullable Consumer<Boolean> fini) {
        UUID u = p.getUUID();
        if (RENDUS.contains(u)) {
            for (Retour r : RETOURS) {
                if (r.joueur == p) return;
            }
        }
        Path f = Coffre.fichier(u);
        Future<CompoundTag> lu = null;
        if (enFond && f != null && Files.isRegularFile(f)) {
            try {
                lu = FIL.submit(() -> lireSansJournal(f));
            } catch (RejectedExecutionException ex) {
                lu = null;
            }
        }
        RENDUS.add(u);
        Retour r = new Retour(p, lu, fini == null ? ok -> { } : fini, ticks);
        Position pos = POSITIONS.get(u);
        if (pos != null) destination(r, p.getServer(), pos.dim(), pos.x(), pos.z());
        RETOURS.addLast(r);
    }

    /** Destination du retour connue : son arrivee est demandee (prechargement). */
    private static void destination(Retour r, @Nullable MinecraftServer s, String dim, double x, double z) {
        if (s == null || r.prechargee) return;
        r.prechargee = true;
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        ServerLevel niveau = rl == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (niveau == null) return;
        r.niveau = niveau;
        r.x = x;
        r.z = z;
        Arrivees.precharger(niveau, x, z);
    }

    /** Voir {@link Coffre#rendre} (tout de suite : staff, garde hors match, API). */
    public static boolean rendre(ServerPlayer p) {
        return rendre(p, null);
    }

    /** Comme {@link #rendre(ServerPlayer)}, avec le depot deja lu (lecture d'avance du retour etale), null : lu ici. */
    private static boolean rendre(ServerPlayer p, @Nullable CompoundTag dejaLu) {
        // une arrivee en attente (salle, arene) ne doit pas le ramener apres son retour
        Arrivees.oublier(p.getUUID());
        Path f = Coffre.fichier(p.getUUID());
        if (f == null || !Files.isRegularFile(f)) {
            // rien a rendre : on retire quand meme tout objet prete, le drapeau et une marque restee seule
            POSITIONS.remove(p.getUUID());
            Equipement.retirer(p);
            detacher(p);
            oublierMarque(p);
            return false;
        }
        CompoundTag t = dejaLu != null ? dejaLu : lire(f);
        if (t == null) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} illisible : deplace en enquete, rien rendu", p.getGameProfile().getName());
            enquete(f, "illisible");
            oublierMarque(p);
            return false;
        }
        long numero = t.getLong("numero");
        if (numero != 0L && marque(p) != numero) {
            // le joueur ne porte pas la marque de ce depot : jamais enregistre vide apres le depot (plantage juste apres :
            // il a encore son inventaire d'avant) ou depot deja rendu (plantage avant l'effacement du fichier) : le rendre
            // copierait ses objets
            HeralizClashWar.LOG.error("[matchs] coffre de {} : marque du joueur {} au lieu de {} (deja rendu, ou joueur jamais enregistre "
                    + "apres le depot) : fichier deplace en enquete, rien rendu", p.getGameProfile().getName(), marque(p), numero);
            enquete(f, "sans_marque");
            Equipement.retirer(p);
            detacher(p);
            oublierMarque(p);
            return false;
        }
        // objets a vie arrives dans l'inventaire du match sans en etre des prets, et ce qui est au curseur ou dans la
        // grille 2 x 2 (sauf les prets du match) : pris a part avant le vidage, rendus apres (02/11, audit point 8)
        List<ItemStack> sauves = sauverAvantVidage(p);
        p.closeContainer();
        Descente.forcer(p);
        detacher(p);
        // vider
        p.getInventory().clearContent();
        Compat.viderCurios(p);
        p.removeAllEffects();
        // recharger
        Inventory inv = p.getInventory();
        inv.load(t.getList("inventaire", Tag.TAG_COMPOUND));
        inv.selected = Math.max(0, Math.min(8, t.getInt("selection")));
        boolean curiosOk = true;
        if (t.contains("curios")) {
            ListTag c = t.getList("curios", Tag.TAG_COMPOUND);
            curiosOk = Compat.chargerCurios(p, c) || c.isEmpty();
        }
        // objets tenus hors de l'inventaire au depot, puis ceux sauves du match
        ListTag ac = t.getList("a_cote", Tag.TAG_COMPOUND);
        for (int i = 0; i < ac.size(); i++) sauves.add(ItemStack.of(ac.getCompound(i)));
        rendreACote(p, sauves);
        GameType mode = GameType.byId(t.getInt("mode"));
        p.setGameMode(mode);
        p.experienceProgress = t.getFloat("xpProgres");
        p.totalExperience = t.getInt("xpTotal");
        p.setExperienceLevels(t.getInt("xpNiveau"));
        p.getFoodData().readAdditionalSaveData(t.getCompound("faim"));
        p.clearFire();
        p.fallDistance = 0;
        teleporter(p, t);
        // effets APRES la teleportation : ServerPlayer.teleportTo vers une autre dimension envoie un paquet de reapparition
        // qui vide les effets cote client sans les renvoyer (le serveur les avait, le client ne les affichait plus)
        ListTag effets = t.getList("effets", Tag.TAG_COMPOUND);
        for (int i = 0; i < effets.size(); i++) {
            MobEffectInstance e = MobEffectInstance.load(effets.getCompound(i));
            if (e != null) p.addEffect(e);
        }
        // sante apres armure, Curios et effets (la sante max peut en dependre)
        p.setAbsorptionAmount(t.getFloat("absorption"));
        float sante = t.getFloat("sante");
        p.setHealth(Math.max(1f, Math.min(sante, p.getMaxHealth())));
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
        // marque retiree : enregistree avec l'inventaire rendu ; le fichier n'est efface qu'apres (jamais rendu deux fois)
        marquer(p, 0L);
        POSITIONS.remove(p.getUUID());
        if (!curiosOk) {
            HeralizClashWar.LOG.error("[matchs] coffre de {} : Curios non recharges, fichier garde en enquete", p.getGameProfile().getName());
            enquete(f, "curios");
            sauverJoueur(p);
            return false;
        }
        // suppression du fichier APRES la sauvegarde du joueur
        if (sauverJoueur(p)) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException e) {
                HeralizClashWar.LOG.error("[matchs] coffre de {} rendu mais fichier non supprime : {}", p.getGameProfile().getName(), e.toString());
            }
        } else {
            HeralizClashWar.LOG.error("[matchs] coffre de {} rendu mais joueur non sauvegarde : fichier garde (sans sa marque, il ne "
                    + "sera jamais rendu une 2e fois)", p.getGameProfile().getName());
        }
        HeralizClashWar.LOG.info("[matchs] coffre de {} rendu ({})", p.getGameProfile().getName(), t.getString("raison"));
        return true;
    }

    /** Marque d'un depot qui n'existe plus (fichier efface par /wipe, deplace en enquete) : retiree et enregistree. */
    private static void oublierMarque(ServerPlayer p) {
        if (marque(p) == 0L) return;
        HeralizClashWar.LOG.warn("[matchs] {} : marque d'un depot sans fichier retiree", p.getGameProfile().getName());
        marquer(p, 0L);
        sauverJoueur(p);
    }

    /** Curseur et grille 2 x 2 de l'inventaire vides : leurs objets (rendus, a garder dans le depot). */
    private static List<ItemStack> horsInventaire(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        ItemStack curseur = p.containerMenu.getCarried();
        if (!curseur.isEmpty()) {
            l.add(curseur);
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grille.getContainerSize(); i++) {
            ItemStack s = grille.removeItemNoUpdate(i);
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    /**
     * Avant le vidage du retour : les objets a vie de l'inventaire qui ne sont pas des prets du match, et tout ce qui est
     * au curseur ou dans la grille 2 x 2 (les prets du match y sont jetes, comme ceux de l'inventaire). Rendus apres.
     */
    private static List<ItemStack> sauverAvantVidage(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        for (ItemStack s : horsInventaire(p)) {
            if (!Equipement.estPrete(s)) l.add(s);
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && !Equipement.estPrete(s) && Tactical.estAVie(s)) {
                l.add(s);
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (!l.isEmpty()) {
            HeralizClashWar.LOG.info("[matchs] {} : {} objet(s) pris a part avant le vidage du retour (rendus apres)",
                    p.getGameProfile().getName(), l.size());
        }
        return l;
    }

    /**
     * Rend des objets pris a part : dans l'inventaire, sinon une case libre de la grille 2 x 2, sinon au curseur (la ou ils
     * etaient), et seulement en tout dernier recours a ses pieds (journal).
     */
    private static void rendreACote(ServerPlayer p, List<ItemStack> objets) {
        if (objets.isEmpty()) return;
        CraftingContainer grille = p.inventoryMenu.getCraftSlots();
        for (ItemStack s : objets) {
            if (s.isEmpty()) continue;
            p.getInventory().add(s);
            if (s.isEmpty()) continue;
            boolean pose = false;
            for (int i = 0; i < grille.getContainerSize() && !pose; i++) {
                if (grille.getItem(i).isEmpty()) {
                    grille.setItem(i, s);
                    pose = true;
                }
            }
            if (!pose && p.containerMenu.getCarried().isEmpty()) {
                p.containerMenu.setCarried(s);
                pose = true;
            }
            if (!pose) {
                HeralizClashWar.LOG.warn("[matchs] {} : inventaire, grille et curseur pleins, {} pose a ses pieds",
                        p.getGameProfile().getName(), s.getHoverName().getString());
                p.drop(s, false);
            }
        }
        p.inventoryMenu.broadcastChanges();
        p.containerMenu.broadcastChanges();
    }

    /** Retire le joueur de sa partie, du mode spectateur et efface son HUD de match. */
    private static void detacher(ServerPlayer p) {
        // « reste allonge » des Zombies (a terre au moment d'une deconnexion ou d'un plantage) : jamais garde hors du match
        if (p.getTags().contains(RESTE_ALLONGE)) {
            p.removeTag(RESTE_ALLONGE);
            p.setForcedPose(null);
        }
        Spectateurs.arreter(p, false);
        MatchApi.quitter(p);
        Hud.effacer(p);
    }

    private static void teleporter(ServerPlayer p, CompoundTag t) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        ServerLevel niveau = null;
        ResourceLocation rl = ResourceLocation.tryParse(t.getString("dim"));
        if (rl != null) niveau = s.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (niveau == null) {
            niveau = s.overworld();
            var sp = niveau.getSharedSpawnPos();
            p.teleportTo(niveau, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
            return;
        }
        p.teleportTo(niveau, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
    }

    /** Sauvegarde immediate du joueur ({@code PlayerList.save}, protegee : par reflexion SRG m_6765_). */
    public static boolean sauverJoueur(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) return false;
        try {
            if (sauverJoueur == null) sauverJoueur = ObfuscationReflectionHelper.findMethod(PlayerList.class, "m_6765_", ServerPlayer.class);
            sauverJoueur.invoke(s.getPlayerList(), p);
            return true;
        } catch (Exception e) {
            HeralizClashWar.LOG.error("[matchs] sauvegarde de {} impossible", p.getGameProfile().getName(), e);
            return false;
        }
    }

    // ------------------------------------------------------------------ chaque tick (19/11)
    /**
     * Chaque tick (MoteurLogique) : confirmations des ecritures finies, nouveaux depots, retours prets, au plus
     * {@code coffres_par_tick} operations et {@code coffres_budget_ms} par tick (au moins une). Sans rien en cours : sort
     * tout de suite.
     */
    public static void tick(MinecraftServer s) {
        ticks++;
        if (DEMANDES.isEmpty() && EN_ECRITURE.isEmpty() && RETOURS.isEmpty() && ABANDONNES.isEmpty()) return;
        long fin = System.nanoTime() + budgetNs;
        int faits = 0;
        // 1. ecritures finies (les joueurs figes d'abord) ; trop longues : abandonnees, le joueur n'est pas vide
        if (!EN_ECRITURE.isEmpty()) {
            FINIS.clear();
            for (Iterator<Depot> it = EN_ECRITURE.values().iterator(); it.hasNext(); ) {
                Depot d = it.next();
                if (!d.ecrit.isDone()) {
                    if (ticks - d.depuis > ATTENTE_ECRITURE_MAX) {
                        it.remove();
                        ABANDONNES.add(d);
                        HeralizClashWar.LOG.error("[matchs] coffre de {} : ecriture sans reponse depuis {} s, depot abandonne (joueur laisse intact)",
                                d.joueur.getGameProfile().getName(), ATTENTE_ECRITURE_MAX / 20);
                        if (present(d.joueur)) defaire(d);
                        depotsRates++;
                        finDepot(d.fini, d.joueur.getUUID(), false);
                    }
                    continue;
                }
                if (faits >= parTick || (faits > 0 && System.nanoTime() > fin)) break;
                it.remove();
                FINIS.add(d);
                faits++;
            }
            // hors de la boucle : la suite d'un depot peut en demander d'autres
            for (int i = 0; i < FINIS.size(); i++) {
                Depot d = FINIS.get(i);
                confirmer(d, resultat(d.ecrit));
            }
            FINIS.clear();
        }
        // 2. depots abandonnes dont l'ecriture a fini quand meme : leur fichier ne doit jamais servir
        if (!ABANDONNES.isEmpty()) {
            for (Iterator<Depot> it = ABANDONNES.iterator(); it.hasNext(); ) {
                Depot d = it.next();
                if (!d.ecrit.isDone()) continue;
                it.remove();
                if (resultat(d.ecrit) >= 0) effacer(d.fichier, "depot abandonne (ecriture trop longue)");
            }
        }
        // 3. nouveaux depots (releve sur le fil serveur, ecriture en fond)
        while (!DEMANDES.isEmpty() && faits < parTick && EN_ECRITURE.size() < MAX_EN_ECRITURE && (faits == 0 || System.nanoTime() < fin)) {
            commencer(DEMANDES.pollFirst(), enFond);
            faits++;
        }
        // 4. retours prets (lecture finie, arrivee chargee ou attendue assez longtemps), dans l'ordre des demandes
        int n = RETOURS.size();
        for (int k = 0; k < n && faits < parTick && (faits == 0 || System.nanoTime() < fin); k++) {
            Retour r = RETOURS.pollFirst();
            if (r == null) break;
            if (!present(r.joueur)) {
                // parti : son depot reste sur le disque, il lui sera rendu a sa reconnexion
                finRetour(r.fini, r.joueur.getUUID(), false);
                continue;
            }
            if (!pret(s, r)) {
                RETOURS.addLast(r);
                continue;
            }
            CompoundTag lu = r.lu != null ? lecture(r.lu) : null;
            boolean rendu = rendre(r.joueur, lu);
            if (rendu) retoursFaits++;
            faits++;
            finRetour(r.fini, r.joueur.getUUID(), rendu);
        }
        if (DEMANDES.isEmpty() && EN_ECRITURE.isEmpty() && RETOURS.isEmpty() && depotsFaits + depotsRates + retoursFaits > 0) {
            HeralizClashWar.LOG.info("[matchs] coffres : {} depot(s) fait(s), {} rate(s), {} retour(s) ; ecriture {} ms en moyenne, {} ms au pire (fil des coffres)",
                    depotsFaits, depotsRates, retoursFaits, depotsFaits > 0 ? ecritureNs / depotsFaits / 1_000_000L : 0, ecritureMaxNs / 1_000_000L);
            depotsFaits = depotsRates = retoursFaits = 0;
            ecritureNs = ecritureMaxNs = 0;
        }
    }

    /** Le retour peut-il se faire maintenant : depot lu (ou rien a lire), arrivee chargee, ou attente au plus long. */
    private static boolean pret(MinecraftServer s, Retour r) {
        if (r.lu != null && !r.lu.isDone()) return ticks - r.depuis > ATTENTE_RETOUR_MAX;
        if (!r.prechargee) {
            CompoundTag t = r.lu != null ? lecture(r.lu) : null;
            if (t != null) destination(r, s, t.getString("dim"), t.getDouble("x"), t.getDouble("z"));
            r.prechargee = true;
        }
        if (ticks - r.depuis > ATTENTE_RETOUR_MAX) return true;
        return r.niveau == null || Arrivees.pret(r.niveau, r.x, r.z);
    }

    /** Duree de l'ecriture (ns), -1 si elle a rate. */
    private static long resultat(@Nullable Future<Long> f) {
        if (f == null) return -1;
        try {
            Long ns = f.get();
            return ns == null ? -1 : ns;
        } catch (Exception ex) {
            return -1;
        }
    }

    @Nullable
    private static CompoundTag lecture(Future<CompoundTag> f) {
        if (!f.isDone()) return null;
        try {
            return f.get();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Efface un fichier de depot qui ne doit jamais servir (journal). */
    private static void effacer(Path f, String pourquoi) {
        try {
            if (Files.deleteIfExists(f)) HeralizClashWar.LOG.info("[matchs] {} efface : {}", f.getFileName(), pourquoi);
        } catch (IOException ex) {
            HeralizClashWar.LOG.error("[matchs] {} ({}) non efface : {}", f, pourquoi, ex.toString());
        }
    }

    /**
     * Arret du serveur (ServerStopped, joueurs deja enregistres) : un depot encore en ecriture n'a jamais vide son joueur
     * (enregistre avec tout) : son fichier est efface une fois l'ecriture finie. Les retours en attente restent sur le
     * disque (rendus a la reconnexion).
     */
    public static void arret() {
        List<Depot> tous = new ArrayList<>(EN_ECRITURE.values());
        tous.addAll(ABANDONNES);
        for (Depot d : tous) {
            try {
                Long ns = d.ecrit.get(10, TimeUnit.SECONDS);
                if (ns != null && ns >= 0) effacer(d.fichier, "depot pas fini a l'arret du serveur");
            } catch (Exception ex) {
                HeralizClashWar.LOG.error("[matchs] coffre de {} : ecriture pas finie a l'arret ({}), fichier garde (sans marque : jamais rendu)",
                        d.joueur.getGameProfile().getName(), ex.toString());
            }
        }
        EN_ECRITURE.clear();
        ABANDONNES.clear();
        DEMANDES.clear();
        RETOURS.clear();
        POSITIONS.clear();
        DEPOSANTS.clear();
        RENDUS.clear();
        FINIS.clear();
    }

    // ------------------------------------------------------------------ connexion (reprise apres crash)
    /**
     * A la connexion : joueur rattache a une partie active qui le reprend = rien ; sinon depot present = restauration
     * (reprise apres crash, partie finie pendant son absence ; 19/11 : etalee, arrivee prechargee) ; sinon purge des
     * objets pretes et du drapeau.
     */
    public static void surConnexion(ServerPlayer p) {
        UUID u = p.getUUID();
        Partie partie = MatchApi.partieDe(u);
        if (partie != null && partie.phase() != Partie.Phase.TERMINEE && partie.contient(u)) {
            boolean garde;
            try {
                garde = partie.surReconnexion(p);
            } catch (RuntimeException e) {
                HeralizClashWar.LOG.error("[matchs] surReconnexion de {} en erreur", partie.id(), e);
                garde = false;
            }
            if (garde) {
                MatchApi.rejoindre(p, partie);
                return;
            }
        }
        if (Coffre.aUnDepot(u)) {
            HeralizClashWar.LOG.warn("[matchs] {} : depot non rendu trouve a la connexion, restauration", p.getGameProfile().getName());
            Equipement.retirer(p);
            rendrePuis(p, null);
            return;
        }
        int n = Equipement.retirer(p);
        if (n > 0) HeralizClashWar.LOG.warn("[matchs] {} : {} objet(s) prete(s) purge(s) a la connexion", p.getGameProfile().getName(), n);
        detacher(p);
        oublierMarque(p);
    }

    // ------------------------------------------------------------------ fichiers
    /** Ecrit le depot de facon atomique et verifiee ; rend faux (fichier final intact) en cas d'echec. */
    static boolean ecrire(Path f, CompoundTag t) {
        return ecrireMesure(f, t) >= 0;
    }

    /**
     * Comme {@link #ecrire}, en rendant sa duree (ns), -1 en cas d'echec. Fil serveur ou fil des coffres (ne touche a aucun
     * objet du jeu : seulement {@code t}, qui n'est plus modifie).
     */
    private static long ecrireMesure(Path f, CompoundTag t) {
        long debut = System.nanoTime();
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try {
            Files.createDirectories(f.getParent());
            ByteArrayOutputStream mem = new ByteArrayOutputStream(16384);
            NbtIo.writeCompressed(t, mem);
            byte[] octets = mem.toByteArray();
            try (FileOutputStream out = new FileOutputStream(tmp.toFile())) {
                out.write(octets);
                out.getFD().sync();
            }
            CompoundTag relu = NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(tmp)));
            if (!t.equals(relu)) throw new IOException("relecture differente");
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return System.nanoTime() - debut;
        } catch (IOException | RuntimeException e) {
            HeralizClashWar.LOG.error("[matchs] ecriture du coffre {} impossible", f, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
                // rien
            }
            return -1;
        }
    }

    /** Lit un depot, ou null s'il est illisible. */
    @Nullable
    public static CompoundTag lire(Path f) {
        try {
            byte[] b = Files.readAllBytes(f);
            return NbtIo.readCompressed(new ByteArrayInputStream(b));
        } catch (IOException | RuntimeException e) {
            HeralizClashWar.LOG.error("[matchs] lecture du coffre {} impossible", f, e);
            return null;
        }
    }

    /** Lecture d'avance (fil des coffres) : null si illisible, et {@link #rendre} relira (et journalisera) sur le fil serveur. */
    @Nullable
    private static CompoundTag lireSansJournal(Path f) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(f)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Depot d'un joueur (lecture seule), ou null. */
    @Nullable
    public static CompoundTag lire(UUID u) {
        Path f = Coffre.fichier(u);
        return f != null && Files.isRegularFile(f) ? lire(f) : null;
    }

    /** Deplace le fichier dans coffres/enquete/ (jamais supprime). */
    private static void enquete(Path f, String motif) {
        try {
            Path d = f.getParent().resolve("enquete");
            Files.createDirectories(d);
            String date = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Files.move(f, d.resolve(f.getFileName().toString().replace(".dat", "") + "-" + motif + "-" + date + ".dat"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            HeralizClashWar.LOG.error("[matchs] coffre {} non deplace en enquete", f, e);
        }
    }

    /** Dimension enregistree dans un depot (journal). */
    public static String dimension(CompoundTag t) {
        return t.getString("dim");
    }

    /** Vrai si la cle de dimension designe un niveau existant. */
    public static boolean niveauExiste(MinecraftServer s, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return false;
        ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, rl);
        return s.getLevel(k) != null;
    }
}
