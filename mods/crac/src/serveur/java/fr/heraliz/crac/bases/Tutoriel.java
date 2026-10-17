package fr.heraliz.crac.bases;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.build.world.Costs;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.claim.UpkeepResource;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupNames;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Reply;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CTutoriel;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

/**
 * Tutoriel du monde des bases, après la cinématique d'arrivée de HeraliZ Utils, pour un joueur qui n'a jamais eu de base.
 * Depuis la 1.7.0 : <b>une étape à la fois</b>, dans une petite carte en haut à gauche de l'écran (S2CTutoriel, dessinée
 * par {@code bases.HudTutoriel} du jar joueur) ; plus de grands titres au milieu de l'écran ni de pluie de messages dans
 * le chat (une ligne au début, une à la fin). Le parcours est dans {@link EtapesTuto} ; le serveur seul le fait avancer.
 * <ol>
 *   <li>Au spawn ({@code heralisz:spawn}), quand la cinématique a commencé (donnée persistante
 *   {@code heraliz_cinematique_vue} de Utils, lue par son nom) : on attend {@code [tuto] attente_min_s} secondes puis le
 *   premier pas du joueur, ou au plus {@code attente_max_s} secondes (la cinématique dure environ 122 s ; Utils n'envoie
 *   pas d'évènement de fin).</li>
 *   <li>Étapes du spawn : accueil, kit (de quoi bâtir avec CRac une cabane 2 × 1 avec une porte, calculé avec les coûts
 *   réels de crac-building.toml, plus la mise en bois au marteau, un jour d'entretien du Claim Block et une marge de
 *   10 % ; un groupe CRac est créé pour lui s'il n'en a pas), départ : envoi dans le monde des bases, à un point plat
 *   tiré au hasard loin des autres bases, où il est protégé ({@link Protections}) tant que sa base n'est pas posée.</li>
 *   <li>Étapes du monde des bases : plan en main, première fondation, Claim Block, deuxième fondation (base posée), murs,
 *   porte, toit, mise en bois, entretien, commandes. Chaque étape « action » avance quand le serveur voit la chose faite.</li>
 * </ol>
 * Une étape qui n'est pas dans son monde (spawn ou monde des bases) attend, carte cachée ; à la reconnexion, le joueur
 * reprend à son étape (gardée dans {@link BasesDonnees.Joueur#tuto}). Le tutoriel automatique ne vise que les joueurs
 * en survie ou en aventure (en créatif ou en spectateur, rien), à l'étape AUCUNE (jamais fait) et sans base. Staff
 * (permission 2) :
 * <ul>
 *   <li>{@code /tuto lancer <joueur>} : le tutoriel complet TOUT DE SUITE, où que soit le joueur (étapes du spawn
 *   comprises), même en créatif, même s'il a déjà une base ou l'a déjà fait (la protection de base n'est redonnée qu'à un
 *   joueur sans base) ;</li>
 *   <li>{@code /tuto rejouer <joueur>} : remise à zéro (il repart au spawn, comme un nouveau joueur) ;</li>
 *   <li>la cinématique rejouée par le staff ({@code /cinematique} pour soi, {@code /cinematique joueur <cibles>})
 *   relance aussi le tutoriel du joueur à la fin de la cinématique, comme {@code /tuto lancer} (un joueur qui rejoue sa
 *   propre cinématique ne reçoit pas de nouveau kit).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "crac")
public final class Tutoriel {
    /** Donnée persistante (PlayerPersisted) posée par HeraliZ Utils au lancement de la cinématique. */
    private static final String CINEMATIQUE_VUE = "heraliz_cinematique_vue";
    /** Pièces de la cabane 2 × 1 avec une porte. */
    private static final Map<PieceKind, Integer> CABANE = new LinkedHashMap<>();

    static {
        CABANE.put(PieceKind.FOUNDATION, 2);
        CABANE.put(PieceKind.WALL, 5);
        CABANE.put(PieceKind.DOORWAY, 1);
        CABANE.put(PieceKind.FLOOR, 2);
    }

    /** Ticks d'affichage au moins d'une étape « action », même déjà faite (jamais deux étapes d'un coup). */
    static final int MIN_ACTION = 50;
    /** Ticks du moment « Étape terminée » avant la suivante. */
    static final int T_FAIT = 40;
    /** Constat des étapes « action » tous les N ticks (pièces relevées dans le monde des bases). */
    private static final int T_CONSTAT = 10;

    /** Attente de la fin de la cinématique : heure de jeu du début, position, relancée par le staff (cinématique rejouée). */
    private record Attente(long debut, Vec3 pos, boolean force) {
    }

    /** Où en est un joueur connecté dans le parcours (l'étape elle-même est gardée dans les données). */
    private static final class Suivi {
        int index;
        /** Lancé par le staff : les étapes du spawn se déroulent où que soit le joueur. */
        boolean force;
        /** Ticks passés sur l'étape (seulement quand le joueur est dans le monde de l'étape). */
        int ticks;
        /** Ticks depuis la fin de l'étape, -1 tant qu'elle n'est pas faite. */
        int faitDepuis = -1;
        int compte;
        int objectif;
        /** Le joueur peut avoir une carte à l'écran (celle de l'étape d'avant au début) : à cacher s'il n'est pas au bon endroit. */
        boolean visible = true;
        /** L'étape a été envoyée au joueur. */
        boolean montre;
        /** Envois ratés d'une étape « départ » lancée par le staff. */
        int essais;
    }

    /** Joueurs qui attendent la fin de la cinématique (heure de jeu du début, position). */
    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    /** Joueurs connectés dont le tutoriel est en cours. */
    private static final Map<UUID, Suivi> SUIVIS = new HashMap<>();
    private static final int ESSAIS_MAX = 5;

    private Tutoriel() {
    }

    private static boolean actif() {
        return BasesReglages.b(BasesReglages.TUTO_ACTIF, true);
    }

    /** Le tutoriel de ce joueur est en cours (carte à l'écran). */
    static boolean enCours(UUID joueur) {
        return SUIVIS.containsKey(joueur);
    }

    // ---------------------------------------------------------------------------------------------- détection

    /** Toutes les secondes (MondeBasesServeur). */
    static void seconde(MinecraftServer server) {
        if (!actif() || server.getLevel(MondeBases.BASES) == null) {
            return;
        }
        BasesDonnees d = BasesDonnees.get(server);
        long now = server.overworld().getGameTime();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            if (SUIVIS.containsKey(id)) {
                continue;
            }
            Attente f = ATTENTES.get(id);
            if (f != null && f.force()) {
                // cinématique rejouée par le staff : tuto à la fin, quels que soient le mode de jeu et l'étape
                if (pret(p, f, now)) {
                    ATTENTES.remove(id);
                    lancer(p);
                }
                continue;
            }
            BasesDonnees.Joueur j = d.joueur(id);
            // reprise après une déconnexion ou un redémarrage, à l'étape gardée
            if (!j.tuto.isEmpty() && !EtapesTuto.FINI.equals(j.tuto)) {
                if (!p.isSpectator()) {
                    reprendre(p, j.tuto, false);
                }
                continue;
            }
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            switch (j.etape) {
                case AUCUNE -> {
                    if (aUneBase(p)) {
                        j.etape = BasesDonnees.Etape.FINI;
                        d.marquer();
                        ATTENTES.remove(id);
                        continue;
                    }
                    if (EtapesTuto.FINI.equals(j.tuto) || !MondeBases.estSpawn(p.level()) || !cinematiqueVue(p)) {
                        ATTENTES.remove(id);
                        continue;
                    }
                    Attente a = ATTENTES.computeIfAbsent(id, k -> new Attente(now, p.position(), false));
                    if (pret(p, a, now)) {
                        ATTENTES.remove(id);
                        demarrer(p, false);
                    }
                }
                // données d'avant la 1.7.0 (pas d'étape gardée) : kit reçu, pas encore envoyé ; ou déjà dans le monde des bases
                case KIT -> {
                    if (j.tuto.isEmpty()) {
                        reprendre(p, "depart", false);
                    }
                }
                case PHASE -> {
                    if (j.tuto.isEmpty()) {
                        reprendre(p, "coin", false);
                    }
                }
                default -> ATTENTES.remove(id);
            }
        }
    }

    /** Fin de l'attente : {@code attente_min_s} passées et un premier pas, ou {@code attente_max_s} passées. */
    private static boolean pret(ServerPlayer p, Attente a, long now) {
        long ecoule = (now - a.debut()) / 20L;
        boolean bouge = p.position().distanceToSqr(a.pos()) > 1.0;
        return ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MAX_S, 130)
            || ecoule >= BasesReglages.i(BasesReglages.ATTENTE_MIN_S, 15) && bouge;
    }

    /**
     * Le tutoriel complet tout de suite, où que soit le joueur : étape remise à AUCUNE pour que le kit soit donné ;
     * protection de base due seulement s'il n'a pas de base (dans le kit).
     */
    static void lancer(ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        j.etape = BasesDonnees.Etape.AUCUNE;
        j.phaseTicks = 0;
        j.tuto = "";
        d.marquer();
        ATTENTES.remove(p.getUUID());
        demarrer(p, true);
        Crac.LOGGER.info("[CRac] tutoriel lancé pour {}", p.getGameProfile().getName());
    }

    /** Début du parcours : première étape utile, une ligne dans le chat. */
    private static void demarrer(ServerPlayer p, boolean force) {
        int i = suivante(p, -1);
        if (i < 0) {
            return;
        }
        T.chat(p, T.t("crac.tuto.debut", "[Tuto] Suis les étapes en haut à gauche de ton écran."), ChatFormatting.YELLOW);
        entrer(p, i, force);
    }

    /** Reprise à l'étape {@code id} (inconnue : au début). */
    private static void reprendre(ServerPlayer p, String id, boolean force) {
        int i = EtapesTuto.index(id);
        if (i < 0 || !EtapesTuto.PARCOURS.get(i).utile().test(p)) {
            i = suivante(p, Math.max(-1, i));
        }
        if (i < 0) {
            finir(p);
            return;
        }
        entrer(p, i, force);
    }

    /** Index de la prochaine étape utile après {@code i}, -1 s'il n'y en a plus. */
    private static int suivante(ServerPlayer p, int i) {
        for (int k = i + 1; k < EtapesTuto.PARCOURS.size(); k++) {
            if (EtapesTuto.PARCOURS.get(k).utile().test(p)) {
                return k;
            }
        }
        return -1;
    }

    private static void entrer(ServerPlayer p, int i, boolean force) {
        EtapesTuto.Pas pas = EtapesTuto.PARCOURS.get(i);
        Suivi s = new Suivi();
        s.index = i;
        s.force = force;
        SUIVIS.put(p.getUUID(), s);
        BasesDonnees d = BasesDonnees.get(p.server);
        d.joueur(p.getUUID()).tuto = pas.id();
        d.marquer();
        if (pas.entree() != null) {
            try {
                pas.entree().accept(p);
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] tutoriel : étape {} en erreur", pas.id(), ex);
            }
        }
    }

    /** Fin du parcours : carte retirée, une ligne dans le chat (les commandes à retenir). */
    private static void finir(ServerPlayer p) {
        SUIVIS.remove(p.getUUID());
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        j.tuto = EtapesTuto.FINI;
        if (j.etape == BasesDonnees.Etape.AUCUNE || j.etape == BasesDonnees.Etape.KIT) {
            j.etape = BasesDonnees.Etape.FINI;
        }
        d.marquer();
        paquet(p, S2CTutoriel.cache());
        T.chat(p, T.t("crac.tuto.fin", "[Tuto] Terminé. Du spawn : /home vers ta base. De ta base : /spawn. Amis : /group."),
            ChatFormatting.GRAY);
        Crac.LOGGER.info("[CRac] tutoriel terminé pour {}", p.getGameProfile().getName());
    }

    /** Chaque tick (MondeBasesServeur) : déroulé des étapes. */
    static void tick(MinecraftServer server) {
        if (SUIVIS.isEmpty()) {
            return;
        }
        List<Runnable> actions = new ArrayList<>();
        Iterator<Map.Entry<UUID, Suivi>> it = SUIVIS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Suivi> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Suivi s = e.getValue();
            EtapesTuto.Pas pas = EtapesTuto.PARCOURS.get(s.index);
            boolean ici = p.isAlive() && (s.force && pas.lieu() == EtapesTuto.Lieu.SPAWN || pas.lieu().ok(p));
            if (!ici) {
                // hors du monde de l'étape (ou mort) : elle attend, carte cachée
                if (s.visible) {
                    s.visible = false;
                    paquet(p, S2CTutoriel.cache());
                }
                continue;
            }
            if (!s.visible || !s.montre) {
                s.visible = true;
                s.montre = true;
                if (s.ticks == 0 && pas.progres() != null) {
                    constater(p, s, pas);
                }
                montrer(p, s, pas);
            }
            s.ticks++;
            if (s.faitDepuis >= 0) {
                if (++s.faitDepuis >= T_FAIT) {
                    actions.add(() -> quitter(p, s, pas));
                }
                continue;
            }
            boolean fait;
            if (pas.progres() == null) {
                fait = s.ticks >= lecture(pas);
            } else {
                if (s.ticks % T_CONSTAT == 0 && constater(p, s, pas)) {
                    montrer(p, s, pas);
                }
                fait = s.ticks >= MIN_ACTION && s.compte >= Math.max(1, s.objectif);
            }
            if (fait) {
                s.faitDepuis = 0;
                montrer(p, s, pas);
            }
        }
        for (Runnable r : actions) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                Crac.LOGGER.error("[CRac] tutoriel en erreur", ex);
            }
        }
    }

    /** Relève l'avancée d'une étape « action » ; vrai si elle a changé. */
    private static boolean constater(ServerPlayer p, Suivi s, EtapesTuto.Pas pas) {
        int[] r;
        try {
            r = pas.progres().apply(p);
        } catch (RuntimeException ex) {
            Crac.LOGGER.debug("[CRac] tutoriel : constat de {} impossible ({})", pas.id(), ex.toString());
            return false;
        }
        boolean change = r[0] != s.compte || r[1] != s.objectif;
        s.compte = r[0];
        s.objectif = r[1];
        return change;
    }

    /** Fin du moment « Étape terminée » : action de sortie (envoi...), puis étape suivante. */
    private static void quitter(ServerPlayer p, Suivi s, EtapesTuto.Pas pas) {
        if (SUIVIS.get(p.getUUID()) != s) {
            return;
        }
        if (pas.sortie() != null && !pas.sortie().test(p)) {
            // à réessayer dans une seconde (aucune place libre pour l'envoi) ; lancé par le staff : 5 essais au plus,
            // ensuite l'étape attend que le joueur soit au spawn
            s.faitDepuis = T_FAIT - 20;
            if (s.force && ++s.essais >= ESSAIS_MAX) {
                s.force = false;
            }
            return;
        }
        int i = suivante(p, s.index);
        if (i < 0) {
            finir(p);
            return;
        }
        // la nouvelle carte est montrée au tick suivant, si le joueur est dans le monde de l'étape (sinon : cachée)
        entrer(p, i, s.force);
    }

    /** Ticks de lecture d'une étape « lecture » ({@code [tuto] lecture_pourcent}). */
    private static int lecture(EtapesTuto.Pas pas) {
        return Math.max(40, pas.lectureS() * 20 * BasesReglages.i(BasesReglages.LECTURE_POURCENT, 100) / 100);
    }

    /** Envoie l'état de l'étape au joueur. */
    private static void montrer(ServerPlayer p, Suivi s, EtapesTuto.Pas pas) {
        int numero = 0, total = 0;
        for (int k = 0; k < EtapesTuto.PARCOURS.size(); k++) {
            if (k == s.index || EtapesTuto.PARCOURS.get(k).utile().test(p)) {
                total++;
                if (k <= s.index) {
                    numero = total;
                }
            }
        }
        List<String> touches = new ArrayList<>(), legendes = new ArrayList<>();
        for (EtapesTuto.Touche t : pas.touches()) {
            touches.add(t.jeton());
            legendes.add(t.legende());
        }
        boolean lecture = pas.progres() == null;
        paquet(p, new S2CTutoriel(true, numero, total, s.faitDepuis >= 0, pas.titre(), pas.texte().apply(p), touches, legendes,
            lecture ? 0 : s.compte, lecture || !pas.compteur() ? 0 : s.objectif, lecture ? Math.max(0, lecture(pas) - s.ticks) : 0));
    }

    private static void paquet(ServerPlayer p, S2CTutoriel msg) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    static void oublier(UUID joueur) {
        ATTENTES.remove(joueur);
        SUIVIS.remove(joueur);
    }

    static boolean aUneBase(ServerPlayer p) {
        Group g = GroupData.get(p.server).getByMember(p.getUUID());
        return g != null && ClaimManager.getByGroupAnywhere(p.server, g.id()) != null;
    }

    private static boolean cinematiqueVue(ServerPlayer p) {
        if (!ModList.get().isLoaded("heralisz_utils")) {
            return true;
        }
        CompoundTag root = p.getPersistentData();
        return root.contains(Player.PERSISTED_NBT_TAG) && root.getCompound(Player.PERSISTED_NBT_TAG).getBoolean(CINEMATIQUE_VUE);
    }

    // ---------------------------------------------------------------------------------------------- actions des étapes

    /** Étape « kit » (à l'entrée) : le kit et le groupe, une seule fois (étape AUCUNE). */
    static void donnerKit(ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        if (j.etape != BasesDonnees.Etape.AUCUNE) {
            return;
        }
        groupe(p);
        for (ItemStack s : contenuKit()) {
            donner(p, s.copy());
        }
        j.etape = BasesDonnees.Etape.KIT;
        // un joueur qui a déjà une base (tuto relancé par le staff) ne regagne pas la protection de base
        j.protectionDue = !aUneBase(p);
        j.phaseTicks = 0;
        d.marquer();
        Crac.LOGGER.info("[CRac] tutoriel : kit de base donné à {}", p.getGameProfile().getName());
    }

    /** Étape « départ » (à la sortie) : envoi dans le monde des bases ; faux si aucune place sûre n'a été trouvée. */
    static boolean envoyer(ServerPlayer p) {
        ServerLevel bases = p.server.getLevel(MondeBases.BASES);
        if (bases == null) {
            return false;
        }
        Vec3 dest = Points.auHasard(bases, p);
        if (dest == null) {
            T.barre(p, T.t("crac.home.pas_de_place", "Aucune place sûre trouvée dans le monde des bases : réessaie dans un instant."),
                ChatFormatting.RED);
            return false;
        }
        BasesDonnees d = BasesDonnees.get(p.server);
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        p.stopRiding();
        p.teleportTo(bases, dest.x, dest.y, dest.z, p.getYRot(), 0f);
        p.resetFallDistance();
        if (j.etape == BasesDonnees.Etape.AUCUNE || j.etape == BasesDonnees.Etape.KIT) {
            j.etape = BasesDonnees.Etape.PHASE;
            j.phaseTicks = 0;
        }
        d.marquer();
        return true;
    }

    // ---------------------------------------------------------------------------------------------- kit

    /** Le kit : plan, marteau, Claim Block, porte en chêne, matières de la cabane (coûts réels) avec la marge. */
    static List<ItemStack> contenuKit() {
        Map<Item, Integer> m = new LinkedHashMap<>();
        m.put(ModItems.BUILDING_PLAN.get(), 1);
        m.put(ModItems.BUILDING_HAMMER.get(), 1);
        m.put(ModItems.CLAIM_BLOCK.get(), 1);
        // la porte est dans la pièce « Porte » (cadre + battant) : pas d'objet porte à donner
        Map<Item, Integer> matieres = new LinkedHashMap<>();
        int pieces = 0;
        for (Map.Entry<PieceKind, Integer> e : CABANE.entrySet()) {
            pieces += e.getValue();
            for (int i = 0; i < e.getValue(); i++) {
                ajouter(matieres, Costs.place(e.getKey()));
                if (BasesReglages.b(BasesReglages.AMELIORATION_BOIS, true)) {
                    ajouter(matieres, Costs.upgrade(e.getKey(), Tier.WOOD));
                }
            }
        }
        double marge = 1.0 + BasesReglages.i(BasesReglages.MARGE_POURCENT, 10) / 100.0;
        matieres.replaceAll((item, n) -> (int) Math.ceil(n * marge - 1e-9));
        // entretien du Claim Block : pièces en bois (ou brindilles) payées en « itemWood »
        int heures = BasesReglages.i(BasesReglages.ENTRETIEN_HEURES, 24);
        if (heures > 0 && Config.SPEC.isLoaded()) {
            Item bois = Config.upkeepItem(UpkeepResource.WOOD);
            int n = (int) Math.ceil(Config.upkeepPerPiece(UpkeepResource.WOOD) * pieces * heures / Config.upkeepHours.get());
            if (bois != Items.AIR && n > 0) {
                matieres.merge(bois, n, Integer::sum);
            }
        }
        matieres.forEach((item, n) -> m.merge(item, n, Integer::sum));
        List<ItemStack> out = new ArrayList<>();
        m.forEach((item, n) -> {
            int max = Math.max(1, new ItemStack(item).getMaxStackSize());
            for (int reste = n; reste > 0; reste -= max) {
                out.add(new ItemStack(item, Math.min(max, reste)));
            }
        });
        return out;
    }

    private static void ajouter(Map<Item, Integer> m, List<BuildCost> couts) {
        for (BuildCost c : couts) {
            if (!c.isValid()) {
                continue;
            }
            ItemStack[] choix = c.ingredient().getItems();
            if (choix.length == 0) {
                continue;
            }
            Item item = choix[0].getItem();
            // pour une liste (#minecraft:planks...), les planches de chêne si elles en sont
            for (ItemStack s : choix) {
                if (s.is(Items.OAK_PLANKS) || s.is(Items.OAK_LOG) || s.is(Items.COBBLESTONE)) {
                    item = s.getItem();
                    break;
                }
            }
            m.merge(item, c.count(), Integer::sum);
        }
    }

    private static void donner(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) {
            p.drop(s, false);
        }
    }

    /** Le groupe CRac du joueur ; s'il n'en a pas, celui de son clan (chef) ou un groupe à son nom, créé ici. */
    private static Group groupe(ServerPlayer p) {
        GroupData data = GroupData.get(p.server);
        Group g = data.getByMember(p.getUUID());
        if (g == null) {
            g = ClanAccess.groupForClanManager(p.server, p.getUUID());
        }
        if (g != null) {
            return g;
        }
        String nom = GroupNames.cleanName(p.getGameProfile().getName(), Config.groupNameMaxLength.get());
        if (nom == null) {
            nom = "Base";
        }
        String tag = null;
        String lettres = p.getGameProfile().getName().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (lettres.length() >= GroupNames.TAG_MIN) {
            String t = lettres.substring(0, Math.min(GroupNames.TAG_MAX, lettres.length()));
            if (!data.tagTaken(t)) {
                tag = t;
            }
        }
        for (int i = 0; tag == null && i < 200; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < GroupNames.TAG_MAX; k++) {
                sb.append((char) ('A' + p.getRandom().nextInt(26)));
            }
            if (!data.tagTaken(sb.toString())) {
                tag = sb.toString();
            }
        }
        if (tag == null) {
            return null;
        }
        g = data.create(nom, tag, 0x55E07A, p.getUUID());
        return g;
    }

    // ---------------------------------------------------------------------------------------------- commande

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tuto").requires(s -> s.hasPermission(2))
            .then(Commands.literal("rejouer").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> rejouer(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
            .then(Commands.literal("lancer").then(Commands.argument("joueur", EntityArgument.player())
                .executes(c -> lancer(c.getSource(), EntityArgument.getPlayer(c, "joueur"))))));
    }

    private static int lancer(CommandSourceStack src, ServerPlayer p) {
        if (src.getServer().getLevel(MondeBases.BASES) == null) {
            Reply.fail(src, T.t("crac.bases.absent", "Le monde des bases n'existe pas sur ce serveur (crac:bases)."));
            return 0;
        }
        boolean base = aUneBase(p);
        lancer(p);
        Reply.ok(src, (base
            ? T.t("crac.tuto.lance_base", "Tutoriel lancé pour %s : étapes à l'écran, kit, puis envoi dans le monde des bases. Il a déjà une base : pas de nouvelle protection de base.",
                p.getGameProfile().getName())
            : T.t("crac.tuto.lance", "Tutoriel lancé pour %s : étapes à l'écran, kit, puis envoi dans le monde des bases.", p.getGameProfile().getName()))
            .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    /**
     * Cinématique rejouée par le staff ({@code /cinematique} pour soi, {@code /cinematique joueur <cibles>}) : le tutoriel
     * du joueur repart à la fin de la cinématique (attente comme à la première arrivée), même en créatif, même s'il l'a
     * déjà fait ou a une base. Avant, rejouer la cinématique ne changeait rien au tutoriel. Lu avant la commande
     * (évènement de Forge) ; une commande mal tapée ne change rien.
     */
    @SubscribeEvent
    public static void commande(CommandEvent e) {
        try {
            ParseResults<CommandSourceStack> r = e.getParseResults();
            CommandContextBuilder<CommandSourceStack> b = r.getContext();
            if (b.getNodes().isEmpty() || !"cinematique".equals(b.getNodes().get(0).getNode().getName())
                || r.getReader().canRead() || !r.getExceptions().isEmpty() || !actif()) {
                return;
            }
            CommandSourceStack src = b.getSource();
            if (!src.hasPermission(2) || src.getServer().getLevel(MondeBases.BASES) == null) {
                return;
            }
            List<ServerPlayer> cibles = new ArrayList<>();
            if (b.getNodes().size() == 1) {
                // pour soi, la cinématique ne se rejoue que depuis le spawn (sinon Utils la refuse)
                if (src.getEntity() instanceof ServerPlayer sp && MondeBases.estSpawn(sp.level())) {
                    cibles.add(sp);
                }
            } else if ("joueur".equals(b.getNodes().get(1).getNode().getName()) && b.getArguments().containsKey("cibles")) {
                CommandContext<CommandSourceStack> ctx = b.build(r.getReader().getString());
                cibles.addAll(EntityArgument.getPlayers(ctx, "cibles"));
            }
            long now = src.getServer().overworld().getGameTime();
            for (ServerPlayer p : cibles) {
                if (SUIVIS.remove(p.getUUID()) != null) {
                    paquet(p, S2CTutoriel.cache());
                }
                ATTENTES.put(p.getUUID(), new Attente(now, p.position(), true));
                Reply.ok(src, T.t("crac.tuto.apres_cinematique", "Le tutoriel de %s repartira à la fin de la cinématique (/tuto lancer %s pour tout de suite).",
                    p.getGameProfile().getName(), p.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
            }
        } catch (CommandSyntaxException | RuntimeException ex) {
            Crac.LOGGER.debug("[CRac] tutoriel : lecture de /cinematique impossible ({})", ex.toString());
        }
    }

    private static int rejouer(CommandSourceStack src, ServerPlayer p) {
        BasesDonnees d = BasesDonnees.get(src.getServer());
        BasesDonnees.Joueur j = d.joueur(p.getUUID());
        boolean base = aUneBase(p);
        j.etape = BasesDonnees.Etape.AUCUNE;
        j.phaseTicks = 0;
        // un joueur qui a déjà une base ne regagne pas la protection de base par cette commande (/protection donner)
        j.protectionDue = !base;
        j.tuto = "";
        d.marquer();
        if (SUIVIS.containsKey(p.getUUID())) {
            paquet(p, S2CTutoriel.cache());
        }
        oublier(p.getUUID());
        fr.heraliz.crac.heraliz.Reply.ok(src, (base
            ? T.t("crac.tuto.rejouer_base", "%s a déjà une base : le tutoriel ne repartira que s'il la perd.", p.getGameProfile().getName())
            : T.t("crac.tuto.rejouer", "Tutoriel remis à zéro pour %s : il repart au spawn.", p.getGameProfile().getName()))
            .withStyle(base ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        if (!base && MondeBases.estSpawn(p.level()) && actif()) {
            demarrer(p, false);
        }
        return 1;
    }
}
